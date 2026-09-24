# OCR 发现复核报告 — findings-module-customer.json（17 条，实际全部落在 `module/approval`）

- 复核对象：`slices-live/findings-module-customer.json`，共 17 条。
- **切片名与实际不符**：文件名为 `module-customer`，但 17 条的 `path` **全部**位于 `module/approval/`（controller/mapper/service），`module/customer | file | sea | reminder` 零命中；本报告按实际路径复核，不涉及客户模块（任务背景中「客户 82 条超范围」的历史裁定与本批无关）。
- 复核方式：逐条 read 真实源码（`ApprovalController` 96 行、`ApprovalConfigController` 49 行、`BizApprovalMapper` 20 行、`BizApprovalConfigMapper` 14 行、`ApprovalConfigService` 149 行、`ApprovalService` 765 行全文），并用 grep/glob/迁移链交叉核对调用方、守卫、种子数据与历史裁定。
- 关键外部事实（本次实测，非推断）：
  - `MybatisPlusConfig.java:98` 构造 `new PaginationInnerInterceptor(DbType.MYSQL)` **未设 maxLimit**；反编译 `mybatis-plus-jsqlparser-3.5.17.jar` 确认构造器只赋值 `optimizeJoin/dbType`，`maxLimit` 恒为 null → `handlerLimit` 空转 → **分页无上限**。
  - 反编译 `mybatis-plus-core-3.5.17.jar`：`BaseMapper.selectOne(Wrapper)` → `selectOne(wrapper,true)` → 命中多行抛 `org.apache.ibatis.exceptions.TooManyResultsException`（字节码 `athrow`）→ 经 `GlobalExceptionHandler.java:143-153`(DataAccessException)/`:165-173`(Exception) 落 **HTTP 500**。
  - `GlobalExceptionHandler` 无 `NumberFormatException` 专用处理器 → 落兜底 `:165-173` = 500 + 支持编号；`MethodArgumentTypeMismatchException` 才有 400（`:100-105`）。
  - 迁移链：`biz_approval.config.approval_code` 全局唯一 `uk approval_code`（`V1__baseline.sql:50`）已由 `V2026.09.21.3__tenant_scoped_code_uniqueness.sql:14-18` 改为 `uk_tenant_approval_code(tenant_id, approval_code)` → 同租户内编码重复**不可能落库**。
  - 生产种子（只读 grep `08-备份索引/09-生产环境备份/database/ycmn-production.sql:89,120`）：`biz_approval_config` 仅 1 行（`flow_type='claim'`, `approver_type='USER'`, `approver_ids='[11]'`, `sign_mode='ANY'`, `sort_order=0`）;`biz_approval_flow` 4 行，**每个 flow_type 各 1 行**（claim/transfer/extend/storage，`sort_order=1`）。
  - DDL：`biz_approval_node.node_order int DEFAULT NULL`（`V1__baseline.sql:114`，可空）；无任何 (approval_id, approver_id) 唯一约束（仅 `idx_approval_id`/`idx_approver_id`）。
- 权威基线对照：`02-迭代源代码/docs/security-audit/` 中 `05-gpt-review-20260922-adjudication.md:17`(DB-P1-007)、`08-gpt-agental-audit-20260922-adjudication.md:21`(P1-003)、`09-gpt-reaudit-external1-20260922-adjudication.md:15`(CONC-P1-005/006) **已裁定取号并发（第 11/13 条）为"属实-已知"，定级低，修复排入下一批次号段表**；`00-项目管理/业务决策会-议题清单-20260919.md` 议题二.1【已裁决 20260919】管理员手动转交 —— 即第 3 条的权限形态是**已裁决设计**。
- 复核限制：只读，未启动应用实测运行时行为；`selectById(null)` 的驱动绑定行为未实测（第 3 条已按服务层兜底判定，不影响裁定）；`IdType.AUTO` 下 id 是否被显式写入按"未实测"表述（第 4 条结论不依赖该点）。

