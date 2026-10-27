# Handoff · 工程规格说明（SPEC）

> 本文件是**唯一的决策真源**。所有已确定的设计、命名、地址、已验证的事实都记录在此。
> 状态：设计定稿，待编码 · 版本：v0.1.0-draft · 最后更新：2026-10-26

---

## 0. 一句话

**Handoff** 是一个 LSPosed 模块。装在 QQ 里，把「好友分享的 Ark 卡片」点击后本应打开的内置小程序，**交接到对应的原生 App**（首期：哔哩哔哩）。

---

## 1. 工程基本信息

| 项 | 值 |
|---|---|
| 工程名 | **Handoff** |
| 工程根目录 | `C:\Users\user\Desktop\vibecoding\安卓项目\Handoff` |
| 版本控制 | **仅本地 git**，不建远程仓库 |
| 默认分支 | `main` |
| License | **Apache-2.0**（Copyright © 2026 Handoff Project Contributors） |
| 文档语言 | **中文为主**，术语中英对照 |
| 首个版本 | `v0.1.0`（M1 探针版） |

### 1.1 命名映射

| 位置 | 值 |
|---|---|
| Gradle root project | `Handoff` |
| applicationId | `com.handoff.module` |
| namespace（代码包） | `com.handoff` |
| LSPosed 显示名（`android:label`） | `Handoff` |
| 模块描述 | 小程序外跳 —— 把聊天卡片里的小程序交给原生 App 打开 |
| 配置 key 前缀 | `handoff_` |
| RemotePreferences 名 | `handoff_config` |
| 日志通道 | Remote Files：`handoff.log` |

---

## 2. 目标与非目标

### 2.1 目标

1. 点击 Ark 卡片 → **不打开 QQ 小程序**，直接进原生 App 对应内容。
2. 规则**数据化**：新增目标 App（抖音、快手、小红书…）不改代码。
3. 提取不到精确 ID 时按**降级链**兜底；最差也要放行原小程序，**绝不"点了没反应"**。
4. 规则**热更新**，改完不重启 QQ。

### 2.2 非目标

- 不拦截 QQ 内置浏览器（`QQBrowserActivity` 那套）——独立需求。
- 不改网络、不伪造设备指纹、不碰风控。
- **本期不做内核模块 / Zygisk 后端**（架构预留接口，见 §9）。

---

## 3. 事实基础（全部经真机与 APK 验证）

| 项 | 值 | 验证方式 |
|---|---|---|
| 包名 / 版本 | `com.tencent.mobileqq` / 9.3.75 (16580) | `aapt2 dump badging` |
| minSdk / targetSdk | 24 / 34 | 同上 |
| 容器 Activity | `com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI` | logcat `START u0` |
| 容器进程 | `:mini`（另有 `:mini1~5`、`:miniapp`、`:wxa_container0~4`） | logcat `Start proc` |
| 启动链 | `QPublicTransFragmentActivity` → `PreloadingFragment` → `AppBrandUI` | logcat |
| **场景码** | **1036**（Ark 卡片） | `[MiniSDK]MiniAppLauncher: getLaunchParam1036` |
| **B 站小程序 QQ appId** | **`1109937557`** | 包内 JS `"qq"===f?"1109937557"` |
| 交叉验证 | `MD5("1109937557") = 2421a9fa948857a957d7d5793dae165a` = 包目录名 | `files/mini/2421A9FA…` |
| 小程序名称 | 哔哩哔哩（`mossPkgName: bilibili`） | 包内 `app-config.json` |
| 视频页路径 | `pages/video/video?bvid=BVxxx` ｜ `?aid=xxx` ｜ `pages/pgcvideo/pgcvideo?ep_id=xxx` | 包内分享逻辑 |
| B 站 App | `tv.danmaku.bili`，App Links 已验证 `bilibili.com` / `m.bilibili.com` / `b23.tv` | `dumpsys package` |

**关键推论**：B 站 App 的 App Links 是系统级 verified 的 → `ACTION_VIEW` 一个 `https://www.bilibili.com/video/BVxxx` 就会被系统直接交给 B 站 App，**不需要猜 `bilibili://` 的路径格式**。

### 3.1 待 M1 探针确认

1. `MiniAppController#startAppByLink` 运行时是否真被调用。
2. `LaunchParam.miniAppId` 运行时值是否为 `1109937557`。
3. `LaunchParam.entryPath` 的实际形态。

