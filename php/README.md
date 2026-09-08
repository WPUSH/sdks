# wpush (PHP)

官方 WPUSH PHP SDK。零运行时依赖（stream HTTP，可注入 transport）。

## 安装 / Install

```bash
composer require wpush/wpush
# 或从源码目录: composer install
export WPUSH_API_KEY=WPUSH_your_key
```

```php
use WPush\Client;
use WPush\ValidationException;
use WPush\WPushException;

$client = new Client(); // 读取 WPUSH_API_KEY
// 或显式传 key:
// $client = new Client('WPUSH_your_key');
```

## send — 基础微信推送

```php
$msgId = $client->send('告警标题', '正文内容', 'wechat');
echo $msgId, PHP_EOL;
```

## send — 多渠道 + option + 幂等键

`option` 为渠道内实例编码（如飞书 `ops`），不可与 `topicCode` 同用。

```php
$msgId = $client->send(
    '运维告警',
    'CPU 过高',
    ['feishu', 'dingtalk'],
    'ops',
    null,
    null,
    'demo-idem-001'
);
echo $msgId, PHP_EOL;
```

## send — topicCode（不带 option）

```php
$msgId = $client->send(
    '主题广播',
    '全员通知',
    null,
    null,
    'mytopic'
);
echo $msgId, PHP_EOL;
```

## query

```php
$info = $client->query($msgId);
print_r($info); // id / title / status / channel ...
```

## sendMail

```php
$relayId = $client->sendMail(
    'user@example.com',
    '邮件标题',
    '邮件正文'
);
echo $relayId, PHP_EOL;
```

## sendCode

```php
$relayId = $client->sendCode('13800138000', '123456');
echo $relayId, PHP_EOL;
```

## queryRelay

```php
$relay = $client->queryRelay($relayId);
print_r($relay);
```

## 错误处理

```php
try {
    $client->send(''); // 本地预检失败
} catch (ValidationException $e) {
    echo 'validation: ', $e->getMessage(), PHP_EOL;
} catch (WPushException $e) {
    echo 'api: ', $e->getMessage(), ' ', $e->getErrorCode(), ' ', $e->httpStatus, PHP_EOL;
}
```

## 规范与 UA

跨语言契约见 [SPEC.md](../SPEC.md)。

User-Agent：`wpush-php/0.1.0`
