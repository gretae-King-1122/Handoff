# Changelog

本项目的所有重要变更都记录在此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Changed

- README 应用图标改为抗锯齿透明圆角，适配浅色／深色页面背景；不修改应用内的启动图标资源。
- 精简 README：加入应用图标、Handoff 自身的最新版本／Android／libxposed API／Apache-2.0 许可证徽章，
  重新整理简介、功能与下载导航；移除额外真机页面、旧版截图、QQ 安装包静态核对、管理 App 长篇介绍和 Git 忽略规则说明，
  技术适配与构建说明折叠展示。仅调整文档及展示资源，不修改已发布 APK 或运行逻辑。

## [0.4.0] - 2026-10-31

### Release

- 发布双风格四页管理界面正式版，versionCode 升至 7；沿用原发布签名，可覆盖同签名的 v0.3.0／双风格开发包及更早正式包。
  上传 `Handoff-v0.4.0.apk` 和 `SHA256SUMS.txt`，Release 两个详细诊断开关保持关闭，不覆盖旧版发布资产。
- 本次主要更新管理界面及激活状态展示，未改变 QQ 视频交接／Hook 契约；更新后需强停重开 QQ 以加载新模块版本，
  旧版本诊断记录不作为当前版本的成功证据。正式发布不等于新增机型／QQ 版本或视频链路已通过真机认证。

### Added

- 新增独立首页，集中展示模块激活状态、框架名称／版本／版本号／libxposed API、QQ 作用域、
  Handoff／QQ／Android 版本、机型和国内版 B 站安装情况；原有信号与报告迁至独立诊断页。
- 激活状态基于框架运行目标列表，要求 QQ 主进程、当前模块版本及 `UP_TO_DATE` 状态；
  区分未连接、作用域未知／未授权、未运行、旧模块／加载异常和重新加载，不把仅连接或历史记录当作激活。
  框架元数据、作用域和运行列表的读取失败独立处理，交接总开关与加载状态分别展示。
- 新增 Material 3 Expressive／Miuix 两套视觉风格，在「设置 → 个性化 → 界面风格」切换；
  默认 Material，两套均跟随系统浅色／深色。外观独立保存在本机，框架离线仍可切换，后台保存成功后
  重建并保留当前页，不改交接开关、不重启 QQ；非法值回退，写入失败提示并尝试回滚内存选择。
- 首页增加真实挂载记录／回归轮次统计；无匹配环境的挂载记录显示 `—`，不伪造成功状态。
- 页面切换加入短距离上移／淡入，选中项加入轻量缩放反馈；快速切换、刷新和离开页面时
  取消并复位动画，系统关闭动画时直接切换，初始显示、恢复页面和普通轮询不播放切页动画。
- README 新增双风格设计说明，以及两套风格各五张 OPPO PGZ110 真机截图（首页、诊断、回归、设置、
  首页版本信息）；替换开发阶段离线预览，仅裁去系统状态栏和手势栏。原版 v0.3.0 三页真机截图单独折叠保留。
  截图展示实际激活／挂载记录和空回归矩阵，不生成模拟成功数据，不把管理页展示当作视频交接回归结果。

### Changed

- 管理界面采用 Material Components 1.13.0 与独立 Android View 导航，四页为首页／诊断／回归／设置，
  保留原有应用图标、诊断／矩阵／适配管理与严格交接逻辑。
- 参照 SukiSU-Ultra 的两套视觉语言，独立实现大标题滚动收起、状态卡和分组卡片：
  Material 使用主题色激活卡、虚线待确认卡、同心环装饰和圆角浮岛；
  Miuix 使用中性色分组、绿色激活卡、线条勾装饰及常规底栏。未复制其 GPL-3.0 源码或资产；参考版本见 UI_DESIGN.md。
- Material 浮岛导航中只有选中项横排显示图标＋文字，胶囊完整覆盖二者；Miuix 采用上图标下文字，
  所有标签常显、强调色标识选中，无填充胶囊。保留至少 48dp 触控区及无障碍选中语义，极大字体时
  自动降级纵向标签布局、允许增高。替代此前透明紧凑底栏方案。
