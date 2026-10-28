# Handoff

> 把聊天卡片里的小程序，交接到原生 App 打开。

一个 **LSPosed** 模块。装在 QQ 里，好友分享的 Ark 卡片（带封面标题那种）点击后不再打开 QQ 内置小程序，
而是直接跳转到对应的原生 App —— 首期支持 **哔哩哔哩**。

```
点击卡片  →  Handoff 拦截  →  提取视频 ID  →  交给 B 站 App
```

---

## 为什么要它

朋友发来一个 B 站视频，点开却进了 QQ 的小程序：加载慢、功能残、还不能投屏。
Handoff 让这一步直接落到 B 站 App，体验和原生分享一致。

## 已支持

| 应用 | 小程序 appId | 目标 App | 状态 |
|---|---|---|---|
| 哔哩哔哩 | `1109937557` | `tv.danmaku.bili` | B 站分享卡片/明文视频链接的浏览器交接已真机验证；短链小程序仍在研究 |

> 当前实际可用路径：`QQBrowserDelegationActivity` → B 站 App；原小程序短链不凭前缀盲拦。
> 默认轻量模式只安装浏览器交接，完整发现探针需构建时显式开启，见 [性能说明](docs/PERFORMANCE.md)。

规则 JSON、多应用扩展与设置页仍是设计目标，尚未接入当前交接实现，详见 [`SPEC.md`](SPEC.md) §7.3。

## 环境要求

- Android 8.1+（模块 minSdk 26）
- **LSPosed**（需 root），作用域勾选 `com.tencent.mobileqq`
- QQ 9.3.75（其他版本可能可用，见 §兼容性）

## 安装

1. 安装 `Handoff.apk`
2. 打开 LSPosed → 模块 → 启用 Handoff
3. 作用域勾选 **QQ**
4. 强制停止 QQ 后重新打开

## 兼容性

模块通过**候选表 + 反射**定位 hook 点，QQ 小版本升级通常不受影响。
若失效，请附上 LSPosed 中 Handoff 的安装和命中日志（暂无设置页导出）。

## 隐私

- 不联网，不上报任何数据
- 正常模式只检查浏览器跳板的目标视频链接；诊断包另会记录启动字段，不建议长期日用
- 所有配置与日志都在本机

## 构建

```bash
./gradlew :app:assembleRelease
```

需要 JDK 21 + Android SDK 37。产物在 `app/build/outputs/apk/`。

日常包默认不挂载历史探针；研究时追加 `-PhandoffDiagnostics=true`。
安装后核对日志 `diagnostics=false/true` 与预期一致，不要仅凭安装 Success 判断包内容正确。

## 文档

| 文件 | 内容 |
|---|---|
| [`SPEC.md`](SPEC.md) | **工程规格说明 —— 唯一决策真源** |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 实现视图：契约签名、模块包结构、装配、线程模型 |
| `docs/HOOKS.md` | Hook 点清单与候选表 |
| `docs/RULES.md` | 规则 JSON schema |
| `docs/TESTING.md` | 单测与真机验证流程 |
| `docs/adr/` | 架构决策记录 |

## License

[Apache-2.0](LICENSE) © 2026 Handoff Project Contributors

## 免责

本项目仅供个人学习与自用。修改第三方应用行为存在被其风控标记的可能，
请自行评估风险，勿用于主力账号的关键场景。
