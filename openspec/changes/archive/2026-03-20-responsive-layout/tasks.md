## 1. 桌面端可拖拽分隔条

- [x] 1.1 将 split-pane 左栏宽度改为 `leftPct` ref（默认 45），移除固定 `flex: 1`，改用 `width: leftPct + '%'`
- [x] 1.2 在两栏之间插入分隔条元素，添加 `mousedown` 事件启动拖拽
- [x] 1.3 实现拖拽逻辑：document `mousemove` 计算新比例（clamp 20%~80%），`mouseup` 结束拖拽
- [x] 1.4 添加分隔条 CSS：6px 宽，hover 变色，cursor: col-resize，拖拽时 user-select: none

## 2. 移动端响应式布局基础

- [x] 2.1 添加 `isMobile` computed（基于 `window.innerWidth <= 768`），在 `onMounted` 初始化并监听 resize
- [x] 2.2 添加 `mobileView: ref<'edit'|'preview'>('edit')`
- [x] 2.3 用 CSS `@media (max-width: 768px)` 将 split-pane 改为垂直 flex 布局，隐藏拖拽分隔条
- [x] 2.4 在移动端，用 CSS class 控制编辑/预览 pane 的显示隐藏（`display: none`，保留实例）

## 3. 移动端浮动切换按钮

- [x] 3.1 添加浮动按钮元素，仅在移动端显示（`@media` 控制）
- [x] 3.2 按钮点击切换 `mobileView`，图标随状态变化（编辑模式显示 👁，预览模式显示 ✏）
- [x] 3.3 按钮定位：`position: fixed`，右下角，`padding-bottom: env(safe-area-inset-bottom, 16px)` 适配 iOS

## 4. 移动端 canvasWidth 自动适配

- [x] 4.1 在预览容器上添加 `ref="previewContainerRef"`
- [x] 4.2 使用 `ResizeObserver` 监听容器宽度，`isMobile` 为 true 时将 `canvasWidth` 设为容器 `clientWidth`
- [x] 4.3 在 `onBeforeUnmount` 中断开 ResizeObserver

## 5. 移动端 topbar 精简与 ⚙ 折叠菜单

- [x] 5.1 用 `@media (max-width: 768px)` 隐藏：浏览画廊按钮、比例选择、宽度输入、复制按钮
- [x] 5.2 在 topbar 添加 ⚙ 按钮（仅移动端显示），`showSettings: ref(false)` 控制菜单展开
- [x] 5.3 实现折叠菜单下拉面板（绝对定位），包含：比例选择、宽度输入、浏览画廊链接
- [x] 5.4 点击菜单外部关闭：`document.addEventListener('click')` + `stopPropagation`，`onBeforeUnmount` 清理
