// 法布施(小程序) · 视图字段整理: 把接口数据换算成 WXML 直接可读的文字与标记。全是纯函数, 不碰 wx / getApp。
// 【2026-11-25 用户裁决】“小程序法布施界面按原型 v7 重做外观”: 原型上的文字(“今日 2/3”“今天 08:02 已诵…”
// “第 8/81 章”“今天 09:30”)都要从接口数据算出来, 换算集中放在这里, 各页面只负责取数和跳转。
// 单元测试: scripts/test-miniprogram-fabushi-view-model.js
// 注意: WXML 里按 item.xxx 读的派生字段(名字以 Text / Label / Class 等结尾)必须出现在同目录的 index.js 里,
//       所以页面里要写成 posts.map((p) => ({ ...p, timeText: vm.timeText(p.createdAt) })) 这样的形式, 而不是整包交给这里。

const SCOPE_LABEL = { songdu: '每日诵读', suishi: '每日随施', zaidu: '再读一本' }
const STATUS_LABEL = { wish: '想读', reading: '在读', finished: '已读完', favorite: '收藏' }
const FREQ_LABEL = { daily: '每天', weekday: '工作日', weekend: '休息日' }
// 作品详情页的标题(原型 A4 的例子是“再读作品”): 按类型
const WORK_TITLE = { songdu: '诵读作品', suishi: '随施作品', zaidu: '再读作品' }
// 封面的几种底色, 与样式里的 .cover.navy / .cyan / … 一一对应
const COVER_TONES = ['navy', 'cyan', 'amber', 'teal', 'violet', 'gray']

// 去掉书名号后的书名; 最多 max 个字(封面地方小)。没有书名时给“书”, 不留空白
function coverText(title, max) {
  const limit = max || 4
  const plain = String(title == null ? '' : title).replace(/[《》]/g, '').trim()
  return (plain || '书').slice(0, limit)
}

// 按书目编号稳定地取一种封面底色
function coverTone(seed) {
  const n = Math.abs(Number(seed))
  return COVER_TONES[(Number.isFinite(n) ? Math.floor(n) : 0) % COVER_TONES.length]
}

// 接口给的是不带时区的本地时间("2026-11-25T09:30:00" 或中间是空格)。
// 原型的写法: 今天 09:30 / 昨天 18:40 / 11-20 07:50 / 2025-12-31
function timeText(value, now) {
  if (!value) return ''
  const m = String(value).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  if (!m) return String(value)
  const today = now || new Date()
  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1)
  const [year, month, day, hour, minute] = [Number(m[1]), Number(m[2]), Number(m[3]), m[4], m[5]]
  const sameDay = (d) => d.getFullYear() === year && d.getMonth() + 1 === month && d.getDate() === day
  if (sameDay(today)) return `今天 ${hour}:${minute}`
  if (sameDay(yesterday)) return `昨天 ${hour}:${minute}`
  if (today.getFullYear() === year) return `${m[2]}-${m[3]} ${hour}:${minute}`
  return `${m[1]}-${m[2]}-${m[3]}`
}

// 阅读进度: 有总页数时“第 x/y 页 · 百分比”; 只有页数或只有百分比时退一步; 都没有给“暂无进度”
function progressText(book) {
  const b = book || {}
  const lastPage = Number(b.lastPage) || 0
  const totalPages = Number(b.totalPages) || 0
  const pct = Number(b.progressPct) || 0
  if (lastPage && totalPages) return `第 ${lastPage}/${totalPages} 页 · ${Math.min(100, Math.round(lastPage * 100 / totalPages))}%`
  if (lastPage) return `读到第 ${lastPage} 页`
  if (pct) return `已读 ${pct}%`
  return '暂无进度'
}

