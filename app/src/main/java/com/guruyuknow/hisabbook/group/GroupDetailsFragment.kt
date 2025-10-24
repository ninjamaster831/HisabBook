package com.guruyuknow.hisabbook.group

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth

class GroupDetailsFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()

    private var groupId: Long = 0
    private var isCurrentUserAdmin = false

    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivGroupImage: ImageView
    private lateinit var btnChangeGroupImage: MaterialCardView
    private lateinit var tvGroupName: TextView
    private lateinit var tvGroupDescription: TextView
    private lateinit var tvCreatedBy: TextView
    private lateinit var tvCreatedDate: TextView
    private lateinit var tvMemberCount: TextView
    private lateinit var rvMembers: RecyclerView
    private lateinit var cardGroupSettings: MaterialCardView
    private lateinit var btnEditGroupInfo: LinearLayout
    private lateinit var switchAdminOnly: MaterialSwitch
    private lateinit var btnAddParticipant: LinearLayout
    private lateinit var dividerAddParticipant: View
    private lateinit var btnExitGroup: MaterialCardView
    private lateinit var memberAdapter: GroupMemberAdapter

    private var selectedImageUri: Uri? = null

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val REQUEST_CAMERA = 1001
        private const val REQUEST_GALLERY = 1002

        fun newInstance(groupId: Long) = GroupDetailsFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_GROUP_ID, groupId)
            }
        }
    }

    // Image picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadImageToView(uri)
                // TODO: Upload image to Supabase storage
                uploadGroupImage(uri)
            }
        }
    }

    // Camera launcher
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

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showToast("Camera permission required")
        }
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

        viewModel.loadGroupDetails(groupId)
    }

    private fun setupViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        ivGroupImage = view.findViewById(R.id.ivGroupImage)
        btnChangeGroupImage = view.findViewById(R.id.btnChangeGroupImage)
        tvGroupName = view.findViewById(R.id.tvGroupName)
        tvGroupDescription = view.findViewById(R.id.tvGroupDescription)
        tvCreatedBy = view.findViewById(R.id.tvCreatedBy)
        tvCreatedDate = view.findViewById(R.id.tvCreatedDate)
        tvMemberCount = view.findViewById(R.id.tvMemberCount)
        rvMembers = view.findViewById(R.id.rvMembers)
        cardGroupSettings = view.findViewById(R.id.cardGroupSettings)
        btnEditGroupInfo = view.findViewById(R.id.btnEditGroupInfo)
        switchAdminOnly = view.findViewById(R.id.switchAdminOnly)
        btnAddParticipant = view.findViewById(R.id.btnAddParticipant)
        dividerAddParticipant = view.findViewById(R.id.dividerAddParticipant)
        btnExitGroup = view.findViewById(R.id.btnExitGroup)

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        memberAdapter = GroupMemberAdapter(
            onMemberClick = { member ->
                if (isCurrentUserAdmin) {
                    showMemberOptionsDialog(member)
                }
            },
            onRemoveMember = { member ->
                showRemoveMemberDialog(member)
            },
            currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
        )

        rvMembers.layoutManager = LinearLayoutManager(requireContext())
        rvMembers.adapter = memberAdapter
    }

    private fun setupListeners() {
        // Change group image
        btnChangeGroupImage.setOnClickListener {
            showImagePickerDialog()
        }

        // View full size image
        ivGroupImage.setOnClickListener {
            // TODO: Open full screen image viewer
            showToast("View full size image")
        }

        // Edit group info (name & description)
        btnEditGroupInfo.setOnClickListener {
            showEditGroupInfoDialog()
        }

        // Admin only messaging toggle
        switchAdminOnly.setOnCheckedChangeListener { _, isChecked ->
            updateAdminOnlySettings(isChecked)
        }

        // Add participant
        btnAddParticipant.setOnClickListener {
            // TODO: Navigate to add participant screen
            showToast("Add participants coming soon!")
        }

        // Exit group
        btnExitGroup.setOnClickListener {
            showExitGroupDialog()
        }
    }

    private fun observeData() {
        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            group?.let {
                tvGroupName.text = it.name
                tvCreatedDate.text = formatDate(it.createdAt)

                // Show description if exists
                if (!it.description.isNullOrBlank()) {
                    tvGroupDescription.text = it.description
                    tvGroupDescription.visibility = View.VISIBLE
                } else {
                    tvGroupDescription.visibility = View.GONE
                }

                // Load group image if available
                if (!it.imageUrl.isNullOrBlank()) {
                    loadImageToView(it.imageUrl)
                }

                // Update admin only switch
                switchAdminOnly.isChecked = it.adminOnly
            }
        }

        viewModel.groupMembers.observe(viewLifecycleOwner) { members ->
            if (members.isEmpty()) {
                tvMemberCount.text = "0 participants"
                tvCreatedBy.text = "Created by Unknown"
                return@observe
            }

            // Check if current user is admin
            val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
            isCurrentUserAdmin = members.any { it.userId == currentUserId && it.isAdmin }

            // Show admin controls
            if (isCurrentUserAdmin) {
                cardGroupSettings.visibility = View.VISIBLE
                btnChangeGroupImage.visibility = View.VISIBLE
                btnAddParticipant.visibility = View.VISIBLE
                dividerAddParticipant.visibility = View.VISIBLE
            } else {
                cardGroupSettings.visibility = View.GONE
                btnChangeGroupImage.visibility = View.GONE
                btnAddParticipant.visibility = View.GONE
                dividerAddParticipant.visibility = View.GONE
            }

            // Map GroupMember to GroupMemberWithUser
            val uiMembers = members.map { member ->
                GroupMemberWithUser(
                    userId = member.userId,
                    userName = member.userName ?: member.userEmail ?: "Unknown User",
                    isAdmin = member.isAdmin,
                    avatarUrl = member.avatarUrl
                )
            }

            tvMemberCount.text = "${uiMembers.size} participants"
            memberAdapter.submitList(uiMembers)

            // Find creator/admin
            val creator = uiMembers.firstOrNull { it.isAdmin }
            if (creator != null) {
                tvCreatedBy.text = "Created by ${creator.userName}"
            } else {
                tvCreatedBy.text = "Created by ${uiMembers.firstOrNull()?.userName ?: "Unknown"}"
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) {
            it?.let {
                showToast(it)
                viewModel.clearError()
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Group Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> openGallery()
                    2 -> removeGroupPhoto()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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

    private fun removeGroupPhoto() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Group Photo")
            .setMessage("Are you sure you want to remove the group photo?")
            .setPositiveButton("Remove") { _, _ ->
                // Reset to default icon
                ivGroupImage.setImageResource(R.drawable.ic_group_chat)
                ivGroupImage.setPadding(30, 30, 30, 30)
                selectedImageUri = null
                // TODO: Update in database
                viewModel.updateGroupImage(groupId, null)
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
                // TODO: Use Glide or Coil to load from URL
                // Glide.with(this).load(uri).centerCrop().into(ivGroupImage)
                ivGroupImage.setPadding(0, 0, 0, 0)
                ivGroupImage.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    private fun uploadGroupImage(uri: Uri) {
        // TODO: Implement Supabase storage upload
        showToast("Uploading image...")
        viewModel.uploadGroupImage(groupId, uri) { success, imageUrl ->
            if (success) {
                showToast("Group photo updated")
            } else {
                showToast("Failed to upload image")
            }
        }
    }

    private fun showEditGroupInfoDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_group_info, null)

        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
        val etGroupDescription = dialogView.findViewById<TextInputEditText>(R.id.etGroupDescription)

        // Pre-fill current values
        etGroupName.setText(tvGroupName.text)
        etGroupDescription.setText(tvGroupDescription.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Group Info")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etGroupName.text.toString().trim()
                val newDescription = etGroupDescription.text.toString().trim()

                if (newName.isNotEmpty()) {
                    updateGroupInfo(newName, newDescription)
                } else {
                    showToast("Group name cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGroupInfo(name: String, description: String) {
        viewModel.updateGroupInfo(groupId, name, description)
        showToast("Updating group info...")
    }

    private fun updateAdminOnlySettings(adminOnly: Boolean) {
        viewModel.updateAdminOnlySettings(groupId, adminOnly)
        val message = if (adminOnly) {
            "Only admins can send messages now"
        } else {
            "All members can send messages now"
        }
        showToast(message)
    }

    private fun showMemberOptionsDialog(member: GroupMemberWithUser) {
        val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
        if (member.userId == currentUserId) return // Can't modify self

        val options = if (member.isAdmin) {
            arrayOf("Remove Admin", "Remove from Group")
        } else {
            arrayOf("Make Admin", "Remove from Group")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(member.userName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (member.isAdmin) {
                            removeAdminRole(member)
                        } else {
                            makeAdmin(member)
                        }
                    }
                    1 -> showRemoveMemberDialog(member)
                }
            }
            .show()
    }

    private fun makeAdmin(member: GroupMemberWithUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Make ${member.userName} an Admin?")
            .setMessage("They will be able to edit group info and manage participants.")
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
            .setMessage("They will be removed from this group and won't be able to see messages.")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeMember(groupId, member.userId)
                showToast("${member.userName} removed from group")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExitGroupDialog() {
        val message = if (isCurrentUserAdmin) {
            "You are an admin. Please assign another admin before leaving the group."
        } else {
            "Are you sure you want to exit this group? You won't be able to see messages anymore."
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Exit Group?")
            .setMessage(message)
            .setPositiveButton("Exit") { _, _ ->
                if (!isCurrentUserAdmin) {
                    exitGroup()
                } else {
                    showToast("Please assign another admin first")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exitGroup() {
        val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return
        viewModel.removeMember(groupId, currentUserId)
        showToast("You have left the group")
        parentFragmentManager.popBackStack()
    }

    private fun applyInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(
                toolbar.paddingLeft,
                systemBars.top,
                toolbar.paddingRight,
                toolbar.paddingBottom
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

        val output = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        output.timeZone = java.util.TimeZone.getDefault()

        for (pattern in inputs) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                if (pattern.endsWith("'Z'")) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(timestamp)
                if (date != null) return output.format(date)
            } catch (_: Exception) {
            }
        }
        return timestamp
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}

// UI Data Classes
data class GroupUi(
    val id: Long,
    val name: String,
    val description: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val imageUrl: String? = null,
    val adminOnly: Boolean = false
)

data class GroupMemberWithUser(
    val userId: String,
    val userName: String = "Unknown User",
    val isAdmin: Boolean = false,
    val avatarUrl: String? = null
)

// Adapter for members list
class GroupMemberAdapter(
    private val onMemberClick: (GroupMemberWithUser) -> Unit,
    private val onRemoveMember: (GroupMemberWithUser) -> Unit,
    private val currentUserId: String?
) : androidx.recyclerview.widget.ListAdapter<GroupMemberWithUser, GroupMemberAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<GroupMemberWithUser>() {
        override fun areItemsTheSame(old: GroupMemberWithUser, new: GroupMemberWithUser) =
            old.userId == new.userId
        override fun areContentsTheSame(old: GroupMemberWithUser, new: GroupMemberWithUser) =
            old == new
    }
) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivMemberAvatar)
        val tvName: TextView = view.findViewById(R.id.tvMemberName)
        val tvRole: TextView = view.findViewById(R.id.tvMemberRole)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveMember)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position)

        holder.tvName.text = member.userName
        holder.tvRole.text = if (member.isAdmin) "Admin" else "Member"

        // Show remove button only if not current user
        holder.btnRemove.visibility = if (currentUserId != member.userId) {
            View.VISIBLE
        } else {
            View.GONE
        }

        holder.root.setOnClickListener { onMemberClick(member) }
        holder.btnRemove.setOnClickListener { onRemoveMember(member) }

        // Load avatar if available
        // Glide.with(holder.itemView).load(member.avatarUrl).circleCrop().into(holder.ivAvatar)
    }
}