-- ============================================================
-- V2026.11.15.1__fabushi_module.sql
-- 【法布施模块一期底座】(技术设计方案 v2.0 2026-11-13 + 需求说明书 v1.1, 2026-11-15 用户令"接入同一个权限中心"开工)
-- 内容按序:
--   1. biz_fabushi_* 12 张表(设计文档 11 张 + biz_fabushi_like 点赞表补缺: 需求 D4"点赞=敲木鱼"
--      计数需落库, 文档 2.2 表清单遗漏, 2026-11-15 规划补上)
--   2. sys_dict_type 6 类型(built_in=1) + 种子(value=稳定英文code, label=中文名——10 大类名称
--      待甲方确认, 改名只动 label 不断 category_code 引用)
--   3. sys_resource + sys_action 9 码注册(WP7 三行调用契约)
--   4. sys_menu 三级节点(组标签 type1 + 3 页面 type2 + 6 按钮 type3, 全自然键锚定+NOT EXISTS 幂等)
--   5. sys_role_menu 角色绑定(admin 全 9 码 / 销售总监0 view+create+stats:view / 行政05+销售06 view+create,
--      =设计文档 §3.2 默认矩阵; book:view/content:view 默认仅 admin)
--   6. role_perm_scope: (incentive,stats_view) → (fabushi,stats_view) 逐行拷贝
--      (照 V2026.11.05.7 拍板 B 模式; 团队统计可见集跟随激励统计档, 权限中心可独立改)
--   7. UPDATE sys_user SET perm_version = perm_version + 1 (权限缓存自愈)
-- 设计纠偏(vs 技术设计方案 v2.0): 包名 com.aigicrm.module.fabushi(LogAspect 切面覆盖硬条件);
--   表前缀 biz_fabushi_*(仓库惯例+租户白名单); fb_post.group_id 列退役(租户列由拦截器维护的
--   tenant_id 取代, 业务代码不读写); 全表无外键约束(业务层维护一致性, 降人员删除耦合, 加索引即可)。
-- 回滚(人工): DROP TABLE 12 张 biz_fabushi_*; DELETE sys_action WHERE code LIKE 'fabushi:%';
--   DELETE FROM sys_resource WHERE module_code='fabushi';
--   DELETE m FROM sys_role_menu m JOIN sys_menu s ON s.id=m.menu_id WHERE s.permission LIKE 'fabushi:%';
--   DELETE FROM sys_menu WHERE permission LIKE 'fabushi:%';
--   DELETE FROM sys_menu WHERE menu_name='法布施' AND menu_type=1 AND parent_id=0;
--   DELETE FROM sys_dict_data WHERE dict_type_id IN (SELECT id FROM sys_dict_type WHERE dict_code LIKE 'fabushi_%');
--   DELETE FROM sys_dict_type WHERE dict_code LIKE 'fabushi_%';
--   DELETE FROM role_perm_scope WHERE module='fabushi';
--   UPDATE sys_user SET perm_version = perm_version + 1;
-- ============================================================

-- ============ 1. 业务表(12 张, utf8mb4_general_ci 显式; tenant_id 由租户插件维护) ============

