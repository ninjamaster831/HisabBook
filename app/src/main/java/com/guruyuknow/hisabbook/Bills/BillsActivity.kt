package com.guruyuknow.hisabbook.Bills

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.guruyuknow.hisabbook.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class BillsActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: BillsPagerAdapter
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bills)

        // Handle edge-to-edge properly
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViewPager()
        setupFAB()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up navigation
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Bills"
        }

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        adapter = BillsPagerAdapter(this)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Expense"
                1 -> "Purchase"
                else -> "Tab $position"
            }

            // Optional: Add icons to tabs
            /*
            tab.icon = when (position) {
                0 -> ContextCompat.getDrawable(this@BillsActivity, R.drawable.ic_expense_24)
                1 -> ContextCompat.getDrawable(this@BillsActivity, R.drawable.ic_purchase_24)
                else -> null
            }
            */
        }.attach()

        // Handle page changes for FAB behavior
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // Update FAB text based on current tab
                updateFABForCurrentTab(position)

                // Show FAB when page changes (in case it was hidden)
                fabAdd.show()
            }
        })
    }

    private fun setupFAB() {
        // Use ExtendedFloatingActionButton for better UX
        fabAdd = findViewById(R.id.fabAdd)

        // Set initial FAB state
        updateFABForCurrentTab(0)

        fabAdd.setOnClickListener {
            // Determine type based on current tab
            val currentTab = viewPager.currentItem
            val type = if (currentTab == 0) "OUT" else "IN"
            val tabName = if (currentTab == 0) "Expense" else "Purchase"

            // Show bottom sheet with appropriate type
            AddBillBottomSheet.newInstance(type, tabName)
                .show(supportFragmentManager, "AddBill")
        }
    }

    private fun updateFABForCurrentTab(position: Int) {
        when (position) {
            0 -> {
                fabAdd.text = "Add Expense"
                // Optional: Change icon
                // fabAdd.setIconResource(R.drawable.ic_expense_add_24)
            }
            1 -> {
                fabAdd.text = "Add Purchase"
                // Optional: Change icon
                // fabAdd.setIconResource(R.drawable.ic_purchase_add_24)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // Optional: Handle back button press for ViewPager
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewPager.currentItem == 0) {
            // If on first tab, exit activity
            super.onBackPressed()
        } else {
            // Go to previous tab
            viewPager.currentItem = viewPager.currentItem - 1
        }
    }
}