# HOOKS · Hook 点清单

> 所有类名与方法签名均来自 QQ 9.3.75 的**真机反编译与 logcat 实测**，非猜测。
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

### P2 · 兜底

```
类    com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI
方法  onCreate
签名  (android.os.Bundle) : void
装哪  :mini / :mini1~5 / :miniapp / :wxa_container0~4
阻断  先 proceed()，再 finish() + 拉起外部（父类 onCreate 不可跳过）
```

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

```java
onPackageReady(PackageReadyParam param)
  if (!param.getPackageName().startsWith("com.tencent.mobileqq")) return;
  backend.install(param.getClassLoader(), Process.myProcessName());
```

- 每个 hook 点**独立 try**，互不影响
- **不做进程名过滤**（否则漏 `:mini`）
- 类查找用 `Class.forName(name, false, cl)`（只加载不初始化）
- 方法查找按「名字 + 参数个数 + 参数可赋值性」，不用 `getDeclaredMethod(精确类型)`
- 默认不 `deoptimize`，保留调试开关

## 5. 抗版本技巧

1. **参数不按下标取**：遍历 `chain.getArgs()`，按 `instanceof` 认 `Context` / `String` / `Integer` / `*LaunchParam`。
2. **字段反射 + Field 缓存**：`getDeclaredField` → `setAccessible(true)` → 静态 Map 缓存。
3. **候选表驱动**：每个 hook 点在 `HookTargets` 里登记多组候选，按序探测，命中即用。

## 6. 已知干扰项

| 干扰 | 说明 | 处理 |
|---|---|---|
| `qfix.redirect.IPatchRedirector` | QQ 自有热修框架，每个方法头都有 | 不影响：我们 hook 的是原始 `Method` 对象 |
| `MiniAppDexLoader` | 容器类由自定义 ClassLoader 加载 | P2 需用 `param.getClassLoader()`；失败再遍历已加载 loader |
| 多实例容器 `AppBrandUI1~3` | QQ 并行开多个小程序 | P2 按类名前缀匹配 `AppBrandUI` |
