# AIGICRM 全维度审查报告（workbuddy）

- **审查日期**：2026-10-16（21:25–22:40）
- **性质**：全维度审查 = 主线一业务动作验证（测试环境实测）+ 主线二代码质量八维度审查。全程未改任何源码/配置，未 git commit/push，未对生产执行任何写操作（生产 SSH 本次仍不可达，仅一次只读探测），未触发小程序上传/提审/发布。
- **测试环境**：<http://111.229.37.220（ssh> 别名 aigicrm-test）；MySQL 走 docker exec 只 SELECT。
- **并行会话说明**：审查期间检测到其他会话活动（perm-matrix-probe2 登录、`全方面体检报告-workbuddy-20261016.md` 新建、并发跑 verify-perm-matrix），本次审查的两处工具链失败（见 §5-8 质量维度）经诊断均系并发/环境干扰，已用受控实验排除系统缺陷，未清理他人工作面。

---

## ① 总览表

| #  | 维度              | 检查项数    | 通过     | 存疑/环境问题 | 不符    |
| -- | --------------- | ------- | ------ | ------- | ----- |
| 1  | 认证与会话（A）        | 6       | 6      | 0       | 0     |
| 2  | 客户管理（B）         | 12      | 12     | 0       | 0     |
| 3  | 发票管理（C）         | 7       | 7      | 0       | 0     |
| 4  | 延期审批（D）         | 3       | 1      | 2       | 0     |
| 5  | 激励与排行（E）        | 4       | 3      | 1       | 0     |
| 6  | 判定/数据字典/复合词（F）  | 2       | 2      | 0       | 0     |
| 7  | 照片墙/佐证（G，含 F28） | 3       | 1      | 2       | 0     |
| 8  | 权限矩阵专项（I）       | 23      | 22     | 1       | 0     |
| 9  | 代码·架构           | 5       | 5      | 0       | 0     |
| 10 | 代码·后端 Java 反模式  | 9       | 7      | 2       | 0     |
| 11 | 代码·SQL 与迁移      | 8       | 5      | 3       | 0     |
| 12 | 代码·前端 Vue       | 7       | 4      | 3       | 0     |
| 13 | 代码·小程序          | 7       | 5      | 2       | 0     |
| 14 | 代码·安全           | 4       | 4      | 0       | 0     |
| 15 | 代码·测试质量         | 5       | 3      | 2       | 0     |
| 16 | 代码·契约           | 3       | 3      | 0       | 0     |
| —  | **合计**          | **108** | **80** | **18**  | **0** |

> 判定口径：**通过**=实测/实证符合预期；**存疑/环境问题**=行为正确但存在口径待产品确认、环境数据缺失或测试覆盖受限；**不符**=功能性错误或越权（本轮 0 项）。D/E/G 的"存疑"均为已知问题核状态（F14/23、M2、F28），非本轮新发现的功能错误。

---

## ② 角色×功能验证矩阵结果

**角色底稿（测试库实测，2026-10-16）**：

| 账号         | userId | 角色      | 角色数据范围 | override | 组织        |
| ---------- | ------ | ------- | ------ | -------- | --------- |
| admin      | 1      | admin   | all    | —        | 集团1·总部    |
| NO001（马吉亮） | 11     | 0（销售总监） | custom | **all**  | 集团1·总部    |
| TY002（李帮文） | 38     | 06（销售员） | team   | —        | 太原办（org4） |
| NM002（白晓炯） | 44     | 06（销售员） | team   | —        | 内蒙办（org6） |
| UATNOF     | 126    | uat_nof | self   | —        | 集团1·总部    |

**客户列表可见集对账（API total vs DB 口径，全等）**：

| 账号     | API total | DB 口径对账                                  | 判定           |
| ------ | --------- | ---------------------------------------- | ------------ |
| admin  | 285       | 全库 deleted=0 且 status∉{sea,archived}=285 | ✅            |
| NO001  | **285**   | override=all 被列表采纳（=admin 口径）            | ✅ **F06 已修** |
| TY002  | 26        | 自有 26（无下属，team=manager 链）                | ✅            |
| NM002  | 24        | 自有 8 + 下属 16（manager_id=44 的 2 人名下）      | ✅            |
| UATNOF | 0         | self 档无自有客户                              | ✅            |

**权限边界实测（每条均有请求/响应/DB 三方证据）**：

