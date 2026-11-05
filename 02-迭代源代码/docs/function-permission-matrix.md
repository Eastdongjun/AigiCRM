# AIGICRM Function And Permission Matrix

Date: 2026-09-06

## Scope

This document lists the current PC and mobile functional surface by module, menu, button/action, backend API, permission code, data-scope boundary, and operation type.

Core rule: PC and mobile must share the same backend identity, permission source, data scope, audit path, and database. The two clients may differ only in page layout and interaction style.

## Permission Model

| Layer | Responsibility | Source |
| --- | --- | --- |
| Identity | Login, token, current user, current group | `/api/auth/login`, `/api/auth/info`, `/api/auth/switch-group` |
| Menu permission | Whether a page or menu can be entered | `sys_menu.permission`, role-menu binding, `/api/auth/info.permissions` |
| Button permission | Whether an operation button is visible | PC `userStore.hasPermission`, mobile `hasPermission` |
| Backend authorization | Final enforcement for every protected API | Spring Security `@PreAuthorize` and service-layer checks |
| Data scope | Which rows the user can see or mutate | Role data scope, org scope, group scope, customer ownership/share, sea-pool visibility |
| Audit | Operation logs for sensitive changes | `@AigicrmLog`, service logs, system log module |

## Role, Position, User Scope Model

| Concept | Table/Field | Purpose | Runtime Effect |
| --- | --- | --- | --- |
| Role | `sys_role.role_code`, `sys_role.data_scope`, `sys_role_menu` | Defines functional permission: which menus/buttons/APIs a user may use | Permission codes are returned by `/api/auth/info.permissions` and enforced by backend `@PreAuthorize` |
| Role custom orgs | `sys_role_org` | Defines custom organizations for role-level custom data scope | Used when role data scope or permission-scope config points to custom org visibility |
| Position | `sys_position`, `sys_user_position` | Supports one user with multiple posts, group boundary, and post-level rule/scope assignment | Complements role; useful for cross-org responsibilities without creating many duplicate roles |
| Position rules | `sys_position_rule`, `sys_permission_rule` | Adds structured permission rules to a position | Used by the permission rule engine and cache invalidation path |
| User override | `sys_user.data_scope_override`, `sys_user_org` | Gives a specific user a stricter or explicit scope different from role default | Highest-priority user-level data scope; null means follow role/default |
| Permission scope config | `role_perm_scope` | Defines `(assignee_type, assignee_key, module, action, scope_level, anchor)` for ROLE/POSITION/USER | Lets the same permission have different data ranges for view/edit/delete/share |
| Org admin | `sys_org_admin` and `system:org:admin` | Delegated org-level system administration | Allows scoped personnel/org operations without full system admin |
| Permission cache | `sys_user.perm_version`, permission cache service | Keeps PC/mobile permission changes synchronized | Permission changes evict cache and bump versions so stale tokens can be detected/refreshed |

Effective scope priority:

1. User-level explicit scope config from `role_perm_scope` where `assignee_type='USER'`.
2. Position-level explicit scope config from `role_perm_scope` where `assignee_type='POSITION'`.
3. Role-level explicit scope config from `role_perm_scope` where `assignee_type='ROLE'`.
4. If no explicit config exists, `view` actions follow role/user default data scope.
5. If no explicit config exists, mutation actions such as `edit`, `delete`, and `share` default to `self`.

Scope anchor:

| Anchor | Meaning | Typical Use |
| --- | --- | --- |
| `OWNER` | Scope is computed from record owner, applicant, uploader, or responsible person | Customer owner, incentive uploader, approval applicant |
| `ORG` | Scope is computed from organization/department fields | Customer office, department tree, regional management |

Valid scope codes:

| Code | Meaning | Notes |
| --- | --- | --- |
| `all` | All data in allowed tenant/group | Only for system admin or top business owners |
| `custom` | Explicit organizations/users configured in mapping tables | Requires `sys_role_org` or `sys_user_org` |
| `region` | Region-level scope | Used for regional responsibility |
| `office` | Office-level scope | Used for office managers and sales offices |
| `dept` | Department and supported subtree semantics | Existing parser treats legacy `dept_and_children` as dept-compatible |
| `dept_and_children` | Legacy department-subtree value | Kept for compatibility |
| `org` | Organization-level scope | Used when module is anchored to org |
| `org_and_children` | Organization subtree | Used for hierarchical org access |
| `team` | Direct/indirect subordinate scope through `manager_id` | Sales manager/team lead path |
| `self` | Current user's own records only | Default for mutation actions when not configured |

## Current Verified Role Baseline

The following role IDs/codes are visible in current migrations and tests. Production should still be checked through the live `sys_role` table before applying final role-menu changes.

