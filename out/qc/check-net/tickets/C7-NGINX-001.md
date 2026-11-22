
---

## 销账（2026-11-19 生产窗口，QC fabushi-upload-security-debt）

**状态：CLOSED**。gap-A（referer 脱敏）：三级 map 上线+负例实测（明文零落日志）；gap-B（JSSDK CSP）：原 location 方案经窗口实锤为**设计缺陷**（内部重定向被 = /index.html 接管，从未生效）→ $request_uri map 方案修复（47a2ca7a）→ 三路由实测含 res.wx.qq.com、首页对照不含。证据：[证据报告] §二。
