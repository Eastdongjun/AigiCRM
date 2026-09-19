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
//
// 退出码：0 成功；1 参数/文件问题；2 上传失败

import { readFileSync, existsSync, mkdirSync } from 'node:fs'
import { dirname, isAbsolute, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(HERE, '..', '..') // 02-迭代源代码
const PROJECT_PATH = resolve(REPO_ROOT, 'aigicrm-miniprogram')

const require = createRequire(resolve(HERE, 'package.json'))
let ci
try {
  ci = require('miniprogram-ci')
} catch (e) {
  console.error('缺少依赖 miniprogram-ci：请先在本目录执行 npm install')
  process.exit(1)
}

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

const now = new Date()
const stamp = `${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
const version = process.env.MINIPROGRAM_VERSION || `1.0.${stamp}`
const desc = process.env.MINIPROGRAM_DESC || 'CI 上传'
const robot = Number(process.env.MINIPROGRAM_ROBOT || 1)

const project = new ci.Project({
  appid,
  type: 'miniProgram',
  projectPath: PROJECT_PATH,
  privateKeyPath: keyPath,
  ignores: ['node_modules/**/*', '**/*.md'],
})

console.log(`项目目录 : ${PROJECT_PATH}`)
console.log(`AppID    : ${appid}`)
console.log(`模式     : ${mode}`)

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