裁定口径：CONFIRMED=属实且原因正确；PARTIAL=现象属实但机理/定性/严重度有实质偏差（写明偏差）；FALSE=有反证；BY_DESIGN=有意设计或已裁定；UNVERIFIABLE=证据不足。

---

## 一、17 条逐条裁定

| 序号 | 校准严重度 | 裁定 | 文件:行（仓库相对路径） | 一句话理由 + 代码证据 | 触发前提 | 修复建议 |
|---|---|---|---|---|---|---|
| 1 | low（原 medium） | CONFIRMED | `02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java:62-64` | 机理与后果完全属实：`Long.valueOf(value)` 对 `customerId=abc`/空串抛 `NumberFormatException`，`GlobalExceptionHandler` 无该异常处理器（仅 `:100-105` 处理 `MethodArgumentTypeMismatchException`），落兜底 `:165-173` → 500+支持编号而非 400。降级理由：仅错误语义/日志噪声，无越权与数据影响（入口已有 `approval:view`，`:40`） | 非 latent：任一持 `approval:view` 用户传非数字 `customerId` 即 500 | 补 try/catch → `BusinessException(400,"customerId 格式不正确")`，空串按 null 处理（与 `AuthController` 补 400 同口径） |
| 2 | medium | CONFIRMED | `.../approval/controller/ApprovalController.java:26` | 属实且全仓无任何兜底：`size` 无上界，直落 `new Page<>(current,size)`（`ApprovalService.java:114`）；`MybatisPlusConfig.java:98` 未设 `maxLimit`（反编译确认字段恒 null）。同仓惯例恰恰是钳制：`SysAuditLogService.java:22,31` `Math.min(size,100)`、`SysRuleVersionService.java:19` `Math.min(size,100)`。与第 16 条 N+1 叠加后放大为 15 万次主键点查级 | 非 latent：持 `approval:view` 传 `size=1000000`（或 `size<0` 时 MP 分页失效=全量）；表行数+2N 次点查即为成本 | `size = Math.min(Math.max(size,1),100)`（`current` 同样 `Math.max(current,1)`），或给 `PaginationInnerInterceptor` 设 `maxLimit` 一次性覆盖全模块 |
| 3 | low | BY_DESIGN | `.../approval/controller/ApprovalController.java:67-68` | 依据：`00-项目管理/业务决策会-议题清单-20260919.md` 议题二.1【已裁决 20260919】**管理员手动转交**，`:66` 代码注释「【议题二.1 裁决】…服务层二次校验 isAdmin」；服务层确有硬校验 `ApprovalService.java:330-332` 且落审计 `:353`，并有负例测试 `ApprovalServiceTest.java:598 transferNode_nonAdmin_rejected`，**无越权**。建议改 `approval:approve` 反而错误（审批人≠转交人）。子诉求（`newApproverId` 为空）已由服务层兜底：`:345-349` `target==null` → `BusinessException` 400 | — （已裁定） | 无需改权限；仅可选：控制器对 `newApproverId==null` 提前返 400 文案，避免依赖服务层兜底 |
| 4 | low（原 medium） | PARTIAL | `.../approval/controller/ApprovalConfigController.java:29-31` | 机理属实：实体直绑 + 无 `@Valid`/DTO，`status`（`biz_approval_config` 无 `deleted` 列，`delete()` 即置 `status=0`，`ApprovalConfigService.java:48-53`）、`approverIds/signMode/sortOrder/createdAt/updatedAt` 客户端可写；`strictInsertFill` 仅当字段为 null 才填充（反编译 `MetaObjectHandler.strictFillStrategy` 确认 `getValue()==null` 判定），故伪造时间戳可入库。**偏差：定性与严重度**——入口需 `system:config:edit`（`:27`），该用户本就能调 `DELETE /{id}` 达到同样效果，不构成提权；且"实体直绑"是全仓 67 处惯例（`SysUserController.java:43/54`、`SysRoleController.java:50/57`、`CustomerController.java:93` 等），非本模块特例（`id` 是否被 MP AUTO 策略显式写入未实测，不影响结论） | latent：需 `system:config:edit` 主动构造请求体（正常前端不传 status） | 可接受现状；若要收口，用 DTO 仅暴露 `flowName/flowType/approverType/approverIds/signMode/sortOrder`，`status` 只在 delete 端点改 |
| 5 | low | CONFIRMED | `.../approval/controller/ApprovalConfigController.java:37-39` | 属实：`updateById` 对不存在/已软删的 id 影响 0 行且不抛异常（`ApprovalConfigService.java:42-45`），控制器无条件 `Result.ok()`，调用方拿到假成功、UI 刷新后"改了没变" | 非 latent：任一持 `system:config:edit` 用户对已删除 id 发 PUT | `return configService.update(config) == 1 ? Result.ok() : Result.error(404,"配置不存在")`（`update` 返回受影响行数） |
| 6 | low（原 medium） | CONFIRMED | `.../approval/mapper/BizApprovalConfigMapper.java:12` | 属实且**可被 UI 轻易触发**：仅 `ORDER BY sort_order`，`biz_approval_config` 无任何唯一键（`V1__baseline.sql:77-78` 只有 `PRIMARY KEY(id)`+`idx_tenant`）；而 `ApprovalConfigService.createNodes:71-88` 用**遍历序号**当串行阶段号（`stage++`），排序不定即阶段序不定。触发面很宽：前端新增流程默认 `sortOrder=0` 且 `el-input-number :min="0"` 无唯一性校验（`aigicrm-ui/src/views/system/approvalConfig.vue:68,89,112`），同 `flow_type` 配两行即并列 | latent：管理员为同一 `flowType` 建 ≥2 行且 `sort_order` 相等（生产现仅 claim 1 行、sort_order=0，故当前未触发） | `ORDER BY sort_order, id`；**同因同修** `ApprovalService.java:267-273` 取"首阶段 flowName"的 `LIMIT 1` 查询，否则 `currentNode` 可能与实际首阶段不一致 |
| 7 | low | CONFIRMED | `.../approval/mapper/BizApprovalConfigMapper.java:13` | 属实：`status=1` 字面量散落 3 处（本行、`ApprovalConfigService.java:32` 的 `list()`、`ApprovalService.java:255` 的 `submit()`），`status=0` 另 1 处（`ApprovalConfigService.java:51`），无共享常量；纯可维护性，无行为缺陷 | 非 latent（纯维护性） | 抽 `static final int STATUS_ENABLED=1` 复用；注解 SQL 本身无需改（显式租户/单表查询意图清楚） |
| 8 | low（原 medium） | CONFIRMED | `.../approval/service/ApprovalConfigService.java:132-141` | 机理属实：`for (Integer did : ids)` 内逐部门 `userMapper.selectList` → N+1。**降级理由**：N = 单行配置里的部门 id 个数（`approver_ids` JSON，`varchar(500)`），前端审批流配置页**根本没有 DEPT 选项**（`approvalConfig.vue:48-51` 仅 USER/ROLE 单选），且生产配置为 `approver_ids='[11]'`；实际 N≈1，一次 `in` 批查收益近乎为零 | latent：仅当直连 API 造出 DEPT 且多部门配置 | 收集 `deptIds` 去重后用一次 `in(SysUser::getDeptId, deptIds)`（改法本身正确，优先级最低） |
| 9 | low（原 medium） | CONFIRMED | `.../approval/service/ApprovalConfigService.java:145-147` | 属实：`catch (Exception e) { return Collections.emptyList(); }` 无任何日志——类内已有 logger 且别处在用（`:27` 定义、`:125` `log.warn`），故属"有工具不用"的静默吞异常，违反 `02-迭代源代码/AGENTS.md`「禁止吞异常/空 catch」。后果确实误导：调用方 `ApprovalService.java:261-266` 据此抛「审批流程已配置但无有效审批人，请联系管理员检查流程配置」，把 DB/解析故障报成配置问题。降级理由：诊断性缺陷，与同批 `verification-module-system-ui.md` S3（空 catch）同口径定 low | latent：仅异常路径（DB 抖动、`approver_ids` 脏 JSON）触发 | 保留兜底但补 `log.error("解析审批人失败 configId={}, approverType={}", cfg.getId(), cfg.getApproverType(), e)`，并区分"配置为空"与"解析/查询失败" |
| 10 | low | CONFIRMED | `.../approval/service/ApprovalConfigService.java:114` | 机理属实：`valid` 是 `List`，`filter(valid::contains)` 线性查（`:108-113` 得到的 `valid` 长度 ≤ `requested` 长度，同量级）→ 理论 O(n²)。但 n 为单行配置的审批人数（生产 `[11]` = 1 个），改 Set 无可测收益；已是最低级别，不构成缺陷 | latent 且现实不可触发 | 可随手改 `new HashSet<>(valid)`；不建议为此单独发版 |
| 11 | low（原 medium） | CONFIRMED | `.../approval/mapper/BizApprovalMapper.java:10` | 事实链完整、描述准确：`selectMaxCodeByPrefix`（本行）+ `insert`（`ApprovalService.java:238-249`）非原子，同租户并发可读到同一 max；`V2026.09.21.3__tenant_scoped_code_uniqueness.sql:14-18` 的 `uk_tenant_approval_code(tenant_id, approval_code)` 使重复**无法落库**，改为撞唯一键 → `DuplicateKeyException`（`DataAccessException`）→ `GlobalExceptionHandler.java:143-153` 返回 **500+支持编号**；`grep -rn "DuplicateKeyException" src/main/java` 与 `grep -rn "Retryable" src/main/java` **均零命中**（无重试，与发现所述一致）。降级依据：`05-gpt-review-...:17`／`08-gpt-agental-...:21`／`09-gpt-reaudit-...:15` 已裁定"属实-已知、定级低、号段表方案排下一批次"，属已知残余风险 | latent：同租户两人同一秒提交（或前端连点）才撞号 | 随下一批次做号段表/单语句原子分配；过渡期在 `submit` 捕获 `DuplicateKeyException` 有限次重试并返回"提交冲突请重试"而非 500 |
| 12 | low | CONFIRMED | `.../approval/mapper/BizApprovalMapper.java:11` | 属实且对照成立：MySQL `LENGTH()` 为字节数，当前唯一调用点传 `"P"+Year+"-"`（`ApprovalService.java:237`，纯 ASCII）故暂时正确；同仓 `BizCustomerMapper.java:14` 正是把 `#{prefixLen}` 从 Java 侧传入（`String.length()`=字符数），本处未对齐 | latent：仅当前缀改含中文/多字节（如大区名）才错位 | 改 `CHAR_LENGTH(#{prefix})`，或对齐 `BizCustomerMapper` 增加 `prefixLen` 参数 |
| 13 | low（原 medium） | PARTIAL | `.../approval/service/ApprovalService.java:238-239` | 现象属实但**结果预测错误 + 修复建议已实现**：发现称会"persist two approvals with the identical code"，实际 `uk_tenant_approval_code(tenant_id, approval_code)` 已存在（`V2026.09.21.3:14-18`），第二条 insert 必抛 `DuplicateKeyException` → 500，**不会**产生重复编码；发现建议"Back this with a DB unique constraint on (tenant/group, approval_code)"恰恰是 V2026.09.21.3 已做的。且与第 11 条为**同一缺陷重复上报**（第 11 条已正确指出唯一键存在）。定级依据同 05/08/09 裁定（低） | latent：同第 11 条（同租户并发提交） | 与第 11 条合并处理：捕获唯一键冲突重试/提示，或随号段表批次改造；本条无需独立修复 |
| 14 | low（信息级，原 high） | FALSE | `.../approval/service/ApprovalService.java:460-461` | **反证（不可达）**：`nodeOrder==null && configId!=null` 的组合在当前代码中无法产生——`config_id` 只在 `ApprovalConfigService.java:87` 写入，而同一代码块 `:85` 必先 `setNodeOrder(stage)`（stage≥1 非空）；legacy 路径（`ApprovalService.java:288-311`）**从不设 configId**，而 `advanceApproval:414-421` 一旦"无任何节点带 configId"即 `legacy=true` 提前 return，根本到不了 `:461`。发现自举的触发场景（flow.sortOrder 为 null 的 legacy 节点）恰被该 legacy 短路挡在 `:420`。故 `node_order` 虽 DDL 可空（`V1__baseline.sql:114`），`:461` 的自动拆箱在本代码路径下不会 NPE；`:447` 的空判之所以存在是因为该循环要区分"无后续阶段"语义，并非同类风险的补丁 | 不适用（无可达触发路径） | 无需修复；若追求防御一致性可顺手补 `n.getNodeOrder()!=null &&`（零风险，非缺陷） |
| 15 | medium | CONFIRMED | `.../approval/service/ApprovalService.java:288-289` | 机理成立且**经管理员 UI 可达**：`transferNode:329-357` 只校验目标用户"存在+启用+未删"（`:345-349`），**不校验该用户在本单是否已有 pending/waiting 节点**，也不拒同人转交；而 `biz_approval_node` 无任何近似唯一约束（`V1__baseline.sql:122-125` 仅普通索引），前端转交候选人=任意启用用户（`aigicrm-ui/src/views/approval/index.vue:246-247,296-303`）→ 同 (approvalId, approverId) 出现 2 条 pending → `approve()`/`reject()` 的 `selectOne`（`:375-380`/`:506-511`）抛 `TooManyResultsException`（MP 3.5.17 字节码已验证）→ **500**，审批单卡死需再转交解救。发现的另一机理（legacy 分支 `for (flow)` 内 `flow` 未使用、每行都插同一 `leaderId`，`:288-296`）代码属实，但生产每个 flow_type 仅 1 行 `biz_approval_flow`（`ycmn-production.sql:120`）且无该表的管理入口 → 该路径现状不可达；`DEPT` 分支未 `distinct`（`ApprovalConfigService.java:133`）同属未启用路径 | latent：管理员把一个待办转交给本单已有待办的人（或 DEPT 配置重复 id）；legacy 机理需先造 ≥2 行旧流程 | `transferNode` 增加"目标用户在本单已有 pending/waiting 节点则拒绝"（可选放行同人 no-op）；`createNodes` 对全部审批人 id 统一 `distinct` 后再落节点 |
| 16 | low（原 medium） | CONFIRMED | `.../approval/service/ApprovalService.java:114-117` | 属实：列表每行 `fillApprovalNames` 两次 `selectById`（`:755-764`），详情每个节点一次 `selectById`（`:160-178`）。降级理由：默认 `size=15` 时仅多 30 次主键点查，点查成本低，单独看是低危；**其破坏力来自与第 2 条叠加**——`size` 无上限后变成 2N 次点查（放大 2 倍于分页本身），故修复顺序应先做第 2 条 | latent：页码越大/`size` 由第 2 条放开后才显著 | 收集去重后的 `applicantId/customerId/approverId`，各用一次 `in(...)` 批查后在内存映射 |
| 17 | low | CONFIRMED | `.../approval/service/ApprovalService.java:200-201` | 属实：`"claim".equals(type) && customerId != null` 使 `customerId` 缺失时**整段**公海可用性/池级权限/可见范围/原子锁定全部跳过（`:201-235`），随后照常建单为 `reviewing`；通过时 `executeApprovalAction` 走 `case "claim"` → `completeClaim(null,...)`（`:636`），而 `SeaService.java:233-238` 的 `eq(customerId, null)` 生成 `customer_id = null` 恒不匹配 → 静默 return，得到"批了但什么都没发生"的单据。`claim` 在生产确有配置（`approver_ids='[11]'`），故节点能建成、流程能走完 | latent：需绕过前端直发 `POST /api/approval/submit {"type":"claim"}`（前端必带 customerId，`aigicrm-ui/src/views/sea/index.vue:222`）且持 `approval:view` | `if ("claim".equals(type) && customerId == null) throw new BusinessException(400,"捞取审批缺少客户ID")`，把静默跳过改为显式拒绝 |