// 工作台主卡与三板块(原型 A1)。接口的 todayPosts 是今天三类合计, 再读一本 = 合计 − 诵读 − 随施(不小于 0)。
function awakeningView(awake, personal, readingBook) {
  const a = awake || {}
  const recitations = Number(a.todayRecitations) || 0
  const gifts = Number(a.todayGifts) || 0
  const zaidu = Math.max(0, (Number(a.todayPosts) || 0) - recitations - gifts)
  const songduDone = recitations > 0
  const suishiDone = gifts > 0
  const zaiduDone = zaidu > 0
  const streak = Number((personal || {}).myStreakDays) || 0
  const rereading = Number(a.rereadingBooks) || 0
  const steps = [
    { scope: 'songdu', label: '去诵读', done: songduDone },
    { scope: 'suishi', label: '去随施', done: suishiDone },
    { scope: 'zaidu', label: '去精读', done: zaiduDone }
  ]
  const next = steps.find((step) => !step.done) || { scope: '', label: '' }
  const book = readingBook && readingBook.title ? `《${String(readingBook.title).replace(/[《》]/g, '')}》 · ${progressText(readingBook)}` : ''
  return {
    doneToday: steps.filter((step) => step.done).length,
    nextScope: next.scope,
    nextLabel: next.label,
    songduDone, suishiDone, zaiduDone,
    songduSub: songduDone ? `今天已诵 ${recitations} 条${streak > 1 ? ` · 连续 ${streak} 天` : ''}` : '每天朗读背诵一小段经典',
    suishiSub: suishiDone ? `今天已施 ${gifts} 条` : '随手传点儿东西 · 一句话/一页书/一张照片',
    zaiduSub: book || (rereading > 0 ? `在读 ${rereading} 本 · 今天还没读` : '挑一本书，按章节精读')
  }
}

// 提醒卡: “每天 20:00” + 是否开着。没有设置时按默认值显示
function reminderView(settings) {
  const s = settings || {}
  return {
    on: Number(s.reminderEnabled) === 1,
    text: `${FREQ_LABEL[s.freqCode] || FREQ_LABEL.daily} ${s.remindTime || '20:00'}`
  }
}

// 统计页签(原型 C5)的四格: 团队本月三类各多少条、被复诵多少次。接口不直接给这几个合计,
// 但成员榜每一行带着三类的条数与被复诵次数, 加起来就是(成员榜是全量名单, 不是只取前几名)。
function teamTotals(monthly) {
  const rows = (Array.isArray((monthly || {}).ranking) ? monthly.ranking : [])
  const sum = (key) => rows.reduce((total, row) => total + (Number(row[key]) || 0), 0)
  return { songdu: sum('songduCount'), suishi: sum('suishiCount'), zaidu: sum('zaiduCount'), recitations: sum('recitationsReceived') }
}

// 环比角标(原型写法“↑ 12%”): 接口给的是百分比数字, 上月基数为 0 时给 null。没有数、或正好是 0 时不显示角标
function trendBadge(pct) {
  const value = Number(pct)
  if (pct == null || !Number.isFinite(value) || value === 0) return ''
  return `${value > 0 ? '↑ ' : '↓ '}${Math.round(Math.abs(value) * 10) / 10}%`
}

// 作品详情页复诵条上的一行小字(原型 A4): 前两位是谁、各多长; 还没有人复诵时说明可以直接诵读
function reciteText(rows) {
  const list = Array.isArray(rows) ? rows : []
  if (!list.length) return '还没有人复诵 · 他人可以直接诵读本段'
  const head = list.slice(0, 2).map((row) => `${row.userName || '同事'}${row.durationSeconds ? ' ' + row.durationSeconds + 's' : ''}`).join(' · ')
  return `${head}${list.length > 2 ? ` 等 ${list.length} 人` : ''} —— 他人可直接诵读本段`
}

// 下载令牌接口回来的可能是 {token} / {data:{token}} / 直接就是字符串: 统一取成字符串。
// 【2026-11-25 修正】详情页复诵列表、发布页九宫格预览原来直接把整个应答对象拼进地址(成了 object Object), 图片和语音都放不出来
function downloadTokenOf(res) {
  if (!res) return ''
  if (typeof res === 'string') return res
  return res.token || (res.data && res.data.token) || ''
}

// 下载地址 + 令牌(与 api/file.js 的 getDownloadUrl 同一条通道); base 为空时只有路径
function downloadUrl(fileId, token, base) {
  return `${base || ''}/api/file/download/${fileId}${token ? `?token=${encodeURIComponent(token)}` : ''}`
}

