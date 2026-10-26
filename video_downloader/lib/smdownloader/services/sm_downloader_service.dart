import 'dart:async';
import 'dart:developer' as developer;
import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import '../models/sm_session.dart';
import '../models/sm_extraction_result.dart';

const MethodChannel _nativeLogChannel = MethodChannel("nativeChannel");

void _smLog(String msg) {
  developer.log(msg, name: 'SM_DOWNLOADER');
  print('⚡ [SMDownloader] $msg');
  try {
    _nativeLogChannel.invokeMethod('logNative', {
      'tag': 'SM_DOWNLOADER',
      'message': msg,
    });
  } catch (_) {}
}

/// Custom exception for SMDownloader operations.
/// Sanitizes session tokens so they are never exposed in exception strings or logs.
class SmDownloaderException implements Exception {
  final String message;
  final int? statusCode;
  final String? serverCode;

  SmDownloaderException(
    this.message, {
    this.statusCode,
    this.serverCode,
  });

  @override
  String toString() {
    return 'SmDownloaderException(message: $message, statusCode: $statusCode, serverCode: $serverCode)';
  }
}

/// Service handling SMDownloader session lifecycle, proactive refresh,
/// and Instagram media extraction with retry on UNAUTHORIZED responses.
class SmDownloaderService {
  final Dio _dio;

  SmSession? _session;
  Future<SmSession>? _inFlightSessionRequest;

  SmDownloaderService({Dio? dio}) : _dio = dio ?? Dio();

  /// Gets the currently cached session in memory (for testing or inspection).
  SmSession? get currentSession => _session;

  /// Manually set or clear session (primarily for unit tests).
  void setSessionForTesting(SmSession? session) {
    _session = session;
  }

  /// Browser User-Agent to prevent Cloudflare/SMDownloader bot detection
  static const String browserUserAgent =
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

  /// Official list of 29 platforms supported by SMDownloader
  static const List<String> supportedPlatforms = [
    'Instagram',
    'Threads',
    'Bluesky',
    'TikTok',
    'YouTube',
    'Facebook',
    'X',
    'Douyin',
    'SoundCloud',
    'Dailymotion',
    'Xiaohongshu',
    'Weibo',
    'Kuaishou',
    'Reddit',
    'Vimeo',
    'VK',
    'Twitch',
    'Pinterest',
    'Rumble',
    'Bilibili',
    'Snapchat',
    'Streamable',
    'Bandcamp',
    'CHZZK',
    'Naver TV',
    'SOOP',
    'Mixcloud',
    'TED',
    'Loom',
  ];

  /// Validates whether the given URL is a valid HTTPS Instagram URL.
  static bool isValidInstagramUrl(String url) {
    final uri = Uri.tryParse(url.trim());
    if (uri == null) return false;
    if (uri.scheme.toLowerCase() != 'https') return false;

    final host = uri.host.toLowerCase();
    final isInstagramHost = host == 'instagram.com' ||
        host == 'www.instagram.com' ||
        host.endsWith('.instagram.com');
    if (!isInstagramHost) return false;

    // Path must contain reel, reels, p, tv, etc. or at least not be empty
    final path = uri.path.toLowerCase();
    return path.isNotEmpty && path != '/';
  }

  /// Obtains an active session, proactively refreshing if it is expired or expiring within 30 seconds.
  /// Prevents concurrent duplicate calls by sharing in-flight session requests.
  Future<SmSession> getOrRefreshSession({bool forceRefresh = false}) async {
    // 1. Check if existing session is valid and not expiring soon
    if (!forceRefresh && _session != null && !_session!.isExpiringSoon(30000)) {
      print('♻️ [SMDownloader] Reusing active session (Valid for: ${_session!.remainingSeconds}s)');
      return _session!;
    }

    // 2. Proactive refresh announcement if it was expiring soon
    if (!forceRefresh && _session != null && _session!.isExpiringSoon(30000)) {
      print('⏱️ [SMDownloader] Session expiring in <30s (${_session!.remainingSeconds}s remaining). Proactively refreshing...');
    } else if (forceRefresh) {
      print('🔄 [SMDownloader] Force refresh requested. Fetching fresh session...');
    }

    // 3. Share in-flight request if one is already running
    if (_inFlightSessionRequest != null) {
      print('⏳ [SMDownloader] Awaiting in-flight session request...');
      return await _inFlightSessionRequest!;
    }

    // 4. Start new session request
    _inFlightSessionRequest = _fetchNewSession();
    try {
      final newSession = await _inFlightSessionRequest!;
      _session = newSession;
      return newSession;
    } finally {
      _inFlightSessionRequest = null;
    }
  }

