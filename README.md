# Simple Music Player

Simple Music Player，简称 SMP，是一款使用 Kotlin 开发的 Android 音乐播放器，面向本地音乐管理、歌单管理、播放控制和轻量流媒体播放场景。

> 本项目仅供学习与研究使用。

## 当前功能

- 自动扫描 Android 系统媒体库中的本地音乐。
- 支持手动导入音频、CUE、LRC 文件，也支持授权文件夹递归扫描。
- 支持常见音频格式，实际播放能力取决于设备解码器。
- 读取标题、歌手、专辑、时长、曲序、年份、日期、作曲家、时间戳和封面等元数据。
- 支持同目录图片作为封面，优先使用 cover、folder、album、front 等命名。
- 支持读取 CUE 文件并生成可播放的分轨音乐。
- 支持 LrcLib 歌词匹配，最多 5 首并发查询，匹配到的时间轴歌词会立即绑定到曲目。
- 支持创建、编辑、保存播放列表，包含我喜欢的音乐、最近播放、播放排行、CUE 歌单和自动专辑歌单分类。
- 支持搜索、筛选、排序、批量收藏、批量移动和删除音乐。
- 支持歌曲别名、点评、标签和 5 星评分编辑。
- 支持顺序播放、真随机、伪随机、单曲循环、倍速播放、系统音量调节和正在播放列表。
- 支持蓝牙耳机控制、状态栏播放控制、锁屏播放控制和系统媒体广播。
- 支持悬浮歌词，可设置透明度、位置，并使用主题色显示描边文字。
- 支持浅色、深色、跟随系统三种模式，支持主题色和背景图片设置。
- 支持配置 JSON 导入/导出，包含设置、本地账号、头像、流媒体 Cookie 和用户歌单。
- 支持 DizzyLab 登录、已购专辑加载、专辑详情解析、串流播放和下载。
- DizzyLab 下载会写入 `album.json` 专辑索引，后续扫描时优先使用索引中的专辑封面、曲目数量和曲目元数据。
- 支持 NCM 转换，相关核心代码参考并移植自 [NCMConverter4a](https://github.com/cdb96/NCMConverter4a)。

## DizzyLab 流媒体支持

SMP 当前已经支持 DizzyLab 作为第一个流媒体来源。用户可以通过应用内 WebView 登录 DizzyLab，应用会在本地保存 Cookie，用于加载已购专辑、读取专辑详情、播放串流音频和下载音乐。

后续版本计划继续支持更多流媒体平台。

## 权限说明

SMP 默认使用 Android 的媒体专用权限，这是正常音乐播放器推荐使用的权限模式。应用也可以通过 Android 的 Storage Access Framework 请求指定文件夹权限，用于手动扫描和下载目录写入。

如果需要更长久稳定的下载与删除源文件能力，SMP 也提供“所有文件访问权限”的设置入口。该权限不是普通本地播放的必要条件，请按实际需求开启。

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

当前发布版本：`beta1.0.2`

Debug APK 默认输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 待办事项

- 更多平台支持
- 动态歌词
- 音效设置
- 已知其他问题修复

## 许可

本项目使用 MIT License，仅供学习使用。请遵守各音乐与流媒体平台服务条款，并尊重音乐内容版权。
