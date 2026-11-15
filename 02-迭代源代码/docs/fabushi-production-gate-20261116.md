# 法布施 + 权限底座生产发布门禁清单（2026-11-16）

> **执行状态：未执行。** 本阶段明令禁止触碰生产；本文件只定义“满足什么条件才允许进入生产窗口”，
> 以及窗口内的顺序、验证、回滚。生产部署命令一律走 `scripts/deploy-*.sh`，禁止手工 docker run。

## 1. 入口条件（全部满足才可申请生产窗口）

| # | 条件 | 证据/命令 | 当前状态 |
|---|---|---|---|
| 1 | 集成分支为唯一基线 | `git log -1 integration/perm-fabushi-20261116` | PASS（源码树/测试树内容 0 diff） |
| 2 | 后端全量测试 | `mvn test`：1,099 / 0 failures | PASS |
| 3 | PC 单测/构建 | `npm test` 324；`npm run build` exit 0 | PASS |
| 4 | 完整门禁 | `bash scripts/gate.sh`：PASS=9 FAIL=0 | PASS（2026-11-16 01:58） |
| 5 | 4,704 格权限矩阵 | 24 码 × 28 账号 × 7 范围，FAIL=0 | PASS（4,312 PASS + 392 DATA_GUARD + 0 FAIL） |
| 6 | 小程序页面级/撤权 | `mp-evidence` + `mp-revoke-evidence` | PASS |
| 7 | PC 权限中心 9 码 | `pc-perm-center-evidence` | PASS |
| 8 | 迁移双口径 | 空库重放 + 快照库 validate；末态码单一源 `scripts/empty-replay-codes.txt` | PASS（空库 137 migrations/0 failed；快照 16.3 全 Success） |
| 9 | 文档新鲜度 | `gate.sh [4]`：落后 0 天 | PASS |
| 10 | 生产上传目录 | `/app/uploads/fabushi` 预建，owner 10001:10001 | 未执行（生产变更） |
| 11 | 备份与回滚演练 | DB/旧 jar/旧 dist/上传目录四类备份 | 未执行（生产窗口） |
| 12 | 变更审批 | 用户/负责人书面批准生产窗口与回滚人 | 未申请 |

## 2. 构建物与指纹（测试环境已验证）

- 后端 jar：`md5 b6975b9d172d3d64add8a01f04b34f12`（测试环境已部署，健康 UP）。
- 前端 dist：测试环境已部署；生产构建必须从同一 commit 重新构建并记录 `index.html`/入口资源 md5。
- 数据库：Flyway `2026.11.16.3`；`biz_fabushi_*` 12 表；9 个 `fabushi:*` 码；临时验收角色已清理。

## 3. 生产窗口顺序（未执行，仅清单）

```bash
# 0) 只在生产服务器执行；先确认 ~/.aigicrm-deploy.env 指向生产且源/目标 commit 一致
cd <repo>/02-迭代源代码/scripts

# 1) 数据库：脚本自动备份 → Flyway migrate → 结构验证
./deploy-db.sh

# 2) 上传目录（若尚未预建；不得用 chmod 777）
#    docker exec ycmn-backend mkdir -p /app/uploads/fabushi
#    docker exec ycmn-backend chown -R 10001:10001 /app/uploads/fabushi

# 3) 后端：测试服已验证 jar → 生产重建镜像 → 重启 → 健康检查（失败自动回滚）
./deploy-backend.sh

# 4) 前端：Mac 构建 → NAS dist 暂存校验 → 备份旧 dist → 原子切换入口
./deploy-frontend.sh

# 5) 冒烟（必须 PASS=20 FAIL=0）
./smoke-test.sh prod
```

## 4. 生产验收（窗口内逐项记录）

- [ ] `/actuator/health` UP；登录 200。
- [ ] admin `/auth/info` 含 9 个 `fabushi:*` 码；销售角色仅 view/create。
- [ ] `/fabushi/book`、`/fabushi/content`、`/fabushi/stats` 可打开；菜单含「法布施」组。
- [ ] PC 权限中心「法布施」组 9 码可见，依赖置灰生效；勾选/取消保存 200。
- [ ] 小程序（HTTPS 合法域名/体验版）：内容流/详情/评论/木鱼/已读正常；撤权后入口消失。
- [ ] 上传 `scope=fabushi` 200；文件落 `/app/uploads/fabushi`，配额计入个人。
- [ ] 跑一次 `scripts/smoke-test.sh prod` 与核心权限负例（越权 403）。

## 5. 回滚

| 对象 | 回滚动作 |
|---|---|
| 后端 | `deploy-backend.sh` 已保留旧 jar 并失败自动回滚；手工回滚用 `/data/aigicrm/backup/app-*.jar` 重建 |
| 前端 | NAS `backup/dist-rollback-<时间戳>/` 拷回 `/Aigicrm/files/aigicrm-dist/`；入口文件最后替换 |
| 数据库 | Flyway 前自动备份 `backup-pre-migrate-*.sql`；V15/V16 只增表/码/绑定，无删除业务数据；如需回退按备份恢复（停写窗口内） |
| 上传 | 本次只新增 `fabushi` 目录，不迁移旧文件；删除目录即回退（先确认无生产引用） |

## 6. 明确不做

- 不在生产执行空库重放（只在测试/临时库）。
- 不手工改 `sys_menu`/`sys_role_menu`，一律走 Flyway/权限中心。
- 不在生产直接跑本次的矩阵脚本（矩阵会改角色绑定，只允许测试环境）。
- 不在未满足入口条件时以“先上再补”方式发布。

## 7. 签字

- 发布负责人：__________  日期：__________
- 回滚负责人：__________  日期：__________
- 批准人：__________  日期：__________
