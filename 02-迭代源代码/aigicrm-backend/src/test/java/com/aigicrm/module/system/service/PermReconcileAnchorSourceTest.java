package com.aigicrm.module.system.service;

import com.aigicrm.common.auth.AuthorizationService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【FILE-13 P2 2026-11-14】对账锚点与生产消费方同源回归钉（文件专项复检三）。
 * 生产文件夹矩阵消费方 matrixDeptSet 固定请求 ORG——对账槽若只做 OWNER 对比,
 * "对账 0 差异"覆盖不到 ORG 通道, FILE-07 类回归（新轨丢请求锚→文件夹矩阵全 403）
 * 对账工具抓不到。本测试钉死: file|manage 的 ORG 槽对账时, 新旧两轨**都**收到 ORG 锚
 * （旧轨 req.anchor=ORG / 新轨 scopeForUser 第 3 参=ORG）; FILE-07 若回退（新轨忽略
 * 请求锚）, 新轨收不到 ORG → 此处变红。
 */
class PermReconcileAnchorSourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void reconcile_fileManageOrgSlot_usesOrgAnchorOnBothTracks() {
        OrgScopeResolver legacyResolver = mock(OrgScopeResolver.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        PermReconcileService svc = new PermReconcileService(
                legacyResolver,
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                authService,
                mock(RolePermScopeService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));

        SecurityUser su = new SecurityUser();
        su.setUserId(7L);
        su.setUsername("NO001");

        ScopeDecision orgView = new ScopeDecision();
        orgView.setVisibleUserIds(List.of(7L, 88L));
        orgView.setVisibleDeptIds(List.of(10L, 11L));
        // 旧轨: resolveScopeLegacy(req) 原样回显(锚点由断言侧 ArgumentCaptor 钉)
        when(legacyResolver.resolveScopeLegacy(any())).thenReturn(orgView);
        when(authService.actionCodeOf("file", "manage")).thenReturn("file:acl:manage");
        when(authService.scopeForUser(any(SecurityUser.class), eq("file:acl:manage"),
                eq(ScopeRequest.AnchorType.ORG))).thenReturn(orgView);

        // file|manage ORG 槽(生产矩阵通道)——SLOTS 经反射取, 或直接构造同款槽
        PermReconcileService.Slot orgSlot = new PermReconcileService.Slot(
                "file", "manage", false, ScopeRequest.AnchorType.ORG);
        var row = svc.reconcileUser(su, orgSlot);

        assertTrue((Boolean) row.get("match"), "双轨同锚(ORG)对账必须 match");
        assertEquals("ORG", row.get("anchor"), "行输出须带锚点字段(同槽 OWNER/ORG 两行靠此区分)");
        // 新轨: scopeForUser 第 3 参必须= ORG(生产 matrixDeptSet 请求锚)——FILE-07 回退则此处红
        ArgumentCaptor<ScopeRequest.AnchorType> anchorCap = ArgumentCaptor.forClass(ScopeRequest.AnchorType.class);
        verify(authService).scopeForUser(any(SecurityUser.class), eq("file:acl:manage"), anchorCap.capture());
        assertEquals(ScopeRequest.AnchorType.ORG, anchorCap.getValue(),
                "新轨对账须收到 ORG 锚(与生产矩阵消费方同源)");
        // 旧轨: req.anchor 必须= ORG(同源)
        ArgumentCaptor<ScopeRequest> reqCap = ArgumentCaptor.forClass(ScopeRequest.class);
        verify(legacyResolver).resolveScopeLegacy(reqCap.capture());
        assertEquals(ScopeRequest.AnchorType.ORG, reqCap.getValue().getAnchor(),
                "旧轨对账 req 锚须= ORG(同槽同锚才可比)");
    }
}
