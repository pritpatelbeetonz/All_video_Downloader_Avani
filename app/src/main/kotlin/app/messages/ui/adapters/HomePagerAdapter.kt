package app.messages.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.messages.ui.fragments.ChatsFragment
import app.messages.ui.fragments.SettingsFragment

class HomePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ChatsFragment()
            1 -> SettingsFragment()
//            1 -> ThemesFragment()
            else -> ChatsFragment()
        }
    }
}
