# Compose 管理界面

v0.8.0 将概览、诊断、回归、设置四页迁移到 Jetpack Compose，分别使用 Material 3 和 Miuix 组件。自动化测试覆盖界面及交互，但不能将仓库 v0.6.0 的真机验证结论直接视作本次界面的验证结果；本次发布未额外安装手机验收。

## 视觉与布局

v0.8.1 界面调整：第三页在界面中统一称为“日志”，其中保留手动测试与结果确认，自动运行信号仍在“诊断”页；内部 RegressionStore、持久化字段与已有数据不改名、不清空。设置不再展示“当前契约”技术面板，底层签名校验与适配规则保持不变。关于面板直接使用原有 xxxhdpi 启动图标的同源 PNG，不再另绘品牌标记；桌面图标不变。

默认 Material 风格使用 Google 蓝、纯色状态卡和中性背景，不使用渐变。大标题、统计卡、目的地卡片与详情形成明确层级。首页的框架信息及版本与设备卡片默认展开，仍可手动收起，并在切页、刷新及界面重建后保留选择；其他详情按需展开。浅色和深色跟随系统。

目的地卡片和详情面板通过 PackageManager 按国内版包名读取本机应用图标：B 站 `tv.danmaku.bili`、抖音 `com.ss.android.ugc.aweme`、快手 `com.smile.gifmaker`。普通图标与自适应图标都保留原色，在 IO 线程转为显示位图；按版本、更新时间和像素尺寸缓存，刷新发现更新或卸载时失效。未安装或读取失败时使用原占位标记，不下载网络图标。

四页均移除固定顶栏，状态栏下直接呈现页面标题，保留 16dp 顶部留白和系统安全区。状态在返回前台时及前台期间自动刷新。普通浮动导航和底部面板使用 Haze 背景采样模糊，文字与操作控件保持清晰。内容可滚过玻璃栏，并通过列表内边距保证首尾内容可完整访问。Android 12 及以上的硬件渲染支持真实模糊；旧系统和软件渲染使用高对比度半透明底色降级，不把内容整体模糊。

Material 默认底栏接入 SukiSU-Ultra 的液态玻璃分支，固定参考提交为 `8e89c3797b91b957b15d1760fc57591adfecc563`。直接移植 `FloatingBottomBar`、透镜 AGSL、组合背景、内阴影、拖动阻尼和交互高光；通过同款 `miuix-blur:0.9.4` 采样真实页面背景。保留 64dp 悬浮胶囊、56dp 可拖动指示器、按压形变、速度拉伸、折射、轻微色散和随设备倾斜变化的边缘高光。图标和文字常驻，选中内容通过独立背景图层着色。原生提权与徽章不在移植范围。来源和 GPL-3.0 组合分发要求见 [LICENSING.md](LICENSING.md)。

页面采样源同时记录不透明页面底色，底色位于 Haze 和液态采样修饰符的内部。Scaffold 自身的底色不在采样范围内，单独依赖它会让组合指示器透出另一份清晰页面文字。浅色和深色像素测试直接读取记录的图层，检查空白区域仍包含完整页面底色；该问题的真机视觉复核须在设备解锁后进行。

完整光学效果仅在 Android 13+ 硬件加速窗口启用，软件渲染展示不带折射的胶囊和滑动反馈；Android 8–12 使用原先的 Suki 普通悬浮底栏及 Haze 降级路径。虽然 `miuix-blur` 的清单标为 minSdk 33，应用仍保持 minSdk 26：只对该库设置清单合并覆盖，并在 API 33 以下阻止创建 LayerBackdrop 和液态底栏对象。Android 8 双风格测试检查这条分流。系统关闭动画、开启减少动效或使用大字体时，回到普通底栏以保留可读性和可操作性。

Miuix 直接接入 SukiSU 使用的 `miuix-ui-android:0.9.4`，使用库内 NavigationBar、Card、Switch 和 Button。它采用无描边分组卡、普通灰／纯黑背景、白／深灰状态卡、双行紧凑统计与目的地纵向分组列表，底栏贴边平铺。Material 保留 Google 蓝状态卡、两列统计和横向目的地卡，采用液态玻璃浮岛。两种风格在结构和组件上均有区别，都跟随系统浅／深色。

Material 在窄屏或较大字体下将三个目的地卡片改为纵向排列，极大字体下底栏回退为纵向图文布局以保留所有目的地。页面内容限宽 720dp，Material 胶囊底栏限宽 480dp；Miuix 底栏背景铺满屏幕，导航项目限宽 720dp。各页单独记忆滚动位置，再次点击当前导航项回到页首。系统栏、刘海和手势区域由 WindowInsets 避让。

设置末尾加入“关于此软件”，通过可滚动底部面板显示实际安装版本、项目维护者、开发语言、Compose 与组件库、模块接口、系统支持和开源致谢。项目主页、发行版及问题反馈链接分别打开 Handoff 的 GitHub 仓库、Releases 和 Issues；浏览器不可用时在面板内提示失败，关闭面板后保留设置页位置。

