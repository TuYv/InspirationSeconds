## Context

EditorView.vue 是 md2img 的核心页面，包含 topbar 控件区、左侧 CodeMirror 编辑器、右侧 MarkdownPreview 三部分。当前布局完全基于 flexbox 固定分栏，无任何响应式处理。改造目标是在不引入新依赖、不拆分文件的前提下，实现桌面可拖拽分栏与移动端全屏切换。

## Goals / Non-Goals

**Goals:**
- 桌面端分隔条可拖拽，鼠标拖动实时更新左右百分比
- 移动端单视图模式，编辑/预览通过浮动按�tml切换
- 移动端 canvasWidth 响应式绑定容器宽度
- 移动端 topbar 折叠次要控件（⚙ 菜单）

**Non-Goals:**
- 触屏拖拽调整分栏（桌面功能，移动端已用 toggle 替代）
- 分栏比例持久化（刷新后恢复默认）
- tablet 横屏专属布局（≤768px 统一走移动端逻辑）
- 拆分组件或新增文件

## Decisions

**① 拖拽分隔条：纯 JS + CSS，不引入第三方库**

用 `mousedown` 在分隔条上启动拖拽，`mousemove` 在 `document` 上监听（避免鼠标移出分隔条丢失事件），`mouseup` 结束。左栏宽度存为 `leftPct: ref(45)`（百分比），用 `:style="{ width: leftPct + '%' }"` 绑定。分隔条宽度 6px，hover 变色提示可交互。

备选：`splitpanes` 库。拒绝理由：引入新依赖，bundle 增大，功能超出需要。

**② 移动端检测：CSS media query 为主，JS 辅助**

布局切换（堆叠 vs 分栏）纯 CSS `@media (max-width: 768px)` 处理。`canvasWidth` 的响应式绑定需要 JS：`onMounted` + `ResizeObserver` 监听预览容器宽度，`isMobile` 为 `true` 时将 `canvasWidth` 设为容器宽度。

**③ 移动端视图状态：`mobileView: ref<'edit'|'preview'>('edit')`**

用 CSS class 控制两个 pane 的显示/隐藏（`display: none`），而非 `v-if`，保持 CodeMirror 实例和预览实例始终存活，避免切换时重新挂载的性能开销。

**④ ⚙ 折叠菜单：简单的 `showSettings: ref(false)` + 绝对定位 dropdown**

不用任何 UI 库。点击 ⚙ 按钮切换 `showSettings`，下拉面板绝对定位在 topbar 下方，点击外部关闭（`@click.outside` 或 document click listener）。

## Risks / Trade-offs

- **拖拽最小宽度**：左右各设 20% 最小值，防止拖到极端导致布局破坏 → 在 `mousemove` handler 中 clamp
- **ResizeObserver 兼容性**：iOS 13.4+ 支持，覆盖主流机型，可接受
- **⚙ 菜单点击外部关闭**：用 `document.addEventListener('click')` + `stopPropagation`，需在 `onBeforeUnmount` 清理，避免内存泄漏
