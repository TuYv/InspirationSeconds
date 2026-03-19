## ADDED Requirements

### Requirement: 用户可切换 UI 颜色模式
系统 SHALL 在 EditorView 顶栏提供一个切换按钮，允许用户在白天（亮色）和夜间（暗色）两种 UI 颜色模式之间切换。

#### Scenario: 默认为夜间模式
- **WHEN** 用户首次访问，localStorage 中无颜色模式记录
- **THEN** 应用 SHALL 以夜间（暗色）模式显示

#### Scenario: 点击切换到白天模式
- **WHEN** 当前为夜间模式，用户点击切换按钮
- **THEN** UI 颜色 SHALL 立即切换为白天（暖白）模式，按钮图标 SHALL 更新为对应状态

#### Scenario: 点击切换回夜间模式
- **WHEN** 当前为白天模式，用户点击切换按钮
- **THEN** UI 颜色 SHALL 立即切换为夜间（暗色）模式，按钮图标 SHALL 更新为对应状态

### Requirement: 颜色模式偏好持久化
系统 SHALL 将用户选择的颜色模式保存到 localStorage，下次访问时自动恢复。

#### Scenario: 切换后刷新页面保持模式
- **WHEN** 用户切换到白天模式后刷新页面
- **THEN** 应用 SHALL 恢复为白天模式，无需用户重新切换

#### Scenario: 切换后关闭再打开保持模式
- **WHEN** 用户切换颜色模式后关闭浏览器，再次打开
- **THEN** 应用 SHALL 恢复上次保存的颜色模式

### Requirement: 颜色模式全局生效
系统 SHALL 确保颜色模式切换对所有视图（EditorView、GalleryView、ThemeEditorView）生效，切换入口仅在 EditorView 顶栏。

#### Scenario: 从 EditorView 切换后进入 GalleryView
- **WHEN** 用户在 EditorView 切换为白天模式，然后导航至 GalleryView
- **THEN** GalleryView SHALL 以白天模式显示

### Requirement: 颜色模式不影响内容主题
系统 SHALL 确保 UI 颜色模式切换不改变 `.preview-wrap` 内的内容主题样式。

#### Scenario: 白天模式下内容主题不变
- **WHEN** 用户切换为白天模式，当前内容主题为"暗夜"
- **THEN** 预览区域 SHALL 仍以"暗夜"主题样式渲染，不受 UI 颜色模式影响