// 发布成功页的文字(原型 B5): “每日诵读已发布” + 一句说明(挂了哪本书、哪个章节、带了几段语音 / 几张图)
function publishSuccessView(scopeType, form) {
  const f = form || {}
  const label = SCOPE_LABEL[scopeType] || '内容'
  const parts = []
  if (f.bookTitle) parts.push(`《${String(f.bookTitle).replace(/[《》]/g, '')}》${f.sectionTitle || ''}`)
  if (f.audioCount) parts.push(`语音 ${f.audioCount} 段`)
  if (f.imageCount) parts.push(`图片 ${f.imageCount} 张`)
  const head = parts.length ? parts.join('，') + '，' : ''
  return { title: `${label}已发布`, summary: `${head}已进认知广场，${scopeType === 'songdu' ? '等书友来复诵你的声音吧' : '等大家来点木鱼吧'}。` }
}

// 信息流条目(原型 A2 / A1 广场页签): 类型名、时间、NEW、九宫格里的图片(最多 3 张)、音频段数、谁点了木鱼
function postView(post, token, now, base) {
  const p = post || {}
  const attachments = Array.isArray(p.attachments) ? p.attachments : []
  const images = attachments.filter((item) => item && item.fileType === 'image')
  const audio = attachments.filter((item) => item && item.fileType === 'audio')
  return {
    typeLabel: SCOPE_LABEL[p.scopeType] || '内容',
    timeText: timeText(p.createdAt, now),
    isNew: Boolean(p.isNew),
    pics: images.slice(0, 3).map((item) => ({ id: item.id, fileId: item.fileId, url: downloadUrl(item.fileId, token, base) })),
    picCount: images.length,
    hasAudio: audio.length > 0,
    audioCount: audio.length,
    likerText: p.likeNames || ''
  }
}

// 书架格子(原型 C1 / C2): 封面文字与底色、格子下面的小字、状态名、是否勾选
function shelfRowView(row, index) {
  const r = row || {}
  const statusText = STATUS_LABEL[r.status] || '在读'
  const progress = progressText(r)
  let subText = progress
  if (r.status === 'finished' && !(Number(r.lastPage) || 0)) subText = '读完'
  if ((r.status === 'wish' || r.status === 'favorite') && progress === '暂无进度') subText = statusText
  return {
    ...r,
    coverText: coverText(r.title),
    coverTone: coverTone(r.bookId != null ? r.bookId : index),
    subText,
    statusText,
    checked: Boolean(r.checked)
  }
}

