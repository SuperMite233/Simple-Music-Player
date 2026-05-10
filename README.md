# Simple Music Player

Simple Music Player，简称 SMP，是一款使用 Kotlin 开发的 Android 音乐播放器，主要面向本地音乐库管理、歌单管理、播放控制和轻量级流媒体播放。

> 本项目仅供学习与研究使用。

## 当前功能

- 自动扫描 Android 系统媒体库中的本地音乐。
- 支持手动导入音频文件、CUE 文件和 LRC 歌词文件。
- 支持通过 Android 文件夹授权扫描用户选择的音乐目录。
- 读取常见音乐元数据，包括标题、歌手、专辑、时长、曲序、年份和内嵌封面。
- 支持设备可播放的常见音频格式，例如 MP3、FLAC、WAV、M4A、AAC、OGG、OPUS、WMA、MIDI 等。
- 支持读取 CUE 文件并生成可播放的分轨音乐。
- 支持检测配套 LRC 歌词文件，并在播放页按时间显示歌词。
- 支持创建、保存、编辑和删除播放列表。
- 扫描音乐时可自动创建专辑歌单。
- 播放历史保留最近 100 条，并作为不可删除歌单显示。
- 内置置顶的“我喜欢的音乐”歌单。
- 支持按标题、歌手、专辑、别名、点评、标签和评分搜索或筛选本地音乐。
- 支持为单曲添加别名、点评、标签和星级评分。
- 支持顺序播放、真随机、伪随机和单曲循环。
- 支持播放倍速、音量调节、正在播放列表查看和歌曲详细信息查看。
- 音乐库、歌单、迷你播放栏和播放页均支持显示音乐封面缩略图。
- 支持蓝牙耳机控制和系统媒体控制。
- 支持配置文件导入/导出，包括设置、本地账号、流媒体 Cookie 和用户歌单。
- 支持 DizzyLab 登录、已购专辑抓取、专辑详情读取、串流播放和串流下载。
- 支持 NCM 转换，相关核心代码参考并移植自 [NCMConverter4a](https://github.com/cdb96/NCMConverter4a)。

## DizzyLab 流媒体支持

SMP 当前已经支持 DizzyLab 作为第一个流媒体来源。用户可以通过应用内 WebView 登录 DizzyLab，应用会在本地保存 Cookie，用于加载已购专辑、读取专辑详情和播放串流音频。

后续版本计划继续支持更多流媒体平台。

## 权限说明

SMP 默认使用 Android 的媒体专用权限，这是正常音乐播放器推荐使用的权限模式。应用也可以通过 Android 的 Storage Access Framework 请求指定文件夹权限，用于手动扫描和下载目录写入。

如果用户需要更长久稳定的下载与删除源文件能力，SMP 也提供了“所有文件访问权限”的设置入口。该权限不是普通本地播放的必要条件，请按实际需求开启。

## 构建方式

使用 Android Studio 打开本项目，并运行 `app` 模块。

当前构建配置：

- Android Gradle Plugin 8.11.1
- Kotlin Android Plugin 2.0.21
- Gradle 8.14.5
- compileSdk 35
- minSdk 26
- Gradle daemon 使用 JDK 17

如果本机 Android SDK 不在 `D:\Android`，请修改 `local.properties` 中的 `sdk.dir`。

## 发布版本

当前发布版本：`1.4.4`

Debug APK 默认输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 待办事项

- 更多平台支持
- 动态歌词
- 音效设置
- 已知其他问题修复

## 许可证

本项目使用 MIT License。

本项目仅供学习与研究使用。请遵守各流媒体平台服务条款，并尊重音乐内容版权。
