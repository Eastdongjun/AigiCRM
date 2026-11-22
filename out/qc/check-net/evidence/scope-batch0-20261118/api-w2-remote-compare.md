# W2 远端实测比对（旧 jar `2ea180c6` + 新库 `aigicrm_prod_20261118`）
## oracle=old
- 用户数 28；不一致（extra/missing/配置少看任一非 0）**1**；HTTP 错误 0
| uid | 用户 | 范围 | oracle | API | extra | missing | 配置少看 |
|---|---|---|---|---|---|---|---|
| 1 | admin | ROLE/all | 295 | 295 | 0 | 0 | 0 |
| 11 | NO001 | USER/all | 295 | 295 | 0 | 0 | 0 |
| 38 | TY002 | ROLE/self | 27 | 27 | 0 | 0 | 0 |
| 39 | TY003 | ROLE/self | 34 | 34 | 0 | 0 | 0 |
| 40 | TY004 | USER/team | 49 | 49 | 0 | 0 | 0 |
| 41 | TY005 | USER/office | 17 | 17 | 0 | 0 | 104 |
| 42 | TY006 | USER/team | 11 | 11 | 0 | 0 | 0 |
| 43 | NM100 | USER/custom | 88 | 88 | 0 | 0 | 0 |
| 44 | NM002 | USER/team | 29 | 29 | 0 | 0 | 0 |
| 45 | TY007 | ROLE/self | 43 | 43 | 0 | 0 | 0 |
| 46 | NM003 | ROLE/self | 29 | 29 | 0 | 0 | 0 |
| 47 | TY008 | ROLE/self | 8 | 8 | 0 | 0 | 0 |
| 49 | NM004 | ROLE/self | 18 | 18 | 0 | 0 | 0 |
| 50 | TY010 | ROLE/self | 14 | 14 | 0 | 0 | 0 |
| 51 | NM005 | ROLE/self | 11 | 11 | 0 | 0 | 0 |
| 52 | TY011 | ROLE/self | 6 | 6 | 0 | 0 | 0 |
| 53 | TY012 | ROLE/self | 8 | 8 | 0 | 0 | 0 |
| 54 | NM006 | ROLE/self | 8 | 8 | 0 | 0 | 0 |
| 55 | TY013 | ROLE/self | 9 | 9 | 0 | 0 | 0 |
| 56 | TY014 | ROLE/self | 11 | 11 | 0 | 0 | 0 |
| 57 | NM007 | ROLE/self | 8 | 8 | 0 | 0 | 0 |
| 58 | TY015 | ROLE/self | 7 | 7 | 0 | 0 | 0 |
| 59 | TY016 | ROLE/self | 1 | 1 | 0 | 0 | 0 |
| 60 | TY017 | ROLE/self | 2 | 2 | 0 | 0 | 0 |
| 61 | TY100 | USER/custom | 122 | 122 | 0 | 0 | 0 |
| 62 | TY200 | USER/custom | 85 | 85 | 0 | 0 | 0 |
| 63 | TY018 | ROLE/self | 6 | 6 | 0 | 0 | 0 |
| 82 | NM008 | ROLE/self | 4 | 4 | 0 | 0 | 0 |

## oracle=b1a
- 用户数 28；不一致（extra/missing/配置少看任一非 0）**1**；HTTP 错误 0
| uid | 用户 | 范围 | oracle | API | extra | missing | 配置少看 |
|---|---|---|---|---|---|---|---|
| 1 | admin | ROLE/all | 295 | 295 | 0 | 0 | 0 |
| 11 | NO001 | OVERRIDE/all | 295 | 295 | 0 | 0 | 0 |
| 38 | TY002 | ROLE/self | 27 | 27 | 0 | 0 | 0 |
| 39 | TY003 | ROLE/self | 34 | 34 | 0 | 0 | 0 |
| 40 | TY004 | OVERRIDE/team | 49 | 49 | 0 | 0 | 0 |
| 41 | TY005 | OVERRIDE/office | 121 | 17 | 0 | 104 | 104 |
| 42 | TY006 | OVERRIDE/team | 11 | 11 | 0 | 0 | 0 |
| 43 | NM100 | OVERRIDE/custom | 88 | 88 | 0 | 0 | 0 |
| 44 | NM002 | OVERRIDE/team | 29 | 29 | 0 | 0 | 0 |
| 45 | TY007 | ROLE/self | 43 | 43 | 0 | 0 | 0 |
| 46 | NM003 | ROLE/self | 29 | 29 | 0 | 0 | 0 |
| 47 | TY008 | OVERRIDE/team | 8 | 8 | 0 | 0 | 0 |
| 49 | NM004 | ROLE/self | 18 | 18 | 0 | 0 | 0 |
| 50 | TY010 | ROLE/self | 14 | 14 | 0 | 0 | 0 |
| 51 | NM005 | ROLE/self | 11 | 11 | 0 | 0 | 0 |
| 52 | TY011 | ROLE/self | 6 | 6 | 0 | 0 | 0 |
| 53 | TY012 | ROLE/self | 8 | 8 | 0 | 0 | 0 |
| 54 | NM006 | OVERRIDE/team | 8 | 8 | 0 | 0 | 0 |
| 55 | TY013 | ROLE/self | 9 | 9 | 0 | 0 | 0 |
| 56 | TY014 | ROLE/self | 11 | 11 | 0 | 0 | 0 |
| 57 | NM007 | ROLE/self | 8 | 8 | 0 | 0 | 0 |
| 58 | TY015 | ROLE/self | 7 | 7 | 0 | 0 | 0 |
| 59 | TY016 | ROLE/self | 1 | 1 | 0 | 0 | 0 |
| 60 | TY017 | ROLE/self | 2 | 2 | 0 | 0 | 0 |
| 61 | TY100 | OVERRIDE/custom | 122 | 122 | 0 | 0 | 0 |
| 62 | TY200 | OVERRIDE/custom | 85 | 85 | 0 | 0 | 0 |
| 63 | TY018 | ROLE/self | 6 | 6 | 0 | 0 | 0 |
| 82 | NM008 | OVERRIDE/self | 4 | 4 | 0 | 0 | 0 |

