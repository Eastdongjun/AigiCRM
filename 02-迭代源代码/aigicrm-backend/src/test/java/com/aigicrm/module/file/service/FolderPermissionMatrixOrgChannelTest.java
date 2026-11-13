package com.aigicrm.module.file.service;

import com.aigicrm.common.auth.AuthorizationService;
import com.aigicrm.common.auth.GrantReader;
import com.aigicrm.common.auth.PermActionRegistry;
import com.aigicrm.common.auth.ScopeSpecResolver;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【FILE-12 P2 2026-11-14】matrixDeptSet→check 生产链回归钉（文件权限专项复检二）。
 * 生产配置实态 = file|manage=dept + anchor=OWNER（V2026.11.10.1 种子），matrixDeptSet
 * 固定请求 ORG。FILE-07 修复前：resolveScope 新轨丢请求锚 → 配置锚 OWNER 分支
 * visibleDeptIds 恒空 → deptVisible 恒 false → 部门档角色在 perm.read.file=new 下
 * 上传/改名/替换/分享/建子夹全 403（功能回归）。
 * 本测试直连 check() 真链：数据侧（类型授权/ACL/manager 链）stub 为无命中强制走矩阵
 * 通道，解析链（OrgScopeResolver→AuthorizationService→授予表→槽聚合）全真——
 * 锚点透传/槽聚合/维度补齐任一回归都会在此变红，不再依赖 KernelScopeTest 的 mock 锚点。
 */
class FolderPermissionMatrixOrgChannelTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final com.aigicrm.module.system.service.SysConfigService configService =
            mock(com.aigicrm.module.system.service.SysConfigService.class);
    private final com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService =
            mock(com.aigicrm.module.system.service.RolePermScopeService.class);

    @Test
    void check_orgChannel_requestedOrgAnchor_honored_afterFile07() {
        // --- 内核侧桩（与生产实态一致：生产码 acl:manage / 配置锚 OWNER / 槽聚合五码） ---
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("file:acl:manage")).thenReturn(
                new PermActionRegistry.ActionDef("file:acl:manage", "file", "file", "uploader_id", null, true, false));
        when(registry.codeOf("file", "manage")).thenReturn("file:acl:manage");
        when(registry.slotOf("file:acl:manage")).thenReturn(new String[]{"file", "manage"});
        when(registry.codesOf("file", "manage")).thenReturn(List.of(
                "file:acl:manage", "file:folder:manage", "file:upload", "file:edit", "file:share"));
        when(configService.getValue("perm.read.file")).thenReturn("new");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("sys_org_closure"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(11L));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("dept_id IN"), eq(Long.class), eq(10L), eq(11L)))
                .thenReturn(List.of(7L, 88L));
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("manage")))
                .thenReturn(ScopeRequest.AnchorType.OWNER); // 生产默认（V2026.11.10.1 种子）
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("file:acl:manage"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        AuthorizationService auth = new AuthorizationService(registry, reader, new ScopeSpecResolver(jdbc),
                mock(OrgScopeResolver.class), rolePermScopeService, configService, jdbc);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<AuthorizationService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(auth);
        OrgScopeResolver resolver = new OrgScopeResolver(
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                configService,
                mock(com.aigicrm.module.system.mapper.SysRoleOrgMapper.class),
                jdbc, provider);

        // --- 数据侧桩：ACL/类型授权无命中 + owner 非上级（强制走矩阵通道） ---
        com.aigicrm.module.file.mapper.BizFileFolderMapper folderMapper =
                mock(com.aigicrm.module.file.mapper.BizFileFolderMapper.class);
        BizFileFolder folder10 = new BizFileFolder();
        folder10.setId(10L);
        folder10.setFolderType("public");
        folder10.setOwnerId(99L);
        folder10.setOrgId(10L);
        BizFileFolder folder20 = new BizFileFolder();
        folder20.setId(20L);
        folder20.setFolderType("public");
        folder20.setOwnerId(99L);
        folder20.setOrgId(20L);
        when(folderMapper.selectById(10L)).thenReturn(folder10);
        when(folderMapper.selectById(20L)).thenReturn(folder20);
        com.aigicrm.module.file.mapper.BizFileMapper fileMapper =
                mock(com.aigicrm.module.file.mapper.BizFileMapper.class);
        com.aigicrm.module.system.mapper.SysUserMapper userMapper =
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        SysUser owner = new SysUser(); // managerId/deptId=null → manager 链不命中
        when(userMapper.selectById(99L)).thenReturn(owner);
        com.aigicrm.module.system.mapper.SysDeptMapper deptMapper =
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class);
        com.aigicrm.module.system.mapper.SysRoleMapper roleMapper =
                mock(com.aigicrm.module.system.mapper.SysRoleMapper.class);
        com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper grantMapper =
                mock(com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper.class);
        when(grantMapper.selectList(any())).thenReturn(List.of()); // 类型授权/ACL 无命中

        FolderPermissionService fps = new FolderPermissionService(
                folderMapper, fileMapper, userMapper, deptMapper, roleMapper, grantMapper, resolver);

        SecurityUser su = new SecurityUser();
        su.setUserId(7L);
        su.setPermissions(new HashSet<>(List.of("file:view")));
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            // ① 矩阵部门集 = MY_DEPT_SUB 闭包{10,11}（修复前恒空集 → 全 403）
            Set<Long> depts = fps.matrixDeptSet(7L, "manage");
            assertNotNull(depts, "dept 档矩阵部门集不得为 null（null=all 档，语义错误）");
            assertEquals(Set.of(10L, 11L), depts, "部门维=MY_DEPT_SUB 闭包{10,11}（请求 ORG 被配置 OWNER 吞掉则此集为空）");
            // ② check 全链：本部门夹 upload 放行 / 其他部门夹拒绝（修方向不是放大）
            assertTrue(fps.check(10L, FolderPermissionService.P_UPLOAD, 7L),
                    "部门档(file|manage=dept)对本部门夹 upload 必须放行（FILE-07 修复前=恒 403）");
            assertFalse(fps.check(20L, FolderPermissionService.P_UPLOAD, 7L),
                    "对其他部门夹 upload 必须拒绝（修复是恢复部门维，不是放大）");
        }
    }
}
