# 数据库 / Flyway 规则

- DB-01 迁移链单向追加,禁止修改已发布版本;生产漂移(V2-V10 missing)用 ignoreMigrationPatterns 显式处理
- DB-02 上线顺序:deploy-db.sh(35 迁移)先于新代码部署;恢复演练必须通过后才发令
- DB-03 utf8mb4 全链路(连接/库/表);FK 命名 fk_<子表>_<语义>
- DB-04 删除端点先查引用表(fk_incentive_read_record 类约束);测试库与本地 schema 指纹比对零差异
- DB-05 生成列+唯一键变更须收编进迁移链,消除 schema 漂移(active_flag 先例)
