import 'package:webview_flutter/webview_flutter.dart';

import '../../../import_all.dart';
import '../providers/SearchToBrowser.dart';
import 'Browser.dart';

class BrowserWrapper extends ConsumerStatefulWidget {
  const BrowserWrapper({super.key});

  @override
  ConsumerState<BrowserWrapper> createState() => _BrowserWrapperState();
}

class _BrowserWrapperState extends ConsumerState<BrowserWrapper> {
  // We no longer strictly need the controller here for the back button,
  // but keeping the variable for onControllerReady is fine.
  WebViewController? controller;

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false, // We still intercept to handle the custom exit logic
      onPopInvoked: (didPop) {
        if (didPop) return;

        // 🚀 DIRECT EXIT: Skip browser history and close the browser view
        ref.read(browserViewProvider.notifier).closeBrowser();
      },
      child: Browser(
        onControllerReady: (ctrl) {
          controller = ctrl;
        },
      ),
    );
  }
}