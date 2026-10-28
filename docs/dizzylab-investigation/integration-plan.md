# Dizzylab 第三方客户端集成方案

调查日期：2026-10-28。本文记录项目接入分析与阶段规划；实际落地情况见[实现与验证记录](implementation-status.md)。网络契约、样本来源、响应字段与验证边界以本目录的[调查总览](README.md)及其 API 说明为准；官方 APK 的静态发现见 [APK 调查](apk-findings.md)。未在实现记录中标为完成的类型和阶段仍为设计建议。

用户后续确定的范围：顶部下拉切换音乐来源；Dizzylab 账户、消息、动态等放入独立账户页面；复用原播放器和下载设施。**不实现原生付费购买/支付 SDK，付费购买统一交给 WebView；免费领取与 CDK 兑换原生化。**

## 1. 当前定位与主要结论

项目已经有 Dizzylab 登录、已购专辑页面解析、专辑详情、串流播放与下载能力，定位仍是“已购库网页解析播放器”。扩展为覆盖网页版大部分能力的第三方客户端，应先建立独立的会话、结构化接口、领域模型和媒体资源解析层，再增加发现、搜索、社团、远端收藏关注、账户及交易相关页面。

本次主调查已经确认的关键前提如下；完整请求参数与原始证据应查阅调查总览，不能将这些概要直接当成完整接口规格：

- `/apis/auth/login/` 的 JSON 登录仍有效。
- 已登录个人库页面内嵌 token；`getmyinfo`、`mydisc`、`mylike`、`myfollowing`、`getcart` 需要 token。只发送 Cookie 可能得到 HTTP 200 的 HTML，不能只看状态码判定 API 成功。
- `getthisdicsinfo` 可公开返回 JSON；接口名中的 `dics` 是已发现的原始拼写。
- 在线播放资源存在带日期样式片段和签名的 URL 路径，提示资源可能有时效；实际过期时间尚未测定，长期保存播放 URL 不能替代保存曲目身份。
- 网页整包下载入口为 `/albums/download/?d=&tp=128|MP3|FLAC&k=<signed>&v=1`。这里的 `<signed>` 是脱敏占位符；不能自行构造签名，也不能把在线播放地址下载等同于该整包下载流程。

Cookie 会话、API token、播放资源地址、下载签名应分别建模；其作用域、有效期和失败恢复流程需依据已验证契约实现。

## 2. 现有代码接入图

以下为调查时的行号。链接相对于本文件，源文件目录仍使用旧的 `com/example/localmusicplayer` 路径，实际 Kotlin package 为 `com.supermite.smp`。

