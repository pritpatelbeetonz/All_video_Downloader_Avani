import 'package:connectivity_plus/connectivity_plus.dart';

showLog(String msg) {
  print("LOG >> $msg");
}
//TO DO: add the Apple API key for your app from the RevenueCat dashboard: https://app.revenuecat.com
const appleApiKey = '';

//TO DO: add the Google API key for your app from the RevenueCat dashboard: https://app.revenuecat.com
const googleApiKey = 'goog_aJDsFyaWEyFCGDRgVOjYqcdbzbH';

//TO DO: add the Amazon API key for your app from the RevenueCat dashboard: https://app.revenuecat.com
const amazonApiKey = '';

const entitlementKey = "AndroidTestProAccess";

class GlobalVariables {

  static Future<bool> isInternetConnected() {
    return Connectivity().checkConnectivity().then((connectivityResult) {
      if (connectivityResult ==ConnectivityResult.none) {
        return false;
      } else {
        return true;
      }
    });
  }
}


