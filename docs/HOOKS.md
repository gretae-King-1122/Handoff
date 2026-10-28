# HOOKS · Hook 点清单

> P0/P1 来自既有真机记录；M2.1 新候选来自 QQ 9.3.75 APK 静态分析，**尚待真机验收**。
> 修改本文件时请同步更新 `HookTargets` 候选表。

## 1. 进程分布（实测）

```
16:56:29.789  [MiniSDK]MiniAppLauncher(12166): getLaunchParam1036   ← 主进程 12166
16:56:29.860  QPublicTransFragmentActivity + PreloadingFragment      ← 预加载页
16:56:29.880  MiniAppDexLoader findClass: ...AppBrandUI3
16:56:29.977  START u0 {flg=0x30040000 cmp=.../.mini.appbrand.ui.AppBrandUI (has extras)}
16:56:29.997  Start proc 17233:com.tencent.mobileqq:mini              ← 容器进程 17233
```

**结论：启动决策发生在主进程，容器创建在 `:mini`。只 hook `:mini` 会晚 200ms。**

## 2. Hook 点

### P0 · 主攻

```
类    com.tencent.mobileqq.mini.sdk.MiniAppController
方法  startAppByLink
签名  (android.content.Context, java.lang.String, int,
       com.tencent.mobileqq.mini.sdk.LaunchParam,
       com.tencent.mobileqq.mini.sdk.MiniAppLauncher$MiniAppLaunchListener) : void
修饰  static
装哪  全部进程
```

能同时拿到 `link`、`linkType` 与完整 `LaunchParam`。

### P1 · 补充

```
类    com.tencent.mobileqq.mini.sdk.MiniAppLauncher
方法  openMiniApp
签名  (android.content.Context, LaunchParam, MiniAppLaunchListener) : boolean
修饰  static（private）
装哪  全部进程
阻断  不调 proceed()，return false
```

### P2 · 容器只读观测（M2.1）

```
类    com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI / AppBrandUI1~3
方法  doOnCreate(Bundle) / doOnNewIntent(Intent) / doOnResume()
装哪  所有 QQ 进程尝试安装，实际运行由 QQ 的容器进程决定
行为  原方法先执行一次，再取快照；不 finish，不接受 sink 的阻断结果
```

定向读取 `KEY_APPINFO` Parcelable；先设置目标 Activity ClassLoader。
SDK `appId` 可继承自 `MiniAppBaseInfo`。不枚举/打印登录等无关对象。
`doOnNewIntent` 使用该次实参 Intent；resume 标记为 `resume-snapshot-not-click`，不能当作新卡片点击。

### M2.1 · 解析后 / 页面事件（只读）

| 类 | 方法与参数（均返回 void） |
|---|---|
| `com.tencent.mobileqq.mini.sdk.MiniAppController` | `startApp(Activity, MiniAppConfig, ResultReceiver)` |
| `com.tencent.mobileqq.mini.sdk.MiniAppLauncher` | `launchAppByAppConfigWithoutCheck(Activity, MiniAppConfig, ResultReceiver)` |
| `com.tencent.mobileqq.mini.launch.MiniSdkLauncher` | `startMiniApp(Activity, MiniAppConfig, Bundle, ResultReceiver)` |
| `com.tencent.mobileqq.mini.launch.AppBrandLaunchManager` | `startMiniApp(Activity, MiniAppConfig, ResultReceiver)`（调用处签名候选） |
| `com.tencent.qqmini.sdk.server.LaunchManagerService` | `startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)` |
| `com.tencent.qqmini.sdk.ipc.AppBrandProxy` | `onAppShowOrHideWithPath(int, String, MiniAppInfo, String)` |

这里 `MiniAppConfig` 指 `com.tencent.mobileqq.mini.apkg.MiniAppConfig`，`MiniAppInfo` 指
`com.tencent.qqmini.sdk.launcher.model.MiniAppInfo`。按完整声明类型匹配，不靠参数个数猜重载。

`MiniRuntimeProbe` 统一挂载，`ResolvedAppData` 只读字段转成中立事件。保留全部候选路径及
`__pathOrigin`；展示优先级不是经过真机确认的 QQ 选页规则。Source 新增 `RESOLVED` / `PAGE`。
所有新点 **只记录，绝不拦截**；现有浏览器外跳不变。旧全方法 `MiniSdkSweep` 不再安装。

安装日志前缀 `[MINI-PROBE]`，输出匹配数量、完整方法签名、ClassLoader 身份；
缺类输出 deferred，匹配数为 0 不能当作安装成功。新事件不经过旧 Sweep 的 URL/数字过滤。

### M2 · 浏览器交接（并行打开修复）

