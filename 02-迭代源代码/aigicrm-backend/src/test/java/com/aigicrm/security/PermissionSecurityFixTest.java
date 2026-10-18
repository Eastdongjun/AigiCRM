package com.aigicrm.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysRoleOrg;
import com.aigicrm.module.system.entity.SysUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleOrgMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.module.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 2026-08-10 权限审计修复回归: P1-03/P1-07/P2-05/P2-07/P2-09/P1-05/P1-06 */
class PermissionSecurityFixTest {

    /** MyBatis-Plus 3.5.17(Boot 4)起纯 mock 环境必须显式注册实体 lambda 缓存，
        否则服务内 LambdaQueryWrapper 抛 can not find lambda cache（升级回归修复） */
    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysRoleOrg.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysDept.class);
    }

    private static final String SECRET = "test-secret-key-for-jwt-hmac-256-bytes-long-enough";

    // ==================== P1-07: 下载 token 必须携带 jti(黑名单机制对其生效) ====================

    @SuppressWarnings("unchecked")
    @Test
    void downloadToken_nowHasJti_soBlacklistApplies() {
        JwtTokenProvider p = new JwtTokenProvider(mock(RedisTemplate.class), mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(p, "secret", SECRET);

        String token = p.generateDownloadToken(42L);
        assertNotNull(p.getJtiFromToken(token), "下载 token 必须带 jti, 否则黑名单恒失效");
    }

    // ==================== P2-13: 改密后旧 token 判定失效 ====================

    @SuppressWarnings("unchecked")
    @Test
    void tokenIssuedBeforePasswordChange_isInvalidated() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        JwtTokenProvider p = new JwtTokenProvider(redis, mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(p, "secret", SECRET);
        ReflectionTestUtils.setField(p, "expiration", 7200000L);
        ReflectionTestUtils.setField(p, "rememberExpiration", 86400000L);

        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        String token = p.generateToken(7L, "user7", false);
        long issuedAt = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload().getIssuedAt().getTime();

        // 改密时刻晚于签发时刻 → 失效
        when(valueOps.get("jwt:pwd-changed:7")).thenReturn(String.valueOf(issuedAt + 1000));
        assertTrue(p.isInvalidatedByPasswordChange(token), "改密晚于签发 → 旧 token 应失效");

        // 改密时刻早于签发时刻(改密后新签发的 token) → 有效
        when(valueOps.get("jwt:pwd-changed:7")).thenReturn(String.valueOf(issuedAt - 1000));
        assertFalse(p.isInvalidatedByPasswordChange(token), "改密前签发的 token 不受影响");
    }

    @SuppressWarnings("unchecked")
    @Test
    void isBlacklisted_redisDown_fallsBackToDb() {
        // 【2026-08-14 安全 P0】Redis 故障不再静默"不拉黑"：回退 DB 黑名单表判定(fail-closed 兜底)
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JwtTokenProvider p = new JwtTokenProvider(redis, jdbc);
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        assertFalse(p.isBlacklisted("some-jti"), "DB 兜底未命中 → 未拉黑");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        assertTrue(p.isBlacklisted("some-jti"), "DB 兜底命中 → 判定拉黑(撤销链路 fail-closed)");
    }

    @SuppressWarnings("unchecked")
    @Test
    void isBlacklisted_redisMiss_queriesDbAuthoritatively() {
        // 【2026-08-14 评审 P1-2】Redis 正常但 miss(重启/淘汰/flush)时也必须回查 DB——
        // 否则 DB 黑名单行被忽略，已撤销 token 复活（撤销 fail-open 窗口）
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JwtTokenProvider p = new JwtTokenProvider(redis, jdbc);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        assertTrue(p.isBlacklisted("some-jti"), "Redis miss 必须回查 DB 权威判定");
    }

    // ==================== P2-05/P2-07: 范围配置合法性校验 + 空列表=恢复默认 ====================

    @Test
    void updateRoleScopes_invalidScopeLevel_rejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        assertThrows(BusinessException.class,
                () -> svc.updateRoleScopes("06", List.of(Map.of("module", "customer", "action", "view", "scopeLevel", "xxx"))),
                "非法范围值必须拒绝, 不得静默降级 self");
    }

    @Test
    void updateRoleScopes_emptyList_deletesAllConfig() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        svc.updateRoleScopes("06", List.of());
        verify(jdbc).update("DELETE FROM role_perm_scope WHERE assignee_type=? AND assignee_key=?", "ROLE", "06");
    }

    @Test
    void renameRoleCode_migratesScopes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        svc.renameRoleCode("old", "new");
        verify(jdbc).update("UPDATE role_perm_scope SET role_code=?, assignee_key=? WHERE assignee_type='ROLE' AND assignee_key=?",
                "new", "new", "old");
    }

    // ==================== P1-05: org_and_children 并入 managedOrgIds(与列表同口径) ====================

    @Test
    void orgAndChildren_mergesManagedOrgIds_withOwnDeptTree() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        SysConfigService cfg = mock(SysConfigService.class);
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, cfg, mock(SysRoleOrgMapper.class), null);

        // 本人 dept=11; 授权组织 22/222。子树收集按"无子节点"处理(每个组织=自身);
        // 部门→用户 解析返回三个用户, 断言三个部门(11/22/222)的用户全部可见。
        when(deptMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(
                List.of(user(1L, 11L), user(2L, 22L), user(3L, 222L)));

        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setDeptId(11L);
        su.setDataScope("org_and_children");
        su.setManagedOrgIds(List.of(22L, 222L));

        List<Long> visible = r.resolve(su);
        assertNotNull(visible);
        // 部门 11(本人)+22/222(授权组织) 的用户都应可见
        assertTrue(visible.containsAll(List.of(1L, 2L, 3L)), "org_and_children 必须并入 managedOrgIds 授权组织: " + visible);
    }

    // ==================== P1-06: custom 范围在 managedOrgIds 未装载时按角色回退查库 ====================

    @Test
    void customScope_withEmptyManagedOrgs_fallsBackToRoleOrgs() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        SysConfigService cfg = mock(SysConfigService.class);
        SysRoleOrgMapper roleOrgMapper = mock(SysRoleOrgMapper.class);
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, cfg, roleOrgMapper, null);

        SecurityUser su = new SecurityUser();
        su.setUserId(5L);
        su.setDataScope("custom");
        su.setManagedOrgIds(List.of()); // 模拟新模型: 角色 dataScope≠custom 时未装载
        su.setRoleCodes(List.of("05"));

        // 角色回退查询返回授权组织 33
        SysRoleOrg ro = new SysRoleOrg();
        ro.setOrgId(33L);
        ro.setIncludeChildren(1);
        when(roleOrgMapper.selectByUserId(5L)).thenReturn(List.of(ro));
        when(deptMapper.selectDescendantIds(33L)).thenReturn(List.of(33L, 333L));
        when(deptMapper.selectList(any())).thenReturn(List.of()); // collectSubtree: 无子
        when(userMapper.selectList(any())).thenReturn(List.of(user(6L, 33L), user(7L, 333L), user(5L, 33L)));

        List<Long> visible = r.resolve(su);
        assertNotNull(visible);
        assertTrue(visible.containsAll(List.of(5L, 6L, 7L)), "custom 范围应回退到角色授权组织: " + visible);
        verify(roleOrgMapper).selectByUserId(5L);
    }

    private com.aigicrm.module.system.entity.SysUser user(Long id, Long deptId) {
        com.aigicrm.module.system.entity.SysUser u = new com.aigicrm.module.system.entity.SysUser();
        u.setId(id);
        u.setDeptId(deptId);
        u.setStatus(1);
        u.setDeleted(0);
        return u;
    }

    // ==================== P1-01 回归: getById 守卫(正/反例) ====================

    @Test
    void getById_sameDeptVisible_ok() {
        // 同部门用户可见: resolver 可见部门含目标 dept → 放行
        com.aigicrm.module.system.mapper.SysUserMapper userMapper = mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        com.aigicrm.module.system.mapper.SysDeptMapper deptMapper = mock(com.aigicrm.module.system.mapper.SysDeptMapper.class);
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        when(userMapper.selectById(2L)).thenReturn(user(2L, 4L));
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of(4L));
        com.aigicrm.module.system.service.SysUserService svc = new com.aigicrm.module.system.service.SysUserService(
                userMapper, mock(com.aigicrm.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(JdbcTemplate.class), mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.sea.service.SeaService.class), deptMapper,
                mock(com.aigicrm.module.system.mapper.SysUserOrgMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.security.JwtTokenProvider.class), resolver,
                mock(com.aigicrm.security.SecurityAuditService.class),
                mock(com.aigicrm.module.common.service.FormDraftService.class));
        try (org.mockito.MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(new com.aigicrm.security.SecurityUser());
            assertNotNull(svc.getById(2L), "同部门用户应可见");
        }
    }

    @Test
    void getById_outOfScopeDept_rejected() {
        com.aigicrm.module.system.mapper.SysUserMapper userMapper = mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        when(userMapper.selectById(2L)).thenReturn(user(2L, 4L));
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of(9L)); // 可见部门不含 4
        com.aigicrm.module.system.service.SysUserService svc = new com.aigicrm.module.system.service.SysUserService(
                userMapper, mock(com.aigicrm.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(JdbcTemplate.class), mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.sea.service.SeaService.class), mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserOrgMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.security.JwtTokenProvider.class), resolver,
                mock(com.aigicrm.security.SecurityAuditService.class),
                mock(com.aigicrm.module.common.service.FormDraftService.class));
        try (org.mockito.MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(new com.aigicrm.security.SecurityUser());
            m.when(SecurityUtils::isOrgAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.getById(2L), "跨部门用户必须拒绝");
        }
    }

    @Test
    void getById_self_alwaysAllowed() {
        // 本人恒可读(无 dept 兜底场景)
        com.aigicrm.module.system.mapper.SysUserMapper userMapper = mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        when(userMapper.selectById(7L)).thenReturn(user(7L, null)); // 本人无部门
        com.aigicrm.module.system.service.SysUserService svc = new com.aigicrm.module.system.service.SysUserService(
                userMapper, mock(com.aigicrm.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(JdbcTemplate.class), mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.sea.service.SeaService.class), mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserOrgMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.security.JwtTokenProvider.class), mock(OrgScopeResolver.class),
                mock(com.aigicrm.security.SecurityAuditService.class),
                mock(com.aigicrm.module.common.service.FormDraftService.class));
        try (org.mockito.MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            assertNotNull(svc.getById(7L), "本人恒可读(即使无部门)");
        }
    }

    // ==================== P2-03: dept_and_children 不再静默降级 self ====================

    @Test
    void deptAndChildren_resolvesAsDeptTree() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, mock(SysConfigService.class), mock(SysRoleOrgMapper.class), null);
        when(deptMapper.selectList(any())).thenReturn(List.of()); // 无子节点
        when(userMapper.selectList(any())).thenReturn(List.of(user(1L, 11L), user(2L, 11L)));
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setDeptId(11L);
        su.setDataScope("dept_and_children");
        List<Long> visible = r.resolve(su);
        assertNotNull(visible);
        assertTrue(visible.containsAll(List.of(1L, 2L)), "dept_and_children 应按部门树解析而非降级 self: " + visible);
    }

    // ==================== 【2026-08-14 安全 P0】随机初始口令 + 首登强制改密 ====================

    private com.aigicrm.module.system.service.SysUserService userServiceWith(
            com.aigicrm.module.system.mapper.SysUserMapper userMapper,
            org.springframework.security.crypto.password.PasswordEncoder encoder) {
        return new com.aigicrm.module.system.service.SysUserService(
                userMapper, mock(com.aigicrm.module.system.mapper.SysUserRoleMapper.class),
                encoder,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(JdbcTemplate.class), mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.sea.service.SeaService.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserOrgMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.security.JwtTokenProvider.class), mock(OrgScopeResolver.class),
                mock(com.aigicrm.security.SecurityAuditService.class),
                mock(com.aigicrm.module.common.service.FormDraftService.class));
    }

    @Test
    void createUser_randomTempPassword_notHardcodedDefault() {
        com.aigicrm.module.system.mapper.SysUserMapper userMapper =
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(encoder.encode(anyString())).thenAnswer(inv -> inv.getArgument(0)); // 明文透传便于断言
        com.aigicrm.module.system.service.SysUserService svc = userServiceWith(userMapper, encoder);

        com.aigicrm.module.system.entity.SysUser u = new com.aigicrm.module.system.entity.SysUser();
        u.setUsername("newuser01");
        u.setRealName("测试用户");
        u.setPhone("13900000000");
        u.setRoleId(11L);
        u.setDataScopeOverride("self");
        when(userMapper.selectCount(any())).thenReturn(0L);

        try (org.mockito.MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            String temp = svc.create(u);
            assertNotNull(temp);
            assertNotEquals("123456", temp, "初始口令必须随机，禁止硬编码默认口令");
            assertTrue(temp.length() >= 12, "随机口令长度至少 12 位");
            assertEquals(1, u.getPwdResetRequired(), "新用户必须首登强制改密");
        }
    }

    @Test
    void resetPassword_randomTempPassword_forcesFirstLoginChange() {
        com.aigicrm.module.system.mapper.SysUserMapper userMapper =
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(encoder.encode(anyString())).thenAnswer(inv -> inv.getArgument(0));
        com.aigicrm.module.system.service.SysUserService svc = userServiceWith(userMapper, encoder);

        com.aigicrm.module.system.entity.SysUser existing = new com.aigicrm.module.system.entity.SysUser();
        existing.setId(7L);
        existing.setUsername("u7");
        existing.setPassword("oldhash");
        when(userMapper.selectById(7L)).thenReturn(existing);

        String temp = svc.resetPassword(7L);
        assertNotNull(temp);
        assertNotEquals("123456", temp, "重置口令必须随机，禁止硬编码默认口令");
        assertEquals(1, existing.getPwdResetRequired(), "重置后必须首登强制改密");
        assertNotNull(existing.getPwdChangedAt(), "重置必须写改密时刻(DB 级旧 token 撤销依据)");
        assertEquals(0, existing.getPwdChangedAt().getNano(),
                "改密时刻必须截断到秒——MySQL DATETIME(0) 四舍五入会进位到下一秒，误杀同秒签发的 token");
    }

    /** 【2026-08-14 评审 P1·静默提权】编辑用户只回插其**当前已绑定**的激励角色：
     *  只绑了分享可见(12)的用户，编辑后不得凭空获得全量查看(13)——否则任意一次编辑静默提权 */
    @Test
    void updateUser_preservesOnlyExistingIncentiveRoles_noSilentGrant() {
        com.aigicrm.module.system.mapper.SysUserMapper userMapper =
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.aigicrm.module.system.service.SysUserService svc = new com.aigicrm.module.system.service.SysUserService(
                userMapper, mock(com.aigicrm.module.system.mapper.SysUserRoleMapper.class),
                encoder,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                jdbc, mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.sea.service.SeaService.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserOrgMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.security.JwtTokenProvider.class), mock(OrgScopeResolver.class),
                mock(com.aigicrm.security.SecurityAuditService.class),
                mock(com.aigicrm.module.common.service.FormDraftService.class));

        com.aigicrm.module.system.entity.SysUser existing = new com.aigicrm.module.system.entity.SysUser();
        existing.setId(7L);
        existing.setUsername("u7");
        when(userMapper.selectById(7L)).thenReturn(existing);
        // 该用户当前只绑定「激励-分享可见」(role id=12)
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(12L));

        com.aigicrm.module.system.entity.SysUser payload = new com.aigicrm.module.system.entity.SysUser();
        payload.setId(7L);
        payload.setRealName("改名");
        payload.setRoleId(11L);
        payload.setDataScopeOverride("self");

        try (org.mockito.MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.update(payload);

            // 回插的只有用户当前已有的 12（分享可见），不得插入 13（全量查看）
            verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO sys_user_role"), eq(7L), eq(12L));
            verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO sys_user_role"), eq(7L), eq(13L));
        }
    }

    // ==================== P1-05 根治: 列表与详情同一解析链路 ====================

    @Test
    void listAndDetail_useSameScopeRequestResolution() {
        // 列表(applyDataScope)与详情(canView)必须走同一 ScopeRequest 解析——
        // 若各自实现范围逻辑, 必然再次口径分裂(组织管理员"列表可见、详情 403"历史缺陷)。
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(1L);
        su.setDataScope("org_and_children");
        su.setManagedOrgIds(List.of(22L));
        try (org.mockito.MockedStatic<com.aigicrm.security.SecurityUtils> m =
                     mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUser).thenReturn(su);

            // 列表侧: CustomerService.applyDataScope → resolveScope(ScopeRequest)
            com.aigicrm.common.scope.OrgScopeResolver resolver = mock(com.aigicrm.common.scope.OrgScopeResolver.class);
            when(resolver.resolveScope(any(com.aigicrm.common.scope.ScopeRequest.class)))
                    .thenReturn(com.aigicrm.common.scope.ScopeDecision.all());
            com.aigicrm.module.system.service.RolePermScopeService rps = mock(com.aigicrm.module.system.service.RolePermScopeService.class);
            when(rps.anchorOf(any(), any(), any())).thenReturn(com.aigicrm.common.scope.ScopeRequest.AnchorType.OWNER);
            com.aigicrm.module.customer.service.CustomerService cs = new com.aigicrm.module.customer.service.CustomerService(
                    mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizInvoiceMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizPaymentMapper.class),
                    mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                    mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                    mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                    mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                    mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                    mock(org.springframework.jdbc.core.JdbcTemplate.class),
                    mock(com.aigicrm.module.system.service.SysConfigService.class),
                    resolver,
                    mock(com.aigicrm.common.engine.CustomerAccessService.class),
                    rps,
                    mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                        mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizCustomer> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(cs, "applyDataScope", w);

            org.mockito.ArgumentCaptor<com.aigicrm.common.scope.ScopeRequest> listReq =
                    org.mockito.ArgumentCaptor.forClass(com.aigicrm.common.scope.ScopeRequest.class);
            verify(resolver).resolveScope(listReq.capture());
            assertEquals("customer", listReq.getValue().getModule());
            assertEquals("view", listReq.getValue().getAction());
            assertEquals(com.aigicrm.common.scope.ScopeRequest.AnchorType.OWNER, listReq.getValue().getAnchor());

            // 详情侧: CustomerAccessService.canView → canAccess(同 req 构造)
            com.aigicrm.module.customer.entity.BizCustomer cust = new com.aigicrm.module.customer.entity.BizCustomer();
            cust.setId(9L);
            cust.setOwnerId(22L);
            cust.setDeptId(22L);
            com.aigicrm.module.customer.mapper.BizCustomerMapper cm = mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class);
            when(cm.selectById(9L)).thenReturn(cust);
            when(rps.anchorOf(any(), any(), any())).thenReturn(com.aigicrm.common.scope.ScopeRequest.AnchorType.OWNER);
            com.aigicrm.common.engine.CustomerAccessService cas = new com.aigicrm.common.engine.CustomerAccessService(
                    cm,
                    mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                    resolver,
                    mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                    mock(com.aigicrm.module.sea.service.SeaConfigService.class),
                    rps);
            when(resolver.canAccess(any(com.aigicrm.common.scope.ScopeRequest.class))).thenReturn(true);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            cas.canView(9L);

            org.mockito.ArgumentCaptor<com.aigicrm.common.scope.ScopeRequest> detailReq =
                    org.mockito.ArgumentCaptor.forClass(com.aigicrm.common.scope.ScopeRequest.class);
            verify(resolver).canAccess(detailReq.capture());
            assertEquals("customer", detailReq.getValue().getModule());
            assertEquals("view", detailReq.getValue().getAction());
            assertEquals(com.aigicrm.common.scope.ScopeRequest.AnchorType.OWNER, detailReq.getValue().getAnchor());
        }
    }
}
