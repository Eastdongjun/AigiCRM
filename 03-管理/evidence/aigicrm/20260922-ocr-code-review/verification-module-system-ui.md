# OCR 发现复核报告 — module-system（后端 7 条）+ ui（前端 14 条）

- 复核对象：`slices-live/findings-module-system.json`（7）、`slices-live/findings-ui.json`（14），共 21 条。
- 复核方式：逐条 read 真实源码 + template/script 双段；grep/glob 交叉核对调用方、守卫、配置、迁移；用 **前端构建产物 `aigicrm-ui/dist/assets/*.css`** 判定 scoped/global 与选择器实际形态；用 **Element Plus 源码** 判定 `class-name` 语义。
- 只读复核，未改动任何业务源码；除本报告外未新建文件。
- 权威基线对照：`02-迭代源代码/docs/security-audit/`（grep `AuthController|LoginRequest|parseUserAgent|switch-group` 仅命中 `01-api-permission-inventory.md` 的接口清单，**无**对本次后端 7 条的既有裁定，即非"已裁定项重复上报"）。
- 复核限制：未启动浏览器实测；CSS 类结论基于模板结构 + 编译产物（`dist`，2026-09-22 前构建），非运行时像素测量。前端 lint/type-check 按项目事实为 N/A，未计入裁定。

裁定口径：CONFIRMED=源码证据成立；PARTIAL=机理成立但与描述偏差（写明偏差）；FALSE=有反证；BY_DESIGN=有意设计；UNVERIFIABLE=证据不足。

---

## 一、后端（`module/system`、`module/auth`）7 条

| 序号 | 校准严重度 | 裁定 | 文件:行（仓库相对路径） | 一句话理由 + 代码证据 | 触发前提 | 修复建议 |
|---|---|---|---|---|---|---|
| S1 | low（原 high） | PARTIAL | `02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/auth/dto/LoginRequest.java:6-11` | 机理成立：`@Data` 无 `@ToString.Exclude`，`password` 会进 toString。**偏差：全仓无任何泄漏 sink** —— `LogAspect` 显式跳过登录接口（`config/LogAspect.java:50`、`:76`），且只反射白名单 getter/Map 键（`:152-166`），从不 toString 入参；`GlobalExceptionHandler` 只记异常消息/栈（`common/GlobalExceptionHandler.java:45-93/166-173`）；`logging.level.com.aigicrm=debug`（`application.yml:94-96`）下也无语句打印该 DTO | latent：仅当日后有人 `log.info("{}", request)` 才泄漏 | `password` 加 `@ToString.Exclude`（零成本加固） |
| S2 | low | PARTIAL | `.../module/auth/dto/LoginRequest.java:8-11` | 事实成立：仅 `@NotBlank`。**偏差：资源耗尽理由错误** —— BCrypt 只取前 72 字节且代价与入参长度无关；登录另有 IP+用户名/IP 双限流（`AuthService.java:42-43,77,85`）；`sys_user.username` 为 `varchar(50)`（`db/migration/V1__baseline.sql:1259`），超长用户名只会匹配失败返 401，不会写入 | 非安全前提；仅一致性/加固诉求 | 可选 `username @Size(max=64)`；密码上限按 `ForgotPasswordResetRequest.java:14` 口径补齐 |
| S3 | low | CONFIRMED | `.../module/auth/controller/AuthController.java:39-41` | `catch (Exception ignored)` 无任何日志：DB/映射故障导致登录审计长期缺失且零信号；违反 `02-迭代源代码/AGENTS.md`「禁止吞异常/空 catch」；同仓惯例至少 `log.debug`（`LogAspect.java:62-64`、`:92-94`，并在 `:167-169` 注明吞因） | 非 latent：构造上必然静默 | `log.warn("登录审计落库失败, username={}", request.getUsername(), e)` 并在类上加 `@Slf4j` |
| S4 | low（信息级） | CONFIRMED | `.../module/auth/controller/AuthController.java:18` | 事实成立且必要：类无 `@Slf4j`/Logger 字段，`grep log.` 在 AuthController 无命中，故 S3 的 `log.warn` 不加 `@Slf4j` 无法编译 | — | 采纳 S3 时同加 `@Slf4j`（本身非独立缺陷，属配套说明） |
| S5 | low（原 medium） | CONFIRMED | `.../module/auth/controller/AuthController.java:53-57` | 死分支成立：Android UA 含 `Linux`（先在 `:55` 命中）、iPhone UA 含 `Mac OS X`（先在 `:54` 命中），`:56`/`:57` 永不可达；影响仅 `sys_log.detail` 的 OS 文案（"Chrome / Linux"、"Safari / macOS"） | 非 latent：所有移动端登录日志均错标 | 移动端判断前置（Android → iPhone/iPad/iOS → Mac OS → Linux） |
| S6 | low（原 medium） | CONFIRMED | `.../module/auth/controller/AuthController.java:80-81` | `Long.parseLong(String.valueOf(gid))` 抛 `NumberFormatException` 时无专用处理器（`GlobalExceptionHandler` 只特判 `MethodArgumentTypeMismatchException` 等，`:100-105`），落入兜底 `Exception` → **HTTP 500 + supportId**（`:166-173`）；裸 `@RequestBody Map` 无 `@Valid` 确实绕过 Bean Validation。安全侧无虞：`AuthService.switchGroup` 先校验集团归属（`AuthService.java:163-165`）；前端只回传登录响应里的数值 `groupIds`（`layout/index.vue:88-89`、`store/user.js:93-99`） | latent：需手工构造非数字 `groupId`（需登录态） | try/catch 转 `BusinessException(400,"groupId 参数非法")` 或改校验 DTO |
| S7 | low | CONFIRMED | `.../module/auth/controller/AuthController.java:27` 与 `:36` | 同一请求内重复解析：`ip` 已在 `:27` 算出，`:36` 又调 `ClientIpUtils.getClientIp`；该函数对同一请求确定性（`common/util/ClientIpUtils.java:28-40` 仅读 remoteAddr/X-Real-IP），两值必等，与 `:26` 注释「限流与审计日志同源」的意图正好相悖 | 非 latent：每次登录多算一次 | `log.setIp(ip)` |

