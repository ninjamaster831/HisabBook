package com.guruyuknow.hisabbook.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth

class GroupChatFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()
    private var groupId: Long = 0

    private lateinit var rv: RecyclerView
    private lateinit var et: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var adapter: ChatMessageAdapter

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        fun newInstance(groupId: Long) = GroupChatFragment().apply {
            arguments = Bundle().apply { putLong(ARG_GROUP_ID, groupId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(ARG_GROUP_ID) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_group_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rv = view.findViewById(R.id.rvChat)
        et = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)

        adapter = ChatMessageAdapter {
            SupabaseManager.client.auth.currentUserOrNull()?.id
        }
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rv.adapter = adapter

        viewModel.messages.observe(viewLifecycleOwner) { msgs ->
            adapter.submitList(msgs)
            rv.scrollToPosition((msgs.size - 1).coerceAtLeast(0))
        }

        btnSend.setOnClickListener {
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(groupId, text)
                et.setText("")
            }
        }

        viewModel.loadMessages(groupId)
    }
}