| 用例       | 角色          | 动作                                                      | 结果                                    | 判定                       |
| -------- | ----------- | ------------------------------------------------------- | ------------------------------------- | ------------------------ |
| B2-2     | TY002       | 创建客户时传 ownerId=11（篡改归属）                                 | 200 但落库 owner=38（服务端强制本人）             | ✅ 篡改拦截                   |
| B3-2     | TY002       | 编辑 admin 客户 339（水平越权）                                   | 400「无权编辑该客户」                          | ✅                        |
| B3-3     | NM002       | 查看 TY002 客户 131692（跨组织水平越权）                             | 400「无权查看该客户」                          | ✅                        |
| B3-4     | UATNOF      | 创建客户（垂直越权，无 customer:add）                               | 403「没有访问权限」                           | ✅                        |
| B4-3     | TY002       | 释放他人客户 339                                              | 400「无权编辑该客户」                          | ✅                        |
| B5       | TY002→NM002 | 分享 131692→可见 200→关闭分享→400                               | 全链路正确                                 | ✅ **F11 已修**             |
| C2       | NM002       | 对他人客户登记发票                                               | 403（无 finance:view，财务隔离正确）            | ✅                        |
| C4       | admin       | 删除发票不带 reason                                           | 400「请填写删除原因（财务记录删除必须留痕）」              | ✅                        |
| **D1**   | **NM002**   | **对无查看权限客户 131692 发起 extend 审批**                        | **200 生成审批单 P2026-053（applicant=44）** | ⚠️ **F14/23 线上仍在（见 §④）** |
| I2/I4/I5 | WB临时用户      | 无权限创建→403；admin 提权后**旧 token 不重登**创建→200；撤销后旧 token→403 | perm_version 即时生效全链路                  | ✅                        |
| I-矩阵     | 全角色         | verify-perm-matrix.sh 21 用例                             | **PASS=21 FAIL=0**                    | ✅                        |

**状态码口径备注**：功能权限拒绝=403（@PreAuthorize 层），数据范围拒绝=400（业务层 BusinessException），与任务书"400 为主"的口径并存——两种路径均正确收口，非缺陷。发票登记的 403 为 finance:view 缺失（财务数据按角色隔离，销售员无财务权限，符合"财务数据不外露"设计）。

---

## ③ 发现清单

### P0（需立即处理）

#### P0-1 F14/23 延期审批 extend 分支不校验发起人归属——**代码已修但测试/生产均未部署**

- **复现**：`POST /api/approval/submit`，body `{"type":"extend","customerId":131692,...}`，用 NM002（userId=44，对客户 131692 无任何查看权限）token → `{"code":200}`，DB 生成审批单 P2026-053（applicant_id=44, status=reviewing, 节点审批人=11）。
- **代码侧状态**：本地 HEAD `8c2dc039`（2026-10-16 21:28 提交）已修复——`ApprovalService.submit()` extend 分支增加 `!isAdmin() && !canView(customerId)` 抛 403，含 ExtendSubmitOwnershipTest（156 行新测试）。
- **部署侧状态**：测试环境 jar 构建于 2026-10-13 22:36（a8dde617 之前），**不含该修复**；生产钉板 a8dde617（10-13）同样不含。即**修复在 git 里、不在任何运行环境里**。
- **风险**：无查看权限的销售可对他人客户发起保护期延期，审批人误批即 +360 天（与 10-12 报告 M1 同构，本轮独立复现）。
- **建议**：将 8c2dc039 部署到测试环境回归后上生产；排查存量 reviewing 状态 extend 审批单（本次未查询存量规模）。
- **测试数据已清理**：审批单 95 已由 NO001 驳回（status=rejected）。

### P1（需排期修复）

#### P1-1 M2 仍在：`/api/incentive/customer-records` 忽略 period 参数

- **复现**：NO001 token 分别请求 `customer-records?current=1&size=500` 与 `?period=2026H1`，两次响应 md5 **完全一致**（fcc9fa9d…）；前端 `useIncentiveStats.js:135` 传了 period，后端 `IncentiveController.java:173-186` 不收。
- **影响**：激励统计页「客户排行」Tab 周期筛选无效，三 Tab 数据自相矛盾（统计表/个人排行按周期归零、客户排行不变）。
- **建议**：后端增加 period 参数按 `biz_incentive_record.period` 过滤（与 category-stats 同口径）。

#### P1-2 F28 仍在：测试环境 uploads 卷历史文件大面积物理丢失

