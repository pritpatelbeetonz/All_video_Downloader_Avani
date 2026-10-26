import 'dart:developer';
import 'package:flutter/services.dart';
import '../../../for_ads/ads/ads_variable.dart';

const MethodChannel _nativeFilterChannel = MethodChannel("nativeChannel");

void _logFilter(String message) {
  log(message, name: 'URL_FILTER');
  print('🔎 [URL_FILTER] $message');
  try {
    _nativeFilterChannel.invokeMethod('logNative', {
      'tag': 'URL_FILTER',
      'message': message,
    });
  } catch (_) {}
}

class PlatformDetector {
  PlatformDetector._();

  /// Host → platform, checked in order. Matching is on the real host (see
  /// [_hostIs]) so e.g. netflix.com is not mistaken for x.com.
  static const List<(String, List<String>)> _platformHosts = [
    // SMDownloader Supported Platforms (29 Platforms)
    ("instagram", ["instagram.com", "instagr.am"]),
    ("threads", ["threads.net", "threads.com"]),
    ("bluesky", ["bsky.app"]),
    ("tiktok", ["tiktok.com"]),
    ("youtube", ["youtube.com", "youtu.be"]),
    ("facebook", ["facebook.com", "fb.watch", "fb.com"]),
    ("twitter", ["twitter.com", "x.com"]),
    ("douyin", ["douyin.com"]),
    ("soundcloud", ["soundcloud.com"]),
    ("dailymotion", ["dailymotion.com", "dai.ly"]),
    ("xiaohongshu", ["xiaohongshu.com", "xhslink.com"]),
    ("weibo", ["weibo.com", "weibo.cn"]),
    ("kuaishou", ["kuaishou.com"]),
    ("reddit", ["reddit.com", "redd.it"]),
    ("vimeo", ["vimeo.com"]),
    ("vk", ["vk.com"]),
    ("twitch", ["twitch.tv"]),
    ("pinterest", ["pinterest.com", "pin.it"]),
    ("rumble", ["rumble.com"]),
    ("bilibili", ["bilibili.com"]),
    ("snapchat", ["snapchat.com"]),
    ("streamable", ["streamable.com"]),
    ("bandcamp", ["bandcamp.com"]),
    ("chzzk", ["chzzk.naver.com"]),
    ("naver tv", ["tv.naver.com"]),
    ("soop", ["sooplive.co.kr", "afreecatv.com"]),
    ("mixcloud", ["mixcloud.com"]),
    ("ted", ["ted.com"]),
    ("loom", ["loom.com"]),
    // Handled via ApiHut fallback
    ("linkedin", ["linkedin.com"]),
  ];

  /// Search engines / start pages: never a downloadable video.
  static const List<String> _searchHosts = [
    "bing.com",
    "yahoo.com",
    "duckduckgo.com",
    "baidu.com",
  ];

  static bool _hostIs(String host, String domain) =>
      host == domain || host.endsWith('.$domain');

  /// Normalised host of [url] (lowercase, without www./m./mobile.), '' if none.
  static String hostOf(String url) {
    var host = Uri.tryParse(url.trim())?.host.toLowerCase() ?? '';
    for (final prefix in const ['www.', 'm.', 'mobile.']) {
      if (host.startsWith(prefix)) {
        host = host.substring(prefix.length);
        break;
      }
    }
    return host;
  }

  /// google.com, google.co.in, ... (any host with a "google" label).
  static bool _isGoogleHost(String host) => host.split('.').contains('google');

  /// pinterest.com plus country domains like pinterest.co.uk / pinterest.ca.
  static bool _isPinterestHost(String host) =>
      host.split('.').contains('pinterest') || _hostIs(host, 'pin.it');

  static bool _isSearchHost(String host) =>
      _isGoogleHost(host) ||
      host.split('.').contains('yandex') ||
      _searchHosts.any((d) => _hostIs(host, d));

