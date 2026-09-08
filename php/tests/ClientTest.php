<?php
declare(strict_types=1);

namespace WPush\Tests;

use PHPUnit\Framework\TestCase;
use WPush\Client;
use WPush\HttpTransport;
use WPush\ValidationException;
use WPush\WPushException;

final class CapturingTransport implements HttpTransport
{
    public $lastUrl;
    public $lastHeaders;
    public $lastBody;
    public $response;

    public function __construct(array $response)
    {
        $this->response = $response;
    }

    public function post(string $url, array $headers, string $jsonBody): array
    {
        $this->lastUrl = $url;
        $this->lastHeaders = $headers;
        $this->lastBody = $jsonBody;
        return $this->response;
    }
}

final class ClientTest extends TestCase
{
    private function client(CapturingTransport $t): Client
    {
        return new Client('k', Client::DEFAULT_BASE_URL, 30.0, null, $t);
    }

    public function testSendSuccess(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'success', 'data' => '12345'])]);
        $mid = $this->client($t)->send('t', 'c', ['wechat', 'dingtalk']);
        $this->assertSame('12345', $mid);
        $this->assertSame(Client::USER_AGENT, $t->lastHeaders['User-Agent']);
        $this->assertSame('k', $t->lastHeaders['X-API-Key']);
        $body = json_decode($t->lastBody, true);
        $this->assertSame('k', $body['apikey']);
        $this->assertSame('wechat,dingtalk', $body['channel']);
        $this->assertStringEndsWith('/api/v1/send', $t->lastUrl);
    }

    public function testSendNumericDataStringified(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'ok', 'data' => 999])]);
        $this->assertSame('999', $this->client($t)->send('t'));
    }

    public function testRejectBoolCode(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => false, 'message' => 'nope', 'data' => null])]);
        try {
            $this->client($t)->send('t');
            $this->fail('expected exception');
        } catch (WPushException $e) {
            $this->assertNull($e->getErrorCode());
        }
    }

    public function testApiError401(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 401, 'message' => 'API Key错误', 'data' => null])]);
        try {
            $this->client($t)->send('t');
            $this->fail('expected');
        } catch (WPushException $e) {
            $this->assertSame(401, $e->getErrorCode());
        }
    }

    public function testOptionTopicConflict(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => '{}']);
        $this->expectException(ValidationException::class);
        $this->client($t)->send('t', null, null, 'ops', 'topic1');
        $this->assertNull($t->lastUrl);
    }

    public function testMissingApiKey(): void
    {
        $prev = getenv('WPUSH_API_KEY');
        putenv('WPUSH_API_KEY');
        try {
            $this->expectException(ValidationException::class);
            new Client();
        } finally {
            if ($prev === false) {
                putenv('WPUSH_API_KEY');
            } else {
                putenv('WPUSH_API_KEY=' . $prev);
            }
        }
    }

    public function testQuerySuccess(): void
    {
        $data = ['id' => '123', 'status' => 1, 'title' => 't'];
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'success', 'data' => $data])]);
        $out = $this->client($t)->query('123');
        $this->assertSame('123', $out['id']);
        $this->assertSame(1, $out['status']);
        $body = json_decode($t->lastBody, true);
        $this->assertSame('123', $body['id']);
    }

    public function testEnvApiKey(): void
    {
        putenv('WPUSH_API_KEY=from-env');
        try {
            $c = new Client(null, Client::DEFAULT_BASE_URL, 30.0, null, new CapturingTransport(['status' => 200, 'body' => '{}']));
            $this->assertSame('from-env', $c->getApiKey());
        } finally {
            putenv('WPUSH_API_KEY');
        }
    }

    public function testIdempotencyHeader(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'ok', 'data' => '1'])]);
        $this->client($t)->send('t', null, null, null, null, null, 'idem-1');
        $this->assertSame('idem-1', $t->lastHeaders['X-Idempotency-Key']);
    }

    public function testSendMailSuccess(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'success', 'data' => 'mail-1'])]);
        $this->assertSame('mail-1', $this->client($t)->sendMail('a@b.com', 'hi', 'body'));
        $this->assertStringEndsWith('/api/v1/send_mail', $t->lastUrl);
        $body = json_decode($t->lastBody, true);
        $this->assertSame('a@b.com', $body['to']);
    }

    public function testSendCodeSuccess(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'success', 'data' => 'code-1'])]);
        $this->assertSame('code-1', $this->client($t)->sendCode('13800138000', '123456'));
        $this->assertStringEndsWith('/api/v1/send_code', $t->lastUrl);
    }

    public function testSendCodeBadPhone(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => '{}']);
        $this->expectException(ValidationException::class);
        $this->client($t)->sendCode('123', '123456');
    }

    public function testQueryRelaySuccess(): void
    {
        $t = new CapturingTransport(['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'success', 'data' => ['id' => 'r1', 'status' => 1]])]);
        $out = $this->client($t)->queryRelay('r1');
        $this->assertSame('r1', $out['id']);
        $this->assertStringEndsWith('/api/v1/query_relay', $t->lastUrl);
    }
}
