# AIGICRM System Functional Blueprint

Date: 2026-09-06

## 1. Document Positioning

This is the software-engineering collection document for AIGICRM functions. It combines product functions, menu hierarchy, page behavior, frontend entry points, backend APIs, data origin, data processing, statistics logic, roles, positions, permissions, data scopes, operation types, workflow order, and release risk.

Important boundary:

- Source-code verified: PC routes, frontend API wrappers, backend controllers/services/entities, mobile pages added in this iteration, and existing docs.
- Historical production evidence: `docs/permission/permission-dict.md` was generated on 2026-08-15 from production `sys_menu + sys_role_menu`.
- Pending production confirmation: live `sys_menu`, `sys_role`, `sys_role_menu`, `sys_position`, `sys_user_position`, `role_perm_scope`, `sys_role_org`, and `sys_user_org`.
- Therefore this document is a V1 engineering blueprint, not yet a V2 live-production configuration export.

## 2. System-Wide Engineering Principles

| Principle | Required Behavior |
| --- | --- |
| One identity source | PC and mobile use the same backend account, password, token, user id, role, position, group, permission and data-scope source. |
| One backend source of truth | All business data comes from the backend APIs and database. Mobile must not keep independent business data. |
| One permission source | `sys_menu.permission` + role binding + backend `@PreAuthorize` + service-layer guards are the source of truth. |
| One data-scope source | Data scope is computed server-side from role, position, user override, org tree, group, ownership, sharing and ACL rules. |
| Frontend is UX, not security | PC/mobile can hide buttons for usability, but the backend must reject unauthorized calls. |
| High-risk operations PC-first | Imports, exports, role/permission config, sea config, file ACL mutation and bulk operations should stay PC-first unless explicitly released. Destructive mobile deletes require explicit row/detail actions and existing backend permission checks. |
| Mobile operational scope | Mobile supports field use with shared backend identity and scope: read, search, preview/download, upload where granted, mark-read, approve/reject, customer follow-up/contact/finance/indicator updates, public-sea claim/edit/delete where backend row grants allow, and statistics/ranking views. |
| Audit sensitive mutations | Approval, customer release, permission changes, file ACL, imports, deletes and password changes need audit evidence. |

## 3. Menu Hierarchy

### 3.1 PC Menu Tree

| Level 1 | Level 2 | Level 3 / Page | Route | Permission | Page Source |
| --- | --- | --- | --- | --- | --- |
| Auth | Login | Login page | `/login` | Public | `aigicrm-ui/src/views/login/index.vue` |
| 工作台 | 工作台 | Overview dashboard | `/dashboard` | `dashboard:view` | `views/dashboard/index.vue` |
| 客户管理 | 客户列表 | List/search/filter/table/export/release | `/customer` | `customer:view` | `views/customer/list.vue` |
| 客户管理 | 新增客户 | Create customer form | `/customer/add` | `customer:add` | `views/customer/add.vue` |
| 客户管理 | 编辑客户 | Edit customer form | `/customer/edit/:id` | `customer:edit` | `views/customer/add.vue` |
| 客户管理 | 客户详情 | Detail tabs: info/logs/finance/indicator/incentive | `/customer/detail/:id` | `customer:view` | `views/customer/detail.vue` |
| 文件管理 | 文件管理 | Folder/file table/upload/share/ACL | `/file` | `file:view` | `views/file/index.vue` |
| 四项激励 | 四项激励 | Record upload/category/record table | `/incentive` | `incentive:view` | `views/incentive/index.vue` |
| 四项激励 | 激励统计 | Category/dept/person statistics | `/incentive/stats` | `incentive:view` | `views/incentive/stats.vue` |
| 四项激励 | 激励查询 | Customer grouped incentive records/read state | `/incentive/search` | `incentive:view` | `views/incentive/search.vue` |
| 提醒管理 | 提醒管理 | Expiring/customer reminder list/process | `/reminder` | `reminder:view` | `views/reminder/index.vue` |
| 公海管理 | 公海池 | Sea customer list/statistics/filter/import/claim/edit/delete | `/sea` | `sea:view` | `views/sea/index.vue` |
| 公海管理 | 捞取记录 | My claim approval records | `/sea/records` | `sea:view` | `views/sea/records.vue` |
| 审批管理 | 审批管理 | Approval tabs/statistics/approve/reject | `/approval` | `approval:view` | `views/approval/index.vue` |
| 统计分析 | 统计分析 | Analysis/team activity/trends | `/statistics` | `statistics:view` | `views/statistics/index.vue` |
| 学分管理 | 学分录入 | Score entry grid | `/score/entry` | `score:entry` | `views/score/entry.vue` |
| 学分管理 | 学分排行 | Score ranking/stats | `/score/ranking` | `score:view` | `views/score/ranking.vue` |
| 核心客户六项指标 | 统计总览 | Indicator overview | `/indicator` | `statistics:view` | `views/indicator/index.vue` |
| 系统管理 | 人员管理 | User CRUD/password/role/org/position binding | `/system/user` | `system:user:view` | `views/system/user.vue` |
| 系统管理 | 组织架构 | Department tree/admins | `/system/org` | `system:dept:view` | `views/system/dept.vue` |
| 系统管理 | 角色管理 | Role CRUD/menu/org/data-scope config | `/system/role` | `system:role:view` | `views/system/role.vue` |
| 系统管理 | 岗位管理 | Position CRUD/rule/user-position binding | `/system/position` | `system:position:view` | `views/system/position.vue` |
| 系统管理 | 系统设置 | Basic settings/dicts/categories/approval/sea/permission scopes | `/system/config` | `system:config:view` | `views/system/config.vue` |
| 系统管理 | 日志管理 | System operation logs | `/system/log` | `system:log:view` | `views/system/log.vue` |
| 系统管理 | 权限规则 | Permission rule CRUD | `/system/rule` | `system:rule:view` | `views/system/rule.vue` |
| 系统管理 | 集团管理 | Group CRUD | `/system/group` | `system:group:view` | `views/system/group.vue` |

