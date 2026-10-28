# 官方 APK 静态调查补充

调查日期：2026-10-28。对象是用户提供的 `C:\Users\lenovo\Downloads\dizzylab012.apk`。本文件只陈述包内静态证据，**没有安装或运行 APK，也没有据此认定旧接口目前可用**。网页版实时证据应以主调查报告为准。

## 证据与边界

| 编号 | 对象与取得方式 | 结果 |
| --- | --- | --- |
| APK-01 | APK 文件，24,613,581 bytes | SHA-256 `b3a5344ef0e276baafa061406a7c772c27b2c7eeacbde5fc69650ebd6e5ae5ab` |
| APK-02 | 项目已有 Android SDK `aapt dump badging` / `dump xmltree` | 包名 `com.cnsouka.dizzwalkman.beta`，versionName `0.1.2`，versionCode `1`；minSdk 16、targetSdk / compileSdk 28 |
| APK-03 | ZIP 成员 `assets/index.android.bundle` | 明文 React Native JS，2,623,709 bytes、1,474 行；SHA-256 `0b132873644dd54fb89d288dfdb6e4e947c549037d3907233d6662854f833b47` |
| APK-04 | REA `analyze_javascript_application` 对提取的单个 JS 文件做惰性语法分析 | Evidence `ev_4510bc28a07bdf83f7bac109f036107b8122238ec8ebdbcc869c723a5aeee9a7`；解析 1 个 JS、662,561 个 AST 节点，parse_failures=0、truncated_scopes=0；不证明执行 |

REA `inspect_android_package` 返回 `capability_unavailable / unsupported_provider`：当前 Windows 不支持其 JADX 子进程所有权管理，因此清单由已有 aapt 解码、业务代码由包内 JS 分析取得。未验证 APK 签名；“官方”来源沿用用户说明，不额外声称已完成发布者认证。APK 中出现 `made by dizzylab @2021` 文案，仅能说明包含该文案，不能据此确定构建时间或代表现行客户端版本。

原始 JS 与完整 REA 证据保留在本机临时目录 `C:\Users\lenovo\AppData\Local\Temp\codex-dizzylab-apk-20261028\`；不把整个应用代码复制进项目。REA 全量导出 `rea-evidence.json` 约 124 MB，包含 SDK 语法树；项目中仅保留调查摘要和端点位置。可用 APK SHA-256 和 ZIP 成员 SHA-256 复现。下文 `L1395 / M1389` 表示原始 bundle 的 1-based 行号和 Metro 模块 ID，换行/格式化后的文件行号不可混用。

## 接口体系与认证

`L1402 / M1396` 配置：

```text
APIURL = https://www.dizzylab.net/apis/
APPVER = 12
DOWNLOADDIR = /albums/
STREAMDIR = /streaming/
```

这些常量是 APK 内观察值，不意味着 `/albums/`、`/streaming/` 是可任意构造媒体地址的公共接口。播放器实际使用响应给出的 `track.url`。

登录 `L1419 / M1413`：`POST /apis/auth/login/`，`Accept: application/json`、`Content-Type: application/json`，JSON `{username,password}`；读取响应 `token`。随后本地保存 `token`、`islogin`，进入主界面。多数接口在 query 或 multipart FormData 中传 `token`，**不能把它直接等同于网页 Cookie，也未发现 APK 将 Edge Cookie 换成 token 的流程**。

`L1398 / M1392` 存储封装通过 React Native AsyncStorage `getItem/setItem/removeItem` 操作 JSON；静态上未见该封装对 token 加密。第三方客户端应采用自己的会话安全存储，避免照搬该实现。`L1461 / M1455` 的退出流程清理本地 token、islogin、playlist、downloadlist、nowlist、currentIndex 等；本次没有找到对应服务端注销/撤销 token 调用，token 有效期、刷新和撤销机制均未知。

## APK 端点清单

下列均相对 `https://www.dizzylab.net/apis/`。`T`=token，`l/r` 为客户端传入的分页左右边界（通常 `page*loadcount`、`(page+1)*loadcount`），**边界是否包含末项由服务端决定，尚待在线验证**。方法按 fetch 显式配置或其默认 GET 记录。字段是客户端使用的响应字段，并非完整服务端 schema。

