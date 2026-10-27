# 小程序卡片：从短链解析后的对象切入

日期：2026-10-27。结论性质：**源码审查 + 本地 APK 静态分析，尚未真机验证新 Hook 点**。

## 结论

不建议直接把「全局文件访问 → MD5 认应用 → WebView URL 认视频」作为正式方案。
优先观察 QQ 自己解析短链之后形成的 `MiniAppConfig` / `MiniAppInfo`，以及容器的
`KEY_APPINFO` Parcelable 和 SDK 的带路径显示事件。无需先找到聊天卡片渲染类。

P0 的字段为空是已知事实；「只有 :mini 才能知道 appId」不是由该事实必然推出的结论。
本地 APK 存在更晚阶段的显式对象传递链；是否用于这次实际点击，需要新探针确认。

## 分析对象与边界

- 文件：项目根目录 `qq_9.3.75.42350.apk`。
- SHA-256：`ee9da76ee5c8cb3f53abf71e9835904348c870015af75c7bc9f1846567be1f4a`。
- 工具：本机 JADX 1.5.6，定向反编译，不执行 APK。
- 原始反编译文件保存在被 Git 忽略的 `tmp/mini-research/`；下列行号对应本次输出。
- ADB 设备列表为空，未复现冷启动、热启动或外跳；不得把静态候选称为实测可用。
- QQ 支持动态 SDK Dex；设备上的实际类加载器/代码可能不同于 APK 内置版本。
- 初次调查仅新增本文；后续 M2.1 已实现只读探针，见 `docs/HOOKS.md` 和 `docs/TESTING.md`。
  新链路仍未启用自动跳转或阻断。

## 静态证据

### E1：短链解析回调会补充 appId

`com.tencent.mobileqq.mini.appbrand.ui.PreloadingFragment`，`classes32.dex`：

- `PreloadingFragment.java:866`：调用 `MiniAppCmdUtil.getAppInfoByLink(...)`。
- `:444`：回调从结果中取出 `MiniAppInfo`，键名 `appInfo`。
- `:478–486`：构造 `MiniAppConfig`，设置 `launchParam.miniAppId = previewInfo.appId`，保留原始 link。
- `:869–876`：`doStartMiniApp(MiniAppConfig)` 调用
  `MiniAppLauncher.launchAppByAppConfigWithoutCheck(...)`。
- `:838–840`：还有 `startMiniAppFromAppInfoLinkDB(...)` 缓存路径，因此不能只盯网络回调。
- `:488–489`：有 shareTicket 时可能改写 scene；不能把「晚阶段 scene 必须仍是 1036」当作不变式。

这证明 APK 中存在「短链 → QQ 内部解析 → 带身份的配置对象 → 启动」的路径，
不证明用户每次点击都经过该回调，也不证明每次 entryPath 都非空。

### E2：视频入口不只在 LaunchParam.entryPath

`com.tencent.mobileqq.mini.launch.MiniSdkLauncher`，`classes10.dex`：

- `MiniSdkLauncher.java:162–185`：`convert(MiniAppConfig)` 复制 scene、miniAppId、entryPath。
- `:420–425`：从 QQ 侧模型复制 appId 到 SDK 侧模型。
- `:447–452`：复制 `firstPage.pagePath`。
- `:156`：`MiniSDK.startMiniApp(activity, convert(miniAppConfig), bundle, resultReceiver)`。

探针应分别记录 `config.config.appId`、`config.launchParam.entryPath`、
`config.config.firstPage.pagePath`、`config.entryPath`、`config.link`，
或 SDK 模型上的对应字段。冲突时不能随意拼接，需要验证最终选页逻辑。
`convert` 同时被预下载使用，只适合辅助观察，不能看到它就执行外跳。

### E3：P2 丢掉了真正的启动对象

`com.tencent.qqmini.sdk.server.LaunchManagerService`，`classes10.dex`：

- `LaunchManagerService.java:320–335`：启动时把整个 `MiniAppInfo` 作为 Parcelable 放入
  Intent，键名 **`KEY_APPINFO`**。
- `:326–331`：启动逻辑明确区分 EMPTY、PRELOADED 和其他进程状态。

项目的 `EventBuilder.java:66–69` 只保留顶层 String / Number / Boolean，
因此不会读取 `KEY_APPINFO`。这属于观测盲区，不是 QQ 没有提供对象的证据。

解析时先使用实际目标类的 ClassLoader 设置 extras ClassLoader，再定向读取 Parcelable；
捕获反序列化异常并放行。不要递归 dump 整个 Bundle：同一个 Intent 还带有登录相关对象。

SDK 模型的 `appId` 在父类 `MiniAppBaseInfo`（本次输出 `:45`），
`launchParam` 在 `MiniAppInfo`（`:76–77`），反射必须沿父类查找。

### E4：存在热启动与带路径事件的候选点

`com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI`，`classes9.dex`：

