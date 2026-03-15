package guru.urchin.group

import android.util.Base64
import guru.urchin.data.AffinityGroupEntity
import guru.urchin.data.AffinityGroupMemberEntity
import guru.urchin.data.AlertRuleDao
import guru.urchin.data.DeviceDao
import guru.urchin.data.SightingDao
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports local data as an encrypted .urchin bundle for sharing with affinity group members.
 *
 * Supports two encryption modes:
 * - Symmetric: derives encryption key from shared group key via HKDF (legacy/default)
 * - Hybrid ECDH: generates random CEK, wraps it per-recipient via ECDH key agreement
 */
class BundleExporter(
  private val deviceDao: DeviceDao,
  private val sightingDao: SightingDao,
  private val alertRuleDao: AlertRuleDao
) {
  suspend fun export(
    group: AffinityGroupEntity,
    config: GroupSharingConfig,
    members: List<AffinityGroupMemberEntity> = emptyList()
  ): ByteArray {
    val exportTimestamp = System.currentTimeMillis()
    val windowMs = config.exportWindowDays.toLong() * 24 * 60 * 60 * 1000
    val windowStart = exportTimestamp - windowMs

    val devices = if (config.devices) deviceDao.getDevices() else emptyList()
    val sightings = if (config.sightings) sightingDao.getSightingsAfter(windowStart) else emptyList()
    val alertRules = if (config.alertRules) alertRuleDao.getRules() else emptyList()
    val starredKeys = if (config.starredDevices) {
      devices.filter { it.starred }.map { it.deviceKey }
    } else emptyList()

    val payloadJson = BundleSerializer.serializePayload(
      devices, sightings, alertRules, starredKeys
    )
    val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)

    val cek = ByteArray(32)
    SecureRandom().nextBytes(cek)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"))
    val ciphertext = cipher.doFinal(payloadBytes)
    val payloadNonce = cipher.iv

    val contentTypes = JSONArray()
    if (config.devices) contentTypes.put("devices")
    if (config.sightings) contentTypes.put("sightings")
    if (config.alertRules) contentTypes.put("alertRules")
    if (config.starredDevices) contentTypes.put("starredDevices")

    val itemCounts = JSONObject().apply {
      put("devices", devices.size)
      put("sightings", sightings.size)
      put("alertRules", alertRules.size)
      put("starredDeviceKeys", starredKeys.size)
    }

    val manifest = JSONObject().apply {
      put("formatVersion", 2)
      put("type", "affinity-bundle")
      put("groupId", group.groupId)
      put("senderId", group.myMemberId)
      put("senderDisplayName", group.myDisplayName)
      put("exportTimestamp", exportTimestamp)
      put("keyEpoch", group.keyEpoch)
      put("contentTypes", contentTypes)
      put("itemCounts", itemCounts)
      put("payloadNonce", Base64.encodeToString(payloadNonce, Base64.NO_WRAP))
    }

    val senderPublicKey = EcdhKeyManager.getPublicKey(group.groupId, group.myMemberId)
    if (senderPublicKey != null) {
      manifest.put("senderPublicKey", senderPublicKey)
    }

    val recipientKeys = buildRecipientKeys(group, members, cek, exportTimestamp)
    if (recipientKeys.length() > 0) {
      manifest.put("recipientKeys", recipientKeys)
    }

    if (!group.requireEcdh) {
      val groupKey = GroupKeyManager.unwrapKey(group.groupKeyWrapped)
      val infoString = "urchin-bundle-cek-wrap-${exportTimestamp}"
      val wrapKey = Hkdf.deriveKey(
        groupKey.encoded, salt = null,
        info = infoString.toByteArray(Charsets.UTF_8), length = 32
      )
      val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
      wrapCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrapKey, "AES"))
      val wrappedCek = wrapCipher.doFinal(cek)
      manifest.put("symmetricCekNonce", Base64.encodeToString(wrapCipher.iv, Base64.NO_WRAP))
      manifest.put("symmetricCekWrapped", Base64.encodeToString(wrappedCek, Base64.NO_WRAP))
    }

    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zip ->
      zip.putNextEntry(ZipEntry("manifest.json"))
      zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
      zip.closeEntry()

      zip.putNextEntry(ZipEntry("payload.enc"))
      zip.write(ciphertext)
      zip.closeEntry()
    }

    return baos.toByteArray()
  }

  private fun buildRecipientKeys(
    group: AffinityGroupEntity,
    members: List<AffinityGroupMemberEntity>,
    cek: ByteArray,
    exportTimestamp: Long
  ): JSONArray {
    val recipientKeys = JSONArray()
    val eligibleMembers = members.filter { !it.revoked && it.publicKeyBase64 != null && it.memberId != group.myMemberId }

    for (member in eligibleMembers) {
      try {
        val info = "urchin-ecdh-cek-wrap-${group.groupId}-${exportTimestamp}".toByteArray(Charsets.UTF_8)
        val sharedSecret = EcdhKeyManager.deriveSharedSecret(
          group.groupId, group.myMemberId, member.publicKeyBase64!!, info
        )
        val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
        wrapCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"))
        val wrappedCek = wrapCipher.doFinal(cek)

        recipientKeys.put(JSONObject().apply {
          put("memberId", member.memberId)
          put("wrappedCek", Base64.encodeToString(wrappedCek, Base64.NO_WRAP))
          put("nonce", Base64.encodeToString(wrapCipher.iv, Base64.NO_WRAP))
        })
      } catch (_: Exception) {
        // Skip members where ECDH fails
      }
    }

    return recipientKeys
  }

  companion object {
    fun defaultFileName(groupName: String): String {
      val sanitized = groupName
        .trim()
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .take(32)
        .ifEmpty { "group" }
      return "urchin-${sanitized}-${System.currentTimeMillis()}.urchin"
    }
  }
}
