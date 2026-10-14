import 'dart:math';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import '../../for_ads/ads/ads_load_util.dart';
import '../../for_ads/ads/ads_variable.dart';
import '../for_ads/ads/ads_splash_utils.dart';
import '../import_all.dart' show GoogleFonts;
import '../welcomeScreen/welcomePageView.dart';

enum UsageLocation { home, office, onTheGo, publicSpaces }

class SurveyForm extends StatefulWidget {
  SurveyForm({super.key});

  @override
  State<SurveyForm> createState() => SurveyFormState();
}

class SurveyFormState extends State<SurveyForm> {
  UsageLocation? selectedLocation = UsageLocation.home;

  @override
  void initState() {
    super.initState();
    checkInterNetConnectivity();
  }

  bool isInterNetConnected = false;

  Future<void> checkInterNetConnectivity() async {
    if (await AdsVariable.isInternetConnected()) {
      setState(() {
        isInterNetConnected = true;
        print('=>>>> Internet Connected');
      });
      loadPreLoadIntroFullNativeAds();
    }
  }

  int selectedIndex = -1;

  //---------------------------------------------------------------main Scafold ---------------------------------------------
  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        body: SafeArea(
          child: SingleChildScrollView(
            child: Padding(
              padding: EdgeInsets.all(46.w),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  title(
                    text1: "What would you Like to use in Video Downloader ?",
                  ),

                  SizedBox(height: 50.h),

                  Options(
                    image: "assets/images/option1.png",
                    title: "Video downloading",
                    selected: selectedLocation == UsageLocation.home,
                    onTap: () {
                      setState(() {
                        selectedLocation = UsageLocation.home;
                        selectedIndex = 1;
                      });
                    },
                  ),

                  Options(
                    image: "assets/images/option2.png",
                    title: "Save Reels & Videos",
                    selected: selectedLocation == UsageLocation.office,
                    onTap: () {
                      setState(() {
                        selectedLocation = UsageLocation.office;
                        selectedIndex = 1;
                      });
                    },
                  ),

                  Options(
                    image: "assets/images/option3.png",
                    title: "Personalize Browser",
                    selected: selectedLocation == UsageLocation.publicSpaces,
                    onTap: () {
                      setState(() {
                        selectedLocation = UsageLocation.publicSpaces;
                        selectedIndex = 1;
                      });
                    },
                  ),

                  SizedBox(height: 40.h),

                  SizedBox(
                    width: double.infinity,
                    height: 200.h,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        elevation: 0,
                        // remove shadow
                        shadowColor: Colors.transparent,
                        splashFactory: NoSplash.splashFactory,
                        // remove ripple
                        backgroundColor: const Color(0xFFFE7338),
                        foregroundColor: Colors.white,
                      ),
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => const WelPageview(),
                          ),
                        );
                      },
                      child: Text(
                        'Done',
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 60.sp,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),

        /// ADS
        bottomNavigationBar: SafeArea(
          child: (AdsVariable.isPurchase == false)
              ? isInterNetConnected
                    ? selectedIndex == -1
                          ? SurveyBigNativeAds1(
                              showNativeAd: AdsVariable.nativeBigAdSurvey1,
                              isBigNative: true,
                              isNativeAdLoaded:
                                  AdsLoadUtil.isBigNativeSurveyAdLoaded1,
                              isNativeAdFailedToLoad:
                                  AdsLoadUtil.isNativeAdFailedToLoadBigSurvey1,
                            )
                          : SurveyBigNativeAds2(
                              showNativeAd: AdsVariable.nativeBigAdSurvey2,
                              isBigNative: true,
                              isNativeAdLoaded:
                                  AdsLoadUtil.isBigNativeSurveyAdLoaded2,
                              isNativeAdFailedToLoad:
                                  AdsLoadUtil.isNativeAdFailedToLoadBigSurvey2,
                            )
                    : const SizedBox()
              : const SizedBox(),
        ),
      ),
    );
  }
}

class title extends StatelessWidget {
  final text1;

  const title({super.key, required this.text1});

  @override
  Widget build(BuildContext context) {
    return Container(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            text1,
            style: GoogleFonts.ibmPlexSans(
              fontSize: 85.sp,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

Widget Options({
  required String image,
  required String title,
  required bool selected,
  required VoidCallback onTap,
}) {
  return InkWell(
    onTap: onTap,
    child: Container(
      margin: EdgeInsets.only(bottom: 40.h),
      decoration: BoxDecoration(
        color: selected ? Color(0xFFFFDFD0) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: selected ? Color(0xFFFE7338) : Color(0xFFFE7338),
          width: 1,
        ),
      ),
      child: Padding(
        padding: EdgeInsetsGeometry.all(1),
        child: Row(
          spacing: 10,
          children: [
            Container(
              width: 210.w,
              height: 210.h,
              padding: EdgeInsets.all(15.w),
              child: Image.asset(image),
            ),
            Text(
              title,
              style: GoogleFonts.ibmPlexSans(
                fontWeight: FontWeight.w500,
                fontSize: 60.sp,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
