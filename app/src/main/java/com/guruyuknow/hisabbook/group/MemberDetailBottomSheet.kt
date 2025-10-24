package com.guruyuknow.hisabbook.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import com.google.android.material.textview.MaterialTextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class MemberDetailBottomSheet : BottomSheetDialogFragment() {

    private var groupId: Long = 0
    private var userId: String = ""

    private var tvName: MaterialTextView? = null
    private var tvPhone: MaterialTextView? = null
    private var tvJoined: MaterialTextView? = null
    private var tvTotalPaid: MaterialTextView? = null
    private var rvExpenses: RecyclerView? = null
    private val expenseAdapter = MemberExpenseAdapter()

    companion object {
        private const val ARG_GROUP_ID = "arg_group_id"
        private const val ARG_USER_ID = "arg_user_id"

        fun newInstance(groupId: Long, userId: String) = MemberDetailBottomSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_GROUP_ID, groupId)
                putString(ARG_USER_ID, userId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = requireArguments().getLong(ARG_GROUP_ID)
        userId = requireArguments().getString(ARG_USER_ID, "")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_member_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvName = view.findViewById(R.id.tvName)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvJoined = view.findViewById(R.id.tvJoined)
        tvTotalPaid = view.findViewById(R.id.tvTotalPaid)
        rvExpenses = view.findViewById(R.id.rvMemberExpenses)
        rvExpenses?.layoutManager = LinearLayoutManager(requireContext())
        rvExpenses?.adapter = expenseAdapter

        loadMemberDetails()
    }

    private fun loadMemberDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) { fetchSummaryAndExpenses(groupId, userId) }
            // Bind
            tvName?.text = summary.userName
            tvPhone?.text = "+91 ${summary.userId}"
            tvJoined?.text = summary.joinedAtFormatted
            tvTotalPaid?.text = "₹" + String.format("%.2f", summary.totalPaid)
            expenseAdapter.submit(summary.expenses)
            rvExpenses?.isVisible = summary.expenses.isNotEmpty()
        }
    }

    // ---- Data access (Supabase) ----

    private suspend fun fetchSummaryAndExpenses(groupId: Long, userId: String): MemberSummary {
        val client = SupabaseManager.client

        // 1) Member row
        val member = client
            .from("group_members")
            .select {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeList<GroupMemberRow>()
            .firstOrNull() ?: GroupMemberRow(groupId, userId, userName = "Member", joinedAt = "")

        // 2) Expenses by this member in this group
        val expenses = client
            .from("expenses")
            .select {
                filter {
                    eq("group_id", groupId)
                    eq("paid_by", userId)
                }
                order("created_at", order = Order.DESCENDING)
            }
            .decodeList<MemberExpenseRow>()

        val total = expenses.sumOf { it.amount ?: 0.0 }

        return MemberSummary(
            userId = member.userId,
            userName = member.userName,
            joinedAtFormatted = member.joinedAt.take(19).replace('T',' '), // simple format
            totalPaid = total,
            expenses = expenses
        )
    }

    // ---- Models for decoding ----
    @Serializable
    private data class GroupMemberRow(
        @SerialName("group_id") val groupId: Long,
        @SerialName("user_id") val userId: String,
        @SerialName("user_name") val userName: String,
        @SerialName("joined_at") val joinedAt: String
    )

    @Serializable
    data class MemberExpenseRow(
        @SerialName("id") val id: Long? = null,
        @SerialName("group_id") val groupId: Long? = null,
        @SerialName("amount") val amount: Double? = null,
        @SerialName("description") val description: String? = null,
        @SerialName("paid_by") val paidBy: String? = null,
        @SerialName("paid_by_name") val paidByName: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    data class MemberSummary(
        val userId: String,
        val userName: String,
        val joinedAtFormatted: String,
        val totalPaid: Double,
        val expenses: List<MemberExpenseRow>
    )

    // ---- Simple RecyclerView adapter for the expense list ----
    private class MemberExpenseAdapter : RecyclerView.Adapter<VH>() {
        private val items = mutableListOf<MemberExpenseRow>()
        fun submit(list: List<MemberExpenseRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_member_expense, parent, false)
            return VH(v)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }
    }

    private class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDesc: MaterialTextView = view.findViewById(R.id.tvDesc)
        private val tvAmt: MaterialTextView = view.findViewById(R.id.tvAmt)
        private val tvDate: MaterialTextView = view.findViewById(R.id.tvDate)
        fun bind(e: MemberExpenseRow) {
            tvDesc.text = e.description ?: "(no description)"
            tvAmt.text = "₹" + String.format("%.2f", e.amount ?: 0.0)
            tvDate.text = (e.createdAt ?: "").take(19).replace('T',' ')
        }
    }
}