// ===== 阅读记录页(原型 C3) =====
// 'YYYY-MM'
function monthKey(date) {
  const d = date || new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

// 前后挪几个月: 'YYYY-MM'
function shiftMonth(key, delta) {
  const m = String(key).match(/^(\d{4})-(\d{2})$/)
  if (!m) return monthKey()
  const d = new Date(Number(m[1]), Number(m[2]) - 1 + delta, 1)
  return monthKey(d)
}

const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

// 阅读记录页要显示的全部数据: 月份标题、打卡日历(周一起始)、四格、近几日时间线。
// records = 阅读记录接口({month, days:[{date, count}], readDays, readCount}); personal = 个人统计; awake = 今日觉醒状态。
function recordsView(key, records, personal, awake, now) {
  const today = now || new Date()
  const current = monthKey(today)
  const month = /^(\d{4}-\d{2})$/.test(String(key || '')) ? String(key) : current
  const [year, monthNo] = month.split('-').map(Number)
  const first = new Date(year, monthNo - 1, 1)
  const daysInMonth = new Date(year, monthNo, 0).getDate()
  const isCurrent = month === current
  // 某天读了多少篇: 按“几号”查。当月还没到的日子即使接口里有记录也不算(正常不会有)
  const countOf = new Map()
  const lastDay = isCurrent ? today.getDate() : daysInMonth
  for (const row of (records && Array.isArray(records.days) ? records.days : [])) {
    const m = String(row.date || '').match(/^(\d{4})-(\d{2})-(\d{2})$/)
    if (m && Number(m[1]) === year && Number(m[2]) === monthNo && Number(m[3]) >= 1 && Number(m[3]) <= lastDay) countOf.set(Number(m[3]), Number(row.count) || 0)
  }
  const cells = []
  const lead = (first.getDay() + 6) % 7                      // 周一起始: 周一=0 … 周日=6
  const prevDays = new Date(year, monthNo - 1, 0).getDate()   // 上个月有几天
  // key: 列表项的唯一标识(补位格的日期会重复, 不能拿日期当标识)
  for (let i = lead - 1; i >= 0; i--) cells.push({ key: 'c' + cells.length, day: prevDays - i, state: 'out' })
  for (let day = 1; day <= daysInMonth; day++) {
    const isToday = isCurrent && day === today.getDate()
    const future = isCurrent && day > today.getDate()
    cells.push({ key: 'c' + cells.length, day, state: isToday ? 'today' : (countOf.has(day) ? 'done' : (future ? 'future' : '')) })
  }
  // 最后一周不满 7 格的, 用下个月的 1、2、… 补齐
  let trailing = 1
  while (cells.length % 7 !== 0) cells.push({ key: 'c' + cells.length, day: trailing++, state: 'out' })
  const readDays = countOf.size
  const elapsed = isCurrent ? today.getDate() : daysInMonth
  // 时间线: 今天一行是觉醒三件套的进度; 下面是有阅读记录的日子, 右边写“到那一天为止连续读了几天”
  const timeline = []
  const a = awake || {}
  if (isCurrent) {
    const progress = awakeningView(a, personal, null)
    const zaidu = Math.max(0, (Number(a.todayPosts) || 0) - (Number(a.todayRecitations) || 0) - (Number(a.todayGifts) || 0))
    timeline.push({ key: 't' + timeline.length, left: `今天已发布 · 诵读 ${Number(a.todayRecitations) || 0} · 随施 ${Number(a.todayGifts) || 0} · 再读 ${zaidu}`, right: progress.nextScope ? `还差${progress.nextLabel.replace('去', '')}` : '三件齐了' })
  }
  // 一段连续的日子合成一行: 写最后那天、这一段一共读了几篇、连续几天
  const recorded = [...countOf.entries()].sort((x, y) => y[0] - x[0])
  for (const [day] of recorded) {
    if (countOf.has(day + 1)) continue                         // 第二天也读了: 这一天属于更靠后那一段, 已经写过了
    let streak = 0
    let total = 0
    for (let d = day; countOf.has(d); d--) { streak += 1; total += countOf.get(d) }
    const date = new Date(year, monthNo - 1, day)
    const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1)
    const label = sameDay(date, today) ? '今天' : (sameDay(date, yesterday) ? '昨天' : `${String(monthNo).padStart(2, '0')}-${String(day).padStart(2, '0')}`)
    timeline.push({ key: 't' + timeline.length, left: `${label} · 阅读 ${total} 篇`, right: `连续 ${streak} 天` })
  }
  const p = personal || {}
  return {
    monthKey: month,
    monthLabel: `${year}年${monthNo}月`,
    cells,
    readDays,
    daysInMonth,
    doneLabel: `已读 ${readDays}/${elapsed} 天`,
    // “下个月”只有在还没到当月时才能点('YYYY-MM' 直接比大小)
    canNext: month < current,
    timeline,
    tiles: [
      { num: Number(p.myStreakDays) || 0, unit: '天', label: '连续阅读' },
      { num: Number(p.myReadDays) || 0, unit: '天', label: '累计打卡' },
      { num: Number(p.monthSongdu) || 0, unit: '条', label: '本月诵读' },
      { num: Number(p.monthSuishi) || 0, unit: '次', label: '本月随施' }
    ]
  }
}

module.exports = { SCOPE_LABEL, STATUS_LABEL, WORK_TITLE, coverText, coverTone, timeText, progressText, awakeningView, reminderView, teamTotals, trendBadge, reciteText, downloadTokenOf, downloadUrl, postView, publishSuccessView, shelfRowView, monthKey, shiftMonth, recordsView }
