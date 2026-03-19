## Context

md2img 前端（`image-frontend/`）是一个 Vue 3 + Vite 应用。UI 颜色完全由 `global.css` 中 `:root` 的 CSS 自定义变量驱动，所有组件通过 `var(--*)` 引用。内容主题通过独立的 CSS 注入机制（`cssScope.ts`）隔离在 `#preview-root` 内，与 UI 层完全解耦。

当前只有一套暗色变量，无亮色模式。

## Goals / Non-Goals

**Goals:**
- 在 EditorView 顶栏添加切换按钮，点击在白天/夜间之间切换
- 亮色模式使用暖白配色（已确认）
- 偏好持久化到 localStorage，下次访问恢复
- 所有视图（Editor / Gallery / ThemeEditor）自动跟随，无需各自处理

**Non-Goals:**
- 跟随系统 `prefers-color-scheme`（已明确不做）
- 修改或影响任何内容主题（.preview-wrap 内的 CSS）
- GalleryView / ThemeEditorView 添加独立切换按钮

## Decisions

### D1：用 `data-theme` 属性挂在 `<html>` 上

**选择**：在 `<html>` 元素上设置 `data-theme="light"` / 移除该属性（默认暗色）。

**CSS 实现**：
```css
/* global.css */
:root { /* 暗色变量（默认） */ }
:root[data-theme="light"] { /* 亮色变量覆盖 */ }
```

**为什么不用 class**：`data-theme` 语义更明确，不会与现有 CSS class 冲突，且与业界惯例一致（Tailwind、shadcn 等均采用此方案）。

**为什么挂在 `<html>` 而不是 `<body>`**：覆盖范围更完整，`:root` 伪类对应 `<html>` 元素。

---

### D2：App.vue 负责初始化，EditorView.vue 负责切换 UI

**初始化**：`App.vue` 的 `onMounted` 读取 localStorage → 设置 `document.documentElement.dataset.theme`。

**切换逻辑**：写成一个简单的 composable `useColorScheme`，暴露 `isDark` ref 和 `toggle()` 方法，同时负责持久化。EditorView 直接调用即可。

**为什么用 composable 而不是 Pinia store**：这个状态不需要跨组件共享复杂数据，composable 更轻量，项目目前也无 Pinia 依赖。

---

### D3：storage.ts 统一管理持久化键

新增 `COLOR_SCHEME_KEY = 'image_color_scheme'`，值为 `'light'` 或 `'dark'`（默认 `'dark'`）。与已有的 `THEME_KEY`、`STARRED_KEY` 保持一致的管理方式。

---

### D4：按钮样式沿用现有 `.btn-icon` 风格

顶栏已有 `.btn-icon` 按钮（复制、导出），切换按钮复用相同样式，图标用文字字符 `☀` / `🌙` 或 SVG icon，保持顶栏视觉一致。

## Risks / Trade-offs

- **SSR/首屏闪烁**：此项目是纯 CSR，无 SSR，不存在 FOUC 问题。
- **内容主题颜色侵染**：`.preview-wrap` 内的 CSS 经 `cssScope.ts` 隔离在 `#preview-root` 内，CSS 变量不会穿透。需在 `global.css` 亮色变量块中确保不覆盖 `#preview-root` 内的变量（目前内容主题不使用 `--bg` 等 UI 变量，已天然隔离）。
- **CodeMirror 编辑器主题**：编辑器使用 `oneDark` 主题，是 CodeMirror 内部样式，不受 CSS 变量影响。白天模式下编辑器仍为暗色，这是可接受的（编辑器本身也是内容区域）。若后续需要，可单独为亮色模式切换 CodeMirror 主题，但当前不在范围内。
