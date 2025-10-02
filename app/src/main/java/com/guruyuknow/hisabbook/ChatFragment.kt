package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.guruyuknow.hisabbook.group.CreateGroupBottomSheet
import com.guruyuknow.hisabbook.group.GroupDetailFragment
import com.guruyuknow.hisabbook.group.GroupExpenseViewModel
import com.guruyuknow.hisabbook.group.GroupListAdapter
import com.guruyuknow.hisabbook.group.JoinGroupBottomSheet

class ChatFragment : Fragment() {

    private val viewModel: GroupExpenseViewModel by viewModels()

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var groupsRv: RecyclerView
    private lateinit var loading: CircularProgressIndicator
    private lateinit var emptyView: LinearLayout
    private lateinit var fabCreate: ExtendedFloatingActionButton
    private lateinit var btnCreateFromEmpty: MaterialButton
    private lateinit var btnJoinFromEmpty: MaterialButton
    private lateinit var adapter: GroupListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        groupsRv = view.findViewById(R.id.groupsRv)
        loading = view.findViewById(R.id.loading)
        emptyView = view.findViewById(R.id.emptyView)
        fabCreate = view.findViewById(R.id.fabCreate)
        btnCreateFromEmpty = view.findViewById(R.id.btnCreateFromEmpty)

        setupRecycler()
        setupInteractions()
        observeVm()

        // initial load
        refreshGroups()
    }

    private fun setupRecycler() {
        adapter = GroupListAdapter(onClick = { group ->
            val id = group.id ?: return@GroupListAdapter
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer, // ← use your actual fragment container ID
                    GroupDetailFragment.Companion.newInstance(id)
                )
                .addToBackStack("group_details_$id")
                .commit()
        })

        groupsRv.layoutManager = LinearLayoutManager(requireContext())
        groupsRv.adapter = adapter
    }



    private fun setupInteractions() {
        swipeRefresh.setOnRefreshListener { refreshGroups() }

        // FAB click handler → show options
        fabCreate.setOnClickListener {
            showGroupOptionsDialog()
        }

        btnCreateFromEmpty.setOnClickListener { openCreateGroupSheet() }

        groupsRv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // fabCreate is ExtendedFloatingActionButton in XML now
                if (dy > 0 && fabCreate.isExtended) fabCreate.shrink()
                else if (dy < 0 && !fabCreate.isExtended) fabCreate.extend()
            }
        })

    }

    private fun showGroupOptionsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Action")
            .setItems(arrayOf("Create Group", "Join Group")) { _, which ->
                when (which) {
                    0 -> openCreateGroupSheet()
                    1 -> openJoinGroupSheet()
                }
            }
            .show()
    }

    private fun openJoinGroupSheet() {
        JoinGroupBottomSheet().show(parentFragmentManager, "JoinGroupBottomSheet")
    }

    private fun openCreateGroupSheet() {
        CreateGroupBottomSheet().show(parentFragmentManager, "CreateGroupBottomSheet")
    }
    private fun observeVm() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loading.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (!isLoading) swipeRefresh.isRefreshing = false
        }

        viewModel.allGroups.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)

            // Show/hide empty state
            val isEmpty = groups.isNullOrEmpty()
            emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE

            // Auto-extend FAB when list is empty
            if (isEmpty) {
                fabCreate.extend()
            }
        }

        viewModel.groupCreationResult.observe(viewLifecycleOwner) { res ->
            if (res.isSuccess) {
                refreshGroups()
                Snackbar.make(
                    requireView(),
                    "Group created successfully!",
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                res.exceptionOrNull()?.message?.let {
                    Snackbar.make(requireView(), it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(requireView(), it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun refreshGroups() {
        viewModel.loadAllGroups()
        swipeRefresh.isRefreshing = true
    }
}