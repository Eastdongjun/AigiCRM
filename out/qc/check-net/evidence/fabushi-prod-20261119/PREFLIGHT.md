# 生产窗口前置取证（2026-11-19 06:0x UTC，QC fabushi-upload-security-debt，全程只读）

## pin 六项（全符阶段 A 基线）
health=200 ｜ DB=ycmn ∧ FILE_GRANT_MODE=new ｜ jar=2ea180c67e011630337ab6da17eaa588 ｜ Flyway latest=2026.11.19.2 failed=0 ｜ biz_folder_type_default=87 ｜ sys_user max=82

## C7 现场实锤（/mp-file-picker 路由响应头）
- Referrer-Policy: strict-origin-when-cross-origin ✅（在）
- CSP script-src 'self' 'unsafe-eval' ——**缺 https://res.wx.qq.com** → 微信 JSSDK 被拦（FilePicker.vue onerror 降级路径触发，"上传后自动返回"失效）
- 与 c7-nginx-20261118/summary.md 缺口 B 判定一致；缺口 A（referer 脱敏 map）随下发仓库 conf 一并解决

## CERT-EXPIRY-001 就地取证（解除 BLOCKED）
notBefore=Jun 29 2026 ｜ **notAfter=Jan 13 2027**（自今约 55 天）→ 建议 2026-12 中旬前续期

## DEPLOY-PROD-001 复核结论
新版 deploy-backend.sh 严格更优（fail-closed/env 钉/TLS 预检/原子上传/自动回滚）；env 钉三项已验证兼容 → 脚本更新列窗口步骤 0

## 产物
后端 /tmp/qc-prod.jar（zip 手术 v2，HEAD 4b5ea1e9：633 条目零陈旧/185 lib/MSG_H5_DISABLED 在）；前端 dist 5.2M（npm build RC=0）

## 窗口序列（分工：20=0-4，QC=5-6）
0 deploy-backend.sh 脚本更新（备份→.new→bash -n→原子 mv）｜0.5 仓配补 /mp-fabushi-uploader location（20 小批）｜1 deploy-db.sh（仅 V2026.11.19.3，自动预 dump）｜2-3 deploy-backend.sh 新 jar（自动回滚武装）｜4 deploy-frontend.sh NAS 原子切换 ｜5 QC：nginx C7 下发+reload+双路由验证 ｜6 QC：L5 终验（pin 复核/三查/红线 grep/日志 ERROR 观察）
回滚：jar=脚本自动回滚；迁移=19.3 逆写（迁移头已备）；前端=NAS dist-rollback-*；nginx=conf 备份回放
