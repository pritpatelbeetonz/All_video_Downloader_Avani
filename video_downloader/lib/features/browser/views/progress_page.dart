import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get/get.dart';
import 'package:share_plus/share_plus.dart';
import 'package:video_downloader/features/videoPlayer/video_player.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import '../providers/download_task_provider.dart';

class ProgressPage extends ConsumerStatefulWidget {
  const ProgressPage({super.key});

  @override
  ConsumerState<ProgressPage> createState() => _ProgressPageState();
}

class _ProgressPageState extends ConsumerState<ProgressPage> {
  late PageController _pageController;
  int _selectedTabIndex = 0;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: 0);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final allTasks = ref.watch(downloadTaskProvider);
    final activeTasks = allTasks
        .where((t) =>
            t.status == DownloadStatus.fetching ||
            t.status == DownloadStatus.downloading ||
            t.status == DownloadStatus.paused ||
            t.status == DownloadStatus.failed)
        .toList();
    final completedTasks =
        allTasks.where((t) => t.status == DownloadStatus.completed).toList();

    return Scaffold(
      backgroundColor: const Color(0xFFFAFAFA),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: const Color(0xFFFAFAFA),
        elevation: 0,
        title: Text(
          'downloads'.tr,
          style: const TextStyle(
            fontWeight: FontWeight.w600,
            fontSize: 20,
            color: Colors.black,
          ),
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(48),
          child: Padding(
            padding: const EdgeInsets.only(left: 16, right: 16, bottom: 8),
            child: Row(
              children: [
                _buildTabPill(0, 'active'.tr, activeTasks.length),
                const SizedBox(width: 10),
                _buildTabPill(1, 'completed'.tr, completedTasks.length),
              ],
            ),
          ),
        ),
      ),
      body: PageView(
        controller: _pageController,
        onPageChanged: (index) {
          setState(() {
            _selectedTabIndex = index;
          });
        },
        children: [
          _buildActiveList(activeTasks),
          _buildCompletedList(completedTasks),
        ],
      ),
    );
  }

  Widget _buildTabPill(int index, String title, int count) {
    final isSelected = _selectedTabIndex == index;

    return GestureDetector(
      onTap: () {
        setState(() {
          _selectedTabIndex = index;
        });
        _pageController.animateToPage(
          index,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeInOut,
        );
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 9),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFFFE7338) : const Color(0xFFE9E9EB),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              title,
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 14,
                color: isSelected ? Colors.white : Colors.black,
              ),
            ),
            if (count > 0) ...[
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: isSelected
                      ? Colors.white.withOpacity(0.25)
                      : Colors.black.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  '$count',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: isSelected ? Colors.white : Colors.black87,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildActiveList(List<DownloadTask> tasks) {
    if (tasks.isEmpty) {
      return _emptyState(
        icon: Icons.cloud_download_outlined,
        title: 'no_active_downloads'.tr,
        subtitle: 'progress_start_hint'.tr,
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: tasks.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final task = tasks[index];
        return _activeTaskCard(task);
      },
    );
  }

  Widget _activeTaskCard(DownloadTask task) {
    final notifier = ref.read(downloadTaskProvider.notifier);
    final percentage = (task.progress * 100).toInt();

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: const Color(0xFFFFE3D7),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.video_file_outlined,
                  color: Color(0xFFFE7338),
                  size: 24,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      task.fileName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      task.status == DownloadStatus.fetching
                          ? task.speed
                          : '${task.formattedReceived} / ${task.formattedTotal} • ${task.speed}',
                      style: TextStyle(
                        color: task.status == DownloadStatus.fetching
                            ? const Color(0xFFFE7338)
                            : Colors.grey.shade600,
                        fontWeight: task.status == DownloadStatus.fetching
                            ? FontWeight.w500
                            : FontWeight.normal,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              if (task.status == DownloadStatus.fetching) ...[
                const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation<Color>(Color(0xFFFE7338)),
                  ),
                ),
                const SizedBox(width: 8),
              ] else if (task.status == DownloadStatus.downloading)
                IconButton(
                  icon: const Icon(Icons.pause_circle_filled,
                      color: Color(0xFFFE7338), size: 28),
                  onPressed: () => notifier.pauseDownload(task.id),
                )
              else if (task.status == DownloadStatus.paused)
                IconButton(
                  icon: const Icon(Icons.play_circle_filled,
                      color: Color(0xFFFE7338), size: 28),
                  onPressed: () => notifier.resumeDownload(task.id),
                )
              else if (task.status == DownloadStatus.failed)
                IconButton(
                  icon: const Icon(Icons.replay_circle_filled,
                      color: Colors.redAccent, size: 28),
                  onPressed: () => notifier.retryDownload(task.id),
                ),
              IconButton(
                icon: const Icon(Icons.cancel_outlined,
                    color: Colors.grey, size: 22),
                onPressed: () => notifier.cancelDownload(task.id),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: (task.status == DownloadStatus.fetching || task.progress <= 0)
                  ? null
                  : task.progress,
              backgroundColor: const Color(0xFFFFE3D7),
              valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFFFE7338)),
              minHeight: 6,
            ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                task.status == DownloadStatus.paused
                    ? 'status_paused'.tr
                    : task.status == DownloadStatus.failed
                        ? 'status_failed'.tr
                        : 'status_downloading'.tr,
                style: TextStyle(
                  fontSize: 11,
                  color: task.status == DownloadStatus.failed
                      ? Colors.redAccent
                      : Colors.grey.shade600,
                  fontWeight: FontWeight.w500,
                ),
              ),
              Text(
                '$percentage%',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFFFE7338),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildCompletedList(List<DownloadTask> tasks) {
    if (tasks.isEmpty) {
      return _emptyState(
        icon: Icons.check_circle_outline,
        title: 'no_completed_downloads'.tr,
        subtitle: 'downloaded_files_hint'.tr,
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: tasks.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final task = tasks[index];
        return _completedTaskCard(task);
      },
    );
  }

  void _playVideo(BuildContext context, DownloadTask task) {
    final file = File(task.filePath);
    if (file.existsSync()) {
      AdsVariable.onShowAds(
        context,
        place: 'VD_Play_Video',
        onComplete: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => VideoPlayerPage(videoFile: file),
            ),
          );
        },
      );
    }
  }

  Widget _completedTaskCard(DownloadTask task) {
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () => _playVideo(context, task),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: Colors.green.shade50,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.check_circle,
                color: Colors.green,
                size: 24,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    task.fileName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    task.formattedTotal,
                    style: TextStyle(
                      color: Colors.grey.shade600,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            IconButton(
              icon: const Icon(Icons.play_arrow_rounded,
                  color: Color(0xFFFE7338), size: 28),
              onPressed: () => _playVideo(context, task),
            ),
            IconButton(
              icon: const Icon(Icons.share_outlined,
                  color: Colors.black87, size: 20),
              onPressed: () {
                final file = File(task.filePath);
                if (file.existsSync()) {
                  Share.shareXFiles([XFile(file.path)]);
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 64, color: Colors.grey.shade400),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey.shade600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
