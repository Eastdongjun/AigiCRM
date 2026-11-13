package com.aigicrm.module.auth.dto;

import com.aigicrm.module.system.entity.SysMenu;
import lombok.Data;
import java.util.List;
import java.util.Set;

@Data
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String realName;
    private String avatar;
    private String deptName;
    private String officeName;
    private List<String> roles;
    private String roleName;
    private String dataScope;
    private List<PositionSummary> positions;
    private List<Long> groupIds;
    private Long currentGroupId;
    /** 【2026-10-08】当前集团名(展示用; 修复前端渲染裸 ID) */
    private String currentGroupName;
    /** 【2026-10-08】集团 id→名称映射(切换器显示名字; 与 groupIds 对齐) */
    private java.util.Map<Long, String> groupNames;
    private Set<String> permissions;
    private List<SysMenu> menus;
    /** 【WP5 2026-11-13】权限版本号: 双端比对, 变更即拉新用户信息(菜单/按钮即时生效) */
    private Long permEpoch;
}
