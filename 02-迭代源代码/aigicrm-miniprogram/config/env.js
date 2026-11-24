// 运行时环境档案选择（真实客户端模式）
//
// 背景：小程序定位为**真实客户端**（2026-09-11 用户确认）。此前只有 demo 默认
// 与本地回环两个档案，release 包没有生产接口地址 ⇒ 无法真实联调。
//
// 设计：
//   · 默认档（本文件）：develop 为 **demo**（apiBaseUrl 为空、demoMode 为 true），
//     确保用微信开发者工具打开本仓库时不会意外打到生产。
//   · release / trial 包在**运行时**自动切到真实接口，无需手工改文件。
//   · 需要连本地/测试后端时，**不要覆盖本文件**，改为新建本机覆盖档
//     `config/env.local.js`（已 gitignore，不入库）：
//        cp ../docs/env-templates/env.local.example.js config/env.local.js
//     （模板在 docs/env-templates/，2026-09-23 移出代码包：example 不参与打包）
//     本文件在 develop 与 trial 档采用其中的地址；删除该文件，develop 回到 demo 档、trial 回到生产地址。
//   · 本机覆盖档**影响 develop 与 trial，不影响 release**：release 永远走生产地址。
//     【GL-15 2026-11-24】体验版也能读覆盖档：从配了覆盖档的机器上传的体验版指向测试环境，
//     用来在真机上验测试环境（测试地址须是已在微信后台登记的 HTTPS 域名）。
//     覆盖档跟着包出去、结果进了正式包的情形，由 scripts/miniprogram/upload.mjs 的上传守卫拦
//     （带着覆盖档只允许声明为测试环境上传）。界面上看不出体验版连的是哪个后端——
//     真机验证时用测试环境的账号登录，登不上就说明这个体验版连的是生产。
//   · 注意：体验版没有覆盖档时连的是**生产**。
//
// 运行时判定用 wx.getAccountInfoSync().miniProgram.envVersion，
// 与本仓库既有做法一致（见 pages/files/index.js:29-34 的版本门禁）。
// 注意：**不得**在本文件添加静态 envVersion 属性——历史上曾因此误判版本
// （见本文件原注释与 pages/files/index.js 的 fail-closed 白名单）。

var PROD_API_BASE = 'https://www.aigicrm.com'
var TRIAL_API_BASE = 'https://www.aigicrm.com'

function resolveEnvVersion() {
  try {
    var accountInfo = wx.getAccountInfoSync()
    return (accountInfo && accountInfo.miniProgram && accountInfo.miniProgram.envVersion) || 'develop'
  } catch (e) {
    // 非小程序运行时（构建期/单测）按 develop 处理：没有本机覆盖档时落到 demo 默认，安全方向。
    return 'develop'
  }
}

function resolveLocalOverride() {
  // 本机调试档 config/env.local.js（已 gitignore）。缺失或结构不对时返回空串。
  try {
    var local = require('./env.local')
    if (local && typeof local.apiBaseUrl === 'string' && local.apiBaseUrl) {
      return local.apiBaseUrl
    }
  } catch (e) {
    // 未配置本机覆盖档属正常情况，回落到默认档
  }
  return ''
}

var envVersion = resolveEnvVersion()
var apiBaseUrl = ''
if (envVersion === 'release') {
  apiBaseUrl = PROD_API_BASE
} else if (envVersion === 'trial') {
  // 【GL-15 2026-11-24】体验版先读本机覆盖档，没有才用生产地址。
  // 配了覆盖档就原样采用、不回落：宁可连不上，也不要让“以为在测测试环境”的体验版悄悄连到生产。
  // 小程序 request 合法域名必须是 HTTPS 且已在微信后台登记；裸 IP 微信不允许直接请求。
  apiBaseUrl = resolveLocalOverride() || TRIAL_API_BASE
} else if (envVersion === 'develop') {
  // develop 没有覆盖档时保持 ''（demo）；未知 envVersion 不走这里，也不读覆盖档。
  apiBaseUrl = resolveLocalOverride()
}

module.exports = {
  appName: 'AIGICRM', // 【2026-10-13】品牌回归 AIGICRM(A4 曾统一为运筹马年·市场资源管理系统,已按用户指令撤销);法务页用全称
  apiBaseUrl: apiBaseUrl,
  // 无接口地址时一律按 demo 运行，避免"地址配错却假装联网"。
  demoMode: !apiBaseUrl
}
