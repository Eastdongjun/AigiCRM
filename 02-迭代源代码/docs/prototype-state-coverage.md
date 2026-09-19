# 小程序原型状态覆盖矩阵

> 定位说明（2026-09-12）：小程序是**真实客户端**（release/trial 直连 `https://www.aigicrm.com`，见 `config/env.js`）。
> 本文件中的"原型"仅指**历史界面基准**（51 张原型截图），用于核对界面与状态覆盖，不代表小程序以 demo 形态交付。
> `demoMode: true` 只在 `develop` 档出现，用于离线浏览，不作为真实权限或数据隔离证据。
> 权限码与迁移链的一致性由 `scripts/check-miniprogram-permission-contract.py` 离线把关。

> 目的：解释原型基准 51 张截图与当前 46 个独立注册页面之间的差额。通用状态不重复注册为页面，但必须在真实页面中可进入、可验证。

## 当前口径

| 类别 | 数量 | 说明 |
| --- | ---: | --- |
| 独立页面 | 46 | 以 `aigicrm-miniprogram/app.json` 的 `pages` 为准（`scripts/check-wechat-project-root.sh` 硬断言 46 页） |
| 原型截图基准 | 51 | 历史原型截图总数 |
| 通用/页面状态差额 | 5 | 不新增路由，绑定到已有页面状态 |

## 六类状态

| 编号 | 状态 | 代码证据 | 验收方式 | 当前状态 |
| --- | --- | --- | --- | --- |
| S1 | 加载中 | `pages/*/index.wxml` 的 `loading` 分支 | 在页面请求期间检查加载提示不遮挡操作区 | 已实现 |
| S2 | 空数据 | 客户、审批、公海、提醒、文件、统计等页面的 `length===0` 分支 | 使用空响应或演示数据切换验证空状态文案 | 已实现 |
| S3 | 请求失败 | 页面 JS 的 `showError(error, ...)` 分支 | 断开接口或返回非 2xx，确认错误提示且页面可恢复 | 自动化回归已覆盖；微信开发者工具/真机人工验收见 `docs/mobile-manual-qa-checklist.md` |
| S4 | 无权限/未登录 | 页面 JS 的 `hasPermission(...)` 守卫与登录跳转 | 使用无权限身份访问页面，确认不展示敏感数据 | 已实现；QA API 矩阵已验证（登录 200、权限数 0、受保护接口 403），小程序 DevTools/真机权限遍历见 `docs/mobile-manual-qa-checklist.md` |
| S5 | 详情不存在 | 详情页的 `!loading && !detail/record/approval` 分支 | 使用不存在 ID 访问详情，确认空详情而非白屏 | 已实现 |
| S6 | 操作提交失败/恢复 | 审批、捞取、反馈、分享等动作的 `catch/finally` 分支 | 返回失败后检查 loading 复位、错误可见、可再次操作 | 自动化回归已覆盖；故障注入和人工验收见 `docs/mobile-manual-qa-checklist.md` |

## 已接入的 DevTools 预检

激励附件 UI 已有微信开发者工具自动化预检：在 `scripts/miniprogram` 下执行 `npm run verify:incentive-attachments`，覆盖激励上传页的已选图片缩略图、预览/下载/移除按钮，激励列表页附件行，以及激励详情页图片/文档/普通文件附件行。该检查只证明页面层和模拟数据渲染正确；真机选择文件、真实提交记录、真实下载保存、权限账号差异和 PC 同数据对账仍按 `docs/mobile-manual-qa-checklist.md` 执行并留证。

## 结论

当前页面数量应报告为 **46 个独立页面**，原型覆盖报告为 **46/51（90.2%）**。这不是把状态页面遗漏后强行合并；而是明确区分“路由页面”与“同一页面的状态截图”。补齐剩余原型验收时，先运行已落地的 DevTools 激励附件预检，再按 `docs/mobile-manual-qa-checklist.md` 做 S1-S6 的微信开发者工具/真机状态截图、故障注入、权限遍历、真实上传下载和 PC 同数据对账证据；S4 已有 QA API/后端证据，但仍需补小程序真实 UI 遍历证据。
