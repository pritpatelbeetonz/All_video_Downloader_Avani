package app.messages.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.messages.data.model.OnboardingModel
import app.messages.ui.fragments.OnboardingFragment

class OnboardingPagerAdapter(
    fa: FragmentActivity,
    private val pages: List<OnboardingModel>
): FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        return OnboardingFragment.newInstance(pages[position])
    }
}