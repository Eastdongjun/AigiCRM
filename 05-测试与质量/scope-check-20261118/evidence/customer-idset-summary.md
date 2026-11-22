# 客户域条目级（ID 集合）比对 · 28 启用用户

- 生成：2026-11-18 15:43:09
- 口径：oracle 最终可见集合 vs `GET /api/customer/list` 全量 ID；另比关键字段 owner_id/dept_id/status
- 用户数：28；有不一致：1

| uid | 用户 | 范围 | codes | anchor | oracle | API | extra | missing | 配置集合少看 | 字段不一致 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | admin | ROLE | all | OWNER | 294 | 294 | 0 | 0 | 0 | 0 |
| 11 | NO001 | USER | all | OWNER | 294 | 294 | 0 | 0 | 0 | 0 |
| 38 | TY002 | ROLE | self | OWNER | 27 | 27 | 0 | 0 | 0 | 0 |
| 39 | TY003 | ROLE | self | OWNER | 34 | 34 | 0 | 0 | 0 | 0 |
| 40 | TY004 | USER | team | OWNER | 49 | 49 | 0 | 0 | 0 | 0 |
| 41 | TY005 | USER | office | OWNER | 17 | 17 | 0 | 0 | 104 | 0 |
| 42 | TY006 | USER | team | OWNER | 11 | 11 | 0 | 0 | 0 | 0 |
| 43 | NM100 | USER | custom | ORG | 88 | 88 | 0 | 0 | 0 | 0 |
| 44 | NM002 | USER | team | OWNER | 29 | 29 | 0 | 0 | 0 | 0 |
| 45 | TY007 | ROLE | self | OWNER | 43 | 43 | 0 | 0 | 0 | 0 |
| 46 | NM003 | ROLE | self | OWNER | 29 | 29 | 0 | 0 | 0 | 0 |
| 47 | TY008 | ROLE | self | OWNER | 8 | 8 | 0 | 0 | 0 | 0 |
| 49 | NM004 | ROLE | self | OWNER | 18 | 18 | 0 | 0 | 0 | 0 |
| 50 | TY010 | ROLE | self | OWNER | 14 | 14 | 0 | 0 | 0 | 0 |
| 51 | NM005 | ROLE | self | OWNER | 11 | 11 | 0 | 0 | 0 | 0 |
| 52 | TY011 | ROLE | self | OWNER | 6 | 6 | 0 | 0 | 0 | 0 |
| 53 | TY012 | ROLE | self | OWNER | 8 | 8 | 0 | 0 | 0 | 0 |
| 54 | NM006 | ROLE | self | OWNER | 8 | 8 | 0 | 0 | 0 | 0 |
| 55 | TY013 | ROLE | self | OWNER | 9 | 9 | 0 | 0 | 0 | 0 |
| 56 | TY014 | ROLE | self | OWNER | 11 | 11 | 0 | 0 | 0 | 0 |
| 57 | NM007 | ROLE | self | OWNER | 8 | 8 | 0 | 0 | 0 | 0 |
| 58 | TY015 | ROLE | self | OWNER | 6 | 6 | 0 | 0 | 0 | 0 |
| 59 | TY016 | ROLE | self | OWNER | 1 | 1 | 0 | 0 | 0 | 0 |
| 60 | TY017 | ROLE | self | OWNER | 2 | 2 | 0 | 0 | 0 | 0 |
| 61 | TY100 | USER | custom | ORG | 122 | 122 | 0 | 0 | 0 | 0 |
| 62 | TY200 | USER | custom | ORG | 84 | 84 | 0 | 0 | 0 | 0 |
| 63 | TY018 | ROLE | self | OWNER | 6 | 6 | 0 | 0 | 0 | 0 |
| 82 | NM008 | ROLE | self | OWNER | 4 | 4 | 0 | 0 | 0 | 0 |

## 不一致明细

- uid=41 TY005（USER/office/OWNER）：oracle=17，API=17，extra=0，missing=0，配置集合少看=104，字段不一致=0