- 导航底座与滚动区分开布局、不叠盖内容；两套风格均避让系统栏，宽屏居中限宽，末项可完整滚动到导航上方。
- 页面切换滚动回顶部，同页轮询保留位置；诊断当前环境校验补充框架版本匹配，避免旧框架记录混为当前信号。

### Notes

- 历史三页截图属于 v0.3.0 原版管理页；双风格截图来自发布前开发包（仍标 v0.3.0 / versionCode 6），
  该开发包已于 2026-10-31 在 OPPO PGZ110
  覆盖安装并启动 Handoff，回读设备 APK 与本次 Release 构建 SHA-256 一致；未卸载、未清数据、未重启 QQ。
  随后在真机实际切换两种外观、浏览四个管理页并截图；只改变外观设置，未修改交接开关或创建回归记录，
  截图后恢复 Material 首页。本次 v0.4.0 发布包升级版本号，未额外安装或执行视频点击端到端回归；界面展示不等于交接验证。
- 双风格源码通过 146 项 JVM／Robolectric 测试、Debug／Release 构建和 Release lint（0 错误、20 条警告）。
  覆盖两套风格四页的浅色／深色、横屏、320dp 窄屏与 1.5 倍字体离线渲染，2.5 倍字体导航降级，
  导航背景像素／选中语义、末项可见、系统栏避让、离线切换／恢复、外观写入失败及动画中间态／取消。
  lint 警告为工具链／依赖更新提示与原有启动图标检查；离线渲染测试不代表真机激活或视频交接成功。

## [0.3.0] - 2026-10-31

### Release

- 发布带管理 App 的 v0.3.0 正式版，versionCode 升至 6，可覆盖同发布签名的 v0.2.0 和
  0.3.0-dev；发布签名 APK 与 SHA-256 校验文件。正式发布不等于新增真机兼容性认证，验证边界见 Notes。

### Added

- 新增离线管理 App：原生深色三页 UI、桌面／模块设置入口，
  使用用户提供的图案制作多密度、自适应和单色应用图标。
- 兼容性自检展示框架连接、QQ／Android／作用域及四类真实入口信号；区分挂载、进入、
  外跳请求接受和用户确认，不将旧版本记录或版本档案当成本机已通过。
  主进程通过有界后台队列向只写 Provider 上报白名单枚举，Binder UID 限制为模块自身或 QQ。
  App 私有保存当前会话最多 96 条事件，不收集聊天、链接、视频 ID 或序列号，不申请网络权限或自动上传。
- 回归矩阵支持绑定环境的新轮次、四项用例、手动确认、历史／主动导出／清除，最多保留 20 轮。
  正向通过必须有本轮同模块、QQ 和框架的匹配交接事件；负向结果明确为用户手动确认。
  使用事件产生时间而非送达时间，避免延迟上报误算为本轮证据。
- 集中维护 QQ 版本档案和实际 Hook 使用的严格调用契约；未知版本保持严格探测，不下载执行远程适配代码。
  UI 的总开关和四入口开关通过 libxposed RemotePreferences 共享，框架不可用时只读；
  Hook 设置读取失败保留 QQ 原流程并报告配置不可用，不误报用户主动禁用。

### Changed

- Java 编译显式使用 UTF-8，避免 Windows 中文路径环境中 Gradle daemon 编码影响中文／Unicode 源码。
- 框架设置读取与本地回归／清除操作移至后台串行线程，不在界面绘制阶段做框架 IPC 或同步落盘；
  设置不可读时明确显示只读／状态未知，保存或清除失败不再静默冒充成功。

### Fixed

- 修复刷新合并遗漏显式刷新意图的问题，避免保存开关后界面一直停在禁用状态；
  保留未变化轮询不重建视图的优化，Activity 销毁时移除观察者，QQ 手动启动失败显示提示而不是崩溃。
