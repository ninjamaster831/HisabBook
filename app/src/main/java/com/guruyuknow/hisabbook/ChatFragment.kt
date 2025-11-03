package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.group.CreateGroupBottomSheet
import com.guruyuknow.hisabbook.group.GroupCodeDialog
import com.guruyuknow.hisabbook.group.GroupDetailFragment
import com.guruyuknow.hisabbook.group.GroupExpenseViewModel
import com.guruyuknow.hisabbook.group.GroupListAdapter
import com.guruyuknow.hisabbook.group.JoinGroupBottomSheet

class ChatFragment : Fragment() {

    // ✅ Activity-scoped ViewModel (shared with bottom sheet)
    private val viewModel: GroupExpenseViewModel by activityViewModels()

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var groupsRv: RecyclerView
    private lateinit var loading: CircularProgressIndicator
    private lateinit var emptyView: LinearLayout
    private lateinit var fabCreate: ExtendedFloatingActionButton
    private lateinit var btnCreateFromEmpty: MaterialButton
    private lateinit var btnJoinFromEmpty: MaterialButton
    private lateinit var btnFilter: MaterialButton
    private lateinit var etSearch: TextInputEditText
    private lateinit var filterChipCard: MaterialCardView
    private lateinit var chipGroup: ChipGroup
    private lateinit var adapter: GroupListAdapter
    private lateinit var notificationCard: MaterialCardView
    private var isFilterVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecycler()
        setupInteractions()
        setupSearchAndFilter()
        observeVm()

