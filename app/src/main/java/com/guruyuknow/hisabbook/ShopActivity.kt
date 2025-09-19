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
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.Shop.Customer
import com.guruyuknow.hisabbook.Shop.CustomerSummary
import com.guruyuknow.hisabbook.Shop.ShopDatabase
import com.guruyuknow.hisabbook.databinding.ActivityShopBinding
import kotlinx.coroutines.launch

class ShopActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShopBinding
    private lateinit var customersAdapter: CustomersAdapter
    private val customers = mutableListOf<CustomerSummary>()

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopBinding.inflate(layoutInflater)
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

        setupUI()
        setupRecyclerView()
        loadCustomers()

        // Add subtle entrance animation for FAB
        binding.fabAddCustomer.alpha = 0f
        binding.fabAddCustomer.scaleX = 0.8f
        binding.fabAddCustomer.scaleY = 0.8f
        binding.fabAddCustomer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setStartDelay(200)
            .start()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.setNavigationOnClickListener {
                finish()
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }

            fabAddCustomer.setOnClickListener {
                showAddCustomerDialog()
            }
        }
    }

    private fun setupRecyclerView() {
        customersAdapter = CustomersAdapter(
            customers = customers,
            onCustomerClick = { customer ->
                val intent = Intent(this, CustomerDetailActivity::class.java)
                intent.putExtra("customer_id", customer.customerId)
                intent.putExtra("customer_name", customer.customerName)
                startActivity(intent)
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            },
            onAddPurchaseClick = { customer ->
                val intent = Intent(this, AddPurchaseActivity::class.java)
                intent.putExtra("customer_id", customer.customerId)
                intent.putExtra("customer_name", customer.customerName)
                startActivity(intent)
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }
        )

        binding.recyclerViewCustomers.apply {
            adapter = customersAdapter
            layoutManager = LinearLayoutManager(this@ShopActivity)
            // Add item animations
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
            }
        }

        updateEmptyState()
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            try {
                showLoadingState(true)
                val customerSummaries = ShopDatabase.getCustomerSummaries()
                customers.clear()
                customers.addAll(customerSummaries)
                customersAdapter.notifyDataSetChanged()

                // Animate total customers and pending amount
                binding.totalCustomersText.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(100)
                    .withEndAction {
                        binding.totalCustomersText.text = customers.size.toString()
                        binding.totalCustomersText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()

                val totalPending = customers.sumOf { it.pendingBalance }
                binding.totalPendingText.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(100)
                    .withEndAction {
                        binding.totalPendingText.text = "₹${String.format("%.2f", totalPending)}"
                        binding.totalPendingText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()

                updateEmptyState()
            } catch (e: Exception) {
                showErrorToast("Error loading customers: ${e.message}")
            } finally {
                showLoadingState(false)
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = customers.isEmpty()

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

            recyclerViewCustomers.animate()
                .alpha(if (isEmpty) 0f else 1f)
                .setDuration(200)
                .withStartAction {
                    if (!isEmpty) recyclerViewCustomers.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (isEmpty) recyclerViewCustomers.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showAddCustomerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_customer, null)
        val customerNameInput = dialogView.findViewById<TextInputEditText>(R.id.customerNameInput)
        val customerPhoneInput = dialogView.findViewById<TextInputEditText>(R.id.customerPhoneInput)

        // Animate dialog entrance
        dialogView.alpha = 0f
        dialogView.scaleX = 0.8f
        dialogView.scaleY = 0.8f
        dialogView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()

        val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Add New Customer")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = customerNameInput.text.toString().trim()
                val phone = customerPhoneInput.text.toString().trim()

                if (name.isEmpty()) {
                    showErrorToast("Customer name is required")
                    return@setPositiveButton
                }

                addNewCustomer(name, phone)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Animate dialog exit
                dialogView.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .start()
            }
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.show()

        // Animate dialog buttons
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.let { button ->
            button.setOnClickListener {
                button.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                        dialog.dismiss()
                        val name = customerNameInput.text.toString().trim()
                        val phone = customerPhoneInput.text.toString().trim()
                        if (name.isEmpty()) {
                            showErrorToast("Customer name is required")
                            return@withEndAction
                        }
                        addNewCustomer(name, phone)
                    }
                    .start()
            }
        }

        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.let { button ->
            button.setOnClickListener {
                button.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                        dialog.dismiss()
                    }
                    .start()
            }
        }
    }

    private fun addNewCustomer(name: String, phone: String) {
        lifecycleScope.launch {
            try {
                showLoadingState(true)
                val customer = Customer(
                    id = 0, // Will be auto-generated
                    name = name,
                    phone = phone,
                    createdAt = System.currentTimeMillis()
                )

                ShopDatabase.insertCustomer(customer)
                loadCustomers()
                Snackbar.make(
                    binding.root,
                    "Customer added successfully",
                    Snackbar.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                showErrorToast("Error adding customer: ${e.message}")
            } finally {
                showLoadingState(false)
            }
        }
    }

    private fun showLoadingState(isLoading: Boolean) {
        binding.apply {
            if (isLoading) {
                fabAddCustomer.isEnabled = false
                fabAddCustomer.alpha = 0.6f
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
                fabAddCustomer.isEnabled = true
                fabAddCustomer.alpha = 1f
                progressContainer.visibility = View.GONE
            }
        }
    }

    private fun showSuccessToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    private fun showErrorToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_LONG)
        toast.show()
    }

    override fun onResume() {
        super.onResume()
        loadCustomers() // Refresh data when returning from other activities
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }
}