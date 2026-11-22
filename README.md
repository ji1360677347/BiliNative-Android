# 哔哩轻看 Android（BiliNative Android）

哔哩轻看（[BiliNative](https://github.com/ji1360677347/BiliNative)）的 Android 重写版：用 **Kotlin + Jetpack Compose + Media3** 重新实现首页浏览、DASH 播放、弹幕、评论、搜索、动态与账号体系，界面按 B 站 App / 原 iOS 客户端的视觉规范自行绘制。

> 非官方第三方客户端，仅供学习与个人使用。视频、弹幕、评论等内容的版权归哔哩哔哩及原作者所有，请遵守 B 站用户协议，不要用于商业用途或大规模抓取。

## 功能

### 浏览
- 首页 **推荐 / 热门 / 排行榜**，双列封面卡片、下拉刷新、分页加载
- 搜索：综合 / 最多播放 / 最新发布 / 最多弹幕，本地搜索历史与 B 站热搜
- 关注动态流：文字、图片、投稿卡片，分页加载
- 收藏夹、关注列表、UP 主投稿、云端观看历史

### 播放
- DASH 视频 + 音频分流自动合流（`MergingMediaSource`），AVC / HEVC，硬件解码优先
- 画质切换（8K / 4K / 1080P60 / 1080P / 720P / 480P / 360P，按账号实际权限展示）
- 倍速（0.5x–3x）、拖动进度、双击暂停、横屏全屏
- 多分 P 选集、相关推荐、UP 主信息
- 会员播放源：可选「自动优先会员 / 个人账号 / 会员账号」，自动模式失败回退并提示
- 播放进度写入本机观看记录

### 弹幕
- 按 6 分钟分段拉取官方 protobuf 弹幕（`seg.so`），本地手写 proto 解码
- 滚动 / 顶部 / 底部弹幕，轨道自动避让
- 以播放器进度计算位置，暂停、拖动、倍速自然同步
- 开关、透明度、字号、显示区域可调

### 互动
- 评论列表、楼中楼预览与展开、评论点赞、发表评论
- 视频点赞 / 投币 / 收藏（收藏夹选择）/ 系统分享
- 发送弹幕（滚动 / 顶部 / 底部）

### 账号
- 扫码登录（二维码本地生成 + 轮询确认）
- Cookie 导入（个人账号 / 会员源分开保存）
- Cookie 仅保存在应用私有 `SharedPreferences`，不会上传到任何第三方
- 深色 / 浅色 / 跟随系统主题

## 尚未实现

直播、番剧独立入口（可通过 `ep` 链接解析）、字幕、投屏 / 画中画、离线缓存、NAS 媒体库。这些在 iOS 版本中已具备，Android 版会按优先级逐步补齐。

## 技术栈

| 模块 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.0、Jetpack Compose（Material 3） |
| 播放器 | AndroidX Media3 / ExoPlayer 1.4 |
| 网络 | OkHttp 4、自研 B 站 Web API 封装 |
| 图片 | Coil 2（共享 OkHttp、统一 Referer / UA） |
| 二维码 | ZXing core |
| 构建 | AGP 8.7、Gradle 8.9、JDK 17+、compileSdk 35、minSdk 26 |

`core` 层逻辑从 iOS 版 `Sources/BiliCore` 移植：

- WBI 签名（`w_rid` / `wts`，mixin 表 + MD5）
- DASH `playurl` 解析与播放源选择
- 弹幕 protobuf 手写解码（`DmSegMobileReply`）
- 评论 / 动态 / 历史 / 收藏 JSON 宽松解析
- 设备 Cookie（`buvid3` / `buvid4`）自动获取，提升游客访问成功率

## 构建

```bash
# 需要 JDK 17+ 与 Android SDK Platform 35、Build-Tools 35.0.0
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

Release 包默认使用 debug 签名，方便直接安装；正式分发请自行配置签名。

## 项目结构

```
app/src/main/java/local/bili23/android/
├── BiliApp.kt              # Application、Coil 配置、全局依赖
├── MainActivity.kt         # 入口、深链（BV 号）
├── AppRuntime.kt           # 主题等跨页状态
├── core/
│   ├── BiliClient.kt       # 全部 B 站接口
│   ├── Http.kt             # OkHttp 封装（UA / Referer / 表单）
│   ├── Wbi.kt              # WBI 签名
│   ├── DanmakuParser.kt    # 弹幕 protobuf 解码
│   ├── Models.kt           # 数据模型与解析
│   └── Store.kt            # Cookie / 设置 / 本机记录
└── ui/
    ├── RootNav.kt          # 导航与底部栏
    ├── home/               # 首页三栏
    ├── player/             # 播放页、弹幕层、评论
    ├── search/             # 搜索与热搜
    ├── dynamic/            # 动态流
    ├── profile/            # 我的、登录、历史、收藏、关注、设置
    ├── common/             # 封面卡片、等级徽章、通用状态
    └── theme/              # B 站配色与排版
```

## 与 iOS 版的关系

本项目是 [BiliNative](https://github.com/ji1360677347/BiliNative) 的独立 Android 实现，共享相同的接口约定与交互设计，但不复用 Swift 代码或官方二进制。iOS 版仍以 SwiftUI 持续维护。

## 免责声明

- 项目未使用 B 站官方开放平台以外的私有接口，接口可能随官方调整而失效。
- 不提供任何破解、去广告、绕过付费墙的能力；会员画质依赖账号自身权益。
- 请勿将本项目用于任何违反 B 站用户协议或法律法规的用途。
