import 'dart:developer' as dev;
import 'package:dio/dio.dart';
import '../models/sm_extraction_result.dart';

/// Service for ApiHut fallback video extraction with API Key rotation and emoji logging.
class ApiHutService {
  final Dio _dio;

  /// Pool of ApiHut API Keys for rotation.
  final List<String> _apiKeys;
  int _currentKeyIndex = 0;

  ApiHutService({
    Dio? dio,
    List<String>? apiKeys,
  })  : _dio = dio ?? Dio(),
        _apiKeys = (apiKeys != null && apiKeys.isNotEmpty)
            ? List.from(apiKeys)
            : ["330f736e-3eb6-4ad7-8bb9-0754c2d68f9f"];

  /// Adds extra keys to the rotation pool if available.
  void addApiKeys(List<String> keys) {
    for (final k in keys) {
      if (!_apiKeys.contains(k)) {
        _apiKeys.add(k);
      }
    }
  }

  /// Current active key (masked for logs).
  String get _currentKey => _apiKeys[_currentKeyIndex % _apiKeys.length];

  String _maskKey(String key) {
    if (key.length <= 8) return '****';
    return '${key.substring(0, 4)}...${key.substring(key.length - 4)}';
  }

  void _rotateKey() {
    _currentKeyIndex = (_currentKeyIndex + 1) % _apiKeys.length;
    dev.log('🔄 [ApiHut] Rotated to API Key #${_currentKeyIndex + 1} (${_maskKey(_currentKey)})');
    print('🔄 [ApiHut] Rotated to API Key #${_currentKeyIndex + 1} (${_maskKey(_currentKey)})');
  }

  /// Extracts media from ApiHut with automatic API key rotation on failure.
  Future<SmExtractionResult?> fetchVideo({
    required String url,
    required String platform,
  }) async {
    print('🔀 [ApiHut] Initiating extraction fallback for platform: $platform');
    dev.log('🔀 [ApiHut] URL: $url | Platform: $platform');

    int attempts = 0;
    final maxAttempts = _apiKeys.length;

    while (attempts < maxAttempts) {
      attempts++;
      final key = _currentKey;
      print('🗝️ [ApiHut] Attempt $attempts/$maxAttempts using key #${_currentKeyIndex + 1} (${_maskKey(key)})');

      try {
        final response = await _dio.post(
          "https://apihut.in/api/download/videos",
          data: {
            "video_url": url,
            "type": platform,
          },
          options: Options(
            headers: {
              "X-Avatar-Key": key,
              "Content-Type": "application/json",
            },
            validateStatus: (status) => status != null && status < 500,
          ),
        );

        final statusCode = response.statusCode ?? 500;
        final data = response.data;

        // Check for unauthorized, quota exhausted or forbidden
        if (statusCode == 401 || statusCode == 403 || statusCode == 429) {
          print('⚠️ [ApiHut] Key returned status $statusCode (quota/unauthorized).');
          _rotateKey();
          continue;
        }

        if (data is Map<String, dynamic> && data["success"] == 1) {
          String? directUrl;
          String? thumbnail;

          if (data["data"] != null && (data["data"] as List).isNotEmpty) {
            final first = data["data"][0];
            directUrl = first["url"];
            thumbnail = first["thumbnail"];
          } else if (data["url"] != null) {
            directUrl = data["url"];
          }

          if (directUrl != null && directUrl.isNotEmpty) {
            print('✅ [ApiHut] Successfully extracted stream: ${directUrl.substring(0, directUrl.length > 60 ? 60 : directUrl.length)}...');
            return SmExtractionResult(
              videoUrl: directUrl,
              thumbnailUrl: thumbnail,
              rawJson: data,
              isApiHut: true,
            );
          }
        }

        // If response is not success, check if we should rotate
        print('⚠️ [ApiHut] Response did not contain success stream: $data');
        if (attempts < maxAttempts) {
          _rotateKey();
        }
      } catch (e) {
        print('❌ [ApiHut] Request error with key #${_currentKeyIndex + 1}: $e');
        if (attempts < maxAttempts) {
          _rotateKey();
        }
      }
    }

    print('❌ [ApiHut] All API keys in rotation pool exhausted or extraction failed');
    return null;
  }
}
