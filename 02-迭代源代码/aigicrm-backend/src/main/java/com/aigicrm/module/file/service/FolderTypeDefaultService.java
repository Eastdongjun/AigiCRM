package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【2026-11-09 文件权限 v2】文件空间类型授权：谁(角色|人) × 哪个空间(公共/私人) × 哪些动作。
 * - 管理员在权限中心「文件权限」页签按 USER(点名某人)/ROLE(整批) 配置, 保存即实时生效;
 * - 判定入口在 {@link FolderPermissionService}(ACL 之后、公共兜底之前的加授层),
 *   覆盖该空间全部存量+未来文件夹——v1 的建夹物化机制已退役(取消勾选立即收回);
 * - 根级公共建夹门槛：admin ∨ {@link #canCreateRoot("public")}(授权含 create)。
 * 词表 = 8 细动作(view,upload,rename,replace,delete,share,download,create);
 * 存量 'edit' 伞写入时归一为 rename+replace, 判定层另做读时展开兼容(FolderPermissionService.csvHas)。
 */
@Service
@RequiredArgsConstructor
public class FolderTypeDefaultService {

    /** create 仅存在于类型授权层(可建该类型根文件夹)，不进逐夹 ACL */
    public static final String P_CREATE = "create";
    public static final String SUBJECT_ROLE = "ROLE";
    public static final String SUBJECT_USER = "USER";
    private static final Set<String> ALLOWED = Set.of(
            FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
            FolderPermissionService.P_RENAME, FolderPermissionService.P_REPLACE,
            FolderPermissionService.P_DELETE, FolderPermissionService.P_SHARE,
            FolderPermissionService.P_DOWNLOAD, P_CREATE);
    /** v1 旧调用方/旧 UI 的 'edit' 伞: 校验放行, 落库前归一为 rename+replace */
    private static final Set<String> ALLOWED_INPUT = new java.util.HashSet<>(ALLOWED);

    static {
        ALLOWED_INPUT.add(FolderPermissionService.P_EDIT);
    }
    private static final Set<String> TYPES = Set.of("public", "private");

    private final BizFolderTypeDefaultMapper defaultMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    // 【体检P4 2026-11-09】类型授权写口=权限等价写操作, 补操作人审计
    private final com.aigicrm.security.SecurityAuditService securityAudit;

    /** 权限中心列表：全量授权行(含主体名), 按类型→主体排序 */
    public List<Map<String, Object>> listDefaults() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizFolderTypeDefault d : defaultMapper.selectList(
                new LambdaQueryWrapper<BizFolderTypeDefault>()
                        .orderByAsc(BizFolderTypeDefault::getFolderType)
                        .orderByAsc(BizFolderTypeDefault::getSubjectType)
                        .orderByAsc(BizFolderTypeDefault::getSubjectId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("folderType", d.getFolderType());
            row.put("subjectType", d.getSubjectType());
            row.put("subjectId", d.getSubjectId());
            row.put("subjectName", subjectNameOf(d));
            row.put("perms", List.of(d.getPerms().split(",")));
            rows.add(row);
        }
        return rows;
    }

    /** 保存(管理端入口已由 controller 的 file:acl:manage 码拦截)。'edit' 伞归一为 rename+replace 后落库 */
    public void saveDefault(String folderType, String subjectType, Long subjectId, List<String> perms) {
        if (!TYPES.contains(folderType)) throw new BusinessException("不支持的文件夹类型");
        if (subjectId == null) throw new BusinessException("授权主体不能为空");
        if (perms == null || perms.isEmpty()) throw new BusinessException("权限不能为空");
        for (String p : perms) if (!ALLOWED_INPUT.contains(p)) throw new BusinessException("非法权限项: " + p);
        if (!SUBJECT_ROLE.equals(subjectType) && !SUBJECT_USER.equals(subjectType)) {
            throw new BusinessException("授权主体类型必须是 ROLE 或 USER");
        }
        if (SUBJECT_ROLE.equals(subjectType)) {
            SysRole role = roleMapper.selectById(subjectId);
            if (role == null || role.getStatus() == null || role.getStatus() != 1) {
                throw new BusinessException("角色不存在或已停用");
            }
        } else {
            // 主体为具体人: 用户须存在(离职/停用过滤由选人下拉负责, 与权限缓存 evict 卫生约定互补)
            if (userMapper.selectById(subjectId) == null) throw new BusinessException("用户不存在");
        }
        // 词表归一: 'edit'(v1 旧值/旧调用方) → rename+replace, 数据面统一收敛到细词
        Set<String> normalized = new LinkedHashSet<>();
        for (String p : perms) {
            if (FolderPermissionService.P_EDIT.equals(p)) {
                normalized.add(FolderPermissionService.P_RENAME);
                normalized.add(FolderPermissionService.P_REPLACE);
            } else {
                normalized.add(p);
            }
        }
        String joined = String.join(",", normalized);
        BizFolderTypeDefault existing = defaultMapper.selectOne(
                new LambdaQueryWrapper<BizFolderTypeDefault>()
                        .eq(BizFolderTypeDefault::getFolderType, folderType)
                        .eq(BizFolderTypeDefault::getSubjectType, subjectType)
                        .eq(BizFolderTypeDefault::getSubjectId, subjectId));
        if (existing != null) {
            existing.setPerms(joined);
            defaultMapper.updateById(existing);
        } else {
            BizFolderTypeDefault d = new BizFolderTypeDefault();
            d.setFolderType(folderType);
            d.setSubjectType(subjectType);
            d.setSubjectId(subjectId);
            d.setPerms(joined);
            d.setCreatedBy(SecurityUtils.getCurrentUserId());
            d.setCreatedAt(LocalDateTime.now());
            defaultMapper.insert(d);
        }
        // 【体检P4】类型授权保存留痕(失败不阻断)
        try { securityAudit.permissionChange("folder_type_default_save",
                "folderTypeDefault:" + folderType + " subject:" + subjectType + ":" + subjectId + " perms:" + joined); } catch (Exception ignore) { }
    }

    public void removeDefault(String folderType, String subjectType, Long subjectId) {
        if (!TYPES.contains(folderType)) throw new BusinessException("不支持的文件夹类型");
        defaultMapper.delete(new LambdaQueryWrapper<BizFolderTypeDefault>()
                .eq(BizFolderTypeDefault::getFolderType, folderType)
                .eq(BizFolderTypeDefault::getSubjectType, subjectType)
                .eq(BizFolderTypeDefault::getSubjectId, subjectId));
        // 【体检P4】类型授权删除留痕(失败不阻断)
        try { securityAudit.permissionChange("folder_type_default_remove",
                "folderTypeDefault:" + folderType + " subject:" + subjectType + ":" + subjectId); } catch (Exception ignore) { }
    }

    /** 当前登录用户可否创建该类型根文件夹（admin 由调用方前置放行）。主体匹配口径与判定层 subjectMatches 同源 */
    public boolean canCreateRoot(String folderType) {
        for (BizFolderTypeDefault d : rowsOfType(folderType)) {
            if (subjectMatchesCurrent(d) && permsContain(d.getPerms(), P_CREATE)) return true;
        }
        return false;
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

    /** 主体匹配当前登录人: USER 行对 userId; ROLE 行解析 roleCode 比对(令牌只带 roleCodes) */
    private boolean subjectMatchesCurrent(BizFolderTypeDefault d) {
        if (SUBJECT_USER.equals(d.getSubjectType())) {
            Long uid = SecurityUtils.getCurrentUserId();
            return uid != null && uid.equals(d.getSubjectId());
        }
        SysRole role = roleMapper.selectById(d.getSubjectId());
        if (role == null || role.getRoleCode() == null) return false;
        return SecurityUtils.getCurrentRoleCodes().contains(role.getRoleCode());
    }

    private String subjectNameOf(BizFolderTypeDefault d) {
        if (SUBJECT_USER.equals(d.getSubjectType())) {
            SysUser u = userMapper.selectById(d.getSubjectId());
            return u != null ? u.getRealName() + "（" + u.getUsername() + "）" : "未知用户";
        }
        SysRole role = roleMapper.selectById(d.getSubjectId());
        return role != null ? role.getRoleName() : "未知角色";
    }
}