### 3.2 Mobile Menu Tree

| Tab/Page | Route | Permission | Function State | Notes |
| --- | --- | --- | --- | --- |
| Login | `pages/login/index` | Public | Implemented | Account/password login, current-user profile, group switch and password recovery are implemented. |
| Home | `pages/home/index` | Authenticated | Implemented | Shows profile and quick entries. |
| Customer | `pages/customer/index` | `customer:view` | Implemented read-focused | Same backend customer list/detail/counts. |
| File | `pages/files/index` | `file:view` | Implemented operations | Unified file center; legacy `pages/file/index` redirects here. Preview/download/upload/share/delete/folder management and ACL read are implemented; ACL mutation stays PC-only. |
| Incentive | `pages/incentive/index` | `incentive:view` | Implemented query/upload/stats | Record query, upload/edit/delete, customer score, ranking, read-state and read stats are implemented; category config stays PC-only. |
| Legacy incentive alias | `pages/cases/index`, `pages/cases/detail/index` | `incentive:view` | Compatibility alias | Historical links redirect to 四项激励 workbench/detail; they must not reintroduce a separate case concept or independent data source. |
| Approval | `pages/approval/index` | `approval:view` | Implemented approve/reject | Action buttons gated by approve/reject permissions. |
| Public Sea | `pages/sea/index` | `sea:view` | Implemented claim/edit/delete | Not a tab to stay under WeChat tab limit; list/stats/pool selector, claim submission, permitted edit route and permitted row delete are implemented; import/config stay PC-only. |

## 4. Cross-System Data Model

| Domain | Main Tables/Entities | Data Start | Data Processing | Data Output |
| --- | --- | --- | --- | --- |
| Account/Auth | `sys_user`, `sys_role`, `sys_user_role`, `sys_menu`, `sys_role_menu`, `sys_group` | Admin creates users/roles; user logs in | Password verification, token creation, permission aggregation, current group selection | JWT token, `/api/auth/info` profile, menus, permissions |
| Org/Position | `sys_dept`, `sys_position`, `sys_user_position`, `sys_org_admin` | Admin configures org tree and positions | Group boundary, org subtree expansion, position and org admin assignment | Data-scope context and personnel management |
| Data Scope | `sys_role.data_scope`, `sys_role_org`, `sys_user.data_scope_override`, `sys_user_org`, `role_perm_scope` | Role/user/position scope config | Effective scope = USER > POSITION > ROLE > role default > self fallback | Service-layer SQL filters and mutation checks |
| Customer | `biz_customer`, `biz_customer_log`, `biz_contact`, `biz_invoice`, `biz_payment`, `biz_customer_indicator`, `biz_customer_share` | Manual create/import/customer release/updates | Customer ownership, protection period, invoices/payments, indicator scoring, sharing, access checks | Customer list/detail/stats/export |
| File | `biz_file`, `biz_file_folder`, `biz_file_share`, `biz_folder_acl` | Upload/create folder/share | Folder ACL, owner/shared visibility, download token/path validation | File list, preview, download, storage stats |
| Incentive | `biz_incentive_category`, `biz_incentive_record`, `biz_incentive_read`, `biz_incentive_grant` | Upload record/category config | Score calculation, customer grouping, read-state per user, special grants | Incentive list/query/stats/ranking/read stats |
| Reminder | `biz_reminder`, customer protection data | Scheduled task or manual ensure | Expiring/overdue detection, pending count, process status | Reminder list/cards |
| Public Sea | `biz_sea_pool`, `biz_sea_config`, `biz_approval` | Customer release, expiration task, sea import | Visible-pool filtering, status tracking, claim approval flow | Sea list/stats/claim records |
| Approval | `biz_approval`, `biz_approval_node`, `biz_approval_flow`, `biz_approval_config` | Submit approval from customer/sea/workflow | Node resolver, applicant/approver scope, approve/reject transitions | Approval list/statistics/status updates/audit |
| Score | `biz_score_item`, `biz_score_entry`, `biz_score_participant` | Admin configures items; authorized users enter scores | Period grid, score aggregation, ranking | Score entry grid/ranking/stats/export |
| Statistics | Customer/file/incentive/score/sea/reminder/log data | Business modules write data | Aggregation by date/week/team/org/module | Dashboard, charts, analysis, team activity |
| System Logs | `sys_log` | Controller/service aspects and sensitive operations | Record module/action/target/operator/time/IP | Audit list and counts |

## 5. Roles, Positions, Permissions And Data Scope

### 5.1 Role Responsibility

