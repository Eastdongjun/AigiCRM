package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.enums.DataScopeType;
import com.aigicrm.common.scope.GroupScopeUtil;
import com.aigicrm.module.system.entity.SysMenu;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysRoleMenu;
import com.aigicrm.module.system.entity.SysRoleOrg;
import com.aigicrm.module.system.entity.SysUserRole;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysMenuMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysRoleMenuMapper;
import com.aigicrm.module.system.mapper.SysRoleOrgMapper;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleOrgMapper roleOrgMapper;
    private final SysDeptMapper deptMapper;
    private final PermissionCacheService permissionCacheService;
    private final RolePermScopeService rolePermScopeService;
    private final com.aigicrm.module.system.mapper.SysUserRoleMapper userRoleMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * 角色列表（按集团边界过滤）。
     * 【修复 角色列表集团边界】非 admin 仅见本集团角色;admin 全量。
     * 口径见 {@link GroupScopeUtil}（与人员列表 applyGroupScope 一致）。
     */
    public List<SysRole> list() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortOrder);
        List<Long> groupIds = GroupScopeUtil.visibleGroupIds();
        if (groupIds != null) {
            wrapper.in(SysRole::getGroupId, groupIds);
        }
        return roleMapper.selectList(wrapper);
    }

    /** 角色详情（含菜单权限与 custom 数据范围的组织集合；非 admin 越权集团返回 null，防 id 枚举） */
    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
            // 【修复 角色详情集团边界】与列表同口径：非 admin 无权读取其他集团角色
            if (!GroupScopeUtil.canAccessGroup(role.getGroupId())) {
                return null;
            }
            role.setMenuIds(menuMapper.selectMenuIdsByRoleId(id));
            // 如果是 custom 类型，加载组织关联
            if (DataScopeType.CUSTOM.getCode().equals(role.getDataScope())) {
                role.setOrgIds(roleOrgMapper.selectByRoleId(id).stream()
                        .map(SysRoleOrg::getOrgId)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
        return role;
    }

    /**
     * 【安全 2026-08-08 提权防护】非超管创建/修改角色时：
     * 1) 不可创建/篡改 admin 角色；2) dataScope 不得宽于调用者自身；3) 授予的菜单权限必须是自身权限子集。
     * @param existing 更新场景的目标角色（create 传 null）
     */
    private void validateGrantableRole(SysRole role, SysRole existing) {
        if (SecurityUtils.isAdmin()) return;
        if ("admin".equals(role.getRoleCode())
                || (existing != null && "admin".equals(existing.getRoleCode()))) {
            throw new BusinessException("无权操作系统管理员角色");
        }
        if (role.getDataScope() != null) {
            DataScopeType caller = DataScopeType.fromCode(SecurityUtils.getCurrentDataScope());
            if (DataScopeType.fromCode(role.getDataScope()).rank() < caller.rank()) {
                throw new BusinessException("无权授予超出自身数据范围的角色");
            }
        }
        if (role.getMenuIds() != null && !role.getMenuIds().isEmpty()) {
            Set<String> callerPerms = SecurityUtils.getCurrentUser() != null
                    ? SecurityUtils.getCurrentUser().getPermissions() : Set.of();
            for (SysMenu m : menuMapper.selectByIds(role.getMenuIds())) {
                if (m.getPermission() != null && !m.getPermission().isBlank()
                        && !callerPerms.contains(m.getPermission())) {
                    throw new BusinessException("无权授予权限: " + m.getPermission());
                }
            }
        }
    }

    /** 新增角色（编码唯一校验 + 菜单/组织关联保存；集团归属兜底，越权集团拒绝） */
    @Transactional
    public void create(SysRole role) {
        validateGrantableRole(role, null);
        // 【修复 角色创建集团边界】未传 groupId 时显式兜底：非 admin=当前用户集团，admin=主集团 1；
        // 传了但越权（非 admin 建到其他集团）→ 拒绝
        if (role.getGroupId() == null) {
            List<Long> g = GroupScopeUtil.visibleGroupIds();
            role.setGroupId(g == null ? GroupScopeUtil.DEFAULT_GROUP_ID : g.get(0));
        } else if (!GroupScopeUtil.canAccessGroup(role.getGroupId())) {
            throw new BusinessException("无权在该集团创建角色");
        }
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.getRoleCode())
        );
        if (count > 0) throw new BusinessException("角色编码已存在");
        roleMapper.insert(role);
        saveRoleMenus(role.getId(), role.getMenuIds());
        saveRoleOrgs(role);
        // 【Redis 权限缓存】新角色已绑定用户（前端可能同时授用户），立即失效该角色下用户缓存
        permissionCacheService.evictByRole(role.getId());
    }

    /** 更新角色（有实际字段才更新；菜单/dataScope 变更时同步重建关联；越权集团拒绝） */
    @Transactional
    public void update(SysRole role) {
        // 【收口 2026-08-12】未知 roleId 直接拒绝——对齐 updateOrgs, 收口孤儿 role_org 写
        // (此前 existing==null 时守卫跳过, saveRoleOrgs 仍会写 role_org(role_id=不存在))
        if (role.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        SysRole existing = roleMapper.selectById(role.getId());
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        // 【修复 角色更新集团边界】目标角色越权不可操作；新 groupId 越权不可归属
        if (!GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
            throw new BusinessException("无权操作其他集团的角色");
        }
        validateGrantableRole(role, existing);
        if (role.getGroupId() != null && !GroupScopeUtil.canAccessGroup(role.getGroupId())) {
            throw new BusinessException("无权将角色归属到其他集团");
        }
        // 仅当有实际字段需要更新时才执行 updateById
        boolean hasRoleFields = role.getRoleName() != null || role.getRoleCode() != null
                || role.getDescription() != null || role.getDataScope() != null
                || role.getSortOrder() != null || role.getStatus() != null
                || role.getGroupId() != null;
        if (hasRoleFields) {
            // 【修复 2026-08-10 P2-09】role_code 改名时同步迁移 role_perm_scope 范围配置,
            // 否则旧 code 配置成孤儿、新 code 无配置回落默认(edit/delete 意外收紧为 self)
            if (role.getRoleCode() != null && existing != null && existing.getRoleCode() != null
                    && !existing.getRoleCode().equals(role.getRoleCode())) {
                rolePermScopeService.renameRoleCode(existing.getRoleCode(), role.getRoleCode());
            }
            roleMapper.updateById(role);
        }
        if (role.getMenuIds() != null) {
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()));
            saveRoleMenus(role.getId(), role.getMenuIds());
        }
        // 如果传了 dataScope，同步更新组织关联
        if (role.getDataScope() != null) {
            saveRoleOrgs(role);
        }
        // 【Redis 权限缓存】角色菜单/dataScope 变更影响该角色下所有用户，立即失效
        permissionCacheService.evictByRole(role.getId());
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpUserPermVersionsByRole(role.getId());
    }

    /**
     * 更新角色-组织数据范围关联
     *
     * 【需求】角色管理页面维护 custom 数据范围的组织集合
     * 【方法】清空后按 orgIds 重建关联（saveRoleOrgs 幂等）
     * 【业务规则】仅 custom 数据范围生效；orgIds 为空则清空
     * 【参数】roleId 角色ID；orgIds 选中组织ID集合
     * 【注意】调用方需有 system:role:edit 权限
     */
    @Transactional
    public void updateOrgs(Long roleId, List<Long> orgIds) {
        // 【修复 2026-08-11 P2 集团边界】对齐 update/delete/create: 目标角色越权不可操作。
        // 此前缺此校验, 持 system:role:edit 的非超管可对任意集团角色写 role_org(扩大 custom 数据范围);
        // 且 sys_role_org 不在租户白名单, 跨集团写入会真实生效。
        SysRole existing = roleMapper.selectById(roleId);
        // 【收口 2026-08-12】未知 roleId 直接拒绝——此前 existing==null 跳过校验仍写孤儿 role_org
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        if (!GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
            throw new BusinessException("无权操作其他集团的角色");
        }
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setDataScope(DataScopeType.CUSTOM.getCode());
        role.setOrgIds(orgIds);
        saveRoleOrgs(role);
        // 【Redis 权限缓存】custom 数据范围组织集变更立即失效
        permissionCacheService.evictByRole(roleId);
        // 【2026-08-14 缓存自愈】版本递增兜底
        bumpUserPermVersionsByRole(roleId);
    }

    /** 删除角色（连带清理菜单/组织/范围配置/用户绑定；越权集团拒绝） */
    @Transactional
    public void delete(Long id) {
        // 【修复 角色删除集团边界】非 admin 无权删除其他集团角色
        SysRole existing = roleMapper.selectById(id);
        if (existing == null) {
            // 【2026-08-13 收口】删除不存在的角色不再静默成功（与 update/updateOrgs 存在性校验对齐）
            throw new BusinessException("角色不存在");
        }
        if (!GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
            throw new BusinessException("无权删除其他集团的角色");
        }
        // 【修复 2026-08-14 缓存审计 G2(P0)】先捕获受影响用户并递增版本，再删绑定——
        // evictByRole 反查依赖 sys_user_role，原"先删绑定后反查"导致反查为空、evict 静默失效、旧权限残留至 TTL
        java.util.List<Long> affectedUserIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id))
                .stream().map(SysUserRole::getUserId).distinct().toList();
        bumpUserPermVersionsByRole(id);
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleOrgMapper.deleteByRoleId(id);
        // 【修复 2026-08-10 P2-06/P2-08】删除角色连带清理:
        // 1. role_perm_scope 范围配置(否则同 role_code 重建角色时旧范围复活)
        // 2. sys_user_role 用户绑定(无外键残留, 返聘/重建 ID 场景埋雷)
        if (existing.getRoleCode() != null) {
            rolePermScopeService.deleteRoleScopes(existing.getRoleCode());
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        // 【修复 2026-08-10 P3-06】角色删除连带清理 biz_folder_acl(角色维度授权, 防 ID 复用权限复活)
        jdbc.update("DELETE FROM biz_folder_acl WHERE role_id=?", id);
        // 【Redis 权限缓存】删除角色立即失效其用户缓存（用删除前捕获的用户列表，反查已不可用）
        for (Long uid : affectedUserIds) {
            permissionCacheService.evict(uid);
        }
    }

    /** 【2026-08-14 缓存自愈】角色级权限版本递增：evict 漏网/Redis 写失败时，缓存与 DB 版本比对自愈 */
    private void bumpUserPermVersionsByRole(Long roleId) {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 " +
                "WHERE id IN (SELECT user_id FROM sys_user_role WHERE role_id=?)", roleId);
    }

    /**
     * 保存角色-菜单绑定(含祖先闭包)。
     *
     * 【修复 2026-08-10 P1-10】祖先闭包: 勾选子菜单未勾父分组时, 权限生效但菜单不显示(孤儿菜单)。
     * 保存时补入每个选中节点的目录行锚点(menu_type=1, permission 为空, 不产生 RBAC),
     * 保证侧边栏目录可见; 但**不**静默补入带真实权限码的 menu_type=2 父菜单——
     * 父菜单须由用户显式勾选(权限分配弹窗以 check-strictly 树展示, 层级可见)。
     *
     * 【安全 2026-08-11 P0/P1 修复】
     * 1) 前端 role.vue 已加 check-strictly(回显精确, 杜绝"打开即全选、保存即全量授权");
     * 2) 【P0 2026-08-12 对齐审计修复方向】闭包只补 menu_type=1 目录行(permission 空, 无 RBAC),
     *    不再补带真实权限码的 menu_type=2 父菜单——此前闭包注入父菜单权限码, 非超管构造
     *    "只含子节点"请求会经闭包静默获得父权限(提权旁路), 靠下方 expanded 重校验兜底;
     *    改为不注入后, 该旁路从构造上消除, 下方重校验降级为纵深防御(现仅校验提交集)。
     */
    private void saveRoleMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) return;
        java.util.Set<Long> expanded = new java.util.LinkedHashSet<>(menuIds);
        for (Long menuId : menuIds) {
            Long cur = menuId;
            int guard = 0;
            while (cur != null && guard++ < 20) { // 环保护
                com.aigicrm.module.system.entity.SysMenu m = menuMapper.selectById(cur);
                if (m == null || m.getParentId() == null || m.getParentId() == 0L) break;
                Long parentId = m.getParentId();
                com.aigicrm.module.system.entity.SysMenu parent = menuMapper.selectById(parentId);
                // 【P0 2026-08-12】只补 menu_type=1 目录行锚点(permission 空, 无 RBAC);
                // menu_type=2/3 父菜单是真实权限载体, 不静默注入, 须用户显式勾选
                if (parent != null && parent.getMenuType() != null && parent.getMenuType() == 1) {
                    if (!expanded.add(parentId)) break; // 目录锚点已含则父链已完整
                }
                cur = parentId;
            }
        }
        // 【安全 2026-08-11】闭包后的集合也要过权限子集校验(非超管)——纵深防御:
        // 现闭包仅注入 permission 空的目录行(type=1), 本循环主要兜底提交集本身,
        // 防止任何未来变更重新引入带权限码的静默注入。
        if (!SecurityUtils.isAdmin()) {
            Set<String> callerPerms = SecurityUtils.getCurrentUser() != null
                    ? SecurityUtils.getCurrentUser().getPermissions() : Set.of();
            for (Long menuId : expanded) {
                com.aigicrm.module.system.entity.SysMenu m = menuMapper.selectById(menuId);
                if (m != null && m.getPermission() != null && !m.getPermission().isBlank()
                        && !callerPerms.contains(m.getPermission())) {
                    throw new BusinessException("无权授予权限: " + m.getPermission());
                }
            }
        }
        for (Long menuId : expanded) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    /**
     * 保存角色-组织关联（custom 类型时生效）
     * 非 custom 类型时清空所有关联
     */
    private void saveRoleOrgs(SysRole role) {
        // 先清空
        roleOrgMapper.deleteByRoleId(role.getId());

        // 非 custom 类型不保存组织关联
        if (!DataScopeType.CUSTOM.getCode().equals(role.getDataScope())) {
            return;
        }

        // custom 类型：保存选中的组织
        List<Long> orgIds = role.getOrgIds();
        if (orgIds == null || orgIds.isEmpty()) return;

        // 【修复 2026-08-11 P2(c) 组织集团归属校验】orgIds 必须都在当前用户可操作集团内。
        // 此前零校验: 同集团非超管可把其他集团 orgId 挂到本集团 custom 角色 role_org;
        // sys_dept 不在租户白名单 + OrgScopeResolver.collectSubtree 装载子树 → 跨集团数据范围扩大真实生效。
        // create/update/updateOrgs 三入口共享此方法, 此处为唯一收口。
        // 目标角色实际集团: create 已设 groupId; update/updateOrgs 未传时回查库
        Long roleGroup = role.getGroupId();
        if (roleGroup == null && role.getId() != null) {
            SysRole existingRole = roleMapper.selectById(role.getId());
            if (existingRole != null) roleGroup = existingRole.getGroupId();
        }
        validateRoleOrgsGroup(orgIds, roleGroup);

        for (Long orgId : orgIds) {
            SysRoleOrg ro = new SysRoleOrg();
            ro.setRoleId(role.getId());
            ro.setOrgId(orgId);
            ro.setIncludeChildren(1); // 默认含下级，前端可传具体值
            roleOrgMapper.insert(ro);
        }
    }

    /** org→集团归属校验: admin 全集团放行; 非 admin 要求每个 org 有效集团==角色集团且子树不跨集团(fail-closed) */
    private void validateRoleOrgsGroup(List<Long> orgIds, Long roleGroup) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        for (Long orgId : orgIds) {
            // P2(c)-r3: null orgId 直接拒收, 避免写 role_org(org_id=NULL) 脏行
            if (orgId == null) {
                throw new BusinessException("数据范围包含空组织(orgId=null)");
            }
            // 存在性: 查不到(已删除/伪造) → 拒绝
            if (deptMapper.selectById(orgId) == null) {
                throw new BusinessException("无权将不存在的组织纳入数据范围(orgId=" + orgId + ")");
            }
            // 有效集团 = 自身或最近祖先的 group_id(历史未回填多为 NULL); 全空退主集团 1(GroupScopeUtil 同口径)
            Long effective = deptMapper.selectEffectiveGroupId(orgId);
            if (effective == null) effective = GroupScopeUtil.DEFAULT_GROUP_ID;
            // P2(c)-r1: org 有效集团必须与目标角色集团一致(纵深防御)——可访问多集团的高权用户
            // 不得把其他集团 org 挂到本集团角色; 角色集团未知(roleGroup=null)时退回调用者可访问集团校验
            if (roleGroup != null && !effective.equals(roleGroup)) {
                throw new BusinessException("无权将其他集团的组织纳入数据范围(orgId=" + orgId + ")");
            }
            if (!GroupScopeUtil.canAccessGroup(effective)) {
                throw new BusinessException("无权将其他集团的组织纳入数据范围(orgId=" + orgId + ")");
            }
            // include_children=1 固定展开整棵子树: 子树内任何显式归属其他集团的组织 → 拒绝
            // (P2(c) 子树旁路: 锚点 org 同集团但子树含跨集团节点仍会扩大数据范围)
            for (Long g : deptMapper.selectDescendantGroupIds(orgId)) {
                if (g != null && !g.equals(effective)) {
                    throw new BusinessException("无权将包含其他集团组织的子树纳入数据范围(orgId=" + orgId + ")");
                }
            }
        }
    }
}
