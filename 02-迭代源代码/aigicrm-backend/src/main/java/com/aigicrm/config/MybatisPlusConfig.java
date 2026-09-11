package com.aigicrm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.aigicrm.security.SecurityUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class MybatisPlusConfig {

    /**
     * 租户白名单：全部 25 张业务表(V9 迁移后全部含 tenant_id 列; 5 张 *_bak_* 快照表
     * 与废弃空表 biz_stats_permission/biz_file_permission 排除, 后者 2026-08-13 由 V15 迁移删除)。
     * 启动自检 {@link #verifyTenantColumns} 保证白名单每张表都有 tenant_id 列, 缺失即 fail-fast——
     * 防止漏配迁移导致 SQL 报错。⚠️ V15 删除表后必须先于旧 jar 重启用新代码, 否则自检失败。
     */
    private static final Set<String> TENANT_TABLES = Set.of(
            "biz_approval", "biz_approval_config", "biz_approval_flow", "biz_approval_node",
            "biz_contact", "biz_customer", "biz_customer_indicator", "biz_customer_log",
            "biz_customer_share", "biz_customer_transfer", "biz_file", "biz_file_folder",
            "biz_file_share", "biz_folder_acl", "biz_incentive_category",
            "biz_incentive_permission", "biz_incentive_record", "biz_invoice", "biz_payment",
            "biz_reminder", "biz_score_entry", "biz_score_item", "biz_score_participant",
            "biz_sea_config", "biz_sea_pool");

    /** 租户插件开关（默认关闭；环境变量 TENANT_ENABLE 可覆盖） */
    @Value("${aigicrm.tenant.enable:false}")
    private boolean tenantEnable;

    /** 懒获取 JdbcTemplate(测试上下文无数据源时 getIfAvailable 返回 null, 自检降级跳过) */
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcTemplateProvider;

    /**
     * 启动自检：租户开启时校验白名单每张表都有 tenant_id 列, 缺失即启动失败。
     * 防止"迁移未执行 / 新表漏配"导致运行时 SQL 报错(历史教训: V7 迁移遗漏致生产 500)。
     */
    @PostConstruct
    public void verifyTenantColumns() {
        if (!tenantEnable) return;
        org.springframework.jdbc.core.JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            log.warn("租户启动自检跳过(JdbcTemplate 不可用)");
            return;
        }
        for (String table : TENANT_TABLES) {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'tenant_id'",
                    Integer.class, table);
            if (cnt == null || cnt == 0) {
                throw new IllegalStateException("租户白名单表缺少 tenant_id 列, 请先执行 V9 迁移: " + table);
            }
        }
        log.info("租户插件启动自检通过: {} 张白名单表全部含 tenant_id 列", TENANT_TABLES.size());
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return buildInterceptor(tenantEnable);
    }

    /**
     * 拦截器组装（独立方法便于单测直接断言开关逻辑）。
     * 租户拦截器必须注册在分页拦截器之前（MyBatis-Plus 约定：先改写 SQL 条件，再分页）。
     */
    static MybatisPlusInterceptor buildInterceptor(boolean tenantEnabled) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (tenantEnabled) {
            interceptor.addInnerInterceptor(buildTenantInterceptor());
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 集团租户拦截器：对白名单表自动附加 tenant_id 条件。
     * 平台管理员(admin 角色)跨集团管理, 跳过全部租户条件(ignoreTable 动态判断, 与 MP 版本无关);
     * 其余用户走 {@link #resolveTenantId}(批④起读会话级 currentGroupId, 当前读集团集合取最小 fail-closed)。
     */
    static TenantLineInnerInterceptor buildTenantInterceptor() {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 【批④ 2026-08-10】会话级当前集团(多集团用户一次一个集团, 切换=重签 token)
                var user = SecurityUtils.getCurrentUser();
                if (user != null && user.getCurrentGroupId() != null) {
                    return new LongValue(user.getCurrentGroupId());
                }
                return resolveTenantId(SecurityUtils.getCurrentGroupIds());
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (SecurityUtils.isAdmin()) return true; // 平台管理员跨集团: 不附加租户条件
                return !TENANT_TABLES.contains(tableName);
            }
        });
    }

    /**
     * 集团过滤表达式：
     * <ul>
     *   <li>空/无集团（平台管理员）→ {@code tenant_id = 1} 主集团默认值。brief 原方案
     *       {@code new NullExpression()}（jsqlparser NullValue）经实验验证（parserSingle 真实重写）
     *       会生成 {@code tenant_id = NULL}——MP 3.5.6 无条件 EqualsTo 包装、无 NullValue 特判，
     *       恒假而非恒真，故按契约退回主集团默认值方案。</li>
     *   <li>单集团 → 该集团 ID。</li>
     *   <li>多集团 → 3.5.6 无 InExpression 特判，{@code new InExpression(null, ...)} 会生成
     *       {@code tenant_id = null IN (...)} 非法 SQL；暂取排序后最小集团 fail-closed
     *       （排序保证同一用户多次登录命中同一集团）。开启开关前需在 Task 12 解决多集团
     *       表达（升级 MP 3.5.7+ 或对多集团查询使用 @InterceptorIgnore + 业务层 DataScopeGuard 兜底）。</li>
     * </ul>
     */
    static Expression resolveTenantId(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new LongValue(1L);
        }
        if (groupIds.size() == 1) {
            return new LongValue(groupIds.get(0));
        }
        // 【修复 多集团 fail-closed 可观测性】一人多集团时目前只能取一个集团做 tenant_id 等值比较
        // （MyBatis-Plus 3.5.6 TenantLineHandler.getTenantId() 契约固定生成 `tenant_id = <expr>`，
        // 不支持返回 IN 谓词——见类注释里已验证的 InExpression/NullExpression 实验记录）。这不是
        // 权限放宽（不会让用户看到不该看的集团），而是收紧过度：该用户其余集团的合法数据会被
        // 静默过滤掉。之前完全没有任何提示，运维/开发很难定位"为什么这个人某个集团的数据不见了"。
        // 这里先加日志留痕，真正支持多集团并集查询需升级 MyBatis-Plus 3.5.7+ 或对多集团用户的查询
        // 改走 @InterceptorIgnore + 业务层手动 IN 过滤（设计文档 §4.3 / §10 项6，属阶段外改造，
        // 不在本次修复范围内，需要在真实多集团数据上验证后再动，此处不贸然改写）。
        Long chosen = groupIds.stream().sorted().findFirst().get();
        log.warn("用户所属集团数={}，租户插件当前仅按最小集团ID={}过滤，其余集团(={})数据本次请求不可见"
                        + "（fail-closed，非安全问题；如需多集团并集可见需先完成 Task 12 多集团表达改造）",
                groupIds.size(), chosen, groupIds);
        return new LongValue(chosen);
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
