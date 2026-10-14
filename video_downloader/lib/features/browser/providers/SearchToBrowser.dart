import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Controls whether Browser is visible inside SearchPage
final browserViewProvider = StateNotifierProvider<BrowserViewNotifier, bool>(
      (ref) => BrowserViewNotifier(),
);

class BrowserViewNotifier extends StateNotifier<bool> {
  BrowserViewNotifier() : super(false);

  /// Open Browser
  void openBrowser() {
    state = true;
  }

  /// Close Browser (go back to SearchPage)
  void closeBrowser() {
    state = false;
  }

  /// Toggle (optional)
  void toggle() {
    state = !state;
  }
}