package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Task 8 数据范围覆盖的持久化与校验测试。
 *
 * 背景（审查 Important-1）：MyBatis-Plus 默认字段策略 NOT_NULL，
 * updateById 时 null 字段不进入 SET 子句 → "清空=跟随角色" 无法回写 NULL，旧值残留。
 * 修复：SysUser.dataScopeOverride 加 @TableField(updateStrategy = FieldStrategy.ALWAYS)。
 *
 * 覆盖：
 *   1. 实体注解策略 = ALWAYS（可回写 NULL）
 *   2. 生成的 SET 片段对 data_scope_override 无 "!= null" 守卫（null 会写入）
 *   3. 对比：普通字段（real_name）默认 NOT_NULL 有守卫（证明断言有效区分策略）
 *   4. 服务层：非法 override 取值在 create/update 均被拒绝
 *   5. 服务层：update 载荷 override=null 时透传 null 给 updateById（配合 ALWAYS 落库清空）
 */
@ExtendWith(MockitoExtension.class)
class SysUserDataScopeOverrideTest {

    @Mock SysUserMapper userMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SysConfigService configService;
    @Mock BizCustomerMapper customerMapper;
    @Mock BizCustomerShareMapper shareMapper;
    @Mock JdbcTemplate jdbc;
    @Mock SysRoleMapper roleMapper;
    @Mock SeaService seaService;

    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    private TableFieldInfo fieldOf(String column) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(SysUser.class);
        return tableInfo.getFieldList().stream()
                .filter(f -> column.equals(f.getColumn()))
                .findFirst().orElseThrow(() -> new AssertionError("列不存在: " + column));
    }

    @Test
    void overrideFieldUsesAlwaysStrategy() {
        assertEquals(FieldStrategy.ALWAYS, fieldOf("data_scope_override").getUpdateStrategy(),
                "data_scope_override 必须是 ALWAYS 策略，null 才能进入 SET 子句回写 NULL");
    }

    @Test
    void overrideFieldSqlSetHasNoNullGuard() {
        // MP 3.5.6 的 SET 片段格式：column=#{...}，守卫以 ['property'] 形式出现
        String sqlSet = fieldOf("data_scope_override").getSqlSet("dataScopeOverride");
        assertTrue(sqlSet.contains("data_scope_override=#{"),
                "SET 片段应包含该列: " + sqlSet);
        assertFalse(sqlSet.contains("['dataScopeOverride']"),
                "ALWAYS 策略不应有 null 守卫（否则清空无法回写 NULL）: " + sqlSet);
    }

    @Test
    void defaultStrategyFieldKeepsNullGuard() {
        // 对比用例：默认 NOT_NULL 字段应有守卫，证明本测试能区分策略差异
        String sqlSet = fieldOf("real_name").getSqlSet("realName");
        assertTrue(sqlSet.contains("['realName']"), "默认 NOT_NULL 字段应有守卫: " + sqlSet);
    }

    private SysUserService newService() {
        return new SysUserService(userMapper, userRoleMapper, passwordEncoder, configService,
                customerMapper, shareMapper, jdbc, roleMapper, seaService);
    }

    private SysUser validCreatePayload() {
        SysUser user = new SysUser();
        user.setUsername("test99");
        user.setRealName("测试用户");
        user.setRoleId(10L);
        user.setPhone("13800000000");
        return user;
    }

    @Test
    void createRejectsIllegalOverride() {
        SysUserService service = newService();
        SysUser user = validCreatePayload();
        user.setDataScopeOverride("hacker_scope");

        assertThrows(BusinessException.class, () -> service.create(user));
        verify(userMapper, never()).insert(any());
    }

    @Test
    void updateRejectsIllegalOverride() {
        SysUserService service = newService();
        SysUser existing = new SysUser();
        existing.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(existing);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setDataScopeOverride("ALL"); // 大写非法（合法码均为小写）
        assertThrows(BusinessException.class, () -> service.update(user));

        user.setDataScopeOverride("hacker_scope");
        assertThrows(BusinessException.class, () -> service.update(user));
    }

    @Test
    void updateClearsOverrideWithNull() {
        SysUserService service = newService();
        SysUser existing = new SysUser();
        existing.setId(1L);
        existing.setDataScopeOverride("team");
        when(userMapper.selectById(1L)).thenReturn(existing);

        // 前端"跟随角色"提交空字符串 → 服务层归一为 null 透传 updateById
        SysUser user = new SysUser();
        user.setId(1L);
        user.setDataScopeOverride("  ");
        service.update(user);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertNull(captor.getValue().getDataScopeOverride(),
                "空白 override 应归一为 null；配合 ALWAYS 策略落库回写 NULL");
    }
}
