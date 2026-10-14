import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get/get.dart';
import 'package:video_downloader/features/browser/providers/download_task_provider.dart';
import 'package:video_downloader/features/home/providers/bottom_nav_provider.dart';
import 'package:video_downloader/settingpage/settingpage.dart';
import 'package:video_player/video_player.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../videoPlayer/video_player.dart';

import '../../utility/app_storage_utils.dart';

class ActivityLibrary extends ConsumerStatefulWidget {
  const ActivityLibrary({super.key});

  @override
  ConsumerState<ActivityLibrary> createState() => ActivityLibraryState();
}

class ActivityLibraryState extends ConsumerState<ActivityLibrary> {
  List<File> downloadedVideos = [];
  bool isLoadingDownloaded = true;
  final Map<String, Uint8List?> thumbnailCache = {};
  final Map<String, String> durationCache = {};

  @override
  void initState() {
    super.initState();
    loadDownloadedVideos();
  }

  // Create the thumbnail
  Future<Uint8List?> getThumbnail(String path) async {
    if (thumbnailCache.containsKey(path)) {
      return thumbnailCache[path];
    }

    try {
      final bytes = await VideoThumbnail.thumbnailData(
        video: path,
        imageFormat: ImageFormat.JPEG,
        quality: 75,
      );

      if (bytes != null && bytes.isNotEmpty) {
        thumbnailCache[path] = bytes;
        return bytes;
      }
    } catch (e) {
      debugPrint("Thumbnail error: $e");
    }

    thumbnailCache[path] = null;
    return null;
  }

  // Load the downloaded videos
  Future<void> loadDownloadedVideos() async {
    if (!mounted) return;
    setState(() => isLoadingDownloaded = true);

    try {
      final directories = await AppStorageUtils.getAllDownloadDirectories();
      final List<File> allVideos = [];

      for (final directory in directories) {
        if (!directory.existsSync()) continue;
        final files = directory.listSync();
        final videos = files
            .whereType<File>()
            .where((file) {
              final p = file.path.toLowerCase();
              return p.endsWith(".mp4") ||
                  p.endsWith(".mkv") ||
                  p.endsWith(".webm") ||
                  p.endsWith(".mov") ||
                  p.endsWith(".avi") ||
                  p.endsWith(".flv");
            });
        allVideos.addAll(videos);
      }

      // De-duplicate by file name in case same file is in multiple folders
      final seenNames = <String>{};
      final uniqueVideos = allVideos.where((f) {
        final name = f.uri.pathSegments.last;
        return seenNames.add(name);
      }).toList()
        ..sort((a, b) {
          try {
            return b.lastModifiedSync().compareTo(a.lastModifiedSync());
          } catch (_) {
            return 0;
          }
        });

      if (!mounted) return;
      setState(() {
        downloadedVideos = uniqueVideos;
        isLoadingDownloaded = false;
      });

      for (var video in uniqueVideos) {
        getThumbnail(video.path);
      }
    } catch (e) {
      debugPrint("Error loading downloaded videos: $e");
      if (!mounted) return;
      setState(() {
        isLoadingDownloaded = false;
      });
    }
  }

  // Video duration helper
  Future<String> getVideoDuration(String path) async {
    if (durationCache.containsKey(path)) {
      return durationCache[path]!;
    }

    final controller = VideoPlayerController.file(File(path));
    await controller.initialize();

    final duration = controller.value.duration;
    controller.dispose();

    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;

    final result = "$minutes min $seconds sec";
    durationCache[path] = result;

    return result;
  }

  @override
  Widget build(BuildContext context) {
    // Auto-refresh when user switches to Library tab (index 1)
    ref.listen<int>(bottomNavProvider, (prev, next) {
      if (next == 1 && prev != 1) {
        loadDownloadedVideos();
      }
    });

    // Auto-refresh when any download task completes
    ref.listen<List<DownloadTask>>(downloadTaskProvider, (prev, next) {
      final prevCompleted = prev?.where((t) => t.status == DownloadStatus.completed).length ?? 0;
      final nextCompleted = next.where((t) => t.status == DownloadStatus.completed).length;
      if (nextCompleted != prevCompleted) {
        loadDownloadedVideos();
      }
    });

    return Scaffold(
      backgroundColor: const Color(0xFFFAFAFA),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: const Color(0xFFFAFAFA),
        title: Text(
          'video_downloader'.tr,
          style: const TextStyle(fontWeight: FontWeight.w600, color: Colors.black),
        ),
        actions: [
          IconButton(
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => Settingpage()),
              );
            },
            icon: Image.asset(
              "assets/icons/settings.png",
              width: 24,
              height: 24,
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: urlVideosScreen(),
      ),
    );
  }

  Widget urlVideosScreen() {
    if (isLoadingDownloaded) {
      return const Center(child: CircularProgressIndicator());
    }

    if (downloadedVideos.isEmpty) {
      return emptyState();
    }

    return RefreshIndicator(
      color: const Color(0xFFFE7338),
      backgroundColor: const Color(0xFFFFE3D7),
      onRefresh: loadDownloadedVideos,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 10),
        separatorBuilder: (context, index) => const SizedBox(height: 12),
        itemCount: downloadedVideos.length,
        itemBuilder: (context, index) {
          final video = downloadedVideos[index];

          return ListTile(
            tileColor: Colors.white,
            minLeadingWidth: 86,
            visualDensity: const VisualDensity(vertical: 4),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 8,
            ),
            leading: SizedBox(
              width: 72,
              height: 72,
              child: buildThumbnail(video.path),
            ),
            title: Text(
              video.path.split('/').last,
              style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 16),
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: FutureBuilder<String>(
              future: getVideoDuration(video.path),
              builder: (context, snapshot) {
                if (!snapshot.hasData) {
                  return Text('loading'.tr);
                }
                return Text(
                  snapshot.data!,
                  style: const TextStyle(color: Colors.grey, fontSize: 13),
                );
              },
            ),
            trailing: Image.asset(
              "assets/icons/arrow.png",
              width: 26,
              height: 26,
            ),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => VideoPlayerPage(videoFile: video),
                ),
              );
            },
          );
        },
      ),
    );
  }

  Widget buildThumbnail(String path) {
    final cached = thumbnailCache[path];

    if (cached != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: Image.memory(
          cached,
          width: 72,
          height: 72,
          fit: BoxFit.cover,
        ),
      );
    }

    return FutureBuilder(
      future: getThumbnail(path),
      builder: (_, snapshot) {
        if (snapshot.connectionState == ConnectionState.done &&
            snapshot.data != null) {
          return ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Image.memory(
              snapshot.data!,
              width: 72,
              height: 72,
              fit: BoxFit.cover,
            ),
          );
        }

        return Container(
          width: 72,
          height: 72,
          color: Colors.grey.shade300,
          child: const Icon(Icons.video_file, color: Colors.grey),
        );
      },
    );
  }

  Widget emptyState() {
    return RefreshIndicator(
      color: const Color(0xFFFE7338),
      backgroundColor: const Color(0xFFFFE3D7),
      onRefresh: loadDownloadedVideos,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          SizedBox(height: MediaQuery.of(context).size.height * 0.22),
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Image.asset(
                  "assets/icons/empty icon.png",
                  width: 64,
                  height: 64,
                  fit: BoxFit.contain,
                ),
                const SizedBox(height: 16),
                Text(
                  'no_videos_here_yet'.tr,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                Text(
                  'downloaded_videos_appear_here'.tr,
                  style: const TextStyle(fontSize: 14, color: Color(0xff8E8E8E)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
