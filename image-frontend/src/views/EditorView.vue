<template>
  <div class="editor-page">
    <!-- Top bar -->
    <header class="topbar">
      <div class="topbar-left">
        <span class="logo">md2img.soloship.top</span>
      </div>
      <div class="topbar-center">
        <!-- Theme selector -->
        <div class="theme-selector">
          <select v-model="activeThemeName" @change="onThemeChange">
            <optgroup label="内置主题">
              <option v-for="t in builtinThemes" :key="t.name" :value="t.name">{{ t.name }}</option>
            </optgroup>
            <option v-if="customTheme" :value="customTheme.name">{{ customTheme.name }} (已应用)</option>
          </select>
          <router-link to="/themes" class="btn-secondary" style="text-decoration:none;padding:7px 12px;border-radius:6px;font-size:13px;background:var(--surface2);border:1px solid var(--border);color:var(--text)">浏览画廊</router-link>
        </div>

        <!-- Canvas size -->
        <div class="canvas-controls">
          <select v-model="aspectRatio">
            <option value="auto">自适应高度</option>
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
            style="width:80px"
            placeholder="宽度px"
          />
        </div>
      </div>
      <div class="topbar-right">
        <button class="btn-secondary" :disabled="exporting" @click="copyImage">
          {{ copied ? '已复制 ✓' : '复制图片' }}
        </button>
        <button class="btn-primary" :disabled="exporting" @click="downloadImage">
          {{ exporting ? '导出中…' : '导出 PNG' }}
        </button>
      </div>
    </header>

    <!-- Main split pane -->
    <div class="split-pane">
      <div class="pane pane-editor">
        <MarkdownEditor v-model="markdownText" />
      </div>
      <div class="pane pane-preview">
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import { exportAsPng, copyToClipboard, supportsClipboardImage } from '../utils/exportImage'
import { loadActiveTheme, saveActiveTheme } from '../utils/storage'

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
const activeThemeName = ref('简约白')
const customTheme = ref<{ name: string; css: string } | null>(null)

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

.topbar-left { flex-shrink: 0; }
.topbar-center { display: flex; align-items: center; gap: 12px; flex: 1; justify-content: center; }
.topbar-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.logo {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
  letter-spacing: -0.02em;
}

.theme-selector, .canvas-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.split-pane {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.pane {
  flex: 1;
  overflow: hidden;
}

.pane-editor {
  border-right: 1px solid var(--border);
}

.pane-preview {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow: auto;
  padding: 24px;
  background: var(--surface2);
}

.preview-scroll {
  display: flex;
  justify-content: center;
}
</style>