- `AppBrandUI.java:186–197`：业务创建逻辑是 `doOnCreate(Bundle)`。
- `:208–215`：`doOnNewIntent(Intent)` 明确 `setIntent(intent)` 并转交 FakeBrandUI。
- `:232–236`：`doOnResume()` 转交 FakeBrandUI。

项目当前只 Hook 一次反射查得的 `onCreate`，未覆盖上述热复用业务回调；
该反射也可能命中继承实现，需要记录实际 declaringClass 和运行时 Activity 类。

`com.tencent.qqmini.sdk.ipc.AppBrandProxy`，`classes10.dex`：

- `AppBrandProxy.java:61–66`：`onAppForeground(MiniAppInfo, Bundle)`。
- `:70–71`：`onAppShowOrHideWithPath(int, String, MiniAppInfo, String)`。
- `LaunchManagerClient.java:491–499`：后者向服务转交 status、进程、MiniAppInfo、currentShowPath。

带路径事件是很好的观测候选，但必须实测 status 的含义、前后台时序、路径新旧，
并区分卡片启动、内部跳页和普通回前台；**不能每次 onResume 都拿旧路径外跳**。

### E5：现有 Sweep 可能 Hook 到了，却没输出

项目 `MiniSdkSweep.java:81–87` 只展开字符串和基本值，其他对象仅输出类型名；
`:52` 又要求输出文本满足 URL / 数字 appId 过滤。

因此 `(Activity, MiniAppConfig, ResultReceiver)` 这类调用可能完全不产生日志。
下一轮应对已确认的配置对象定向取字段，不应再以旧 Sweep 的静默排除整个启动链。

## 建议下一轮：只观测，不阻断

按优先级设置少量、类型签名明确的探针：

1. `MiniAppController.startApp(Activity, MiniAppConfig, ResultReceiver)`：读取解析后的配置。
2. `MiniSdkLauncher.startMiniApp(Activity, MiniAppConfig, Bundle, ResultReceiver)`，
   或 SDK 的 `LaunchManagerService.startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)`：
   观察最终传递的 appId / 入口与缓存复用。实际安装以运行时签名为准。
3. P2 补 `KEY_APPINFO`，并覆盖 `doOnCreate` / `doOnNewIntent`；基类/子类按实际 Method 去重。
4. `AppBrandProxy.onAppShowOrHideWithPath`：验证最终页面与热复用。

每条只记：单调时间、PID/进程、实际类加载器身份、方法签名、Activity/session 标识、
appId、scene、候选页面、原始短链（必要时脱敏）和事件类型。不要靠时间接近把不同容器的值拼起来。
动态 Dex 通过 `MiniAppDexLoader.findClass/create` 返回的实际 Class/对象定位；
不要全局扫描所有 ClassLoader 或长期 Hook 所有文件/网络访问。

## 正式拦截的护栏

- 同一个明确的启动/显示会话内，同时满足「可信 appId 为 1109937557」和「有效内容 ID」。
- 路径严格解析 query，区分 BV / aid / ep_id；不能仅凭出现 BV 字样、B 站目录或域名子串判定。
- 卡片来源要有证据；若晚阶段 scene 被改写，应保留来源关联，而不是取消场景限制。
- 以 `(session, appId, 规范化内容目标)` 做原子去重，状态区分待执行、成功、失败。
  失败不得占住成功去重窗口；成功重复必须复用“已处理”结果，不能返回 false 又放进 QQ。
- 跨进程 static 不共享；第一版应选单一决策点/进程，其他点只观测，避免多个点同时外跳。
- 在主线程先发出指定 `tv.danmaku.bili` 的精确 https Intent；启动调用成功后才取消对应启动，
  或 finish 精确匹配的容器。startActivity 返回只表示请求被接受，不保证目标已显示，仍需真机验收。
- 生命周期必须执行必要父类逻辑；不要吞 onCreate，不要 kill :mini，也不要 finish 整个 QQ task。
- 身份、内容、来源或当前会话任一不确定，以及外跳失败，都放行；未知短链不得降级成随便开 B 站首页。
- 文件路径观察只留作定位调用栈的后备：预加载会误报、热复用可能不读文件、native IO 可能绕过 Java Hook。
  小程序路由也不保证经过 Android WebView.loadUrl；可能使用 X5、JS 路由或缓存。

## 最小真机反馈闭环

每次清晰标记测试开始时间与操作；分别采集：冷启动 B 站视频 A、后台复用点视频 B、
短时间重复点 A、两容器交错、B 站普通回前台、非 B 站/支付卡片，以及 B 站 App 不可用。

第一阶段通过条件：冷/热点击都能在同一会话中读到正确 appId 和本次视频 ID，
非 B 站和预加载不被误归因。此时才接入执行与阻断，并验证重复点击只发一次外跳、
外跳失败仍能正常进入原小程序。尚未满足这些条件，不能宣称问题已修复。
