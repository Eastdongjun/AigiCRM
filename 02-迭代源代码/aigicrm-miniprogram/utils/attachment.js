// 四项服务附件「下载 → 预览/保存」共用逻辑。
// 原先 incentive 列表页 / incentive 详情页 / incentive 上传页(既有附件)各持一份约 50 行
// 近似复制; 三份在细节上存在既定差异, 提取时逐条保留(勿"顺手统一"):
// - busy 守卫与复位: 各页各自的 previewing / fileActionLoading, 由调用方以 setBusy 闭包写回;
// - hideLoading 时机: 列表/详情在 success 内、全屏 API(previewImage/openDocument)之前收掉,
//   避免"必须配对使用"警告(hideLoadingBeforeResult=true); 上传页维持 complete 统一收(false);
// - 保存失败文案: 列表/详情"保存失败", 上传页"下载保存失败"(saveFailTitle);
// - 视频分支仅列表/详情有: onVideo 回调把临时路径抛回页面写 previewVideoPath。
const { getToken } = require('./auth')
const { showError } = require('./feedback')

const TEXT_EXT_RE = /\.(txt|csv|md|log|json|xml)$/i
/** 纯文本文件名判定(txt/csv/md/log/json/xml)——openDocument 不支持这些格式, 走文本查看页 */
function isTextFileName(name) {
  return TEXT_EXT_RE.test(String(name || ''))
}

/** 跳文本查看页(下载完成后的临时文件路径跨页面有效) */
function openTextView(tempFilePath, name) {
  wx.navigateTo({ url: `/pages/files/textview/index?path=${encodeURIComponent(tempFilePath)}&name=${encodeURIComponent(name || '文本文件')}` })
}

function attachmentAuthHeader() {
  return getToken() ? { Authorization: 'Bearer ' + getToken() } : {}
}

// 保存到本机: 优先文件系统管理器 fs.saveFile, 基础库不支持时退回 wx.saveFile
function saveDownloadedFileToDisk(tempFilePath, failTitle) {
  const options = {
    tempFilePath,
    success: () => wx.showToast({ title: '已保存到本机', icon: 'success' }),
    fail: (error) => showError(error, failTitle || '保存失败')
  }
  const fs = typeof wx.getFileSystemManager === 'function' ? wx.getFileSystemManager() : null
  if (fs && typeof fs.saveFile === 'function') return fs.saveFile(options)
  return wx.saveFile(options)
}

/**
 * 下载远程附件并按 mode 分发(预览/保存)。
 * @param {Object} options
 * @param {string} options.url 下载地址(由调用方按各自文件形状用 getDownloadUrl 生成)
 * @param {string} options.mode 'preview' | 'download'
 * @param {Object} options.file 附件对象(供 isImage/onVideo 判定)
 * @param {boolean} options.isBusy 页面 busy 态守卫(重复点击直接返回)
 * @param {Function} options.setBusy busy 写回(进入时置 true, complete 里置 false)
 * @param {Function} options.isImage 预览分支的图片判定(各页文件 type/fileKind 字段口径不同)
 * @param {Function} [options.onVideo] 视频临时路径回调; 不传则视频走 openDocument
 * @param {string} [options.saveFailTitle] 保存失败 toast 文案(默认"保存失败")
 * @param {boolean} [options.hideLoadingBeforeResult] true=success 内先 hideLoading(列表/详情口径)
 */
function fetchRemoteAttachment(options) {
  const { url, mode, file, isBusy, setBusy, isImage, onVideo, saveFailTitle, hideLoadingBeforeResult = true } = options
  if (isBusy) return
  if (!url) return wx.showToast({ title: '附件地址缺失', icon: 'none' })
  setBusy(true)
  // 【下载进度 2026-10-17】微信 openDocument 无流式(必须整包下载完), 大文件等待只有转圈会显得"卡死"——
  // 用 DownloadTask.onProgressUpdate 把百分比写进 loading 标题。done 守卫防 success 后迟到的进度回调
  // 把已收掉的 loading 又顶回来(与 L17 hideLoading/showToast 单例坑同族)。
  let done = false
  const progressTitle = (percent) => `${mode === 'download' ? '下载中' : '加载中'} ${Math.min(99, percent)}%`
  wx.showLoading({ title: mode === 'download' ? '下载中' : '准备预览' })
  return new Promise((resolve) => {
    const task = wx.downloadFile({
      url,
      header: attachmentAuthHeader(),
      success: (result) => {
        if (hideLoadingBeforeResult) wx.hideLoading()
        if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
        if (mode === 'download') return saveDownloadedFileToDisk(result.tempFilePath, saveFailTitle)
        if (isImage(file)) {
          wx.previewImage({ urls: [result.tempFilePath], fail: (error) => showError(error, '图片预览失败') })
          return
        }
        if (isTextFileName(file.name || file.label)) return openTextView(result.tempFilePath, file.name || file.label)
        // 视频判定: 标注 type 或扩展名(既有附件只有 fileKind/文件名, 统一兜住)
        if ((file.type === 'video' || file.fileKind === 'video' || /\.(mp4|mov|m4v|webm)$/i.test(String(file.name || file.label || ''))) && onVideo) return onVideo(result.tempFilePath)
        wx.openDocument({ filePath: result.tempFilePath, showMenu: true, fail: (error) => showError(error, '无法直接预览，可下载后查看') })
      },
      fail: (error) => {
        if (hideLoadingBeforeResult) wx.hideLoading()
        showError(error, '文件下载失败')
      },
      complete: () => {
        done = true
        if (!hideLoadingBeforeResult) wx.hideLoading()
        setBusy(false)
        resolve()
      }
    })
    if (task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate((res) => {
        if (done || !res || !res.progress) return
        wx.showLoading({ title: progressTitle(res.progress) })
      })
    }
  })
}

module.exports = {
  attachmentAuthHeader,
  fetchRemoteAttachment,
  saveDownloadedFileToDisk,
  isTextFileName,
  openTextView
}
