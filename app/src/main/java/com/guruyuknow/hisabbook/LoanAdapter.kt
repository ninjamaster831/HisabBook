package com.guruyuknow.hisabbook

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.databinding.ItemLoanBinding
import java.text.SimpleDateFormat
import java.util.*

class LoanAdapter(
    private val loans: List<Loan>,
    private val onActionClick: (Loan, Action) -> Unit
) : RecyclerView.Adapter<LoanAdapter.LoanViewHolder>() {

    enum class Action {
        EDIT, DELETE, MARK_PAID, SEND_REMINDER
    }

    inner class LoanViewHolder(private val binding: ItemLoanBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(loan: Loan) = with(binding) {
            // run the enter animation only once per holder instance
            if (cardLoan.tag != true) {
                cardLoan.alpha = 0f
                cardLoan.scaleX = 0.95f
                cardLoan.scaleY = 0.95f
                cardLoan.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
                cardLoan.tag = true
            }

            tvFriendName.text = loan.friend_name
            tvPhoneNumber.text = loan.phone_number
            tvAmount.text = "₹${String.format("%.2f", loan.amount)}"

            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            tvDateCreated.text = "Loaned on: ${dateFormat.format(Date(loan.date_created))}"

            if (loan.notes.isNotEmpty()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = "Note: ${loan.notes}"
            } else {
                tvNotes.visibility = View.GONE
            }

            // Reminder text
            if (loan.reminder_date_time != null) {
                tvReminderDate.visibility = View.VISIBLE
                val reminderFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                tvReminderDate.text = "Reminder: ${reminderFormat.format(Date(loan.reminder_date_time))}"
            } else {
                tvReminderDate.visibility = View.GONE
            }

            // Paid/unpaid UI
            if (loan.is_paid) {
                cardLoan.alpha = 0.95f
                tvFriendName.paintFlags = tvFriendName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvAmount.paintFlags = tvAmount.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvPaidStatus.visibility = View.VISIBLE
                btnMarkPaid.visibility = View.GONE
                btnEdit.visibility = View.GONE
                btnSendReminder.visibility = View.GONE
                // keep delete visible even when paid (change to GONE if you want to hide it)
                btnDeleteTop.visibility = View.VISIBLE
            } else {
                cardLoan.alpha = 1.0f
                tvFriendName.paintFlags = tvFriendName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvAmount.paintFlags = tvAmount.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvPaidStatus.visibility = View.GONE
                btnMarkPaid.visibility = View.VISIBLE
                btnEdit.visibility = View.VISIBLE
                btnSendReminder.visibility = View.VISIBLE
                btnDeleteTop.visibility = View.VISIBLE
            }

            // Clicks with micro-bounce
            btnSendReminder.setOnClickListener { animateButton(it) { onActionClick(loan, Action.SEND_REMINDER) } }
            btnEdit.setOnClickListener         { animateButton(it) { onActionClick(loan, Action.EDIT) } }
            btnMarkPaid.setOnClickListener     { animateButton(it) { onActionClick(loan, Action.MARK_PAID) } }
            btnDeleteTop.setOnClickListener    { animateButton(it) { onActionClick(loan, Action.DELETE) } }
        }

        private fun animateButton(view: View, onEnd: () -> Unit) {
            view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction { onEnd() }
                        .start()
                }
                .start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoanViewHolder {
        val binding = ItemLoanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LoanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LoanViewHolder, position: Int) {
        holder.bind(loans[position])
    }

    override fun getItemCount(): Int = loans.size
}
