// AIGICRM 小程序：一键上传体验版 / 生成预览二维码（miniprogram-ci 包装）
//
// 为什么要这层包装：
//   · 微信开发者工具点上传要求人工扫码登录，无法脚本化/CI 化；miniprogram-ci 用「代码上传密钥」即可自动化。
//   · 密钥是**凭据**：本脚本只从环境变量或命令行读取密钥文件路径，脚本内不落任何密钥，也不打印密钥内容。
//
// 用法（在小程序源码目录之外运行，避免 node_modules 进小程序包）：
//   cd 02-迭代源代码/scripts/miniprogram && npm install
//
//   上传体验版：
//     MINIPROGRAM_PRIVATE_KEY=/绝对路径/private.<appid>.key \
//     MINIPROGRAM_VERSION=1.0.0 MINIPROGRAM_DESC="首次体验版" npm run upload
//
//   生成预览二维码（手机扫码即可用，等价开发者工具"预览"）：
//     MINIPROGRAM_PRIVATE_KEY=/绝对路径/private.<appid>.key npm run preview
//
// 环境变量：
//   MINIPROGRAM_PRIVATE_KEY  必填，微信公众平台「开发管理 → 开发设置 → 小程序代码上传」生成的密钥文件路径
//   MINIPROGRAM_APPID        选填，默认读 project.config.json 的 appid
//   MINIPROGRAM_VERSION      上传用版本号，默认取当天日期 1.0.<MMDD>
//   MINIPROGRAM_DESC         上传备注，默认 "CI 上传"
//   MINIPROGRAM_ROBOT        上传机器人编号 1-30，默认 1（同版本重复上传会被拒，换 robot 或版本号）
//   MINIPROGRAM_QR_OUTPUT    预览二维码输出路径，默认 ./preview-qrcode.png（已 gitignore）
//   MP_TARGET                上传目标：prod（默认，包指向生产）或 staging（测试环境）。见下方「上传守卫」
//   MINIPROGRAM_DRY_RUN      =1 时只做检查并打印将要上传的内容：不加载 miniprogram-ci、不联网、不要求密钥
//   MINIPROGRAM_PROJECT_PATH 选填，小程序源码目录（默认 aigicrm-miniprogram；供行为测试使用）
//
// 上传守卫（GL-10 2026-11-24）——真实上传之前必须全部满足，否则以退出码 3 结束：
//   ① 小程序源码目录没有未提交改动。未跟踪文件也会被打进包，同样算。
//   ② 存在本机覆盖档 config/env.local.js 时，必须显式声明 MP_TARGET=staging。
//      覆盖档已 gitignore，但会被打进上传包；体验版也读它之后（GL-15），带着它上传的包会指向非生产环境。
//   ③ 声明了 MP_TARGET=staging 时，覆盖档必须存在（否则这个包其实指向生产，名不副实）。
//   预览不受守卫限制：那是开发者自己扫码看，带着改动预览是常态。
//   守卫通过后，目标与提交号会写进上传备注，线上版本因此可以追溯到提交。
//   判定逻辑在 ./upload-guard.mjs；行为测试在 ../test-miniprogram-upload-guard.js。
//
// 退出码：0 成功；1 参数/文件问题；2 上传失败；3 上传守卫拒绝