---

## 二、统计

### 2.1 裁定计数（17 条）

| 裁定 | 条数 | 序号 |
|---|---|---|
| CONFIRMED | 13 | 1、2、5、6、7、8、9、10、11、12、15、16、17 |
| PARTIAL | 2 | 4、13 |
| FALSE | 1 | 14 |
| BY_DESIGN | 1 | 3 |
| UNVERIFIABLE | 0 | — |

结论：**假阳性 1/17**（第 14 条 NPE 经 legacy 短路反证为不可达）；另有 1 条事实重复（第 13 条 = 第 11 条同因）且其"会落重复编码"的结论被唯一键反证；1 条严重度过重（第 14 条原 high → 不成立）。

### 2.2 校准严重度计数

| 严重度 | 条数 | 序号 |
|---|---|---|
| critical | 0 | — |
| high | 0 | — |
| medium | 2 | 2、15 |
| low | 14 | 1、3、4、5、6、7、8、9、10、11、12、13、16、17 |
| 不适用（FALSE，第 14 条另计） | 1 | 14 |

合计 16 + 1 = 17。原标 high 的仅第 14 条，经复核**不成立**；原标 medium 的 10 条中 8 条下调为 low、2 条维持 medium，无一条需上调。

- **latent（有明确触发前提、当前未生效）**：4（需 config 编辑者构造请求体）、6（需同 flowType 重复 sort_order，UI 默认 0 极易凑出）、8（需 DEPT 配置且多部门）、9（仅异常路径）、10（n 需很大）、11/13（同租户同刻并发）、12（前缀需非 ASCII）、15 的部分机理（legacy 多行/DEPT 重复 id）、16 的放大效应（依赖第 2 条）、17（需绕过前端直发）。
- **非 latent（当前即可触发）**：1（任意非数字 customerId → 500）、2（size 无上界 → 资源耗尽）、5（对不存在 id 的 PUT 假成功）、7（纯维护性）、14 之外无。
- 第 15 条整体判 medium 而非 latent 过低：其转交路径在**现有管理端 UI 上即可点出**，后果是 500 + 审批单卡死。

