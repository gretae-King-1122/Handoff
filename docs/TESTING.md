# TESTING · 测试策略

## 1. 分层测试

| 层 | 测试方式 | 覆盖目标 |
|---|---|---|
| `:module-api` | 纯 JVM 单测 | 数据类不变式、Builder 默认值 |
| `:module-rules` | **纯 JVM 单测（重点）** | 匹配、提取、降级链选择 |
| `:module-executor` | Robolectric / 真机 | 三种 target 的 Intent 构造正确性 |
| `:module-storage` | fake 实现 | UI ↔ 存储契约 |
| `:module-hook-lsposed` | **只能真机** | hook 命中、字段读取、阻断语义 |

**约 60% 的代码可以脱离手机测试**，这是模块化的主要收益。

## 2. 规则引擎测试用例（必须覆盖）

| 用例 | 输入 | 期望 |
|---|---|---|
| appId 精确命中 | `miniAppId=1109937557, scene=1036` | 选 bilibili 规则 |
| 名称兜底命中 | `miniAppId=null, name="哔哩哔哩"` | 选 bilibili 规则 |
| 场景不符 | `scene=1001` | passthrough |
| 保护名单 | `name="QQ钱包"` | passthrough（即使匹配到规则） |
| 提取 bvid | `entryPath="pages/video/video?bvid=BV1xx411c7mD"` | `vars.bvid=BV1xx411c7mD` |
| 提取 aid | `entryPath="pages/video/video?aid=170001"` | `vars.aid=170001` |
| 提取失败 | `entryPath="pages/video/video"` | vars 为空 → 降级到 deeplink |
| 模板填充 | `tpl="…/video/{bvid}", bvid=BV1` | `https://www.bilibili.com/video/BV1` |
| 降级链 | 第 1 个 target 抛 ActivityNotFound | 顺序尝试第 2 个 |
| 全失败 | 所有 target 失败 | 返回 false → 放行原小程序 |

## 3. 真机验证流程

### M1 探针版

1. `adb install -r app-debug.apk`
2. LSPosed 启用 + 勾选 QQ + 强停 QQ
3. 打开 QQ → 点一次 Ark 卡片
4. 取日志：**M1 的探针日志走 Xposed log，不走 logcat、也不写文件**

- LSPosed 管理器 → 日志 → 选 `Handoff`，直接看（最快）
- 或用 adb 拉取框架日志（文件名随 LSPosed 版本不同，先 `ls` 确认）：

```bash
adb logcat -c
# 点卡片
adb shell su -c 'ls -l /data/adb/lspd/log/'
adb shell su -c 'tail -n 300 /data/adb/lspd/log/modules.log'
```

> 为什么不用文件：目标进程里 `XposedInterface.openRemoteFile()` 是**只读**的，
> QQ 进程写不了 Remote Files；`log()` 是唯一能把数据带出目标进程的官方通道（见 SPEC §9.3）。

5. 核对三件事：
   - `miniAppId` 是否 `1109937557`
   - `entryPath` 实际形态
   - `scene` 是否 `1036`

### M2 拦截版

| 编号 | 场景 | 期望 |
|---|---|---|
| T1 | 点 B 站视频卡片 | 直接进 B 站 App 对应视频 |
| T2 | 点 B 站番剧卡片 | 进番剧页 |
| T3 | 卸载 B 站 App 后点卡片 | 打开 bilibili.com 网页 |
| T4 | 点 QQ 钱包/支付卡片 | **不拦截**，正常进小程序 |
| T5 | 连续点 10 次 | 无误跳、无循环 |
| T6 | 关闭模块开关后点卡片 | 正常进小程序 |

## 4. 回归清单（每次改 hook 层必跑）

- [ ] QQ 冷启动无 crash
- [ ] 聊天列表、朋友圈、扫码功能正常
- [ ] 小程序（非 B 站）能正常打开
- [ ] 支付流程未被拦截
- [ ] 模块关闭后行为与未安装一致

## 5. M2.1 解析后探针

### 本地字段/反射测试

```powershell
.\gradlew.bat :module-hook-lsposed:testDebugUnitTest :app:assembleDebug
```

`ResolvedAppDataTest` 覆盖 SDK 父类身份、QQ 配置对象、多路径保留、当前页事件、身份冲突、
空字段、不串用前次事件，以及不输出非白名单字段；`ReflectTest` 覆盖同名不同 ClassLoader 的缓存隔离。
这不验证 Android Parcelable 反序列化、LSPosed 安装或手机上的真实选页/外跳。

本机 Windows + 中文工作目录下，Gradle 9.5.1 的测试 worker 参数文件会被 Java 启动器按另一编码读取，
导致已编译测试类报 ClassNotFoundException。已验证可仅对测试命令覆盖 daemon 编码：

```powershell
.\gradlew.bat :module-hook-lsposed:testDebugUnitTest --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=GBK'
```

该命令 7 项测试通过（XML 报告 2 + 5 项，失败/错误均 0）；也用 JUnit Platform Launcher 直接执行了
同一编译产物，结果一致。此处 GBK 是本机启动器的编码兼容措施，不是项目源码编码；
不修改 `gradle.properties`，APK 仍按项目默认 UTF-8 配置构建。

本轮 Debug / Release（含 R8）均构建通过，`git diff --check` 通过；Android lint 的依赖离线缺失，
在线补取长时间未完成后已取消，因此 **lint 未验收**。ADB 无设备，真机步骤仍待执行。

