/// API or transport error from WPUSH.
class WPushException implements Exception {
  WPushException(
    this.message, {
    this.code,
    this.httpStatus,
    this.data,
  });

  final String message;
  final int? code;
  final int? httpStatus;
  final dynamic data;

  @override
  String toString() {
    final parts = <String>[message];
    if (code != null) parts.add('code=$code');
    if (httpStatus != null) parts.add('http=$httpStatus');
    return parts.join(' | ');
  }
}

/// Client-side validation failure before the request is sent.
class ValidationException extends WPushException {
  ValidationException(String message) : super(message);
}
