<?php
declare(strict_types=1);

namespace WPush;

final class Client
{
    public const USER_AGENT = 'wpush-php/0.1.0';
    public const DEFAULT_BASE_URL = 'https://api.wpush.cn';

    /** @var string */
    private $apiKey;
    /** @var string */
    private $baseUrl;
    /** @var float */
    private $timeout;
    /** @var string|array|null */
    private $defaultChannel;
    /** @var HttpTransport */
    private $transport;

    /**
     * @param string|null $apiKey
     * @param string $baseUrl
     * @param float $timeout
     * @param string|array|null $defaultChannel
     * @param HttpTransport|null $transport
     */
    public function __construct(
        ?string $apiKey = null,
        string $baseUrl = self::DEFAULT_BASE_URL,
        float $timeout = 30.0,
        $defaultChannel = null,
        ?HttpTransport $transport = null
    ) {
        $key = $apiKey !== null ? $apiKey : (getenv('WPUSH_API_KEY') ?: null);
        if ($key === null || $key === false || $key === '') {
            throw new ValidationException('api_key is required (or set WPUSH_API_KEY)');
        }
        $this->apiKey = (string) $key;
        $this->baseUrl = rtrim($baseUrl, '/');
        $this->timeout = $timeout;
        $this->defaultChannel = $defaultChannel;
        $this->transport = $transport ?? new StreamHttpTransport($timeout);
    }

    public function getApiKey(): string
    {
        return $this->apiKey;
    }

    /**
     * @param string|array|null $channel
     */
    public function send(
        string $title,
        ?string $content = null,
        $channel = null,
        ?string $option = null,
        ?string $topicCode = null,
        ?string $url = null,
        ?string $idempotencyKey = null
    ): string {
        if ($title === '') {
            throw new ValidationException('title is required');
        }
        if ($option !== null && $option !== '' && $topicCode !== null && $topicCode !== '') {
            throw new ValidationException('option and topic_code cannot be used together');
        }
        $ch = self::joinChannel($channel !== null ? $channel : $this->defaultChannel);
        $payload = ['apikey' => $this->apiKey, 'title' => $title];
        if ($content !== null) {
            $payload['content'] = $content;
        }
        if ($ch !== null) {
            $payload['channel'] = $ch;
        }
        if ($option !== null && $option !== '') {
            $payload['option'] = $option;
        }
        if ($topicCode !== null && $topicCode !== '') {
            $payload['topic_code'] = $topicCode;
        }
        if ($url !== null && $url !== '') {
            $payload['url'] = $url;
        }
        $data = $this->post('/api/v1/send', $payload, $idempotencyKey);
        return self::stringifyId($data);
    }

    public function query(string $messageId, ?string $idempotencyKey = null)
    {
        if ($messageId === '') {
            throw new ValidationException('message_id is required');
        }
        return $this->post('/api/v1/query', [
            'apikey' => $this->apiKey,
            'id' => (string) $messageId,
        ], $idempotencyKey);
    }

    public function sendMail(
        string $to,
        string $title,
        ?string $content = null,
        ?string $idempotencyKey = null
    ): string {
        if ($to === '') {
            throw new ValidationException('to is required');
        }
        if ($title === '') {
            throw new ValidationException('title is required');
        }
        $payload = ['apikey' => $this->apiKey, 'to' => $to, 'title' => $title];
        if ($content !== null) {
            $payload['content'] = $content;
        }
        return self::stringifyId($this->post('/api/v1/send_mail', $payload, $idempotencyKey));
    }

    public function sendCode(string $phone, string $code, ?string $idempotencyKey = null): string
    {
        if ($phone === '' || !preg_match('/^(\+?86)?1\d{10}$/', $phone)) {
            throw new ValidationException('invalid phone');
        }
        if ($code === '' || !preg_match('/^[A-Za-z0-9]{1,6}$/', $code)) {
            throw new ValidationException('invalid code');
        }
        return self::stringifyId($this->post('/api/v1/send_code', [
            'apikey' => $this->apiKey,
            'phone' => $phone,
            'code' => $code,
        ], $idempotencyKey));
    }

    public function queryRelay(string $id, ?string $idempotencyKey = null)
    {
        if ($id === '') {
            throw new ValidationException('id is required');
        }
        return $this->post('/api/v1/query_relay', [
            'apikey' => $this->apiKey,
            'id' => (string) $id,
        ], $idempotencyKey);
    }

    /**
     * @param array<string,mixed> $payload
     * @return mixed
     */
    private function post(string $path, array $payload, ?string $idempotencyKey = null)
    {
        $headers = [
            'Content-Type' => 'application/json',
            'Accept' => 'application/json',
            'User-Agent' => self::USER_AGENT,
            'X-API-Key' => $this->apiKey,
        ];
        if ($idempotencyKey !== null && $idempotencyKey !== '') {
            $headers['X-Idempotency-Key'] = $idempotencyKey;
        }
        $json = json_encode($payload, JSON_UNESCAPED_UNICODE);
        if ($json === false) {
            throw new WPushException('failed to encode JSON');
        }
        try {
            $resp = $this->transport->post($this->baseUrl . $path, $headers, $json);
        } catch (WPushException $e) {
            throw $e;
        } catch (\Throwable $e) {
            throw new WPushException($e->getMessage());
        }
        $status = (int) ($resp['status'] ?? 0);
        $raw = (string) ($resp['body'] ?? '');
        $body = json_decode($raw, true);
        if (!is_array($body)) {
            throw new WPushException('invalid JSON response', null, $status, $raw);
        }
        return $this->parseEnvelope($body, $status);
    }

    /**
     * @param array<string,mixed> $body
     * @return mixed
     */
    private function parseEnvelope(array $body, int $httpStatus)
    {
        $code = $body['code'] ?? null;
        $message = $body['message'] ?? '';
        $data = $body['data'] ?? null;
        if (!self::isSuccessCode($code)) {
            $errCode = is_int($code) ? $code : null;
            // reject bool / float-ish non-int
            if (is_bool($code)) {
                $errCode = null;
            } elseif (is_float($code) && (float) (int) $code === $code) {
                $errCode = (int) $code;
            }
            $msg = is_string($message) ? $message : (string) $message;
            if ($msg === '') {
                $msg = 'request failed';
            }
            throw new WPushException($msg, $errCode, $httpStatus, $data);
        }
        return $data;
    }

    /** @param mixed $code */
    public static function isSuccessCode($code): bool
    {
        // JSON true/false decode to bool; numbers to int/float. Reject bool.
        if (is_bool($code)) {
            return false;
        }
        if (is_int($code)) {
            return $code === 0;
        }
        if (is_float($code)) {
            return $code === 0.0;
        }
        return false;
    }

    /**
     * @param string|array|null $channel
     */
    public static function joinChannel($channel): ?string
    {
        if ($channel === null) {
            return null;
        }
        if (is_string($channel)) {
            return $channel;
        }
        if (is_array($channel)) {
            return implode(',', array_map('strval', $channel));
        }
        return (string) $channel;
    }

    /** @param mixed $data */
    public static function stringifyId($data): string
    {
        if ($data === null) {
            return '';
        }
        return (string) $data;
    }
}
