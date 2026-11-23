-- 契约（2026-11-23 D1 类型重构）：biz_fabushi_post.scope_type 必须 ∈ {songdu, suishi, zaidu}
-- 后端单源 = FabushiScopeType 枚举；旧值 heart/recitation/znl 已由 V2026.11.23.1 迁移，禁止再现。
-- 本查询返回残留旧值/非法值的行；0 行才算通过。
-- 用法：mysql -N -B "$DB_NAME" < scripts/check-fabushi-scope-types.sql
-- 负例自证：向 biz_fabushi_post 插一行 scope_type='heart'（status=offline 免展示），本查询必须出 1 行（红），
--   删除后复跑必须归零——"改坏会红"证明见 scripts/dev-verify.sh contracts 段挂接。
SELECT id, scope_type, status
FROM biz_fabushi_post
WHERE scope_type NOT IN ('songdu', 'suishi', 'zaidu')
   OR scope_type IS NULL
LIMIT 50;