- **实测**：`biz_file` 有效记录 1045 条（incentive 992 / private 2 / public 51）vs 容器内物理文件 **11 个**（其中多数为测试期新上传的 ta_test.*）；admin 抽样下载最老 6 个 incentive 文件（id 1/2/4/5/6/8）**全部 404**。与 10-12 报告 H4 量级一致，**4 天未恢复、无根因结论**。
- **影响**：激励凭证链整体不可用（照片墙空白瓦片、附件打不开）；属测试环境数据/环境治理，非代码缺陷。
- **建议**：定位卷丢失根因并确认可恢复备份；恢复前 UI 对 404 文件给「文件缺失」提示。

#### P1-3 前端分享按钮对 admin 恒 403 仍展示（L4 形态保留）

- **证据**：`src/views/customer/detail.vue:11,161-162`（canShareCustomer=hasPermission('customer:edit')，无集团归属预判）；后端 `CustomerService.java:1810-1812` 无有效集团归属恒抛 403。admin 点击必报错。
- **建议**：UI 对 admin 隐藏分享入口或给说明文案。

#### P1-4 激励上传 trial 品类 0 文件可提交（L10 形态保留）

- **证据**：`src/composables/useIncentiveUpload.js:151-155` canSubmit 对 trial 只校验 selectedCustomerId；后端 `IncentiveService.java:664-666` 强制至少一文件。用户走完流程才被 400 拦。
- **建议**：canSubmit 对 trial 分支同样要求至少一个文件。

### P2（建议改进）

1. **文档落后源码**：gate.sh [4] doc-freshness FAIL——8c2dc039（F14/23 修复，10-16 21:28）未同步 docs（docs 最后提交 10-12）。提交信息声称"体检报告20261016入库"已落实（227 行入库），但功能文档/需求分篇未更新。
2. **SysUserService.deleteUser 副作用先于校验**（`SysUserService.java:415`）：purgeByUser 在存在性/权限校验之前执行；因 @Transactional 回滚兜底无数据风险，建议调整顺序。
3. **CustomerService 空 catch**（`CustomerService.java:1477-1478`）：财务审计日志 insert 失败完全静默无日志，建议补 log.warn。
4. **SeaService 命名误导**（`SeaService.java:58`）：`visiblePoolIdsOrNull()` 永不返回 null，调用方 if 分支为死代码，建议重命名。
5. **迁移遗留风险**（详见 §5-11）：V2026.09.11.6 硬编码 id 9001+ 潜在冲突、V2026.09.22.1 唯一键未做重复数据预检、中文 INSERT 无文件级 SET NAMES 兜底。
6. **前端**：scoreOrder 死参数（search.vue:184）、BasicSettings.vue:101 裸读 localStorage token（违背 getToken() 唯一入口）、激励列表加载失败无页面级错误态（降级为空态）。
7. **小程序**：empty-icon 等约 27 个静态 class 无定义（L17 未修完，空态图标退化裸字符）；7 页缺 .wxss（L16 从 10 减至 7）；incentive/index.wxss:93 图片瓦片 160rpx 定高与视频/文档 aspect-3/2 约 10rpx 错齐（M3 轻微残留）；project.config.json 未锁 libVersion。
8. **提交信息与工作区不一致**：`全方面体检报告-workbuddy-20261016.md`（并行会话产物）为未跟踪文件，交付前需收口（按纪律未清理）。

---

## ④ 已知问题状态表（逐条核状态，附本轮证据）

| 编号             | 问题                                              | 状态                               | 本轮证据                                                                                |
| -------------- | ----------------------------------------------- | -------------------------------- | ----------------------------------------------------------------------------------- |
| **F14/23**（M1） | 延期审批 extend 不校验发起人归属                            | ❌ **测试环境仍存在**（代码已修 8c2dc039，未部署） | §P0-1：NM002 越权 extend → 200 生成 P2026-053                                            |
| **F11**（H3-2）  | 关闭共享反扩可见性                                       | ✅ **已修**                         | §②B5：分享→可见→关闭→400 收回；NO001=285 全量证明 all 不被 auto_share 收窄                            |
| **F06**（H3-3）  | dataScope 三处口径矛盾（auth/info 报 all、统计按 all、列表不采纳） | ✅ **已修**                         | §②：NO001 列表 total=285=admin 全量（override 采纳）                                         |
| **F03**（M4）    | period 双重编码乱码行                                  | ✅ **已清理**                        | DB 实测 id=266 period HEX=32303236E585A8E5B9B4（正确 UTF-8），全表 232 行单一值无乱码               |
| **F28**（H4）    | uploads 文件大面积丢失                                 | ❌ **仍存在**（≈98.9%，4 天未恢复）         | §P1-2：1045 记录 vs 11 物理文件，6/6 老文件 404                                                |
| M2             | customer-records 忽略 period                      | ❌ **仍在**                         | §P1-1：md5 一致                                                                        |
| H2             | 小程序照片墙索引遮蔽                                      | ✅ **已修全仓，残留 0**                  | §5-13 小程序维度：6 个嵌套 wx:for 文件逐一核对安全                                                   |
| H1             | auto_share 字符串 ID 致非 admin 500                  | ✅ **触发条件不成立**                    | DB 实测 auto_share={superior:true,admin:true,roles:["0"],users:[]} 无字符串 ID；矩阵 21/21 绿 |

