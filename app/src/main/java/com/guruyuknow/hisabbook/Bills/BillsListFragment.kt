package com.guruyuknow.hisabbook.Bills

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.databinding.FragmentBillsListBinding
import kotlinx.coroutines.launch

class BillsListFragment : Fragment() {

    private var _binding: FragmentBillsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var type: String
    private lateinit var adapter: BillHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getString(ARG_TYPE) ?: "OUT"
        Log.d(TAG, "onCreate: arg type=$type")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        setupRefreshListener()
        setupFragmentResultListener()
        load()
    }

    private fun setupRecyclerView() {
        adapter = BillHistoryAdapter(viewLifecycleOwner) // binds BillWithEntry
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
    }

    private fun setupRefreshListener() {
        binding.swipe.setOnRefreshListener { load() }
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener("bill_saved", viewLifecycleOwner) { _, _ ->
            Log.d(TAG, "FragmentResult: bill_saved received -> reload()")
            load()
        }
    }

    private fun showEmpty(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    fun load() {
        Log.d(TAG, "load(): requesting bills for type=$type")
        binding.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bills = SupabaseCashbook.getBillsByType(type)
                Log.d(TAG, "load(): received ${bills.size} bills for type=$type")

                bills.firstOrNull()?.let { first ->
                    Log.d(TAG, "first bill -> id=${first.id}, image=${first.imageUrl}, amount(entry)=${first.entry?.amount}, type(entry)=${first.entry?.type}")
                }

                adapter.submitList(bills)
                showEmpty(bills.isEmpty())
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bills", e)
                showEmpty(true)
            } finally {
                binding.swipe.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val TAG = "BillsListFragment"
        fun newInstance(type: String) = BillsListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }
}
