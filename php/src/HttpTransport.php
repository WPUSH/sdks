<?php
declare(strict_types=1);

namespace WPush;

interface HttpTransport
{
    /**
     * @param array<string,string> $headers
     * @return array{status:int,body:string}
     */
    public function post(string $url, array $headers, string $jsonBody): array;
}
