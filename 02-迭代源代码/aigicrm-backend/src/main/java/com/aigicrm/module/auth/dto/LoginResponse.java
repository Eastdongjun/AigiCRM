package com.aigicrm.module.auth.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    /** 【批④ 2026-08-10】用户所属集团集合(岗位推导) */
    private java.util.List<Long> groupIds = new java.util.ArrayList<>();
    /** 当前会话集团(默认最小; 多集团时可切换) */
    private Long currentGroupId;
    /** 【2026-08-14 安全 P0】首次登录强制改密标记(1=前端强制跳转改密页; 初始/重置口令为随机值) */
    private Integer pwdResetRequired;
}
