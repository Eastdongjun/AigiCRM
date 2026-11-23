# 法布施 2026-11-23 变更 · 新增表结构补充

> 说明：`docs/database/data-dictionary.md` 由 `scripts/gen-data-dictionary.sh` 从**线上 information_schema** 自动生成，文件头明示「禁止手改」。
> 本次变更尚未部署，线上库还没有这 3 张表，因此**不在主字典手改**；先以本补充文件随代码评审，部署窗口后重跑脚本并入主字典。
> 迁移源：`aigicrm-backend/src/main/resources/db/migration/V2026.11.23.{2,3,4}__*.sql`。

## biz_fabushi_recitation 复诵（V2026.11.23.2）

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI | 主键 |
| `post_id` | bigint | NO | NULL | MUL | 被复诵的法布施内容 |
| `user_id` | bigint | NO | NULL | MUL | 复诵人 |
| `audio_file_id` | bigint | NO | NULL |  | 复诵语音文件（scope=fabushi） |
| `duration_seconds` | int | YES | NULL |  | 时长（≤60s） |
| `deleted` | tinyint | NO | 0 |  | 逻辑删 |
| `tenant_id` | bigint | NO | 1 |  | 集团隔离 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP | MUL | 创建时间 |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

## biz_fabushi_shelf_group 书架分组（V2026.11.23.3）

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI | 主键 |
| `user_id` | bigint | NO | NULL | MUL | 自建组=所属用户；自动模板组=0 |
| `name` | varchar(64) | NO | NULL |  | 分组名（自动组=类别字典 label） |
| `group_type` | varchar(20) | NO | custom |  | custom/auto |
| `category_code` | varchar(64) | YES | NULL |  | 自动组对应 fabushi_category value |
| `sort_no` | int | NO | 0 |  | 排序号 |
| `tenant_id` | bigint | NO | 1 |  | 集团隔离 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

唯一键：`uk_fb_shelf_group_user_name(user_id, name)`。

## biz_fabushi_bookmark 阅读器页码书签（V2026.11.23.4）

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI | 主键 |
| `user_id` | bigint | NO | NULL | MUL | 书签归属人 |
| `book_id` | bigint | NO | NULL | MUL | 法布施书目 |
| `page` | int | NO | NULL |  | 页码（1 起） |
| `note` | varchar(200) | YES | NULL |  | 可选备注 |
| `deleted` | tinyint | NO | 0 |  | 逻辑删 |
| `tenant_id` | bigint | NO | 1 |  | 集团隔离 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

索引：`idx_fb_bm_user_book(user_id, book_id, deleted)`、`idx_fb_bm_page(book_id, page)`。同人同书同页由服务层幂等，不建唯一键（逻辑删后同页可再标记）。

## 同批新增列（非新表，主字典部署后自动反映）

| 表 | 列 | 说明 |
|---|---|---|
| `biz_fabushi_post` | `scope_type` 等 | D1 类型重构（V2026.11.23.1） |
| `biz_fabushi_shelf` | `pinned` / `sort_no` / `group_id` / `finished_at` | D20/D23 书架管理（V2026.11.23.3） |
| `biz_fabushi_book` | `layout_type` | D19 版式标记 text/scan（V2026.11.23.4） |
