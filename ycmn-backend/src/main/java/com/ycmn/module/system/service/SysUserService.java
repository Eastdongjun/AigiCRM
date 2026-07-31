package com.ycmn.module.system.service;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.security.SecurityUtils;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.entity.SysUserRole;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.entity.SysRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final SysRoleMapper roleMapper;
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
        existing.setManagerId(user.getManagerId());
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

    // ====== 离职/返聘/转移 ======

    @Transactional
    public Map<String, Object> markLeave(Long userId, Long transferToUserId, String reason) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        int transferred = 0;
        if (transferToUserId != null) {
            transferred = transferAllCustomers(userId, transferToUserId);
            jdbc.update("INSERT INTO biz_customer_transfer (from_user_id,to_user_id,transfer_type,customer_count,operator_id) VALUES (?,?,?,?,?)",
                userId, transferToUserId, "leave", transferred, SecurityUtils.getCurrentUserId());
        }
        user.setLeaveStatus(1);
        user.setLeaveDate(java.time.LocalDate.now());
        user.setLeaveReason(reason);
        user.setStatus(0);
        userMapper.updateById(user);
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("transferred", transferred);
        return r;
    }

    public void markRehire(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setLeaveStatus(0);
        user.setLeaveDate(null);
        user.setLeaveReason(null);
        user.setStatus(1);
        userMapper.updateById(user);
    }

    public Map<String, Object> transferCustomers(Long fromUserId, Long toUserId) {
        int count = transferAllCustomers(fromUserId, toUserId);
        jdbc.update("INSERT INTO biz_customer_transfer (from_user_id,to_user_id,transfer_type,customer_count,operator_id) VALUES (?,?,?,?,?)",
            fromUserId, toUserId, "manual", count, SecurityUtils.getCurrentUserId());
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("count", count);
        return r;
    }

    private int transferAllCustomers(Long fromUserId, Long toUserId) {
        int c = jdbc.update("UPDATE biz_customer SET owner_id=? WHERE owner_id=? AND deleted=0", toUserId, fromUserId);
        jdbc.update("UPDATE biz_customer_share SET shared_by_user_id=? WHERE shared_by_user_id=?", toUserId, fromUserId);
        jdbc.update("UPDATE biz_incentive_record SET user_id=? WHERE user_id=?", toUserId, fromUserId);
        return c;
    }

    @Transactional
    public void permanentDelete(Long userId, String reason) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) return;
        java.util.List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        String roleNames = roles.stream().map(SysRole::getRoleName).collect(java.util.stream.Collectors.joining(","));
        jdbc.update("INSERT INTO sys_user_archive (id,username,real_name,phone,email,dept_id,office_id,org_id,manager_id,role_names,data_scope,leave_status,leave_date,leave_reason,deleted_by,deleted_reason,original_created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(),
            user.getDeptId(), user.getOfficeId(), user.getOrgId(), user.getManagerId(),
            roleNames, roles.isEmpty() ? null : roles.get(0).getDataScope(),
            user.getLeaveStatus(), user.getLeaveDate(), user.getLeaveReason(),
            SecurityUtils.getCurrentUserId(), reason, user.getCreatedAt());
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        userMapper.deleteById(userId);
    }

    public java.util.List<Map<String, Object>> listArchived() {
        return jdbc.queryForList("SELECT * FROM sys_user_archive ORDER BY deleted_at DESC");
    }

}
