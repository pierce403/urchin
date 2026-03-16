package guru.urchin.data

import guru.urchin.group.EcdhKeyManager
import guru.urchin.group.GroupKeyManager
import kotlinx.coroutines.flow.Flow

class AffinityGroupRepository(
  private val dao: AffinityGroupDao
) {
  fun observeGroups(): Flow<List<AffinityGroupEntity>> = dao.observeGroups()

  fun observeGroup(groupId: String): Flow<AffinityGroupEntity?> = dao.observeGroup(groupId)

  suspend fun getGroups(): List<AffinityGroupEntity> = dao.getGroups()

  suspend fun getGroup(groupId: String): AffinityGroupEntity? = dao.getGroup(groupId)

  suspend fun createGroup(group: AffinityGroupEntity) = dao.insert(group)

  suspend fun updateGroup(group: AffinityGroupEntity) = dao.update(group)

  suspend fun deleteGroup(groupId: String) {
    val members = dao.getMembers(groupId)
    for (member in members) {
      EcdhKeyManager.deleteKeypair(groupId, member.memberId)
    }
    dao.deleteMembersByGroup(groupId)
    dao.deleteById(groupId)
  }

  // Members

  fun observeMembers(groupId: String): Flow<List<AffinityGroupMemberEntity>> =
    dao.observeMembers(groupId)

  suspend fun getMembers(groupId: String): List<AffinityGroupMemberEntity> =
    dao.getMembers(groupId)

  suspend fun addMember(member: AffinityGroupMemberEntity) = dao.insertMember(member)

  suspend fun revokeMember(groupId: String, memberId: String) =
    dao.revokeMember(groupId, memberId)

  suspend fun revokeMemberAndRotateKey(groupId: String, memberId: String): AffinityGroupEntity? {
    dao.revokeMember(groupId, memberId)
    EcdhKeyManager.deleteKeypair(groupId, memberId)

    val group = dao.getGroup(groupId) ?: return null
    val (_, newWrappedKey) = GroupKeyManager.rotateGroupKey()
    val updated = group.copy(
      groupKeyWrapped = newWrappedKey,
      keyEpoch = group.keyEpoch + 1,
      requireEcdh = true
    )
    dao.update(updated)
    return updated
  }

  // Import log

  suspend fun getImportLog(groupId: String): List<AffinityImportLogEntity> =
    dao.getImportLog(groupId)

  suspend fun hasImport(groupId: String, senderId: String, exportTimestamp: Long): Boolean =
    dao.hasImport(groupId, senderId, exportTimestamp) > 0

  suspend fun logImport(log: AffinityImportLogEntity) = dao.insertImportLog(log)
}
