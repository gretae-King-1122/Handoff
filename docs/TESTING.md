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
