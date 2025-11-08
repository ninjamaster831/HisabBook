package com.guruyuknow.hisabbook.Bills

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ActivityBillDetailBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class BillDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBillDetailBinding

    companion object {
        const val EXTRA_BILL_ID = "bill_id"
        const val EXTRA_IMAGE_URL = "image_url"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_DATE = "date"
        const val EXTRA_TYPE = "type"
        const val EXTRA_PAYMENT_METHOD = "payment_method"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_EXTRACTED_TEXT = "extracted_text"
        const val EXTRA_EXTRACTED_AMOUNT = "extracted_amount"
        const val EXTRA_CONFIDENCE_SCORE = "confidence_score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBillDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set status bar color
        window.statusBarColor = getColor(R.color.primary_color)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Bill Details"
        }

        loadBillDetails()
    }

    private fun loadBillDetails() {
        // Get data from intent
        val billId = intent.getStringExtra(EXTRA_BILL_ID) ?: ""
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val date = intent.getStringExtra(EXTRA_DATE)
        val type = intent.getStringExtra(EXTRA_TYPE)
        val paymentMethod = intent.getStringExtra(EXTRA_PAYMENT_METHOD)
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val extractedText = intent.getStringExtra(EXTRA_EXTRACTED_TEXT)
        val extractedAmount = intent.getDoubleExtra(EXTRA_EXTRACTED_AMOUNT, 0.0)
        val confidenceScore = intent.getDoubleExtra(EXTRA_CONFIDENCE_SCORE, 0.0)

        // Load bill image
        if (!imageUrl.isNullOrBlank()) {
            binding.imgBill.isVisible = true
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.imgBill)

            // Click to view full screen (TODO: Implement if needed)
            binding.imgBill.setOnClickListener {
                // TODO: Open full screen image viewer
            }
        } else {
            binding.imgBill.isVisible = false
        }

        // Amount (Primary)
        val amountStr = formatCurrency(amount)
        binding.tvAmount.text = amountStr

        // Type indicator
        binding.tvType.text = when (type?.uppercase()) {
            "IN" -> "Income"
            "OUT" -> "Expense"
            else -> type ?: "Unknown"
        }
        binding.tvType.setBackgroundResource(
            if (type?.uppercase() == "IN") R.drawable.bg_type_in
            else R.drawable.bg_type_out
        )

        // Date
        binding.tvDate.text = formatDate(date)

        // Payment Method
        binding.tvPaymentMethod.text = paymentMethod ?: "N/A"

        // Category
        binding.tvCategory.text = category ?: "Uncategorized"

        // Description
        if (!description.isNullOrBlank()) {
            binding.tvDescription.text = description
            binding.labelDescription.isVisible = true
            binding.tvDescription.isVisible = true
        } else {
            binding.labelDescription.isVisible = false
            binding.tvDescription.isVisible = false
        }

        // Extracted Text (OCR)
        if (!extractedText.isNullOrBlank()) {
            binding.tvExtractedText.text = extractedText
            binding.labelExtractedText.isVisible = true
            binding.tvExtractedText.isVisible = true
        } else {
            binding.labelExtractedText.isVisible = false
            binding.tvExtractedText.isVisible = false
        }

        // OCR Details
        if (extractedAmount > 0.0) {
            binding.tvExtractedAmount.text = "OCR Amount: ${formatCurrency(extractedAmount)}"
            binding.tvExtractedAmount.isVisible = true
        } else {
            binding.tvExtractedAmount.isVisible = false
        }

        if (confidenceScore > 0.0) {
            binding.tvConfidence.text = "Confidence: ${String.format("%.1f", confidenceScore * 100)}%"
            binding.tvConfidence.isVisible = true
        } else {
            binding.tvConfidence.isVisible = false
        }
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
        } catch (e: Exception) {
            "₹$amount"
        }
    }

    private fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "N/A"

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}