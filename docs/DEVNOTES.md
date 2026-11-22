# 开发笔记

记录移植 iOS 版 `Sources/BiliCore` 时踩到的坑，方便后续维护。

## 1. cid / aid 必须是 64 位

2026 年起部分视频的 `cid` 已超过 32 位有符号整数范围。例如 `43750131546` 用 `Int` 截断后会变成 `800458586`，表现为 `playurl` 稳定返回 `-404 啥都木有`，而详情接口、评论接口都正常，很难定位。

- iOS 的 `Int` 是 64 位，原项目没有这个问题。
- 本项目 `Video.cid`、`Video.aid`、`VideoPart.cid`、`BiliClient` 的取流/评论参数全部使用 `Long`。
- Navigation 路由不支持 Long 参数，`player/{id}?cid={cid}` 的 cid 以字符串传递后再 `toLongOrNull()`。

## 2. 风控与请求头

- **buvid 补取**：冷启动时 Wi-Fi 往往还没就绪，此时 `finger/spi` 会失败。`raw()` 在 `buvid3` 为空时会同步补取一次，否则后续 `view` / `playurl` 会返回 HTTP 412。
- **请求头**：`User-Agent`、`Referer`、`Origin`、`Accept-Language` 缺一不可；实测缺 `Origin` 时 `view` 接口 412。
- **UP 主投稿**：`/x/space/wbi/arc/search` 除 WBI 外还有 `dm_img_*`、`w_webid` 等浏览器指纹校验，本项目不做绕过，失败时提示并提供浏览器入口。

## 3. WBI 签名

`nav` 返回的 `wbi_img.img_url` / `sub_url` 取文件名后拼成 64 字符，按 32 位 mixin 表重排得到 `mixin_key`；参数去掉 `!'()*`、按 key 排序、RFC 3986 编码后拼 `wts`，`w_rid = MD5(query + mixin_key)`。

固定测试向量见 `app/src/test/java/.../CoreTest.kt`，改动签名逻辑时先跑单测。

## 4. playurl

- 普通视频优先 `/x/player/wbi/playurl`（带签名），失败回退 `/x/player/playurl`。
- 番剧使用 `/pgc/player/web/v2/playurl`，返回体在 `result.video_info.dash`。
- 视频轨只保留 `avc` / `hev` / `hvc` / `dvh`，忽略 AV1（部分设备硬解不可用）；音频选 `mp4a` 中码率最高的一条。
- 请求 `qn=127&fnval=4048&fourk=1`，实际可用画质由账号权限决定。

## 5. 播放实现

- `MergingMediaSource(videoOnly, audioOnly)` 把两条 DASH 流合成一路播放。
- `DefaultHttpDataSource` 统一加 `Referer` / `User-Agent`，并允许跨协议重定向。
- 切换画质保留当前进度；切换分 P 从头播放。
- 全屏退出时显式设置 `SCREEN_ORIENTATION_PORTRAIT`，避免部分设备停留在横屏。

## 6. 弹幕

- `seg.so` 返回 protobuf（`DmSegMobileReply`），手写 varint 读取字段：`id=1`、`progress=2`（毫秒）、`mode=3`、`color=5`、`content=7`。
- 渲染以播放器进度计算横坐标，暂停、拖动、倍速天然同步，不需要额外计时器。
- 轨道按时间顺序贪心分配；放不下的弹幕直接丢弃，避免重叠。
- 顶部预留 1.6 行安全区，减少与标题栏、状态栏的重叠。

## 7. 测试

```bash
./gradlew testDebugUnitTest
```

覆盖 WBI mixin / 签名向量、弹幕 protobuf 解码、弹幕接口返回 JSON 时的容错。

## 8. 登录链路

- **扫码**：`qrcode/generate` 生成二维码，`poll` 返回 `86101/86090/86038/0`。登录成功时部分账号的 `data.url` 为空，必须同时解析响应头 `Set-Cookie`，否则会误报「未取得 Cookie」。
- **网页登录**：WebView 打开 `passport.bilibili.com/login`，使用标准手机 Chrome UA。登录成功后从 `CookieManager` 读取 `.bilibili.com` 的 Cookie；`shouldInterceptRequest` 里的 `Cookie` 请求头同时兜底 HttpOnly 的 `SESSDATA`。
- 登录页固定用浅色 `Configuration` 渲染，避免深色模式下协议勾选框不可见；页面加载完成后 1.2 秒轮询一次 Cookie，也可点右上角「完成」手动触发。
- **Cookie 导入**：只强制要求 `SESSDATA`；没有 `bili_jct` 时按只读登录处理，评论/弹幕会提示需要完整 Cookie。
- 所有 Cookie 仅写入应用私有 `SharedPreferences`，WebView 与接口会话互不共享。
