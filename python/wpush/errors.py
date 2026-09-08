from __future__ import annotations

from typing import Any, Optional


class WPushError(Exception):
    """API or transport error from WPUSH."""

    def __init__(
        self,
        message: str,
        *,
        code: Optional[int] = None,
        http_status: Optional[int] = None,
        data: Any = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.code = code
        self.http_status = http_status
        self.data = data

    def __str__(self) -> str:
        parts = [self.message]
        if self.code is not None:
            parts.append(f"code={self.code}")
        if self.http_status is not None:
            parts.append(f"http={self.http_status}")
        return " | ".join(parts)


class ValidationError(WPushError):
    """Client-side validation failure before the request is sent."""

    def __init__(self, message: str) -> None:
        super().__init__(message, code=None, http_status=None)
