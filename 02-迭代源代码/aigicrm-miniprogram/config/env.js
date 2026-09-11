// NOTE: envVersion 已从静态配置中移除。
// Dev 自测入口的版本门禁现在使用运行时 wx.getAccountInfoSync().miniProgram.envVersion
// 进行 fail-closed 白名单判断（仅 'develop' 放行），不再依赖此静态值。
// 不要在此处添加 envVersion 属性——它曾被误认为能阻止 release 构建进入自测入口。

module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: '',
  // Preview default; set apiBaseUrl and demoMode=false for local backend integration.
  demoMode: true
}