| Role | Verified Evidence | Can Do | Should Not Do By Default | Recommended Scope |
| --- | --- | --- | --- | --- |
| 系统管理员 | `admin`, role id `1` in migrations | Full platform config, all menus, emergency repair, role/permission/config/data recovery | None at system level, but operational use should be minimized | `all` |
| 行政 | role id `9` in permission dict | Back office operations, customer support, import/export if granted, approval support, selected system config | Should not get unrestricted sales ownership, full group admin, or security config unless needed | `custom`, `dept`, or `office` |
| 销售总监 | role id `10` in migrations | Sales management, broad customer/sea/incentive/statistics, approvals, selected config | Should not manage system foundation unless explicitly delegated | `region`, `office`, or configured `all` |
| 销售员 | role id `11` in migrations | Own customer work, incentive upload/query, reminders, public sea visibility/claim if granted | Should not delete broadly, export broadly, edit others by default, or manage config | `self`, optionally `team` for team lead |
| 组织管理员 | `system:org:admin` authority path | Scoped personnel/org operations in delegated org | Should not become full system admin automatically | `org_and_children` or `custom` |

### 5.2 Position Responsibility

| Position | Purpose | Typical Role Pairing | Scope Effect |
| --- | --- | --- | --- |
| 总部总经理 | Company-wide business view | Sales Director/Admin | `all` or `org_and_children` |
| 大区经理 | Region business management | Sales Director/Sales Manager | `region` |
| 办事处经理 | Office operations | Sales Manager | `office` |
| 部门经理 | Team/dept management | Sales Manager | `dept` or `team` |
| 销售人员 | Frontline sales | Salesperson | `self` |
| 行政/运营 | Back office | Back Office/Admin Staff | `custom`, `dept`, or `office` |
| 财务/结算 | Invoice/payment work | Finance role if created | `custom` |
| 审计/只读 | Audit visibility | Auditor role if created | `custom` or `all` read-only |

### 5.3 Scope Evaluation Order

| Priority | Source | Meaning |
| --- | --- | --- |
| 1 | `role_perm_scope` user-level config | Specific user override for module/action |
| 2 | `role_perm_scope` position-level config | Scope added by one or more positions |
| 3 | `role_perm_scope` role-level config | Role default for module/action |
| 4 | `sys_user.data_scope_override` / role `data_scope` | General data scope |
| 5 | Fallback | `view` uses default scope; mutation falls back to `self` |

### 5.4 Scope Codes

| Code | Meaning | Example |
| --- | --- | --- |
| `all` | All data in current tenant/group | System admin, top leader |
| `custom` | Explicit org list | Back office supports two offices |
| `region` | Region subtree | Regional director |
| `office` | Office subtree | Office manager |
| `dept` | Department subtree | Department manager |
| `dept_and_children` | Legacy dept subtree | Compatibility |
| `org` | Organization node | Org-anchored checks |
| `org_and_children` | Organization subtree | Org admin |
| `team` | Manager/subordinate chain | Sales team lead |
| `self` | Own records only | Salesperson mutation |

## 6. Module Specifications

### 6.1 Login And Account

| Item | Detail |
| --- | --- |
| Business goal | Let PC and mobile share the same account, token, role, permissions and group context. |
| Data origin | `sys_user`, role/user/group binding tables. |
| Main APIs | `POST /api/auth/login`, `GET /api/auth/info`, `POST /api/auth/logout`, `POST /api/auth/switch-group`, forgot-password APIs. |
| Page functions | Login, remember account, forgot password, reset password, change password, logout, group switch. |
| Click behavior | Login validates credentials and stores token; `/info` loads user/menus/permissions; 401 clears token and redirects to login. |
| Permissions | Login public; `/info/logout/switch-group` require valid user/session semantics. |
| Data scope | Current user only; switch-group limited to allowed groups. |
| Operation types | Login/session read/session mutation/password mutation. |
| Mobile status | Login, `/info`, group switch, forgot-password and password reset are implemented. WeChat binding has a reserved status page only; OAuth/openid binding is not connected. |

### 6.2 Dashboard

| Item | Detail |
| --- | --- |
| Business goal | Show current user's business overview and quick access. |
| Data origin | Customer, file, reminder, approval, sea, statistics services. |
| Main APIs | `/api/statistics/dashboard`, `/daily-stats`, `/weekly-stats`, `/indicator-stats`, `/team-activities`, reminder pending APIs. |
| Page functions | Statistic cards, weekly charts, pending reminders, team activities, pending approvals, quick entries. |
| Click behavior | Cards/quick entries navigate to target menu; approval buttons approve/reject when user has action permission. |
| Permissions | Route `dashboard:view`; some APIs allow `statistics:view` or `dashboard:view`. |
| Data scope | User/org/role/group scope; team activity should stay within visible scope. |
| Operation types | Read/statistics, approval status mutation from embedded widget. |
| Mobile status | Home profile/cards/quick entries and dashboard summary cards are implemented; mobile dashboard contract now verifies frontend permission gates, backend `statistics:view`/`dashboard:view` authorization and scope-aware dashboard counts. Live multi-account fixture QA remains release-level enhancement. |

### 6.3 Customer Management