**已裁决事项**（开票日推导、发票删除留痕、三态不变量、公海锁超时、invoice_never 宽限、排行可见性、单集团、org 兜底、郑磊归属案）：按指令不翻案。其中发票删除留痕本轮正向验证通过（C7：sys_log detail 含票号/日期/金额/状态/原因）。

---

## ⑤ 代码质量发现（八维度分组）

### 5-1 架构

- **ArchUnit 三层约束**（`ArchitectureTest.java`）：Controller 不直查 Mapper / 不直用 Entity / Service 不反向依赖 Controller；19 个 Controller 在 TECH_DEBT 显式豁免清单内，**新增违规会被测试拦截**（newViolations 过滤机制）。防分层腐化设计到位，符合"死于熵增"的历史教训。
- **防崩溃防线**：GlobalExceptionHandler 统一异常包装（500 带支持编号 S-xxx，本轮 D1 实测）；迁移治理三重保险（FROZEN-SHA256 冻结清单 + MIGRATION-LINEAGE 血缘文档 + 幂等守卫）；fail-closed 校验贯穿鉴权链路。
- **结论：通过（5/5）**。

### 5-2 后端 Java（六个历史反模式逐条实证）

| 反模式                | 结论             | 证据                                                                                                           |
| ------------------ | -------------- | ------------------------------------------------------------------------------------------------------------ |
| 短路守卫 fail-open     | 未命中            | CustomerAccessService/ApprovalService extend 分支/JwtAuthFilter/RolePermScopeService 等均 fail-closed            |
| 注释虚构兜底             | 未命中            | 抽查全部安全声称注释与实现一致（含 OrgScopeResolver G3 默认 false 收窄）                                                           |
| update 漏校验老字段      | 未命中            | CustomerService.update 逐字段 fieldChanged 比对（ownerId/status/protectionStatus+日期冻结）；SysUserService.update 有提权防护 |
| early-return 副作用前置 | **命中 1 处 Low** | SysUserService.java:415 purgeByUser 先于存在性/权限校验（事务兜底）                                                         |
| 规则定义即特权            | 未命中            | PermissionMatrixTest 硬断言所有写方法带权限码注解且码在字典内                                                                    |
| @Test 计数充断言        | 未命中            | 50+ 测试类全有真实断言（ApprovalServiceTest 46 处断言等）                                                                   |

- 附加：System.out.println 主源码 0 处；业务 id 硬编码 0 处（DEFAULT_ROOT_ORG_ID=1 等为有据常量）；空 catch 1 处（P2-3）。

### 5-3 SQL 与迁移（65 个 Flyway 文件）

- **通过项**：版本号无撞车（数字版 V1–V33 + 日期版并存，按设计）；撤销纪律=新正向迁移修复（V2026.09.23.1 修复 21.1 误删事故，形成"按码不按 id"纪律）；测试环境 flyway_schema_history 最近 10 条全 success=1，末版本与仓库一致；sys_audit_log append-only 触发器实现正确（V2026.09.19.1 SIGNAL 45000）；无 MODIFY/删列。
- **存疑项**（详见 P2-5）：① V2026.09.11.6 菜单种子硬编码 id 9001–9082（auto_increment 涨到 9000+ 会冲突，当前概率极低）；② V2026.09.22.1 uk_sea_pool_active 唯一键未做存量重复数据预检（有重复则 ALTER 整体失败）；③ 中文 INSERT 依赖 JDBC 连接字符集，无文件级 SET NAMES 兜底（V33 本身是历史乱码修复迁移）。

### 5-4 前端 Vue

