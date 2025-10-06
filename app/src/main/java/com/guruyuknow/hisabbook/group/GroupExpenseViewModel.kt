package com.guruyuknow.hisabbook.group

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guruyuknow.hisabbook.Expense
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
private const val TAG_VM_CHAT = "GroupChatVM"

class GroupExpenseViewModel : ViewModel() {

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

    // ==================== ACTIONS ====================

    // ✅ Store created group info for showing code dialog
    private val _createdGroupInfo = MutableLiveData<Pair<String, String>?>()  // (name, code)
    val createdGroupInfo: LiveData<Pair<String, String>?> = _createdGroupInfo
    private val _memberCount = MutableLiveData<Int>()
    val memberCount: LiveData<Int> = _memberCount

    // Success messages (observed in GroupChatFragment)
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage
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

    // ⚠️ LEGACY: Join using phone number (for non-authenticated users)
    fun joinGroupByCodeWithPhone(code: String, name: String, phone: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val res = repository.joinGroupByCodeWithPhone(code, phone, name)
                _joinResult.value = res
                if (res.isSuccess) {
                    // ✅ Reload groups to include the newly joined group
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

    fun loadGroupDetails(groupId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _currentGroup.value = repository.getGroupById(groupId)
                _groupMembers.value = repository.getMembersByGroupId(groupId)
                _groupExpenses.value = repository.getExpensesByGroupId(groupId)
                _groupBalances.value = repository.getBalancesByGroupId(groupId)
                _groupStats.value = repository.getGroupStatistics(groupId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load group details: ${e.message}"
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