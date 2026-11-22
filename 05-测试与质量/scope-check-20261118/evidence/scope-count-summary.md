# 客户域数据范围计数比对（28 启用用户）

- 生成：2026-11-18 15:21:17
- 口径：配置应见 vs 配置+auto_share 应见 vs API `counts-by-filter`
- auto_share：`{"superior": true, "admin": true, "roles": [], "users": []}`
- 系统开关：superior=True, leader=True
- 用户数：28；总数不一致：0；页签计数不一致：0

| uid | 用户 | 角色 | 范围类型 | codes | anchor | 配置应见 | +auto_share | API实际 | diff(最终) | 集合少看 | 集合多看 | 说明 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | admin | admin | ROLE | all | OWNER | 294 | 294 | 294 | 0 | 0 | 0 | all/no-config |
| 11 | NO001 | 0 | USER | all | OWNER | 294 | 294 | 294 | 0 | 0 | 0 | all/no-config |
| 38 | TY002 | 06 | ROLE | self | OWNER | 27 | 27 | 27 | 0 | 0 | 0 | no-share |
| 39 | TY003 | 06 | ROLE | self | OWNER | 34 | 34 | 34 | 0 | 0 | 0 | no-share |
| 40 | TY004 | 06 | USER | team | OWNER | 49 | 49 | 49 | 0 | 0 | 0 | narrowed |
| 41 | TY005 | 06 | USER | office | OWNER | 121 | 17 | 17 | 0 | 104 | 0 | narrowed |
| 42 | TY006 | 06 | USER | team | OWNER | 11 | 11 | 11 | 0 | 0 | 0 | narrowed |
| 43 | NM100 | 05 | USER | custom | ORG | 88 | 88 | 88 | 0 | 0 | 0 | no-share |
| 44 | NM002 | 06 | USER | team | OWNER | 29 | 29 | 29 | 0 | 0 | 0 | narrowed |
| 45 | TY007 | 06 | ROLE | self | OWNER | 43 | 43 | 43 | 0 | 0 | 0 | no-share |
| 46 | NM003 | 06 | ROLE | self | OWNER | 29 | 29 | 29 | 0 | 0 | 0 | no-share |
| 47 | TY008 | 06 | ROLE | self | OWNER | 8 | 8 | 8 | 0 | 0 | 0 | no-share |
| 49 | NM004 | 06 | ROLE | self | OWNER | 18 | 18 | 18 | 0 | 0 | 0 | no-share |
| 50 | TY010 | 06 | ROLE | self | OWNER | 14 | 14 | 14 | 0 | 0 | 0 | no-share |
| 51 | NM005 | 06 | ROLE | self | OWNER | 11 | 11 | 11 | 0 | 0 | 0 | no-share |
| 52 | TY011 | 06 | ROLE | self | OWNER | 6 | 6 | 6 | 0 | 0 | 0 | no-share |
| 53 | TY012 | 06 | ROLE | self | OWNER | 8 | 8 | 8 | 0 | 0 | 0 | no-share |
| 54 | NM006 | 06 | ROLE | self | OWNER | 8 | 8 | 8 | 0 | 0 | 0 | no-share |
| 55 | TY013 | 06 | ROLE | self | OWNER | 9 | 9 | 9 | 0 | 0 | 0 | no-share |
| 56 | TY014 | 06 | ROLE | self | OWNER | 11 | 11 | 11 | 0 | 0 | 0 | no-share |
| 57 | NM007 | 06 | ROLE | self | OWNER | 8 | 8 | 8 | 0 | 0 | 0 | no-share |
| 58 | TY015 | 06 | ROLE | self | OWNER | 6 | 6 | 6 | 0 | 0 | 0 | no-share |
| 59 | TY016 | 06 | ROLE | self | OWNER | 1 | 1 | 1 | 0 | 0 | 0 | no-share |
| 60 | TY017 | 06 | ROLE | self | OWNER | 2 | 2 | 2 | 0 | 0 | 0 | no-share |
| 61 | TY100 | 05 | USER | custom | ORG | 122 | 122 | 122 | 0 | 0 | 0 | no-share |
| 62 | TY200 | 05 | USER | custom | ORG | 84 | 84 | 84 | 0 | 0 | 0 | no-share |
| 63 | TY018 | 06 | ROLE | self | OWNER | 6 | 6 | 6 | 0 | 0 | 0 | no-share |
| 82 | NM008 | 06 | ROLE | self | OWNER | 4 | 4 | 4 | 0 | 0 | 0 | no-share |

## 页签计数不一致明细

无。全部 28 用户 × 7 页签一致。

## 总数不一致明细

无。
