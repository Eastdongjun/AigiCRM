package com.ycmn.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String usernameOrId) throws UsernameNotFoundException {
        SysUser user;

        // 尝试按ID查找（JWT过滤器传入的是userId）
        try {
            Long userId = Long.parseLong(usernameOrId);
            user = userMapper.selectById(userId);
        } catch (NumberFormatException e) {
            // 按用户名查找（登录时传入的是username）
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, usernameOrId)
                            .eq(SysUser::getDeleted, 0)
            );
        }

        if (user == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户不存在或已禁用");
        }

        // 查询用户权限
        Set<String> permissions = menuMapper.selectPermissionsByUserId(user.getId());

        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        SecurityUser securityUser = new SecurityUser();
        securityUser.setUserId(user.getId());
        securityUser.setUsername(user.getUsername());
        securityUser.setPassword(user.getPassword());
        securityUser.setRealName(user.getRealName());
        securityUser.setDeptId(user.getDeptId());
        securityUser.setOfficeId(user.getOfficeId());
        securityUser.setPermissions(permissions);
        securityUser.setAuthorities(authorities);

        // 填充角色信息（取第一个角色）
        List<SysRole> roles = roleMapper.selectRolesByUserId(user.getId());
        if (!roles.isEmpty()) {
            SysRole primaryRole = roles.get(0);
            securityUser.setRoleCode(primaryRole.getRoleCode());
            securityUser.setDataScope(primaryRole.getDataScope() != null ? primaryRole.getDataScope() : "self");
        }

        return securityUser;
    }
}
