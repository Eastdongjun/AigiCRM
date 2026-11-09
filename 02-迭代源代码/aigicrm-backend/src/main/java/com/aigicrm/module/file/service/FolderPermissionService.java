package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderAcl;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderPermissionService {

    public static final String P_VIEW = "view";
    public static final String P_UPLOAD = "upload";
    /** 伞词(存量 ACL/类型行遗留): 判定时按 rename OR replace 展开; 新类型授权写入已归一到细词 */
    public static final String P_EDIT = "edit";
    public static final String P_DELETE = "delete";
    public static final String P_SHARE = "share";
    public static final String P_DOWNLOAD = "download";
    /** 【2026-11-09 v2 动作细分】改名/替换从 edit 拆出两个独立动作(用户拍板) */
    public static final String P_RENAME = "rename";
    public static final String P_REPLACE = "replace";
    private static final List<String> ALL_PERMS =
            List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD, P_RENAME, P_REPLACE);
    private static final int MAX_HOP = 5;

    private final BizFolderAclMapper aclMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    /** 【文件权限 v2】类型授权层(角色|人 × 空间 × 动作, 实时生效) */
    private final BizFolderTypeDefaultMapper typeDefaultMapper;

    /**
     * 文件夹权限判定：管理员 > 本人 > 直属上级 > 逐夹ACL > 类型授权(实时) > 公共仅查看/下载。
     * 【2026-11-09 v2】ACL 与类型授权均为"只加不减"的加授层(并集语义)。
     */
    public boolean check(Long folderId, String action, Long userId) {
        if (folderId == null) return false;
        if (SecurityUtils.isAdmin()) return true;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return false;
        if (Objects.equals(folder.getOwnerId(), userId)) return true;
        if (isManagerChain(userId, folder.getOwnerId())) return true;
        if (aclGranted(folderId, userId, action)) return true;
        // 【文件权限 v2】类型授权实时生效: 覆盖该空间全部存量+未来文件夹(勾了即生效, 取消即收回)
        if (folder.getFolderType() != null && typeGranted(folder.getFolderType(), userId, action)) return true;
        // 【文件权限体系】公共文件夹兜底扩展至 download：全员可见，下载开关允许时全员可下载
        // （开关在 downloadById 控制，管理员全权不受开关约束）
        return (P_VIEW.equals(action) || P_DOWNLOAD.equals(action)) && "public".equals(folder.getFolderType());
    }

    /**
     * 文件权限判定：【2026-11-10 用户令"A"】上传者本人恒可管理自己上传的文件(改名/替换/删除/分享/下载)，
     * 无论躺在哪个文件夹——此前"夹权优先于上传人"致公共夹里自己传的文件自己改不了名。
     * 上级链不随本人短路扩展(主管改下属文件仍走夹子判定)；激励佐证禁替换由 FileService 决策5 守卫另行拦截。
     */
    public boolean checkFile(Long fileId, String action, Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return false;
        if (Objects.equals(file.getUploaderId(), userId)) return true;
        if (file.getFolderId() != null) return check(file.getFolderId(), action, userId);
        return isManagerChain(userId, file.getUploaderId());
    }

    /** 个人上级(manager_id)优先，部门 leader 链兜底（仅当 lower 的 manager_id 为空），各限 5 层（设计 §6） */
    public boolean isManagerChain(Long upperId, Long lowerId) {
        if (upperId == null || lowerId == null) return false;
        SysUser lower = userMapper.selectById(lowerId);
        if (lower == null) return false;
        SysUser cur = lower;
        for (int i = 0; i < MAX_HOP && cur != null; i++) {
            if (cur.getManagerId() != null) {
                if (cur.getManagerId().equals(upperId)) return true;
                cur = userMapper.selectById(cur.getManagerId());
            } else break;
        }
        // 部门 leader 链兜底：仅当 lower 的 manager_id 为空（设计 §6：个人上级优先，未设置才走部门链）
        if (lower.getManagerId() == null && lower.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(lower.getDeptId());
            for (int i = 0; i < MAX_HOP && dept != null; i++) {
                if (dept.getLeaderId() != null && dept.getLeaderId().equals(upperId)) return true;
                if (dept.getParentId() == null || dept.getParentId() == 0) break;
                dept = deptMapper.selectById(dept.getParentId());
            }
        }
        return false;
    }

    /** 当前用户对文件夹的权限列表（前端按钮控制） */
    public List<String> permsFor(Long folderId, Long userId) {
        if (folderId == null) return List.of();
        if (SecurityUtils.isAdmin()) return ALL_PERMS;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return List.of();
        if (Objects.equals(folder.getOwnerId(), userId) || isManagerChain(userId, folder.getOwnerId())) {
            return ALL_PERMS;
        }
        List<String> result = new ArrayList<>();
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                mergePerms(result, acl.getPerms());
            }
        }
        // 【文件权限 v2】类型授权层实时并入(角色|人 × 空间)
        if (folder.getFolderType() != null) {
            for (BizFolderTypeDefault d : typeDefaultMapper.selectList(new LambdaQueryWrapper<BizFolderTypeDefault>()
                    .eq(BizFolderTypeDefault::getFolderType, folder.getFolderType()))) {
                if (subjectMatches(d, userId)) mergePerms(result, d.getPerms());
            }
        }
        // 【文件权限体系】公共文件夹对全员始终可见（ACL 只叠加管理权限，未匹配角色的用户同样可见），
        // 下载权限一并兜底（是否实际可下载由下载接口的开关约束）
        if ("public".equals(folder.getFolderType())) {
            if (!result.contains(P_VIEW)) result.add(P_VIEW);
            if (!result.contains(P_DOWNLOAD)) result.add(P_DOWNLOAD);
        }
        return result;
    }

    /** 批量权限视图：getFolderTree 组装时使用。ACL 一次批量查询，上级链按 owner 缓存，类型授权按空间缓存，避免逐文件夹 N+1 */
    public Map<Long, List<String>> permsForBatch(Collection<BizFileFolder> folders, Long userId) {
        Map<Long, List<String>> result = new HashMap<>();
        if (folders == null || folders.isEmpty()) return result;
        if (SecurityUtils.isAdmin()) {
            for (BizFileFolder f : folders) result.put(f.getId(), ALL_PERMS);
            return result;
        }
        Set<Long> ids = folders.stream().map(BizFileFolder::getId).collect(Collectors.toSet());
        Map<Long, List<BizFolderAcl>> aclByFolder = new HashMap<>();
        for (BizFolderAcl acl : aclMapper.selectList(
                new LambdaQueryWrapper<BizFolderAcl>().in(BizFolderAcl::getFolderId, ids))) {
            aclByFolder.computeIfAbsent(acl.getFolderId(), k -> new ArrayList<>()).add(acl);
        }
        // 上级链按 owner 缓存：同一 owner 的多个文件夹只爬一次链
        Map<Long, Boolean> manageCache = new HashMap<>();
        // 【文件权限 v2】类型授权按空间缓存：一行授权对整个空间只解析一次主体/展开一次词表
        Map<String, List<String>> typePermCache = new HashMap<>();
        for (BizFileFolder folder : folders) {
            Long ownerId = folder.getOwnerId();
            boolean privileged = manageCache.computeIfAbsent(ownerId, oid ->
                    oid != null && (oid.equals(userId) || isManagerChain(userId, oid)));
            List<String> perms = new ArrayList<>();
            if (privileged) {
                perms.addAll(ALL_PERMS);
            } else {
                for (BizFolderAcl acl : aclByFolder.getOrDefault(folder.getId(), Collections.emptyList())) {
                    if (userHasRole(userId, acl.getRoleId())) {
                        mergePerms(perms, acl.getPerms());
                    }
                }
                List<String> typePerms = typePermCache.computeIfAbsent(folder.getFolderType(), t -> {
                    List<String> tokens = new ArrayList<>();
                    if (t == null) return tokens;
                    for (BizFolderTypeDefault d : typeDefaultMapper.selectList(
                            new LambdaQueryWrapper<BizFolderTypeDefault>().eq(BizFolderTypeDefault::getFolderType, t))) {
                        if (subjectMatches(d, userId)) mergePerms(tokens, d.getPerms());
                    }
                    return tokens;
                });
                for (String p : typePerms) if (!perms.contains(p)) perms.add(p);
                // 公共文件夹对全员始终可见，下载权限一并兜底（是否可下载由下载接口开关约束）
                if ("public".equals(folder.getFolderType())) {
                    if (!perms.contains(P_VIEW)) perms.add(P_VIEW);
                    if (!perms.contains(P_DOWNLOAD)) perms.add(P_DOWNLOAD);
                }
            }
            result.put(folder.getId(), perms);
        }
        return result;
    }

    private boolean aclGranted(Long folderId, Long userId, String action) {
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                if (csvHas(acl.getPerms(), action)) return true;
            }
        }
        return false;
    }

    /**
     * 【文件权限 v2】类型授权判定：该空间(公共/私人)存在主体匹配(本人=USER行 / 本人角色=ROLE行)且含该动作的授权行。
     * 实时生效——不落 ACL 快照，取消勾选立即收回(V2026.11.09.5 起 materializeAcl 已退役)。
     */
    private boolean typeGranted(String folderType, Long userId, String action) {
        for (BizFolderTypeDefault d : typeDefaultMapper.selectList(
                new LambdaQueryWrapper<BizFolderTypeDefault>().eq(BizFolderTypeDefault::getFolderType, folderType))) {
            if (subjectMatches(d, userId) && csvHas(d.getPerms(), action)) return true;
        }
        return false;
    }

    /** 授权主体匹配: USER 行直接对 userId; ROLE 行解析 roleId→roleCode 比对当前登录角色(与 userHasRole 同口径) */
    private boolean subjectMatches(BizFolderTypeDefault d, Long userId) {
        if ("USER".equals(d.getSubjectType())) {
            return userId != null && userId.equals(d.getSubjectId());
        }
        return userHasRole(userId, d.getSubjectId());
    }

    private boolean userHasRole(Long userId, Long roleId) {
        // SecurityUser 只携带 roleCodes（角色 code），ACL/类型行存 roleId → 解析 roleId 为 code 后比对
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || role.getRoleCode() == null) return false;
        return SecurityUtils.getCurrentRoleCodes().contains(role.getRoleCode());
    }

    /**
     * 词表展开匹配(读时, 数据不回填)：
     * - 存量 'edit' 伞 ⇒ rename/replace 任一细词命中均真(兼容 2026-08 起 ACL 存量与类型行旧值)；
     * - 查 P_EDIT(伞, 如 ACL 配置门槛 requireEdit) ⇒ rename 或 replace 命中即真；
     * - 其余动作精确匹配。
     */
    static boolean csvHas(String csv, String action) {
        if (csv == null || action == null) return false;
        boolean hasEdit = false, hasRename = false, hasReplace = false;
        for (String p : csv.split(",")) {
            switch (p) {
                case P_EDIT -> hasEdit = true;
                case P_RENAME -> hasRename = true;
                case P_REPLACE -> hasReplace = true;
            }
        }
        return switch (action) {
            case P_EDIT -> hasEdit || hasRename || hasReplace;
            case P_RENAME -> hasEdit || hasRename;
            case P_REPLACE -> hasEdit || hasReplace;
            default -> csv.contains(",") ? Arrays.asList(csv.split(",")).contains(action) : csv.equals(action);
        };
    }

    /** 把一段 perms CSV 并入结果(读时展开): 细词直接并入; 'edit' 伞展开为 edit+rename+replace; rename/replace 命中时补发伞词兼容旧按钮(MP includes('edit')) */
    private static void mergePerms(List<String> result, String csv) {
        if (csv == null) return;
        for (String p : csv.split(",")) {
            if (p.isBlank()) continue;
            if (!result.contains(p)) result.add(p);
        }
        if (csvHas(csv, P_EDIT) && !result.contains(P_EDIT)) result.add(P_EDIT);
        if (csvHas(csv, P_RENAME) && !result.contains(P_RENAME)) result.add(P_RENAME);
        if (csvHas(csv, P_REPLACE) && !result.contains(P_REPLACE)) result.add(P_REPLACE);
    }
}
