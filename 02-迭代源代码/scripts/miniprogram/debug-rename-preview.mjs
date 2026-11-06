import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

mkdirSync('/tmp/mp-debug-shots', { recursive: true })
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log('connected')
await mini.reLaunch('/pages/files/index')
await new Promise((r) => setTimeout(r, 3000))
const page = await mini.currentPage()
console.log('page:', page && page.path)
const d = await page.data()
const rows = d.filteredFiles || []
console.log('rows:', rows.length, 'dlToken len:', (d.dlToken || '').length)
const imgIdx = rows.findIndex((r) => /\.(png|jpg|jpeg|gif|webp|bmp)$/i.test(String(r.name || '')))
console.log('first image row index:', imgIdx, imgIdx >= 0 ? rows[imgIdx].name : '')
if (imgIdx >= 0) {
  const evt = { currentTarget: { dataset: { index: imgIdx } } }
  // 真实路径=previewFile 内先 ensureDlToken(缓存 promise); 调试脚本同序直调
  await page.callMethod('ensureDlToken')
  await new Promise((r) => setTimeout(r, 1500))
  const dAfter = await page.data()
  console.log('dlToken after ensure len:', (dAfter.dlToken || '').length)
  const group = await page.callMethod('collectImagePreviewGroup', evt)
  console.log('group urls:', group.urls.length, 'current set:', Boolean(group.current))
  await page.callMethod('previewFile', evt)
  await new Promise((r) => setTimeout(r, 1500))
  await mini.screenshot({ path: '/tmp/mp-debug-shots/6-image-group-preview.png' })
  console.log('shot: 6-image-group-preview')
}
await page.callMethod('openRenameModal', { currentTarget: { dataset: { index: imgIdx >= 0 ? imgIdx : 0 } } })
await new Promise((r) => setTimeout(r, 900))
const d2 = await page.data()
console.log('renameModal visible:', d2.renameModal.visible, 'input:', d2.renameModal.input)
await mini.screenshot({ path: '/tmp/mp-debug-shots/7-rename-modal.png' })
console.log('shot: 7-rename-modal')
await page.callMethod('cancelRenameModal')
await mini.disconnect()
console.log('DONE-PART4')