| 方法 / 路径 | 参数或请求体 | 客户端读取 / 语义 | 原始位置 |
| --- | --- | --- | --- |
| GET `getappinfo/` | `fromver=12,token=T` | `appinfo,needupdate` | L1395 / M1389 |
| GET `getdiscs/` | `l,r,sort=ad,token=T` | `discs,canshowmore`；首页专辑 | L1396 / M1390 |
| GET `getlabels/` | `l,r,token=T` | `labels,canshowmore`；社团列表 | L1403 / M1397 |
| GET `getmyinfo/` | `token=T` | `user`，包括页面使用的 uid、username、user_group 等 | L1405 / M1399；L1461 / M1455 |
| GET `getmydisc/` | `l,r,sort=ad,token=T` | `discs,canshowmore`；已购库 | L1407 / M1401 |
| GET `getmylike/` | `l,r,sort=ad,token=T` | `discs,canshowmore`；喜欢 | L1408 / M1402 |
| GET `getmyfollowing/` | `l,r,sort=ad,token=T` | `discs,canshowmore`；关注社团，也用于邮件订阅设置 | L1409 / M1403；L1461 / M1455 |
| GET `getotheruserinfo/` | `l,r,uid,token=T` | `user,discs,mylabel,canshowmore` | L1411 / M1405 |
| GET `gettrackdownloadurl/` | `discid,token=T,trackid,packtype` | `track.url`；实际下载 URL | L1413 / M1407 |
| POST `auth/login/` | JSON `username,password` | `token`，失败走登录错误提示 | L1419 / M1413 |
| GET `getsomecover/` | `l,r`；无显式 token | `discs,canshowmore`；登录页封面背景 | L1419 / M1413 |
| POST `auth/register/` | FormData `user_name,user_email,user_password`；Referer | `res,token`；res `0`成功，`1`邮箱已用，`2`昵称已用 | L1422 / M1416 |
| GET `getthisdicsinfo/` | `discid,token=T` | 专辑对象、`tracks,ilikeit,likes,ihavethis,price` 等 | L1429 / M1423 |
| POST `ilikethisornot/` | FormData `discid,token=T` | `ilikeit,likes`；切换专辑喜欢状态 | L1429 / M1423 |
| GET `getdiscbuyers/` | `l,r,discid,token=T` | `buyers,boosters,canshowmore` | L1429 / M1423 |
| GET `getdisccommit/` | `l,r,discid,token=T` | `commit,mycomment,canshowmore` | L1429 / M1423 |
| POST `postcomment/` | FormData `discid,token=T,comment` | `return=ok` 后刷新留言 | L1429 / M1423 |
| GET `getlabeldiscs/` | `l,r,labelid,token=T,without=<当前discid>` | `discs,canshowmore`；同社团更多作品 | L1429 / M1423 |
| POST `unlockafreedisc/` | FormData `discid,token=T`；Referer | `state=ok`；免费商品入库 | L1443 / M1437 |
| GET `checkout_alipay/` | `token=T,id,q,type,price` | `order_string`；调用原生支付宝 SDK | L1443 / M1437；L1459 / M1453 |
| GET `gethislabelinfo/` | `labelid,token=T,l,r` | 社团对象、`disc,members,following,canshowmore` | L1446 / M1440 |
| POST `ifollowthisornot/` | FormData `labelid,token=T` | `following`；切换关注 | L1446 / M1440 |
| GET `getcart/` | `l,r,token=T` | `discs,allcount,allfee,canshowmore` | L1459 / M1453 |
| GET `editcart/` | `token=T,discid,price,func=ADD` 或 `token=T,discid,func=DELETE` | 随后重读购物车；有副作用 | L1459 / M1453 |
| GET `iwanttweeksomething/` | `labelid,token=T,changemail`（裸 query flag） | 切换邮件订阅；有副作用 | L1461 / M1455 |
| POST `uploadandchangeuseravatar/` | multipart `token=T,avatar=<裁剪的图片文件>` | 客户端成功后重新读取资料 | L1461 / M1455 |
| POST `redeem/` | FormData `token=T,code`；Referer | 兑换预检，`res,disc` | L1466 / M1460 |
| POST `comfirm_redeem/` | FormData `token=T,code`；Referer | 确认兑换；原路径拼写是 **comfirm** | L1466 / M1460 |
| GET `search/` | `l,r,token=T,keyword` | `discs,allcount,canshowmore`；该 APK 搜索专辑 | L1467 / M1461 |
| GET `tags/` | `l,r,token=T,tag` | `discs,allcount,alltagslist,canshowmore` | L1468 / M1462 |

共 30 个不同路径。`getthisdicsinfo`、`gethislabelinfo`、`getdisccommit`、`comfirm_redeem` 均保留包中实际拼写，不作拼写修正。机器可读清单与 UTF-8 文件内位置见 [apk-endpoints.json](apk-endpoints.json)。

静态 API 中 GET 也会改变购物车、创建支付单、切换订阅，后续自动验证不能按“GET 等于只读”批量探测。此次没有请求这些接口，也没有购物、入库、关注、评论、上传或兑换任何数据。

## 播放、下载与本地库

`L1429 / M1423` 专辑详情使用服务端 `tracks`，并读取 `ihavethis` 以决定已购界面；试播播放器直接使用 track.url。`L1447 / M1441` 的 `geturl()` 返回 `nowtrack.url`；播放器 source 设 `Referer: https://www.dizzylab.net`，audioOnly、后台播放、缓存、暂停/继续、seek、进度、上一首/下一首及系统音乐控制。这里没有“根据 token 拼接固定流媒体地址”的代码证据。

