import 'package:get/get.dart';
import 'package:video_downloader/features/browser/providers/download_task_provider.dart';
import 'package:video_downloader/features/browser/views/progress_page.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:video_downloader/for_ads/utils/app_constants.dart';
import 'package:video_downloader/utility/shared_prefrence_service.dart';

import '../../browser/providers/SearchToBrowser.dart';
import '../../Activity_library/Activity_library.dart' show ActivityLibrary;
import '../providers/bottom_nav_provider.dart';
import 'package:video_downloader/import_all.dart';
import 'package:video_downloader/features/browser/views/search_page.dart';

class HomePage extends ConsumerStatefulWidget {
  const HomePage({super.key});

  @override
  ConsumerState<HomePage> createState() => HomePageState();
}

class HomePageState extends ConsumerState<HomePage> {
  @override
  void initState() {
    super.initState();
    if (SharedPrefService.getIsFirstTime()) {
      SharedPrefService.setIsFirstTime(false);
      showLog("Entered First page");
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentIndex = ref.watch(bottomNavProvider);
    final activeCount = ref.watch(activeDownloadsCountProvider);
    final showBrowser = ref.watch(browserViewProvider);
    final pages = [
      const SearchPage(),
      const ActivityLibrary(),
      const ProgressPage(),
    ];

    return PopScope(
      canPop: false,
      child: Scaffold(
        body: IndexedStack(index: currentIndex, children: pages),
        bottomNavigationBar: (showBrowser && currentIndex == 0)
            ? null
            : Container(
          decoration: const BoxDecoration(
            border: Border(
              top: BorderSide(
                color: Colors.black12,
                width: 1,
              ),
            ),
          ),
          child: BottomNavigationBar(
            selectedItemColor: const Color(0xffFF6A2B),
            unselectedItemColor: Colors.grey,

            selectedLabelStyle: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
            ),

            unselectedLabelStyle: const TextStyle(
              fontSize: 12,
            ),

            elevation: 0,
            backgroundColor: const Color(0xFFFAFAFA),
            type: BottomNavigationBarType.fixed,
            currentIndex: currentIndex,
            onTap: (index) {
              if (index != currentIndex && currentIndex == 0) {
                AppBigNativeAdPool.detachScreen('SearchPage');
              }
              AdsVariable.onShowAds(
                context,
                place: 'VD_Quick_App',
                onComplete: () {
                  ref.read(bottomNavProvider.notifier).changeTab(index);
                },
              );
            },
            items: [
              BottomNavigationBarItem(
                icon: currentIndex == 0
                    ? Image.asset(
                  "assets/icons/Home_s.png",
                  width: 28,
                  height: 28,
                )
                    : Image.asset(
                  "assets/icons/Home.png",
                  width: 28,
                  height: 28,
                ),
                label: 'home'.tr,
              ),
              BottomNavigationBarItem(
                icon: currentIndex == 1
                    ? Image.asset(
                  "assets/icons/Library_s.png",
                  width: 28,
                  height: 28,
                )
                    : Image.asset(
                  "assets/icons/Library.png",
                  width: 28,
                  height: 28,
                ),
                label: 'library'.tr,
              ),
              BottomNavigationBarItem(
                icon: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Icon(
                      Icons.downloading_rounded,
                      size: 28,
                      color: currentIndex == 2
                          ? const Color(0xffFF6A2B)
                          : Colors.grey,
                    ),
                    if (activeCount > 0)
                      Positioned(
                        right: -6,
                        top: -3,
                        child: Container(
                          padding: const EdgeInsets.all(4),
                          decoration: const BoxDecoration(
                            color: Color(0xFFFE7338),
                            shape: BoxShape.circle,
                          ),
                          child: Text(
                            '$activeCount',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 9,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                label: 'progress'.tr,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
