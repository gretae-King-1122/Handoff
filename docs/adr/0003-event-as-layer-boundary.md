# ADR-0003 · `MiniAppEvent` 作为唯一层间契约

- 状态：已采纳
- 日期：2026-10-26

## 背景

Hook 层必然会大量引用 QQ 的内部类（`MiniAppController`、`LaunchParam`、`MiniAppInfo`），
而这些类名/字段**随版本变化**。如果规则层、执行层也直接接触它们，一次 QQ 升级就要改遍全部代码。

同时，未来可能更换 hook 后端（Zygisk 自研 / 内核 binder 拦截）。

## 决策

Hook 层**必须**在边界处把 QQ 类型转换为中立数据类 `MiniAppEvent`，
下游所有层只依赖它，**不得 import 任何 `com.tencent.*`**。

```kotlin
data class MiniAppEvent(
    val source: Source,
    val processName: String,
    val miniAppId: String?,   // LaunchParam.miniAppId
    val name: String?,        // MiniAppInfo.name
    val entryPath: String?,   // LaunchParam.entryPath
    val fakeUrl: String?,
    val link: String?,
    val linkType: Int?,
    val scene: Int?,
    val via: String?,
    val rawExtras: Map<String, String>?
)
```

## 理由

1. **QQ 升级只影响一个模块**（`:module-hook-lsposed`）。
2. 换后端（Zygisk/内核）只重写这一个模块，规则/执行/存储/UI **零改动**。
3. `:module-rules` 可以变成纯 JVM 模块，用 JUnit 测——因为 `MiniAppEvent` 不依赖 Android。

## 代价

- 需要一层转换代码（`EventBuilder`），且字段缺失时只能给 null。
- 丢失了 QQ 类型的信息（如 `MiniAppInfo` 的完整对象）——但规则引擎本来也用不到。

## 后果

- 代码评审红线：`:module-rules` / `:module-executor` / `:module-storage` / `:module-ui` 中
  **出现 `com.tencent` 一律视为设计缺陷**。
- `HookBackend` 接口只暴露 `install(cl, processName)` 与 `Pipeline` 回调。