  /// Returns the real target of Google redirect / AMP links
  /// (google.com/url?q=..., google.com/amp/s/...), otherwise [url] unchanged.
  static String unwrap(String url) {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || !_isGoogleHost(hostOf(url))) return url;
    if (uri.path == '/url') {
      final target = uri.queryParameters['q'] ?? uri.queryParameters['url'];
      if (target != null && target.startsWith('http')) return target;
    }
    if (uri.path.startsWith('/amp/s/')) {
      final rest = uri.path.substring('/amp/s/'.length);
      return 'https://$rest${uri.hasQuery ? '?${uri.query}' : ''}';
    }
    return url;
  }

  // 🎯 1. Detect Platform
  static String detectPlatform(String url) {
    final host = hostOf(unwrap(url));
    if (host.isEmpty) return "unknown";
    if (_isPinterestHost(host)) return "pinterest";
    for (final (platform, domains) in _platformHosts) {
      if (domains.any((d) => _hostIs(host, d))) return platform;
    }
    return "unknown";
  }

  /// List of all 29 platforms supported natively by SMDownloader
  static const Set<String> smDownloaderPlatforms = {
    "instagram",
    "threads",
    "bluesky",
    "tiktok",
    "youtube",
    "facebook",
    "twitter",
    "x",
    "douyin",
    "soundcloud",
    "dailymotion",
    "xiaohongshu",
    "weibo",
    "kuaishou",
    "reddit",
    "vimeo",
    "vk",
    "twitch",
    "pinterest",
    "rumble",
    "bilibili",
    "snapchat",
    "streamable",
    "bandcamp",
    "chzzk",
    "naver tv",
    "soop",
    "mixcloud",
    "ted",
    "loom",
  };

  static bool isSupportedBySmDownloader(String platform) {
    return smDownloaderPlatforms.contains(platform.toLowerCase());
  }

  /// Why a download can't start from [url], or null if it can:
  /// 'search_page' (Google/Bing/... or blank), 'unsupported_site' (platform
  /// not recognised) or 'not_a_post' (feed/profile/home of a known platform).
  static String? rejectReason(String url, String platform) {
    final target = unwrap(url);
    final host = hostOf(target);
    if (target.trim().isEmpty || target == "about:blank" || host.isEmpty || _isSearchHost(host)) {
      _logFilter("⛔ [URL_REJECTED] Search/blank page, not a video. Host: '$host' | URL: '$target'");
      return 'search_page';
    }
    if (platform.isEmpty || platform == "unknown") {
      _logFilter("⛔ [URL_REJECTED] Unsupported site. Host: '$host' | URL: '$target'");
      return 'unsupported_site';
    }
    return _matchesPostRule(target, platform) ? null : 'not_a_post';
  }

  static bool isValidVideoUrl(String url, String platform) =>
      rejectReason(url, platform) == null;

  static bool _matchesPostRule(String url, String platform) {
    final uri = Uri.tryParse(url);
    if (uri == null) {
      _logFilter("⛔ [URL_REJECTED] Cannot parse URL as valid URI: '$url' (Platform: $platform)");
      return false;
    }

    final path = uri.toString();
    final lowerUrl = url.toLowerCase();
    bool isValid = false;
    String matchedRule = "";

    switch (platform) {
      // INSTAGRAM
      case "instagram":
        if (path.contains("/reels/") || path.contains("/reel/")) {
          isValid = true;
          matchedRule = "Instagram Reel (/reel/ or /reels/)";
        } else if (path.contains("/p/")) {
          isValid = true;
          matchedRule = "Instagram Post (/p/)";
        } else if (path.contains("/tv/")) {
          isValid = true;
          matchedRule = "Instagram TV (/tv/)";
        } else if (path.contains("/stories/")) {
          isValid = true;
          matchedRule = "Instagram Story (/stories/)";
        }
        break;

      // THREADS
      case "threads":
        if (path.contains("/post/") || path.contains("/t/")) {
          isValid = true;
          matchedRule = "Threads Post";
        }
        break;

      // BLUESKY
      case "bluesky":
        if (path.contains("/post/")) {
          isValid = true;
          matchedRule = "Bluesky Post";
        }
        break;

      // TIKTOK
      case "tiktok":
        if (path.contains("/video/") ||
            path.contains("/@") ||
            path.contains("/t/") ||
            lowerUrl.contains("vm.tiktok.com") ||
            lowerUrl.contains("vt.tiktok.com")) {
          isValid = true;
          matchedRule = "TikTok Video / Share Link";
        }
        break;

      // YOUTUBE
      case "youtube":
        if (!AdsVariable.isYouTubeEnabled) {
          _logFilter("🛑 [URL_REJECTED] YouTube downloads restricted by remote config. URL: '$url'");
          return false;
        }
        if (lowerUrl.contains("watch?v=") ||
            lowerUrl.contains("watch/?v=") ||
            lowerUrl.contains("/shorts/") ||
            uri.host.contains("youtu.be") ||
            lowerUrl.contains("/live/")) {
          isValid = true;
          matchedRule = "YouTube Watch / Shorts / youtu.be";
        }
        break;

      // FACEBOOK
      case "facebook":
        if (path.contains("/videos/")) {
          isValid = true;
          matchedRule = "Facebook Video (/videos/)";
        } else if (lowerUrl.contains("watch?v=") || lowerUrl.contains("watch/?v=")) {
          isValid = true;
          matchedRule = "Facebook Watch (watch?v=)";
        } else if (path.contains("/reel/")) {
          isValid = true;
          matchedRule = "Facebook Reel (/reel/)";
        } else if (path.contains("/share/v/") || path.contains("/share/r/")) {
          isValid = true;
          matchedRule = "Facebook Share Link (/share/v/ or /share/r/)";
        } else if (uri.host.contains("fb.watch")) {
          isValid = true;
          matchedRule = "Facebook fb.watch Link";
        }
        break;

      // TWITTER / X
      case "twitter":
        if (path.contains("/status/")) {
          isValid = true;
          matchedRule = "Twitter/X Status/Post (/status/)";
        }
        break;

      // DOUYIN
      case "douyin":
        if (path.contains("/video/") || lowerUrl.contains("v.douyin.com")) {
          isValid = true;
          matchedRule = "Douyin Video";
        }
        break;

      // SOUNDCLOUD
      case "soundcloud":
        if (uri.pathSegments.isNotEmpty) {
          isValid = true;
          matchedRule = "Soundcloud Audio Track";
        }
        break;

      // DAILYMOTION
      case "dailymotion":
        if (path.contains("/video/") || lowerUrl.contains("dai.ly")) {
          isValid = true;
          matchedRule = "Dailymotion Video";
        }
        break;

      // XIAOHONGSHU
      case "xiaohongshu":
        if (path.contains("/discovery/item/") ||
            path.contains("/explore/") ||
            lowerUrl.contains("xhslink.com")) {
          isValid = true;
          matchedRule = "Xiaohongshu Post";
        }
        break;

      // WEIBO
      case "weibo":
        if (path.contains("/status/") || path.contains("/detail/")) {
          isValid = true;
          matchedRule = "Weibo Status/Detail";
        }
        break;

      // KUAISHOU
      case "kuaishou":
        if (path.contains("/short-video/") || lowerUrl.contains("v.kuaishou.com")) {
          isValid = true;
          matchedRule = "Kuaishou Video";
        }
        break;

      // REDDIT
      case "reddit":
        if (path.contains("/comments/") ||
            path.contains("/r/") ||
            lowerUrl.contains("redd.it")) {
          isValid = true;
          matchedRule = "Reddit Comments/Post";
        }
        break;

      // VIMEO
      case "vimeo":
        if (uri.pathSegments.isNotEmpty) {
          isValid = true;
          matchedRule = "Vimeo Video";
        }
        break;

      // VK
      case "vk":
        if (path.contains("/video") || path.contains("z=video")) {
          isValid = true;
          matchedRule = "VK Video";
        }
        break;

      // TWITCH
      case "twitch":
        if (path.contains("/videos/") ||
            path.contains("/clip/") ||
            lowerUrl.contains("clips.twitch.tv")) {
          isValid = true;
          matchedRule = "Twitch Video/Clip";
        }
        break;

      // PINTEREST
      case "pinterest":
        if (path.contains("/pin/") || lowerUrl.contains("pin.it")) {
          isValid = true;
          matchedRule = "Pinterest Pin";
        }
        break;

      // RUMBLE
      case "rumble":
        if (path.contains("/v")) {
          isValid = true;
          matchedRule = "Rumble Video";
        }
        break;

      // BILIBILI
      case "bilibili":
        if (path.contains("/video/")) {
          isValid = true;
          matchedRule = "Bilibili Video";
        }
        break;

      // SNAPCHAT
      case "snapchat":
        if (path.contains("/spotlight/")) {
          isValid = true;
          matchedRule = "Snapchat Spotlight";
        }
        break;

      // STREAMABLE
      case "streamable":
        if (uri.pathSegments.isNotEmpty) {
          isValid = true;
          matchedRule = "Streamable Video";
        }
        break;

      // BANDCAMP
      case "bandcamp":
        if (path.contains("/track/") || path.contains("/album/")) {
          isValid = true;
          matchedRule = "Bandcamp Track/Album";
        }
        break;

      // CHZZK
      case "chzzk":
        if (path.contains("/video/") || path.contains("/live/")) {
          isValid = true;
          matchedRule = "CHZZK Video/Live";
        }
        break;

      // NAVER TV
      case "naver tv":
        if (path.contains("/vod/") || path.contains("/live/")) {
          isValid = true;
          matchedRule = "Naver TV VOD/Live";
        }
        break;

      // SOOP
      case "soop":
        if (path.contains("/vod/") || path.contains("/player/")) {
          isValid = true;
          matchedRule = "SOOP VOD/Player";
        }
        break;

      // MIXCLOUD
      case "mixcloud":
        if (uri.pathSegments.length >= 2) {
          isValid = true;
          matchedRule = "Mixcloud Audio";
        }
        break;

      // TED
      case "ted":
        if (path.contains("/talks/")) {
          isValid = true;
          matchedRule = "TED Talk";
        }
        break;

      // LOOM
      case "loom":
        if (path.contains("/share/")) {
          isValid = true;
          matchedRule = "Loom Share";
        }
        break;

      // LINKEDIN (Handled via ApiHut fallback)
      case "linkedin":
        if (path.contains("/posts/") || path.contains("/activity-")) {
          isValid = true;
          matchedRule = "LinkedIn Post/Activity";
        }
        break;

      // Unrecognised platforms are rejected in rejectReason(); nothing generic here.
      default:
        break;
    }

    if (isValid) {
      _logFilter("🟢 [URL_ALLOWED] Valid video URL! Platform: '$platform' | Rule: '$matchedRule' | URL: '$url'");
    } else {
      _logFilter("🔴 [URL_REJECTED] Feed/Home/Profile detected (NOT a direct video post)! Platform: '$platform' | URL: '$url'");
    }

    return isValid;
  }
}
