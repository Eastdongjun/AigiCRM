package com.aigicrm.module.file.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 【2026-11-10 文件权限终版：组织底座】用户令"开干全部都要做"。
 * 模型一句话: 文件跟着人走, 人跟着组织走, 组织决定看得见什么; 动作跟矩阵走, 例外跟授权走, 授权必须过期。
 *
 * 判定链(文件夹): admin > 创建人 > 创建人上级链 > 例外授权(未过期) > 组织矩阵(file,manage)
 * 判定链(文件):   admin > 上传者本人恒可管 > 上传者上级链 > 例外授权(未过期) > 组织矩阵(动作分view/manage/delete)
 *   - 矩阵动作映射: view/download→(file,view); upload/rename/replace/share→(file,manage); delete→(file,delete)
 *   - 逐夹 ACL 已退役(V2026.11.10.1 转换为 folder_id 定向授权, 代码停读 biz_folder_acl)
 *   - 公共"全员可见"兜底已删除(拍板①B: 看也按组织收窄); 跨组织通道=分享/例外授权, 别无暗门
 * 归属快照: biz_file.org_id/biz_file_folder.org_id(上传/建夹时组织节点, 转岗不漂移), 授权 scope_org_id 匹配用。
 */
@Service
@RequiredArgsConstructor
public class FolderPermissionService {

