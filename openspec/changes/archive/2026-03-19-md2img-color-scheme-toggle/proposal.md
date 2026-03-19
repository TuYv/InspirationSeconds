## Why

md2img 的编辑器 UI 目前只有暗色模式，在白天或明亮环境下使用时视觉疲劳。用户需要一个白天/夜间切换开关，偏好应持久化，下次打开保持记忆。

## What Changes

- EditorView 顶栏复制按钮左侧新增一个图标切换按钮（☀️/🌙）
- `global.css` 新增亮色（暖白）CSS 变量集，通过 `[data-theme="light"]` 覆盖
- `storage.ts` 新增颜色模式持久化方法
- `App.vue` 在 mount 时读取并应用上次保存的颜色模式
- 颜色模式仅影响 UI 层，`.preview-wrap` 内容主题完全隔离不受影响

## Capabilities

### New Capabilities
- `color-scheme-toggle`: UI 层白天/夜间模式切换，含持久化与所有视图联动

### Modified Capabilities

## Impact

- `image-frontend/src/assets/global.css`：新增亮色变量集
- `image-frontend/src/utils/storage.ts`：新增颜色模式读写
- `image-frontend/src/App.vue`：mount 时初始化颜色模式
- `image-frontend/src/views/EditorView.vue`：顶栏新增切换按钮
- 无新依赖，无 API 变更，无破坏性改动
