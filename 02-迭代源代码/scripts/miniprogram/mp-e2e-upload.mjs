import automator from 'miniprogram-automator'
const sleep = (ms) => new Promise(r => setTimeout(r, ms))
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9423' })
try {
  await mini.reLaunch('/pages/home/index'); await sleep(1200)
  const page = await mini.switchTab('/pages/files/index'); await sleep(3500)
  const d0 = await page.data()
  const folders = d0.folders || []
  const target = folders.find(f => (f.rawName || f.name) === '测试') || folders[0]
  const targetName = target.rawName || target.name
  console.log('[准备] scope=', d0.activeTab, ' 目标文件夹=', targetName, '(id=' + target.id + ')')

  const realPath = await mini.evaluate(() => new Promise((resolve) => {
    const fs = wx.getFileSystemManager()
    const p = `${wx.env.USER_DATA_PATH}/e2e-folder-upload-test.txt`
    fs.writeFile({ filePath: p, data: 'AIGICRM e2e folder-select upload ' + Date.now(), success: () => resolve(p), fail: (e) => resolve('FAIL:' + (e && e.errMsg)) })
  }))
  console.log('[准备] 真实临时文件:', realPath)
  if (String(realPath).startsWith('FAIL')) throw new Error('临时文件创建失败')

  // mock 原生选文件——路径字面量内联(mock 体在客户端执行, 闭包变量带不过去)
  await mini.mockWxMethod('chooseMedia', function () {
    const p = `${wx.env.USER_DATA_PATH}/e2e-folder-upload-test.txt`
    return { tempFiles: [{ tempFilePath: p }], errMsg: 'chooseMedia:ok' }
  })

  await page.callMethod('openFolderSelect', { mode: 'album' }); await sleep(900)
  const d1 = await page.data()
  console.log('[① 选夹面板先弹] visible=', d1.folderSelect.visible, ' 列表=', (d1.folderSelect.folders || []).length, '项 默认=', d1.folderSelect.selectedName)
  await page.callMethod('selectFolderItem', { currentTarget: { dataset: { id: String(target.id), name: targetName } } }); await sleep(500)
  const d2 = await page.data()
  console.log('[② 选定文件夹]', d2.folderSelect.selectedName, '(id=' + d2.folderSelect.selectedId + ')')
  await page.callMethod('confirmFolderSelect'); await sleep(400)
  const tgt = await mini.evaluate(() => {
    const pg = getCurrentPages()[getCurrentPages().length - 1]
    return pg._uploadTarget
  })
  console.log('[③ 目标定死]', JSON.stringify(tgt), '→ [mock]选文件 → 上传中...')
  await sleep(7000)
  const d3 = await page.data()
  console.log('[④ 上传完成] uploading=', d3.uploading, ' 落点 activeFolderName=', d3.activeFolderName, '(应为 ' + targetName + ')')
  const found = (d3.filteredFiles || d3.files || []).find(f => f.name && f.name.includes('e2e-folder-upload-test'))
  console.log('[⑤ 服务端实据] 所选文件夹内新文件:', found ? found.name + ' (id=' + found.id + ')' : '未找到!')
  const newId = found && found.id
  await mini.restoreWxMethod('chooseMedia')

  if (newId) {
    const del = await mini.evaluate((fid) => new Promise((resolve) => {
      const app = getApp()
      const base = app.globalData.apiBaseUrl
      const token = wx.getStorageSync('AIGICRM_TOKEN::' + base)
      wx.request({ url: base + '/api/file/' + fid, method: 'DELETE', header: { Authorization: 'Bearer ' + token },
        success: (r) => resolve(r.statusCode + '/' + (r.data && r.data.code)), fail: (e) => resolve('FAIL:' + (e && e.errMsg)) })
    }), newId)
    console.log('[清理] 删除测试文件 id=' + newId, '→', del)
  }

  // 场景 B: 手机文件入口(选夹→带夹进 H5)
  await page.callMethod('openFolderSelect', { mode: 'device' }); await sleep(800)
  await page.callMethod('selectFolderItem', { currentTarget: { dataset: { id: String(target.id), name: targetName } } }); await sleep(400)
  await page.callMethod('confirmFolderSelect'); await sleep(2500)
  const cur = await mini.currentPage()
  console.log('[场景B·手机文件] 选夹确认后落点:', cur.path, '(应为 files/picker)')
  await mini.navigateBack(); await sleep(1200)
  console.log('E2E DONE')
} finally { await mini.disconnect() }
