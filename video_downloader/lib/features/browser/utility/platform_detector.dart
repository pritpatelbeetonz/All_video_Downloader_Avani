import 'dart:developer';
import '../../../for_ads/ads/ads_variable.dart';

class PlatformDetector {
  PlatformDetector._();

  // 🎯 1. Detect Platform
  static String detectPlatform(String url) {
    print("===========> Checking the platform supported or not: $url");
    final lowerUrl = url.toLowerCase();

    // SMDownloader Supported Platforms (29 Platforms)
    if (lowerUrl.contains("instagram.com")) {
      return "instagram";
    } else if (lowerUrl.contains("threads.net")) {
      return "threads";
    } else if (lowerUrl.contains("bsky.app")) {
      return "bluesky";
    } else if (lowerUrl.contains("tiktok.com")) {
      return "tiktok";
    } else if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
      return "youtube";
    } else if (lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.watch")) {
      return "facebook";
    } else if (lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
      return "twitter";
    } else if (lowerUrl.contains("douyin.com")) {
      return "douyin";
    } else if (lowerUrl.contains("soundcloud.com")) {
      return "soundcloud";
    } else if (lowerUrl.contains("dailymotion.com") || lowerUrl.contains("dai.ly")) {
      return "dailymotion";
    } else if (lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.com")) {
      return "xiaohongshu";
    } else if (lowerUrl.contains("weibo.com") || lowerUrl.contains("weibo.cn")) {
      return "weibo";
    } else if (lowerUrl.contains("kuaishou.com")) {
      return "kuaishou";
    } else if (lowerUrl.contains("reddit.com") || lowerUrl.contains("redd.it")) {
      return "reddit";
    } else if (lowerUrl.contains("vimeo.com")) {
      return "vimeo";
    } else if (lowerUrl.contains("vk.com")) {
      return "vk";
    } else if (lowerUrl.contains("twitch.tv")) {
      return "twitch";
    } else if (lowerUrl.contains("pinterest.com") || lowerUrl.contains("pin.it")) {
      return "pinterest";
    } else if (lowerUrl.contains("rumble.com")) {
      return "rumble";
    } else if (lowerUrl.contains("bilibili.com")) {
      return "bilibili";
    } else if (lowerUrl.contains("snapchat.com")) {
      return "snapchat";
    } else if (lowerUrl.contains("streamable.com")) {
      return "streamable";
    } else if (lowerUrl.contains("bandcamp.com")) {
      return "bandcamp";
    } else if (lowerUrl.contains("chzzk.naver.com")) {
      return "chzzk";
    } else if (lowerUrl.contains("tv.naver.com")) {
      return "naver tv";
    } else if (lowerUrl.contains("sooplive.co.kr") || lowerUrl.contains("afreecatv.com")) {
      return "soop";
    } else if (lowerUrl.contains("mixcloud.com")) {
      return "mixcloud";
    } else if (lowerUrl.contains("ted.com")) {
      return "ted";
    } else if (lowerUrl.contains("loom.com")) {
      return "loom";
    } else if (lowerUrl.contains("linkedin.com")) {
      return "linkedin";
    } else {
      return "unknown";
    }
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
    if (uri == null) return false;

    final path = uri.toString();
    final lowerUrl = url.toLowerCase();
    print("===========> Checking if url is supported: $path (platform: $platform)");

    switch (platform) {
      // INSTAGRAM
      case "instagram":
        return path.contains("/reels/") ||
            path.contains("/reel/") ||
            path.contains("/p/") ||
            path.contains("/tv/");

      // THREADS
      case "threads":
        return path.contains("/post/") || path.contains("/t/");

      // BLUESKY
      case "bluesky":
        return path.contains("/post/");

      // TIKTOK
      case "tiktok":
        return path.contains("/video/") ||
            path.contains("/@") ||
            path.contains("/t/") ||
            lowerUrl.contains("vm.tiktok.com");

      // YOUTUBE
      case "youtube":
        if (!AdsVariable.isYouTubeEnabled) return false;
        return lowerUrl.contains("watch?v=") ||
            lowerUrl.contains("/shorts/") ||
            uri.host.contains("youtu.be");

      // FACEBOOK
      case "facebook":
        return path.contains("/videos/") ||
            lowerUrl.contains("watch?v=") ||
            path.contains("/reel/") ||
            uri.host.contains("fb.watch");

      // TWITTER / X
      case "twitter":
        return path.contains("/status/");

      // DOUYIN
      case "douyin":
        return path.contains("/video/") || lowerUrl.contains("v.douyin.com");

      // SOUNDCLOUD
      case "soundcloud":
        return uri.pathSegments.isNotEmpty;

      // DAILYMOTION
      case "dailymotion":
        return path.contains("/video/") || lowerUrl.contains("dai.ly");

      // XIAOHONGSHU
      case "xiaohongshu":
        return path.contains("/discovery/item/") ||
            path.contains("/explore/") ||
            lowerUrl.contains("xhslink.com");

      // WEIBO
      case "weibo":
        return path.contains("/status/") || path.contains("/detail/");

      // KUAISHOU
      case "kuaishou":
        return path.contains("/short-video/") || lowerUrl.contains("v.kuaishou.com");

      // REDDIT
      case "reddit":
        return path.contains("/comments/") ||
            path.contains("/r/") ||
            lowerUrl.contains("redd.it");

      // VIMEO
      case "vimeo":
        return uri.pathSegments.isNotEmpty;

      // VK
      case "vk":
        return path.contains("/video") || path.contains("z=video");

      // TWITCH
      case "twitch":
        return path.contains("/videos/") ||
            path.contains("/clip/") ||
            lowerUrl.contains("clips.twitch.tv");

      // PINTEREST
      case "pinterest":
        return path.contains("/pin/") || lowerUrl.contains("pin.it");

      // RUMBLE
      case "rumble":
        return path.contains("/v");

      // BILIBILI
      case "bilibili":
        return path.contains("/video/");

      // SNAPCHAT
      case "snapchat":
        return path.contains("/spotlight/");

      // STREAMABLE
      case "streamable":
        return uri.pathSegments.isNotEmpty;

      // BANDCAMP
      case "bandcamp":
        return path.contains("/track/") || path.contains("/album/");

      // CHZZK
      case "chzzk":
        return path.contains("/video/") || path.contains("/live/");

      // NAVER TV
      case "naver tv":
        return path.contains("/vod/") || path.contains("/live/");

      // SOOP
      case "soop":
        return path.contains("/vod/") || path.contains("/player/");

      // MIXCLOUD
      case "mixcloud":
        return uri.pathSegments.length >= 2;

      // TED
      case "ted":
        return path.contains("/talks/");

      // LOOM
      case "loom":
        return path.contains("/share/");

      // LINKEDIN (Handled via ApiHut fallback)
      case "linkedin":
        return path.contains("/posts/") || path.contains("/activity-");

      default:
        // Any other non-empty path
        return uri.pathSegments.isNotEmpty;
    }
  }
}
