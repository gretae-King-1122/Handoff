# Handoff · 工程规格说明（SPEC）

> 本文件是**唯一的决策真源**。所有已确定的设计、命名、地址、已验证的事实都记录在此。
> 状态：设计定稿，待编码 · 版本：v0.2.0-draft · 最后更新：2026-10-27

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
| 日志通道 | Remote Files：逻辑名 `handoff.log`（物理路径由 LSPosed 决定，见 §9.3） |

---

## 2. 目标与非目标

### 2.1 目标

1. 点击 Ark 卡片 → **不打开 QQ 小程序**，直接进原生 App 对应内容。
2. 规则**数据化**：新增目标 App（抖音、快手、小红书…）不改代码。
3. 提取不到精确 ID 时按**降级链**兜底；最差也要放行原小程序，**绝不"点了没反应"**。
4. 规则**热更新**，改完不重启 QQ。

### 2.2 非目标

- 不改网络、不伪造设备指纹、不碰风控。
- **本期不做内核模块 / Zygisk 后端**（架构预留接口，见 §9）。

> 变更（2026-10-27 真机验证后）：原「不拦截 QQ 内置浏览器」已**撤销**。
> B 站 App 分享到 QQ 的卡片不走小程序，而是 `QQBrowserDelegationActivity → QQBrowserActivity`，
> 且 URL 明文可读 —— 这成了当前**唯一可用**的落地路径（见 §3.2）。

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
| B 站 App | `tv.danmaku.bili`：**声明**了 `http` / `https` + BROWSABLE 过滤器，但**本机未通过域名验证** —— 裸 `ACTION_VIEW https://www.bilibili.com/...` 会落到 `ResolverActivity` / 默认浏览器；必须 `setPackage("tv.danmaku.bili")` 才能真正进 App | `dumpsys package` + `cmd package resolve-activity` |

**关键推论**：B 站 App 的 App Links 是系统级 verified 的 → `ACTION_VIEW` 一个 `https://www.bilibili.com/video/BVxxx` 就会被系统直接交给 B 站 App，**不需要猜 `bilibili://` 的路径格式**。

### 3.1 M1 探针结论（2026-10-27 真机，QQ 9.3.75 / LSPosed 2.2.1）

1. `MiniAppController#startAppByLink` **确实被调用**，`scene` 稳定为 `1036`。
2. 但 `LaunchParam` 的 35 个字段里，`miniAppId` / `entryPath` **恒为 null**；有值的只有 `fakeUrl`（= `link`）与 `scene`。
3. 小程序卡片的链接形如 `https://m.q.qq.com/a/s/<32hex>?scene=1007`：服务端生成的哈希，既非 MD5(URL)，HTTP 抓取也只返回扫码引导页 —— **本地不可还原**。
4. 一次点击会触发 **2~3 次 P0**；容器 `:mini` 比 P0 晚约 5 秒启动。

### 3.2 三类卡片的实测落点

| 卡片类型 | 链接形态 | 落点 | 视频 ID 可得？ |
|---|---|---|---|
| 小程序分享卡片 | `m.q.qq.com/a/s/<hash>?scene=1007` | `MiniAppController#startAppByLink` → `AppBrandUI` | ❌ |
| **B 站 App 分享卡片** | `www.bilibili.com/video/av…`（明文） | `QQBrowserDelegationActivity`（主进程）→ `QQBrowserActivity`（`:tool`） | ✅ **当前唯一可用路径** |
| b23.tv 短链 | `b23.tv/<code>` | 同上 | ❌（需联网跳转，但可整包交给 B 站 App 自行解析） |

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

### 4.3 `MiniAppEvent` 字段（唯一层间契约）

| 字段 | 类型 | 来源 | 说明 |
|---|---|---|---|
| `source` | `CONTROLLER` / `LAUNCHER` / `CONTAINER` | 产生事件的 hook 点 | 日志里区分命中点 |
| `processName` | String | `Process.myProcessName()` | 判断决策发生在主进程还是 `:mini` |
| `miniAppId` | String? | `LaunchParam.miniAppId` | 规则匹配主键（B 站 `1109937557`） |
| `name` | String? | `MiniAppInfo.name` | `nameRegex` 兜底匹配用 |
| `entryPath` | String? | `LaunchParam.entryPath` | 提取 `bvid` / `aid` / `ep_id` |
| `fakeUrl` | String? | `LaunchParam.fakeUrl` | 原始链接（若有） |
| `link` / `linkType` | String? / Int? | `startAppByLink` 实参 | 仅 P0 有值 |
| `scene` | Int? | `LaunchParam.scene` | Ark 卡片 = `1036` |
| `via` | String? | `LaunchParam.via` | 来源标记，仅日志 |
| `arkApp` | String? | Ark 卡片 extras | `arkApp` 匹配条件用，通常 `com.tencent.miniapp_01` |
| `meta` | String? | Ark 卡片 meta JSON 原文 | `extract` 的 `meta:` 正则源 |
| `rawExtras` | `Map<String,String>?` | `AppBrandUI.onCreate(Intent)` extras | 兜底，仅 P2 有值 |

