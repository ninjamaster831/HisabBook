package com.guruyuknow.hisabbook.group

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.guruyuknow.hisabbook.ExpenseAdapter
import com.guruyuknow.hisabbook.MainActivity
import com.guruyuknow.hisabbook.R
import kotlin.math.max

class GroupDetailFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()
    private var groupId: Long = 0

    // Views
    private var btnOpenChat: MaterialButton? = null
    private var btnViewSettlements: MaterialButton? = null
    private var btnAddMember: MaterialButton? = null
    private var fabAddExpense: ExtendedFloatingActionButton? = null

    private var groupNameText: TextView? = null
    private var budgetText: TextView? = null
    private var totalExpensesText: TextView? = null
    private var perPersonText: TextView? = null
    private var memberCountText: TextView? = null

    private var expensesRecyclerView: RecyclerView? = null
    private var balancesRecyclerView: RecyclerView? = null
    private var settlementsRecyclerView: RecyclerView? = null
    private var tvNoSettlements: TextView? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var chipMembers: ChipGroup? = null

    // Adapters
    private val expenseAdapter = ExpenseAdapter()
    private val balanceAdapter = BalanceAdapter()
    private val settlementAdapter = SettlementAdapter()

    // Dialog field references (so contact picker can fill them)
    private var currentNameField: TextInputEditText? = null
    private var currentPhoneField: TextInputEditText? = null

    // Cache hero paddings to avoid additive insets (fixes “big green box”)
    private var heroInitialPaddingTop = 0
    private var heroInitialPaddingBottom = 0

    // Contact picker
    private val pickPhoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            requireContext().contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val number = c.getString(0)?.trim().orEmpty()
                    val name = c.getString(1)?.trim().orEmpty()
                    val cleaned = cleanPhone(number) // keep only last 10 digits
                    currentNameField?.setText(name)
                    currentPhoneField?.setText(cleaned) // no +91 duplication; prefix shown by TextInputLayout
                }
            }
        }
    }

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        fun newInstance(groupId: Long) = GroupDetailFragment().apply {
            arguments = Bundle().apply { putLong(ARG_GROUP_ID, groupId) }
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
    ): View = inflater.inflate(R.layout.fragment_group_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()

        viewModel.loadGroupDetails(groupId)
        viewModel.fetchGroupMembers(groupId) // ensure members loaded at start

        (activity as? MainActivity)?.hideBottomNav()
        applyInsets(view)
    }

    override fun onResume() {
        super.onResume()
        // Defensive refresh when coming back
        viewModel.loadGroupDetails(groupId)
        viewModel.fetchGroupMembers(groupId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.showBottomNav()

        btnOpenChat = null; btnViewSettlements = null; btnAddMember = null; fabAddExpense = null
        groupNameText = null; budgetText = null; totalExpensesText = null
        perPersonText = null; memberCountText = null
        expensesRecyclerView = null; balancesRecyclerView = null
        settlementsRecyclerView = null; tvNoSettlements = null
        progressIndicator = null; chipMembers = null
        currentNameField = null; currentPhoneField = null
        heroInitialPaddingTop = 0; heroInitialPaddingBottom = 0
    }

    private fun initViews(view: View) {
        groupNameText       = view.findViewById(R.id.tvGroupName)
        budgetText          = view.findViewById(R.id.tvBudget)
        totalExpensesText   = view.findViewById(R.id.tvTotalExpenses)
        perPersonText       = view.findViewById(R.id.tvPerPerson)
        memberCountText     = view.findViewById(R.id.tvMemberCount)

        expensesRecyclerView    = view.findViewById(R.id.rvExpenses)
        balancesRecyclerView    = view.findViewById(R.id.rvBalances)
        settlementsRecyclerView = view.findViewById(R.id.rvSettlements)
        tvNoSettlements         = view.findViewById(R.id.tvNoSettlements)
        chipMembers             = view.findViewById(R.id.chipMembers)

        btnOpenChat        = view.findViewById(R.id.btnOpenChat)
        btnViewSettlements = view.findViewById(R.id.btnViewSettlements)
        btnAddMember       = view.findViewById(R.id.btnAddMember)
        fabAddExpense      = view.findViewById(R.id.fabAddExpense)
        progressIndicator  = view.findViewById(R.id.progressIndicator)
    }

    private fun setupRecyclerViews() {
        balancesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = balanceAdapter
        }
        expensesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
        }
        settlementsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = settlementAdapter
        }
    }

    private fun setupClickListeners() {
        fabAddExpense?.setOnClickListener {
            AddGroupExpenseBottomSheet.newInstance(groupId)
                .show(parentFragmentManager, "add_group_expense")
        }
        btnViewSettlements?.setOnClickListener {
            Toast.makeText(requireContext(), "Calculating settlements…", Toast.LENGTH_SHORT).show()
            viewModel.calculateSettlements(groupId)
        }
        btnOpenChat?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GroupChatFragment.newInstance(groupId))
                .addToBackStack("group_chat")
                .commit()
        }
        btnAddMember?.setOnClickListener {
            showAddMemberDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressIndicator?.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }

        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            group?.let {
                groupNameText?.text = it.name
                budgetText?.text = it.budget?.let { b -> "₹${String.format("%.2f", b)}" } ?: "No budget"
            }
        }

        viewModel.groupStats.observe(viewLifecycleOwner) { stats ->
            totalExpensesText?.text = "Total: ₹${String.format("%.2f", stats.totalExpenses)}"
            perPersonText?.text = "₹${String.format("%.2f", stats.perPersonShare)}"
            memberCountText?.text = "${stats.memberCount} members"

            stats.remainingBudget?.let { remaining ->
                val base = budgetText?.text?.toString()?.substringBefore(" (") ?: budgetText?.text ?: ""
                val status = if (remaining >= 0)
                    "Remaining: ₹${String.format("%.2f", remaining)}"
                else
                    "Over budget: ₹${String.format("%.2f", -remaining)}"
                budgetText?.text = "$base ($status)"
            }
        }

        viewModel.groupExpenses.observe(viewLifecycleOwner) { expenses ->
            expenseAdapter.submitList(expenses ?: emptyList())
        }
        viewModel.groupBalances.observe(viewLifecycleOwner) { balances ->
            balanceAdapter.submitList(balances ?: emptyList())
        }
        viewModel.settlements.observe(viewLifecycleOwner) { settlements ->
            val list = settlements ?: emptyList()
            settlementAdapter.submitList(list)
            if (list.isEmpty()) {
                tvNoSettlements?.visibility = View.VISIBLE
                settlementsRecyclerView?.visibility = View.GONE
            } else {
                tvNoSettlements?.visibility = View.GONE
                settlementsRecyclerView?.visibility = View.VISIBLE
            }
        }

        // Show all member names as chips and auto-refresh after actions
        viewModel.groupMembers.observe(viewLifecycleOwner) { members ->
            val list = members ?: emptyList()
            renderMemberChips(list) // <-- pass the whole member list
            memberCountText?.text = "${list.size} members"
        }



        // Feedback for add-member action
        viewModel.addMemberResult.observe(viewLifecycleOwner) { res ->
            res ?: return@observe
            if (res.isSuccess) {
                Toast.makeText(requireContext(), "Member added", Toast.LENGTH_SHORT).show()
                // Refresh related data
                viewModel.loadGroupDetails(groupId)
                viewModel.fetchGroupMembers(groupId)
                viewModel.calculateSettlements(groupId) // optional
            } else {
                Toast.makeText(
                    requireContext(),
                    res.exceptionOrNull()?.message ?: "Failed to add member",
                    Toast.LENGTH_LONG
                ).show()
            }
            viewModel.clearAddMemberResult()
        }
    }

    /** Nice insets: push header below status bar & lift FAB above nav (no additive growth) */
    private fun applyInsets(root: View) {
        val coordinator: View = root.findViewById(R.id.rootCoordinator)
        val hero: View? = root.findViewById(R.id.heroSection)

        ViewCompat.setOnApplyWindowInsetsListener(coordinator) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            hero?.let { h ->
                if (heroInitialPaddingTop == 0 && heroInitialPaddingBottom == 0) {
                    heroInitialPaddingTop = h.paddingTop
                    heroInitialPaddingBottom = h.paddingBottom
                }
                h.setPadding(
                    h.paddingLeft,
                    heroInitialPaddingTop + sysBars.top,
                    h.paddingRight,
                    heroInitialPaddingBottom
                )
            }

            (fabAddExpense?.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                val baseBottom = dp(20)
                val baseEnd = dp(20)
                lp.bottomMargin = max(baseBottom, baseBottom + sysBars.bottom)
                lp.marginEnd = max(baseEnd, baseEnd + sysBars.right)
                fabAddExpense?.layoutParams = lp
            }

            insets
        }
        ViewCompat.requestApplyInsets(coordinator)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- DIALOG ----------

    private fun showAddMemberDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_member, null)

        val tilName  = view.findViewById<TextInputLayout>(R.id.tilName)
        val tilPhone = view.findViewById<TextInputLayout>(R.id.tilPhone)
        val etName   = view.findViewById<TextInputEditText>(R.id.etName)
        val etPhone  = view.findViewById<TextInputEditText>(R.id.etPhone)
        val btnPick  = view.findViewById<MaterialButton>(R.id.btnPickContact)
        val btnCancel= view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnAdd   = view.findViewById<MaterialButton>(R.id.btnAdd)

        // Hold refs so picker can fill fields
        currentNameField = etName
        currentPhoneField = etPhone

        // Digits‐only and 10 max (prefix "+91 " is only visual in TextInputLayout)
        etPhone.keyListener = DigitsKeyListener.getInstance("0123456789")
        etPhone.filters = arrayOf(InputFilter.LengthFilter(10))

        // Create dialog with transparent background for rounded corners
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        // Auto-focus name field and show keyboard
        etName.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        // Clear errors on text change
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilName.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etPhone.addTextChangedListener(object : TextWatcher {
            private var selfEdit = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (selfEdit) return
                val cleaned = s?.filter { it.isDigit() }?.take(10)?.toString().orEmpty()
                if (cleaned != s.toString()) {
                    selfEdit = true
                    etPhone.setText(cleaned)
                    etPhone.setSelection(cleaned.length)
                    selfEdit = false
                }
                tilPhone.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Pick from contacts button
        btnPick.setOnClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                )
                pickPhoneLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open contacts", Toast.LENGTH_SHORT).show()
            }
        }

        // Cancel button
        btnCancel.setOnClickListener { dialog.dismiss() }

        // Add member button
        btnAdd.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            val rawPhone = etPhone.text?.toString()?.trim().orEmpty()
            val phone = cleanPhone(rawPhone)

            var isValid = true
            tilName.error = null
            tilPhone.error = null

            // Validate name
            if (name.isEmpty()) {
                tilName.error = "Please enter member name"
                etName.requestFocus()
                isValid = false
            } else if (name.length < 2) {
                tilName.error = "Name is too short"
                etName.requestFocus()
                isValid = false
            }

            // Validate phone (exactly 10 digits)
            if (phone.isEmpty()) {
                tilPhone.error = "Please enter phone number"
                if (isValid) etPhone.requestFocus()
                isValid = false
            } else if (phone.length != 10 || !phone.all { it.isDigit() }) {
                tilPhone.error = "Please enter a valid 10-digit number"
                if (isValid) etPhone.requestFocus()
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            // Disable buttons to prevent double-click
            btnAdd.isEnabled = false
            btnCancel.isEnabled = false
            btnPick.isEnabled = false

            // Add member (userId = phone, userName = name)
            viewModel.addMemberToGroup(groupId, MemberInput(userId = phone, userName = name))

            Toast.makeText(requireContext(), "Adding $name to group...", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // Helpers

    private fun cleanPhone(input: String): String {
        if (input.isBlank()) return ""
        // Strip everything but digits, keep last 10 digits — handles +91/0/91 prefixes & spaces/dashes
        val digits = input.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    private fun renderMemberChips(members: List<GroupMember>) {
        val group = chipMembers ?: return
        group.removeAllViews()

        members.forEach { member ->
            val chip = Chip(requireContext()).apply {
                text = member.userName
                isClickable = true              // <-- clickable now
                isCheckable = false

                // style without style resources (avoids R.style issues)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                chipStrokeWidth = dp(1).toFloat()
                setChipStrokeColorResource(android.R.color.black)
                chipStartPadding = dp(8).toFloat()
                chipEndPadding = dp(8).toFloat()
                textStartPadding = dp(6).toFloat()
                textEndPadding = dp(6).toFloat()
                setPadding(dp(2), 0, dp(2), 0)

                setOnClickListener {
                    // Open member detail bottom sheet
                    MemberDetailBottomSheet.newInstance(groupId, member.userId)
                        .show(parentFragmentManager, "member_detail")
                }
            }
            group.addView(chip)
        }

    }

}