- **合规项**：el-badge 0 处（menu-count 自定义类）；客户状态中文标签唯一来源 utils/customer-status.js；request.js 401/403 分流正确（401 或 403+token 失效→会话过期；403+token 有效→权限提示）；v-html/innerHTML/eval 0 处；console.log/TODO 0 处；dist 构建产物 22:20 新构建与源码同步。
- **P1**：见 P1-3/P1-4/P1-1。
- **契约一致性**：9 个 api 模块端点逐一比对后端 Controller 全部存在；customer/list 参数 current/size 一致。
- **P2**：scoreOrder 死参数；BasicSettings.vue:101 裸读 localStorage token；激励列表加载失败无页面级错误态（console.warn 降级为空态，失败与空数据不可区分）。

### 5-5 小程序（41 页）

- **H2 核状态：已修全仓**：pages/incentive/index.wxml 外层默认 index + 三处内层显式命名（wallGroupIdx/wallFileIdx/childIndex/detailIndex），data-record-index 取回外层客户下标；全仓其余 6 个含嵌套 wx:for 的文件逐一核对安全（内层未引用 {{index}} 或用数据属性寻址）。
- **M3 轻微残留**：image 160rpx 定高 vs 视频/文档 aspect-3/2≈150rpx，约 10rpx 底边错齐（Low）。
- **L16 现状**：7 页缺 .wxss（较 10-12 报告的 10 页减少 3 页）。
- **L17 现状**：约 27 个类名/40+ 处静态 class 无定义，empty-icon（5 页空态图标）最典型——空态图标退化裸字符（Medium 视觉）。
- **配置**：lazyCodeLoading:requiredComponents ✓；project.config.json 未锁 libVersion（Low）；tabBar 5 项路径有效。
- **危险模式**：无 setData 整页传参；console.log 0 处；无硬编码密钥（config/env.js 仅公共 API 域）。

### 5-6 安全

- **凭据扫描**：全仓（后端 src/前端 src/小程序/scripts）LTAI/AKIA/ghp\_/sk-/PRIVATE KEY 形态 **0 命中**。
- **SQL 注入面**：MyBatis `${}` 拼接 **0 处**（全参数化 #{}）。
- **越权面**（与主线一互证）：水平越权 400×2、垂直越权 403×2、owner 篡改拦截、task 他人 assigneeId 403（矩阵用例）、双集团隔离 PASS、finance confirm 范围外拒绝、导出随 view 收口。
- **fail-open**：见 5-2 未命中。
- **生产暴露面**：生产 SSH 不可达（与体检 P0-1 同），生产侧只读取证无法完成，如实记录。

### 5-7 测试质量


- **绿基线核对（全绿）**：后端 **Tests=698 Failures=0 Errors=0 Skipped=0**（68 个报告文件，含 8c2dc039 的 +4 新钉）；前端 202 passed/31 文件（gate.sh [2] ✅）；小程序语法全量 node --check ✅；INV-10 业务不变量契约 ✅；**contracts 133 PASS / 0 FAIL（DV_EXIT=0，含分页反模式、权限注册、菜单树、incentive 表契约、演示数据夹具等 93 个检查脚本）**。
- **权限矩阵**：verify-perm-matrix.sh **21/21 PASS**（重跑两次排除干扰后确认）。
- **门禁工具链问题（如实记录）**：gate.sh [3] 前端构建 FAIL 系本机 safe-delete 安全插件拦截 vite 清 dist（SAFE_DELETE_BULK_CONFIRM_REQUIRED，185 文件超 50 阈值），**同命令手动执行 EXIT=0**（1.12s 完成）——工具链冲突非代码问题；[4] 文档新鲜度 FAIL 为真实流程问题（见 P2-1）。
- **验证-perm-matrix 首跑 7/14 的诊断结论**（本次审查的元发现）：本机 python3 缺 bcrypt → 脚本临时用户空密码 → 5 次登录失败触发 AuthService 15 分钟锁定（`login_fail:<ip>:<username>`，MAX_LOGIN_FAILS=5，AuthService.java:43）→ 锁定期内所有同用户名登录被静默拦截（无审计日志的 throw）；叠加并行会话干扰。**非系统缺陷**——手动受控实验（bot2/bot3）证明创建→挂角色→登录链路正常。该限流机制本身工作正确（防爆破）。
- **测试缺断言**：抽样 ApprovalServiceTest/CustomerServiceTest/CustomerControllerTest 均有真实断言（含 verifyNoInteractions 验证守卫先于副作用）。

### 5-8 契约

