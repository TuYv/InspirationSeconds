const THEME_KEY = 'image_active_theme'
const STARRED_KEY = 'image_starred_themes'
const COLOR_SCHEME_KEY = 'image_color_scheme'

export interface SavedTheme {
  id?: number
  name: string
  css: string
}

export function saveActiveTheme(theme: SavedTheme) {
  localStorage.setItem(THEME_KEY, JSON.stringify(theme))
}

export function loadActiveTheme(): SavedTheme | null {
  const raw = localStorage.getItem(THEME_KEY)
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
}

export function isStarred(id: number): boolean {
  const raw = localStorage.getItem(STARRED_KEY)
  if (!raw) return false
  try {
    const ids: number[] = JSON.parse(raw)
    return ids.includes(id)
  } catch { return false }
}

export function saveColorScheme(scheme: 'light' | 'dark') {
  localStorage.setItem(COLOR_SCHEME_KEY, scheme)
}

export function loadColorScheme(): 'light' | 'dark' {
  const val = localStorage.getItem(COLOR_SCHEME_KEY)
  return val === 'light' ? 'light' : 'dark'
}

export function markStarred(id: number) {
  const raw = localStorage.getItem(STARRED_KEY)
  let ids: number[] = []
  try { ids = JSON.parse(raw ?? '[]') } catch { /* */ }
  if (!ids.includes(id)) {
    ids.push(id)
    localStorage.setItem(STARRED_KEY, JSON.stringify(ids))
  }
}
