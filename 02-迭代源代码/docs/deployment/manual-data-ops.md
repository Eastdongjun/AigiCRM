# 手工数据操作登记册（Flyway 外数据变更）

> 纪律：所有不经过 Flyway 迁移的数据库写操作（修复脚本、清理工具、一次性的 UPDATE/DELETE），
> 必须在本文件登记后才能在生产执行；无登记 = 不可执行。每条登记必须包含：用途、批准状态、
> 执行记录、回滚方式。测试环境执行同样登记（标注环境）。
> —— 2026-11-11 审计 N5 整改新设（此前手工工具无登记通道，与"所有库变更走迁移"纪律存在张力）。

## 登记条目

### 1. fix-file-perm-dedup.py —— 文件权限授权表去重

- **用途**: 清理 2026-11-10 双写期竞态产生的 `biz_folder_acl`/`biz_folder_type_default`
  重复行（public 区同键 6 行 + 词内重复），恢复权限判定的唯一行语义。
- **为什么绕过 Flyway**: 2026-11-10 治本批尝试过三种 SQL 迁移形态全部翻车
  （临时表跨 collation / 同表子查询 MySQL 1093 / JSON_TABLE 相关子查询丢词），
  且生产修复需先人工核对 diff 后执行；独立 flyway 容器架构下 Java 迁移不可用。
  最终形态 = python 本地计算全表快照 → 仅生成按主键的朴素 UPDATE/DELETE 语句。
- **批准状态**: ⏳ 等用户令（生产）。测试环境已于 2026-11-10 执行验证（幂等复跑 0 变更）。
- **执行记录**: （生产执行后填写：日期/操作人/影响行数/前后计数）
- **回滚方式**: 执行前 mysqldump 全量备份对应表；异常时回灌备份表。
  工具本身幂等（重复执行 0 变更），无需反向脚本。
- **关联**: scripts/fix-file-perm-dedup.py；a3291231（治本⑤）；00-项目管理 线上稳定守护提示词。

### 2. flyway_schema_history 空转重复行清理 —— 测试库

- **用途**: 删除 2026-11-05 02:08/03:22 两批空转事故在测试快照库
  （aigicrm_prod_20261016）写入的 32 行同版本重复 history 行
  （V2~V11 type=DELETE 10 行 + V12~V33 双行中 11-05 批 22 行），恢复每版单行。
- **批准状态**: ✅ 2026-11-11 执行（T1 空库重放整改的配套动作，测试环境）。
- **执行记录**: 2026-11-11 深夜；删除 32 行；删前整表快照 `_repair_bak_fsh_20261111`；
  删后数字版 33 版每版 1 行；deploy-db-test validate 恢复绿。
- **回滚方式**: `INSERT INTO flyway_schema_history SELECT * FROM _repair_bak_fsh_20261111 WHERE ...`
  （按 installed_on 前缀过滤 2026-11-05 02:08/03:22 批次行）。
- **关联**: 本文件同日 deploy-db-test.sh 口径厘清注释；scripts/replay-empty-db.sh。

### 3. flyway_schema_history 补 .05.9 对齐行 + 清 DELETE 空转行 —— 测试库

- **用途**: V2026.11.05.9__remove_file_edit_dead_code.sql 找回主目录后(从 6ac68aa1 逐字节
  恢复, 生产 checksum 322618291), 测试库 history 无行会导致 migrate 想补跑 .05.9——
  它会删掉 .05.10 已重建的 file:edit 节点(破坏现状)。补一行与生产对齐的 applied 行;
  同版 DELETE 型空转遗迹行(11-05 批次)一并清除。
- **批准状态**: ✅ 2026-11-11 执行(T7 整改, 测试环境)。
- **执行记录**: 补 SQL 行(checksum=322618291=生产权威值); 删 1 行 DELETE 空转行;
  validate 122 migrations 全绿。
- **回滚方式**: 删该 INSERT 行即可(纯 history 层操作, 不涉业务表)。
- **关联**: FROZEN-SHA256.txt T7 补登记段; scripts/replay-empty-db.sh 复跑 110 迁移全绿。