| Role ID | Role Code/Name Evidence | Intended Responsibility | Recommended Default Data Scope | Notes |
| --- | --- | --- | --- | --- |
| `1` | `admin` / 系统管理员 | Full system administration, configuration, RBAC, data repair, release support | `all` | Can receive all menu/config permissions; should be limited to trusted admins |
| `9` | 行政 | Operational back office: customer import/export support, sea import, statistics, approval support | `dept`, `office`, or `custom` | Avoid granting unrestricted system/user/group management unless explicitly required |
| `10` | 销售总监 | Sales leadership: broad customer visibility, incentive oversight, sea import/management, approval | `region`, `office`, or `all` by business unit | Existing migrations grant broader incentive capabilities to this role |
| `11` | 销售员 | Frontline sales: own customers, own incentive upload, file preview, reminders, limited public sea | `self` or `team` only when leading subordinates | Existing migrations intentionally remove broad incentive edit/delete semantics |
| N/A | `system:org:admin` delegated admin | Organization-scoped administrator | `custom` or `org_and_children` | This is an authority path used beside normal role codes, not necessarily a standalone business role |

## Recommended Role Packages

| Role Package | Menus | Buttons/Actions | Data Scope | Mobile Access | Risk Level |
| --- | --- | --- | --- | --- | --- |
| System Admin | All system, role, user, org, logs, config, business modules | All config and recovery operations | `all` | Usually no mobile system admin needed | Very high |
| Org Admin | Personnel, org, role assignment within delegated org, selected business read | Create/edit users in managed org, reset password, bind roles/positions | `org_and_children` or `custom` | No first-release mobile admin | High |
| Sales Director | Dashboard, customers, public sea, approval, incentive, statistics, score ranking | Approve/reject, import sea/customers if business requires, view read stats | `region`, `office`, or configured `all` | Yes: customer/file/incentive/approval/sea read | High |
| Sales Manager | Dashboard, customer list/detail, reminders, public sea, approval participation, incentive | Team follow-up, approve if configured, view team stats | `team`, `dept`, or `office` | Yes | Medium |
| Salesperson | Own customers, files, incentive upload/query, reminders, public sea read | Add/update own customer data, upload own incentive records, mark read | `self` | Yes | Medium |
| Back Office/Admin Staff | Customer import/export, file management, approval config support, statistics | Import/export, maintain dictionaries/configs only if authorized | `custom`, `dept`, or `office` | Optional read-only mobile | High |
| Finance/Settlement | Customer invoice/payment read, payment maintenance, statistics | Maintain invoice/payment data | `custom`, `dept`, or `office` | Planned only after field-level protection | High |
| Read-Only Auditor | Logs, statistics, selected business read | No mutation buttons | `custom` or `all` depending audit mandate | Optional | Medium |

## Recommended Position Templates

Positions should not replace roles. Use positions to model where a person sits and which extra scope/rules they carry. This prevents creating many duplicate roles such as `sales_manager_shanxi`, `sales_manager_neimeng`, and so on.

| Position Code | Position Name | Typical Role Pairing | Default Scope Add-on | Suitable Rules |
| --- | --- | --- | --- | --- |
| `gm` | 总部总经理 | Sales Director or Admin Staff | `all` or `org_and_children` | Broad statistics, approval visibility |
| `region_manager` | 大区经理 | Sales Director / Sales Manager | `region` or `org_and_children` | Region customer view, team performance |
| `office_manager` | 办事处经理 | Sales Manager | `office` | Office customer view/edit, approval participation |
| `dept_manager` | 部门经理 | Sales Manager | `dept` or `team` | Team customer view, reminder processing |
| `salesman` | 销售人员 | Salesperson | `self` | Own customer and own incentive operations |
| `back_office` | 行政/运营 | Back Office/Admin Staff | `custom` | Import/export and config support by assigned org |
| `finance` | 财务/结算 | Finance/Settlement | `custom` | Invoice/payment field operations |
| `auditor` | 审计/只读 | Read-Only Auditor | `custom` or `all` | Read-only and log visibility |

## Role And Position Combination Matrix

| User Type | Role | Position | User Override | Result |
| --- | --- | --- | --- | --- |
| Normal salesperson | Salesperson | `salesman` in one department | None | Can use salesperson menus and sees own data |
| Team lead who also sells | Salesperson or Sales Manager | `dept_manager` plus `salesman` | None | Keeps own sales work and gains team/dept scope where configured |
| Regional director | Sales Director | `region_manager` | Optional custom orgs | Broad regional visibility without full system admin |
| Back office for two offices | Back Office/Admin Staff | `back_office` | `custom` with two offices | Can operate only assigned offices |
| Temporary support user | Minimal role | Any position | User-level `custom` or `self` | Short-lived override without changing role template |
| Org delegated admin | Org Admin authority | Office or department admin position | `org_and_children` or custom | Can manage users/org under delegated scope |
| Super admin | System Admin | Optional admin position | None | Full system control |

## Recommended Role-Module Permission Matrix

Legend: `V` view, `C` create, `U` update, `D` delete, `I` import, `E` export, `A` approve/reject, `S` share/ACL/config scope.

