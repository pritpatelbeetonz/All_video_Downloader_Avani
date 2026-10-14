import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:video_downloader/features/home/views/home_page.dart';
import 'package:video_downloader/welcomeScreen/welcome1.dart';
import 'package:video_downloader/welcomeScreen/welcome2.dart';
import '../for_ads/ads/ads_load_util.dart';

class WelPageview extends StatefulWidget {
  const WelPageview({super.key});

  @override
  State<WelPageview> createState() => WelPageviewState();
}

class WelPageviewState extends State<WelPageview> {
  final PageController pageController = PageController(initialPage: 0);
  int currentIndex = 0;

  @override
  void initState() {
    super.initState();
    // Preload Intro App Open ad as soon as user enters Intro 01
    AdsLoadUtil.loadPreloadIntroAppOpen();
  }

  @override
  void dispose() {
    pageController.dispose();
    super.dispose();
  }

  Widget bottomnavigation() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: SizedBox(
        height: 40,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            /// DOT INDICATOR
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                2,
                (index) => AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  width: 8,
                  height: 8,
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  decoration: BoxDecoration(
                    color: currentIndex == index
                        ? const Color(0xFFFE7338)
                        : Colors.grey,
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 20),

            /// CONTINUE BUTTON
            SizedBox(
              width: 120,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  elevation: 0,
                  shadowColor: Colors.transparent,
                  splashFactory: NoSplash.splashFactory,
                  backgroundColor: const Color(0xFFFE7338),
                  foregroundColor: Colors.white,
                ),
                onPressed: () {
                  if (currentIndex == 1) {
                    AdsLoadUtil.showIntroAppOpen(
                      context: context,
                      onDismissed: () {
                        if (mounted) {
                          Navigator.pushAndRemoveUntil(
                            context,
                            MaterialPageRoute(builder: (_) => const HomePage()),
                            (route) => false,
                          );
                        }
                      },
                    );
                  } else {
                    pageController.nextPage(
                      duration: const Duration(milliseconds: 300),
                      curve: Curves.ease,
                    );
                  }
                },
                child: Text(
                  'continue'.tr,
                  style: GoogleFonts.ibmPlexSans(
                    fontSize: 16,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: SafeArea(
        top: false,
        child: Scaffold(
          body: Stack(
            children: [
              PageView(
                controller: pageController,
                onPageChanged: (index) {
                  setState(() {
                    currentIndex = index;
                  });
                },
                children: const [
                  Welcome1(),
                  Welcome2(),
                ],
              ),
              Align(
                alignment: Alignment.bottomCenter,
                child: bottomnavigation(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}