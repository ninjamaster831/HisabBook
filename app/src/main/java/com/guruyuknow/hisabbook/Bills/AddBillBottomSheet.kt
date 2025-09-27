package com.guruyuknow.hisabbook.Bills

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.BottomSheetAddBillBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AddBillBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBillBinding? = null
    private val binding get() = _binding!!

    private lateinit var billType: String
    private var tabName: String = ""
    private var imageUri: Uri? = null
    private var ocrResult: BillOcr.OcrResult? = null
    private var selectedDate: LocalDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDate.now()
    } else {
        // Fallback for older Android versions
        null!!
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            displayImagePreview(it)
            processImageWithOCR(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            billType = it.getString(ARG_TYPE, "OUT")
            tabName = it.getString(ARG_TAB_NAME, "")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddBillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBottomSheet()
        setupUI()
        setupImagePicker()
        setupDatePicker()
        setupPaymentMethods()
        setupCategories()
        setupSaveButton()
    }

    private fun setupBottomSheet() {
        // Make bottom sheet fully expanded
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // Set title based on type and tab name
        val title = when (billType) {
            "OUT" -> "Add ${tabName.ifEmpty { "Expense" }}"
            "IN" -> "Add ${tabName.ifEmpty { "Purchase" }}"
            else -> "Add Bill"
        }
        binding.tvTitle?.text = title
    }

    private fun setupUI() {
        // Set appropriate colors/icons based on bill type
        val colorResId = if (billType == "OUT") {
            R.color.expense_color // Red for expenses
        } else {
            R.color.income_color // Green for income/purchases
        }

        // Apply theme colors if needed
        try {
            val color = ContextCompat.getColor(requireContext(), colorResId)
            // Apply to relevant UI elements
        } catch (e: Exception) {
            // Handle color resource not found
        }
    }

    private fun displayImagePreview(uri: Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.imgPreview?.apply {
                    setImageBitmap(bitmap)
                    visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error loading image")
        }
    }

    private fun processImageWithOCR(uri: Uri) {
        // Show loading state
        binding.btnPick?.apply {
            text = "Processing..."
            isEnabled = false
        }

        lifecycleScope.launch {
            try {
                ocrResult = withContext(Dispatchers.IO) {
                    BillOcr.processImageWithOcr(requireContext(), uri)
                }

                ocrResult?.let { result ->
                    // Auto-fill amount if extracted
                    result.extractedAmount?.let { amount ->
                        binding.etAmount?.setText(amount.toString())
                    }

                    // Try to extract merchant name for description
                    val merchantName = BillOcr.extractMerchantName(result.fullText)
                    if (!merchantName.isNullOrEmpty() && binding.etDesc?.text.isNullOrEmpty()) {
                        binding.etDesc?.setText(merchantName)
                    }

                    // Try to extract and parse date
                    val extractedDate = BillOcr.extractDate(result.fullText)
                    extractedDate?.let { dateStr ->
                        // Try to parse the extracted date and update UI
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // Try different date formats
                                val parsedDate = parseExtractedDate(dateStr)
                                parsedDate?.let {
                                    selectedDate = it
                                    updateDateDisplay()
                                }
                            }
                        } catch (e: Exception) {
                            // Date parsing failed, keep current date
                        }
                    }

                    // Show confidence score
                    val confidence = (result.confidenceScore * 100).toInt()
                    showToast("OCR completed with $confidence% confidence")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showToast("OCR processing failed: ${e.message}")
            } finally {
                // Reset button state
                binding.btnPick?.apply {
                    text = "Pick Image"
                    isEnabled = true
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseExtractedDate(dateStr: String): LocalDate? {
        val patterns = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        )

        for (pattern in patterns) {
            try {
                return LocalDate.parse(dateStr, pattern)
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    private fun setupImagePicker() {
        binding.btnPick?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupDatePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updateDateDisplay()

            binding.tilDate?.setEndIconOnClickListener {
                showDatePickerDialog()
            }

            binding.etDate?.setOnClickListener {
                showDatePickerDialog()
            }
        } else {
            // Handle older Android versions
            setupDatePickerLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateDateDisplay() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        binding.etDate?.setText(selectedDate.format(formatter))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupDatePickerLegacy() {
        // Legacy date picker setup for older Android versions
        val calendar = Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        binding.etDate?.setText(dateFormat.format(calendar.time))

        binding.tilDate?.setEndIconOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    binding.etDate?.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupPaymentMethods() {
        val paymentMethods = arrayOf("CASH", "UPI", "CARD", "WALLET", "BANK", "CHEQUE")
        binding.actPayment?.setSimpleItems(paymentMethods)

        // Set default payment method
        binding.actPayment?.setText("CASH", false)
    }

    private fun setupCategories() {
        lifecycleScope.launch {
            try {
                val categories = withContext(Dispatchers.IO) {
                    SupabaseCashbook.getCategories()
                }

                if (categories.isNotEmpty()) {
                    binding.actCategory?.setSimpleItems(categories.toTypedArray())
                } else {
                    // Provide default categories if none found
                    val defaultCategories = getDefaultCategories()
                    binding.actCategory?.setSimpleItems(defaultCategories)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Use default categories on error
                val defaultCategories = getDefaultCategories()
                binding.actCategory?.setSimpleItems(defaultCategories)
                showToast("Using default categories")
            }
        }
    }

    private fun getDefaultCategories(): Array<String> {
        return if (billType == "OUT") {
            arrayOf("Food", "Transport", "Shopping", "Bills", "Entertainment", "Healthcare", "Other")
        } else {
            arrayOf("Sales", "Investment", "Salary", "Business", "Other")
        }
    }

    private fun setupSaveButton() {
        binding.btnSave?.setOnClickListener {
            saveBillEntry()
        }
    }

    private fun saveBillEntry() {
        // Get values
        val amountStr = binding.etAmount?.text?.toString()?.trim()
        val amount = amountStr?.toBigDecimalOrNull()
        val category = binding.actCategory?.text?.toString()?.trim()
        val payment = binding.actPayment?.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() } ?: "CASH"
        val date = binding.etDate?.text?.toString()?.trim()
        val desc = binding.etDesc?.text?.toString()?.trim()

        // Validation
        var hasError = false

        if (amount == null || amount <= BigDecimal.ZERO) {
            binding.tilAmount?.error = "Enter valid amount"
            hasError = true
        } else {
            binding.tilAmount?.error = null
        }

        if (date.isNullOrEmpty()) {
            binding.tilDate?.error = "Select date"
            hasError = true
        } else {
            binding.tilDate?.error = null
        }

        if (hasError) {
            return
        }

        // Show loading state
        binding.btnSave?.apply {
            isEnabled = false
            text = "Saving..."
        }

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    if (imageUri != null) {
                        // Save with bill image and OCR data
                        SupabaseCashbook.insertEntryWithBill(
                            amount = amount!!,
                            date = date!!,
                            type = billType,
                            paymentMethod = payment,
                            description = desc ?: "",
                            categoryName = category,
                            context = requireContext(),
                            imageUri = imageUri,
                            extractedText = ocrResult?.fullText,
                            extractedAmount = ocrResult?.extractedAmount,
                            confidenceScore = ocrResult?.confidenceScore
                        )
                    } else {
                        // Save without bill image
                        SupabaseCashbook.insertEntry(
                            amount = amount!!,
                            date = date!!,
                            type = billType,
                            paymentMethod = payment,
                            description = desc ?: "",
                            categoryName = category
                        )
                    }
                }

                if (success) {
                    showToast("Bill saved successfully!")
                    refreshBillsList()
                    dismiss()
                } else {
                    showToast("Failed to save bill")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error saving bill: ${e.message}")
            } finally {
                // Reset button state
                binding.btnSave?.apply {
                    isEnabled = true
                    text = "Save"
                }
            }
        }
    }

    private fun refreshBillsList() {
        try {
            (requireActivity() as? BillsActivity)?.let { activity ->
                val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                val fragmentManager = activity.supportFragmentManager

                // Try to find and refresh current fragment
                val currentFragment = fragmentManager.findFragmentByTag("f${viewPager.currentItem}")
                (currentFragment as? BillsListFragment)?.load()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fragment refresh failed, but bill was saved successfully
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_TAB_NAME = "tab_name"

        @JvmStatic
        fun newInstance(type: String, tabName: String = "") = AddBillBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_TYPE, type)
                putString(ARG_TAB_NAME, tabName)
            }
        }

        // Backward compatibility
        @JvmStatic
        fun newInstance(type: String) = newInstance(type, "")
    }
}