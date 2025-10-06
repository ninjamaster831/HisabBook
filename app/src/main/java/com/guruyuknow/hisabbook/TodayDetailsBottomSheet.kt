package com.guruyuknow.hisabbook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.databinding.BsTodayDetailsBinding
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TodayDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BsTodayDetailsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val entriesAdapter = TodayEntriesAdapter()
    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BsTodayDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.entriesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.entriesRecycler.adapter = entriesAdapter
        binding.swipeRefresh.setOnRefreshListener { loadToday() }

        loadToday()
    }

    private fun loadToday() {
        scope.launch {
            Log.d("TodayDetails", "=== loadToday() start ===")
            binding.progress.isVisible = true
            binding.errorText.isVisible = false
            try {
                val user = SupabaseManager.getCurrentUser()
                Log.d("TodayDetails", "current user -> ${user?.id}")
                val uid = user?.id ?: run {
                    showError("Please login to view details")
                    return@launch
                }

                // Calc start/end of day UTC
                val tz = TimeZone.getTimeZone("UTC")
                val cal = Calendar.getInstance(tz).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.time
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val end = cal.time

                val isoZ = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
                    timeZone = tz
                }
                val startIso = isoZ.format(start)
                val endIso = isoZ.format(end)
                Log.d("TodayDetails", "date window start=$startIso end=$endIso")

                val rows = withContext(Dispatchers.IO) {
                    SupabaseManager.client
                        .from("cashbook_entries")
                        .select {
                            filter {
                                eq("user_id", uid)
                                gte("created_at", startIso)
                                lt("created_at", endIso)
                            }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }
                        .decodeList<CashbookRow>()
                }

                Log.d("TodayDetails", "fetched rows=${rows.size}")
                rows.forEachIndexed { i, r ->
                    Log.d("TodayDetails", "row[$i] -> id=${r.id}, type=${r.type}, amount=${r.amount}, date=${r.date}, created=${r.createdAt}")
                }

                val totalIn  = rows.filter { it.type.equals("IN",  true) }.sumOf { it.amount ?: 0.0 }
                val totalOut = rows.filter { it.type.equals("OUT", true) }.sumOf { it.amount ?: 0.0 }
                val net = totalIn - totalOut

                Log.d("TodayDetails", "totals income=$totalIn expense=$totalOut net=$net")

                binding.totalIncome.text = currency.format(totalIn)
                binding.totalExpense.text = currency.format(totalOut)
                binding.netAmount.text = currency.format(net)
                binding.netAmount.setTextColor(
                    requireContext().getColor(if (net >= 0) R.color.success_green else R.color.error_red)
                )

                entriesAdapter.submit(rows.map { it.toUi() })
                binding.emptyState.isVisible = rows.isEmpty()
                binding.entriesRecycler.isVisible = rows.isNotEmpty()

            } catch (e: Exception) {
                Log.e("TodayDetails", "Error loading today entries", e)
                showError(e.message ?: "Failed to load")
            } finally {
                binding.progress.isVisible = false
                binding.swipeRefresh.isRefreshing = false
                Log.d("TodayDetails", "=== loadToday() end ===")
            }
        }
    }



    private fun showError(msg: String) {
        binding.errorText.text = msg
        binding.errorText.isVisible = true
        binding.entriesRecycler.isVisible = false
        binding.emptyState.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TodayDetailsBottomSheet()
    }
}
@Serializable
private data class CashbookRow(
    val id: String? = null,                  // ⬅️ was Long? — must be String?
    @SerialName("user_id") val userId: String? = null,
    val amount: Double? = null,
    val type: String? = null,                 // "IN" or "OUT"
    @SerialName("payment_method") val paymentMethod: String? = null,
    val description: String? = null,
    val category: String? = null,
    val date: String? = null,                 // yyyy-MM-dd (if you store it)
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toUi(): TodayEntryUi = TodayEntryUi(
        title = if (!category.isNullOrBlank()) category!! else (description ?: "No description"),
        subtitle = buildString {
            if (!paymentMethod.isNullOrBlank()) append(paymentMethod.lowercase().replaceFirstChar { it.titlecase() })
            if (!description.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(description)
            }
        },
        amount = amount ?: 0.0,
        isIncome = type.equals("IN", true)
    )
}


/* ---- UI model for adapter ---- */
data class TodayEntryUi(
    val title: String,
    val subtitle: String,
    val amount: Double,
    val isIncome: Boolean
)
