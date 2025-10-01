package com.guruyuknow.hisabbook.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.R

class AddGroupExpenseBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()
    private var groupId: Long = 0

    private lateinit var amountInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var addExpenseButton: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator

    // keep members cached so we can show a picker
    private var members: List<GroupMember> = emptyList()

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(groupId: Long): AddGroupExpenseBottomSheet {
            return AddGroupExpenseBottomSheet().apply {
                arguments = Bundle().apply { putLong(ARG_GROUP_ID, groupId) }
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
    ): View = inflater.inflate(R.layout.bottom_sheet_add_group_expense, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupClickListeners()
        observeViewModel()

        // ensure we have members to pick a payer from
        viewModel.loadGroupDetails(groupId)
    }

    private fun initViews(view: View) {
        amountInput = view.findViewById(R.id.etAmount)
        descriptionInput = view.findViewById(R.id.etDescription)
        addExpenseButton = view.findViewById(R.id.btnAddExpense)
        progressIndicator = view.findViewById(R.id.progressIndicator)
    }

    private fun setupClickListeners() {
        addExpenseButton.setOnClickListener { addExpenseFlow() }
    }

    private fun addExpenseFlow() {
        val amountText = amountInput.text?.toString()?.trim().orEmpty()
        val description = descriptionInput.text?.toString()?.trim().orEmpty()

        if (amountText.isEmpty()) { amountInput.error = "Enter amount"; return }
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) { amountInput.error = "Enter valid amount"; return }
        if (description.isEmpty()) { descriptionInput.error = "Enter description"; return }

        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "Members not loaded yet. Please wait…", Toast.LENGTH_SHORT).show()
            return
        }

        // Show a quick payer picker (no layout change required)
        val names = members.map { it.userName }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select who paid")
            .setItems(names) { dialog, which ->
                val payer = members[which]
                viewModel.addExpenseByMember(
                    groupId = groupId,
                    amount = amount,
                    description = description,
                    payer = payer
                )
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val loading = isLoading == true
            progressIndicator.visibility = if (loading) View.VISIBLE else View.GONE
            addExpenseButton.isEnabled = !loading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.expenseAdded.observe(viewLifecycleOwner) { added ->
            if (added == true) {
                Toast.makeText(requireContext(), "Expense added successfully!", Toast.LENGTH_SHORT).show()
                viewModel.markExpenseHandled()
                dismiss()
            }
        }

        // cache members to power the payer picker
        viewModel.groupMembers.observe(viewLifecycleOwner) { list ->
            members = list ?: emptyList()
        }
    }
}
