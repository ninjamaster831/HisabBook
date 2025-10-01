package com.guruyuknow.hisabbook.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guruyuknow.hisabbook.Expense
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class GroupExpenseViewModel : ViewModel() {

    private val repository = GroupExpenseRepository()
    private val auth get() = SupabaseManager.client.auth

    // Groups
    private val _allGroups = MutableLiveData<List<Group>>()
    val allGroups: LiveData<List<Group>> = _allGroups

    // Group creation result
    private val _groupCreationResult = MutableLiveData<Result<Long>>()
    val groupCreationResult: LiveData<Result<Long>> = _groupCreationResult

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

    // One-shot: become true right after a successful addExpense
    private val _expenseAdded = MutableLiveData<Boolean>()
    val expenseAdded: LiveData<Boolean> = _expenseAdded

    // ==================== ACTIONS ====================

    fun createGroup(name: String, budget: Double?, members: List<MemberInput>) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val currentUserId = getCurrentUserId()
                if (currentUserId == null) {
                    _groupCreationResult.value = Result.failure(IllegalStateException("User not logged in"))
                } else {
                    val result = repository.createGroup(
                        name = name,
                        budget = budget,
                        createdBy = currentUserId,
                        members = members
                    )
                    _groupCreationResult.value = result
                    if (result.isSuccess) loadAllGroups()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create group: ${e.message}"
                _groupCreationResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAllGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _allGroups.value = repository.getAllActiveGroups()
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

    /**
     * ✅ Preferred: add expense using the *selected group member* as payer.
     * This ensures expenses.paid_by == group_members.user_id.
     */
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

    /**
     * Legacy/low-level API (kept for compatibility).
     * Use only if you are SURE `paidBy` matches a member's userId.
     */
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
                    paidBy = paidBy,           // must equal GroupMember.userId
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

    fun clearError() { _errorMessage.value = null }

    fun markExpenseHandled() { _expenseAdded.value = false }

    // ==================== HELPERS ====================

    private fun getCurrentUserId(): String? =
        try { auth.currentUserOrNull()?.id } catch (_: Exception) { null }

    private fun getCurrentUserName(): String =
        try {
            val email = auth.currentUserOrNull()?.email
            email?.substringBefore("@") ?: "User"
        } catch (_: Exception) { "User" }
}
