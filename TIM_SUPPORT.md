# TIM 支持（v0.9.1）

## 使用

1. 安装 v0.9.1 正式包或构建当前源码的 Handoff（v0.8.1 原正式包不含 TIM）。
2. 在 LSPosed 为模块勾选 **TIM** `com.tencent.tim`；使用 QQ 时也可同时勾选 QQ。无需勾选目标 App 或系统框架。
3. 强停重开 TIM，在 Handoff 首页选择 TIM，检查版本、作用域、主进程激活及入口诊断。
4. 在 TIM 点击支持的 B 站视频链接／小程序卡片；v0.9.1 增加已适配浏览器入口的 `space.bilibili.com/数字UID` 个人空间链接。抖音和快手须先独立开启试验开关，两宿主共用这些开关。

TIM 适配延用 QQ 的链接白名单、身份校验和启动失败放行策略；v0.9.1 仅新增精确个人空间域名和数字 UID 路径，不任意扩大链接范围、不绕过外跳确认。快手短链解析失败时用 TIM 自己创建的不可变、一次性 PendingIntent 返回原 TIM 浏览器，不回到 QQ。

## 静态适配基准

- 输入：仓库根目录本地 `tim_4.1.0.4050.apk`（不提交安装包和反编译源码）。
- 包名 `com.tencent.tim`，versionName `4.1.0`，versionCode `4050`。
- SHA-256：`5e126598722a743a5464907ec222d58a878e5088d9c8cbd92b86fa56fac58c83`。
- 证据来自 APK manifest、DEX 方法／字段表及定向反编译；不执行 APK。

TIM 虽然包名不同，相关实现仍使用 `com.tencent.mobileqq.*` / `com.tencent.qqmini.*` 类名；不能把所有类名前缀直接替换为 `com.tencent.tim`。

| 边界 | 本地 APK 契约 |
| --- | --- |
| 浏览器派发 | `com.tencent.mobileqq.app.QBaseActivity` 声明 `void startActivity(Intent)` 和 `void startActivity(Intent, Bundle)` |
| 浏览器调用方 | `com.tencent.mobileqq.activity.QQBrowserDelegationActivity` |
| 浏览器目标 | `QQBrowserActivity`、`QQH5BrowserActivity`、`QQTranslucentBrowserActivity`，均在 `com.tencent.mobileqq.activity` 下；Intent 组件包名必须为 `com.tencent.tim` |
| 小程序 SDK | `com.tencent.qqmini.sdk.MiniSDK` 的 static void `startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)` |
| 小程序服务 | `com.tencent.qqmini.sdk.server.LaunchManagerService` 的 instance void 同签名方法 |
| SDK 模型 | `com.tencent.qqmini.sdk.launcher.model.MiniAppInfo` |
| 小程序调用方 | `com.tencent.mobileqq.activity.QPublicTransFragmentActivity` |
| 最终容器 | `com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI` 和 `AppBrandUI1`～`AppBrandUI3`，组件包名必须为 `com.tencent.tim` |

定位：派发基类在 `classes.dex`，浏览器／过渡调用方在 `classes9.dex`，SDK／模型在 `classes4.dex`，服务在 `classes16.dex`，容器在 `classes23.dex`。
定向反编译观察到浏览器跳板复制 Intent、按 `browserType` 设置上述目标、读取 `url` 后调用 `startActivity` 再 `finish`；服务在派发前写入 `KEY_APPINFO`、`sdk_mode=true` 和 `receiver`。模型仍提供 `appId`、`launchParam.entryPath`／`miniAppId` 与 `firstPage.pagePath`，因此无需放宽既有身份／页面白名单。

可用以下命令重现 DEX 类、方法签名与字段库存（不执行 APK，不输出聊天或登录数据；JSON 保存在已忽略的 `tmp` 目录）：

```powershell
python tools/inspect_tim_apk.py tim_4.1.0.4050.apk --output tmp/tim-contract.json --check-contract
```

包名与版本另外使用 Android SDK 的 `aapt2 dump badging` 核对；库存脚本不是动态控制流或真机验收工具。

TIM 配置与 QQ 配置独立，版本号在各自包名内匹配；未知 TIM 版本只严格探测既有签名，失败即放行，不被误判为某个 QQ 版本。健康通道携带宿主身份，并将其与调用 UID 对应包名核对；存储、激活、诊断与手动验收证据不跨宿主混用。旧版无宿主字段的 QQ 记录仍按 QQ 读取。

