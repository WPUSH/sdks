# wpush (Python)

Official WPUSH Python SDK. Zero runtime deps (stdlib `urllib`).

```bash
pip install -e ".[dev]"
export WPUSH_API_KEY=your_key
```

```python
from wpush import Client
c = Client()  # or Client(api_key="...")
mid = c.send("title", "content", channel="wechat")
print(c.query(mid))
```

See root [SPEC.md](../SPEC.md). UA: `wpush-python/0.1.0`