- 修复旧会话 SESSION 延迟到达会覆盖新会话、重复 SESSION 会清空已有证据的问题，
  绑定会话产生时间与环境，拒绝同会话混入不同模块／QQ／框架版本。三个问题先由回归测试复现。
  回归矩阵读改写加串行保护，防止并发修改丢失结果。

### Notes

- 本轮 96 项 JVM／Robolectric 测试通过（含原有 Hook 回归、契约切换、开关／诊断异常隔离、
  Provider UID 与存储边界、回归证据约束、三页导航和离线原生渲染）；Debug／Release 构建及
  Release lint 通过，发布签名核验一致，Release 两个详细诊断开关均关闭。
- 新 UI 的装机视觉验收、QQ → Provider 上报和 LSPosed 设置共享端到端回归尚待测试设备；
  不将 JVM／Robolectric 测试或 APK 构建当作真机通过。已有 v0.2.0 真机记录不替代本开发版验收。

## [0.2.0] - 2026-10-30

### Release

- 发布小程序交接与运行时 ClassLoader 补挂正式版，versionCode 升至 4；沿用独立发布签名，
  可覆盖同签名 v0.1.0／v0.2.0-rc.1。Release 的两个诊断开关保持关闭，发布 APK 与 SHA-256 校验文件。
  本版支持下述已适配路径，不将“正式版”解释为所有 QQ 版本、机型或热更新时机均已验证。

### Added

- **小程序多入口互补**：保留已适配的服务入口，新增精确四参数 static void
  `MiniSDK.startMiniApp` 入口，以及主进程内的 `Activity.startActivityForResult(Intent, int, Bundle)` 派发前兜底。
  各入口独立安装，共用原有 App ID／视频路径校验与视频拉起链；命中摘要增加入口来源。
  派发兜底只接受已适配调用方到 QQ `AppBrandUI`／`AppBrandUI1~3` 的显式、非结果请求，
  拒绝转发结果、selector、非 SDK 模式、未知模型和非空回调。只在 Intent 副本上读取必要字段，
  不改原 Intent、不读登录字段、不提前结束 Activity，不以容器恢复通知触发跳转。
  该层不依赖 QQ 内部类的 Class 身份，但不替代 SDK／服务／浏览器入口对实际运行类的补挂。
- 新增默认关闭的定向小程序边界诊断（`-PhandoffBoundaryDiagnostics=true`，仅非 Release）：
  记录已适配方法是否进入、调用方契约以及身份／视频路径校验结果，并对少量 QQ 上游入口增加只读调用标记。
  不记录原始 App ID、视频 ID、分享链接或查询参数，不启用全量发现探针，不改变现有拦截条件；
  Release 强制关闭。用于区分 QQ 9.3.75 新设备上的入口差异与模型提取后静默放行。
  补充 Application 启动边界的新旧 ClassLoader／同名类身份对照，只记录类型和进程内身份摘要，
  用于验证 Tinker 热补丁是否让早期挂载点与实际运行类分离，不修改 QQ 补丁或其加载策略。
- 定向诊断验证：44 项 JVM 测试通过（含 4 项摘要隐私／拒绝原因测试），Debug／Release 构建与 Release lint 通过；
  确认仅诊断 Debug 开启边界开关，完整探针保持关闭，Release 的两个诊断开关均为 false。

### Fixed

