package com.guruyuknow.hisabbook.Bills

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.guruyuknow.hisabbook.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class BillsActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: BillsPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bills)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        viewPager = findViewById(R.id.viewPager)
        adapter = BillsPagerAdapter(this)
        viewPager.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "Expense" else "Purchase"
        }.attach()

        // BillsActivity.kt
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            // Tab 0 = Expense -> OUT, Tab 1 = Purchase -> IN
            val type = if (viewPager.currentItem == 0) "OUT" else "IN"
            AddBillBottomSheet.newInstance(type)
                .show(supportFragmentManager, "AddBill")
        }

    }
}
