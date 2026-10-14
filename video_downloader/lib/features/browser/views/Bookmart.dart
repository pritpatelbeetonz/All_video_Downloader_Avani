import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get/get.dart';
import '../providers/Bookmark_Provider.dart';
import '../providers/Browser_data_provider.dart';

class BookMark extends ConsumerStatefulWidget {
  const BookMark({super.key});

  @override
  ConsumerState<BookMark> createState() => _BookMarkState();
}

class _BookMarkState extends ConsumerState<BookMark> {

  // @override
  // void initState() {
  //   super.initState();
  //
  //   WidgetsBinding.instance.addPostFrameCallback((_) {
  //     ref.read(bookMarksProvider.notifier).loadBookmarks();
  //   });
  // }

  @override
  Widget build(BuildContext context) {
    final bookmarks = ref.watch(bookMarksProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text('bookmarks'.tr),
        automaticallyImplyLeading: false,
        leading: IconButton(
          icon: Image.asset("assets/icons/back.png", height: 30),
          onPressed: () {
            Navigator.pop(context);
          },
        ),
        centerTitle: true,
        elevation: 0,
      ),

      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [

            /// Search Bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: const Color(0xffE9E9E9),
                borderRadius: BorderRadius.circular(30),
              ),
              child: TextField(
                decoration: InputDecoration(
                  border: InputBorder.none,
                  icon: const Icon(Icons.search),
                  hintText: 'search_bookmarks'.tr,
                ),
              ),
            ),

            const SizedBox(height: 20),

            /// Title
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'all_bookmarks'.tr,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),

            const SizedBox(height: 10),

            /// Bookmark List
            Expanded(
              child: bookmarks.isEmpty
                  ? Center(child: Text('no_bookmarks_yet'.tr))
                  : ListView.builder(
                itemCount: bookmarks.length,
                itemBuilder: (context, index) {

                  final url = bookmarks[index];
                  final host = Uri.tryParse(url)?.host ?? url;

                  return Container(
                    margin: const EdgeInsets.only(bottom: 12),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 14),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                    ),

                    child: Row(
                      children: [

                        /// Title + URL
                        Expanded(
                          child: GestureDetector(
                            onTap: () {
                              ref.read(BrowserURl.notifier).submit(url);
                              Navigator.pop(context);
                            },
                            child: Column(
                              crossAxisAlignment:
                              CrossAxisAlignment.start,
                              children: [

                                Text(
                                  host,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),

                                const SizedBox(height: 4),

                                Text(
                                  url,
                                  style: const TextStyle(
                                    color: Colors.grey,
                                    fontSize: 13,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                        ),

                        /// Delete Icon
                        GestureDetector(
                          onTap: () {
                            ref
                                .read(bookMarksProvider.notifier)
                                .removeBookMark(url);
                          },
                          child: Image.asset(
                            "assets/icons/delete.png",
                            width: 22,
                            height: 22,
                          ),
                        )
                      ],
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