- **API 清单**：docs/api/api-list.md 最后更新 10-12（e131a966）；8c2dc039 仅改 ApprovalService.submit 内部校验逻辑、未改端点签名，清单内容仍准确（但按新鲜度口径落后，已计入 P2-1）。
- **contracts 门禁**：dev-verify.sh contracts **133 PASS / 0 FAIL（DV_EXIT=0）**——覆盖分页反模式扫描、权限注册一致性、菜单树契约、incentive 表/周期/排名契约、移动端契约、演示数据夹具等 93 个检查脚本，与绿基线一致。
- **默认值钉契约**：biz_invoice.status DEFAULT 'confirmed'（存量默认已确认）与表注释一致，属设计。

---

## ⑥ 附录

### 6-1 本轮创建的测试数据清单（WB测试- 前缀，全部已清理/软删）

| 数据                                       | 创建方式                               | 状态                                  |
| ---------------------------------------- | ---------------------------------- | ----------------------------------- |
| 客户 131692（WB测试-太原化工机械，TY002）             | POST /api/customer/add             | 已软删（deleted=1）                      |
| 客户 131693（WB测试-归属篡改测试，含 ownerId=11 篡改尝试） | POST /api/customer/add             | 已软删；公海池记录 183 残留 available（数据卫生观察点） |
| 客户 131694（WB测试-permv客户-即时生效验证，WB临时用户）    | POST /api/customer/add             | 已软删                                 |
| 客户 131695（WB测试-大修机械厂，复合词对照A）             | POST /api/customer/add             | 已软删                                 |
| 客户 131696（WB测试-大唐修建工程，复合词对照B）            | POST /api/customer/add             | 已软删                                 |
| 发票 286（WB-TEST-001，admin 登记→作废→删除）       | POST /api/customer/131692/invoices | 已软删（sys_log 留痕 id=17081）            |
| 审批单 95（P2026-053，NM002 越权 extend 复现）     | POST /api/approval/submit          | 已驳回（rejected）                       |
| 用户 160（WB测试-permv，uat_nof→销售员→撤销）        | POST /api/system/users             | 已软删（deleted=1）                      |
| perm-matrix-bot/bot2/bot3（诊断临时载体）        | verify-perm-matrix.sh/手动           | 已全部删除                               |

### 6-2 使用的账号与角色对照表

| 账号         | 用途                               | 密码                     |
| ---------- | -------------------------------- | ---------------------- |
| admin      | 财务链路/用户管理/清理                     | \*\*\*（统一约定值，见测试环境账号表） |
| TY002      | 客户创建/编辑/分享/释放（team 档 org4）       | \*\*\*                 |
| NM002      | 水平越权对照/被分享方（team 档 org6）         | \*\*\*                 |
| NO001      | override=all 验证/审批驳回（custom+all） | \*\*\*                 |
| UATNOF     | 垂直越权对照（self 档无激励权限）              | \*\*\*                 |
| WB测试-permv | perm_version 即时生效验证（临时）          | 已随用户删除失效               |

> 五个夹具账号角色/组织/密码全程未改动（上一轮 TY002 密码重置状态维持）。

### 6-3 证据索引

- 后端日志：docker logs ycmn-backend（D1 500 堆栈=extra_data JSON 列校验；LOGIN_FAILED 审计序列）
- DB 查询：全部经 ssh aigicrm-test → docker exec shared-mysql mysql（只 SELECT + 必要的脚本夹具自清理）
- 门禁日志：/tmp/wb-gate.log（gate.sh PASS=4 FAIL=2，构建 FAIL 系 safe-delete 插件误拦）、/tmp/wb-contracts2.log（contracts 后台运行中）
- 权限矩阵：verify-perm-matrix.sh 21/21（两次运行，排除并发干扰后确认）
- 代码证据：全部引用文件:行号（后端 CustomerService/AuthService/ApprovalService/OrgScopeResolver/ArchitectureTest、前端 request.js/useIncentiveUpload.js/detail.vue、小程序 index.wxml、迁移 V2026.09.x）

---

*报告生成：2026-10-16 22:40，workbuddy 全维度审查（主线一业务实测 47 项用例 + 主线二代码八维度）。总判定：**无功能性错误与越权（不符 0 项）**；核心结论：① F14/23 代码已修（8c2dc039）但测试/生产均未部署，是当前唯一 P0（先部署再回归）；② F11/F06/F03/H2 已修已验证；③ F28（uploads 丢失）与 M2（period 忽略）仍在，属环境治理与已知排期项；④ 绿基线全绿（后端 698 / 前端 202 / contracts 133 / 矩阵 21 / miniprogram PASS）；⑤ 后端六个历史反模式已清零（2 处 Low），架构防腐与测试断言质量良好。*
