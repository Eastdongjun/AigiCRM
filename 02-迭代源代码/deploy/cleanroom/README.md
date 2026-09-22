# 本机 Clean Room(近似)

目的:在开发机上以**全新 Docker 栈 + 空 MySQL + Flyway 全量重放**复现"从零部署"路径,验证迁移链、prod 档启动自检、健康检查与最小冒烟。对应《2026-09-22 全景审查总报告》"Clean Room 部署"最低门槛项。

与测试/生产的关系:独立 project(`aigicrm-cleanroom`)、独立端口(33306/38080)、独立卷与网络;不触碰生产(地址见运维手册,经内部渠道获取),不复用本机既有 aigicrm-local-mysql(3306)/aigicrm-local-redis-pub(6380)。

## 文件

| 文件 | 作用 |
| --- | --- |
| `docker-compose.cleanroom.yml` | mysql:8.0.28 + redis:7 + 后端(eclipse-temurin:21-jre 直跑本地构建 jar,prod 档+TENANT_ENABLE=true;**应用不跑迁移,须显式 command 启动 jar**) |
| `gen-cleanroom-env.sh` | 生成一次性 `.env`(root/应用库密码、JWT、admin 口令、FLYWAY_* 全部随机/渲染)与 `build/` 两个 SQL |
| `migrate.sh` | **迁移步骤(生产同构)**: flyway/flyway:9.22.3 容器(与 deploy-db.sh 同版本)直连 cleanroom-mysql 全量 migrate;凭据经 `-e FLYWAY_*` 透传,零明文 |
| `smoke.sh` | health UP → 匿名无细节 → Flyway 库内=磁盘数 → 组织种子 → admin 登录 → auth/info → customer/list(空库 total=0)→ 统计 → 匿名 403 → uploads 目录;全过 exit 0 |
| `.gitignore` | `.env`/`build/` 永不入库 |

## 为什么需要 init 种子+独立迁移(交付缺口,2026-09-22 实测确认)

三个"从零部署"必知事实,此前只存在于散落注释中:

1. **应用运行时不执行迁移**: `application.yml` 默认 `FLYWAY_ENABLED=false`,迁移走 deploy-db.sh(flyway 镜像+管理账号;生产应用账号无 DDL 权限)。顺序错了后端会因租户白名单自检 fail-closed 拒启——这是设计哨兵,不是 bug。
2. **组织维度与 admin 用户无种子**: V1 为纯 DDL,V2026.09.11.6 只种菜单/角色/权限字典。空库必须补 `build/02-seed-org.sql`(集团1+根部门+admin+角色绑定,幂等),否则 admin 无集团归属,租户插件下查询恒假无法登录。
3. **应用账号在 initdb.d 建**(不依赖业务表),组织种子必须等 Flyway 建表后执行(smoke.sh 第 3.5 步自动做)。

## 执行(约 3-5 分钟,首次拉镜像另计)

```bash
cd 02-迭代源代码/aigicrm-backend && MAVEN_SKIP_RC=true mvn -DskipTests package   # 产出 target/aigicrm-backend-1.0.0.jar
cd ../deploy/cleanroom
./gen-cleanroom-env.sh
docker compose -p aigicrm-cleanroom -f docker-compose.cleanroom.yml --env-file .env up -d mysql redis
./migrate.sh                    # 空库全量 Flyway(成功标志: Successfully applied N migrations)
docker compose -p aigicrm-cleanroom -f docker-compose.cleanroom.yml --env-file .env up -d backend
./smoke.sh 2>&1 | tee smoke.log # RESULT: PASS=10 FAIL=0 即过;admin 口令在 .env
```

2026-09-22 实测: Flyway 48/48(v2026.09.22.1)+ smoke 10/10(health UP 无组件细节/登录/空库列表/统计/匿名 403 统一拒绝语义)。

## 销毁

```bash
docker compose -p aigicrm-cleanroom -f docker-compose.cleanroom.yml --env-file .env down -v
```

卷 `cr-mysql-data`/`cr-redis-data`/`cr-uploads` 一并删除,不留状态;重新执行需从 gen 脚本重跑(密码全部轮换)。
