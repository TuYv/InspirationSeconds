## ADDED Requirements

### Requirement: 左右分栏布局
编辑器页面 SHALL 采用左右等宽分栏布局，左侧为 markdown 文本输入区，右侧为实时渲染预览区。

#### Scenario: 实时预览同步
- **WHEN** 用户在左侧编辑器输入或修改 markdown 内容
- **THEN** 右侧预览区 SHALL 在 300ms 内更新渲染结果

#### Scenario: 初始示例内容
- **WHEN** 用户首次打开页面（无 URL 参数）
- **THEN** 编辑器 SHALL 预填一段示例 markdown，包含标题、段落、引用块，帮助用户理解效果

### Requirement: Canvas 尺寸选择
系统 SHALL 提供预设 canvas 尺寸和自定义宽度选项，控制预览区的输出比例。

#### Scenario: 预设尺寸切换
- **WHEN** 用户从尺寸选择器选择 1:1、4:5、16:9 或 9:16
- **THEN** 预览容器 SHALL 立即按对应比例调整，内容重新排版

#### Scenario: 自定义宽度
- **WHEN** 用户输入自定义像素宽度（如 800px）
- **THEN** 预览容器 SHALL 按该宽度固定，高度自适应内容

### Requirement: Markdown 渲染规范
系统 SHALL 使用 markdown-it 按标准 CommonMark 规范解析 markdown。

#### Scenario: 基础元素渲染
- **WHEN** markdown 包含标题（# ## ###）、段落、粗体、斜体、引用块、无序列表、有序列表、代码块
- **THEN** 均 SHALL 正确渲染为对应 HTML 元素

#### Scenario: 不支持的扩展
- **WHEN** markdown 包含数学公式（$...$）或自定义指令
- **THEN** 系统 SHALL 原样显示文本，不报错

### Requirement: 主题选择器
编辑器页面 SHALL 提供主题选择下拉，显示当前应用的主题名称。

#### Scenario: 切换主题
- **WHEN** 用户选择不同主题
- **THEN** 预览区 SHALL 立即应用新主题 CSS，不需要刷新页面

#### Scenario: 跳转画廊
- **WHEN** 用户点击"浏览画廊"
- **THEN** 系统 SHALL 跳转到 /themes 画廊页面，返回时恢复之前选择的主题
