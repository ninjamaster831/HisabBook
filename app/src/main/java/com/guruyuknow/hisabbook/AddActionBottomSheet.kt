package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.databinding.BottomSheetAddActionBinding
import com.guruyuknow.hisabbook.group.CreateGroupBottomSheet

class AddActionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddActionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardEvent.setOnClickListener {
            dismiss()
            CreateEventBottomSheet().show(parentFragmentManager, "create_event")
        }

        binding.cardGroup.setOnClickListener {
            dismiss()
            CreateGroupBottomSheet().show(parentFragmentManager, "create_group")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}