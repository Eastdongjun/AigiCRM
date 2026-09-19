# 生产 MySQL 密码最小权限 + 轮换方案（N1-B，2026-08-08）

> 状态：**执行中**。Step 1 已完成，Step 2-4 待单独授权。
> 前置：N1-A（密码移出 git 文件）已完成，commit `084ac65`。

## 执行进度

- ✅ **Step 1 测试环境最小权限账号（2026-08-08 完成）**：测试库建 `aigicrm_app`（SELECT/INSERT/UPDATE/DELETE on aigicrm.*），测试后端容器切 `DB_USER=aigicrm_app`（凭据存 `~/.aigicrm-deploy.env` 的 `TEST_DB_USER/TEST_DB_PASSWORD`）。验证：6 关键接口 200 + 写操作（INSERT 自动提醒 / UPDATE process 提醒）均通过，jar 保持 `c7d2837e`。
- ✅ **Step 2 生产切换（2026-08-08 完成）**：生产库建 `aigicrm_app`（独立强密码，存 `~/.aigicrm-deploy.env` 的 `APP_DB_USER/APP_DB_PASSWORD`）；deploy-backend.sh/rollback.sh 改 `APP_DB_*`（含守卫）；生产容器已切 `DB_USER=aigicrm_app`，4 关键接口 200。commit `7dc3119`，已 push origin/main。**顺带修复**：rollback.sh 缺 JWT_SECRET 的回滚断链 bug。
- ✅ **Step 3 备份 cron 切换（2026-08-08 完成）**：服务器建 `aigicrm_backup`（SELECT/LOCK TABLES/SHOW VIEW/TRIGGER/EVENT on aigicrm.* + 全局 PROCESS），凭据 `/usr/local/bin/.aigicrm-backup.env`(600)；`aigicrm-daily-backup.sh` 4 处改 env 注入（备份 .bak）。手动跑备份 `db=OK login=200 missing=0`。
- ✅ **Step 4 root 轮换（2026-08-08 完成）**：生产 root（`%` + `localhost` 两 host）改新强密码（28 位字母数字）；`~/.aigicrm-deploy.env` 的 `DB_PASSWORD` 更新为新 root。验证三链路：应用 aigicrm_app 登录 200 / Mac 脚本新 root 读 574 / 备份 aigicrm_backup db=OK。旧 `123456` 已拒绝。
- ✅ **测试环境密码分离**：脚本测试模式改用 `TEST_DB_ROOT_PASSWORD`（测试 root 不变）。commit `8731677`。
- ⚠️ **遗留**：① git 历史仍含 `123456` 明文（已轮换失效；`git filter-repo` 清理可选）；② 测试库 root 未轮换（与 ShopJoy 共享 shared-mysql，改密可能影响 ShopJoy，需单独评估）；③ 服务器 `.aigicrm-backup.env` 与 Mac `~/.aigicrm-deploy.env` 是本机凭证，勿入 git。

## 一、目标

1. 后端应用不再用 `root` 连生产库，改用**最小权限账号** `aigicrm_app`（仅 `aigicrm` 库 DML）。
2. 轮换生产 MySQL `root` 密码（当前 `123456`，已泄露至 git 历史），旧值立即失效。
3. 备份/运维链路同步切换，不因轮换中断每日备份。

## 二、现状（已核实）

| 项 | 现状 |
|---|---|
| 生产 MySQL | Docker 容器 `aigicrm-mysql`，root/<redacted> |
| 后端容器 | `DB_USER=root`、`DB_PASSWORD` 经 env（N1-A 后从 `~/.aigicrm-deploy.env` 注入，当前值仍 123456） |
| 部署脚本 | 6 个脚本已改 `-p"$DB_PASSWORD"`（N1-A） |
| 服务器备份 cron | `/usr/local/bin/aigicrm-daily-backup.sh`（mysqldump，用 root/<redacted>） |
| 测试环境 | 同一套 `DB_PASSWORD`（shared-mysql，root/<redacted>） |

## 三、执行步骤（顺序敏感，含回滚）

