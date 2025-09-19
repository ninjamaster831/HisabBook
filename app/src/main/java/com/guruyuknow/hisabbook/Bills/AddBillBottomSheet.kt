package com.guruyuknow.hisabbook.Bills

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.databinding.BottomSheetAddBillBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AddBillBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBillBinding? = null
    private val binding get() = _binding!!
    private lateinit var type: String
    private var imageUri: Uri? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            requireContext().contentResolver.openInputStream(uri)?.use {
                val bmp = BitmapFactory.decodeStream(it)
                binding.imgPreview.setImageBitmap(bmp)
            }
            // OCR
            uiScope.launch {
                val text = BillOcr.readTextFromUri(requireContext(), uri)
                val amount = BillOcr.extractBestAmount(text)
                if (amount != null) binding.etAmount.setText(amount.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getString(ARG_TYPE) ?: "EXPENSE"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddBillBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnPick.setOnClickListener { pickImage.launch("image/*") }

        // Date
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var selectedDate = LocalDate.now()
        binding.etDate.setText(selectedDate.format(df))
        binding.tilDate.setEndIconOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(requireContext(),
                { _, y, m, d ->
                    selectedDate = LocalDate.of(y, m + 1, d)
                    binding.etDate.setText(selectedDate.format(df))
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Payment methods
        binding.actPayment.setSimpleItems(arrayOf("CASH","UPI","CARD","WALLET","BANK"))

        // Categories from Supabase
        uiScope.launch {
            val cats = SupabaseCashbook.getCategories() // returns List<String> or pairs
            binding.actCategory.setSimpleItems(cats.toTypedArray())
        }

        binding.btnSave.setOnClickListener {
            val amount = binding.etAmount.text?.toString()?.toBigDecimalOrNull()
            val category = binding.actCategory.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
            val payment = (binding.actPayment.text?.toString()?.trim()).takeIf { !it.isNullOrEmpty() } ?: "CASH"
            val date = binding.etDate.text?.toString()?.trim()
            val desc = binding.etDesc.text?.toString()?.trim()

            if (amount == null || date.isNullOrEmpty()) {
                binding.tilAmount.error = if (amount == null) "Enter amount" else null
                binding.tilDate.error = if (date.isNullOrEmpty()) "Pick date" else null
                return@setOnClickListener
            }

            uiScope.launch {
                val ok = SupabaseCashbook.insertEntry(
                    amount = amount,
                    date = date,
                    type = type,
                    paymentMethod = payment,
                    description = desc ?: "",
                    categoryName = category // nullable
                )
                if (ok) {
                    // refresh current list
                    (requireActivity() as BillsActivity).let { act ->
                        val pager = act.findViewById<androidx.viewpager2.widget.ViewPager2>(com.guruyuknow.hisabbook.R.id.viewPager)
                        val frag = act.supportFragmentManager.findFragmentByTag("f${pager.currentItem}")
                        // Simpler: just dismiss; user can pull-to-refresh
                    }
                    dismiss()
                }
            }
        }
    }

    companion object {
        private const val ARG_TYPE = "type"
        fun newInstance(type: String) = AddBillBottomSheet().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }
}
