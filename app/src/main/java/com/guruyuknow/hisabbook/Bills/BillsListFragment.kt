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
        adapter = BillHistoryAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        // refresh after a bill is saved
        parentFragmentManager.setFragmentResultListener("bill_saved", viewLifecycleOwner) { _, _ ->
            Log.d(TAG, "FragmentResult: bill_saved received -> reload()")
            load()
        }

        binding.swipe.setOnRefreshListener { load() }
        load()
    }

    private fun showEmpty(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    fun load() {
        Log.d(TAG, "load(): requesting entries for type=$type")
        binding.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val items = SupabaseCashbook.getEntriesByType(type)
            Log.d(TAG, "load(): received ${items.size} rows for type=$type")
            if (items.isNotEmpty()) {
                Log.d(TAG, "first item -> id=${items.first().id}, amount=${items.first().amount}, category=${items.first().category}, date=${items.first().date}, pm=${items.first().paymentMethod}, type=${items.first().type}")
            }
            adapter.submitList(items)
            showEmpty(items.isEmpty())
            binding.swipe.isRefreshing = false
        }
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val TAG = "BillsListFragment"
        fun newInstance(type: String) = BillsListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }
}
