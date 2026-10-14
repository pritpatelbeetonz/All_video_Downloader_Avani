import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:gallery_saver_plus/gallery_saver.dart';
import '../../../utility/app_storage_utils.dart';

enum DownloadStatus { fetching, downloading, paused, completed, failed, cancelled }

class DownloadTask {
  final String id;
  final String url;
  final String fileName;
  final String filePath;
  final String? thumbnailUrl;
  final String? platform;
  final double progress;
  final int receivedBytes;
  final int totalBytes;
  final String speed;
  final DownloadStatus status;
  final CancelToken? cancelToken;
  final DateTime createdAt;
  final String? errorMessage;
  final bool isApiHut;

  DownloadTask({
    required this.id,
    required this.url,
    required this.fileName,
    required this.filePath,
    this.thumbnailUrl,
    this.platform,
    this.progress = 0.0,
    this.receivedBytes = 0,
    this.totalBytes = 0,
    this.speed = '0 KB/s',
    this.status = DownloadStatus.downloading,
    this.cancelToken,
    required this.createdAt,
    this.errorMessage,
    this.isApiHut = false,
  });

  DownloadTask copyWith({
    String? id,
    String? url,
    String? fileName,
    String? filePath,
    String? thumbnailUrl,
    String? platform,
    double? progress,
    int? receivedBytes,
    int? totalBytes,
    String? speed,
    DownloadStatus? status,
    CancelToken? cancelToken,
    DateTime? createdAt,
    String? errorMessage,
    bool? isApiHut,
  }) {
    return DownloadTask(
      id: id ?? this.id,
      url: url ?? this.url,
      fileName: fileName ?? this.fileName,
      filePath: filePath ?? this.filePath,
      thumbnailUrl: thumbnailUrl ?? this.thumbnailUrl,
      platform: platform ?? this.platform,
      progress: progress ?? this.progress,
      receivedBytes: receivedBytes ?? this.receivedBytes,
      totalBytes: totalBytes ?? this.totalBytes,
      speed: speed ?? this.speed,
      status: status ?? this.status,
      cancelToken: cancelToken ?? this.cancelToken,
      createdAt: createdAt ?? this.createdAt,
      errorMessage: errorMessage ?? this.errorMessage,
      isApiHut: isApiHut ?? this.isApiHut,
    );
  }