---

## 4. 架构

### 4.1 分层

```
① Hook 层 ──MiniAppEvent(纯数据)──▶ ② 提取层 ──▶ ③ 规则引擎 ──Decision──▶ ④ 执行层 ──▶ ⑤ 阻断/放行
                                                                                 ▲
                                                             ⑥ 存储层（配置 + 命中日志）
                                                             ⑦ 日志层（debug 才输出）
```

**第一原则：`MiniAppEvent` 是唯一层间契约。**
Hook 层不知道规则，规则层不知道 QQ 类。换 Hook 后端（Zygisk / 内核）只重写 ①，其余零改动。

### 4.2 三条铁律

1. **先执行、后阻断** —— 绝不先吞掉再想办法打开。
2. **任何异常 → `chain.proceed()`** —— 模块永远不能让 QQ 卡死或崩溃。
3. **规则不命中 → 完全透明** —— 装了跟没装一样。

---

## 5. 模块划分（7 个 Gradle 模块）

| # | 模块 | 语言 | 依赖 | 职责 | 可单测 |
|---|---|---|---|---|---|
| 1 | `:module-api` | Kotlin | **零依赖** | 契约层：数据类 + 全部接口 | ✅ |
| 2 | `:module-rules` | Kotlin | api | 匹配 / 提取 / 降级链（`Engine` 实现） | ✅ **纯 JVM** |
| 3 | `:module-executor` | Kotlin | api | Http / Deeplink / Package 三种执行器 | ⚠️ 需 Context |
| 4 | `:module-hook-lsposed` | **Java** | api | LSPosed 后端：hook 点、反射、LaunchParam 读取 | ❌ 需真机 |
| 5 | `:module-storage` | Kotlin | api | 配置 + 命中日志的跨进程持久化 | ✅ fake 测 |
| 6 | `:module-ui` | Kotlin | api | 设置页 / 规则编辑 / 日志查看 | ✅ fake 测 |
| 7 | `:app` | Java | 全部 | 唯一出 APK：装配 + `META-INF/xposed` 资源 | — |

### 5.1 依赖图

```
                    :app  ←─ 唯一的 Composition Root
         ┌───────┬────┴────┬──────────┬─────────┐
      :ui   :storage  :hook-lsposed  :executor  :rules
         └───────┴────┬────┴──────────┴─────────┘
                  :module-api   （零依赖，纯契约）
```

- 五个功能模块**互不依赖**，全部只指向 `:module-api`，由 `:app` 装配。
- 不单独开 `build-logic` 模块，约定插件用 `gradle/*.gradle.kts` script plugin 实现。

### 5.2 语言选择理由

- **Hook 层用 Java**：只做反射 + 框架调用，无业务逻辑；少一层 Kotlin 运行时依赖；libxposed 本身是 Java 风格的 `intercept` 链。
- **其余用 Kotlin**：`api` / `rules` 是纯 JVM 模块，可用 JUnit 直接测规则匹配、模板填充、降级链，不需要手机。

---

## 6. Hook 逻辑

### 6.1 进程分布（实测）

```
16:56:29.789  [MiniSDK]MiniAppLauncher(12166): getLaunchParam1036   ← 主进程
16:56:29.977  START u0 {cmp=.../.mini.appbrand.ui.AppBrandUI}
16:56:29.997  Start proc 17233:com.tencent.mobileqq:mini             ← 容器进程
```

**决策在主进程、容器在 `:mini`，间隔约 200ms。只 hook `:mini` 为时已晚 → 必须所有进程都装。**

### 6.2 Hook 点

| | 类·方法 | 静态 | 返回 | 装在哪 | 职责 |
|---|---|---|---|---|---|
| **P0** | `com.tencent.mobileqq.mini.sdk.MiniAppController#startAppByLink(Context, String, int, LaunchParam, MiniAppLaunchListener)` | ✓ | void | 全部进程 | **主攻**，同时拿到 link + linkType + 完整 LaunchParam |
| **P1** | `com.tencent.mobileqq.mini.sdk.MiniAppLauncher#openMiniApp(Context, LaunchParam, MiniAppLaunchListener)` | ✓ | boolean | 同上 | 私有收口，兜 P0 漏网 |
| **P2** | `com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI#onCreate(Bundle)` | — | void | `:mini*` / `:wxa_container*` | 最后一道闸 |

