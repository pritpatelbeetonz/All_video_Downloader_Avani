import 'package:flutter/material.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

import '../../for_ads/ads/ads_load_util.dart';
import '../../for_ads/ads/ads_variable.dart';
import '../../utility/shared_prefrence_service.dart';
import '../../welcomeScreen/welcomePageView.dart';
import 'models/language_model.dart';

class LanguageScreen extends StatefulWidget {
  final bool isFromSettings;

  const LanguageScreen({
    super.key,
    this.isFromSettings = false,
  });

  @override
  State<LanguageScreen> createState() => _LanguageScreenState();
}

class _LanguageScreenState extends State<LanguageScreen> {
  String? selectedLanguageCode;
  bool isInterNetConnected = false;
  bool hasSelectedAnyLanguage = false;
  NativeAd? _settingsNativeAd;
  bool _isLoadingSettingsAd = false;

  @override
  void initState() {
    super.initState();
    if (widget.isFromSettings) {
      selectedLanguageCode = SharedPrefService.getSelectedLanguage();
      hasSelectedAnyLanguage = true;
      _initSettingsNativeAd();
    } else {
      selectedLanguageCode = null; // No language selected by default from splash flow
      hasSelectedAnyLanguage = false;
      checkInterNetConnectivity();
    }
  }

  @override
  void dispose() {
    _settingsNativeAd?.dispose();
    super.dispose();
  }

  Future<void> _initSettingsNativeAd() async {
    if (AdsVariable.isPurchase) return;
    if (!AdsLoadUtil.isValidAdId(AdsVariable.prLanguageBigNative3)) {
      if (mounted) {
        setState(() {
          _isLoadingSettingsAd = false;
        });
      }
      return;
    }
    final bool hasInternet = await AdsVariable.isInternetConnected();
    if (!hasInternet) {
      if (mounted) {
        setState(() {
          isInterNetConnected = false;
          _isLoadingSettingsAd = false;
        });
      }
      return;
    }

    if (mounted) {
      setState(() {
        isInterNetConnected = true;
      });
    }

    if (AdsVariable.nativeBigAdLanguage3 != null) {
      // 1. Show the cached ad first!
      if (mounted) {
        setState(() {
          _settingsNativeAd = AdsVariable.nativeBigAdLanguage3;
          AdsVariable.nativeBigAdLanguage3 = null;
          _isLoadingSettingsAd = false;
        });
      }
      // 2. Trigger on-the-spot call in background to prepare/cache the next ad
      AdsLoadUtil().loadLanguageBigNative3();
    } else {
      // No cached ad: load on-the-spot with shimmer
      if (mounted) {
        setState(() {
          _isLoadingSettingsAd = true;
        });
      }
      AdsLoadUtil().loadLanguageBigNative3(
        onLoaded: (loadedAd) {
          if (mounted) {
            setState(() {
              _settingsNativeAd = loadedAd;
              AdsVariable.nativeBigAdLanguage3 = null;
              _isLoadingSettingsAd = false;
            });
          }
        },
        onFailed: () {
          if (mounted) {
            setState(() {
              _isLoadingSettingsAd = false;
            });
          }
        },
      );
    }
  }

  Future<void> checkInterNetConnectivity() async {
    if (await AdsVariable.isInternetConnected()) {
      if (mounted) {
        setState(() {
          isInterNetConnected = true;
        });
      }
    }
  }

  void _onLanguageSelected(String code) {
    setState(() {
      selectedLanguageCode = code;
      hasSelectedAnyLanguage = true; // Switches to Native Ad 2 and stays on it
    });
  }

