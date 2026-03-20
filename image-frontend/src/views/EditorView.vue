<template>
  <div class="editor-page">
    <!-- Top bar -->
    <header class="topbar">
      <div class="topbar-left">
        <span class="logo">
          <span class="logo-md">md</span><span class="logo-arrow">→</span><span class="logo-img">img</span>
        </span>
      </div>

      <div class="topbar-sep"></div>

      <div class="topbar-center">
        <!-- Theme selector -->
        <div class="ctrl-group">
          <label class="ctrl-label">主题</label>
          <select v-model="activeThemeName" @change="onThemeChange" class="ctrl-select">
            <optgroup label="内置主题">
              <option v-for="t in builtinThemes" :key="t.name" :value="t.name">{{ t.name }}</option>
            </optgroup>
            <option v-if="customTheme" :value="customTheme.name">{{ customTheme.name }} ✦</option>
          </select>
          <router-link to="/themes" class="btn-secondary gallery-btn hide-mobile" style="text-decoration:none">浏览画廊</router-link>
        </div>

        <div class="topbar-sep-sm hide-mobile"></div>

        <!-- Canvas size -->
        <div class="ctrl-group hide-mobile">
          <label class="ctrl-label">比例</label>
          <select v-model="aspectRatio" class="ctrl-select ctrl-select-sm">
            <option value="auto">自适应</option>
            <option value="1:1">1:1</option>
            <option value="4:5">4:5</option>
            <option value="16:9">16:9</option>
            <option value="9:16">9:16</option>
          </select>
          <input
            v-model.number="canvasWidth"
            type="number"
            min="300"
            max="1600"
            step="50"
            class="ctrl-input-width"
            placeholder="宽度 px"
          />
        </div>
      </div>

      <div class="topbar-sep"></div>

      <div class="topbar-right">
        <!-- ⚙ settings button — mobile only -->
        <div class="settings-wrapper show-mobile">
          <button class="btn-secondary btn-settings" @click.stop="showSettings = !showSettings" title="设置">
            ⚙
          </button>
          <div v-if="showSettings" class="settings-dropdown" @click.stop>
            <div class="settings-item">
              <label class="ctrl-label">比例</label>
              <select v-model="aspectRatio" class="ctrl-select ctrl-select-sm">
                <option value="auto">自适应</option>
                <option value="1:1">1:1</option>
                <option value="4:5">4:5</option>
                <option value="16:9">16:9</option>
                <option value="9:16">9:16</option>
              </select>
            </div>
            <div class="settings-item">
              <label class="ctrl-label">宽度</label>
              <input
                v-model.number="canvasWidth"
                type="number"
                min="300"
                max="1600"
                step="50"
                class="ctrl-input-width"
                placeholder="宽度 px"
              />
            </div>
            <div class="settings-item">
              <router-link to="/themes" class="btn-secondary gallery-btn" style="text-decoration:none" @click="showSettings = false">浏览画廊</router-link>
            </div>
          </div>
        </div>

        <button class="btn-secondary btn-theme-toggle" :title="isDark ? '切换到白天模式' : '切换到夜间模式'" @click="toggle">
          <span class="btn-icon">{{ isDark ? '☀' : '🌙' }}</span>
        </button>
        <button class="btn-secondary btn-copy hide-mobile" :disabled="exporting" @click="copyImage">
          <span class="btn-icon">⊞</span>
          {{ copied ? '已复制 ✓' : '复制' }}
        </button>
        <button class="btn-primary btn-export" :disabled="exporting" @click="downloadImage">
          <span class="btn-icon">↓</span>
          {{ exporting ? '导出中…' : '导出 PNG' }}
        </button>
      </div>
    </header>

    <!-- Main split pane -->
    <div class="split-pane" :class="{ dragging: isDragging }">
      <div class="pane pane-editor" :class="{ 'mobile-hidden': isMobile && mobileView === 'preview' }" :style="isMobile ? {} : { width: leftPct + '%' }">
        <div class="pane-label">
          <span class="pane-label-dot pane-label-dot-edit"></span>
          Markdown
        </div>
        <div class="editor-body">
          <MarkdownEditor v-model="markdownText" :is-dark="isDark" />
        </div>
      </div>

      <!-- Drag divider — desktop only -->
      <div class="pane-divider" @mousedown="startDrag"></div>

      <div class="pane pane-preview" ref="previewContainerRef" :class="{ 'mobile-hidden': isMobile && mobileView === 'edit' }">
        <div class="pane-label pane-label-right">
          <span class="pane-label-dot pane-label-dot-preview"></span>
          预览
          <span class="pane-label-hint">{{ canvasWidth }}px · {{ aspectRatio === 'auto' ? '自适应' : aspectRatio }}</span>
        </div>
        <div class="preview-scroll">
          <MarkdownPreview
            ref="previewRef"
            :markdown="markdownText"
            :css="activeCss"
            :width="canvasWidth"
            :aspect-ratio="aspectRatio"
          />
        </div>
      </div>
    </div>

    <!-- Floating toggle button — mobile only -->
    <button class="fab-toggle show-mobile" @click="mobileView = mobileView === 'edit' ? 'preview' : 'edit'" :title="mobileView === 'edit' ? '查看预览' : '返回编辑'">
      {{ mobileView === 'edit' ? '👁' : '✏' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import { exportAsPng, copyToClipboard, supportsClipboardImage } from '../utils/exportImage'
import { loadActiveTheme, saveActiveTheme } from '../utils/storage'
import { useColorScheme } from '../composables/useColorScheme'

const { isDark, toggle } = useColorScheme()

// ── builtin themes (css is loaded from the seed, here we use the same CSS inline)
const builtinThemes = [
  { name: '简约白', css: '.preview-wrap{background:#fff;color:#333;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;font-size:16px;line-height:1.8;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#111;margin:0 0 .5em;font-weight:700}h2{font-size:1.4em;color:#222;border-bottom:1px solid #eee;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0}blockquote{border-left:4px solid #ddd;margin:1em 0;padding:.5em 1em;color:#666;background:#fafafa}code{background:#f5f5f5;padding:2px 6px;border-radius:3px;font-size:.88em}strong{font-weight:600;color:#111}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '暗夜', css: '.preview-wrap{background:#1a1a2e;color:#e0e0e0;font-family:"SF Mono",monospace;font-size:15px;line-height:1.9;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#7ec8e3;margin:0 0 .5em}h2{font-size:1.4em;color:#a8d8ea;border-bottom:1px solid #2d2d4e;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0}blockquote{border-left:3px solid #7ec8e3;margin:1em 0;padding:.5em 1em;color:#9a9abf;background:#16213e}code{background:#0f3460;color:#7ec8e3;padding:2px 7px;border-radius:4px;font-size:.88em}strong{color:#7ec8e3}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '暖纸', css: '.preview-wrap{background:#fdf6e3;color:#5c4a2a;font-family:Georgia,serif;font-size:16px;line-height:1.9;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#8b4513;margin:0 0 .5em;font-style:italic}h2{font-size:1.4em;color:#a0522d;border-bottom:2px solid #d4a96a;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0}blockquote{border-left:4px solid #d4a96a;margin:1em 0;padding:.5em 1.2em;color:#8b7355;font-style:italic;background:#fdf0c8}code{background:#f0e6c0;padding:2px 6px;border-radius:3px;font-size:.88em;color:#8b4513}strong{color:#8b4513;font-weight:700}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '森林', css: '.preview-wrap{background:#f0f7f0;color:#2d4a2d;font-family:"Helvetica Neue",Arial,sans-serif;font-size:16px;line-height:1.8;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#1a3a1a;margin:0 0 .5em;font-weight:800}h2{font-size:1.4em;color:#2d5a2d;border-bottom:2px solid #7ab77a;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0}blockquote{border-left:4px solid #5a9a5a;margin:1em 0;padding:.5em 1em;color:#4a7a4a;background:#e0f0e0}code{background:#d5ead5;padding:2px 6px;border-radius:3px;font-size:.88em;color:#1a5a1a}strong{color:#1a4a1a;font-weight:700}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '深海', css: '.preview-wrap{background:#0d1b2a;color:#c8d8e8;font-family:-apple-system,sans-serif;font-size:16px;line-height:1.8;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#4fc3f7;margin:0 0 .5em}h2{font-size:1.4em;color:#81d4fa;border-bottom:1px solid #1a3a5a;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0}blockquote{border-left:3px solid #4fc3f7;margin:1em 0;padding:.5em 1em;color:#90a4ae;background:#0a2540}code{background:#0a2540;color:#4fc3f7;padding:2px 7px;border-radius:4px;font-size:.88em}strong{color:#4fc3f7}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '玫瑰', css: '.preview-wrap{background:#fff5f7;color:#4a2030;font-family:"Segoe UI",sans-serif;font-size:16px;line-height:1.8;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#c0406a;margin:0 0 .5em;font-weight:700}h2{font-size:1.4em;color:#d4607a;border-bottom:2px solid #f4a0b5;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0}blockquote{border-left:4px solid #f4a0b5;margin:1em 0;padding:.5em 1em;color:#8a5060;background:#ffe0e8;font-style:italic}code{background:#fce0e8;padding:2px 6px;border-radius:3px;font-size:.88em;color:#c0406a}strong{color:#c0406a;font-weight:700}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '极简黑', css: '.preview-wrap{background:#000;color:#fff;font-family:"SF Mono",monospace;font-size:15px;line-height:2;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#fff;margin:0 0 .5em;font-weight:900;letter-spacing:-.02em}h2{font-size:1.4em;color:#ccc;border-bottom:1px solid #333;padding-bottom:.3em;margin:1.2em 0 .6em}p{margin:.8em 0;color:#ddd}blockquote{border-left:3px solid #fff;margin:1em 0;padding:.5em 1em;color:#888}code{background:#1a1a1a;padding:2px 6px;border-radius:2px;font-size:.88em;color:#aaa;border:1px solid #333}strong{color:#fff;font-weight:900}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0;color:#ddd}' },
  { name: '复古报纸', css: '.preview-wrap{background:#f4f0e0;color:#2a2018;font-family:Georgia,serif;font-size:16px;line-height:1.7;padding:48px;box-sizing:border-box;border:2px solid #c8b89a}h1{font-size:2em;color:#1a1008;margin:0 0 .3em;font-weight:900;text-transform:uppercase;letter-spacing:.05em;border-bottom:3px double #8b7355;padding-bottom:.3em}h2{font-size:1.3em;color:#2a1808;text-transform:uppercase;letter-spacing:.08em;margin:1.2em 0 .4em}p{margin:.7em 0;text-align:justify}blockquote{border-left:4px solid #8b7355;margin:1em 0;padding:.5em 1em;color:#5a4a30;font-style:italic;background:#ede8d4}code{font-family:monospace;background:#e8e0c8;padding:1px 4px;font-size:.88em;border:1px solid #c8b89a}strong{font-weight:900}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0}' },
  { name: '紫梦', css: '.preview-wrap{background:linear-gradient(135deg,#1a0533 0%,#2d1054 100%);color:#e8d5ff;font-family:-apple-system,sans-serif;font-size:16px;line-height:1.8;padding:48px;box-sizing:border-box}h1{font-size:1.8em;color:#c084fc;margin:0 0 .5em;font-weight:700}h2{font-size:1.4em;color:#a855f7;border-bottom:1px solid #4a1a8a;padding-bottom:.3em;margin:1.2em 0 .6em}h3{font-size:1.1em;color:#d8b4fe;margin:1em 0 .4em}p{margin:.8em 0;color:#e2d0ff}blockquote{border-left:3px solid #a855f7;margin:1em 0;padding:.5em 1em;color:#c4a0e8;background:rgba(168,85,247,.1);font-style:italic}code{background:rgba(168,85,247,.2);color:#c084fc;padding:2px 7px;border-radius:4px;font-size:.88em}strong{color:#c084fc;font-weight:600}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0;color:#e2d0ff}' },
  { name: '商务蓝', css: '.preview-wrap{background:#f8fafc;color:#1e293b;font-family:"Segoe UI","PingFang SC",sans-serif;font-size:15px;line-height:1.7;padding:48px;box-sizing:border-box;border-top:4px solid #2563eb}h1{font-size:1.7em;color:#1e293b;margin:0 0 .8em;font-weight:700;padding-bottom:.5em;border-bottom:2px solid #e2e8f0}h2{font-size:1.3em;color:#2563eb;margin:1.2em 0 .5em;font-weight:600}h3{font-size:1.05em;color:#475569;margin:1em 0 .4em;font-weight:600}p{margin:.7em 0;color:#334155}blockquote{border-left:4px solid #2563eb;margin:1em 0;padding:.5em 1em;color:#475569;background:#eff6ff}code{background:#f1f5f9;color:#2563eb;padding:2px 6px;border-radius:3px;font-size:.85em;border:1px solid #e2e8f0}strong{color:#1e293b;font-weight:700}ul,ol{padding-left:1.5em;margin:.5em 0}li{margin:.3em 0;color:#334155}' },
]

const DEFAULT_MARKDOWN = `# 今日回响

写下属于你的，那些难以言说的时刻。

> 生活不是等待风暴过去，而是学会在雨中跳舞。

## 今日感悟

- 专注于当下，而非过去
- 每一个微小的进步都值得被看见
- **保持好奇**，永远不要停止探索

---

*2024 年，继续前行。*`

const markdownText = ref(DEFAULT_MARKDOWN)
const canvasWidth = ref(600)
const aspectRatio = ref('auto')
const exporting = ref(false)
const copied = ref(false)
const previewRef = ref<InstanceType<typeof MarkdownPreview>>()
const previewContainerRef = ref<HTMLElement>()
const activeThemeName = ref('简约白')
const customTheme = ref<{ name: string; css: string } | null>(null)

// ── Split pane drag
const leftPct = ref(45)
const isDragging = ref(false)

function startDrag(e: MouseEvent) {
  isDragging.value = true
  const startX = e.clientX
  const startPct = leftPct.value
  const pane = (e.target as HTMLElement).closest('.split-pane') as HTMLElement

  function onMove(ev: MouseEvent) {
    if (!pane) return
    const totalWidth = pane.clientWidth
    const delta = ev.clientX - startX
    const newPct = startPct + (delta / totalWidth) * 100
    leftPct.value = Math.min(80, Math.max(20, newPct))
  }

  function onUp() {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// ── Mobile
const isMobile = ref(false)
const mobileView = ref<'edit' | 'preview'>('edit')

function checkMobile() {
  isMobile.value = window.innerWidth <= 768
}

// ── Settings dropdown
const showSettings = ref(false)

function onDocumentClick() {
  showSettings.value = false
}

// ── ResizeObserver for canvasWidth on mobile
let resizeObserver: ResizeObserver | null = null

const activeCss = computed(() => {
  if (customTheme.value && activeThemeName.value === customTheme.value.name) {
    return customTheme.value.css
  }
  return builtinThemes.find((t) => t.name === activeThemeName.value)?.css ?? ''
})

function onThemeChange() {
  // If user picks a builtin, deactivate custom
  if (builtinThemes.find((t) => t.name === activeThemeName.value)) {
    // keep customTheme in memory but don't apply
  }
}

onMounted(() => {
  const saved = loadActiveTheme()
  if (saved) {
    const isBuiltin = builtinThemes.find((t) => t.name === saved.name)
    if (isBuiltin) {
      activeThemeName.value = saved.name
    } else {
      customTheme.value = saved
      activeThemeName.value = saved.name
    }
  }

  checkMobile()
  window.addEventListener('resize', checkMobile)

  document.addEventListener('click', onDocumentClick)

  resizeObserver = new ResizeObserver(() => {
    if (isMobile.value && previewContainerRef.value) {
      canvasWidth.value = previewContainerRef.value.clientWidth
    }
  })
  if (previewContainerRef.value) {
    resizeObserver.observe(previewContainerRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('click', onDocumentClick)
  resizeObserver?.disconnect()
  resizeObserver = null
})

async function downloadImage() {
  const el = previewRef.value?.$el?.querySelector('#preview-root') as HTMLElement
  if (!el) return
  exporting.value = true
  try {
    await exportAsPng(el)
  } finally {
    exporting.value = false
  }
}

async function copyImage() {
  if (!supportsClipboardImage()) return
  const el = previewRef.value?.$el?.querySelector('#preview-root') as HTMLElement
  if (!el) return
  exporting.value = true
  try {
    await copyToClipboard(el)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.editor-page {
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
  padding: 0 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  gap: 0;
}

.topbar-left {
  flex-shrink: 0;
  padding-right: 16px;
}

.topbar-sep {
  width: 1px;
  height: 20px;
  background: var(--border);
  flex-shrink: 0;
  margin: 0 4px;
}

.topbar-sep-sm {
  width: 1px;
  height: 16px;
  background: var(--border);
  flex-shrink: 0;
  margin: 0 8px;
}

.topbar-center {
  display: flex;
  align-items: center;
  flex: 1;
  padding: 0 12px;
  gap: 0;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-left: 16px;
}

/* ── Logo ── */
.logo {
  font-size: 14px;
  font-weight: 800;
  letter-spacing: -0.04em;
  user-select: none;
  line-height: 1;
}

.logo-md {
  color: var(--text);
}

.logo-arrow {
  color: var(--accent);
  font-weight: 400;
  margin: 0 1px;
  font-size: 12px;
}

.logo-img {
  color: var(--text-muted);
}

/* ── Controls ── */
.ctrl-group {
  display: flex;
  align-items: center;
  gap: 7px;
}

.ctrl-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.ctrl-select {
  font-size: 12px;
  padding: 5px 26px 5px 9px;
  height: 30px;
}

.ctrl-select-sm {
  width: 90px;
}

.ctrl-input-width {
  width: 80px;
  font-size: 12px;
  padding: 5px 9px;
  height: 30px;
}

.gallery-btn {
  font-size: 12px;
  padding: 5px 11px;
  height: 30px;
  display: inline-flex;
  align-items: center;
}

/* ── Export buttons ── */
.btn-theme-toggle {
  height: 32px;
  width: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-copy, .btn-export {
  height: 32px;
  padding: 0 13px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.btn-icon {
  font-size: 13px;
  line-height: 1;
  opacity: 0.8;
}

/* ── Settings dropdown ── */
.settings-wrapper {
  position: relative;
}

.btn-settings {
  height: 32px;
  width: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.settings-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 180px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  z-index: 100;
}

.settings-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── Split pane ── */
.split-pane {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.split-pane.dragging {
  user-select: none;
  cursor: col-resize;
}

.pane {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pane-editor {
  border-right: 1px solid var(--border);
  flex-shrink: 0;
}

.editor-body {
  flex: 1;
  padding: 12px;
  overflow: hidden;
  background: var(--surface2);
  display: flex;
  flex-direction: column;
}

.pane-preview {
  flex: 1;
}

.mobile-hidden {
  display: none;
}

/* ── Drag divider ── */
.pane-divider {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  background: var(--border);
  transition: background 0.15s;
  position: relative;
  z-index: 1;
}

.pane-divider:hover {
  background: var(--accent);
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

.pane-label-right {
  justify-content: flex-start;
}

.pane-label-hint {
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
  color: var(--text-subtle);
  margin-left: auto;
  font-size: 10px;
}

.pane-label-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pane-label-dot-edit {
  background: var(--accent);
  box-shadow: 0 0 6px var(--accent-dim);
}

.pane-label-dot-preview {
  background: var(--success);
  box-shadow: 0 0 6px rgba(78, 203, 139, 0.3);
}

/* ── Preview area ── */
.pane-preview {
  background: var(--surface2);
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

/* ── Floating toggle button ── */
.fab-toggle {
  position: fixed;
  right: 20px;
  bottom: 20px;
  padding-bottom: env(safe-area-inset-bottom, 16px);
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-size: 20px;
  border: none;
  cursor: pointer;
  display: none;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(0,0,0,0.2);
  z-index: 50;
}

/* ── Mobile responsive ── */
.hide-mobile { display: flex; }
.show-mobile { display: none; }

@media (max-width: 768px) {
  .hide-mobile { display: none !important; }
  .show-mobile { display: flex !important; }

  .split-pane {
    flex-direction: column;
  }

  .pane-editor {
    width: 100% !important;
    border-right: none;
    border-bottom: 1px solid var(--border);
    flex: 1;
  }

  .pane-preview {
    width: 100% !important;
    flex: 1;
  }

  .pane-divider {
    display: none;
  }

  .fab-toggle {
    display: flex;
  }
}
</style>
