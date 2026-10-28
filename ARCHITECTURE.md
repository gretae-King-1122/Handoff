# Handoff · 架构设计（ARCHITECTURE）

> 本文是 **SPEC 的实现视图**，不是决策出处。
>
> - **决策真源**：[`SPEC.md`](SPEC.md)。本文与 SPEC 冲突时，**一律以 SPEC 为准**，并须修正本文。
> - 本文只负责 SPEC 不展开的部分：模块内部包结构、接口签名、装配与生命周期、线程与异常边界、M1 剪裁。
> - 版本：v0.2.1-draft · 对齐 SPEC v0.2.0-draft · 最后更新：2026-10-27
> - 上一版（v0.1，旧名 `MiniJump`、单模块结构、hook 层 Kotlin）**已作废**，差异见 [§14](#14-与上一版minijump-v01的差异)。

---

## 1. 系统边界

> 2026-10-28 实现快照：当前生产路径为 `BrowserHandoff` 浏览器派发交接；
> P0/P1/P2、字段事件 Pipeline 均仍为诊断。默认只装生产路径，构建时
> `-PhandoffDiagnostics=true` 才装完整发现链，且总是先装浏览器交接。
> 下文规则分层/存储/热更新是目标架构，并非全部已接线；实际模式与测量见 `docs/PERFORMANCE.md`。

一句话：**Handoff 是一个 LSPosed 模块。装在 QQ 里，把「好友分享的 Ark 卡片」点击后本应打开的内置小程序，交接到对应的原生 App（首期：哔哩哔哩）。**

```
点击卡片 → Hook 拦截(P0) → 提取变量 → 规则决策 → 降级执行 → 成功则阻断 / 失败则放行
```

### 1.1 一次点击的时序（QQ 9.3.75 实测）

```
16:56:29.789  主进程 12166  [MiniSDK]MiniAppLauncher: getLaunchParam1036
16:56:29.860  主进程 12166  QPublicTransFragmentActivity + PreloadingFragment
16:56:29.977  主进程 12166  START u0 {cmp=.../.mini.appbrand.ui.AppBrandUI (has extras)}
16:56:29.997  :mini  17233  Start proc com.tencent.mobileqq:mini
```

两条硬约束（来源：SPEC §6.1、docs/HOOKS.md §1）：

| 观测 | 架构后果 |
|---|---|
| 启动决策在主进程完成，容器进程晚 ~200ms 才起 | **不做进程名过滤**，全部进程都装 hook；只装 `:mini` 必然来不及 |
| 真正能同时拿到 `link` + 完整 `LaunchParam` 的是主进程里的 P0 | 阻断必须在 P0/P1 完成；P2 只能兜底，且允许一次闪屏 |

### 1.2 系统的输入与输出

| 方向 | 唯一入口 / 出口 |
|---|---|
| 入 | 三个 hook 点（P0 `MiniAppController#startAppByLink` / P1 `MiniAppLauncher#openMiniApp` / P2 `AppBrandUI#onCreate`） |
| 出 | ① `Context.startActivity`（跳原生 App）；② `chain.proceed()`（放行原小程序） |

除这两个出口，系统**不得**产生任何第三方可观测副作用（不联网、不写 QQ 数据、不碰风控）。

---

## 2. 分层与契约

```
① Hook 层 ──MiniAppEvent(纯数据)──▶ ② 提取层 ──▶ ③ 规则引擎 ──Decision──▶ ④ 执行层 ──▶ ⑤ 阻断/放行
                                                                              ▲
                                                            ⑥ 存储层（配置 + 命中日志）
                                                            ⑦ 日志层（debug 才输出）
```

**第一原则（ADR-0003）：`MiniAppEvent` 是唯一层间契约。** Hook 层不知道规则，规则层不知道 QQ 类。换 hook 后端（Zygisk / 内核）只重写 ①。

### 2.1 契约清单（全部定义在 `:module-api`）

| 类型 | 生产者 | 消费者 | 说明 |
|---|---|---|---|
| `MiniAppEvent` | hook 后端（`EventBuilder`） | `Pipeline` → `RuleEngine` | 唯一层间数据，见 §2.2 |
| `Decision` | `RuleEngine` | `TargetExecutor` | 放行 or 重定向 |
| `Target` / `TargetSpec` | `RuleEngine`（由规则 JSON 解析而来） | `TargetExecutor` | 降级链单项 |
| `ExecResult` | `TargetExecutor` | `Pipeline` | 成功/失败 + 命中的 target 类型 |
| `Config` | `ConfigRepository` | `Pipeline` / `RuleEngine` | 总开关 + 规则 JSON 原文 |
| `LogEntry` | `Pipeline` | `LogRepository` | 一条命中记录 |
| `Pipeline.handle` | `:app` 实现 | hook 后端调用 | 返回 `true` = 请阻断 |
| `HookBackend.install` | `:app` 实现 | `:module-hook-lsposed` | 把事件接到 Pipeline |
| `RuleEngine` / `TargetExecutor` / `ConfigRepository` / `LogRepository` | 各自模块实现 | `:app` 装配 | 接口在此，实现在外 |

`:module-api` **零依赖**（不含 Android、不含 QQ 类型、不含 JSON 库），因此 `:module-rules` 可以是纯 JVM 模块，规则逻辑全部可 JUnit 覆盖。

### 2.2 MiniAppEvent

```kotlin
enum class Source { CONTROLLER, LAUNCHER, CONTAINER }

data class MiniAppEvent(
    val source: Source,
    val processName: String,
    val miniAppId: String? = null,   // LaunchParam.miniAppId，如 1109937557
    val name: String? = null,        // MiniAppInfo.name，如 哔哩哔哩
    val entryPath: String? = null,   // pages/video/video?bvid=BV...
    val fakeUrl: String? = null,     // LaunchParam.fakeUrl（m.q.qq.com/a/... 或原链接）
    val link: String? = null,        // startAppByLink 的 link 参数
    val linkType: Int? = null,
    val scene: Int? = null,          // Ark 卡片 = 1036
    val via: String? = null,
    val arkApp: String? = null,      // Ark 卡片 app 名，如 com.tencent.miniapp_01
    val meta: String? = null,        // Ark 卡片 meta JSON 原文（规则 extract 的 meta: 源）
    val rawExtras: Map<String, String> = emptyMap()  // AppBrandUI.onCreate 的 Intent extras（兜底）
)
```

**字段来源**

| 字段 | 来源 |
|---|---|
| `miniAppId` / `entryPath` / `fakeUrl` / `scene` / `via` | `LaunchParam` 反射读字段（`Field` 静态缓存） |
| `name` | `MiniAppInfo` 反射读字段 |
| `link` / `linkType` | `startAppByLink` 实参 |
| `arkApp` / `meta` / `rawExtras` | Ark 卡片 `Intent` extras + `meta` JSON |

**三条不变式**

1. 不可变、可空字段一律 `null` 而非空串（空串会让正则误命中）。
2. 不含任何 `com.tencent.*`、不含 `android.*`。
3. Java 侧要能顺畅构造 → `:module-api` 内提供 **Java 风格的 `Builder`**（SPEC §6.3 的 hook 函数体写法依赖它）。

### 2.3 Decision / Target / ExecResult

```kotlin
sealed interface Decision {
    data object Passthrough : Decision
    data class Redirect(
        val ruleId: String,
        val vars: Map<String, String>,      // bvid / aid / ep ...
        val targets: List<TargetSpec>       // 已按规则顺序排好，末项通常 passthrough
    ) : Decision
}

/** 降级链的一项；JSON 里的 "when" 在 Kotlin 侧叫 whenVar（when 是关键字） */
data class TargetSpec(
    val whenVar: String?,   // 非 null 时：该变量存在才尝试
    val action: Target
)

sealed interface Target {
    data class Http(val tpl: String) : Target                // ACTION_VIEW + https，不设 package
    data class Deeplink(val pkg: String, val tpl: String) : Target  // ACTION_VIEW + scheme + setPackage
    data class Package(val pkg: String) : Target             // 显式 MAIN/LAUNCHER + setPackage
    data object Passthrough : Target                         // 永不成功，用于触发放行
}

data class ExecResult(
    val success: Boolean,
    val type: String?,      // http / deeplink / package / passthrough
    val detail: String?     // 失败原因：ActivityNotFound / 包未安装 / ...
)
```

### 2.4 Pipeline

```kotlin
fun interface Pipeline {
    /** @return true = 已接管，hook 应阻断原方法；false = 放行 */
    fun handle(event: MiniAppEvent): Boolean
}
```

**唯一实现在 `:app`**（`com.handoff.PipelineImpl`）。理由：它是唯一同时看得见 rules / executor / storage 的地方；而具体算法（护栏、匹配、提取、降级链）全部下沉到 `:module-rules` 与 `:module-executor`，`:app` 只做**顺序拼接 + 开关判断**，不含业务规则。

```java
// 伪代码：handle 的完整逻辑，一个屏内看得完
public boolean handle(MiniAppEvent ev) {
    try {
        Config cfg = configRepo.load();                     // 内存快照，变更时重建
        if (!cfg.getEnabled()) return false;                // ① 总开关
        Decision d = engine.decide(ev, cfg);                // ② 护栏 + 匹配 + 提取（纯内存）
        if (d instanceof Decision.Passthrough) { log(ev, "pass"); return false; }
        ExecResult r = executor.run((Decision.Redirect) d);  // ③ 降级链，首个成功即停
        log(ev, r);
        return r.getSuccess();                              // ④ 成功才阻断
    } catch (Throwable t) {
        log(ev, "error:" + t);                              // ⑤ 任何异常 → 放行
        return false;
    }
}
```

顺序与 SPEC §6.3 完全一致：

```
保护名单命中?        → false（放行）
config.enabled 关闭? → false
去重窗口命中?        → false
scene != 1036?      → false
规则匹配?           → 无 → false
提取变量 → 解析 targets（降级链）
executor.run(...)   → 成功才返回 true
```

---

## 3. 模块地图

| # | Gradle 模块 | 目录 | 语言 | 依赖 | namespace | 对外接口 | 可单测 |
|---|---|---|---|---|---|---|---|
| 1 | `:module-api` | `module-api/` | Kotlin | **零依赖** | `com.handoff.api` | §2.1 全部契约 | ✅ 纯 JVM |
| 2 | `:module-rules` | `module-rules/` | Kotlin | `api` | `com.handoff.rules` | `RuleEngine` 实现 + `RuleParser` | ✅ **纯 JVM** |
| 3 | `:module-executor` | `module-executor/` | Kotlin | `api` | `com.handoff.executor` | `TargetExecutor` 实现 | ⚠️ 需 Context |
| 4 | `:module-hook-lsposed` | `module-hook-lsposed/` | **Java** | `api` | `com.handoff.hook` | `HookBackend` 实现 | ❌ 需真机 |
| 5 | `:module-storage` | `module-storage/` | Kotlin | `api` | `com.handoff.storage` | `ConfigRepository` / `LogRepository` 实现 | ✅ fake 测 |
| 6 | `:module-ui` | `module-ui/` | Kotlin | `api` | `com.handoff.ui` | 设置页 Activity | ✅ fake 测 |
| 7 | `:app` | `app/` | Java | 全部 | `com.handoff` | 唯一出 APK + `META-INF/xposed` | — |

### 3.1 依赖图

```
                    :app  ←─ 唯一的 Composition Root
         ┌───────┬────┴────┬──────────┬─────────┐
      :ui   :storage  :hook-lsposed  :executor  :rules
         └───────┴────┬────┴──────────┴─────────┘
                  :module-api   （零依赖，纯契约）
```

- 五个功能模块**互不依赖**，全部只指向 `:module-api`，由 `:app` 装配。
- 不开 `build-logic` 模块；约定插件用 `gradle/*.gradle.kts` script plugin。

### 3.2 包结构

```
module-api/src/main/kotlin/com/handoff/api/
├── MiniAppEvent.kt       事件 + Source 枚举 + Java Builder
├── Decision.kt           Passthrough / Redirect
├── Target.kt             Target / TargetSpec / ExecResult
├── Config.kt             enabled / debug / rulesJson / version
├── LogEntry.kt           单条命中记录
├── Pipeline.kt           唯一出口契约
├── HookBackend.kt        install(cl, processName, sink)
├── RuleEngine.kt         decide(event, config)
├── TargetExecutor.kt     run(redirect)
├── ConfigRepository.kt   load / save / observe
└── LogRepository.kt      append / tail / clear
```

```
module-rules/src/main/kotlin/com/handoff/rules/
├── Rule.kt            规则模型（match / extract / targets）
├── RuleSet.kt         有序规则集（按 order 升序）
├── RuleParser.kt      rulesJson → RuleSet，错误带行号/字段定位
├── Guard.kt           保护名单 + scene 过滤 + 去重（时钟注入）
├── Matcher.kt         AND / OR 语义，首个命中即用
├── Extractor.kt       源前缀（entryPath:/meta:/link:）+ 首个命中 + 第 1 捕获组
├── Templates.kt       {var} 填充，缺变量 → 该 target 跳过
└── RuleEngineImpl.kt  Guard → Matcher → Extractor → Decision
```

```
module-executor/src/main/kotlin/com/handoff/executor/
├── AndroidTargetExecutor.kt  降级链：顺序执行，首个成功即停
├── HttpTarget.kt             ACTION_VIEW + https，**不设 package**，交给系统 App Links
├── DeeplinkTarget.kt         ACTION_VIEW + 自定义 scheme + setPackage
├── PackageTarget.kt          显式 MAIN/LAUNCHER + setPackage（不用查询类 API）
└── ContextProvider.kt        Application 获取与缓存（见 §4.2）
```

```
module-hook-lsposed/src/main/java/com/handoff/hook/
├── LsposedBackend.java   HookBackend 实现；每个 hook 点独立 try
├── HookTargets.java      候选表：类名 + 「名字 + 参数个数 + 参数可赋值性」
├── Reflect.java          Class.forName(name,false,cl) / Field+Method 查找与静态缓存
├── EventBuilder.java     QQ 类型 → MiniAppEvent
├── ControllerHook.java   P0 主攻
├── LauncherHook.java     P1 补充
└── ContainerHook.java    P2 兜底
```

```
module-storage/src/main/kotlin/com/handoff/storage/
├── RemoteConfigRepository.kt   getRemotePreferences("handoff_config") + 变更监听
├── RemoteLogRepository.kt      Remote Files handoff.log（App 侧写）；M1 不用
├── ConfigCodec.kt             Config ↔ JSON 字符串
└── LogEntryCodec.kt           单行文本 ↔ LogEntry
```

```
module-ui/src/main/kotlin/com/handoff/ui/
├── MainActivity.kt       总开关 / 模块状态 / LSPosed 作用域提示
├── RulesActivity.kt      规则列表 + JSON 编辑 + 导入导出
├── LogsActivity.kt       tail(200) + 「从日志生成规则」
├── SettingsActivity.kt   调试日志开关 / 关于
└── UiState.kt            StateFlow 形态的界面状态（只依赖 api 接口）
```

```
app/src/main/
├── AndroidManifest.xml
├── resources/META-INF/xposed/
│   ├── java_init.list        → com.handoff.ModuleMain
│   ├── module.prop           → minApiVersion=101 / targetApiVersion=102 / staticScope=false / autoHotReload=true
│   └── scope.list            → com.tencent.mobileqq
├── res/                      启动图标、主题、<queries>
└── java/com/handoff/
    ├── ModuleMain.java       XposedModule 入口：装配 + onPackageReady 安装
    ├── Assembly.java         组装各模块实现（唯一 new 出实现类的地方）
    └── PipelineImpl.java     Pipeline 实现（§2.4）
```

---

## 4. 装配与生命周期

### 4.1 入口

```java
public final class ModuleMain extends XposedModule {
    private Pipeline pipeline;

    @Override public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        pipeline = Assembly.create(this);            // 每进程一次：读配置、建 RuleSet、挂变更监听
    }

    @Override public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.getPackageName().startsWith("com.tencent.mobileqq")) return;
        new LsposedBackend(this)
            .install(param.getClassLoader(), Process.myProcessName(), pipeline::handle);
    }
}
```

- **不做进程名过滤**：QQ 进程众多（`:mini` / `:mini1~5` / `:miniapp` / `:wxa_container0~4` …），漏一个就失效。
- 每个 hook 点**独立 try**，任一失败不影响其它点。
- 不 `deoptimize`；保留调试开关（SPEC §6.6）。

### 4.2 Context 从哪来

`:module-api` 不含 Android 类型，因此 `MiniAppEvent` 不携带 `Context`。执行层需要 `Context` 时统一走 `ContextProvider`：

1. 首选 `ActivityThread.currentApplication()`（反射，一次成功后缓存）；
2. 拿不到就放弃本次执行 → 返回失败 → `chain.proceed()`（放行）。

执行上下文统一 `Application` + `FLAG_ACTIVITY_NEW_TASK`（hook 回调不一定发生在有 Activity 的线程上，SPEC §8）。

### 4.3 线程模型

| 环节 | 线程约束 |
|---|---|
| hook 回调 | 任意线程（主进程主线程 / `:mini` 启动流程线程）→ 不得假设 Looper |
| `Pipeline.handle` | **同步、纯内存**：无文件 IO、无网络、无阻塞锁 |
| `engine.decide` | 同上；规则集与去重表都是内存快照 |
| `executor.run` | 只做 `startActivity` + 包可见性查询（缓存） |
| 日志写入 | 唯一 IO：单写者队列，**满了丢最旧**，绝不阻塞决策路径 |

---

## 5. 规则引擎内部（`:module-rules`）

职责边界：**纯粹的「事件 + 规则 → 决策」函数**，无 Android、无 IO、**无全局可变状态**。唯一的状态是 `RuleEngineImpl` 持有的去重窗口（实例级、非全局，时钟由构造注入以便单测；容量 64 条 LRU，见 §13）。

```
decide(event, config):
  ① Guard.protected(event)      → 命中 支付/登录/实名/充值/qqpay/upay → Passthrough（高于一切）
  ② Guard.scene(event)          → scene != 1036 → Passthrough
  ③ Guard.dedup(event)          → (miniAppId+entryPath) 5s 内重复 → Passthrough
  ④ Matcher.first(event, rules) → 按 order 升序，首个命中；无 → Passthrough
  ⑤ Extractor.run(rule, event)  → vars（每个变量按正则顺序试，首个命中即用）
  ⑥ Templates.expand(rule, vars)      → targets（变量缺失的 target 跳过，末项固定 passthrough）
  → Redirect(rule.id, vars, targets)
```

配套单测即 docs/TESTING.md §2 的十条用例，一一对应：

| 用例 | 归属类 |
|---|---|
| appId 精确命中 / 名称兜底 / 场景不符 / 保护名单 | `MatcherTest`、`GuardTest` |
| 提取 bvid / aid / 提取失败 | `ExtractorTest` |
| 模板填充 | `TemplatesTest` |
| 降级链 / 全失败 | `TargetChainTest`（纯 JVM 选链）+ `AndroidTargetExecutor` 的 Robolectric 测 |

> JSON 解析用平台自带的 `org.json`：模块以 `compileOnly` 声明、单测以 `testImplementation` 补齐，避免 Android 侧打包出重复类。

---

## 6. 执行层（`:module-executor`）

| type | Intent 构造 | 成功判定 | 失败条件 |
|---|---|---|---|
| `http` | `ACTION_VIEW` + `Uri.parse(tpl 填充后)`，**不 `setPackage`** | `startActivity` 未抛异常 | `ActivityNotFoundException` |
| `deeplink` | `ACTION_VIEW` + scheme + `setPackage(pkg)` | 同上 | 包未安装 / 无 Activity |
| `package` | `ACTION_MAIN` + `CATEGORY_LAUNCHER` + `setPackage(pkg)`，直接 `startActivity` | 未抛异常 | `ActivityNotFoundException` |
| `passthrough` | 不动作 | 恒 false | — |

- `targets` 顺序执行，**首个成功即停**；最后一项固定 `passthrough`（除非用户显式配置「绝不降级」）。
- **发起方是 QQ 进程**：hook 回调里调用的 `startActivity` 用的是 QQ 的 Application Context 与 QQ 的 uid —— `system_server` 与目标 App 看到的调用方都是 `com.tencent.mobileqq`。Handoff 自己的 App 进程不参与跳转。
- **包可见性（Android 11+）按调用方算**，也就是按 **QQ 的 manifest** 算，模块改不了：
  - `http` / `deeplink` 不受影响（启动 Intent 不经可见性过滤）；
  - `package` 型**不要**用 `getLaunchIntentForPackage` / `resolveActivity` 判定可用性——查询类 API 会被过滤，目标不可见即返回 null，导致误判"未安装"并静默降级；改为显式 `setPackage` + `startActivity`，用 `ActivityNotFoundException` 兜底；
  - Handoff manifest 里的 `<queries>` 只服务设置页的"是否已安装"展示，**不影响跳转**；不使用 `QUERY_ALL_PACKAGES`。
  - 待 M1/M2 真机确认 QQ 自身是否已声明宽泛可见性（`dumpsys package com.tencent.mobileqq` 可查），即便有，代码也不依赖它。
- `http` 优先于 `deeplink` 是刻意的：B 站 App Links 已 verified（SPEC §3），走 https 不需要猜 `bilibili://` 的路径格式。

---

## 7. 存储层（`:module-storage`）

| 数据 | 通道 | 键 / 路径 | 说明 |
|---|---|---|---|
| 配置 | **RemotePreferences** | `handoff_config` | 设置页写、hook 进程读（**目标进程侧只读**），有变更监听，改完不重启 QQ |
| 命中日志 M1/M2 | **Xposed log** | `log(priority, "Handoff", line)` | 目标进程内 `openRemoteFile()` 只读，`log()` 是唯一能把数据带出 QQ 进程的官方通道 |
| 命中日志 M3 起 | 广播回传 → 模块 App 落盘 `handoff.log`（Remote Files） | App 侧 `XposedService.openRemoteFile()` 可写 | 待 M3 决策（§13 #2） |

```
QQ 进程（主进程 / :mini 等）                模块 App 进程（设置页）
  Hook 命中 → Pipeline → 提取/决策/执行
     ↓
  log(priority, "Handoff", line) ──▶ LSPosed 日志   ← M1/M2 唯一出口
     ↑
  ConfigRepository.load() ◀──RemotePreferences 变更监听──  ConfigRepository.save()
                                        （App 侧经 XposedService 写入）

  广播回传（M3 待定）───▶ 模块 App 落盘 handoff.log
```

- **热更新链路**：设置页 `save()` → RemotePreferences 变更 → hook 进程监听器回调 → 重建 `RuleSet` 快照（原子替换引用）→ 下一次点击即生效。
- **日志行格式**（单行文本，M1 直接进 Xposed 日志，便于肉眼与 `grep`）：

  ```
  Handoff | 2026-10-27 18:20:31.412 | com.tencent.mobileqq | CONTROLLER | 1109937557 | 哔哩哔哩 | 1036 | pages/video/video?bvid=BV1xx | bilibili | bvid=BV1xx | http | OK
  ```
  字段顺序：时间 / 进程 / source / miniAppId / name / scene / entryPath / ruleId / vars / targetType / 结果。
- **日志静默**：`debug=false` 时不调用 `log()`（QQ 有 `liblogcathook.so`，虽然 Xposed log 不走 logcat，仍保持静默）。
- **不做「进程内 append 文件」**：目标进程写不了 Remote Files，环形缓冲只在内存里（最近 64 条，供调试时 dump）。

---

## 8. UI 层（`:module-ui`）

| 页面 | 内容 |
|---|---|
| `MainActivity` | 总开关、模块是否被 LSPosed 激活、作用域提示、跳转规则/日志 |
| `RulesActivity` | 规则列表（启用/排序/编辑 JSON）、导入导出、语法校验错误定位 |
| `LogsActivity` | 最近 200 条命中，点一条 → **「从日志生成规则」**自动填 `match` 段 |
| `SettingsActivity` | 调试日志开关、版本、关于 |

**边界（SPEC §9.2）：**

- `:module-ui` **不得出现** `getRemotePreferences` / JSON 序列化 / 文件 IO / `com.tencent.*`，只依赖 `ConfigRepository` / `LogRepository` 接口。
- `:module-storage` **不得出现** 任何 `Activity` / `View` / 资源引用，只管存取。
- 设置页进程通过 libxposed 的 `service` 构件（`io.github.libxposed:service`）绑定 LSPosed 服务，拿到与 QQ 进程同名的 `handoff_config` 与 `handoff.log`；具体绑定类以 API 102 文档为准，M3 落地时确认。

---

## 9. Hook 后端（`:module-hook-lsposed`，Java）

细节真源是 [`docs/HOOKS.md`](docs/HOOKS.md)，本文只固定结构。

| 类 | 职责 |
|---|---|
| `HookTargets` | 候选表：每个 hook 点登记多组「类名 + 方法名 + 参数个数 + 参数可赋值性」，按序探测命中即用 |
| `Reflect` | `Class.forName(name, false, cl)`；`getDeclaredField` + `setAccessible` + `Field` 静态缓存；方法按特征匹配，不用 `getDeclaredMethod(精确类型)` |
| `EventBuilder` | 按 `instanceof` 认参（`Context` / `String` / `Integer` / `*LaunchParam`），反射读字段，产出 `MiniAppEvent` |
| `ControllerHook` / `LauncherHook` / `ContainerHook` | 三个点的注册与阻断语义 |

**阻断语义**（SPEC §6.5）：

| 点 | 返回类型 | 阻断做法 |
|---|---|---|
| P0 | `void` | 不调 `proceed()`，`return null` |
| P1 | `boolean` | 不调 `proceed()`，`return false` |
| P2 | `void`(onCreate) | **先 `proceed()`**，再 `finish()` + 拉起外部 |

**异常矩阵**（SPEC §6.7）：类/方法签名变化 → 该点跳过；字段读不到 → null → 规则多半不中 → 放行；规则引擎异常 → 捕获 → 放行；拉起失败 → 记日志 → 放行；被 QQ 重新拉起 → 5s 去重挡住；模块整体失效 → 等同未安装。

---

## 10. 构建与产物

```
Handoff/
├── settings.gradle.kts          include 7 个模块
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml       agp / kotlin / libxposed 版本集中
│   ├── android-module.gradle.kts   script plugin：compileSdk 37 / minSdk 26 / Java+Kotlin 21
│   ├── jvm-module.gradle.kts       script plugin：纯 JVM（:module-api / :module-rules）
│   └── wrapper/
├── module-api/  module-rules/  module-executor/
├── module-hook-lsposed/  module-storage/  module-ui/
└── app/
```

| 项 | 值 |
|---|---|
| AGP / Gradle | 9.2.1 / 9.5.1（wrapper） |
| compileSdk / buildTools | 37 / 37.0.0（本机缺，需 `sdkmanager` 安装） |
| minSdk / target | 26 / Java·Kotlin 21 |
| libxposed | `io.github.libxposed:api:102.0.0`（compileOnly）+ `io.github.libxposed:service` |
| JDK | 21（当前 `JAVA_HOME` 指向 Minecraft 的 JRE，必须改） |
| 打包 | `packaging { resources { merges += "META-INF/xposed/*" } }`（不加则模块不出现） |

产物：`app/build/outputs/apk/release/`。安装后 LSPosed 里勾选 `com.tencent.mobileqq` 并强停 QQ。

---

## 11. M1 剪裁（探针版只启用 3 个模块）

M1 的目标是**把 §3.1 的三个未知量测出来**，不是跳转。所以规则与执行**不参与**：

| 模块 | M1 状态 | 说明 |
|---|---|---|
| `:module-api` | ✅ 完整 | 契约必须先定，否则 hook 层无处落事件 |
| `:module-hook-lsposed` | ✅ 完整 | 三个点全装，只读不阻断 |
| `:app` | ✅ | `PipelineImpl` 恒返回 `false`，但把事件全量 `log()` 出去 |
| `:module-storage` | 🚧 空桩 | M1 不需要：配置用编译期常量，日志走 Xposed log |
| `:module-rules` | 🚧 空桩 | 只有包结构，`RuleEngine` 未接 |
| `:module-executor` | 🚧 空桩 | 不 `startActivity` |
| `:module-ui` | 🚧 空桩 | 日志用 LSPosed 管理器看 |

**为什么必须这样切**：`miniAppId` 是否真是 `1109937557`、`entryPath` 的实际形态、`startAppByLink` 是否真被调用——这三件事决定 M2 的提取正则能否写对。先跳转再验证，等于拿猜的正则去猜 URL。

验收（docs/TESTING.md §3）：点一次卡片 → LSPosed 日志里看到真实 `miniAppId` / `entryPath` / `scene`。

---

## 12. 质量红线（评审 checklist）

- [ ] 下游四个模块（`:module-rules` / `:module-executor` / `:module-storage` / `:module-ui`）**出现 `com.tencent` 一律视为设计缺陷**（ADR-0003）。
- [ ] `:module-api` / `:module-rules` 不出现 `android.*` 导入（保证纯 JVM 可测）。
- [ ] 每条 hook 函数体都在 `try/catch(Throwable)` 内，且异常分支必然走到 `chain.proceed()`。
- [ ] `Pipeline.handle` 里没有文件 IO / 网络 / `Thread.sleep`。
- [ ] `debug=false` 时无任何 logcat 调用。
- [ ] 目标 App 的可用性判定不得依赖查询类 API（`getLaunchIntentForPackage` / `resolveActivity`）——它们按调用方（QQ）的可见性过滤。
- [ ] 新增目标 App 时，设置页用 `<queries>` 名单同步更新（只影响「是否已安装」展示）。
- [ ] 新增 hook 点必须同时登记候选表与 `docs/HOOKS.md`。

---

## 13. 待确认 / 未决

| # | 项 | 影响 | 何时定 |
|---|---|---|---|
| 1 | `startAppByLink` 运行时是否真被调用、`miniAppId` 运行时值、`entryPath` 形态（SPEC §3.1） | 决定 P0 是否为唯一主攻点、提取正则怎么写 | **M1 探针** |
| 2 | M3 命中日志回传方案：广播回传 App 落盘 vs 只保留 Xposed 日志 | 决定设置页能否列历史 | M3 开工前 |
| 3 | 包可见性实际行为：QQ 自身是否声明了宽泛可见性（`dumpsys package com.tencent.mobileqq`） | 决定 `package` 型 target 有多可靠 | M1 真机顺手看一眼 |
| 4 | 去重表容量与淘汰（建议 64 条 LRU，5s 窗口） | 长时间挂着是否涨内存 | M2 |
| 5 | UI 技术栈：View + Material3 还是 Compose | M3 工作量 | M3 开工前 |

> 已解决（2026-10-27）：日志物理路径三处不一致 → 统一为「代码只认逻辑名 `handoff.log`，物理路径由 LSPosed 决定」，
> 见 SPEC §1.1 / §9.3 与 docs/TESTING.md §3（取日志改用通配定位）。

---

## 14. 与上一版（MiniJump v0.1）的差异

| 维度 | v0.1（旧，已作废） | v0.2（本文，与 SPEC 对齐） |
|---|---|---|
| 项目名 / 包名 | `MiniJump` / `com.minijump` | **Handoff** / `com.handoff`，applicationId `com.handoff.module` |
| 工程结构 | 单 `app/` 扁平目录 | **7 个 Gradle 模块** + `:app` 装配 |
| hook 层语言 | Kotlin | **Java**（其余 Kotlin） |
| 降级链顺序 | `DeepLink → Http → Package` | **`http → deeplink → package → passthrough`** |
| 配置键 | `getRemotePreferences("config")` | `handoff_config` |
| 日志通道 | RingLog（进程内环形缓冲） | **Remote Files `handoff.log`** + `tail(200)` |
| 层边界 | 「不 import 规则/执行器类型」的口头约定 | ADR-0003 落为可评审红线（§12） |
| 规则 JSON | 内嵌示例 | `docs/RULES.md` 为对外契约（ADR-0002） |
| 里程碑颗粒度 | M1 一句话 | M1 明确到「哪几个模块启用、哪些是空桩」（§11） |

---

## 15. 本文补充项的回写状态

以下 7 条原本是「SPEC 未写死、但实现必须定」的点，已于 2026-10-27 **全部回写 SPEC**（SPEC 升为 v0.2.0-draft）。保留本表作溯源：若 SPEC 里再次找不到某条，说明被误删。

| # | 补充 | 已写入 |
|---|---|---|
| 1 | `Config` 字段：`enabled` / `debug` / `rulesJson` / `version` | SPEC §9.1 |
| 2 | `Pipeline` 实现位置：`PipelineImpl` 在 `:app`；护栏/匹配/提取/降级链在 `:module-rules` | SPEC §5 表 + §5.1 装配约定 |
| 3 | `MiniAppEvent` 补 `name` / `arkApp` / `meta` / `rawExtras`（`meta:` 正则源必需） | SPEC **§4.3**（新增字段表）+ §6.3 builder |
| 4 | `when` → Kotlin `whenVar`；`TargetSpec` 与 `Target` 拆开 | SPEC §7.1 表示层约定 |
| 5 | Context 获取策略：`ActivityThread.currentApplication()` + 缓存，失败即放行 | SPEC §8 |
| 6 | `org.json` 按 `compileOnly` + `testImplementation` 引入 | SPEC §11 表 |
| 7 | 包可见性归属（按 QQ 的 manifest 算）与 `package` 型的判定口径 | SPEC §8 |

> 维护规则：改 SPEC 时若动到以上条目，必须同步回本文对应章节（§2 契约、§3 包结构、§5/§6 算法、§11 剪裁）。

---

## 16. API 102 事实核对（源码级，2026-10-27）

来源：`github.com/LibXposed/API`（javadoc 即源码）、`LibXposed/example`、`LibXposed/service`、`LSPosed.wiki`。

| 事实 | 影响 |
|---|---|
| 入口必须 `extends io.github.libxposed.api.XposedModule`，**公开无参构造**；框架 `attachFramework()` 后才可用 API | 构造函数里什么都别做；R8 要 keep `<init>()` |
| 生命周期：`onModuleLoaded(ModuleLoadedParam)` → `onPackageLoaded` → `onPackageReady(PackageReadyParam)` | `onPackageReady` 拿 `param.getClassLoader()`；`param.getProcessName()` 可用 |
| `hook(Executable)` 返回 `HookBuilder`：`setPriority` / `setExceptionMode` / `setId` / `intercept(Hooker)`→`HookHandle` | 我们的 P0/P1/P2 直接用 |
| `Chain`：`getArgs()` 是 `List<Object>`（不可变）、`getArg(i)`、`proceed()`、`proceed(Object[])`、`proceedWith(thisObj[, args])`、`getExecutable()` | 参数顺序不变，按 `instanceof` 认参仍成立 |
| void 方法 `return null`（框架忽略返回值）；boolean 直接返回目标值 | P0/P1 阻断语义不变 |
| `ExceptionMode.PROTECTIVE`（默认）：hooker 抛出时按"无此 hook"继续；可用 `module.prop` 的 `exceptionMode=protective` 设全局默认 | 与"任何异常必放行"对齐；仍保留显式 `try/catch` 以便记日志 |
| 没有 `XposedHelpers`；反射自备 | `Reflect.java` 计划不变 |
| `getInvoker(Method).setType(Invoker.Type.ORIGIN).invoke(thisObj, args)` 可绕过 hook 调原方法 | P2 `finish()` 场景可能需要 |
| `XposedInterface.openRemoteFile()` / `listRemoteFiles()` 在目标进程**只读**；可写版本只在 App 侧 `XposedService` | **推翻**"QQ 进程写 handoff.log"的原设计，见 §7 |
| `XposedService`（App 侧）：`getRemotePreferences(group)`（可写）、`openRemoteFile(name)`（可创建）、`getRunningTargets()`、`hotReloadModule()`、`requestScope()` | M3 设置页与热重载实现路径 |
| `service` AAR 自带 `<provider android:name=".XposedProvider" android:authorities="${applicationId}.XposedService">` | manifest 自动合并，无需手写 |
| 资源打包需 `packaging { resources { merges += "META-INF/xposed/*"; excludes += "**" } }` | 见 SPEC §11 |
| `module.prop` 支持 `minApiVersion` / `targetApiVersion` / `staticScope` / `autoHotReload` / `exceptionMode` | 见 SPEC §11.1 |