-- 1.1 书目(类别=字典 fabushi_category; 人人可提交 status=pending, admin 审核后展示)
CREATE TABLE IF NOT EXISTS `biz_fabushi_book` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_code` varchar(50) NOT NULL COMMENT '类别(字典 fabushi_category.value)',
  `title` varchar(200) NOT NULL COMMENT '书名',
  `author` varchar(100) DEFAULT NULL COMMENT '作者',
  `publisher` varchar(100) DEFAULT NULL COMMENT '出版社/版本来源',
  `edition_type` varchar(50) DEFAULT NULL COMMENT '版本类型(字典 fabushi_edition_type.value)',
  `pages` int DEFAULT NULL COMMENT '页数',
  `pdf_file_id` bigint DEFAULT NULL COMMENT '电子书PDF文件id(空=未挂载)',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending待审核/approved通过/rejected驳回/disabled停用',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回原因',
  `created_by` bigint NOT NULL COMMENT '提交人',
  `approved_by` bigint DEFAULT NULL COMMENT '审核人',
  `approved_at` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_book_category` (`category_code`, `status`),
  KEY `idx_fb_book_creator` (`created_by`),
  KEY `idx_fb_book_pdf` (`pdf_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施书目';

-- 1.2 章节结构树(序/部分/章/节, 字典 fabushi_section_level)
CREATE TABLE IF NOT EXISTS `biz_fabushi_section` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `book_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父章节(0=根)',
  `level_code` varchar(50) NOT NULL COMMENT '层级(字典 fabushi_section_level.value)',
  `title` varchar(200) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_sec_book` (`book_id`, `parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施书目章节结构';

-- 1.3 统一内容表(心得heart/诵读recitation/正能量znl 共用, scope_type 区分)
CREATE TABLE IF NOT EXISTS `biz_fabushi_post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scope_type` varchar(20) NOT NULL COMMENT '内容类型: heart心得/recitation诵读/znl正能量',
  `book_id` bigint DEFAULT NULL COMMENT '关联书目(正能量可空)',
  `section_id` bigint DEFAULT NULL COMMENT '关联章节(诵读挂结构, 通读可空)',
  `user_id` bigint NOT NULL COMMENT '发布人',
  `title` varchar(200) DEFAULT NULL COMMENT '标题(正能量流可空)',
  `content` text COMMENT '文字内容',
  `status` varchar(20) NOT NULL DEFAULT 'published' COMMENT 'published已发布/offline已下架',
  `offline_by` bigint DEFAULT NULL COMMENT '下架操作人',
  `offline_at` datetime DEFAULT NULL,
  `offline_reason` varchar(500) DEFAULT NULL COMMENT '下架原因(留痕)',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_post_scope` (`scope_type`, `status`, `created_at`),
  KEY `idx_fb_post_user` (`user_id`),
  KEY `idx_fb_post_book` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施统一内容(心得/诵读/正能量)';

-- 1.4 内容附件(图/音/视, file_id 指向 biz_file, scope=fabushi 命名空间不进文件管理列表)
CREATE TABLE IF NOT EXISTS `biz_fabushi_post_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `file_type` varchar(20) NOT NULL COMMENT 'image/audio/video',
  `file_id` bigint NOT NULL COMMENT 'biz_file.id',
  `sort_order` int NOT NULL DEFAULT '0',
  `duration_seconds` int DEFAULT NULL COMMENT '音/视频时长(秒)',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_att_post` (`post_id`, `sort_order`),
  KEY `idx_fb_att_file` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施内容附件';

-- 1.5 统一评论(text/highlight重点/audio语音跟读 共用)
CREATE TABLE IF NOT EXISTS `biz_fabushi_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '评论人',
  `type` varchar(20) NOT NULL DEFAULT 'text' COMMENT 'text文字/highlight重点/audio语音跟读',
  `content` varchar(2000) DEFAULT NULL COMMENT '文字内容(text/highlight)',
  `audio_file_id` bigint DEFAULT NULL COMMENT '语音文件id(audio)',
  `parent_comment_id` bigint DEFAULT NULL COMMENT '父评论(0/NULL=顶级)',
  `status` varchar(20) NOT NULL DEFAULT 'published' COMMENT 'published/offline',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_cmt_post` (`post_id`, `status`, `created_at`),
  KEY `idx_fb_cmt_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施统一评论';

-- 1.6 点赞=敲木鱼(需求 D4+C4 木鱼双用; 设计文档表清单缺口补)
CREATE TABLE IF NOT EXISTS `biz_fabushi_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '点赞人',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_like_post_user` (`post_id`, `user_id`),
  KEY `idx_fb_like_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施木鱼点赞';

-- 1.7 已读未读(需求 B5; 唯一键幂等, 照 biz_incentive_read 模式)
CREATE TABLE IF NOT EXISTS `biz_fabushi_read_status` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `read_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_read_user_post` (`user_id`, `post_id`),
  KEY `idx_fb_read_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施内容已读记录';

-- 1.8 个人设置(提醒 F1 开关/频率/时间 + 木鱼音效开关 C4)
CREATE TABLE IF NOT EXISTS `biz_fabushi_user_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `reminder_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '缺卡提醒开关',
  `freq_code` varchar(50) DEFAULT 'daily' COMMENT '频率(字典 fabushi_remind_freq.value)',
  `remind_time` varchar(5) NOT NULL DEFAULT '20:00' COMMENT '提醒时间HH:mm',
  `sound_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '木鱼音效开关',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_setting_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施个人设置';

-- 1.9 书架(在读reading/已读完finished/收藏favorite 三态, 需求 B3)
CREATE TABLE IF NOT EXISTS `biz_fabushi_shelf` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `book_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'reading' COMMENT 'reading/finished/favorite',
  `last_section_id` bigint DEFAULT NULL COMMENT '续读章节',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_shelf_user_book` (`user_id`, `book_id`),
  KEY `idx_fb_shelf_book` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施书架';

-- 1.10 PDF 阅读进度(需求 B2 进度记忆; 页码级进度=web-view 阅读器回传)
CREATE TABLE IF NOT EXISTS `biz_fabushi_reading_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `book_id` bigint NOT NULL,
  `last_page` int NOT NULL DEFAULT '1',
  `total_pages` int DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_prog_user_book` (`user_id`, `book_id`),
  KEY `idx_fb_prog_book` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施阅读进度';

-- 1.11 共读活动(竞品借鉴功能 4; 二期激活, 表先建)
CREATE TABLE IF NOT EXISTS `biz_fabushi_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `book_id` bigint NOT NULL COMMENT '共读书目',
  `description` varchar(1000) DEFAULT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active进行中/ended已结束',
  `created_by` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_act_status` (`status`, `start_date`, `end_date`),
  KEY `idx_fb_act_book` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施共读活动';

-- 1.12 活动参与(唯一键防重复参与)
CREATE TABLE IF NOT EXISTS `biz_fabushi_activity_join` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_join_act_user` (`activity_id`, `user_id`),
  KEY `idx_fb_join_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施活动参与';

-- ============ 2. 字典(6 类型 built_in=1; value=稳定英文code, label=中文名) ============

INSERT INTO sys_dict_type (dict_name, dict_code, status, built_in, remark)
SELECT '法布施内容类别', 'fabushi_category', 1, 1, '法布施书目分类(名称待甲方确认, 改名只改label)'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.dict_code = 'fabushi_category');
INSERT INTO sys_dict_type (dict_name, dict_code, status, built_in, remark)
SELECT '法布施提醒频率', 'fabushi_remind_freq', 1, 1, '缺卡提醒频率'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.dict_code = 'fabushi_remind_freq');
INSERT INTO sys_dict_type (dict_name, dict_code, status, built_in, remark)
SELECT '法布施章节层级', 'fabushi_section_level', 1, 1, '诵读结构层级'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.dict_code = 'fabushi_section_level');
INSERT INTO sys_dict_type (dict_name, dict_code, status, built_in, remark)
SELECT '法布施成就徽章', 'fabushi_badge_type', 1, 1, '轻量成就徽章类型'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.dict_code = 'fabushi_badge_type');
INSERT INTO sys_dict_type (dict_name, dict_code, status, built_in, remark)
SELECT '法布施正能量标签', 'fabushi_znl_tag', 1, 1, '正能量流内容标签'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.dict_code = 'fabushi_znl_tag');
INSERT INTO sys_dict_type (dict_name, dict_code, status, built_in, remark)
SELECT '法布施版本类型', 'fabushi_edition_type', 1, 1, '书目版本类型'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.dict_code = 'fabushi_edition_type');

-- 种子数据(10 类别=占位名待甲方确认 + 3 频率 + 4 层级 + 4 徽章 + 3 标签 + 4 版本)
INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, status)
SELECT t.id, x.label, x.value, x.sort, 1
FROM sys_dict_type t
JOIN (
  SELECT '弟子规' AS label, 'dizigui' AS value, 1 AS sort UNION ALL
  SELECT '了凡四训', 'liaofan', 2 UNION ALL
  SELECT '论语', 'lunyu', 3 UNION ALL
  SELECT '道德经', 'daodejing', 4 UNION ALL
  SELECT '孝经', 'xiaojing', 5 UNION ALL
  SELECT '感应篇', 'ganyingpian', 6 UNION ALL
  SELECT '十善业', 'shishanye', 7 UNION ALL
  SELECT '群书治要', 'qunshuzhiyao', 8 UNION ALL
  SELECT '国外类', 'foreign', 9 UNION ALL
  SELECT '实用类', 'practical', 10 UNION ALL
  SELECT '每天', 'daily', 1 UNION ALL
  SELECT '工作日', 'workday', 2 UNION ALL
  SELECT '休息日', 'weekend', 3 UNION ALL
  SELECT '序', 'preface', 1 UNION ALL
  SELECT '部分', 'part', 2 UNION ALL
  SELECT '章', 'chapter', 3 UNION ALL
  SELECT '节', 'section', 4 UNION ALL
  SELECT '首次诵读', 'first_recite', 1 UNION ALL
  SELECT '连续5天', 'streak5', 2 UNION ALL
  SELECT '百次木鱼', 'fish100', 3 UNION ALL
  SELECT '读完3本', 'books3', 4 UNION ALL
  SELECT '工作心得', 'work_insight', 1 UNION ALL
  SELECT '人生感悟', 'life_feeling', 2 UNION ALL
  SELECT '美景分享', 'scenery', 3 UNION ALL
  SELECT '原版', 'original', 1 UNION ALL
  SELECT '译本', 'translated', 2 UNION ALL
  SELECT '注释本', 'annotated', 3 UNION ALL
  SELECT '讲义', 'lecture', 4
) x ON (
  (t.dict_code = 'fabushi_category' AND x.value IN ('dizigui','liaofan','lunyu','daodejing','xiaojing','ganyingpian','shishanye','qunshuzhiyao','foreign','practical'))
  OR (t.dict_code = 'fabushi_remind_freq' AND x.value IN ('daily','workday','weekend'))
  OR (t.dict_code = 'fabushi_section_level' AND x.value IN ('preface','part','chapter','section'))
  OR (t.dict_code = 'fabushi_badge_type' AND x.value IN ('first_recite','streak5','fish100','books3'))
  OR (t.dict_code = 'fabushi_znl_tag' AND x.value IN ('work_insight','life_feeling','scenery'))
  OR (t.dict_code = 'fabushi_edition_type' AND x.value IN ('original','translated','annotated','lecture'))
)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = x.value
);

-- ============ 3. WP7 资源/动作注册 ============

INSERT INTO sys_resource (module_code, resource_code, name, owner_column, dept_column, default_anchor, object_policy)
VALUES ('fabushi', 'fabushi', '法布施', 'user_id', NULL, 'OWNER', 'NONE')
ON DUPLICATE KEY UPDATE name = VALUES(name), owner_column = VALUES(owner_column);

-- scope_required: fabushi:stats:view=1(团队统计消费 (fabushi,stats_view) 独立数据范围档, 照 incentive:stats:view 先例);
--   其余 8 码=0(入口/路由/对象级写动作, 无范围解析)。view_like: 查看+导出类=1(照 customer:export=1 先例)。
INSERT INTO sys_action (resource_id, action_code, code, name, scope_required, view_like)
SELECT res.id, x.action_code, x.code, x.name, x.scope_required, x.view_like
FROM sys_resource res
JOIN (
  SELECT 'view'            AS action_code, 'fabushi:view'            AS code, '法布施内容浏览'   AS name, 0 AS scope_required, 1 AS view_like UNION ALL
  SELECT 'create',                'fabushi:create',                  '法布施发布互动',         0, 0 UNION ALL
  SELECT 'book_view',             'fabushi:book:view',               '法布施书目管理页',       0, 1 UNION ALL
  SELECT 'book_manage',           'fabushi:book:manage',             '法布施书目审核管理',     0, 0 UNION ALL
  SELECT 'content_view',          'fabushi:content:view',            '法布施内容治理页',       0, 1 UNION ALL
  SELECT 'content_manage',        'fabushi:content:manage',          '法布施内容下架',         0, 0 UNION ALL
  SELECT 'stats_view',            'fabushi:stats:view',              '法布施团队统计看板',     1, 1 UNION ALL
  SELECT 'stats_export',          'fabushi:stats:export',            '法布施统计导出',         0, 1 UNION ALL
  SELECT 'activity_manage',       'fabushi:activity:manage',         '法布施共读活动管理',     0, 0
) x
WHERE res.module_code = 'fabushi' AND res.resource_code = 'fabushi'
  AND NOT EXISTS (SELECT 1 FROM sys_action a WHERE a.code = x.code);

-- ============ 4. sys_menu 三级节点 ============

-- 4.1 顶层组标签「法布施」(type1, parent=0, sort=现有顶层组最大+1)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT 0, '法布施', 1, '', '', '', 'Reading', 99, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '法布施' AND x.`menu_type` = 1 AND COALESCE(x.`parent_id`, 0) = 0);

-- 4.2 页面行(type2, parent=「法布施」组; 一码一页面行)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT g.id, x.menu_name, 2, x.path, x.component, x.permission, x.icon, x.sort, 1, 1
FROM (SELECT id FROM sys_menu WHERE menu_name = '法布施' AND menu_type = 1 AND COALESCE(parent_id, 0) = 0 LIMIT 1) g
JOIN (
  SELECT '书目管理' AS menu_name, '/fabushi/book'     AS path, 'fabushi/book'     AS component, 'fabushi:book:view'     AS permission, 'Collection' AS icon, 1 AS sort UNION ALL
  SELECT '内容治理',       '/fabushi/content', 'fabushi/content', 'fabushi:content:view', 'Document',        2 UNION ALL
  SELECT '统计看板',       '/fabushi/stats',   'fabushi/stats',   'fabushi:stats:view',   'TrendCharts',     3
) x
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = x.permission);

-- 4.3 按钮行(type3, path='' 约定; 组级 3 行逐行显式插入, 页面级 3 行父锚=对应页面行)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT g.id, '法布施内容浏览', 3, '', '', 'fabushi:view', '', 1, 1, 1
FROM (SELECT id FROM sys_menu WHERE menu_name = '法布施' AND menu_type = 1 AND COALESCE(parent_id, 0) = 0 LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = 'fabushi:view');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT g.id, '法布施发布互动', 3, '', '', 'fabushi:create', '', 2, 1, 1
FROM (SELECT id FROM sys_menu WHERE menu_name = '法布施' AND menu_type = 1 AND COALESCE(parent_id, 0) = 0 LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = 'fabushi:create');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT g.id, '法布施活动管理', 3, '', '', 'fabushi:activity:manage', '', 3, 1, 1
FROM (SELECT id FROM sys_menu WHERE menu_name = '法布施' AND menu_type = 1 AND COALESCE(parent_id, 0) = 0 LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = 'fabushi:activity:manage');

-- 页面下按钮行(父=对应页面行)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT p.id, '法布施书目审核', 3, '', '', 'fabushi:book:manage', '', 1, 1, 1
-- 父锚用 path(页面行 path 唯一且先插): permission 码锚会让登记契约把"路由级消费码"当
-- 未跟踪守卫拦下(book:view 只在 PC 路由 meta 消费, 无 @PreAuthorize, 不进在用码集)
FROM (SELECT id FROM sys_menu WHERE path = '/fabushi/book' AND menu_type = 2 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = 'fabushi:book:manage');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT p.id, '法布施内容下架', 3, '', '', 'fabushi:content:manage', '', 1, 1, 1
-- 父锚用 path(同上: 路由级消费码不进登记契约在用码集, permission 码锚会被未跟踪守卫拦下)
FROM (SELECT id FROM sys_menu WHERE path = '/fabushi/content' AND menu_type = 2 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = 'fabushi:content:manage');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT p.id, '法布施统计导出', 3, '', '', 'fabushi:stats:export', '', 1, 1, 1
FROM (SELECT id FROM sys_menu WHERE permission = 'fabushi:stats:view' AND menu_type = 2 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` s WHERE s.`permission` = 'fabushi:stats:export');