### Step 1 — 创建最小权限应用账号（测试环境先行）
```sql
-- 测试环境 shared-mysql 先做
CREATE USER 'aigicrm_app'@'%' IDENTIFIED BY '<强密码-app-测试>';
GRANT SELECT, INSERT, UPDATE, DELETE ON aigicrm.* TO 'aigicrm_app'@'%';
FLUSH PRIVILEGES;
```
- **验证**：改测试后端容器 `DB_USER=aigicrm_app` 重启 → 全量 smoke test（smoke-test.sh 21 项）+ 登录/列表/激励/审批/文件 5 主链路。
- 若应用有特殊 SQL（临时表/视图/存储过程）需补授权，以 smoke test 失败项为准。
- 密码要求：**字母数字组合**（避免 `$`/`"`/`'`/空格——deploy-backend.sh/rollback.sh 当前 `-e DB_PASSWORD="$DB_PASSWORD"` 引号语义对特殊字符不安全）。

### Step 2 — 生产切换应用账号（同 Step 1 测试通过后）
1. 生产 MySQL 建 `aigicrm_app`（同 grants）+ 独立强密码（**与测试不同**）。
2. `deploy-backend.sh` 重启生产后端：`DB_USER=aigicrm_app` + 新 `DB_PASSWORD`。
3. 生产健康检查（登录 API 200）+ 关键接口回归。

### Step 3 — 备份 cron 切换
- 服务器 `/usr/local/bin/aigicrm-daily-backup.sh`：新增 `DB_BACKUP_USER=aigicrm_app`（mysqldump 需 SELECT/LOCK TABLES——若授权不足则建独立 `aigicrm_backup` 账号，仅给 aigicrm 库 dump 所需权限）。
- 手动跑一次备份验证产物，确认日报正常。

### Step 4 — 轮换 root 密码（最后一步）
```sql
ALTER USER 'root'@'%' IDENTIFIED BY '<强密码-root-生产>';
```
- 轮换前确认：应用已切 `aigicrm_app`、备份已切 `aigicrm_backup`/新 root 密码、部署脚本 `DB_PASSWORD` 已更新。
- 轮换后立即验证：应用健康、备份 cron 下个周期正常、git 里的旧值 123456 全部失效。

### 回滚
- 任一生产步骤失败：`rollback.sh rollback_backend` 回滚 jar（DB_USER/DB_PASSWORD 仍是 env 注入，改回 root/旧值即可）。
- MySQL 账号切换失败 → 立即改回 `DB_USER=root` + 旧密码重启（root 密码未轮换前仍有效）。
- root 轮换一旦执行不可逆旧值——**必须在应用/备份全部就绪后才做**。

## 四、凭据管理（N1 之后）

- `~/.aigicrm-deploy.env` 新增：`DB_USER`、`PROD_DB_PASSWORD`、`TEST_DB_PASSWORD`（生产/测试分离），删除共享 `DB_PASSWORD` 或保留为测试值。
- 服务器备份脚本的凭据存在服务器 `/usr/local/bin`（不入 git）。
- 禁止在任何 git 跟踪文件写入凭据（scripts 已 fail-closed）。

## 五、Git 历史清理（可选，非紧急）

- 仓库为**私有**，疑似仅本人有权限；root 轮换后 git 历史里的 `123456` 已失效。
- 如需彻底清理：`git filter-repo` 移除含密码的 blob → 强推（force push）所有远端分支。⚠️ 会重写历史，需先确认无协作者。
- 推荐：先轮换密码（止血），filter-repo 作为择期操作。

## 六、决策点（需用户确认）

- [ ] 应用账号名 `aigicrm_app` 是否可接受（或自定义）
- [ ] 是否同时轮换测试库密码（建议是，两环境密码应不同）
- [ ] Git 历史是否执行 filter-repo 清理
- [ ] 排期：是否本轮（今天）执行 Step 1-4，或分两天（先测试环境验证）

## 相关
- N1-A commit: `084ac65`（密码移出 git 文件）
- 审查报告 N1（2026-08-08）
