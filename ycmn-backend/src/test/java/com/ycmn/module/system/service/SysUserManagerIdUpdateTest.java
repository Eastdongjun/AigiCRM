package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I5：managerId 清空回写 NULL（同 dataScopeOverride 缺陷修复模式，参考 SysUserDataScopeOverrideTest）。
 * 背景：MyBatis-Plus 默认字段策略 NOT_NULL，updateById 时 null 不进入 SET 子句 →
 * "清空上级"无法回写 NULL，旧值残留（team 数据范围按 manager_id 递归，残留上级会扩大可见范围）。
 * 修复：managerId 加 @TableField(updateStrategy = FieldStrategy.ALWAYS) + 服务层 0/null 归一化。
 */
@ExtendWith(MockitoExtension.class)
class SysUserManagerIdUpdateTest {

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
    void managerIdFieldUsesAlwaysStrategy() {
        assertEquals(FieldStrategy.ALWAYS, fieldOf("manager_id").getUpdateStrategy(),
                "manager_id 必须是 ALWAYS 策略，清空才能回写 NULL");
    }

    @Test
    void managerIdSqlSetHasNoNullGuard() {
        String sqlSet = fieldOf("manager_id").getSqlSet("managerId");
        assertTrue(sqlSet.contains("manager_id=#{"), "SET 片段应包含该列: " + sqlSet);
        assertFalse(sqlSet.contains("['managerId']"),
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

    /** 前端"无上级"提交 0/null → 服务层归一为 null 透传 updateById（配合 ALWAYS 落库回写 NULL） */
    @Test
    void updateClearsManagerIdWithZero() {
        SysUserService service = newService();
        SysUser existing = new SysUser();
        existing.setId(1L);
        existing.setManagerId(9L);
        when(userMapper.selectById(1L)).thenReturn(existing);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setManagerId(0L); // 前端"无上级"归一值
        service.update(user);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertNull(captor.getValue().getManagerId(),
                "managerId 0/null 应归一为 null；配合 ALWAYS 策略落库回写 NULL（防残留上级扩大 team 可见范围）");
    }

    /** 正常上级值保留透传 */
    @Test
    void updateKeepsNormalManagerId() {
        SysUserService service = newService();
        SysUser existing = new SysUser();
        existing.setId(1L);
        existing.setManagerId(9L);
        when(userMapper.selectById(1L)).thenReturn(existing);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setManagerId(5L);
        service.update(user);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getManagerId(), "有效上级值应原样透传");
    }
}
