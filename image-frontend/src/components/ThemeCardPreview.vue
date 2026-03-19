<template>
  <div class="thumb-wrap" ref="wrapRef">
    <iframe
      :srcdoc="srcdoc"
      sandbox="allow-same-origin"
      frameborder="0"
      scrolling="no"
      class="thumb-iframe"
      :style="iframeStyle"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import MarkdownIt from 'markdown-it'
// @ts-ignore
import markdownItMark from 'markdown-it-mark'

const props = defineProps<{
  css: string
  previewMd?: string
}>()

const IFRAME_W = 600

const DEFAULT_MD = `# 主题预览

写下属于你的，那些**难以言说**的时刻。

> 生活不是等待风暴过去，而是学会在雨中跳舞。

## 今日感悟

- 专注于当下，而非过去
- 每一个微小的进步都值得被看见
- **保持好奇**，永远不要停止探索

---

*2024 年，继续前行。*`

const md = new MarkdownIt({ html: false, linkify: true, typographer: true }).use(markdownItMark)

const wrapRef = ref<HTMLElement | null>(null)
const scale = ref(0.38)

let ro: ResizeObserver | null = null

onMounted(() => {
  ro = new ResizeObserver(([entry]) => {
    scale.value = entry.contentRect.width / IFRAME_W
  })
  if (wrapRef.value) ro.observe(wrapRef.value)
})

onUnmounted(() => ro?.disconnect())

const iframeStyle = computed(() => ({
  width: IFRAME_W + 'px',
  height: IFRAME_W + 'px',
  transform: `scale(${scale.value})`,
  transformOrigin: 'top left',
  pointerEvents: 'none' as const,
}))

const srcdoc = computed(() => {
  const html = md.render(props.previewMd || DEFAULT_MD)
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
* { box-sizing: border-box; }
body { margin: 0; padding: 0; overflow: hidden; }
</style>
<style>${props.css || ''}</style>
</head>
<body>
<div class="preview-wrap">${html}</div>
</body>
</html>`
})
</script>

<style scoped>
.thumb-wrap {
  width: 100%;
  height: 148px;
  overflow: hidden;
}

.thumb-iframe {
  border: none;
  display: block;
}
</style>