## 标准组件与交互动画

Material 使用移植的 SukiSU 悬浮底栏，其余复用 Material 3 的 OutlinedCard、Switch、FilterChip、RadioButton、进度指示器、Snackbar、AlertDialog 和 ModalBottomSheet。Miuix 使用其独立的导航、卡片、开关及主要按钮；诊断筛选、确认对话框等辅助组件仍共享 Material 实现。图标来自官方 Material Icons；Material 开启的开关显示 Check，Miuix 开关保留库内形变和滑动反馈。

两套组件库提供各自的点击、开关与选择反馈；页面过渡、内容展开以及移植底栏的标签展开使用 Compose Animation。没有重新绘制图标。

| 交互 | 反馈及目的 |
| --- | --- |
| 切换页面 | 短距离定向位移与淡变，帮助理解导航方向 |
| 展开详情 | AnimatedVisibility 改变内容高度，箭头淡变提示状态 |
| 刷新 | 标准进度指示器；后台刷新保留滚动位置及已展开内容 |
| 保存设置 | 写入期间禁用对应开关，读取真实保存结果后显示提示 |
| 查看目的地／外观 | 标准可拖动底部面板，保留上下文 |
| 回归进度／信号更新 | 平滑进度和列表项目动画，表达数据变化 |
| 清除／开始回归／确认通过 | 标准确认对话框，避免误操作 |

不播放错峰入场、循环光点或自制按压缩放。系统关闭动画时直接切换自定义过渡；本机“减少动态效果”可进一步简化页面、展开、图标和列表动画。标准组件自身遵循平台动画设置。

## 架构与状态约束

- `DashboardActivity` 只负责 Compose 入口、生命周期、分享和启动 QQ；仅在前台恢复状态周期刷新。
- `DashboardViewModel` 通过 StateFlow 提供状态，合并连续刷新请求，并串行处理存储访问。IO 在后台执行，保存失败显示提示，不伪造成功状态。
- `AndroidDashboardBackend` 复用原有框架服务、设置、HealthStore 和 RegressionStore。未改变视频交接规则或模块作用域。
- 页面选择、面板、筛选和折叠状态通过 Compose 保存；外观单独持久化在本机，不修改远程交接设置。
- 未连接框架或设置不可读时，交接开关只读并标明未知；抖音和快手仍为默认关闭、相互独立的可选试验。
- 激活判断仍由框架确认当前版本在 QQ 主进程运行；挂载、外跳请求被接受、实际作品打开三者分开呈现。
- 回归不预填结果。正向 PASS 仍需 RegressionStore 校验本轮匹配证据，并由用户确认实际结果。

## 验证和预览

运行：

```shell
./gradlew test :app:assembleDebug :module-ui:lintDebug
```

本次累计 219 项测试通过，其中 UI 模块 68 项；Debug APK 构建成功，UI lint 为 0 errors、4 条存储 API 建议。UI 测试覆盖四页导航与恢复、Activity / ViewModel 集成、只读和独立开关、存储失败与刷新合并、回归确认、两种外观的浅深色、320dp 窄屏、大字体、横屏、Android 8 双风格及动画关闭。新增测试确认液态底栏拖动释放后切换到目标页面、浅深色采样源包含页面底色、两张信息卡默认展开及手动收起状态恢复、关于面板的真实版本显示、GitHub 链接、浏览器失败提示和返回设置页；后续新增原版图标资源一致性、双风格日志文案和契约面板移除测试。测试报告以构建输出为准。

`ComposeDashboardTest` 从实际 Compose 树生成离线像素截图和逐帧页面过渡，原图在 `module-ui/build/outputs/compose-preview/`。可通过 `tools/export_compose_previews.py` 导出到 [`assets/screenshots/compose/`](assets/screenshots/compose/)：

- [四页预览](assets/screenshots/compose/pages.png)
- [浅深色与底部面板](assets/screenshots/compose/appearance.png)
- [Material / Miuix 并排对照](assets/screenshots/compose/styles.png)
- [页面过渡动图](assets/screenshots/compose/motion.gif)
- [关于此软件面板](assets/screenshots/compose/about-sheet.png)

拼图仅缩放原图并在截图外增加说明；底部面板预览按实际尺寸合成 Activity 与 Dialog 两个窗口的渲染像素，包含面板自身的遮罩，不改动控件和状态。它们是 Robolectric 离线渲染，不是真机截图；软件渲染可能使用玻璃降级效果，真实模糊及帧率还需硬件设备核验。默认预览保留“未确认激活”和未知设置，不模拟激活或视频成功。

`assets/screenshots/dual-style/` 为历史 Views 版真机素材：OPPO PGZ110、Android 16、QQ 9.3.75、LSPosed 2.2.1，拍摄于 2026-10-31，来自 v0.4.0 发布前同源开发包。原图仅裁去系统栏；它们不代表当前 Compose 实现。
