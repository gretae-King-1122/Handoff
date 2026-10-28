# 响应链路与轻量运行模式

## 现状和边界

当前可工作的交接在 `BrowserHandoff`，独立于尚未接入的规则/执行模块。
小程序 P0/P1、M2.1 以及 Ark/Struct/Template 等都是发现用探针，没有已验证的自动交接功能。
因此这轮不把空壳模块接入点击链路，也不改视频 URL/降级顺序或绕过系统与 QQ 确认。

2026-10-28 上一轮成功测试的时间线：

| 事件 | 手机本地时间 |
|---|---|
| QQ 发起跳板 Activity | 11:13:03.581 |
| 模块记录浏览器已阻断 | 11:13:03.661 |
| QQ QPublicTransFragmentActivity 启动 | 11:13:04.097 |
| B 站 IntentHandlerActivity 启动 | 11:13:07.991 |
| B 站视频详情启动 | 11:13:10.801 |

前 80ms **包含 QQ 跳板生命周期与模块外跳调用**，不是模块自身耗时。
后面约 7 秒包括确认等待、系统调度与 B 站启动，不能以此承诺移除日志就能缩短几秒。
不直接调用 B 站内部 Activity（版本兼容和确认语义风险），不通过提前跳过 QQ 生命周期来提速。

## 已实施

1. 默认 `handoffDiagnostics=false`，包括 debug APK。主进程只安装两个浏览器派发签名；
   辅助进程不安装发现探针，不再为普通运行扫描/加载 MiniSDK、枚举方法、生成字段快照/栈和完整事件日志。
   这不是停止辅助进程，也不改变其原有 QQ 行为。
2. 诊断包保留全部旧探针，但浏览器交接先安装，避免安装探针/大量日志延迟可用交接点。
3. 按 ClassLoader 身份原子防重装，重复 package-ready 不重复叠加 Hook；不同 loader 不混用。
   每个安装步骤独立容错，日志错误不改变步骤结果；setup complete 不冒充精确方法命中。
4. `Reflect` 缓存成功与不存在的查找结果；缓存含 Class 身份，不以类名替代。
   不存在的可选字段不再每次沿父类扫描并重复抛 NoSuchFieldException。
5. 诊断解析 Hook 的参数元数据提前到安装时获取。
6. 命中日志追加 `matchUs` / `launchUs` / `totalUs`，只在命中视频时输出一条摘要，不记录查询参数。
   - matchUs：匹配闭包入口至 URL 提取/校验结束，不含前面的 QQ 生命周期。
   - launchUs：外跳降级链同步调用耗时，不代表用户已确认或视频已加载。
   - totalUs：上述两段之和；不含日志输出、Hook 框架开销和后续 QQ finish。
   - installUs：该 loader 计划的同步安装耗时，不代表 QQ 整体启动时间。

正常模式关闭的只是**诊断能力**；短链小程序本来尚无可用自动交接，不能宣传为本轮新增或被移除的功能。
不要用跨点击时间窗吞点击来制造“去重提速”，防重入和失败放行仍按既有逻辑。

## 构建

```powershell
# 日常使用（默认轻量；曾出现旧构建产物，部署前强制构建）
.\gradlew.bat :app:assembleDebug --no-configuration-cache --no-build-cache --rerun-tasks

# 继续攻坚真实小程序时显式开启完整探针
.\gradlew.bat :app:assembleDebug -PhandoffDiagnostics=true --no-configuration-cache --no-build-cache --rerun-tasks
```

只接受 true/false，错误值直接构建失败。此为**构建开关**，不是已实现的设置页/热更新。
生成的 `BuildConfig.HANDOFF_DIAGNOSTICS` 与手机 `loaded ... diagnostics=false/true` 应一致。
正常包日志不应出现 P0/P1、MINI-PROBE、ARK/STRUCT 等探针安装记录（务必按本次进程/时间筛选旧日志）。

## 验证与下一轮采样

- 本轮 24 项单元测试：9 派发分支 + 4 运行计划 + 2 并发/loader 身份防重装 + 4 反射 + 5 字段提取。
  默认与诊断 debug 均可构建；默认 release 含 R8 构建通过。
- 本机 JDK 26 的临时反射微基准：与本次优化前 Reflect 比较，父类链含三层，12 次预热，
  9 批各 10,000 次读取取中位数；不存在字段 1415.4 → 9.0 ns/次，已存在字段 12.3 → 11.5 ns/次。
  **仅说明缓存消除了反复查找，不是 Android 真机性能结论**，也不是整个点击链路的加速比例。
- 修改后 ADB 无设备，尚未安装优化版；旧版 11:13 的真机通过不能替代本轮验收。

接机后先核对 diagnostics=false 与两个实际 dispatch installed 签名，分别采集卡片/链接、
冷/热启动各至少 10 次，记录 totalUs 中位数/P95，再单独统计确认后的 IntentHandler → 详情时间。
人工确认时长不得混进模块耗时；匹配/启动日志不足以证明实际打开视频，必须对照系统 START。
同时回归确认/取消、B 站不可用、非 B 站链接、重复点击和返回聊天无残留浏览器。
