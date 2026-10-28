# Dizzylab 网络契约与端点目录

基线：2026-10-28，本机 Agent-Browser + Edge 实时观察及有限请求。`V`=请求/响应已验证；`S`=页面/APK 静态发现；`U`=未验证。路径拼写、字段拼写保留原样，包括 `getthisdicsinfo`、`gethislabelinfo`、`authers`、`commit`、`comfirm_redeem`。不把旧客户端字段当成完整服务端规范。

后续实施范围已由用户限定：**付费购买只通过 WebView，不调用原生付费 checkout 或接入支付 SDK；原生支持免费领取与 CDK 预检/确认。** 下文交易端点是调查资料，不能据此扩大实施范围。

## 1. 主机与资源边界

| 主机 | 本次观察的职责 | 凭据策略 |
| --- | --- | --- |
| `www.dizzylab.net` | 网页、JSON、登录、CSRF、下载跳转 | Cookie 按 domain/path/secure 管理；Token 只放该站已验证参数中 |
| `cdn.dizzylab.net` | 封面、静态 JS/CSS、占位媒体；图片有 `!cover` 变换 | 不默认附带站点 Cookie/Token |
| `streaming.dizzylab.net` | API 返回的串流和单曲下载资源，Range 支持 | 使用响应 URL；参考官网/旧 APK 的 Referer；不默认传账户 Cookie |
| `albumsbase.dizzylab.net` | 网页整包下载跳转目标，ZIP、Range | 按签名资源访问；本次抽样不向该主机附账户 Cookie |

其他第三方 CDN、统计和社交分享主机只是页面依赖，不属于客户端业务 API。`myu.dizzylab.net` 等导航链接也不能据此扩展认证发送范围。以上是观察到的主机集合，不是未来完整白名单。

## 2. 登录与会话

### 2.1 网页 Cookie 会话（V）

入口为 `GET /albums/login/`。表单使用 `POST`，字段为 `username`、`password`、`csrfmiddlewaretoken`，另有隐藏的 `next` 跳转字段；见 [登录表单证据](evidence/web-login-form.json)。本次独立 Edge 登录成功后可访问个人库、订单、消息及设置页面。相关 Cookie 名包含 `sessionid`、`csrftoken`；其值均未保留。

CSRF 是 Cookie/页面中取得的当前值，不能硬编码。使用网站实际页面和同源 Referer；不要照搬某次 masked CSRF。`POST /getatrack/` 对照结果：无 CSRF 为 **403 HTML**，有效 CSRF 为 **200 JSON**。不能从这一个结果推断所有 `/apis/` 都使用同一种 CSRF 策略。

`/albums/logout` 为网页退出入口（S，未调用以免影响现有账号），不等同于已证实 Token 撤销。

### 2.2 App Token 登录（V）

```http
POST https://www.dizzylab.net/apis/auth/login/
Content-Type: application/json
Accept: application/json

{"username":"<temporary-username>","password":"<temporary-password>"}
```

成功响应形状：`{"token":"<redacted>"}`，本次为 HTTP 200 / `application/json`。本次没有测试错误密码、锁定、验证码、Token 过期/刷新。不能对未知登录错误盲目重试。

其后按客户端实际契约在 query 或 FormData 中传 `token`；**没有验证 `Authorization: Bearer` 可替代它**。API Token 不是 Cookie 串，也不等于 `csrftoken`。

### 2.3 网页衔接 Token（V 观察，稳定性 U）

登录后的 `/u/<uid>/music/` 内联脚本会构造：

```text
/apis/getthisdicsinfo/?discid=<discid>&token=<redacted>
```

从当前已登录页面取得的 Token 已用于资料、已购、关注、搜索等读取。没有发现独立、稳定的 Cookie 换 Token 专用接口。建议保留 WebSession 与 AppTokenSession，两者可分别失效；页面提取失败要显式回退，不能把 Cookie 内容作为 Token。

### 2.4 已验证的认证对照

| 请求 | Cookie | Token | 结果 |
| --- | --- | --- | --- |
| `getdiscs/getlabels/getthisdicsinfo` 的本次公开样本 | 无 | 无 | 200 JSON |
| `getmyinfo/getmydisc` | 有，网页已登录 | 无 | 200 HTML，非 API 成功 |
| `getmyinfo/getmydisc` | 无 | 有效 | 200 JSON |
| `getmyinfo` | 无 | 故意无效占位值 | 200 HTML，“出错了！” |
| 未购商品的 `gettrackdownloadurl` | 已登录 | 有效 | 200 HTML，不能作为可下载资源 |