下载链 `L1413 / M1407`：

1. 从设置读取 `downloadtype`，默认 `320`。
2. 使用 `gettrackdownloadurl`，传专辑、曲目、格式和 token。
3. 读取返回 `track.url`，以 Referer 下载到 `DocumentDirectoryPath/<discid>/<packtype>/<trackid>.mp3`。
4. 成功后记录 `downloaded=true`，将 `url` 替换为本地 `file://` URL，保存 playlist；同一专辑加入后批量排队下载曲目。

本地条目形状来自 `L1413`：`id`（专辑 ID）、`trackid`、`album`、`type`、`label`、`title`、`authers`（原拼写）、`like`、`url`、`coverurl`、`downloaded`。这里的曲目 `like` 是本地状态；专辑喜欢才对应 `ilikethisornot`。不要把本地曲目收藏误认为服务端歌单/曲目收藏 API。

`L1461 / M1455` 下载设置 UI 仅列出 MP3 `128` / `320`，并明确提示移动端暂不支持 FLAC、特典和下载商品需网站下载。底层代码有 `FLAC` 扩展名分支，**这不足以证明 APK UI 或现行接口支持 FLAC 下载**。APK 的能力是当前调查的补充基线，第三方客户端若以网页版大部分能力为目标，需要另行验证整包、FLAC、特典、下载商品和文件有效期等网页流程。

## 社交、购买与网页回退

- 专辑喜欢与社团关注均为 toggle 接口；重试可能反转状态。应先核对当前值，再进行用户明确触发的修改，成功后使用服务端响应更新状态。
- `postcomment` 同时承载发表与删除：正常发表传用户文字；删除分支传固定协议哨兵 `Y25zb3VrYTY2Ng==`。这只是旧客户端代码观察，当前服务端删除语义须验证，不能以普通留言测试。
- 兑换预检 `redeem`：代码将字符串 res `1`解释为无效/已用，`2`为已经拥有，`4`为可确认（同时读取 disc），`6`为必须网页版兑换；res `0`分支没有明确 UI 成功处理。确认 `comfirm_redeem` 中 res `1`进入成功页，`2`报错，`6`提示网站下载。**同名 res 在不同接口含义不同**。
- 购物车支持自选支付金额；免费且金额为零走 `unlockafreedisc`。`checkout_alipay` 的购物车路径用 `id=cart,q=allcount,type=cart,price=allfee`，赞助路径用 `id=discid,q=1,type=boost,price=用户金额`。返回 `order_string` 交给支付宝 SDK，客户端把 `9000`当成功、`6001`当取消；最终权益仍应重新查询服务端，不能只信 SDK 回调。
- APK 内支付宝 scheme 和非沙箱配置是该官方包自身配置，不应复制其应用身份到第三方客户端。支付、重置密码等可先采用网站页面完成，再刷新客户端权益。
- `L1423 / M1417` 密码重置以 WebView 打开 `https://www.dizzylab.net/resetpassword`；`L1470 / M1464` 打开 `https://www.dizzylab.net/app`。网页回退是包内已经采用的设计。
- Manifest 声明网络、前台服务、外部存储、网络/Wi-Fi 状态权限，并包含音乐通知服务、WebView FileProvider、支付宝 Activities。声明不等于运行时行为；第三方客户端应按自己的现代 Android SDK、Media3 和存储方案设计。

## 对本项目的落地价值与未决项

已回答：旧原生 API 的根地址、完整包内调用路径、请求方法/参数、token 登录形态、主要响应使用字段、专辑/社团/用户/已购/喜欢/关注关系、播放 URL 的来源、单曲下载链、购物/兑换入口、网页回退范围。

部分回答：token 生命周期、在线播放与已购下载的权限差异、分页边界、详情完整 schema、错误码全集、FLAC/整包/特典能力。这些只能以现行网页版实际请求和响应核对。

APK 静态分析本身不能回答：当前 API 是否仍有效、网页 Cookie 能否用于 `/apis/`、是否存在当前客户端 refresh/logout 接口、付费后权益同步时延、下载签名和过期时间、设备/并发限制、限流、恢复下载和 HTTP Range、服务端歌单/曲目收藏接口。APK 中未见这些调用不能证明服务端没有这些能力。主调查随后已实时验证其中一部分 API、Cookie/Token 差异及媒体/整包 Range，最新结论见 [网络契约](network-contract.md)，不要将这里的静态边界误读为主调查仍完全未验证。

建议扩展时维护 `WebSession`（网页 Cookie/CSRF）与 `AppTokenSession`（旧 /apis token）两种独立认证能力，按实时证据决定使用范围；只有经过现行服务端验证的调用才能提升到生产接入清单。个人库、喜欢、关注、已购权益与本地队列/离线文件应各有模型，避免以播放器的本地 playlist 代替平台资料库。
