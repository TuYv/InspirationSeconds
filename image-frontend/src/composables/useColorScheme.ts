import { ref } from 'vue'
import { loadColorScheme, saveColorScheme } from '../utils/storage'

const isDark = ref(loadColorScheme() === 'dark')

function applyToDom(dark: boolean) {
  if (dark) {
    delete document.documentElement.dataset.theme
  } else {
    document.documentElement.dataset.theme = 'light'
  }
}

export function useColorScheme() {
  function toggle() {
    isDark.value = !isDark.value
    saveColorScheme(isDark.value ? 'dark' : 'light')
    applyToDom(isDark.value)
  }

  return { isDark, toggle }
}
