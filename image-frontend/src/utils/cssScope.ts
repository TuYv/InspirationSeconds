const ROOT = '#preview-root'

function scopeSelector(selector: string): string {
  return selector
    .split(',')
    .map((s) => {
      const trimmed = s.trim()
      if (!trimmed) return ''
      if (trimmed.startsWith(ROOT)) return trimmed
      if (trimmed === ':root') return ROOT
      // .preview-wrap targets the container itself
      if (trimmed === '.preview-wrap') return `${ROOT}.preview-wrap`
      return `${ROOT} ${trimmed}`
    })
    .filter(Boolean)
    .join(', ')
}

export function scopeCss(css: string): string {
  if (!css || !css.trim()) return ''

  // Remove comments
  const cleaned = css.replace(/\/\*[\s\S]*?\*\//g, '')

  let result = ''
  let i = 0

  while (i < cleaned.length) {
    // Skip whitespace
    if (/\s/.test(cleaned[i])) {
      result += cleaned[i++]
      continue
    }

    // At-rule
    if (cleaned[i] === '@') {
      const rest = cleaned.slice(i)
      const atMatch = rest.match(/^@[\w-]+[^{;]*/)
      if (!atMatch) { result += cleaned[i++]; continue }

      const atRule = atMatch[0]
      i += atRule.length

      // skip whitespace
      while (i < cleaned.length && /\s/.test(cleaned[i])) i++

      if (cleaned[i] === '{') {
        // block at-rule — find matching brace
        let depth = 1
        let block = '{'
        i++
        while (i < cleaned.length && depth > 0) {
          if (cleaned[i] === '{') depth++
          else if (cleaned[i] === '}') depth--
          if (depth > 0) block += cleaned[i]
          i++
        }
        block += '}'

        // For @media / @supports, recursively scope inner rules
        if (/^@(media|supports)/.test(atRule)) {
          result += `${atRule} {\n${scopeCss(block.slice(1, -1))}\n}`
        } else {
          // @keyframes etc — keep as-is
          result += `${atRule} ${block}`
        }
      } else if (cleaned[i] === ';') {
        result += `${atRule};`
        i++
      } else {
        result += atRule
      }
      continue
    }

    // Regular rule: find selector up to '{'
    const braceIdx = cleaned.indexOf('{', i)
    if (braceIdx === -1) break

    const selector = cleaned.slice(i, braceIdx)
    i = braceIdx

    // Find matching '}'
    let depth = 1
    let declarations = '{'
    i++
    while (i < cleaned.length && depth > 0) {
      if (cleaned[i] === '{') depth++
      else if (cleaned[i] === '}') depth--
      if (depth > 0) declarations += cleaned[i]
      i++
    }
    declarations += '}'

    result += `${scopeSelector(selector)} ${declarations}`
  }

  return result
}
