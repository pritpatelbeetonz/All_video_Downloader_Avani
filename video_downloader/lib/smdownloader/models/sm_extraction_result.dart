/// Normalized result from a video extraction request.
class SmExtractionResult {
  final String videoUrl;
  final String? thumbnailUrl;
  final String? title;
  final Map<String, dynamic> rawJson;
  final bool isApiHut;

  SmExtractionResult({
    required this.videoUrl,
    this.thumbnailUrl,
    this.title,
    this.rawJson = const {},
    this.isApiHut = false,
  });

  /// Converts this result into a map formatted to be backward-compatible with ApiHut responses.
  Map<String, dynamic> toCompatibleMap() {
    return {
      "success": 1,
      "url": videoUrl,
      "data": [
        {
          "url": videoUrl,
          if (thumbnailUrl != null) "thumbnail": thumbnailUrl,
          if (title != null) "title": title,
        }
      ],
      ...rawJson,
    };
  }

  @override
  String toString() {
    return 'SmExtractionResult(videoUrl: $videoUrl, thumbnail: $thumbnailUrl)';
  }
}
