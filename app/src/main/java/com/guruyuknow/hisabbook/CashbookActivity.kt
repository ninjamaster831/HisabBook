package com.guruyuknow.hisabbook

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.widget.ImageView

class CashbookActivity : AppCompatActivity() {

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var helpButton: ImageView
    private lateinit var totalBalanceAmount: TextView
    private lateinit var todayBalanceAmount: TextView
    private lateinit var totalCashInHand: TextView
    private lateinit var totalOnline: TextView
    private lateinit var todayCashInHand: TextView
    private lateinit var todayOnline: TextView
    private lateinit var viewReportButton: MaterialButton
    private lateinit var outButton: MaterialButton
    private lateinit var inButton: MaterialButton
    private lateinit var entriesRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var bottomBar: LinearLayout

    // Insets state
    private var baseRecyclerBottomPadding = 0
    private var baseBottomBarBottomPadding = 0
    private var lastSystemBarBottomInset = 0

    // Data
    private val entries = mutableListOf<CashbookEntry>()
    private lateinit var entriesAdapter: CashbookEntriesAdapter
    private var currentUser: User? = null

    // Date formatter
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_cashbook)

        initViews()
        applyWindowInsets()
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        helpButton = findViewById(R.id.helpButton)
        totalBalanceAmount = findViewById(R.id.totalBalanceAmount)
        todayBalanceAmount = findViewById(R.id.todayBalanceAmount)
        totalCashInHand = findViewById(R.id.totalCashInHand)
        totalOnline = findViewById(R.id.totalOnline)
        todayCashInHand = findViewById(R.id.todayCashInHand)
        todayOnline = findViewById(R.id.todayOnline)
        viewReportButton = findViewById(R.id.viewReportButton)
        outButton = findViewById(R.id.outButton)
        inButton = findViewById(R.id.inButton)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        entriesRecyclerView = findViewById(R.id.entriesRecyclerView)
        bottomBar = findViewById(R.id.bottomBar)

        baseRecyclerBottomPadding = entriesRecyclerView.paddingBottom
        baseBottomBarBottomPadding = bottomBar.paddingBottom
    }

    /**
     * Keep header clear of status bar; lift list above bottom bar and gestural nav.
     * Works for both gesture navigation and 3-button navigation.
     */
    private fun applyWindowInsets() {
        // Apply to the whole content so we can adjust header spacer and bottom paddings.
        val content = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            lastSystemBarBottomInset = bars.bottom

            // Status bar spacer height (keep at least 24dp as per your layout)
            findViewById<View>(R.id.statusBarSpace)?.let { spacer ->
                val min24 = resources.getDimensionPixelSize(R.dimen.spacing_24dp)
                spacer.layoutParams = spacer.layoutParams.apply {
                    height = maxOf(min24, bars.top)
                }
                spacer.requestLayout()
            }

            // Lift the bottom bar above gesture nav
            bottomBar.updatePadding(
                bottom = baseBottomBarBottomPadding + bars.bottom
            )

            // Ensure the list clears bottom bar + nav
            updateRecyclerBottomPadding()

            insets
        }


        // Once the bottom bar knows its height, re-apply padding for the list
        bottomBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRecyclerBottomPadding()
        }
    }

    private fun updateRecyclerBottomPadding() {
        val extra = lastSystemBarBottomInset + bottomBar.height
        entriesRecyclerView.updatePadding(
            bottom = baseRecyclerBottomPadding + extra
        )
    }

    private fun setupRecyclerView() {
        entriesAdapter = CashbookEntriesAdapter(entries) { entry ->
            showEntryOptionsDialog(entry)
        }
        entriesRecyclerView.layoutManager = LinearLayoutManager(this)
        entriesRecyclerView.adapter = entriesAdapter
        entriesRecyclerView.clipToPadding = false
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        helpButton.setOnClickListener { showHelpDialog() }

        viewReportButton.setOnClickListener {
            startActivity(Intent(this, CashbookReportActivity::class.java))
        }

        outButton.setOnClickListener { showAddEntryDialog(CashbookEntry.TYPE_OUT) }
        inButton.setOnClickListener { showAddEntryDialog(CashbookEntry.TYPE_IN) }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
                if (currentUser != null) {
                    loadCashbookEntries()
                } else {
                    Toast.makeText(this@CashbookActivity, "Please login first", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CashbookActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadCashbookEntries() {
        try {
            val userId = currentUser?.id ?: return

            val result = SupabaseManager.client
                .from("cashbook_entries")
                .select {
                    filter { eq("user_id", userId) }
                    order("date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<CashbookEntry>()

            entries.clear()
            entries.addAll(result)

            runOnUiThread {
                entriesAdapter.setEntries(entries.toList())
                updateBalances()
                updateEmptyState()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(
                    this@CashbookActivity,
                    "Error loading entries: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAddEntryDialog(type: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_entry, null)

        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val categoryInput = dialogView.findViewById<TextInputEditText>(R.id.categoryInput)
        val cashRadio = dialogView.findViewById<MaterialButton>(R.id.cashRadio)
        val onlineRadio = dialogView.findViewById<MaterialButton>(R.id.onlineRadio)
        val dateButton = dialogView.findViewById<MaterialButton>(R.id.dateButton)

        var selectedPaymentMethod = CashbookEntry.PAYMENT_METHOD_CASH
        var selectedDate = dateFormatter.format(Date())
        dateButton.text = displayDateFormatter.format(Date())

        cashRadio.setOnClickListener {
            selectedPaymentMethod = CashbookEntry.PAYMENT_METHOD_CASH
            updatePaymentMethodButtons(cashRadio, onlineRadio, true)
        }
        onlineRadio.setOnClickListener {
            selectedPaymentMethod = CashbookEntry.PAYMENT_METHOD_ONLINE
            updatePaymentMethodButtons(cashRadio, onlineRadio, false)
        }

        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = dateFormatter.format(calendar.time)
                    dateButton.text = displayDateFormatter.format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (type == CashbookEntry.TYPE_IN) "Add Income" else "Add Expense")
            .setView(dialogView)
            .setPositiveButton("Save", null) // we override to avoid auto-dismiss
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = amountInput.text?.toString()?.toDoubleOrNull()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                val category = categoryInput.text?.toString()?.trim()?.ifEmpty { "General" }

                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                addCashbookEntry(amount, type, selectedPaymentMethod, description,
                    category.toString(), selectedDate)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updatePaymentMethodButtons(
        cashRadio: MaterialButton,
        onlineRadio: MaterialButton,
        isCashSelected: Boolean
    ) {
        val primary = ContextCompat.getColor(this, R.color.colorPrimary)
        val white = ContextCompat.getColor(this, android.R.color.white)
        val transparent = ContextCompat.getColor(this, android.R.color.transparent)

        if (isCashSelected) {
            cashRadio.setBackgroundColor(primary)
            cashRadio.setTextColor(white)
            onlineRadio.setBackgroundColor(transparent)
            onlineRadio.setTextColor(primary)
        } else {
            onlineRadio.setBackgroundColor(primary)
            onlineRadio.setTextColor(white)
            cashRadio.setBackgroundColor(transparent)
            cashRadio.setTextColor(primary)
        }
    }

    private fun addCashbookEntry(
        amount: Double,
        type: String,
        paymentMethod: String,
        description: String,
        category: String,
        date: String
    ) {
        Log.d("CashbookActivity", "=== Starting addCashbookEntry ===")

        lifecycleScope.launch {
            try {
                val userId = currentUser?.id
                if (userId == null) {
                    Toast.makeText(this@CashbookActivity, "User not found. Please login again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val entry = CashbookEntry(
                    userId = userId,
                    amount = amount,
                    type = type,
                    paymentMethod = paymentMethod,
                    description = description.ifEmpty { null },
                    category = category.ifEmpty { null },
                    date = date
                )

                val result = SupabaseManager.client
                    .from("cashbook_entries")
                    .insert(entry) { select() }
                    .decodeSingle<CashbookEntry>()

                entries.add(0, result)

                runOnUiThread {
                    entriesAdapter.notifyItemInserted(0)
                    entriesRecyclerView.scrollToPosition(0)
                    updateBalances()
                    updateEmptyState()
                    Toast.makeText(this@CashbookActivity, "Entry added successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CashbookActivity", "Error in addCashbookEntry", e)
                runOnUiThread {
                    Toast.makeText(this@CashbookActivity, "Error adding entry: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showEntryOptionsDialog(entry: CashbookEntry) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Entry Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editEntry(entry)
                    1 -> deleteEntry(entry)
                }
            }
            .show()
    }

    private fun editEntry(entry: CashbookEntry) {
        // TODO: implement edit flow (prefill dialog and update row)
        Toast.makeText(this, "Edit functionality coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun deleteEntry(entry: CashbookEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this entry?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        SupabaseManager.client
                            .from("cashbook_entries")
                            .delete { filter { eq("id", entry.id ?: "") } }

                        val index = entries.indexOf(entry)
                        if (index != -1) {
                            entries.removeAt(index)
                            runOnUiThread {
                                entriesAdapter.notifyItemRemoved(index)
                                updateBalances()
                                updateEmptyState()
                                Toast.makeText(this@CashbookActivity, "Entry deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@CashbookActivity, "Error deleting entry", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateBalances() {
        val today = dateFormatter.format(Date())

        var totalCash = 0.0
        var totalOnlineAmount = 0.0
        var todayCash = 0.0
        var todayOnlineAmount = 0.0

        entries.forEach { entry ->
            val delta = if (entry.type == CashbookEntry.TYPE_IN) entry.amount else -entry.amount
            if (entry.paymentMethod == CashbookEntry.PAYMENT_METHOD_CASH) {
                totalCash += delta
                if (entry.date == today) todayCash += delta
            } else {
                totalOnlineAmount += delta
                if (entry.date == today) todayOnlineAmount += delta
            }
        }

        val totalBalance = totalCash + totalOnlineAmount
        val todayBalance = todayCash + todayOnlineAmount

        totalBalanceAmount.text = "₹ ${String.format("%.0f", totalBalance)}"
        todayBalanceAmount.text = "₹ ${String.format("%.0f", todayBalance)}"
        totalCashInHand.text = "₹ ${String.format("%.0f", totalCash)}"
        totalOnline.text = "₹ ${String.format("%.0f", totalOnlineAmount)}"
        todayCashInHand.text = "₹ ${String.format("%.0f", todayCash)}"
        todayOnline.text = "₹ ${String.format("%.0f", todayOnlineAmount)}"

        updateBalanceTextColor(totalBalanceAmount, totalBalance)
        updateBalanceTextColor(todayBalanceAmount, todayBalance)
    }

    private fun updateBalanceTextColor(textView: TextView, balance: Double) {
        val green = ContextCompat.getColor(this, R.color.colorGreen)
        val red = ContextCompat.getColor(this, R.color.colorRed)
        val black = ContextCompat.getColor(this, R.color.black)
        textView.setTextColor(
            when {
                balance > 0 -> green
                balance < 0 -> red
                else -> black
            }
        )
    }

    private fun updateEmptyState() {
        if (entries.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            entriesRecyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            entriesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cashbook Help")
            .setMessage(
                """
                • Use IN button to add income/money received
                • Use OUT button to add expenses/money spent
                • Choose between Cash or Online payment method
                • View your total and today's balance at the top
                • Tap on any entry to edit or delete it
                • View detailed reports using the report button
                """.trimIndent()
            )
            .setPositiveButton("Got it", null)
            .show()
    }
}
