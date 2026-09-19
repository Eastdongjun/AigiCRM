// 本机覆盖档模板：**复制为 config/env.local.js，不要覆盖 config/env.js**。
//
//   cp aigicrm-miniprogram/config/env.local.example.js aigicrm-miniprogram/config/env.local.js
//
// 生效范围：**只影响 develop 档**（微信开发者工具）。release/trial 永远走生
// 产地址，不受本文件影响；删除 config/env.local.js 即回到 demo 档。
//
// 常用取值：
//   · 本地后端           http://127.0.0.1:8080   （docker-compose up -d）
//   · 测试服（先开隧道） http://127.0.0.1:8080   （ssh -L 8080:127.0.0.1:80 ubuntu@TEST_SERVER_IP）
//
// 本文件已 gitignore，不会入库；请勿在此写入任何凭据——认证一律走登录页。
module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: 'http://127.0.0.1:8080',
  demoMode: false
}
