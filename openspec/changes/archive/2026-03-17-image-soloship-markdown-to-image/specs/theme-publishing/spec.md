## ADDED Requirements

### Requirement: 发布时生成缩略图
用户点击发布时，系统 SHALL 由前端自动截取预览区生成缩略图，随主题数据一起提交。

#### Scenario: 截图并上传
- **WHEN** 用户点击"发布到画廊"按钮
- **THEN** 系统 SHALL 先用 html2canvas 截取 #preview-root，scale: 1，质量 0.8，生成 JPEG base64
- **THEN** 将 base64 连同 name、description、css、preview_md、author_name 一起 POST 到 /api/themes
- **THEN** 后端解码 base64，写入文件，存储 thumbnail_url

#### Scenario: 截图超时处理
- **WHEN** html2canvas 超过 5 秒未完成
- **THEN** 系统 SHALL 跳过缩略图，以空 thumbnail 继续发布流程

### Requirement: 匿名发布流程
系统 SHALL 允许未登录用户发布主题，仅需填写主题名称（昵称可选）。

#### Scenario: 最小化发布
- **WHEN** 用户只填写主题名称，不填写描述和昵称
- **THEN** 系统 SHALL 成功发布，author_name 存为"匿名"

#### Scenario: 发布成功反馈
- **WHEN** 后端返回 201 Created
- **THEN** 系统 SHALL 显示成功提示，并提供"去画廊查看"跳转链接

#### Scenario: 重复名称处理
- **WHEN** 提交的主题名称与已有主题重名
- **THEN** 后端 SHALL 接受（不限制重名），画廊通过 id 区分

### Requirement: 后端主题存储 API

#### Scenario: 创建主题 POST /api/themes
- **WHEN** 接收到合法的主题创建请求（含 name、css、thumbnail base64）
- **THEN** 系统 SHALL 解码 base64 写入本地存储，返回 thumbnail_url
- **THEN** 插入 themes 表，返回 201 及新建主题完整信息

#### Scenario: 获取主题列表 GET /api/themes
- **WHEN** 请求携带 page、size、sort（star_count/created_at）、q（搜索词）参数
- **THEN** 系统 SHALL 返回分页结果，含 total、items 数组（每项含 id、name、author_name、star_count、thumbnail_url、created_at）

#### Scenario: 获取单个主题 GET /api/themes/:id
- **WHEN** 请求指定 id
- **THEN** 系统 SHALL 返回完整主题信息，包含 css 字段（用于 Fork 和应用）

#### Scenario: 点赞 POST /api/themes/:id/star
- **WHEN** 接收到点赞请求
- **THEN** 系统 SHALL 将 star_count + 1 并返回 200（不做重复校验，由前端 localStorage 控制）

### Requirement: 内置预置主题
系统 SHALL 在初始化时预置不少于 10 个主题，覆盖不同风格（简约、暗黑、温暖、商务等）。

#### Scenario: 预置主题始终可用
- **WHEN** 画廊页面加载
- **THEN** 预置主题 SHALL 出现在列表中，可被应用和 star，但不可被删除