- 修复 QQ 启动时更换 ClassLoader，导致早期 Hook 挂载成功却不拦截的问题。PJD110 的定向日志确认
  PathClassLoader 与实际 DelegateLastClassLoader 解析出的 QBaseActivity、浏览器跳板、MiniSDK 和
  LaunchManagerService 是不同的 Class 对象。保留 package-ready 挂载，并在主进程 Application 初始化前
  按实际 ClassLoader 补挂；按 Loader 身份及 Method 真实声明类身份防重，同名补丁类可独立安装，
  委托到同一类的不同 Loader 不重复 Hook，单个方法安装失败可重试。
  补挂失败不阻止 QQ 初始化，原始返回值／异常保留且只调用一次；不修改 QQ 加载器、不移除其补丁。
  浏览器调用方保留精确类名与 Activity 类型限制，但不再依赖早期跳板 Class 身份，兼容部分类型被补丁替换的情况。
  新增 8 项回归测试，覆盖真实同名异类、Application 前补挂、重复回调、空／相同 Loader、
  读取／安装／日志异常、委托 Loader、方法安装失败重试、并发防重及同名跳板类；核心回归先失败再修复。
- 根据本地 QQ 9.3.75／16580 APK 的启动队列与预加载页收尾逻辑，修正新增容器派发兜底对
  `isFinishing()` 的过严拒绝：仅在已校验的最终启动请求中允许正在结束但未销毁的过渡页，
  保留已销毁、未知路由／模型、回调和视频校验限制；早期 SDK／服务入口的生命周期策略不变。
  生命周期信息由实际 Hook 适配器传入可测试边界，新增 3 项测试，其中 2 项先复现漏拦再修复；
  同时覆盖已销毁时不读取 extras、启动失败仅放行一次和无效 payload 不触发跳转。
- 修正防重入标志在外层提取不到视频时仍覆盖整条原调用链、从而屏蔽内层入口的行为：
  外层未解析到视频时允许内层继续识别；同步调用链内同一视频仅尝试一次，实际拉起期间仍防重入。
  已成功交接的视频在该同步链的后续入口仍保持阻断，避免“防重复拉起”反而让 QQ 小程序再次打开。
  原方法返回值／异常保持原样且只执行一次，链路结束清理状态，不跨线程或跨点击缓存。
  回归测试先复现“外层模型不完整、内层已解析却无法交接”，再验证修复及容器兜底策略。

### Notes

- APK 静态核对：本地 QQ APK 的 SDK／服务四参数签名、`KEY_APPINFO` 常量及 SDK 容器字段与新增契约一致；
  已追踪普通启动 Runnable 和 QQ Activity 注入层到 framework 的原始派发路径。
  本地 APK 的 SHA-256 为 `ee9da76ee5c8cb3f53abf71e9835904348c870015af75c7bc9f1846567be1f4a`，
  与正常设备此前提取的 base APK 不同，也不含故障设备单独下载的 Tinker 补丁；不能据此确认热补丁根因。
- 补挂修复前的多入口阶段验证：57 项 JVM 测试通过（0 失败／0 错误），覆盖不同 ClassLoader 的同名模型、严格容器路由、
  外层缺信息的内层交接、同链路成功保持阻断／失败不重复、独立点击与异常放行。
  禁用构建缓存并重跑全部任务后，Debug／Release 构建及 Release lint 通过；两个诊断开关均为 false，
  Release 签名验证通过；该阶段尚未安装或重启设备，不把该轮构建当作真机验收。
- 运行时补挂修复验证：65 项 JVM 测试通过（0 失败／0 错误），Debug／Release 构建及 Release lint 通过。
  2026-10-30 PJD110、QQ 9.3.75／16580、LSPosed 2.2.1 环境中，13:21:25 已按实际 Application Loader
  补挂，13:21:30／39／54 记录浏览器拦截，13:21:46 记录 SDK 小程序拦截；用户确认成功。
  首轮点击使用签名 Release 修复包；正式版再补浏览器精确调用方的跨 Loader 回归与版本元数据；
  正式版 APK 出包后 ADB 设备已断开，未再覆盖安装，不能把首轮点击表述为最终 APK 的装机验收。
  不等于已完成所有负向真机回归、未知晚期 Loader 切换或所有机型验证，也不保证外部视频最终加载完成。

## [0.2.0-rc.1] - 2026-10-29

### Release

- 小程序视频交接预发布版，versionCode 升至 3，继续使用 v0.1.0 的独立发布签名。
  发布 APK 与 SHA-256 校验文件；Release 强制关闭诊断。完整真机回归仍待完成，暂不标记为稳定版。

