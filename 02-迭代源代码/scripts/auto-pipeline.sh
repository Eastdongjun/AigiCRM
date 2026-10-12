#!/bin/bash
# 【已退役 2026-10-12】本脚本是 2026-08-04 的一次性交付流水线, 已完成历史使命, 禁止再执行:
#   · Phase 1 的 git add 硬编码的是 2026-08-04 那批文件清单, 今天跑会提交错误的文件集;
#   · Phase 2 硬编码执行 sql/migration/20260804/ 下两个旧迁移(已被 flyway 取代);
#   · 全程不跑 scripts/gate.sh, 是一条"绕过门禁直通生产"的通道(全量体检 2026-10-12 强制链断层项)。
#
# 现行发布链(均有 md5/env 契约门禁):
#   bash scripts/gate.sh                          # 一键门禁(后端全量测试+前端测试/构建+MP语法+文档新鲜度+INV-10)
#   bash scripts/deploy-db.sh                     # 生产库迁移(flyway)
#   bash scripts/deploy-backend.sh                # 生产后端(md5 门禁)
#   bash scripts/deploy-frontend.sh               # 生产前端
# 保留本文件仅为 git 历史可考; 如需流水线重建, 以 gate.sh 为唯一前置强制步骤重新设计。
echo "❌ auto-pipeline.sh 已于 2026-10-12 退役(2026-08-04 一次性脚本, 硬编码旧文件/旧迁移且绕过门禁)。" >&2
echo "   现行发布链: gate.sh → deploy-db.sh → deploy-backend.sh → deploy-frontend.sh" >&2
exit 1
