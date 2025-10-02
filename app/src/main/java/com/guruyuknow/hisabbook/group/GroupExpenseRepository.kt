package com.guruyuknow.hisabbook.group

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.guruyuknow.hisabbook.Expense
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.data.expense.GroupInsert
import com.guruyuknow.hisabbook.data.expense.GroupIdOnly
import com.guruyuknow.hisabbook.data.expense.GroupMemberInsert
import com.guruyuknow.hisabbook.data.expense.MemberBalanceInsert
import com.guruyuknow.hisabbook.data.expense.MemberBalanceUpdate
import com.guruyuknow.hisabbook.data.expense.ExpenseInsert
import io.github.jan.supabase.exceptions.RestException
import kotlin.math.round

private const val TAG_RECALC = "Recalc"
private const val TAG_CHAT = "GroupChat"
class GroupExpenseRepository {

    private val supabase = SupabaseManager.client

    private object Tables {
        const val GROUPS = "groups"
        const val GROUP_MEMBERS = "group_members"
        const val MEMBER_BALANCES = "member_balances"
        const val EXPENSES = "expenses"
        const val GROUP_MESSAGES = "group_messages" // ✅ added
    }

    // ==================== GROUP OPERATIONS ====================

    suspend fun createGroup(
        name: String,
        budget: Double?,
        createdBy: String,
        members: List<MemberInput>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val groupId = supabase.from(Tables.GROUPS)
                .insert(
                    GroupInsert(
                        name = name,
                        budget = budget,
                        created_by = createdBy,
                        is_active = true
                    )
                ) {
                    select(columns = Columns.list("id"))
                    single()
                }
                .decodeAs<GroupIdOnly>()
                .id

            if (members.isNotEmpty()) {
                val memberRows = members.map { m ->
                    GroupMemberInsert(group_id = groupId, user_id = m.userId, user_name = m.userName)
                }
                supabase.from(Tables.GROUP_MEMBERS).insert(memberRows)

                val balanceRows = members.map { m ->
                    MemberBalanceInsert(group_id = groupId, user_id = m.userId, user_name = m.userName)
                }
                supabase.from(Tables.MEMBER_BALANCES).insert(balanceRows)
            }

            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all active groups where the user is either the creator OR a member
     */
    suspend fun getAllActiveGroups(userId: String): List<Group> = withContext(Dispatchers.IO) {
        try {
            // 1️⃣ Groups created by me
            val createdGroups = supabase.from(Tables.GROUPS)
                .select {
                    filter {
                        eq("is_active", true)
                        eq("created_by", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<Group>()

            // 2️⃣ Group IDs where I am a member
            val memberGroupIds = supabase.from(Tables.GROUP_MEMBERS)
                .select(columns = Columns.list("id:group_id")) {   // alias group_id → id
                    filter { eq("user_id", userId) }
                }
                .decodeList<GroupIdOnly>()
                .map { it.id }

            // 3️⃣ Fetch those groups
            val joinedGroups = if (memberGroupIds.isNotEmpty()) {
                supabase.from(Tables.GROUPS)
                    .select {
                        filter {
                            isIn("id", memberGroupIds)
                            eq("is_active", true)
                        }
                        order(column = "created_at", order = Order.DESCENDING)
                    }
                    .decodeList<Group>()
            } else {
                emptyList()
            }

            // 4️⃣ Combine & remove duplicates
            (createdGroups + joinedGroups)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e("GroupExpenseRepository", "Error fetching groups for user $userId", e)
            emptyList()
        }
    }




    suspend fun getGroupById(groupId: Long): Group? = withContext(Dispatchers.IO) {
        try {
            supabase.from(Tables.GROUPS)
                .select {
                    filter { eq("id", groupId) }
                    limit(1)
                }
                .decodeSingle<Group>()
        } catch (_: Exception) {
            null
        }
    }

    // ==================== MEMBER OPERATIONS ====================

    suspend fun getMembersByGroupId(groupId: Long): List<GroupMember> = withContext(Dispatchers.IO) {
        try {
            supabase.from(Tables.GROUP_MEMBERS)
                .select { filter { eq("group_id", groupId) } }
                .decodeList<GroupMember>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getMemberCount(groupId: Long): Int = withContext(Dispatchers.IO) {
        getMembersByGroupId(groupId).size
    }

    // ==================== EXPENSE OPERATIONS ====================

    /**
     * Preferred API: pass the selected GroupMember as payer so paid_by matches group_members.user_id.
     */
    suspend fun addExpenseByMember(
        groupId: Long,
        amount: Double,
        description: String,
        payer: GroupMember
    ): Result<Expense> = addExpense(
        groupId = groupId,
        amount = amount,
        description = description,
        paidBy = payer.userId,
        paidByName = payer.userName
    )

    /**
     * Lower-level API (kept for compatibility). Only use if you ensure paidBy equals a member userId.
     */
    suspend fun addExpense(
        groupId: Long,
        amount: Double,
        description: String,
        paidBy: String,
        paidByName: String
    ): Result<Expense> = withContext(Dispatchers.IO) {
        try {
            val expense = supabase.from(Tables.EXPENSES)
                .insert(
                    ExpenseInsert(
                        group_id = groupId,
                        amount = amount,
                        description = description,
                        paid_by = paidBy,
                        paid_by_name = paidByName
                    )
                ) {
                    select()
                    single()
                }
                .decodeAs<Expense>()

            recalculateBalances(groupId)
            Result.success(expense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getExpensesByGroupId(groupId: Long): List<Expense> = withContext(Dispatchers.IO) {
        try {
            supabase.from(Tables.EXPENSES)
                .select {
                    filter { eq("group_id", groupId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<Expense>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTotalExpenses(groupId: Long): Double = withContext(Dispatchers.IO) {
        getExpensesByGroupId(groupId).sumOf { it.amount }
    }

    // ==================== BALANCE OPERATIONS ====================

    suspend fun getBalancesByGroupId(groupId: Long): List<MemberBalance> = withContext(Dispatchers.IO) {
        try {
            supabase.from(Tables.MEMBER_BALANCES)
                .select {
                    filter { eq("group_id", groupId) }
                    order(column = "balance", order = Order.DESCENDING)
                }
                .decodeList<MemberBalance>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun recalculateBalances(groupId: Long) {
        val members = getMembersByGroupId(groupId)
        val expenses = getExpensesByGroupId(groupId)
        val count = members.size
        if (count == 0) return

        val total = expenses.sumOf { it.amount }
        val perPerson = total / count

        Log.d(TAG_RECALC, "Group $groupId → total=$total perPerson=$perPerson; members=$count")

        fun norm(s: String?): String = s?.trim()?.lowercase() ?: ""

        val paidById: Map<String, Double> =
            expenses.groupBy { it.paidBy }.mapValues { (_, list) -> list.sumOf { it.amount } }

        val paidByName: Map<String, Double> =
            expenses.groupBy { norm(it.paidByName) }.mapValues { (_, list) -> list.sumOf { it.amount } }

        for (member in members) {
            val id = member.userId
            val name = member.userName

            val viaId = paidById[id] ?: 0.0
            val viaName = paidByName[norm(name)] ?: 0.0

            val paidAmount = if (viaId > 0.0) viaId else viaName
            val balance = paidAmount - perPerson

            Log.d(
                TAG_RECALC,
                "Member=$name uid=$id → paidById=$viaId paidByName=$viaName chosen=$paidAmount balance=$balance"
            )

            try {
                supabase.from(Tables.MEMBER_BALANCES)
                    .update(
                        MemberBalanceUpdate(
                            total_paid = paidAmount,
                            total_owed = perPerson,
                            balance = balance
                        )
                    ) {
                        filter {
                            eq("group_id", groupId)
                            eq("user_id", id)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG_RECALC, "Update failed for uid=$id", e)
            }
        }
    }

    // ==================== SETTLEMENT CALCULATIONS ====================

    suspend fun calculateSettlements(groupId: Long): List<Settlement> = withContext(Dispatchers.IO) {
        val balances = getBalancesByGroupId(groupId).sortedByDescending { it.balance }

        val settlements = mutableListOf<Settlement>()
        val creditors = balances.filter { it.balance > 0.01 }.toMutableList()
        val debtors = balances.filter { it.balance < -0.01 }.toMutableList()

        var i = 0
        var j = 0
        while (i < creditors.size && j < debtors.size) {
            val c = creditors[i]
            val d = debtors[j]
            val amount = minOf(c.balance, -d.balance)

            settlements += Settlement(
                fromUser = d.userId,
                fromUserName = d.userName,
                toUser = c.userId,
                toUserName = c.userName,
                amount = round(amount * 100) / 100.0
            )

            creditors[i] = c.copy(balance = c.balance - amount)
            debtors[j] = d.copy(balance = d.balance + amount)

            if (creditors[i].balance < 0.01) i++
            if (debtors[j].balance > -0.01) j++
        }

        settlements
    }
    suspend fun joinGroupByCodeWithPhone(
        code: String,
        phone: String,
        userName: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 1. Find group by join_code
            val group = supabase.from(Tables.GROUPS)
                .select {
                    filter { eq("join_code", code) }
                    limit(1)
                }
                .decodeSingle<Group>()  // throws if not found

            val groupId = group.id ?: return@withContext Result.failure<Long>(
                IllegalStateException("Group id missing")
            )

            // 2. Insert into group_members (phone used as user_id)
            supabase.from(Tables.GROUP_MEMBERS).insert(
                GroupMemberInsert(
                    group_id = groupId,
                    user_id = phone,
                    user_name = userName
                )
            )

            // 3. Insert into member_balances
            supabase.from(Tables.MEMBER_BALANCES).insert(
                MemberBalanceInsert(
                    group_id = groupId,
                    user_id = phone,
                    user_name = userName
                )
            )

            Result.success(groupId)  // ✅ Non-nullable Long
        } catch (e: Exception) {
            Log.e(TAG_CHAT, "joinGroupByCodeWithPhone() failed", e)
            Result.failure(e)
        }
    }

    // ==================== STATISTICS ====================
    private suspend fun isMember(groupId: Long, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = supabase.from(Tables.GROUP_MEMBERS)
                .select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<GroupMember>()
            rows.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG_CHAT, "isMember() failed", e)
            false
        }
    }

    suspend fun getGroupStatistics(groupId: Long): GroupStatistics = withContext(Dispatchers.IO) {
        val totalExpenses = getTotalExpenses(groupId)
        val memberCount = getMemberCount(groupId)
        val group = getGroupById(groupId)

        GroupStatistics(
            totalExpenses = totalExpenses,
            memberCount = memberCount,
            perPersonShare = if (memberCount > 0) totalExpenses / memberCount else 0.0,
            budget = group?.budget,
            remainingBudget = group?.budget?.let { it - totalExpenses }
        )
    }

    // ==================== CHAT (moved inside the class) ====================
    private fun logSupabaseError(tag: String, where: String, e: Throwable) {
        Log.e(tag, "$where: ${e.message} (type=${e::class.qualifiedName})", e)
        if (e is RestException) {
            // These are the stable fields on RestException
            Log.e(tag, "$where: error=${e.error ?: "-"}")
            // If your library version later adds more fields, they just won’t crash here.
        }
    }
    suspend fun sendMessage(groupId: Long, text: String, senderId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Message is empty"))
            }

            try {
                val inserted = supabase.from(Tables.GROUP_MESSAGES)
                    .insert(
                        GroupMessageInsert(
                            groupId = groupId,
                            senderId = senderId,
                            message = trimmed
                        )
                    ) {
                        select()  // return inserted row
                        single()
                    }
                    .decodeAs<GroupMessage>() // your GroupMessage is @Serializable ✅

                Log.d(TAG_CHAT, "sendMessage(): inserted id=${inserted.id} by=${inserted.senderId}")
                Result.success(Unit)
            } catch (e: RestException) {
                logSupabaseError(TAG_CHAT, "sendMessage()", e)
                Result.failure(e)
            } catch (e: Exception) {
                logSupabaseError(TAG_CHAT, "sendMessage()", e)
                Result.failure(e)
            }
        }


    suspend fun loadMessages(groupId: Long): Result<List<GroupMessage>> = withContext(Dispatchers.IO) {
        try {
            val rows = supabase.from(Tables.GROUP_MESSAGES)
                .select {
                    filter { eq("group_id", groupId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<GroupMessage>()
            Result.success(rows.reversed())
        } catch (e: RestException) {
            logSupabaseError(TAG_CHAT, "loadMessages()", e)
            Result.failure(e)
        } catch (e: Exception) {
            logSupabaseError(TAG_CHAT, "loadMessages()", e)
            Result.failure(e)
        }
    }
}

// Used by UI when creating a group
data class MemberInput(
    val userId: String,
    val userName: String
)
