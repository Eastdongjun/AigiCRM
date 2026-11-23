# B1 交付壳（产品化）

目标：把 AIGICRM 从“能在一台机器上跑”推进到“干净服务器按文档装到能登录”。

## 当前状态

| 切片 | 状态 |
|---|---|
| S6.1a 安装前预检 | **已完成**：`preflight.sh` |
| S6.1b 后端最小安装（DB/迁移/种子/健康/登录/回滚） | **已完成**：`install.sh`（本机实测通过） |
| S6.1c 前端接入（构建/nginx/代理登录） | **已完成**：同一条 `--apply`，实测通过 |
| S6.2 首启向导（企业/行业/完成） | **最小闭环已完成**：首登强制改密 + `/onboarding` 向导；业务演示数据导入未做 |
| S6.3 行业词表配置化 | **部分完成**：向导可选用/新增 industry 字典；其他行业文案配置化未做 |
| S6.4 监控接线 | 未开始 |

## preflight.sh

只读检查，不启动容器、不改系统、不读取/打印凭据值。

```bash
bash scripts/product/preflight.sh
bash scripts/product/preflight.sh --env-file /path/to/product.env --ports 18080,13306,16379
```

检查项：

- `docker` / `docker compose` v2 / `curl` / `openssl` / `python3`
- Docker daemon 可连接
- 安装分区磁盘 ≥ 5GB；内存 ≥ 4GB（无法确认时 WARN）
- 端口可用（默认 18080/13306/16379，避开生产 80/443/3306/8080）
- 可选 env 文件：`DB_PASSWORD`、`APP_DB_USER`、`APP_DB_PASSWORD`、`JWT_SECRET`、
  `TENANT_ENABLE=true`；JWT ≥ 32 字符

退出码：`0` 全过；`1` 有失败项；`2` 参数错误。

## install.sh（S6.1b）

默认 dry-run；必须显式 `--apply` 才创建容器/迁移：

```bash
# 生成随机 env（runtime/ 已 gitignore）并 dry-run
bash scripts/product/install.sh

# 真装：mysql + redis + Flyway + 组织/admin 种子 + backend + frontend
# + health + 经 nginx /api 的登录冒烟
bash scripts/product/install.sh --apply --project aigicrm-b1-test \
  --http-port 18081 --web-port 18082 --mysql-port 13307 --redis-port 16380

# 只装后端 API
bash scripts/product/install.sh --apply --no-frontend ...

# 停止（保留数据卷）
bash scripts/product/install.sh --project aigicrm-b1-test --down
```

实测（2026-11-23，本机 aarch64 Docker）：

- 全新卷：Flyway `163/163`（schema `2026.11.23.5`）、`sys_group=1`、`admin=1`；
- health `UP`、admin 登录成功；
- frontend 首页 HTTP 200（含 `<div id="app">`）、入口 JS 200、经 `/api/auth/login` 代理登录成功；
- 同项目幂等重跑：Flyway `No migration necessary`，health/登录仍通过；
- 失败回滚：`down` 保留数据卷；测试栈已 `down -v` 清理。

架构注意：MySQL 默认 `mysql:8.0`（多架构）；Flyway 在 arm64 自动用 `9.22.3-alpine`。
生产/amd64 可显式设置 `MYSQL_IMAGE=mysql:8.0.28`、`FLYWAY_IMAGE=flyway/flyway:9.22.3`。

> S6.1（空机器到可用实例）已完成；S6.2 只做到“首登强制改密 + 企业/行业/完成”最小向导，
> 业务演示数据导入未做；S6.3 仅 industry 字典可选/新增；S6.4 监控未开始。
> 因此 B1 整体仍未验收。

## 离线回归

```bash
bash scripts/product/test-install.sh
```

覆盖：脚本语法、preflight 合法/非法 env、install dry-run；不启动容器。

## 安全边界

- 不读取 `.env` 的值到输出；只做键存在/长度/布尔判断。
- 不调用 SSH、不部署生产、不迁移任何数据库。
- 不把生成的凭据写入仓库；后续 install 将使用 gitignored runtime 目录。
