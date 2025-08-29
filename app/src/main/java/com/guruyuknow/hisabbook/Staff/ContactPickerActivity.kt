package com.guruyuknow.hisabbook.Staff

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.databinding.ActivityContactPickerBinding
import kotlinx.coroutines.launch

class ContactPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactPickerBinding
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contactHelper: ContactPickerHelper

    private val allContacts = mutableListOf<Contact>()
    private val filteredContacts = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupClickListeners()
        loadContacts()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Select Contact"
    }

    private fun setupRecyclerView() {
        contactHelper = ContactPickerHelper()
        contactAdapter = ContactAdapter(filteredContacts) { contact ->
            openStaffConfiguration(contact)
        }

        binding.recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(this@ContactPickerActivity)
            adapter = contactAdapter
        }
    }

    private fun setupSearchView() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterContacts(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupClickListeners() {
        binding.btnAddNewStaff.setOnClickListener {
            // Open manual staff entry
            openManualStaffEntry()
        }
    }

    private fun loadContacts() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val contacts = contactHelper.getAllContacts(this@ContactPickerActivity)
                allContacts.clear()
                allContacts.addAll(contacts)

                filteredContacts.clear()
                filteredContacts.addAll(contacts)
                contactAdapter.notifyDataSetChanged()

                binding.progressBar.visibility = View.GONE

                if (contacts.isEmpty()) {
                    binding.tvNoContacts.visibility = View.VISIBLE
                } else {
                    binding.tvNoContacts.visibility = View.GONE
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ContactPickerActivity, "Error loading contacts: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterContacts(query: String) {
        val searchResults = contactHelper.searchContacts(allContacts, query)
        filteredContacts.clear()
        filteredContacts.addAll(searchResults)
        contactAdapter.notifyDataSetChanged()

        binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun openStaffConfiguration(contact: Contact) {
        val intent = Intent(this, StaffConfigurationActivity::class.java).apply {
            putExtra("contact_name", contact.name)
            putExtra("contact_phone", contact.phoneNumber)
            putExtra("contact_email", contact.email)
        }
        startActivity(intent)
        finish()
    }

    private fun openManualStaffEntry() {
        val intent = Intent(this, StaffConfigurationActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}