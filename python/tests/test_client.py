import io
import json
import urllib.error
from unittest.mock import MagicMock, patch

import pytest

from wpush import Client, ValidationError, WPushError
from wpush.client import USER_AGENT


def _http_response(payload, status=200):
    body = json.dumps(payload).encode("utf-8")
    resp = MagicMock()
    resp.status = status
    resp.read.return_value = body
    resp.__enter__.return_value = resp
    resp.__exit__.return_value = False
    return resp


@patch("wpush.client.urllib.request.urlopen")
def test_send_success(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": 0, "message": "success", "data": "12345"})
    c = Client(api_key="k")
    mid = c.send("t", "c", channel=["wechat", "dingtalk"])
    assert mid == "12345"
    req = mock_urlopen.call_args[0][0]
    assert req.get_header("User-agent") == USER_AGENT or req.headers.get("User-Agent") == USER_AGENT
    assert req.get_header("X-api-key") == "k" or req.headers.get("X-API-Key") == "k"
    body = json.loads(req.data.decode())
    assert body["apikey"] == "k"
    assert body["channel"] == "wechat,dingtalk"
    assert body["title"] == "t"


@patch("wpush.client.urllib.request.urlopen")
def test_send_numeric_data_stringified(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": 0, "message": "ok", "data": 999})
    assert Client(api_key="k").send("t") == "999"


@patch("wpush.client.urllib.request.urlopen")
def test_reject_bool_code(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": False, "message": "nope", "data": None})
    with pytest.raises(WPushError) as ei:
        Client(api_key="k").send("t")
    assert ei.value.code is None


@patch("wpush.client.urllib.request.urlopen")
def test_api_error_401(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": 401, "message": "API Key错误", "data": None})
    with pytest.raises(WPushError) as ei:
        Client(api_key="bad").send("t")
    assert ei.value.code == 401


def test_option_topic_conflict():
    c = Client(api_key="k")
    with pytest.raises(ValidationError):
        c.send("t", option="ops", topic_code="topic1")


def test_missing_api_key(monkeypatch):
    monkeypatch.delenv("WPUSH_API_KEY", raising=False)
    with pytest.raises(ValidationError):
        Client()


@patch("wpush.client.urllib.request.urlopen")
def test_query_success(mock_urlopen):
    data = {"id": "123", "status": 1, "title": "t"}
    mock_urlopen.return_value = _http_response({"code": 0, "message": "success", "data": data})
    out = Client(api_key="k").query("123")
    assert out["id"] == "123"
    assert out["status"] == 1
    body = json.loads(mock_urlopen.call_args[0][0].data.decode())
    assert body["id"] == "123"
    assert body["apikey"] == "k"


def test_env_api_key(monkeypatch):
    monkeypatch.setenv("WPUSH_API_KEY", "from-env")
    c = Client()
    assert c.api_key == "from-env"


@patch("wpush.client.urllib.request.urlopen")
def test_idempotency_header(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": 0, "message": "ok", "data": "1"})
    Client(api_key="k").send("t", idempotency_key="idem-1")
    req = mock_urlopen.call_args[0][0]
    assert req.get_header("X-idempotency-key") == "idem-1" or req.headers.get("X-Idempotency-Key") == "idem-1"


@patch("wpush.client.urllib.request.urlopen")
def test_send_mail_success(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": 0, "message": "success", "data": "mail-1"})
    mid = Client(api_key="k").send_mail("a@b.com", "hi", "body")
    assert mid == "mail-1"
    req = mock_urlopen.call_args[0][0]
    assert req.full_url.endswith("/api/v1/send_mail")
    body = json.loads(req.data.decode())
    assert body["to"] == "a@b.com"
    assert body["title"] == "hi"
    assert body["content"] == "body"
    assert body["apikey"] == "k"


@patch("wpush.client.urllib.request.urlopen")
def test_send_code_success(mock_urlopen):
    mock_urlopen.return_value = _http_response({"code": 0, "message": "success", "data": "code-1"})
    mid = Client(api_key="k").send_code("13800138000", "123456")
    assert mid == "code-1"
    req = mock_urlopen.call_args[0][0]
    assert req.full_url.endswith("/api/v1/send_code")
    body = json.loads(req.data.decode())
    assert body["phone"] == "13800138000"
    assert body["code"] == "123456"


def test_send_code_bad_phone():
    with pytest.raises(ValidationError):
        Client(api_key="k").send_code("123", "123456")


@patch("wpush.client.urllib.request.urlopen")
def test_query_relay_success(mock_urlopen):
    data = {"id": "r1", "status": 1}
    mock_urlopen.return_value = _http_response({"code": 0, "message": "success", "data": data})
    out = Client(api_key="k").query_relay("r1")
    assert out["id"] == "r1"
    body = json.loads(mock_urlopen.call_args[0][0].data.decode())
    assert body["id"] == "r1"
    assert mock_urlopen.call_args[0][0].full_url.endswith("/api/v1/query_relay")
