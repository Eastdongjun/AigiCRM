-- 【议题五.1 裁决 2026-09-19】客户别名:独立字段,搜索命中(简称/曾用名)
ALTER TABLE biz_customer ADD COLUMN alias VARCHAR(200) NULL
    COMMENT '别名/简称/曾用名(搜索命中,查重提示口径同主名)';