| Item | Detail |
| --- | --- |
| Business goal | Manage customer lifecycle from creation, ownership, protection, follow-up, finance, indicator scoring, sharing, release to public sea. |
| Data origin | Manual entry, Excel import, customer release, invoice/payment entries, indicator entries, incentive links. |
| Main APIs | `/api/customer/list`, `/{id}`, `/add`, `PUT /{id}`, `DELETE /{id}`, `/logs`, `/statistics`, `/invoices`, `/payments`, `/indicator`, `/relationship`, `/shares`, `/release`, `/users/simple`, `/import`, `/export`, `/check-name`. |
| Level 2/3 pages | Customer list, add customer, edit customer, customer detail tabs: info, logs, finance, indicator, incentive. |
| List functions | Search, industry filter, org/dept filter, status filter, custom columns, pagination, export, release. |
| Add/edit functions | Basic info, business info, address, org owner, contacts, duplicate-name check, save/cancel. |
| Detail functions | View header/status/score, edit relationship score, logs, contacts, invoices, payments, indicators, incentive records, shares, release. |
| Button behavior | Add opens form; row click opens detail; save validates required fields; release opens reason dialog and moves customer into public sea if backend allows. |
| Permissions | `customer:view`, `customer:add`, `customer:edit`, `customer:delete`, `customer:import`, `customer:export`, `customer:share`. |
| Data scope | Ownership, first owner, department/office/region/team, customer share, group, public sea access when status is `sea`. |
| Operation types | Read, create, update, delete, import, export, share mutation, status mutation. |
| Business workflow | Create/import customer -> assign org/owner -> protect period calculated -> follow-up/invoice/payment/indicator updates -> reminder task detects expiry -> release or expire into sea -> claim approval may create new ownership. |
| Statistics logic | Counts by total, month new, protected, expiring, overdue, industry, status. Protection uses registration/invoice/protection days. |
| Mobile status | Read list/detail/counts, create/edit/delete, share/release, logs, contacts create/edit/delete, finance read/create/delete, relationship score and six-indicator save are implemented with scoped permissions. Import/export stay PC-first. |

### 6.4 File Management

| Item | Detail |
| --- | --- |
| Business goal | Manage business files with folders, sharing, ACL and authorized download/preview. |
| Data origin | File upload, folder creation, file replacement, sharing records, folder ACL. |
| Main APIs | `/api/file/folders`, `/list`, `/upload`, `/folders`, `/folders/{id}`, `/storage`, `/download-token`, `/download/{id}`, `/download/**`, `/download/public/**`, `/{id}/share`, `/shared-to-me`, `/shared-to-me/count`, `/file/folders/{id}/acl`, `/download`, `/permissions`. |
| Page functions | Folder tree, file table, upload dialog, folder create/rename/delete, file delete/replace/share, ACL dialog, storage usage, shared-to-me. |
| Button behavior | Upload validates folder and file; share selects users; ACL changes folder visibility; download requires token and backend ACL; preview uses authorized URL. |
| Permissions | `file:view`, plus dictionary codes `file:upload`, `file:edit`, `file:delete`, `file:share`, `file:folder:manage`, `file:acl:manage`. Current controller often uses `file:view` and service ACL for mutations. |
| Data scope | File owner, folder owner, folder ACL, shared-to-me, public token, downloadable flag. |
| Operation types | Read, upload/create, update/replace, delete, share, ACL mutation, download. |
| Business workflow | Create folder -> set ACL/download flag -> upload file -> list/search -> preview/download -> share/replace/delete when authorized. |
| Statistics logic | Storage quota/used by user or tenant, shared count, file counts by folder/scope. |
| Mobile status | File list, preview/download, upload, share, replace, file delete, folder create/rename/delete, storage stats, shared-to-me and folder ACL read are implemented. ACL mutation and download-setting changes stay PC-first. |

### 6.5 Incentive

| Item | Detail |
| --- | --- |
| Business goal | Record four incentive categories, calculate scores, query customer cases, rank users/depts, and synchronize read state across PC/mobile. |
| Data origin | Incentive category config, uploaded records, customer links, uploaded files, read-state table, grant table. |
| Main APIs | `/api/incentive/categories`, `/records`, `/records/{id}`, `/records/recalculate`, `/score/customer/{customerId}`, `/scoreboard`, `/ranking`, `/customer-records`, `/records/{id}/read`, `/read-stats`, `/category-stats`, `/users`, `/permission/*`. |
| Pages | Incentive management, incentive stats, incentive search. |
| Page functions | Category tree, upload record, edit/delete record, record table, filters, score stats, customer grouped cards, sorting, NEW/unread badge, read statistics. |
| Button behavior | Upload opens dialog; save validates category/customer/files; mark-read writes one read row per current user/record; stats refresh after read. |
| Permissions | `incentive:view`, `incentive:add`, `incentive:edit`, `incentive:delete`, `incentive:read-stats`, historical `incentive:view-all/view-shared/manage` now noted as deprecated/grant-table related. |
| Data scope | Uploader ownership, customer visibility, shared records, special user grants, admin override, read-state per user. |
| Operation types | Read, create/upload, update, delete, batch recalculation, mark-read, statistics. |
| Business workflow | Configure category -> upload incentive record -> compute score -> expose in customer detail/search/stats -> user reads record -> read-state syncs PC/mobile -> authorized manager checks read stats. |
| Statistics logic | Score by category, customer total score, ranking by score, grouped customer record count, read/unread counts per user. |
| Mobile status | Category tree, record list, upload, own/permitted edit/delete, customer score, scoreboard/ranking, category stats, user selector, customer-record list, file preview/download, mark-read and read-stats are implemented. Category config stays PC-first. |

### 6.6 Reminder

