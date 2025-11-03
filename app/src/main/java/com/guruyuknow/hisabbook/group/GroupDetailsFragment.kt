package com.guruyuknow.hisabbook.group

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlin.math.abs

class GroupDetailsFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()

    private var groupId: Long = 0
    private var isCurrentUserAdmin = false

    private lateinit var appBarLayout: AppBarLayout
    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivGroupImage: ImageView
    private lateinit var btnChangeGroupImage: FloatingActionButton
    private lateinit var tvGroupName: TextView
    private lateinit var tvGroupDescription: TextView
    private lateinit var tvNoDescription: TextView
    private lateinit var tvCreatedBy: TextView
    private lateinit var tvCreatedDate: TextView
    private lateinit var tvMemberCount: TextView
    private lateinit var rvMembers: RecyclerView
    private lateinit var cardGroupSettings: MaterialCardView
    private lateinit var btnEditGroupInfo: MaterialButton
    private lateinit var switchAdminOnly: MaterialSwitch
    private lateinit var btnAddParticipant: FloatingActionButton
    private lateinit var searchMembersLayout: TextInputLayout
    private lateinit var etSearchMembers: TextInputEditText
    private lateinit var btnMediaDocs: MaterialCardView
    private lateinit var btnExitGroup: MaterialButton
    private lateinit var btnDeleteGroup: MaterialButton
    private lateinit var memberAdapter: GroupMemberAdapter

    private var selectedImageUri: Uri? = null
    private var allMembers = listOf<GroupMemberWithUser>()

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(groupId: Long) = GroupDetailsFragment().apply {
            arguments = Bundle().apply { putLong(ARG_GROUP_ID, groupId) }
        }
    }

    // Gallery
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadImageToView(uri)
                uploadGroupImage(uri)
            }
        }
    }

    // Camera
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadImageToView(uri)
                uploadGroupImage(uri)
            }
        }
    }

    // Permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openCamera() else showToast("Camera permission required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(ARG_GROUP_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_group_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)
        setupRecyclerView()
        setupListeners()
        observeData()
        applyInsets(view)
        setupStatusBar()

        viewModel.loadGroupDetails(groupId)
    }

    private fun setupStatusBar() {
        // Make status bar transparent to show gradient behind it
        activity?.window?.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            decorView.systemUiVisibility =
                decorView.systemUiVisibility and
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun setupViews(view: View) {
        appBarLayout = view.findViewById(R.id.appBarLayout)
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar)
        toolbar = view.findViewById(R.id.toolbar)
        ivGroupImage = view.findViewById(R.id.ivGroupImage)
        btnChangeGroupImage = view.findViewById(R.id.btnChangeGroupImage)
        tvGroupName = view.findViewById(R.id.tvGroupName)
        tvGroupDescription = view.findViewById(R.id.tvGroupDescription)
        tvNoDescription = view.findViewById(R.id.tvNoDescription)
        tvCreatedBy = view.findViewById(R.id.tvCreatedBy)
        tvCreatedDate = view.findViewById(R.id.tvCreatedDate)
        tvMemberCount = view.findViewById(R.id.tvMemberCount)
        rvMembers = view.findViewById(R.id.rvMembers)
        cardGroupSettings = view.findViewById(R.id.cardGroupSettings)
        btnEditGroupInfo = view.findViewById(R.id.btnEditGroupInfo)
        switchAdminOnly = view.findViewById(R.id.switchAdminOnly)
        btnAddParticipant = view.findViewById(R.id.btnAddParticipant)
        searchMembersLayout = view.findViewById(R.id.searchMembersLayout)
        etSearchMembers = view.findViewById(R.id.etSearchMembers)
        btnMediaDocs = view.findViewById(R.id.btnMediaDocs)
        btnExitGroup = view.findViewById(R.id.btnExitGroup)
        btnDeleteGroup = view.findViewById(R.id.btnDeleteGroup)

        // Navigation/back
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Setup collapsing toolbar behavior
        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()

            // When collapsed (percentage close to 1), show group name in toolbar
            // When expanded (percentage close to 0), hide toolbar title
            if (percentage > 0.9f) {
                collapsingToolbar.title = tvGroupName.text
            } else {
                collapsingToolbar.title = ""
            }
        })

        // Toolbar menu actions
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    showToast("Search coming soon")
                    true
                }
                R.id.action_edit_group -> {
                    showEditGroupInfoDialog()
                    true
                }
                R.id.action_share -> {
                    showToast("Share coming soon")
                    true
                }
                R.id.action_mute -> {
                    showToast("Muted")
                    true
                }
                R.id.action_report -> {
                    showToast("Reported")
                    true
                }
                else -> false
            }
        }
        reduceHeaderGap()
    }
    private fun reduceHeaderGap() {
        // headerContainer exists in your XML (inside CollapsingToolbarLayout)
        val header = view?.findViewById<View>(R.id.headerContainer)
        if (header != null) {
            // Remove bottom padding (was adding extra green space)
            header.setPadding(
                header.paddingLeft,
                header.paddingTop,
                header.paddingRight,
                0 // bottom
            )
        }

        // Reduce the top margin above "ram" (group name)
        (tvGroupName.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.topMargin = 6.dp // was ~12dp
            tvGroupName.layoutParams = lp
        }
    }

    private fun removeScrollTopPadding() {
        val scroll = view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll) ?: return

        // The direct child is your white LinearLayout; trim its top padding.
        val content = scroll.getChildAt(0)
        content?.let {
            it.setPadding(
                it.paddingLeft,
                0,                // remove top padding completely
                it.paddingRight,
                (it.paddingBottom).coerceAtMost(16.dp) // keep bottom modest
            )
        }
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun setupRecyclerView() {
        memberAdapter = GroupMemberAdapter(
            onMemberClick = { member -> if (isCurrentUserAdmin) showMemberOptionsDialog(member) },
            onRemoveMember = { member -> if (isCurrentUserAdmin) showRemoveMemberDialog(member) },
            onMakeAdmin = { member -> if (isCurrentUserAdmin) makeAdmin(member) },
            currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id,
            isCurrentUserAdmin = isCurrentUserAdmin
        )

        rvMembers.layoutManager = LinearLayoutManager(requireContext())
        rvMembers.adapter = memberAdapter
    }

    private fun setupListeners() {
        btnChangeGroupImage.setOnClickListener {
            Log.d("GroupDetailsFragment", "Change photo button clicked")
            showImagePickerDialog()
        }

        ivGroupImage.setOnClickListener {
            viewModel.currentGroup.value?.imageUrl?.let { showFullScreenImage(it) }
        }
        btnEditGroupInfo.setOnClickListener { showEditGroupInfoDialog() }

        switchAdminOnly.setOnCheckedChangeListener { _, isChecked ->
            updateAdminOnlySettings(isChecked)
        }

        btnAddParticipant.setOnClickListener { showAddParticipantDialog() }

        etSearchMembers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMembers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnMediaDocs.setOnClickListener { showToast("Media & Files feature coming soon!") }
        btnExitGroup.setOnClickListener { showExitGroupDialog() }
        btnDeleteGroup.setOnClickListener { showDeleteGroupDialog() }
    }

    private fun observeData() {
        // Inside the observeData method
        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            group?.let {
                // Set group name
                tvGroupName.text = it.name ?: ""
                tvCreatedDate.text = formatDate(it.createdAt)

                // Description handling
                if (!it.description.isNullOrBlank()) {
                    tvGroupDescription.text = it.description
                    tvGroupDescription.visibility = View.VISIBLE
                    tvNoDescription.visibility = View.GONE
                } else {
                    tvGroupDescription.visibility = View.GONE
                    tvNoDescription.visibility = View.VISIBLE
                }

                // Image handling
                val groupImageUrl = it.imageUrl
                if (!groupImageUrl.isNullOrBlank()) {
                    // Use Glide to load the image into ivGroupImage
                    Glide.with(requireContext())
                        .load(groupImageUrl)  // Group image URL
                        .centerCrop()          // Crop to center
                        .into(ivGroupImage)    // Load the image into the ImageView
                } else {
                    ivGroupImage.setImageResource(R.drawable.ic_group_chat)  // Default image
                }

                switchAdminOnly.isChecked = it.adminOnly
            }
        }


        viewModel.groupMembers.observe(viewLifecycleOwner) { members ->
            allMembers = members.map { m ->
                GroupMemberWithUser(
                    userId = m.userId,
                    userName = m.userName ?: m.userEmail ?: "Unknown User",
                    userEmail = m.userEmail,
                    isAdmin = m.isAdmin,
                    avatarUrl = m.avatarUrl,
                    joinedAt = m.joinedAt
                )
            }

            if (allMembers.isEmpty()) {
                tvMemberCount.text = "0 members"
                tvCreatedBy.text = "Created by Unknown"
                memberAdapter.submitList(emptyList())
                return@observe
            }

            val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
            isCurrentUserAdmin = allMembers.any { it.userId == currentUserId && it.isAdmin }
            memberAdapter.updateAdminStatus(isCurrentUserAdmin)

            if (isCurrentUserAdmin) {
                cardGroupSettings.visibility = View.VISIBLE
                btnChangeGroupImage.visibility = View.VISIBLE
                btnEditGroupInfo.visibility = View.VISIBLE
                btnAddParticipant.visibility = View.VISIBLE
                searchMembersLayout.visibility = if (allMembers.size > 5) View.VISIBLE else View.GONE
            } else {
                cardGroupSettings.visibility = View.GONE
                btnChangeGroupImage.visibility = View.GONE
                btnEditGroupInfo.visibility = View.GONE
                btnAddParticipant.visibility = View.GONE
                searchMembersLayout.visibility = View.GONE
            }

            tvMemberCount.text = "${allMembers.size} members"
            memberAdapter.submitList(allMembers)

            val creator = allMembers.firstOrNull { it.isAdmin }
            tvCreatedBy.text = "Created by ${creator?.userName ?: (allMembers.firstOrNull()?.userName ?: "Unknown")}"
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let { showToast(it); viewModel.clearError() }
        }
    }

    private fun filterMembers(query: String) {
        if (query.isBlank()) {
            memberAdapter.submitList(allMembers)
            return
        }
        val filtered = allMembers.filter { m ->
            m.userName.contains(query, ignoreCase = true) ||
                    (m.userEmail?.contains(query, ignoreCase = true) == true)
        }
        memberAdapter.submitList(filtered)
    }

    private fun showImagePickerDialog() {
        val hasImage = (selectedImageUri != null || viewModel.currentGroup.value?.imageUrl != null)
        val options = if (hasImage) {
            arrayOf("Take Photo", "Choose from Gallery", "View Full Size", "Remove Photo")
        } else {
            arrayOf("Take Photo", "Choose from Gallery")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Group Photo")
            .setItems(options) { _, which ->
                when {
                    which == 0 -> checkCameraPermissionAndOpen()
                    which == 1 -> openGallery()
                    which == 2 && hasImage -> viewModel.currentGroup.value?.imageUrl?.let { showFullScreenImage(it) }
                    which == 3 && hasImage -> removeGroupPhoto()
                }
            }.show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Create a ContextThemeWrapper using the custom style
        val themedContext = ContextThemeWrapper(requireContext(), R.style.CustomPopupMenu)
        // Use the themed context to inflate the menu
        inflater.inflate(R.menu.menu_group_details, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }




    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> openCamera()
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun showFullScreenImage(imageUrl: String) {
        // TODO: Implement full-screen viewer
        showToast("Opening full size image…")
    }

    private fun removeGroupPhoto() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Group Photo")
            .setMessage("Are you sure you want to remove the group photo?")
            .setPositiveButton("Remove") { _, _ ->
                ivGroupImage.setImageResource(R.drawable.ic_group_chat)
                ivGroupImage.setPadding(35, 35, 35, 35)
                selectedImageUri = null
                viewModel.updateGroupImage(groupId, null)
                showToast("Group photo removed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadImageToView(uri: Any) {
        when (uri) {
            is Uri -> {
                ivGroupImage.setImageURI(uri)
                ivGroupImage.setPadding(0, 0, 0, 0)
                ivGroupImage.scaleType = ImageView.ScaleType.CENTER_CROP
            }
            is String -> {
                // TODO: Use Glide/Coil in production
                // Glide.with(this).load(uri).centerCrop().into(ivGroupImage)
                ivGroupImage.setPadding(0, 0, 0, 0)
                ivGroupImage.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    private fun uploadGroupImage(uri: Uri) {
        showToast("Uploading image...")
        viewModel.uploadGroupImage(groupId, uri) { success, _ ->
            showToast(if (success) "Group photo updated" else "Failed to upload image")
        }
    }

    private fun showEditGroupInfoDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_group_info, null)

        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
        val etGroupDescription = dialogView.findViewById<TextInputEditText>(R.id.etGroupDescription)
        val ivDialogGroupImage = dialogView.findViewById<ImageView>(R.id.ivDialogGroupImage) // Image view to show the image
        val btnChangeGroupImage = dialogView.findViewById<MaterialButton>(R.id.btnChangeDialogGroupImage)

        // Set the current group name and description
        etGroupName.setText(tvGroupName.text)
        etGroupDescription.setText(
            if (tvGroupDescription.visibility == View.VISIBLE) tvGroupDescription.text else ""
        )

        // Fetch group image URL and load it into the dialog
        val groupImageUrl = viewModel.currentGroup.value?.imageUrl
        if (!groupImageUrl.isNullOrBlank()) {
            // Use Glide or Coil to load the image into the dialog's ImageView
            Glide.with(requireContext())
                .load(groupImageUrl)
                .centerCrop() // You can change this if you want different scaling
                .into(ivDialogGroupImage)
        }

        btnChangeGroupImage.setOnClickListener {
            showImagePickerDialog() // Trigger image picker if the user wants to change the image
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Group Info")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etGroupName.text.toString().trim()
                val newDescription = etGroupDescription.text.toString().trim()
                if (newName.isNotEmpty()) updateGroupInfo(newName, newDescription)
                else showToast("Group name cannot be empty")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun updateGroupInfo(name: String, description: String) {
        viewModel.updateGroupInfo(groupId, name, description)
        showToast("Updating group info…")
    }

    private fun updateAdminOnlySettings(adminOnly: Boolean) {
        viewModel.updateAdminOnlySettings(groupId, adminOnly)
        showToast(if (adminOnly) "Only admins can send messages now" else "All members can send messages now")
    }

    private fun showAddParticipantDialog() {
        // Hook up to your member-add flow
        showToast("Add participants feature coming soon!")
    }

    private fun showMemberOptionsDialog(member: GroupMemberWithUser) {
        val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
        if (member.userId == currentUserId) return

        val options = if (member.isAdmin)
            arrayOf("View Profile", "Remove Admin", "Remove from Group")
        else
            arrayOf("View Profile", "Make Admin", "Remove from Group")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(member.userName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showMemberProfile(member)
                    1 -> if (member.isAdmin) removeAdminRole(member) else makeAdmin(member)
                    2 -> showRemoveMemberDialog(member)
                }
            }
            .show()
    }

    private fun showMemberProfile(member: GroupMemberWithUser) {
        val message = buildString {
            append("Name: ${member.userName}\n")
            if (!member.userEmail.isNullOrBlank()) append("Email: ${member.userEmail}\n")
            append("Role: ${if (member.isAdmin) "Admin" else "Member"}\n")
            if (!member.joinedAt.isNullOrBlank()) append("Joined: ${formatDate(member.joinedAt)}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Member Info")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun makeAdmin(member: GroupMemberWithUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Make ${member.userName} an Admin?")
            .setMessage("They will be able to edit group info, manage participants, and delete the group.")
            .setPositiveButton("Make Admin") { _, _ ->
                viewModel.updateMemberAdminStatus(groupId, member.userId, true)
                showToast("${member.userName} is now an admin")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeAdminRole(member: GroupMemberWithUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove ${member.userName} as Admin?")
            .setMessage("They will no longer be able to edit group info or manage participants.")
            .setPositiveButton("Remove Admin") { _, _ ->
                viewModel.updateMemberAdminStatus(groupId, member.userId, false)
                showToast("${member.userName} is no longer an admin")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveMemberDialog(member: GroupMemberWithUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove ${member.userName}?")
            .setMessage("They will be removed from this group and won't be able to see messages or expenses.")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeMember(groupId, member.userId)
                showToast("${member.userName} removed from group")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExitGroupDialog() {
        val message = if (isCurrentUserAdmin) {
            "You are an admin. Please assign another admin before leaving, or delete the group instead."
        } else {
            "Are you sure you want to exit this group? You won't be able to see messages or expenses anymore."
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Exit Group?")
            .setMessage(message)
            .setPositiveButton("Exit") { _, _ ->
                if (!isCurrentUserAdmin) exitGroup() else showToast("Please assign another admin first or delete the group")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteGroupDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Group?")
            .setMessage("This will permanently delete the group, all messages, and expenses. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteGroup() {
        viewModel.deleteGroup(groupId)
        showToast("Group deleted")
        parentFragmentManager.popBackStack()
    }

    private fun exitGroup() {
        val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return
        viewModel.removeMember(groupId, currentUserId)
        showToast("You have left the group")
        parentFragmentManager.popBackStack()
    }

    private fun applyInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
    }

    private fun formatDate(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return ""
        val inputs = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        val out = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        out.timeZone = java.util.TimeZone.getDefault()
        for (p in inputs) {
            try {
                val sdf = java.text.SimpleDateFormat(p, java.util.Locale.US)
                if (p.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val d = sdf.parse(timestamp)
                if (d != null) return out.format(d)
            } catch (_: Exception) {}
        }
        return timestamp
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

data class GroupMemberWithUser(
    val userId: String,
    val userName: String = "Unknown User",
    val userEmail: String? = null,
    val isAdmin: Boolean = false,
    val avatarUrl: String? = null,
    val joinedAt: String? = null
)