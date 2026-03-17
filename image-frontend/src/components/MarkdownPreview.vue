<template>
  <div class="preview-outer" :style="outerStyle">
    <div
      id="preview-root"
      class="preview-wrap"
      v-html="renderedHtml"
    />
    <style v-if="scopedCss">{{ scopedCss }}</style>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
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

const scopedCss = computed(() => scopeCss(props.css || ''))

const outerStyle = computed(() => {
  const w = props.width ?? 600
  if (!props.aspectRatio || props.aspectRatio === 'auto') {
    return { width: w + 'px' }
  }
  const [wr, hr] = props.aspectRatio.split(':').map(Number)
  const h = Math.round((w * hr) / wr)
  return { width: w + 'px', height: h + 'px', overflow: 'hidden' }
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