| Module | System Admin | Org Admin | Sales Director | Sales Manager | Salesperson | Back Office | Finance | Auditor |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Dashboard | V | V | V | V | V | V | V | V |
| Customer | V/C/U/D/I/E/S | V/C/U scoped | V/C/U/E/S scoped | V/C/U scoped | V/C/U own | V/C/U/I/E scoped | V payments/invoices scoped | V |
| File | V/C/U/D/S/ACL | V/C/U scoped | V/C/U/S scoped | V/C/U scoped | V/C own | V/C/U/S scoped | V scoped | V |
| Incentive | V/C/U/D/S/read-stats/config | V scoped | V/C/U/D/read-stats scoped | V/C scoped | V/C own and mark-read | V/read-stats scoped | V if needed | V/read-stats |
| Reminder | V/U | V/U scoped | V/U scoped | V/U team | V/U own | V/U scoped | V | V |
| Public Sea | V/I/config/U/D/claim | V scoped | V/I/claim scoped | V/claim scoped | V/claim if pool grants | V/I scoped | V | V |
| Approval | V/A/config | V/A scoped | V/A | V/A if approver | V own/applicant | V/A support | V/A finance-specific | V |
| Statistics | V | V scoped | V broad | V team | V own | V scoped | V finance | V |
| Score | V/C/U/D/entry/export | V scoped | V/entry/export scoped | V/entry team | V own ranking | V/entry support | V | V |
| System Users | V/C/U/D | V/C/U/D delegated | No default | No default | No | Optional scoped | No | No |
| Roles/Permissions | V/C/U/D | Limited delegated only | No | No | No | No default | No | V optional |
| Config/Dict/Logs | V/C/U/D | Limited delegated | V logs optional | No | No | V/U dict optional | V/U finance dict optional | V logs |

## Mobile Role Matrix

| Mobile Function | System Admin | Sales Director | Sales Manager | Salesperson | Back Office | Finance | Auditor |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Login/home | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Customer read | Yes | Yes | Yes | Own/scoped | Scoped | Scoped finance fields later | Yes |
| Customer create/edit | No mobile admin | Implemented scoped | Implemented scoped | Implemented own/scoped | Implemented scoped | No | No |
| File preview | Yes | Yes | Yes | Yes | Yes | Yes scoped | Yes |
| File upload/share/delete | No mobile admin | Upload/share/delete implemented by granted row action | Upload/delete implemented by granted row action | Upload own/share/delete if granted | Upload/share/delete if granted | Yes | Yes |
| Incentive query | Yes | Yes | Yes | Yes | Optional | Optional | Yes |
| Incentive upload/edit/delete | No mobile admin | Upload/edit/delete implemented by owner or grant | Upload/edit/delete if granted | Upload own/edit own or granted, delete own or granted | Optional upload/edit/delete by grant | Yes | Yes |
| Incentive read stats | Yes | Yes | Manager if granted | No default | Optional | Optional | Auditor if granted |
| Approval list | Yes | Yes | Yes if participant | Own/applicant | Support if participant | Finance if participant | Read-only |
| Approval approve/reject | Yes | Yes | If approver | No default | If approver | If approver | No |
| Public sea read | Yes | Yes | Yes | Yes if `sea:view` | Yes | Optional | Yes |
| Public sea claim/edit/delete | No mobile admin | Claim/edit/delete implemented by backend row grant | Claim/edit/delete if granted | Claim/edit/delete if granted | Claim/edit/delete if granted | Yes | Yes |
| System management | No mobile first release | No | No | No | No | No | No |

## Role Governance Rules

- Do not create a new role for every department or region. Use one business role plus positions and scoped org mappings.
- Roles answer "what can this person do"; positions and data scope answer "where or to whose data can they do it".
- Mutation permissions should be narrower than view permissions. If no explicit mutation scope exists, keep `self`.
- Mobile must never invent broader permissions than PC. It can hide actions for usability, but backend remains the final guard.
- High-risk operations stay PC-first: role management, permission scope configuration, sea config, file ACL mutation, imports, exports, and bulk operations. Destructive mobile deletes are limited to explicit row/detail actions with existing backend permission checks.
- Permission changes must evict permission cache and bump user permission versions so PC and mobile converge after refresh or next `/api/auth/info`.

## Data Scope Types

| Scope | Meaning | Applies To |
| --- | --- | --- |
| Own data | User can see or edit records owned by self | Customers, incentive upload/edit ownership, approvals initiated by self |
| Department/subtree | User can see records under allowed department tree | Customer list, statistics, system users, public sea where dept filter is used |
| Group | User is restricted to current or allowed group | Tenant/group-sensitive system and business data |
| Shared data | User can see records explicitly shared to them | Customer share, incentive share, file share |
| Pool visibility | User can see enabled sea pools visible to their role/user/org | Public sea list, public sea statistics |
| ACL | Folder/file access is computed by backend ACL and folder permissions | File management |
| Admin override | Admin or org admin may bypass part of normal scope | System management, org admin operations, selected service-layer checks |

## Client Surfaces

| Client | Current Role |
| --- | --- |
| PC `aigicrm-ui` | Full management console: all menus, configuration, imports, batch operations, data management |
| Mobile `aigicrm-miniprogram` | Lightweight operational client: login, home, scoped customer create/edit/read, customer share/log/indicator/contact/finance create, file preview/upload/share/folder management, incentive query/upload/edit/read-state/stats, approval action/submission, public sea read and claim approval |

