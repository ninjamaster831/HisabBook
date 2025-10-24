package com.guruyuknow.hisabbook.group

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.R

class CreateGroupBottomSheet : BottomSheetDialogFragment() {

    // ✅ Activity-scoped ViewModel so dismiss() doesn't cancel jobs
    private val viewModel: GroupExpenseViewModel by activityViewModels()

    private lateinit var memberAdapter: MemberAdapter
    private val members = mutableListOf<MemberInput>()

    private lateinit var groupNameInput: TextInputEditText
    private lateinit var budgetInput: TextInputEditText
    private lateinit var memberNameInput: TextInputEditText
    private lateinit var memberPhoneInput: TextInputEditText
    private lateinit var addMemberButton: MaterialButton
    private lateinit var pickFromContactsButton: MaterialButton
    private lateinit var createGroupButton: MaterialButton
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var cvMembersList: MaterialCardView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var tvMemberCount: TextView

    private val TAG = "CreateGroupBottomSheet"

    // ---- Permissions + Contact picker launchers ----
    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openContactsPicker()
            else Toast.makeText(requireContext(), "Contacts permission is required", Toast.LENGTH_SHORT).show()
        }

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                fillMemberFromContact(uri)
            }
        }

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
        updateMemberUI()
    }

    private fun initViews(view: View) {
        groupNameInput = view.findViewById(R.id.etGroupName)
        budgetInput = view.findViewById(R.id.etBudget)
        memberNameInput = view.findViewById(R.id.etMemberName)
        memberPhoneInput = view.findViewById(R.id.etMemberPhone)
        addMemberButton = view.findViewById(R.id.btnAddMember)
        pickFromContactsButton = view.findViewById(R.id.btnPickFromContacts)
        createGroupButton = view.findViewById(R.id.btnCreateGroup)
        membersRecyclerView = view.findViewById(R.id.rvMembers)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        cvMembersList = view.findViewById(R.id.cvMembersList)
        llEmptyState = view.findViewById(R.id.llEmptyState)
        tvMemberCount = view.findViewById(R.id.tvMemberCount)
    }

    private fun setupRecyclerView() {
        memberAdapter = MemberAdapter(members) { position ->
            members.removeAt(position)
            memberAdapter.notifyItemRemoved(position)
            updateMemberUI()
        }
        membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        membersRecyclerView.adapter = memberAdapter
    }

    private fun setupClickListeners() {
        // Add member button
        addMemberButton.setOnClickListener {
            val name = memberNameInput.text?.toString()?.trim().orEmpty()
            val phone = cleanPhone(memberPhoneInput.text?.toString())

            if (name.isEmpty()) {
                memberNameInput.error = "Enter member name"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                memberPhoneInput.error = "Enter phone number"
                return@setOnClickListener
            }
            // Avoid duplicates by "userId" (phone as key)
            if (members.any { it.userId == phone }) {
                memberPhoneInput.error = "Member already added"
                return@setOnClickListener
            }

            members.add(MemberInput(phone, name))
            memberAdapter.notifyItemInserted(members.size - 1)
            updateMemberUI()

            memberNameInput.text = null
            memberPhoneInput.text = null
            memberNameInput.requestFocus()
        }

        // Pick from Contacts button
        pickFromContactsButton.setOnClickListener {
            // On Android, READ_CONTACTS is still required for picker data
            requestContactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
        }

        // Create group button
        createGroupButton.setOnClickListener { createGroup() }
    }

    private fun updateMemberUI() {
        val memberCount = members.size

        if (memberCount > 0) {
            cvMembersList.visibility = View.VISIBLE
            llEmptyState.visibility = View.GONE
            tvMemberCount.text = "$memberCount ${if (memberCount == 1) "member" else "members"}"
        } else {
            cvMembersList.visibility = View.GONE
            llEmptyState.visibility = View.VISIBLE
        }
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
            pickFromContactsButton.isEnabled = !isLoading
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
            code?.let { Toast.makeText(requireContext(), "Share this code: $it", Toast.LENGTH_LONG).show() }
        }

        viewModel.groupCreationResult.observe(viewLifecycleOwner) { result ->
            Log.d(TAG, "groupCreationResult observed: $result")
            if (result == null) return@observe

            if (result.isSuccess) {
                Log.d(TAG, "Group created successfully, dismissing sheet")
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

    // ---- Contacts helpers ----

    private fun openContactsPicker() {
        try {
            // Pick directly from phone numbers list so we always get a number
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            pickContactLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch contacts picker", e)
            Toast.makeText(requireContext(), "Unable to open contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fillMemberFromContact(contactUri: Uri) {
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx  = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val name = c.getString(nameIdx)?.trim().orEmpty()
                    val raw  = c.getString(numIdx)?.trim().orEmpty()
                    val phone = cleanPhone(raw)

                    // Prefill fields; user can hit "Add Member"
                    memberNameInput.text = name.toEditable()
                    memberPhoneInput.text = phone.toEditable()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading selected contact", e)
            Toast.makeText(requireContext(), "Could not read contact", Toast.LENGTH_SHORT).show()
        }
    }

    private fun String?.toEditable(): Editable? = this?.let { Editable.Factory.getInstance().newEditable(it) }

    private fun cleanPhone(input: String?): String {
        if (input.isNullOrBlank()) return ""
        // Remove spaces, dashes, parentheses, and leading +91 if present
        var p = input.replace("[\\s\\-()]+".toRegex(), "")
        if (p.startsWith("+91")) p = p.removePrefix("+91")
        if (p.startsWith("0") && p.length > 10) p = p.removePrefix("0")
        return p
    }
}

// Updated Adapter for the new member item layout
class MemberAdapter(
    private val members: List<MemberInput>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<MemberAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
        val tvMemberPhone: TextView = view.findViewById(R.id.tvMemberPhone)
        val btnRemoveMember: View = view.findViewById(R.id.btnRemoveMember)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]

        // Set avatar with first letter of name
        val initial = member.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvAvatar.text = initial

        // Set member details
        holder.tvMemberName.text = member.userName
        holder.tvMemberPhone.text = "+91 ${member.userId}"

        // Remove button click
        holder.btnRemoveMember.setOnClickListener {
            onRemove(holder.adapterPosition)
        }
    }

    override fun getItemCount() = members.size
}