        // Initial load with animation
        refreshGroups()
    }

    private fun initializeViews(view: View) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        groupsRv = view.findViewById(R.id.groupsRv)
        loading = view.findViewById(R.id.loading)
        emptyView = view.findViewById(R.id.emptyView)
        fabCreate = view.findViewById(R.id.fabCreate)
        btnCreateFromEmpty = view.findViewById(R.id.btnCreateFromEmpty)
        btnJoinFromEmpty = view.findViewById(R.id.btnJoinFromEmpty)
        btnFilter = view.findViewById(R.id.btnFilter)
        etSearch = view.findViewById(R.id.etSearch)
        filterChipCard = view.findViewById(R.id.filterChipCard)
        chipGroup = view.findViewById(R.id.chipGroup)
        notificationCard = view.findViewById(R.id.notification)
    }

    private fun setupRecycler() {
        adapter = GroupListAdapter(onClick = { group ->
            val id = group.id ?: return@GroupListAdapter

            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(
                    R.id.fragmentContainer,
                    GroupDetailFragment.newInstance(id)
                )
                .addToBackStack("group_details_$id")
                .commit()
        })

        groupsRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatFragment.adapter
            itemAnimator?.apply {
                addDuration = 300
                removeDuration = 300
            }
        }
    }

    private fun setupInteractions() {
        swipeRefresh.apply {
            setOnRefreshListener { refreshGroups() }
            setColorSchemeResources(
                R.color.hsb_primary,
                R.color.hsb_accent_blue,
                R.color.hsb_primary_dark
            )
        }

        fabCreate.setOnClickListener { showModernGroupOptionsDialog() }

        btnCreateFromEmpty.setOnClickListener {
            animateButton(it)
            openCreateGroupSheet()
        }

        btnJoinFromEmpty.setOnClickListener {
            animateButton(it)
            openJoinGroupSheet()
        }

        btnFilter.setOnClickListener { toggleFilterChips() }
        notificationCard.setOnClickListener {
            animateButton(it) // Optional: Add subtle animation for feedback
            openNotificationActivity()
        }
        groupsRv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 10 && fabCreate.isExtended) fabCreate.shrink()
                else if (dy < -10 && !fabCreate.isExtended) fabCreate.extend()
            }
        })
    }
    private fun openNotificationActivity() {
        val intent = Intent(requireContext(), NotificationsActivity::class.java)
        startActivity(intent)
        // Optional: Add transition animation
        // requireActivity().overridePendingTransition(R.anim.slide_in_up, R.anim.slide_out_down)
    }
    private fun setupSearchAndFilter() {
        etSearch.addTextChangedListener { text ->
            val query = text?.toString()?.trim().orEmpty()
            applyFilter(query, getSelectedChipFilter())
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedFilter = when {
                checkedIds.contains(R.id.chipMine) -> "mine"
                checkedIds.contains(R.id.chipJoined) -> "joined"
                else -> "all"
            }
            applyFilter(etSearch.text?.toString()?.trim().orEmpty(), selectedFilter)
        }
    }

    private fun getSelectedChipFilter(): String = when (chipGroup.checkedChipId) {
        R.id.chipMine -> "mine"
        R.id.chipJoined -> "joined"
        else -> "all"
    }

    private fun applyFilter(query: String, filter: String) {
        viewModel.filterGroups(query, filter)
    }

    private fun toggleFilterChips() {
        isFilterVisible = !isFilterVisible

        if (isFilterVisible) {
            filterChipCard.visibility = View.VISIBLE
            filterChipCard.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down)
            )
            btnFilter.setIconResource(R.drawable.ic_filter_off)
        } else {
            filterChipCard.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
            )
            filterChipCard.postDelayed({ filterChipCard.visibility = View.GONE }, 200)
            btnFilter.setIconResource(R.drawable.ic_filter)
        }
    }

    private fun showModernGroupOptionsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose an Action")
            .setMessage("Would you like to create a new group or join an existing one?")
            .setPositiveButton("Create Group") { _, _ -> openCreateGroupSheet() }
            .setNegativeButton("Join Group") { _, _ -> openJoinGroupSheet() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun openJoinGroupSheet() {
        JoinGroupBottomSheet().show(parentFragmentManager, "JoinGroupBottomSheet")
    }

    private fun openCreateGroupSheet() {
        CreateGroupBottomSheet().show(parentFragmentManager, "CreateGroupBottomSheet")
    }

    private fun animateButton(view: View) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun observeVm() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loading.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (!isLoading) swipeRefresh.isRefreshing = false
        }

        viewModel.filteredGroups.observe(viewLifecycleOwner) { groups ->
            Log.d("ChatFragment", "Filtered groups received: ${groups?.size ?: 0}")
            groups?.forEach { group ->
                Log.d("ChatFragment", "  - '${group.name}' (id=${group.id}, createdBy=${group.createdBy})")
            }

            adapter.submitList(groups)

            val isEmpty = groups.isNullOrEmpty()
            if (isEmpty) {
                emptyView.visibility = View.VISIBLE
                emptyView.alpha = 0f
                emptyView.animate().alpha(1f).setDuration(300).start()
                fabCreate.extend()
            } else {
                emptyView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { emptyView.visibility = View.GONE }
                    .start()
            }
        }

        viewModel.groupCreationResult.observe(viewLifecycleOwner) { res ->
            if (res == null) return@observe
            if (res.isSuccess) {
                refreshGroups()
                showSuccessSnackbar("Group created successfully! 🎉")
                viewModel.clearGroupCreationResult()
            } else {
                res.exceptionOrNull()?.message?.let { showErrorSnackbar(it) }
            }
        }

        // ✅ This will now fire after creation, even after the sheet dismisses
        viewModel.createdGroupInfo.observe(viewLifecycleOwner) { info ->
            Log.d("ChatFragment", "createdGroupInfo observed: $info")
            if (info != null) {
                val (groupName, groupCode) = info
                Log.d("ChatFragment", "Showing GroupCodeDialog: name=$groupName, code=$groupCode")
                GroupCodeDialog.newInstance(groupName, groupCode)
                    .show(parentFragmentManager, "group_code_dialog")
                viewModel.clearCreatedGroupInfo()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showErrorSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    private fun refreshGroups() {
        viewModel.loadAllGroups()
        swipeRefresh.isRefreshing = true
    }

    private fun showSuccessSnackbar(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(resources.getColor(R.color.hsb_primary, null))
            .setTextColor(resources.getColor(android.R.color.white, null))
            .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
            .show()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(android.R.color.holo_red_dark, null))
            .setTextColor(resources.getColor(android.R.color.white, null))
            .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
            .setAction("Retry") { refreshGroups() }
            .show()
    }
}