| Item | Detail |
| --- | --- |
| Business goal | Track customer protection expiry and pending work. |
| Data origin | Customer registration/invoice/protection data, scheduled expiry task, manual ensure endpoint, reminder processing. |
| Main APIs | `/api/reminder/expiring-list`, `/list`, `/pending-count`, `/ensure-expiring`, `/{id}/process`. |
| Page functions | Expiring list, reminder list, pending count, process reminder. |
| Button behavior | Ensure creates missing reminders; process marks reminder handled; list refreshes count/status. |
| Permissions | `reminder:view`. |
| Data scope | Current user/customer visibility scope. |
| Operation types | Read, batch ensure/create, status mutation. |
| Business workflow | Customer approaches expiry -> scheduled task creates reminder -> dashboard/reminder page shows it -> user processes -> reminder closed. |
| Statistics logic | Pending count, expiring soon, overdue days. |
| Mobile status | Expiring list, reminder list, pending count and reminder process are implemented. Batch ensure-expiring remains PC/backend-first. |

### 6.7 Public Sea

| Item | Detail |
| --- | --- |
| Business goal | Hold released/expired/imported customers for visible users to claim through configured rules and approval. |
| Data origin | Customer release, protection expiry task, sea import, sea pool config. |
| Main APIs | `/api/sea/list`, `/statistics`, `/pool/{id}`, `/import`, `/configs`, `/configs/visible`, `/org-tree`, `/configs/{id}`, `/claim-records`. |
| Pages | Public sea list, claim records, sea config under system settings. |
| Page functions | Sea stats, pool selector, status tabs, dept/industry/reason/keyword filters, import, claim, edit, delete, claim record search. |
| Button behavior | Import opens Excel upload; claim submits approval/status change; edit opens customer edit; delete removes available pool item if service allows; config writes visible users/orgs and pool permissions. |
| Permissions | `sea:view`, `sea:claim`, `sea:import`, `sea:config:view`, `sea:config:edit`, `sea:pool:delete`; service also returns `canClaim/canEdit/canDelete`. |
| Data scope | Enabled visible sea pools, pool-level view/edit/claim/delete/import permission, dept subtree filters, customer keyword/industry filters. |
| Operation types | Read, import, claim/status mutation, edit, delete, config mutation. |
| Business workflow | Customer enters sea -> pool visibility decides who sees it -> user submits claim -> approval flow locks pending item -> approve completes claim and assigns owner -> claim records show outcome. |
| Statistics logic | Available total, new-this-month, pending approval, claimed-this-month. |
| Mobile status | List/stats/pool selector, claim approval submission, claim records, permitted customer edit route and permitted row delete are implemented. Import and sea config remain PC-only. |

### 6.8 Approval

| Item | Detail |
| --- | --- |
| Business goal | Provide unified workflow approval for claim, transfer, release, extension and other business actions. |
| Data origin | Submit approval API, approval config, approval nodes, linked customer/sea action. |
| Main APIs | `/api/approval/list`, `/submit`, `/{id}/approve`, `/{id}/reject`, `/statistics`, `/api/approval/config/*`. |
| Page functions | Approval tabs, type/dept/search filters, statistics cards, approve/reject comment, approval config. |
| Button behavior | Submit creates approval and nodes; approve/reject changes current node/status; final approval triggers downstream action such as sea claim completion. |
| Permissions | `approval:view`, `approval:approve`, `approval:reject`, config uses `system:config:view/edit`. |
| Data scope | Current applicant, current approver node, department filter if available, approval type. |
| Operation types | Read, create approval, status mutation, config mutation. |
| Business workflow | Business action requests approval -> approver list receives pending item -> approver approves/rejects -> service updates target business object -> audit/log records action. |
| Statistics logic | Pending/approved/rejected counts by current user scope. |
| Mobile status | List/statistics/detail, approve/reject and business-flow approval submission from customer release / sea claim are implemented. Approval config remains PC-only. |

### 6.9 Statistics And Core Indicators

| Item | Detail |
| --- | --- |
| Business goal | Provide aggregate business insight across customer, file, incentive, sea, score and team activity data. |
| Data origin | Business module tables and logs. |
| Main APIs | `/api/statistics/dashboard`, `/daily-stats`, `/weekly-stats`, `/indicator-stats`, `/analysis`, `/team-activities`. |
| Page functions | Dashboard cards/charts, statistics analysis, team activities, core customer six-indicator overview. |
| Button behavior | Filters update aggregation; chart/table rows navigate to detail where supported. |
| Permissions | `statistics:view` and/or `dashboard:view`. |
| Data scope | Role/org/group/customer scope depending metric. |
| Operation types | Read/statistics. |
| Business workflow | Modules write operational data -> statistics service aggregates by period/org/person/customer -> dashboard/statistics pages render charts/tables. |
| Statistics logic | Daily/weekly counts, team activity feed, customer indicator ranking, dashboard summary; `todayExpiring` counts customers with `expireDate = today` after account and optional department/office filters. |
| Mobile status | Core-indicator read-only overview, dashboard cards and broader read-only statistics are implemented; mobile statistics contract now verifies page permission gates, backend authorization and scope-aware daily/weekly/team-activity queries. Live role-scope fixture QA remains release-level enhancement. |

#### Statistics formula and source contract

The authoritative implementation is `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java`:

