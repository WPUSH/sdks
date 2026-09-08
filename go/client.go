package wpush

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	defaultBaseURL = "https://api.wpush.cn"
	userAgent      = "wpush-go/0.1.0"
)

// Client talks to the WPUSH open API.
type Client struct {
	APIKey         string
	BaseURL        string
	HTTPClient     *http.Client
	DefaultChannel string
}

// Options configures a Client.
type Options struct {
	APIKey         string
	BaseURL        string
	HTTPClient     *http.Client
	Timeout        time.Duration
	DefaultChannel string
}

// NewClient creates a Client. API key from opts or WPUSH_API_KEY.
func NewClient(opts Options) (*Client, error) {
	key := opts.APIKey
	if key == "" {
		key = os.Getenv("WPUSH_API_KEY")
	}
	if key == "" {
		return nil, &ValidationError{Message: "api_key is required (or set WPUSH_API_KEY)"}
	}
	base := opts.BaseURL
	if base == "" {
		base = defaultBaseURL
	}
	hc := opts.HTTPClient
	if hc == nil {
		to := opts.Timeout
		if to == 0 {
			to = 30 * time.Second
		}
		hc = &http.Client{Timeout: to}
	}
	return &Client{APIKey: key, BaseURL: strings.TrimRight(base, "/"), HTTPClient: hc, DefaultChannel: opts.DefaultChannel}, nil
}

func joinChannel(ch any) string {
	switch v := ch.(type) {
	case nil:
		return ""
	case string:
		return v
	case []string:
		return strings.Join(v, ",")
	default:
		return fmt.Sprint(v)
	}
}

func isSuccessCode(code any) bool {
	switch v := code.(type) {
	case float64:
		return v == 0
	case json.Number:
		i, err := v.Int64()
		return err == nil && i == 0
	case int:
		return v == 0
	default:
		return false
	}
}

type envelope struct {
	Code    any             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

func (c *Client) post(path string, payload map[string]any, idempotencyKey string) (json.RawMessage, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequest(http.MethodPost, c.BaseURL+path, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", userAgent)
	req.Header.Set("X-API-Key", c.APIKey)
	if idempotencyKey != "" {
		req.Header.Set("X-Idempotency-Key", idempotencyKey)
	}
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, &Error{Message: err.Error()}
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	var env envelope
	if err := json.Unmarshal(raw, &env); err != nil {
		return nil, &Error{Message: "invalid JSON response", HTTPStatus: resp.StatusCode, Data: string(raw)}
	}
	if !isSuccessCode(env.Code) {
		var codePtr *int
		if f, ok := env.Code.(float64); ok {
			i := int(f)
			codePtr = &i
		}
		return nil, &Error{Message: env.Message, Code: codePtr, HTTPStatus: resp.StatusCode, Data: env.Data}
	}
	return env.Data, nil
}

// SendParams are arguments for Send.
type SendParams struct {
	Title          string
	Content        string
	Channel        any // string or []string
	Option         string
	TopicCode      string
	URL            string
	IdempotencyKey string
}

// Send posts /api/v1/send and returns the message id string.
func (c *Client) Send(p SendParams) (string, error) {
	if p.Title == "" {
		return "", &ValidationError{Message: "title is required"}
	}
	if p.Option != "" && p.TopicCode != "" {
		return "", &ValidationError{Message: "option and topic_code cannot be used together"}
	}
	ch := joinChannel(p.Channel)
	if ch == "" {
		ch = c.DefaultChannel
	}
	payload := map[string]any{"apikey": c.APIKey, "title": p.Title}
	if p.Content != "" {
		payload["content"] = p.Content
	}
	if ch != "" {
		payload["channel"] = ch
	}
	if p.Option != "" {
		payload["option"] = p.Option
	}
	if p.TopicCode != "" {
		payload["topic_code"] = p.TopicCode
	}
	if p.URL != "" {
		payload["url"] = p.URL
	}
	data, err := c.post("/api/v1/send", payload, p.IdempotencyKey)
	if err != nil {
		return "", err
	}
	var s string
	if err := json.Unmarshal(data, &s); err == nil {
		return s, nil
	}
	var n json.Number
	if err := json.Unmarshal(data, &n); err == nil {
		return n.String(), nil
	}
	return strings.Trim(string(data), "\""), nil
}

// Query posts /api/v1/query and returns decoded data as map.
func (c *Client) Query(messageID string, idempotencyKey string) (map[string]any, error) {
	if messageID == "" {
		return nil, &ValidationError{Message: "message_id is required"}
	}
	payload := map[string]any{"apikey": c.APIKey, "id": messageID}
	data, err := c.post("/api/v1/query", payload, idempotencyKey)
	if err != nil {
		return nil, err
	}
	var out map[string]any
	if err := json.Unmarshal(data, &out); err != nil {
		return nil, &Error{Message: "invalid query data", Data: string(data)}
	}
	return out, nil
}
