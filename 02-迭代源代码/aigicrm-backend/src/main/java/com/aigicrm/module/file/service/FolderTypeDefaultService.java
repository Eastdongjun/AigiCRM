package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFolderAcl;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【2026-11-09 类型级默认权限】文件空间(公共/个人)默认权限：类型级默认 + 逐夹覆盖。
 * - 管理员在权限中心按 角色×空间类型×动作 配置默认(含 create=可建该类型根夹)；
 * - 新建文件夹时 {@link #materializeAcl} 把默认(除 create)物化进 biz_folder_acl，可逐夹微调；
 * - 根级公共建夹门槛：admin ∨ {@link #canCreateRoot("public")}。
 * 词表与 biz_folder_acl 同源(短名) + create，写入校验仿 FolderAclService.saveAcl。
 */
@Service
@RequiredArgsConstructor
public class FolderTypeDefaultService {

    /** create 仅存在于类型默认层(可建该类型根文件夹)，不进逐夹 ACL */
    public static final String P_CREATE = "create";
    private static final Set<String> ALLOWED = Set.of(
            FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
            FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
            FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD,
            P_CREATE);
    private static final Set<String> TYPES = Set.of("public", "private");

    private final BizFolderTypeDefaultMapper defaultMapper;
    private final BizFolderAclMapper aclMapper;
    private final SysRoleMapper roleMapper;

    /** 权限中心列表：全量默认行(含角色名)，按类型→角色排序 */
    public List<Map<String, Object>> listDefaults() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizFolderTypeDefault d : defaultMapper.selectList(
                new LambdaQueryWrapper<BizFolderTypeDefault>()
                        .orderByAsc(BizFolderTypeDefault::getFolderType)
                        .orderByAsc(BizFolderTypeDefault::getRoleId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("folderType", d.getFolderType());
            row.put("roleId", d.getRoleId());
            SysRole role = roleMapper.selectById(d.getRoleId());
            row.put("roleName", role != null ? role.getRoleName() : "未知角色");
            row.put("perms", List.of(d.getPerms().split(",")));
            rows.add(row);
        }
        return rows;
    }

    /** 保存(管理端入口已由 controller 的 file:acl:manage 码拦截) */
    public void saveDefault(String folderType, Long roleId, List<String> perms) {
        if (!TYPES.contains(folderType)) throw new BusinessException("不支持的文件夹类型");
        if (roleId == null) throw new BusinessException("角色不能为空");
        if (perms == null || perms.isEmpty()) throw new BusinessException("权限不能为空");
        for (String p : perms) if (!ALLOWED.contains(p)) throw new BusinessException("非法权限项: " + p);
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || role.getStatus() == null || role.getStatus() != 1) {
            throw new BusinessException("角色不存在或已停用");
        }
        String joined = String.join(",", perms);
        BizFolderTypeDefault existing = defaultMapper.selectOne(
                new LambdaQueryWrapper<BizFolderTypeDefault>()
                        .eq(BizFolderTypeDefault::getFolderType, folderType)
                        .eq(BizFolderTypeDefault::getRoleId, roleId));
        if (existing != null) {
            existing.setPerms(joined);
            defaultMapper.updateById(existing);
        } else {
            BizFolderTypeDefault d = new BizFolderTypeDefault();
            d.setFolderType(folderType);
            d.setRoleId(roleId);
            d.setPerms(joined);
            d.setCreatedBy(SecurityUtils.getCurrentUserId());
            d.setCreatedAt(LocalDateTime.now());
            defaultMapper.insert(d);
        }
    }

    public void removeDefault(String folderType, Long roleId) {
        if (!TYPES.contains(folderType)) throw new BusinessException("不支持的文件夹类型");
        defaultMapper.delete(new LambdaQueryWrapper<BizFolderTypeDefault>()
                .eq(BizFolderTypeDefault::getFolderType, folderType)
                .eq(BizFolderTypeDefault::getRoleId, roleId));
    }

    /** 当前登录用户可否创建该类型根文件夹（admin 由调用方前置放行） */
    public boolean canCreateRoot(String folderType) {
        for (BizFolderTypeDefault d : rowsOfType(folderType)) {
            if (userHasRole(d.getRoleId()) && permsContain(d.getPerms(), P_CREATE)) return true;
        }
        return false;
    }

    /** 新建文件夹继承类型默认：除 create 外逐行物化进 biz_folder_acl(幂等: 新夹无既有行) */
    @Transactional
    public void materializeAcl(Long folderId, String folderType) {
        for (BizFolderTypeDefault d : rowsOfType(folderType)) {
            List<String> perms = new ArrayList<>(List.of(d.getPerms().split(",")));
            perms.remove(P_CREATE);
            perms.removeIf(String::isBlank);
            if (perms.isEmpty()) continue;
            BizFolderAcl acl = new BizFolderAcl();
            acl.setFolderId(folderId);
            acl.setRoleId(d.getRoleId());
            acl.setPerms(String.join(",", perms));
            acl.setCreatedBy(SecurityUtils.getCurrentUserId());
            acl.setCreatedAt(LocalDateTime.now());
            aclMapper.insert(acl);
        }
    }

    private List<BizFolderTypeDefault> rowsOfType(String folderType) {
        if (folderType == null) return List.of();
        return defaultMapper.selectList(new LambdaQueryWrapper<BizFolderTypeDefault>()
                .eq(BizFolderTypeDefault::getFolderType, folderType));
    }

    private boolean permsContain(String csv, String token) {
        for (String p : csv.split(",")) if (p.equals(token)) return true;
        return false;
    }

    /** 角色匹配口径与 FolderPermissionService.userHasRole 同源(ACL 存 roleId, 令牌只带 roleCodes) */
    private boolean userHasRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || role.getRoleCode() == null) return false;
        return SecurityUtils.getCurrentRoleCodes().contains(role.getRoleCode());
    }
}
