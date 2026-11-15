/**
 * 法布施小程序页面级验收（DevTools automator）
 *
 * 前置：开发者工具已用 `cli auto --project <mp> --auto-port 9424` 启动自动化。
 * 凭据：默认从 ~/.aigicrm-deploy.env 读取 HEALTH_CHECK_USER / HEALTH_CHECK_PASS，
 *       也可用 MP_USER / MP_PASS 环境变量覆盖。脚本不打印密码/token。
 * 输出：test-results/fabushi/mp-evidence-20261116.json + mp-*.png
 */
import { createRequire } from 'node:module'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const WS = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9424'
const OUT = path.resolve('test-results/fabushi')
const EVIDENCE = path.join(OUT, 'mp-evidence-20261116.json')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function loadEnvFile(p) {
  if (!fs.existsSync(p)) return {}
  const out = {}
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/)
    if (!m) continue
    out[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
  return out
}

const envFile = loadEnvFile(path.join(os.homedir(), '.aigicrm-deploy.env'))
const USER = process.env.MP_USER || envFile.HEALTH_CHECK_USER
const PASS = process.env.MP_PASS || envFile.HEALTH_CHECK_PASS
if (!USER || !PASS) {
  console.error('BLOCKED: 缺少登录凭据（MP_USER/MP_PASS 或 ~/.aigicrm-deploy.env HEALTH_CHECK_*）')
  process.exit(2)
}

const evidence = {
  timestamp: new Date().toISOString(),
  environment: 'test http://111.229.37.220',
  automator: WS,
  account: USER,
  steps: {},
  screenshots: [],
  errors: []
}

function record(name, ok, detail) {
  evidence.steps[name] = { ok, ...detail }
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`, detail && Object.keys(detail).length ? JSON.stringify(detail) : '')
  if (!ok) throw new Error(`step failed: ${name}`)
}

async function shot(mini, name) {
  const file = path.join(OUT, `mp-${name}.png`)
  await mini.screenshot({ path: file })
  evidence.screenshots.push(file)
  return file
}

let mini
try {
  mini = await automator.connect({ wsEndpoint: WS })
  console.log('connected', WS)
  mini.on('exception', (e) => evidence.errors.push(String(e && e.message ? e.message : e).slice(0, 200)))

  const appState = async () => mini.evaluate(() => {
    const g = getApp().globalData
    return {
      demoMode: Boolean(g.demoMode),
      permissions: Array.isArray(g.permissions) ? g.permissions : [],
      user: g.currentUser ? { id: g.currentUser.id, username: g.currentUser.username, admin: Boolean(g.currentUser.admin) } : null
    }
  })

  // 总是用目标账号重新登录：本地可能残留已被删除/失效账号的 token 与 globalData
  await mini.evaluate(() => {
    try { wx.removeStorageSync('AIGICRM_TOKEN::http://111.229.37.220') } catch (e) {}
    const g = getApp().globalData
    g.currentUser = null
    g.permissions = []
  })
  let state = await appState()
  {
    const page = await mini.reLaunch('/pages/login/index')
    await page.setData({ username: USER, password: PASS, agreed: true })
    await page.callMethod('submit')
    for (let i = 0; i < 20; i++) {
      await sleep(1000)
      state = await appState()
      if (state.user && state.permissions.length) break
    }
    evidence.steps.login = { ok: Boolean(state.user && state.permissions.length), path: (await mini.currentPage()).path }
    if (!state.user || !state.permissions.length) throw new Error('登录失败或权限未加载')
  }
  record('permissions', state.permissions.some((p) => p === '*' || p === 'fabushi:view'), {
    count: state.permissions.length,
    fabushi: state.permissions.filter((p) => String(p).startsWith('fabushi')),
    admin: state.user && state.user.admin
  })

  // 1) 工作台入口
  let page = await mini.reLaunch('/pages/fabushi/index')
  await sleep(2500)
  let d = await page.data()
  const entries = await page.$$('.fb-entry')
  const entryTitles = []
  for (const e of entries) entryTitles.push(((await e.text()) || '').trim())
  record('workbench', entries.length >= 2 && entryTitles.some((t) => t.includes('内容流')), {
    path: page.path, entries: entries.length, entryTitles, canCreate: d.canCreate, personal: d.personal
  })
  await shot(mini, 'workbench')

  // 2) 内容流
  page = await mini.reLaunch('/pages/fabushi/feed/index')
  await sleep(3000)
  d = await page.data()
  const posts = d.posts || []
  record('feed', posts.length > 0, {
    path: page.path, count: posts.length, total: d.total,
    first: posts[0] ? { id: posts[0].id, title: posts[0].title, likes: posts[0].likeCount, comments: posts[0].commentCount, readByMe: posts[0].readByMe } : null
  })
  await shot(mini, 'feed')
  const postId = posts[0] && posts[0].id

  // 3) 详情 / 评论 / 木鱼 / 已读
  page = await mini.reLaunch(`/pages/fabushi/detail/index?id=${postId}`)
  await sleep(3000)
  d = await page.data()
  record('detail', Boolean(d.post && d.post.id), {
    path: page.path, postId: d.post && d.post.id, title: d.post && d.post.title,
    comments: (d.comments || []).length, likeCount: d.likeCount, liked: d.liked, canCreate: d.canCreate
  })
  await shot(mini, 'detail')

  const beforeLike = { count: Number(d.likeCount) || 0, liked: Boolean(d.liked) }
  await page.callMethod('toggleLike')
  await sleep(1500)
  d = await page.data()
  const afterLike = { count: Number(d.likeCount) || 0, liked: Boolean(d.liked) }
  record('woodfish-toggle', afterLike.liked !== beforeLike.liked && afterLike.count !== beforeLike.count, {
    before: beforeLike, after: afterLike
  })
  // 还原木鱼状态，避免留下测试副作用
  await page.callMethod('toggleLike')
  await sleep(1500)
  d = await page.data()
  evidence.steps['woodfish-restore'] = {
    ok: Boolean(d.liked) === beforeLike.liked && Number(d.likeCount) === beforeLike.count,
    liked: d.liked, count: d.likeCount
  }

  const beforeComments = (d.comments || []).length
  const commentText = `MP验收-${Date.now()}`
  await page.setData({ commentText })
  await page.callMethod('submitComment')
  await sleep(2500)
  await page.callMethod('loadComments')
  await sleep(1500)
  d = await page.data()
  const comments = d.comments || []
  record('comment-submit', comments.length === beforeComments + 1 && comments.some((c) => c.content === commentText), {
    before: beforeComments, after: comments.length, matched: comments.some((c) => c.content === commentText)
  })
  await shot(mini, 'detail-commented')

  // 4) 已读回写：回内容流看 readByMe
  page = await mini.reLaunch('/pages/fabushi/feed/index')
  await sleep(3000)
  d = await page.data()
  const target = (d.posts || []).find((p) => p.id === postId)
  record('read-status', Boolean(target && target.readByMe), {
    postId, readByMe: target ? Boolean(target.readByMe) : null
  })

  evidence.result = 'PASS'
} catch (e) {
  evidence.result = 'FAIL'
  evidence.failure = String(e && e.message ? e.message : e)
  console.error('FAILED:', evidence.failure)
} finally {
  try { if (mini) await mini.disconnect() } catch (_) {}
  fs.mkdirSync(OUT, { recursive: true })
  fs.writeFileSync(EVIDENCE, JSON.stringify(evidence, null, 2))
  console.log('evidence:', EVIDENCE, 'result:', evidence.result)
  process.exit(evidence.result === 'PASS' ? 0 : 1)
}