  void _onContinue() async {
    if (selectedLanguageCode == null) return;
    await SharedPrefService.setSelectedLanguage(selectedLanguageCode!);
    Get.updateLocale(Locale(selectedLanguageCode!));

    if (widget.isFromSettings) {
      if (mounted) {
        Navigator.pop(context);
      }
    } else {
      if (mounted) {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => const WelPageview(),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.ltr,
      child: PopScope(
        canPop: widget.isFromSettings,
        child: Scaffold(
        backgroundColor: const Color(0xFFFAFAFA),
        appBar: widget.isFromSettings
            ? AppBar(
                backgroundColor: const Color(0xFFFAFAFA),
                elevation: 0,
                leading: IconButton(
                  icon: Image.asset("assets/icons/back.png", height: 30),
                  onPressed: () => Navigator.pop(context),
                ),
                title: Text(
                  'language'.tr,
                  style: GoogleFonts.ibmPlexSans(
                    fontSize: 60.sp,
                    fontWeight: FontWeight.w600,
                    color: Colors.black,
                  ),
                ),
                centerTitle: true,
                actions: [
                  IconButton(
                    icon: Icon(
                      Icons.check,
                      color: selectedLanguageCode != null
                          ? const Color(0xFFFE7338)
                          : Colors.grey,
                      size: 70.sp,
                    ),
                    onPressed: selectedLanguageCode != null ? _onContinue : null,
                  ),
                ],
              )
            : null,
        body: SafeArea(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: 46.w),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (!widget.isFromSettings) ...[
                  SizedBox(height: 30.h),
                  Text(
                    'choose_language'.tr,
                    style: GoogleFonts.ibmPlexSans(
                      fontSize: 85.sp,
                      fontWeight: FontWeight.w700,
                      color: Colors.black87,
                    ),
                  ),
                  SizedBox(height: 15.h),
                  Text(
                    'select_preferred_language'.tr,
                    style: GoogleFonts.ibmPlexSans(
                      fontSize: 45.sp,
                      fontWeight: FontWeight.w400,
                      color: Colors.black54,
                    ),
                  ),
                  SizedBox(height: 30.h),
                ] else ...[
                  SizedBox(height: 20.h),
                ],
                Expanded(
                  child: ListView.separated(
                    physics: const BouncingScrollPhysics(),
                    itemCount: supportedLanguages.length,
                    separatorBuilder: (context, index) => SizedBox(height: 30.h),
                    itemBuilder: (context, index) {
                      final language = supportedLanguages[index];
                      final isSelected = language.code == selectedLanguageCode;

                      return InkWell(
                        borderRadius: BorderRadius.circular(16),
                        onTap: () => _onLanguageSelected(language.code),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          padding: EdgeInsets.symmetric(
                            horizontal: 40.w,
                            vertical: 36.h,
                          ),
                          decoration: BoxDecoration(
                            color: isSelected
                                ? const Color(0xFFFFDFD0)
                                : Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: isSelected
                                  ? const Color(0xFFFE7338)
                                  : const Color(0xFFFE7338).withValues(alpha: 0.3),
                              width: isSelected ? 1.5 : 1,
                            ),
                          ),
                          child: Row(
                            children: [
                              Container(
                                width: 110.w,
                                height: 110.w,
                                decoration: BoxDecoration(
                                  color: isSelected
                                      ? const Color(0xFFFE7338)
                                      : const Color(0xFFF1F5F9),
                                  shape: BoxShape.circle,
                                ),
                                alignment: Alignment.center,
                                child: Text(
                                  language.code.toUpperCase(),
                                  style: GoogleFonts.ibmPlexSans(
                                    fontSize: 42.sp,
                                    fontWeight: FontWeight.w700,
                                    color: isSelected
                                        ? Colors.white
                                        : const Color(0xFF64748B),
                                  ),
                                ),
                              ),
                              SizedBox(width: 40.w),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      language.nativeName,
                                      style: GoogleFonts.ibmPlexSans(
                                        fontSize: 54.sp,
                                        fontWeight: FontWeight.w600,
                                        color: Colors.black87,
                                      ),
                                    ),
                                    if (language.nativeName != language.name)
                                      Padding(
                                        padding: EdgeInsets.only(top: 6.h),
                                        child: Text(
                                          language.name,
                                          style: GoogleFonts.ibmPlexSans(
                                            fontSize: 40.sp,
                                            fontWeight: FontWeight.w400,
                                            color: Colors.black54,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                              Container(
                                width: 68.w,
                                height: 68.w,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: isSelected
                                      ? const Color(0xFFFE7338)
                                      : Colors.transparent,
                                  border: Border.all(
                                    color: isSelected
                                        ? const Color(0xFFFE7338)
                                        : const Color(0xFFCBD5E1),
                                    width: 1.5,
                                  ),
                                ),
                                child: isSelected
                                    ? Icon(
                                        Icons.check,
                                        color: Colors.white,
                                        size: 45.sp,
                                      )
                                    : null,
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
                if (!widget.isFromSettings) ...[
                  SizedBox(height: 30.h),
                  SizedBox(
                    width: double.infinity,
                    height: 180.h,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        elevation: 0,
                        shadowColor: Colors.transparent,
                        splashFactory: selectedLanguageCode != null
                            ? null
                            : NoSplash.splashFactory,
                        backgroundColor: selectedLanguageCode != null
                            ? const Color(0xFFFE7338)
                            : const Color(0xFFFFD1BE),
                        foregroundColor: selectedLanguageCode != null
                            ? Colors.white
                            : const Color(0x99FFFFFF),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                      onPressed: selectedLanguageCode != null ? _onContinue : null,
                      child: Text(
                        'continue'.tr,
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 60.sp,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                  SizedBox(height: 20.h),
                ],
              ],
            ),
          ),
        ),
        bottomNavigationBar: !AdsVariable.isPurchase
            ? SafeArea(
                child: widget.isFromSettings
                    ? isInterNetConnected
                        ? LanguageBigNativeAds3(
                            isBigNative: true,
                            showNativeAd: _settingsNativeAd,
                            isLoading: _isLoadingSettingsAd,
                          )
                        : const SizedBox()
                    : isInterNetConnected
                        ? !hasSelectedAnyLanguage
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
                        : const SizedBox(),
              )
            : const SizedBox(),
      ),
    ),
  );
}
}
