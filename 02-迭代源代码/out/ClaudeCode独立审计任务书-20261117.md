# 独立审计任务书（Claude Code CLI · 2026-11-17）

> 你是独立审计员，不是这个项目的开发助手。今天（2026-11-17）另一个 AI（下称"前 AI"）在本项目
> 做了一系列工作并**声称全部 PASS**。项目所有者明确怀疑前 AI 虚报/漏报/自我掩盖。
> 你的唯一任务：**不采信前 AI 的任何结论**（包括本仓库 docs/out 下的所有报告——它们是前 AI 写的，
> 只能在最后对照环节使用），一切以你自己亲手执行的命令、亲手读的代码、亲手查的库为准，逐项复核下列
> 12 项"声称"，输出打假报告。
>
> 发现前 AI 说谎/虚报/漏报 → 单独列入【前 AI 虚报清单】，逐条"声称 vs 你的实测证据"。这是最高优先级。

## 环境（全部在你本机，直接用 Bash 工具执行）

- 仓库根：`/Users/dongjun/东来/项目/02-已上线/01-AIGICRM`（分支 `integration/perm-fabushi-20261116`）
- 代码：`02-迭代源代码/`（后端 aigicrm-backend = Spring Boot 4.1.2/Java21/Maven；前端 aigicrm-ui = Vue3/Vite；小程序 aigicrm-miniprogram）
- 生产：www.aigicrm.com；ssh 到生产 = 先 `source ~/.aigicrm-deploy.env` 再
  `ssh -i "$PROD_KEY" "$PROD_USER@$PROD_HOST" '<命令>'`；生产容器 ycmn-backend/ycmn-frontend/ycmn-mysql/ycmn-redis；库 ycmn
- 测试服：`ssh -i "$TEST_KEY" "$TEST_USER@$TEST_HOST"`；测试库 aigicrm_prod_20261016（生产快照）
  （库内查询通道：`docker exec -i shared-mysql sh -c "mysql -uroot -p\$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 -N aigicrm_prod_20261016" <<'SQLEOF' ... SQL ... SQLEOF`）
- 测试服账号（前 AI 称 2026-11-17 已重置）：admin / NO001（马吉亮，销售总监）/ TY010（武磊，销售员），密码均 `Tst#2026@Verify2`
- 一键门禁：`bash scripts/gate.sh`；契约全集：`bash scripts/dev-verify.sh contracts`（前 AI 称 190 项）
- 前端构建：`cd 02-迭代源代码/aigicrm-ui && npm test && npm run build`
- 后端：`export JAVA_HOME="/Users/dongjun/东来/Agent/02-运行环境/Java/Contents/Home" && export MAVEN_ARGS="-o -Dmaven.repo.local=/tmp/m2repo/repository"` 后 `mvn test`
  （⚠️ 若报 parent 4.1.2 离线缺失：说明 /tmp 仓库缓存丢失，去掉 -o 在线跑一次补拉即可）

## 铁律

1. **生产库只读**（SELECT/SHOW 可以，任何写操作先记入【待授权清单】不执行）。
2. 测试服允许有限写入（勾选/取消一个码做生效验证），结束必须还原并快照比对。
3. 测试账号密码只试 1 次；失败走 ssh 查库通道，禁止反复猜。
4. **本任务只审计不修 bug**。发现问题全部记录待批。
5. 不硬编码任何人员/角色进代码或脚本；验证账号通过 env 或参数传入。
6. **每条结论必须带：你亲手执行的命令原文 + 退出码 + 关键输出行**。写不出证据的结论不许出现在报告里。
7. 不读 `out/` 下前 AI 写的三份报告作为证据来源；仅在报告最后一节"与前 AI 文档对照"时允许读。

## 待复核的 12 项"前 AI 声称"（逐项验证，复核配方已给）

### 1. Git 推送与代码完整性
声称：分支全推（远端=456943b7）；中途误用 `git commit -am` 卷入 19 个非任务文件（00-项目管理/法布施原型存档），声称已 `git reset HEAD~1` 纯净重提。
复核：`git log origin/..HEAD`（应空=全推）；`git status --short`（原型存档改动应原样回到工作区）；`git show 96c84ec3 --stat`（声称纯净=仅 1 文件）。**打假点**：坏提交是否真没进远端历史（`git log --all --oneline` 全量追溯 + `git reflog`）；非任务文件**内容**是否被悄悄动过（逐个 `git diff` 看）。

