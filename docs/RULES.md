# RULES · 规则模型

> 规则是**纯数据**。新增一个目标应用 = 加一条 JSON，引擎代码零改动。

## 1. 完整结构

```jsonc
{
  "id": "bilibili",            // 唯一标识
  "label": "哔哩哔哩",          // 显示名
  "enabled": true,
  "order": 10,                 // 越小越优先，首个命中即用
  "match": { ... },            // 匹配条件，全为 AND
  "extract": { ... },          // 变量提取
  "targets": [ ... ]           // 降级链
}
```

## 2. match

| 字段 | 类型 | 语义 |
|---|---|---|
| `miniAppId` | string[] | 精确匹配小程序 appId（最可靠） |
| `nameRegex` | string | 匹配小程序名称（appId 未知时兜底） |
| `arkApp` | string[] | 匹配 Ark 卡片 app 名，通常 `com.tencent.miniapp_01` |
| `scene` | int[] | 场景码，Ark 卡片 = `1036` |
| `linkType` | int[] | 可选，按来源类型过滤 |

规则：
- 同一条 rule 内所有条件为 **AND**
- 数组内多值为 **OR**
- 未填写（或 null）的字段视为**不限制**

## 3. extract

```jsonc
"extract": {
  "bvid": ["entryPath:[?&]bvid=([A-Za-z0-9]+)", "meta:\"bvid\"\\s*:\\s*\"([^\"]+)\""]
}
```

- 每个变量是一组正则，**按顺序尝试，首个命中即用**
- 前缀指定匹配源：
  - `entryPath:` → `MiniAppEvent.entryPath`
  - `meta:` → Ark 卡片 meta JSON
  - `link:` → `MiniAppEvent.link`
  - 无前缀 → 依次扫全部源
- 取**第 1 个捕获组**作为变量值
- 变量提取失败 → 依赖它的 target 被跳过

## 4. targets

```jsonc
[
  { "when": "bvid", "type": "http",     "tpl": "https://www.bilibili.com/video/{bvid}" },
  { "type": "deeplink", "pkg": "tv.danmaku.bili", "tpl": "bilibili://" },
  { "type": "package",  "pkg": "tv.danmaku.bili" },
  { "type": "passthrough" }
]
```

| type | 参数 | 行为 | 失败条件 |
|---|---|---|---|
| `http` | `tpl` | `ACTION_VIEW` + https，不指定 package（交给系统 App Links） | `ActivityNotFound` |
| `deeplink` | `tpl` + `pkg` | `ACTION_VIEW` + 自定义 scheme + `setPackage` | 包未装 / 无 Activity |
| `package` | `pkg` | 显式 `ACTION_MAIN` + `CATEGORY_LAUNCHER` + `setPackage` 直接 `startActivity`；**不用** `getLaunchIntentForPackage` 判定（查询类 API 会被包可见性过滤） | `ActivityNotFound` / 包未安装 |
| `passthrough` | — | 不动作 | 永远返回失败 |

- `when`：变量存在时才尝试，缺省表示总是尝试
- 顺序执行，**首个成功即停**
- **最后一项必须是 `passthrough`**（除非用户显式配置「绝不降级」）

## 5. 内置规则：哔哩哔哩

```jsonc
{
  "id": "bilibili",
  "label": "哔哩哔哩",
  "enabled": true,
  "order": 10,
  "match": {
    "miniAppId": ["1109937557"],
    "nameRegex": "(?i)哔哩哔哩|bilibili",
    "arkApp": ["com.tencent.miniapp_01"],
    "scene": [1036]
  },
  "extract": {
    "bvid": ["entryPath:[?&]bvid=([A-Za-z0-9]+)", "meta:\"bvid\"\\s*:\\s*\"([^\"]+)\""],
    "aid":  ["entryPath:[?&]aid=(\\d+)", "meta:\"aid\"\\s*:\\s*(\\d+)"],
    "ep":   ["entryPath:[?&]ep_id=(\\d+)", "meta:\"ep_id\"\\s*:\\s*(\\d+)"]
  },
  "targets": [
    { "when": "bvid", "type": "http", "tpl": "https://www.bilibili.com/video/{bvid}" },
    { "when": "aid",  "type": "http", "tpl": "https://www.bilibili.com/video/av{aid}" },
    { "when": "ep",   "type": "http", "tpl": "https://www.bilibili.com/bangumi/play/ep{ep}" },
    { "type": "deeplink", "pkg": "tv.danmaku.bili", "tpl": "bilibili://" },
    { "type": "package",  "pkg": "tv.danmaku.bili" },
    { "type": "passthrough" }
  ]
}
```

**依据**：
- `1109937557` — 小程序包内 JS `"qq"===f?"1109937557"`
- `MD5("1109937557") = 2421a9fa…` 与包目录名一致（交叉验证）
- 页面路径 — 包内分享逻辑拼接 `/pages/video/video?bvid=`
- App Links — `dumpsys package tv.danmaku.bili` 显示 `bilibili.com` / `b23.tv` 均已 verified

## 6. 新增应用：抖音（示例，appId 待实测）

```jsonc
{
  "id": "douyin",
  "label": "抖音",
  "enabled": true,
  "order": 20,
  "match": { "miniAppId": ["<待实测>"], "nameRegex": "(?i)抖音|douyin", "scene": [1036] },
  "extract": { "id": ["entryPath:[?&]aweme_id=(\\d+)"] },
  "targets": [
    { "when": "id", "type": "http", "tpl": "https://www.douyin.com/video/{id}" },
    { "type": "deeplink", "pkg": "com.ss.android.ugc.aweme", "tpl": "snssdk1128://" },
    { "type": "package",  "pkg": "com.ss.android.ugc.aweme" },
    { "type": "passthrough" }
  ]
}
```

## 7. 保护名单（优先级高于一切规则）

`miniAppId` 或 `name` 命中以下关键词 → **强制 passthrough**：

```
支付 / 登录 / 实名 / 充值 / 绑定 / 授权
qqpay / upay / 微信支付 / 银行卡
```

理由：这些小程序用同一套 `MiniAppController`，拦了会把 QQ 的支付登录搞坏。