### Added

- **B 站小程序视频启动前交接**：新增 `MiniAppHandoff`，精确挂载主进程
  `LaunchManagerService.startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)` 四参数 void 签名。
  从已解析的 SDK 模型校验 App ID `1109937557`，仅接受 `pages/video/video[.html]` 的 `bvid`／正整数 `aid`；
  优先采用非空 `launchParam.entryPath`，缺失才读 `firstPage.pagePath`，拒绝身份冲突、视频冲突、重复 ID 和未知页面。
  不解析 QQ 短链、不跨点击拼接缓存，不以容器恢复／页面通知触发跳转。
- 新增独立小程序协调器与 13 项 JVM 日志模型回放／策略测试，覆盖成功阻断、失败放行一次、原方法异常不重试、
  重入保护、连续独立点击、浏览器 guard 隔离，以及编码／伪造 URL／陈旧首屏候选等负向输入。

### Fixed

- 修正小程序交接第一版对启动 Bundle 的过严限制：真机定向日志确认 B 站身份与视频 ID 均匹配，
  但普通非空启动参数被误判为未知契约而放行。现允许普通附加参数，仅在覆盖 `KEY_APPINFO` 或携带
  `receiver`／ResultReceiver 时保守放行；新增 3 项路由回归测试，先复现非空元数据导致的漏拦再修复。
  增加不含参数值的放行原因摘要，删除临时诊断探针。
- 修复已适配的 B 站小程序视频路径只记录而不交接的问题：正常运行计划在浏览器交接后安装小程序交接，
  不再依赖诊断开关；单个安装点异常仍不影响其他安装点。
  仅在视频外跳请求被同步接受后跳过 QQ 原启动；启动失败、无视频 ID、未知启动契约均放行。
  保留 QQ／系统原有外跳确认；`blocked` 不代表用户确认或视频加载完成。

### Changed

- 抽取浏览器与小程序共用的视频拉起链（HTTP(S) + 指定包 → B 站 scheme）。
  浏览器保留原有首页降级；小程序不把拉起首页当作视频交接成功，视频启动均失败时放行 QQ 原路径。
- 小程序交接保守限定为 `QPublicTransFragmentActivity`、无模型覆盖和 ResultReceiver／回调的已适配路径。
  IPC／回调启动和其他 Activity 暂不阻断，不伪造回调、不手动结束共享 Activity，保留 QQ 调用方的预加载页收尾。
  正常日志仅输出视频 ID、字段来源与启动耗时，不记录短链、分享查询参数或登录字段。
- README 区分未发布源码与 v0.1.0 APK 的支持范围，补充小程序边界、失败语义和诊断设备实际 QQ 版本。

### Notes

- 2026-10-29 诊断日志已确认：QQ 9.3.5（versionCode 14540）在四参数 `startMiniApp` 执行前，
  同时具备 B 站 App ID、视频路径与原始短链。采用该实测入口，不把尚未挂载验证的 `doStartMiniApp` 当成实测结果。
- 2026-10-29 验证（含 Bundle 漏拦补修）：40 项单元测试通过（0 失败／0 错误），Debug／Release 构建及 Release lint 通过；
  使用 `--no-build-cache --rerun-tasks` 完整执行，Debug／Release 均确认 `HANDOFF_DIAGNOSTICS=false`。
- 2026-10-29 21:38（北京时间）修复版 Debug 包已覆盖安装并重开 QQ，日志确认 `diagnostics=false`，
  浏览器派发与四参数小程序交接签名均已挂载；这仅验证安装生效。
- 2026-10-29 Bundle 补修后真机正向验证：21:45:36、21:45:54、21:48:13（北京时间）三次记录
  `HANDOFF blocked QQ mini`，均从 `launchParam.entryPath` 提取同一 BV 号并以 `http+setPackage` 交接；
  用户确认进入 B 站 App。尚未单独核实对应视频页、预加载页收尾、冷／热启动组合、取消确认、
  失败放行和非 B 站负对照；补修后的浏览器端到端回归亦待完成，不据此宣称全场景验收。

