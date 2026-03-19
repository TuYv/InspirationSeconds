## 1. CSS 变量 & 全局样式

- [x] 1.1 在 `global.css` 的 `:root` 中确认现有暗色变量完整
- [x] 1.2 在 `global.css` 中新增 `:root[data-theme="light"]` 块，写入暖白亮色变量（--bg / --surface / --surface2 / --surface3 / --text / --text-muted / --text-subtle / --border / --border-light / --accent / --accent-hover）

## 2. 持久化层

- [x] 2.1 在 `storage.ts` 中新增 `COLOR_SCHEME_KEY`、`saveColorScheme()`、`loadColorScheme()` 方法

## 3. Composable

- [x] 3.1 创建 `src/composables/useColorScheme.ts`，暴露 `isDark` ref 和 `toggle()` 方法，内部调用 storage 读写并同步到 `document.documentElement.dataset.theme`

## 4. App.vue 初始化

- [x] 4.1 在 `App.vue` 的 `onMounted` 中读取 `loadColorScheme()` 并初始化 `document.documentElement.dataset.theme`

## 5. EditorView 切换按钮

- [x] 5.1 在 `EditorView.vue` 顶栏复制按钮左侧新增切换按钮，使用 `useColorScheme` 的 `isDark` 和 `toggle()`
- [x] 5.2 按钮图标：夜间显示 `☀`（点击切白天），白天显示 `🌙`（点击切夜间），复用 `.btn-icon` 样式
