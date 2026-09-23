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
//     本文件在 develop 档采用其中的地址；删除该文件即回到 demo 档。
//   · 本机覆盖档**只影响 develop 档**：release/trial 永远走生产地址，
//     避免本机调试配置污染正式包。
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
    // 非小程序运行时（构建期/单测）按 develop 处理 ⇒ 落到 demo 默认，安全方向。
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
  // 体验版默认也指向生产。若要指向测试环境，改为 'https://<测试域名>'；
  // 注意小程序 request 合法域名必须是 HTTPS 且已在微信后台登记，
  // 裸 IP（如 <测试服IP>）微信不允许直接请求。
  apiBaseUrl = TRIAL_API_BASE
} else if (envVersion === 'develop') {
  // 仅 develop 档读取本机覆盖档；未知 envVersion 不走这里，保持 ''（demo）。
  apiBaseUrl = resolveLocalOverride()
}

module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: apiBaseUrl,
  // 无接口地址时一律按 demo 运行，避免"地址配错却假装联网"。
  demoMode: !apiBaseUrl
}
