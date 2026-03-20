## Why

当前 md2img 编辑器采用桌面优先的固定 50/50 分栏布局，在手机上完全不可用：分栏各约 187px 宽，CodeMirror 编辑器拥挤，预览区无法正常渲染，复制按钮在 iOS 上失效。即使在桌面端，固定比例也限制了用户按需调整工作区的能力。

## What Changes

- 桌面端（>768px）：左右分栏之间增加可拖拽分隔条，用户可自由调整编辑区与预览区的比例，默认 45:55
- 移动端（≤768px）：编辑器与预览区改为全屏单视图模式，右下角浮动按钮（含 safe-area-inset 适配）在编辑/预览间切换
- 移动端 `canvasWidth` 自动绑定容器实际宽度，不再显示宽度输入框
- 移动端 topbar 精简：仅保留 logo、主题选择、模式切换、导出 PNG；比例选择、宽度输入、浏览画廊收入 ⚙ 折叠菜单
- 移动端隐藏复制按钮（iOS Safari 不支持 Clipboard API 写图片）

## Capabilities

### New Capabilities

- `resizable-split-pane`：桌面端可拖拽调整分栏比例
- `mobile-view-toggle`：移动端全屏编辑/预览切换，含浮动按钮与 topbar 折叠菜单

### Modified Capabilities

（无现有 spec 需要变更）

## Impact

- 仅修改 `image-frontend/src/views/EditorView.vue`
- 无新增文件，无 API 变更，无依赖变更
- `canvasWidth` 在移动端由响应式逻辑接管，桌面端行为不变
