---
name: run-aigicrm
description: Run, start, build, screenshot, or drive the AigiCRM/AIGICRM customer management system locally. Full-stack launch on Homebrew MySQL/Redis (no Docker) — Spring Boot backend :8080 + Vue3 frontend dev server :3000, driven via the Playwright driver at .claude/skills/run-aigicrm/driver.py (login flow + dashboard/customer screenshots). Verbs: run, start, build, launch, screenshot, drive, login, test.
---

# Run: AigiCRM 客户管理系统（本地全栈，无 Docker）

Spring Boot 后端 (`aigicrm-backend/`, :8080) + Vue3 前端 (`aigicrm-ui/`, vite :3000, `/api` 代理到 :8080)。
本机用 Homebrew MySQL/Redis 起全栈，浏览器交互由 Playwright 驱动脚本完成。

**路径约定：本文所有相对路径相对仓库根 `<unit>/` = `/Users/donglai/东来/01-Projects/01-aigicrm/04-源码`。**

## 先决条件（Homebrew，已装）

```bash
# 版本要求(关键): Java 必须 17, 不是默认 shell 的 25
/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java -version
mysql --version          # 8.x
redis-server --version
node -v && mvn -version
# Playwright(python) 用 /usr/local/bin/python3(该解释器装了 playwright, 系统 `python3` 没有)
/usr/local/bin/python3 -c "import playwright; print('ok')"
# 系统 Google Chrome(playwright channel='chrome' 使用)
# 测试服凭据(DB 恢复用): ~/.aigicrm-deploy.env(TEST_HOST/TEST_USER/TEST_PASS/TEST_DB_ADMIN_USER/TEST_DB_ADMIN_PASSWORD)
```

## 构建

```bash
# 后端(Java 17)
cd aigicrm-backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
MAVEN_SKIP_RC=true mvn -q package -DskipTests     # 产物 target/aigicrm-backend-1.0.0.jar (~91MB)
# 前端(依赖已装则跳过)
cd ../aigicrm-ui && npm install
```

## Run（agent 主路径）

完整启动 = 4 个常驻进程。全部在 `/tmp` 留日志，可随时重启。建议一个 shell 脚本按序执行。

### 1. MySQL（独立实例 :3308，专用 datadir，不碰本机现有 MySQL）

```bash
mkdir -p /tmp/aigicrm-mysql-data
mysqld --initialize-insecure --datadir=/tmp/aigicrm-mysql-data   # 首次: root 空密码
mysqld --datadir=/tmp/aigicrm-mysql-data --port=3308 --socket=/tmp/aigicrm-mysql.sock \
  --pid-file=/tmp/aigicrm-mysql.pid --log-error=/tmp/aigicrm-mysql.log \
  --bind-address=127.0.0.1 --daemonize
# 设 root 密码(匹配后端默认)+ 建库
mysql -h127.0.0.1 -P3308 -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '123456'; CREATE DATABASE IF NOT EXISTS aigicrm DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
```

### 2. Redis（独立 :6380，干净配置——**必须 `/dev/null` 作 config，否则继承全局 requirepass**）

```bash
redis-server /dev/null --port 6380 --bind 127.0.0.1 --save "" --appendonly no --daemonize yes
redis-cli -p 6380 ping   # PONG
```

### 3. 数据库恢复（从测试服 dump 当前库——仓库里 V1 baseline 是纯 schema、init.sql 是旧 25 表）

```bash
cd ~ && source ~/.aigicrm-deploy.env
sshpass -p "$TEST_PASS" ssh -o StrictHostKeyChecking=no "$TEST_USER@$TEST_HOST" \
  "docker exec shared-mysql mysqldump -u'$TEST_DB_ADMIN_USER' -p'$TEST_DB_ADMIN_PASSWORD' \
  --single-transaction --default-character-set=utf8mb4 --skip-lock-tables aigicrm" > /tmp/aigicrm-test-dump.sql
mysql -h127.0.0.1 -P3308 -uroot -p123456 aigicrm < /tmp/aigicrm-test-dump.sql   # 62 表 + admin 种子
```

### 4. 后端（Java 17；JWT_SECRET 必注入否则启动失败；OSS 需占位 key）

```bash
cd <unit>   # 仓库根, 让 LOGGING_CONFIG 相对路径成立
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
mkdir -p /tmp/aigicrm-logs /tmp/aigicrm-uploads
DB_HOST=127.0.0.1 DB_PORT=3308 DB_USER=root DB_PASSWORD=123456 \
REDIS_HOST=127.0.0.1 REDIS_PORT=6380 \
JWT_SECRET=$(openssl rand -hex 32) CORS_ORIGINS=http://localhost:3000 \
STORAGE_TYPE=local UPLOAD_DIR=/tmp/aigicrm-uploads TENANT_ENABLE=false \
OSS_ACCESS_KEY_ID=local-oss-key OSS_ACCESS_KEY_SECRET=local-oss-secret \
LOGGING_CONFIG="$(pwd)/.claude/skills/run-aigicrm/logback-local.xml" \
nohup "$JAVA_HOME/bin/java" -jar aigicrm-backend/target/aigicrm-backend-1.0.0.jar > /tmp/aigicrm-backend.log 2>&1 &
# 注: LOGGING_CONFIG 必须绝对路径(pwd 展开)——相对路径 Spring 按 classpath 根解析, 解析不到会回退 logback 默认配置。
# 等日志出现 "Started AigicrmApplication" (约 6s)
```

