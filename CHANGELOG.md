# Changelog

本项目的所有重要变更都记录在此文件。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 工程骨架：7 个 Gradle 模块（`app` / `module-api` / `module-rules` / `module-executor` / `module-hook-lsposed` / `module-storage` / `module-ui`）
- 工程规格说明 [`SPEC.md`](SPEC.md)：记录全部已验证的 QQ 侧事实与设计决策

### Notes
- 设计阶段完成，代码尚未落地

## [0.1.0] - 计划中 · M1 探针版

### Added
- LSPosed 模块入口，`META-INF/xposed` 元数据
- 三个 hook 点（`MiniAppController#startAppByLink` / `MiniAppLauncher#openMiniApp` / `AppBrandUI#onCreate`），**只记录不阻断**
- 命中日志（Remote Files 通道）

### 验收
- 点击 Ark 卡片后，日志中可见真实的 `miniAppId` / `entryPath` / `scene`

## [0.2.0] - 计划中 · M2 单规则

### Added
- 哔哩哔哩规则（`1109937557`）
- 提取 `bvid` / `aid` / `ep_id` 并拼接 B 站 URL
- 四级降级链：精确 URL → deeplink → 包启动 → 放行

## [0.3.0] - 计划中 · M3 设置页

### Added
- 设置页：总开关、调试日志开关
- 规则列表编辑 / 导入 / 导出
- 命中日志查看与「从日志生成规则」

## [0.4.0] - 计划中 · M4 扩展

### Added
- 抖音等新增应用规则
- 长时间运行稳定性改进

---

[Unreleased]: about:blank
[0.1.0]: about:blank
[0.2.0]: about:blank
[0.3.0]: about:blank
[0.4.0]: about:blank