### 6.3 Hook 函数体算法（P0）

```java
hook(startAppByLink).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
    try {
        // ① 抗版本取参：不按下标，按类型认
        Context ctx = null; String link = null; Integer linkType = null; Object lp = null;
        for (Object arg : chain.getArgs()) {
            if (arg instanceof Context)  ctx = (Context) arg;
            else if (arg instanceof String)   link = (String) arg;
            else if (arg instanceof Integer)  linkType = (Integer) arg;
            else if (arg != null && arg.getClass().getName().endsWith("LaunchParam")) lp = arg;
        }

        // ② 组装中立事件
        MiniAppEvent ev = MiniAppEvent.builder()
            .source(Source.CONTROLLER).processName(Process.myProcessName())
            .miniAppId(Reflect.str(lp, "miniAppId"))
            .entryPath(Reflect.str(lp, "entryPath"))
            .fakeUrl(Reflect.str(lp, "fakeUrl"))
            .scene(Reflect.integer(lp, "scene"))
            .via(Reflect.str(lp, "via"))
            .link(link).linkType(linkType).build();

        // ③ 交给 Pipeline（规则 + 执行），true = 已处理，请阻断
        if (pipeline.handle(ev)) return null;   // 不调用 proceed → 原方法体不执行
    } catch (Throwable ignored) { /* 掉到下面放行 */ }
    return chain.proceed();
});
```

`pipeline.handle()` 内部顺序：

```
保护名单命中?        → false（放行）
config.enabled 关闭? → false
去重窗口命中?        → false
scene != 1036?      → false
规则匹配?            → 无 → false
提取变量 → 解析 targets（降级链）
executor.run(...)    → 成功才返回 true
```

### 6.4 三个抗版本技巧

1. **参数不按下标**：按 `instanceof` 类型认（`Context` / `String` / `Integer` / `*LaunchParam`）。
2. **字段反射读 + 缓存**：`LaunchParam` 的 `miniAppId / entryPath / fakeUrl / scene / via` 用 `getDeclaredField` + `setAccessible(true)`，`Field` 对象静态缓存；读不到时记日志，补映射即可。
3. **方法按"名字 + 参数个数 + 参数可赋值性"匹配**，不用 `getDeclaredMethod(精确类型…)`。

### 6.5 阻断语义

| 原方法 | 阻断做法 | 说明 |
|---|---|---|
| `void`（P0） | 不调 `proceed()`，`return null` | libxposed 官方：void 方法必须返回 null |
| `boolean`（P1） | 不调 `proceed()`，`return false` | 让 QQ 走自己的失败分支 |
| `void`（P2，onCreate） | **先 `proceed()`**，再 `activity.finish()` + 拉起外部 | 父类 onCreate 不执行会抛异常 → P2 允许闪屏 |

### 6.6 安装时机

```java
public final class ModuleMain extends XposedModule {
    @Override public void onPackageReady(PackageReadyParam param) {
        if (!param.getPackageName().startsWith("com.tencent.mobileqq")) return;
        backend.install(param.getClassLoader(), Process.myProcessName());
    }
}
```

- 每个 hook 点**独立 try**，任一失败不影响其它点。
- **不做进程名过滤**（否则漏 `:mini`）。
- 不做 `deoptimize`（QQ 自有类通常未被跨包内联）；保留调试开关。

### 6.7 异常矩阵

| 情况 | 行为 |
|---|---|
| 类/方法签名变化 | 该点跳过，其它点继续；QQ 无感 |
| 字段读取失败 | 字段为 null → 规则多半不中 → 放行 |
| 规则引擎抛异常 | 捕获 → 放行 |
| 拉起 App 失败 | 记日志 → 放行原小程序 |
| 被 QQ 重新拉起 | 5 秒去重窗口挡住 |
| 模块整体失效 | 全 try/catch，等同未安装 |

---

## 7. 规则模型

### 7.1 内置规则（B 站）