证据：[guest-auth-and-pagination.json](evidence/guest-auth-and-pagination.json)、[token-only-and-error.json](evidence/token-only-and-error.json)、[web-cookie-myinfo.json](evidence/web-cookie-myinfo.json)、[unowned-download.json](evidence/unowned-download.json)。HTML 错误可能有多种原因，应结合已验证会话、请求参数、购买状态和响应判定，不能一律清空账号。

## 3. 已验证 JSON 接口

下列 API 路径相对 `https://www.dizzylab.net/apis/`。所有列表样本仅取 2 项；`T` 表示有效 Token。标记“公开”的接口有对应无 Cookie/Token 验证，其余不据此推断游客可用。

| 方法 / 路径 | 本次参数 | 关键响应字段 | 证据 |
| --- | --- | --- | --- |
| POST `auth/login/` | JSON `username,password` | `token` | [login](evidence/app-token-login-shape.json) |
| GET `getdiscs/` | `l,r,sort=ad,type=album`；公开 | `discs,canshowmore,total_count` | [catalog](evidence/catalog.json)、[guest](evidence/guest-auth-and-pagination.json) |
| GET `getlabels/` | `l,r`；公开；页面还使用 `sort` | `labels,canshowmore,total_count` | [labels](evidence/labels.json) |
| GET `getthisdicsinfo/` | `discid`，可加 `token=T`；公开 | 详情、`tracks,ihavethis,ilikeit` | [owned](evidence/owned-detail.json)、[unowned](evidence/unowned-detail.json) |
| GET `getmyinfo/` | `token=T` | `user{uid,username,mail,cover,user_group}` | [myinfo](evidence/token-myinfo.json) |
| GET `getmydisc/` | `l,r,sort=ad,token=T` | `discs,canshowmore` | [library](evidence/token-library.json) |
| GET `getmylike/` | `l,r,sort=ad,token=T` | `discs,canshowmore`；本次空列表 | [likes](evidence/token-likes.json) |
| GET `getmyfollowing/` | `l,r,sort=ad,token=T` | **`discs`** 内实际是社团；含 `id,title,cover,wantsomemail` | [following](evidence/token-following.json) |
| GET `getfeed/` | `l,r,sort=ad,token=T` | `labels[].{labelid,title,labelcover,add_date,discs[]},canshowmore` | [feed](evidence/feed.json) |
| GET `gethislabelinfo/` | `labelid,l,r,token=T` | `labelid,title,cover,description,description_2,disc,members,following,canshowmore` | [label](evidence/label-info.json) |
| GET `getlabeldiscs/` | `labelid,l,r,token=T`；APK 还使用 `without` | `discs,canshowmore` | [label-discs](evidence/label-discs.json) |
| GET `search/` | `keyword,l,r,token=T` | `discs,allcount,canshowmore` | [search](evidence/token-search.json) |
| GET `tags/` | `tag,l,r,token=T` | `discs,allcount,alltagslist,canshowmore` | [tags](evidence/token-tags.json) |
| GET `getdiscbuyers/` | `discid,l,r` | `buyers,boosters,canshowmore`；条目含 `id,name,cover` | [buyers](evidence/buyers.json) |
| GET `getdisccommit/` | `discid,l,r` | `commit,icommitthis,mycomment,canshowmore` | [comments](evidence/comments.json) |
| GET `getcart/` | `l,r,token=T` | `discs,allcount,allfee,canshowmore`；本次空车 | [cart](evidence/token-cart.json) |
| GET `gettrackdownloadurl/` | `discid,trackid,packtype=128或320,token=T` | `track{trackid,ext,url}` | [128](evidence/track-download-128.json)、[320](evidence/track-download-320.json) |

`search/tags` 在本次 Cookie-only 测试也返回 HTML；带 Token 后才取得 JSON。不要因为它们的内容公开就去掉 Token。网页 HTML 搜索不需要与 API 搜索共用同一种适配器。

## 4. 字段、身份和分页

### 4.1 专辑和曲目

详情观察到的字段：

```text
Album:
  id:string, title:string, cover:string
  label:string, labelid:number, labelcover:string
  label_description:string, label_description_2:string
  disc_description:string, disc_description_2:string
  release_date:string, tags:string[], price:number
  ihavethis:boolean, ilikeit:boolean, likes:number
  onsell:boolean, ispreselling:boolean, ishires:boolean
  hasgift:boolean, onlyhavegift:boolean
  tracks:Track[]

Track:
  discid:string, id:string, title:string, authers:string
  album:string, coverurl:string, url:string
```

