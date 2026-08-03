# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 生产 `docker compose config` 输出 `upload-data: name: upload-data, external: true`;backend volumes source=upload-data |
| AC-002 | TEST-002 | PASS | 生产手动执行备份脚本:upload-data-20260804.tar.gz 307M(旧版仅 128M),tar 内容 225 项;日报含"缺失文件数: 7"+清单;旧脚本备份 .bak-20260804 |
| AC-003 | TEST-003 | PASS | 测试环境:上传 200,落盘 14 字节=上传大小,下载 200;生产:上传 200,落盘 26 字节=上传大小,下载 200;测试数据已清理 |
| AC-004 | TEST-004 | PASS | 测试环境:/app/logs/app.log 生成(4KB,内容正常);生产:挂载 ycmn-logs→/app/logs,app.log 生成(4KB) |
| AC-005 | TEST-005 | PASS | 生产 curl -I:strict-transport-security/x-frame-options/x-content-type-options/referrer-policy 四项全在;首页+静态 JS+logo API 200 |
| AC-006 | TEST-006 | PASS | 生产 curl:favicon.ico→200、apple-touch-icon.png→204、apple-touch-icon-precomposed.png→204(不再 404) |
| AC-007 | TEST-007 | PASS | 生产 SQL 回读:57 file_pre_id='[162]';biz_file 159/160 计数 0;磁盘 private/69 无 ty101-test.*;备份副本在 /root/ycmn/backup-testfiles-20260804/ |
| AC-008 | TEST-008 | PASS | 生产 /tmp 无 ty101-test.* 等今晚残留;历史 ycmn-* 文件保留(7 月底,非本次范围) |

## 自测场景分类

| 场景 | 说明 | 结果 |
|---|---|---|
| 正常路径 | 上传→落盘→下载 200(测试+生产实测) | PASS |
| 权限 | 无权限变化;159/160 删除仅管理员执行 | PASS |
| 状态 | 备份缺失>0 → 日报判定"需要人工介入" | PASS |
| 重复操作 | 备份脚本重跑幂等(每日目录覆盖) | PASS |
| 参数 | 上传大小校验以 MultipartFile.getSize 为准 | PASS |
| 并发 | 无新并发面 | PASS |
| 异常 | 写盘校验失败→删除已写文件+业务报错(代码路径,测试环境未模拟权限故障) | PASS(代码审查) |
| 历史数据 | 记录 57 修正前已备份原行;旧记录无新字段影响 | PASS |

## 静态与构建验证

- `mvn compile` 成功;`mvn test` 34/34 通过(BUILD SUCCESS);`mvn package -DskipTests` 产出 aigicrm-backend-1.0.0.jar(90M)
- `bash -n ycmn-daily-backup.sh` 语法通过;`nginx -t` 语法通过

## 功能与集成验证

- 生产部署后:健康检查登录 API 200;上传实测 200;下载 200;日志文件生成
- 备份:数据库 dump OK 792K + 上传 307M + 完整性巡检

## UI 验证

不适用(ui_scope=none):无界面变更;nginx 安全头经浏览器请求路径验证不影响页面加载。

## 回归验证

- 非 www→www 301、首页 200、登录 API 200、logo 公开端点 200(全部复测通过)
- 后端 34 个既有测试全绿,无回归

## 未覆盖与风险

- 写盘校验失败分支仅在代码路径审查(生产/测试环境未模拟文件系统故障)
- 文件丢失根因未查明(巡检+日志兜底)
- TY101 7 个文件待用户重传(缺失巡检将自动归零)
