import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'LanguageScreen/translations/app_translations.dart';
import 'features/home/views/home_page.dart';
import 'package:video_downloader/utility/shared_prefrence_service.dart';
import 'for_ads/ads/ads_splash_utils.dart';
import 'for_ads/ads/ads_variable.dart';
import 'import_all.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

void main() async {
  debugPrint("🚀 [VideoDownloader] main() started");
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await Firebase.initializeApp();
    debugPrint("✅ [VideoDownloader] Firebase initialized");
  } catch (e) {
    debugPrint("⚠️ [VideoDownloader] Firebase.initializeApp error: $e");
  }

  try {
    await SharedPrefService.init();
    debugPrint("✅ [VideoDownloader] SharedPrefService initialized");
  } catch (e) {
    debugPrint("⚠️ [VideoDownloader] SharedPrefService.init error: $e");
  }

  try {
    await AdsVariable.loadFromPrefs();
  } catch (e) {
    debugPrint("⚠️ [VideoDownloader] AdsVariable.loadFromPrefs error: $e");
  }

  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
    ),
  );

  const platformMethodChannel = MethodChannel('nativeChannel');
  platformMethodChannel.setMethodCallHandler((call) async {
    if (call.method == 'changeLanguage') {
      try {
        final args = call.arguments;
        String? code;
        if (args is Map) {
          code = args['languageCode'] as String?;
        } else if (args is String) {
          code = args;
        }
        if (code != null && code.isNotEmpty) {
          final normalized = (code == 'in') ? 'id' : code;
          await SharedPrefService.setSelectedLanguage(normalized);
          Get.updateLocale(Locale(normalized));
          debugPrint("🌐 [VideoDownloader] Updated locale to $normalized");
        }
      } catch (e) {
        debugPrint("⚠️ [VideoDownloader] Error updating locale: $e");
      }
    } else if (call.method == 'syncAdsConfig') {
      try {
        final args = call.arguments;
        String? rawConfig;
        if (args is Map) {
          rawConfig = args['rawConfig'] as String?;
        } else if (args is String) {
          rawConfig = args;
        }
        if (rawConfig != null && rawConfig.isNotEmpty) {
          debugPrint("📡 [VideoDownloader] Received syncAdsConfig from Launcher");
          await AdsVariable.updateFromRawJson(rawConfig);
        }
      } catch (e) {
        debugPrint("⚠️ [VideoDownloader] Error in syncAdsConfig: $e");
      }
    }
    return null;
  });

  // Pull initial ad configuration from Launcher Native if already available
  platformMethodChannel.invokeMethod('getAdsConfig').then((res) {
    if (res != null && res.toString().isNotEmpty) {
      debugPrint("📥 [VideoDownloader] Received getAdsConfig response from Launcher");
      AdsVariable.updateFromRawJson(res.toString());
    }
  }).catchError((e) {
    debugPrint("⚠️ [VideoDownloader] Error getting initial ads config: $e");
  });

  // Start the UI FIRST. Nothing below may block the first frame — the engine
  // is pre-warmed in MessagesApplication and the launcher shows it at index 0.
  runApp(const ProviderScope(child: MyApp()));
  debugPrint("🎉 [VideoDownloader] runApp executed");

  // Non-blocking: ads are already initialized natively; never gate the UI on it.
  MobileAds.instance
      .initialize()
      .timeout(const Duration(seconds: 5))
      .then((_) => debugPrint("✅ [VideoDownloader] MobileAds initialized"))
      .catchError((e) {
    debugPrint("⚠️ [VideoDownloader] MobileAds.initialize error: $e");
  });

  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]).catchError((e) {
    debugPrint("⚠️ [VideoDownloader] setPreferredOrientations error: $e");
  });

  // Initialize GDPR and mobile ads in the background (non-blocking)
  AdsSplashUtils.initServices();
}

final RouteObserver<ModalRoute<void>> routeObserver =
    RouteObserver<ModalRoute<void>>();

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return ScreenUtilInit(
      // designSize: Size(1290, 2796),
      minTextAdapt: true,
      splitScreenMode: true,
      designSize: Size(1284, 2778),
      child: GetMaterialApp(
        debugShowCheckedModeBanner: false,
        navigatorObservers: [routeObserver],
        translations: AppTranslations(),
        locale: Locale(SharedPrefService.getSelectedLanguage()),
        fallbackLocale: const Locale('en'),
        builder: (context, child) {
          return Directionality(
            textDirection: TextDirection.ltr,
            child: child ?? const SizedBox(),
          );
        },
        theme: ThemeData(
          scaffoldBackgroundColor: Color(0xFFFAFAFA),
          appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFFAFAFA)),
          fontFamily: 'IBMPlexSans',
          textTheme: GoogleFonts.ibmPlexSansTextTheme(
            ThemeData.light().textTheme,
          ),
          textSelectionTheme: const TextSelectionThemeData(
            cursorColor: Color(0xFFFE7338),
            selectionColor: Color(0x33FE7338),
            selectionHandleColor: Color(0xFFFE7338),
          ),
        ),
        home: const HomePage(),
      ),
    );
  }
}
