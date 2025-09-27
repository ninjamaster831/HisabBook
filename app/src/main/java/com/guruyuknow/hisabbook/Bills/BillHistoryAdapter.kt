package com.guruyuknow.hisabbook.Bills

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

        // Amount: prefer entry.amount; fallback to extractedAmount
        val amountValue = bill.entry?.amount ?: bill.extractedAmount ?: 0.0

        val amountStr = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amountValue)

        // Title: ₹ • Category
        val category = entry?.category ?: "Uncategorized"
        holder.b.tvTitle.text = "$amountStr • $category"

        // Subtitle: Date • Payment • Type
        val date = entry?.date.orEmpty()
        val payment = entry?.paymentMethod.orEmpty()
        val type = entry?.type.orEmpty()
        holder.b.tvSubtitle.text = listOf(date, payment, type).filter { it.isNotBlank() }.joinToString(" • ")

        // Description: prefer entry.description; fallback to extractedText
        val desc = when {
            !entry?.description.isNullOrBlank() -> entry!!.description!!
            !bill.extractedText.isNullOrBlank() -> bill.extractedText!!.take(120)
            else -> ""
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

        // Click (you can navigate to detail if needed)
        holder.itemView.setOnClickListener {
            android.widget.Toast.makeText(
                holder.itemView.context,
                "Bill ID: ${bill.id}\nAmount: $amountStr",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