`Track.id` 样本是专辑内的字符串序号，不能假设是全站唯一 ID；建议曲目键为 `(provider, discid, trackid)`，账号权益另以 account 关联。不要从媒体 URL hash 生成持久身份。页内播放器把序号转成零基 DOM 索引，不代表 API `trackid` 也要减一。

不同列表的 DTO 不相同：`search` 简表只含 `id/title/cover/label`；已购列表有 `boost:null`；目录含 `ishires/onlyhavegift/tags`；关注接口把社团列表命名为 `discs`。按端点分别解析，再映射领域模型，避免强套详情 DTO。

`onsell=false` 在首页会显示“兑换”，`ispreselling` 为额外状态；不能只靠 `price<=0` 决定能否免费入库。`ihavethis` 与服务端下载结果共同决定可下载能力，不能只看页面按钮、`full` 路径或音频是否能播放。

### 4.2 分页

网页首页 `pagecount=24`，构造 `l=index*pagecount`、`r=(index+1)*pagecount`，用 `total_count` 计算页数。APK 多处使用同类左右边界与 `canshowmore`。

实测 `[0,2)`、`[2,4)` 各返回 2 项、无交集，符合该样本的左闭右开模型。不要把其他接口的页边界、上限和 `sort` 枚举视为已证明。个人库 HTML 使用 `page`，社团关注 HTML 使用 **`dp`**，不能统一硬编码成 `page`。

原生层建议统一返回 `PageResult(items,next,hasMore,total?)`；保留原接口分页上下文，去重与取消请求，避免像现有实现那样先抓所有页再让 UI 假分页。

## 5. 网页 JSON 与 HTML 适配

以下路径相对站点根，与 `/apis/` 是另一组请求。

### 5.1 已验证网页 JSON

| 方法 / 路径 | 参数 | 响应摘要 / 限制 |
| --- | --- | --- |
| GET `/albums/getthisdisclikes/` | `discid` | `ilikethis:boolean,likes:number`；注意不同于 API 的 `ilikeit` |
| GET `/albums/getbuyers/` | `discid,l,r` | 平行数组 `avatar_url,names,prebuyer,redeemit,user_url`，以及 `canshowmore` |
| GET `/albums/getdisccomment/` | `discid,l,r` | 平行数组 `avatar_url,disc_comment,prebuyer,redeemit,user_names,user_url`，以及 `canshowmore`；本次空样本 |
| GET `/getreviews/` | `discid,l,r` | `review_id,review_title,review_desc,review_comment,like,dislike,add_date,user_* ,detail_pic_1..4` 等平行数组；本次空样本，元素类型未实测 |
| POST `/getatrack/` | URL-encoded `csrfmiddlewaretoken` | `discid,disctitle,disclabel,disccover,discdesc,thistrack,thisauther,thisurl,full,gotourl,rndnum,taglist` |

证据：[web-disc-likes.json](evidence/web-disc-likes.json)、[web-buyers.json](evidence/web-buyers.json)、[web-comments.json](evidence/web-comments.json)、[web-reviews.json](evidence/web-reviews.json)、[web-random-post.json](evidence/web-random-post.json)。`GET /getatrack/` 返回 HTML；实际方法是 POST，不能据错误方法断言端点不可用。

平行数组须逐行校验长度，不能盲目用同一 index 拼对象。空样本只确认字段存在，不证明非空 item schema。

### 5.2 已访问的 HTML 页面

| 页面 | 参数 / 数据用途 | 适配提示 |
| --- | --- | --- |
| `/` | Banner、分类、Pack、优惠 | 普通专辑主体由 JSON 追加；不要只解析最初 HTML |
| `/label/`、`/l/<label-name>/` | 社团目录与详情 | 数字 `labelid` 与名字型网页路由是不同标识 |
| `/d/<discid>/` | 商品、曲目、下载、评论、REPO | 可能 301/302 到带尾斜杠路径 |
| `/search/` | `s,page`；另见表单 `/albums/search` | 全站搜索，不同于个人已购 `q` |
| `/tags/` | `tag,page`；专辑页亦链接 `/albums/tags/` | URL 编码参数，不拼接裸空格/特殊字符 |
| `/u/<uid>/music/` | `q,page` | 已购 HTML；会内嵌 API Token |
| `/u/<uid>/likes/`、`following/` | 喜欢、关注；后者 `dp` | 不直接等同本地播放器收藏 |
| `/feed/` | 关注动态 | 实际数据来自 `getfeed` |
| `/pack/?pk=<packid>` | 合集、折扣、可能含实体商品 | 支付链接有 `type=pack`；不要自动跟随 |
| `/albums/purchases/`、`/albums/purchases/boost/` | 数字订单、BOOST；分页 | 已访问数字订单页；BOOST 子页只发现链接 |
| `/albums/msgbox/` | 收件箱 `tab,q,page` | `?m=` 是删除链接（S），不能当分页参数 |
| `/u/<uid>/options/` | 当前设置表单 | 字段见下；没有提交修改 |
| `/redeem/` | POST `redeembox,csrfmiddlewaretoken` | 未提交兑换码 |
| `/ranking/` | 排行榜 | 未建立结构化数据契约 |
| `/deflate/`、`/u/<uid>/deflate/` | 音游/个人页面 | 只盘点入口和统计脚本；不声明完成游戏协议调查 |