  /// Performs `GET https://www.smdownloader.com/api/session`
  Future<SmSession> _fetchNewSession() async {
    _smLog('🔑 Requesting new session from /api/session...');
    try {
      final response = await _dio.get(
        "https://www.smdownloader.com/api/session",
        options: Options(
          headers: {
            "Accept": "*/*",
            "Origin": "https://www.smdownloader.com",
            "Referer": "https://www.smdownloader.com/link-video-downloader",
            "User-Agent": browserUserAgent,
          },
          sendTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 15),
        ),
      );

      final data = response.data;
      if (data == null || data is! Map<String, dynamic>) {
        throw SmDownloaderException(
          "Invalid response received from /api/session",
          statusCode: response.statusCode,
        );
      }

      final token = data["token"]?.toString();
      final expiresAtRaw = data["expiresAt"];

      if (token == null || token.isEmpty) {
        throw SmDownloaderException("Missing session token in /api/session response");
      }

      int? expiresAt;
      if (expiresAtRaw is num) {
        expiresAt = expiresAtRaw.toInt();
      } else if (expiresAtRaw is String) {
        expiresAt = int.tryParse(expiresAtRaw);
      }

      if (expiresAt == null) {
        throw SmDownloaderException("Missing or invalid expiresAt in /api/session response");
      }

      final now = DateTime.now().millisecondsSinceEpoch;
      if (expiresAt <= now) {
        throw SmDownloaderException("Received session token that is already expired");
      }

      final session = SmSession(token: token, expiresAt: expiresAt);
      _smLog('✅ New session obtained (Expires in: ${session.remainingSeconds}s)');
      return session;
    } on DioException catch (e) {
      _smLog('❌ Session network error: ${e.message}');
      throw SmDownloaderException(
        "Failed to obtain SMDownloader session: ${e.message ?? 'Network error'}",
        statusCode: e.response?.statusCode,
      );
    } catch (e) {
      if (e is SmDownloaderException) rethrow;
      _smLog('❌ Session error: $e');
      throw SmDownloaderException("Failed to obtain SMDownloader session: $e");
    }
  }

  /// Cleans tracking query parameters (e.g. ?utm_source=...) from Instagram URL.
  static String cleanInstagramUrl(String url) {
    try {
      final uri = Uri.parse(url.trim());
      // Reconstruct clean URL with scheme, host, and path
      return Uri(
        scheme: uri.scheme,
        host: uri.host,
        path: uri.path,
      ).toString();
    } catch (_) {
      return url.trim();
    }
  }

  /// Resolves relative URLs (e.g. /api/youtube/download?t=...) to full absolute URLs.
  static String resolveUrl(String? url) {
    if (url == null || url.trim().isEmpty) return '';
    final trimmed = url.trim();
    if (trimmed.startsWith('/')) {
      return 'https://www.smdownloader.com$trimmed';
    }
    return trimmed;
  }

  /// Extracts media from any supported platform (Instagram, YouTube, etc.)
  Future<SmExtractionResult> extractMedia(String url) async {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || uri.scheme.toLowerCase() != 'https') {
      throw SmDownloaderException(
        "Invalid URL. Please provide a valid HTTPS link.",
      );
    }

    final cleanedUrl = uri.host.contains('instagram.com')
        ? cleanInstagramUrl(url)
        : url.trim();

    print('🚀 [SMDownloader] Starting media extraction for: $cleanedUrl');
    return _executeExtract(url: cleanedUrl, isRetry: false);
  }

  /// Extracts Instagram media using SMDownloader API.
  /// Handles proactively refreshed sessions and automatic recovery on UNAUTHORIZED responses.
  Future<SmExtractionResult> extractInstagramMedia(String url) async {
    return extractMedia(url);
  }

  Future<SmExtractionResult> _executeExtract({
    required String url,
    required bool isRetry,
  }) async {
    // Step 2: Ensure valid session (proactive refresh if within 30s)
    final session = await getOrRefreshSession(forceRefresh: false);

    try {
      final response = await _dio.post(
        "https://www.smdownloader.com/api/extract",
        data: {"url": url},
        options: Options(
          headers: {
            "Accept": "*/*",
            "Content-Type": "application/json",
            "Origin": "https://www.smdownloader.com",
            "Referer": "https://www.smdownloader.com/link-video-downloader",
            "x-sd-session": session.token,
            "User-Agent": browserUserAgent,
          },
          sendTimeout: const Duration(seconds: 20),
          receiveTimeout: const Duration(seconds: 20),
          validateStatus: (status) => status != null && status < 500,
        ),
      );

      final statusCode = response.statusCode ?? 500;
      final data = response.data;
      _smLog('📦 Response statusCode=$statusCode, ok=${data is Map ? data["ok"] : null}');

      // Check if response indicates unauthorized (HTTP 401 or JSON code == 'UNAUTHORIZED')
      final isUnauthorized = statusCode == 401 ||
          (data is Map<String, dynamic> &&
              data["code"]?.toString().toUpperCase() == "UNAUTHORIZED");

      if (isUnauthorized) {
        if (!isRetry) {
          print('🔄 [SMDownloader] UNAUTHORIZED response received. Invalidating cached session and retrying once...');
          // 1. Invalidate session
          _session = null;
          // 2. Obtain fresh session
          await getOrRefreshSession(forceRefresh: true);
          // 3. Retry exactly once
          final retryResult = await _executeExtract(url: url, isRetry: true);
          print('✅ [SMDownloader] Retry after unauthorized succeeded!');
          return retryResult;
        } else {
          print('❌ [SMDownloader] Retry also returned UNAUTHORIZED. Stopping recovery to prevent loops.');
          throw SmDownloaderException(
            "Session unauthorized and recovery retry failed. Please try again later.",
            statusCode: statusCode,
            serverCode: "UNAUTHORIZED",
          );
        }
      }

      // Check for other error statuses
      if (statusCode >= 400) {
        final serverMsg = (data is Map<String, dynamic>)
            ? data["message"]?.toString()
            : null;
        throw SmDownloaderException(
          serverMsg ?? "Extraction failed with HTTP status $statusCode",
          statusCode: statusCode,
        );
      }

      // Parse JSON response
      if (data is Map<String, dynamic>) {
        // If server returns error object with HTTP 200
        if (data["ok"] == false || data["status"] == "restricted" || data["status"] == "error") {
          final serverMsg = data["message"]?.toString() ?? "Extraction restricted or failed";
          final serverCode = data["code"]?.toString();
          throw SmDownloaderException(
            serverMsg,
            statusCode: statusCode,
            serverCode: serverCode,
          );
        }

        // Parse extracted media URL and thumbnail across multiple possible formats
        String? extractedUrl;
        String? thumbnail;
        String? title = data["title"]?.toString();

        if (data["data"] is List && (data["data"] as List).isNotEmpty) {
          final first = data["data"][0];
          if (first is Map) {
            extractedUrl = first["url"]?.toString() ?? first["video"]?.toString();
            thumbnail = first["thumbnail"]?.toString() ?? first["thumb"]?.toString();
            title ??= first["title"]?.toString();
          } else if (first is String) {
            extractedUrl = first;
          }
        } else if (data["data"] is Map) {
          final map = data["data"] as Map;
          title ??= map["title"]?.toString();
          thumbnail = map["thumbnail"]?.toString() ?? map["thumb"]?.toString();

          // 🎯 SMDownloader response format: data.data.media contains video list
          if (map["media"] is List && (map["media"] as List).isNotEmpty) {
            final mediaList = map["media"] as List;
            // Prefer video type/role if present, else take first
            final videoItem = mediaList.firstWhere(
              (m) => m is Map && (m["type"] == "video" || m["role"] == "video"),
              orElse: () => mediaList.first,
            );
            if (videoItem is Map) {
              extractedUrl = videoItem["url"]?.toString();
              thumbnail ??= videoItem["thumbnail"]?.toString() ?? videoItem["thumb"]?.toString();
              title ??= videoItem["filename"]?.toString();
            }
          }

          extractedUrl ??= map["url"]?.toString() ??
              map["video"]?.toString() ??
              map["videoUrl"]?.toString() ??
              map["downloadUrl"]?.toString();
        } else if (data["media"] is List && (data["media"] as List).isNotEmpty) {
          final first = data["media"][0];
          if (first is Map) {
            extractedUrl = first["url"]?.toString() ?? first["video"]?.toString();
            thumbnail = first["thumbnail"]?.toString();
          }
        } else if (data["medias"] is List && (data["medias"] as List).isNotEmpty) {
          final first = data["medias"][0];
          if (first is Map) {
            extractedUrl = first["url"]?.toString() ?? first["video"]?.toString();
            thumbnail = first["thumbnail"]?.toString();
          }
        } else if (data["url"] != null) {
          extractedUrl = data["url"]?.toString();
          thumbnail = data["thumbnail"]?.toString();
        } else if (data["downloadUrl"] != null) {
          extractedUrl = data["downloadUrl"]?.toString();
        }

        // Resolve relative URLs (e.g. /api/youtube/download?t=...) to full absolute URLs
        if (extractedUrl != null && extractedUrl.isNotEmpty) {
          extractedUrl = resolveUrl(extractedUrl);
        }
        if (thumbnail != null && thumbnail.isNotEmpty) {
          thumbnail = resolveUrl(thumbnail);
        }

        if (extractedUrl != null && extractedUrl.isNotEmpty) {
          print('✅ [SMDownloader] Extraction successful! Stream found: ${extractedUrl.substring(0, extractedUrl.length > 60 ? 60 : extractedUrl.length)}...');
          return SmExtractionResult(
            videoUrl: extractedUrl,
            thumbnailUrl: thumbnail,
            title: title,
            rawJson: data,
          );
        }

        throw SmDownloaderException("No media download link found in extraction response");
      }

      throw SmDownloaderException("Unexpected response format from SMDownloader extraction");
    } on DioException catch (e) {
      if (e.response?.statusCode == 401 && !isRetry) {
        print('🔄 [SMDownloader] HTTP 401 DioException. Invalidating cached session and retrying once...');
        _session = null;
        await getOrRefreshSession(forceRefresh: true);
        return await _executeExtract(url: url, isRetry: true);
      }

      print('❌ [SMDownloader] Network error during extraction: ${e.message}');
      throw SmDownloaderException(
        "Network error during extraction: ${e.message ?? 'Unknown error'}",
        statusCode: e.response?.statusCode,
      );
    } catch (e) {
      if (e is SmDownloaderException) rethrow;
      print('❌ [SMDownloader] Extraction exception: $e');
      throw SmDownloaderException("Extraction failed: $e");
    }
  }
}
