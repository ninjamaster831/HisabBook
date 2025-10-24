package com.guruyuknow.hisabbook.Staff

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.databinding.ItemContactBinding

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.apply {
                tvContactName.text = contact.name
                tvContactPhone.text = formatPhoneNumber(contact.phoneNumber)

                // Set initials with improved logic
                val initials = generateInitials(contact.name)
                tvContactInitials.text = initials

                // Set click listener
                root.setOnClickListener {
                    onContactClick(contact)
                }

                // Accessibility
                root.contentDescription = "Contact ${contact.name}, phone ${contact.phoneNumber}"
            }
        }

        private fun generateInitials(name: String): String {
            return name.trim()
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifEmpty { "?" }
        }

        private fun formatPhoneNumber(phone: String): String {
            val cleaned = phone.replace(Regex("[^\\d]"), "")
            return when {
                cleaned.length == 10 -> {
                    "${cleaned.substring(0, 5)} ${cleaned.substring(5)}"
                }
                cleaned.length > 10 -> {
                    val countryCode = cleaned.substring(0, cleaned.length - 10)
                    val mainNumber = cleaned.substring(cleaned.length - 10)
                    "+$countryCode ${mainNumber.substring(0, 5)} ${mainNumber.substring(5)}"
                }
                else -> phone
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount(): Int = contacts.size
}