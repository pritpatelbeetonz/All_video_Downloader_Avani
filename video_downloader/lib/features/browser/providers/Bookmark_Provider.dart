import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

class BookmarkProvider extends StateNotifier<List<String>> {
  BookmarkProvider() : super([]) {
    loadBookmarks();
  }

  Future<void> addBookMark(String url) async {
    if (state.contains(url)) return;

    final updatesList = [...state, url];

    state = updatesList;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList("bookmarks", updatesList);
  }

  Future<void> removeBookMark(String url) async {
    final updatedList = state.where((e) => e != url).toList();

    state = updatedList;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList("bookmarks", updatedList);
  }

  Future<void> loadBookmarks() async {
    final prefs = await SharedPreferences.getInstance();
    final savedList = prefs.getStringList("bookmarks") ?? [];

    state = savedList;
  }
}final bookMarksProvider =StateNotifierProvider<BookmarkProvider , List<String>>((ref)=>BookmarkProvider());
