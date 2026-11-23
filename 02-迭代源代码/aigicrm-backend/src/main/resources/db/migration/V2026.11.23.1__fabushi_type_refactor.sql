-- V2026.11.23.1 法布施类型体系重构（D1）+ 菜单命名排序（D4/D5/D6）+ 提醒频率字典统一
-- 依据：REQUIREMENT-DELTA-20261122（已确认 2026-11-23）；Q1/Q3 映射=用户拍板
--   旧 recitation(诵读) → songdu(每日诵读)；旧 heart(心得)+znl(正能量) → suishi(每日随施)；新增 zaidu(再读一本)
-- 纪律：全部自然键定位（menu_type+path / dict_code / 枚举值），禁止硬编码行 id；
--       迁移末尾守卫：若仍残留旧 scope_type 值，用 PREPARE 动态语句制造失败使 Flyway 中断回滚
--       （不能把不存在的列写进 IF 未命中分支——MySQL 解析期就报 1054，空库也跑不过）。
-- 对账口径：迁移前后条数一致（songdu 数 = 旧 recitation 数；suishi 数 = 旧 heart 数 + 旧 znl 数），
--       验证 SQL 见文末注释，数字随空库重放/部署日志入证据（03-阶段/06-测试/02-证据/）。

-- ───────── 1. 内容类型迁移（幂等：仅匹配旧值） ─────────
UPDATE biz_fabushi_post SET scope_type = 'songdu' WHERE scope_type = 'recitation';
UPDATE biz_fabushi_post SET scope_type = 'suishi' WHERE scope_type IN ('heart', 'znl');

ALTER TABLE biz_fabushi_post
    MODIFY COLUMN `scope_type` varchar(20) NOT NULL COMMENT '内容类型: songdu=每日诵读 suishi=每日随施 zaidu=再读一本 (D1 2026-11-23 重构, 原 heart/recitation/znl 已迁移)';

-- ───────── 2. 菜单改名（不动 menu_id，sys_role_menu 绑定保持） ─────────
UPDATE sys_menu SET menu_name = '认知广场' WHERE menu_type = 2 AND path = '/fabushi/feed';
UPDATE sys_menu SET menu_name = '我的觉醒' WHERE menu_type = 2 AND path = '/fabushi/shelf';

-- 法布施组内排序：我的觉醒(1) → 认知广场(2) → 书目浏览(3) → 书目管理(4) → 内容治理(5) → 统计看板(6)
-- 列名以 V1__baseline.sql 为准：sys_menu.parent_id / sys_menu.sort_order
-- INV-MIG-001：父组按 menu_type + menu_name 自然键 JOIN 定位，不写 parent_id = 数字（禁硬编码行 id 定位）；
--            子行再以 path 前缀收敛，双重自然键，无数字常量。
UPDATE sys_menu m
JOIN sys_menu g ON g.id = m.parent_id AND g.menu_type = 1 AND g.menu_name = '法布施'
SET m.sort_order = CASE m.path
    WHEN '/fabushi/shelf'   THEN 1
    WHEN '/fabushi/feed'    THEN 2
    WHEN '/fabushi/library' THEN 3
    WHEN '/fabushi/book'    THEN 4
    WHEN '/fabushi/content' THEN 5
    WHEN '/fabushi/stats'   THEN 6
    ELSE m.sort_order END
WHERE m.menu_type = 2 AND m.path LIKE '/fabushi/%';

-- ───────── 3. 提醒频率字典统一：workday → weekday（代码白名单 FabushiReadingService= daily/weekday/weekend） ─────────
UPDATE sys_dict_data d
JOIN sys_dict_type t ON d.dict_type_id = t.id AND t.dict_code = 'fabushi_remind_freq'
SET d.dict_value = 'weekday'
WHERE d.dict_value = 'workday';

UPDATE biz_fabushi_user_setting SET freq_code = 'weekday' WHERE freq_code = 'workday';

-- ───────── 4. 守卫：残留旧 scope_type 即失败回滚 ─────────
-- 注意：MySQL 在解析期就校验列名/表名，把「不存在的列」写进 IF 的未命中分支同样会 1054 报错，
--       会导致空库也迁移失败。故改用 PREPARE 动态语句：仅当残留数 > 0 时才准备会失败的那条 SQL。
SET @remain_old_scope := (
    SELECT COUNT(*) FROM biz_fabushi_post
    WHERE scope_type IN ('heart', 'recitation', 'znl') OR scope_type IS NULL
);
SET @guard_scope_sql := IF(@remain_old_scope > 0,
    'SELECT * FROM __MIGRATION_FAILED_仍存在旧scope_type值_请勿手工改数据_回滚排查__',
    'SELECT ''OK'' AS scope_migration_guard');
PREPARE guard_scope FROM @guard_scope_sql;
EXECUTE guard_scope;
DEALLOCATE PREPARE guard_scope;

SET @remain_old_freq := (
    SELECT COUNT(*) FROM sys_dict_data d
    JOIN sys_dict_type t ON d.dict_type_id = t.id AND t.dict_code = 'fabushi_remind_freq'
    WHERE d.dict_value = 'workday'
);
SET @guard_freq_sql := IF(@remain_old_freq > 0,
    'SELECT * FROM __MIGRATION_FAILED_提醒频率字典仍有workday__',
    'SELECT ''OK'' AS freq_migration_guard');
PREPARE guard_freq FROM @guard_freq_sql;
EXECUTE guard_freq;
DEALLOCATE PREPARE guard_freq;

-- ───────── 对账验证 SQL（T4 证据用，不随迁移执行） ─────────
-- SELECT scope_type, COUNT(*) FROM biz_fabushi_post GROUP BY scope_type;
--   期望：仅 songdu/suishi/zaidu；songdu 数=迁移前 recitation 数；suishi 数=迁移前 heart+znl 数；总量不变。
-- SELECT d.dict_label, d.dict_value FROM sys_dict_data d JOIN sys_dict_type t ON d.dict_type_id=t.id
--   WHERE t.dict_code='fabushi_remind_freq' ORDER BY d.sort_order;
--   期望：每天/daily、工作日/weekday、休息日/weekend。
-- SELECT menu_name, path, sort_order FROM sys_menu WHERE menu_type=2 AND path LIKE '/fabushi/%' ORDER BY sort_order;
--   期望：我的觉醒1 / 认知广场2 / 书目浏览3 / 书目管理4 / 内容治理5 / 统计看板6。
