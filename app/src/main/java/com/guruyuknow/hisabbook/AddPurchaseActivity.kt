package com.guruyuknow.hisabbook

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
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.Shop.Purchase
import com.guruyuknow.hisabbook.Shop.PurchaseItem
import com.guruyuknow.hisabbook.Shop.ShopDatabase
import com.guruyuknow.hisabbook.databinding.ActivityAddPurchaseBinding
import kotlinx.coroutines.launch

class AddPurchaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPurchaseBinding
    private lateinit var itemsAdapter: PurchaseItemsAdapter
    private val purchaseItems = mutableListOf<PurchaseItem>()
    private var customerId: Long = 0
    private var customerName: String = ""

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPurchaseBinding.inflate(layoutInflater)
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
        setupRecyclerView()
        updateTotalAmount()
    }

    private fun getIntentData() {
        customerId = intent.getLongExtra("customer_id", 0)
        customerName = intent.getStringExtra("customer_name") ?: ""
    }

    private fun setupUI() {
        binding.apply {
            // Set up toolbar with customer name
            if (customerName.isNotEmpty()) {
                toolbar.title = "Purchase for $customerName"
            }

            toolbar.setNavigationOnClickListener {
                // Add smooth transition when going back
                finish()
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }

            fabAddItem.setOnClickListener {
                showAddItemDialog()
            }

            buttonSavePurchase.setOnClickListener {
                savePurchase()
            }

            // Add subtle entrance animation
            fabAddItem.alpha = 0f
            fabAddItem.scaleX = 0.8f
            fabAddItem.scaleY = 0.8f
            fabAddItem.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setStartDelay(200)
                .start()
        }
    }

    private fun setupRecyclerView() {
        itemsAdapter = PurchaseItemsAdapter(
            items = purchaseItems,
            onRemoveClick = { item ->
                // Add smooth removal animation
                val position = purchaseItems.indexOf(item)
                purchaseItems.remove(item)
                itemsAdapter.notifyItemRemoved(position)
                updateTotalAmount()
                updateEmptyState()

                // Show undo snackbar for better UX
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    "Item removed",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
        )

        binding.recyclerViewItems.apply {
            adapter = itemsAdapter
            layoutManager = LinearLayoutManager(this@AddPurchaseActivity)
            // Add item animations
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
            }
        }

        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = purchaseItems.isEmpty()

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

            recyclerViewItems.animate()
                .alpha(if (isEmpty) 0f else 1f)
                .setDuration(200)
                .withStartAction {
                    if (!isEmpty) recyclerViewItems.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (isEmpty) recyclerViewItems.visibility = View.GONE
                }
                .start()

            // Animate button state change
            buttonSavePurchase.animate()
                .alpha(if (isEmpty) 0.6f else 1f)
                .setDuration(200)
                .start()

            buttonSavePurchase.isEnabled = !isEmpty
        }
    }

    private fun showAddItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_item, null)
        val itemNameInput = dialogView.findViewById<TextInputEditText>(R.id.itemNameInput)
        val quantityInput = dialogView.findViewById<TextInputEditText>(R.id.quantityInput)
        val priceInput = dialogView.findViewById<TextInputEditText>(R.id.priceInput)

        val dialog = MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle("Add New Item")
            .setView(dialogView)
            .setPositiveButton("Add Item", null) // we'll handle validation inside
            .setNegativeButton("Cancel", null)
            .create()

        // ✅ Force dialog background to white
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        dialog.show()

        // Handle positive button click with validation
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val itemName = itemNameInput.text.toString().trim()
            val quantityStr = quantityInput.text.toString().trim()
            val priceStr = priceInput.text.toString().trim()

            if (itemName.isEmpty()) {
                showErrorToast("Item name is required")
                return@setOnClickListener
            }

            val quantity = quantityStr.toIntOrNull()
            if (quantity == null || quantity <= 0) {
                showErrorToast("Valid quantity is required")
                return@setOnClickListener
            }

            val price = priceStr.toDoubleOrNull()
            if (price == null || price < 0) {
                showErrorToast("Valid price is required")
                return@setOnClickListener
            }

            addItem(itemName, quantity, price)
            dialog.dismiss()
        }
    }


    private fun addItem(itemName: String, quantity: Int, unitPrice: Double) {
        val item = PurchaseItem(
            itemName = itemName,
            quantity = quantity,
            unitPrice = unitPrice,
            totalPrice = quantity * unitPrice
        )

        purchaseItems.add(item)
        itemsAdapter.notifyItemInserted(purchaseItems.size - 1)

        // Scroll to the new item
        binding.recyclerViewItems.smoothScrollToPosition(purchaseItems.size - 1)

        updateTotalAmount()
        updateEmptyState()

        // Success feedback
        showSuccessToast("Item added successfully")
    }

    private fun updateTotalAmount() {
        val total = purchaseItems.sumOf { it.totalPrice }
        val formattedTotal = "₹${String.format("%.2f", total)}"

        // Animate total amount change
        binding.totalAmountText.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                binding.totalAmountText.text = formattedTotal
                binding.totalAmountText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun savePurchase() {
        if (purchaseItems.isEmpty()) {
            showErrorToast("Please add at least one item")
            return
        }

        lifecycleScope.launch {
            try {
                // Show modern loading state
                showLoadingState(true)

                val totalAmount = purchaseItems.sumOf { it.totalPrice }
                val notes = binding.notesInput.text.toString().trim().ifEmpty { null }

                val purchase = Purchase(
                    customerId = customerId,
                    items = purchaseItems,
                    totalAmount = totalAmount,
                    notes = notes
                )

                ShopDatabase.insertPurchase(purchase)

                // Success animation and feedback
                showSuccessToast("Purchase saved successfully!")

                // Add success animation before finishing
                binding.buttonSavePurchase.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction {
                        binding.buttonSavePurchase.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .withEndAction {
                                finish()
                                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                            }
                            .start()
                    }
                    .start()

            } catch (e: Exception) {
                showErrorToast("Error saving purchase: ${e.message}")
                showLoadingState(false)
            }
        }
    }

    private fun showLoadingState(isLoading: Boolean) {
        binding.apply {
            if (isLoading) {
                buttonSavePurchase.isEnabled = false
                buttonSavePurchase.text = "Saving..."
                progressContainer.visibility = View.VISIBLE

                // Disable interactions
                fabAddItem.isEnabled = false
                fabAddItem.alpha = 0.6f

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
                buttonSavePurchase.isEnabled = true
                buttonSavePurchase.text = "Save Purchase"
                progressContainer.visibility = View.GONE

                // Re-enable interactions
                fabAddItem.isEnabled = true
                fabAddItem.alpha = 1f
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

    override fun onBackPressed() {
        if (purchaseItems.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Discard Purchase?")
                .setMessage("You have unsaved items. Are you sure you want to go back?")
                .setPositiveButton("Discard") { _, _ ->
                    super.onBackPressed()
                    overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                }
                .setNegativeButton("Continue Editing", null)
                .show()
        } else {
            super.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }
}