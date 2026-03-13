package guru.urchin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import guru.urchin.R
import guru.urchin.UrchinApp
import guru.urchin.data.AffinityGroupEntity
import guru.urchin.data.AffinityGroupMemberEntity
import guru.urchin.data.AffinityGroupRepository
import guru.urchin.databinding.ActivityAffinityGroupsBinding
import guru.urchin.group.GroupKeyManager
import guru.urchin.group.EcdhKeyManager
import guru.urchin.group.GroupSharingConfig
import guru.urchin.util.DeviceCredentialHelper
import guru.urchin.util.WindowInsetsHelper
import org.json.JSONObject
import java.util.UUID

class AffinityGroupsActivity : AppCompatActivity() {
  private lateinit var binding: ActivityAffinityGroupsBinding
  private lateinit var adapter: AffinityGroupAdapter
  private val repository: AffinityGroupRepository by lazy {
    (application as UrchinApp).affinityGroupRepository
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityAffinityGroupsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = getString(R.string.affinity_groups)
    WindowInsetsHelper.applyToolbarInsets(binding.toolbar)
    WindowInsetsHelper.applyBottomInsets(binding.groupsList)
    WindowInsetsHelper.requestApplyInsets(binding.root)

    adapter = AffinityGroupAdapter { group ->
      startActivity(
        Intent(this, AffinityGroupDetailActivity::class.java)
          .putExtra("groupId", group.groupId)
      )
    }
    binding.groupsList.layoutManager = LinearLayoutManager(this)
    binding.groupsList.adapter = adapter

    binding.addGroupFab.setOnClickListener { showCreateOrJoinDialog() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        repository.observeGroups().collect { groups ->
          adapter.submitList(groups)
          binding.emptyGroupState.isVisible = groups.isEmpty()
          binding.groupsList.isVisible = groups.isNotEmpty()
        }
      }
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  private fun showCreateOrJoinDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.affinity_groups)
      .setItems(arrayOf(getString(R.string.create_group), getString(R.string.join_group))) { _, which ->
        when (which) {
          0 -> DeviceCredentialHelper.authenticate(
            this,
            getString(R.string.auth_required_title),
            getString(R.string.auth_required_create_group),
            onSuccess = { showCreateGroupDialog() }
          )
          1 -> showJoinGroupDialog()
        }
      }
      .show()
  }

  private fun showCreateGroupDialog() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(64, 32, 64, 0)
    }
    val nameInputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
      hint = getString(R.string.group_name_hint)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { bottomMargin = 16 }
    }
    val nameInput = TextInputEditText(nameInputLayout.context)
    nameInputLayout.addView(nameInput)

    val displayNameInputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
      hint = getString(R.string.your_display_name_hint)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }
    val displayNameInput = TextInputEditText(displayNameInputLayout.context)
    displayNameInputLayout.addView(displayNameInput)

    layout.addView(nameInputLayout)
    layout.addView(displayNameInputLayout)

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.create_group)
      .setView(layout)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.create_group) { _, _ ->
        val name = nameInput.text.toString().trim()
        val displayName = displayNameInput.text.toString().trim()
        if (name.isNotEmpty() && displayName.isNotEmpty()) {
          createGroup(name, displayName)
        }
      }
      .show()
  }

  private fun createGroup(name: String, displayName: String) {
    lifecycleScope.launch {
      val groupId = UUID.randomUUID().toString()
      val memberId = UUID.randomUUID().toString()
      val rawKey = GroupKeyManager.generateGroupKey()
      val wrappedKey = GroupKeyManager.wrapKey(rawKey)
      val config = GroupSharingConfig()

      val group = AffinityGroupEntity(
        groupId = groupId,
        groupName = name,
        createdAt = System.currentTimeMillis(),
        myMemberId = memberId,
        myDisplayName = displayName,
        groupKeyWrapped = wrappedKey,
        keyEpoch = 1,
        sharingConfigJson = config.toJson()
      )
      val publicKeyBase64 = EcdhKeyManager.generateKeypair(groupId, memberId)

      repository.createGroup(group)
      repository.addMember(
        AffinityGroupMemberEntity(
          groupId = groupId,
          memberId = memberId,
          displayName = displayName,
          joinedAt = System.currentTimeMillis(),
          lastSeenEpoch = 1,
          publicKeyBase64 = publicKeyBase64,
          revoked = false
        )
      )

      val shareJson = JSONObject().apply {
        put("g", groupId)
        put("n", name)
        put("k", GroupKeyManager.exportKeyForSharing(rawKey))
        put("e", 1)
        put("c", displayName)
        put("m", memberId)
        put("pk", publicKeyBase64)
        put("h", GroupKeyManager.computeKeyChecksum(rawKey, groupId))
      }

      copyToClipboardSensitive(shareJson.toString())
      toast(getString(R.string.group_created))
      toast(getString(R.string.group_key_copied))
    }
  }

  private fun showJoinGroupDialog() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(64, 32, 64, 0)
    }
    val keyInputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
      hint = getString(R.string.join_paste_hint)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { bottomMargin = 16 }
    }
    val keyInput = TextInputEditText(keyInputLayout.context).apply {
      minLines = 3
    }
    keyInputLayout.addView(keyInput)

    val displayNameInputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
      hint = getString(R.string.your_display_name_hint)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }
    val displayNameInput = TextInputEditText(displayNameInputLayout.context)
    displayNameInputLayout.addView(displayNameInput)

    layout.addView(keyInputLayout)
    layout.addView(displayNameInputLayout)

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.join_group)
      .setView(layout)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.join_group) { _, _ ->
        val keyJson = keyInput.text.toString().trim()
        val displayName = displayNameInput.text.toString().trim()
        if (keyJson.isNotEmpty() && displayName.isNotEmpty()) {
          joinGroup(keyJson, displayName)
        }
      }
      .show()
  }

  private fun joinGroup(keyJson: String, displayName: String) {
    lifecycleScope.launch {
      try {
        val obj = JSONObject(keyJson)
        val groupId = obj.getString("g")
        val groupName = obj.getString("n")
        val rawKeyBase64 = obj.getString("k")
        val epoch = obj.getInt("e")
        val creatorName = obj.optString("c", "Creator")
        val creatorMemberId = obj.optString("m", UUID.randomUUID().toString())

        if (repository.getGroup(groupId) != null) {
          toast(getString(R.string.group_joined))
          return@launch
        }

        val rawKey = GroupKeyManager.importKeyFromSharing(rawKeyBase64)

        val checksum = obj.optString("h", "")
        if (checksum.isNotEmpty() && !GroupKeyManager.verifyKeyChecksum(rawKey, groupId, checksum)) {
          toast(getString(R.string.join_invalid_key))
          return@launch
        }

        val wrappedKey = GroupKeyManager.wrapKey(rawKey)
        val memberId = UUID.randomUUID().toString()
        val config = GroupSharingConfig()

        val publicKeyBase64 = EcdhKeyManager.generateKeypair(groupId, memberId)
        val creatorPublicKey = if (obj.has("pk")) obj.getString("pk") else null

        repository.createGroup(
          AffinityGroupEntity(
            groupId = groupId,
            groupName = groupName,
            createdAt = System.currentTimeMillis(),
            myMemberId = memberId,
            myDisplayName = displayName,
            groupKeyWrapped = wrappedKey,
            keyEpoch = epoch,
            sharingConfigJson = config.toJson()
          )
        )

        repository.addMember(
          AffinityGroupMemberEntity(
            groupId = groupId, memberId = memberId, displayName = displayName,
            joinedAt = System.currentTimeMillis(), lastSeenEpoch = epoch,
            publicKeyBase64 = publicKeyBase64, revoked = false
          )
        )

        repository.addMember(
          AffinityGroupMemberEntity(
            groupId = groupId, memberId = creatorMemberId, displayName = creatorName,
            joinedAt = System.currentTimeMillis(), lastSeenEpoch = epoch,
            publicKeyBase64 = creatorPublicKey, revoked = false
          )
        )

        toast(getString(R.string.group_joined))
      } catch (e: Exception) {
        toast(getString(R.string.join_invalid_key))
      }
    }
  }

  private fun copyToClipboardSensitive(text: String) {
    val clip = ClipData.newPlainText("Urchin group key", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
      }
    }
    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(clip)
    scheduleClipboardClear(clipboard)
  }

  private fun scheduleClipboardClear(clipboard: ClipboardManager) {
    Handler(Looper.getMainLooper()).postDelayed({
      val current = clipboard.primaryClip
      if (current?.description?.label == "Urchin group key") {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
      }
    }, 60_000)
  }

  private fun toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }
}
