package com.guruyuknow.hisabbook.Bills

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.CashbookEntry
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ItemBillEntryBinding
import java.text.NumberFormat
import java.util.Locale

class BillHistoryAdapter :
    ListAdapter<CashbookEntry, BillHistoryAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<CashbookEntry>() {
        override fun areItemsTheSame(o: CashbookEntry, n: CashbookEntry) = o.id == n.id
        override fun areContentsTheSame(o: CashbookEntry, n: CashbookEntry) = o == n
    }

    class VH(val b: ItemBillEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBillEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = getItem(position)

        // Amount (₹)
        val amount = NumberFormat.getCurrencyInstance(Locale("en","IN"))
            .format(it.amount.toDouble())

        // Title: ₹ • Category
        holder.b.tvTitle.text = "$amount • ${it.category ?: "Uncategorized"}"

        // Subtitle: Date • Payment • Type
        holder.b.tvSubtitle.text = "${it.date} • ${it.paymentMethod} • ${it.type}"

        // Description
        holder.b.tvDesc.text = it.description.orEmpty()

        // Image: always show placeholder for now
        holder.b.imgThumb.setImageResource(R.drawable.ic_image_placeholder)
    }
}
