// 激励上传·手机文件 H5 回挂逻辑（纯函数模块, 零 wx 依赖, 供 pages/incentive/upload 与单元测试共同引用）。
// 【2026-11-04 用户令"所有上传都走 H5"】槽位点「手机文件」→ web-view H5(/mp-file-picker?scope=incentive)
// 调起手机文件管理器直传 → 返回后按 上传人+导航时间戳 从 /api/file/list 取回最新一条,
// 槽位以 existing+fileId 挂载(uploadPendingFiles 对 existing&&fileId 直接进 doneIds, 免二次上传)。
// 单元测试: scripts/test-miniprogram-upload-handback.js（dev-verify contracts 链内运行）。

// 取回规则: 只认 uploaderId 是本人的记录; createdAt 晚于导航时刻(容差 60s 吸收客户端/服务端时钟偏差);
// 多条取最新; 空或全不符返回 null(调用方提示重试或改从聊天选择)。
function pickLatestOwnUpload(records, uploaderId, sinceTsMs) {
  const toleranceMs = 60000
  let latest = null
  let latestTs = Number.NaN
  for (const r of records || []) {
    if (!r || Number(r.uploaderId) !== Number(uploaderId)) continue
    const ts = Date.parse(r.createdAt || '')
    if (Number.isNaN(ts)) continue
    if (sinceTsMs && ts < sinceTsMs - toleranceMs) continue
    if (!latest || ts > latestTs) {
      latest = r
      latestTs = ts
    }
  }
  return latest
}

module.exports = {
  pickLatestOwnUpload
}