| 范围 | 代码证据 | 当前行为 |
| --- | --- | --- |
| Dizzylab 网络层 | [DizzylabClient.kt:10](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L10)、[314](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L314) | 单一 Cookie 字符串，HttpURLConnection GET，自动重定向，无 JSON API/POST/CSRF/token 适配 |
| 用户识别 | [DizzylabClient.kt:11](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L11)、[192](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L192) | 优先保存的数字 UID；否则读取首页、`/albums`、`/u/` 并正则搜索个人链接 |
| 已购库与搜索 | [DizzylabClient.kt:27](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L27) | `/u/{uid}/music`，搜索参数 `q`，循环获取全部分页；不是全站搜索 |
| 专辑详情 | [DizzylabClient.kt:159](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L159) | HTML 提取标题、社团、发行时间、简介、标签、曲目 |
| 曲目解析 | [DizzylabClient.kt:206](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L206) | 优先 `li[data-id][data-audio]`，否则扫描音频扩展名链接 |
| 现有专辑模型 | [DizzylabClient.kt:515](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L515)、[522](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L522) | 只有 URL、标题、封面及基础详情，缺少价格、购买状态、稳定曲目身份、资源变体 |
| 通用曲目模型 | [Models.kt:3](../../app/src/main/java/com/example/localmusicplayer/data/Models.kt#L3) | 面向本地播放器的基础元数据、URI、歌词和 CUE 字段 |
| 登录界面 | [MainActivity.kt:7230](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7230)、[7259](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7259) | WebView 登录，页面加载完成后从 CookieManager 读取 Cookie |
| 登录与注销设置 | [MainActivity.kt:2875](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2875)、[2902](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2902) | 以 Cookie 是否为空显示登录状态；清除操作调用整个 WebView Cookie 库的 `removeAllCookies` |
| Dizzylab 导航 | [MainActivity.kt:2271](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2271)、[2417](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2417) | STREAMING 中的来源卡片、已购列表、打开的专辑详情 |
| 已购列表 UI | [MainActivity.kt:2302](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2302)、[2354](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2354) | 标题本地过滤、已购库在线搜索；每次显示 40 项，更多按钮不是网络分页 |
| 加载协调 | [MainActivity.kt:7270](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7270)、[7300](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7300) | 临时 Thread + runOnUiThread；请求和页面状态直接放 Activity |
| 播放 Header | [MainActivity.kt:320](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L320)、[2050](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2050)、[6461](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L6461) | 多处分配 `headersForTrack`，按 ID 前缀选择凭据 |
| 播放器 | [MusicPlayer.kt:50](../../app/src/main/java/com/example/localmusicplayer/playback/MusicPlayer.kt#L50) | Android MediaPlayer，支持带 Header 的 setDataSource、异步准备 |
| Dizzylab 曲目菜单 | [MainActivity.kt:4233](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L4233) | 播放、下载、Last.fm 元数据匹配、曲目详情 |
| Dizzylab 下载 | [MainActivity.kt:7351](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7351)、[7399](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7399) | 逐曲下载当前串流 URI，写元数据并加入本地库；没有官方整包格式选择流程 |
| 公共下载队列 | [MainActivity.kt:5912](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5912)、[5923](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5923) | 单线程执行器、任务列表、下载通知 |
| 凭据持久化 | [MainActivity.kt:5485](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5485)、[5538](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5538) | 原始 Cookie 存入 settings SharedPreferences |
| 配置导入导出 | [MainActivity.kt:5640](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5640)、[5647](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5647)、[5696](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5696) | Cookie 进入导出的配置 JSON，导入后恢复 |
| 播放恢复 | [MainActivity.kt:5585](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5585)、[5600](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5600) | 持久化队列 ID，恢复时从 allTracks 解析，没有独立远端实体恢复流程 |

## 3. 扩展前应处理的差距

### 会话与网络契约

当前客户端只有 Cookie，没有 token 层；不能直接把网页请求改成 API URL 后继续沿用相同成功判断。新增响应分类应检查最终 URL、Content-Type、JSON 结构和业务状态，识别“200 HTML 登录页/错误页”，区别会话失效、token 缺失、权限不足、限流、网络失败与解析失败。只在已验证可安全重试的读取请求上自动恢复；写入请求发生不确定结果时先查询状态。

现有 [open:314](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L314) 对传入 URL 无条件附整串 Cookie，下载与封面函数均接受 URL。建议使用按 domain/path/secure 匹配的 CookieJar，并校验跳转目的域；媒体 CDN 是否需要凭据应依真实请求单独配置。token、Cookie、下载签名不能写入调试日志或普通配置导出。

当前 [登录状态:2881](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2881) 只判断 Cookie 非空，应改为“游客/验证中/有效/过期/暂时离线”等明确状态。会话验证成功后再绑定 UID 和用户展示信息。注销仅清理 Dizzylab 相关域、该账号 token 和账号私有缓存，避免影响未来其他 WebView 来源。

### 身份与媒体生命周期

[曲目 ID:223](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L223) 使用音频 URL 的 hashCode；资源签名变化、播放品质切换或 CDN 变化都会改变 ID，也可能发生 hash 冲突。本次 API 曲目 ID 为专辑内字符串序号，身份键应组合 provider、专辑 ID 和曲目 ID；若某类页面没有服务端曲目 ID，再采用“专辑 ID + 曲序”的临时兼容键并记录局限，不能将 URL hash 作为长期身份。

当前 [downloadStreamTrackSync:7401](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L7401) 下载播放用 URI。新方案必须区分试听资源、购买后的播放资源与整包下载。保存曲目身份，播放或恢复队列时重新解析时效 URL；保存离线文件时另存格式、品质和取得时间。下载授权由服务器响应决定。

### 分页、状态与失败恢复

[albums:31](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L31) 串行读取所有页，UI 再每次展示 40 项。建议返回 `PageResult(items, nextPage)` 并按需加载，检测重复分页、保留筛选条件、支持取消旧查询，避免旧响应覆盖新页面。

下载应流式写入临时文件，完成校验后提交最终文件；当前 [download:182](../../app/src/main/java/com/example/localmusicplayer/stream/DizzylabClient.kt#L182) 整文件读入内存。官方整包还需文件名、Content-Type、压缩包完整性、可用空间、格式选择与失败重试处理。断点续传只在服务器确实支持 Range/验证器后启用。

公共队列 [enqueueDownload:5932](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5932) 执行回调后无条件标记完成，可能覆盖内部失败；Dizzylab 下载异常还可能变为 null。新增任务应返回明确结果，不得把“请求结束”当作“下载成功”。这属于共用能力修复，应对其他来源一起回归。

## 4. 建议的模块边界与领域模型

沿用当前单 app 模块即可，先按 Kotlin 包划分责任，不必立即拆 Gradle 模块。建议增加 `dizzylab/network`、`dizzylab/data`、`dizzylab/domain`、`dizzylab/ui`；当前 `stream/DizzylabClient.kt` 可在迁移期间保留为网页适配器。

| 建议模型 | 最小责任 | 关键限制 |
| --- | --- | --- |
| DizzylabAccount / SessionState | UID、展示信息、会话状态、凭据引用、最近验证时间 | Cookie/token 独立存储，不直接放 UI state 或通用 Track |
| DizzylabAlbum | 稳定 ID、URL、标题、封面、社团引用、基础发布信息 | 价格、币种、购买状态等字段按已验证响应增加，未知值用 nullable/unknown |
| DizzylabTrack | 稳定 ID、专辑 ID、曲序、标题、作者、时长 | 不把时效 URL 当身份；不要将试听可见推断为可下载 |
| Circle / Tag / SearchResult | 社团与标签身份、结果类型、可导航引用 | 各来源 ID 独立；全站搜索和已购搜索分别建模 |
| AccessState | 是否可试听、是否已购、是否可播放/可下载及未知状态 | 服务端决定权限；未查询不能默认有权访问 |
| PlaybackResource | URI、必要 Header、品质/格式、取得时间、已知过期信息 | 有效期未知时明确 unknown，失败后按契约重新解析 |
| DownloadOption / DownloadRequest | 专辑 ID、服务器提供的格式、合法签名来源、结果文件信息 | `128/MP3/FLAC` 属于当前已发现选项，最终展示以页面/接口可用值为准 |
| RemoteLike / Following | 远端对象 ID、账号 ID、服务端状态 | 与本地 LibraryStore 收藏、歌单分开 |
| Cart / Order / Message | 对应远端业务状态和实体引用 | 仅在读写契约明确后实现，不假定所有网页版对象都有 API |
| PageResult<T> | items、下一页/游标、加载状态 | 页面和 API 的分页方式可不同，不虚构统一服务端参数 |
| RemoteTrackRef | provider、accountId、remoteTrackId、remoteAlbumId | 可转为现有 Track 供播放，长期恢复时经 repository 重新解析 |

建议接口职责：

- `DizzylabSessionRepository`：WebView Cookie 会话、JSON 登录、页面 token 获取与会话验证。多种登录途径是否共享同一 token 生命周期，应依据调查契约明确处理。
- `DizzylabCatalogRepository`：公开发现、搜索、专辑、社团和标签；优先已验证结构化接口，网页解析作为独立 fallback。
- `DizzylabLibraryRepository`：已购、喜欢、关注的分页与状态；需 token 的请求统一经会话层。
- `DizzylabAccountRepository`：用户信息、购物车、订单、消息等已验证账户能力；写操作显式表示副作用及返回状态。
- `PlaybackResourceResolver`：按 provider/account/稳定曲目 ID 生成实际播放资源；集中替代 Activity 中多处分配 Header 的逻辑。
- `DizzylabDownloadRepository`：格式选项、签名取得、整包下载与文件验证；与在线播放解析分离。
- `DizzylabHtmlAdapter` / `DizzylabApiAdapter`：只负责请求/解析及 DTO，转换为 domain 后交 UI；HTML 200 和 JSON 错误均可追踪，不静默返回空列表。

项目已依赖 [OkHttp 4.12.0](../../app/build.gradle.kts#L30)，可复用这一网络库。仓库当前没有统一 repository 层，因此应以 Dizzylab 为范围逐步提取，避免一次改动全部来源。新增字段同时更新 [Track 序列化:567](../../app/src/main/java/com/example/localmusicplayer/data/LibraryStore.kt#L567) 与旧数据迁移策略；不能只改内存类型。

## 5. 保留本地、Subsonic 与 WebDAV 的边界

| 现有能力 | 保留边界与代码入口 | Dizzylab 扩展原则 |
| --- | --- | --- |
| 本地库、歌单、历史、收藏 | [LibraryStore.kt:10](../../app/src/main/java/com/example/localmusicplayer/data/LibraryStore.kt#L10)、[136](../../app/src/main/java/com/example/localmusicplayer/data/LibraryStore.kt#L136) | 保留本地语义，远端喜欢/关注单独存储；下载入库后仍使用现有扫描和本地播放流程 |
| 本地/CUE 播放、均衡器、速度 | [MusicPlayer.kt:13](../../app/src/main/java/com/example/localmusicplayer/playback/MusicPlayer.kt#L13) | 先复用播放器和队列，资源解析位于播放器之前，不要求全盘替换播放引擎 |
| Subsonic/Navidrome | [SubsonicClient.kt:81](../../app/src/main/java/com/example/localmusicplayer/stream/SubsonicClient.kt#L81)、[MainActivity.kt:1238](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L1238) | 保留 ping、搜索、目录、歌单、star/rating/scrobble、转码和下载；账号配置与 Dizzylab 独立 |
| WebDAV | [WebDavClient.kt:44](../../app/src/main/java/com/example/localmusicplayer/stream/WebDavClient.kt#L44)、[178](../../app/src/main/java/com/example/localmusicplayer/stream/WebDavClient.kt#L178) | 保留 Basic/Digest、目录与缓存、Range 和下载；不向 WebDAV 注入 Dizzylab Cookie/token |
| 页面入口 | [MainActivity.kt:8203](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L8203)、[8213](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L8213) | 保留其他来源入口，Dizzylab 增加自己的 screen/state/back stack；不挤占 Subsonic 的独立页面 |
| 下载、缓存、通知、设置 | [MainActivity.kt:2920](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L2920)、[5912](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5912) | 共用设施按 provider/account 隔离任务、缓存与 Header；修复公共失败语义时测试全部来源 |
| 配置兼容 | [LibraryStore.kt:430](../../app/src/main/java/com/example/localmusicplayer/data/LibraryStore.kt#L430)、[MainActivity.kt:5665](../../app/src/main/java/com/example/localmusicplayer/MainActivity.kt#L5665) | 旧服务器、歌单和偏好继续可读；凭据迁移后默认配置导出不带敏感值 |

## 6. 分阶段实施路线

| 阶段 | 实施内容 | 完成条件 |
| --- | --- | --- |
| P0 契约与底座 | 实现会话状态、JSON/HTML 响应识别、token 取得、按域 CookieJar、错误分类、稳定实体 ID、资源解析接口；为已确认接口建立脱敏 fixtures | 有效会话、过期会话、Cookie-only HTML 和真实业务失败都可区分；不泄露凭据；现有已购播放仍可用 |
| P1 核心音乐客户端 | 公开发现/全站搜索、专辑与社团详情、账号信息、已购分页；连接已验证 API，保留网页 fallback | 可从公开页面发现内容，登录后访问自己的库；详情曲目映射正确，播放器可重新取得时效资源 |
| P1 下载完善 | 官方格式选项与整包下载、流式文件写入、进度取消、失败重试、校验和本地入库；保持当前逐曲下载语义清楚 | 播放资源与原档/整包下载明确区分；失败不报完成；下载结果可在本地库使用 |
| P2 远端账户能力 | 喜欢/关注、购物车与订单等先读后写；仅接入已验证请求；消息、兑换、设置按证据逐项补齐 | 远端状态与网页一致；写操作有明确 UI 触发和结果验证；不可实现项有说明或受控 WebView 路径 |
| P2 完整网页流程 | 付费购买/支付只使用 WebView；免费领取、CDK 预检/确认原生实现；账号资料、消息与订单先使用站内 WebView | 不创建原生付费订单、不接入支付 SDK；返回应用重新查询权益；不将未经验证的 API 当成上线能力 |
| P3 稳定性与整合 | 独立 Dizzylab screen/state、远端队列恢复、缓存隔离、账号切换、错误恢复、旧配置迁移和多来源回归 | 下表核心验收通过；调查未覆盖功能仍标为待验证 |

P2 中涉及账户变化的行为属于未来产品实现，不表示本次调查已经提交过这些操作。需要新增网络验证时，应延续主调查的只读/副作用边界。

## 7. 验收矩阵

这些是后续实现的验收要求，本次文档工作未运行下列功能测试。

| 场景 | 验证输入/动作 | 预期 |
| --- | --- | --- |
| 游客访问 | 无会话打开公开专辑及搜索 | 公开能力正常，私有能力提示登录，不显示空白成功状态 |
| JSON 登录 | 已确认的 `/apis/auth/login/` 合法成功/失败样本 | 正确解析业务结果，凭据进入会话层，错误不写入日志 |
| Cookie 与 token | 有 Cookie 无 token、有效 token、过期状态 | 识别 200 HTML；必要时按契约重新取得 token；不无限重试 |
| 账号隔离 | A 登录、退出、B 登录 | 个人库、喜欢、关注、购物车和缓存不会混用，其他来源仍可用 |
| 分页 | 多页已购/搜索，切换筛选并快速返回 | 真正按需加载；无重复、死循环或旧响应覆盖；可取消请求 |
| 详情身份 | 同曲目更换资源签名/品质 | 稳定 ID 不变；曲序、作者、时长及专辑关联不漂移 |
| 时效播放 | 原 URL 到期后重播/恢复队列 | 重新解析资源；必要时要求重新登录；不会将 URL 到期当作文件永久不存在 |
| 播放控制 | 远端歌曲 seek、暂停、跳曲、锁屏与蓝牙控制 | 复用现有播放控制，服务器能力允许时正常 seek |
| Header 作用域 | 站点、媒体 CDN、外部跳转与 WebDAV | 只向允许域发送必要凭据，缓存本地文件不带远端凭据 |
| 下载格式 | 页面允许的 128/MP3/FLAC 选项 | 请求与合法签名对应；结果格式和名称正确；不可用选项不强行请求 |
| 下载失败 | 断网、HTML 错误页、磁盘不足、取消 | 保留明确失败/取消状态，不写伪音频、不报完成；临时文件可清理 |
| 下载完成 | 合法整包及现有逐曲下载 | 校验后提交文件，保留来源/格式信息；本地入库可用 |
| 远端喜欢/关注 | 查询、显式修改、刷新网页确认 | 远端状态一致；本地收藏不会被错误替换 |
| 交易和账户流程 | 购物车、订单读取及受控网页流程 | 状态与服务器一致；无法验证的步骤显式保留未知/网页入口 |
| 配置迁移 | 导入旧配置、导出新配置 | 旧服务器与用户偏好保留，敏感凭据默认不外泄 |
| 本地/CUE 回归 | 扫描、歌单、歌词、CUE、下载入库 | 原有核心行为保持，远端对象不污染本地实体语义 |
| Subsonic 回归 | ping、搜索、播放/转码、收藏/歌单、下载 | 原服务器登录、API、缓存及播放正常 |
| WebDAV 回归 | Basic/Digest、目录、缓存、播放、下载 | 认证与缓存正常，不受 Dizzylab 会话切换影响 |
| 混合队列 | 本地、Dizzylab、Subsonic、WebDAV 连续切歌 | 每项解析自己的来源和账号，Header 不串用，返回各来源页面正常 |

## 8. 实现前仍需明确的事项

1. token 的完整生命周期、可否刷新、JSON 登录与页面内嵌 token 的关系，以主网络契约为准，不能从一次成功请求推断永久有效。
2. 每个列表的排序、筛选、分页参数及空结果语义，逐接口确认；网页已有能力不代表存在相同名称的 JSON API。
3. 媒体 URL 的确切有效期、购买与试听权限、特殊商品的下载签名方式仍需分别验证；本次已抽样确认音频与整包 Range，以及已购页签名整包入口，没有测定资源过期时间或完成断点恢复测试。
4. 写接口的 CSRF、重复提交与失败状态应单独调查；静态 APK 中有路径不表示当前服务器仍支持全部行为。
5. 第三方客户端能否覆盖发布者后台、账号敏感设置等非日常听歌功能，需要单独界定产品范围；本方案优先覆盖听众使用的网页能力。
