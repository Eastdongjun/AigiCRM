# 新会话启动指南

> 新开 Claude Code 窗口后,让 Claude 读本文件 + 下面的话术,最快恢复上下文。

## 启动话术(复制使用)

**场景 1:继续开发/修复**
```
继续 AIGICRM 客户管理系统项目。先读 /Users/donglai/东来/01-Projects/01-aigicrm/04-源码/docs/README.md
和 CLAUDE.md,然后检查:git 状态、生产健康(scripts/smoke-test.sh prod)、待办清单。
当前我要做的是:【你的需求】
```

**场景 2:巡检/运维**
```
巡检 AIGICRM 生产环境(PROD_SERVER_IP):运行 scripts/pre-delivery-check.sh 检查全套状态,
再看 NAS 每日日报 /Aigicrm/files/backup/daily/<今天>/report.md,报告健康状况和问题。
```

**场景 3:部署**
```
按 scripts/deploy-backend.sh / deploy-frontend.sh / deploy-db.sh 部署 AIGICRM,
部署前先跑 verify-delivery.sh 门禁,生产变更先向我确认再执行。
```

## 关键路径速查

| 内容 | 路径 |
|---|---|
| 权威源码(git 仓库,唯一真源) | `/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/` |
| 知识资产 | 同上 `docs/`(database 数据字典/permission 权限/api 接口/ops 运维) |
| 部署/门禁/测试脚本 | `scripts/`(verify-delivery/smoke-test/pre-delivery-check/deploy-*/gen-*) |
| 功能包(交付记录) | `/Users/donglai/Agent/0-Claude/aigicrm-workspace/engineering/features/` |
| 配置矩阵(三环境) | `docs/deployment/config-matrix.md` |
| 完成定义 DoD | `docs/ops/dod.md` |
| 故障手册 | `docs/ops/incident.md` |
| 生产服务器 | `root@PROD_SERVER_IP` |
| 测试服务器 | `ubuntu@TEST_SERVER_IP` |
| 部署凭据 | `~/.aigicrm-deploy.env`(不入 git,chmod 600) |
| NAS 备份/每日日报 | 生产 `/Aigicrm/files/backup/` |

## 关键规则(记忆已吸收,简述)

1. **生产变更必须先经用户确认**(教训:2026-08-02 未经确认部署被叫停)
2. 交付前必须跑 `scripts/pre-delivery-check.sh`(知识资产+需求确认+冒烟+门禁)
3. 开发与审查分离:code-reviewer/security-reviewer 独立审查,记录存档 evidence/review.md
4. 数据字典/权限字典/API 清单由脚本自动生成,库/接口变更后必须重新生成并提交
5. 需求模板:需求确认=是 + 就绪 10 项清单,未确认不得进入开发
6. 凭据不写进代码/提示词/记忆,只引用位置

## 版本状态(2026-08-02)

- 生产:v2.7.0(后端 jar md5 9f681956 / 前端 index-DMDq9ywO.js)
- 后端安全修复已上线(logo 端点路径穿越,commit 9501897)
- 回滚:旧 jar 在 `/root/aigicrm/backup-pre-deploy-20260802/aigicrm-backend-1.0.0.jar`(94700361B)