---

## 二、前端（`02-迭代源代码/aigicrm-ui/src`）14 条

| 序号 | 校准严重度 | 裁定 | 文件:行（仓库相对路径） | 一句话理由 + 代码证据 | 触发前提 | 修复建议 |
|---|---|---|---|---|---|---|
| U1 | low（原 high） | CONFIRMED | `02-迭代源代码/aigicrm-ui/src/views/customer/add-common.css:9` | 规则永不生效：`el-form` 直属子节点为 6 个 `div.section-block`（`IndicatorSection.vue:2` 为最后一个）+ `div.el-form-item.remark-field` + `div.form-actions`（`add.vue:18-30`），**最后一个 div 兄弟是 `.form-actions`**，故无 `.section-block` 命中 `:last-of-type`；`add-common.css` 为全局（`import './add-common.css'` 6 处，如 `BasicSection.vue:62`；编译产物 `dist/assets/add-*.css` 无 data-v），`add.vue` 无补偿规则 → IndicatorSection 保留 `border-bottom`，备注字段上方出现多余分隔线（纯视觉） | 非 latent：每次打开新增/编辑客户页 | 改 `.section-block:not(:has(+ .section-block))` |
| U2 | low | PARTIAL | `.../views/customer/add-common.css:50`（文件头 `:1-3`） | 前提成立：确为全局非 scoped（编译产物 `.full-width{grid-column:1/-1}` 无 data-v）。**偏差：目前零实际冲突** —— `grep -rn "full-width"` 仅 `BasicSection.vue:8,15`，`section-block` 仅该 6 个 Section，无第二处同名用法 | latent：后续任意页面复用同名类才冲突 | 命名空间化（`.cust-add-full`）或保留但删除文件头"页面私有"表述 |
| U3 | low | PARTIAL | `.../views/customer/add-common.css:48-49` | 事实成立：`.form-row.two/.three` 固定列宽、该文件无 media query。**偏差：项目并非无响应式实践**（`views/statistics/index.vue:373-382`、`views/incentive/search.vue:1005`、`views/incentive/UploadDialog.vue:235`），且客户新增页属 PC 端管理页（移动端由小程序承载） | latent：视口收窄（<约 1000px）才挤压 | 加 `@media (max-width:1100px){ .form-row.two,.form-row.three{grid-template-columns:1fr} }` |
| U4 | low | PARTIAL | `.../views/system/perm-common.css:197-204`（文件头 `:1`） | 前提成立：`import './perm-common.css'` 于 4 个 SFC（`CategoryManager.vue:181` 等），编译产物 `.action-btns{...}` 无 data-v，确为全局。**偏差：目前零冲突** —— `grep -rn "action-btns"` 仅 `CategoryManager.vue:60` | latent：他处复用同名类才碰撞 | 改名 `.perm-action-btns` 并同步 `CategoryManager.vue:60` |
| U5 | low（原 medium） | CONFIRMED | `.../views/system/perm-common.css:116-122` | 事实成立：`.cat-group-actions{opacity:0}` 仅由 `.cat-group-head:hover` 恢复，编译产物无 `:focus-within`/`@media (hover:none)`；其内是真实操作按钮（`CategoryManager.vue:26-30` 编辑/子项/删除），opacity:0 仍可点击但键盘焦点不可见、触摸端需盲点 | latent：键盘/无 hover 设备 | 补 `.cat-group-head:focus-within .cat-group-actions{opacity:1}` 与 `@media (hover:none){.cat-group-actions{opacity:1}}` |
| U6 | low | CONFIRMED | `.../views/system/perm-common.css:220` | 事实成立：`/* 公海池可见范围树 */` 后为空行，`:222` 才是 `/* 激励授权层级人员树 */` 及其规则，该注释下方零规则，属残留误导 | 非 latent | 删除该行 |
| U7 | low（作为死 CSS） | FALSE | `02-迭代源代码/aigicrm-ui/src/style.css:32-43` | 反证：`.pagination`/`.pagination-row` **全前端零消费者** —— 排除 `node_modules`/`dist` 后 `pagination-row` 仅命中 style.css 自身，`.vue` 中无 `class="…pagination…"` 字面量（`grep 'class="[^"]*pagination[^"]*"'` 0 命中），编译产物 `dist/assets/index-*.css` 原样保留该规则。故"粘底栏溢出白容器 6px/8px、圆角外露"的视觉后果不可能发生；真实问题只是引用旧 `.main-content` 20px 内边距的死规则 | 不适用（无命中元素） | 直接删除该规则；若日后要做粘底分页，按 `.page-shell` 的 `12px 14px` 重新取负值或用 CSS 变量 |
| U8 | low | PARTIAL | `.../src/style.css:77` | 魔法数成立，且有可量化偏差：实际 chrome = 60px 头部（`layout/index.vue:445`）+ main-content 上下 20px（`layout/index.vue:702-705，:704`）= **100px**，`calc(100vh - 116px)` 少算 16px → 短页面白容器填不满，底部留 16px 灰条。偏差：`100dvh`/移动端 URL 栏论点不适用（PC 管理端，全仓无 `dvh` 使用） | latent：内容不足一屏时可见 | 改 `calc(100vh - 100px)` 或抽 `--app-chrome-height` 变量 |
| U9 | low | CONFIRMED | `.../src/style.css:12-16` | 事实成立：`--primary-color/--sidebar-bg/--sidebar-active` 仅此 3 行定义；全仓（含 `dist`）grep 无 `var(--primary-color/--sidebar-bg/--sidebar-active)` 消费点，Element Plus 走自有 `--el-color-primary`，属死声明 | 非 latent | 删除或真正接入（如侧栏激活态） |
| U10 | low | PARTIAL | `.../src/style.css:1-5` | 技术点成立：`*` 不匹配伪元素（`box-sizing` 亦不继承），确有 1-2px 隐患。**偏差："破坏性"无受害证据** —— 全仓 `grep -rn "v-html"` = 0，无富文本/编辑器内容依赖默认外边距；且建议的枚举选择器会漏掉 `video/canvas/svg` 等仍受 `*` 覆盖的元素 | 非 latent（伪元素一节）；破坏性一节无前提 | 保留 `*` 重置，仅补 `*::before,*::after{box-sizing:border-box}` |
| U11 | low（原 medium） | PARTIAL | `.../views/incentive/rank-common.css:1-4` | 偏差（与 U12 同源）：该文件**实际是 scoped** —— 两个消费者均为 `<style scoped src="./rank-common.css">`（`RankTableView.vue:70`、`DeptRankTableView.vue:59`），编译产物为 `.rank-matrix.el-table[data-v-3957dda2]`，**不存在**"一旦非 scoped 就全局泄漏"的现状；成立的部分只是文件头注释「非 scoped 便于跨子组件复用」与实际用法自相矛盾（`.card`/`.table-header` 在 `StatsTableView.vue:103-104` 确实各自重复声明，但各自 scoped 互不影响） | latent：仅未来改成非 scoped 共享才泄漏 | 修正文件头注释为"scoped 使用"；若确要跨组件共享，先加 `.rank-` 前缀 |
| U12 | medium（原 high） | CONFIRMED | `.../views/incentive/rank-common.css:9-10`（另见 `:48-51`） | 死规则成立，**有编译产物直证**：`dist/assets/stats-*.css` 为 `.rank-matrix .el-table__row:hover>td[data-v-3957dda2]{background:#f5f9ff!important}` 与 `.rank-matrix .el-table__body td[data-v-3957dda2]{padding:5px 0}`；scoped 属性只加在最后一个复合选择器上，而 `td` 由 `el-table` 内部渲染、不带父组件 scope id（父级 scope id 只落在 `el-table` 根节点，见 `.rank-matrix.el-table[data-v-3957dda2]` 生效）→ 行 hover 高亮与 5px 单元格内边距整张矩阵表从未生效（纯视觉，不影响数据） | 非 latent：激励排行矩阵全程可见 | 包 `:deep()`：`.rank-matrix :deep(.el-table__body td)`、`.rank-matrix :deep(.el-table__row:hover > td)` |
| U13 | low（原 medium） | CONFIRMED | `.../views/incentive/rank-common.css:48-51` | 双重失效：①关系错 —— Element Plus 把 `column.className` 加在单元格自身（表头 `node_modules/element-plus/es/components/table/src/table-header/style.helper.mjs:41`，表体 `.../table-body/styles-helper.mjs:49`），而调用方是 `:class-name="'cat-group cat-group-'+(pi%4)"`（`RankTableView.vue:32`、`DeptRankTableView.vue:32`），故不存在"祖先 `.cat-group-N` 包住 `.el-table__header th`"；②scoped 同 U12，编译产物为 `.cat-group-0 .el-table__header th[data-v-3957dda2]` → 大类分组表头色条全不生效，表头仍由内联 `:header-cell-style` 的 `#f7f9fc` 呈现（`RankTableView.vue:12`）。纯视觉，不影响功能 | 非 latent | 用 `label-class-name` 落到 `th` 上，并改 `.rank-matrix :deep(.el-table__header th.cat-group-0)` |
| U14 | low | CONFIRMED | `.../views/incentive/rank-common.css:25-26` | 事实成立：同特异性重复声明，`:26` 后者胜出 → `:25` 的 `color:#94a3b8`、`font-weight:600` 是死声明，仅 `font-size:13px` 存活；`:26` 还以注释把行为钉在外部"基线 505 行"上，易随基线漂移而腐化 | 非 latent | 合并为一条 `.rank-num{color:#86909c;font-weight:500;font-size:13px}` 并删行号引用 |

