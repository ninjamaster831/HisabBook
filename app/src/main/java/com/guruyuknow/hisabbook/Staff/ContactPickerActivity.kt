package com.guruyuknow.hisabbook.Staff

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ActivityContactPickerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ContactPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactPickerBinding
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contactHelper: ContactPickerHelper

    private val allContacts = mutableListOf<Contact>()
    private val filteredContacts = mutableListOf<Contact>()
    private val sectionPositions = mutableMapOf<Char, Int>() // A->index in filtered list

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge; we’ll apply insets manually
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityContactPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupClickListeners()
        applyWindowInsets()

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
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // debounce to avoid filtering on every keystroke harshly
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(120)
                    filterContacts(s?.toString().orEmpty())
                }
                binding.btnClearSearch.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupClickListeners() {
        binding.btnAddNewStaff.setOnClickListener { openManualStaffEntry() }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Pad app bar for status bar
            binding.appBar.setPadding(
                binding.appBar.paddingLeft, sys.top,
                binding.appBar.paddingRight, binding.appBar.paddingBottom
            )
            // Bottom-safe content + list
            val bottom = sys.bottom
            binding.contentContainer.updatePadding(bottom = bottom)
            binding.recyclerViewContacts.updatePadding(bottom = bottom)
            insets
        }
    }

    private fun loadContacts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoContacts.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val contacts = contactHelper.getAllContacts(this@ContactPickerActivity)
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }

                allContacts.clear()
                allContacts.addAll(contacts)

                filteredContacts.clear()
                filteredContacts.addAll(contacts)
                contactAdapter.notifyDataSetChanged()

                buildSectionsAndIndex(filteredContacts)
                binding.progressBar.visibility = View.GONE
                binding.tvNoContacts.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ContactPickerActivity,
                    "Error loading contacts: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun filterContacts(query: String) {
        val results = contactHelper.searchContacts(allContacts, query)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }

        filteredContacts.clear()
        filteredContacts.addAll(results)
        contactAdapter.notifyDataSetChanged()

        buildSectionsAndIndex(filteredContacts)
        binding.tvNoContacts.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Build map for first-letter -> position, then (re)render alphabet bar */
    private fun buildSectionsAndIndex(list: List<Contact>) {
        sectionPositions.clear()
        list.forEachIndexed { idx, c ->
            val ch = c.name.firstOrNull()?.uppercaseChar() ?: '#'
            sectionPositions.putIfAbsent(ch, idx)
        }
        renderAlphabetIndex()
    }

    private fun renderAlphabetIndex() {
        val container = binding.alphabetIndex
        container.removeAllViews()

        val letters = ('A'..'Z').toList()
        letters.forEach { ch ->
            val tv = TextView(this).apply {
                text = ch.toString()
                textSize = 12f
                setPadding(8, 4, 8, 4)
                setTextColor(resources.getColor(
                    if (sectionPositions.containsKey(ch)) R.color.colorPrimary else android.R.color.darker_gray,
                    null
                ))
                gravity = Gravity.CENTER
                background = null
                isAllCaps = true
                setOnClickListener {
                    sectionPositions[ch]?.let { pos ->
                        (binding.recyclerViewContacts.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(pos, 0)
                    }
                }
            }
            container.addView(tv)
        }
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
        startActivity(Intent(this, StaffConfigurationActivity::class.java))
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
