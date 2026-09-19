# 分片F 审查发现：前端UI + 工程化流程 + 系统技能

> 审查对象：`02-迭代源代码/aigicrm-ui/`（Vue 3 + Vite + Element Plus + Pinia，152个源文件）
> 审查模式：绝对只读，未修改任何代码或配置
> 构建验证：`npm run build` 成功（715ms，1997 modules）；`npm test` 175个测试全部通过（29个测试文件）
> SonarQube 基线：项目 key `Eastdongjun_AigiCRM`，OPEN 问题为 0

---

## 问题清单

| 序号 | 严重级别 | 文件路径 | 问题描述 | 对应技能 | 建议方向 |
|------|----------|----------|----------|----------|----------|
| F-01 | Critical | 全局（275处 @click，零 aria） | **无障碍完全缺失**：全代码库零 aria-* 属性、零 role、零 tabindex、零 alt 文本。大量 div/span/a 上的 @click 无键盘导航支持（如 `<div class="icon-btn" @click="...">`、`<span class="tab-item" @click="...">`、`<div class="quick-item" @click="...">`、`<a href="javascript:void(0)" @click="...">`）。违反 WCAG 2.1.1（键盘可访问）和 4.1.2（名称/角色/值）。 | 无障碍审计、interaction-design-expert | 将可点击 div/span 替换为 `<button>` 或添加 `@keydown.enter`/`@keydown.space` + `tabindex="0"` + `role="button"`；为图标按钮添加 `aria-label`；为图片添加 `alt` |
| F-02 | High | `src/App.vue`、全局 | **无错误边界**：全项目无 `onErrorCaptured`/`errorCaptured`/ErrorBoundary。Vue 组件渲染错误会导致整个白屏，用户无法恢复。对比 main.js 已有路由 chunk 加载失败自愈（router.onError → reload），但组件级运行时错误无任何兜底。 | interaction-design-expert、高级前端 | 在 App.vue 顶层添加 `onErrorCaptured` 钩子，捕获组件渲染错误并显示友好的错误恢复界面（而非白屏） |
| F-03 | High | `02-迭代源代码/aigicrm-ui/` 根目录 | **无 ESLint/Prettier 配置**：项目无 `.eslintrc.*`、无 `.prettierrc`、无 `.editorconfig`、无 `.nvmrc`。package.json scripts 仅有 dev/build/preview/test，缺 lint/format。152个源文件无任何自动化代码风格约束。CI 也不跑 lint。 | 工程化总控、sonarqube适配器 | 安装 ESLint（Vue 3 推荐配置）+ Prettier，添加 `lint`/`format` script，CI 增加 lint 步骤 |
| F-04 | High | `src/layout/index.vue:470,482` 等21处 | **`transition: all` 滥用**：21处 CSS 使用 `transition: all .2s`，违反动效审查第7条（GPU-only properties）。`all` 会动画化非 GPU 属性（如 width/height/padding），导致 layout thrashing 和掉帧。 | 动效审查 | 将 `transition: all` 改为明确属性，如 `transition: background-color .2s, color .2s, transform .2s, box-shadow .2s` |
| F-05 | High | 全局 | **无 `prefers-reduced-motion` 支持**：全代码库零 `prefers-reduced-motion` 媒体查询。动效审查第8条要求尊重系统减少动效偏好。 | 动效审查、无障碍审计 | 在 style.css 添加 `@media (prefers-reduced-motion: reduce)` 全局降级动画 |
| F-06 | High | 全局 | **无暗色模式支持**：无 `prefers-color-scheme` 媒体查询，无 CSS 变量主题切换机制。所有颜色硬编码为浅色主题（`#fff`/`#f5f7fa`/`#1f2329`）。 | 高级前端、interaction-design-expert | 如需暗色模式，应将颜色抽取为 CSS 变量并通过 `prefers-color-scheme` 或类切换实现 |
| F-07 | High | 全局（仅3处） | **响应式设计几乎缺失**：全代码库仅3个 `@media` 查询（statistics/index.vue 3个 + UploadDialog.vue 2个）。侧边栏固定200px，表格无横向滚动处理假设，主要面向桌面端。无移动端适配策略。 | interaction-design-expert、高级前端 | 明确目标设备为桌面端（企业内部系统可接受），但应在布局层添加响应式断点，至少保证小屏可用 |
| F-08 | Medium | `index.html:2` | **`lang="en"` 错误**：应用为全中文界面，但 index.html 设置 `lang="en"`，影响屏幕阅读器发音和浏览器翻译提示。 | 无障碍审计 | 改为 `lang="zh-CN"` |
| F-09 | Medium | `src/views/login/index.vue:32` | **`href="javascript:void(0)"` 反模式**：忘记密码链接使用 `href="javascript:void(0)"` + `@click`，无 href 语义、键盘焦点可达性差。 | 无障碍审计、interaction-design-expert | 改为 `<button type="button">` 或 `<a href="#" @click.prevent="...">` |
| F-10 | Medium | 全局（40处空 catch） | **空 catch 块泛滥**：40处 `catch {}` 或 `catch (e) {}` 静默吞错误。部分是合理的后台数据加载兜底（由 request.js 全局通知），但部分如 `dashboard/index.vue` 多处连续空 catch 掩盖了真实加载失败，用户看到空数据不知原因。 | doubao-coding-diagnose-and-fix-bugs、doubao-coding-review-code | 区分合理静默（后台预加载）和应上报错误的场景；对关键数据加载失败设置空状态/错误提示而非静默 |
| F-11 | Medium | `src/composables/useCustomerList.js:209,320`、`src/composables/useFilePage.js:27`、`src/api/customer.js:128`、`src/views/system/BasicSettings.vue:101` | **直接使用 `localStorage.getItem('token')`**：已有 `utils/token.js` 封装 `getToken()`，但5处直接读 localStorage 绕过封装，破坏 token 管理一致性（如 clearToken 重置下载 token 模块态时这些调用点不感知）。 | doubao-coding-analyze-codebase、高级前端 | 统一替换为 `getToken()` 调用 |
| F-12 | Medium | `02-迭代源代码/aigicrm-ui/README.md` | **README 为 Vite 默认模板**：未包含项目说明、启动指南、架构概述、命令列表。新开发者/AI Agent 无法从 README 了解项目。 | 文档与决策记录、技术文档 | 按 AIGICRM 实际项目重写 README：技术栈、目录结构、启动命令、环境要求 |
| F-13 | Medium | 项目根目录 | **无 CHANGELOG.md**：无变更日志。git commit message 规范良好（conventional commits），但无聚合的版本变更记录。 | 变更日志生成 | 建立 CHANGELOG.md，从 git log 聚合版本变更 |
| F-14 | Medium | `package.json:27` | **TypeScript 为 devDependency 但项目为纯 JS**：`typescript: ^5.9.3` 在 devDependencies，但无 tsconfig.json、无 .ts 文件。TS 仅作为 IDE 类型提示存在，无实际编译检查。 | 工程化总控 | 要么移除 TS 依赖，要么正式引入 TS 渐进迁移 |
| F-15 | Medium | `src/composables/useUserList.js:192,238` | **生产代码残留 console.error**：2处 `console.error` 在生产代码中。虽为错误日志（非 debug），但无统一日志级别管理。 | sonarqube适配器、高级前端 | 考虑封装统一 logger 或依赖 request.js 全局错误通知 |
| F-16 | Medium | `src/layout/index.vue:359` | **`transition: width 0.3s` 动画 layout 属性**：侧边栏折叠/展开动画 width，触发 layout（非 GPU）。动效审查第7条禁止动画 width/height。 | 动效审查 | 改用 `transform: translateX()` 或 `flex-basis` + `overflow` 方案，或接受为桌面端低频操作 |
| F-17 | Low | `src/App.vue:27` | **`setTimeout` 关闭路由 loading**：用 `setTimeout(50ms)` 等待组件挂载后关闭 loading，脆弱——如果路由 chunk 加载超过 50ms，loading 会提前消失。 | doubao-coding-diagnose-and-fix-bugs | 用 `router.afterEach` + `nextTick()` 替代固定 setTimeout |
| F-18 | Low | `src/composables/useCustomerList.js:173` | **单次加载500条客户数据**：`size: 500` 一次拉取全部客户在前端做筛选/分页。数据量增长后内存和渲染压力大。 | doubao-coding-optimize-performance、高级前端 | 改为服务端分页+服务端筛选，或引入虚拟滚动 |
| F-19 | Low | 全局 | **无 jsconfig.json**：虽有 `@` 别名配置在 vite.config.js，但无 jsconfig.json 供 IDE 识别路径别名，编辑器跳转和自动补全不完整。 | 工程化总控 | 添加 jsconfig.json 配置 `@/*: src/*` |
| F-20 | Low | `src/store/user.js:101` | **`isAdmin()` 字符串硬编码**：`this.roles.includes('admin')` 和 `this.username === 'admin'` 硬编码管理员判断，应与后端角色常量对齐。 | doubao-coding-review-code | 与后端确认管理员角色标识，抽取为常量 |

