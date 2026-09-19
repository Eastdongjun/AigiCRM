# 20260917 incentive detail polish (轻量精修)

## Goal

用户反馈小程序激励详情页 UI 不够美观。选定轻量精修：只动 WXML/WXSS，不碰 JS 逻辑与契约。

## Changes (本轮新增)

- `aigicrm-miniprogram/pages/incentive/detail/index.wxml`：信息行外加一层
  `detail-info-group` 容器；其余一字不动（契约字符串、绑定、handler 全保留）。
- `aigicrm-miniprogram/pages/incentive/detail/index.wxss`：页面底色 `#f4f7fc`；
  hero 加深渐变；分值改为渐变徽章；信息行改分组卡片 + 分隔线 + 右对齐数值；
  时间轴连线渐变、节点加辉光；附件卡片加阴影、图标加内描边、按钮加高加渐变；
  空状态改虚线框。所有原有 class 名保留（`attachment-row` 等 automator 断言依赖）。

## 未碰

- `index.js` / `index.json` 本轮未改（工作区内 JS/JSON 的 diff 属既有未提交改动，非本轮引入）。

## Verification

- `node --check pages/incentive/detail/index.js`：通过。
- `git diff --check`（detail 目录）：通过。
- `scripts/check-mobile-incentive-contract.sh`：
  `PASS: mobile incentive query, detail, upload, and permission contract is aligned.`
- `scripts/dev-verify.sh miniprogram`：全部 PASS（46 pages；71 JS, 48 JSON, 82 WXML/WXSS）。
- 微信开发者工具 CLI preview（`pages/incentive/detail/index?id=336`）：构建成功 799.8 KB，
  仅 Electron punycode deprecation 警告（非业务代码）。

## Status

样式精修完成并通过门禁。真机视觉确认待用户在开发者工具/预览二维码中查看。
