<?php
declare(strict_types=1);

namespace WPush;

class WPushException extends \Exception
{
    /** @var int|null */
    public $codeValue;
    /** @var int|null */
    public $httpStatus;
    /** @var mixed */
    public $data;

    /** @param mixed $data */
    public function __construct(string $message, ?int $code = null, ?int $httpStatus = null, $data = null)
    {
        parent::__construct($message);
        $this->codeValue = $code;
        $this->httpStatus = $httpStatus;
        $this->data = $data;
    }

    public function getErrorCode(): ?int
    {
        return $this->codeValue;
    }

    public function __toString(): string
    {
        $parts = [$this->message];
        if ($this->codeValue !== null) {
            $parts[] = 'code=' . $this->codeValue;
        }
        if ($this->httpStatus !== null) {
            $parts[] = 'http=' . $this->httpStatus;
        }
        return implode(' | ', $parts);
    }
}
