package com.guruyuknow.hisabbook.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.R

class JoinGroupBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: GroupExpenseViewModel by activityViewModels()

    private lateinit var codeInput: TextInputEditText
    private lateinit var nameInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var btnJoin: MaterialButton
    private lateinit var progress: CircularProgressIndicator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_join_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        codeInput = view.findViewById(R.id.etJoinCode)
        nameInput = view.findViewById(R.id.etDisplayName)
        phoneInput = view.findViewById(R.id.etPhoneNumber)
        btnJoin = view.findViewById(R.id.btnJoin)
        progress = view.findViewById(R.id.progressIndicator)

        btnJoin.setOnClickListener {
            val code = codeInput.text?.toString().orEmpty().trim().uppercase()
            val name = nameInput.text?.toString().orEmpty().trim().ifEmpty { "User" }

            if (code.length != 6) {
                codeInput.error = "Enter a valid 6-character code"
                return@setOnClickListener
            }

            codeInput.error = null

            // ✅ OPTION 1: Use authenticated user ID (recommended)
            viewModel.joinGroupByCode(code, name)

            // ✅ OPTION 2: Keep phone-based if you want to allow non-auth users
            // Uncomment below and keep phone input field:
            // val phone = phoneInput.text?.toString().orEmpty().trim()
            // if (phone.isEmpty()) {
            //     phoneInput.error = "Enter phone number"
            //     return@setOnClickListener
            // }
            // viewModel.joinGroupByCodeWithPhone(code, name, phone)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress.visibility = if (loading == true) View.VISIBLE else View.GONE
            btnJoin.isEnabled = loading != true
        }

        viewModel.joinResult.observe(viewLifecycleOwner) { res ->
            if (res == null) return@observe
            if (res.isSuccess) {
                Toast.makeText(requireContext(), "Joined group successfully!", Toast.LENGTH_SHORT).show()

                // ✅ Explicitly reload and reapply filter
                viewModel.loadAllGroups()

                // Clear the result to avoid re-triggering
                viewModel.clearJoinResult()

                dismiss()
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Failed to join"
                codeInput.error = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}