## [0.1.0] - 2026-10-28

### Release

- 首个正式 GitHub Release，提供 APK 与 SHA-256 校验文件；发布身份使用仓库外的独立 PKCS12 签名。
- versionCode 升至 2，最低 libxposed API 修正为 102；Release 强制关闭诊断与调试标记。
- 不再默认使用 debug 签名发布；未配置签名时生成 unsigned APK。旧 debug 安装需先卸载模块再装正式包。
- 增加 Android 12+ 云备份与设备迁移的排除规则，保留旧系统的备份禁用设置；Release lint 已完成。
- 正式发布不代表所有场景已验收：取消确认、失败放行、负对照和最新轻量链路仍待完整真机回归。

### Changed

- 补齐公开仓库 README：说明实际支持范围、尚未发布 APK、安装及停用步骤、签名限制、构建测试方法和脱敏反馈要求；
  修正 Android 最低编译版本为 8.0（API 26），明确不代表框架兼容或全场景真机验证。
- **默认轻量交接模式**：debug/release 均不再默认挂载历史诊断探针，
  `-PhandoffDiagnostics=true` 可恢复完整发现链；交接 Hook 优先安装，loader 身份防重复安装。
  保留失败放行与外跳确认，不缓存失效 Intent 或 QQ Activity，不改变视频跳转降级链。
- 反射查找缓存缺失结果，诊断回调的参数类型元数据移至安装阶段；
  新增安装和命中分段耗时，正常路径只记短摘要。新增 8 项运行计划/防重装/缓存测试，合计 24 项通过。
  优化版尚未真机测量，不声明端到端提速比例。
- 精简仓库文档：保留 README、CHANGELOG 与 LICENSE，移除内部规格、架构、研究和测试记录；
  补充本地凭据及签名材料的 Git 忽略规则。

### Fixed

- **B 站外跳与 QQ 浏览器并行打开**：将 `BrowserHandoff` 的晚期 onCreate 观测改为
  `QBaseActivity.startActivity(Intent[, Bundle])` 的派发前拦截，仅限 QQBrowserDelegationActivity
  发往白名单内置浏览器的可信 B 站视频链接。外跳接受则不再启动浏览器，失败则放行一次；
  保留原生命周期与确认流程，新增重入保护、域名/路径校验和 9 项协调器测试。
  2026-10-28 真机一轮确认外跳通过：进入 B 站视频页，无 QQ 内置浏览器启动，返回 QQ；
  取消/失败与负对照仍待回归。

### Added

- **M2.1 小程序解析后只读探针**：观察 `MiniAppConfig` / SDK `MiniAppInfo`，
  P2 定向读取 `KEY_APPINFO`，覆盖 `doOnCreate` / `doOnNewIntent` / `doOnResume` 和 SDK 带路径事件。
  新增 `RESOLVED` / `PAGE` 来源、候选路径/身份冲突/单调时钟/Activity 与 loader 标识；
  通过已知 `MiniAppDexLoader` 补装动态类，按 Method 去重。新点只记录不阻断，替代旧全方法 Sweep。
- 新增 7 项纯 JVM 字段提取与跨 ClassLoader 反射缓存测试；修正反射同名类缓存串用和日志时间格式器线程安全。

- **M2 第一刀（旧实现，已由派发前拦截替代）** —— hook 继承的 `QQBrowserDelegationActivity#onCreate`，读 intent extra `url`，
  命中 `bilibili.com/video/(av\d+|BV…)` 则 `setPackage("tv.danmaku.bili")` 交给 B 站 App 并 `finish()` 掉跳板页。
  当时实测可进入 B 站对应视频；后续发现 QQ 浏览器可能已同步启动，不能以外跳成功作为阻断成功证据。
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
