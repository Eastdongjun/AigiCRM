package com.aigicrm.module.incentive.service;

import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 激励计分规则 + 专项授权判定（2026-08-16 授权表化）：
 * canEdit/canDelete/canViewAll/canViewShared 由 biz_incentive_grant 表驱动——
 * 无记录=默认(分享可见开, 其余关)；admin 恒可(编辑/删除/看全部)。
 */
class IncentiveScoreTest {

    private IncentiveService newService(BizIncentiveCategoryMapper categoryMapper, JdbcTemplate jdbc) {
        return new IncentiveService(
                categoryMapper,
                mock(BizIncentiveRecordMapper.class),
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysDeptMapper.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                null,
                jdbc);
    }

    private int calcScore(IncentiveService service, BizIncentiveRecord record,
                          BizIncentiveCategory category, int base) {
        Integer result = (Integer) ReflectionTestUtils.invokeMethod(
                service, "calcScore", record, category, base);
        return result == null ? -1 : result;
    }

    private BizIncentiveCategory category(String code, Integer score) {
        BizIncentiveCategory c = new BizIncentiveCategory();
        c.setCategoryCode(code);
        c.setScore(score);
        return c;
    }

    private BizIncentiveRecord record(String pre, String mid, String post) {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setFilePreId(pre);
        r.setFileMidId(mid);
        r.setFilePostId(post);
        return r;
    }

    // ======================== 计分规则 ========================

    @Test
    void planCategory_anySingleFileScoresBase() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), mock(JdbcTemplate.class));
        BizIncentiveCategory plan = category("plan", 100);
        assertEquals(100, calcScore(service, record("[1]", null, null), plan, 100));
        assertEquals(100, calcScore(service, record(null, "[1]", null), plan, 100));
        assertEquals(100, calcScore(service, record(null, null, "[1]"), plan, 100));
    }

    @Test
    void planCategory_noFilesScoresZero() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), mock(JdbcTemplate.class));
        assertEquals(0, calcScore(service, record(null, null, null), category("plan", 100), 100));
    }

    @Test
    void planCategory_multipleFilesScoresBaseOnce() {
        // 2026-08-10 规则确认: 两谈两做多文件也只积一次(不论数量, 上限5份)
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), mock(JdbcTemplate.class));
        BizIncentiveCategory plan = category("plan", 100);
        assertEquals(100, calcScore(service, record("[1,2,3,4,5]", null, null), plan, 100));
        assertEquals(100, calcScore(service, record("[1,2]", "[3,4]", "[5]"), plan, 100));
    }

    @Test
    void otherCategory_requiresAllThreeFiles() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), mock(JdbcTemplate.class));
        BizIncentiveCategory promo = category("promotion", 100);
        assertEquals(100, calcScore(service, record("[1]", "[2]", "[3]"), promo, 100));
        assertEquals(0, calcScore(service, record("[1]", "[2]", null), promo, 100));
        assertEquals(0, calcScore(service, record("[1]", null, "[3]"), promo, 100));
        assertEquals(0, calcScore(service, record(null, "[2]", "[3]"), promo, 100));
    }

    @Test
    void nullCategoryOrZeroBaseScoresZero() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), mock(JdbcTemplate.class));
        assertEquals(0, calcScore(service, record("[1]", "[2]", "[3]"), null, 100));
        assertEquals(0, calcScore(service, record("[1]", "[2]", "[3]"), category("plan", 0), 0));
    }

    @Test
    void childOfPlanParentCountsAsPlan() {
        BizIncentiveCategoryMapper mapper = mock(BizIncentiveCategoryMapper.class);
        BizIncentiveCategory parent = category("plan", 100);
        parent.setId(1L);
        when(mapper.selectById(1L)).thenReturn(parent);
        IncentiveService service = newService(mapper, mock(JdbcTemplate.class));

        BizIncentiveCategory child = new BizIncentiveCategory();
        child.setParentId(1L);
        child.setScore(100);
        assertEquals(100, calcScore(service, record("[1]", null, null), child, 100));
    }

    // ======================== 专项授权判定（2026-08-16 授权表化） ========================

    /** 设置当前登录用户（roleCodes 含 admin 时 SecurityUtils.isAdmin 为真） */
    private void setCurrentUser(SecurityUser u) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(u);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 表 stub：无授权记录（查询抛 EmptyResult，走默认语义） */
    private JdbcTemplate jdbcWithoutRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), eq(5L))).thenThrow(new EmptyResultDataAccessException(1));
        return jdbc;
    }

    /** 表 stub：存在授权行 */
    private JdbcTemplate jdbcWithRow(Boolean viewShared, Boolean viewAll, Boolean edit, Boolean delete) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>();
        if (viewShared != null) row.put("can_view_shared", viewShared ? 1 : 0);
        if (viewAll != null) row.put("can_view_all", viewAll ? 1 : 0);
        if (edit != null) row.put("can_edit", edit ? 1 : 0);
        if (delete != null) row.put("can_delete", delete ? 1 : 0);
        when(jdbc.queryForMap(anyString(), eq(5L))).thenReturn(row);
        return jdbc;
    }

    @Test
    void canEdit_adminRoleInAnyPosition_grantsFullPermission() {
        // admin 在角色列表第二位：修复前取首角色会误判为销售员
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Arrays.asList("sales_engineer", "admin"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), mock(JdbcTemplate.class));
        assertTrue(service.canEdit());
        assertTrue(service.canDelete());
    }

    @Test
    void canEdit_grantEditTrue_true() {
        // 授权表 can_edit=1/can_delete=1（销售总监迁移语义）→ 可编辑/删除他人
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Arrays.asList("06", "0"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class),
                jdbcWithRow(null, null, true, true));
        assertTrue(service.canEdit());
        assertTrue(service.canDelete());
    }

    @Test
    void canEdit_noGrantRow_false() {
        // 无授权记录（默认全关）→ 不能编辑/删除他人激励记录
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), jdbcWithoutRow());
        assertFalse(service.canEdit());
        assertFalse(service.canDelete());
    }

    @Test
    void canViewAll_grantViewAllOnly_true_notEditDelete() {
        // 仅 can_view_all=1（只读看全部）→ canViewAll true，但不隐含编辑/删除
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class),
                jdbcWithRow(null, true, false, false));
        assertTrue(service.canViewAll(5L));
        assertFalse(service.canEdit());
        assertFalse(service.canDelete());
    }

    @Test
    void canViewShared_defaultOnWhenNoRow() {
        // 无授权记录 = 默认开（全员可看他人已分享记录）
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class), jdbcWithoutRow());
        assertTrue(service.canViewShared(5L), "无记录应默认可查看已分享记录");
    }

    @Test
    void canViewShared_rowZero_meansNarrowed() {
        // 授权行 can_view_shared=0 → 收窄，不可看他人已分享
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class),
                jdbcWithRow(false, null, null, null));
        assertFalse(service.canViewShared(5L), "授权行 can_view_shared=0 应拒绝查看已分享");
    }
}