---

## 前端架构与质量评估

### 整体架构
项目采用 **Vue 3 Composition API + `<script setup>`** + **Pinia** + **Vue Router 4** + **Element Plus** 技术栈，架构清晰：

- **目录组织**：`api/`（API 层，10个模块）、`composables/`（共享逻辑，10个）、`store/`（Pinia，1个 user store）、`router/`（路由+权限守卫）、`utils/`（工具函数，20+个）、`views/`（页面，按业务模块分组）、`layout/`（全局布局）、`components/`（全局组件，仅 FilePreview）
- **状态管理**：Pinia 单一 user store，管理用户信息、权限、菜单、角标、系统配置。采用 Options API 风格的 Pinia store，30s/60s 布局级缓存设计合理。
- **路由设计**：全部路由懒加载（`() => import(...)`），路由守卫在 `beforeEach` 中检查 token 有效性和页面级权限。有旧路由自愈（router.onError → reload）和 chunk 预加载（requestIdleCallback 预热常用页面）。
- **API 层封装**：axios 实例 `request.js` 统一处理 token 注入、401 会话过期、错误中文化、分组通知。设计成熟，有静默模式（`silent` config）、badge 接口特殊处理。
- **Composables 抽取**：`useCustomerList`/`useCustomerForm` 等使用 provide/inject 模式实现页面级共享状态，子组件通过 Symbol key 注入，避免 prop drilling。设计合理。