-- ============ 5. sys_role_menu 角色绑定(设计文档 §3.2 默认矩阵) ============
-- admin=全 9 码; 销售总监(0)=view/create/stats:view; 行政(05)/销售员(06)=view/create。
-- book:view/content:view(PC 管理页)默认仅 admin, 其余走权限中心勾选。

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` IN ('fabushi:view', 'fabushi:create', 'fabushi:book:view', 'fabushi:book:manage',
                                        'fabushi:content:view', 'fabushi:content:manage', 'fabushi:stats:view',
                                        'fabushi:stats:export', 'fabushi:activity:manage')
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` IN ('fabushi:view', 'fabushi:create', 'fabushi:stats:view')
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` IN ('fabushi:view', 'fabushi:create')
WHERE r.`role_code` IN ('05', '06')
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

-- ============ 6. role_perm_scope 团队统计范围档(照 V2026.11.05.7 拷贝模式) ============
-- (incentive, stats_view) → (fabushi, stats_view) 逐行拷贝(用户/岗位/角色三级 assignee 全拷):
-- 存量激励统计档位行为在迁移后完全保持(同值), 此后管理员可在权限中心对法布施独立勾选范围。
INSERT INTO role_perm_scope (role_code, assignee_type, assignee_key, module, action, scope_level, anchor)
SELECT src.role_code, src.assignee_type, src.assignee_key, 'fabushi', 'stats_view', src.scope_level, src.anchor
FROM role_perm_scope src
WHERE src.module = 'incentive' AND src.action = 'stats_view'
  AND NOT EXISTS (
    SELECT 1 FROM role_perm_scope x
    WHERE x.assignee_type = src.assignee_type AND x.assignee_key = src.assignee_key
      AND x.module = 'fabushi' AND x.action = 'stats_view'
  );

-- ============ 7. 权限缓存自愈 ============
UPDATE sys_user SET perm_version = perm_version + 1;
