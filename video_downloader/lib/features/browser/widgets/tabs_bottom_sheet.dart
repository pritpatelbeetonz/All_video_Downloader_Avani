import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get/get.dart';
import '../providers/Browser_data_provider.dart';
import '../providers/SearchToBrowser.dart';
import '../providers/Tab_provider.dart';

/// Chrome-like square tab badge button showing current open tab count
class TabBadgeButton extends ConsumerWidget {
  final VoidCallback onTap;
  final Color? color;
  final EdgeInsetsGeometry? margin;

  const TabBadgeButton({
    super.key,
    required this.onTap,
    this.color,
    this.margin,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tabState = ref.watch(tabsProvider);
    final tabs = tabState["tabs"] as List? ?? [];
    final count = tabs.length;

    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: onTap,
      child: Container(
        width: 26,
        height: 26,
        margin: margin ?? const EdgeInsets.symmetric(horizontal: 4),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(6),
          border: Border.all(
            color: color ?? Colors.black87,
            width: 1.8,
          ),
        ),
        child: Text(
          '$count',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.bold,
            color: color ?? Colors.black87,
          ),
        ),
      ),
    );
  }
}

class TabsBottomSheet extends ConsumerWidget {
  final ScrollController? scrollController;

  const TabsBottomSheet({
    super.key,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tabState = ref.watch(tabsProvider);
    final tabs = (tabState["tabs"] as List? ?? [])
        .map((e) => Map<String, String>.from(e as Map))
        .toList();
    final currentIndex = (tabState["currentIndex"] as int?) ?? 0;

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.7,
      ),
      decoration: const BoxDecoration(
        color: Color(0xFFFAFAFA),
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Drag handle
            Container(
              margin: const EdgeInsets.only(top: 10, bottom: 8),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade400,
                borderRadius: BorderRadius.circular(2),
              ),
            ),

            // Header: Title & New Tab button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Text(
                        'tabs'.tr,
                        style: const TextStyle(
                          fontSize: 19,
                          fontWeight: FontWeight.bold,
                          color: Colors.black87,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFE7338).withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          "${tabs.length}",
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFFE7338),
                          ),
                        ),
                      ),
                    ],
                  ),
                  TextButton.icon(
                    style: TextButton.styleFrom(
                      foregroundColor: const Color(0xFFFE7338),
                    ),
                    onPressed: () {
                      ref
                          .read(tabsProvider.notifier)
                          .addTab("https://www.google.com", "New Tab");
                      ref
                          .read(BrowserURl.notifier)
                          .submit("https://www.google.com");
                      Navigator.pop(context);
                      // Close browser view so user lands on Search Page to type or select quick access
                      ref.read(browserViewProvider.notifier).closeBrowser();
                    },
                    icon: const Icon(Icons.add, size: 20),
                    label: Text(
                      'new_tab'.tr,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),

            // Tabs List
            Flexible(
              child: ListView.separated(
                controller: scrollController,
                shrinkWrap: true,
                padding: const EdgeInsets.all(16),
                itemCount: tabs.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final isActive = index == currentIndex;
                  final tab = tabs[index];
                  final title = tab["title"]?.isNotEmpty == true
                      ? tab["title"]!
                      : "Tab ${index + 1}";
                  final url = tab["url"] ?? "https://www.google.com";

                  return InkWell(
                    borderRadius: BorderRadius.circular(14),
                    onTap: () {
                      ref.read(tabsProvider.notifier).switchTab(index);
                      ref.read(BrowserURl.notifier).submit(url);
                      Navigator.pop(context);
                      ref.read(browserViewProvider.notifier).openBrowser();
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 12,
                      ),
                      decoration: BoxDecoration(
                        color: isActive
                            ? const Color(0xFFFE7338).withValues(alpha: 0.08)
                            : Colors.white,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: isActive
                              ? const Color(0xFFFE7338)
                              : Colors.grey.shade300,
                          width: isActive ? 1.8 : 1,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.03),
                            blurRadius: 4,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Row(
                        children: [
                          Icon(
                            Icons.tab,
                            size: 22,
                            color: isActive
                                ? const Color(0xFFFE7338)
                                : Colors.grey.shade600,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  title,
                                  style: TextStyle(
                                    fontSize: 14,
                                    fontWeight: isActive
                                        ? FontWeight.bold
                                        : FontWeight.w600,
                                    color: isActive
                                        ? const Color(0xFFFE7338)
                                        : Colors.black87,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  url,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: Colors.grey.shade600,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                          if (isActive)
                            Container(
                              margin: const EdgeInsets.symmetric(horizontal: 6),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: const Color(0xFFFE7338),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                'active_badge'.tr,
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          IconButton(
                            icon: const Icon(Icons.close, size: 18),
                            color: Colors.grey.shade700,
                            splashRadius: 18,
                            onPressed: () {
                              ref.read(tabsProvider.notifier).removeTab(index);
                              final updatedState = ref.read(tabsProvider);
                              final newIdx =
                                  updatedState["currentIndex"] as int;
                              final updatedTabsList =
                                  updatedState["tabs"] as List;
                              if (updatedTabsList.isNotEmpty &&
                                  newIdx < updatedTabsList.length) {
                                final activeTabUrl =
                                    updatedTabsList[newIdx]["url"] ??
                                        "https://www.google.com";
                                ref
                                    .read(BrowserURl.notifier)
                                    .submit(activeTabUrl);
                              }
                            },
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
