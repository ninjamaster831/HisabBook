package com.guruyuknow.hisabbook.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.guruyuknow.hisabbook.R

class GroupCodeDialog : DialogFragment() {

    private lateinit var tvGroupName: TextView
    private lateinit var tvGroupCode: TextView
    private lateinit var btnCopyCode: MaterialButton
    private lateinit var btnDone: MaterialButton

    companion object {
        private const val ARG_GROUP_NAME = "group_name"
        private const val ARG_GROUP_CODE = "group_code"

        fun newInstance(groupName: String, groupCode: String): GroupCodeDialog {
            return GroupCodeDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_NAME, groupName)
                    putString(ARG_GROUP_CODE, groupCode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make dialog fill most of the screen
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_group_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvGroupName = view.findViewById(R.id.tvGroupName)
        tvGroupCode = view.findViewById(R.id.tvGroupCode)
        btnCopyCode = view.findViewById(R.id.btnCopyCode)
        btnDone = view.findViewById(R.id.btnDone)

        val groupName = arguments?.getString(ARG_GROUP_NAME) ?: "Your Group"
        val groupCode = arguments?.getString(ARG_GROUP_CODE) ?: ""

        tvGroupName.text = groupName
        tvGroupCode.text = groupCode

        btnCopyCode.setOnClickListener {
            copyToClipboard(groupCode)
        }

        btnDone.setOnClickListener {
            dismiss()
        }
    }

    private fun copyToClipboard(code: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Group Code", code)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            requireContext(),
            "Code copied to clipboard!",
            Toast.LENGTH_SHORT
        ).show()

        // Optional: animate the button
        btnCopyCode.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                btnCopyCode.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    override fun onStart() {
        super.onStart()
        // Make dialog full width
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}