  String get formattedReceived {
    if (receivedBytes < 1024 * 1024) {
      return '${(receivedBytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(receivedBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  String get formattedTotal {
    if (totalBytes <= 0) return 'Unknown size';
    if (totalBytes < 1024 * 1024) {
      return '${(totalBytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(totalBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

class DownloadTaskNotifier extends StateNotifier<List<DownloadTask>> {
  DownloadTaskNotifier() : super([]);

  final Dio _dio = Dio();

  /// Immediately creates a task in "fetching" state so it appears in the Downloads tab instantly.
  String createFetchingTask({
    required String pageUrl,
    String? platform,
    String? customFileName,
  }) {
    final id = DateTime.now().millisecondsSinceEpoch.toString();
    final rawName = customFileName ??
        '${platform ?? "video"}_${DateTime.now().millisecondsSinceEpoch}';
    final fileName = AppStorageUtils.sanitizeFileName(rawName);

    final newTask = DownloadTask(
      id: id,
      url: pageUrl,
      fileName: fileName,
      filePath: '',
      platform: platform,
      progress: 0.0,
      speed: 'Fetching video stream...',
      status: DownloadStatus.fetching,
      createdAt: DateTime.now(),
    );

    state = [newTask, ...state];
    return id;
  }

  /// Attaches direct stream URL to an existing fetching task and begins background download.
  Future<void> attachStreamAndDownload({
    required String taskId,
    required String streamUrl,
    String? customFileName,
    String? thumbnailUrl,
    String? platform,
    bool isApiHut = false,
  }) async {
    final taskIndex = state.indexWhere((t) => t.id == taskId);
    if (taskIndex == -1) return;

    final directory = await AppStorageUtils.getDownloadDirectory();

    String extension = streamUrl.contains("/thumb") ? "jpg" : "mp4";
    final rawName = customFileName ??
        '${platform ?? "video"}_${DateTime.now().millisecondsSinceEpoch}';
    final fileName = AppStorageUtils.sanitizeFileName(rawName, extension: extension);
    final filePath = "${directory.path}/$fileName";
    final cancelToken = CancelToken();

    final updatedTask = state[taskIndex].copyWith(
      url: streamUrl,
      fileName: fileName,
      filePath: filePath,
      thumbnailUrl: thumbnailUrl ?? state[taskIndex].thumbnailUrl,
      status: DownloadStatus.downloading,
      cancelToken: cancelToken,
      speed: 'Connecting...',
      isApiHut: isApiHut || state[taskIndex].isApiHut,
    );

    _updateTask(taskId, (_) => updatedTask);
    _executeDownload(updatedTask);
  }

  /// Flags a task as currently using the ApiHut fallback extractor.
  void markUsingApiHut(String taskId) {
    _updateTask(taskId, (t) => t.copyWith(isApiHut: true));
  }

  /// Marks a fetching task as failed if extraction could not succeed.
  void markFetchingFailed(String taskId, [String? error]) {
    _updateTask(taskId, (t) => t.copyWith(
      status: DownloadStatus.failed,
      speed: 'Failed to fetch link',
      errorMessage: error ?? 'Extraction failed',
    ));
  }

  Future<String> startDownload({
    required String url,
    String? customFileName,
    String? platform,
    String? thumbnailUrl,
  }) async {
    final id = DateTime.now().millisecondsSinceEpoch.toString();
    final directory = await AppStorageUtils.getDownloadDirectory();

    String extension = url.contains("/thumb") ? "jpg" : "mp4";
    final rawName = customFileName ??
        '${platform ?? "video"}_${DateTime.now().millisecondsSinceEpoch}';
    final fileName = AppStorageUtils.sanitizeFileName(rawName, extension: extension);
    final filePath = "${directory.path}/$fileName";

    final cancelToken = CancelToken();

    final newTask = DownloadTask(
      id: id,
      url: url,
      fileName: fileName,
      filePath: filePath,
      thumbnailUrl: thumbnailUrl,
      platform: platform,
      progress: 0.0,
      status: DownloadStatus.downloading,
      cancelToken: cancelToken,
      createdAt: DateTime.now(),
    );

    state = [newTask, ...state];

    _executeDownload(newTask);
    return id;
  }

  Future<void> _executeDownload(DownloadTask task) async {
    int lastTime = DateTime.now().millisecondsSinceEpoch;
    int lastBytes = task.receivedBytes;

    try {
      await _dio.download(
        task.url,
        task.filePath,
        cancelToken: task.cancelToken,
        deleteOnError: false,
        onReceiveProgress: (received, total) {
          final now = DateTime.now().millisecondsSinceEpoch;
          final timeDiff = (now - lastTime) / 1000;

          String speed = task.speed;
          if (timeDiff >= 0.5) {
            final bytesDiff = received - lastBytes;
            final bytesPerSec = bytesDiff / timeDiff;
            if (bytesPerSec >= 1024 * 1024) {
              speed = '${(bytesPerSec / (1024 * 1024)).toStringAsFixed(1)} MB/s';
            } else {
              speed = '${(bytesPerSec / 1024).toStringAsFixed(0)} KB/s';
            }
            lastTime = now;
            lastBytes = received;
          }

          final progress = total > 0 ? (received / total).clamp(0.0, 1.0) : 0.0;

          _updateTask(task.id, (t) => t.copyWith(
            progress: progress,
            receivedBytes: received,
            totalBytes: total > 0 ? total : t.totalBytes,
            speed: speed,
            status: DownloadStatus.downloading,
          ));
        },
      );

      _updateTask(task.id, (t) => t.copyWith(
        progress: 1.0,
        status: DownloadStatus.completed,
        speed: 'Done',
      ));

      Fluttertoast.showToast(msg: "Download completed: ${task.fileName}");

      // Save a copy to public Gallery/MediaStore if it's a video file
      if (task.filePath.toLowerCase().endsWith(".mp4") ||
          task.filePath.toLowerCase().endsWith(".mkv") ||
          task.filePath.toLowerCase().endsWith(".webm")) {
        try {
          await GallerySaver.saveVideo(task.filePath, albumName: 'Video_downloader');
        } catch (e) {
          debugPrint("GallerySaver error: $e");
        }
      }
    } catch (e) {
      if (e is DioException && CancelToken.isCancel(e)) {
        // Paused or cancelled
      } else {
        _updateTask(task.id, (t) => t.copyWith(
          status: DownloadStatus.failed,
          errorMessage: e.toString(),
          speed: 'Failed',
        ));
        Fluttertoast.showToast(msg: "Download failed");
      }
    }
  }

  void pauseDownload(String id) {
    final taskIndex = state.indexWhere((t) => t.id == id);
    if (taskIndex == -1) return;
    final task = state[taskIndex];
    task.cancelToken?.cancel("Paused by user");

    _updateTask(id, (t) => t.copyWith(
      status: DownloadStatus.paused,
      speed: 'Paused',
    ));
  }

  void resumeDownload(String id) {
    final taskIndex = state.indexWhere((t) => t.id == id);
    if (taskIndex == -1) return;
    final task = state[taskIndex];

    final newCancelToken = CancelToken();
    final updatedTask = task.copyWith(
      status: DownloadStatus.downloading,
      cancelToken: newCancelToken,
      speed: 'Resuming...',
    );

    _updateTask(id, (_) => updatedTask);
    _executeDownload(updatedTask);
  }

  void cancelDownload(String id) {
    final taskIndex = state.indexWhere((t) => t.id == id);
    if (taskIndex == -1) return;
    final task = state[taskIndex];
    task.cancelToken?.cancel("Cancelled by user");

    // Try deleting partial file if exists
    try {
      final file = File(task.filePath);
      if (file.existsSync()) file.deleteSync();
    } catch (_) {}

    state = state.where((t) => t.id != id).toList();
    Fluttertoast.showToast(msg: "Download cancelled");
  }

  void retryDownload(String id) {
    final taskIndex = state.indexWhere((t) => t.id == id);
    if (taskIndex == -1) return;
    final task = state[taskIndex];

    final newCancelToken = CancelToken();
    final updatedTask = task.copyWith(
      progress: 0.0,
      receivedBytes: 0,
      status: DownloadStatus.downloading,
      cancelToken: newCancelToken,
      speed: 'Connecting...',
    );

    _updateTask(id, (_) => updatedTask);
    _executeDownload(updatedTask);
  }

  void removeTask(String id) {
    state = state.where((t) => t.id != id).toList();
  }

  void clearCompleted() {
    state = state.where((t) => t.status != DownloadStatus.completed).toList();
  }

  void _updateTask(String id, DownloadTask Function(DownloadTask) updater) {
    state = [
      for (final t in state)
        if (t.id == id) updater(t) else t,
    ];
  }
}

final downloadTaskProvider =
    StateNotifierProvider<DownloadTaskNotifier, List<DownloadTask>>((ref) {
  return DownloadTaskNotifier();
});

final activeDownloadsCountProvider = Provider<int>((ref) {
  final tasks = ref.watch(downloadTaskProvider);
  return tasks
      .where((t) =>
          t.status == DownloadStatus.fetching ||
          t.status == DownloadStatus.downloading ||
          t.status == DownloadStatus.paused)
      .length;
});
