import 'dart:convert';
import 'dart:io' show Platform;

import 'package:http/http.dart' as http;

import 'errors.dart';

const String kUserAgent = 'wpush-dart/0.1.0';
const String kDefaultBaseUrl = 'https://api.wpush.cn';

final RegExp _phoneRe = RegExp(r'^(\+?86)?1\d{10}$');
final RegExp _codeRe = RegExp(r'^[A-Za-z0-9]{1,6}$');

typedef ChannelInput = Object; // String or List<String>

String? joinChannel(Object? channel) {
  if (channel == null) return null;
  if (channel is String) return channel;
  if (channel is List) return channel.map((e) => '$e').join(',');
  return '$channel';
}

bool isSuccessCode(Object? code) {
  // Reject bool; accept int/num == 0.
  if (code is bool) return false;
  if (code is num) return code == 0;
  return false;
}

String stringifyId(Object? data) {
  if (data == null) return '';
  return '$data';
}

class Client {
  Client({
    String? apiKey,
    String baseUrl = kDefaultBaseUrl,
    Duration timeout = const Duration(seconds: 30),
    Object? defaultChannel,
    http.Client? httpClient,
  })  : apiKey = _resolveKey(apiKey),
        baseUrl = baseUrl.replaceAll(RegExp(r'/+$'), ''),
        timeout = timeout,
        defaultChannel = defaultChannel,
        _http = httpClient ?? http.Client(),
        _ownsHttp = httpClient == null;

  static String _resolveKey(String? apiKey) {
    final key = apiKey ?? Platform.environment['WPUSH_API_KEY'];
    if (key == null || key.isEmpty) {
      throw ValidationException('api_key is required (or set WPUSH_API_KEY)');
    }
    return key;
  }

  final String apiKey;
  final String baseUrl;
  final Duration timeout;
  final Object? defaultChannel;
  final http.Client _http;
  final bool _ownsHttp;

  void close() {
    if (_ownsHttp) _http.close();
  }

  Future<String> send({
    required String title,
    String? content,
    Object? channel,
    String? option,
    String? topicCode,
    String? url,
    String? idempotencyKey,
  }) async {
    if (title.isEmpty) {
      throw ValidationException('title is required');
    }
    if (option != null &&
        option.isNotEmpty &&
        topicCode != null &&
        topicCode.isNotEmpty) {
      throw ValidationException('option and topic_code cannot be used together');
    }
    final ch = joinChannel(channel ?? defaultChannel);
    final payload = <String, dynamic>{'apikey': apiKey, 'title': title};
    if (content != null) payload['content'] = content;
    if (ch != null) payload['channel'] = ch;
    if (option != null && option.isNotEmpty) payload['option'] = option;
    if (topicCode != null && topicCode.isNotEmpty) {
      payload['topic_code'] = topicCode;
    }
    if (url != null && url.isNotEmpty) payload['url'] = url;
    final data = await _post('/api/v1/send', payload, idempotencyKey);
    return stringifyId(data);
  }

  Future<Map<String, dynamic>> query(
    String messageId, {
    String? idempotencyKey,
  }) async {
    if (messageId.isEmpty) {
      throw ValidationException('message_id is required');
    }
    final data = await _post(
      '/api/v1/query',
      {'apikey': apiKey, 'id': messageId},
      idempotencyKey,
    );
    if (data is! Map<String, dynamic>) {
      throw WPushException('invalid query data', data: data);
    }
    return data;
  }

  Future<String> sendMail({
    required String to,
    required String title,
    String? content,
    String? idempotencyKey,
  }) async {
    if (to.isEmpty) throw ValidationException('to is required');
    if (title.isEmpty) throw ValidationException('title is required');
    final payload = <String, dynamic>{
      'apikey': apiKey,
      'to': to,
      'title': title,
    };
    if (content != null) payload['content'] = content;
    final data = await _post('/api/v1/send_mail', payload, idempotencyKey);
    return stringifyId(data);
  }

  Future<String> sendCode({
    required String phone,
    required String code,
    String? idempotencyKey,
  }) async {
    if (phone.isEmpty || !_phoneRe.hasMatch(phone)) {
      throw ValidationException('invalid phone');
    }
    if (code.isEmpty || !_codeRe.hasMatch(code)) {
      throw ValidationException('invalid code');
    }
    final data = await _post(
      '/api/v1/send_code',
      {'apikey': apiKey, 'phone': phone, 'code': code},
      idempotencyKey,
    );
    return stringifyId(data);
  }

  Future<Map<String, dynamic>> queryRelay(
    String id, {
    String? idempotencyKey,
  }) async {
    if (id.isEmpty) throw ValidationException('id is required');
    final data = await _post(
      '/api/v1/query_relay',
      {'apikey': apiKey, 'id': id},
      idempotencyKey,
    );
    if (data is! Map<String, dynamic>) {
      throw WPushException('invalid query data', data: data);
    }
    return data;
  }

  Future<dynamic> _post(
    String path,
    Map<String, dynamic> payload,
    String? idempotencyKey,
  ) async {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'User-Agent': kUserAgent,
      'X-API-Key': apiKey,
    };
    if (idempotencyKey != null && idempotencyKey.isNotEmpty) {
      headers['X-Idempotency-Key'] = idempotencyKey;
    }
    late http.Response resp;
    try {
      resp = await _http
          .post(
            Uri.parse('$baseUrl$path'),
            headers: headers,
            body: jsonEncode(payload),
          )
          .timeout(timeout);
    } catch (e) {
      throw WPushException(e.toString());
    }
    dynamic body;
    try {
      body = resp.body.isEmpty ? <String, dynamic>{} : jsonDecode(resp.body);
    } catch (_) {
      throw WPushException(
        'invalid JSON response',
        httpStatus: resp.statusCode,
        data: resp.body,
      );
    }
    if (body is! Map) {
      throw WPushException(
        'invalid JSON envelope',
        httpStatus: resp.statusCode,
        data: body,
      );
    }
    return _parseEnvelope(Map<String, dynamic>.from(body), resp.statusCode);
  }

  dynamic _parseEnvelope(Map<String, dynamic> body, int httpStatus) {
    final code = body['code'];
    final message = body['message'] ?? '';
    final data = body['data'];
    if (!isSuccessCode(code)) {
      int? errCode;
      if (code is int) {
        errCode = code;
      } else if (code is num && code is! bool) {
        errCode = code.toInt();
      }
      if (code is bool) errCode = null;
      final msg = message.toString().isEmpty ? 'request failed' : message.toString();
      throw WPushException(
        msg,
        code: errCode,
        httpStatus: httpStatus,
        data: data,
      );
    }
    return data;
  }
}
