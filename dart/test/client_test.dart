import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';
import 'package:wpush/wpush.dart';

void main() {
  http.Request? lastReq;

  Client clientWith(MockClientHandler handler) {
    lastReq = null;
    final mock = MockClient((request) async {
      lastReq = request;
      return handler(request);
    });
    return Client(apiKey: 'k', httpClient: mock);
  }

  test('send success joins channel and headers', () async {
    final c = clientWith((req) async {
      return http.Response(
        jsonEncode({'code': 0, 'message': 'success', 'data': '12345'}),
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    final mid = await c.send(title: 't', content: 'c', channel: ['wechat', 'dingtalk']);
    expect(mid, '12345');
    expect(lastReq!.headers['user-agent'], kUserAgent);
    expect(lastReq!.headers['x-api-key'], 'k');
    expect(lastReq!.url.path, endsWith('/api/v1/send'));
    final body = jsonDecode(lastReq!.body) as Map<String, dynamic>;
    expect(body['apikey'], 'k');
    expect(body['channel'], 'wechat,dingtalk');
    expect(body['title'], 't');
  });

  test('send numeric data stringified', () async {
    final c = clientWith((req) async {
      return http.Response(jsonEncode({'code': 0, 'message': 'ok', 'data': 999}), 200);
    });
    expect(await c.send(title: 't'), '999');
  });

  test('reject bool code', () async {
    final c = clientWith((req) async {
      return http.Response(jsonEncode({'code': false, 'message': 'nope', 'data': null}), 200);
    });
    try {
      await c.send(title: 't');
      fail('expected');
    } on WPushException catch (e) {
      expect(e.code, isNull);
    }
  });

  test('api error 401', () async {
    final c = clientWith((req) async {
      return http.Response(jsonEncode({'code': 401, 'message': 'API Key error', 'data': null}), 200);
    });
    try {
      await c.send(title: 't');
      fail('expected');
    } on WPushException catch (e) {
      expect(e.code, 401);
    }
  });

  test('option topic conflict', () async {
    var called = false;
    final c = clientWith((req) async {
      called = true;
      return http.Response('{}', 200);
    });
    await expectLater(
      c.send(title: 't', option: 'ops', topicCode: 'topic1'),
      throwsA(isA<ValidationException>()),
    );
    expect(called, isFalse);
  });

  test('empty api key rejected', () {
    expect(() => Client(apiKey: ''), throwsA(isA<ValidationException>()));
  });

  test('query success', () async {
    final c = clientWith((req) async {
      return http.Response(
        jsonEncode({
          'code': 0,
          'message': 'success',
          'data': {'id': '123', 'status': 1, 'title': 't'},
        }),
        200,
      );
    });
    final out = await c.query('123');
    expect(out['id'], '123');
    expect(out['status'], 1);
    final body = jsonDecode(lastReq!.body) as Map<String, dynamic>;
    expect(body['id'], '123');
    expect(body['apikey'], 'k');
  });

  test('idempotency header', () async {
    final c = clientWith((req) async {
      return http.Response(jsonEncode({'code': 0, 'message': 'ok', 'data': '1'}), 200);
    });
    await c.send(title: 't', idempotencyKey: 'idem-1');
    expect(lastReq!.headers['x-idempotency-key'], 'idem-1');
  });

  test('sendMail success', () async {
    final c = clientWith((req) async {
      return http.Response(jsonEncode({'code': 0, 'message': 'success', 'data': 'mail-1'}), 200);
    });
    expect(await c.sendMail(to: 'a@b.com', title: 'hi', content: 'body'), 'mail-1');
    expect(lastReq!.url.path, endsWith('/api/v1/send_mail'));
    final body = jsonDecode(lastReq!.body) as Map<String, dynamic>;
    expect(body['to'], 'a@b.com');
  });

  test('sendCode success', () async {
    final c = clientWith((req) async {
      return http.Response(jsonEncode({'code': 0, 'message': 'success', 'data': 'code-1'}), 200);
    });
    expect(await c.sendCode(phone: '13800138000', code: '123456'), 'code-1');
    expect(lastReq!.url.path, endsWith('/api/v1/send_code'));
  });

  test('sendCode bad phone', () async {
    final c = clientWith((req) async => http.Response('{}', 200));
    await expectLater(
      c.sendCode(phone: '123', code: '123456'),
      throwsA(isA<ValidationException>()),
    );
  });

  test('queryRelay success', () async {
    final c = clientWith((req) async {
      return http.Response(
        jsonEncode({
          'code': 0,
          'message': 'success',
          'data': {'id': 'r1', 'status': 1},
        }),
        200,
      );
    });
    final out = await c.queryRelay('r1');
    expect(out['id'], 'r1');
    expect(lastReq!.url.path, endsWith('/api/v1/query_relay'));
  });

  test('isSuccessCode rejects bool', () {
    expect(isSuccessCode(false), isFalse);
    expect(isSuccessCode(true), isFalse);
    expect(isSuccessCode(0), isTrue);
    expect(isSuccessCode(0.0), isTrue);
  });
}
