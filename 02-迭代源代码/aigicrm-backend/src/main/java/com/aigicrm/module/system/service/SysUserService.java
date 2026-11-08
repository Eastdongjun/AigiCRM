package com.aigicrm.module.system.service;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.enums.DataScopeType;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.customer.entity.BizCustomerShare;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.security.SecurityUtils;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.entity.SysUserRole;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysDept;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SysUserService {

    /** 用户级数据范围合法取值（DataScopeType 七大主码；不含 @Deprecated 的 org/org_and_children） */
    private static final java.util.Set<String> VALID_DATA_SCOPES = java.util.Set.of(
            "self", "team", "dept", "office", "region", "all", "custom");

    // ===== 【D1=A 2026-10-18 存储空间申请】配置键与配额边界 =====

    /** 非管理员申请开关(管理员始终可申请, 不受此开关限制) */
    public static final String STORAGE_APPLY_ENABLED_KEY = "file.storage.apply.enabled";
    /** 非管理员授权申请人名单(JSON 数组, 元素 userId) */
    public static final String STORAGE_APPLY_USERS_KEY = "file.storage.apply.users";
    /** 单用户配额上限(bytes), 系统设置可改 */
    public static final String STORAGE_MAX_QUOTA_KEY = "file.storage.max.quota";
    /** 上限配置非法/缺失时的回退值: 100GB */
    public static final long DEFAULT_STORAGE_MAX_QUOTA = 100L * 1024 * 1024 * 1024;
    /** 上限合法区间下界: 1GB(再低会导致正常用户无法申请) */
    public static final long MIN_STORAGE_MAX_QUOTA = 1L * 1024 * 1024 * 1024;
    /** 上限合法区间上界: 1TB(防配置误输天文数字) */
    public static final long MAX_STORAGE_MAX_QUOTA = 1024L * 1024 * 1024 * 1024;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 校验数据范围覆盖取值：空=跟随角色合法；非空必须在合法集合内，否则拒绝 */
    private static void validateDataScopeOverride(String override) {
        if (StringUtils.hasText(override) && !VALID_DATA_SCOPES.contains(override)) {
            throw new BusinessException("数据范围取值非法: " + override);
        }
    }


    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysConfigService configService;
    private final BizCustomerMapper customerMapper;
    private final BizCustomerShareMapper shareMapper;
    private final JdbcTemplate jdbc;
    private final SysRoleMapper roleMapper;
    private final SeaService seaService;
    private final com.aigicrm.module.system.mapper.SysDeptMapper deptMapper;
    private final com.aigicrm.module.system.mapper.SysUserOrgMapper userOrgMapper;
    private final com.aigicrm.security.PermissionCacheService permissionCacheService;
    private final com.aigicrm.security.JwtTokenProvider jwtTokenProvider;
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;
    private final com.aigicrm.security.SecurityAuditService securityAudit;
    /** 【议题四裁决】停用/删除时立即清理全部草稿 */
    private final com.aigicrm.module.common.service.FormDraftService formDraftService;

    /** 【2026-08-14 安全 P0】随机初始口令(12 位, 去易混字符, SecureRandom)——替代硬编码 123456 */
    private String generateRandomPassword() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    /** 【2026-08-14 缓存自愈】用户级权限版本递增：evict 漏网/Redis 写失败时，缓存与 DB 版本比对自愈 */
    private void bumpPermVersion(Long userId) {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 WHERE id=?", userId);
    }

    /** 主集团顶级组织兜底(sys_group id=1 对应的组织根,与 MybatisPlusConfig.applyGroupScope 退主集团口径一致) */
    private static final Long DEFAULT_ROOT_ORG_ID = 1L;

    /**
     * 【租户可见性 2026-09-23】默认组织落库:org_id 是集团/法人推导链的兜底输入
     * (无岗位时 user.org_id → dept.group_id)。此前新建用户从不落 org_id ⇒ 集团归属恒空
     * ⇒ 非 admin 对租户白名单表查询恒空(fail-closed;详见 V2026.09.23.3 迁移注记)。
     * 规则:部门有集团归属 → org_id=dept_id;否则兜底主集团顶级组织。
     */
    private void syncDefaultOrg(SysUser user) {
        if (user.getOrgId() != null) return; // 显式指定时尊重
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            if (dept != null && dept.getGroupId() != null) {
                user.setOrgId(user.getDeptId());
                return;
            }
        }
        user.setOrgId(DEFAULT_ROOT_ORG_ID);
    }

    /** 【2026-08-08 用户级 custom 授权】保存用户授权组织（清空重建） */
    private void saveUserOrgs(Long userId, List<Long> orgIds) {
        if (userId == null) return;
        userOrgMapper.deleteByUserId(userId);
        if (orgIds == null) return;
        for (Long orgId : orgIds) {
            if (orgId == null) continue;
            com.aigicrm.module.system.entity.SysUserOrg uo = new com.aigicrm.module.system.entity.SysUserOrg();
            uo.setUserId(userId);
            uo.setOrgId(orgId);
            uo.setIncludeChildren(1); // 默认含子级
            userOrgMapper.insert(uo);
        }
    }

    public IPage<SysUser> page(int current, int size, String keyword, String deptIds, Integer status) {
        String scopedDeptIds = applyVisibleDeptScope(deptIds);
        if (scopedDeptIds == null) {
            // 越权请求或本集团确无可见组织：直接返回空页，不下发任何记录
            return new Page<>(current, size, 0);
        }
        return userMapper.selectUserPage(new Page<>(current, size), keyword, scopedDeptIds, status);
    }

    /**
     * 【审查 perm-4 2026-10-18】人员列表与详情同口径的可见部门范围。
     * 旧实现只按集团过滤，同集团内非 admin 也能看到不可见部门的人员（列表/详情口径分裂）。
     * 现口径与 {@link #getById} 的读守卫一致：
     * <ul>
     *   <li>admin：不按部门过滤（仍保留调用方显式 deptIds）；</li>
     *   <li>非 admin 且 OrgScopeResolver 返回 null（all 范围）：仍受集团边界约束（applyGroupScope）；</li>
     *   <li>非 admin 其它范围：visibleDeptIds；组织管理员额外并入管辖组织子树；</li>
     *   <li>可见部门为空 → 返回 null（上层空页）；请求的 deptIds 与可见集合取交集。</li>
     * </ul>
     */
    private String applyVisibleDeptScope(String deptIds) {
        if (SecurityUtils.isAdmin()) return StringUtils.hasText(deptIds) ? deptIds : "";
        java.util.List<Long> visibleDeptIds = scopeResolver.resolveVisibleDeptIds(SecurityUtils.getCurrentUser());
        if (visibleDeptIds == null) {
            // all 范围：仍须落在当前用户集团的组织集合内（与 getById 的 all 分支同口径）
            return applyGroupScope(deptIds);
        }
        java.util.Set<Long> allowed = new LinkedHashSet<>(visibleDeptIds);
        if (SecurityUtils.isOrgAdmin()) {
            java.util.List<Long> managedOrgIds = SecurityUtils.getCurrentManagedOrgIds();
            if (managedOrgIds != null) {
                for (Long orgId : managedOrgIds) {
                    if (orgId == null) continue;
                    allowed.add(orgId);
                    java.util.List<Long> descendants = deptMapper.selectDescendantIds(orgId);
                    if (descendants != null) allowed.addAll(descendants);
                }
            }
        }
        if (allowed.isEmpty()) return null;
        if (!StringUtils.hasText(deptIds)) {
            return allowed.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        }
        java.util.Set<Long> requested = java.util.Arrays.stream(deptIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toSet());
        requested.retainAll(allowed);
        if (requested.isEmpty()) return null;
        return requested.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * 【修复 系统管理页无集团边界】人员列表此前不区分集团，任何持有 system:user:view 权限的
     * 账号都能看到全部集团的人员架构（当前测试/生产库仅 1 个集团在用，暂无实际数据泄漏，但
     * sys_group/sys_legal_entity 架构已经就绪，先补边界，避免后续真正接入第二个集团时才发现）。
     * 平台超管（isAdmin，即 hasRole("admin")）不受限，可跨集团管理/审计；其余账号按
     * SecurityUtils.getCurrentGroupIds() 限定到本集团的组织(部门)集合内；取不到集团归属时按
     * 与 MybatisPlusConfig.resolveTenantId 一致的口径退回主集团(1)。若调用方已经传了 deptIds
     * 筛选，取与"本集团允许集合"的交集（防止前端手改参数越权查询其他集团部门）。
     * 返回 null 表示本次查询无任何可见部门，调用方应直接返回空结果。
     */
    private String applyGroupScope(String deptIds) {
        // 【修复 2026-08-07 生产回归】admin 未传 deptIds 时透传 null，会被上层 page() 误判为
        // "本集团无可见部门"直接返回空页（管理员看不到人员数据）。归一为空串：mapper SQL 中
        // `#{deptIds} = ''` 与 IS NULL 等价（均视为"不按部门过滤"），且区别于"确无可见部门"的 null。
        if (SecurityUtils.isAdmin()) return StringUtils.hasText(deptIds) ? deptIds : "";
        java.util.List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        // 【PR-01 P0-02 fail-closed】无集团归属不再回退主集团 1：allowedDeptIds 为空 → 上层返回空页
        if (groupIds == null || groupIds.isEmpty()) return null;
        java.util.List<Long> allowedDeptIds = deptMapper.selectList(
                        new LambdaQueryWrapper<com.aigicrm.module.system.entity.SysDept>()
                                .in(com.aigicrm.module.system.entity.SysDept::getGroupId, groupIds))
                .stream().map(com.aigicrm.module.system.entity.SysDept::getId)
                .collect(java.util.stream.Collectors.toList());
        if (allowedDeptIds.isEmpty()) return null;
        if (!StringUtils.hasText(deptIds)) {
            return allowedDeptIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
        }
        java.util.Set<Long> requested = java.util.Arrays.stream(deptIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toSet());
        requested.retainAll(new java.util.HashSet<>(allowedDeptIds));
        if (requested.isEmpty()) return null;
        return requested.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    public SysUser getById(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) return null;
        // 【修复 2026-08-10 P1-01】读详情按"可见组织"校验——此前仅列表有集团边界、详情无任何校验,
        // 持 system:user:view/org:admin 者可按 ID 枚举任意用户全量 PII(0801 泄漏事件同源残留)。
        // 守卫: admin 全量; 本人恒可读; 其余目标用户必须落在当前用户的数据范围可见部门内
        // (self/team/office/region/dept/custom 各范围经 OrgScopeResolver 解析)。
        if (!SecurityUtils.isAdmin() && !id.equals(SecurityUtils.getCurrentUserId()) && !isUserVisible(u)) {
            throw new BusinessException(403, "无权查看该用户");
        }
        if ("custom".equals(u.getDataScopeOverride())) {
            // 【2026-08-08 用户级 custom 授权】回填个人授权组织，供编辑表单回显
            u.setOrgIds(userOrgMapper.selectByUserId(id).stream()
                    .map(com.aigicrm.module.system.entity.SysUserOrg::getOrgId).toList());
        }
        return u;
    }

    /**
     * 【审查 perm-3 2026-10-18】非 admin 写操作（岗位分配等）的目标用户可见性守卫。
     * 与 {@link #getById} 同口径：目标用户必须存在，且落在调用者数据范围可见部门
     * （visibleDeptIds）或组织管理员管辖组织子树内；admin 放行；本人恒可操作。
     */
    public void assertCanManageUser(Long userId) {
        if (userId == null) throw new BusinessException("用户不存在");
        SysUser target = userMapper.selectById(userId);
        if (target == null) throw new BusinessException("用户不存在");
        if (SecurityUtils.isAdmin()) return;
        if (userId.equals(SecurityUtils.getCurrentUserId())) return;
        if (!isUserVisible(target)) {
            throw new BusinessException(403, "无权操作该用户");
        }
    }

    /** 目标用户是否在当前用户可见范围：visibleDeptIds / 组织管理员管辖子树；all 范围叠加集团边界 */
    private boolean isUserVisible(SysUser target) {
        Long targetDeptId = target.getDeptId();
        java.util.List<Long> visibleDeptIds = scopeResolver.resolveVisibleDeptIds(SecurityUtils.getCurrentUser());
        if (visibleDeptIds == null) {
            // all 范围(总监等): 补集团边界——与列表 applyGroupScope 同口径, 防跨集团枚举
            if (targetDeptId == null) return false;
            String scoped = applyGroupScope(String.valueOf(targetDeptId));
            return scoped != null && java.util.Arrays.asList(scoped.split(",")).contains(String.valueOf(targetDeptId));
        }
        boolean allowed = targetDeptId != null && visibleDeptIds.contains(targetDeptId);
        if (!allowed && targetDeptId != null && SecurityUtils.isOrgAdmin()) {
            // 组织管理员兜底: 目标在其管辖组织子树内也可读(与写操作 canManageUser 同权)
            java.util.List<Long> managedOrgIds = SecurityUtils.getCurrentManagedOrgIds();
            if (managedOrgIds != null) {
                for (Long orgId : managedOrgIds) {
                    if (orgId == null) continue;
                    if (deptMapper.selectDescendantIds(orgId).contains(targetDeptId)) return true;
                }
            }
        }
        return allowed;
    }

    /**
     * 【安全 2026-08-08 提权防护】授予的角色/数据范围不得超过调用者自身权限（超管不受限）。
     * 防"持有 system:org:admin / system:user:edit 的账号把自己或他人提升为系统超管"。
     * 规则：非超管不可授予 admin 角色；授予角色的数据范围与 dataScopeOverride 不得宽于调用者自身范围。
     */
    private void validateGrantableRoleAndScope(Long roleId, String dataScopeOverride) {
        if (SecurityUtils.isAdmin()) return;
        DataScopeType callerScope = DataScopeType.fromCode(SecurityUtils.getCurrentDataScope());
        if (roleId != null) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) throw new BusinessException("角色不存在");
            if ("admin".equals(role.getRoleCode())) throw new BusinessException(403, "无权授予系统管理员角色");
            DataScopeType grantScope = DataScopeType.fromCode(role.getDataScope());
            if (grantScope.rank() < callerScope.rank()) {
                throw new BusinessException(403, "无权授予超出自身数据范围的角色");
            }
            // 【P0 2026-10-17 提权防护】授予的角色权限必须是调用者自身权限子集，
            // 与 SysRoleService.validateGrantableRole/saveRoleMenus 同口径：
            // 否则持 system:user:edit 的非超管可给用户/自己绑定含未授权权限码的角色，横向提权。
            validateGrantablePermissions(roleId);
        }
        if (StringUtils.hasText(dataScopeOverride)) {
            DataScopeType overrideScope = DataScopeType.fromCode(dataScopeOverride);
            if (overrideScope.rank() < callerScope.rank()) {
                throw new BusinessException(403, "无权设置超出自身数据范围的数据范围");
            }
        }
    }

    /** 授予角色的菜单权限码必须是调用者自身权限子集（admin 已在调用入口放行） */
    private void validateGrantablePermissions(Long roleId) {
        Set<String> callerPerms = SecurityUtils.getCurrentUser() != null
                && SecurityUtils.getCurrentUser().getPermissions() != null
                ? SecurityUtils.getCurrentUser().getPermissions() : Set.of();
        List<String> granted = jdbc.queryForList(
                "SELECT DISTINCT m.permission FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id "
                        + "WHERE rm.role_id = ? AND m.permission IS NOT NULL AND m.permission <> ''",
                String.class, roleId);
        if (granted == null) return;
        for (String permission : granted) {
            if (StringUtils.hasText(permission) && !callerPerms.contains(permission)) {
                throw new BusinessException(403, "无权授予权限: " + permission);
            }
        }
    }

    /**
     * 【P0 2026-10-17 用户管理越界】非 admin 创建/更新用户时，目标部门/办事处/组织必须在
     * 调用者可见范围内。不能只看请求里的 orgId（前端表单根本不传），必须同时校验 deptId/officeId；
     * 可见范围来源：OrgScopeResolver.resolveVisibleDeptIds（与人员详情读权限同源）；
     * 组织管理员额外允许其管辖组织子树（SecurityUtils.getCurrentManagedOrgIds + dept 子树）。
     * all 范围用户仍受本集团边界约束（与 applyGroupScope/列表同口径）。
     * 【审查 perm-3 2026-10-18】公开复用：岗位分配时校验岗位所属组织也在同一可见口径内。
     */
    public void assertTargetOrgVisible(Long deptId, Long officeId, Long orgId) {
        if (SecurityUtils.isAdmin()) return;
        Set<Long> targets = new LinkedHashSet<>();
        if (deptId != null) targets.add(deptId);
        if (officeId != null) targets.add(officeId);
        if (orgId != null) targets.add(orgId);
        if (targets.isEmpty()) return;

        List<Long> visibleDeptIds = scopeResolver.resolveVisibleDeptIds(SecurityUtils.getCurrentUser());
        Set<Long> managedOrgScope = new HashSet<>();
        List<Long> managedOrgIds = SecurityUtils.getCurrentManagedOrgIds();
        if (managedOrgIds != null) {
            for (Long managedOrgId : managedOrgIds) {
                if (managedOrgId == null) continue;
                managedOrgScope.add(managedOrgId);
                List<Long> descendants = deptMapper.selectDescendantIds(managedOrgId);
                if (descendants != null) managedOrgScope.addAll(descendants);
            }
        }
        for (Long target : targets) {
            boolean allowed;
            if (visibleDeptIds == null) {
                // all 范围：仍须落在当前用户集团的组织集合内（防跨集团）
                String scoped = applyGroupScope(String.valueOf(target));
                allowed = scoped != null && Arrays.asList(scoped.split(",")).contains(String.valueOf(target));
            } else {
                allowed = visibleDeptIds.contains(target) || managedOrgScope.contains(target);
            }
            if (!allowed) {
                throw new BusinessException(403, "无权在目标部门/组织下创建或修改用户");
            }
        }
    }

    @Transactional
    public String create(SysUser user) {
        securityAudit.userAdminEvent("user_create", user.getId(), user.getUsername());
        // 必填兜底校验（前端表单校验的接口层防线）
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException("账号不能为空");
        }
        if (!StringUtils.hasText(user.getRealName())) {
            throw new BusinessException("姓名不能为空");
        }
        if (user.getRoleId() == null) {
            throw new BusinessException("角色不能为空");
        }
        if (!StringUtils.hasText(user.getPhone())) {
            throw new BusinessException("手机号不能为空");
        }
        if (!StringUtils.hasText(user.getDataScopeOverride())) {
            throw new BusinessException("数据范围不能为空");
        }
        validateDataScopeOverride(user.getDataScopeOverride());
        validateGrantableRoleAndScope(user.getRoleId(), user.getDataScopeOverride());
        // 检查用户名唯一性
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()).eq(SysUser::getDeleted, 0)
        );
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        // 【2026-08-14 安全 P0】移除硬编码默认口令：随机初始口令 + 首登强制改密，口令随响应返回给管理员
        String tempPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setPwdResetRequired(1);
        // 从配置读取默认存储空间（单位：字节），默认10GB
        long defaultStorage = 10737418240L;
        String storageVal = configService.getValue("user.default.storage");
        if (storageVal != null) {
            try {
                defaultStorage = Long.parseLong(storageVal.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认配额:解析失败使用默认值(注明吞因,消除 S1166)
            }
        }
        user.setStorageQuota(defaultStorage);
        user.setStorageUsed(0L);
        if (!StringUtils.hasText(user.getDataScopeOverride())) {
            user.setDataScopeOverride(null); // 空=跟随角色，与 update 归一一致
        }
        // 【租户可见性 2026-09-23】默认组织随部门落库:org_id 是集团推导链的兜底输入
        // (无岗位时 user.org_id → dept.group_id)。此前新建用户从不落 org_id ⇒ 集团归属
        // 恒空 ⇒ 非 admin 租户白名单表查询恒空(fail-closed,详见 V2026.09.23.3 迁移注记)
        syncDefaultOrg(user);
        // 【P0 2026-10-17】非 admin 只能在自己的可见组织内建人（含 syncDefaultOrg 推导出的 orgId）
        assertTargetOrgVisible(user.getDeptId(), user.getOfficeId(), user.getOrgId());
        userMapper.insert(user);

        // 分配角色
        if (user.getRoleId() != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(user.getRoleId());
            userRoleMapper.insert(ur);
        }
        // 【2026-08-08 用户级 custom 授权】custom 时保存个人授权组织
        if ("custom".equals(user.getDataScopeOverride())) {
            saveUserOrgs(user.getId(), user.getOrgIds());
        }
        // 【2026-08-16 授权表化】新用户无需绑定载体角色——激励分享可见=授权表无记录时的默认语义(开)
        return tempPassword;
    }

    @Transactional
    public void update(SysUser user) {
        SysUser existing = userMapper.selectById(user.getId());
        securityAudit.userAdminEvent("user_update", user.getId(),
                existing != null ? existing.getUsername() : null);
        if (existing == null) throw new BusinessException("用户不存在");
        if (!StringUtils.hasText(user.getDataScopeOverride())) {
            throw new BusinessException("数据范围不能为空");
        }
        validateDataScopeOverride(user.getDataScopeOverride());
        validateGrantableRoleAndScope(user.getRoleId(), user.getDataScopeOverride());

        // 【P0 2026-10-17】非 admin 更新目标用户时，目标部门/办事处/组织必须在可见范围内；
        // orgId 与 create 同口径随部门重算（前端不传 orgId，不能作为唯一校验依据）。
        SysDept targetDept = user.getDeptId() != null ? deptMapper.selectById(user.getDeptId()) : null;
        Long effectiveOrgId = user.getDeptId() != null
                ? (targetDept != null && targetDept.getGroupId() != null ? user.getDeptId() : DEFAULT_ROOT_ORG_ID)
                : DEFAULT_ROOT_ORG_ID;
        assertTargetOrgVisible(user.getDeptId(), user.getOfficeId(),
                user.getDeptId() != null ? effectiveOrgId : user.getOrgId());

        existing.setRealName(user.getRealName());
        existing.setPhone(user.getPhone());
        existing.setEmail(user.getEmail());
        existing.setDeptId(user.getDeptId());
        existing.setOfficeId(user.getOfficeId());
        // 【租户可见性 2026-09-23】默认组织随部门重算(与 create 同口径):防调部门后集团归属失联。
        // 同值重算幂等无害;显式传 org_id 的旧语义不存在(前端表单从未提交该字段)
        existing.setOrgId(effectiveOrgId);
        // managerId 归一：null/0（前端"无上级"）→ null，配合 ALWAYS 策略落库回写 NULL
        existing.setManagerId(user.getManagerId() != null && user.getManagerId() > 0 ? user.getManagerId() : null);
        existing.setDataScopeOverride(StringUtils.hasText(user.getDataScopeOverride()) ? user.getDataScopeOverride() : null);
        existing.setStatus(user.getStatus());
        // 【2026-08-14 并发一致性】置 null 让 MyBatis-Plus 跳过 perm_version 列回写，
        // 避免"updateById 回写旧版本值覆盖并发 bump"的 lost-update（版本递增由下方 jdbc 独占）
        existing.setPermVersion(null);
        userMapper.updateById(existing);

        // 更新角色
        if (user.getRoleId() != null) {
            // 【2026-08-14 RBAC 收敛】单角色替换前保留该用户**当前已绑定**的激励载体角色(分享可见/全量查看)。
            // ⚠️ 只回插现有绑定：若回插全部 incentive_% 角色，未授权"全量查看"的用户会被任意一次编辑静默提权，
            // 且面板 revoke 的解绑会在下次编辑时复活（评审 P1·静默提权，2026-08-14 修复）
            java.util.List<Long> existingIncentiveRoleIds = jdbc.queryForList(
                    "SELECT r.id FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id " +
                            "WHERE ur.user_id = ? AND r.role_code LIKE 'incentive\\_%'", Long.class, existing.getId());
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(user.getRoleId());
            userRoleMapper.insert(ur);
            for (Long irId : existingIncentiveRoleIds) {
                jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?) ON DUPLICATE KEY UPDATE role_id=role_id",
                        existing.getId(), irId);
            }
        }
        // 【2026-08-08 用户级 custom 授权】custom 时保存个人授权组织（未传 orgIds 不清空，仅当显式传了才更新）。
        // 【修复 2026-08-08】必须用"新值"user.getDataScopeOverride() 判断——若旧值非 custom、本次改 custom，
        // 用 existing 旧值会跳过保存导致授权组织为空（毛颖娜实际案例）。
        if ("custom".equals(user.getDataScopeOverride()) && user.getOrgIds() != null) {
            saveUserOrgs(existing.getId(), user.getOrgIds());
        } else if (!"custom".equals(user.getDataScopeOverride())) {
            // 不再是 custom：清空个人授权组织，避免残留
            userOrgMapper.deleteByUserId(existing.getId());
        }
        // 【Redis 权限缓存】用户角色/数据范围/部门变更立即失效
        permissionCacheService.evict(existing.getId());
        // 【2026-08-14 缓存自愈】版本递增兜底（evict 漏网时缓存与 DB 版本比对自愈）
        bumpPermVersion(existing.getId());
    }

    public String resetPassword(Long userId) {
        SysUser user = userMapper.selectById(userId);
        securityAudit.userAdminEvent("user_reset_password", userId,
                user != null ? user.getUsername() : null);
        if (user == null) throw new BusinessException("用户不存在");
        // 【2026-08-15 安全审计 H1 纵深防御】非 admin 操作者禁止重置 admin 角色账号口令——
        // 临时口令明文随响应返回, 得手即接管全系统(不依赖 controller 的 canManageUser 守卫, 服务层独立兜底)
        if (!SecurityUtils.isAdmin() && hasAdminRole(userId)) {
            throw new BusinessException(403, "无权操作该用户");
        }
        // 【2026-08-14 安全 P0】随机临时口令 + 首登强制改密 + DB 级改密时刻(旧 token 立即失效)。
        // 截断到秒：避免 MySQL DATETIME(0) 四舍五入进位导致同秒 token 误判（与 changePassword 同因）
        String tempPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setPwdResetRequired(1);
        user.setPwdChangedAt(java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        userMapper.updateById(user);
        // 【修复 2026-08-10 P2-13】改密后旧 token 立即失效(含"记住我"7 天 token)；DB pwd_changed_at 兜底
        jwtTokenProvider.markPasswordChanged(userId);
        permissionCacheService.evict(userId);
        return tempPassword;
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        // 【工作流复核确认修复】后端补长度校验: 前端三入口口径分裂(顶栏≥6/首登与忘记密码≥8),
        // 后端此前零校验——顶栏可成功设置 6-7 位弱密码。统一 8-30(与首登改密/忘记密码同口径)
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 30) {
            throw new BusinessException("新密码长度须为 8-30 位");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // 【2026-08-14 安全 P0】DB 级改密时刻(旧 token 失效依据) + 清除首登强制改密标记。
        // 截断到秒：MySQL DATETIME(0) 对小数秒**四舍五入**，不截断会把改密时刻进位到下一秒，
        // 导致同秒签发的 token 被误判"早于改密"而 401（测试服实测复现）
        user.setPwdChangedAt(java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        user.setPwdResetRequired(0);
        userMapper.updateById(user);
        // 【修复 2026-08-10 P2-13】改密后旧 token 立即失效
        jwtTokenProvider.markPasswordChanged(userId);
    }

    public void updateStorageQuota(Long userId, Long quota) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStorageQuota(quota);
        userMapper.updateById(user);
    }

    // ===== 【D1=A 2026-10-18】存储空间申请审批 =====
    // 规则: 管理员始终可申请; 非管理员须 enabled=true 且 userId 在授权名单 JSON 数组中。
    // 只允许调高配额(首版), 单用户上限默认 100GB 且限制在 [1GB,1TB], 理由 1-200 字。

    /**
     * 存储空间申请面板信息(PC/MP 契约):
     * used/quota(当前账户)、maxQuota(生效上限)、canApply(资格)、reason(不可申请原因)、
     * enabled(开关原值, 管理端展示用)、allowDecrease(首版恒 false)。
     */
    public Map<String, Object> storageApplyInfo(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        boolean enabled = isStorageApplyEnabled();
        boolean canApply = canApplyStorageQuota(userId, enabled);
        String reason = null;
        if (!canApply) {
            reason = !enabled
                    ? "存储空间申请功能未开放，请联系系统管理员"
                    : "您不在存储空间申请授权名单中，请联系系统管理员";
        }
        long maxQuota = resolveStorageMaxQuota();
        long currentQuota = user.getStorageQuota() != null ? user.getStorageQuota() : 0L;
        // 【D1=A】已达上限时入口必须关闭；否则弹窗 min>max 无合法值，只能提交后报错。
        if (canApply && currentQuota >= maxQuota) {
            canApply = false;
            reason = "当前配额已达到系统上限，暂无法继续申请";
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("used", user.getStorageUsed() != null ? user.getStorageUsed() : 0L);
        info.put("quota", currentQuota);
        info.put("maxQuota", maxQuota);
        info.put("canApply", canApply);
        info.put("reason", reason);
        info.put("enabled", enabled);
        info.put("allowDecrease", false);
        return info;
    }

    /**
     * 提交侧配额校验(不锁行): 用户存在/未删/启用、申请资格、正整数 bytes、
     * 必须高于当前配额、不得超过生效上限。返回当前用户(含旧配额), 供审批单快照 oldQuota。
     */
    public SysUser validateStorageQuotaRequest(Long userId, Long newQuota) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已停用，无法申请存储空间");
        }
        boolean enabled = isStorageApplyEnabled();
        if (!canApplyStorageQuota(userId, enabled)) {
            throw new BusinessException(403, !enabled
                    ? "存储空间申请功能未开放，请联系系统管理员"
                    : "您不在存储空间申请授权名单中，无权申请存储空间");
        }
        if (newQuota == null || newQuota <= 0) {
            throw new BusinessException("申请配额必须是正整数(bytes)");
        }
        long current = user.getStorageQuota() != null ? user.getStorageQuota() : 0L;
        if (newQuota <= current) {
            throw new BusinessException("存储配额只能调高不能降低，申请配额须大于当前配额");
        }
        long max = resolveStorageMaxQuota();
        if (newQuota > max) {
            throw new BusinessException("申请配额超过系统上限(" + max + " bytes)");
        }
        return user;
    }

    /** 申请理由: trim 后长度须为 1-200, 返回规范化文本 */
    public String validateStorageApplyReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("申请理由不能为空");
        }
        if (normalized.length() > 200) {
            throw new BusinessException("申请理由长度不能超过200个字符");
        }
        return normalized;
    }

    /**
     * 审批通过执行配额变更(approve 事务内调用):
     * 申请人行 SELECT ... FOR UPDATE 锁内读取当前配额, 再次校验只升不降与上限;
     * 若并发调整导致 newQuota <= current, 抛业务异常随审批事务回滚。
     * 变更成功后写安全审计(userId/operator/oldQuota/newQuota/approvalId), 审计失败不阻断业务。
     */
    @Transactional
    public void applyStorageQuota(Long userId, Long newQuota, Long approvalId) {
        SysUser user = userId == null ? null : userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("申请人不存在或已删除，存储配额调整失败");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("申请人账号已停用，存储配额调整失败");
        }
        if (newQuota == null || newQuota <= 0) {
            throw new BusinessException("申请配额必须是正整数(bytes)");
        }
        long current = user.getStorageQuota() != null ? user.getStorageQuota() : 0L;
        if (newQuota <= current) {
            throw new BusinessException("存储配额调整失败：申请额度已不高于当前配额(可能已被其他审批调整)，请驳回后重新申请");
        }
        long max = resolveStorageMaxQuota();
        if (newQuota > max) {
            throw new BusinessException("存储配额调整失败：申请额度超过当前系统上限，请驳回后重新申请");
        }
        user.setStorageQuota(newQuota);
        userMapper.updateById(user);
        securityAudit.storageQuotaChanged(userId, current, newQuota, approvalId);
    }

    /** 生效上限: 配置缺失/非法回退 100GB, 并限制在 [1GB, 1TB] */
    public long resolveStorageMaxQuota() {
        long configured = DEFAULT_STORAGE_MAX_QUOTA;
        String raw = null;
        try {
            raw = configService.getValue(STORAGE_MAX_QUOTA_KEY);
        } catch (Exception ignored) {
            // 配置读取异常按非法处理, 回退默认上限
        }
        if (StringUtils.hasText(raw)) {
            try {
                configured = Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                configured = DEFAULT_STORAGE_MAX_QUOTA;
            }
        }
        if (configured <= 0) {
            configured = DEFAULT_STORAGE_MAX_QUOTA;
        }
        return Math.max(MIN_STORAGE_MAX_QUOTA, Math.min(MAX_STORAGE_MAX_QUOTA, configured));
    }

    private boolean isStorageApplyEnabled() {
        return configService.getBooleanValue(STORAGE_APPLY_ENABLED_KEY, true);
    }

    /** 资格判定: 管理员始终可申请(不受 enabled/名单限制); 非管理员需 enabled 且命中名单 */
    private boolean canApplyStorageQuota(Long userId, boolean enabled) {
        if (SecurityUtils.isAdmin()) return true;
        return enabled && isStorageApplyUser(userId);
    }

    /** 授权名单解析: JSON 数组, 兼容数字元素与数字字符串; 脏值/非数组按空名单 fail-closed */
    private boolean isStorageApplyUser(Long userId) {
        if (userId == null) return false;
        String raw = configService.getValue(STORAGE_APPLY_USERS_KEY);
        if (!StringUtils.hasText(raw)) return false;
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(raw.trim());
            if (node == null || !node.isArray()) return false;
            for (com.fasterxml.jackson.databind.JsonNode item : node) {
                if (item == null) continue;
                if (item.isIntegralNumber() && item.asLong() == userId) return true;
                if (item.isTextual()) {
                    try {
                        if (Long.parseLong(item.asText().trim()) == userId) return true;
                    } catch (NumberFormatException ignored) {
                        // 非数字元素忽略
                    }
                }
            }
        } catch (Exception ignored) {
            return false; // 配置脏值: 名单不可信, 按未授权处理
        }
        return false;
    }

    @Transactional
    public void deleteUser(Long userId) {
        // 【P2-2 2026-10-18】校验前置: 审计/草稿清理原在校验之前——被拒的删除也会落"已删除"审计
        // 并清空草稿(事务兜底只回滚 DB 写, audit 的 event() 非事务)。拒绝路径必须零副作用。
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        // 【2026-08-15 安全审计 H1 纵深防御】非 admin 操作者禁止删除 admin 角色账号(服务层兜底, 不依赖 controller 守卫)
        if (!SecurityUtils.isAdmin() && hasAdminRole(userId)) {
            throw new BusinessException(403, "无权操作该用户");
        }
        securityAudit.userAdminEvent("user_delete", userId, null);
        formDraftService.purgeByUser(userId);

        // 1. 将该用户名下所有客户释放到公海
        java.util.List<BizCustomer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getOwnerId, userId)
                        .ne(BizCustomer::getStatus, "sea")
                        .eq(BizCustomer::getDeleted, 0));
        for (BizCustomer c : customers) {
            // 【修复 2026-08-09 B4】客户所属办事处无启用公海池时不入池,跳过并继续(避免单客户中断整个离职事务)
            if (seaService.matchSeaConfig(c.getOfficeId()) == null) {
                // 【复核 Low】跳过入池也清理分享——无池客户的分享不再指向离职用户(FEAT-20260804 分享清理原则)
                shareMapper.delete(new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, c.getId()));
                continue;
            }
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

        // 3b. 【修复 2026-08-10 P2-08】软删除同样清理用户级孤儿关联(与 permanentDelete 一致)
        //     【2026-08-14 RBAC 收敛】biz_incentive_permission 已废弃(改角色承载, M1 后删表), 不再清理
        jdbc.update("DELETE FROM sys_user_position WHERE user_id=?", userId);
        jdbc.update("DELETE FROM sys_user_org WHERE user_id=?", userId);
        jdbc.update("DELETE FROM sys_org_admin WHERE user_id=?", userId);

        // 4. 软删除用户
        userMapper.deleteById(userId);
        // 【Redis 权限缓存】删除用户立即失效，避免软删用户在 TTL 内仍可访问
        permissionCacheService.evict(userId);
    }

    /** 目标用户是否持有 admin 角色(角色码 sys_role.role_code='admin', 经 sys_user_role 关联查询) */
    private boolean hasAdminRole(Long userId) {
        if (userId == null) return false;
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id " +
                        "WHERE ur.user_id = ? AND r.role_code = 'admin'", Integer.class, userId);
        return cnt != null && cnt > 0;
    }

}
