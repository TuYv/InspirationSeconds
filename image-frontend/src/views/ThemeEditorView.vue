<template>
  <div class="theme-editor-page">
    <header class="topbar">
      <router-link to="/themes" class="back-link">← 画廊</router-link>
      <div class="topbar-title">主题编辑器</div>
      <div class="topbar-actions">
        <button class="btn-secondary" @click="saveDraft" :disabled="publishing">保存草稿</button>
        <button class="btn-primary" @click="openPublishModal" :disabled="publishing">
          {{ publishing ? '发布中…' : '发布到画廊' }}
        </button>
      </div>
    </header>

    <div class="split-pane">
      <div class="pane pane-css">
        <div class="pane-label">CSS 编辑器 <span class="hint">选择器会自动限定在预览容器内</span></div>
        <CssEditor v-model="cssText" />
      </div>
      <div class="pane pane-preview">
        <div class="pane-label">实时预览</div>
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
        <h2>发布主题</h2>
        <label class="field">
          <span>主题名称 <em>*</em></span>
          <input v-model="themeName" placeholder="给你的主题起个名字" :class="{ error: nameError }" @input="nameError = false" />
          <span v-if="nameError" class="error-msg">主题名称不能为空</span>
        </label>
        <label class="field">
          <span>描述（可选）</span>
          <input v-model="themeDesc" placeholder="简单描述一下这个主题的风格" />
        </label>
        <label class="field">
          <span>你的昵称（可选）</span>
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
      ✓ 发布成功！
      <router-link to="/themes" class="toast-link">去画廊查看</router-link>
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

.topbar {
  height: 50px;
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
  flex-shrink: 0;
}
.back-link:hover { color: var(--text); }

.topbar-title {
  font-size: 14px;
  font-weight: 600;
}

.topbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

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

.pane-label {
  height: 32px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  gap: 8px;
}

.hint {
  font-weight: 400;
  text-transform: none;
  letter-spacing: 0;
  color: var(--border);
}

.pane-preview {
  background: var(--surface2);
}

.preview-scroll {
  flex: 1;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 24px;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 28px;
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal h2 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.field em {
  color: var(--danger);
  font-style: normal;
}

.field input {
  width: 100%;
}

.field input.error {
  border-color: var(--danger);
}

.error-msg {
  color: var(--danger);
  font-size: 11px;
}

.modal-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* Toast */
.toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  background: var(--surface);
  border: 1px solid var(--accent);
  color: var(--text);
  padding: 12px 18px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  gap: 12px;
  align-items: center;
  z-index: 200;
  box-shadow: 0 4px 20px rgba(0,0,0,0.4);
}

.toast-link {
  color: var(--accent);
  text-decoration: none;
}
</style>
