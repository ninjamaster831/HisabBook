package com.guruyuknow.hisabbook

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.databinding.FragmentHomeBinding
import com.guruyuknow.hisabbook.group.Group
import com.guruyuknow.hisabbook.group.GroupExpenseViewModel
import com.guruyuknow.hisabbook.group.GroupDetailFragment
import com.guruyuknow.hisabbook.group.CreateGroupBottomSheet
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val TAG_HOME = "HomeFragment"

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var recentTransactionsAdapter: RecentTransactionsAdapter
    private val homeDatabase = HomeDatabase()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private lateinit var eventsAdapter: EventsAdapter

    private val groupsVm: GroupExpenseViewModel by viewModels()
    private lateinit var groupsAdapter: GroupsCarouselAdapter

    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDayFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    @Serializable
    private data class CashbookEntryInsert(
        @SerialName("user_id") val userId: String,
        val amount: Double,
        val type: String, // "IN" or "OUT"
        @SerialName("payment_method") val paymentMethod: String, // "CASH"/"ONLINE"
        val description: String? = null,
        val category: String? = null,
        val date: String // "yyyy-MM-dd"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG_HOME, "onCreateView()")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG_HOME, "onViewCreated()")
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.showLoading()
        setupUI()
        loadDashboardData()
        loadUserEvents()
        setupGroupsSectionIfPresent()
        (activity as? MainActivity)?.hideLoading()

        // Listen when an event gets created elsewhere
        parentFragmentManager.setFragmentResultListener("event_created", viewLifecycleOwner) { _, bundle ->
            val eventId = bundle.getString("event_id") ?: return@setFragmentResultListener
            Log.d(TAG_HOME, "Fragment result: event_created id=$eventId")
            loadUserEvents()
            EventDetailBottomSheet.newInstance(eventId)
                .show(parentFragmentManager, "event_detail")
        }
        parentFragmentManager.setFragmentResultListener("transaction_added", viewLifecycleOwner) { _, _ ->
            Log.d(TAG_HOME, "transaction_added -> refreshing dashboard")
            viewLifecycleOwner.lifecycleScope.launch {
                (activity as? MainActivity)?.showLoading()
                delay(250)
                loadDashboardData()
                (activity as? MainActivity)?.hideLoading()
            }
        }

        // Listen when a group gets created from CreateGroupBottomSheet
        parentFragmentManager.setFragmentResultListener("group_created", viewLifecycleOwner) { _, _ ->
            Log.d(TAG_HOME, "Fragment result: group_created -> refreshing groups")
            groupsVm.loadAllGroups()
        }
    }

    private fun setupGroupsSectionIfPresent() {
        val recycler = runCatching { binding.groupsRecycler }.getOrNull() ?: return
        Log.d(TAG_HOME, "setupGroupsSectionIfPresent(): initializing groups carousel")

        groupsAdapter = GroupsCarouselAdapter { group ->
            val id = group.id ?: return@GroupsCarouselAdapter
            Log.d(TAG_HOME, "Group clicked id=$id name=${group.name}")
            try {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right
                    )
                    .replace(
                        R.id.fragmentContainer,
                        GroupDetailFragment.newInstance(id)
                    )
                    .addToBackStack("group_details_$id")
                    .commit()
            } catch (e: Exception) {
                Log.e(TAG_HOME, "Navigation to group failed", e)
            }
        }

        recycler.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = groupsAdapter
            setHasFixedSize(true)
        }

        // Wire up "Create" -> open CreateGroupBottomSheet
        runCatching { binding.createGroupBtn }.getOrNull()?.setOnClickListener {
            Log.d(TAG_HOME, "CreateGroup clicked -> opening bottom sheet")
            CreateGroupBottomSheet().show(parentFragmentManager, "create_group")
        }

        // Wire up "View All" -> navigate to GroupListFragment (replace with your all-groups screen if named differently)
        runCatching { binding.viewAllGroupsBtn }.getOrNull()?.setOnClickListener {
            Log.d(TAG_HOME, "ViewAllGroups clicked -> navigating to GroupListFragment")
            try {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right
                    )
                    .replace(R.id.fragmentContainer, ChatFragment())
                    .addToBackStack("group_list")
                    .commit()
            } catch (e: Exception) {
                Log.e(TAG_HOME, "Navigation to all groups failed", e)
                Toast.makeText(requireContext(), "Unable to open groups", Toast.LENGTH_SHORT).show()
            }
        }

        groupsVm.filteredGroups.observe(viewLifecycleOwner) { list ->
            Log.d(TAG_HOME, "Groups observe: size=${list?.size ?: 0}")
            groupsAdapter.submit(list)
            val isEmpty = list.isNullOrEmpty()
            runCatching { binding.groupsEmptyState }.getOrNull()?.visibility =
                if (isEmpty) View.VISIBLE else View.GONE
            recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }

        // default filter + initial load
        groupsVm.filterGroups(searchQuery = "", filter = "all")
        groupsVm.loadAllGroups()
    }

    private fun loadUserEvents() {
        Log.d(TAG_HOME, "loadUserEvents() start")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = SupabaseManager.getCurrentUser()
                Log.d(TAG_HOME, "Current user for events: ${user?.id}")
                if (user?.id == null) {
                    showErrorMessage("Please login to see your events")
                    if (::eventsAdapter.isInitialized) {
                        eventsAdapter.submitList(emptyList())
                        toggleEventsEmptyState(true)
                    }
                    return@launch
                }
                val events = SupabaseManager.getUserEvents(user.id!!)
                Log.d(TAG_HOME, "Events fetched: count=${events.size}")
                if (::eventsAdapter.isInitialized) {
                    eventsAdapter.submitList(events)
                    toggleEventsEmptyState(events.isEmpty())
                }
            } catch (e: Exception) {
                Log.e(TAG_HOME, "Failed to load events", e)
                showErrorMessage("Failed to load events: ${e.message}")
                toggleEventsEmptyState(true)
            }
        }
    }

    private fun toggleEventsEmptyState(isEmpty: Boolean) {
        runCatching { binding.eventsEmptyState }.getOrNull()?.visibility =
            if (isEmpty) View.VISIBLE else View.GONE
        runCatching { binding.eventsRecycler }.getOrNull()?.visibility =
            if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun setupUI() {
        Log.d(TAG_HOME, "setupUI()")
        setupGreeting()

        recentTransactionsAdapter = RecentTransactionsAdapter { transaction: Transaction ->
            Log.d(TAG_HOME, "Recent transaction clicked: id=${transaction.id}")
            navigateToTransactionDetails(transaction)
        }
        binding.recentTransactionsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentTransactionsAdapter
        }

        eventsAdapter = EventsAdapter { event ->
            Log.d(TAG_HOME, "Event clicked: id=${event.id}")
            EventDetailBottomSheet.newInstance(event.id)
                .show(parentFragmentManager, "event_detail")
        }
        binding.eventsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventsAdapter
        }

        setupClickListeners()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        Log.d(TAG_HOME, "setupGreeting(): hour=$hour, greeting='$greeting'")
        _binding?.greetingText?.text = greeting
    }

    private fun setupClickListeners() {
        binding.apply {
            quickAddExpenseBtn.setOnClickListener { showAddCashbookEntryDialogFromHome("OUT", "Expense") }
            quickAddSaleBtn.setOnClickListener    { showAddCashbookEntryDialogFromHome("IN",  "Sale") }
            quickAddPurchaseBtn.setOnClickListener{ showAddCashbookEntryDialogFromHome("OUT", "Purchase") }

            viewTodayDetails.setOnClickListener {
                TodayDetailsBottomSheet.newInstance().show(parentFragmentManager, "today_details")
            }

            cashFlowCard.setOnClickListener { Log.d(TAG_HOME, "cashFlowCard clicked"); navigateToCashbook() }
            salesCard.setOnClickListener    { Log.d(TAG_HOME, "salesCard clicked"); navigateToSales() }
            purchasesCard.setOnClickListener{ Log.d(TAG_HOME, "purchasesCard clicked"); navigateToPurchases() }
            staffCard.setOnClickListener    { Log.d(TAG_HOME, "staffCard clicked"); navigateToStaffManagement() }
            loansCard.setOnClickListener    { Log.d(TAG_HOME, "loansCard clicked"); navigateToLoans() }
            viewAllTransactionsBtn.setOnClickListener { Log.d(TAG_HOME, "ViewAllTransactions clicked"); navigateToAllTransactions() }
            voiceInputBtn.setOnClickListener { openVoiceInputBottomSheet() }
        }
    }

    private fun showAddCashbookEntryDialogFromHome(
        type: String,
        prefillCategory: String? = null
    ) {
        Log.d(TAG_HOME, "showAddCashbookEntryDialogFromHome(type=$type, prefill=$prefillCategory)")
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_entry, null)

        val amountInput      = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val categoryInput    = dialogView.findViewById<TextInputEditText>(R.id.categoryInput)
        val cashRadio        = dialogView.findViewById<MaterialButton>(R.id.cashRadio)
        val onlineRadio      = dialogView.findViewById<MaterialButton>(R.id.onlineRadio)
        val dateButton       = dialogView.findViewById<MaterialButton>(R.id.dateButton)

        prefillCategory?.let { categoryInput.setText(it) }

        var selectedPaymentMethod = "CASH"
        var selectedDate = isoDateFormatter.format(Date())
        dateButton.text = displayDayFormatter.format(Date())
        Log.d(TAG_HOME, "dialog defaults: method=$selectedPaymentMethod date=$selectedDate")

        cashRadio.setOnClickListener {
            selectedPaymentMethod = "CASH"
            updatePaymentMethodButtonsFromHome(cashRadio, onlineRadio, true)
            Log.d(TAG_HOME, "payment method changed -> $selectedPaymentMethod")
        }
        onlineRadio.setOnClickListener {
            selectedPaymentMethod = "ONLINE"
            updatePaymentMethodButtonsFromHome(cashRadio, onlineRadio, false)
            Log.d(TAG_HOME, "payment method changed -> $selectedPaymentMethod")
        }

        dateButton.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                ctx,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDate = isoDateFormatter.format(cal.time)
                    dateButton.text = displayDayFormatter.format(cal.time)
                    Log.d(TAG_HOME, "date picked: iso=$selectedDate display=${dateButton.text}")
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(if (type == "IN") "Add Income" else "Add Expense")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = amountInput.text?.toString()?.toDoubleOrNull()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                val category = categoryInput.text?.toString()?.trim().orEmpty()

                Log.d(TAG_HOME, "dialog save clicked: amount=$amount, type=$type, pm=$selectedPaymentMethod, date=$selectedDate, cat='$category'")

                if (amount == null || amount <= 0) {
                    Toast.makeText(ctx, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    Log.w(TAG_HOME, "invalid amount")
                    return@setOnClickListener
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val user = SupabaseManager.getCurrentUser()
                        val userId = user?.id
                        Log.d(TAG_HOME, "insert entry userId=$userId")
                        if (userId.isNullOrEmpty()) {
                            Toast.makeText(ctx, "Please login again", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val body = CashbookEntryInsert(
                            userId = userId,
                            amount = amount,
                            type = type,
                            paymentMethod = selectedPaymentMethod,
                            description = description.ifEmpty { null },
                            category = category.ifEmpty { null },
                            date = selectedDate
                        )

                        Log.d(TAG_HOME, "inserting into cashbook_entries: $body")
                        SupabaseManager.client
                            .from("cashbook_entries")
                            .insert(body) { select() }

                        Toast.makeText(ctx, "Entry added successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        (activity as? MainActivity)?.showLoading()
                        viewLifecycleOwner.lifecycleScope.launch {
                            delay(300)
                            loadDashboardData()
                            (activity as? MainActivity)?.hideLoading()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_HOME, "Error adding entry", e)
                        Toast.makeText(ctx, "Error adding entry: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }
    private fun openVoiceInputBottomSheet() {
        try {
            VoiceInputBottomSheet().show(parentFragmentManager, "voice_input")
        } catch (e: Exception) {
            Log.e(TAG_HOME, "Failed to open VoiceInputBottomSheet", e)
            Toast.makeText(requireContext(), "Unable to open voice input", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePaymentMethodButtonsFromHome(
        cashRadio: MaterialButton,
        onlineRadio: MaterialButton,
        isCashSelected: Boolean
    ) {
        Log.d(TAG_HOME, "updatePaymentMethodButtonsFromHome(isCash=$isCashSelected)")
        if (isCashSelected) {
            cashRadio.setBackgroundColor(requireContext().getColor(R.color.colorPrimary))
            cashRadio.setTextColor(requireContext().getColor(android.R.color.white))
            onlineRadio.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            onlineRadio.setTextColor(requireContext().getColor(R.color.colorPrimary))
        } else {
            onlineRadio.setBackgroundColor(requireContext().getColor(R.color.colorPrimary))
            onlineRadio.setTextColor(requireContext().getColor(android.R.color.white))
            cashRadio.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            cashRadio.setTextColor(requireContext().getColor(R.color.colorPrimary))
        }
    }

    private fun loadDashboardData() {
        Log.d(TAG_HOME, "loadDashboardData() start")
        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: run {
                Log.w(TAG_HOME, "loadDashboardData(): binding null, abort")
                return@launch
            }
            try {
                b.progressBar.visibility = View.VISIBLE
                val currentUser = try {
                    SupabaseManager.getCurrentUser()
                } catch (_: CancellationException) {
                    Log.w(TAG_HOME, "loadDashboardData(): cancelled getting user")
                    return@launch
                }
                Log.d(TAG_HOME, "currentUser id=${currentUser?.id}")

                if (currentUser?.id == null) {
                    showErrorMessage("User not authenticated")
                    Log.w(TAG_HOME, "User not authenticated")
                    return@launch
                }

                val result = homeDatabase.loadDashboardData(currentUser.id)
                Log.d(TAG_HOME, "homeDatabase.loadDashboardData() -> success=${result.isSuccess}")

                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (data == null) {
                        Log.w(TAG_HOME, "Dashboard data null")
                        showErrorMessage("No dashboard data available")
                    } else {
                        Log.d(
                            TAG_HOME,
                            "DashboardData: todayIncome=${data.todayIncome}, todayExpenses=${data.todayExpenses}, monthlyIncome=${data.monthlyIncome}, monthlyExpenses=${data.monthlyExpenses}, todaySales=${data.todaySales}, monthlySales=${data.monthlySales}, recentTx=${data.recentTransactions.size}"
                        )
                        updateUI(data)
                    }
                } else {
                    val err = result.exceptionOrNull()
                    Log.e(TAG_HOME, "Failed to load dashboard data", err)
                    showErrorMessage("Failed to load dashboard data: ${err?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG_HOME, "loadDashboardData() exception", e)
                showErrorMessage("Failed to load dashboard data: ${e.message}")
            } finally {
                _binding?.progressBar?.visibility = View.GONE
                Log.d(TAG_HOME, "loadDashboardData() end")
            }
        }
    }

    private fun updateUI(data: DashboardData) {
        Log.d(TAG_HOME, "updateUI(): START")
        val b = _binding ?: run {
            Log.w(TAG_HOME, "updateUI(): binding null")
            return
        }

        b.todayIncomeAmount.text = numberFormat.format(data.todayIncome)
        b.todayExpenseAmount.text = numberFormat.format(data.todayExpenses)
        val todayBalance = data.todayIncome - data.todayExpenses
        b.todayBalanceAmount.text = numberFormat.format(todayBalance)
        b.todayBalanceAmount.setTextColor(
            if (todayBalance >= 0)
                resources.getColor(R.color.success_green, null)
            else
                resources.getColor(R.color.error_red, null)
        )

        b.monthlyIncomeAmount.text = numberFormat.format(data.monthlyIncome)
        b.monthlyExpenseAmount.text = numberFormat.format(data.monthlyExpenses)
        val monthlyProfit = data.monthlyIncome - data.monthlyExpenses
        b.monthlyProfitAmount.text = numberFormat.format(monthlyProfit)
        b.monthlyProfitAmount.setTextColor(
            if (monthlyProfit >= 0)
                resources.getColor(R.color.success_green, null)
            else
                resources.getColor(R.color.error_red, null)
        )

        b.todaySalesAmount.text = numberFormat.format(data.todaySales)
        b.monthlySalesAmount.text = numberFormat.format(data.monthlySales)
        b.monthlyPurchasesAmount.text = numberFormat.format(data.monthlyPurchases)

        b.totalStaffCount.text = "${data.totalStaff} Active"
        b.monthlyStaffExpenseAmount.text = numberFormat.format(data.monthlyStaffExpenses)

        b.loansGivenAmount.text = numberFormat.format(data.totalLoansGiven)
        b.loansReceivedAmount.text = numberFormat.format(data.totalLoansReceived)
        b.pendingCollectionsAmount.text = numberFormat.format(data.pendingCollections)

        recentTransactionsAdapter.submitList(data.recentTransactions as List<Transaction?>?)
        val empty = data.recentTransactions.isEmpty()
        b.emptyStateLayout.visibility = if (empty) View.VISIBLE else View.GONE
        b.recentTransactionsRecycler.visibility = if (empty) View.GONE else View.VISIBLE

        Log.d(TAG_HOME, "updateUI(): END")
    }

    // ------- stubs -------
    private fun navigateToCashbook() {}
    private fun navigateToSales() {}
    private fun navigateToPurchases() {}
    private fun navigateToStaffManagement() {}
    private fun navigateToLoans() {}
    private fun navigateToAllTransactions() {
        try {
            val intent = android.content.Intent(requireContext(), CashbookReportActivity::class.java)
            startActivity(intent)
            requireActivity().overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            Log.d(TAG_HOME, "Navigated to CashbookReportActivity")
        } catch (e: Exception) {
            Log.e(TAG_HOME, "Failed to open CashbookReportActivity", e)
            Toast.makeText(requireContext(), "Unable to open transactions report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToTransactionDetails(transaction: Transaction) {}

    private fun showErrorMessage(message: String) {
        Log.w(TAG_HOME, "showErrorMessage(): $message")
        _binding?.root?.let { root ->
            Toast.makeText(root.context, message, Toast.LENGTH_LONG).show()
        } ?: run {
            if (isAdded) Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()
        }
    }
    override fun onResume() {
        super.onResume()
        Log.d(TAG_HOME, "onResume(): refreshing dashboard")

        // Show loading while refreshing
        (activity as? MainActivity)?.showLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            loadDashboardData()
            (activity as? MainActivity)?.hideLoading()
        }
    }

    override fun onDestroyView() {
        Log.d(TAG_HOME, "onDestroyView()")
        super.onDestroyView()
        _binding = null
    }

    data class DashboardData(
        val todayIncome: Double,
        val todayExpenses: Double,
        val monthlyIncome: Double,
        val monthlyExpenses: Double,
        val todaySales: Double,
        val monthlySales: Double,
        val monthlyPurchases: Double,
        val totalStaff: Int,
        val monthlyStaffExpenses: Double,
        val totalLoansGiven: Double,
        val totalLoansReceived: Double,
        val pendingCollections: Double,
        val recentTransactions: List<Transaction>
    )
}

/* ===== Simple horizontal groups adapter for Home ===== */
private class GroupsCarouselAdapter(
    private val onClick: (Group) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<GroupsCarouselAdapter.VH>() {

    private var data: List<Group> = emptyList()

    fun submit(list: List<Group>?) {
        data = list ?: emptyList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_chip_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position], onClick)
    }

    class VH(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<android.widget.TextView>(R.id.groupTitle)
        private val subtitle = itemView.findViewById<android.widget.TextView>(R.id.groupSubtitle)
        private val card = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.groupCard)

        fun bind(group: Group, onClick: (Group) -> Unit) {
            title.text = group.name ?: "Untitled"
            subtitle.text = if (!group.joinCode.isNullOrBlank()) "Code: ${group.joinCode}" else "Tap to open"
            card.setOnClickListener { onClick(group) }
        }
    }
}
