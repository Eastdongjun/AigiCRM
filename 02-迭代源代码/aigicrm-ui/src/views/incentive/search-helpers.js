// 激励查询页（search.vue）拆分出的共享纯工具（D9 2026-10-04）
// 消费方：search.vue（筛选/客户卡网格）、SearchLightbox.vue（灯箱）、SearchDetailDialog.vue（客户详情）。
// 放在 views/incentive/ 下是本批次改动文件权限边界所致（utils 目录不在授权清单内）。
import { getToken, getDownloadToken, refreshDownloadToken } from '@/utils/token'
import { downloadFile as downloadFileUtil } from '@/utils/download'

// 【修复 2026-08-14】下载 token 未就绪时不拼 token=（避免产生必然 403 的空 token 请求）；
// token 就绪后 getDownloadToken() 读取的 dlToken ref 变化会触发渲染链重跑，<img> 自动用带 token 的 URL 重载
export function fileUrl(id) {
  const t = getDownloadToken()
  return t ? '/api/file/download/' + id + '?token=' + t : '/api/file/download/' + id
}

export function thumbUrl(id) {
  const t = getDownloadToken()
  return t ? '/api/file/download/' + id + '?token=' + t + '&thumbnail=1' : '/api/file/download/' + id
}

// 【需求】文件下载(FEAT 0804):window.open 下载在新标签闪开/被拦截时表现为"没反应";
// 改为 fetch blob + a.download 强制保存,任何浏览器/任何状态下可靠触发
export async function downloadFile(id, name) {
  if (!id) return
  await refreshDownloadToken()
  await downloadFileUtil(fileUrl(id), { name: name || ('file-' + id), headers: { 'Authorization': 'Bearer ' + (getToken() || '') } })
}

function parseIds(v) {
  if (!v) return []
  if (typeof v === 'number') return [v]
  try { const a = JSON.parse(v); return Array.isArray(a) ? a.map(x => Number(x)) : [v] } catch { return String(v).split(',').map(s => s.trim()).filter(Boolean) }
}

function splitNames(n) {
  if (!n) return []
  return String(n).split(',').map(s => s.trim()).filter(Boolean)
}

function pushFiles(f, ids, names, tag) {
  const idList = parseIds(ids), nameList = splitNames(names)
  idList.forEach((id, i) => { f.push({ id: Number(id), label: nameList[i] || '文件', type: detectType(nameList[i] || ''), tag }) })
}

// 记录内全部附件清单：安装前/中/后 + 单文件 + 跟踪（各带阶段标签），过滤无效 id
export function getFiles(r) {
  const f = []
  pushFiles(f, r.filePreId, r.filePreName, '安装前')
  pushFiles(f, r.fileMidId, r.fileMidName, '安装中')
  pushFiles(f, r.filePostId, r.filePostName, '安装后')
  pushFiles(f, r.fileId, r.fileName, '')
  pushFiles(f, r.fileTrackId, r.fileTrackName, '跟踪')
  return f.filter(x => x.id)
}

export function detectType(n) {
  if (!n) return 'doc'
  if (/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n)) return 'image'
  if (/\.(mp4|mov|avi|webm)$/i.test(n)) return 'video'
  return 'doc'
}

// 大类名定位：2 级结构取父级名，3 级结构取祖父名；无分类信息回退空
export function getMain(catMap, r) {
  const c = catMap[r.categoryId]
  if (!c) return ''
  if (c.parentId === 0) return c.categoryName
  const p = catMap[c.parentId]
  if (!p) return c.categoryName
  const gp = catMap[p.parentId]
  return gp && gp.parentId === 0 ? gp.categoryName : p.categoryName
}

// 顶层品类 code（visit/exchange/plan/trial）：决定标签配色与文案口径
export function getCls(catMap, r) {
  const c = catMap[r.categoryId]
  if (!c) return ''
  if (c.parentId === 0) return c.categoryCode || ''
  const p = catMap[c.parentId]
  if (!p) return ''
  return (catMap[p.parentId] && catMap[p.parentId].parentId === 0 ? catMap[p.parentId].categoryCode : p.categoryCode) || ''
}

export function scoreColor(c) {
  return { visit: '#d97706', exchange: '#2563eb', plan: '#7c3aed', trial: '#059669' }[c] || '#303133'
}

// 文件槽位标签颜色（安装前蓝/安装中绿/安装后橙/跟踪紫）
export function tagColor(t) {
  return { 安装前: '#2563eb', 安装中: '#059669', 安装后: '#f59e0b', 跟踪: '#7c3aed' }[t] || '#6b7280'
}
