package com.guruyuknow.hisabbook

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class CashbookEntry(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val type: String, // "IN" or "OUT"
    @SerialName("payment_method") val paymentMethod: String, // "CASH" or "ONLINE"
    val description: String?,
    val category: String?,
    val date: String, // ISO date string
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("payment_method_id") val paymentMethodId: String? = null
) {
    init {
        // Client-side validation to match database constraints
        require(amount > 0) { "Amount must be greater than 0" }
        require(type in listOf("IN", "OUT")) { "Type must be 'IN' or 'OUT'" }
        require(category != null || categoryId != null) {
            "Either category or categoryId must be provided"
        }
    }

    companion object {
        const val TYPE_IN = "IN"
        const val TYPE_OUT = "OUT"
        const val PAYMENT_METHOD_CASH = "CASH"
        const val PAYMENT_METHOD_ONLINE = "ONLINE"
    }
}

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

        // Add RecyclerView to your layout (you'll need to add this to XML)
        entriesRecyclerView = findViewById(R.id.entriesRecyclerView)
    }

    private fun setupRecyclerView() {
        entriesAdapter = CashbookEntriesAdapter(entries) { entry ->
            // Handle entry click (edit/delete)
            showEntryOptionsDialog(entry)
        }
        entriesRecyclerView.layoutManager = LinearLayoutManager(this)
        entriesRecyclerView.adapter = entriesAdapter
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        helpButton.setOnClickListener {
            // Show help dialog
            showHelpDialog()
        }

        viewReportButton.setOnClickListener {
            // Navigate to report screen
            // startActivity(Intent(this, ReportActivity::class.java))
            Toast.makeText(this, "Report feature coming soon", Toast.LENGTH_SHORT).show()
        }

        outButton.setOnClickListener {
            showAddEntryDialog("OUT")
        }

        inButton.setOnClickListener {
            showAddEntryDialog("IN")
        }
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
                    filter {
                        eq("user_id", userId)
                    }
                    order("date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<CashbookEntry>()

            entries.clear()
            entries.addAll(result)

            runOnUiThread {
                entriesAdapter.notifyDataSetChanged()
                updateBalances()
                updateEmptyState()
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@CashbookActivity, "Error loading entries: ${e.message}", Toast.LENGTH_SHORT).show()
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

        var selectedPaymentMethod = "CASH"
        var selectedDate = dateFormatter.format(Date())

        // Set initial date
        dateButton.text = displayDateFormatter.format(Date())

        // Payment method selection
        cashRadio.setOnClickListener {
            selectedPaymentMethod = "CASH"
            updatePaymentMethodButtons(cashRadio, onlineRadio, true)
        }

        onlineRadio.setOnClickListener {
            selectedPaymentMethod = "ONLINE"
            updatePaymentMethodButtons(cashRadio, onlineRadio, false)
        }

        // Date picker
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = dateFormatter.format(calendar.time)
                    dateButton.text = displayDateFormatter.format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (type == "IN") "Add Income" else "Add Expense")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                val description = descriptionInput.text.toString().trim()
                val category = categoryInput.text.toString().trim()

                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                addCashbookEntry(amount, type, selectedPaymentMethod, description, category, selectedDate)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun updatePaymentMethodButtons(cashRadio: MaterialButton, onlineRadio: MaterialButton, isCashSelected: Boolean) {
        if (isCashSelected) {
            cashRadio.setBackgroundColor(getColor(R.color.colorPrimary))
            cashRadio.setTextColor(getColor(android.R.color.white))
            onlineRadio.setBackgroundColor(getColor(android.R.color.transparent))
            onlineRadio.setTextColor(getColor(R.color.colorPrimary))
        } else {
            onlineRadio.setBackgroundColor(getColor(R.color.colorPrimary))
            onlineRadio.setTextColor(getColor(android.R.color.white))
            cashRadio.setBackgroundColor(getColor(android.R.color.transparent))
            cashRadio.setTextColor(getColor(R.color.colorPrimary))
        }
    }

    private fun addCashbookEntry(amount: Double, type: String, paymentMethod: String, description: String, category: String, date: String) {
        Log.d("CashbookActivity", "=== Starting addCashbookEntry ===")
        Log.d("CashbookActivity", "Parameters - Amount: $amount, Type: $type, PaymentMethod: $paymentMethod")
        Log.d("CashbookActivity", "Parameters - Description: '$description', Category: '$category', Date: $date")

        lifecycleScope.launch {
            try {
                Log.d("CashbookActivity", "Inside coroutine, checking currentUser...")
                val userId = currentUser?.id

                if (userId == null) {
                    Log.e("CashbookActivity", "ERROR: currentUser is null, cannot proceed")
                    runOnUiThread {
                        Toast.makeText(this@CashbookActivity, "User not found. Please login again.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("CashbookActivity", "User ID: $userId")

                // Create entry object
                val entry = CashbookEntry(
                    userId = userId,
                    amount = amount,
                    type = type,
                    paymentMethod = paymentMethod,
                    description = description.ifEmpty { null },
                    category = category.ifEmpty { null },
                    date = date
                )

                Log.d("CashbookActivity", "Created CashbookEntry object: $entry")
                Log.d("CashbookActivity", "About to insert entry into Supabase...")

                // Insert into database - Use select() to force return of inserted data
                val result = SupabaseManager.client
                    .from("cashbook_entries")
                    .insert(entry) {
                        // Force return the inserted row
                        select()
                    }
                    .decodeSingle<CashbookEntry>()

                Log.d("CashbookActivity", "✅ Successfully inserted entry into database")
                Log.d("CashbookActivity", "Result from database: $result")
                Log.d("CashbookActivity", "Current entries list size before adding: ${entries.size}")

                // Add to local list
                entries.add(0, result)
                Log.d("CashbookActivity", "Added entry to local list. New size: ${entries.size}")

                runOnUiThread {
                    Log.d("CashbookActivity", "Updating UI on main thread...")
                    entriesAdapter.notifyItemInserted(0)
                    Log.d("CashbookActivity", "Notified adapter of item insertion")

                    updateBalances()
                    Log.d("CashbookActivity", "Updated balances")

                    updateEmptyState()
                    Log.d("CashbookActivity", "Updated empty state")

                    Toast.makeText(this@CashbookActivity, "Entry added successfully", Toast.LENGTH_SHORT).show()
                    Log.d("CashbookActivity", "✅ Entry added successfully - UI updated")
                }

            } catch (e: Exception) {
                Log.e("CashbookActivity", "❌ ERROR in addCashbookEntry: ${e.message}")
                Log.e("CashbookActivity", "Exception type: ${e.javaClass.simpleName}")
                Log.e("CashbookActivity", "Stack trace:", e)

                runOnUiThread {
                    val errorMessage = "Error adding entry: ${e.message}"
                    Toast.makeText(this@CashbookActivity, errorMessage, Toast.LENGTH_LONG).show()
                    Log.e("CashbookActivity", "Showed error toast: $errorMessage")
                }
            }
        }

        Log.d("CashbookActivity", "=== End of addCashbookEntry function (coroutine launched) ===")
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
        // Similar to showAddEntryDialog but pre-filled with entry data
        // Implementation similar to add dialog but with update functionality
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
                            .delete {
                                filter {
                                    eq("id", entry.id ?: "")
                                }
                            }

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
            val amount = if (entry.type == "IN") entry.amount else -entry.amount

            if (entry.paymentMethod == "CASH") {
                totalCash += amount
                if (entry.date == today) {
                    todayCash += amount
                }
            } else {
                totalOnlineAmount += amount
                if (entry.date == today) {
                    todayOnlineAmount += amount
                }
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

        // Update balance text colors based on positive/negative
        updateBalanceTextColor(totalBalanceAmount, totalBalance)
        updateBalanceTextColor(todayBalanceAmount, todayBalance)
    }

    private fun updateBalanceTextColor(textView: TextView, balance: Double) {
        when {
            balance > 0 -> textView.setTextColor(getColor(R.color.colorGreen))
            balance < 0 -> textView.setTextColor(getColor(R.color.colorRed))
            else -> textView.setTextColor(getColor(R.color.black))
        }
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
            .setMessage("""
                • Use IN button to add income/money received
                • Use OUT button to add expenses/money spent
                • Choose between Cash or Online payment method
                • View your total and today's balance at the top
                • Tap on any entry to edit or delete it
                • View detailed reports using the report button
            """.trimIndent())
            .setPositiveButton("Got it", null)
            .show()
    }
}