### 真机步骤（只读，不应发生新增外跳）

1. 用 `-PhandoffDiagnostics=true` 构建再安装，确认 LSPosed QQ 作用域，强停 QQ 后启动。
2. 在 Xposed 日志中找 `[MINI-PROBE] installed`，确认是实际签名；`deferred` 或 `matched=0` 不是命中。
3. 每轮标记时间，分别点冷启动视频 A、后台复用视频 B；查看 `RESOLVED` / `CONTAINER` / `PAGE` 事件。
4. 对照 `__hook`、`__pid`、`__elapsedMs`、`__hookLoader`、`__modelIdentity`、`__activityIdentity`、
   `__taskId`、`__status`，确认路径和身份属于同一次操作，不要仅凭时间相近关联。
5. `__currentPath` / `__launchPath` / `__firstPagePath` / `__configPath` 分别比对；
   `__pathOrigin` 只是探针的显示选择，`__identityConflict=true` 必须记录，不能用于跳转。
6. 普通返回前台、预加载、两小程序交错，以及非 B 站/支付卡片作为负对照。
   `resume-snapshot-not-click` 明确不是新的卡片点击。

通过标准：冷/热点击都能定位本次内容 ID；来源和 status 语义清楚；探针不改变 QQ 小程序行为。
全部满足后再实现精确跳转、会话关联和成功态去重，不能把“APK 可构建”当成真机验收通过。

## 6. 浏览器并行打开回归

`BrowserDispatchTest` 新增 9 项纯 JVM 测试：外跳成功不调用原浏览器、失败/异常后只放行一次、
原方法异常不重试且清理重入标记、重载互调不重复尝试、外跳自身重入不循环、
跳板/目标 component 白名单、可信域名与完整视频路径匹配，以及伪造 URL/非 B 站放行。
它们测试实际 hook 使用的派发协调器，不验证 Android 生命周期、QQ 是否走该点或确认弹窗归属。

本轮共 16 项 hook 模块测试通过（9 + 原有 7），Debug / Release（R8）构建通过。
ADB 当前无设备，因此以下情况尚未实测：

1. 安装 debug APK，强停 QQ 后重开；确认两条 `HANDOFF dispatch installed` 安装日志。
2. 分别点 B 站卡片和明文链接，确认外跳：应有 `HANDOFF blocked QQ browser`，且系统
   ActivityTaskManager 日志没有该次操作的 QQBrowserActivity / QQH5BrowserActivity /
   QQTranslucentBrowserActivity 启动；从 B 站返回应回到聊天，不是内置浏览器。
3. 取消外跳确认：不应残留 QQ 内置浏览器，返回聊天。确认 UI 归属仍需核实；本修复不劫持弹窗。
4. B 站不可用/外跳启动抛异常：浏览器正常打开一次。不要仅靠 PackageManager 查询结果判断可用性。
5. 普通链接、QQ 钱包和其他小程序不变；连续点击无重复外跳/循环。

`startActivity` 请求被系统接受后，本模块无法同步获知用户是否取消确认；取消不自动回退打开网页。
此处只解决经 QQBrowserDelegationActivity 的视频交接；短链小程序解析与会话去重不在本修复范围。

### 2026-10-28 真机记录（成功确认路径通过）

OPPO PGZ110，QQ 9.3.75，LSPosed 2.2.1 / API 102：

- 初次安装发现 APK 为旧缓存产物，11:10:31 仍走旧 `HANDOFF hit`，QQBrowserActivity 已启动；
  该轮不能算修复验收。随后使用 `--no-build-cache --rerun-tasks` 强制重建，检查 APK dex
  包含 `HANDOFF dispatch installed` / `HANDOFF blocked QQ browser id=` 且无旧 `HANDOFF hit`。
- 重装 APK SHA256：`83da82209d53ef01d7fac4a19760f02da9afd0460828b8b1a151bdf8e9d81a9f`。
- 11:12:50 两个 `QBaseActivity.startActivity` 精确签名均挂载成功。
- 11:13:03.661 单条 `HANDOFF blocked QQ browser … via http+setPackage`；
  同次操作 11:13:03.581 打开跳板页、11:13:04.097 出现 QQ 的 QPublicTransFragmentActivity，
  11:13:07.991 启动 B 站 IntentHandlerActivity，11:13:10.801 进入视频详情。
- 本轮系统日志无 QQBrowserActivity / QQH5BrowserActivity / QQTranslucentBrowserActivity 启动；
  操作后 resumed 为 QQ SplashActivity，当前活动栈无上述内置浏览器。

因此该次成功确认外跳未并行启动内置浏览器；不据此推断弹窗具体实现归属，
也不代替取消确认、外跳失败、非 B 站及连续点击回归。以后出包安装前应核对 APK 中的新版本标记，
不能仅凭 Gradle UP-TO-DATE 或安装 Success 判断部署了新实现。

## 7. 轻量模式与响应计时（2026-10-28，本轮尚待真机）

默认包只安装浏览器交接，完整探针需 `-PhandoffDiagnostics=true`。
24 项 hook 测试覆盖派发行为、运行计划、并发防重装与反射缺失缓存；
实际构建开关、计时字段语义、旧轮基线与后续采样要求统一见 `PERFORMANCE.md`。
本轮发现字段不存在缓存的新增测试先失败，再实现缓存后通过；不能用主机微基准替代 Android 验收。
