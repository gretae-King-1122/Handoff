# Handoff

> 将 QQ 中支持的 B 站视频卡片和链接，交给 B 站 App 打开。

一个 **LSPosed** 模块。在 QQ 中点击支持的 B 站分享卡片或明文视频链接时，
交给 **哔哩哔哩 App**，避免同时启动 QQ 内置浏览器。保留原有外跳确认，外跳失败则放行浏览器。

**开发测试阶段，非稳定版。** 浏览器并行打开修复已完成一轮真机验证；最新轻量优化版已通过
24 项单元测试及 Debug / Release 构建，但仍待真机回归，尚无实测提速结论。

[下载／版本](https://github.com/lihuimin1145-coder/Handoff/releases) ·
[问题反馈](https://github.com/lihuimin1145-coder/Handoff/issues) ·
[更新记录](CHANGELOG.md)

```text
点击支持的卡片／链接 → 校验 B 站视频地址 → 请求打开 B 站 → 原有外跳确认 → 视频页
                                      └─ 启动失败 → 放行 QQ 浏览器
```

## 支持范围

| 场景 | 当前状态 |
|---|---|
| 经 QQ 浏览器跳板打开的 B 站分享卡片／明文视频链接 | 支持；派发前交接，避免并行启动内置浏览器 |
| `/video/av数字`、`/video/BV…` 视频地址 | 支持 `bilibili.com`、`www.bilibili.com`、`m.bilibili.com` 的标准 HTTP(S) 地址 |
| `m.q.qq.com/a/s/…` 小程序短链 | 尚不支持自动交接，不凭短链前缀盲拦 |
| `b23.tv` 短链 | 模块不主动解析；只有 QQ 已解析并走支持的明文视频派发路径时才可能命中 |
| 番剧、直播、专栏、其他 App | 尚未支持 |
| 设置界面、总开关、规则编辑／热更新 | 尚未实现；停用请在 LSPosed 中禁用模块后强停 QQ |

目标 App 为国内版哔哩哔哩 `tv.danmaku.bili`。国际版、概念版、分身及其他 QQ 版本未验证。
小程序探针仅供研究，不等于已经实现小程序跳转。

## 环境要求

- **已 root 的设备**，安装支持现代 libxposed API 的 LSPosed 兼容框架。
- 模块 `minSdk 26`，即 Android 8.0+；这只是编译下限，框架和 QQ 自身可能要求更高版本，
  **不代表所有 Android 8.0+ 设备均经过验证**。
- 真机验证环境：OPPO PGZ110、QQ 9.3.75、LSPosed 2.2.1 / API 102。
- 安装国内版哔哩哔哩，并为模块勾选 QQ `com.tencent.mobileqq` 作用域。

## 获取 APK

当前尚未发布 APK Release。可自行[构建](#构建)，后续发布的安装包会放在
[GitHub Releases](https://github.com/lihuimin1145-coder/Handoff/releases)，不会将 APK 放入源码目录。

> 当前 Release 构建仍使用本机 debug 签名，尚未建立正式版本的签名与升级方案。
> 不同机器自行构建的包可能因签名不同而无法覆盖安装；签名密钥不要提交到仓库。

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

**为什么没有立刻打开视频？**

总时长还包含人工确认、系统调度及 B 站冷启动。轻量模式减少的是模块探针与日志开销，
不能保证消除其他 App 的加载时间。

## 隐私

- 模块不主动请求网络、不上传日志，不提供遥测；QQ 与 B 站自身的联网行为不受此声明约束。
- 正常模式只检查浏览器跳板的目标视频链接，并输出简短安装／命中／耗时日志。
- 诊断包额外读取并记录启动字段及调用信息，不建议长期日用或直接公开完整日志。
- 本地配置、临时日志、APK、常见密钥与签名材料已加入 Git 忽略规则。

## 问题反馈

请在 [Issues](https://github.com/lihuimin1145-coder/Handoff/issues) 中提供：

- Android／系统版本、QQ 版本、LSPosed 版本、Handoff 提交或版本号。
- 卡片还是明文链接、视频 ID 类型、复现步骤、预期和实际结果。
- 已脱敏的 LSPosed Handoff 日志及复现时间。暂无设置页日志导出。

`HANDOFF dispatch installed` 表示具体派发签名已挂载；
`HANDOFF blocked QQ browser` 表示该次内置浏览器派发被阻断，**不是用户确认或视频加载完成的证明**。
请先删除 QQ 号、聊天内容、分享链接查询参数、Token 和设备标识，再公开日志或截图。

## 构建

需要 Git、JDK 21+ 和 Android SDK Platform 37；本机已用 JDK 26 验证。
使用仓库自带 Gradle Wrapper，首次构建需联网下载依赖。
通过 Android Studio 配置 SDK，或在本地 `local.properties` 中设置 `sdk.dir`，不要提交个人路径。

```powershell
git clone https://github.com/lihuimin1145-coder/Handoff.git
cd Handoff

# Windows PowerShell；默认关闭诊断探针
.\gradlew.bat :app:assembleDebug :app:assembleRelease

# 单元测试
.\gradlew.bat :module-hook-lsposed:testDebugUnitTest

# 仅研究小程序时开启完整探针
.\gradlew.bat :app:assembleDebug -PhandoffDiagnostics=true
```

macOS／Linux 将 `.\gradlew.bat` 换为 `./gradlew`；若无执行权限，先运行 `chmod +x gradlew`。

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

正常包应记录 `diagnostics=false`。诊断包仅适合排查问题，切回日常包时去掉构建参数并重新构建、安装。
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