## Auth And Account

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Login | Login | `POST /api/auth/login` | Public | None | Create session | Yes | Yes |
| Current User | Load profile, permissions, menus | `GET /api/auth/info` | Authenticated token | Current user | Read | Yes | Yes |
| Logout | Logout | `POST /api/auth/logout` | Authenticated token | Current user | End session | Yes | Local token clear; backend API can be reused |
| Group Switch | Switch current group | `POST /api/auth/switch-group` | Authenticated token | User allowed groups | Session mutation | Yes | Implemented |
| Password Recovery | Lookup/send-code/reset | `/api/auth/forgot-password/*` | Public or token/code verified | User account | Account recovery | Yes or backend available | Implemented |
| Profile Password | Change own password | `POST /api/system/users/change-password` | Authenticated token and old password verification | Current user only | Credential mutation | Yes | Implemented |

## Dashboard

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 工作台 | View dashboard | `GET /api/statistics/dashboard` | `statistics:view` or `dashboard:view` | Current user/org/group scope inside service | Read/statistics | Yes | Implemented |
| 工作台 | Daily stats | `GET /api/statistics/daily-stats` | `statistics:view` or `dashboard:view` | Role/org scope | Read/statistics | Yes | Implemented |
| 工作台 | Weekly stats | `GET /api/statistics/weekly-stats` | `statistics:view` or `dashboard:view` | Role/org scope | Read/statistics | Yes | Implemented |
| 工作台 | Indicator stats | `GET /api/statistics/indicator-stats` | `statistics:view` | Role/org scope | Read/statistics | Yes | Implemented |

## Customer Management

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 客户列表 | View/search/filter/list counts | `GET /api/customer/list`, `GET /api/customer/list/counts` | `customer:view` | Customer ownership, role data scope, org/group scope, shared visibility | Read | Yes | Yes |
| 客户详情 | View detail | `GET /api/customer/{id}` | `customer:view` | Backend customer access check | Read | Yes | Mobile list/detail card |
| 新增客户 | Create customer | `POST /api/customer/add` | `customer:add` | Current user's allowed org/group | Create | Yes | Implemented |
| 编辑客户 | Edit customer | `PUT /api/customer/{id}` | `customer:edit` | Backend edit access check | Update | Yes | Implemented |
| 客户详情 | Delete customer | `DELETE /api/customer/{id}` | `customer:delete` | Backend delete access check | Delete | Yes | Implemented |
| 客户详情 | View follow-up logs | `GET /api/customer/{id}/logs` | `customer:view` | Same customer visibility | Read | Yes | Implemented |
| 客户详情 | Add follow-up log | `POST /api/customer/{id}/logs` | `customer:edit` | Same customer edit boundary | Create | Yes | Implemented |
| 客户详情 | View invoices | `GET /api/customer/{id}/invoices`, `/summary` | `customer:view` + `finance:view` | Same customer visibility; backend returns empty data without finance permission | Read | Yes | Implemented |
| 客户详情 | Add invoice | `POST /api/customer/{id}/invoices` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Create | Yes | Implemented |
| 客户详情 | Delete invoice | `DELETE /api/customer/{id}/invoices/{invoiceId}` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Delete | Yes | Implemented |
| 客户详情 | View six indicators | `GET /api/customer/{id}/indicator` | `customer:view` | Same customer visibility | Read | Yes | Implemented |
| 客户详情 | Save six indicators | `POST /api/customer/{id}/indicator` | `customer:edit` | Same customer edit boundary | Update | Yes | Implemented |
| 客户详情 | Update relationship | `PUT /api/customer/{id}/relationship` | `customer:edit` | Same customer edit boundary | Update | Yes | Implemented |
| 客户详情 | View shares | `GET /api/customer/{id}/shares` | `customer:view` | Same customer visibility | Read | Yes | Implemented |
| 客户详情 | Add/remove share | `POST /api/customer/{id}/shares`, `DELETE /api/customer/{id}/shares/{userId}` | `customer:edit` | Same customer edit boundary | Share mutation | Yes | Implemented |
| 客户详情 | Release/return to sea | `POST /api/customer/{id}/release` | `customer:edit` | Same customer edit boundary and sea config match | Status mutation | Yes | Implemented |
| 客户联系人 | View contacts | `GET /api/customer/{id}/contacts` | `customer:view` + `contact:view` | Same customer visibility; backend returns empty data without contact permission | Read | Yes | Implemented |
| 客户联系人 | Add contact | `POST /api/customer/{id}/contacts` | `customer:edit` | Same customer edit boundary | Create | Yes | Implemented |
| 客户联系人 | Edit/delete contacts | `PUT/DELETE /api/customer/{id}/contacts...` | `customer:edit` | Same customer edit boundary | Update/Delete | Yes | Implemented |
| 客户回款 | View payments | `GET /api/customer/{id}/payments`, `/summary` | `customer:view` + `finance:view` | Same customer visibility; backend returns empty data without finance permission | Read | Yes | Implemented |
| 客户回款 | Add payment | `POST /api/customer/{id}/payments` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Create | Yes | Implemented |
| 客户回款 | Delete payment | `DELETE /api/customer/{id}/payments/{paymentId}` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Delete | Yes | Implemented |
| 客户导入 | Import customers | `POST /api/customer/import` | `customer:import` | Import target org/group validation | Import | Yes | No |
| 客户导入 | Download template | `GET /api/customer/import/template` | `customer:import` | None | Download | Yes | No |
| 客户导出 | Export customers | `GET /api/customer/export` | `customer:export` | Same list data scope | Export | Yes | No |
| 客户校验 | Check duplicate name | `GET /api/customer/check-name` | `customer:view` | Backend check rules | Read/validate | Yes | Implemented |
| 用户简表 | User selector | `GET /api/customer/users/simple` | `customer:view` | Backend user scope | Read | Yes | Implemented |

