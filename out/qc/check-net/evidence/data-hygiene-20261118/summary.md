# A7 数据卫生画像（新快照 `aigicrm_prod_20261118`，2026-11-18）

> 只读 SQL，独立于应用层；93 张 BASE TABLE；生产零写入。执行者 QC `01-claude-1c`。
> 证据：`tables.tsv`（表清单）、`counts.tsv`（精确行数）、`columns.tsv`（982 列）、`hygiene.tsv`（129 项通用检查）、`business.tsv`（22 项业务引用检查）。

## 一、表规模

- BASE TABLE **93 张**；精确行数已全量取（`counts.tsv`）。
- 大表（行数 ≥ 万）与空表清单见 `counts.tsv`；核心表：`biz_customer` 295（非删口径，batch0 已核）、`sys_user` 69。

## 二、通用卫生（129 项）

| 检查 | 结果 |
|---|---|
| `deleted` 值非 0/1 或 NULL | **0**（所有含 deleted 列的表） |
| `deleted=1` 但 `deleted_at IS NULL` | **0** |
| `created_at` 未来日期 | **0** |
| `updated_at < created_at` | **6 行**，全部在 `biz_folder_type_default`（id 179-184），差 **-1 秒**——V2026.11.13 迁移种子插入时 `created_at DEFAULT CURRENT_TIMESTAMP` 与显式 `updated_at` 的 1 秒顺序差；**无业务影响，P3 观察项** |

## 三、业务引用完整性（22 项，全部 0）

- `sys_user`：重复 username 0、孤儿 dept 0、孤儿 manager 0
- `biz_customer`：重复 customer_code 0、孤儿 owner/office/dept 0
- `biz_customer_share`：孤儿 customer/user 0
- `biz_file`：孤儿 folder/uploader 0；`biz_file_grant`：孤儿 FILE 对象 0
- `sys_perm_grant`：孤儿 USER/ROLE/DEPT subject 0
- `biz_approval`：孤儿 applicant/customer 0
- `biz_incentive_record`：孤儿 user/customer/file 0
- `biz_reminder`：孤儿 owner 0
- `biz_score_entry`：孤儿 user 0

## 四、法布施专项（23 项引用，全部 0）

- `biz_fabushi_book`：孤儿 pdf_file 0、孤儿 creator 0；`section`：孤儿 book/parent 0
- `post`：孤儿 book/section/user 0；`post_attachment`：孤儿 post/file 0
- `comment`：孤儿 post/user/parent 0；`like`：孤儿 post/user 0
- `shelf`：孤儿 user/book 0；`read_status`：孤儿 user/post 0；`reading_progress`/`user_setting`：孤儿 user 0
- `activity`：孤儿 book 0；`activity_join`：孤儿 activity/user 0
- 证据：`fabushi.tsv`

## 五、名称与唯一性（客户域）

- `name_norm` 空值 0、与 `LOWER(REPLACE(name,' ',''))` 不匹配 0、`customer_name` 带首尾空格 0。
- **3 组同名活跃客户**（内蒙古汇能/乌兰浩特钢铁/大同煤矿圣厚源），每组一条 `dup_exempt=0`（占唯一索引）、一条 `dup_exempt=1`（`active_name=NULL` grandfather）——**V2026.11.17.1 的显式设计与豁免，非缺陷**；新客/改名严格唯一。
- `active_name` 生成列逻辑：`deleted=0 AND dup_exempt=0 → name_norm`，否则 NULL；与实测一致。

## 五之二、索引分析（A9 补充，2026-11-18）

- **重复索引 1 组**：`biz_file_version` 的 `idx_bfv_file` 与唯一索引 `uk_file_version` 同为 `(file_id, version_no)`——`idx_bfv_file` **冗余可删**（P3，唯一索引已覆盖前缀查找）。
- **仅主键无二级索引 8 表**：biz_incentive_grant、sys_dict_data、sys_menu、sys_perm_epoch、sys_permission_meta、sys_position_role、sys_scope_type、sys_user_archive——多为小表/配置表（96 菜单/字典），全表扫可接受；`sys_menu` 若将来增长可加 `permission` 索引。
- **索引数 TOP**：biz_customer 20（偏多，建议后续审冗余）、biz_file 9、biz_approval 7、biz_incentive_record 7、biz_sea_pool 7、biz_file_folder 6。
- 证据：`index-a9.tsv`

## 六、文件类型观察

- `biz_file` 存量含 1 个 `geek.exe`（admin 2026-08-04 测试上传）、7 个 `.wpt`、2 个 `.tif`——均**早于 2026-09-18 PR-01 N-09 上传白名单**（`ALLOWED_UPLOAD_EXTS` 不含 exe/wpt/tif），当前策略下不可再上传；存量可下载属历史遗留，建议 0b 评估是否清理 `geek.exe`（P3 数据清理，非当前缺陷）。

## 七、未覆盖（诚实边界）

- 各表 status 值域已取分布（`status-dist.tsv`），未发现意外枚举值；业务语义正确性需链上验证（D1）；
- 重复业务记录（同名客户不同 owner）由 `dup_exempt` 机制处理，已核；
- 文件-MD5 与 OSS 实际对象三方一致性属 A8，不在 A7。

## 五、命令（脱敏）

```bash
# 表清单 / 行数 / 列 / 通用卫生 / 业务检查
bash /tmp/qc-a7-tables.sh；/tmp/qc-a7-run.sh；/tmp/qc-a7-cols.sh；/tmp/qc-a7-hyg.sh；/tmp/qc-a7-biz.sh
# 远端依赖：/tmp/a7-counts.sql、/tmp/a7-hygiene.sql、/tmp/a7-biz.sql（scp 预置）
# 注意：docker exec -i 需要 stdin 重定向时必须用 < file，不可再追加 </dev/null（会覆盖）
```
