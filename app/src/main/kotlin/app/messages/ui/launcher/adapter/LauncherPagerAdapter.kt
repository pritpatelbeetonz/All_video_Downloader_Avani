package app.messages.ui.launcher.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.messages.ui.launcher.dashboard.DashboardFragment
import app.messages.ui.launcher.downloader.VideoDownloaderContainerFragment
import app.messages.ui.launcher.home.HomeFragment

class LauncherPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        PAGE_VIDEO_DOWNLOADER -> VideoDownloaderContainerFragment()
        PAGE_HOME -> HomeFragment()
        PAGE_MESSAGE -> DashboardFragment()
        else -> HomeFragment()
    }

    companion object {
        const val PAGE_VIDEO_DOWNLOADER = 0
        const val PAGE_DASHBOARD = 0
        const val PAGE_HOME = 1
        const val PAGE_MESSAGE = 2
    }
}
