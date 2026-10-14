import 'dart:developer' as dev;
import 'package:dio/dio.dart';
import '../../for_ads/ads/ads_variable.dart';
import '../models/sm_extraction_result.dart';

/// Service for ApiHut fallback video extraction with API Key rotation and emoji logging.
class ApiHutService {
  final Dio _dio;

  /// Custom pool of ApiHut API Keys for rotation (if specified, overrides RemoteConfig).
  final List<String>? _customApiKeys;
  int _currentKeyIndex = 0;
  final bool? _customRotationOn;

  ApiHutService({
    Dio? dio,
    List<String>? apiKeys,
    bool? isRotationOn,
  })  : _dio = dio ?? Dio(),
        _customApiKeys = (apiKeys != null && apiKeys.isNotEmpty) ? List.from(apiKeys) : null,
        _customRotationOn = isRotationOn;

  /// Effective pool of API Keys (prefers RemoteConfig list from AdsVariable.apiHutKeys)
  List<String> get _effectiveKeys {
    if (_customApiKeys != null && _customApiKeys.isNotEmpty) {
      return _customApiKeys;
    }
    if (AdsVariable.apiHutKeys.isNotEmpty) {
      return AdsVariable.apiHutKeys;
    }
    return const ["330f736e-3eb6-4ad7-8bb9-0754c2d68f9f"];
  }

  /// Flag indicating if API key rotation is on (from RemoteConfig or override)
  bool get isRotationOn => _customRotationOn ?? AdsVariable.isApiHutKeyRotationOn;

  /// Adds extra keys to the rotation pool if available.
  void addApiKeys(List<String> keys) {
    for (final k in keys) {
      if (!AdsVariable.apiHutKeys.contains(k)) {
        AdsVariable.apiHutKeys.add(k);
      }
    }
  }

  /// Current active key (masked for logs).
  String get _currentKey {
    final keys = _effectiveKeys;
    return keys[_currentKeyIndex % keys.length];
  }

  String _maskKey(String key) {
    if (key.length <= 8) return '****';
    return '${key.substring(0, 4)}...${key.substring(key.length - 4)}';
  }

  void _rotateKey() {
    final keys = _effectiveKeys;
    if (!isRotationOn || keys.length <= 1) {
      print('ℹ️ [ApiHut] Key rotation is OFF or only 1 key available. Skipping rotation.');
      return;
    }
    _currentKeyIndex = (_currentKeyIndex + 1) % keys.length;
    dev.log('🔄 [ApiHut] Rotated to API Key #${_currentKeyIndex + 1} (${_maskKey(_currentKey)})');
    print('🔄 [ApiHut] Rotated to API Key #${_currentKeyIndex + 1} (${_maskKey(_currentKey)})');
  }

  /// Extracts media from ApiHut with automatic API key rotation on failure.
  Future<SmExtractionResult?> fetchVideo({
    required String url,
    required String platform,
  }) async {
    final keys = _effectiveKeys;
    final rotationActive = isRotationOn;
    final maxAttempts = rotationActive ? keys.length : 1;

    print('🔀 [ApiHut] Initiating extraction fallback for platform: $platform (Keys in pool: ${keys.length}, Rotation: ${rotationActive ? "ON" : "OFF"})');
    dev.log('🔀 [ApiHut] URL: $url | Platform: $platform | Rotation: $rotationActive');

    int attempts = 0;

    while (attempts < maxAttempts) {
      attempts++;
      final key = _currentKey;
      print('🗝️ [ApiHut] Attempt $attempts/$maxAttempts using key #${(_currentKeyIndex % keys.length) + 1} (${_maskKey(key)})');

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
          if (rotationActive && attempts < maxAttempts) {
            _rotateKey();
          }
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
        if (rotationActive && attempts < maxAttempts) {
          _rotateKey();
        }
      } catch (e) {
        print('❌ [ApiHut] Request error with key #${(_currentKeyIndex % keys.length) + 1}: $e');
        if (rotationActive && attempts < maxAttempts) {
          _rotateKey();
        }
      }
    }

    print('❌ [ApiHut] All allowed attempts ($maxAttempts) exhausted or extraction failed');
    return null;
  }
}