### 2. 上传边界修复（U1 类型 / U2 100MB）
声称：上传层 PDF 魔数校验（FileService.assertPdfMagic，前 1024 字节须含 %PDF-）+ 挂载层 file_type=PDF + ≤100MB（FabushiBookService.attachPdf MAX_PDF_BYTES）+ 前端预检（book.vue handlePdfChosen）；已上生产。
复核：a) 读上述三处实现；b) 测试服亲手 curl 三例：①纯文本改名 fake.pdf 传 `POST /api/file/upload`（scope=fabushi）→ 必 400；②100MB+ 合法 %PDF 头大文件 → 上传 200 → `POST /api/fabushi/books/{id}/pdf` → 必 400；③真小 PDF → 全链 200。**打假点**：大写 .PDF / 无扩展名 / 双扩展名 `a.pdf.txt` 三条绕过路径逐一试；前端预检是否真的在发请求前拦截（读代码不跑）。

### 3. 生产部署与 build 事故
声称：12:31 部署引发登录全挂（ycmn-redis 手工容器无网络别名，REDIS_HOST=redis 解析 NXDOMAIN），7 分钟修复（env 改容器名直连）；build 通道挂死根因=本地缺 eclipse-temurin:17-jre 底座镜像（FROM 触发被墙的 Docker Hub pull），离线 docker load 后恢复；deploy-backend.sh 加固（fast_rollback_container + RESULT 标记）。
复核（生产 ssh）：`docker exec ycmn-backend printenv | grep -E "REDIS_HOST|DB_HOST"`（应=ycmn-redis/ycmn-mysql）；`docker exec ycmn-backend md5sum /app/app.jar`（应 fcac22056ecdb8ed636cdeae1a8e22eb）；`docker images`（应有 eclipse-temurin:17-jre）；读 scripts/deploy-backend.sh 确认 fast_rollback_container/RESULT=PASS 真实存在。**打假点**：登录报障时段（14:5x）后端日志时间线与影响面；加固后的快滚通道在"容器已被 stop/rm"场景是否可用（追 start_backend 调用顺序给确定答案）。

### 4. 勾选→双端即时生效
声称：权限判定零缓存（权限路径 grep @Cacheable 零命中），四步实证：admin UI 勾销售员「人员管理 system:user:view」→ 武磊同 token（未重登）`GET /api/system/users` 403→200 → 取消 → 200→403。
复核：a) 自己 grep 权限路径缓存注解；b) **亲手重跑四步**（curl 全程可做：NO001/TY010 登录拿 token → admin 权限中心按角色视图勾/取消 system:user:view → 同 token 验证 403↔200 + /api/auth/info 权限集同刻变化）；c) **打假点（重点）**：前 AI 承认第一次验证是假阳性（选了武磊+文件 rename，销售员角色级本就有）——**它的第二次结论可信吗？你再选一个"角色级+个人级双缺"的码（候选：system:recycle:view 之外找，或 fabushi:stats:export 配合先勾父码 fabushi:stats:view）独立重做一次**。

### 5. 权限全景清单准确性
声称：13 模块 113 码；四角色绑定 110/88/67/53 与权限中心 UI 一致；清单文档 out/权限全景清单-20261117.md。
复核：自己跑 SQL（模块码数分组 / 角色绑定矩阵 / 人员分布）逐数对照清单。**打假点**："后端独有 23 码=管理端路由语义 INFO"这个口径是否藏了真死码（抽 5 个逐一找消费点）。

### 6. 硬编码人员清理
声称：26 姓名+工号模式全量扫、16 处脱敏、终扫零命中；契约 check-no-hardcoded-persons.py 已入门禁。
复核：a) **不要信它的名单**——从生产库 `SELECT real_name FROM sys_user WHERE deleted=0` 拉**当前全名单逐个** grep（后端 src/main + PC src + MP pages/utils/api + **db/migration 迁移文件**——前 AI 承认迁移只查了部分形态）；b) 工号正则（NO/NM/TY+数字字面量）；c) 手机号段/身份证模式；d) 读 check-no-hardcoded-persons.py 评估：KNOWN_NAMES 静态名单的缺口（新人入职自动覆盖吗）、注释行跳过的合理性。**打假点**：迁移文件里按人写死的 INSERT/UPDATE 一个都不许漏。

