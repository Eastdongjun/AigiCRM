# 部署配置矩阵(生产 / 测试 / 本地)

> 部署前必须逐行核对本表。2026-08-02 部署时曾因 OSS 环境变量缺失、CORS 域名未配置导致启动失败与登录 403,已记录在此防止复发。

## 一、环境总览

| 项 | 生产 | 测试 | 本地开发 |
|---|---|---|---|
| 服务器 | 39.99.35.218 (CentOS 7) | 111.229.37.220 (Ubuntu 24.04) | Mac |
| 访问 | https://www.aigicrm.com (SSL) | http://111.229.37.220 | http://localhost:5173 |
| 后端容器 | aigicrm-backend:2.7.0 (独立) | aigicrm-backend (共享 MySQL/Redis) | 源码运行 |
| 前端容器 | aigicrm-frontend:2.6.1 + NAS 挂载 | aigicrm-frontend (docker cp) | vite dev |
| 数据库 | 独立 aigicrm-mysql (55 表) | shared-mysql (与 ShopJoy 共享) | 本地 |
| 存储 | 数据盘 /data + NAS /Aigicrm/files | 系统盘 | 本地 |

## 二、后端环境变量(容器必需)

| 变量 | 生产值 | 测试值 | 缺失后果 |
|---|---|---|---|
| DB_HOST | mysql | shared-mysql | 连不上库 |
| DB_PORT | 3306 | 3306 | — |
| DB_USER / DB_PASSWORD | aigicrm_app / ${APP_DB_PASSWORD}（经 ~/.aigicrm-deploy.env 注入，不落明文；生产 root 已轮换） | root / <redacted>（测试库已接受风险） | — |
| REDIS_HOST / PORT | redis / 6379 | shared-redis / 6379 | — |
| STORAGE_TYPE | local | (无,默认) | local=本地存储 |
| UPLOAD_DIR | /app/uploads | /app/uploads | — |
| **OSS_ENDPOINT** | oss-cn-beijing.aliyuncs.com | 同 | **缺失则启动失败**(新版强制) |
| **OSS_ACCESS_KEY_ID** | your-key(占位) | dummy | **同上** |
| **OSS_ACCESS_KEY_SECRET** | your-secret(占位) | dummy | **同上** |
| OSS_BUCKET_NAME | your-bucket | 占位 | 本地存储下不实际使用 |
| **CORS_ORIGINS** | https://www.aigicrm.com,http://localhost:5173,http://localhost:3000 | (无) | **缺失则登录 403 Invalid CORS request** |
| **JWT_SECRET** | 必配(256位随机,存 ~/.aigicrm-deploy.env) | 必配(测试服务器 .jwt-secret) | **缺失则启动失败**(安全失败,2026-08-02 H9 修复后无默认值) |
| TRUSTED_PROXY_CIDRS | 默认 10/8,172.16/12,192.168/16 | 同 | 登录限流取真实 IP 的代理网段,默认私有网段即可 |
| TZ | Asia/Shanghai | — | 时区 |

> 说明:OSS 为 bean 初始化必需(非空即可),本地存储模式下不真正调用;CORS 白名单按环境配置,HTTPS 下后端同源判断依赖白名单(不依赖 X-Forwarded-Proto)。JWT_SECRET 变更后**全员需重新登录**(旧 token 失效)。

## 三、数据库差异

| 项 | 生产 | 测试 |
|---|---|---|
| 表数 | 50 (v2.7.0 迁移后) | 50 |
| 客户 | 572 (权威) | 577 (含测试残留) |
| 用户 | 63 (28 启用) | 63 (29 启用) |
| 数据方向 | **生产 → 测试**(单向) | — |
| 迁移脚本 | sql/migration/20260802/ | 同脚本演练 |

## 四、前端差异

| 项 | 生产 | 测试 |
|---|---|---|
| dist 位置 | NAS /Aigicrm/files/aigicrm-dist (bind mount) | 容器内 docker cp |
| 部署方式 | 更新 NAS 目录即生效 | 构建后 docker cp |
| nginx | nginx-ssl.conf (443 SSL + 301) | default.conf (80) |

## 五、部署后必检清单

- [ ] 登录 API 带 Origin 返回 200(非 403)
- [ ] 核心 API:auth/info、customer/list、system/users 200
- [ ] 页面:工作台、客户列表、系统设置 Logo 预览正常
- [ ] 磁盘/数据盘/NAS 挂载正常
- [ ] compose 与实际运行一致(镜像 tag/挂载/env),并提交 git

## 六、运维要点(踩坑记录)

- NAS 挂载**必须用 VPC 挂载点地址**(如 0823dohqgjv16v6efx9-gaf47...),**接入点(ap-*)地址会被拒**(权限控制独立)
- 权限组 AIGICRM 规则需含服务器 IP(172.29.188.178/32)
- 数据盘 /dev/vdb → /data;Docker 卷符号链接指向 /data/volumes/
- 每日备份:02:30 crontab → NAS backup/daily/(保留 14 天)
