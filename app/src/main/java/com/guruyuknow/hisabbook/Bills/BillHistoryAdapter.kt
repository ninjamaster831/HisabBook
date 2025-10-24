package com.guruyuknow.hisabbook.Bills

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ItemBillEntryBinding
import java.text.NumberFormat
import java.util.Locale

class BillHistoryAdapter(private val lifecycleOwner: LifecycleOwner) :
    ListAdapter<SupabaseCashbook.BillWithEntry, BillHistoryAdapter.VH>(Diff) {

    companion object {
        private const val TAG = "BillHistoryAdapter"
    }

    object Diff : DiffUtil.ItemCallback<SupabaseCashbook.BillWithEntry>() {
        override fun areItemsTheSame(o: SupabaseCashbook.BillWithEntry, n: SupabaseCashbook.BillWithEntry) = o.id == n.id
        override fun areContentsTheSame(o: SupabaseCashbook.BillWithEntry, n: SupabaseCashbook.BillWithEntry) = o == n
    }

    class VH(val b: ItemBillEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBillEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val bill = getItem(position)
        val entry = bill.entry

        // FIXED: Properly get amount from cashbook entry (this is the actual bill total)
        val amountValue = entry?.amount ?: run {
            // Only use extractedAmount as fallback if entry amount is null
            Log.w(TAG, "Bill ${bill.id}: entry.amount is null, using extractedAmount=${bill.extractedAmount}")
            bill.extractedAmount ?: 0.0
        }

        // Debug log
        Log.d(TAG, "Bill ${bill.id}: amountString='${entry?.amountString}', parsed amount=$amountValue")

        val amountStr = try {
            NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amountValue)
        } catch (e: Exception) {
            "₹$amountValue"
        }

        // Title: ₹Amount • Category
        val category = entry?.category ?: "Uncategorized"
        holder.b.tvTitle.text = "$amountStr • $category"

        // Subtitle: Date • Payment • Type
        val date = entry?.date.orEmpty()
        val payment = entry?.paymentMethod.orEmpty()
        val type = entry?.type.orEmpty()
        holder.b.tvSubtitle.text = listOf(date, payment, type)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        // Description: prefer entry.description; fallback to extractedText
        val desc = when {
            !entry?.description.isNullOrBlank() -> entry!!.description!!
            !bill.extractedText.isNullOrBlank() -> {
                val truncated = bill.extractedText!!.take(120)
                if (bill.extractedText!!.length > 120) "$truncated..." else truncated
            }
            else -> "No description"
        }
        holder.b.tvDesc.text = desc

        // Thumbnail
        if (!bill.imageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(bill.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .centerCrop()
                .into(holder.b.imgThumb)
            holder.b.imgThumb.alpha = 1f
        } else {
            holder.b.imgThumb.setImageResource(R.drawable.ic_image_placeholder)
            holder.b.imgThumb.alpha = 0.5f
        }

        // Click handler
        holder.itemView.setOnClickListener {
            android.widget.Toast.makeText(
                holder.itemView.context,
                "Bill ID: ${bill.id}\nAmount: $amountStr\nDate: ${entry?.date ?: "N/A"}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}