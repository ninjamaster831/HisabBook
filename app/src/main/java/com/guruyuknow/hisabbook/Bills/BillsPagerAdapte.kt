package com.guruyuknow.hisabbook.Bills
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class BillsPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment {
        val type = if (position == 0) "OUT" else "IN"  // 0=Expense, 1=Purchase
        return BillsListFragment.newInstance(type)
    }

}