设置表单静态字段：`avatar` 文件、`newname`、`newdesc`、`newmail`、`check_mail`、`check_rank`、`password_old`、`password_new`、`csrfmiddlewaretoken`；页面有 `action` 名称的提交控件。读取表单目标须使用 `getAttribute('action')`，避免 `form.action` 被同名控件遮蔽。未验证每个按钮的提交值和服务器分支。

## 6. 播放与下载契约（V）

### 6.1 在线资源

详情 → `tracks[].url` → 播放；不要根据常量拼出音频路径。样本为：

```text
https://streaming.dizzylab.net/<date-like-segment>/<signature>/<discid>/<variant>/<track-file>
```

日期样式和签名提示资源有时效，但没有测试 TTL，不解析它们来宣称精确过期时间。游客样本也可能返回含 `full` 的路径；这不意味着全站商品都提供完整下载。

在线播放与 320 单曲下载的 Range 抽样均为 `206 audio/mpeg`，`Content-Range: bytes 0-1023/<total>`、`Accept-Ranges: bytes`。一个已购播放样本带/不带 Referer 都成功；APK 确实设置 Dizzylab Referer。**不能从单次对照推断全站永远无需 Referer**；实现应按提供方策略配置，并避免发送不必要凭据。

### 6.2 单曲下载

```text
GET /apis/gettrackdownloadurl/
    ?discid=<discid>&trackid=<album-local-trackid>
    &packtype=128|320&token=<redacted>

200 application/json
{ "track": { "trackid": "string", "ext": "string", "url": "<temporary-url>" } }
```

128/320 的 URL 查询均成功；320 最终资源做了 Range 验证。未购样本返回 HTML 而非 URL。文件扩展名用返回 `ext`，音质选项用服务端支持的能力；单曲 FLAC 尚未实测，不因旧 APK 有扩展名分支就启用。

### 6.3 网页整包下载

先读取已购商品页取得当次链接，再请求：

```text
GET /albums/download/?d=<discid>&tp=<128|MP3|FLAC>&k=<signed>&v=1
Cookie: <web-session>

302 Location: https://albumsbase.dizzylab.net/<signed-resource>
```

本次 3 种格式的最终资源均为 ZIP、支持 206 Range。抽样总长度分别约 33.16 MB、80.18 MB、483.35 MB，仅用于说明已验证的不同格式资源，不写死到业务逻辑。没有把完整资源或签名写入证据。

`tp=MP3` 是网页高音质的实际拼写，不能直接改成 API 的 `packtype=320`。`k` 从页面取得，不是 Token；不要自行生成、长期缓存或跨账号重用。未测试缺 `k`、过期 `k`、特典、下载商品、全部压缩包内容。

原生下载应流式写文件，检查最终域、Content-Type、Range/偏移和实际字节数；验证成功才标“完成”。单曲保存与 ZIP 保存/解压是两种任务类型。Range 支持只证明服务器接受范围请求，恢复下载、ETag/Last-Modified、一致性和临时地址刷新仍需后续实现验收。

详见 [media-and-pack-range.json](evidence/media-and-pack-range.json)。

## 7. 写入、交易与特殊 GET：主要为 S

这些端点有足够静态线索用于后续设计，**不能直接标记为已实测的写功能**。不做自动批量探测、扫描或超时盲重试。

