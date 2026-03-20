## Requirements

### Requirement: 移动端全屏单视图模式
在移动端（视口宽度 ≤768px），编辑区与预览区 SHALL 不同时显示。页面 SHALL 默认显示编辑视图。

#### Scenario: 默认进入编辑视图
- **WHEN** 用户在移动端加载页面
- **THEN** 编辑器全屏显示，预览区隐藏

### Requirement: 移动端浮动切换按钮
移动端 SHALL 在页面右下角显示一个浮动按钮。当前为编辑模式时按钮 SHALL 显示预览图标（👁），当前为预览模式时 SHALL 显示编辑图标（✏）。按钮 SHALL 适配 iOS safe-area-inset-bottom。

#### Scenario: 从编辑切换到预览
- **WHEN** 用户点击浮动按钮（当前为编辑模式）
- **THEN** 预览区全屏显示，编辑器隐藏，按钮图标变为 ✏

#### Scenario: 从预览切换到编辑
- **WHEN** 用户点击浮动按钮（当前为预览模式）
- **THEN** 编辑器全屏显示，预览区隐藏，按钮图标变为 👁

### Requirement: 移动端 canvasWidth 自动适配
移动端 SHALL 自动将 canvasWidth 设置为预览容器的实际宽度，不显示宽度输入框。容器宽度变化时（如横竖屏切换）SHALL 自动更新。

#### Scenario: 页面加载时自动设置宽度
- **WHEN** 移动端用户加载页面
- **THEN** canvasWidth 自动设置为预览容器宽度，宽度输入框不可见

#### Scenario: 横竖屏切换时更新宽度
- **WHEN** 移动端用户旋转设备
- **THEN** canvasWidth 自动更新为新的容器宽度

### Requirement: 移动端 topbar 精简与折叠菜单
移动端 topbar SHALL 只显示：logo、主题选择、模式切换、导出 PNG。比例选择、宽度输入、浏览画廊 SHALL 收入 ⚙ 折叠菜单。复制按钮 SHALL 在移动端隐藏。

#### Scenario: 点击 ⚙ 展开次要控件
- **WHEN** 用户点击 topbar 中的 ⚙ 按钮
- **THEN** 折叠菜单展开，显示比例选择、宽度输入、浏览画廊

#### Scenario: 点击外部关闭折叠菜单
- **WHEN** 折叠菜单已展开，用户点击菜单外部区域
- **THEN** 折叠菜单收起
