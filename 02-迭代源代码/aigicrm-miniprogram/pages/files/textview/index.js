// 【文本在线预览 2026-10-17】txt/csv/md/log/json 等纯文本文件的查看页。
// 微信 openDocument 不支持文本类格式——此前这些文件在小程序里点开只有"无法直接打开"。
// 入参: ?path=<下载后的临时文件路径>&name=<文件名>(均 encodeURIComponent)。
// 解码: 先 utf-8;替换符密度高时按 GBK 再读一次(微信 readFile 支持 gbk 编码参数),仍高则提示。
// 上限: 2MB 读入 / 500KB 渲染(超长截断提示下载看全量)——长文本 setData 会顶爆渲染层。
const MAX_READ = 2 * 1024 * 1024
const MAX_RENDER = 500 * 1024

function mojibakeRatio(text) {
  if (!text) return 0
  let bad = 0
  for (let i = 0; i < text.length; i += 1) if (text.charCodeAt(i) === 0xfffd) bad += 1
  return bad / text.length
}

Page({
  data: { name: '', content: '', truncated: false, gbkNotice: false, failed: false },

  onLoad(options) {
    const path = decodeURIComponent(options.path || '')
    const name = decodeURIComponent(options.name || '文本文件')
    this.setData({ name })
    wx.setNavigationBarTitle({ title: name, fail: () => {} })
    if (!path) return this.setData({ failed: true })
    const fs = wx.getFileSystemManager()
    fs.stat({
      path,
      success: (statRes) => {
        const size = statRes.stats && statRes.stats.size
        if (size && size > MAX_READ) {
          return this.setData({ failed: true })
        }
        this.readText(fs, path, 'utf8')
      },
      fail: () => this.readText(fs, path, 'utf8')
    })
  },

  readText(fs, path, encoding) {
    fs.readFile({
      filePath: path,
      encoding,
      success: (res) => {
        const text = String(res.data || '')
        if (encoding === 'utf8' && mojibakeRatio(text) > 0.02) {
          // utf-8 读出大量替换符 → 大概率 GBK 编码, 换编码重读一次
          return this.readText(fs, path, 'gbk')
        }
        if (encoding === 'gbk' && mojibakeRatio(text) > 0.02) {
          this.setData({ gbkNotice: true })
        }
        const truncated = text.length > MAX_RENDER
        this.setData({ content: truncated ? text.slice(0, MAX_RENDER) : text, truncated })
      },
      fail: () => this.setData({ failed: true })
    })
  }
})
