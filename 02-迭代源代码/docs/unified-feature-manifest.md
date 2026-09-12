# AIGICRM Unified Feature Manifest

Date: 2026-09-06

This manifest is the working source-of-truth for PC and WeChat Mini Program parity. PC and Mini Program must use the same backend, database, account, role, position, permission and data-scope model. Only the UI layout may differ.

## 1. Feature Key Rules

- Feature key format: `module.action`.
- Page permission controls whether the page entry is visible.
- Button permission controls whether the action is visible.
- Backend permission and data-scope checks are the final security boundary.
- Mini Program pages must map to the same feature keys as PC pages.
- If a feature is PC-only in the first mobile release, it must still be listed with a mobile status of `PC-only`.

## 2. Module Manifest

| Feature Key | Module | PC Route/Page | Mini Program Page | Permission | Data Scope | Operation Type | Mobile Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `auth.login` | Auth | `/login` | `pages/login/index` | public | current account | login | implemented |
| `auth.currentUser` | Auth | layout/store | all pages | authenticated | current account/group | read | implemented |
| `dashboard.view` | Dashboard | `/dashboard` | `pages/home/index` | `dashboard:view` or `statistics:view` | current user scope | read/statistics | implemented |
| `customer.list` | Customer | `/customer` | `pages/customer/index` | `customer:view` | unified customer data scope | read | implemented |
| `customer.detail` | Customer | `/customer/detail/:id` | `pages/customer/detail/index` | `customer:view` | owner/shared/org scope | read | implemented |
| `customer.add` | Customer | `/customer/add` | `pages/customer/form/index` | `customer:add` | current user/group | create | implemented |
| `customer.edit` | Customer | `/customer/edit/:id`, detail edit | `pages/customer/form/index?id=:id` | `customer:edit` | editable customer scope | update | implemented |
| `customer.delete` | Customer | customer table | PC-only by default | `customer:delete` | editable customer scope | delete | PC-only |
| `customer.release` | Customer/Sea | customer table/detail | `pages/customer/detail/index` | `customer:edit` | editable customer scope | workflow/update | implemented |
| `customer.share` | Customer | customer detail | `pages/customer/share/index` | `customer:view` to read, `customer:edit` to add/revoke (the legacy `customer:share` code is not a runtime gate) | owner/editable scope | grant/read-share | implemented |
| `customer.import` | Customer | customer toolbar | PC-only by default | `customer:import` | current group/import scope | import | PC-only |
| `customer.export` | Customer | customer toolbar | PC-only by default | `customer:export` | current query data scope | export | PC-only |
| `file.list` | File | `/file` | `pages/file/index` | `file:view` | folder ACL plus owner/share | read | implemented |
| `file.download` | File | file list/action | `pages/file/index` | `file:view` plus folder download | folder ACL | download | implemented |
| `file.upload` | File | file toolbar/dialog | `pages/files/index` | `file:upload` plus folder ACL | folder ACL | upload | implemented |
| `file.share` | File | file row/dialog | `pages/file/index` share action | `file:share` plus folder ACL | folder ACL | grant/share | implemented |
| `file.replace` | File | file row | `pages/files/detail/index` action menu | `file:edit` plus folder ACL | folder ACL | update | implemented |
| `file.delete` | File | file row | PC-only by default | `file:delete` plus folder ACL | folder ACL | delete | PC-only |
| `file.folderManage` | File | folder panel | PC-only by default | `file:folder:manage` plus folder ACL | folder ACL | create/update/delete | PC-only |
| `file.aclManage` | File | folder ACL dialog | PC-only by default | `file:acl:manage` | folder admin ACL | grant/revoke | PC-only |
| `incentive.index` | Incentive | `/incentive` | `pages/incentive/index` query/detail/upload | `incentive:view` to read, `incentive:add` to upload | incentive category/user grant scope | read/create | implemented |
| `incentive.search` | Incentive | `/incentive/search` | `pages/incentive/index` | `incentive:view` | incentive category/user grant scope | read | implemented |
| `incentive.markRead` | Incentive | search detail/read | `pages/incentive/index` | `incentive:view` | visible incentive record | update read-state | implemented |
| `incentive.readStats` | Incentive | search stats panel | `pages/incentive/index` | `incentive:read-stats` | current group/month | statistics | implemented |
| `incentive.upload` | Incentive | `/incentive` upload dialog | `pages/incentive/index` evidence upload | `incentive:add` | current user/customer scope | create/upload | implemented |
| `incentive.recordEdit` | Incentive | record table append | `pages/incentive/detail/index` | owner or grant `canEdit` | incentive grant scope | update | implemented |
| `incentive.recordDelete` | Incentive | record table delete | PC-only by default | owner or grant `canDelete` | incentive grant scope | delete | PC-only |
| `incentive.stats` | Incentive | `/incentive/stats` | `pages/statistics/index` and `pages/ranking/index` | `incentive:view` | incentive grant/data scope | statistics | implemented |
| `reminder.list` | Reminder | `/reminder` | `pages/reminder/index` | `reminder:view` | current user reminders | read | implemented |
| `reminder.expiring` | Reminder | `/reminder` 到期客户 | `pages/reminder/index` 到期客户 | `reminder:view` | unified customer scope | read | implemented |
| `reminder.process` | Reminder | reminder row | `pages/reminder/index` | `reminder:view` | current user reminders | update | implemented |
| `approval.list` | Approval | `/approval` | `pages/approval/index` | `approval:view` | applicant/approver/org scope | read | implemented |
| `approval.detail` | Approval | approval row | `pages/approval/index` detail | `approval:view` | applicant/approver/org scope | read/timeline | implemented |
| `approval.approve` | Approval | approval row/dashboard | `pages/approval/index` | `approval:approve` | current approver node | workflow/update | implemented |
| `approval.reject` | Approval | approval row/dashboard | `pages/approval/index` | `approval:reject` | current approver node | workflow/update | implemented |
| `approval.submit` | Approval | customer/sea/reminder flows | customer detail and sea claim pages | source action permission | source business scope | workflow/create | implemented |
| `sea.list` | Public Sea | `/sea` | `pages/sea/index` | `sea:view` | visible pool/data scope | read | implemented |
| `sea.claim` | Public Sea | sea row | `pages/sea/index` and claim detail | `sea:claim` plus row `canClaim` | pool/org/user rule | workflow/create | implemented |
| `sea.import` | Public Sea | sea import dialog | PC-only by default | `sea:import` | pool import scope | import | PC-only |
| `sea.delete` | Public Sea | sea row | PC-only by default | `sea:pool:delete` plus row `canDelete` | pool delete scope | delete | PC-only |
| `sea.config` | Public Sea | system config/pool manager | PC-only by default | `sea:config:view/edit` | config scope | config CRUD | PC-only |
| `statistics.dashboard` | Statistics | `/statistics`, `/indicator` | PC dashboard; `pages/home/index`, `pages/statistics/index`, `pages/statistics/detail/index`, `pages/indicator/index` | `statistics:view` or `dashboard:view` where applicable | unified customer data scope | statistics | implemented |
| `score.entry` | Score | `/score/entry` | pending decision | `score:entry` page, `score:manage` write | visible users | create/update | pending decision |
| `score.ranking` | Score | `/score/ranking` | `pages/ranking/index` and `pages/ranking/detail/index` | `score:view` | visible users | read/statistics | implemented |
| `system.user` | System | `/system/user` | PC-only | `system:user:*` | org-admin/user admin scope | account CRUD | PC-only |
| `system.role` | System | `/system/role` | PC-only | `system:role:*` | current group/global config | role CRUD/grant | PC-only |
| `system.position` | System | `/system/position` | PC-only | `system:position:*` | current group/org config | position CRUD/rule binding | PC-only |
| `system.dept` | System | `/system/org` | PC-only | `system:dept:view/edit` | current group/org tree | org CRUD/admin binding | PC-only |
| `system.config` | System | `/system/config` | PC-only | `system:config:view/edit` | current group/system config | config CRUD | PC-only |
| `system.log` | System | `/system/log` | PC-only | `system:log:view` | current admin scope | audit read | PC-only |
| `system.rule` | System | `/system/rule` | PC-only | `system:rule:*` | current group/system config | rule CRUD | PC-only |
| `system.group` | System | `/system/group` | PC-only | `system:group:*` | super-admin group scope | group CRUD | PC-only |

## 3. Immediate Engineering Use

- Mini Program development must pick one pending feature key at a time.
- Each feature key requires backend permission verification before UI completion.
- Each mobile page must call `/api/auth/info`-derived permissions and never maintain its own independent role rules.
- Any new backend endpoint must be added to `scripts/gen-unified-feature-audit.py` output by re-running the audit.
- Any new permission code must be added through migration/database config and then regenerated into the permission dictionary.
