package com.ycmn.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

@Data
public class SecurityUser implements UserDetails {
    private Long userId;
    private String username;
    private String password;
    private String realName;
    private Long deptId;
    private Long officeId;
    private String roleCode;
    private String dataScope = "self";
    private Set<String> permissions;
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