```
类    com.tencent.mobileqq.app.QBaseActivity（QQ 自有类）
方法  startActivity(Intent) / startActivity(Intent, Bundle) : void
装哪  QQ 主进程
范围  receiver 必须恰好是 QQBrowserDelegationActivity
目标  显式 component 属于 com.tencent.mobileqq，且为 QQBrowserActivity /
      QQH5BrowserActivity / QQTranslucentBrowserActivity
```

QQ 9.3.75 APK 中，跳板页 `doOnCreate` 在 `startActivity(intent2)` 后自行 `finish()`。
旧版先执行继承来的 `onCreate` 再 finish，浏览器此时已经发出，因而可能与外跳同时打开。
现在在派发前尝试外跳；`startActivity` 未抛错表示请求已接受，此时不调用原浏览器启动方法。
不修改系统确认弹窗、不绕过确认，也不把“请求接受”当作用户已经确认。

仅匹配 http/https 的 `bilibili.com` / `www.bilibili.com` / `m.bilibili.com`，
标准端口、无 userinfo、完整 `/video/av数字` 或 `/video/BV十位字符` 路径。
非命中/提取异常/外跳全失败才原样调用 `proceed()` 一次；原方法异常原样抛出，不重试。
线程级重入保护覆盖重载互调和外跳自身的 startActivity；不是跨点击的时间窗口去重。
不 Hook/跳过 Activity 生命周期，不主动 finish；由 QQ 原代码结束跳板页。
安装日志 `HANDOFF dispatch installed`；阻断日志 `HANDOFF blocked QQ browser id=… via …`，不输出完整 URL 查询参数。

2026-10-28 11:13 真机已确认一轮成功外跳：阻断日志单次命中，进入 B 站视频详情，
同次系统日志无内置浏览器启动，返回 QQ 聊天 Activity。取消/失败与负对照仍待回归，详见 TESTING §6。

## 3. LaunchParam 字段（反射读取）

| 字段 | 类型 | 用途 |
|---|---|---|
| `miniAppId` | String | 规则匹配主键（B 站 = `1109937557`） |
| `entryPath` | String | 页面路径，提取 `bvid` / `aid` / `ep_id` |
| `fakeUrl` | String | 原始链接（若有） |
| `scene` | int | 场景码，Ark 卡片 = `1036` |
| `via` | String | 来源标记 |
| `reportData` | String | 埋点，仅日志 |

> 字段名若在新版本变化，`Reflect` 读取会返回 null，日志中会体现；补一条映射即可。

## 4. 安装策略

2026-10-28 起按运行计划区分：默认仅 QQ 主进程的 BrowserHandoff，其他只读点不安装；
`-PhandoffDiagnostics=true` 恢复下述跨进程诊断覆盖。浏览器优先，每个 loader 身份只安装一次。

```java
onPackageReady(PackageReadyParam param)
  if (!param.getPackageName().equals("com.tencent.mobileqq")) return;
  backend.install(param.getClassLoader(), Process.myProcessName());
```

- 每个 hook 点**独立 try**，互不影响
- 诊断模式不漏 `:mini` 等 QQ 子进程；正常模式辅助进程不挂载探针（见 PERFORMANCE.md）
- 类查找用 `Class.forName(name, false, cl)`（只加载不初始化）
- 旧 P0/P1 按「名字 + 参数个数 + 参数可赋值性」查找；新探针与浏览器派发按 APK 已确认的精确签名匹配
- 默认不 `deoptimize`，保留调试开关
- M2.1 按实际 `Method` 去重安装，反射缓存包含 `Class` 身份而不仅是类名。
- 监听 `MiniAppDexLoader.findClass(String)` / `create(String)` / `loadDex()` 的正常返回，
  对实际 Class / `mNewClassLoader` 补装；不全局扫描 ClassLoader，也不 Hook 全部文件访问。

## 5. 抗版本技巧

1. **参数不按下标取**：遍历 `chain.getArgs()`，按 `instanceof` 认 `Context` / `String` / `Integer` / `*LaunchParam`。
2. **字段反射 + Field 缓存**：`getDeclaredField` → `setAccessible(true)` → 静态 Map 缓存。
3. **候选表驱动**：每个 hook 点在 `HookTargets` 里登记多组候选，按序探测，命中即用。

## 6. 已知干扰项

| 干扰 | 说明 | 处理 |
|---|---|---|
| `qfix.redirect.IPatchRedirector` | QQ 自有热修框架，每个方法头都有 | 不影响：我们 hook 的是原始 `Method` 对象 |
| `MiniAppDexLoader` | 容器类可能由动态 ClassLoader 加载 | 使用已知 loader 返回的实际 Class / 新 loader 补装 |
| 多实例容器 `AppBrandUI1~3` | QQ 并行开多个小程序 | 精确类白名单；只 Hook 本类声明的业务生命周期，不 Hook 共享 Activity 基类 |
