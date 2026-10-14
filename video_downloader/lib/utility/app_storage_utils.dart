import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

class AppStorageUtils {
  AppStorageUtils._();

  static const String publicDownloadPath = "/storage/emulated/0/Download/Video_downloader";

  /// Kept as safe no-op so no OS permission popups interrupt the user.
  /// Downloads are saved without requiring runtime permission dialogs.
  static Future<void> requestPermissions() async {
    // No-op: Do not prompt user with system permission dialogs
  }

  /// Gets the best directory to save downloaded videos without triggering permission dialogs.
  /// 1. Tries public /storage/emulated/0/Download/Video_downloader directly.
  /// 2. If blocked by Android Scoped Storage, falls back to app external downloads dir.
  static Future<Directory> getDownloadDirectory() async {
    if (Platform.isAndroid) {
      // 1. Try public downloads folder
      try {
        final publicDir = Directory(publicDownloadPath);
        if (!await publicDir.exists()) {
          await publicDir.create(recursive: true);
        }
        // Verify write access
        final testFile = File("${publicDir.path}/.test_access_${DateTime.now().millisecondsSinceEpoch}");
        await testFile.writeAsString("ok");
        await testFile.delete();
        return publicDir;
      } catch (e) {
        debugPrint("Public Download dir not directly writable ($e). Using scoped storage fallback.");
      }

      // 2. Try app external downloads directory (No OS Permission 13 error ever)
      try {
        final extDirs = await getExternalStorageDirectories(type: StorageDirectory.downloads);
        if (extDirs != null && extDirs.isNotEmpty) {
          final dir = Directory("${extDirs.first.path}/Video_downloader");
          if (!await dir.exists()) await dir.create(recursive: true);
          return dir;
        }
      } catch (e) {
        debugPrint("External downloads dir error: $e");
      }

      // 3. Try app external storage directory
      try {
        final extDir = await getExternalStorageDirectory();
        if (extDir != null) {
          final dir = Directory("${extDir.path}/Video_downloader");
          if (!await dir.exists()) await dir.create(recursive: true);
          return dir;
        }
      } catch (e) {
        debugPrint("External storage dir error: $e");
      }
    }

    // 4. Default to app documents directory
    final docDir = await getApplicationDocumentsDirectory();
    final dir = Directory("${docDir.path}/Video_downloader");
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  /// Returns all directories where videos might be stored so ActivityLibrary can scan them all.
  static Future<List<Directory>> getAllDownloadDirectories() async {
    final list = <Directory>[];

    try {
      final publicDir = Directory(publicDownloadPath);
      if (await publicDir.exists()) list.add(publicDir);
    } catch (_) {}

    if (Platform.isAndroid) {
      try {
        final extDirs = await getExternalStorageDirectories(type: StorageDirectory.downloads);
        if (extDirs != null) {
          for (final d in extDirs) {
            final sub = Directory("${d.path}/Video_downloader");
            if (await sub.exists()) list.add(sub);
          }
        }
      } catch (_) {}

      try {
        final extDir = await getExternalStorageDirectory();
        if (extDir != null) {
          final sub = Directory("${extDir.path}/Video_downloader");
          if (await sub.exists()) list.add(sub);
        }
      } catch (_) {}
    }

    try {
      final docDir = await getApplicationDocumentsDirectory();
      final sub = Directory("${docDir.path}/Video_downloader");
      if (await sub.exists()) list.add(sub);
    } catch (_) {}

    return list;
  }

  /// Sanitizes file names to remove illegal filesystem characters, slashes, emojis, and limits length
  static String sanitizeFileName(String name, {String extension = 'mp4'}) {
    // Strip file extension if already present
    var base = name;
    if (base.toLowerCase().endsWith('.$extension')) {
      base = base.substring(0, base.length - (extension.length + 1));
    }

    // Remove illegal characters: \ / : * ? " < > |
    var clean = base
        .replaceAll(RegExp(r'[\\/:*?"<>|]'), '_')
        // Remove emoji characters and surrogate pairs
        .replaceAll(RegExp(r'[\u{1F000}-\u{1FFFF}|\u{2600}-\u{27BF}]', unicode: true), '')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();

    if (clean.isEmpty) {
      clean = 'video_${DateTime.now().millisecondsSinceEpoch}';
    }

    // Limit length to 60 chars to avoid filesystem name length violations
    if (clean.length > 60) {
      clean = clean.substring(0, 60).trim();
    }

    return '$clean.$extension';
  }
}