```jsonc
{
  "id": "bilibili",
  "label": "哔哩哔哩",
  "enabled": true,
  "order": 10,
  "match": {
    "miniAppId": ["1109937557"],
    "nameRegex": "(?i)哔哩哔哩|bilibili",
    "arkApp":    ["com.tencent.miniapp_01"],
    "scene":     [1036]
  },
  "extract": {
    "bvid": ["entryPath:[?&]bvid=([A-Za-z0-9]+)", "meta:\"bvid\"\\s*:\\s*\"([^\"]+)\""],
    "aid":  ["entryPath:[?&]aid=(\\d+)",          "meta:\"aid\"\\s*:\\s*(\\d+)"],
    "ep":   ["entryPath:[?&]ep_id=(\\d+)",        "meta:\"ep_id\"\\s*:\\s*(\\d+)"]
  },
  "targets": [
    { "when": "bvid", "type": "http",     "tpl": "https://www.bilibili.com/video/{bvid}" },
    { "when": "aid",  "type": "http",     "tpl": "https://www.bilibili.com/video/av{aid}" },
    { "when": "ep",   "type": "http",     "tpl": "https://www.bilibili.com/bangumi/play/ep{ep}" },
    { "type": "deeplink", "pkg": "tv.danmaku.bili", "tpl": "bilibili://" },
    { "type": "package",  "pkg": "tv.danmaku.bili" },
    { "type": "passthrough" }
  ]
}
```

### 7.2 匹配 / 提取语义

- 同一 rule 内条件 **AND**；数组内多值 **OR**；未填字段视为不限制。
- 多 rule 按 `order` 升序，**首个命中即用**。
- `extract` 每个变量是一组正则，**按顺序尝试，首个命中即用**。
- 正则前缀 `entryPath:` / `meta:` / `link:` 指定源；无前缀时依次扫全部源。
- 变量提取失败 → 跳过依赖它的 target。

### 7.3 扩展方式（抖音示例）

```jsonc
{ "id":"douyin", "label":"抖音",
  "match": { "miniAppId":["<待实测>"], "nameRegex":"(?i)抖音|douyin", "scene":[1036] },
  "extract": { "id": ["entryPath:[?&]aweme_id=(\\d+)"] },
  "targets": [
    { "when":"id","type":"http","tpl":"https://www.douyin.com/video/{id}" },
    { "type":"package","pkg":"com.ss.android.ugc.aweme" },
    { "type":"passthrough" } ] }
```

**加抖音 = 加一条 JSON，引擎代码零改动。**

---

## 8. 执行层与降级链

| type | 实现 | 失败条件 |
|---|---|---|
| `http` | `ACTION_VIEW` + https URL，**不指定 package**（交给系统 App Links） | `ActivityNotFound` |
| `deeplink` | `ACTION_VIEW` + 自定义 scheme + `setPackage(pkg)` | 包未安装 / 无 Activity |
| `package` | `getLaunchIntentForPackage(pkg)` | 返回 null |
| `passthrough` | 不动作，返回失败 → 触发 `chain.proceed()` | — |

- 执行上下文统一 `Application` + `FLAG_ACTIVITY_NEW_TASK`。
- `targets` 顺序执行，**首个成功即停**；最后一项固定 `passthrough`。

---

## 9. 存储与配置（UI 与存储严格隔离）

### 9.1 接口（定义在 `:module-api`）

```kotlin
interface ConfigRepository {
    fun load(): Config
    fun save(config: Config)
    fun observe(onChange: (Config) -> Unit)
}

interface LogRepository {
    fun append(entry: LogEntry)
    fun tail(limit: Int): List<LogEntry>
    fun clear()
}
```

### 9.2 边界

| 约束 | 说明 |
|---|---|
| `:module-ui` **不得出现** `getRemotePreferences` / JSON 序列化 / 文件 IO | 只依赖接口 |
| `:module-storage` **不得出现** 任何 `Activity` / `View` / 资源引用 | 只管存取 |

### 9.3 通道选型

| 数据 | 通道 | 理由 |
|---|---|---|
| 配置 | **RemotePreferences**（`handoff_config`） | 体小、有变更监听、热更新 |
| 命中日志 | **Remote Files**（`/data/adb/lspd/modules/<user>/<module>/handoff.log`） | 会频繁 `tail(200)`，塞 SharedPreferences 会撑爆 |

### 9.4 跨进程数据流

```
QQ 进程（:mini 等）                     模块 App 进程（设置页）
  Hook 命中 → Pipeline → 提取/决策/执行
     ↓
  LogRepository.append()  ──Remote Files──▶  LogRepository.tail()
     ↑                                          ↓
  ConfigRepository.load() ◀──变更监听──────  ConfigRepository.save()
```

