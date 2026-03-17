## ADDED Requirements

### Requirement: 主题卡片网格展示
画廊页面 (/themes) SHALL 以网格布局展示所有已发布主题，每个主题显示为一张卡片。

#### Scenario: 卡片内容
- **WHEN** 画廊页面加载完成
- **THEN** 每张卡片 SHALL 显示：缩略图、主题名称、作者昵称、star 数量、"应用"按钮

#### Scenario: 空画廊
- **WHEN** 数据库中没有已发布主题
- **THEN** 系统 SHALL 至少显示内置预置主题（不少于 10 个），不显示空白页面

### Requirement: 分页加载
画廊 SHALL 支持分页，默认每页 20 个主题。

#### Scenario: 加载更多
- **WHEN** 用户滚动到页面底部
- **THEN** 系统 SHALL 自动加载下一页主题（无限滚动），或显示"加载更多"按钮

#### Scenario: 首次加载状态
- **WHEN** 画廊正在请求第一页数据
- **THEN** 系统 SHALL 显示骨架屏占位，不显示空白

### Requirement: 排序和搜索
画廊 SHALL 支持按最热（star 数量）和最新（创建时间）排序，并支持按名称关键词搜索。

#### Scenario: 切换排序
- **WHEN** 用户选择"最热"排序
- **THEN** API 请求 SHALL 携带 `sort=star_count` 参数，结果按 star 降序排列

#### Scenario: 关键词搜索
- **WHEN** 用户在搜索框输入文字
- **THEN** 系统 SHALL 在输入停止 400ms 后发起搜索请求（防抖），过滤名称包含关键词的主题

### Requirement: 一键应用主题
用户 SHALL 能从画廊直接将主题应用到主编辑器。

#### Scenario: 应用主题
- **WHEN** 用户点击主题卡片上的"应用"按钮
- **THEN** 系统 SHALL 将该主题 CSS 存入 localStorage
- **THEN** 跳转到主编辑器 (/) 并自动应用该主题

#### Scenario: star 点赞
- **WHEN** 用户点击 star 图标
- **THEN** 系统 SHALL 调用 POST /api/themes/:id/star
- **THEN** star 数量 +1，图标变为已点赞状态
- **THEN** 将已点赞的主题 ID 存入 localStorage，防止同一浏览器重复点赞
