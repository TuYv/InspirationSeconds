import { describe, it, expect } from 'vitest'
import { scopeCss } from './cssScope'

describe('scopeCss', () => {
  it('scopes a simple selector', () => {
    expect(scopeCss('h1 { color: red; }')).toContain('#preview-root h1')
  })

  it('scopes group selectors individually', () => {
    const result = scopeCss('h1, h2, p { margin: 0; }')
    expect(result).toContain('#preview-root h1')
    expect(result).toContain('#preview-root h2')
    expect(result).toContain('#preview-root p')
  })

  it('does not double-scope already scoped selectors', () => {
    const result = scopeCss('#preview-root h1 { color: red; }')
    expect(result).not.toContain('#preview-root #preview-root')
  })

  it('converts :root to #preview-root', () => {
    const result = scopeCss(':root { --color: red; }')
    expect(result).toContain('#preview-root')
    expect(result).not.toContain(':root')
  })

  it('handles .preview-wrap as the container itself', () => {
    const result = scopeCss('.preview-wrap { background: white; }')
    expect(result).toContain('#preview-root.preview-wrap')
  })

  it('scopes rules inside @media', () => {
    const result = scopeCss('@media (max-width: 600px) { h1 { font-size: 1em; } }')
    expect(result).toContain('@media (max-width: 600px)')
    expect(result).toContain('#preview-root h1')
  })

  it('leaves @keyframes untouched', () => {
    const input = '@keyframes fade { from { opacity: 0; } to { opacity: 1; } }'
    const result = scopeCss(input)
    expect(result).toContain('@keyframes fade')
    expect(result).not.toContain('#preview-root from')
  })

  it('handles incomplete CSS without throwing', () => {
    expect(() => scopeCss('h1 { color:')).not.toThrow()
  })

  it('returns empty string for empty input', () => {
    expect(scopeCss('')).toBe('')
    expect(scopeCss('   ')).toBe('')
  })

  it('strips CSS comments before scoping', () => {
    const result = scopeCss('/* comment */ h1 { color: red; }')
    expect(result).not.toContain('comment')
    expect(result).toContain('#preview-root h1')
  })
})
