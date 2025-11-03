package com.guruyuknow.hisabbook.group

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guruyuknow.hisabbook.Expense
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG_VM_CHAT = "GroupChatVM"

class GroupExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GroupExpenseRepository()
    private val auth get() = SupabaseManager.client.auth

    // Groups
    private val _allGroups = MutableLiveData<List<Group>>()
    val allGroups: LiveData<List<Group>> = _allGroups

    // ✅ FIXED: Make nullable to allow clearing
    private val _groupCreationResult = MutableLiveData<Result<Long>?>()
    val groupCreationResult: LiveData<Result<Long>?> = _groupCreationResult

    private val _joinResult = MutableLiveData<Result<Long>?>()
    val joinResult: LiveData<Result<Long>?> = _joinResult

    // Current group + data
    private val _currentGroup = MutableLiveData<Group?>()
    val currentGroup: LiveData<Group?> = _currentGroup

    private val _groupMembers = MutableLiveData<List<GroupMember>>()
    val groupMembers: LiveData<List<GroupMember>> = _groupMembers

    private val _groupExpenses = MutableLiveData<List<Expense>>()
    val groupExpenses: LiveData<List<Expense>> = _groupExpenses

    private val _groupBalances = MutableLiveData<List<MemberBalance>>()
    val groupBalances: LiveData<List<MemberBalance>> = _groupBalances

    private val _settlements = MutableLiveData<List<Settlement>>()
    val settlements: LiveData<List<Settlement>> = _settlements

    private val _groupStats = MutableLiveData<GroupStatistics>()
    val groupStats: LiveData<GroupStatistics> = _groupStats

    // UI state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _expenseAdded = MutableLiveData<Boolean>()
    val expenseAdded: LiveData<Boolean> = _expenseAdded

    private val _filteredGroups = MutableLiveData<List<Group>>()
    val filteredGroups: LiveData<List<Group>> = _filteredGroups

    private var currentFilter = "all"
    private var currentSearchQuery = ""

    private val _lastCreatedGroupCode = MutableLiveData<String?>()
    val lastCreatedGroupCode: LiveData<String?> = _lastCreatedGroupCode

    // Result of add-member action (null means "not set")
    private val _addMemberResult = MutableLiveData<Result<Unit>?>()
    val addMemberResult: LiveData<Result<Unit>?> = _addMemberResult

    fun clearAddMemberResult() { _addMemberResult.value = null }

    // ==================== ACTIONS ====================

    // ✅ Store created group info for showing code dialog
    private val _createdGroupInfo = MutableLiveData<Pair<String, String>?>()  // (name, code)
    val createdGroupInfo: LiveData<Pair<String, String>?> = _createdGroupInfo

    private val _memberCount = MutableLiveData<Int>()
    val memberCount: LiveData<Int> = _memberCount

    // Success messages (observed in GroupChatFragment)
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage
    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)
            try {
                val result = repository.deleteGroup(groupId)
                if (result.isSuccess) {
                    _successMessage.postValue("Group deleted successfully")
                    // Reload all groups to remove deleted group from list
                    loadAllGroups()
                } else {
                    _errorMessage.postValue("Failed to delete group: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "deleteGroup failed", e)
                _errorMessage.postValue("Failed to delete group: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    fun createGroup(name: String, budget: Double?, members: List<MemberInput>) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val currentUserId = getCurrentUserId()
                if (currentUserId == null) {
                    Log.e("GroupExpenseVM", "createGroup: User not logged in")
                    _groupCreationResult.value = Result.failure(IllegalStateException("User not logged in"))
                } else {
                    Log.d("GroupExpenseVM", "createGroup: Creating group '$name' for user $currentUserId")

                    val result = repository.createGroup(
                        name = name,
                        budget = budget,
                        createdBy = currentUserId,
                        members = members
                    )

                    Log.d("GroupExpenseVM", "createGroup: Repository returned: success=${result.isSuccess}")
                    _groupCreationResult.value = result

                    if (result.isSuccess) {
                        val groupId = result.getOrNull()
                        Log.d("GroupExpenseVM", "createGroup: Group ID = $groupId")

                        // ✅ Fetch the created group to get its code
                        if (groupId != null) {
                            val group = repository.getGroupById(groupId)
                            Log.d("GroupExpenseVM", "createGroup: Fetched group = ${group?.name}")

                            val code = group?.joinCode
                            Log.d("GroupExpenseVM", "createGroup: Join code = $code")

                            if (code != null) {
                                Log.d("GroupExpenseVM", "Group created: name=$name, code=$code")
                                Log.d("GroupExpenseVM", "Setting createdGroupInfo LiveData with: ($name, $code)")
                                _createdGroupInfo.value = Pair(name, code)
                                Log.d("GroupExpenseVM", "createdGroupInfo.value is now: ${_createdGroupInfo.value}")
                            } else {
                                Log.e("GroupExpenseVM", "createGroup: Join code is null!")
                            }
                        } else {
                            Log.e("GroupExpenseVM", "createGroup: Group ID is null!")
                        }

                        loadAllGroups()
                    } else {
                        Log.e("GroupExpenseVM", "createGroup failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "createGroup exception", e)
                _errorMessage.value = "Failed to create group: ${e.message}"
                _groupCreationResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSuccess() {
        _successMessage.value = null
    }
    fun loadGroupDetails(groupId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Load basic group info
                _currentGroup.value = repository.getGroupById(groupId)

                // Load members WITH user details using JOIN
                val membersWithDetails = loadGroupMembersWithUserDetails(groupId)
                _groupMembers.value = membersWithDetails
                _memberCount.value = membersWithDetails.size

                // Load other data
                _groupExpenses.value = repository.getExpensesByGroupId(groupId)
                _groupBalances.value = repository.getBalancesByGroupId(groupId)
                _groupStats.value = repository.getGroupStatistics(groupId)
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "loadGroupDetails failed", e)
                _errorMessage.value = "Failed to load group details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    // ==================== ADD THIS NEW METHOD ====================
    private suspend fun loadGroupMembersWithUserDetails(groupId: Long): List<GroupMember> {
        return try {
            Log.d("GroupExpenseVM", "Loading members with user details for group $groupId")

            // Query with JOIN to get user details from 'users' table
            val response = SupabaseManager.client
                .from("group_members")
                .select(columns = Columns.raw("""
                *,
                users!inner(
                    name,
                    email,
                    avatar_url
                )
            """)) {
                    filter {
                        eq("group_id", groupId)
                    }
                }
                .decodeList<GroupMemberWithUserResponse>()

            Log.d("GroupExpenseVM", "Loaded ${response.size} members with user details")

            // Map to GroupMember with ALL required fields
            response.map { item ->
                GroupMember(
                    id = item.id,
                    groupId = item.groupId,
                    userId = item.userId,
                    // Prioritize: users.name -> user_name -> email username -> "Unknown"
                    userName = item.users?.name
                        ?: item.userName
                        ?: item.users?.email?.substringBefore("@")
                        ?: "Unknown User",
                    isAdmin = item.isAdmin,
                    joinedAt = item.joinedAt,
                    avatarUrl = item.users?.avatarUrl,
                    userEmail = item.users?.email
                )
            }
        } catch (e: Exception) {
            Log.e("GroupExpenseVM", "loadGroupMembersWithUserDetails failed", e)
            Log.e("GroupExpenseVM", "Error details: ${e.message}")
            // Fallback to existing method if JOIN fails
            repository.getMembersByGroupId(groupId)
        }
    }


    @Serializable
    private data class GroupMemberWithUserResponse(
        val id: Long? = null,
        @SerialName("group_id")
        val groupId: Long,
        @SerialName("user_id")
        val userId: String,
        @SerialName("user_name")
        val userName: String?,
        @SerialName("is_admin")
        val isAdmin: Boolean = false,
        @SerialName("joined_at")
        val joinedAt: String?,
        val users: UserDetailsResponse?
    )

    @Serializable
    private data class UserDetailsResponse(
        val name: String?,
        val email: String?,
        @SerialName("avatar_url") val avatarUrl: String?
    )
    /**
     * Adds a member to the given group.
     * Expects MemberInput(userId=<phone or uid>, userName=<display name>)
     * Inserts a row in "group_members" table: (group_id, user_id, user_name).
     */
    fun addMemberToGroup(groupId: Long, member: MemberInput) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)

            try {
                val name = member.userName.trim()
                val uid = member.userId.trim()

                if (name.isEmpty() || uid.isEmpty()) {
                    throw IllegalArgumentException("Member name and phone cannot be empty")
                }

                val payload = GroupMemberInsert(
                    groupId = groupId,
                    userId = uid,
                    userName = name
                )

                Log.d("GroupExpenseVM", "Inserting member $name ($uid) into group $groupId")

                // ✅ Using insert instead of upsert (cleaner for your schema)
                SupabaseManager.client
                    .from("group_members")
                    .insert(payload)

                // ✅ Refresh UI (fast + full)
                fetchGroupMembers(groupId)      // NEW: quick members refresh for chips/count
                loadGroupDetails(groupId)       // existing deep refresh

                _addMemberResult.postValue(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "addMemberToGroup failed", e)
                _errorMessage.postValue("Failed to add member: ${e.message}")
                _addMemberResult.postValue(Result.failure(e))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    @Serializable
    private data class GroupMemberInsert(
        @SerialName("group_id") val groupId: Long,
        @SerialName("user_id") val userId: String,
        @SerialName("user_name") val userName: String,
        @SerialName("joined_at") val joinedAt: String = Clock.System.now().toString()
    )

    // ✅ NEW: Lightweight members-only refresh (used by Fragment to re-render chips immediately)
    fun fetchGroupMembers(groupId: Long) {
        viewModelScope.launch {
            try {
                val members = repository.getMembersByGroupId(groupId)
                _groupMembers.value = members
                _memberCount.value = members.size
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "fetchGroupMembers failed", e)
                _errorMessage.value = "Failed to load members: ${e.message}"
            }
        }
    }

    // ✅ RECOMMENDED: Join using authenticated user ID
    fun joinGroupByCode(code: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val currentUserId = getCurrentUserId()
                if (currentUserId == null) {
                    _joinResult.value = Result.failure(IllegalStateException("User not logged in"))
                    return@launch
                }

                Log.d("GroupExpenseVM", "joinGroupByCode: userId=$currentUserId, name=$name")
                val res = repository.joinGroupByCode(code, currentUserId, name)
                _joinResult.value = res

                if (res.isSuccess) {
                    Log.d("GroupExpenseVM", "Join successful, reloading groups")
                    loadAllGroups()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to join: ${e.message}"
                _joinResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateGroupInfo(groupId: Long, name: String, description: String) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)
            try {
                val result = repository.updateGroupInfo(groupId, name, description)
                if (result.isSuccess) {
                    _successMessage.postValue("Group info updated successfully")
                    loadGroupDetails(groupId)
                } else {
                    _errorMessage.postValue("Failed to update group info: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "updateGroupInfo failed", e)
                _errorMessage.postValue("Failed to update group info: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Update admin-only messaging setting
     */
    fun updateAdminOnlySettings(groupId: Long, adminOnly: Boolean) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)
            try {
                val result = repository.updateAdminOnlySettings(groupId, adminOnly)
                if (result.isSuccess) {
                    // Update local state immediately
                    _currentGroup.value?.let {
                        _currentGroup.postValue(it.copy(adminOnly = adminOnly))
                    }
                    val message = if (adminOnly) {
                        "Only admins can send messages now"
                    } else {
                        "All members can send messages now"
                    }
                    _successMessage.postValue(message)
                } else {
                    _errorMessage.postValue("Failed to update settings: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "updateAdminOnlySettings failed", e)
                _errorMessage.postValue("Failed to update settings: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Upload group image to Supabase Storage
     */
    fun uploadGroupImage(groupId: Long, imageUri: Uri, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                val result = repository.uploadGroupImage(
                    context = getApplication<Application>().applicationContext,
                    groupId = groupId,
                    imageUri = imageUri
                )

                if (result.isSuccess) {
                    val imageUrl = result.getOrNull()
                    _successMessage.postValue("Group photo updated successfully")

                    // Update local state
                    _currentGroup.value?.let {
                        _currentGroup.postValue(it.copy(imageUrl = imageUrl))
                    }

                    callback(true, imageUrl)
                } else {
                    _errorMessage.postValue("Failed to upload image: ${result.exceptionOrNull()?.message}")
                    callback(false, null)
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "uploadGroupImage failed", e)
                _errorMessage.postValue("Failed to upload image: ${e.message}")
                callback(false, null)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Update group image URL in database (or set to null to remove)
     */
    fun updateGroupImage(groupId: Long, imageUrl: String?) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                val result = repository.updateGroupImage(groupId, imageUrl)
                if (result.isSuccess) {
                    // Update local state
                    _currentGroup.value?.let {
                        _currentGroup.postValue(it.copy(imageUrl = imageUrl))
                    }
                    _successMessage.postValue(if (imageUrl == null) "Group photo removed" else "Group photo updated")
                } else {
                    _errorMessage.postValue("Failed to update image: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "updateGroupImage failed", e)
                _errorMessage.postValue("Failed to update image: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Update member admin status
     */
    fun updateMemberAdminStatus(groupId: Long, userId: String, isAdmin: Boolean) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                val result = repository.updateMemberAdminStatus(groupId, userId, isAdmin)
                if (result.isSuccess) {
                    _successMessage.postValue(if (isAdmin) "Admin role granted" else "Admin role removed")
                    // Reload members to reflect changes
                    fetchGroupMembers(groupId)
                } else {
                    _errorMessage.postValue("Failed to update member role: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "updateMemberAdminStatus failed", e)
                _errorMessage.postValue("Failed to update member role: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Remove member from group
     */
    fun removeMember(groupId: Long, userId: String) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                val result = repository.removeMember(groupId, userId)
                if (result.isSuccess) {
                    _successMessage.postValue("Member removed successfully")
                    // Reload members and balances
                    fetchGroupMembers(groupId)
                    loadGroupDetails(groupId)
                } else {
                    _errorMessage.postValue("Failed to remove member: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("GroupExpenseVM", "removeMember failed", e)
                _errorMessage.postValue("Failed to remove member: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun filterGroups(searchQuery: String, filter: String) {
        currentSearchQuery = searchQuery
        currentFilter = filter

        val allGroupsList = _allGroups.value ?: emptyList()

        val currentUserId = getCurrentUserId()

        Log.d("GroupExpenseVM", "filterGroups: filter=$filter, searchQuery='$searchQuery', " +
                "currentUserId=$currentUserId, allGroups=${allGroupsList.size}")

        var filtered = when (filter) {
            "mine" -> {
                // Groups where I am the creator
                allGroupsList.filter { group ->
                    val isCreator = group.createdBy == currentUserId
                    Log.d("GroupExpenseVM", "  Group '${group.name}': createdBy=${group.createdBy}, " +
                            "isCreator=$isCreator")
                    isCreator
                }
            }
            "joined" -> {
                // Groups where I am NOT the creator (but I'm a member)
                allGroupsList.filter { group ->
                    val isNotCreator = group.createdBy != currentUserId
                    Log.d("GroupExpenseVM", "  Group '${group.name}': createdBy=${group.createdBy}, " +
                            "isNotCreator=$isNotCreator")
                    isNotCreator
                }
            }
            else -> allGroupsList // "all"
        }

        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { group ->
                val nameMatches = group.name?.contains(searchQuery, ignoreCase = true) == true
                val codeMatches = (group.joinCode ?: "").contains(searchQuery, ignoreCase = true)
                nameMatches || codeMatches
            }
        }

        Log.d("GroupExpenseVM", "filterGroups: Result = ${filtered.size} groups")

        _filteredGroups.postValue(filtered)
    }

    fun loadAllGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val currentUserId = getCurrentUserId()
                if (currentUserId != null) {
                    val groups = repository.getAllActiveGroups(currentUserId)
                    _allGroups.value = groups

                    // ✅ Reapply current filter
                    filterGroups(currentSearchQuery, currentFilter)
                } else {
                    _errorMessage.value = "User not logged in"
                    _allGroups.value = emptyList()
                    _filteredGroups.value = emptyList()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load groups: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun addExpenseByMember(
        groupId: Long,
        amount: Double,
        description: String,
        payer: GroupMember
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val res = repository.addExpenseByMember(
                    groupId = groupId,
                    amount = amount,
                    description = description,
                    payer = payer
                )
                if (res.isSuccess) {
                    loadGroupDetails(groupId)
                    _expenseAdded.value = true
                } else {
                    _errorMessage.value = "Failed to add expense: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add expense: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addExpense(
        groupId: Long,
        amount: Double,
        description: String,
        paidBy: String,
        paidByName: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val res = repository.addExpense(
                    groupId = groupId,
                    amount = amount,
                    description = description,
                    paidBy = paidBy,
                    paidByName = paidByName
                )
                if (res.isSuccess) {
                    loadGroupDetails(groupId)
                    _expenseAdded.value = true
                } else {
                    _errorMessage.value = "Failed to add expense: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add expense: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculateSettlements(groupId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _settlements.value = repository.calculateSettlements(groupId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to calculate settlements: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ✅ Clear methods
    fun clearError() {
        _errorMessage.value = null
    }

    fun clearJoinResult() {
        _joinResult.value = null
    }

    fun clearGroupCreationResult() {
        _groupCreationResult.value = null
    }

    fun clearCreatedGroupInfo() {
        _createdGroupInfo.value = null
    }

    fun markExpenseHandled() {
        _expenseAdded.value = false
    }

    // ==================== CHAT STATE ====================
    private val _messages = MutableLiveData<List<GroupMessage>>(emptyList())
    val messages: LiveData<List<GroupMessage>> = _messages

    fun loadMessages(groupId: Long) {
        viewModelScope.launch {
            Log.d(TAG_VM_CHAT, "loadMessages(): groupId=$groupId")
            val res = repository.loadMessages(groupId)
            if (res.isSuccess) {
                _messages.value = res.getOrNull().orEmpty()
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG_VM_CHAT, "loadMessages(): $msg", res.exceptionOrNull())
                _errorMessage.value = "Failed to load messages: $msg"
            }
        }
    }

    fun sendMessage(groupId: Long, text: String) {
        viewModelScope.launch {
            val uid = try { SupabaseManager.client.auth.currentUserOrNull()?.id } catch (_: Exception) { null }
            Log.d(TAG_VM_CHAT, "sendMessage(): uid=$uid groupId=$groupId text='${text.take(50)}'")
            if (uid == null) {
                _errorMessage.value = "You're not logged in."
                Log.e(TAG_VM_CHAT, "sendMessage(): currentUserOrNull() is null")
                return@launch
            }

            val res = repository.sendMessage(groupId, text, uid)
            if (res.isSuccess) {
                loadMessages(groupId)
            } else {
                val err = res.exceptionOrNull()
                _errorMessage.value = "Failed to send: ${err?.message ?: "Unknown error"}"
                Log.e(TAG_VM_CHAT, "sendMessage() failed", err)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    // ==================== HELPERS ====================

    private fun getCurrentUserId(): String? =
        try { auth.currentUserOrNull()?.id } catch (_: Exception) { null }

    @Suppress("SameReturnValue")
    private fun getCurrentUserName(): String =
        try {
            val email = auth.currentUserOrNull()?.email
            email?.substringBefore("@") ?: "User"
        } catch (_: Exception) { "User" }
}