### 优势
1. **路由懒加载 + chunk 预加载**：所有页面路由均懒加载，main.js 在 `requestIdleCallback` 中预加载6个常用页面 chunk，首屏只加载必要代码。
2. **代码分割**：vite.config.js 配置了 `vue-core` chunk 组，Element Plus 按需引入（unplugin-auto-import + unplugin-vue-components），主包从 1.1MB 降到合理水平。
3. **安全细节**：下载专用短时 token（scope=download, 1h）替代主 JWT 进 URL，避免主 token 泄漏到访问日志；JWT 前端本地解析判断过期。
4. **错误处理**：request.js 响应拦截器统一中文化错误提示，401 会话过期只处理一次（防抖），badge 接口 403 静默（预期行为）。
5. **单元测试覆盖**：29个测试文件、175个测试用例，覆盖 utils/composables/store/router/api 层。测试质量较高（包含边界条件、错误路径）。

### 待改进
1. **无障碍**：完全缺失，是最大短板。Element Plus 组件本身有一定无障碍支持，但自定义交互元素（div/span @click）完全无键盘支持。
2. **错误边界**：无组件级错误兜底。
3. **空 catch 块**：40处静默吞错误，需区分合理静默与应上报错误。
4. **Token 管理不一致**：5处直接读 localStorage 而非用封装的 getToken()。

---

## 工程化成熟度评估

### 构建配置
- **Vite 8 + rolldown**：使用最新构建工具，配置了 Element Plus 按需引入、`@` 别名、dev proxy（/api → localhost:8080）。
- **代码分割**：`vue-core` chunk 组分离 Vue/Pinia/Router，Element Plus 组件随路由懒加载拆分。
- **构建产物**：dist/assets 总计 2.2MB，最大 chunk 为 `add-BNnuJigN.js`（161KB / gzip 34KB），`vue-core` 120KB（gzip 46KB）。路由级 chunk 拆分合理。

### 缺失的工程化工具
| 工具 | 状态 | 影响 |
|------|------|------|
| ESLint | **未安装/未配置** | 代码风格无自动化约束 |
| Prettier | **未安装/未配置** | 格式化不一致 |
| .editorconfig | **缺失** | 编辑器缩进/换行不一致 |
| .nvmrc | **缺失** | Node 版本不锁定 |
| jsconfig.json | **缺失** | IDE 路径别名识别不完整 |
| .env / .env.example | **缺失** | 环境变量硬编码在 vite.config.js proxy target |
| lint script | **缺失** | package.json 无 lint/format script |
| format script | **缺失** | 无格式化命令 |

### CI/CD
- **GitHub Actions**（`.github/workflows/ci.yml`）：配置完整，含 backend（Maven verify + ArchUnit）、frontend（npm ci + build + test）、SonarCloud、diff-check（空白检查+密钥粗检）、quality-gate 汇总门禁。
- **Docker**：多阶段构建（node:22-alpine build → nginx:alpine serve），nginx.conf.template 使用 envsubst 注入 BACKEND_HOST。
- **注意**：CI frontend job 只跑 build + test，不跑 lint（因为未配置）。

