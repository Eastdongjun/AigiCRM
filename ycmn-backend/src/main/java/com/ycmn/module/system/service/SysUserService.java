package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.entity.SysUserRole;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysConfigService configService;
    private final BizCustomerMapper customerMapper;
    private final BizCustomerShareMapper shareMapper;
    private final SeaService seaService;

    public IPage<SysUser> page(int current, int size, String keyword, String deptIds, Integer status) {
        return userMapper.selectUserPage(new Page<>(current, size), keyword, deptIds, status);
    }

    public SysUser getById(Long id) {
        return userMapper.selectById(id);
    }

    @Transactional
    public void create(SysUser user) {
        // 检查用户名唯一性
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()).eq(SysUser::getDeleted, 0)
        );
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode("123456")); // 默认密码
        // 从配置读取默认存储空间（单位：字节），默认10GB
        long defaultStorage = 10737418240L;
        String storageVal = configService.getValue("user.default.storage");
        if (storageVal != null) {
            try { defaultStorage = Long.parseLong(storageVal.trim()); } catch (NumberFormatException ignored) {}
        }
        user.setStorageQuota(defaultStorage);
        user.setStorageUsed(0L);
        userMapper.insert(user);

        // 分配角色
        if (user.getRoleId() != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(user.getRoleId());
            userRoleMapper.insert(ur);
        }
    }

    @Transactional
    public void update(SysUser user) {
        SysUser existing = userMapper.selectById(user.getId());
        if (existing == null) throw new BusinessException("用户不存在");

        existing.setRealName(user.getRealName());
        existing.setPhone(user.getPhone());
        existing.setEmail(user.getEmail());
        existing.setDeptId(user.getDeptId());
        existing.setOfficeId(user.getOfficeId());
        existing.setStatus(user.getStatus());
        userMapper.updateById(existing);

        // 更新角色
        if (user.getRoleId() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(user.getRoleId());
            userRoleMapper.insert(ur);
        }
    }

    public void resetPassword(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(passwordEncoder.encode("123456"));
        userMapper.updateById(user);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    public void updateStorageQuota(Long userId, Long quota) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStorageQuota(quota);
        userMapper.updateById(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        // 1. 将该用户名下所有客户释放到公海
        java.util.List<BizCustomer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getOwnerId, userId)
                        .ne(BizCustomer::getStatus, "sea")
                        .eq(BizCustomer::getDeleted, 0));
        for (BizCustomer c : customers) {
            seaService.addToSea(c.getId(), "离职交接");
            // 清除该客户的分享记录
            shareMapper.delete(new LambdaQueryWrapper<BizCustomerShare>()
                    .eq(BizCustomerShare::getCustomerId, c.getId()));
        }

        // 2. 清除该用户作为分享接收者的记录
        shareMapper.delete(new LambdaQueryWrapper<BizCustomerShare>()
                .eq(BizCustomerShare::getSharedToUserId, userId));

        // 3. 删除角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));

        // 4. 软删除用户
        userMapper.deleteById(userId);
    }
}
