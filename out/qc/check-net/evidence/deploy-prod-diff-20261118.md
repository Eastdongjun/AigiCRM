# 生产 deploy-backend.sh 差异与回滚预案（2026-11-18，DEPLOY-PROD-001）

> 只读拉取：生产 `/data/ycmn/scripts/deploy-backend.sh` 67 行、md5 `2f8707d27f5d80290b24a058eb743300`；仓库版 352 行（HEAD e12c8d43）。差异 395 行。
> 生产脚本更新是运维动作，需用户窗口授权；本文件只给差异与回滚预案，不执行。

## 一、关键差异（生产旧版 → 仓库新版）

| 项 | 生产 67 行旧版 | 仓库 352 行新版 |
|---|---|---|
| fail-closed | `set -euo pipefail` | `set -Eeuo pipefail` + 远端同样 |
| 生产 env 硬门禁 | 无 | `assert_prod_env_pinned`（APP_SLUG/DB_NAME/DEPLOY_DIR/NAS_DIST_DIR 三口径一致才放行） |
| 架构自检 | `check_deploy_arch` | 保留 + fail-closed |
| TLS 预检 | 无 | `check_prod_tls`（证书链/到期，去 `curl -k`） |
| 凭据校验 | JWT/DB 账号 | + OSS 短信凭据（占位值拒绝） |
| jar 上传 | 直接覆盖 | `.upload-<TS>` 临时文件 + 大小/md5 校验 + 原子 mv |
| 旧 jar 备份 | 无/静默 | 备份失败不吞；备份路径记录 |
| 镜像 tag | 硬编码/分散 | `BACKEND_TAG` 单一来源（deploy-lib） |
| 快滚 | 无 | 镜像重建失败时 `docker cp jar + restart`（免重建快滚） |
| RESULT 标记 | 无 | `RESULT=PASS/FAIL rc=...`（脚本退出码语义） |
| 启动等待 | 无限 until | 有限次数 + 失败回滚 |
| 自动回滚 | 无 | 构建/启动/健康失败自动用备份 jar 重建重启 |

## 二、风险评估

- 生产脚本是**部署工具**，更新本身不改变运行中的服务；风险在后续首次使用新脚本部署时暴露。
- 新脚本的 `assert_prod_env_pinned` 依赖部署 env：当前实际 env 已钉 `APP_SLUG=ycmn/DB_NAME=ycmn/DEPLOY_DIR=/root/ycmn/NAS_DIST_DIR=/Aigicrm/files/ycmn-dist`（已核），应能通过。
- 新脚本的 `check_prod_tls` 需部署机系统 CA 能校验生产证书；若不通过会 fail-closed（这是设计）。
- 旧脚本没有任何备份/快滚/RESULT，属于运维风险，不是业务功能缺陷。

## 三、回滚预案

1. **备份**：生产窗口内
   `cp -p /data/ycmn/scripts/deploy-backend.sh /data/ycmn/scripts/deploy-backend.sh.bak-20261118`
   并记 md5（`2f8707d2…`）。
2. **预检**：上传新脚本到 `/data/ycmn/scripts/deploy-backend.sh.new`；`bash -n`；`diff` 审阅；确认 `assert_prod_env_pinned` 能读生产 env。
3. **切换**：`mv deploy-backend.sh.new deploy-backend.sh`（原子）；保留备份。
4. **验证**：不立即部署；下次部署窗口首次使用新脚本时观察 `RESULT=` 与健康检查；若失败，脚本自带自动回滚（备份 jar）。
5. **回滚**：`mv deploy-backend.sh.bak-20261118 deploy-backend.sh`；恢复旧版；无需重启服务（脚本不常驻）。
6. **证据**：生产脚本 md5 before/after；`bash -n` 输出；diff 审阅；窗口日志。

## 四、需要用户/协调方

- 生产窗口授权（脚本更新属生产写）。
- 确认新脚本的 `BACKEND_TAG` 与当前生产镜像 tag 一致（避免下次部署重建错 tag）。
- 更新后首次部署仍按标准窗口：db→backend→frontend→冒烟→公网实证。

## 五、证据

- 生产脚本：`/tmp/deploy-backend-prod.sh`（67 行，md5 2f8707d2…）
- 差异：`/tmp/deploy-backend.diff`（395 行）
- 仓库版：`02-迭代源代码/scripts/deploy-backend.sh`（352 行）
