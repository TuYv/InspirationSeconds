<template>
  <div ref="editorEl" class="editor-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorView, keymap, lineNumbers, highlightActiveLine } from '@codemirror/view'
import { EditorState } from '@codemirror/state'
import { css } from '@codemirror/lang-css'
import { defaultKeymap, historyKeymap, history } from '@codemirror/commands'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const editorEl = ref<HTMLElement>()
let view: EditorView | null = null

onMounted(() => {
  view = new EditorView({
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        history(),
        keymap.of([...defaultKeymap, ...historyKeymap]),
        lineNumbers(),
        highlightActiveLine(),
        css(),
        oneDark,
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            emit('update:modelValue', update.state.doc.toString())
          }
        }),
        EditorView.theme({
          '&': { height: '100%', fontSize: '13px' },
          '.cm-scroller': { overflow: 'auto', fontFamily: '"SF Mono", "Cascadia Code", monospace' },
          '.cm-content': { padding: '16px' },
        }),
      ],
    }),
    parent: editorEl.value!,
  })
})

watch(() => props.modelValue, (val) => {
  if (!view) return
  const current = view.state.doc.toString()
  if (current !== val) {
    view.dispatch({ changes: { from: 0, to: current.length, insert: val } })
  }
})

onBeforeUnmount(() => view?.destroy())
</script>

<style scoped>
.editor-container {
  height: 100%;
  overflow: hidden;
}
</style>