---

## 10. 护栏

| 护栏 | 规则 | 优先级 |
|---|---|---|
| 保护名单 | `miniAppId`/`name` 命中 `支付/登录/实名/充值/qqpay/upay` → **强制 passthrough** | 高于一切规则 |
| 场景过滤 | 只处理 `scene == 1036` | 高 |
| 去重窗口 | `(miniAppId + entryPath)` 5 秒内只处理一次 | 高 |
| 循环防护 | 重定向后被拉回不重复触发 | 高 |
| 失败放行 | 任何异常 → `chain.proceed()` | 最高 |
| 日志静默 | `debug=false` 时**零 logcat 输出**（QQ 有 `liblogcathook.so`） | 高 |

---

## 11. 构建配置

| 项 | 值 | 备注 |
|---|---|---|
| AGP | 9.2.1 | 与 LSPosed 官方 example 一致 |
| Gradle | 9.5.1（wrapper） | 本机已装 9.4.1 |
| compileSdk | 37 | |
| buildToolsVersion | 37.0.0 | **本机缺，需 `sdkmanager` 安装** |
| minSdk | 26 | libxposed 要求 |
| Java / Kotlin target | 21 | AGP 9 默认 |
| libxposed | `io.github.libxposed:api:102.0.0`（compileOnly）+ `service` | Maven Central |
| JDK | 17 或 21 | 当前 `JAVA_HOME` 指向 Minecraft 的 JRE，**必须改** |
| 打包 | `packaging { resources { merges += "META-INF/xposed/*" } }` | 不加则模块不出现 |

### 11.1 `META-INF/xposed/`

```
java_init.list   →  com.handoff.ModuleMain
module.prop      →  minApiVersion=101
                    targetApiVersion=102
                    staticScope=false
                    autoHotReload=true
scope.list       →  com.tencent.mobileqq
```

---

## 12. 里程碑

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| **M1 探针** | 只 hook 不阻断，`RingLog` 记录全部事件 | 点一次卡片，日志里看到真实 `miniAppId` / `entryPath` / `scene` |
| **M2 单规则** | B 站规则 + 降级链 | 点卡片 → B 站 App 打开对应视频；失败时降级；全失败仍能进小程序 |
| **M3 设置页** | 开关 / 规则编辑 / 日志 / 导入导出 | 不重启 QQ 改规则生效 |
| **M4 扩展** | 抖音等 + 长时间稳定性 | 连续 100 次点击无崩溃 |

**M1 必须独立存在**，不能与 M2 合并——因为 §3.1 的三个未知量只能靠探针确认。

---

## 13. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| QQ 升级改类名/字段 | hook 失效 | 候选表 + 反射探测 + 日志定位 |
| 提取不到 bvid | 只能开 B 站首页 | 降级链第 4/5 档 |
| QQ 风控标记环境异常 | 登录需额外验证 | 不碰风控类；走合法路径；**无证据支持封号** |
| 阻断后无反应 | 体验崩 | 先执行后阻断 + 失败必放行 |
| 注入影响性能 | QQ 变卡 | hook ≤3 个方法；规则计算纯内存 |
| 环境可检测 | 无法完全避免 | 只 hook QQ 自有类；不碰 `turingfd`/`tfd`/`QSec`/`MSF`；正式版关闭日志 |

---

## 14. 已拍板的决策（速查）

| # | 决策 | 值 |
|---|---|---|
| 1 | 项目名 | **Handoff** |
| 2 | License | **Apache-2.0** |
| 3 | 语言 | **混合**：api/rules/storage/ui = Kotlin，hook/app = Java |
| 4 | 模块数 | **7** |
| 5 | 版本控制 | **仅本地 git** |
| 6 | UI 与存储 | **严格隔离**，只通过 `:module-api` 接口通信 |
| 7 | 日志通道 | **Remote Files**；配置走 RemotePreferences |
| 8 | build-logic | **不开**，用 script plugin |
| 9 | 文档语言 | 中文为主 |
| 10 | 内核 / Zygisk | **本期不做**，架构预留 `HookBackend` 接口 |
| 11 | 失败策略 | 任何异常/未命中 → **passthrough** |
