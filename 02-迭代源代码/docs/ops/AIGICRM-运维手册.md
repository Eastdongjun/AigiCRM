# AIGICRM / AigiCRM 客户管理系统 运维手册

> 版本: v2.7.0（2026-08-07 生产部署）
> 维护人: 运维 / 开发者
> 更新日期: 2026-08-07

---

## 目录

1. [系统概览](#1-系统概览)
2. [技术架构与代码层级](#2-技术架构与代码层级)
3. [环境信息](#3-环境信息)
4. [数据库](#4-数据库)
5. [功能模块说明](#5-功能模块说明)
6. [权限体系](#6-权限体系)
7. [积分与激励计分规则](#7-积分与激励计分规则)
8. [部署指南](#8-部署指南)
9. [备份与恢复](#9-备份与恢复)
10. [版本历史](#10-版本历史)
11. [常见问题排查](#11-常见问题排查)
12. [运维检查清单](#12-运维检查清单)
13. [安全说明](#13-安全说明)

---

## 1. 系统概览

AIGICRM 客户管理系统，商业名 AigiCRM，面向销售团队的市场资源管理：
（2026-10-13 品牌回归：曾于 A4 轮统一为「运筹马年·市场资源管理系统」，已按用户指令撤销）

- **核心业务**：客户管理（保护期/公海/分享）、四项服务（产品试点/两谈两做/交流拜访/外出参观）、文件管理、审批流、提醒、统计分析、积分排行
- **服务形态**：单体 Spring Boot 后端 + Vue3 前端，Docker 部署
- **运行环境**：生产 + 测试双环境

---

## 2. 技术架构与代码层级

### 2.1 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot | Java 21 |
| ORM | MyBatis-Plus | — |
| 数据库 | MySQL | 8.0.28（容器）|
| 缓存 | Redis | 7-alpine（容器）|
| 前端 | Vue 3 + Element Plus | — |
| 构建 | Vite (rolldown) | — |
| 部署 | Docker | compose + 手工 run |

### 2.2 代码目录结构（权威源码）

```
/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/
├── aigicrm-backend/          # 后端 (Spring Boot, 独立 Maven 工程)
│   └── src/main/java/com/aigicrm/
│       ├── module/        # 业务模块
│       │   ├── customer/  # 客户
│       │   ├── incentive/ # 激励 (控制器/服务/实体)
│       │   ├── file/      # 文件
│       │   ├── sea/       # 公海
│       │   ├── approval/  # 审批
│       │   ├── reminder/  # 提醒
│       │   ├── statistics/# 统计
│       │   ├── score/     # 积分
│       │   └── system/    # 系统 (用户/角色/菜单/配置/组织)
│       ├── security/      # JWT/权限
│       ├── common/        # 通用 (数据范围/权限规则/异常)
│       └── config/        # 配置
├── aigicrm-ui/               # 前端 (Vue3)
│   ├── src/
│   │   ├── views/         # 页面 (按模块)
│   │   ├── layout/        # 布局 (侧边栏/顶部)
│   │   ├── store/         # Pinia (user 等)
│   │   ├── api/           # 接口定义
│   │   └── utils/         # 工具
│   └── public/            # 静态 (favicon/logo 等)
├── scripts/               # 部署/检查脚本
├── sql/migration/         # 数据库迁移
├── docs/                  # 文档
└── docker-compose*.yml    # compose 参考
```

### 2.3 后端分层

Controller（接口/权限）→ Service（业务逻辑）→ Mapper（MyBatis-Plus）→ Entity（表映射）

- Controller 用 `@PreAuthorize("hasAuthority('code')")` 或方法内守卫做权限控制
- Service 中 `applyDataScope`/`OrgScopeResolver` 做数据范围过滤
- Entity 用 Lombok `@Data`，驼峰↔下划线自动映射

### 2.4 前端要点

- 路由懒加载（chunk 按页面拆分）
- 动态菜单（从 `/api/auth/info` 加载 sys_menu 渲染侧边栏）
- 权限按钮：`userStore.hasPermission('code')`
- 分页：后端分页（total+records）/ 前端切片（slice）
- ⚠️ Vue3 模板 ref 自动解包：模板里写 `pendingChild`（非 `.value`）

---

## 3. 环境信息

### 3.1 服务器

| 环境 | IP | 系统 | 用途 |
|---|---|---|---|
| **生产** | 39.99.35.218 | CentOS 7 (阿里云 ECS) | 正式系统 |
| **测试** | 111.229.37.220 | Ubuntu 24.04 (腾讯云轻量) | 验证/开发 |

### 3.2 访问方式

- 生产：https://www.aigicrm.com（`aigicrm.com` 301 → www）
- 测试：http://111.229.37.220（admin，口令见服务器 `~/aigicrm/.env` 的 `AIGI_ADMIN_PASS`；2026-09-22 已由弱口令轮换为 20 位强口令，旧值即刻失效）

### 3.3 SSH 连接

```bash
# 生产 (root)
sshpass -p '[见 ~/.aigicrm-deploy.env]' ssh root@39.99.35.218
# 测试 (ubuntu)
sshpass -p '[见 ~/.aigicrm-deploy.env]' ssh ubuntu@111.229.37.220
```

⚠️ **所有凭据在 Mac 的 `~/.aigicrm-deploy.env`**（不进入 git）。部署脚本通过 `deploy-lib.sh` 加载。

### 3.4 容器架构

**生产**（aigicrm_default 网络）：
```
aigicrm-backend  (2.7.0)  8080  ← 后端
aigicrm-frontend (2.6.1)  80/443 nginx ← 前端 (NAS dist 挂载)
aigicrm-mysql    (8.0.28) 3306  ← 数据库
aigicrm-redis    (7-alpine)6379 ← Redis
```

**测试**（shopjoy-net 网络）：
```
aigicrm-backend  ← 后端
aigicrm-frontend ← 前端 (docker cp dist)
shared-mysql  ← 数据库 (与 ShopJoy 共享)
shared-redis  ← Redis (与 ShopJoy 共享)
```

---

## 4. 数据库

### 4.1 连接方式

```bash
# 生产
docker exec aigicrm-mysql mysql -uroot -p'[DB密码]' aigicrm
# 测试
docker exec shared-mysql mysql -uroot -p'[DB密码]' aigicrm
```

⚠️ DB 密码：生产与测试均不落文档——生产在容器 env / Mac `~/.aigicrm-deploy.env`；测试口令同样从 env 读取。

### 4.2 关键表

| 表 | 说明 |
|---|---|
| `sys_user` / `sys_role` / `sys_menu` | 用户/角色/菜单 |
| `sys_user_role` / `sys_role_menu` | 角色分配/菜单权限绑定 |
| `biz_customer` | 客户（保护期/公海/行业）|
| `biz_incentive_category` | 激励分类（四大类+子类，含分值）|
| `biz_incentive_record` | 激励记录（含 specific_product 具体产品字段）|
| `biz_file` | 文件（oss_key 定位磁盘）|
| `biz_sea_pool` | 公海池 |
| `biz_approval` / `biz_approval_node` | 审批流 |
| `biz_reminder` | 到期提醒 |
| `biz_folder_acl` | 文件目录权限（ACL）|
| `biz_incentive_permission` | 激励用户级授权（can_edit/can_delete）|
| `sys_config` | 系统配置（名称/logo/参数）|

### 4.3 数据同步原则

- 数据库同步方向：**生产 → 测试**
- 迁移脚本在 `sql/migration/`（执行时带库名 + `--default-character-set=utf8mb4`）

---

## 5. 功能模块说明

| 模块 | 说明 |
|---|---|
| **工作台** | 统计卡片（客户/保护期/到期/超期）、待处理提醒 |
| **客户管理** | 客户 CRUD、保护期、公海、分享、导出、离职交接 |
| **四项服务** | 上传（产品试点三槽/两谈两做/交流/参观）、查询、统计、排行 |
| **文件管理** | 目录级权限（查看/上传/编辑/删除/分享/下载）、版本替换 |
| **公海** | 捞取（含审批流）、批量导入 |
| **审批** | 捞取/转移/延期/存储空间 |
| **提醒** | 到期提醒、分享提醒 |
| **统计分析** | 年度/月度、行业分布、办事处对比 |
| **积分** | 评分录入、个人/部门排行 |
| **系统管理** | 组织/人员/角色/岗位/集团/权限规则/系统设置/日志 |

### 5.1 激励分类（四大阶段）

| 阶段 | 子分类 | 分值 |
|---|---|---|
| 产品试点 | 涂层/楼梯板/机封/密封/润滑 | 1 分 |
| 两谈两做 | 项目方案/价值服务手册/使用报告 | 2 分 |
| 交流拜访 | 技术交流会/高层拜访 | 5 分 |
| 外出参观 | 南京工厂/终端考察 | 10 分 |

---

## 6. 权限体系

### 6.1 授权模型

**权限矩阵是唯一授权来源**：管理员在 角色管理→权限分配 勾选权限码 → 角色用户即获得该动作；未勾选 → 后端 403 + 前端隐藏按钮。

### 6.2 权限码（module:action）

- 菜单权限码存在 `sys_menu.permission`
- 隐藏载体：98=基础数据-部门 / 99=基础数据-字典 / 100=基础数据-用户

### 6.3 数据范围（六级）

`self`(本人) / `team`(团队) / `dept`(部门) / `office`(办事处) / `region`(大区) / `all`(全部)

- 角色 `sys_role.data_scope` 配置
- 生效即时（后端实时查库，无需重登）

### 6.4 角色现状

| 角色 | data_scope | 说明 |
|---|---|---|
| admin 系统管理员 | all | 全部 |
| 行政 | dept | 部门 |
| 销售总监 | all | 全部 |
| 销售员 | self（生产）/ dept（测试）| 本人/部门 |

⚠️ 测试库与生产库角色配置可能不同步（测试销售员是 dept）。

---

## 7. 积分与激励计分规则

**激励总额 = 该人员所有激励记录分值之和**

| 阶段 | 规则 | 分值 |
|---|---|---|
| 产品试点 | 安装前+中+后 **三槽齐全** → +base；缺任一 → 0 | 1 |
| 两谈两做 | 文件总数 × base，上限 5×base | 2 |
| 交流拜访 | 任一槽有文件 → +base | 5 |
| 外出参观 | 任一槽有文件 → +base | 10 |

- 跟踪记录不加分
- 记录分值上传时锁定；改规则需 `/records/recalculate` 全量重算
- 计分逻辑：`IncentiveService.calcScore`

---

## 8. 部署指南

### 8.1 前置

- Mac 权威源码：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码`
- 凭据：`~/.aigicrm-deploy.env`
- 脚本：`scripts/deploy-lib.sh`（含 ssh/scp/健康检查封装）

### 8.2 后端部署（生产）

```bash
bash scripts/deploy-backend.sh
```

流程：
1. 从测试服提取已验证 jar（`docker cp aigicrm-backend:/app/app.jar`）
2. 上传生产 + 备份旧 jar 到 NAS
3. 重建镜像 `aigicrm-backend:2.7.0`
4. 重启容器（保留 env：DB_HOST=mysql / JWT_SECRET / CORS_ORIGINS 等）
5. 健康检查（登录 API 200）

### 8.3 前端部署（生产）

```bash
bash scripts/deploy-frontend.sh
```

流程：
1. Mac 构建 `npm run build`
2. 上传 → 切换 NAS dist（生产现值 `/Aigicrm/files/ycmn-dist`；改名窗口完成后为 `/Aigicrm/files/aigicrm-dist`，一律以部署 env `NAS_DIST_DIR` 为准）
3. 服务器端注入 prefetch + 写 version.txt
4. 公网验证

### 8.4 测试环境部署

- 后端：Mac 构建 jar → 上传 → `docker cp` 替换 → `docker restart aigicrm-backend`
- 前端：`npm run build` → tar → `docker cp` 完整替换 `aigicrm-frontend:/usr/share/nginx/html/`
  - ⚠️ 不要 `rm -rf dist`；容器内替换用 `docker cp /tmp/new/. aigicrm-frontend:/usr/share/nginx/html/`

### 8.5 数据库迁移

```bash
cat sql/migration/xxx.sql | sshpass ... ssh prod 'docker exec -i aigicrm-mysql mysql -uroot -p[DB密码] --default-character-set=utf8mb4 aigicrm'
```

⚠️ 必须带库名 + utf8mb4；执行前备份。

### 8.6 部署后验证

- 公网首页 200 + 登录 200
- 核心接口 9 项 smoke（客户/激励/公海/审批/提醒/用户/统计/文件）
- 浏览器实测关键页面

---

## 9. 备份与恢复

### 9.1 每日自动备份

**生产（服务器 cron）**：

- cron：`30 2 * * * /usr/local/bin/aigicrm-daily-backup.sh`（生产）
- 内容：DB dump（库名以部署 env `DB_NAME` 为准，当前生产为 `ycmn`）+ **NAS 上传文件目录 `/Aigicrm/files/uploads`**
  （不再使用旧 `upload-data` 卷；旧卷只读保留，见 `docs/生产部署规范.md` §4）
- 位置：NAS `/Aigicrm/files/backup/daily/YYYYMMDD/`
- 保留：14 天自动清理（KEEP_DAYS=14）
- 产物：`<库名>-db-YYYYMMDD.sql` + 上传文件 tar + `report.md`（健康报告）

**Mac 本地每日归档（launchd，fail-closed）**：

- 脚本：`02-迭代源代码/scripts/backup-retention.sh`
- plist 示例：`06-部署与运维/com.donglai.aigicrm.daily-backup.plist.example`（安装/加载校验命令见文件头注释）
- 成功判据：退出码 0 且日志出现 `backup complete: artifacts=N`（N≥1）；缺部署 env/SSH 密钥/
  源目录/备份目录不可写、mirror 拉新失败、产物为空或不可解包时脚本均非零退出，不会打印成功。

### 9.2 手动全量备份

> 以下 1-3 在**生产服务器**执行。上传文件含大小写同名文件，禁止在 Mac（大小写不敏感）解包/打包
> （生产部署规范 §4）。数据库口令从容器环境读取，不写进命令行 argv。

```bash
# 0. 备份目录
mkdir -p /Aigicrm/files/backup/manual
# 1. 数据库（库名以部署 env DB_NAME 为准，当前生产 ycmn）
docker exec aigicrm-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --default-character-set=utf8mb4 ycmn' \
  > /Aigicrm/files/backup/manual/ycmn-full-$(date +%Y%m%d).sql
# 2. 上传文件（NAS 真实路径；不要用 upload-data 卷）
tar -czf /Aigicrm/files/backup/manual/uploads-$(date +%Y%m%d).tar.gz -C /Aigicrm/files/uploads .
# 3. 镜像
docker save aigicrm-backend:2.7.0 aigicrm-frontend:2.6.1 mysql:8.0.28 redis:7-alpine | gzip > /Aigicrm/files/backup/manual/images.tar.gz
# 4. 拉取到本地（在 Mac 执行；目标目录按实际备份盘调整）
rsync -az root@39.99.35.218:/Aigicrm/files/backup/ <本地备份目录>/Aigicrm-backup/
```

### 9.3 恢复

> **上传文件只能恢复到 NAS `/Aigicrm/files/uploads`**。禁止恢复进 `upload-data` /
> `aigicrm_upload-data` 卷；旧卷只读保留、**不挂载**（误挂会让 NAS 文件"消失"）。
> 恢复上传文件前建议先停后端（`docker stop aigicrm-backend`），恢复完成后再启动。

```bash
# 数据库（先备份当前库；口令从容器环境读取）
docker exec aigicrm-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --default-character-set=utf8mb4 ycmn' \
  > /Aigicrm/files/backup/manual/pre-restore-$(date +%Y%m%d-%H%M%S).sql
docker exec -i aigicrm-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 ycmn' \
  < /Aigicrm/files/backup/manual/ycmn-full-YYYYMMDD.sql

# 上传文件：只恢复到 NAS（在服务器执行；不要在 Mac 解包）
tar -xzf /Aigicrm/files/backup/manual/uploads-YYYYMMDD.tar.gz -C /Aigicrm/files/uploads
docker inspect aigicrm-backend --format '{{range .Mounts}}{{.Source}} {{end}}' | grep -q '/Aigicrm/files/uploads' \
  || { echo '❌ 后端未挂载 NAS /Aigicrm/files/uploads，禁止继续'; }

# 镜像
docker load < /Aigicrm/files/backup/manual/images.tar.gz
# 容器按 containers-inspect.json 重建
```

### 9.4 备份巡检（report.md）

每日报告含：DB/上传/首页/登录/MySQL/NAS/客户数 + **文件完整性巡检**（DB记录 vs 磁盘缺失数）。缺失>0 判"需要人工介入"。

---

## 10. 版本历史

| 版本 | 时间 | 说明 |
|---|---|---|
| v2.7.0 | 2026-08-07 | 具体产品字段/权限混合双轨/全站分页/详情页美化/图标LOGO/行业统计修正 |
| v2.6.x | 2026-08 | 权限体系阶段0/六级数据范围/租户隔离/文件ACL |
| v2.0-2.6 | 2026-07 | 客户/激励/文件/公海/审批/积分逐步上线 |

---

## 11. 常见问题排查

### 11.1 生产 403 排查顺序
1. nginx access log
2. 无 token 请求实测（403 空 body=会话/CORS；有中文 message=权限）
3. CORS Origin 差异（www vs 非 www）
4. JWT 密钥实测

### 11.2 菜单没反应/空白
- 浏览器缓存旧版 → 强刷 Cmd+Shift+R / 无痕
- 懒加载 chunk 404 → 版本检测自动刷新

### 11.3 上传/下载卡顿
- 先测后端接口耗时（curl 直连）→ 排除代理（Mac VPN 白名单）

### 11.4 文件下载 404
- 检查 `biz_file.oss_key` 对应磁盘文件是否存在（NAS `/Aigicrm/files/uploads`）
- 检查后端容器挂载是否为 `/Aigicrm/files/uploads`：
  `docker inspect aigicrm-backend --format '{{range .Mounts}}{{.Source}} {{end}}'`
- 旧卷 `upload-data` / `aigicrm_upload-data` 只读保留、**不挂载**；误挂旧卷会造成"文件消失"假象

### 11.5 分页显示错乱
- 跑 `scripts/check-pagination-anti-patterns.sh`
- 全局 `.pagination` 有 sticky 规则 → 新页面用独立类名（rem-pager/list-pager）

---

## 12. 运维检查清单

**每日**：
- [ ] 备份日报 report.md 无异常（缺失=0）
- [ ] 生产首页/登录 200

**每周**：
- [ ] 磁盘/NAS 空间
- [ ] 容器状态（0 重启）
- [ ] 后端日志 ERROR 数

**每次部署**：
- [ ] 迁移前备份
- [ ] 测试环境验证 → 生产
- [ ] 部署后 9 接口 smoke + 浏览器实测
- [ ] `scripts/pre-delivery-check.sh` 全 PASS（含分页门禁）

---

## 13. 安全说明

- **凭据不入库不入 git**：全部在 `~/.aigicrm-deploy.env`（JWT_SECRET / DB / SSH）
- **权限矩阵是安全边界**：前端按钮仅展示层，后端 @PreAuthorize/守卫是真正防线
- **敏感数据脱敏**：排查时勿回显密码/token/手机号
- **已知待决策**：
  - 销售员持 `system:user:view`（可读用户列表 PII）——由矩阵配置决定
  - 行政持 `system:user:add/edit`（可建用户）——业务确认
- **历史垃圾清理**：临时文件/悬空镜像/构建缓存/旧版本镜像 已清理；dist-rollback 保留最新 1 个

---

*本文档基于 2026-08-07 运维实践整理。所有密码/密钥以 `~/.aigicrm-deploy.env` 为准，文中用占位符。*
