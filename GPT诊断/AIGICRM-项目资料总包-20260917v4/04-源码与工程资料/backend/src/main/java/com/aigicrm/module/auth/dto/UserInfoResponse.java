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
    private Set<String> permissions;
    private List<SysMenu> menus;
}
