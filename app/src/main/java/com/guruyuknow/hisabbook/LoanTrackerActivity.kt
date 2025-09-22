package com.guruyuknow.hisabbook

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.databinding.ActivityLoanTrackerBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LoanTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoanTrackerBinding
    private lateinit var loanAdapter: LoanAdapter
    private val loanList = mutableListOf<Loan>()

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoanTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable edge-to-edge design with proper system window handling
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Handle system windows properly for different screen sizes
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadLoans()

        // Add subtle entrance animation for FAB
        binding.fabAddLoan.alpha = 0f
        binding.fabAddLoan.scaleX = 0.8f
        binding.fabAddLoan.scaleY = 0.8f
        binding.fabAddLoan.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setStartDelay(200)
            .start()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun setupRecyclerView() {
        loanAdapter = LoanAdapter(loanList) { loan, action ->
            when (action) {
                LoanAdapter.Action.EDIT -> editLoan(loan)
                LoanAdapter.Action.DELETE -> deleteLoan(loan)
                LoanAdapter.Action.MARK_PAID -> markAsPaid(loan)
                LoanAdapter.Action.SEND_REMINDER -> sendWhatsAppReminder(loan)
            }
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@LoanTrackerActivity)
            adapter = loanAdapter
            // Add item animations
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
            }
        }
        updateEmptyState()
    }

    private fun setupClickListeners() {
        binding.fabAddLoan.setOnClickListener {
            // Add click animation
            binding.fabAddLoan.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    binding.fabAddLoan.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    showAddLoanDialog()
                }
                .start()
        }
    }

    private fun loadLoans() {
        lifecycleScope.launch {
            try {
                val loans = LoanDatabaseManager.getAllLoans()
                loanList.clear()
                loanList.addAll(loans)
                loanAdapter.notifyDataSetChanged()
                updateEmptyState()
            } catch (e: Exception) {
                showErrorToast("Error loading loans: ${e.message}")
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = loanList.isEmpty()
        binding.apply {
            // Smooth transition between empty state and list
            emptyStateLayout.animate()
                .alpha(if (isEmpty) 1f else 0f)
                .setDuration(200)
                .withStartAction {
                    if (isEmpty) emptyStateLayout.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (!isEmpty) emptyStateLayout.visibility = View.GONE
                }
                .start()

            recyclerView.animate()
                .alpha(if (isEmpty) 0f else 1f)
                .setDuration(200)
                .withStartAction {
                    if (!isEmpty) recyclerView.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (isEmpty) recyclerView.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showAddLoanDialog(existingLoan: Loan? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_loan, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.etFriendName)
        val phoneInput = dialogView.findViewById<TextInputEditText>(R.id.etPhoneNumber)
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val reminderDateInput = dialogView.findViewById<TextInputEditText>(R.id.etReminderDate)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.etNotes)

        var selectedDateTime: Calendar? = null

        existingLoan?.let { loan ->
            nameInput.setText(loan.friend_name)
            phoneInput.setText(loan.phone_number)
            amountInput.setText(loan.amount.toString())
            notesInput.setText(loan.notes)
            loan.reminder_date_time?.let { dateTime ->
                selectedDateTime = Calendar.getInstance().apply { timeInMillis = dateTime }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                reminderDateInput.setText(dateFormat.format(Date(dateTime)))
            }
        }

        reminderDateInput.setOnClickListener {
            showDateTimePicker { dateTime ->
                selectedDateTime = dateTime
                val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                reminderDateInput.setText(dateFormat.format(dateTime.time))
            }
        }

        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(if (existingLoan == null) "Add Loan" else "Edit Loan")
            .setView(dialogView)
            .setPositiveButton(if (existingLoan == null) "Add" else "Update") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val amountText = amountInput.text.toString().trim()
                val notes = notesInput.text.toString().trim()

                if (validateInput(name, phone, amountText)) {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (existingLoan == null) {
                        addLoan(name, phone, amount, notes, selectedDateTime?.timeInMillis)
                    } else {
                        updateLoan(
                            existingLoan.copy(
                                friend_name = name,
                                phone_number = phone,
                                amount = amount,
                                notes = notes,
                                reminder_date_time = selectedDateTime?.timeInMillis
                            )
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDateTimePicker(onDateTimeSelected: (Calendar) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            c.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, min)
                onDateTimeSelected(c)
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun validateInput(name: String, phone: String, amount: String): Boolean {
        return when {
            name.isEmpty() -> {
                showErrorToast("Please enter friend's name")
                false
            }
            phone.isEmpty() -> {
                showErrorToast("Please enter phone number")
                false
            }
            amount.isEmpty() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0 -> {
                showErrorToast("Please enter valid amount")
                false
            }
            else -> true
        }
    }

    private fun addLoan(name: String, phone: String, amount: Double, notes: String, reminderDateTime: Long?) {
        lifecycleScope.launch {
            try {
                val loan = Loan(
                    id = UUID.randomUUID().toString(),
                    user_id = null,
                    friend_name = name,
                    phone_number = phone,
                    amount = amount,
                    notes = notes,
                    date_created = System.currentTimeMillis(),
                    reminder_date_time = reminderDateTime,
                    is_paid = false
                )
                LoanDatabaseManager.insertLoan(loan)
                reminderDateTime?.let { scheduleReminder(loan) }
                loadLoans()
                showSuccessToast("Loan added successfully")
                Snackbar.make(binding.root, "Loan added", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showErrorToast("Error adding loan: ${e.message}")
            }
        }
    }

    private fun updateLoan(loan: Loan) {
        lifecycleScope.launch {
            try {
                LoanDatabaseManager.updateLoan(loan)
                WorkManager.getInstance(this@LoanTrackerActivity).cancelUniqueWork("reminder_${loan.id}")
                loan.reminder_date_time?.let { scheduleReminder(loan) }
                loadLoans()
                showSuccessToast("Loan updated successfully")
                Snackbar.make(binding.root, "Loan updated", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showErrorToast("Error updating loan: ${e.message}")
            }
        }
    }

    private fun editLoan(loan: Loan) = showAddLoanDialog(loan)

    private fun deleteLoan(loan: Loan) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Loan")
            .setMessage("Are you sure you want to delete this loan record?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        LoanDatabaseManager.deleteLoan(loan.id)
                        WorkManager.getInstance(this@LoanTrackerActivity).cancelUniqueWork("reminder_${loan.id}")
                        val position = loanList.indexOf(loan)
                        loanList.remove(loan)
                        loanAdapter.notifyItemRemoved(position)
                        updateEmptyState()
                        showSuccessToast("Loan deleted successfully")
                        Snackbar.make(binding.root, "Loan deleted", Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        showErrorToast("Error deleting loan: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun markAsPaid(loan: Loan) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Mark as Paid")
            .setMessage("Mark this loan as paid?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val updatedLoan = loan.copy(is_paid = true)
                        LoanDatabaseManager.updateLoan(updatedLoan)
                        WorkManager.getInstance(this@LoanTrackerActivity).cancelUniqueWork("reminder_${loan.id}")
                        loadLoans()
                        showSuccessToast("Loan marked as paid")
                        Snackbar.make(binding.root, "Loan marked as paid", Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        showErrorToast("Error updating loan: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        intent?.let {
            if (it.getBooleanExtra("openReminderDialog", false)) {
                val friendName = it.getStringExtra("friend_name") ?: return
                val phone = it.getStringExtra("phone_number") ?: return
                val msg = it.getStringExtra("message") ?: ""
                showMessageOptionsDialog(friendName, phone, msg)
                it.removeExtra("openReminderDialog")
            }
        }
    }

    private fun normalizeForWhatsApp(phone: String, defaultCountryCode: String = "91"): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > 10) digits else defaultCountryCode + digits
    }

    private fun isAppInstalled(pkg: String): Boolean {
        val pm = packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun sendWhatsAppReminder(loan: Loan) {
        val msg = buildString {
            append("Hi ${loan.friend_name}! This is a friendly reminder about the loan of ₹${"%.2f".format(loan.amount)}")
            append(" from ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(loan.date_created))}.")
            if (loan.notes.isNotEmpty()) append(" Note: ${loan.notes}")
        }
        val phoneWa = normalizeForWhatsApp(loan.phone_number)

        try {
            if (isAppInstalled("com.whatsapp.w4b")) {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$phoneWa?text=${Uri.encode(msg)}")
                    setPackage("com.whatsapp.w4b")
                }
                startActivity(i)
                return
            }
            if (isAppInstalled("com.whatsapp")) {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$phoneWa?text=${Uri.encode(msg)}")
                    setPackage("com.whatsapp")
                }
                startActivity(i)
                return
            }
            runCatching {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phoneWa?text=${Uri.encode(msg)}"))
                startActivity(i)
            }.onFailure {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$phoneWa&text=${Uri.encode(msg)}"))
                startActivity(web)
            }
        } catch (e: Exception) {
            showMessageOptionsDialog(loan.friend_name, loan.phone_number, msg)
        }
    }

    private fun showMessageOptionsDialog(friendName: String, phoneNumber: String, message: String) {
        val options = arrayOf("Open Web WhatsApp", "Send SMS", "Copy Message", "Cancel")
        MaterialAlertDialogBuilder(this)
            .setTitle("WhatsApp not installed")
            .setMessage("Choose how to send the reminder to $friendName:")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        runCatching {
                            val webWhatsapp = Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://api.whatsapp.com/send?phone=${normalizeForWhatsApp(phoneNumber)}&text=${Uri.encode(message)}"))
                            startActivity(webWhatsapp)
                            showSuccessToast("Opening Web WhatsApp...")
                        }.onFailure { showErrorToast("Cannot open web browser") }
                    }
                    1 -> {
                        runCatching {
                            val sms = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("sms:$phoneNumber")
                                putExtra("sms_body", message)
                            }
                            startActivity(sms)
                            showSuccessToast("Opening SMS app...")
                        }.onFailure { showErrorToast("Cannot open SMS app") }
                    }
                    2 -> {
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Loan Reminder", message))
                        showSuccessToast("Message copied to clipboard")
                    }
                }
            }
            .setCancelable(true)
            .show()
    }

    private fun scheduleReminder(loan: Loan) {
        loan.reminder_date_time?.let { reminderTime ->
            val delay = reminderTime - System.currentTimeMillis()
            if (delay > 0) {
                val data = workDataOf(
                    "loanId" to loan.id,
                    "friend_name" to loan.friend_name,
                    "phone_number" to loan.phone_number,
                    "amount" to loan.amount,
                    "notes" to loan.notes
                )
                val req = OneTimeWorkRequestBuilder<LoanReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(this)
                    .enqueueUniqueWork("reminder_${loan.id}", ExistingWorkPolicy.REPLACE, req)
            }
        }
    }

    private fun showSuccessToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }
}