---

## 三、统计

### 3.1 裁定计数（21 条）

| 裁定 | 后端(7) | 前端(14) | 合计 |
|---|---|---|---|
| CONFIRMED | 5（S3、S4、S5、S6、S7） | 7（U1、U5、U6、U9、U12、U13、U14） | 12 |
| PARTIAL | 2（S1、S2） | 6（U2、U3、U4、U8、U10、U11） | 8 |
| FALSE | 0 | 1（U7） | 1 |
| BY_DESIGN | 0 | 0 | 0 |
| UNVERIFIABLE | 0 | 0 | 0 |

结论：OCR 假阳性率 1/21（U7 描述的影响不可能发生）；另有 2 条"机理对、结论过重"（S1 的原 high、U11/U12 的原 high 之一）经反证下调为 latent/纯视觉。

### 3.2 校准严重度计数

| 严重度 | 后端 | 前端 | 合计 |
|---|---|---|---|
| critical | 0 | 0 | 0 |
| high | 0 | 0 | 0 |
| medium | 0 | 1（U12） | 1 |
| low | 7 | 13 | 20 |

- latent 条目：S1（需未来新增日志语句）、S2（仅一致性）、S6（需手工构造入参）、U2/U3/U4（需他处复用同名类）、U5（键盘/触摸端）、U8（内容不足一屏）、U11（需改为非 scoped）。
- 非 latent（当前即生效的缺陷）：S3、S4、S5、S7、U1、U6、U9、U12、U13、U14。
- 全部 21 条中无 critical/high；OCR 标为 high 的 3 条（S1、U1、U12）经复核分别下调为 low（latent 加固）、low（纯视觉）、medium（纯视觉），均不构成高 severity。

### 3.3 最值得优先修的 5 条

| 优先级 | 条目 | 理由 |
|---|---|---|
| 1 | U12 rank 矩阵深度选择器失效 | 唯一 medium：悬停高亮 + 单元格内边距整表失效，改 `:deep()` 一行即修，主页面可见 |
| 2 | U13 分组表头色条失效 | 与 U12 同文件同因，改 `label-class-name` + `:deep()` 一次修完，恢复"大类分组"设计意图 |
| 3 | U1 客户表单末段分隔线 | 每次新增/编辑客户页可见的多余分割线，改选择器一行 |
| 4 | S6 switch-group 非法参数返 500 | 唯一后端会产生错误状态码/错误日志的路径，补 400 语义或校验 DTO |
| 5 | S3 登录审计落库失败被静默吞 | 安全审计盲区且违反项目「禁止吞异常」规则，补一行 warn 即可 |

（紧随其后：U9 死变量、U6 残留注释、U14 重复规则、U5 键盘可达性、S5 移动端 OS 误标 —— 均为一两行即可清理的低风险项。）
