# B1 交付壳（产品化）

目标：把 AIGICRM 从“能在一台机器上跑”推进到“干净服务器按文档装到能登录”。

## 当前状态

| 切片 | 状态 |
|---|---|
| S6.1a 安装前预检 | **已完成**：`preflight.sh` |
| S6.1b install.sh（DB/迁移/健康/回滚） | 未开始 |
| S6.2 首启向导 | 未开始 |
| S6.3 行业词表配置化 | 未开始 |
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

## 安全边界

- 不读取 `.env` 的值到输出；只做键存在/长度/布尔判断。
- 不调用 SSH、不部署生产、不迁移任何数据库。
- 不把生成的凭据写入仓库；后续 install 将使用 gitignored runtime 目录。