import { readFileSync, existsSync, mkdirSync, realpathSync } from 'node:fs'
import { dirname, isAbsolute, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'
import { parseTarget, decideUpload, isNestedRepo, nestedRepoRefusal, noGitRefusal, OVERRIDE_REL } from './upload-guard.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(HERE, '..', '..') // 02-迭代源代码
const PROJECT_PATH = process.env.MINIPROGRAM_PROJECT_PATH
  ? resolve(process.env.MINIPROGRAM_PROJECT_PATH)
  : resolve(REPO_ROOT, 'aigicrm-miniprogram')
const DRY_RUN = process.env.MINIPROGRAM_DRY_RUN === '1'

const mode = process.argv[2] || 'upload'
const projectConfigPath = resolve(PROJECT_PATH, 'project.config.json')
if (!existsSync(projectConfigPath)) {
  console.error(`找不到小程序项目配置：${projectConfigPath}`)
  process.exit(1)
}
const projectConfig = JSON.parse(readFileSync(projectConfigPath, 'utf8'))
const appid = process.env.MINIPROGRAM_APPID || projectConfig.appid
const keyPath = process.env.MINIPROGRAM_PRIVATE_KEY

if (!appid) {
  console.error('缺少 appid：请设置 MINIPROGRAM_APPID 或确认 project.config.json 中的 appid')
  process.exit(1)
}
if (!DRY_RUN) {
  // 演练模式不碰密钥：它的用途就是在找不到密钥的机器上先确认守卫会不会放行
  if (!keyPath) {
    console.error('缺少上传密钥：请设置 MINIPROGRAM_PRIVATE_KEY=/绝对路径/private.<appid>.key（密钥请在微信公众平台生成并自行保管）')
    process.exit(1)
  }
  if (!existsSync(keyPath)) {
    console.error(`密钥文件不存在：${keyPath}`)
    process.exit(1)
  }
  if (isAbsolute(keyPath) && keyPath.startsWith(REPO_ROOT)) {
    console.warn('⚠️  密钥文件位于仓库内，存在被误提交的风险：建议移到仓库外（如 ~/.wechat-mp/）')
  }
}

// ---------- 上传守卫：只对上传生效，预览不受限 ----------
let uploadPlan = null   // { target, hasOverride, commit }
if (mode !== 'preview') {
  const target = parseTarget(process.env.MP_TARGET)
  const hasOverride = existsSync(resolve(PROJECT_PATH, OVERRIDE_REL))
  // 固定用参数数组启动 git，不经过 shell。取不到输出时返回 null。
  const gitOutput = (args) => {
    try {
      return execFileSync('git', ['-C', PROJECT_PATH].concat(args), { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
    } catch (e) {
      return null
    }
  }
  const topSelf = gitOutput(['rev-parse', '--show-toplevel'])
  let topParent = null
  try {
    topParent = execFileSync('git', ['-C', dirname(PROJECT_PATH), 'rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch (e) {
    topParent = null
  }
  const projectReal = realpathSync(PROJECT_PATH)
  // 只看小程序目录：上传的只是这个目录。取不到输出一律按"无法判断"处理, 不放行。
  const statusOut = topSelf === null ? null : gitOutput(['status', '--porcelain', '--', '.'])
  // 先认"小程序目录是不是一个嵌套在外层仓库里的仓库"：是的话 git 看到的是嵌套仓库的状态，后面的检查都没有意义
  const result = isNestedRepo({ topSelf, topParent, project: projectReal })
    ? nestedRepoRefusal()
    : (topSelf === null || statusOut === null)
      ? noGitRefusal()
      : decideUpload({ dirtyEntries: statusOut === '' ? [] : statusOut.split('\n'), hasOverride, target })
  if (!result.ok) {
    console.error('❌ 上传守卫拒绝：')
    for (const line of result.reasons) console.error('   ' + line)
    console.error('   怎么处理：')
    for (const line of result.hints) console.error('   · ' + line)
    process.exit(3)
  }
  // 小程序目录属于哪个仓库, 提交号就取自哪个仓库
  const commit = gitOutput(['rev-parse', '--short', 'HEAD']) || 'unknown'
  uploadPlan = { target: result.target, hasOverride, commit }
  console.log(`守卫     : 通过（目标=${result.target}，本机覆盖档=${hasOverride ? '有' : '无'}，commit=${commit}）`)
  if (hasOverride) {
    console.warn('⚠️  这个包带着本机覆盖档，指向非生产环境：只能用作测试环境的体验版，不要提交审核')
  }
}

const now = new Date()
const stamp = `${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
const version = process.env.MINIPROGRAM_VERSION || `1.0.${stamp}`
const baseDesc = process.env.MINIPROGRAM_DESC || 'CI 上传'
const robot = Number(process.env.MINIPROGRAM_ROBOT || 1)
// 上传备注带上目标与提交号：线上版本因此可以追溯到提交。覆盖档的包另有醒目标记，避免被当成正式包。
const desc = uploadPlan
  ? `${baseDesc} [${uploadPlan.target}${uploadPlan.hasOverride ? '+本机覆盖档·仅限测试环境' : ''} commit=${uploadPlan.commit}]`
  : baseDesc

console.log(`项目目录 : ${PROJECT_PATH}`)
console.log(`AppID    : ${appid}`)
console.log(`模式     : ${mode}${DRY_RUN ? '（演练：不加载上传依赖、不联网）' : ''}`)

if (DRY_RUN) {
  if (uploadPlan) {
    console.log(`将上传   : 版本 ${version}，备注 ${desc}`)
    console.log('演练模式 : 检查已通过，未上传。正式上传请去掉 MINIPROGRAM_DRY_RUN。')
  } else {
    console.log('演练模式 : 预览不生成二维码，未上传任何内容。')
  }
  process.exit(0)
}

// ---------- 以下才会加载 miniprogram-ci 并联网 ----------
const require = createRequire(resolve(HERE, 'package.json'))
let ci
try {
  ci = require('miniprogram-ci')
} catch (e) {
  console.error('缺少依赖 miniprogram-ci：请先在本目录执行 npm install')
  process.exit(1)
}

const project = new ci.Project({
  appid,
  type: 'miniProgram',
  projectPath: PROJECT_PATH,
  privateKeyPath: keyPath,
  ignores: ['node_modules/**/*', '**/*.md'],
})

try {
  if (mode === 'preview') {
    const out = process.env.MINIPROGRAM_QR_OUTPUT || resolve(HERE, 'preview-qrcode.png')
    mkdirSync(dirname(out), { recursive: true })
    const result = await ci.preview({
      project,
      desc,
      robot,
      setting: { es6: true, minify: true },
      qrcodeFormat: 'image',
      qrcodeOutputDest: out,
      onProgressUpdate: (p) => p && p.message && console.log('  ·', p.message),
    })
    console.log(`✅ 预览二维码已生成：${out}（用微信扫码即可在手机上打开开发版）`)
    if (result) console.log('  附加信息：', typeof result === 'string' ? result : JSON.stringify(result).slice(0, 200))
  } else {
    const result = await ci.upload({
      project,
      version,
      desc,
      robot,
      setting: { es6: true, minify: true },
      onProgressUpdate: (p) => p && p.message && console.log('  ·', p.message),
    })
    console.log(`✅ 已上传版本 ${version}（${desc}）。下一步：微信公众平台 → 版本管理 → 设为体验版并添加体验成员`)
    if (result) console.log('  附加信息：', typeof result === 'string' ? result : JSON.stringify(result).slice(0, 200))
  }
} catch (e) {
  const msg = String(e && e.message ? e.message : e)
  console.error('❌ 失败：', msg)
  if (/ip|whitelist|非法|invalid ip/i.test(msg)) {
    console.error('   提示：需在公众平台「开发管理 → 开发设置 → 小程序代码上传」把当前出口 IP 加入白名单')
  }
  if (/private ?key|密钥/i.test(msg)) {
    console.error('   提示：确认密钥与该 AppID 匹配、且已在公众平台重新生成/下载')
  }
  process.exit(2)
}
