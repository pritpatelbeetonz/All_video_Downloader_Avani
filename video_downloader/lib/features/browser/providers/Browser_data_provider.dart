import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:video_downloader/features/browser/views/Browser.dart';
import '../utility/platform_detector.dart';

class BrowserData extends StateNotifier<Map<String, String>> {
  BrowserData() : super({
    "url": "",
    "platform": "unknown",
  });
  void submit(String value) {
    print('reached the submit function');
    final finalUrl = getFinalUrl(value);
    final platform = PlatformDetector.detectPlatform(finalUrl);

    state = {
      "url": finalUrl,
      "platform": platform,
    };
    print('completed the submit function');
  }

  void setUrlFromWeb(String url) {
    final platform = PlatformDetector.detectPlatform(url);

    state = {
      "url": url,
      "platform": platform,
    };
  }

  void goHome() {
    const home = "https://www.google.com";
    state = {
      "url": home,
      "platform": PlatformDetector.detectPlatform(home),
    };
    print('Reached the goHomeFunction');
  }

  String getFinalUrl(String input) {
    input = input.trim();

    if (input.startsWith("http://") || input.startsWith("https://")) {
      return input;
    } else if (input.startsWith("www")) {
      return "https://$input";
    } else if (input.contains(".") && !input.contains(" ")) {
      return "https://$input";
    } else {
      return "https://www.google.com/search?q=${Uri.encodeComponent(input)}";
    }
  }
}

final BrowserURl =
StateNotifierProvider<BrowserData, Map<String, String>>(
      (ref) => BrowserData(),
);
