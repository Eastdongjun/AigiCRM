# 代码问题核查处置记录（2026-08-08）

> 来源：Claude 代码审查（对照 7-31 双审报告 + TECH_DEBT.md + 全面检查完整报告）。本文件记录各项发现与处置状态。

## 处置总览

| 项 | 发现 | 处置 | 状态 |
|---|---|---|---|
| N1-A | 生产 MySQL root 密码明文硬编码，已入 git 历史 | 密码移出 git 文件，改经 `~/.ycmn-deploy.env` 注入 | ✅ commit `084ac65` |
| N1-B | root 密码泄露 + 应用用 root 连库 | 最小权限账号（ycmn_app）+ 备份账号（ycmn_backup）+ **root 密码轮换**（Step1-4 全部完成） | ✅ commits `7dc3119/8731677/74dce58` |
| N2 | 租户隔离默认关 | **单集团部署，不启用**（用户确认） | ✅ 决策记录 |
| N3 | 从未开票 NULL→9999 天超期误报 | `invoice_never` 独立提醒 + 存量清理 + 提醒页 UI 接入 | ✅ commits `9a92e9c/881f3ee`，已部署 |
| 前端孤儿文件 | `src/index.vue`/`index.js` 废弃副本 | 删除（无引用） | ✅ commit `283b513` |
| 生产 Swagger/SQL日志 | `/v3/api-docs` permitAll + StdOutImpl + debug | `application-prod.yml`（关 Swagger + SQL 降 info）+ `SPRING_PROFILES_ACTIVE=prod` | ✅ commit `a79d625`，已部署验证 403 阻断 |
| 文档滞后 | data-dictionary/api-list/permission-dict 滞后 3-6 天 | **文档新鲜度门禁**：`check-doc-freshness.sh` 接入 pre-delivery-check `[7]`，重新生成 3 文档 | ✅ commit `709bc38` |
| 操作手册过时 | 品牌"运筹马年"、权限模型过时 | 重写至 v4.1.0（AigiCRM + 六级数据范围 + 新功能） | ✅ commit `300b7f7` |
| 越权回归测试缺口 | canManageUser（H3）、approve/reject（H4）无测试 | 新增 2 个测试文件 10 用例 | ✅ commit `f5384db`，全量 258 tests |
| **数据库零外键**（下季度项） | FK 是 IDOR 土壤 | **孤儿审计全 0** → FK 迁移 `sql/migration/20260808/db-foreign-keys.sql` 6 个强归属 FK，测试库 canary + 生产库**已应用**（用户确认），FK 强制生效验证（孤儿插入被 1452 拒绝） | ✅ commit `de5d3ae`，已应用测试+生产 |
| **6 张备份表混 schema** | biz_*_bak_* 等（0-31 行） | 已核：**全部无代码引用**；TECH_DEBT 既定"正常噪音"放行，暂不动 | 📋 保留，可归档待定 |

## 新增防复发机制

- `pre-delivery-check.sh [7]` 文档新鲜度：自动文档与库/源码不一致即 FAIL（堵"文档存在但不新鲜"漏洞）
- 越权回归测试：组织管理员（canManageUser）+ 审批（approve/reject）非审批人拒绝

## 7-31 报告核对

- B1/B3/B5/H1-H6/H8/H9/H11/M9/M12：已修复（审查报告第三节逐一验证）
- B2/B6、H7/H10/H12、M1-M3/M5/M6/M8：未逐条深挖，建议下轮核查
- M7/M10/M11：技术债保留，按 TECH_DEBT.md 节奏推进

## 遗留决策处置（2026-08-08 已全部结案）

- ✅ **数据范围过滤锚点**：保持 owner 部门归属（六级范围语义正确），客户自身 office_id 是属性筛选；如确有"按客户所属组织看"诉求单独立项。已记设计记忆。
- ✅ **测试库 root**：YCMN 测试运维改专用 `ycmn_admin`（commit `13f557d`，测试脚本不再用共享 root）；共享 root/<redacted> 保留（ShopJoy 微服务已停止 8 天但配置依赖，重启需兼容）。
- ✅ **git filter-repo**：**决定不做**。prod root 已轮换（123456 失效，安全收益 0）；全局 scrub 会改写当前 AGENTS.md/CLAUDE.md/运维手册等多处文档需修复；force-push 不可逆且重写全部远端分支。仅当需要"干净历史"作为独立立项时再做。
