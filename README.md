# Simple Music Player

Simple Music Player，简称 SMP，是一款使用 Kotlin 开发的 Android 音乐播放器，面向本地音乐管理、播放列表管理、歌词显示、播放控制和轻量流媒体播放场景。

> 本项目仅供学习与研究使用。请尊重音乐内容版权，并遵守各音乐与流媒体平台的服务条款。

## 当前功能

- 自动扫描 Android 系统媒体库中的本地音乐，并支持首次启动自动扫描。
- 支持手动导入音频、CUE、LRC 文件，也支持授权文件夹递归扫描。
- 支持常见音频格式，实际播放能力取决于设备解码器。
- 读取标题、歌手、专辑、时长、曲序、年份、日期、作曲家、时间戳和封面等元数据。
- 支持读取 CUE 文件并生成可播放的分轨音乐。
- 支持 LrcLib 歌词匹配， Last.fm 元数据匹配，最多 5 首并发查询，匹配到的数据会立即绑定到曲目。
- 支持创建、编辑、保存播放列表，包含我喜欢的音乐、最近播放、播放排行、CUE 歌单和自动专辑歌单分类。
- 支持搜索、筛选、排序、批量收藏、批量移动和删除音乐。
- 支持歌曲别名、点评、标签和 5 星评分编辑。
- 支持顺序播放、真随机、伪随机、单曲循环、倍速播放、系统音量调节和正在播放列表。
- 支持蓝牙耳机控制、状态栏播放控制、锁屏播放控制和系统媒体广播。
- 支持悬浮歌词，可设置透明度、字号、位置和描边。
- 支持浅色、深色、跟随系统三种模式，支持主题色和背景图片设置。
- 支持配置 JSON 导入/导出，包含设置、本地账号、头像、服务器配置和用户歌单；Dizzylab 登录凭证独立加密保存，不随配置导出。
- 在线音乐支持通过顶部下拉列表切换 Subsonic / Dizzylab；Dizzylab 支持发现、搜索、社团、已购和喜欢的专辑，复用原播放器及下载队列。
- Dizzylab 账户页提供账户管理、消息、关注动态、关注社团、订单与 CDK 兑换；付费购买仅在站内 WebView 完成，免费领取提供原生确认流程。
- 支持 Subsonic/Navidrome 兼容服务器登录、搜索、艺术家/歌曲/专辑/歌单浏览、随机音乐、串流播放与下载。
- 支持 WebDav 服务器连接与浏览，支持 Basic/Digest 认证，目录索引缓存，串流缓存。
- 支持 WebDav 在线播放（自动缓存、文件夹播放列表、同目录封面匹配）和下载（整张专辑下载、元数据自动重扫描）。
- 支持 NCM 转换，相关核心代码参考并移植自 [NCMConverter4a](https://github.com/cdb96/NCMConverter4a)。
- 支持调试模式，报错自动生成日志文件。
- 界面切换淡入淡出动画，卡片按压反馈效果。

## 流媒体支持

SMP 的“在线音乐”页面通过顶部下拉列表切换 Subsonic / Dizzylab。Dizzylab 提供音乐发现、搜索、社团、已购/喜欢列表和专辑播放，使用服务端稳定曲目标识，并在播放前重新取得资源地址。单曲和逐曲整专辑下载通过原下载队列、目录设置、元数据写入及本地入库流程完成，支持 MP3 128/320；网页提供的 ZIP 文件包也进入同一队列，流式保存但不自动解压。

Dizzylab 的账户功能位于“设置 > 用户帐号 > 流媒体账号凭证 > Dizzylab”独立页面。关注动态、关注列表、免费领取和 CDK 预检/确认提供原生界面；资料修改、消息、订单与付费购买使用站内 WebView。项目不接入原生付费订单或支付 SDK。旧“流媒体模式”仅保留 WebDAV 入口。

Dizzylab Cookie/Token 使用 Android Keystore 加密保存，并排除在配置导出及系统备份之外。已有 Cookie 配置可迁移；退出 Dizzylab 不清理其他来源账号或已下载音乐。

Dizzylab 接口调查和后续实现状态见 [调查文档](docs/dizzylab-investigation/README.md)及[实现与验证记录](docs/dizzylab-investigation/implementation-status.md)。

SMP 当前也已经支持 Subsonic 兼容服务器，包括 Navidrome 等实现。应用支持：

- 连接并验证服务器信息
- 搜索歌曲、艺术家和专辑
- 浏览艺术家、专辑、歌单和收藏歌曲
- 串流播放、转码播放、缓存封面与音频
- 下载单曲、整张专辑和整个歌单
- 将歌曲收藏到“喜爱的音乐”或其他服务器歌单

SMP 支持通过 WebDav 协议连接远程服务器并浏览、播放和下载音乐：
- 支持 Basic 和 Digest 认证
- 首次连接后自动建立目录索引缓存，再次访问直接读取本地缓存
- 在线播放自动缓存到本地，再次播放无需重载网络
- 下载后自动重扫描音频文件内嵌元数据（标题、歌手、专辑、时长）
- 支持整张专辑一键下载，下载文件夹与远程文件夹同名
- 播放时自动将同目录所有音乐加入播放列表

## 权限说明

SMP 默认使用 Android 的媒体专用权限，这是正常音乐播放器推荐使用的权限模式。应用也可以通过 Android 的 Storage Access Framework 请求指定文件夹权限，用于手动扫描、下载目录写入、歌词输出和转换文件输出。

如果需要更长期稳定的下载与删除源文件能力，SMP 也提供“所有文件访问权限”的设置入口。该权限不是普通本地播放的必要条件，请按实际需求开启。
- 当前有一些非核心的功能依赖于该权限，未开启可能导致这些功能异常，大部分情况无影响

## 构建方式

使用 Android Studio 打开本项目，并运行 `app` 模块。

当前构建配置：

- Android Gradle Plugin 9.1.1，使用其内置 Kotlin 2.2.10
- Gradle Wrapper 9.3.1，发行包 SHA-256 固定
- compileSdk 36 / targetSdk 35
- minSdk 26
- Gradle 使用 `D:\Android\Android Studio\jbr` 的 JBR 25，Java/Kotlin 字节码目标仍为 17
- SDK：`D:\Android\SDK`；AVD：`D:\Android\AVD`

构建、测试和 lint：

```powershell
.\tools\android.ps1 -Task Check
# 本机启用了 Windows 系统代理时，可为本次 Gradle 进程显式读取代理：
.\tools\android.ps1 -Task Check -UseSystemProxy
.\tools\android.ps1 -Task Emulator
.\tools\android.ps1 -Task Install -UseSystemProxy
```

也可直接使用 `gradlew.bat`。`local.properties` 的 `sdk.dir` 应指向 `D:\Android\SDK`；其他机器同时调整 `gradle.properties` 的 JBR 路径。兼容关系参考 [AGP 9.1 官方说明](https://developer.android.com/build/releases/agp-9-1-0-release-notes)和 [Gradle Java 兼容表](https://docs.gradle.org/current/userguide/compatibility.html)。

Debug 包使用 `com.supermite.smp.debug`，可与正式版并行安装，便于在模拟器中隔离测试账号；正式版 applicationId 仍为 `com.supermite.smp`。

## 发布版本

当前预发行版本：`2.1.0-beta.1`；现有正式版本：`2.0.0`。

## 待办事项

- 更多平台支持
- 已知其他问题修复

## 许可

本项目使用 MIT License，仅供学习使用。