### 5. 前端

```bash
cd aigicrm-ui && nohup npm run dev > /tmp/aigicrm-ui.log 2>&1 &   # VITE ready on :3000
```

### 6. 驱动（登录 + 截图）——driver 即主要交互路径

```bash
cd <unit>
/usr/local/bin/python3 .claude/skills/run-aigicrm/driver.py                     # 登录 → 工作台截图 /tmp/aigicrm-dashboard.png
/usr/local/bin/python3 .claude/skills/run-aigicrm/driver.py --flow customer     # 登录 → 客户列表截图
/usr/local/bin/python3 .claude/skills/run-aigicrm/driver.py --headed            # 本机有头模式
/usr/local/bin/python3 .claude/skills/run-aigicrm/driver.py --user u --password p --out /tmp/x.png
```

driver 登录 admin/admin123(测试服库的 admin 密码)，等 dashboard 元素"客户总数"出现即成功。
已登录会话可 `--screenshot-only` 直接开目标页截图。截图落 `/tmp/*.png`，非空白校验用：
`python3 -c "from PIL import Image;from statistics import pstdev;... uniq colors / luminance std"`。

## Run（human 路径）

前端 dev server 起来后浏览器开 `http://localhost:3000`，admin/admin123 登录。无头下 driver 是唯一交互方式。

## 停服

```bash
# 后端/前端进程
pkill -f aigicrm-backend-1.0.0.jar; pkill -f "vite"
# MySQL 独立实例
mysqladmin -h127.0.0.1 -P3308 -uroot -p123456 shutdown
# Redis 6380
redis-cli -p 6380 shutdown nosave
```

## Gotchas（本会话踩过的坑）

- **Java 必须是 17**：默认 shell `java` 是 JDK25，后端构建/运行会失败(Mockito/字节码不兼容)。用 `/opt/homebrew/opt/openjdk@17/...` 的 JAVA_HOME。
- **logback 硬编码 `/app/logs`**：`logback-spring.xml` 写死容器路径，本机无 `/app` 直接启动失败。必须 `LOGGING_CONFIG=.../logback-local.xml` 覆盖(已随 skill 提供)。
- **`-D` 参数放 `-jar` 后面是程序参数不是 JVM 属性**：`java -jar app.jar -Dx=y` 无效。统一用环境变量(`DB_HOST=... java -jar`)，Spring `${DB_HOST}` 读 env。
- **OSS client 无条件初始化**：即使 `STORAGE_TYPE=local`，`OssConfig` 仍建 bean，空 key 报 "Access key id should not be null"。注入占位 `OSS_ACCESS_KEY_ID/SECRET`(非真实密钥)。
- **JWT_SECRET 无默认回退**：不注入启动失败(安全设计)。`openssl rand -hex 32` 生成。
- **本机共享 MySQL root 有未知密码**：别动它，起独立实例(见 Run 1)。Homebrew MySQL 默认 3306，后端默认连 3308——正好用独立实例。
- **Redis 全局 config 有 requirepass**：`redis-server --port 6380` 不带 config 会继承，必须 `/dev/null` 作 config 清掉。
- **前端 `wait_for_url` 对 SPA 路由跳转不可靠**：登录后等元素(driver 已处理为等"客户总数")。
- **数据库种子**：`sql/init.sql` 是旧 25 表(缺 ~30 张表)，不能用于当前 schema；V1 baseline 是纯 schema 无数据。本地库从测试服 dump(见 Run 3)。
- 前端有 2 条无害 Vue warning(`systemSubtitle` not defined)，不影响运行。

## Troubleshooting

| 症状 | 修复 |
|---|---|
| 启动日志 `Logback configuration error ... /app/logs/app.log (No such file or directory)` | 注入 `LOGGING_CONFIG="$(pwd)/.claude/skills/run-aigicrm/logback-local.xml"`(**绝对路径**, 相对路径 Spring 按 classpath 解析不到) |
| 重启报 `Port 8080 was already in use` | 前进程未完全退出: `lsof -ti :8080 \| xargs kill -9`, 确认端口空后再启 |
| 启动失败 `Access key id should not be null or empty` | 注入 `OSS_ACCESS_KEY_ID=local-oss-key OSS_ACCESS_KEY_SECRET=local-oss-secret` |
| 启动失败 `JWT_SECRET` 相关/WeakKey | 注入 ≥32 字符 `JWT_SECRET=$(openssl rand -hex 32)` |
| 登录 401 账号或密码错误 | 测试服库 admin 密码是 `admin123`(不是 123456)；`--user/--password` 换账号 |
| driver 超时等不到"客户总数" | 后端没起/起挂：看 `/tmp/aigicrm-backend.log` 是否 `Started AigicrmApplication`；前端 3000 是否 VITE ready |
| MySQL 连接拒绝 | 确认 3308 独立实例在(日志 `/tmp/aigicrm-mysql.log`)，`mysql -h127.0.0.1 -P3308 -uroot -p123456` 能进 |
| driver `ModuleNotFoundError: playwright` | 用 `/usr/local/bin/python3`(该解释器装了 playwright)，不是系统 `python3` |
