package com.guruyuknow.hisabbook.group

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.guruyuknow.hisabbook.ExpenseAdapter
import com.guruyuknow.hisabbook.R

class GroupDetailFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()
    private var groupId: Long = 0

    private lateinit var groupNameText: TextView
    private lateinit var budgetText: TextView
    private lateinit var totalExpensesText: TextView
    private lateinit var perPersonText: TextView
    private lateinit var memberCountText: TextView

    private lateinit var expensesRecyclerView: RecyclerView
    private lateinit var balancesRecyclerView: RecyclerView
    private lateinit var settlementsRecyclerView: RecyclerView
    private lateinit var tvNoSettlements: TextView

    private lateinit var fabAddExpense: FloatingActionButton
    private lateinit var btnViewSettlements: MaterialButton
    private lateinit var progressIndicator: LinearProgressIndicator

    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var balanceAdapter: BalanceAdapter
    private lateinit var settlementAdapter: SettlementAdapter

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(groupId: Long): GroupDetailFragment {
            return GroupDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_GROUP_ID, groupId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(ARG_GROUP_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_group_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()

        // Load group details
        viewModel.loadGroupDetails(groupId)
    }

    private fun initViews(view: View) {
        groupNameText = view.findViewById(R.id.tvGroupName)
        budgetText = view.findViewById(R.id.tvBudget)
        totalExpensesText = view.findViewById(R.id.tvTotalExpenses)
        perPersonText = view.findViewById(R.id.tvPerPerson)
        memberCountText = view.findViewById(R.id.tvMemberCount)

        expensesRecyclerView = view.findViewById(R.id.rvExpenses)
        balancesRecyclerView = view.findViewById(R.id.rvBalances)
        settlementsRecyclerView = view.findViewById(R.id.rvSettlements)
        tvNoSettlements = view.findViewById(R.id.tvNoSettlements)

        fabAddExpense = view.findViewById(R.id.fabAddExpense)
        btnViewSettlements = view.findViewById(R.id.btnViewSettlements)
        progressIndicator = view.findViewById(R.id.progressIndicator)
    }

    private fun setupRecyclerViews() {
        // Expenses RecyclerView
        expenseAdapter = ExpenseAdapter()
        expensesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        expensesRecyclerView.adapter = expenseAdapter

        // Balances RecyclerView
        balanceAdapter = BalanceAdapter()
        balancesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        balancesRecyclerView.adapter = balanceAdapter

        // Settlements RecyclerView
        settlementAdapter = SettlementAdapter()
        settlementsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        settlementsRecyclerView.adapter = settlementAdapter
    }

    private fun setupClickListeners() {
        fabAddExpense.setOnClickListener {
            AddGroupExpenseBottomSheet.Companion.newInstance(groupId)
                .show(parentFragmentManager, "add_group_expense")
        }

        btnViewSettlements.setOnClickListener {
            Log.d("GroupDetailFragment", "Calculate Settlements clicked for groupId=$groupId")
            Toast.makeText(requireContext(), "Calculating settlements…", Toast.LENGTH_SHORT).show()
            viewModel.calculateSettlements(groupId)
        }
    }

    private fun observeViewModel() {
        // Loading
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressIndicator.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }

        // Current group
        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            group?.let {
                groupNameText.text = it.name
                budgetText.text = it.budget?.let { b -> "₹${String.format("%.2f", b)}" } ?: "No budget set"
            }
        }

        // Statistics
        viewModel.groupStats.observe(viewLifecycleOwner) { stats ->
            totalExpensesText.text = "Total: ₹${String.format("%.2f", stats.totalExpenses)}"
            perPersonText.text = "Per Person: ₹${String.format("%.2f", stats.perPersonShare)}"
            memberCountText.text = "${stats.memberCount} members"

            stats.remainingBudget?.let { remaining ->
                val budgetStatus = if (remaining >= 0) {
                    "Remaining: ₹${String.format("%.2f", remaining)}"
                } else {
                    "Over budget: ₹${String.format("%.2f", -remaining)}"
                }
                budgetText.text = "${budgetText.text} ($budgetStatus)"
            }
        }

        // Expenses
        viewModel.groupExpenses.observe(viewLifecycleOwner) { expenses ->
            expenseAdapter.submitList(expenses ?: emptyList())
        }

        // Balances
        viewModel.groupBalances.observe(viewLifecycleOwner) { balances ->
            balanceAdapter.submitList(balances ?: emptyList())
        }

        // Settlements
        viewModel.settlements.observe(viewLifecycleOwner) { settlements ->
            val list = settlements ?: emptyList()
            Log.d("GroupDetailFragment", "Settlements received: ${list.size}")
            settlementAdapter.submitList(list)

            if (list.isEmpty()) {
                tvNoSettlements.visibility = View.VISIBLE
                settlementsRecyclerView.visibility = View.GONE
            } else {
                tvNoSettlements.visibility = View.GONE
                settlementsRecyclerView.visibility = View.VISIBLE
            }
        }
    }
}
