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

  // 🎯 1. Detect Platform
  static String detectPlatform(String url) {
    final lowerUrl = url.toLowerCase();
    String detected;

    // SMDownloader Supported Platforms (29 Platforms)
    if (lowerUrl.contains("instagram.com")) {
      detected = "instagram";
    } else if (lowerUrl.contains("threads.net")) {
      detected = "threads";
    } else if (lowerUrl.contains("bsky.app")) {
      detected = "bluesky";
    } else if (lowerUrl.contains("tiktok.com")) {
      detected = "tiktok";
    } else if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
      detected = "youtube";
    } else if (lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.watch")) {
      detected = "facebook";
    } else if (lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
      detected = "twitter";
    } else if (lowerUrl.contains("douyin.com")) {
      detected = "douyin";
    } else if (lowerUrl.contains("soundcloud.com")) {
      detected = "soundcloud";
    } else if (lowerUrl.contains("dailymotion.com") || lowerUrl.contains("dai.ly")) {
      detected = "dailymotion";
    } else if (lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.com")) {
      detected = "xiaohongshu";
    } else if (lowerUrl.contains("weibo.com") || lowerUrl.contains("weibo.cn")) {
      detected = "weibo";
    } else if (lowerUrl.contains("kuaishou.com")) {
      detected = "kuaishou";
    } else if (lowerUrl.contains("reddit.com") || lowerUrl.contains("redd.it")) {
      detected = "reddit";
    } else if (lowerUrl.contains("vimeo.com")) {
      detected = "vimeo";
    } else if (lowerUrl.contains("vk.com")) {
      detected = "vk";
    } else if (lowerUrl.contains("twitch.tv")) {
      detected = "twitch";
    } else if (lowerUrl.contains("pinterest.com") || lowerUrl.contains("pin.it")) {
      detected = "pinterest";
    } else if (lowerUrl.contains("rumble.com")) {
      detected = "rumble";
    } else if (lowerUrl.contains("bilibili.com")) {
      detected = "bilibili";
    } else if (lowerUrl.contains("snapchat.com")) {
      detected = "snapchat";
    } else if (lowerUrl.contains("streamable.com")) {
      detected = "streamable";
    } else if (lowerUrl.contains("bandcamp.com")) {
      detected = "bandcamp";
    } else if (lowerUrl.contains("chzzk.naver.com")) {
      detected = "chzzk";
    } else if (lowerUrl.contains("tv.naver.com")) {
      detected = "naver tv";
    } else if (lowerUrl.contains("sooplive.co.kr") || lowerUrl.contains("afreecatv.com")) {
      detected = "soop";
    } else if (lowerUrl.contains("mixcloud.com")) {
      detected = "mixcloud";
    } else if (lowerUrl.contains("ted.com")) {
      detected = "ted";
    } else if (lowerUrl.contains("loom.com")) {
      detected = "loom";
    } else if (lowerUrl.contains("linkedin.com")) {
      detected = "linkedin";
    } else {
      detected = "unknown";
    }

    return detected;
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

  static bool isValidVideoUrl(String url, String platform) {
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

      default:
        if (uri.pathSegments.isNotEmpty) {
          isValid = true;
          matchedRule = "Generic URL with path segments";
        }
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
