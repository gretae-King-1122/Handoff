# Changelog

本项目的所有重要变更都记录在此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- **M2 第一刀：B 站分享卡片拦截上线** —— hook `QQBrowserDelegationActivity#onCreate`，读 intent extra `url`，
  命中 `bilibili.com/video/(av\d+|BV…)` 则 `setPackage("tv.danmaku.bili")` 交给 B 站 App 并 `finish()` 掉跳板页。
  实测 `HANDOFF hit av117512318557006 via http+setPackage`，直接进 B 站对应视频，无闪屏。
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
