# 需求规格

## 目标用户与场景

- 运维/管理员:正式客户管理系统(www.aigicrm.com)的文件安全加固
- 触发场景:2026-08-03 晚真实用户 TY101 上传的 7 个激励文件在部署后物理消失,
  且每日备份从未覆盖新卷数据 → 丢失即不可恢复
- 目标:部署/更新不再导致文件丢失;丢失可发现、可恢复、可追溯

## 事实

1. 双卷并存:compose 卷 `ycmn_upload-data`(旧,45 文件/135M)vs 部署脚本卷 `upload-data`
   (新,204 文件/405M);当前容器挂新卷,compose 部署会挂旧卷
2. 备份脚本 tar 指向旧卷 `/data/volumes/ycmn_upload-data/_data`,0802 后新数据从未备份
3. 7 个文件(150/151/153/155/156/157/158)物理缺失,2 个(152/154)被同名覆盖,已不可恢复
4. 后端日志仅 stdout,容器删除即丢
5. nginx 无安全响应头;缺 favicon/apple-touch-icon
6. 记录 57 引用 private 测试文件 159;159/160 为 Codex 测试上传
7. 上传接口无写盘后回读校验

## 假设

- 统一挂载指向即可,不迁移卷数据(新卷已含全部运行数据)
- nginx 先加四项保守头(HSTS/X-Frame-Options/X-Content-Type-Options/Referrer-Policy),
  不加 CSP(避免破坏前端)
- 日志持久化用 logback 文件输出(需重新构建 jar)+ 部署脚本挂载卷
- 备份脚本修正为备份新卷,并增加 DB vs 磁盘完整性巡检

## 需求就绪检查(进入开发前逐项勾选)

- [x] 业务目标明确(防文件丢失,可恢复可追溯)
- [x] 使用对象明确(运维/管理员)
- [x] 当前/目标流程明确(见 intake)
- [x] 功能范围明确(8 项 REQ)
- [x] 页面原型或界面描述完成(非 UI 功能,明确不适用)
- [x] 字段清单完整(无新字段;记录 57 引用修正)
- [x] 权限规则明确(上传校验对所有用户;测试文件删除仅管理员操作)
- [x] 异常场景明确(写盘校验失败→删记录报错;巡检缺失→日报标记)
- [x] 验收标准可测试(AC 可执行)
- [x] 涉及数据/接口/文档影响已识别(记录 57、biz_file、备份日报、compose、nginx)

## 需求确认

- [x] 用户已确认本需求清单:是(2026-08-04,含决策:记录 57 修正引用保留;测试文件 159/160 删除)

## 阻断问题

- 无(用户决策已确认;生产部署前仍按交付流程再次确认)

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 统一上传卷:docker-compose.yml 顶层 volumes.upload-data 改为外部卷(名称 upload-data),任何部署方式挂同一卷 | 双卷并存隐患 | Must |
| REQ-002 | 修正每日备份脚本:tar 指向新卷 /var/lib/docker/volumes/upload-data/_data,并增加 DB vs 磁盘文件完整性巡检(缺失文件数写入日报,日报判定含此项) | 备份指向旧卷 P0 | Must |
| REQ-003 | 上传落盘校验:FileService.upload 本地存储分支写盘后回读 Files.size 与 file.getSize() 一致,不一致则删除已写文件并抛异常(不插库) | 静默失败风险 | Must |
| REQ-004 | 容器日志持久化:新增 logback-spring.xml(控制台+滚动文件 /app/logs/app.log),部署脚本 docker run 增加 -v ycmn-logs:/app/logs;docker rm 后日志保留 | 排查能力缺失 | Must |
| REQ-005 | nginx 安全响应头:www server 增加 add_header(HSTS 31536000/X-Frame-Options DENY/X-Content-Type-Options nosniff/Referrer-Policy strict-origin-when-cross-origin) | 安全头缺失 | Should |
| REQ-006 | nginx 图标:location /favicon.ico、/apple-touch-icon*.png 指向前端 dist 已有图标或返回 204 | 404 噪声 | Should |
| REQ-007 | 激励测试残留清理:记录 57 的 file_pre_id [159,162]→[162];删除 private 测试文件 159/160 及 biz_file 记录(物理文件+记录) | 用户决策 | Must |
| REQ-008 | 服务器 /tmp 测试残留清理(ty101-test.jpg/mp4、ycmn-backend-new.jar 等 Codex 测试产物) | 环境整洁 | Should |

## 业务规则与权限

- 上传落盘校验失败:接口返回业务错误,不产生 biz_file 记录,不占用存储空间
- 记录 57 修正:仅改 file_pre_id 字段(移除 159),remark/score/customer 不变
- 159/160 删除:仅管理员执行;删除物理文件 + 逻辑/物理删除 biz_file 记录
- 巡检:每日 02:30 备份时对比全部 deleted=0 记录与磁盘,缺失数写入日报;缺失>0 时日报判定为"需要人工介入"

## 异常与边界

- 写盘成功但回读大小不一致:删除已写文件,返回"文件上传失败"(不残留)
- 巡检对比时文件临时被占/读写中:按缺失统计(次日重查可确认)
- nginx 头配置后需验证前端页面与 API 不受影响(重点:iframe 无;下载 Content-Disposition 不受影响)
- 备份脚本修正后首次运行:新卷 405M 上传压缩包,NAS 容量充足

## 非功能需求

- 可观测性:日志持久化 + 备份日报含文件完整性 → 丢失事件可发现
- 可恢复性:备份覆盖全部运行数据
- 安全:四项安全响应头
- 兼容性:compose 外部卷配置向后兼容部署脚本

## 验收标准

| 验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given 生产 compose 文件;When 执行 docker compose config;Then backend volumes 解析为外部卷 upload-data(路径 /var/lib/docker/volumes/upload-data/_data) |
| AC-002 | REQ-002 | Given 备份脚本修正后;When 手动执行 ycmn-daily-backup.sh;Then tar 包含新卷 204 文件/405M 数据,日报含"文件缺失: 0" |
| AC-003 | REQ-003 | Given 上传接口;When 上传正常文件;Then 返回 200 且磁盘文件存在且大小一致;Given 模拟写盘后文件被删;When 上传;Then 返回失败且无 biz_file 记录残留 |
| AC-004 | REQ-004 | Given 部署脚本挂载 ycmn-logs 卷;When 后端启动;Then /app/logs/app.log 生成并滚动;容器重建后日志仍在 |
| AC-005 | REQ-005 | Given 生产 nginx 热加载;When curl -I https://www.aigicrm.com/;Then 响应含 Strict-Transport-Security/X-Frame-Options/X-Content-Type-Options/Referrer-Policy,前端页面与 API 正常 |
| AC-006 | REQ-006 | Given nginx 配置;When curl favicon.ico / apple-touch-icon.png;Then 200(或 204),不再 404 |
| AC-007 | REQ-007 | Given 生产库;When 执行清理;Then 记录 57 file_pre_id='[162]';private 文件 159/160 记录与磁盘文件均不存在 |
| AC-008 | REQ-008 | Given 生产服务器;When 清理;Then /tmp 无 ty101-test.* 等测试残留 |

## 追踪关系

- REQ-001→AC-001;REQ-002→AC-002;REQ-003→AC-003;REQ-004→AC-004;
  REQ-005→AC-005;REQ-006→AC-006;REQ-007→AC-007;REQ-008→AC-008