| Output | Formula / filter | Source fields and scope |
| --- | --- | --- |
| Dashboard cards | `myCustomers`, `inProtection`, `expiring`, `expired`, `todayExpiring`, and `monthNew` delegate to scope-aware counters; `todayExpiring` filters `expireDate = today`; `daysLeft = expireDate - today` for the five-item expiring preview. | `biz_customer` customer status/protection/expire/create fields through `CustomerService`; sea and approval summaries through their services; current account scope. |
| Daily files | For each day from the first day of the current month through today: `createdAt >= dayStart AND createdAt < nextDayStart`. | `biz_file.created_at`, filtered by `applyFileScope`. Customer daily counts use `CustomerService.dailyNewCustomers()`. |
| Weekly files | Seven-day half-open segments beginning on the first day of the current month; the last segment ends at today. | `biz_file.created_at`, filtered by `applyFileScope`; customer weekly counts use `CustomerService.weeklyNewCustomers()`. |
| Core customer | `score = indicator02 + indicator03 + indicator04 + indicator05 + indicator06`; core when `score >= 12 AND SUM(invoice.amount) >= 100 AND SUM(payment.payment_amount) >= 100`. | `biz_customer_indicator`, `biz_invoice.amount`, `biz_payment.payment_amount`, active customers; ranking aggregates by `biz_customer.owner_id`. |
| Annual analysis | Monthly customer/file counts use `[monthStart, nextMonthStart)`; optional office or department subtree filter is applied before the account data-scope filter. Summary cards, including `todayExpiring`, use the same department/office and account scope. | `biz_customer.created_at`, `biz_customer.office_id`, `biz_customer.expire_date`, `biz_customer.status/deleted`; `biz_file.created_at`; industry distribution uses nonblank `biz_customer.industry`. |

Source references: dashboard `StatisticsQueryService.java:120-152`, daily/weekly `:155-213`, indicator aggregation `:216-403`, annual analysis `:404-569`, team activities `:570-652`. Threshold amounts use the service's `CORE_THRESHOLD = 100` convention.

### 6.10 Score

| Item | Detail |
| --- | --- |
| Business goal | Configure score items, enter scores by period and rank participants. |
| Data origin | Score item config, score entries, participants, users/depts. |
| Main APIs | `/api/score/items`, `/items/{id}`, `/grid`, `/entries/batch`, `/ranking`, `/ranking/stats`, `/export/grid`. |
| Pages | Score entry, score ranking. |
| Page functions | Item config, period grid, batch entry, ranking list, ranking stats, export. |
| Button behavior | Save entries posts batch; ranking filters refresh; export downloads current grid/ranking. |
| Permissions | `score:manage`, `score:entry`, `score:view`, historical/menu `score:ranking`. |
| Data scope | Managed users/depts/role scope; ranking may be broader depending configuration. |
| Operation types | Config create/update/delete, batch mutation, read/statistics/export. |
| Business workflow | Configure items -> assign participants -> enter period scores -> aggregate ranking/stats -> export if authorized. |
| Statistics logic | Sum score entries by participant/period/item; derive ranking and summary stats. |
| Mobile status | Personal/dept score ranking, period filter, stats and rule explanation are implemented as read-only. Score entry, item config and export remain PC-only. |

### 6.11 System Management

| Item | Detail |
| --- | --- |
| Business goal | Configure platform foundation: users, org, roles, positions, menus, data scopes, dicts, approval, sea, logs and groups. |
| Data origin | Admin UI forms and migration seed data. |
| Main APIs | `/api/system/users`, `/roles`, `/depts`, `/positions`, `/groups`, `/menus`, `/configs`, `/dict`, `/logs`, `/rules`, `/perm-scopes`, sea config, approval config. |
| Pages | User, department, role, position, config, log, rule, group. |
| Page functions | CRUD, role-menu binding, role-org binding, user role/org/position binding, password reset, dict config, basic config, category config, sea config, approval config, permission scope manager. |
| Button behavior | Add/edit opens dialog; save validates and writes backend; delete confirms; permission changes evict cache and bump user permission versions. |
| Permissions | `system:user:*`, `system:dept:*`, `system:role:*`, `system:position:*`, `system:group:*`, `system:config:*`, `system:log:view`, `system:rule:*`, `system:menu:*`, `system:org:admin`. |
| Data scope | Admin all scope, org-admin delegated scope, group boundary, custom orgs. |
| Operation types | Read, create, update, delete, config mutation, permission mutation, credential mutation, audit read. |
| Business workflow | Create group/org -> create roles/positions -> bind menus/rules/scopes -> create users -> bind role/position/org -> user logs in -> permissions and scope drive PC/mobile behavior. |
| Statistics logic | Log counts, user/role/org list counts, config-driven business calculations. |
| Mobile status | No first-release system management. |

## 7. End-To-End Business Processes

### 7.1 User/Permission Setup

| Step | Actor | Action | Data Written/Read | Result |
| --- | --- | --- | --- | --- |
| 1 | System admin | Create group/org/dept | `sys_group`, `sys_dept` | Organization baseline exists |
| 2 | System admin | Create roles and bind menu permissions | `sys_role`, `sys_role_menu`, `sys_menu` | Functional permissions exist |
| 3 | System admin | Create positions and bind rules | `sys_position`, `sys_position_rule` | Position layer exists |
| 4 | System admin | Configure role/position/user scope | `role_perm_scope`, `sys_role_org`, `sys_user_org` | Data range is defined |
| 5 | System admin/org admin | Create user and bind role/position/org | `sys_user`, `sys_user_role`, `sys_user_position` | User can log in |
| 6 | User | Login from PC/mobile | `/api/auth/login`, `/api/auth/info` | Same permissions returned to both clients |

### 7.2 Customer Lifecycle

