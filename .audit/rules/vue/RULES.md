# Vue / JS / TS / CSS(前端细则)

静态分析主通道=服务端 SonarQube(aigicrm-ui/src 已在 sonar.sources);
本地补充约定:

- FE-01 组件内禁止直接操作其他组件实例;$refs 只读自身
- FE-02 环境地址走 Vite env;禁止源码硬编码 API 域名
- FE-03 路由守卫与菜单渲染以 auth/info 权限码为准;按钮级 v-perm 与后端 @PreAuthorize 同码
- FE-04 测试:Vitest 行为断言优先;235 条 Vue warn=纯测试环境产物,不计线上问题(先例)
- FE-05 stylelint 未装(TOOL_NOT_INSTALLED);样式问题暂由 Sonar 覆盖,装后启用
