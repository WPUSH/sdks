<?php
declare(strict_types=1);

namespace WPush;

class ValidationException extends WPushException
{
    public function __construct(string $message)
    {
        parent::__construct($message, null, null, null);
    }
}
