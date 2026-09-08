<?php
declare(strict_types=1);

namespace WPush;

/** Zero-dep HTTP via PHP stream contexts. */
final class StreamHttpTransport implements HttpTransport
{
    /** @var float */
    private $timeout;

    public function __construct(float $timeout = 30.0)
    {
        $this->timeout = $timeout;
    }

    public function post(string $url, array $headers, string $jsonBody): array
    {
        $headerLines = [];
        foreach ($headers as $k => $v) {
            $headerLines[] = $k . ': ' . $v;
        }
        $opts = [
            'http' => [
                'method' => 'POST',
                'header' => implode("\r\n", $headerLines),
                'content' => $jsonBody,
                'timeout' => $this->timeout,
                'ignore_errors' => true,
            ],
        ];
        $ctx = stream_context_create($opts);
        $body = @file_get_contents($url, false, $ctx);
        if ($body === false) {
            throw new WPushException('transport error');
        }
        $status = 0;
        if (isset($http_response_header[0]) && preg_match('/\s(\d{3})\s/', $http_response_header[0], $m)) {
            $status = (int) $m[1];
        }
        return ['status' => $status, 'body' => $body];
    }
}
