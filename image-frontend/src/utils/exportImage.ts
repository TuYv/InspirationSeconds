import html2canvas from 'html2canvas'

export async function exportAsPng(element: HTMLElement, filename?: string): Promise<void> {
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: null,
    logging: false,
  })

  const link = document.createElement('a')
  link.download = filename ?? `image-${Date.now()}.png`
  link.href = canvas.toDataURL('image/png')
  link.click()
}

export async function copyToClipboard(element: HTMLElement): Promise<void> {
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: null,
    logging: false,
  })

  return new Promise((resolve, reject) => {
    canvas.toBlob(async (blob) => {
      if (!blob) { reject(new Error('toBlob failed')); return }
      try {
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': blob }),
        ])
        resolve()
      } catch (e) {
        reject(e)
      }
    }, 'image/png')
  })
}

export function supportsClipboardImage(): boolean {
  return typeof ClipboardItem !== 'undefined'
}

/** Generate a JPEG base64 thumbnail (1x, quality 0.8) for publishing */
export async function generateThumbnail(element: HTMLElement): Promise<string> {
  const canvas = await html2canvas(element, {
    scale: 1,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  })
  return canvas.toDataURL('image/jpeg', 0.8)
}
