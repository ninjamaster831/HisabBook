package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.Shop.CustomerDetail
import com.guruyuknow.hisabbook.Shop.Payment
import com.guruyuknow.hisabbook.Shop.ShopDatabase
import com.guruyuknow.hisabbook.databinding.ActivityCustomerDetailBinding
import kotlinx.coroutines.launch

class CustomerDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerDetailBinding
    private lateinit var purchasesAdapter: PurchasesAdapter
    private lateinit var paymentsAdapter: PaymentsAdapter
    private var customerId: Long = 0
    private var customerName: String = ""
    private var customerDetail: CustomerDetail? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerDetailBinding.inflate(layoutInflater)
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

        getIntentData()
        setupUI()
        setupTabs()
        setupRecyclerViews()
        loadCustomerDetail()

        // Add subtle entrance animations for FABs
        binding.fabAddPurchase.alpha = 0f
        binding.fabAddPurchase.scaleX = 0.8f
        binding.fabAddPurchase.scaleY = 0.8f
        binding.fabAddPurchase.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setStartDelay(200)
            .start()

        binding.fabAddPayment.alpha = 0f
        binding.fabAddPayment.scaleX = 0.8f
        binding.fabAddPayment.scaleY = 0.8f
        binding.fabAddPayment.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setStartDelay(200)
            .start()
    }

    private fun getIntentData() {
        customerId = intent.getLongExtra("customer_id", 0)
        customerName = intent.getStringExtra("customer_name") ?: ""
    }

    private fun setupUI() {
        binding.apply {
            toolbar.title = customerName
            toolbar.setNavigationOnClickListener {
                finish()
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }

            fabAddPurchase.setOnClickListener {
                val intent = Intent(this@CustomerDetailActivity, AddPurchaseActivity::class.java)
                intent.putExtra("customer_id", customerId)
                intent.putExtra("customer_name", customerName)
                startActivity(intent)
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }

            fabAddPayment.setOnClickListener {
                showAddPaymentDialog()
            }

            deleteCustomerButton.setOnClickListener {
                showDeleteConfirmationDialog()
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Purchases"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Payments"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.purchasesContainer.visibility = View.VISIBLE
                        binding.paymentsContainer.visibility = View.GONE
                        binding.fabAddPurchase.show()
                        binding.fabAddPayment.hide()
                        updatePurchasesEmptyState()
                    }
                    1 -> {
                        binding.purchasesContainer.visibility = View.GONE
                        binding.paymentsContainer.visibility = View.VISIBLE
                        binding.fabAddPurchase.hide()
                        binding.fabAddPayment.show()
                        updatePaymentsEmptyState()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerViews() {
        purchasesAdapter = PurchasesAdapter(emptyList())
        paymentsAdapter = PaymentsAdapter(emptyList())

        binding.recyclerViewPurchases.apply {
            adapter = purchasesAdapter
            layoutManager = LinearLayoutManager(this@CustomerDetailActivity)
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
            }
        }

        binding.recyclerViewPayments.apply {
            adapter = paymentsAdapter
            layoutManager = LinearLayoutManager(this@CustomerDetailActivity)
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
            }
        }

        updatePurchasesEmptyState()
        updatePaymentsEmptyState()
    }

    private fun loadCustomerDetail() {
        lifecycleScope.launch {
            try {
                showLoadingState(true)
                val detail = ShopDatabase.getCustomerDetail(customerId)
                customerDetail = detail

                detail?.let { updateUI(it) }
                    ?: run {
                        showErrorToast("Customer not found")
                        finish()
                        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                    }
            } catch (e: Exception) {
                showErrorToast("Error loading data: ${e.message}")
            } finally {
                showLoadingState(false)
            }
        }
    }

    private fun updateUI(detail: CustomerDetail) {
        binding.apply {
            // Animate summary card updates
            customerNameText.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    customerNameText.text = detail.customer.name
                    customerNameText.animate().alpha(1f).setDuration(100).start()
                }
                .start()

            customerPhoneText.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    customerPhoneText.text = detail.customer.phone ?: "No phone number"
                    customerPhoneText.animate().alpha(1f).setDuration(100).start()
                }
                .start()

            totalPurchasedText.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .withEndAction {
                    totalPurchasedText.text = detail.summary.getFormattedTotalPurchased()
                    totalPurchasedText.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
                .start()

            totalPaidText.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .withEndAction {
                    totalPaidText.text = detail.summary.getFormattedTotalPaid()
                    totalPaidText.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
                .start()

            pendingBalanceText.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .withEndAction {
                    pendingBalanceText.text = detail.summary.getFormattedPendingBalance()
                    val status = detail.summary.getBalanceStatus()
                    balanceStatusText.text = status.displayName
                    balanceStatusText.setTextColor(status.colorRes)
                    pendingBalanceText.setTextColor(status.colorRes)
                    pendingBalanceText.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
                .start()

            // Update adapters
            purchasesAdapter.updateData(detail.purchases)
            paymentsAdapter.updateData(detail.payments)

            updatePurchasesEmptyState()
            updatePaymentsEmptyState()
        }
    }

    private fun updatePurchasesEmptyState() {
        val isEmpty = purchasesAdapter.itemCount == 0
        binding.apply {
            purchasesEmptyStateLayout.animate()
                .alpha(if (isEmpty) 1f else 0f)
                .setDuration(200)
                .withStartAction {
                    if (isEmpty) purchasesEmptyStateLayout.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (!isEmpty) purchasesEmptyStateLayout.visibility = View.GONE
                }
                .start()

            recyclerViewPurchases.animate()
                .alpha(if (isEmpty) 0f else 1f)
                .setDuration(200)
                .withStartAction {
                    if (!isEmpty) recyclerViewPurchases.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (isEmpty) recyclerViewPurchases.visibility = View.GONE
                }
                .start()
        }
    }

    private fun updatePaymentsEmptyState() {
        val isEmpty = paymentsAdapter.itemCount == 0
        binding.apply {
            paymentsEmptyStateLayout.animate()
                .alpha(if (isEmpty) 1f else 0f)
                .setDuration(200)
                .withStartAction {
                    if (isEmpty) paymentsEmptyStateLayout.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (!isEmpty) paymentsEmptyStateLayout.visibility = View.GONE
                }
                .start()

            recyclerViewPayments.animate()
                .alpha(if (isEmpty) 0f else 1f)
                .setDuration(200)
                .withStartAction {
                    if (!isEmpty) recyclerViewPayments.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (isEmpty) recyclerViewPayments.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showAddPaymentDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_payment, null)
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.notesInput)

        // Set pending balance as hint
        customerDetail?.let { detail ->
            if (detail.summary.pendingBalance > 0) {
                amountInput.setText(detail.summary.getFormattedPendingBalance().replace("₹", ""))
            }
        }

        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Add Payment")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val amountStr = amountInput.text.toString().trim()
                val notes = notesInput.text.toString().trim().ifEmpty { null }

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    showErrorToast("Valid amount is required")
                    return@setPositiveButton
                }

                addPayment(amount, notes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addPayment(amount: Double, notes: String?) {
        lifecycleScope.launch {
            try {
                showLoadingState(true)
                val payment = Payment(
                    customerId = customerId,
                    amount = amount,
                    notes = notes
                )

                ShopDatabase.insertPayment(payment)
                loadCustomerDetail() // Refresh data

                Snackbar.make(
                    binding.root,
                    "Payment added successfully",
                    Snackbar.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                showErrorToast("Error adding payment: ${e.message}")
            } finally {
                showLoadingState(false)
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Delete Customer")
            .setMessage("Are you sure you want to delete this customer and all associated data?")
            .setPositiveButton("Delete") { _, _ ->
                deleteCustomer()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCustomer() {
        lifecycleScope.launch {
            try {
                showLoadingState(true)
                ShopDatabase.deleteCustomer(customerId)
                showSuccessToast("Customer deleted successfully")
                finish()
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            } catch (e: Exception) {
                showErrorToast("Error deleting customer: ${e.message}")
            } finally {
                showLoadingState(false)
            }
        }
    }

    private fun showLoadingState(isLoading: Boolean) {
        binding.apply {
            if (isLoading) {
                fabAddPurchase.isEnabled = false
                fabAddPurchase.alpha = 0.6f
                fabAddPayment.isEnabled = false
                fabAddPayment.alpha = 0.6f
                progressContainer.visibility = View.VISIBLE

                // Animate progress container entrance
                progressContainer.alpha = 0f
                progressContainer.scaleX = 0.8f
                progressContainer.scaleY = 0.8f
                progressContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            } else {
                fabAddPurchase.isEnabled = true
                fabAddPurchase.alpha = 1f
                fabAddPayment.isEnabled = true
                fabAddPayment.alpha = 1f
                progressContainer.visibility = View.GONE
            }
        }
    }

    private fun showSuccessToast(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        loadCustomerDetail() // Refresh data when returning from add purchase
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }
}