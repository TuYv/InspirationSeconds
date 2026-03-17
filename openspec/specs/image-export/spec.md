## ADDED Requirements

### Requirement: 导出为 PNG 文件
系统 SHALL 支持将预览区内容导出为 PNG 图片文件。

#### Scenario: 点击导出按钮
- **WHEN** 用户点击"导出 PNG"按钮
- **THEN** 系统 SHALL 使用 html2canvas 以 scale: 2 截取 #preview-root 容器
- **THEN** 生成的 PNG 文件 SHALL 自动触发浏览器下载，文件名格式为 `image-{timestamp}.png`

#### Scenario: 导出时的加载状态
- **WHEN** html2canvas 正在处理（通常 500ms~2s）
- **THEN** 导出按钮 SHALL 显示加载状态，防止重复点击

#### Scenario: 导出失败
- **WHEN** html2canvas 抛出异常
- **THEN** 系统 SHALL 显示错误提示，不静默失败

### Requirement: 复制图片到剪贴板
系统 SHALL 支持将预览区内容复制为图片到系统剪贴板。

#### Scenario: 点击复制按钮
- **WHEN** 用户点击"复制图片"按钮
- **THEN** 系统 SHALL 生成 PNG blob 并写入 Clipboard API
- **THEN** 显示"已复制"确认提示，持续 2 秒后恢复

#### Scenario: 浏览器不支持 Clipboard API
- **WHEN** 浏览器不支持 `ClipboardItem` API（如部分旧版 Safari）
- **THEN** 系统 SHALL 隐藏复制按钮，仅显示导出下载按钮

### Requirement: 导出分辨率
导出图片 SHALL 使用 2x 像素密度（devicePixelRatio: 2），确保在 Retina 屏幕上清晰显示。

#### Scenario: 分辨率验证
- **WHEN** 预览容器宽度为 600px
- **THEN** 导出 PNG 的实际像素宽度 SHALL 为 1200px
