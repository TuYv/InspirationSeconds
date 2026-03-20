<template>
  <div ref="editorEl" class="editor-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorView, keymap, lineNumbers, highlightActiveLine } from '@codemirror/view'
import { EditorState, Compartment } from '@codemirror/state'
import { markdown } from '@codemirror/lang-markdown'
import { defaultKeymap, historyKeymap, history } from '@codemirror/commands'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps<{ modelValue: string; isDark: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const editorEl = ref<HTMLElement>()
let view: EditorView | null = null
const themeCompartment = new Compartment()

const lightTheme = EditorView.theme({
  '&': { background: '#f0f0ec', color: '#2a2a3a' },
  '.cm-content': { caretColor: '#2a2a3a' },
  '.cm-cursor': { borderLeftColor: '#2a2a3a' },
  '.cm-gutters': { background: '#e8e8e4', color: '#6a6a7a', border: 'none', borderRight: '1px solid #d8d8d0' },
  '.cm-activeLineGutter': { background: 'rgba(107, 92, 231, 0.08)' },
  '.cm-activeLine': { background: 'rgba(107, 92, 231, 0.06)' },
  '.cm-selectionBackground, ::selection': { background: 'rgba(107, 92, 231, 0.18) !important' },
  '.cm-selectionMatch': { background: 'rgba(107, 92, 231, 0.12)' },
}, { dark: false })

onMounted(() => {
  view = new EditorView({
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        history(),
        keymap.of([...defaultKeymap, ...historyKeymap]),
        lineNumbers(),
        highlightActiveLine(),
        markdown(),
        themeCompartment.of(props.isDark ? oneDark : lightTheme),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            emit('update:modelValue', update.state.doc.toString())
          }
        }),
        EditorView.theme({
          '&': { height: '100%', fontSize: '14px' },
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

watch(() => props.isDark, (dark) => {
  view?.dispatch({
    effects: themeCompartment.reconfigure(dark ? oneDark : lightTheme),
  })
})

onBeforeUnmount(() => view?.destroy())
</script>

<style scoped>
.editor-container {
  flex: 1;
  overflow: hidden;
}
</style>
