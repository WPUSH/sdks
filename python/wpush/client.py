from __future__ import annotations

import json
import re
import os
import urllib.error
import urllib.request
from typing import Any, Mapping, Optional, Sequence, Union

from .errors import ValidationError, WPushError

DEFAULT_BASE_URL = "https://api.wpush.cn"
USER_AGENT = "wpush-python/0.1.0"
_PHONE_RE = re.compile(r"^(\+?86)?1\d{10}$")
_CODE_RE = re.compile(r"^[A-Za-z0-9]{1,6}$")
ChannelType = Union[str, Sequence[str]]


def _join_channel(channel: Optional[ChannelType]) -> Optional[str]:
    if channel is None:
        return None
    if isinstance(channel, str):
        return channel
    return ",".join(str(c) for c in channel)


def _stringify_id(data: Any) -> str:
    if data is None:
        return ""
    return str(data)


def _is_success_code(code: Any) -> bool:
    # Reject bool: True/False are int subclasses in Python.
    return type(code) is int and code == 0


class Client:
    def __init__(
        self,
        api_key: Optional[str] = None,
        *,
        base_url: str = DEFAULT_BASE_URL,
        timeout: float = 30.0,
        default_channel: Optional[ChannelType] = None,
    ) -> None:
        key = api_key if api_key is not None else os.environ.get("WPUSH_API_KEY")
        if not key:
            raise ValidationError("api_key is required (or set WPUSH_API_KEY)")
        self.api_key = key
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.default_channel = default_channel

    def _headers(self, idempotency_key: Optional[str] = None) -> dict:
        h = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": USER_AGENT,
            "X-API-Key": self.api_key,
        }
        if idempotency_key:
            h["X-Idempotency-Key"] = idempotency_key
        return h

    def _parse_envelope(self, body: Any, http_status: int) -> Any:
        if not isinstance(body, dict):
            raise WPushError("invalid JSON envelope", http_status=http_status, data=body)
        code = body.get("code")
        message = body.get("message") or ""
        data = body.get("data")
        if not _is_success_code(code):
            err_code = code if type(code) is int else None
            raise WPushError(str(message) or "request failed", code=err_code, http_status=http_status, data=data)
        return data

    def _post(self, path: str, payload: Mapping[str, Any], *, idempotency_key: Optional[str] = None) -> Any:
        url = self.base_url + path
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers=self._headers(idempotency_key), method="POST")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
                status = getattr(resp, "status", 200)
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            status = e.code
            try:
                body = json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                raise WPushError(raw or e.reason, http_status=status) from e
            return self._parse_envelope(body, status)
        except urllib.error.URLError as e:
            raise WPushError(str(e.reason), http_status=None) from e
        try:
            body = json.loads(raw) if raw else {}
        except json.JSONDecodeError as e:
            raise WPushError("invalid JSON response", http_status=status, data=raw) from e
        return self._parse_envelope(body, status)

    def send(
        self,
        title: str,
        content: Optional[str] = None,
        *,
        channel: Optional[ChannelType] = None,
        option: Optional[str] = None,
        topic_code: Optional[str] = None,
        url: Optional[str] = None,
        idempotency_key: Optional[str] = None,
    ) -> str:
        if not title:
            raise ValidationError("title is required")
        if option and topic_code:
            raise ValidationError("option and topic_code cannot be used together")
        ch = _join_channel(channel if channel is not None else self.default_channel)
        payload: dict[str, Any] = {"apikey": self.api_key, "title": title}
        if content is not None:
            payload["content"] = content
        if ch is not None:
            payload["channel"] = ch
        if option:
            payload["option"] = option
        if topic_code:
            payload["topic_code"] = topic_code
        if url:
            payload["url"] = url
        data = self._post("/api/v1/send", payload, idempotency_key=idempotency_key)
        return _stringify_id(data)

    def query(self, message_id: str, *, idempotency_key: Optional[str] = None) -> Any:
        if not message_id:
            raise ValidationError("message_id is required")
        payload = {"apikey": self.api_key, "id": str(message_id)}
        return self._post("/api/v1/query", payload, idempotency_key=idempotency_key)

    def send_mail(
        self,
        to: str,
        title: str,
        content: Optional[str] = None,
        *,
        idempotency_key: Optional[str] = None,
    ) -> str:
        if not to:
            raise ValidationError("to is required")
        if not title:
            raise ValidationError("title is required")
        payload: dict[str, Any] = {"apikey": self.api_key, "to": to, "title": title}
        if content is not None:
            payload["content"] = content
        data = self._post("/api/v1/send_mail", payload, idempotency_key=idempotency_key)
        return _stringify_id(data)

    def send_code(
        self,
        phone: str,
        code: str,
        *,
        idempotency_key: Optional[str] = None,
    ) -> str:
        if not phone or not _PHONE_RE.match(str(phone)):
            raise ValidationError("invalid phone")
        if not code or not _CODE_RE.match(str(code)):
            raise ValidationError("invalid code")
        payload = {"apikey": self.api_key, "phone": phone, "code": code}
        data = self._post("/api/v1/send_code", payload, idempotency_key=idempotency_key)
        return _stringify_id(data)

    def query_relay(self, relay_id: str, *, idempotency_key: Optional[str] = None) -> Any:
        if not relay_id:
            raise ValidationError("id is required")
        payload = {"apikey": self.api_key, "id": str(relay_id)}
        return self._post("/api/v1/query_relay", payload, idempotency_key=idempotency_key)