| Step | Trigger | Backend Data | User Reaction |
| --- | --- | --- | --- |
| 1 | Add/import customer | Insert/update `biz_customer` | List refresh shows customer if in scope |
| 2 | Follow-up/finance/indicator | Write logs, invoices, payments, indicators | Detail tabs update scores/status |
| 3 | Protection period changes | Registration/invoice/config dates | Dashboard/reminder statistics change |
| 4 | Expiry task or manual release | Insert/update reminder or sea pool | Customer may leave normal list and enter sea |
| 5 | Claim request | Insert approval and sea pending status | Approver sees pending approval |
| 6 | Approval final pass | Update approval, sea pool, customer owner/status | PC/mobile lists converge after refresh |

### 7.3 Incentive Read-State Sync

| Step | Trigger | Backend Data | PC/Mobile Reaction |
| --- | --- | --- | --- |
| 1 | Record uploaded | `biz_incentive_record` | Appears in PC and mobile query if visible |
| 2 | User opens/marks record | `biz_incentive_read` upsert | Unread badge clears for same user |
| 3 | Manager opens read stats | Aggregates read/unread by user | Only visible with `incentive:read-stats` |

### 7.4 File Preview

| Step | Trigger | Backend Data | PC/Mobile Reaction |
| --- | --- | --- | --- |
| 1 | File uploaded | `biz_file`, storage path | File list refreshes |
| 2 | User requests preview/download | Download token and ACL checked | Authorized file opens; forbidden file shows error |
| 3 | File shared | `biz_file_share` | Recipient sees shared-to-me list |

### 7.5 Approval Cooperation

| Step | Trigger | Backend Data | Reaction |
| --- | --- | --- | --- |
| 1 | Business action needs approval | `biz_approval`, nodes | Approver pending count/list changes |
| 2 | Approver approves/rejects | Node and approval status update | Applicant and approver lists refresh |
| 3 | Final state reached | Target customer/sea/action updated | Business module state changes |
| 4 | Audit | `sys_log` or service log | Admin can review action history |

## 8. Click Behavior Checklist By Page

| Page | Click/Action | Expected Reaction | Backend Guard |
| --- | --- | --- | --- |
| Login | Login button | Store token, load `/info`, enter dashboard/home | Credential check and lockout |
| Login | Forgot password | 3-step lookup/code/reset | Code/token validation |
| Layout/menu | Click menu | Route if permission exists; otherwise hidden/redirect | API still enforces permission |
| Customer list | Search/filter | Reset page and reload list | `customer:view` + data scope |
| Customer list | Export | Download current scoped result | `customer:export` |
| Customer list | Release | Confirm reason, submit release | `customer:edit` + customer access + sea config |
| Customer form | Save | Validate required fields, create/update, navigate detail/list | `customer:add/edit` |
| Customer detail | Add log/contact/invoice/payment | Save and reload tab | `customer:edit` |
| Customer detail | Share | Select users and grant visibility | `customer:edit/share` semantics |
| File page | Upload | Validate ACL and storage, refresh table | `file:view` + service ACL |
| File page | Share | Select users, create shares | Service ACL |
| File page | ACL | Save role/folder ACL | `file:acl:manage` |
| Incentive | Upload | Validate customer/category/files, compute score | `incentive:add` |
| Incentive search | Open/read | Mark current user's read state | `incentive:view` |
| Incentive search | Read stats | Show user read/unread stats | `incentive:read-stats` |
| Reminder | Process | Mark reminder handled | `reminder:view` + reminder scope |
| Sea | Claim | Submit claim approval and lock pending | Pool claim permission |
| Sea | Import | Upload Excel into sea pool | `sea:import` + pool import permission |
| Sea | Delete | Confirm and delete available item | Pool delete permission |
| Approval | Approve/reject | Change status and update downstream business object | `approval:approve/reject` + approver node |
| Score entry | Save batch | Persist period entries | `score:entry` |
| System user | Reset password | New credential state, force reset if configured | `system:user:edit` or org admin |
| System role | Save role menus/scopes | Cache evicted; user permission version bumped | `system:role:*`, `system:config:*` |

## 9. Permission And Role Matrix Summary

| Module | View Permission | Mutation Permissions | Suggested Mobile Phase |
| --- | --- | --- | --- |
| Dashboard | `dashboard:view` | Approval widget uses approval permissions | Mobile dashboard/statistics cards implemented |
| Customer | `customer:view` | `customer:add/edit/delete/import/export/share`, finance uses `finance:view` | Mobile create/edit/delete/share/release/finance/contact/indicator implemented; import/export remain PC-only |
| File | `file:view` | `file:upload/edit/delete/share/folder:manage/acl:manage` | Mobile preview/download/upload/share/delete/folder management/ACL read implemented; ACL mutation remains PC-only |
| Incentive | `incentive:view` | `incentive:add/edit/delete/read-stats` | Mobile query/upload/edit/delete/read-state/stats implemented; category config remains PC-only |
| Reminder | `reminder:view` | Process uses same permission/service scope | Mobile list and process implemented; batch ensure remains PC/backend-first |
| Public Sea | `sea:view` | `sea:claim/import/config:* / pool delete` | Mobile list/claim/edit/delete/claim-records implemented; import/config remain PC-only |
| Approval | `approval:view` | `approval:approve/reject` | Mobile list/detail/approve/reject and business-flow submission implemented |
| Statistics | `statistics:view` | None | Mobile dashboard/statistics/indicator/ranking views implemented |
| Score | `score:view` | `score:entry/manage`, export | Mobile personal/dept ranking and stats implemented; entry/config/export remain PC-only |
| System | `system:*:view` | `system:*:add/edit/delete`, config, rule, org-admin | PC only |

