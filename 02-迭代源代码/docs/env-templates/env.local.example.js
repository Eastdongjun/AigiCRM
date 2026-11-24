// 本机覆盖档模板：**复制为 aigicrm-miniprogram/config/env.local.js，不要覆盖 config/env.js**。
//
//   cp docs/env-templates/env.local.example.js aigicrm-miniprogram/config/env.local.js
//
// 生效范围：**影响 develop 档（微信开发者工具）与 trial 档（体验版），不影响 release**。
//   · develop：没有本文件时是 demo 档；有本文件就连本文件里的地址。
//   · trial（体验版）：有本文件就连本文件里的地址——用来在真机上验测试环境，地址必须是
//     已在微信后台登记的 HTTPS 域名；没有本文件时连的是**生产**。
//   · release（正式版）：永远是生产地址。
// 删除 config/env.local.js：develop 回到 demo 档，trial 回到生产。
// 【GL-15 2026-11-24】此前只影响 develop 档。
// 注意：体验版在界面上看不出连的是哪个后端。带着本文件上传时，上传脚本只允许声明为测试环境
// （scripts/miniprogram/upload.mjs）；真机验证请用测试环境的账号登录。
//
// 常用取值：
//   · 本地后端           http://127.0.0.1:8080   （docker-compose up -d）
//   · 测试服（先开隧道） http://127.0.0.1:8080   （ssh -L 8080:127.0.0.1:8080 ubuntu@<测试服IP>）
//   · 测试环境（真机）   https://<测试域名>      （须在微信后台登记为 request / uploadFile / downloadFile 合法域名）
//
// 2026-09-23 从 aigicrm-miniprogram/config/ 移出代码包：example 模板不参与打包，
// 消除开发者工具「代码质量-未使用的 JS 文件」未通过项；env.local.js 本身已 gitignore。
// 请勿在此写入任何凭据——认证一律走登录页。
module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: 'http://127.0.0.1:8080',
  demoMode: false
}
