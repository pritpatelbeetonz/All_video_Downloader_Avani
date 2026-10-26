import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:gallery_saver_plus/gallery_saver.dart';
import '../../../utility/app_storage_utils.dart';
import '../../../firebase_analysis.dart';

const MethodChannel _nativeChannel = MethodChannel("nativeChannel");

void _acquireNativeWakeLock() {
  try {
    _nativeChannel.invokeMethod('acquireWakeLock');
  } catch (_) {}
}

void _releaseNativeWakeLockIfIdle(List<DownloadTask> tasks) {
  final hasActive = tasks.any((t) => t.status == DownloadStatus.downloading);
  if (!hasActive) {
    try {
      _nativeChannel.invokeMethod('releaseWakeLock');
    } catch (_) {}
  }
}

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
    final sanitizedName = AppStorageUtils.sanitizeFileName(rawName, extension: extension);
    final filePath = AppStorageUtils.getUniqueFilePath(directory.path, sanitizedName);
    final fileName = filePath.split(Platform.pathSeparator).last;
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
    final task = state.where((t) => t.id == taskId).firstOrNull;
    FirebaseAnalyticsService.logDownloadFailed(
      stage: 'extraction',
      platform: task?.platform ?? 'unknown',
      extractor: (task?.isApiHut ?? false) ? 'apihut' : 'smdownloader',
      reason: error ?? 'Extraction failed',
    );
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
    final sanitizedName = AppStorageUtils.sanitizeFileName(rawName, extension: extension);
    final filePath = AppStorageUtils.getUniqueFilePath(directory.path, sanitizedName);
    final fileName = filePath.split(Platform.pathSeparator).last;

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

  Future<void> _executeDownload(DownloadTask initialTask) async {
    const int maxRetries = 5;
    int attempt = 0;
    _acquireNativeWakeLock();

    final file = File(initialTask.filePath);
    bool rangeDisabled = false;

    while (attempt < maxRetries) {
      final currentTask = state.firstWhere((t) => t.id == initialTask.id, orElse: () => initialTask);
      if (currentTask.status == DownloadStatus.paused ||
          currentTask.status == DownloadStatus.cancelled) {
        _releaseNativeWakeLockIfIdle(state);
        return;
      }

      int existingBytes = 0;
      if (!rangeDisabled && file.existsSync()) {
        try {
          existingBytes = file.lengthSync();
        } catch (_) {}
      }

      // If existingBytes >= totalBytes (file on disk is already full or beyond),
      // requesting Range past totalBytes will trigger 416 Range Not Satisfiable!
      if (currentTask.totalBytes > 0 && existingBytes >= currentTask.totalBytes) {
        existingBytes = 0;
        try {
          if (file.existsSync()) file.deleteSync();
        } catch (_) {}
      }

      int totalBytes = currentTask.totalBytes;
      IOSink? sink;

      try {
        final options = Options(
          responseType: ResponseType.stream,
          headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
            'Accept': '*/*',
            if (existingBytes > 0 && !rangeDisabled) 'Range': 'bytes=$existingBytes-',
          },
          sendTimeout: const Duration(seconds: 25),
          receiveTimeout: const Duration(seconds: 60),
        );

        final response = await _dio.get<ResponseBody>(
          currentTask.url,
          options: options,
          cancelToken: currentTask.cancelToken,
        );

        final statusCode = response.statusCode ?? 200;
        final isPartial = statusCode == 206;

        if (isPartial) {
          final contentRange = response.headers.value('content-range');
          if (contentRange != null) {
            final match = RegExp(r'/(\d+)').firstMatch(contentRange);
            if (match != null) {
              totalBytes = int.tryParse(match.group(1)!) ?? totalBytes;
            }
          }
        } else {
          // Full 200 response, start from byte 0
          existingBytes = 0;
          final contentLength = response.headers.value('content-length');
          if (contentLength != null) {
            totalBytes = int.tryParse(contentLength) ?? totalBytes;
          }
        }

        sink = file.openWrite(mode: isPartial ? FileMode.append : FileMode.write);
        int received = existingBytes;
        int lastTime = DateTime.now().millisecondsSinceEpoch;
        int lastBytes = received;

        await for (final chunk in response.data!.stream) {
          final liveTask = state.firstWhere((t) => t.id == initialTask.id, orElse: () => initialTask);
          if (liveTask.status == DownloadStatus.paused ||
              liveTask.status == DownloadStatus.cancelled) {
            await sink.flush();
            await sink.close();
            _releaseNativeWakeLockIfIdle(state);
            return;
          }

          sink.add(chunk);
          received += chunk.length;

          final now = DateTime.now().millisecondsSinceEpoch;
          final timeDiff = (now - lastTime) / 1000;
          String speed = currentTask.speed;
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

          final progress = totalBytes > 0 ? (received / totalBytes).clamp(0.0, 1.0) : 0.0;

          _updateTask(initialTask.id, (t) => t.copyWith(
            progress: progress,
            receivedBytes: received,
            totalBytes: totalBytes > 0 ? totalBytes : t.totalBytes,
            speed: speed,
            status: DownloadStatus.downloading,
          ));
        }

        await sink.flush();
        await sink.close();

        _updateTask(initialTask.id, (t) => t.copyWith(
          progress: 1.0,
          receivedBytes: totalBytes > 0 ? totalBytes : received,
          totalBytes: totalBytes > 0 ? totalBytes : received,
          status: DownloadStatus.completed,
          speed: 'Done',
        ));

        Fluttertoast.showToast(msg: "Download completed: ${initialTask.fileName}");
        _releaseNativeWakeLockIfIdle(state);

        // 📊 Firebase Event: Log download completion with extractor info
        FirebaseAnalyticsService.logDownloadCompleted(
          platform: initialTask.platform ?? 'unknown',
          extractor: initialTask.isApiHut ? 'apihut' : 'smdownloader',
          isFallback: initialTask.isApiHut,
        );

        // Save a copy to public Gallery/MediaStore if it's a video file
        if (initialTask.filePath.toLowerCase().endsWith(".mp4") ||
            initialTask.filePath.toLowerCase().endsWith(".mkv") ||
            initialTask.filePath.toLowerCase().endsWith(".webm")) {
          try {
            await GallerySaver.saveVideo(initialTask.filePath, albumName: 'Video_downloader');
          } catch (e) {
            debugPrint("GallerySaver error: $e");
          }
        }
        return; // Success, exit retry loop!

      } catch (e) {
        if (sink != null) {
          try {
            await sink.flush();
            await sink.close();
          } catch (_) {}
        }

        if (e is DioException && CancelToken.isCancel(e)) {
          _releaseNativeWakeLockIfIdle(state);
          return;
        }

        // SPECIFIC 416 (Range Not Satisfiable) HANDLING:
        final is416 = (e is DioException && e.response?.statusCode == 416) ||
            e.toString().contains('416');

        if (is416) {
          debugPrint("⚠️ [DOWNLOAD_STREAM] 416 Range Not Satisfiable. Resetting file and retrying from byte 0 without Range.");
          try {
            _nativeChannel.invokeMethod('logNative', {
              'tag': 'DOWNLOADER',
              'message': '⚠️ [STREAM_416_RESET] 416 Range Not Satisfiable. Resetting file and fetching from byte 0.',
            });
          } catch (_) {}

          rangeDisabled = true;
          existingBytes = 0;
          try {
            if (file.existsSync()) file.deleteSync();
          } catch (_) {
            try {
              file.writeAsBytesSync([], flush: true);
            } catch (_) {}
          }

          _updateTask(initialTask.id, (t) => t.copyWith(
            speed: 'Restarting...',
            progress: 0.0,
            receivedBytes: 0,
          ));
          await Future.delayed(const Duration(milliseconds: 300));
          continue; // Re-attempt immediately without Range header
        }

        debugPrint("❌ [DOWNLOAD_STREAM_ERROR] Attempt $attempt: $e");
        try {
          _nativeChannel.invokeMethod('logNative', {
            'tag': 'DOWNLOADER',
            'message': '❌ [STREAM_ERROR] Attempt $attempt: $e',
          });
        } catch (_) {}

        attempt++;
        if (attempt < maxRetries) {
          _updateTask(initialTask.id, (t) => t.copyWith(
            speed: 'Reconnecting (attempt $attempt/$maxRetries)...',
            status: DownloadStatus.downloading,
          ));
          // Wait before retrying to give network / screen resume time
          await Future.delayed(Duration(seconds: attempt * 2));
        } else {
          _updateTask(initialTask.id, (t) => t.copyWith(
            status: DownloadStatus.failed,
            errorMessage: 'Download interrupted. Please check connection and tap Retry.',
            speed: 'Failed',
          ));
          Fluttertoast.showToast(msg: "Download interrupted");
          FirebaseAnalyticsService.logDownloadFailed(
            stage: 'download',
            platform: initialTask.platform ?? 'unknown',
            extractor: initialTask.isApiHut ? 'apihut' : 'smdownloader',
            reason: e.toString(),
          );
          _releaseNativeWakeLockIfIdle(state);
        }
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
    _releaseNativeWakeLockIfIdle(state);
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
    _releaseNativeWakeLockIfIdle(state);
  }

  void retryDownload(String id) {
    final taskIndex = state.indexWhere((t) => t.id == id);
    if (taskIndex == -1) return;
    final task = state[taskIndex];

    // Clean up any stale partial/failed file on disk so retry starts fresh
    try {
      final file = File(task.filePath);
      if (file.existsSync()) file.deleteSync();
    } catch (_) {}

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