## File Management

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 文件管理 | Get download token | `GET /api/file/download-token` | Token/session path | Current authenticated user | Read token | Yes | Yes |
| 文件管理 | Folder tree | `GET /api/file/folders` | `file:view` | Backend folder ACL and visible folders | Read | Yes | Implemented |
| 文件管理 | File list/search | `GET /api/file/list` | `file:view` | Backend folder/file ACL, shared files | Read | Yes | Yes |
| 文件管理 | Upload file | `POST /api/file/upload` | `file:upload` | Folder upload permission and service ACL | Upload/Create | Yes | Implemented |
| 文件管理 | Create folder | `POST /api/file/folders` | `file:folder:manage` | Parent folder permission | Create | Yes | Implemented |
| 文件管理 | Rename/update folder | `PUT /api/file/folders/{id}` | `file:folder:manage` | Folder owner/ACL | Update | Yes | Implemented |
| 文件管理 | Delete folder | `DELETE /api/file/folders/{id}` | `file:folder:manage` | Folder owner/ACL | Delete | Yes | Implemented |
| 文件管理 | Delete file | `DELETE /api/file/{id}` | `file:delete` | File owner/ACL | Delete | Yes | Implemented |
| 文件管理 | Replace file | `POST /api/file/{id}/replace` | `file:edit` | File owner/ACL | Replace | Yes | Implemented |
| 文件管理 | Storage stats | `GET /api/file/storage` | `file:view` | Current user/tenant | Read/statistics | Yes | Implemented |
| 文件管理 | Download by id | `GET /api/file/download/{id}` | `file:view` | File ACL and token check | Download | Yes | Mobile preview/download |
| 文件管理 | Public download | `GET /api/file/download/public/**` | Public token/path | Public share constraints | Download | Yes | Can preview if linked |
| 文件管理 | Path download | `GET /api/file/download/**` | `file:view` | File path ACL | Download | Yes | Implemented |
| 文件分享 | Share file | `POST /api/file/{id}/share` | `file:share` | File owner/share permission | Share mutation | Yes | Implemented |
| 文件分享 | Shared to me | `GET /api/file/shared-to-me`, `/count` | `file:view` | Files shared to current user | Read | Yes | Implemented |
| 文件夹 ACL | View ACL | `GET /api/file/folders/{id}/acl` | `file:acl:manage` | Folder ACL manager | Read | Yes | No first release (2026-11-05 用户令撤除 MP 只读弹窗, PC 权限设置保留) |
| 文件夹 ACL | Add/remove ACL | `POST/DELETE /api/file/folders/{id}/acl...` | `file:acl:manage` | Folder ACL manager | ACL mutation | Yes | No first release |
| 文件夹 ACL | Folder permissions | `GET /api/file/folders/{id}/permissions` | `file:acl:manage` | Folder ACL manager | Read | Yes | No first release |
| 文件夹 ACL | Download setting | `PUT /api/file/folders/{id}/download` | `file:acl:manage` | Folder ACL manager | Update | Yes | No first release |

## Incentive

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 四项服务 | Category list | `GET /api/incentive/categories` | `incentive:view` | Tenant/group | Read | Yes | Implemented |
| 四项服务 | Create/edit/delete category | `POST/PUT/DELETE /api/incentive/categories...` | `incentive:edit` | Admin/config scope | Config mutation | Yes | No first release |
| 四项服务 | Record list | `GET /api/incentive/records` | `incentive:view` | Upload owner, share visibility, role scope | Read | Yes | Implemented |
| 四项服务 | Upload/add record | `POST /api/incentive/records` | `incentive:add` | Customer access and uploader identity | Create | Yes | Implemented |
| 四项服务 | Edit own/permitted record | `PUT /api/incentive/records/{id}` | Method allows service-layer ownership/permitted edit | Owner/permitted editor | Update | Yes | Implemented |
| 四项服务 | Delete own/permitted record | `DELETE /api/incentive/records/{id}` | Service-layer ownership/permitted delete | Owner/permitted editor/admin | Delete | Yes | Implemented |
| 四项服务 | Recalculate records | `POST /api/incentive/records/recalculate` | `incentive:edit` | Config/admin scope | Batch mutation | Yes | No |
| 激励查询 | Customer score | `GET /api/incentive/score/customer/{customerId}` | `incentive:view` | Same incentive/customer visibility | Read/statistics | Yes | Implemented |
| 激励查询 | Scoreboard | `GET /api/incentive/scoreboard` | `incentive:view` | Role/share/customer visibility | Read/statistics | Yes | Implemented |
| 激励查询 | Ranking | `GET /api/incentive/ranking` | `incentive:view` | Role/share/customer visibility | Read/statistics | Yes | Implemented |
| 激励查询 | Customer-record grouped list | `GET /api/incentive/customer-records` | `incentive:view` | Role/share/customer visibility | Read | Yes | Yes |
| 激励查询 | Mark record read | `POST /api/incentive/records/{id}/read` | `incentive:view` | Current user and visible record | Read-state mutation | Yes | Yes |
| 激励查询 | Read statistics | `GET /api/incentive/read-stats` | `incentive:read-stats` | Backend stats scope | Read/statistics | Yes | Yes |
| 激励统计 | Category stats | `GET /api/incentive/category-stats` | `incentive:view` | Role/share/customer visibility | Read/statistics | Yes | Implemented |
| 激励配置 | User selector | `GET /api/incentive/users` | `incentive:view` | Backend user scope | Read | Yes | Implemented |
| 激励授权 | Permission list | `GET /api/incentive/permission/list` | `system:config:view` | Config/admin scope | Read | Yes | No |
| 激励授权 | Grant/revoke permission | `POST /api/incentive/permission/grant`, `DELETE /api/incentive/permission/{id}` | `system:config:edit` | Config/admin scope | Permission mutation | Yes | No |
| 激励授权 | Permission check | `GET /api/incentive/permission/check` | Authenticated self-check; global login guard plus service grant table | Current user only | Read/validate | Yes | Implemented |