### SonarQube
- 项目 key `Eastdongjun_AigiCRM`，当前 OPEN 问题为 0。
- sonar-project.properties 正确排除了 node_modules/target/dist 等。
- 前端源码纳入扫描范围。

---

## 文档与流程评估

### 文档丰富度
项目文档体系较为完善，位于 `02-迭代源代码/docs/`：
- **ADR**：`docs/permission/adr-20260813-data-permission-enforcement-route.md`、`docs/permission/adr-20260909-customer-file-association.md` — 有架构决策记录
- **技术文档**：`system-functional-blueprint.md`、`mobile-pc-unified-architecture.md`、`unified-feature-manifest.md`、`high-risk-action-matrix.md`
- **API 文档**：`api/api-list.md`、`api/README.md`
- **运维文档**：`ops/AIGICRM-运维手册.md`、`ops/incident.md`、`ops/dod.md`
- **技术债**：`TECH_DEBT.md`（19个待重构 Controller，有明确模板和检查命令）
- **部署文档**：`生产部署规范.md`、`deployment/config-matrix.md`、`local-integration-runbook.md`
- **权限文档**：`permission/design.md`、`permission-dict.md`、`function-permission-matrix.md`

### 待改进
1. **前端 README 为空壳**：`aigicrm-ui/README.md` 仍是 Vite 模板默认文案，未反映 AIGICRM 项目实际情况。
2. **无 CHANGELOG**：git commit message 规范（conventional commits），但无聚合版本变更日志。
3. **ADR 数量少**：仅2个 ADR，对于一个有路由权限、多集团、数据范围等复杂决策的项目，建议补充关键技术决策的 ADR（如 Element Plus 按需引入策略、下载 token 设计、30s/60s 缓存策略）。

---

## 系统技能应用发现

### doubao-coding-analyze-codebase（代码库结构化分析）
按 SCOPE → ROUTE → EFFECT → BREAK → SHIP 方法论分析：
- **入口与调用链**：`main.js` → Pinia + Router 挂载 → 路由守卫 `beforeEach`（token 检查 → fetchUserInfo → 权限检查）→ 布局 `layout/index.vue` → 子路由页面。
- **数据流**：API 层（request.js 拦截器注入 Bearer token）→ composables（ref/reactive 状态管理）→ views（模板渲染）→ provide/inject 跨组件共享。
- **关键旁路**：`main.js` 中的 `requestIdleCallback` 预加载和版本检测（version.txt）在应用启动时异步执行，不阻塞首屏。
- **效果主张验证**：路由懒加载和预加载机制已由构建产物验证（路由级 chunk 拆分确认）；token 过期检查由 `isTokenValid()` 前端本地 JWT 解析实现，不依赖网络。

### doubao-coding-review-code（代码审查方法论）
按 defect-first 原则审查：
- **错误传播**：request.js 响应拦截器统一处理错误，各处 `catch (e) {}` 依赖全局通知。但 dashboard/index.vue 中多处连续空 catch（269-289行），审批操作失败后 `ElMessage.success('已通过')` 仍会执行——实际上第289行 `try { await apiApprove(id, {}); ElMessage.success('已通过'); ... } catch {}` 中，如果 apiApprove 失败，catch 静默吞掉，用户看不到失败提示（但 request.js 拦截器会弹错误通知，所以实际有兜底）。
- **状态一致性**：`store/user.js` 的 30s 缓存 TTL 在切换集团后通过 `fetchUserInfo(true)` force 刷新，逻辑正确。
- **并发问题**：`request.js` 的 `sessionExpiredHandled` 标记防止多个在途请求同时 401 时重复跳转，设计合理。

### doubao-coding-optimize-performance（性能优化，只读分析）
按"无测量不结论"原则，仅记录可观察的机制假设：
- **正面**：路由懒加载、Element Plus 按需引入、vue-core chunk 分离、requestIdleCallback 预加载、下载 token 内存缓存。
- **假设**：`useCustomerList.js` 单次 `size: 500` 拉取全部数据在前端筛选/分页，数据量增长后可能成为瓶颈（当前规模未验证）。`transition: all` 21处可能导致 layout thrashing（未 profile 验证）。
- **未授权**：无实际 profile/benchmark，不给出具体收益数字。

