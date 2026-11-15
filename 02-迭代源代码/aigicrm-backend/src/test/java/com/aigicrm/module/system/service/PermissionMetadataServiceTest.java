package com.aigicrm.module.system.service;

import com.aigicrm.module.system.entity.SysMenu;
import com.aigicrm.module.system.mapper.SysMenuMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 权限点元数据：类型/父码/范围轴/adminOnly/依赖/废弃。 */
class PermissionMetadataServiceTest {

    private static SysMenu menu(long id, Long parent, String name, String perm, int type, int status) {
        SysMenu m = new SysMenu();
        m.setId(id); m.setParentId(parent); m.setMenuName(name);
        m.setPermission(perm); m.setMenuType(type); m.setStatus(status);
        return m;
    }

    @Test
    void buildsFieldAndAdminOnlyMetadata() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of(
                menu(41, 10L, "客户编辑", "customer:edit", 3, 1),
                menu(128, 41L, "修改客户保护日期（字段）", "customer:date:edit", 3, 1),
                menu(78, 77L, "新增集团", "system:group:add", 3, 1),
                menu(158, 34L, "触发到期任务", "system:task:run", 3, 1),
                menu(122, 15L, "激励查看(全部)", "incentive:view-all", 3, 0)));
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(java.util.List.of());
        PermissionMetadataService svc = new PermissionMetadataService(mapper, jdbc);
        List<Map<String, Object>> meta = svc.list();
        Map<String, Map<String, Object>> byCode = new java.util.HashMap<>();
        for (Map<String, Object> m : meta) byCode.put((String) m.get("code"), m);

        assertEquals("FIELD", byCode.get("customer:date:edit").get("type"));
        assertEquals("customer:edit", byCode.get("customer:date:edit").get("parentCode"));
        assertEquals("INHERIT", byCode.get("customer:date:edit").get("scopeMode"));
        assertTrue((Boolean) byCode.get("system:group:add").get("adminOnly"));
        assertTrue((Boolean) byCode.get("system:task:run").get("adminOnly"));
        assertTrue((Boolean) byCode.get("incentive:view-all").get("deprecated"));
        assertEquals("DIRECT", byCode.get("customer:edit").get("scopeMode"));
    }

    @Test
    void readsRegistryTableWhenAvailable() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("code", "customer:date:edit"); row.put("name", "修改客户保护日期（字段）");
        row.put("type", "FIELD"); row.put("module", "customer"); row.put("parent_code", "customer:edit");
        row.put("scope_module", null); row.put("scope_action", null); row.put("scope_mode", "INHERIT");
        row.put("admin_only", 0); row.put("requires_org_admin", 0); row.put("deprecated", 0);
        row.put("no_endpoint", 0); row.put("depends_on", "customer:edit");
        when(jdbc.queryForList(anyString())).thenReturn(java.util.List.of(row));
        PermissionMetadataService svc = new PermissionMetadataService(mapper, jdbc);
        java.util.List<java.util.Map<String, Object>> meta = svc.list();
        assertEquals(1, meta.size());
        assertEquals("FIELD", meta.get(0).get("type"));
        assertEquals("customer:edit", meta.get(0).get("parentCode"));
        assertEquals("INHERIT", meta.get(0).get("scopeMode"));
    }
}
