export interface Theme {
  id: number
  name: string
  description?: string
  css: string
  previewMd?: string
  authorName: string
  thumbnailUrl?: string
  starCount: number
  isBuiltin: boolean
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export async function listThemes(params: {
  page?: number
  size?: number
  sort?: string
  q?: string
}): Promise<PageResult<Theme>> {
  const query = new URLSearchParams()
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  if (params.sort) query.set('sort', params.sort)
  if (params.q) query.set('q', params.q)
  const res = await fetch(`/api/themes?${query}`)
  return res.json()
}

export async function getTheme(id: number): Promise<Theme> {
  const res = await fetch(`/api/themes/${id}`)
  return res.json()
}

export async function createTheme(data: {
  name: string
  description?: string
  css: string
  previewMd?: string
  authorName?: string
  thumbnail?: string
}): Promise<Theme> {
  const res = await fetch('/api/themes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}

export async function starTheme(id: number): Promise<void> {
  await fetch(`/api/themes/${id}/star`, { method: 'POST' })
}
