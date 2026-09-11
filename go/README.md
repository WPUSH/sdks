# wpush (Go)

官方 WPUSH Go SDK。模块：`github.com/WPUSH/sdks/go`

## 安装 / 环境变量

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
	client, err := wpush.NewClient(wpush.Options{}) // 读取 WPUSH_API_KEY
	if err != nil {
		log.Fatal(err)
	}
	_ = client
	// 或显式传 key:
	// wpush.NewClient(wpush.Options{APIKey: "WPUSH_your_key"})
}
```

## Send — 基础微信推送

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

## Send — 多渠道 + Option + IdempotencyKey

`Option` 为渠道内实例编码（如飞书 `ops`），不可与 `TopicCode` 同用。

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

## Send — TopicCode（不带 Option）

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

## 错误处理

Go 使用 `*wpush.Error`（API/传输错误）与 `*wpush.ValidationError`（本地预检）。

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

## 规范与 UA

跨语言契约见 [SPEC.md](../SPEC.md)。

User-Agent：`wpush-go/0.1.0`
