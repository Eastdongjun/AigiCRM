# 实现记录

## 变更文件

| 文件 | 对应 REQ/AC | 行为变化 |
|---|---|---|
| 生产 /usr/local/bin/aigicrm-daily-backup.sh | REQ-002/AC-002 | 备份路径旧卷→新卷 upload-data(/var/lib/docker/volumes/upload-data/_data);新增文件完整性巡检(DB 记录 vs 磁盘,缺失数+清单进日报,缺失>0 判定人工介入) |
| 生产 /root/aigicrm/docker-compose.yml | REQ-001/AC-001 | 顶层 volumes.upload-data 改为 external: true + name: upload-data,与部署脚本卷一致 |
| 生产 /root/aigicrm/nginx-ssl.conf | REQ-005/006/AC-005/006 | www server 加 4 项安全头;静态 location 补相同安全头(避免 add_header 覆盖);favicon.ico/apple-touch-icon 兜底(200/204) |
| 源码 aigicrm-backend/.../FileService.java | REQ-003/AC-003 | 本地存储上传后回读 Files.size 校验,不一致删除已写文件并抛"文件写入校验失败" |
| 源码 aigicrm-backend/src/main/resources/logback-spring.xml | REQ-004/AC-004 | 新增:控制台+滚动文件 /app/logs/app.log(10MB×5 天压缩) |
| 源码 scripts/deploy-backend.sh | REQ-004/AC-004 | docker run 增加 -v aigicrm-logs:/app/logs(已同步服务器) |
| 生产 DB(一次性修正) | REQ-007/AC-007 | biz_incentive_record 57: file_pre_id [159,162]→[162];biz_file 159/160 物理删除+磁盘文件删除 |
| 生产 /tmp | REQ-008/AC-008 | 清理 ty101-test.*、aigicrm-backend-new.jar 等今晚测试残留;7 月底历史文件保留 |
| 本地 04-源码/nginx/nginx-ssl.conf | — | 同步生产新配置 |

## 实现决策

- 与技术计划一致,无偏差
- 备份脚本巡检用 mysql -N --batch + bash 循环(无新依赖)
- 安全头不加 CSP(避免破坏前端),采用四项保守头
- 测试文件删除前备份到 /root/aigicrm/backup-testfiles-20260804/(记录 57 原行 + 2 个文件)

## 新增或更新的测试

- 后端现有测试套件 34 个全过(BUILD SUCCESS);新增校验逻辑无独立单测文件,以生产/测试环境端到端实测覆盖(上传→落盘大小→下载 200)
- 测试环境:新 jar 启动、登录 200、app.log 生成、上传落盘 14 字节一致
- 生产:日志卷挂载、上传落盘 26 字节一致、下载 200

## 实际执行命令

- `bash scripts/deploy-backend.sh`(生产部署,commit=2aad4fc,健康检查通过)
- `bash /usr/local/bin/aigicrm-daily-backup.sh`(手动执行,备份 307M,日报含缺失 7)
- `docker exec aigicrm-frontend nginx -t && nginx -s reload`(热加载)
- `docker compose config`(外部卷解析验证)
- mvn compile / mvn test / mvn package -DskipTests(本地)

## 失败与纠正

- 无实现失败。执行中发现并纠正:备份脚本原指向旧卷(P0,已修);nginx 静态 location 的 add_header 会覆盖 server 级安全头(双处添加)
- gate 检查一次失败:00-intake.md 缺标准标题"## 当前行为"(已修正)

## 未完成与残余风险

- TY101 7 个丢失文件不可恢复,需重新上传;上传后备份巡检缺失数归零
- 文件丢失根因未查明,以"落盘校验+日志持久化+完整性巡检"兜底,复现即可定位
- /tmp 中 7 月底历史文件(aigicrm-*.tar.gz/jar 等)保留未动
- 旧卷 aigicrm_upload-data 保留(45 历史文件),不删除,避免数据混淆