## Reminder

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 提醒管理 | Expiring customer list | `GET /api/reminder/expiring-list` | `reminder:view` | Customer data scope | Read | Yes | Implemented |
| 提醒管理 | Reminder list | `GET /api/reminder/list` | `reminder:view` | Current user/customer scope | Read | Yes | Implemented |
| 提醒管理 | Pending count | `GET /api/reminder/pending-count` | `reminder:view` | Current user scope | Read/statistics | Yes | Implemented |
| 提醒管理 | Ensure expiring reminders | `POST /api/reminder/ensure-expiring` | `reminder:view` | Customer data scope | Batch create/check | Yes | No first release |
| 提醒管理 | Process reminder | `POST /api/reminder/{id}/process` | `reminder:view` | Current user/reminder scope | Status mutation | Yes | Implemented |

## Public Sea

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 公海池 | List/search/filter | `GET /api/sea/list` | `sea:view` | Enabled visible pools, dept subtree filter, customer keyword/industry filter | Read | Yes | Implemented |
| 公海池 | Statistics | `GET /api/sea/statistics` | `sea:view` | Enabled visible pools | Read/statistics | Yes | Yes |
| 公海池 | Visible pool selector | `GET /api/sea/configs/visible` | `sea:view` | Current user's visible enabled pools | Read | Yes | Yes |
| 公海池 | Claim customer | Approval submission through shared approval flow | Backend service checks pool claim permission and `sea:claim` compatibility | Pool-level claim permission | Submit approval/status mutation | Yes | Implemented |
| 公海池 | Edit sea customer | Customer edit route | `customer:edit` plus pool edit permission | Customer/pool edit permission | Update | Yes | Implemented |
| 公海池 | Delete pool item | `DELETE /api/sea/pool/{id}` | `sea:view` at controller entry; service-layer pool delete permission remains final guard | Pool delete permission, available status only | Delete | Yes | Implemented |
| 公海导入 | Import to sea | `POST /api/sea/import` | `sea:import` | Target pool import permission | Import | Yes | No |
| 公海配置 | Config list | `GET /api/sea/configs` | `sea:config:view` | Config/admin scope | Read | Yes | No |
| 公海配置 | Org tree for config | `GET /api/sea/org-tree` | `sea:config:view` | Config/admin scope | Read sensitive org/person tree | Yes | No |
| 公海配置 | Create/edit/delete config | `POST/PUT/DELETE /api/sea/configs...` | `sea:config:edit` | Config/admin scope | Config mutation | Yes | No |
| 捞取记录 | My claim records | `GET /api/sea/claim-records` | `sea:view` | Current applicant only | Read | Yes | Implemented |

## Approval

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 审批管理 | Approval list/tabs | `GET /api/approval/list` | `approval:view` | Current user as applicant/approver, tab/type/dept scope in service | Read | Yes | Yes |
| 审批管理 | Submit approval | `POST /api/approval/submit` | `approval:view` | Customer/action-specific service validation | Create | Yes | Implemented |
| 审批管理 | Approve | `POST /api/approval/{id}/approve` | `approval:approve` | Current approver node/approval service check | Status mutation | Yes | Yes |
| 审批管理 | Reject | `POST /api/approval/{id}/reject` | `approval:reject` | Current approver node/approval service check | Status mutation | Yes | Yes |
| 审批管理 | Statistics | `GET /api/approval/statistics` | `approval:view` | Current user approval scope | Read/statistics | Yes | Yes |
| 审批配置 | Config list | `GET /api/approval/config/list` | `system:config:view` | Config/admin scope | Read | Yes | No |
| 审批配置 | Create/edit/delete config | `POST/PUT/DELETE /api/approval/config...` | `system:config:edit` | Config/admin scope | Config mutation | Yes | No |

## Statistics And Indicator

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 统计分析 | Analysis | `GET /api/statistics/analysis` | `statistics:view` | Role/org/group scope | Read/statistics | Yes | Implemented |
| 统计分析 | Team activities | `GET /api/statistics/team-activities` | `statistics:view` | Role/org/group scope | Read/statistics | Yes | Implemented |
| 统计排行 | Customer rankings | `GET /api/statistics/customer-rankings` | `statistics:view` | Role/org/group/customer scope | Read/statistics | Yes | Implemented |
| 核心客户六项指标 | Indicator overview | `GET /api/statistics/indicator-stats` | `statistics:view` | Role/org/customer scope | Read/statistics | Yes | Yes |

