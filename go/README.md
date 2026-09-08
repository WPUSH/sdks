# wpush (Go)

Official WPUSH Go SDK. Module: `github.com/WPUSH/sdks/go`

```go
import "github.com/WPUSH/sdks/go"
c, _ := wpush.NewClient(wpush.Options{}) // WPUSH_API_KEY
id, _ := c.Send(wpush.SendParams{Title: "t", Content: "c", Channel: "wechat"})
// also: SendMail / SendCode / QueryRelay
```

UA: `wpush-go/0.1.0`. See root SPEC.md.
