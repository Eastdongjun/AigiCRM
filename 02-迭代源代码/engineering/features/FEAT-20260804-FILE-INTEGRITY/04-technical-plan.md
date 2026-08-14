# 技术实施计划

## 已确认影响范围

### 影响范围清单

| 层 | 涉及项 | 核对结果 |
|---|---|---|
| 数据库 | biz_incentive_record(57 修正)、biz_file(159/160 删除) | 一次性数据修正,无迁移脚本 |
| 后端 | FileService.upload(写盘回读校验);新增 logback-spring.xml | ycmn-backend 源码 |
| 部署 | compose 外部卷、deploy-backend.sh 挂载日志卷 | 服务器 /root/ycmn + 本地 04-源码/scripts |
| 运维 | ycmn-daily-backup.sh(新卷+巡检) | 服务器 /usr/local/bin |
| 网关 | nginx-ssl.conf(安全头+图标) | 服务器 /root/ycmn/nginx-ssl.conf |
| 环境 | /tmp 测试残留 | 服务器 |

### 隐藏入口检查

- [x] 定时任务:每日 02:30 备份脚本(REQ-002 直接修改)
- [x] 后台管理员操作:159/160 删除仅管理员执行
- [x] 历史数据兼容:记录 57 修正一次性执行
- [x] 部署自动流程:compose/脚本统一卷(REQ-001/004)

## 架构与关键决策

1. **卷统一方式**:compose 顶层 volumes 改为外部卷(`external: true, name: upload-data`)。
   理由:新卷 upload-data 已含全部运行数据;旧卷 ycmn_upload-data 保留不动(含历史 45 文件,不删除)。
   备选(脚本改用 ycmn_upload-data)拒绝:旧卷数据不完整。
2. **日志持久化方式**:logback-spring.xml 滚动文件输出 `/app/logs/app.log`(10MB×5) + 部署脚本
   `-v ycmn-logs:/app/logs`。备选(docker json-file + 部署前 docker logs 导出)拒绝:运行期不可查,仍依赖部署动作。
3. **上传校验实现**:本地存储分支 `Files.copy` 后 `Files.size(filePath) != file.getSize()` 则
   `Files.deleteIfExists(filePath)` 并抛 BusinessException(不插库、不占用存储)。
   OSS 分支保持(生产 STORAGE_TYPE=local,OSS 分支不触发)。
4. **备份巡检实现**:备份脚本内嵌 bash 循环(`docker exec mysql -N --batch` 导出 id/oss_key,
   `[ -f ]` 逐个对比),缺失清单与计数写入日报;缺失>0 → 判定"需要人工介入"。
5. **安全头**:四项保守头(HSTS 31536000 含子域 / X-Frame-Options DENY /
   X-Content-Type-Options nosniff / Referrer-Policy strict-origin-when-cross-origin)。
   不加 CSP(避免 inline/动态加载被阻断破坏前端)。nginx 热加载验证。

## 数据、API 与事件契约

- `biz_incentive_record.file_pre_id`: `[159,162]` → `[162]`(UPDATE,仅 id=57,一次性)
- `biz_file`: 159/160 物理删除记录 + 删除磁盘 `private/69/ty101-test.{jpg,mp4}`
- 上传接口:成功返回不变;校验失败返回 `{"code":500,"message":"文件上传失败: <原因>"}` 且无记录
- 备份日报新增行:`- 文件完整性: 缺失 0/140`;判定条件增加"缺失=0"
- 日志文件:`/app/logs/app.log` + `app.%d{yyyyMMdd}.%i.log.gz`(滚动)

## 权限与安全边界

- 上传校验对所有用户生效,无权限变化
- 159/160 删除:仅管理员执行(SQL+文件删除),脚本记录到本功能包 evidence
- nginx 安全头对全部响应生效;下载的 Content-Disposition 不受影响

## 实施顺序

1. **P0 备份脚本修正 + 巡检**(服务器,立即):备份新卷;手动执行验证日报含"缺失 0";验证备份含新卷数据
2. **compose 外部卷**(服务器):备份原文件 → 修改 → `docker compose config` 验证解析
3. **nginx 安全头 + 图标**(服务器):备份 nginx-ssl.conf → 修改 → `nginx -t` → 热加载 → curl 验证头与页面
4. **记录 57 修正 + 159/160 删除**(服务器 DB):SQL 执行 → 回读验证
5. **/tmp 清理**(服务器)
6. **后端代码**(本地):FileService.upload 校验 + logback-spring.xml → `mvn compile/test`
7. **后端部署**:构建 jar → 测试环境验证 → 生产部署(用户确认后,deploy-backend.sh)
8. **deploy-backend.sh 日志卷挂载**(本地权威 + 服务器同步)
9. **回归实测**:生产上传→落盘→下载 200;日报重跑

## 测试策略

- AC-001: `docker compose config` 输出 volumes 解析为外部卷
- AC-002: 手动执行备份脚本,检查 tar 内容与新卷一致 + 日报"缺失: 0"
- AC-003: 正常上传 200+落盘;模拟写盘异常(权限/路径)上传失败无记录(测试环境验证)
- AC-004: 部署后 /app/logs/app.log 存在;docker rm 后日志仍在(测试环境验证)
- AC-005: curl -I 响应头 4 项;页面 200、登录 API 200、下载 200
- AC-006: curl favicon.ico/apple-touch-icon.png 非 404
- AC-007: SELECT 回读 57=[162];159/160 记录与磁盘文件均不存在
- AC-008: /tmp 无测试残留
- 后端构建:mvn compile + 现有测试套件(gates 自动发现)

## 迁移、发布与兼容

- 无 schema 变更;compose 变更不重建现有容器(脚本管理运行),仅将来 compose 部署生效
- 后端 jar 构建后:测试环境部署验证 → 生产部署(deploy-backend.sh 规范流程,部署前用户确认)
- nginx/备份/compose/DB 修正均先备份原文件/SQL

## 可观测性

- 日志:app.log 持久化,部署/异常可追溯
- 日报:文件完整性缺失计数,缺失即人工介入
- 本功能包 evidence 记录所有变更前后状态

## 回滚

- 备份脚本:保留旧版副本 ycmn-daily-backup.sh.bak-YYYYMMDD
- compose:保留 docker-compose.yml.bak-YYYYMMDD
- nginx:保留 nginx-ssl.conf.bak-YYYYMMDD;`nginx -s reload` 回滚后再次 reload
- DB:记录 57 修正前导出该行 SQL;159/160 删除前备份文件到 /root/ycmn/backup-testfiles-20260804/
- 后端:回滚旧 jar(部署脚本 rollback.sh / 备份 jar 路径已有)

## 风险与盲区

- 文件丢失根因未明:以巡检+日志兜底,复现可定位
- nginx 安全头:热加载后需完整验证页面/API/下载
- 备份首跑 405M 上传 NAS:容量充足(10P),耗时增加可接受
- 新卷 204 文件与旧卷 45 文件并存:旧卷不动,避免混淆数据
