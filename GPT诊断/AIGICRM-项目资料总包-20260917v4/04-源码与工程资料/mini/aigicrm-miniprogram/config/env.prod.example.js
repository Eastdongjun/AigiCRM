// 生产环境档案模板（真实客户端）。
//
// ⚠️ 本文件是**模板**，供需要显式固定生产接口时复制为 config/env.js 使用。
//
// 通常情况下**不需要**它：config/env.js 会在运行时按
// wx.getAccountInfoSync().miniProgram.envVersion 自动选择接口地址
//   · release（正式版）→ https://www.aigicrm.com
//   · trial  （体验版）→ https://www.aigicrm.com
//   · develop（开发者工具）→ 空地址 + demo（避免误打生产）
//
// 只有在需要把某个通道临时指到别的地址（例如独立测试域名）时才复制本文件覆盖 env.js。
//
// 约束：
//   · 小程序 request 合法域名必须是 **HTTPS** 且已在微信后台登记；
//     裸 IP（如测试服 TEST_SERVER_IP）微信不允许直接请求，需走域名。
//   · **不要**在本文件内写任何凭据：认证一律走登录页，
//     小程序端不落任何口令/token 常量（契约脚本会 fail-closed 检查）。
module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: 'https://www.aigicrm.com',
  demoMode: false
}
