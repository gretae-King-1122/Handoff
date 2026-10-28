# Changelog

本项目的所有重要变更都记录在此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Changed

- 补齐公开仓库 README：说明实际支持范围、尚未发布 APK、安装及停用步骤、签名限制、构建测试方法和脱敏反馈要求；
  修正 Android 最低编译版本为 8.0（API 26），明确不代表框架兼容或全场景真机验证。
- **默认轻量交接模式**：debug/release 均不再默认挂载历史诊断探针，
  `-PhandoffDiagnostics=true` 可恢复完整发现链；交接 Hook 优先安装，loader 身份防重复安装。
  保留失败放行与外跳确认，不缓存失效 Intent 或 QQ Activity，不改变视频跳转降级链。
- 反射查找缓存缺失结果，诊断回调的参数类型元数据移至安装阶段；
  新增安装和命中分段耗时，正常路径只记短摘要。新增 8 项运行计划/防重装/缓存测试，合计 24 项通过。
  优化版尚未真机测量，不声明端到端提速比例。
- 精简仓库文档：保留 README、CHANGELOG 与 LICENSE，移除内部规格、架构、研究和测试记录；
  补充本地凭据及签名材料的 Git 忽略规则。

### Fixed

- **B 站外跳与 QQ 浏览器并行打开**：将 `BrowserHandoff` 的晚期 onCreate 观测改为
  `QBaseActivity.startActivity(Intent[, Bundle])` 的派发前拦截，仅限 QQBrowserDelegationActivity
  发往白名单内置浏览器的可信 B 站视频链接。外跳接受则不再启动浏览器，失败则放行一次；
  保留原生命周期与确认流程，新增重入保护、域名/路径校验和 9 项协调器测试。
  2026-10-28 真机一轮确认外跳通过：进入 B 站视频页，无 QQ 内置浏览器启动，返回 QQ；
  取消/失败与负对照仍待回归。

### Added

- **M2.1 小程序解析后只读探针**：观察 `MiniAppConfig` / SDK `MiniAppInfo`，
  P2 定向读取 `KEY_APPINFO`，覆盖 `doOnCreate` / `doOnNewIntent` / `doOnResume` 和 SDK 带路径事件。
  新增 `RESOLVED` / `PAGE` 来源、候选路径/身份冲突/单调时钟/Activity 与 loader 标识；
  通过已知 `MiniAppDexLoader` 补装动态类，按 Method 去重。新点只记录不阻断，替代旧全方法 Sweep。
- 新增 7 项纯 JVM 字段提取与跨 ClassLoader 反射缓存测试；修正反射同名类缓存串用和日志时间格式器线程安全。

- **M2 第一刀（旧实现，已由派发前拦截替代）** —— hook 继承的 `QQBrowserDelegationActivity#onCreate`，读 intent extra `url`，
  命中 `bilibili.com/video/(av\d+|BV…)` 则 `setPackage("tv.danmaku.bili")` 交给 B 站 App 并 `finish()` 掉跳板页。
  当时实测可进入 B 站对应视频；后续发现 QQ 浏览器可能已同步启动，不能以外跳成功作为阻断成功证据。
- 拉起降级链：`https + setPackage` → `bilibili://video/<aid>` → 包启动 → 放行（失败绝不阻塞用户）

- `module-hook-lsposed` 真机探针（M1.1 ~ M1.8，全部只观测、不改行为）：
  - `Dump`：反射打印对象全部实例字段
  - `ArkClickProbe`：`MessageForArkApp` 的三个 `click*` 方法
  - `MiniEntryProbe`：`MiniAppServiceImpl#launchMiniApp` / `MiniAppLauncher#startMiniApp` / `launchAppByFakeLink` 实参
  - `ArkMessageProbe`：`doParse` / `doOnEvent` / `openThirdApp` / `getJumpUrl` + `ArkAppContainer#doOpenCardViewEvent`
  - `TemplateProbe`：`TemplateMsgMiniAppLinkConfigParser`
  - `StructProbe`：`MessageForStructing#doParse`
- `ControllerHook` 增加调用栈打印（M1.3），用于定位 QQ 侧调用链


- 工程骨架：7 个 Gradle 模块（`app` / `module-api` / `module-rules` / `module-executor` / `module-hook-lsposed` / `module-storage` / `module-ui`），Gradle 9.5.1 + AGP 9.3.1 + Kotlin 2.4.10 + JDK 25
- `:module-api` 契约层（纯 JVM、零依赖）：`MiniAppEvent`（含 Java `Builder`）、`Decision`、`Target` / `TargetSpec` / `ExecResult`、`Config`、`LogEntry`、`Pipeline`、`HookBackend` / `EventSink`、`RuleEngine`、`TargetExecutor`、`ConfigRepository`、`LogRepository`
- `:module-hook-lsposed`：P0 / P1 / P2 三个 hook 点、`HookTargets` 候选表、`Reflect` 反射缓存、`EventBuilder` 事件转换
- `:app`：`ModuleMain` 入口、`PipelineImpl`（M1 只记录不阻断）、`META-INF/xposed/{java_init.list,module.prop,scope.list}`、R8 规则
- 可出包：`./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

### Notes

- M1 探针版：**不跳转、不写文件、不联网**，命中事件只走 Xposed log（`log(INFO, "Handoff", …)`）
- 目标进程内 `openRemoteFile()` 只读，原「QQ 进程写 handoff.log」方案作废，见 SPEC §9.3
- 待真机验收：`miniAppId` / `entryPath` / `scene` 的实际值（SPEC §3.1）
