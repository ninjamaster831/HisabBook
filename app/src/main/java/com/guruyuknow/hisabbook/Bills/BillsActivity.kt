package com.guruyuknow.hisabbook.Bills

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.guruyuknow.hisabbook.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class BillsActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: BillsPagerAdapter
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var tabLayout: TabLayout
    private lateinit var hintCard: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bills)

        // Set status bar color and make it transparent to extend into status bar
        window.statusBarColor = getColor(R.color.primary_color)

        setupToolbar()
        setupViewPager()
        setupFAB()
        setupHintCard()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup back button
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        adapter = BillsPagerAdapter(this)
        viewPager.adapter = adapter

        // Connect tabs with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Expense"
                1 -> "Purchase"
                else -> "Tab $position"
            }
        }.attach()

        // Update FAB on page change
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateFAB(position)
            }
        })
    }

    private fun setupFAB() {
        fabAdd = findViewById(R.id.fabAdd)

        fabAdd.setOnClickListener {
            val currentTab = viewPager.currentItem
            val type = if (currentTab == 0) "OUT" else "IN"
            val tabName = if (currentTab == 0) "Expense" else "Purchase"

            AddBillBottomSheet.newInstance(type, tabName)
                .show(supportFragmentManager, "AddBill")
        }
    }

    private fun updateFAB(position: Int) {
        fabAdd.text = when (position) {
            0 -> "Add Expense"
            1 -> "Add Purchase"
            else -> "Add Bill"
        }
    }

    private fun setupHintCard() {
        hintCard = findViewById(R.id.hintCard)
        val btnCloseHint = findViewById<ImageView>(R.id.btnCloseHint)

        // Check if hint was dismissed before
        val prefs = getSharedPreferences("BillsPrefs", MODE_PRIVATE)
        val hintDismissed = prefs.getBoolean("hint_dismissed", false)

        if (hintDismissed) {
            hintCard.visibility = View.GONE
        }

        // Close hint on click
        btnCloseHint.setOnClickListener {
            hintCard.animate()
                .alpha(0f)
                .translationY(-hintCard.height.toFloat())
                .setDuration(300)
                .withEndAction {
                    hintCard.visibility = View.GONE
                    // Save preference
                    prefs.edit().putBoolean("hint_dismissed", true).apply()
                }
                .start()
        }
    }
}