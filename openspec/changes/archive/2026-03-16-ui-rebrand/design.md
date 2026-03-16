## Context

Change 1 建立了 JWT 鉴权和 Vue 3 前端框架；Change 2 新增了 Notion 数据浏览（热力图 + 详情）。当前 `/dashboard` 页作为登录后落点，内容为账号配置概览。品牌名「Notion Config Console」、蓝色主色调、橙光+蓝光背景渐变均与产品名「灵感妙记」和热力图的绿色系不协调。

## Goals / Non-Goals

**Goals:**
- 品牌、文案、配色统一到「灵感妙记」视觉语言
- 删除冗余的 Dashboard 页，精简导航
- Settings 页承载账号信息展示 + 配置修改两个职责
- NotionView 加载期间给用户友好反馈

**Non-Goals:**
- 深色模式
- 多主题切换
- 后端任何变更

## Decisions

### D1: DashboardView 删除策略

**决策**：直接删除文件，在 router 中移除 `/dashboard` 路由，并将所有引用替换为 `/notion`。

**理由**：Dashboard 唯一的实质内容是账号信息卡片，并入 Settings 后无剩余价值。保留空壳只增加维护成本。

---

### D2: Settings 页结构

**决策**：SettingsView 分两区——顶部只读账号卡片，卡片下方保持原 wizard（Step 1→2→成功）。账号卡片复用 Dashboard 的数据请求（`GET /api/user/me`），在 `onMounted` 一并拉取。

**理由**：方案 A（静态展示区 + 操作区分离）比折叠方案（方案 B）实现更简单，用户扫一眼就能看到当前状态再决定是否修改，交互路径更清晰。

账号卡片展示字段：
| 字段 | 说明 |
|---|---|
| 头像 + 昵称 | 身份识别 |
| 状态 badge（ACTIVE/INACTIVE）| 服务状态 |
| 应用类型 | NOTION 等 |
| 访客账号 | 是/否 |
| Notion 数据库 ID | 只读 + 复制按钮 |

---

### D3: 色彩系统

**决策**：方案 B——保留暖米底色，主操作色换为绿色系。

| 变量 | 旧值 | 新值 |
|---|---|---|
| `--accent` | `#d97706`（amber） | `#52a96e`（中绿） |
| `--accent-2` | `#2563eb`（blue） | `#2d7a4a`（深绿） |
| 背景渐变左上 | `#f0d9c6`（橙暖） | `#d4ead9`（暖绿） |
| 背景渐变右上 | `#dbe7ff`（蓝） | `#d6f0e8`（薄荷） |
| input focus border | `rgba(37,99,235,.45)` | `rgba(45,122,74,.45)` |
| input focus shadow | `rgba(37,99,235,.15)` | `rgba(45,122,74,.15)` |
| badge 默认背景 | `rgba(37,99,235,.12)` | `rgba(45,122,74,.12)` |
| badge 默认文字 | `#1d4ed8` | `#1a5c35` |

`--bg: #f6f1e9` 保留，`--surface`/`--border`/`--shadow`/`--ink`/`--muted` 保留。

---

### D4: NotionView monthLoading 状态

**决策**：在 `loadMonth()` 开始时设 `monthLoading = true`，`pageMap` 赋值后（第一阶段完成）立即设为 `false`，不等 block-count 并行请求。右侧面板优先级：

```
monthLoading=true          → 「✦ 正在加载你的灵感...」
monthLoading=false, 无选中  → 「点击左侧日期查看当天记录」
monthLoading=false, 有选中  → 内容加载态 / 内容 / 无记录提示
```

**理由**：block-count 是渐进式更新，用户已可与热力图交互，右侧不需要等待。

## Risks / Trade-offs

**[风险] Settings 页首次加载需两个职责的数据** → 缓解：`/api/user/me` 单接口同时满足账号卡片和 wizard 的预填充，只请求一次。

**[风险] 删除 DashboardView 后已有书签/链接失效** → 接受：产品早期用户少，`/dashboard` 不对外公开宣传。可在 router 加一条 `/dashboard` → `/notion` 的重定向守护保险。