## 10. Statistics And Calculation Inventory

| Area | Inputs | Calculation | Output |
| --- | --- | --- | --- |
| Customer total | `biz_customer` visible rows | Count by data scope and filters | Dashboard/list cards |
| Month new customers | Customer created date | Count where created in current month | Dashboard/statistics |
| Protection status | Registration date, invoice date, configured protection days | Normal/warning/overdue based on expiry window | Customer list/detail/reminder |
| Industry distribution | Customer industry dictionary | Group count by industry | Customer side panel/statistics |
| File storage | File size and quota | Used/quota/share counts | File page storage card |
| Incentive score | Incentive record category/score | Sum by customer/user/category/period | Search, stats, ranking |
| Incentive read stats | Visible incentive records and `biz_incentive_read` | Read/unread count by user | Read-stats panel |
| Sea stats | `biz_sea_pool` status/date/visible pools | Available, new month, pending approval, claimed month | Sea cards |
| Approval stats | `biz_approval` by status/current user | Pending/approved/rejected totals | Approval cards |
| Reminder stats | Reminders and customer expiry | Pending count, expiring, overdue | Dashboard/reminder |
| Score ranking | Score entries by period/participant | Sum and rank | Score ranking |
| Team activity | Logs/business events | Recent scoped events | Dashboard feed |

## 11. Data Configuration Inventory

| Configuration | Owner Page | Backend/Table | Affects |
| --- | --- | --- | --- |
| System configs | 系统设置 | `sys_config` | Logo, protection days, numbering rules, global switches |
| Dictionaries | 系统设置/字典 | `sys_dict_type`, `sys_dict_data` | Industry, customer type, reasons, statuses |
| Menus/permissions | 角色/菜单管理 | `sys_menu`, `sys_role_menu` | PC/mobile page and button visibility |
| Roles | 角色管理 | `sys_role` | Permission package and default data scope |
| Role orgs | 角色管理 | `sys_role_org` | Custom data scope |
| Positions | 岗位管理 | `sys_position`, `sys_user_position` | One-user-many-position scope/rule carrier |
| Permission rules | 权限规则 | `sys_permission_rule`, `sys_role_rule`, `sys_position_rule` | Field/action/org/time restrictions |
| Permission scopes | 系统设置/权限范围 | `role_perm_scope` | Module/action-level row scope |
| Sea pools | 公海配置 | `biz_sea_config` | Visible pools and pool-level actions |
| Approval config | 审批配置 | `biz_approval_config`, flow/node config | Approval routing and approver resolution |
| Incentive categories | 四项激励/系统设置 | `biz_incentive_category` | Score type and category tree |
| Score items | 学分管理 | `biz_score_item` | Score grid/ranking dimensions |

## 12. Completeness Gaps Before V2

| Gap | Why It Matters | How To Close |
| --- | --- | --- |
| Live production role/menu export missing | Current doc uses source and older generated permission dict | Query live `sys_menu`, `sys_role`, `sys_role_menu` and regenerate permission dict |
| Live position/user binding missing | Cannot confirm actual one-person-many-position setup | Query `sys_position`, `sys_user_position` |
| Live scope config missing | Cannot confirm exact data range by role/position/user | Query `role_perm_scope`, `sys_role_org`, `sys_user_org` |
| Some controller permissions rely on service-layer checks | Method annotations alone do not describe final authorization | Keep service tests and document each service guard |
| Field-level permissions not fully mapped | Finance/contact/file fields may need visibility rules | Finance `finance:view`, contact `contact:view`, and file folder ACL/download checks are implemented; continue mapping any new file fields |
| Mobile high-risk boundary needs live QA | Mobile now has selected destructive actions: customer delete, contact delete, finance delete, file delete, incentive record delete and public-sea row delete. Each must stay behind explicit permissions, row/detail confirmation and backend service guards; imports/exports, system admin, file ACL mutation, incentive category config, sea import/config and score entry remain PC-only. | Run WeChat DevTools/manual QA with granted and denied accounts, then compare PC/mobile behavior against the same backend data. |
| Statistics SQL details not fully line-by-line documented | Algorithms need exact query definitions for finance/business sign-off | Extract `StatisticsQueryService`, `CustomerService`, `IncentiveService`, `ScoreService` method-level formulas |

## 13. Recommended Next Engineering Steps

1. Export live production configuration into a generated `function-permission-production-matrix.md`.
2. Add method-level backend tests for every endpoint that has no direct `@PreAuthorize` but relies on service-layer authorization.
3. Run WeChat DevTools/manual QA for login, customer, file, incentive, approval, public sea, statistics and ranking with both granted and denied accounts.
4. Compare PC/mobile behavior against the same backend data for implemented mobile mutations: customer/contact/finance delete, file delete, incentive record delete and public-sea row delete.
5. Keep imports/exports, system management, file ACL mutation, incentive category config, public-sea import/config, score entry/config/export, role/position/scope config and batch operations PC-only unless separately accepted with release-level QA evidence.
6. Maintain the landed DevTools automator precheck for mobile PC parity (`npm run verify:mobile-parity` in `scripts/miniprogram`), with `npm run verify:incentive-attachments` available for focused incentive upload/list/detail/timeline attachment retesting. Real-account submission, true download/save behavior and same-backend PC/mobile reconciliation still require live DevTools or phone QA.
