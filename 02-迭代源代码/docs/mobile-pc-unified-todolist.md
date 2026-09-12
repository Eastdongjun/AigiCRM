# PC and Mobile Unified Project Todo List

Date: 2026-09-06

## Goal

Make PC and mobile share the same backend data, account identity, permission model, audit behavior, and deployment discipline. PC and mobile differ only in page structure and interaction design.

## P0 Architecture Baseline

- [x] Define one-backend, one-account, one-permission, one-database architecture.
- [x] Keep production backup, private keys, local editor settings, and audit evidence out of final source merges.
- [x] Add mobile request/auth/API skeleton with demo and real-backend modes.
- [x] Move incentive read-status migration into Flyway as `V20`.
- [x] Confirm `/api/auth/info` response shape for mobile: user id, username, real name, department, roles, role name, data scope, permissions, and menus.
- [x] Document API response envelope used by both clients: success code, data field, message field, and auth failure behavior.
- [x] Decide whether mobile should use existing account/password login first, then add WeChat binding later.

## P1 Mobile Login And Permission Sync

- [x] Add mobile login page using existing backend login API.
- [x] Store backend token through `utils/auth.js`.
- [x] Load current user and permissions after login.
- [x] Add route guard behavior for pages that require login.
- [x] Add permission helpers for page entry visibility and button visibility.
- [x] Verify mobile permissions match PC permissions for the same account through the local source/API audit; live-account parity remains a manual QA item.
- [x] Add logout and token-expired handling.
- [x] Add a small manual QA checklist for login, permission refresh, and expired token. See `docs/mobile-manual-qa-checklist.md`; live execution remains a separate manual QA item.

## P2 Incentive Shared Workflow

- [x] Add backend read status table and read-stat permission migration.
- [x] Add PC read marker, unread badge, NEW badge, score sort, and read stats entry.
- [x] Optimize backend unread lookup to avoid per-record database queries.
- [x] Connect mobile incentive list to `/api/incentive/customer-records`.
- [x] Normalize backend customer-record payload for mobile cards.
- [x] Mark mobile detail views as read through `/api/incentive/records/{id}/read`.
- [x] Gate mobile read stats by `incentive:read-stats`.
- [x] Verify PC and mobile unread state changes are wired to the same backend read-state API; immediate live refresh remains a manual QA item.
- [x] Add backend tests for read stats permission and read marker idempotency.
- [x] Add frontend/mobile checklist for score sorting, NEW, unread, and permission-driven stats.

## P1 Manual QA Checklist

- [ ] Login with an existing PC account in mobile real-backend mode.
- [ ] Confirm `/api/auth/login` returns a token and `/api/auth/info` returns the same user id, real name, role, data scope, and permissions as PC.
- [ ] Confirm an account without `incentive:view` cannot open the mobile incentive tab.
- [ ] Confirm a token-expired response clears local token and redirects to login.
- [x] Confirm logout clears token, user profile, and permissions. Automated coverage: `scripts/test-miniprogram-request-failures.js`.

## P2 Manual QA Checklist

- [ ] Open PC and mobile with the same account and compare incentive customer card count.
- [ ] Sort by score high-to-low and low-to-high on mobile.
- [ ] Open a mobile incentive card with unread status, refresh PC and mobile, and confirm unread is cleared for the same user.
- [ ] Confirm an account with `incentive:read-stats` sees mobile read stats.
- [ ] Confirm an account without `incentive:read-stats` does not see mobile read stats and cannot call the backend stats endpoint.

## P3 Customer Shared Workflow

- [x] Map PC customer list fields to mobile card fields.
- [x] Add mobile customer search and detail page through existing customer APIs.
- [x] Reuse backend customer data-scope rules without duplicating them in mobile.
- [ ] Verify same user sees the same allowed customer set on PC and mobile.
- [x] Complete mobile and PC file preview/download entry for customer-related files. Backend relation table, scoped read/attach/detach APIs, mobile customer entry with attach/detach actions, and PC customer-detail file tab are implemented. Live permission-matrix QA remains tracked below. Design: [`ADR: Customer File Association`](permission/adr-20260909-customer-file-association.md).
- [x] Add tests or manual QA evidence for customer access boundaries.

## P3 Manual QA Checklist

- [ ] Login with the same account on PC and mobile, then compare `/api/customer/list` totals.
- [ ] Search the same keyword on PC and mobile and compare visible customer names.
- [ ] Open a customer visible on PC from mobile and confirm detail fields match.
- [ ] Try opening a customer outside the account data scope and confirm backend returns forbidden or not found.
- [ ] Confirm mobile customer page does not encode its own data-scope rules; visibility comes from backend response only.

## P4 File Shared Workflow

