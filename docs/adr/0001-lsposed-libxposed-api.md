# ADR-0001 · 使用 libxposed API 102（而非 legacy XposedBridge）

- 状态：已采纳
- 日期：2026-10-26

## 背景

LSPosed 同时支持两套 API：

1. **Legacy XposedBridge**（`de.robv.android.xposed`）：`assets/xposed_init` 入口，自带 `XposedHelpers`。
2. **Modern libxposed**（`io.github.libxposed:api`，当前 102）：`META-INF/xposed/java_init.list` 入口，拦截器链模型，**不提供** `XposedHelpers`。

## 决策

采用 **Modern libxposed API 102**。

## 理由

| 维度 | Legacy | Modern |
|---|---|---|
| 配置热更新 | `XSharedPreferences` 改完要杀进程 | **`getRemotePreferences()` 返回 SharedPreferences，有变更监听** |
| 热重载 | 无 | **`autoHotReload=true`，改代码不重启 QQ** |
| 作用域声明 | 管理器手勾 | `scope.list` + `staticScope` |
| 入口声明 | `assets/xposed_init` | `META-INF/xposed/java_init.list` |
| Hook 模型 | `findAndHookMethod` | `hook(Executable).intercept(chain -> …)` |

本项目**核心需求就是"规则热更新"与"频繁调试"**，这两点 Modern API 完胜。

## 代价

- 框架不提供 `XposedHelpers` → 需自行实现 `Reflect` 工具类（字段/方法查找 + 缓存），约 100 行。
- `minApiVersion=101` 要求用户的 LSPosed 较新（当前版本远超要求）。

## 后果

- `module.prop` 固定 `minApiVersion=101` / `targetApiVersion=102`。
- 所有 hook 代码直接使用 `XposedInterface` 的方法（`hook()` / `log()` / `getRemotePreferences()`）。