### doubao-coding-develop-unit-tests（测试缺口识别）
- **已有覆盖**：29个测试文件/175个测试，覆盖 utils（18个 spec）、composables（5个 spec）、store（1个 spec）、router（2个 spec）、api（1个 spec）、views（4个 spec）。
- **缺口**：
  - 多数 views 组件无组件测试（仅4个 views 有 spec）
  - 无 E2E 测试（Playwright 在 devDependencies 但未配置测试文件）
  - 无视觉/无障碍测试（无 axe-core 集成）
  - 测试主要集中在纯函数 utils，组件交互逻辑覆盖不足

### doubao-coding-diagnose-and-fix-bugs（Bug 模式诊断，只读）
诊断常见 bug 模式：
- **空 catch 吞错误**：40处 `catch {}`，部分掩盖了真实加载失败
- **路由 loading 竞态**：App.vue 用 `setTimeout(50ms)` 关闭 loading，如果 chunk 加载慢于50ms会提前消失
- **Token 存储不一致**：5处直接 localStorage 读 token 绕过封装
- **`javascript:void(0)`**：登录页忘记密码链接

### verifier-hub（产物验证）
dist/ 目录存在（本次构建产物），结构完整：index.html + assets/（166个文件）+ favicon。构建无警告。未运行 verifier 因为无特定产物文件需校验。

### artifact-preview（前端产物预览）
dist/ 为 SPA 构建产物（HTML+JS+CSS），需 dev server 运行才能渲染，不适合直接 artifact-preview（HTML 产物不需要自检——按 skill 说明）。

---

## 技能覆盖清单

### 已调用技能（已 Read SKILL.md 并应用方法论）
- **高级前端**：应用组件设计、bundle 分析、状态管理、可访问性检查框架
- **interaction-design-expert**：应用状态覆盖（empty/loading/error）、交互模式审查、状态机思维
- **无障碍审计**：应用 WCAG 2.2 检查清单（语义化HTML、键盘导航、ARIA、对比度）
- **动效审查**：应用 Emil Kowalski 十条标准（transition: all、GPU-only properties、prefers-reduced-motion）
- **工程化总控**：应用12阶段工程化评估框架
- **文档与决策记录**：应用 ADR/READMES/CHANGELOG 评估标准
- **sonarqube适配器**：验证 SonarQube 基线（0 OPEN issues），应用规则手册检查前端代码
- **doubao-coding-analyze-codebase**：应用 SCOPE→ROUTE→EFFECT→BREAK→SHIP 方法论
- **doubao-coding-review-code**：应用 defect-first 变更审查方法论
- **doubao-coding-optimize-performance**：应用"无测量不结论"原则，仅记录机制假设
- **doubao-coding-develop-unit-tests**：识别测试覆盖缺口
- **doubao-coding-diagnose-and-fix-bugs**：诊断常见 bug 模式（只读模式）
- **verifier-hub**：检查 dist 产物结构
- **artifact-preview**：评估适用性（SPA 产物不适用直接预览）

### 未找到/不适用而跳过的技能
- **react组合模式**：项目为 Vue 3，非 React，不适用
- **sonner提示库**：项目使用 Element Plus 的 ElMessage/ElNotification，非 React/sonner，不适用
- **苹果设计**：项目为 Web 端企业内部系统，非 iOS/macOS 原生应用，不适用
- **设计工程**：项目无独立设计系统文件/Figma token 管理，设计决策内联在 Vue 组件 style 中
- **任务分解/任务追踪/自动编码循环/增量实现/规格驱动开发/编写计划/执行计划/适应度函数**：这些是开发执行流程技能，本次为只读审查，不涉及开发任务分解
- **技术文档/变更日志生成**：已在文档评估章节应用其评估标准，未运行脚本
- **apple-hig**：非 Apple 平台

---

## 构建与测试验证记录

| 命令 | 结果 | 详情 |
|------|------|------|
| `npm run build` | ✅ 成功 | 715ms，1997 modules transformed，无警告 |
| `npm test` | ✅ 全部通过 | 29 test files，175 tests passed，3.22s |
| SonarQube OPEN issues | 0 | 项目 key `Eastdongjun_AigiCRM` |
| console.log 残留 | 2处 console.error | `useUserList.js:192,238`（错误日志，非 debug） |
| TODO/FIXME/HACK | 0处 | 无技术债标记 |
| v-html (XSS风险) | 0处 | 安全 |
| aria/role/alt/tabindex | 0处 | 完全缺失 |
| transition: all | 21处 | 动效性能问题 |
| @media 查询 | 3处文件 | 响应式设计不足 |
| prefers-reduced-motion | 0处 | 未支持 |
| prefers-color-scheme | 0处 | 无暗色模式 |
| 空 catch 块 | 40处 | 部分合理部分需改进 |
