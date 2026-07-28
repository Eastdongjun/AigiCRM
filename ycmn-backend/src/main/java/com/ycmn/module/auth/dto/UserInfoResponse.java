package com.ycmn.module.auth.dto;

import com.ycmn.module.system.entity.SysMenu;
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
    private Set<String> permissions;
    private List<SysMenu> menus;
}
