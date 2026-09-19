# AIGICRM PC Function And Defect Audit

Date: 2026-09-06

## 1. Audit Goal

Before continuing Mini Program parity development, freeze the current PC-side fact base and identify defects caused by repeated iterations.

## 2. Current PC Baseline

| Item | Count | Evidence |
| --- | ---: | --- |
| PC routes | 27 | `docs/unified-feature-audit.md` |
| PC API wrappers | 163 | `docs/unified-feature-audit.md` |
| Backend endpoints | 186 | `docs/unified-feature-audit.md` |
| PC page actions | 195 | `docs/unified-feature-audit.md` |
| PC API wrappers without backend endpoint match | 0 | `docs/unified-feature-audit.md` |
| Mini Program API wrappers without backend endpoint match | 0 | `docs/unified-feature-audit.md` |

Conclusion: PC API and backend endpoint contracts are broadly connected. The main risks are permission, data-scope, workflow and UI-action consistency risks.

## 3. Findings

| ID | Severity | Status | Finding | Impact |
| --- | --- | --- | --- | --- |
| PC-AUD-001 | High | Fixed locally | Route unauthorized fallback always redirected to `/dashboard`, while layout used a separate first-accessible-page fallback. | Users without `dashboard:view` could land on an unauthorized page or confusing redirect path. |
| PC-AUD-002 | High | Todo | New backend permission `incentive:read-stats` is present in code/migration, but generated permission dictionary has not been regenerated from the real DB/config source. | Administrators may not see or assign the permission from generated docs/config exports. |
| PC-AUD-003 | Medium | Partially fixed locally | Many PC destructive or approval buttons relied on backend permission checks but did not expose a consistent frontend button permission hint. | Security boundary remains backend-side, but users may see buttons that fail with 403. |
| PC-AUD-004 | Medium | Todo | Several dictionary permissions are not directly visible in controller annotations because enforcement may be in service-layer checks or historical/deprecated permissions. | Every such permission needs a documented enforcement location and test. |
| PC-AUD-005 | Medium | Todo | Build passes but still emits Rolldown pure-annotation warnings from `@vueuse/core` and large chunk warnings. | Not a functional blocker, but should be tracked before production release. |
| PC-AUD-006 | Medium | Todo | PC pages and Mini Program pages do not yet share a formal feature manifest. | There is no source-of-truth guarantee that PC and Mini Program expose the same capability set in different layouts. |

## 4. Fixed Locally

- Added `/403` no-permission route and page.
- Changed router guard to redirect unauthorized users to the first route they actually have permission to access, or `/403` if they have no page permissions.
- Changed layout fallback to use the backend-provided dynamic menu tree instead of a hardcoded candidate list.
- Improved the source audit script so `@GetMapping` / `@PostMapping` root mappings are parsed correctly.
- Added automatic PC/Mini Program API-to-backend endpoint drift checks to the generated audit report.
- Added frontend button visibility checks for customer release/delete/detail edit/share, approval approve/reject, role CRUD, position CRUD/rules, group CRUD, permission-rule CRUD, organization management, basic settings, dictionary management, sea-pool config and incentive user grants.
- Fixed approval configuration delete confirmation slot from `#ref` to `#reference`.

## 5. Button Permission Mapping Added Locally

| Module | UI Action | Frontend Gate | Backend Permission |
| --- | --- | --- | --- |
| Customer | Release customer | `canRelease` | `customer:edit` |
| Customer | Delete customer | `canDelete` | `customer:delete` |
| Customer | Detail edit | `canEditCustomer` | `customer:edit` |
| Customer | Detail share | `canShareCustomer` | `customer:share` |
| Approval | Approve | `canApprove` | `approval:approve` |
| Approval | Reject | `canReject` | `approval:reject` |
| System Role | Add role | `canAddRole` | `system:role:add` |
| System Role | Edit/assign permissions | `canEditRole` | `system:role:edit` |
| System Role | Delete role | `canDeleteRole` | `system:role:delete` |
| System Position | Add position | `canAddPosition` | `system:position:add` |
| System Position | Edit/assign rules | `canEditPosition` | `system:position:edit` |
| System Position | Delete position | `canDeletePosition` | `system:position:delete` |
| System Group | Add group | `canAddGroup` | `system:group:add` |
| System Group | Edit group | `canEditGroup` | `system:group:edit` |
| System Group | Delete group | `canDeleteGroup` | `system:group:delete` |
| System Rule | Add rule | `canAddRule` | `system:rule:add` |
| System Rule | Edit rule | `canEditRule` | `system:rule:edit` |
| System Rule | Delete rule | `canDeleteRule` | `system:rule:delete` |
| System Org | Org CRUD/admin management | `canEditDept` | `system:dept:edit` |
| System Config | Basic settings/dict edit | `canEditConfig` | `system:config:edit` |
| Approval Config | Flow config add/edit/delete | `canEditConfig` | `system:config:edit` |
| Sea Config | Pool config CRUD | `canEditSeaConfig` | `sea:config:edit` |
| Incentive Grant | Grant/revoke per-user incentive capability | direct store check | `system:config:edit` |

## 6. Required Todo Before Final Merge

- [ ] Regenerate `docs/permission/permission-dict.md` from the real target database after applying `V24__incentive_read_status.sql`.
- [ ] Verify intended role bindings for `incentive:read-stats`.
- [x] Finish button-level permission mapping documentation for file folder ACL, sea row-level claim/delete and score entry/manage. Evidence is recorded in `docs/high-risk-action-matrix.md` and the unified feature audit.
- [x] For every dictionary permission not directly seen in controller annotations, record the service-layer guard or mark it deprecated and remove it from role packages. Historical entries and replacement guards are recorded in `docs/high-risk-action-matrix.md` and `docs/permission/permission-dict.md`.
- [x] Add tests for org-admin scoped user CRUD, role/position/scope cache invalidation and forbidden privilege escalation. Covered by `SysUserControllerOrgAdminTest`, `OrgAdminServiceSecurityTest`, `SysRoleServiceTest`, `SysPositionServiceTest`, `SysUserDataScopeOverrideTest`, and `OrgScopeResolverTest`.
- [ ] Add local browser QA evidence for the no-permission fallback after local deployment.
- [x] Build chunk warnings are accepted for local merge as a documented performance risk; production release still requires a separate bundle-budget decision.

## 7. Verification

| Command | Result |
| --- | --- |
| `python3 -m py_compile 02-迭代源代码/scripts/gen-unified-feature-audit.py` | Passed |
| `python3 02-迭代源代码/scripts/gen-unified-feature-audit.py` | Passed |
| `node -c 02-迭代源代码/aigicrm-ui/src/router/index.js` | Passed |
| `npm test -- --run` in `aigicrm-ui` | Passed, 10 files and 71 tests |
| `npm run build` in `aigicrm-ui` | Passed with existing warnings |
| `sonar list issues -p Eastdongjun_AigiCRM --statuses OPEN --format json` | Passed, 0 open issues returned |

## 8. Engineering Recommendation

Use PC as the functional baseline, but do not treat the current PC UI as a perfect blueprint. The safer path is:

1. Keep PC and Mini Program on one backend, one database, one token/session model and one permission/data-scope engine.
2. Convert PC routes, backend permissions and high-risk buttons into a shared feature manifest.
3. Let PC render the full desktop workflows and Mini Program render the same feature keys as mobile cards, tabs and action sheets.
4. Require backend permission and data-scope tests before marking any mobile parity module complete.
5. Keep production deployment blocked until local integrated QA and database permission regeneration are complete.
