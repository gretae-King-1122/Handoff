# 快手作品链接交接（0.4.2-dev，未发布）

## 范围与边界

沿用抖音试验的本地解析接口和标准目标模型，保留独立快手开关。B 站与抖音的匹配、启动及失败策略不迁移、不重写。

- 支持 `https://v.m.chenzhongtech.com/fw/photo/{作品ID}` 和 `/fw/long-video/{作品ID}`。
- 新增 `https://v.kuaishou.com/{短链码}`，可带末尾斜杠及分享跟踪参数；标准化后不保留查询或片段。
- 精确匹配 host、路径、标准 HTTP(S) 端口，目标重建为 HTTPS；不接受用户信息、仿冒 host、编码路径绕过或非标准端口。
- 作品 ID 是不透明字符串；短链码不是作品 ID，不猜测或直接传给快手当作品 ID。
- 作品页若有 `photoId`，必须与路径一致；重复、冲突、空 ID 或编码后冲突的参数均拒绝。
- 重建作品地址仅保留路径与 `photoId`，不转发 `shareToken`、`userId`、`shareId`、`efid` 等跟踪信息。
- 不支持 `k.ksurl.cn`、其他短链、直播、个人页、极速版、分身或小程序；未知内容保留 QQ 流程。

## 派发与异步解析

```text
QQBrowserDelegationActivity → 已适配的显式 QQ 浏览器 Intent
  → KuaishouBrowserDispatch → KuaishouLinkParser（纯本地、无 I/O）
  → 总开关 + 独立快手开关
    ├─ 作品长链：KuaishouLauncher → ACTION_VIEW + com.smile.gifmaker
    └─ 短链：模块自己的 KuaishouResolveActivity（显示解析中，可返回取消）
        → 后台 KuaishouShortResolver，只读取 HTTP 重定向响应头
        → 合法作品页：仍用相同原生快手启动器
        → 解析/启动失败：一次性 PendingIntent 发送 QQ 原浏览器请求
```

- 保留原浏览器派发的同步重入防护、ClassLoader 补挂及严格调用方/目标检查；不增加全局网页 Hook。
- 长链仍仅在 Android 接受快手启动后阻断原 QQ 浏览器调用；同步失败执行原方法一次。
- 短链仅在模块解析页启动被接受后接管后续处理；同步启动解析页失败则执行 QQ 原方法一次。
- 解析页运行在模块进程，模块新增 INTERNET 权限；QQ UI 线程不做网络请求，也不持有延迟执行的 Xposed chain。
- 回退携带原 QQ 浏览器 Intent 的完整 extras，通过 QQ 创建的 immutable + one-shot PendingIntent 执行；直接去原浏览器，不再经过跳板解析形成循环。
- 导出的解析 Activity 在任何网络操作前验证短链、QQ 创建者及（API 31+）不可变 Activity PendingIntent，不能传入任意目标来代发 Intent。
- 只对固定 `v.kuaishou.com` 域名进行 HTTPS 请求，关闭自动重定向；每个 Location 都重新严格校验。
- 最多 3 次短链请求，解析预算约 6 秒，页面 7 秒超时兜底；不请求作品落地页、不下载正文、不访问未知重定向目标。
- 旋转屏幕保留同一解析任务；超时、重复/晚到回调仅处理一次。
- 返回键或离开解析页会取消，不在后台突然打开快手，也不自动打开 QQ 浏览器。模块被系统杀死或系统异步阻止启动，不能承诺自动回退。
- 仍保留系统外跳确认；用户取消、快手内异步错误或错误内容不属于此启动边界可自动判定的结果。

短链启动解析页只记录“正在解析”，不记为 `ACCEPTED`。只有解析成功且 Android 接受快手启动后，才由 ResultReceiver 回报快手交接证据。回退、取消、回退自身失败分别记录类型化原因。诊断不保存分享 URL、作品 ID 或查询 Token。

## 设备证据（2026-10-31）

环境：PGZ110 / Android 16 / QQ 9.3.75 / LSPosed 2.2.1 / 国内快手 14.6.50.49423。

- 包管理器确认 `/fw/photo/…` 与 `/fw/long-video/…` 均可匹配 `GrowthAppLinkActivity`；普通 `v.kuaishou.com` 网页 Intent 无接收者，不能直接照搬抖音启动方式。
- 实际短链 HTTP 302 可分别落到 `/fw/photo/…` 与 `/fw/long-video/…`；未将实际分享 Token 或账号信息写入源码、测试和报告。
- 0.4.1-dev 首次长链失败的原因是独立快手开关没有保存记录、默认为关闭。用户显式开启后确认进入对应快手作品。
- 用户授权后，19:11:40 已同签名覆盖安装 0.4.2-dev / versionCode 9，保留数据和开关并强停重开 QQ；LSPosed 确认新版加载且 diagnostics / boundaryDiagnostics 均为 false。
- 用户随后确认：QQ 点击 `v.kuaishou.com` 短链进入了对应快手作品。
- 此次用户确认不代替冷/热启动、断网回退、取消及所有其他机型的真机回归；也不为 B 站／抖音补填本次回归结果。

## 验收建议

1. 确认总开关和「快手链接（试验）」开启；快手默认关闭，抖音开启不自动开启快手。
2. QQ 分别点击已知作品长链和 `v.kuaishou.com` 短链，确认对应作品、没有并行打开 QQ 浏览器。
3. 关闭快手开关，确认恢复原 QQ 浏览器；普通网页、未知短链及其他平台不被误捕获。
4. 分别验证短链超时/断网、作品启动失败、返回取消、离开页面、连续点击和屏幕旋转。
5. 再测试抖音及 B 站已知可用链接，确认原逻辑保持。

## 自动化

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26'
.\gradlew.bat :module-api:test :module-rules:test `
  :module-hook-lsposed:testDebugUnitTest :module-executor:testDebugUnitTest `
  :module-ui:testDebugUnitTest :module-storage:testDebugUnitTest `
  :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease `
  '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=GBK'
```

210 项 JVM / Robolectric 测试通过（相对 0.4.1-dev 新增 23 项），Debug / Release 构建及 lint 通过。
覆盖短链解析、长视频、重定向限制、异常/取消、延迟与重复回调、解析页回退、旋转及不可变回退能力检查。
临时 `[DEBUG-browser-boundary]` 探针和摘要类/测试已清理；保留模块版本启动摘要便于确认安装版本。