三条不变式：

1. 可空字段一律 `null`，**不用空串**（空串会让正则误命中）。
2. 不含任何 `com.tencent.*`，不含 `android.*`（否则 `:module-rules` 无法纯 JVM 测）。
3. `:module-api` 内提供 **Java 风格的 `Builder`** —— hook 层是 Java，§6.3 的写法依赖它。

> `arkApp` / `meta` / `rawExtras` 在 P0（主进程）通常为空，由 P2（`AppBrandUI.onCreate`）补齐；
> 规则里的 `meta:` 正则源无值时，视为该源不参与匹配。

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
| 7 | `:app` | Java | 全部 | 唯一出 APK：装配（Composition Root）+ `Pipeline` 实现 + `META-INF/xposed` 资源 | — |

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
- **装配约定**：`Pipeline`（§6.3 的 `pipeline.handle`）唯一实现在 `:app`。它只做「总开关 → 调用 `engine.decide` → 调用 `executor.run` → 成功才返回 true」的顺序拼接；护栏、匹配、提取、降级链算法一律在 `:module-rules` / `:module-executor`，`:app` 不得长出业务规则。

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
            .name(Reflect.str(Reflect.obj(lp, "miniAppInfo"), "name"))  // 可为 null
            .entryPath(Reflect.str(lp, "entryPath"))
            .fakeUrl(Reflect.str(lp, "fakeUrl"))
            .scene(Reflect.integer(lp, "scene"))
            .via(Reflect.str(lp, "via"))
            .link(link).linkType(linkType).build();
        // arkApp / meta / rawExtras 在 P0 通常为空，由 P2（AppBrandUI.onCreate）补齐，见 §4.3

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

> **表示层约定（Kotlin）**：`targets` 每一项为 `TargetSpec(whenVar: String?, action: Target)` —— JSON 里的 `when` 在 Kotlin 侧叫 `whenVar`（`when` 是关键字）。
> 四种 `action` 与 §8 的 `type` 一一对应：`Http(tpl)` / `Deeplink(pkg, tpl)` / `Package(pkg)` / `Passthrough`。

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
| `package` | `ACTION_MAIN` + `CATEGORY_LAUNCHER` + `setPackage(pkg)`，直接 `startActivity`（**不用** `getLaunchIntentForPackage` 判定） | `ActivityNotFound` / 包未安装 |
| `passthrough` | 不动作，返回失败 → 触发 `chain.proceed()` | — |

- **发起方是 QQ 进程**：模块代码注入 QQ 进程内执行，`startActivity` 用的是 **QQ 的 Application Context 与 QQ 的 uid**。系统与目标 App 看到的调用方都是 `com.tencent.mobileqq`；Handoff 自己的 App 进程不参与跳转（只做设置页）。这也是唯一可行的路径——Android 10+ 限制后台启动 Activity，点击瞬间前台是 QQ，模块进程在后台会被系统拒掉。
- 执行上下文统一 `Application` + `FLAG_ACTIVITY_NEW_TASK`。
- **B 站拉起必须 `setPackage`**：本机 `tv.danmaku.bili` 的 https 过滤器未通过域名验证，裸 `ACTION_VIEW` 会落到 `ResolverActivity` / 默认浏览器。实测降级链：
  1. `ACTION_VIEW` + 原 https URL + `setPackage("tv.danmaku.bili")` —— **实测即命中**；
  2. `bilibili://video/<aid>` + `setPackage`；
  3. `getLaunchIntentForPackage("tv.danmaku.bili")` → 至少进 App 首页；
  4. 全失败 → 不 `finish()`，放行 QQ 浏览器。
- **Context 获取**：`MiniAppEvent` 不含 Android 类型，执行器需要 `Context` 时走 `ActivityThread.currentApplication()`（反射，成功后缓存）；取不到即视为本次执行失败 → 放行。
- **包可见性（Android 11+）按调用方算，即按 QQ 的 manifest 算**，模块无法修改。因此：
  - `http` / `deeplink` 不受影响——启动 Intent 本身不经可见性过滤；
  - `package` 型**不得用 `getLaunchIntentForPackage` 判定可用性**：它属查询类 API，目标不可见即返回 `null`，会被误判成"未安装"而静默降级。改为显式 `setPackage` + `startActivity`，失败靠 `ActivityNotFoundException` 兜底（见表）；
  - Handoff 自己 manifest 的 `<queries>` 仅供设置页展示"目标 App 是否已安装"，**与跳转无关**；不使用 `QUERY_ALL_PACKAGES`。
  - 待 M1/M2 真机确认 QQ 自身是否已声明宽泛可见性（若已声明，上述风险自然消失，但代码仍按"不依赖它"来写）。
