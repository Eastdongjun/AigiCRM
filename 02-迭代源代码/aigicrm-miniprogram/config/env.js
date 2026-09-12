// 运行时环境档案选择（真实客户端模式）
//
// 背景：小程序定位为**真实客户端**（2026-09-11 用户确认）。此前只有 demo 默认
// 与本地回环两个档案，release 包没有生产接口地址 ⇒ 无法真实联调。
//
// 设计：
//   · 未检测到可用的真实接口时，**默认仍是 demo**（apiBaseUrl 为空、
//     demoMode 为 true）—— 保留既有契约
//     `scripts/check-miniprogram-local-profile.sh` 的反向断言，确保开发者用
//     微信开发者工具打开本仓库时不会意外打到生产。
//   · release / trial 包在**运行时**自动切到真实接口，无需手工改文件。
//   · develop（开发者工具）保持 demo，便于离线看原型；如需连本地后端，
//     把 config/env.local.example.js 复制为 config/env.js 覆盖即可。
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

var envVersion = resolveEnvVersion()
var apiBaseUrl = ''
if (envVersion === 'release') {
  apiBaseUrl = PROD_API_BASE
} else if (envVersion === 'trial') {
  // 体验版默认也指向生产。若要指向测试环境，改为 'https://<测试域名>'；
  // 注意小程序 request 合法域名必须是 HTTPS 且已在微信后台登记，
  // 裸 IP（如 111.229.37.220）微信不允许直接请求。
  apiBaseUrl = TRIAL_API_BASE
}
// develop 及任何未知值 ⇒ apiBaseUrl 保持 ''（demo）

module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: apiBaseUrl,
  // 无接口地址时一律按 demo 运行，避免"地址配错却假装联网"。
  demoMode: !apiBaseUrl
}
