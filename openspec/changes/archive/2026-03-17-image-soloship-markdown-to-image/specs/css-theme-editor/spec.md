## ADDED Requirements

### Requirement: CSS 编辑器界面
主题编辑器页面 (/themes/edit) SHALL 提供 CodeMirror 6 CSS 编辑器，支持语法高亮和基本自动补全。

#### Scenario: 打开编辑器
- **WHEN** 用户访问 /themes/edit（无参数）
- **THEN** 系统 SHALL 显示空白 CSS 编辑器和默认示例 markdown 预览

#### Scenario: 从画廊 Fork 主题
- **WHEN** 用户在画廊点击"编辑此主题"
- **THEN** 系统 SHALL 跳转到 /themes/edit?fork={id}，CSS 编辑器预填该主题的 CSS

### Requirement: CSS 实时预览
主题编辑器 SHALL 在右侧显示与主编辑器相同的预览容器，实时应用编辑器中的 CSS。

#### Scenario: CSS 变更实时反映
- **WHEN** 用户在 CSS 编辑器修改内容
- **THEN** 右侧预览 SHALL 在 300ms 内应用新 CSS

#### Scenario: CSS 语法错误不崩溃
- **WHEN** 用户输入语法不完整的 CSS（如 `h1 { color:`）
- **THEN** 预览 SHALL 忽略该 CSS 块，不抛出 JS 错误

### Requirement: CSS 自动 Scope 隔离
系统 SHALL 自动将用户编写的 CSS 选择器限定在预览容器内，防止影响 app 全局样式。

#### Scenario: 普通选择器隔离
- **WHEN** 用户写 `h1 { color: red; }`
- **THEN** 系统实际注入的 CSS SHALL 为 `#preview-root h1 { color: red; }`

#### Scenario: 多选择器隔离
- **WHEN** 用户写 `h1, h2, p { margin: 0; }`
- **THEN** 系统 SHALL 为每个选择器分别加前缀：`#preview-root h1, #preview-root h2, #preview-root p { margin: 0; }`

#### Scenario: .preview 根选择器
- **WHEN** 用户写 `.preview { background: white; }`
- **THEN** 系统 SHALL 处理为 `#preview-root.preview { background: white; }` 或直接作用于容器本身

### Requirement: 主题元信息填写
发布前用户 SHALL 填写主题名称，描述为可选。

#### Scenario: 必填项验证
- **WHEN** 用户点击"发布"但主题名称为空
- **THEN** 系统 SHALL 阻止发布并高亮名称输入框

#### Scenario: 作者昵称可选
- **WHEN** 用户未填写昵称
- **THEN** 系统 SHALL 使用"匿名"作为 author_name 存储