## Score

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 学分配置 | Add score item | `POST /api/score/items` | Controller should be rechecked; expected `score:manage` | Config/admin scope | Create | Yes | No |
| 学分配置 | Edit/delete item | `PUT/DELETE /api/score/items/{id}` | `score:manage` | Config/admin scope | Update/Delete | Yes | No |
| 学分录入 | Grid | `GET /api/score/grid` | `score:manage` | Managed users/depts | Read | Yes | No |
| 学分录入 | Batch entry | `POST /api/score/entries/batch` | `score:entry` | Managed users/depts | Batch mutation | Yes | No |
| 学分排行 | Ranking | `GET /api/score/ranking` | `score:view` | Role/org/group scope | Read/statistics | Yes | Implemented |
| 学分排行 | Ranking stats | `GET /api/score/ranking/stats` | `score:view` | Role/org/group scope | Read/statistics | Yes | Implemented |
| 学分导出 | Export grid | `GET /api/score/export/grid` | `score:manage` | Same grid/ranking scope | Export | Yes | No |

## System Management

| Menu/Page | Button/Action | API | Permission | Data Scope | Operation Type | PC | Mobile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 人员管理 | User list/detail | `GET /api/system/users`, `GET /api/system/users/{id}` | `system:user:view` or `system:org:admin` | Org admin scope, role/group scope | Read | Yes | No |
| 人员管理 | Create user | `POST /api/system/users` | `system:user:add` or `system:org:admin` | Org admin allowed orgs | Create | Yes | No |
| 人员管理 | Edit user | `PUT /api/system/users/{id}` | `system:user:edit` or `system:org:admin` | Org admin allowed orgs | Update | Yes | No |
| 人员管理 | Admin reset user password | `POST /api/system/users/{id}/reset-password` | `system:user:edit` or `system:org:admin` | Target user/admin scope | Credential mutation | Yes | No |
| 人员管理 | Delete user | `DELETE /api/system/users/{id}` | `system:user:delete` or `system:org:admin` | Org admin allowed orgs | Delete | Yes | No |
| 组织架构 | Dept tree/list | `GET /api/system/depts/tree`, `GET /api/system/depts` | Authenticated or `system:dept:view` depending endpoint | Org scope | Read | Yes | No |
| 组织架构 | Create/edit/delete dept | `POST/PUT/DELETE /api/system/depts...` | `system:dept:edit` | Org admin/config scope | Create/Update/Delete | Yes | No |
| 组织架构 | Dept admins | `GET/POST/DELETE /api/system/depts/{deptId}/admins...` | `system:dept:view` or `system:dept:edit` | Org admin/config scope | Read/Mutation | Yes | No |
| 角色管理 | Role list/detail/orgs | `GET /api/system/roles...` | `system:role:view` | Config/admin scope | Read | Yes | No |
| 角色管理 | Create/edit/delete role | `POST/PUT/DELETE /api/system/roles...` | `system:role:add/edit/delete` | Config/admin scope | Create/Update/Delete | Yes | No |
| 岗位管理 | Position list/detail | `GET /api/system/positions...` | `system:position:view` | Config/admin scope | Read | Yes | No |
| 岗位管理 | Create/edit/delete position | `POST/PUT/DELETE /api/system/positions...` | `system:position:add/edit/delete` | Config/admin scope | Create/Update/Delete | Yes | No |
| 岗位管理 | User-position binding | `GET/PUT /api/system/positions/users/{userId}` | `system:user:view`, `system:position:view`, or `system:user:edit` depending endpoint | Target user/admin scope | Read/Mutation | Yes | No |
| 集团管理 | Group list | `GET /api/system/groups` | `system:group:view` | Config/admin scope | Read | Yes | No |
| 集团管理 | Create/edit/delete group | `POST/PUT/DELETE /api/system/groups...` | `system:group:add/edit/delete` | Config/admin scope | Create/Update/Delete | Yes | No |
| 菜单管理 | Menu tree | `GET /api/system/menus/tree` | `system:menu:view` | Config/admin scope | Read | Yes | No |
| 系统设置 | Config list/update | `GET/PUT /api/system/configs` | `system:config:view/edit` | Config/admin scope | Read/Update | Yes | No |
| 系统设置 | Public/logo config | `GET /api/system/configs/public`, `/logo` | Public or config endpoint specific | Public config | Read | Yes | Login/mobile can reuse |
| 字典管理 | Dict type/data read | `GET /api/system/dict/*` | Authenticated or config view depending endpoint | Config/global | Read | Yes | Can reuse for pickers |
| 字典管理 | Dict type/data mutate | `POST/PUT/DELETE /api/system/dict/*` | `system:config:edit` | Config/admin scope | Create/Update/Delete | Yes | No |
| 日志管理 | Logs/counts | `GET /api/system/logs`, `/counts` | `system:log:view` | Log admin scope | Read/audit | Yes | No |
| 权限规则 | Rule list | `GET /api/system/rules` | `system:rule:view` | Config/admin scope | Read | Yes | No |
| 权限规则 | Create/edit/delete rule | `POST/PUT/DELETE /api/system/rules...` | `system:rule:add/edit/delete` | Config/admin scope | Permission rule mutation | Yes | No |
| 权限范围 | Role/assignee permission scopes | `GET/PUT /api/system/perm-scopes...` | `system:config:view/edit` | Config/admin scope | Read/Mutation | Yes | No |

