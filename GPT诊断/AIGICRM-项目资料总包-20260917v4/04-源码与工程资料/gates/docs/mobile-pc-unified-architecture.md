# PC and Mobile Unified Architecture

Date: 2026-09-06

## Goal

AIGICRM uses one backend, one account system, one permission model, and one production database. PC and mobile clients differ only in presentation and interaction density.

## Architecture

```text
PC Vue admin
        \
         -> Spring Boot API -> MySQL / Redis / file storage
        /
WeChat Mini Program
```

The backend remains the source of truth for business rules, data scope, permissions, audit logs, and file access. Mobile pages must not duplicate permission rules or maintain a separate data store.

## Shared Domains

- Account: `sys_user`, `sys_role`, `sys_user_role`, department, group, and position data are shared.
- Permission: backend authorities such as `incentive:view`, `incentive:read-stats`, `customer:view`, and `approval:view` are shared.
- Business data: customer, file, incentive, approval, sea pool, and read status use the same tables and services.
- Session: PC and mobile may use different token storage, but both tokens represent the same `sys_user`.

## Client Responsibilities

PC:

- Dense table and management workflows.
- Role and system configuration.
- Full operational controls.

Mobile:

- Card-oriented browsing.
- Quick search, approval, reading, and field feedback.
- Same permissions, lighter screen layout.

The mobile client can have mobile-friendly endpoints such as `/api/mobile/home`, but those endpoints must call the same backend services and permission guards.

## Phase Plan

1. P0: Preserve the current backend and PC behavior, add formal mobile architecture and request/auth skeleton.
2. P1: Connect mobile login to existing backend identity, then load current user and authorities.
3. P2: Make incentive query the first shared workflow: list, score sort, NEW, unread, mark read, and read stats.
4. P3: Add customer, file, approval, and sea pool workflows one module at a time.
5. P4: Add optional WeChat account binding using a dedicated binding table after account/password login is stable.

## Merge Rule

Merge shared backend APIs, Flyway migrations, PC implementation, and mobile infrastructure. Do not merge local editor settings, production backup snapshots, private keys, or audit evidence into the final source repository.