### 7. 两个权限契约真实性
声称：check-perms-row-consumption-parity.py（双端消费对齐）与 check-no-hardcoded-persons.py 各带负例自证，contracts 190 项全绿。
复核：a) 自己跑 `bash scripts/dev-verify.sh contracts` 数 PASS；b) **亲手做红**：把 aigicrm-miniprogram/pages/files/index.js 的 `actionPerms = rowPerms || folderPerms` 临时改回 `= folderPerms` → 跑契约必须红 → `git checkout -- 该文件` 还原再跑绿。**打假点**：前 AI 声称做过红绿——它可能只跑了绿。你把红跑出来。

### 8. "销售总监不能改文件"结案
声称：结论=线上 MP 1.0.69 不消费行级 perms（旧逻辑页级判定），修复 42b9ac2e 已含 1.0.70+（1.0.72 已上传待提审）；后端权限正常（销售总监 token 行 perms 含 rename 实测）。
复核：a) 生产销售总监 token 亲手调 `GET /api/file/list?scope=public` 看行 perms；b) git diff 对比 MP 新旧 buildFileRowView；c) **反向打**：构造"销售总监应当能改但不能改"的反例在生产搜一遍（他人私人文件=设计拒绝不算；incentive 空间文件夹动作=FOLDER 级无行算一个，验证它是否真如前 AI 所说"影响≈0"——查 incentive 空间到底有没有文件夹）。

### 9. 体检"🔴0"的可信度
声称：7 域体检零红项，7 黄牌已列（out/全面体检报告-20261117.md）。
复核：**自己定体检项，别用它列的**。至少加测：a) 同 token **并发**勾两个码的竞态；b) 权限相关接口 P95 时延（抽 5 个）；c) SQL 注入探针（列表接口 keyword 参数 3 类 payload）；d) 水平越权：销售员 token 遍历他人 resourceId 打 3 类详情接口；e) MP 前端门禁绕过：无 token/错角色直接打 3 个 MP-only 后端接口；f) 备份**可恢复性**抽样（拿一份 NAS 备份在测试库演练 restore——只演练到测试库，不碰生产）。每个它没报的问题 =【前 AI 漏报清单】+1。

### 10. 部署脚本改动（96c84ec3）
声称：deploy-backend.sh 加 fast_rollback_container（免重建快滚）+ RESULT=PASS/FAIL 标记。
复核：`git show 96c84ec3` 读 diff；`bash -n` 语法；**推演并实测**（测试服可安全验证）：快滚通道 disabled 分支的真实触发条件；"容器已被删"场景回滚确实失败的具体行为。

### 11. 空 role_code 14 行纠偏
声称：原拟删除，核验为活配置（RolePermScopeService.java:102 按 assignee_type='USER' AND assignee_key=userId 匹配），已撤销。
复核：a) 读该消费代码确认匹配键；b) 抽马吉亮 customer:view 一行，实测其 customer 数据范围行为是否真受此行影响（对比取消该行的预期行为——只在测试服做等价实验，不碰生产）；c) 结论"不删"是否正确（还是应补 role_code/应走 PermCenter 取消）。

### 12. 三份文档一致性
声称：out/权限全景清单-20261117.md、out/全面体检报告-20261117.md、out/权限体系验证与全面治理方案-20261117.md 与生产事实一致。
复核：每份抽 10 个具体数字/结论对照你的实测。**打假点**：找"写了结论但无证据行"的段落。

## 铁律（违反即审计无效）

1. 生产库只读；写操作记【待授权清单】。
2. 测试服写入仅限"勾选/取消一个码"的生效验证，结束必须还原+快照比对。
3. 测试账号密码只试 1 次；失败走查库通道。
4. 只审计不修；不硬编码人员/角色；验证账号经 env/参数。
5. 每条结论：命令原文+退出码+关键输出行。
6. `out/` 下前 AI 的三份报告只在最后对照环节读，之前禁止读（防被它的结论锚定）。

## 输出格式（强制，写入 out/独立审计报告-<日期>.md 并在对话全文输出）

```
【前 AI 虚报清单】声称 vs 你的实测证据（逐条）
【前 AI 漏报清单】它没查/没报的问题（按严重度排序）
【12 项复核判定表】逐项 PASS/FAIL/BLOCKED + 一行证据
【红线外新发现】
【待授权清单】
总判定：前 AI 今日工作 可信 / 部分可信（百分比+依据）/ 不可信
元问题回答：「这个 AI 交付的权限体系，我敢不敢用？」（十条以内白话）
```
