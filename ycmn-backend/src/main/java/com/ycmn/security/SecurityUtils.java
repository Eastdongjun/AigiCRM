package com.ycmn.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static SecurityUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            return (SecurityUser) authentication.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static String getCurrentUsername() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public static String getCurrentRealName() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getRealName() : null;
    }

    public static Long getCurrentOfficeId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getOfficeId() : null;
    }

    public static Long getCurrentDeptId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getDeptId() : null;
    }

    public static String getCurrentRoleCode() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getRoleCode() : null;
    }

    public static String getCurrentDataScope() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getDataScope() : "self";
    }
}
