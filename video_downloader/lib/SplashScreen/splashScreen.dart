import 'dart:developer';

import 'package:get/get_core/src/get_main.dart';
import 'package:get/get_navigation/src/extension_navigation.dart';
import 'package:video_downloader/features/home/views/home_page.dart';
import 'package:video_downloader/firebase_analysis.dart';
import 'package:video_downloader/for_ads/ads/ads_splash_utils.dart';
import 'package:video_downloader/utility/shared_prefrence_service.dart';
import '../LanguageScreen/LanguageScreen.dart';
import '../import_all.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => SplashScreenState();
}

class SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((callback) async {
      FirebaseAnalyticsService.logEvent(eventName: 'SPLASH_SCREEN');
      await AdsSplashUtils().getOnlineIds(
        navigateScreen: () async {
          log('navigate screen');
          navigatingToNextActivity();
        },
      );
    });
  }

  void navigatingToNextActivity() async {
    bool isFirstLaunch = SharedPrefService.getIsFirstTime();
    if (isFirstLaunch) {
      Get.off(const LanguageScreen());
    } else {
      Get.off(HomePage()); // homepage // rootpage
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body:
      SafeArea(
        top: false,
        child: Stack(
        children: [
          //Background image
          Positioned.fill(
            child: Image.asset(
              "assets/images/bg.png",
              fit: BoxFit.cover,
            ),
          ),

          //Center logo
          Center(
            child: Image.asset(
              "assets/images/ic.png",
              width: 120,
            ),
          ),

          /// Bottom Section
          Align(
            alignment: Alignment.bottomCenter,
            child: Container(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Image.asset("assets/images/a9f449586f474284034c753cdad392d48be307f4.gif",
                    width:41,),
                  Text('Video Downloader',style: GoogleFonts.ibmPlexSans(fontWeight: FontWeight.w600,fontSize: 20),),
                ],
              ),
            )
          ),
        ],
      ),
      ),
    );
  }
}
