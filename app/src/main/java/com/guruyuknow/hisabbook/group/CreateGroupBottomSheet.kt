package com.guruyuknow.hisabbook.group

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.guruyuknow.hisabbook.R

class CreateGroupBottomSheet : BottomSheetDialogFragment() {

    // ✅ Activity-scoped ViewModel so dismiss() doesn't cancel jobs
    private val viewModel: GroupExpenseViewModel by activityViewModels()

    private lateinit var memberAdapter: AddMemberAdapter
    private val members = mutableListOf<MemberInput>()

    private lateinit var groupNameInput: TextInputEditText
    private lateinit var budgetInput: TextInputEditText
    private lateinit var memberNameInput: TextInputEditText
    private lateinit var memberPhoneInput: TextInputEditText
    private lateinit var addMemberButton: MaterialButton
    private lateinit var createGroupButton: MaterialButton
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var progressIndicator: CircularProgressIndicator
    private val TAG = "CreateGroupBottomSheet"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_create_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        groupNameInput = view.findViewById(R.id.etGroupName)
        budgetInput = view.findViewById(R.id.etBudget)
        memberNameInput = view.findViewById(R.id.etMemberName)
        memberPhoneInput = view.findViewById(R.id.etMemberPhone)
        addMemberButton = view.findViewById(R.id.btnAddMember)
        createGroupButton = view.findViewById(R.id.btnCreateGroup)
        membersRecyclerView = view.findViewById(R.id.rvMembers)
        progressIndicator = view.findViewById(R.id.progressIndicator)
    }

    private fun setupRecyclerView() {
        memberAdapter = AddMemberAdapter(members) { position ->
            members.removeAt(position)
            memberAdapter.notifyItemRemoved(position)
        }
        membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        membersRecyclerView.adapter = memberAdapter
    }

    private fun setupClickListeners() {
        // Add member button
        addMemberButton.setOnClickListener {
            val name = memberNameInput.text?.toString()?.trim().orEmpty()
            val phone = memberPhoneInput.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                memberNameInput.error = "Enter member name"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                memberPhoneInput.error = "Enter phone number"
                return@setOnClickListener
            }
            // Avoid duplicates by "userId" (you’re using phone as key here)
            if (members.any { it.userId == phone }) {
                memberPhoneInput.error = "Member already added"
                return@setOnClickListener
            }

            members.add(MemberInput(phone, name))
            memberAdapter.notifyItemInserted(members.size - 1)

            memberNameInput.text?.clear()
            memberPhoneInput.text?.clear()
            memberNameInput.requestFocus()
        }

        // Create group button
        createGroupButton.setOnClickListener { createGroup() }
    }

    private fun createGroup() {
        val groupName = groupNameInput.text?.toString()?.trim().orEmpty()
        val budgetText = budgetInput.text?.toString()?.trim().orEmpty()

        Log.d(TAG, "CreateGroup clicked: groupName=$groupName budget=$budgetText members=${members.size}")

        if (groupName.isEmpty()) {
            groupNameInput.error = "Enter group name"
            return
        }
        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least one member", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Validation failed: no members")
            return
        }

        val budget = if (budgetText.isNotEmpty()) budgetText.toDoubleOrNull() else null
        if (budgetText.isNotEmpty() && budget == null) {
            budgetInput.error = "Invalid budget amount"
            Log.w(TAG, "Validation failed: invalid budget")
            return
        }

        Log.d(TAG, "Calling viewModel.createGroup(...) with $groupName and ${members.size} members")

        viewModel.createGroup(
            name = groupName,
            budget = budget,
            members = members
        )
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "isLoading=$isLoading")
            progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            createGroupButton.isEnabled = !isLoading
            addMemberButton.isEnabled = !isLoading
        }

        viewModel.joinResult.observe(viewLifecycleOwner) { res ->
            if (res == null) return@observe
            if (res.isSuccess) {
                Toast.makeText(requireContext(), "Joined group successfully!", Toast.LENGTH_SHORT).show()
                viewModel.clearJoinResult()
                dismiss()
            } else {
                Log.e(TAG, "Join failed: ${res.exceptionOrNull()?.message}")
            }
        }

        viewModel.lastCreatedGroupCode.observe(viewLifecycleOwner) { code ->
            code?.let {
                Toast.makeText(requireContext(), "Share this code: $it", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.groupCreationResult.observe(viewLifecycleOwner) { result ->
            Log.d(TAG, "groupCreationResult observed: $result")
            if (result == null) return@observe

            if (result.isSuccess) {
                Log.d(TAG, "Group created successfully, dismissing sheet")
                // ✅ Dismiss so ChatFragment (same VM) can show the GroupCodeDialog
                dismiss()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to create group"
                Log.e(TAG, "Group creation failed: $errorMsg")
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            Log.e(TAG, "errorMessage observed: $error")
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
}

// Adapter for adding members
class AddMemberAdapter(
    private val members: List<MemberInput>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<AddMemberAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chipMember: Chip = view.findViewById(R.id.chipMember)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member_chip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        holder.chipMember.text = "${member.userName} (${member.userId})"
        holder.chipMember.isCloseIconVisible = true
        holder.chipMember.setOnCloseIconClickListener {
            onRemove(holder.adapterPosition)
        }
    }

    override fun getItemCount() = members.size
}
