// 【S4.4 2026-11-23】PDF 挂载前质检：只看两件事——有没有文本层、页面尺寸是否一致。
//
// 为什么要做：D19 的重排 / 字号行距 / 书内搜索全部依赖 PDF 文本层，
// 影印件（扫描件）没有文本层，能力会静默失效。此前是挂上去之后进阅读器才发现，
// 管理员还得自己判断「这本能搜吗」。这里在上传前就告诉他，并给出「换一个 PDF」的口子。
//
// 不做的事：不 OCR、不改文件、不拦上传（只提示，仍允许强行挂载）。
// 解析失败（加密/损坏/超大）一律返回 null，由调用方放行——质检不能变成新的失败点。
import '@/utils/map-upsert-polyfill'
import pdfjsWorkerUrl from '@/composables/pdfjs-worker-shim.worker.js?worker&url'

/** 抽查页数上限：只看前几页，避免大文件在浏览器里解析过久 */
const SAMPLE_PAGES = 5

/**
 * @param {File|Blob} file
 * @returns {Promise<null|{pages:number, sampled:number, hasTextLayer:boolean, textPages:number, uniformPageSize:boolean, distinctSizes:number}>}
 */
export async function inspectPdfFile(file) {
  if (!file) return null
  let task = null
  try {
    const pdfjsLib = await import('pdfjs-dist')
    pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorkerUrl
    const buf = await file.arrayBuffer()
    task = pdfjsLib.getDocument({
      data: new Uint8Array(buf),
      // 质检不需要字体渲染，关掉减少无关报错
      disableFontFace: true,
    })
    const doc = await task.promise
    const sampled = Math.min(doc.numPages, SAMPLE_PAGES)
    const sizes = new Set()
    let textPages = 0
    for (let p = 1; p <= sampled; p += 1) {
      const page = await doc.getPage(p)
      const vp = page.getViewport({ scale: 1 })
      // 取整到 1pt，容忍浮点误差导致的「伪不一致」
      sizes.add(`${Math.round(vp.width)}x${Math.round(vp.height)}`)
      const content = await page.getTextContent()
      // TextItem 才有 str；TextMarkedContent 只有 type/tag，必须先收窄再取
      const hasText = (content.items || []).some(
        (it) => 'str' in it && typeof it.str === 'string' && it.str.trim().length > 0
      )
      if (hasText) textPages += 1
    }
    return {
      pages: doc.numPages,
      sampled,
      // 抽查页里一页文本都没有，就按「无文本层」提示；不逐页扫全本
      hasTextLayer: textPages > 0,
      textPages,
      uniformPageSize: sizes.size <= 1,
      distinctSizes: sizes.size,
    }
  } catch (e) {
    // 加密 / 损坏 / 浏览器不支持：不阻断挂载，交由后端与阅读器兜底
    return null
  } finally {
    if (task && typeof task.destroy === 'function') {
      try { await task.destroy() } catch (e) { /* 忽略 */ }
    }
  }
}
