package guru.urchin.group

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Manages P-256 ECDH keypairs for hybrid bundle encryption.
 *
 * Private keys are stored in Android Keystore (alias: urchin_ecdh_{groupId}_{memberId}).
 * Public keys are stored as Base64 in AffinityGroupMemberEntity.publicKeyBase64.
 */
object EcdhKeyManager {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"

  private fun aliasFor(groupId: String, memberId: String): String =
    "urchin_ecdh_${groupId}_${memberId}"

  fun generateKeypair(groupId: String, memberId: String): String {
    val alias = aliasFor(groupId, memberId)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)

    if (keyStore.containsAlias(alias)) {
      val cert = keyStore.getCertificate(alias)
      return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
      .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
      .build()

    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
    kpg.initialize(spec)
    val keyPair = kpg.generateKeyPair()

    return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
  }

  fun deriveSharedSecret(
    groupId: String,
    memberId: String,
    peerPublicKeyBase64: String,
    info: ByteArray
  ): ByteArray {
    val alias = aliasFor(groupId, memberId)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)

    val privateKey = keyStore.getKey(alias, null)
      ?: throw IllegalStateException("No ECDH private key for $alias")

    val peerKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
    val keyFactory = KeyFactory.getInstance("EC")
    val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

    val keyAgreement = KeyAgreement.getInstance("ECDH")
    keyAgreement.init(privateKey)
    keyAgreement.doPhase(peerPublicKey, true)
    val rawSecret = keyAgreement.generateSecret()

    return Hkdf.deriveKey(rawSecret, salt = null, info = info, length = 32)
  }

  fun getPublicKey(groupId: String, memberId: String): String? {
    val alias = aliasFor(groupId, memberId)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    val cert = keyStore.getCertificate(alias) ?: return null
    return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
  }

  fun hasKeypair(groupId: String, memberId: String): Boolean {
    val alias = aliasFor(groupId, memberId)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    return keyStore.containsAlias(alias)
  }

  fun deleteKeypair(groupId: String, memberId: String) {
    val alias = aliasFor(groupId, memberId)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    if (keyStore.containsAlias(alias)) {
      keyStore.deleteEntry(alias)
    }
  }
}
