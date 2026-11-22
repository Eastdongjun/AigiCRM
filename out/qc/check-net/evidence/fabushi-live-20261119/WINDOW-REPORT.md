# W-A 法布施 live 窗口报告（2026-11-19，执行 QC `fabushi-upload-security-debt`）

> 窗口：进场 13:34 UTC 起约 50 分钟，单执行人，测试服 111.229.37.220。
> 环境：分支 jar（be8a0d99，zip 手术构建 md5 `ef5d8a8a…`）+ 新库 `aigicrm_prod_20261118`；基线 A 全程未触碰。
> 还原：基线八项零漂移（jar 2ea180c6/老库/19.2/62 表/user82/health200/Redis 0/0）。

## 一、用例结果（三态）

| # | 用例 | 结果 |
|---|---|---|
| F5 | 迁移实跑+对账（真实生产快照） | **PASS**：19.2→19.3；旧五码 0 行、字典=10、孤儿 0、了凡四训→tuanduibidu；分布 40 本与设计一致 |
| T1 | 发票权限门 | **PASS**：admin 200 `{ticket,expiresIn:300}`。口径修正：普通用户 200 系**设计使然**（spec"人人可提交"→fabushi:create 全员开放）；@PreAuthorize 执行层由单元 PermissionMatrixTest 覆盖，负例需回收授权用户（本数据集不存在） |
| T2.1 | 有效票上传 | **PASS** 200，fileId=2693 |
| T2.2 | 过期票 | **PASS** 401 "上传票无效或已过期" |
| T2.3 | reader 票冒充上传 | **PASS** 403 "上传票 scope 不符" |
| T2.4 | scene 不符 | **PASS** 403 "上传票 scene 不符" |
| T2.5 | pjti 撤销联动 | **PASS** 401 "上传票已随登录撤销"（Redis 黑名单注入模拟登出） |
| T3 | 分型大小闸 5 例 | **PASS** 5/5：11MB 图/21MB 音/101MB 视/伪 .pdf/.svg 全 400 |
| T4 | 发票限速 | **PASS**：60s 窗总额 10 后 429 |
| T5 | 附件限额（=F2 live case4/5） | **PASS** 3/3：第 6 图 400、第 11 段音 400、61s 音 400——**F2 live BLOCKED 项就此销账** |
| T6 | H5 全链 | **PASS**：发票→上传→建帖→挂附件→详情 attach=1、列表 isNew=true |
| T7 | 阅读票 | **PASS**：Bearer 票拉文件 200（size 一致）、过期票 401 |
| T8 | 进度代传 | **PASS**：`PUT /api/fabushi/progress/{bookId}` 200、DB last_page=7、GET 回读一致（计划文档误写 POST /progress，已纠正） |
| T9 | 提醒日界 | **PASS**：mark 前 false→后 true；昨日 23:59:59→false、今日 00:00:00→true（沪时界精确） |
| T10 | 收尾 | **PASS**：测试帖下架 200、perm:ctx*/login_fail* 清零、密件删除、基线 A 还原零漂移 |

**总判定：14/14 PASS，0 FAIL，0 BLOCKED（本窗口范围内）**。剩余 BLOCKED：真机手工清单（录音手感/H5 手机文件选择 UI/PDF 续读 localStorage/提醒弹窗）——需用户真机验收。

## 二、发现（非阻断）

1. **FLYWAY_ENABLED=true 是无效摆设**：Boot4+Flyway12 启动日志零 flyway 行，应用启动从不跑迁移；测试环境迁移**唯一通道**=deploy-db-test.sh（其头注早已写明此不对称）。建议摘除容器 env 防误导（P3，交实施批）。
2. deploy env 的 JWT_SECRET 与测试容器不同源（属生产/本地密钥）——本窗口采用"服务器端就地铸票"（密钥不出服务器），此法已固化在 /tmp/qc-mint-remote.py 模式（已清理）。
3. 构建通道坑位（记录供复用）：maven 拉新插件描述符失败（Java 侧下载故障，curl 通）——**zip 手术**可绕过：基线 fat jar（BOOT-INF/lib 与当前 pom 一致，唯一 pom 变更仅 test 域）+ 最新 target/classes 换 BOOT-INF/classes；验证法=条目计数+陈旧日期清零抽查。
4. 测试脚本三处自坑已修：Linux dd 要 `bs=1M`；不可嵌套 docker exec；progress 端点是 PUT `/progress/{bookId}`。

## 三、证据

- 对账/用例原始输出：本目录 `window-transcript-*.txt`（随本报告归档的关键输出节选见上表）
- 迁移实跑：Flyway 输出 "Successfully applied 1 migration … now at v2026.11.19.3"
- 还原核验：RESTORE_DONE + 八项值与 pin-before 逐项一致
