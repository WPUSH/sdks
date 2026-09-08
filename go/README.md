# wpush (Go)

Official WPUSH Go SDK. Module: `github.com/WPUSH/sdks/go`

## Install / env

```bash
go get github.com/WPUSH/sdks/go
export WPUSH_API_KEY=WPUSH_your_key
```

```go
package main

import (
	"fmt"
	"log"

	wpush "github.com/WPUSH/sdks/go"
)

func main() {
	client, err := wpush.NewClient(wpush.Options{}) // reads WPUSH_API_KEY
	if err != nil {
		log.Fatal(err)
	}
	_ = client
	// or: wpush.NewClient(wpush.Options{APIKey: "WPUSH_your_key"})
}
```

## Send — basic wechat

```go
msgID, err := client.Send(wpush.SendParams{
	Title:   "告警标题",
	Content: "正文内容",
	Channel: "wechat",
})
if err != nil {
	log.Fatal(err)
}
fmt.Println(msgID)
```

## Send — multi-channel + Option + IdempotencyKey

`Option` is the per-channel instance code (e.g. Feishu `ops`). Mutually exclusive with `TopicCode`.

```go
msgID, err := client.Send(wpush.SendParams{
	Title:          "运维告警",
	Content:        "CPU 过高",
	Channel:        []string{"feishu", "dingtalk"},
	Option:         "ops",
	IdempotencyKey: "demo-idem-001",
})
if err != nil {
	log.Fatal(err)
}
fmt.Println(msgID)
```

## Send — TopicCode (no Option)

```go
msgID, err := client.Send(wpush.SendParams{
	Title:     "主题广播",
	Content:   "全员通知",
	TopicCode: "mytopic",
})
if err != nil {
	log.Fatal(err)
}
fmt.Println(msgID)
```

## Query

```go
info, err := client.Query(msgID, "")
if err != nil {
	log.Fatal(err)
}
fmt.Println(info) // id / title / status / channel ...
```

## SendMail

```go
relayID, err := client.SendMail(wpush.SendMailParams{
	To:      "user@example.com",
	Title:   "邮件标题",
	Content: "邮件正文",
})
if err != nil {
	log.Fatal(err)
}
fmt.Println(relayID)
```

## SendCode

```go
relayID, err := client.SendCode(wpush.SendCodeParams{
	Phone: "13800138000",
	Code:  "123456",
})
if err != nil {
	log.Fatal(err)
}
fmt.Println(relayID)
```

## QueryRelay

```go
relay, err := client.QueryRelay(relayID, "")
if err != nil {
	log.Fatal(err)
}
fmt.Println(relay)
```

## Error handling

Go uses `*wpush.Error` (API/transport) and `*wpush.ValidationError` (local pre-check).

```go
_, err := client.Send(wpush.SendParams{Title: "", Channel: "wechat"})
if err != nil {
	switch e := err.(type) {
	case *wpush.ValidationError:
		fmt.Println("validation:", e.Message)
	case *wpush.Error:
		fmt.Println("api:", e.Message, e.Code, e.HTTPStatus)
	default:
		log.Fatal(err)
	}
}
```

## Spec & UA

See [SPEC.md](../SPEC.md).

User-Agent: `wpush-go/0.1.0`
