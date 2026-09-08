package wpush

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSendSuccess(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/send" {
			t.Fatalf("path %s", r.URL.Path)
		}
		if r.Header.Get("User-Agent") != userAgent {
			t.Fatalf("ua %s", r.Header.Get("User-Agent"))
		}
		if r.Header.Get("X-API-Key") != "k" {
			t.Fatalf("key header")
		}
		body, _ := io.ReadAll(r.Body)
		var m map[string]any
		_ = json.Unmarshal(body, &m)
		if m["apikey"] != "k" || m["channel"] != "wechat,dingtalk" {
			t.Fatalf("body %#v", m)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"code": 0, "message": "success", "data": "12345"})
	}))
	defer ts.Close()
	c, err := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	if err != nil {
		t.Fatal(err)
	}
	id, err := c.Send(SendParams{Title: "t", Content: "c", Channel: []string{"wechat", "dingtalk"}})
	if err != nil || id != "12345" {
		t.Fatalf("id=%s err=%v", id, err)
	}
}

func TestRejectBoolCode(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"code": false, "message": "nope", "data": nil})
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	_, err := c.Send(SendParams{Title: "t"})
	if err == nil {
		t.Fatal("expected error")
	}
	if e, ok := err.(*Error); !ok || e.Code != nil {
		t.Fatalf("got %#v", err)
	}
}

func TestAPIError401(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"code": 401, "message": "bad key", "data": nil})
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "bad", BaseURL: ts.URL})
	_, err := c.Send(SendParams{Title: "t"})
	e, ok := err.(*Error)
	if !ok || e.Code == nil || *e.Code != 401 {
		t.Fatalf("%#v", err)
	}
}

func TestOptionTopicConflict(t *testing.T) {
	c, _ := NewClient(Options{APIKey: "k", BaseURL: "http://example.invalid"})
	_, err := c.Send(SendParams{Title: "t", Option: "ops", TopicCode: "x"})
	if _, ok := err.(*ValidationError); !ok {
		t.Fatalf("%#v", err)
	}
}

func TestQuerySuccess(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/query" {
			t.Fatalf("path %s", r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		var m map[string]any
		_ = json.Unmarshal(body, &m)
		if m["id"] != "123" || m["apikey"] != "k" {
			t.Fatalf("%#v", m)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"code": 0, "message": "success",
			"data": map[string]any{"id": "123", "status": float64(1)},
		})
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	out, err := c.Query("123", "")
	if err != nil || out["id"] != "123" {
		t.Fatalf("%v %#v", err, out)
	}
}

func TestSendNumericData(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`{"code":0,"message":"ok","data":999}`))
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	id, err := c.Send(SendParams{Title: "t"})
	if err != nil || id != "999" {
		t.Fatalf("id=%s err=%v", id, err)
	}
}

func TestSendMailSuccess(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/send_mail" {
			t.Fatalf("path %s", r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		var m map[string]any
		_ = json.Unmarshal(body, &m)
		if m["to"] != "a@b.com" || m["title"] != "hi" || m["content"] != "body" || m["apikey"] != "k" {
			t.Fatalf("body %#v", m)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"code": 0, "message": "success", "data": "mail-1"})
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	id, err := c.SendMail(SendMailParams{To: "a@b.com", Title: "hi", Content: "body"})
	if err != nil || id != "mail-1" {
		t.Fatalf("id=%s err=%v", id, err)
	}
}

func TestSendCodeSuccess(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/send_code" {
			t.Fatalf("path %s", r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		var m map[string]any
		_ = json.Unmarshal(body, &m)
		if m["phone"] != "13800138000" || m["code"] != "123456" {
			t.Fatalf("body %#v", m)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"code": 0, "message": "success", "data": "code-1"})
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	id, err := c.SendCode(SendCodeParams{Phone: "13800138000", Code: "123456"})
	if err != nil || id != "code-1" {
		t.Fatalf("id=%s err=%v", id, err)
	}
}

func TestSendCodeBadPhone(t *testing.T) {
	c, _ := NewClient(Options{APIKey: "k", BaseURL: "http://example.invalid"})
	_, err := c.SendCode(SendCodeParams{Phone: "123", Code: "123456"})
	if _, ok := err.(*ValidationError); !ok {
		t.Fatalf("%#v", err)
	}
}

func TestQueryRelaySuccess(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/query_relay" {
			t.Fatalf("path %s", r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		var m map[string]any
		_ = json.Unmarshal(body, &m)
		if m["id"] != "r1" || m["apikey"] != "k" {
			t.Fatalf("%#v", m)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"code": 0, "message": "success",
			"data": map[string]any{"id": "r1", "status": float64(1)},
		})
	}))
	defer ts.Close()
	c, _ := NewClient(Options{APIKey: "k", BaseURL: ts.URL})
	out, err := c.QueryRelay("r1", "")
	if err != nil || out["id"] != "r1" {
		t.Fatalf("%v %#v", err, out)
	}
}