## Mobile First-Release Functional Set

| Mobile Page | Menu/Entry | Permission | APIs | Notes |
| --- | --- | --- | --- | --- |
| Login | `pages/login/index` | Public | `/api/auth/login`, `/api/auth/info` | Existing account/password first; WeChat binding later |
| Home | `pages/home/index` | Authenticated plus `dashboard:view` or `statistics:view` for dashboard cards | `/api/auth/info`, `/api/statistics/dashboard`, reminder pending/expiring APIs | Shows user, role, dashboard cards and quick entries when permitted |
| Mobile Shell | `pages/app/index` | Authenticated and per-entry permission gates from `/api/auth/info` | No extra business API; navigates to registered mobile pages | Mobile counterpart of the PC left menu/application navigation; hides unauthorized app entries |
| Customer | `pages/customer/index` | `customer:view`, plus action permissions | `/api/customer/list`, `/api/customer/{id}`, `/api/customer/list/counts`, customer form/share/log/contact/finance/indicator APIs | List/detail/counts, scoped create/edit/delete, share/release, logs, contacts create/edit/delete, finance create/delete, relationship score and six indicators are implemented; import/export stay PC-first |
| File | `pages/files/index` | `file:view`, plus action permissions | `/api/file/list`, `/api/file/download-token`, `/api/file/upload`, folder/share/storage/shared-to-me/ACL read APIs | Unified file center; preview/download/upload/share/replace/delete/folder management/storage/shared-to-me/folder ACL read are implemented; ACL mutation stays PC-first |
| Incentive | `pages/incentive/index` | `incentive:view`, optional `incentive:add`, `incentive:read-stats`, `incentive:read-ranking:view` | `/api/incentive/customer-records`, `/records`, `/records/{id}/read`, `/read-stats`, `/read-ranking`, `/category-stats`, `/score/customer/{id}` | Query/detail/upload/edit/delete, file preview/download, read-state, read stats/ranking, category stats and customer score are implemented; category config stays PC-first |
| Approval | `pages/approval/index` | `approval:view`, optional `approval:approve`, `approval:reject` | `/api/approval/list`, `/approve`, `/reject`, `/statistics` | Action buttons hidden without permissions; backend remains final guard |
| Public Sea | `pages/sea/index` | `sea:view`, optional `sea:claim` and row edit/delete grant | `/api/sea/list`, `/statistics`, `/configs/visible`, `/claim-records`, shared approval submit and customer edit route | List/stats/pool selector, claim approval submission, claim records, permitted edit route and row delete are implemented; import/config stay PC-first |
| Score | `pages/ranking/index` | `score:view`, legacy `score:ranking` menu-compatible, or `statistics:view` for customer rankings | `/api/score/ranking`, `/api/score/ranking/stats`, `/api/statistics/customer-rankings` | Personal/dept score ranking, period filter, stats and customer ranking drilldown are implemented; score entry/config/export stay PC-first |

## Operation Type Legend

| Type | Meaning | Release Risk |
| --- | --- | --- |
| Read | View/list/detail/statistics | Low, still requires data-scope validation |
| Create | Add new business record | Medium, requires validation and audit |
| Update | Modify existing record | Medium/high, requires ownership or scoped edit check |
| Delete | Remove business data | High, avoid mobile first release unless strongly needed |
| Import | Batch create/update | High, PC-only first |
| Export | Bulk data extraction | High, PC-only first |
| Share/ACL mutation | Expand data visibility | High, PC admin/owner flow first |
| Status mutation | Approval, release, claim, process reminder | High, must be audited and idempotent where possible |
| Config mutation | System, role, dict, sea, approval config | Very high, PC-only |

## Known Review Items Before Final Release

- Some backend endpoints intentionally rely on service-layer checks instead of method-level `@PreAuthorize`, for example incentive own-record edit/delete and sea pool delete. Keep tests around these paths.
- File mutation permissions are split by V22: `file:upload`, `file:edit`, `file:delete`, `file:share`, and `file:folder:manage`; V22 inherits bindings from existing `file:view` roles before controller enforcement changes.
- Score controller and PC route permissions were rechecked: ranking reads use `score:view`; score item management and batch entry remain separately gated by `score:manage` and `score:entry`.
- Mobile public sea executes claim/edit/delete only through backend-returned capability flags and service-layer permission checks.
- Mobile customer detail now uses non-tab detail subpages for share, logs, contacts, finance, timeline and six-indicator workflows. Keep customer id propagation covered by navigation and customer-form contracts.
- Sonar current query shows `OPEN=0`, but release sign-off should still include a fresh full analysis/quality gate record when the CI/Sonar pipeline is available.

## Merge Recommendation

Merge this matrix with the architecture and todo documents because it defines the intended final-source permission contract.

Do not merge local editor settings, production backup snapshots, private keys, or audit evidence unless explicitly approved as project deliverables.
