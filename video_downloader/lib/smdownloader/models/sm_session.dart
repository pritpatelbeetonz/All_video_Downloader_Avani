/// In-memory representation of an SMDownloader session.
class SmSession {
  final String token;
  final int expiresAt; // Unix timestamp in milliseconds

  SmSession({
    required this.token,
    required this.expiresAt,
  });

  /// True if the current time is at or beyond [expiresAt].
  bool get isExpired => DateTime.now().millisecondsSinceEpoch >= expiresAt;

  /// True if the session will expire within the given [bufferMs] (defaults to 30,000ms / 30s).
  bool isExpiringSoon([int bufferMs = 30000]) {
    return DateTime.now().millisecondsSinceEpoch + bufferMs >= expiresAt;
  }

  /// Remaining seconds before expiry.
  int get remainingSeconds {
    final diff = expiresAt - DateTime.now().millisecondsSinceEpoch;
    return diff > 0 ? (diff / 1000).round() : 0;
  }

  @override
  String toString() {
    // Session token is strictly kept private and masked
    return 'SmSession(expiresAt: $expiresAt, remaining: ${remainingSeconds}s)';
  }
}
