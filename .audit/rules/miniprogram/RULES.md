# 微信小程序专项(规范§10)

## 网络与环境
- MINI-01 所有请求必须经统一 request 层(utils/request.js);禁止页面内裸 wx.request 绕过 401/403 语义
- MINI-02 环境地址只允许 config/env*.js;源码硬编码 http(s) 地址=MEDIUM
- MINI-03 环境档案:develop 默认 demo;release/trial 指向生产;env.local.js 仅影响 develop
- MINI-04 预览/自动化令牌与用户令牌分键;探针结束必须清 storage(假 token 401 风暴先例)

## 前端权限≠后端权限
- MINI-10 页面按钮 canXxx 只是显示层;真实判定在服务端(矩阵行为门禁守卫)
- MINI-11 任何用户可改的 userId/orgId/tenantId/recordId 不得作为授权依据,服务端重校验
- MINI-12 权限守卫失败必须可见(toast+跳转),不得静默白屏;冷启权限空窗期不得误踢(会话回填)

## 视图与数据
- MINI-20 页面数据必须经 *View 构建器+官方 setter;裸 setData 不重建派生字段(canClaimNow/metaText 等)
- MINI-21 分类树/统计数字须与后端口径一致(_recordCount 含子类聚合)
- MINI-22 删除/提交等破坏性操作:确认弹窗+结果反馈+列表刷新(无反应=缺陷)

## 构建验证
- MINI-30 DevTools cli auto 可用时接入编译验证;不可用输出 MANUAL_WECHAT_VALIDATION_REQUIRED
- MINI-31 外部改文件后必须 cli auto 重编译,否则模拟器跑旧包(实证:分类树计数旧包误报)