- [x] Reuse existing file download token and authorization flow on mobile.
- [x] Confirm WeChat preview support for image, PDF, Office document, and video file types.
- [x] Add mobile file list and preview interactions.
- [x] Verify file permission rules are enforced by backend only.
- [x] Add failure handling for missing file, forbidden file, and expired download token.

## P4 Manual QA Checklist

- [ ] Login with a user that has `file:view` and confirm mobile customer-file list matches PC customer-file list for the same customer.
- [ ] Login without `file:view` and confirm the mobile file tab redirects back to home.
- [ ] Open an image file and confirm WeChat image preview uses backend download authorization.
- [ ] Open a PDF or Office document and confirm `wx.openDocument` works after `wx.downloadFile`.
- [ ] Try a forbidden file and confirm the backend denial is surfaced as a mobile failure toast.

## P5 Approval Shared Workflow

- [x] Add mobile approval todo list.
- [x] Add approve/reject actions using existing backend approval APIs.
- [x] Reuse backend permission and ownership checks.
- [ ] Verify PC and mobile approval state synchronize after action.
- [x] Add audit-log evidence for mobile approval actions. The mobile approval contract now checks approve/reject routes and the backend persistence of node action, comment, and operatedAt; `ApprovalApproveRejectSecurityTest` covers the service behavior.

## P5 Manual QA Checklist

- [ ] Login with a user that has `approval:view` and confirm mobile approval list matches PC approval list for the same tab.
- [ ] Login without `approval:view` and confirm the mobile approval tab redirects back to home.
- [ ] Login with `approval:approve` and approve a pending item on mobile, then refresh PC and confirm status is updated.
- [ ] Login with `approval:reject` and reject a pending item on mobile, then refresh PC and confirm status is updated.
- [ ] Confirm a user without approve or reject permission can view permitted approvals but does not see action buttons.
- [x] Confirm mobile approve/reject writes the same backend audit log path as PC actions. Both clients call `ApprovalController` and `ApprovalService`, which persist node `action`, `comment`, and `operatedAt`; `ApprovalApproveRejectSecurityTest` covers the persisted approve action/comment.

## P6 Sea Pool And Operations

- [x] Identify mobile-safe sea pool workflows.
- [x] Add mobile read-only sea pool list first.
- [ ] Add claim/edit actions only after permission and data-scope checks are verified.
- [ ] Verify same role has matching visibility on PC and mobile.

## P6 Manual QA Checklist

- [ ] Login with a user that has `sea:view` and confirm mobile public sea list matches PC for the same filters.
- [ ] Login without `sea:view` and confirm mobile public sea page redirects back to home.
- [ ] Confirm visible pools come from `/api/sea/configs/visible`, not local hard-coded pool rules.
- [ ] Confirm mobile only displays `canClaim`, `canEdit`, and `canDelete` returned by backend and does not execute those actions in this phase.
- [ ] Compare statistics from PC and mobile for total, new-this-month, pending approval, and claimed-this-month.

## P7 Account Binding Evolution

- [ ] Add optional `sys_user_wechat_binding` design.
- [ ] Add WeChat openid/unionid binding only after account/password login is stable.
- [ ] Add bind, unbind, and duplicate-binding protection.
- [ ] Add account recovery behavior when WeChat identity is unbound.

## P8 Quality And Release Gates

- [x] Run `npm test` in `aigicrm-ui`.
- [x] Run `npm run build` in `aigicrm-ui`.
- [x] Run `mvn test` in `aigicrm-backend`.
- [x] Run `mvn -DskipTests compile` in `aigicrm-backend`.
- [x] Run `git diff --check`.
- [ ] Query SonarQube issues for `Eastdongjun_AigiCRM` (server-side verification remains incomplete: local Vortex unavailable).
- [ ] Verify Flyway migration order and production backup readiness before deployment.
- [ ] Confirm no private key, production backup snapshot, local editor config, or audit-only evidence is staged for final merge. Local workspace files remain outside the intended source merge set; final staging review is still required before delivery.

## Merge Policy

Merge:

- Backend source and Flyway migrations required by shared data and permission behavior.
- PC source changes that consume the shared backend behavior.
- Mobile source once it uses shared auth, request, API, and permission infrastructure.
- Architecture and implementation docs that define final-source behavior.

Do not merge:

- `.claude/`, `.codex/`, `.vscode/`, local workspace files, and machine-specific settings.
- `08-备份索引/`, production snapshots, private keys, generated archives, and runtime logs.
- Audit evidence unless the user explicitly asks to keep it as a project deliverable.

## API Contract Notes

- Login endpoint: `POST /api/auth/login`.
- Current user endpoint: `GET /api/auth/info`.
- Response envelope: successful calls use `code=200` and return payload in `data`; failures return a non-200 `code` or HTTP error with `message`.
- Mobile starts with existing account/password login. WeChat binding is a later optional account convenience, not a separate identity source.