### 2.3 最值得优先修的 5 条

| 优先级 | 条目 | 理由 |
|---|---|---|
| 1 | **第 2 条** 审批列表 `size` 无上限 | 唯一资源耗尽路径，且与第 16 条 N+1 复利放大；一行 `Math.min/Math.max` 或给 `PaginationInnerInterceptor` 设 `maxLimit` 全模块受益 |
| 2 | **第 15 条** 转交不校验"本单已有待办"→ 重复节点 → approve 500 卡单 | 管理端现网可点出，后果是 500 且单据卡死；补一次 `selectCount` 即修 |
| 3 | **第 9 条** 审批人解析异常静默吞 | 一行 `log.error` 恢复可诊断性，避免把 DB 故障误报成"请检查流程配置"；违反项目「禁止吞异常」 |
| 4 | **第 11 条（含第 13 条重复）** 取号 MAX+1 并发撞唯一键 → 500 | 已裁定已知残余（05/08/09），过渡期加唯一键冲突捕获+重试即可把 500 变业务提示，长期随号段表批次解决 |
| 5 | **第 1 条** `customerId` 非法值 → 500 | 与项目"参数错误返 400"口径不一致，`try/catch` 三行收口 |

（紧随其后：第 5 条 update 假成功、第 6 条 `ORDER BY` 补 tie-breaker、第 4 条 `status` 可写收口 —— 均为一两行改动的低风险项。）
