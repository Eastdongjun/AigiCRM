package com.ycmn.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ycmn.security.SecurityUtils;
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
public class MybatisPlusConfig {

    /**
     * 租户白名单：仅已迁移 tenant_id 列的核心业务表（Task 12 迁移后开启 ycmn.tenant.enable）。
     * 开关关闭时本类不注册 TenantLineInnerInterceptor——未迁移列的表一旦被附加 tenant_id
     * 条件会直接报"列不存在"，因此默认关闭是本任务合入不破坏现有功能的前提。
     */
    private static final Set<String> TENANT_TABLES =
            Set.of("biz_customer", "biz_customer_share", "biz_file_folder", "biz_file",
                    "biz_incentive_record", "biz_sea_pool", "biz_approval", "biz_reminder");

    /** 租户插件开关（默认关闭；环境变量 TENANT_ENABLE 可覆盖） */
    @Value("${ycmn.tenant.enable:false}")
    private boolean tenantEnable;

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
     * 平台管理员（groupIds 为空）走 {@link #resolveTenantId} 的默认值分支。
     */
    static TenantLineInnerInterceptor buildTenantInterceptor() {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return resolveTenantId(SecurityUtils.getCurrentGroupIds());
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
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
        return new LongValue(groupIds.stream().sorted().findFirst().get());
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
