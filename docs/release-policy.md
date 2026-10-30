# 版本、签名与发布规范

本规范自 `beta-2.0.0-20261030` 起生效。基准正式版是仓库当前已发布的 `2.0.0`；`version.properties` 是版本信息的唯一来源，Gradle 生成应用内版本与更新基准时间。

## 版本规则

| 类型 | versionName | Git 标签 | 本次状态 |
|---|---|---|---|
| 正式版 | `大规模重构.中等规模重构.小规模重构` | `v<versionName>` | 当前基准 `2.0.0`；下一个中等重构目标 `2.1.0` |
| 预发行 | `beta-<最新正式版版本号>-<本次重构日期YYYYMMDD>` | 与 versionName 相同 | `beta-2.0.0-20261030` |

正式版三个计数按层级递增：大规模重构加第一位并清零后两位；中等重构加第二位并清零第三位；小规模重构加第三位。本次预发行不提前移动正式版基准。

`versionCode` 是两个频道共用、严格递增的安装序号，与展示版本独立；本次是 `31`。正式发布、预发行或同日重新打包均须使用更大的 versionCode。日期以 Asia/Shanghai 的重构日期为准。一天内沿用相同预发行名称时，更新既有预发行及附件，不增加其他不规范的版本后缀；随包 `SMP-release.json` 提供更高的 versionCode，客户端可识别同名重打包。

构建默认是 beta；正式构建通过 `-PreleaseChannel=stable` 选择。正式发布前必须更新版本文件的 stableVersion、versionCode、releaseBuiltAt，并把已发布正式版回写为后续 beta 的 baseStableVersion。禁止通过搜索替换 MainActivity 中的字符串维护版本。

## 包名与签名

- 所有对外发布 APK 的包名统一为 **`com.supermite.smp`**，正式与 beta 共用一把长期私钥。开发验收使用 `.debug` 隔离包，不能作为对外安装包。
- 新签名别名 `supermite`，RSA 3072 / SHA256withRSA。`supermite` 是 DRBG 个性化输入，密钥同时使用系统安全随机熵；它不是可公开复现私钥的密码或唯一种子。
- 新证书 SHA-256：`7e5b2656f4111b0278efe397324850c6d987fec9a5e8505096ccb216e452890d`。
- 私钥与签名配置保存在本机项目之外的 `%LOCALAPPDATA%/SMP/signing/`。可通过 `SMP_SIGNING_PROPERTIES` 指向其他安全备份位置。源码和发布附件只包含公钥证书指纹，不包含 keystore、密码或签名配置。
- 缺少签名配置时，release 打包必须失败；不得回退为调试签名，也不得在另一台机器自动生成新发布私钥。请安全备份同目录的 `supermite-release.p12` 与 `signing.properties`。

### 旧版迁移

已核验仓库正式版 `v2.0.0` 的包名也是 `com.supermite.smp`，但旧证书 SHA-256 为 `196a149b3f56f2ffe13fb895a9044cb6a7331dd9ee2f8eb1e9e19a7fec052826`。旧私钥不再可用，经用户确认建立了本次新长期签名。**旧签名安装不能直接被新签名覆盖。**

首次迁移前，在旧版导出配置，并备份需要保留的音乐/歌单；卸载旧签名应用后安装新版、导入配置。Dizzylab 凭证不随配置导出，需重新登录。旧 `.debug` 验收包与正式包相互独立。后续采用本规范发布的正式/beta APK 可在 versionCode 递增时相互覆盖升级。

## 更新选择规则

1. 通过 GitHub Releases API 分页读取记录；正式频道只取 `prerelease=false`，预发行频道只取 `prerelease=true`。
2. 排除草稿、没有 APK 和名称含 unsigned 的包；不依赖接口返回的首条记录，也不从版本字符串中抽取数字比较。
3. 在所选频道中按 `published_at` 降序选择最近发布、带 APK 的记录；编辑旧发行说明不会把它当成新版本。
4. 新规范附件 `SMP-release.json` 中的 packageName/versionName 匹配时，优先以 versionCode 判断升级；没有元数据的旧发行，使用发布日期与本地 releaseBuiltAt 比较。同版本不会重复提示。
5. 手动检查总是显示该频道最新安装包、发布日期和文件名，可打开 APK 与发行说明。自动检查只提示较新安装。切换到较旧频道时说明 versionCode/旧签名限制，不自动降级。

发布内容包括：签名 release APK、对应源码 ZIP、校验和、SMP-release.json、变更说明与视觉验收报告。发布前验证包名/versionCode、证书指纹、单元测试、Lint 和真实模拟器视觉证据；Git 提交应对应发布标签及源码包。