## 验收边界

### 本次自动验证（2026-11-02）

- 236 项 JVM／Robolectric 测试通过（API 17、规则 11、Hook 93、执行器 28、存储 15、UI 72），无失败、错误或跳过。包括跨宿主浏览器／容器拒绝、TIM 短链失败回退、UID 身份绑定和旧 QQ 数据兼容。
- Debug／Release APK 构建通过；应用及 UI 的 Release lint 均为 0 错误，仍有非阻断依赖升级、图标及 SharedPreferences API 建议（应用 21、UI 5 条），不为此次适配更改无关依赖或图标。
- Release APK 元数据核实为 `com.handoff.module` / `0.9.0-dev` / 15，APK v2 签名验证通过；打包作用域同时包含 QQ 与 TIM。
- `tools/inspect_tim_apk.py --check-contract` 对输入 APK 校验通过；TIM 首页离线 Compose 渲染已检查。测试日志和静态库存位于已忽略的 `tmp` 下，不提交反编译源码或 APK。
- 本机使用 JBR 21、GBK Gradle daemon 解决中文目录测试 classpath 问题，源码保持 UTF-8；具体绕行方式见 README 构建章节。

### 用户实机反馈（2026-11-02）

Handoff `0.9.0-dev` / 15 已覆盖安装到 PGZ110，保留原有应用数据。用户反馈 TIM 中带「QQ 小程序」标识的卡片可以点击；带「哔哩哔哩」标识的卡片，无论开启还是关闭 Handoff 模块，都提示「请到最新版QQ使用」。此处按界面标识记录，不推断内部卡片类型，也不把“可以点击”扩大为所有视频交接成功。

该提示尚未定位具体原因，当前适配没有解决或绕过；不能把这类卡片列为已验证成功的入口。用户反馈原文及限制同步见 README。

### 正式发布验证（2026-11-03）

- v0.9.0 / versionCode 16 包含 TIM 适配及经审计合并的 PR #2；242 项 JVM／Robolectric 测试通过（API 17、规则 11、Hook 95、执行器 28、存储 19、UI 72）。
- Debug／Release 构建、应用／UI Release lint 及 Hook／存储 Debug lint 通过（存在非阻断警告）；补齐主分支已有的 Android 13 进程名 API 版本保护。
- 本次未重新装机执行 QQ／TIM 交接回归，不能将前述开发包有限反馈升级为正式包全流程验收。签名、版本和作用域验证不等于具体视频已打开。

当前已有上述有限实机反馈，**尚未完成 TIM 全流程交接验收**。签名存在、模块激活或入口已安装均不代表已打开对应视频。动态加载／热补丁、TIM 新版本、各卡片入口是否走这些路径仍需设备验证。

### v0.9.1 发布验证（2026-11-03）

- 审计合并 PR #3，249 项 JVM／Robolectric 测试及 150 项 QQ／TIM URL 边界检查通过；Debug／Release 构建与应用／UI Release、Hook／存储／执行器 Debug lint 通过（有非阻断警告）。
- 新增个人空间仅发往国内 B 站包：空间 scheme → 同空间 HTTPS，全部启动失败则保留原宿主网页，不新增首页兜底。复用 B 站网页开关。
- 未重新装机验收个人空间、视频及卡片；不能将 Android 启动请求被接受或原开发包反馈视为 v0.9.1 已端到端验证。既有「请到最新版QQ使用」提示未修复。

真机清单（分别在 TIM 和 QQ 执行）：

- B 站标准 `BV`／`av` 视频链接：进入目标视频，不并行打开宿主网页。
- B 站个人空间链接：进入对应 UID 的空间，不并行打开宿主网页；关闭网页开关或启动均失败时保留原网页，不新增首页兜底。
- B 站小程序卡片：进入视频；不支持的页面、身份冲突及带回调请求保留原流程。
- 抖音／快手默认关闭；分别开启后验证对应作品及短链，关闭后保留宿主行为。
- 快手短链解析失败／超时返回原宿主浏览器；取消后不延迟拉起其他 App。
- 目标 App 未安装、普通网页、非视频及不支持的卡片不得误拦。
- 两宿主同时安装：单独作用域不误报另一宿主激活；切换后诊断与测试记录互不串用。
- 升级模块后强停重开启用的宿主；确认 QQ 既有功能无回归。
