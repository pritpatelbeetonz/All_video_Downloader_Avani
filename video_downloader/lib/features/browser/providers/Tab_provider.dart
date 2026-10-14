import 'package:flutter_riverpod/flutter_riverpod.dart';

class TabsNotifier extends StateNotifier<Map<String, dynamic>> {
  TabsNotifier()
      : super({
          "tabs": [
            {"url": "https://www.google.com", "title": "Google"},
          ],
          "currentIndex": 0,
        });

  List<Map<String, String>> get tabs {
    final list = state["tabs"] as List? ?? [];
    return list.map((e) => Map<String, String>.from(e as Map)).toList();
  }

  int get currentIndex => (state['currentIndex'] as int?) ?? 0;

  int get tabCount => (state["tabs"] as List? ?? []).length;

  String get currentUrl {
    final list = tabs;
    final idx = currentIndex;
    if (idx >= 0 && idx < list.length) {
      return list[idx]["url"] ?? "";
    }
    return "";
  }

  // ➕ Add new tab
  void addTab([String url = "https://www.google.com", String title = "New Tab"]) {
    final updated = tabs;
    updated.add({"url": url, "title": title});

    state = {
      "tabs": updated,
      "currentIndex": updated.length - 1,
    };
  }

  // 🔄 Switch tab
  void switchTab(int index) {
    if (index >= 0 && index < tabs.length) {
      state = {
        ...state,
        "currentIndex": index,
      };
    }
  }

  // ✏️ Update URL and title of the active tab
  void updateUrl(String url, {String? title}) {
    if (url.isEmpty) return;

    final updated = tabs;
    final idx = currentIndex;
    if (idx < 0 || idx >= updated.length) return;

    final existingUrl = updated[idx]["url"];
    if (existingUrl == url && title == null) return;

    String resolvedTitle = title ?? updated[idx]["title"] ?? "Page";
    if (title == null) {
      try {
        final host = Uri.parse(url).host.replaceFirst('www.', '');
        if (host.isNotEmpty) {
          resolvedTitle = host;
        }
      } catch (_) {}
    }

    updated[idx] = {
      "url": url,
      "title": resolvedTitle,
    };

    state = {
      ...state,
      "tabs": updated,
    };
  }

  // ❌ Remove tab
  void removeTab(int index) {
    final updated = tabs;

    if (updated.length <= 1) {
      // If closing the last tab, reset it to home instead of an empty browser
      state = {
        "tabs": [
          {"url": "https://www.google.com", "title": "Google"}
        ],
        "currentIndex": 0,
      };
      return;
    }

    if (index < 0 || index >= updated.length) return;

    updated.removeAt(index);

    int newIndex = currentIndex;
    if (newIndex >= updated.length) {
      newIndex = updated.length - 1;
    } else if (index < newIndex) {
      newIndex--;
    }

    state = {
      "tabs": updated,
      "currentIndex": newIndex,
    };
  }
}

final tabsProvider =
    StateNotifierProvider<TabsNotifier, Map<String, dynamic>>(
  (ref) => TabsNotifier(),
);
