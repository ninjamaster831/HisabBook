package com.guruyuknow.hisabbook.group

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.MainActivity
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

class GroupChatFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()

    private var groupId: Long = 0
    private var groupNameArg: String = "Group Chat"
    private lateinit var et: EditText
    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var btnSend: ImageButton
    private lateinit var btnAttach: ImageButton
    private lateinit var btnCamera: ImageButton
    private lateinit var btnEmoji: ImageButton
    private lateinit var fabScrollToBottom: FloatingActionButton
    private lateinit var tvGroupName: TextView
    private lateinit var tvMemberCount: TextView
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var layoutManager: LinearLayoutManager

    // Preview strip
    private lateinit var rvPreview: RecyclerView
    private lateinit var previewCard: MaterialCardView
    private lateinit var attachCard: MaterialCardView
    private lateinit var cameraCard: MaterialCardView
    private lateinit var inputCard: MaterialCardView
    private lateinit var previewAdapter: PreviewImageAdapter
    private val pendingUris = mutableListOf<Uri>()
    private lateinit var ivGroupAvatar: ShapeableImageView
    private var isAtBottom = true
    private var isFirstLoad = true
    private var shouldScrollToBottom = false
    private var lastMessageCount = 0
    // fields
    private lateinit var sendCard: MaterialCardView

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_group_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)
        setupRecyclerView()
        setupPreviewStrip(view)
        setupListeners()
        observeData()
        setupRealtimeUpdates()
        refreshSendButtonState()
        viewModel.loadGroupDetails(groupId)
        viewModel.loadMessages(groupId)

        // Hide bottom navigation while chat is visible
        (activity as? MainActivity)?.hideBottomNav()

        // Apply system insets
        applyInsets(view)
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.hideBottomNav()
        // Refresh messages when returning to chat
        viewModel.loadMessages(groupId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.showBottomNav()
    }

    private fun setupViews(view: View) {
        appBar = view.findViewById(R.id.appBarLayout)
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
        attachCard = view.findViewById(R.id.attachCard)
        cameraCard = view.findViewById(R.id.cameraCard)
        previewCard = view.findViewById(R.id.previewCard)
        inputCard = view.findViewById(R.id.inputCard)
        ivGroupAvatar = view.findViewById(R.id.ivGroupAvatar)
        tvGroupName.text = groupNameArg
        tvMemberCount.text = ""
// in setupViews(view)
        sendCard = view.findViewById(R.id.sendCard)

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            myUserIdProvider = { SupabaseManager.client.auth.currentUserOrNull()?.id }
        )
        layoutManager = LinearLayoutManager(requireContext())
        // Set stack from end to show latest messages at bottom
        layoutManager.stackFromEnd = true
        rv.layoutManager = layoutManager
        rv.adapter = adapter

        // Add proper spacing between messages
        rv.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.bottom = dp(2) // 2dp spacing between messages
            }
        })

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateScrollState()
            }
        })

        // Register adapter data observer to handle auto-scroll
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                handleNewMessages(positionStart, itemCount)
            }
        })
    }

    private fun updateScrollState() {
        val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
        val itemCount = adapter.itemCount
        isAtBottom = lastVisible >= itemCount - 1

        // Show/hide FAB based on scroll position
        fabScrollToBottom.isVisible = !isAtBottom && itemCount > 0
    }

    private fun handleNewMessages(positionStart: Int, itemCount: Int) {
        val totalItems = adapter.itemCount

        // First load - always scroll to bottom
        if (isFirstLoad && totalItems > 0) {
            isFirstLoad = false
            rv.post {
                rv.scrollToPosition(totalItems - 1)
            }
            return
        }

        // New message arrived
        if (totalItems > lastMessageCount) {
            // If user is at bottom or sent the message, scroll to bottom
            if (isAtBottom || shouldScrollToBottom) {
                rv.post {
                    rv.smoothScrollToPosition(totalItems - 1)
                }
                shouldScrollToBottom = false
            }
        }

        lastMessageCount = totalItems
    }

    private fun setupPreviewStrip(view: View) {
        rvPreview = view.findViewById(R.id.rvPreview)
        rvPreview.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        previewAdapter = PreviewImageAdapter(pendingUris) { index ->
            pendingUris.removeAt(index)
            previewAdapter.notifyItemRemoved(index)
            previewCard.isVisible = pendingUris.isNotEmpty()
            refreshSendButtonState()
        }
        rvPreview.adapter = previewAdapter
        previewCard.isVisible = false
    }

    // Add this to your setupListeners() method in GroupChatFragment

    private fun setupListeners() {
        et.addTextChangedListener {
            refreshSendButtonState()
        }

        btnSend.setOnClickListener { sendMessage() }

        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        btnAttach.setOnClickListener { pickImage.launch("image/*") }
        btnCamera.setOnClickListener { requestCameraPerm.launch(Manifest.permission.CAMERA) }
        btnEmoji.setOnClickListener { showToast("Emoji picker coming soon!") }


        fabScrollToBottom.setOnClickListener {
            scrollToBottom(smooth = true)
        }

        // NEW: Click listener for opening group details
        // Click on the title area to open group details
        tvGroupName.setOnClickListener {
            openGroupDetails()
        }

        // Also allow clicking on member count
        tvMemberCount.setOnClickListener {
            openGroupDetails()
        }

        // Make the entire toolbar clickable (optional)
        view?.findViewById<LinearLayout>(R.id.titleStack)?.setOnClickListener {
            openGroupDetails()
        }
    }

    // NEW: Method to open group details fragment
    private fun openGroupDetails() {
        val fragment = GroupDetailsFragment.newInstance(groupId)
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun scrollToBottom(smooth: Boolean = false) {
        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            if (smooth) {
                rv.smoothScrollToPosition(itemCount - 1)
            } else {
                rv.scrollToPosition(itemCount - 1)
            }
        }
    }

    private fun observeData() {
        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            group?.let {
                // Set group name
                tvGroupName.text = it.name ?: "Group Chat"

                // Load group avatar image if available
                val groupImageUrl = it.imageUrl
                if (!groupImageUrl.isNullOrBlank()) {
                    Glide.with(requireContext())
                        .load(groupImageUrl)
                        .centerCrop()  // Ensure the image is cropped to fit the circular shape
                        .into(ivGroupAvatar)
                } else {
                    // Set default avatar if no group image is available
                    ivGroupAvatar.setImageResource(R.drawable.ic_group_chat)
                }

            }
        }


        viewModel.groupMembers.observe(viewLifecycleOwner) { members ->
            tvMemberCount.text = formatMembersSubtitle(members.map { it.userName ?: "" })
        }

        viewModel.messages.observe(viewLifecycleOwner) { msgs ->
            val oldCount = adapter.itemCount
            adapter.submitList(msgs) {
                // Callback after list is committed
                if (oldCount == 0 && msgs.isNotEmpty()) {
                    // First load
                    rv.post {
                        rv.scrollToPosition(msgs.size - 1)
                    }
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) {
            it?.let {
                showToast(it)
                viewModel.clearError()
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) {
            it?.let {
                showToast(it)
                viewModel.clearSuccess()
            }
        }
    }

    private fun setupRealtimeUpdates() {
        // Poll for new messages every 5 seconds
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(5000) // 5 seconds
                    viewModel.loadMessages(groupId)
                }
            }
        }
    }

    // -------------------- Activity Result Contracts --------------------
    private fun initContracts() {
        pickImage =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    pendingUris.add(uri)
                    previewAdapter.notifyItemInserted(pendingUris.lastIndex)
                    previewCard.isVisible = true
                    refreshSendButtonState()
                }
            }

        requestCameraPerm =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) openCamera() else showToast("Camera permission denied")
            }

        capturePhoto =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
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
        val dir = File(requireContext().cacheDir, "images")
        dir.mkdirs()
        val photo = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(
            requireContext(),
            requireContext().packageName + FILE_AUTH,
            photo
        )
        capturePhoto.launch(cameraUri)
    }

    // -------------------- Compose & Send --------------------
    private fun refreshSendButtonState() {
        val hasText = !et.text.isNullOrBlank()
        val hasImages = pendingUris.isNotEmpty()
        sendCard.isVisible = hasText || hasImages
        // Show send button if there's text or images
        btnSend.parent?.let { parent ->
            if (parent is View) {
                parent.isVisible = hasText || hasImages
            }
        }

        // Hide attach/camera when typing or has images
        attachCard.isVisible = !hasText && !hasImages
        cameraCard.isVisible = !hasText && !hasImages
    }

    private fun sendMessage() {
        val text = et.text?.toString()?.trim().orEmpty()
        val hasText = text.isNotEmpty()
        val hasImages = pendingUris.isNotEmpty()

        // Don't send if nothing to send
        if (!hasText && !hasImages) return

        // Mark that we should scroll to bottom after sending
        shouldScrollToBottom = true

        // Send text message
        if (hasText) {
            viewModel.sendMessage(groupId, text)
            et.setText("")
            et.clearFocus()
        }

        // Send images
        if (hasImages) {
            val uid = SupabaseManager.client.auth.currentUserOrNull()?.id
            if (uid == null) {
                showToast("You're not logged in.")
                shouldScrollToBottom = false
                return
            }

            btnSend.isEnabled = false
            val imagesToSend = pendingUris.toList()

            lifecycleScope.launch {
                for (uri in imagesToSend) {
                    val res = GroupExpenseRepository()
                        .sendMediaMessage(requireContext(), groupId, uid, uri, "image", null)
                    if (res.isFailure) {
                        showToast("Upload failed: ${res.exceptionOrNull()?.message}")
                    }
                }

                // Clear preview after sending
                pendingUris.clear()
                previewAdapter.notifyDataSetChanged()
                previewCard.isVisible = false
                btnSend.isEnabled = true
                refreshSendButtonState()

                // Reload messages after upload
                viewModel.loadMessages(groupId)
            }
        }

        hideKeyboard()
        refreshSendButtonState()

        // Scroll to bottom after a short delay to ensure message is added
        rv.postDelayed({
            if (shouldScrollToBottom) {
                scrollToBottom(smooth = true)
            }
        }, 100)
    }

    // -------------------- Insets & Utils --------------------
    private fun applyInsets(root: View) {
        val sysBarsTypes = WindowInsetsCompat.Type.systemBars()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(sysBarsTypes)

            // Top inset -> move app bar below status bar
            appBar.setPadding(
                appBar.paddingLeft,
                sys.top,
                appBar.paddingRight,
                appBar.paddingBottom
            )

            // Bottom inset -> lift input/preview/fab above navigation bar
            fun liftCard(card: MaterialCardView, baseBottomDp: Int) {
                (card.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                    val base = dp(baseBottomDp)
                    lp.bottomMargin = base + sys.bottom
                    card.layoutParams = lp
                }
            }

            liftCard(inputCard, 20)
            liftCard(previewCard, 110)

            (fabScrollToBottom.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                val base = dp(130)
                lp.bottomMargin = base + sys.bottom
                lp.marginEnd = dp(24) + sys.right
                fabScrollToBottom.layoutParams = lp
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken, 0)
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT)
            .show()
    }

    private fun formatMembersSubtitle(names: List<String>): String {
        val cleaned = names.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return ""
        if (cleaned.size <= 3) return cleaned.joinToString(", ")
        return "${cleaned.take(3).joinToString(", ")} +${cleaned.size - 3} more"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}