- `targets` 顺序执行，**首个成功即停**；最后一项固定 `passthrough`。

---

## 9. 存储与配置（UI 与存储严格隔离）

### 9.1 接口（定义在 `:module-api`）

```kotlin
/** 配置。规则以 JSON 原文存放、由 :module-rules 解析 —— 以保持 :module-api 零依赖 */
data class Config(
    val enabled: Boolean = true,   // 总开关
    val debug: Boolean = false,    // 调试日志（同时决定是否输出 logcat）
    val rulesJson: String,         // 规则数组 JSON 原文
    val version: Int = 1           // 结构版本，导入导出兼容用
)

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

| 数据 | 通道 | 方向 | 理由 |
|---|---|---|---|
| 配置 | **RemotePreferences**（`handoff_config`） | 设置页写 → hook 进程读 | 体小、有变更监听、热更新。**目标进程侧只读**（官方 API 约束） |
| 命中日志（M1 / M2） | **Xposed log**：`log(priority, "Handoff", …)` | hook 进程 → LSPosed 日志 | 目标进程内 `openRemoteFile()` **只读**，无法写；`log()` 是唯一把数据带出目标进程的官方通道 |
| 命中日志（M3 起，待定） | 广播回传模块 App，由 App 落盘为 `handoff.log` | hook 进程 → 模块 App | 设置页要能列历史就需要可读文件，见 §13 待确认 |

> **能力边界（按 libxposed API 102 源码核实）**：`XposedInterface.listRemoteFiles()` / `openRemoteFile()` 在**目标进程内只读**；
> 可写的 `openRemoteFile()` 只在模块 App 侧的 `XposedService` 上（文件不存在会创建）。
> 因此「QQ 进程 append 日志 → 设置页 tail」在 modern API 下不可实现，不要按这个方向写代码。

### 9.4 跨进程数据流

```
QQ 进程（主进程 / :mini 等）                模块 App 进程（设置页）
  Hook 命中 → Pipeline → 提取/决策/执行
     ↓
  log(priority, "Handoff", line) ──▶ LSPosed 日志   ← M1/M2 唯一出口
     ↑
  ConfigRepository.load() ◀──RemotePreferences 变更监听──  ConfigRepository.save()
                                        （App 侧经 XposedService 写入）

  广播回传（M3 待定）───▶ ✉ ──▶ 模块 App 落盘 handoff.log
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
| AGP | 9.3.1 | 官方 example 用 9.2.1；本机缓存 9.3.1，实测可构建 |
| Gradle | 9.5.1（wrapper） | 已随仓库提交 wrapper |
| compileSdk | 37 | 本机 platforms 有 `android-37.0`；build-tools 用 36.1.0（本机无 37.0.0） |
| buildToolsVersion | 36.1.0 | 本机 SDK 无 37.0.0；AGP 9.3.1 + compileSdk 37 实测可用 |
| minSdk | 26 | libxposed 要求 |
| Java / Kotlin target | 21 | AGP 9 默认 |
| libxposed | `io.github.libxposed:api:102.0.0`（compileOnly）+ `service` | Maven Central |
| JSON 解析 | `org.json`：`compileOnly`（Android 平台自带）+ 单测 `testImplementation` | 避免 Android 侧打包出重复类 |
| JDK | 21+（本机 25） | 当前 `JAVA_HOME` 指向 Minecraft 的 JRE，构建时须显式指定 |
| 打包 | `packaging { resources { merges += "META-INF/xposed/*"; excludes += "**" } }` | 不加 `merges` 模块不出现；不加 `excludes += "**"` 会把 resources 全打进 APK |
| R8 / 混淆 | `-adaptresourcefilecontents META-INF/xposed/java_init.list` + keep 入口类 | 否则入口类被裁 / `java_init.list` 类名与混淆后不一致，模块加载不到 |

### 11.1 `META-INF/xposed/`

```
java_init.list   →  com.handoff.ModuleMain
module.prop      →  minApiVersion=101
                    targetApiVersion=102
                    staticScope=false
                    autoHotReload=true
                    exceptionMode=protective
scope.list       →  com.tencent.mobileqq
```

> `exceptionMode` 是全局默认异常策略：`protective` = hooker 抛异常时按"没有这个 hook"继续执行（等价于我们要的"任何异常 → 放行"）；
> `passthrough` 仅用于调试。

### 11.2 R8 / 混淆规则（`app/proguard-rules.pro`）

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

入口类必须有**公开无参构造**；框架会 `attachFramework()` 注入实现，模块自己在 `onModuleLoaded()` 之前不得做任何初始化。

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
