package com.ycmn.module.system.service;

import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysRoleMenuMapper;
import com.ycmn.module.system.mapper.SysRoleOrgMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SysRoleServiceTest {

    private SysRoleService newService(SysRoleOrgMapper orgMapper) {
        return new SysRoleService(
                mock(SysRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                mock(SysMenuMapper.class),
                orgMapper);
    }

    @Test
    void updateOrgs_customScope_savesSelectedOrgs() {
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newService(orgMapper);

        service.updateOrgs(5L, List.of(10L, 20L));

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, times(2)).insert(any());
    }

    @Test
    void updateOrgs_emptyList_clearsOnly() {
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newService(orgMapper);

        service.updateOrgs(5L, List.of());

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, never()).insert(any());
    }

    @Test
    void updateOrgs_nullList_clearsOnly() {
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newService(orgMapper);

        service.updateOrgs(5L, null);

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, never()).insert(any());
    }
}