    public static final String P_VIEW = "view";
    public static final String P_UPLOAD = "upload";
    /** 伞词(ACL 转换行遗留): 判定读时展开为 rename∨replace; 新授权写入归一细词 */
    public static final String P_EDIT = "edit";
    public static final String P_DELETE = "delete";
    public static final String P_SHARE = "share";
    public static final String P_DOWNLOAD = "download";
    public static final String P_RENAME = "rename";
    public static final String P_REPLACE = "replace";
    public static final String P_CREATE = "create";
    public static final String OBJ_FOLDER = "FOLDER";
    public static final String OBJ_FILE = "FILE";
    private static final List<String> ALL_PERMS =
            List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD, P_RENAME, P_REPLACE);
    private static final int MAX_HOP = 5;
    /** 矩阵模块名 */
    public static final String MODULE_FILE = "file";

    private final BizFileFolderMapper folderMapper;
    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final BizFolderTypeDefaultMapper grantMapper;
    private final OrgScopeResolver scopeResolver;

    // ==================== 判定入口 ====================

    /** 夹上下文的动作归组: 夹内文件动作(upload/view/download/share)→FILE 定向授权; 夹自身动作(rename/delete/create)→FOLDER */
    static String ctxObjectTypeOf(String action) {
        return switch (action == null ? "" : action) {
            case P_UPLOAD, P_VIEW, P_DOWNLOAD, P_SHARE, P_EDIT, P_REPLACE -> OBJ_FILE;
            default -> OBJ_FOLDER;
        };
    }

    public boolean check(Long folderId, String action, Long userId) {
        if (folderId == null || userId == null) return false;
        if (SecurityUtils.isAdmin()) return true;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return false;
        if (Objects.equals(folder.getOwnerId(), userId)) return true;
        if (isManagerChain(userId, folder.getOwnerId())) return true;
        if (grantGranted(ctxObjectTypeOf(action), folder.getFolderType(), folder.getId(), folder.getOrgId(), action, userId)) return true;
        // 【拍板②文件跟夹走】矩阵通道锚定夹归属组织(夹.org ∈ 我的可见部门集), 不再按创建人
        return deptVisible(folder.getOrgId(), userId, matrixActionOf(action));
    }

    public boolean checkFile(Long fileId, String action, Long userId) {
        if (fileId == null || userId == null) return false;
        if (SecurityUtils.isAdmin()) return true;
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return false;
        // 【2026-11-10 用户令"A"】上传者本人恒可管理自己上传的文件(改名/替换/删除/分享/下载), 无论在哪个夹
        if (Objects.equals(file.getUploaderId(), userId)) return true;
        if (isManagerChain(userId, file.getUploaderId())) return true;
        String folderType = null;
        Long folderRef = file.getFolderId();
        BizFileFolder ref = null;
        if (folderRef != null) {
            ref = folderMapper.selectById(folderRef);
            if (ref != null) folderType = ref.getFolderType();
        }
        if (grantGranted(OBJ_FILE, folderType, folderRef, file.getOrgId(), action, userId)) return true;
        // 【拍板②】夹内文件跟夹走(夹.org ∈ 可见部门集); 无夹文件(激励附件/私人无夹)跟上传人(OWNER 锚)
        if (ref != null) return deptVisible(ref.getOrgId(), userId, matrixActionOf(action));
        Long uploader = file.getUploaderId();
        return uploader != null && matrixVisible(uploader, userId, matrixActionOf(action));
    }

    /**
     * 建夹门槛(公共/私人分列): 私人夹=全员可建(控制器 file:folder:manage 码前置, 个人空间天生自由);
     * 公共夹=admin ∨ 例外授权 FOLDER:create ∨ 组织负责人(拍板④: 负责人可建本组织公共夹, org 快照=所辖最高组织)。
     */
    public boolean canCreateFolder(String folderType, Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        if ("private".equals(folderType)) return true;
        if (grantGranted(OBJ_FOLDER, folderType, null, null, P_CREATE, userId)) return true;
        return userId != null && leaderOrgId(userId) != null;
    }

    /** 用户担任负责人的最高组织节点 id(公共夹 org 快照用; 未任负责人=null) */
    public Long leaderOrgId(Long userId) {
        List<SysDept> led = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getLeaderId, userId));
        if (led == null || led.isEmpty()) return null;
        // 最高=祖先链最短: 逐节点算深度取最浅
        Long best = null; int bestDepth = Integer.MAX_VALUE;
        for (SysDept d : led) {
            int depth = 0; SysDept cur = d;
            while (cur.getParentId() != null && cur.getParentId() != 0 && depth < 50) {
                SysDept parent = deptMapper.selectById(cur.getParentId());
                if (parent == null) break;
                cur = parent; depth++;
            }
            if (depth < bestDepth) { bestDepth = depth; best = d.getId(); }
        }
        return best;
    }

    // ==================== 组织矩阵(底座) ====================

    /** 动作→矩阵档位: view/download→view; upload/rename/replace/share→manage; delete→delete(拍板③独立) */
    static String matrixActionOf(String action) {
        return switch (action == null ? "" : action) {
            case P_VIEW, P_DOWNLOAD -> "view";
            case P_UPLOAD, P_RENAME, P_REPLACE, P_SHARE, P_EDIT -> "manage";
            case P_DELETE -> "delete";
            default -> "view";
        };
    }

    /**
     * 组织矩阵判定: 目标人(anchor=owner/uploader)是否落在 当前登录人 的可见集内。
     * resolveModuleUsers 走权限矩阵(role_perm_scope file 域) + 角色默认 + 系统开关(负责人子树/上级链),
     * null=全部, 集合恒含本人; SecurityUser 缺席(系统任务/测试上下文)=空集 fail-closed。
     */
    private boolean matrixVisible(Long targetUserId, Long currentUserId, String matrixAction) {
        if (targetUserId == null) return false;
        Set<Long> visible = matrixSet(currentUserId, matrixAction);
        return visible != null && visible.contains(targetUserId);
    }

    /** 【拍板②】可见部门集(ORG 锚, null=全部)——夹/文件归属过滤用 */
    Set<Long> matrixDeptSet(Long currentUserId, String matrixAction) {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Set.of();
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(user).module(MODULE_FILE).action(matrixAction)
                .anchor(com.aigicrm.common.scope.ScopeRequest.AnchorType.ORG).build();
        com.aigicrm.common.scope.ScopeDecision d = scopeResolver.resolveScope(req);
        return d == null || d.getVisibleDeptIds() == null ? null : new HashSet<>(d.getVisibleDeptIds());
    }

    private boolean deptVisible(Long orgId, Long userId, String matrixAction) {
        if (orgId == null) return false;
        Set<Long> depts = matrixDeptSet(userId, matrixAction);
        return depts != null && depts.contains(orgId);
    }

    /** 可见用户集(OWNER 锚, null=全部); 无夹文件/激励附件过滤用 */
    Set<Long> matrixSet(Long currentUserId, String matrixAction) {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Set.of();
        List<Long> ids = scopeResolver.resolveModuleUsers(user, MODULE_FILE, matrixAction);
        return ids == null ? null : new HashSet<>(ids);
    }

    // ==================== 例外授权(必须过期) ====================

    /** 授权命中: 主体匹配 ∧ 对象组匹配 ∧ 空间/逐夹定向匹配 ∧ 组织范围匹配 ∧ 词表命中 ∧ 未过期 */
    private boolean grantGranted(String objectType, String folderType, Long targetFolderId, Long targetOrgId, String action, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (BizFolderTypeDefault g : grantMapper.selectList(new LambdaQueryWrapper<>())) {
            if (g.getExpiresAt() != null && g.getExpiresAt().isBefore(now)) continue;
            if (!Objects.equals(g.getObjectType(), objectType)) continue;
            if (g.getFolderId() != null && !g.getFolderId().equals(targetFolderId)) continue;
            if (g.getFolderId() == null && folderType != null && !Objects.equals(g.getFolderType(), folderType)) continue;
            if (g.getScopeOrgId() != null && !g.getScopeOrgId().equals(targetOrgId)) continue;
            if (!subjectMatches(g, userId)) continue;
            if (csvHas(g.getPerms(), action)) return true;
        }
        return false;
    }

    private boolean subjectMatches(BizFolderTypeDefault g, Long userId) {
        if ("USER".equals(g.getSubjectType())) return userId != null && userId.equals(g.getSubjectId());
        SysRole role = roleMapper.selectById(g.getSubjectId());
        if (role == null || role.getRoleCode() == null) return false;
        return SecurityUtils.getCurrentRoleCodes().contains(role.getRoleCode());
    }

    // ==================== 按钮权限视图(双词表输出, 兼容 MP includes('edit')) ====================

    public List<String> permsFor(Long folderId, Long userId) {
        if (folderId == null) return List.of();
        if (SecurityUtils.isAdmin()) return ALL_PERMS;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return List.of();
        if (Objects.equals(folder.getOwnerId(), userId) || isManagerChain(userId, folder.getOwnerId())) {
            return ALL_PERMS;
        }
        List<String> result = new ArrayList<>();
        for (String p : List.of(P_VIEW, P_UPLOAD, P_DOWNLOAD, P_RENAME, P_REPLACE, P_SHARE, P_DELETE)) {
            if (check(folderId, p, userId)) mergePerms(result, p);
        }
        return result;
    }

    /** 批量: 组织集按矩阵动作缓存一次, 授权全表缓存一次, 防逐夹 N+1 */
    public Map<Long, List<String>> permsForBatch(Collection<BizFileFolder> folders, Long userId) {
        Map<Long, List<String>> result = new HashMap<>();
        if (folders == null || folders.isEmpty()) return result;
        if (SecurityUtils.isAdmin()) {
            for (BizFileFolder f : folders) result.put(f.getId(), ALL_PERMS);
            return result;
        }
        Map<String, Set<Long>> matrixCache = new HashMap<>();
        List<BizFolderTypeDefault> grants = grantMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Boolean> chainCache = new HashMap<>();
        for (BizFileFolder folder : folders) {
            boolean privileged = chainCache.computeIfAbsent(folder.getOwnerId() == null ? -1L : folder.getOwnerId(),
                    oid -> oid.equals(userId) || (oid > 0 && isManagerChain(userId, oid)));
            if (privileged) { result.put(folder.getId(), ALL_PERMS); continue; }
            List<String> perms = new ArrayList<>();
            for (String p : List.of(P_VIEW, P_UPLOAD, P_DOWNLOAD, P_RENAME, P_REPLACE, P_SHARE, P_DELETE)) {
                Set<Long> depts = matrixCache.computeIfAbsent(matrixActionOf(p), ma -> matrixDeptSet(userId, ma));
                if ((depts != null && folder.getOrgId() != null && depts.contains(folder.getOrgId()))
                        || grantHitIn(grants, ctxObjectTypeOf(p), folder.getFolderType(), folder.getId(), folder.getOrgId(), p, userId)) {
                    mergePerms(perms, p);
                }
            }
            result.put(folder.getId(), perms);
        }
        return result;
    }

    private boolean grantHitIn(List<BizFolderTypeDefault> grants, String objectType, String folderType, Long targetFolderId, Long targetOrgId, String action, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (BizFolderTypeDefault g : grants) {
            if (g.getExpiresAt() != null && g.getExpiresAt().isBefore(now)) continue;
            if (!Objects.equals(g.getObjectType(), objectType)) continue;
            if (g.getFolderId() != null && !g.getFolderId().equals(targetFolderId)) continue;
            if (g.getFolderId() == null && folderType != null && !Objects.equals(g.getFolderType(), folderType)) continue;
            if (g.getScopeOrgId() != null && !g.getScopeOrgId().equals(targetOrgId)) continue;
            if (!subjectMatches(g, userId)) continue;
            if (csvHas(g.getPerms(), action)) return true;
        }
        return false;
    }

    /** 【①B 列表过滤】该夹是否存在命中当前人的 FILE view 定向授权(=夹内全部文件对其可见) */
    public boolean grantCoversFolderFiles(String folderType, Long folderId, Long orgId, Long userId) {
        return grantGranted(OBJ_FILE, folderType, folderId, orgId, P_VIEW, userId);
    }

    // ==================== 上级链(个人以下) ====================

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

    // ==================== 词表展开(edit 伞读时兼容, ACL 转换行零迁移) ====================

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

    private static void mergePerms(List<String> result, String token) {
        if (token != null && !result.contains(token)) result.add(token);
    }

    /** 保留给既有调用方的兼容常量集合 */
    private static final List<String> FOLDER_LEAF_PERMS = List.of(P_VIEW, P_UPLOAD, P_DOWNLOAD, P_RENAME, P_REPLACE, P_SHARE, P_DELETE);

    public static List<String> folderLeafPerms() { return FOLDER_LEAF_PERMS; }
}
