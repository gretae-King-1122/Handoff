# Handoff

> QQ 负责聊天，B 站负责看视频，内置浏览器就别加班了。

**Handoff** 是个 LSPosed「视频传送门」：点击 QQ 里支持的 B 站卡片或视频链接，
直接交给 B 站 App，不让内置浏览器抢戏。该确认还得确认，跳不过去就走原路。

**当前发布：v0.2.0。** 支持已适配的 B 站网页与小程序视频交接；新增 SDK／容器多入口互补，
并修复 QQ 启动时切换 ClassLoader 后“挂载成功却不执行”的问题。
PJD110、QQ 9.3.75 的修复包已记录网页和小程序实际命中，用户确认成功；65 项 JVM 测试、
Debug／Release 构建及 Release lint 通过。支持范围仍受下述调用方／视频路径限制，不承诺所有设备和热更新时机。
旧 v0.1.0 不含小程序交接，v0.2.0-rc.1 不含本轮运行时补挂修复。

[下载／版本](https://github.com/gretae-King-1122/Handoff/releases) ·
[问题反馈](https://github.com/gretae-King-1122/Handoff/issues) ·
[更新记录](CHANGELOG.md)

```text
点击支持的卡片／链接 → 校验 B 站视频地址 → 请求打开 B 站 → 原有外跳确认 → 视频页
                                      └─ 启动失败 → 放行 QQ 原路径
```

## 支持范围

| 场景 | 当前状态 |
|---|---|
| 经 QQ 浏览器跳板打开的 B 站分享卡片／明文视频链接 | 支持；派发前交接，避免并行启动内置浏览器 |
| `/video/av数字`、`/video/BV…` 视频地址 | 支持 `bilibili.com`、`www.bilibili.com`、`m.bilibili.com` 的标准 HTTP(S) 地址 |
| `m.q.qq.com/a/s/…` 小程序短链 | QQ 解析出 B 站 App ID 与视频页后，在已适配的小程序启动路径交接；不凭短链前缀盲拦 |
| `b23.tv` 短链 | 模块不主动解析；只有 QQ 已解析并走支持的明文视频派发路径时才可能命中 |
| 番剧、直播、专栏、其他 App | 尚未支持 |
| 设置界面、总开关、规则编辑／热更新 | 尚未实现；停用请在 LSPosed 中禁用模块后强停 QQ |

目标 App 为国内版哔哩哔哩 `tv.danmaku.bili`。国际版、概念版、分身及其他 QQ 版本未验证。
小程序交接限 App ID `1109937557` 的 `pages/video/video[.html]` 页面，读取 `bvid` 或正整数 `aid`。
已发布 rc.1 仅适配主进程的 `LaunchManagerService.startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)`：
Activity 为 `QPublicTransFragmentActivity`、无 ResultReceiver；允许普通启动附加参数，
但 Bundle 不能覆盖 `KEY_APPINFO` 或携带 `receiver` 回调。
其他入口、IPC／回调启动、身份或视频 ID 冲突、未知页面均保留 QQ 原行为；不承诺所有小程序入口已覆盖。
只读诊断探针本身仍不执行交接，也不以页面恢复通知作为新的点击。

v0.2.0 在原服务入口之外增加两层独立安装的入口：
- `MiniSDK.startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)`：精确匹配四参数 static void 签名，
  沿用服务入口的调用方和 Bundle／回调限制；单个入口不可用不影响其他入口。
- 主进程内的 `Activity.startActivityForResult(Intent, int, Bundle)` 派发前兜底：只接受上述调用方，
  显式目标必须为 QQ 的 `AppBrandUI`／`AppBrandUI1~3`，不接受结果请求、转发结果或 selector。
  在 Intent 副本上读取 `sdk_mode=true`、无非空 `receiver` 的 SDK `KEY_APPINFO` 模型，
  复用同一身份／视频校验，不读取登录字段、不修改原 Intent，也不在容器生命周期中二次跳转。
  此处允许尚未销毁但正在结束的过渡页：QQ 可先结束预加载页，再执行排队的启动请求；
  已销毁的调用方仍放行。SDK／服务早期入口继续要求 Activity 未结束、未销毁。

外层尚无视频信息时允许内层继续识别；同一同步调用链中相同视频只尝试一次，不跨线程或跨点击缓存。
兜底依赖 Android 派发方法和上述 QQ 契约，不等于已解决所有热更新场景或支持所有小程序入口。

### QQ 热补丁环境的运行时补挂

PJD110 的定向诊断确认：package-ready 阶段使用 PathClassLoader，Application 启动及真实派发时
使用 DelegateLastClassLoader；浏览器派发类、跳板类、MiniSDK 和服务类均出现“同名但 Class 身份不同”。
因此只挂早期类，即使报告成功，也可能不进入 QQ 实际使用的方法。

现在保留早期安装，并在主进程 `Instrumentation.callApplicationOnCreate` 执行前，按 Application 的实际
ClassLoader 补挂正常交接入口；按 Loader 身份和反射 Method 的真实声明类身份分别防重。
浏览器调用方仍限定精确类名及 Activity 类型，但不再捕获早期跳板 Class 做身份比较，避免部分补丁类被误拒。
这只用于挂载方法，不在 Application 生命周期内外跳，不替换 QQ 的 ClassLoader、不删除或禁用其补丁。
加载器提取／补挂失败时继续 QQ 原始初始化，原方法异常保持原样，不重试原调用。
2026-10-30 13:21 的修复包日志已确认网页及 SDK 小程序入口命中，用户确认成功；
尚不覆盖 Application 启动之后再次更换加载器的未知路径，也没有完成全部机型、入口和负向真机回归。

### QQ 9.3.75 安装包静态核对（2026-10-30）

本地 `qq_9.3.75.42350.apk` 报告版本 9.3.75／versionCode 16580，SHA-256 为
`ee9da76ee5c8cb3f53abf71e9835904348c870015af75c7bc9f1846567be1f4a`。
已核对 SDK／服务四参数签名，以及服务层在容器 Intent 中写入 `KEY_APPINFO`、`sdk_mode` 和
可为 null 的 `receiver`，与新增入口契约一致；启动 Bundle 最后合并，因此兜底读取最终 Intent。
普通服务启动会将 `Activity.startActivity` 排入 UI 队列；预加载页另有 `finish()` 收尾，
不能把 `isFinishing()` 当作最终启动请求失效的充分条件。该边界已补先失败后通过的单测。

本地 APK 与正常设备此前提取的 base APK 哈希不同，且不包含故障设备另行下载的 Tinker 补丁。
因此静态证据不能替代故障设备回归；后续运行时类身份对照及修复结果见上一节。

## 环境要求

- **已 root 的设备**，安装支持现代 libxposed API 的 LSPosed 兼容框架。
- 模块 `minSdk 26`，即 Android 8.0+；这只是编译下限，框架和 QQ 自身可能要求更高版本，
  **不代表所有 Android 8.0+ 设备均经过验证**。
- 真机验证环境：OPPO PGZ110／PJD110、QQ 9.3.75、LSPosed 2.2.1 / API 102。
- 2026-10-29 小程序诊断设备实际报告 QQ 9.3.5（versionCode 14540）：已确认上述四参数签名可挂载，
  视频 ID 在启动前可见；这不等于修复包已通过该版本的真机回归。
- 安装国内版哔哩哔哩，并为模块勾选 QQ `com.tencent.mobileqq` 作用域。

## 获取 APK

在 [v0.2.0 正式版](https://github.com/gretae-King-1122/Handoff/releases/tag/v0.2.0) 下载
`Handoff-v0.2.0.apk`；`SHA256SUMS.txt` 用于校验文件。正式签名兼容 v0.1.0／v0.2.0-rc.1，
不能覆盖 Debug 签名包。也可以自行[构建](#构建)。

> 官方 APK 使用独立发布签名。旧 debug 包无法直接覆盖安装，需先在 LSPosed 停用并卸载旧 Handoff，
> 再安装正式包、重新启用并勾选 QQ（不要卸载 QQ）。自行构建的签名也可能不同；密钥不要提交到仓库。

## 安装

1. 安装自行构建或 Releases 中的 APK。
2. 打开 LSPosed → 模块 → 启用 **Handoff**。
3. 作用域勾选 **QQ**，不需要勾选 B 站或系统框架。
4. 强制停止 QQ，再重新打开。
5. 在 QQ 中点击支持的 B 站视频卡片／链接，按原有弹窗确认外跳。

模块目前**没有桌面图标或设置页**，安装后请到 LSPosed 管理。
更新模块后也建议强停并重开 QQ；停用时同样操作。

## 常见问题

**点了仍打开 QQ 浏览器？**

先确认模块已启用、作用域已勾选、QQ 已强停重开，并检查链接是否属于支持范围。
QQ 内部派发路径随版本变化，不保证其他版本或其他卡片类型可用。

**为什么还有“是否跳转”的弹窗？**

模块保留原有外跳确认，不尝试绕过。外跳请求被系统接受不等于用户已经确认。
取消确认不会自动再打开 QQ 网页；此场景仍需完整真机回归。

**没有安装 B 站，或者外跳失败怎么办？**

会顺序尝试视频链接、B 站 scheme、B 站首页；同步启动均失败时放行 QQ 浏览器。
最终降级到首页不代表已打开对应视频；不可用场景仍待完整真机回归。

预发布版的小程序交接只尝试视频链接与 scheme，不把打开首页算作视频交接；启动失败时放行 QQ 小程序。

**为什么某些小程序卡片仍会打开 QQ 小程序？**

先确认使用的是包含小程序交接的源码构建包。QQ 必须先解析出可信的 App ID 和视频路径；
只剩短链、QQ 解析失败、未知启动入口或带未适配回调时都会放行，不猜视频 ID。
尚未证明卡片存在固定失效时长；可尝试重新分享或直接发送明文视频链接，但不保证解决所有入口问题。

**为什么没有立刻打开视频？**

总时长还包含人工确认、系统调度及 B 站冷启动。轻量模式减少的是模块探针与日志开销，
不能保证消除其他 App 的加载时间。

## 隐私

- 模块不主动请求网络、不上传日志，不提供遥测；QQ 与 B 站自身的联网行为不受此声明约束。
- 正常模式检查浏览器跳板的目标视频链接，以及已适配小程序启动模型中的 App ID 和候选视频路径，
  输出简短安装／命中／耗时日志；小程序外跳重建标准视频 URL，不携带分享跟踪参数。
- 诊断包额外读取并记录启动字段及调用信息，不建议长期日用或直接公开完整日志。
- 本地配置、临时日志、APK、常见密钥与签名材料已加入 Git 忽略规则。

## 问题反馈

请在 [Issues](https://github.com/gretae-King-1122/Handoff/issues) 中提供：

- Android／系统版本、QQ 版本、LSPosed 版本、Handoff 提交或版本号。
- 卡片还是明文链接、视频 ID 类型、复现步骤、预期和实际结果。
- 已脱敏的 LSPosed Handoff 日志及复现时间。暂无设置页日志导出。

`HANDOFF dispatch installed` 表示具体派发签名已挂载；
`HANDOFF blocked QQ browser` 表示该次内置浏览器派发被阻断，**不是用户确认或视频加载完成的证明**。
预发布版的 `HANDOFF mini installed` 表示小程序启动签名已挂载；`HANDOFF blocked QQ mini`
表示视频外跳请求被接受且原小程序启动被跳过，同样不代表用户已确认或视频已加载。
`HANDOFF mini pass reason=launch-failed` 表示尝试启动失败，已放行 QQ 小程序。
v0.2.0 的命中日志额外标明 `entry=sdk`／`service`／`container-dispatch`；
`HANDOFF mini container installed` 仅表示派发兜底挂载，不代表实际点击命中。
请先删除 QQ 号、聊天内容、分享链接查询参数、Token 和设备标识，再公开日志或截图。

## 构建

需要 Git、JDK 21+ 和 Android SDK Platform 37；本机已用 JDK 26 验证。框架需提供 libxposed API 102。
使用仓库自带 Gradle Wrapper，首次构建需联网下载依赖。
通过 Android Studio 配置 SDK，或在本地 `local.properties` 中设置 `sdk.dir`，不要提交个人路径。

```powershell
git clone https://github.com/gretae-King-1122/Handoff.git
cd Handoff

# Windows PowerShell；默认关闭诊断探针
.\gradlew.bat :app:assembleDebug :app:assembleRelease

# 单元测试
.\gradlew.bat :module-hook-lsposed:testDebugUnitTest

# 仅研究小程序时开启完整探针
.\gradlew.bat :app:assembleDebug -PhandoffDiagnostics=true

# 仅排查小程序启动边界，不开启完整探针或记录原始链接
.\gradlew.bat :app:assembleDebug -PhandoffBoundaryDiagnostics=true
```

macOS／Linux 将 `.\gradlew.bat` 换为 `./gradlew`；若无执行权限，先运行 `chmod +x gradlew`。

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

官方 Release 需要本地签名配置：默认读取 `~/.handoff/release-signing/signing.properties`，
或通过环境变量 `HANDOFF_SIGNING_PROPERTIES` 指定；包含 `storeFile`、`storePassword`、
`keyAlias`、`keyPassword`（PKCS12）。没有配置时只生成 `app-release-unsigned.apk`，不能直接安装；
自行体验可构建 Debug。Release 始终关闭诊断探针。

正常包应记录 `diagnostics=false`。诊断包仅适合排查问题，切回日常包时去掉构建参数并重新构建、安装。
定向边界诊断包另有 `boundaryDiagnostics=true` 和 `[DEBUG-mini-boundary]` 标记；正常包应为
`boundaryDiagnostics=false`。两个诊断开关在 Release 中均强制关闭；定向诊断不改变拦截规则。
如果安装后仍出现旧日志，可追加 `--no-build-cache --rerun-tasks` 强制构建；不要仅凭安装成功判断实现已更新。

Windows 中文工作目录下若测试 worker 报已编译测试类找不到，可尝试仅对该命令覆盖 daemon 编码：

```powershell
.\gradlew.bat :module-hook-lsposed:testDebugUnitTest --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=GBK'
```

这不是源码编码变更，不要据此修改项目的 UTF-8 配置。

## 更新记录

见 [CHANGELOG.md](CHANGELOG.md)。

## License

[Apache-2.0](LICENSE) © 2026 Handoff Project Contributors

## 免责

本项目为非官方实验性模块，与腾讯、哔哩哔哩无关联。不修改网络、不伪造设备指纹，也不规避风控。
Root 与 Hook 第三方应用可能造成崩溃、兼容性或账号风险；不保证零封号风险。
请自行评估，避免用于主力账号的支付、登录等关键场景。取消确认、外跳失败、非 B 站与连续点击仍需完整回归。
