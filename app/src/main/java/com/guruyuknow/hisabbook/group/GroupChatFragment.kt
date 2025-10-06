package com.guruyuknow.hisabbook.group

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class GroupChatFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()

    private var groupId: Long = 0
    private var groupNameArg: String = "Group Chat"

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var et: TextInputEditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnAttach: ImageButton
    private lateinit var btnCamera: ImageButton
    private lateinit var btnEmoji: ImageButton
    private lateinit var fabScrollToBottom: FloatingActionButton
    private lateinit var tvGroupName: TextView
    private lateinit var tvMemberCount: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var adapter: ChatMessageAdapter

    // Preview strip
    private lateinit var rvPreview: RecyclerView
    private lateinit var previewCard: MaterialCardView
    private lateinit var attachCard: MaterialCardView
    private lateinit var cameraCard: MaterialCardView
    private lateinit var previewAdapter: PreviewImageAdapter
    private val pendingUris = mutableListOf<Uri>()

    private var isAtBottom = false

    // Activity Result launchers
    private lateinit var pickImage: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var requestCameraPerm: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var capturePhoto: androidx.activity.result.ActivityResultLauncher<Uri>
    private var cameraUri: Uri? = null

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_GROUP_NAME = "group_name"
        private const val FILE_AUTH = ".fileprovider"

        fun newInstance(groupId: Long, groupName: String = "Group Chat") =
            GroupChatFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_GROUP_ID, groupId)
                    putString(ARG_GROUP_NAME, groupName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(ARG_GROUP_ID) ?: 0
        groupNameArg = arguments?.getString(ARG_GROUP_NAME) ?: "Group Chat"
        initContracts()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_group_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)
        setupRecyclerView()
        setupPreviewStrip(view)
        setupListeners()
        observeData()

        viewModel.loadGroupDetails(groupId)
        viewModel.loadMessages(groupId)
    }

    private fun setupViews(view: View) {
        toolbar = view.findViewById(R.id.chatToolbar)
        rv = view.findViewById(R.id.rvChat)
        et = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        btnAttach = view.findViewById(R.id.btnAttach)
        btnCamera = view.findViewById(R.id.btnCamera)
        btnEmoji = view.findViewById(R.id.btnEmoji)
        fabScrollToBottom = view.findViewById(R.id.fabScrollToBottom)
        tvGroupName = view.findViewById(R.id.tvGroupName)
        tvMemberCount = view.findViewById(R.id.tvMemberCount)
        menuButton = view.findViewById(R.id.menuButton)
        attachCard = view.findViewById(R.id.attachCard)
        cameraCard = view.findViewById(R.id.cameraCard)
        previewCard = view.findViewById(R.id.previewCard)

        tvGroupName.text = groupNameArg
        tvMemberCount.text = ""

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            myUserIdProvider = { SupabaseManager.client.auth.currentUserOrNull()?.id }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        rv.layoutManager = layoutManager
        rv.adapter = adapter

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val itemCount = adapter.itemCount
                isAtBottom = lastVisible >= itemCount - 1
                fabScrollToBottom.isVisible = !isAtBottom && itemCount > 0
            }
        })
    }

    private fun setupPreviewStrip(view: View) {
        rvPreview = view.findViewById(R.id.rvPreview)
        rvPreview.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        previewAdapter = PreviewImageAdapter(pendingUris) { index ->
            pendingUris.removeAt(index)
            previewAdapter.notifyItemRemoved(index)
            previewCard.isVisible = pendingUris.isNotEmpty()
            refreshSendButtonState()
        }
        rvPreview.adapter = previewAdapter
        previewCard.isVisible = false
    }

    private fun setupListeners() {
        et.addTextChangedListener { refreshSendButtonState() }
        btnSend.setOnClickListener { sendMessage() }

        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        btnAttach.setOnClickListener { pickImage.launch("image/*") }
        btnCamera.setOnClickListener { requestCameraPerm.launch(Manifest.permission.CAMERA) }
        btnEmoji.setOnClickListener { showToast("Emoji picker coming soon!") }

        menuButton.setOnClickListener {
            showToast("Group menu coming soon!")
        }

        fabScrollToBottom.setOnClickListener {
            val last = (adapter.itemCount - 1).coerceAtLeast(0)
            rv.smoothScrollToPosition(last)
        }
    }

    private fun observeData() {
        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            tvGroupName.text = group?.name ?: groupNameArg
        }
        viewModel.groupMembers.observe(viewLifecycleOwner) { members ->
            tvMemberCount.text = formatMembersSubtitle(members.map { it.userName ?: "" })
        }
        viewModel.messages.observe(viewLifecycleOwner) { msgs ->
            adapter.submitList(msgs)
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { it?.let { showToast(it); viewModel.clearError() } }
        viewModel.successMessage.observe(viewLifecycleOwner) { it?.let { showToast(it); viewModel.clearSuccess() } }
    }

    // -------------------- Activity Result Contracts --------------------

    private fun initContracts() {
        pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                pendingUris.add(uri)
                previewAdapter.notifyItemInserted(pendingUris.lastIndex)
                previewCard.isVisible = true
                refreshSendButtonState()
            }
        }
        requestCameraPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera() else showToast("Camera permission denied")
        }
        capturePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraUri
            if (success && uri != null) {
                pendingUris.add(uri)
                previewAdapter.notifyItemInserted(pendingUris.lastIndex)
                previewCard.isVisible = true
                refreshSendButtonState()
            }
        }
    }

    private fun openCamera() {
        val file = File(requireContext().cacheDir, "images")
        file.mkdirs()
        val photo = File(file, "cam_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + FILE_AUTH, photo)
        capturePhoto.launch(cameraUri)
    }

    // -------------------- Compose & Send --------------------

    private fun refreshSendButtonState() {
        val hasText = !et.text.isNullOrBlank()
        val hasImages = pendingUris.isNotEmpty()

        btnSend.parent?.let { parent ->
            if (parent is MaterialCardView) {
                parent.isVisible = hasText || hasImages
            }
        }
        attachCard.isVisible = !hasText && !hasImages
        cameraCard.isVisible = !hasText && !hasImages
    }

    private fun sendMessage() {
        val text = et.text?.toString()?.trim().orEmpty()

        // 1) send text first (if any)
        if (text.isNotEmpty()) {
            viewModel.sendMessage(groupId, text)
            et.setText("")
        }

        // 2) send all pending images
        if (pendingUris.isNotEmpty()) {
            val uid = SupabaseManager.client.auth.currentUserOrNull()?.id
            if (uid == null) {
                showToast("You're not logged in.")
                return
            }

            btnSend.isEnabled = false
            CoroutineScope(Dispatchers.Main).launch {
                for (uri in pendingUris.toList()) {
                    val res = GroupExpenseRepository()
                        .sendMediaMessage(requireContext(), groupId, uid, uri, "image", null)
                    if (res.isFailure) {
                        showToast("Upload failed: ${res.exceptionOrNull()?.message}")
                    }
                }
                // clear preview
                pendingUris.clear()
                previewAdapter.notifyDataSetChanged()
                previewCard.isVisible = false
                btnSend.isEnabled = true
                refreshSendButtonState()

                // refresh messages
                viewModel.loadMessages(groupId)
            }
        }

        hideKeyboard()
        refreshSendButtonState()
    }

    // -------------------- Utils --------------------

    private fun formatMembersSubtitle(names: List<String>): String {
        val cleaned = names.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return ""
        if (cleaned.size <= 3) return cleaned.joinToString(", ")
        return "${cleaned.take(3).joinToString(", ")} +${cleaned.size - 3} more"
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken, 0)
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // If you hide bottom nav here, keep your MainActivity methods. Otherwise remove.
        // (activity as? MainActivity)?.hideBottomNav()
    }

    override fun onPause() {
        super.onPause()
        // (activity as? MainActivity)?.showBottomNav()
    }
}