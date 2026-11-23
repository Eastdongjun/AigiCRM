# B1 交付壳安装手册（最小可用实例）

> 状态：S6.1 已完成本机隔离实测；S6.2 最小向导已实现；S6.3/S6.4 未完成。
> 本手册只用于交付验证/新客户环境，**不替代生产部署审批**。

## 1. 前置

- Linux/macOS，Docker 24+（含 `docker compose` v2）
- 磁盘 ≥ 5GB、内存 ≥ 4GB
- 端口（可改）：后端 `18080`、前端 `18082`、MySQL `13306`、Redis `16379`
- 生产/amd64 如需锁定 MySQL 8.0.28：`MYSQL_IMAGE=mysql:8.0.28`；
  Flyway 默认 `flyway/flyway:9.22.3`（arm64 自动用 `9.22.3-alpine`）

## 2. 一键安装

```bash
cd 02-迭代源代码
# 先预检（只读）
bash scripts/product/preflight.sh --env-file /path/to/product.env \
  --ports 18080,18082,13306,16379

# 真装（显式 --apply）；未指定 env 时生成随机 env 到 scripts/product/runtime/<project>/
bash scripts/product/install.sh --apply --project aigicrm-b1-test \
  --http-port 18080 --web-port 18082 --mysql-port 13306 --redis-port 16379

# 只装后端 API
bash scripts/product/install.sh --apply --no-frontend ...
```

安装器顺序：预检 → 拉/建镜像 → MySQL/Redis → Flyway（164 迁移）→
组织/admin 种子（`pwd_reset_required=1`）→ backend → health → 前端构建/nginx →
经 `/api` 代理登录冒烟。可选 `--monitoring` 启动 node-exporter + Prometheus
（含告警规则）并验证抓到 `aigicrm_api_up=1`。失败 `down` 保留数据卷；`--down` 停止容器。

## 3. 首启

1. 浏览器打开 `http://<host>:18082/`，用 `admin` + `scripts/product/runtime/<project>/.env` 中初始口令登录。
2. 首登强制改密（`pwdResetRequired=1`）；改密后重新登录。
3. 进入 `/onboarding`：企业信息 → 行业词表 → 完成；写入 `onboarding.completed=true`。
4. 之后正常进入工作台。

## 4. 验证命令

```bash
curl -fsS http://127.0.0.1:18080/actuator/health   # UP，匿名不返回组件细节
curl -fsS http://127.0.0.1:18082/                 # 前端首页 200
bash scripts/product/test-install.sh              # 离线语法/preflight/dry-run 回归
```

## 5. 数据与回滚

- 数据卷：`<project>_mysql-data`、`<project>_redis-data`、`<project>_uploads`。
- 停止：`install.sh --project ... --down`（保留卷）。
- 彻底清理（仅测试）：`docker compose -p <project> -f scripts/product/docker-compose.yml \
  --env-file .../\.env down -v`。
- 升级 jar：`--jar` 指向新 jar 后重跑 `--apply`；Flyway 只增量迁移。

## 6. 已知限制

- S6.2 只做到企业/行业/完成；业务演示数据导入未做。
- S6.3 只有 industry 字典可选/新增；其他行业文案未配置化。
- S6.4 监控未接线；Actuator health/prometheus 存在，但外部 Prometheus 抓取/告警未落地。
- 前端构建在受限沙箱可能受 `~/.docker/buildx/activity` 写权限影响；可把 `DOCKER_CONFIG`
  指向工作区内可写目录（本机安装实测采用此法）。
- 真机/生产发布仍需单独门禁与授权，本手册不构成发布批准。
