package com.guruyuknow.hisabbook

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.Bills.SupabaseCashbook.BillWithEntry
import com.guruyuknow.hisabbook.Bills.BillDetailActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class BillsAdapter(
    private val onClick: (BillWithEntry) -> Unit
) : RecyclerView.Adapter<BillsAdapter.BillViewHolder>() {

    private var bills: List<BillWithEntry> = emptyList()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun submitList(bills: List<BillWithEntry>?) {
        this.bills = bills ?: emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bill, parent, false)
        return BillViewHolder(view)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(bills[position])
    }

    override fun getItemCount(): Int = bills.size

    inner class BillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val billImage: ImageView = itemView.findViewById(R.id.billImage)
        private val amountText: TextView = itemView.findViewById(R.id.billAmount)
        private val dateText: TextView = itemView.findViewById(R.id.billDate)
        private val descriptionText: TextView = itemView.findViewById(R.id.billDescription)

        fun bind(bill: BillWithEntry) {
            // Always show the receipt icon for the bill image
            billImage.setImageResource(R.drawable.ic_receipt)

            // Set amount text
            val amount = bill.entry?.amount ?: bill.extractedAmount ?: 0.0
            amountText.text = numberFormat.format(amount)

            // Set amount text color based on bill type
            val type = bill.entry?.type ?: "Unknown"
            amountText.setTextColor(
                when (type.uppercase()) {
                    "IN" -> itemView.context.getColor(R.color.success_green)  // Green for Income
                    "OUT" -> itemView.context.getColor(R.color.error_red)     // Red for Expense
                    else -> itemView.context.getColor(R.color.black)         // Default for unknown type
                }
            )

            // Set date
            val dateStr = bill.entry?.date?.let { date ->
                try {
                    dateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date))
                } catch (e: Exception) {
                    "Unknown Date"
                }
            } ?: bill.createdAt?.let { dateFormat.format(Date(it)) } ?: "Unknown Date"
            dateText.text = dateStr

            // Set description
            val description = bill.entry?.description ?: bill.extractedText ?: "No description"
            descriptionText.text = description

            // Handle item click to open BillDetailActivity
            itemView.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, BillDetailActivity::class.java).apply {
                    putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.id)
                    putExtra(BillDetailActivity.EXTRA_IMAGE_URL, bill.imageUrl)
                    putExtra(BillDetailActivity.EXTRA_AMOUNT, amount)
                    putExtra(BillDetailActivity.EXTRA_DATE, bill.entry?.date)
                    putExtra(BillDetailActivity.EXTRA_TYPE, bill.entry?.type)
                    putExtra(BillDetailActivity.EXTRA_PAYMENT_METHOD, bill.entry?.paymentMethod)
                    putExtra(BillDetailActivity.EXTRA_CATEGORY, bill.entry?.category)
                    putExtra(BillDetailActivity.EXTRA_DESCRIPTION, bill.entry?.description)
                    putExtra(BillDetailActivity.EXTRA_EXTRACTED_TEXT, bill.extractedText)
                    putExtra(BillDetailActivity.EXTRA_EXTRACTED_AMOUNT, bill.extractedAmount)
                    //putExtra(BillDetailActivity.EXTRA_CONFIDENCE_SCORE, bill.confidenceScore)
                }
                context.startActivity(intent)
            }
        }
    }
}
