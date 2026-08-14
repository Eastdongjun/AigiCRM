package com.ycmn.module.incentive.service;

import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncentiveScoreTest {

    private IncentiveService newService(BizIncentiveCategoryMapper categoryMapper) {
        return new IncentiveService(
                categoryMapper,
                mock(BizIncentiveRecordMapper.class),
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysDeptMapper.class),
                mock(com.ycmn.common.scope.OrgScopeResolver.class));
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

    @Test
    void planCategory_anySingleFileScoresBase() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        BizIncentiveCategory plan = category("plan", 100);
        assertEquals(100, calcScore(service, record("[1]", null, null), plan, 100));
        assertEquals(100, calcScore(service, record(null, "[1]", null), plan, 100));
        assertEquals(100, calcScore(service, record(null, null, "[1]"), plan, 100));
    }

    @Test
    void planCategory_noFilesScoresZero() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertEquals(0, calcScore(service, record(null, null, null), category("plan", 100), 100));
    }

    @Test
    void planCategory_multipleFilesScoresBaseOnce() {
        // 2026-08-10 规则确认: 两谈两做多文件也只积一次(不论数量, 上限5份)
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        BizIncentiveCategory plan = category("plan", 100);
        // 单槽 5 份文件 → 100(不是 5×100)
        assertEquals(100, calcScore(service, record("[1,2,3,4,5]", null, null), plan, 100));
        // 三槽各多份 → 100
        assertEquals(100, calcScore(service, record("[1,2]", "[3,4]", "[5]"), plan, 100));
    }

    @Test
    void otherCategory_requiresAllThreeFiles() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        BizIncentiveCategory promo = category("promotion", 100);
        assertEquals(100, calcScore(service, record("[1]", "[2]", "[3]"), promo, 100));
        assertEquals(0, calcScore(service, record("[1]", "[2]", null), promo, 100));
        assertEquals(0, calcScore(service, record("[1]", null, "[3]"), promo, 100));
        assertEquals(0, calcScore(service, record(null, "[2]", "[3]"), promo, 100));
    }

    @Test
    void nullCategoryOrZeroBaseScoresZero() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertEquals(0, calcScore(service, record("[1]", "[2]", "[3]"), null, 100));
        assertEquals(0, calcScore(service, record("[1]", "[2]", "[3]"), category("plan", 0), 0));
    }

    @Test
    void childOfPlanParentCountsAsPlan() {
        BizIncentiveCategoryMapper mapper = mock(BizIncentiveCategoryMapper.class);
        BizIncentiveCategory parent = category("plan", 100);
        parent.setId(1L);
        when(mapper.selectById(1L)).thenReturn(parent);
        IncentiveService service = newService(mapper);

        BizIncentiveCategory child = new BizIncentiveCategory();
        child.setParentId(1L);
        child.setScore(100);
        assertEquals(100, calcScore(service, record("[1]", null, null), child, 100));
    }

    // ======================== H6：RBAC 权限码判定（2026-08-14 收敛，不再依赖首角色/销售总监硬编码） ========================

    private void setCurrentUser(SecurityUser u, String... perms) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(u);
        when(auth.getAuthorities()).thenAnswer(inv -> java.util.Arrays.stream(perms)
                .map(p -> (GrantedAuthority) () -> p)
                .collect(java.util.stream.Collectors.toList()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void canEdit_adminRoleInAnyPosition_grantsFullPermission() {
        // admin 在角色列表第二位：修复前取首角色会误判为销售员
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Arrays.asList("sales_engineer", "admin"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertTrue(service.canEdit());
        assertTrue(service.canDelete());
    }

    @Test
    void canEdit_salesDirectorRole_true() {
        // 销售总监(角色0)经 M1 迁移获得 incentive:manage/incentive:view-all 权限码（2026-08-14 RBAC 收敛）
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Arrays.asList("06", "0")); // 销售员 + 销售总监
        setCurrentUser(u, "incentive:manage", "incentive:view-all");

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertTrue(service.canEdit());
        assertTrue(service.canDelete());
    }

    @Test
    void canEdit_nonAdmin_nonDirector_false() {
        // 普通销售员(06)/行政(05)未绑定激励管理角色 → 不能编辑/删除他人激励记录（2026-08-14 RBAC 收敛，不再查 can_edit 表/销售总监）
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u);

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertFalse(service.canEdit());
        assertFalse(service.canDelete());
    }

    /** 非 admin 但持 incentive:manage 权限码（M1 迁移绑定 admin+销售总监角色）→ 可编辑/删除他人 */
    @Test
    void canEdit_manageAuthorityOnly_true() {
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u, "incentive:manage");

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertTrue(service.canEdit());
        assertTrue(service.canDelete());
    }

    /** 仅 incentive:view-all（只读看全部）→ canViewAll true，但不隐含编辑/删除 */
    @Test
    void canViewAll_viewAllAuthorityOnly_true_notEditDelete() {
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u, "incentive:view-all");

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertTrue(service.canViewAll(5L));
        assertFalse(service.canEdit());
        assertFalse(service.canDelete());
    }

    /** incentive:view-shared 决定"查看已分享"（「激励-分享可见」角色 M1 全员绑定，等价旧"无面板行默认可见"）；未绑则收窄 */
    @Test
    void canViewShared_sharedAuthorityControlsVisibility() {
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setRoleCodes(java.util.Collections.singletonList("06"));
        setCurrentUser(u, "incentive:view-shared");

        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertTrue(service.canViewShared(5L), "持 incentive:view-shared 应可查看已分享记录");

        setCurrentUser(u); // 未绑「激励-分享可见」→ 不可看已分享（收窄语义）
        assertFalse(service.canViewShared(5L), "未持 incentive:view-shared 不应查看已分享记录");
    }
}
