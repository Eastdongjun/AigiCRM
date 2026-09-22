#!/usr/bin/env bash
# 生成 Clean Room 一次性环境: .env(随机密钥) + build/01-init.sql(应用账号+最小种子)
# 每次运行全部重新随机;产物均已 gitignore,不入库。admin 口令只在 .env(本机文件)。
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build

CR_DB_ROOT_PASSWORD=$(openssl rand -hex 16)
CR_DB_APP_PASSWORD=$(openssl rand -hex 16)
CR_DB_APP_USER=aigicrm_app
CR_JWT_SECRET=$(openssl rand -hex 32)   # 64 hex ≥ 256 位,满足 jjwt
CR_ADMIN_PASS="CR-$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)"

cat > .env <<EOF
# Clean Room 一次性凭据(本机,勿提交)。重新运行本脚本=全部轮换。
CR_DB_ROOT_PASSWORD=$CR_DB_ROOT_PASSWORD
CR_DB_APP_USER=$CR_DB_APP_USER
CR_DB_APP_PASSWORD=$CR_DB_APP_PASSWORD
CR_JWT_SECRET=$CR_JWT_SECRET
CR_ADMIN_USER=admin
CR_ADMIN_PASS=$CR_ADMIN_PASS
# migrate.sh 直接以 -e 透传给 flyway 容器(官方变量名,值同 root;allowPublicKeyRetrieval 同 deploy-db.sh,MySQL8 caching_sha2 必需)
# URL 含 & 必须单引号包裹, 否则被 . ./.env source 时按后台符解析(2026-09-22 实测)
FLYWAY_URL='jdbc:mysql://mysql:3306/aigicrm?characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true'
FLYWAY_USER=root
FLYWAY_PASSWORD=$CR_DB_ROOT_PASSWORD
EOF
chmod 600 .env

# bcrypt hash 用与后端一致的 2a 前缀(同 verify-perm-matrix.sh 做法)
HASH=$(CR_ADMIN_PW="$CR_ADMIN_PASS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["CR_ADMIN_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')

# 01: initdb.d 阶段执行 —— 只做账号(CREATE USER 不依赖业务表;此时 Flyway 尚未建表)
cat > build/01-app-user.sql <<EOF
-- Clean Room 首次建库: 应用最小权限账号(仅此段不依赖业务表)
CREATE USER '${CR_DB_APP_USER}'@'%' IDENTIFIED BY '${CR_DB_APP_PASSWORD}';
GRANT ALL PRIVILEGES ON aigicrm.* TO '${CR_DB_APP_USER}'@'%';
FLUSH PRIVILEGES;
EOF

# 02: 后端 Flyway 建表完成后由 smoke.sh 执行 —— 组织维度+admin 用户
# (迁移链 V1 纯 DDL、V2026.09.11.6 只种菜单/角色字典;空库缺 org/user 种子,详见 README)
cat > build/02-seed-org.sql <<EOF
-- Clean Room 组织/用户种子(Flyway 之后执行;幂等: NOT EXISTS 防重跑)
INSERT INTO sys_group (id, group_name, group_code, description, sort_order, status)
SELECT 1, '默认集团', 'default', 'Clean Room 初始集团', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_group WHERE id=1);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_type, sort_order, status, group_id)
SELECT 1, 0, '总部', 1, 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE id=1);
INSERT INTO sys_user (id, username, password, real_name, org_id, dept_id, status)
SELECT 1, 'admin', '${HASH}', '系统管理员', 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='admin');
INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, r.id FROM sys_role r
WHERE r.role_code='admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=1 AND ur.role_id=r.id)
LIMIT 1;
EOF

echo "OK: .env + build/01-app-user.sql + build/02-seed-org.sql 已生成(凭据随机,admin 口令见 .env,不入库)"