| 方法 / 路径 | 已观察字段 / 语义 | 状态 |
| --- | --- | --- |
| POST `/apis/ilikethisornot/` | FormData `discid,token`，Toggle 喜欢；返回 `ilikeit,likes` | APK S |
| POST `/apis/ifollowthisornot/` | `labelid,token`，Toggle 关注 | APK S |
| POST `/apis/postcomment/` | `discid,token,comment`；旧代码也用特殊 comment 值删除 | APK S，不将删除哨兵当普通内容发送 |
| POST `/albums/ilikethisornot/` | `discid,csrfmiddlewaretoken` | 网页 S |
| POST `/albums/postdisccomment/`、`/albums/deletemycomment/` | `discid,csrfmiddlewaretoken`；发表另有 `comment` | 网页 S |
| GET 社团页 `?dislike`、`?mailoff` | 当前样本的取消关注、关闭通知链接 | 网页 S，有写入作用；其他相反动作参数未假设 |
| GET `/apis/editcart/` | `discid,token,func=ADD|DELETE`，ADD 另有 `price` | APK S，有副作用 |
| GET `/albums/addtocart/` | `id,q,type,price,commit` | 网页 S；金额为零的前端分支会直接走 checkout |
| POST `/apis/unlockafreedisc/` | `discid,token`，免费入库 | APK S，免费仍会修改权益 |
| GET `/apis/checkout_alipay/` | `token,id,q,type,price` → `order_string` | APK S，原生支付宝 SDK，不复用官方应用身份 |
| GET `/albums/checkout_alipay/`、`/albums/checkout_paypal/` | `id,q,type=dig|boost|pack,price`；商品/BOOST 有 `commit` | 网页 S，可能生成订单；金额/币种不能由前端猜测 |
| GET `/albums/ajaxcheckpay/`、`ajaxcheckpay_pack/`、`ajaxcheckpay_cart/` | `discid&tn` / `pk&tn` / `g&tn`，旧脚本读取 `paid` | 网页静态脚本 S；没有用真实单号测试 |
| POST `/apis/redeem/`、`/apis/comfirm_redeem/` | `token,code`；预检/确认、接口间 res 含义不同 | APK S；未消费兑换码 |
| GET `/apis/iwanttweeksomething/` | `labelid,token,changemail` 裸 flag | APK S，切换通知 |
| POST `/apis/uploadandchangeuseravatar/` | multipart `token,avatar` | APK S |
| GET `/apis/checkusername/` | `name`；前端期望 `return=ok` | 网页 S；没有拿用户名做可用性探测 |
| POST `/u/<uid>/options/` | 设置表单及 CSRF | 网页 S；不提交账号变更 |
| GET `/review/create/?discid=` | 打开/创建 REPO 草稿入口 | 实际跳转至编辑页；可能建草稿，不是只读 |
| POST `/review/<id>/` | `frmtitle,frmdesc1,frmfavsong,detail_pic_1..4,csrfmiddlewaretoken` | 表单 S，未发表 |
| GET `/review/<id>/?iwant=deletethis` | 删除 REPO | 本次仅对空白草稿按用户单独授权执行并复核；不代表通用写功能完成 |
| POST `/postreviewcomment/` | `id,iwant,comment,csrfmiddlewaretoken` | 网页 S |
| POST `/ilikethisreviewornot/` | `id,iwant=like|dislike,csrfmiddlewaretoken` | 网页 S；读取为 `/getreviewlikesanddislikes/?id=` |
| GET `/getreviewcomments/?id=` | REPO 评论读取；本次仅脚本发现 | S；分页字段须另验 |
| `/albums/sendto/?uid=`、`/albums/msgbox/?m=` | 回信编辑入口、消息删除链接 | 页面链接 S；未发送或删除消息 |
| GET `/deflate/api/play-stats/` | 脚本期望 `status=ok,data` | S，未调查游戏写入协议 |

支付应由实际服务端订单状态和权益复查确定结果；SDK 成功回调或页面关闭不等于入库成功。建议先使用受控网页完成支付/兑换/设置，返回应用后刷新库和权限。不要把网页 GET 写动作加入预取、缓存预热或后台重试。

## 8. 错误、重试与日志建议（I）

读取响应按以下顺序分类：网络/TLS → 状态/最终 URL → Content-Type → JSON 解码 → 必要字段及业务状态。遇 200 HTML 保留脱敏诊断种类并验证会话，不把正文原样上传日志。二进制下载也必须先排除 HTML。

读取可用限次退避与取消；修改操作需要动作级策略。特别是喜欢/关注为 Toggle，超时可能“已成功但客户端未收到”，再次请求会撤销；应先查询当前状态。交易/兑换不可自动重复创建。

日志、备份、配置导出同时隐藏：Cookie、Authorization、登录 JSON、Token query/FormData、CSRF、下载 `k`、媒体签名路径、支付单串/交易标识及个人邮箱。保留 endpoint 模板、方法、状态、耗时、响应类型和字段错误即可。

完整真实请求不放在仓库 HAR 或 fixture 中。本目录 evidence 是抽取的字段/请求形状与来源位置，不能用它重放账号会话。
