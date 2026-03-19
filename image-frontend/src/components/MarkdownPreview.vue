<template>
  <div class="preview-outer" :style="outerStyle">
    <div
      id="preview-root"
      class="preview-wrap"
      v-html="renderedHtml"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onUnmounted } from 'vue'
import MarkdownIt from 'markdown-it'
import { scopeCss } from '../utils/cssScope'

const props = defineProps<{
  markdown: string
  css: string
  width?: number
  aspectRatio?: string // e.g. '1:1', '4:5', '16:9', '9:16'
}>()

const md = new MarkdownIt({ html: false, linkify: true, typographer: true })

const renderedHtml = computed(() => md.render(props.markdown || ''))

const outerStyle = computed(() => {
  const w = props.width ?? 600
  if (!props.aspectRatio || props.aspectRatio === 'auto') {
    return { width: w + 'px' }
  }
  const [wr, hr] = props.aspectRatio.split(':').map(Number)
  const h = Math.round((w * hr) / wr)
  return { width: w + 'px', height: h + 'px', overflow: 'hidden' }
})

// Inject theme CSS into <head> for reliable dynamic style application.
// Vue template <style>{{ css }}</style> is unreliable — browsers may not process
// style elements injected into the body via Vue's template compiler.
let styleEl: HTMLStyleElement | null = null

watch(
  () => props.css,
  (css) => {
    if (!styleEl) {
      styleEl = document.createElement('style')
      styleEl.setAttribute('data-preview-theme', '')
      document.head.appendChild(styleEl)
    }
    styleEl.textContent = scopeCss(css || '')
  },
  { immediate: true },
)

onUnmounted(() => {
  styleEl?.remove()
  styleEl = null
})
</script>

<style scoped>
.preview-outer {
  flex-shrink: 0;
}

.preview-wrap {
  width: 100%;
  height: 100%;
  overflow: auto;
  word-break: break-word;
}
</style>
