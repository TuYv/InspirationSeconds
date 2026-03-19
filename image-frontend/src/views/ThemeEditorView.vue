<template>
  <div class="theme-editor-page">
    <header class="topbar">
      <router-link to="/themes" class="back-link">
        <span class="back-arrow">←</span> 画廊
      </router-link>
      <div class="topbar-title">主题编辑器</div>
      <div class="topbar-actions">
        <button class="btn-secondary" @click="saveDraft" :disabled="publishing">
          保存草稿
        </button>
        <button class="btn-primary" @click="openPublishModal" :disabled="publishing">
          {{ publishing ? '发布中…' : '发布到画廊' }}
        </button>
      </div>
    </header>

    <div class="split-pane">
      <div class="pane pane-css">
        <div class="pane-label">
          <span class="pane-label-dot pane-label-dot-css"></span>
          CSS 编辑器
          <span class="pane-hint">选择器自动限定在预览容器内</span>
        </div>
        <CssEditor v-model="cssText" />
      </div>
      <div class="pane pane-preview">
        <div class="pane-label pane-label-preview-bar">
          <span class="pane-label-dot pane-label-dot-preview"></span>
          实时预览
        </div>
        <div class="preview-scroll">
          <MarkdownPreview
            ref="previewRef"
            :markdown="previewMarkdown"
            :css="cssText"
            :width="580"
          />
        </div>
      </div>
    </div>

    <!-- Publish modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h2>发布主题</h2>
          <button class="modal-close" @click="showModal = false">✕</button>
        </div>
        <label class="field">
          <span class="field-label">主题名称 <em>*</em></span>
          <input v-model="themeName" placeholder="给你的主题起个名字" :class="{ error: nameError }" @input="nameError = false" />
          <span v-if="nameError" class="error-msg">主题名称不能为空</span>
        </label>
        <label class="field">
          <span class="field-label">描述（可选）</span>
          <input v-model="themeDesc" placeholder="简单描述一下这个主题的风格" />
        </label>
        <label class="field">
          <span class="field-label">你的昵称（可选）</span>
          <input v-model="authorName" placeholder="留空则显示为匿名" />
        </label>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showModal = false">取消</button>
          <button class="btn-primary" :disabled="publishing" @click="doPublish">
            {{ publishing ? '发布中…' : '确认发布' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Success toast -->
    <div v-if="publishSuccess" class="toast">
      <span class="toast-icon">✓</span>
      发布成功！
      <router-link to="/themes" class="toast-link">去画廊查看 →</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import CssEditor from '../components/CssEditor.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import { generateThumbnail } from '../utils/exportImage'
import { getTheme, createTheme } from '../api/themes'

const route = useRoute()

const DEFAULT_CSS = `.preview-wrap {
  background: #ffffff;
  color: #333333;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 16px;
  line-height: 1.8;
  padding: 48px;
  box-sizing: border-box;
}

h1 {
  font-size: 1.8em;
  color: #111111;
  margin: 0 0 0.5em;
  font-weight: 700;
}

h2 {
  font-size: 1.4em;
  color: #222222;
  border-bottom: 1px solid #eeeeee;
  padding-bottom: 0.3em;
  margin: 1.2em 0 0.6em;
}

p { margin: 0.8em 0; }

blockquote {
  border-left: 4px solid #dddddd;
  margin: 1em 0;
  padding: 0.5em 1em;
  color: #666666;
  background: #fafafa;
}

strong { font-weight: 600; }
ul, ol { padding-left: 1.5em; margin: 0.5em 0; }
li { margin: 0.3em 0; }`

const PREVIEW_MD = `# 今日回响

写下属于你的，那些难以言说的时刻。

> 生活不是等待风暴过去，而是学会在雨中跳舞。

## 感悟

- 专注于当下
- **保持好奇**，永远不要停止探索

---

*继续前行。*`

const cssText = ref(DEFAULT_CSS)
const previewMarkdown = ref(PREVIEW_MD)
const previewRef = ref<InstanceType<typeof MarkdownPreview>>()

const showModal = ref(false)
const publishing = ref(false)
const publishSuccess = ref(false)
const themeName = ref('')
const themeDesc = ref('')
const authorName = ref('')
const nameError = ref(false)

onMounted(async () => {
  const forkId = route.query.fork
  if (forkId) {
    try {
      const theme = await getTheme(Number(forkId))
      cssText.value = theme.css
      if (theme.previewMd) previewMarkdown.value = theme.previewMd
      themeName.value = `${theme.name} (fork)`
    } catch (e) {
      console.warn('Fork 加载失败', e)
    }
  } else {
    // Load draft from localStorage if exists
    const draft = localStorage.getItem('theme_draft_css')
    if (draft) cssText.value = draft
  }
})

function saveDraft() {
  localStorage.setItem('theme_draft_css', cssText.value)
}

function openPublishModal() {
  showModal.value = true
}

async function doPublish() {
  if (!themeName.value.trim()) {
    nameError.value = true
    return
  }

  publishing.value = true
  try {
    let thumbnail: string | undefined
    const el = previewRef.value?.$el?.querySelector('#preview-root') as HTMLElement
    if (el) {
      try {
        const p = generateThumbnail(el)
        const result = await Promise.race([p, new Promise<null>((res) => setTimeout(() => res(null), 5000))])
        if (typeof result === 'string') thumbnail = result
      } catch (e) {
        console.warn('缩略图生成失败，跳过', e)
      }
    }

    await createTheme({
      name: themeName.value.trim(),
      description: themeDesc.value.trim() || undefined,
      css: cssText.value,
      previewMd: previewMarkdown.value,
      authorName: authorName.value.trim() || undefined,
      thumbnail,
    })

    showModal.value = false
    publishSuccess.value = true
    localStorage.removeItem('theme_draft_css')
    setTimeout(() => { publishSuccess.value = false }, 5000)
  } catch (e) {
    alert('发布失败，请重试')
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.theme-editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

/* ── Topbar ── */
.topbar {
  height: 54px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  gap: 12px;
}

.back-link {
  color: var(--text-muted);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  transition: color 0.15s;
}

.back-link:hover { color: var(--text); }

.back-arrow {
  font-size: 14px;
  opacity: 0.6;
}

.topbar-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.01em;
}

.topbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ── Split pane ── */
.split-pane {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pane-css {
  border-right: 1px solid var(--border);
}

/* ── Pane labels ── */
.pane-label {
  height: 30px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.pane-label-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pane-label-dot-css {
  background: var(--amber);
  box-shadow: 0 0 6px rgba(224, 168, 74, 0.35);
}

.pane-label-dot-preview {
  background: var(--success);
  box-shadow: 0 0 6px rgba(78, 203, 139, 0.3);
}

.pane-hint {
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
  color: var(--text-subtle);
  margin-left: 4px;
  font-size: 10px;
}

.pane-preview {
  background: var(--surface2);
}

.pane-label-preview-bar {
  background: var(--surface);
}

.preview-scroll {
  flex: 1;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 28px;
  background-image: radial-gradient(circle at 1px 1px, var(--border) 1px, transparent 0);
  background-size: 24px 24px;
}

/* ── Modal ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  backdrop-filter: blur(4px);
  animation: overlay-in 0.2s ease;
}

@keyframes overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal {
  background: rgba(20, 20, 30, 0.9);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 24px 28px 28px;
  width: 420px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255,255,255,0.04);
  animation: modal-in 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes modal-in {
  from { transform: scale(0.94) translateY(8px); opacity: 0; }
  to { transform: scale(1) translateY(0); opacity: 1; }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.02em;
}

.modal-close {
  width: 28px;
  height: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}

.modal-close:hover {
  background: var(--surface2);
  color: var(--text);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.01em;
}

.field em {
  color: var(--danger);
  font-style: normal;
}

.field input {
  width: 100%;
  font-size: 14px;
  padding: 9px 12px;
}

.field input.error {
  border-color: var(--danger);
}

.error-msg {
  color: var(--danger);
  font-size: 11px;
  font-weight: 500;
}

.modal-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 4px;
}

/* ── Toast ── */
.toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  background: var(--surface);
  border: 1px solid var(--border-light);
  color: var(--text);
  padding: 12px 18px;
  border-radius: var(--radius-lg);
  font-size: 13px;
  font-weight: 500;
  display: flex;
  gap: 10px;
  align-items: center;
  z-index: 200;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(78, 203, 139, 0.2);
  animation: toast-in 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes toast-in {
  from { transform: translateY(12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.toast-icon {
  color: var(--success);
  font-weight: 700;
  font-size: 14px;
}

.toast-link {
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
  transition: color 0.15s;
}

.toast-link:hover {
  color: var(--accent-hover);
}
</style>
