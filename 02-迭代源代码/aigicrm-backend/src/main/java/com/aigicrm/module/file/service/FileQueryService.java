package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFileShare;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 文件查询面：文件夹树、文件列表、可见性过滤、访问/下载判定、激励可见缓存。
 *
 * <p>S5.3 从 {@link FileService} 抽出。授权仍以 FolderPermissionService 为单一来源；
 * 本类只负责“查得到什么/能否访问”，不做上传、删除、版本、缩略图与批量编排。</p>
 */
@Service
@RequiredArgsConstructor
public class FileQueryService {

    private static final String LIT_PUBLIC = "public";
    private static final String LIT_PRIVATE = "private";
    private static final String LIT_INCENTIVE = "incentive";
    private static final String LIT_FABUSHI = "fabushi";
    private static final String LIT_HTTPS = "https://";

    private static final long INCENTIVE_VISIBILITY_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final int INCENTIVE_CACHE_PRUNE_THRESHOLD = 256;

    private final BizFileMapper fileMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileShareMapper fileShareMapper;
    private final SysUserMapper userMapper;
    private final FolderPermissionService folderPermissionService;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final JdbcTemplate jdbc;

    /** 激励可见 fileId 集合缓存：key=userId:currentGroupId。 */
    private final Map<String, IncentiveVisibilityCache> incentiveVisibilityCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 可替换的单调时钟（单测控制 TTL）；AtomicReference 保证替换的可见性/原子性。 */
    private final java.util.concurrent.atomic.AtomicReference<java.util.function.LongSupplier> incentiveCacheClock =
            new java.util.concurrent.atomic.AtomicReference<>(System::nanoTime);

    public List<BizFileFolder> getFolderTree(String scope, Long userId) {
        LambdaQueryWrapper<BizFileFolder> wrapper = new LambdaQueryWrapper<>();
        if (LIT_PUBLIC.equals(scope)) {
            wrapper.eq(BizFileFolder::getFolderType, LIT_PUBLIC);
        } else if (LIT_PRIVATE.equals(scope)) {
            // 【文件权限体系】私有树候选集扩展为全部私有文件夹，
            // 可见性（本人创建/上级链/ACL view）在下方内存过滤，支持上级与 ACL 授权用户查看他人私有树。
            wrapper.eq(BizFileFolder::getFolderType, LIT_PRIVATE);
        } else if (com.aigicrm.module.file.service.FabushiFolderService.FOLDER_TYPE.equals(scope)) {
            // 【FILE-FABU-FOLDER-001】法布施树=固定两个系统夹(书籍 sort1/分享心得 sort2), 全员可见
            // (书籍=电子书大家读; 分享沿用空间现口径), 无子夹无 ACL。
            // 【FILE-FABU-FOLDER-003 尾巴】角标 fileCount 按夹批量 count 回填(其他分支同口径,
            // 原漏回填致 UI 显示 0/N 而实际 5/12)。
            List<BizFileFolder> fabushiFolders = folderMapper.selectList(new LambdaQueryWrapper<BizFileFolder>()
                    .eq(BizFileFolder::getFolderType, FabushiFolderService.FOLDER_TYPE)
                    .orderByAsc(BizFileFolder::getSortOrder));
            for (BizFileFolder folder : fabushiFolders) {
                long count = fileMapper.selectCount(
                        new LambdaQueryWrapper<BizFile>()
                                .eq(BizFile::getFolderId, folder.getId())
                                .eq(BizFile::getDeleted, 0)
                                .eq(BizFile::getFileScope, FabushiFolderService.FOLDER_TYPE));
                folder.setFileCount((int) count);
            }
            return fabushiFolders;
        } else {
            // 【安全修复 FEAT-20260804-FILE-AUTHZ】未识别的 scope 默认拒绝(返回空)，
            // 而不是默认放行返回全部文件夹(此前会泄露他人私有文件夹信息)。
            // 文件夹功能目前只服务于 public/private 两个 Tab，incentive/shared_to_me 不走文件夹树。
            return Collections.emptyList();
        }
        wrapper.orderByAsc(BizFileFolder::getSortOrder);
        List<BizFileFolder> folders = folderMapper.selectList(wrapper);

        // 【文件权限体系】可见性过滤：本人创建 / 上级链 / ACL 含 view / public（管理员全部可见）
        Set<Long> visibleIds = visibleFolderIds(folders, userId);
        folders = folders.stream()
                .filter(f -> visibleIds.contains(f.getId()))
                .toList();

        // 【文件权限体系】批量填充每个文件夹的当前用户权限（前端逐行控制按钮显隐），
        // ACL 一次批量查询 + 上级链按 owner 缓存，避免逐文件夹 N+1
        Map<Long, List<String>> permsMap = folderPermissionService.permsForBatch(folders, userId);
        // 【修复 2026-11-12 根因5】夹行自身动作(rename/delete)与文件动作分字段——FOLDER 行授权终于点亮夹按钮
        Map<Long, List<String>> folderActionsMap = folderPermissionService.folderActionsForBatch(folders, userId);
        for (BizFileFolder folder : folders) {
            long count = fileMapper.selectCount(
                    new LambdaQueryWrapper<BizFile>()
                            .eq(BizFile::getFolderId, folder.getId())
                            .eq(BizFile::getDeleted, 0)
            );
            folder.setFileCount((int) count);
            folder.setPerms(permsMap.getOrDefault(folder.getId(), Collections.emptyList()));
            folder.setFolderActions(folderActionsMap.getOrDefault(folder.getId(), Collections.emptyList()));
        }

        return buildFolderTree(folders, 0L);
    }

    public IPage<BizFile> pageFiles(int current, int size, Long folderId, String scope, String keyword, Long userId) {

        // 【安全修复 FEAT-20260804-FILE-AUTHZ】scope 白名单：缺省/空串/未知 scope 一律返回空页，
        // 在查询前拒绝，避免未识别 scope 落入下方 folderType=scope 空候选集 → isNull(folder_id)
        // 分支后跨 scope 枚举他人私有无文件夹文件的元数据。
        if (!isKnownScope(scope)) {
            return new Page<>(current, size, 0);
        }
        LambdaQueryWrapper<BizFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizFile::getDeleted, 0);

        if (folderId != null) {
            wrapper.eq(BizFile::getFolderId, folderId);
        }
        wrapper.eq(BizFile::getFileScope, scope);
        // 【S3516 2026-11-23】applyIncentiveVisibility 恒返回 true（见其注释），原 !...return 空页
        // 是永假分支，Sonar 以 BLOCKER 报出。改为 void：语义就是"只加过滤条件，不短路"。
        if (LIT_INCENTIVE.equals(scope)) {
            applyIncentiveVisibility(wrapper, userId);
        }
        if (LIT_FABUSHI.equals(scope) && !applyFabushiVisibility(wrapper, userId)) {
            return new Page<>(current, size, 0);
        }
        if (!LIT_INCENTIVE.equals(scope) && !LIT_FABUSHI.equals(scope)
                && !applyFolderVisibility(wrapper, scope, folderId, userId)) {
            return new Page<>(current, size, 0);
        }
        if (keyword != null && !keyword.isEmpty()) {
            FileQuerySupport.applyGlobalKeyword(wrapper, keyword);
        }
        wrapper.orderByDesc(BizFile::getCreatedAt);
        return decoratePage(fileMapper.selectPage(new Page<>(current, size), wrapper), userId);
    }

    /** public/private/incentive/fabushi 四类合法 scope。 */
    private static boolean isKnownScope(String scope) {
        return scope != null && (LIT_PUBLIC.equals(scope) || LIT_PRIVATE.equals(scope)
                || LIT_INCENTIVE.equals(scope) || LIT_FABUSHI.equals(scope));
    }

    /**
     * 激励空间可见性过滤：只往 wrapper 上加条件，不短路。
     * 【S3516 2026-11-23】原签名为 {@code boolean} 且注释写「返回 false 表示候选集必空」，
     * 但三条路径全部 return true——契约是假的，调用点的 {@code !...} 分支永远不成立。
     * 现改为 void，把假契约删掉；若将来真需要短路，另设显式语义再改调用方。
     */
    private void applyIncentiveVisibility(LambdaQueryWrapper<BizFile> wrapper, Long userId) {
        // 【2026-11-01 审计修复】admin/持 incentive:view-all 全量旁路——与激励侧 applyShareVisibility
        // 同口径(原文件侧无旁路: 总监在 MP 四项服务tab可见的未分享激励文件, PC 文件中心不出现,
        // 且下载分支无旁路时会出现"MP 可见 PC 下载 403"的双端分叉)
        if (incentiveViewAllGranted()) {
            return; // 全量可见, 不加可见性过滤
        }
        // 自己的 + 其他挂了分享的激励文件（与下载可见性同源，30s 缓存见 incentiveVisibleFileIds）
        Set<Long> sharedFileIds = incentiveVisibleFileIds(userId);
        // 【2026-11-10 拍板②激励按组织】可见 = 上传人 ∈ 组织矩阵(file,view)集 ∪ 本人 ∨ 记录分享
        Set<Long> orgVisible = folderPermissionService.matrixSet(userId, "view");
        if (orgVisible == null) {
            // 组织矩阵=全部(file view 配到 all): 激励附件本域不过滤(分享语义兜底在 record 侧)
            if (!sharedFileIds.isEmpty()) {
                wrapper.and(w -> w.in(BizFile::getUploaderId, java.util.List.of(userId))
                        .or().in(BizFile::getId, sharedFileIds));
            }
            return;
        }
        java.util.List<Long> allow = new java.util.ArrayList<>(orgVisible);
        if (!allow.contains(userId)) allow.add(userId);
        if (sharedFileIds.isEmpty()) {
            wrapper.in(BizFile::getUploaderId, allow);
        } else {
            wrapper.and(w -> w.in(BizFile::getUploaderId, allow)
                    .or().in(BizFile::getId, sharedFileIds));
        }
    }

    /** 法布施空间可见性过滤；返回 false 表示候选集必空。
     * 【FILE-FABU-FOLDER-003 2026-11-20】分夹后 folderId=两系统夹为合法过滤维度(夹内文件
     * 属于"上传人可见集"口径的自然子集, 不放宽——书籍夹文件本就全员可读=书目库口径,
     * 分享夹沿用 fabushi|file_view 空间现口径); 原"带 folderId 即空"是分夹前旧假设。
     * 非系统夹 folderId 已在 FileController.upload 路由层拒绝, 此处不重复判(查询侧
     * 传非法夹 id 属越权探测, 命中空集自然为空)。 */
    private boolean applyFabushiVisibility(LambdaQueryWrapper<BizFile> wrapper, Long userId) {
        // 【2026-11-17 阶段二】法布施空间：默认公司级 all，但可由权限中心按角色/人员收窄到
        // 大区/办事处/部门/self；按上传人 ∈ fabushi|file_view 范围过滤。
        // 【003 修复】上传人可见集过滤与 folderId 过滤叠加生效(L224-226 已按 folderId eq),
        // 夹内文件仍受可见集约束(非系统夹探测空集)。
        java.util.Optional<Set<Long>> visibleUploaders = folderPermissionService.fabushiVisibleUploaderIds(userId);
        if (visibleUploaders.isPresent()) {
            if (visibleUploaders.get().isEmpty()) return false;
            wrapper.in(BizFile::getUploaderId, visibleUploaders.get());
        }
        return true;
    }

    /** 文件夹可见性过滤（incentive/fabushi 之外所有 scope）；返回 false 表示候选集必空。 */
    private boolean applyFolderVisibility(LambdaQueryWrapper<BizFile> wrapper, String scope,
                                          Long folderId, Long userId) {
        // 【文件权限体系】统一文件夹可见性过滤（除 incentive 外的所有 scope 生效）：
        // 夹内文件仅返回用户可见文件夹中的（本人创建/上级链/ACL view/public），上级可查看下级私有文件夹内文件；
        // private scope 的无文件夹文件仍需本人上传；未知/空 scope 候选集为空 → 夹内文件一律不可见
        // （与 getFolderTree 的 scope 默认拒绝对称，封堵"任意 file:view 用户枚举任意文件夹文件"）。
        // incentive scope 已有文件级归属约束（本人上传/被分享），不在此过滤；
        // fabushi 已在上面按 fabushi|file_view 过滤，且无文件夹，同样跳过夹树逻辑。
        List<BizFileFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<BizFileFolder>()
                        .eq(BizFileFolder::getFolderType, scope));
        Set<Long> visibleIds = visibleFolderIds(folders, userId);
        if (folderId != null) {
            if (!visibleIds.contains(folderId)) {
                return false;
            }
        } else if (visibleIds.isEmpty()) {
            applyNoVisibleFolderBranch(wrapper, scope, userId);
        } else if (LIT_PRIVATE.equals(scope)) {
            applyPrivateMixedBranch(wrapper, userId, visibleIds);
        } else {
            applyNonPrivateMixedBranch(wrapper, visibleIds);
        }
        // 【2026-11-10 终版二段·拍板①②】逐文件过滤(夹可见≠夹内全部文件可见):
        // 夹内文件跟夹走(夹.org ∈ view 可见部门集) ∨ 无夹文件跟上传人(view 可见用户集) ∨ 本人 ∨ FILE view 定向授权(整夹覆盖)
        // 【拍板 A 2026-11-14 私人空间收口】private scope 不再走部门/用户矩阵——
        // 夹内文件=夹 ∈ visibleIds(本人/上级链/ACL) ∨ FILE view 授权整夹; 无夹=上传人 ∈ 本人∪下属。
        // 原逻辑把"夹.org ∈ deptSet"的同部门他人私人夹文件捞进列表(与树口径打架), 本批拔除。
        applyFileLevelFilter(wrapper, folders, visibleIds, scope, userId);
        return true;
    }

    /** 无任何可见文件夹时的分支：private 退化为 本人 ∪ 下属 的无夹文件，其余 scope 仅无夹文件。 */
    private void applyNoVisibleFolderBranch(LambdaQueryWrapper<BizFile> wrapper, String scope, Long userId) {
        if (LIT_PRIVATE.equals(scope)) {
            // 【拍板 A 2026-11-14】私人无夹文件=本人 ∪ manager 链下属(上级可见下属, 链上其余人不可见)
            java.util.Set<Long> subs = folderPermissionService.subordinateUserIds(userId);
            java.util.List<Long> owners = new java.util.ArrayList<>(subs);
            owners.add(userId);
            wrapper.and(w -> w.isNull(BizFile::getFolderId)
                    .and(w2 -> w2.in(BizFile::getUploaderId, owners)));
        } else {
            wrapper.isNull(BizFile::getFolderId);
        }
    }

    /** private scope 且有可见夹：无夹文件按 本人∪下属，夹内文件按 visibleIds。 */
    private void applyPrivateMixedBranch(LambdaQueryWrapper<BizFile> wrapper, Long userId, Set<Long> visibleIds) {
        // 【拍板 A 2026-11-14】私人无夹文件=本人 ∪ 下属(原仅本人); 私人夹内文件=夹 ∈ visibleIds(本人/上级链/ACL)
        java.util.Set<Long> subs = folderPermissionService.subordinateUserIds(userId);
        java.util.List<Long> owners = new java.util.ArrayList<>(subs);
        owners.add(userId);
        wrapper.and(w -> w.isNull(BizFile::getFolderId)
                .and(w2 -> w2.in(BizFile::getUploaderId, owners))
                .or().in(BizFile::getFolderId, visibleIds));
    }

    /** 非 private scope 且有可见夹：无夹文件 OR 夹 ∈ visibleIds。 */
    private void applyNonPrivateMixedBranch(LambdaQueryWrapper<BizFile> wrapper, Set<Long> visibleIds) {
        wrapper.and(w -> w.isNull(BizFile::getFolderId)
                .or().in(BizFile::getFolderId, visibleIds));
    }

    /** 逐文件过滤：private 走 本人∪下属∪授权覆盖夹∪visibleIds；其余 scope 走 本人∪部门夹∪用户矩阵∪授权覆盖夹。 */
    private void applyFileLevelFilter(LambdaQueryWrapper<BizFile> wrapper, List<BizFileFolder> folders,
                                      Set<Long> visibleIds, String scope, Long userId) {
        if (LIT_PRIVATE.equals(scope)) {
            applyPrivateFileLevelFilter(wrapper, folders, visibleIds, userId);
        } else {
            applyScopedFileLevelFilter(wrapper, folders, userId);
        }
    }

    private void applyPrivateFileLevelFilter(LambdaQueryWrapper<BizFile> wrapper, List<BizFileFolder> folders,
                                             Set<Long> visibleIds, Long userId) {
        java.util.Set<Long> subs = folderPermissionService.subordinateUserIds(userId);
        java.util.List<Long> owners = new java.util.ArrayList<>(subs);
        owners.add(userId);
        java.util.Set<Long> grantCovered = grantCoveredFolderIds(folders, userId);
        wrapper.and(w -> applyPrivateLevelAnd(w, owners, grantCovered, visibleIds));
    }

    /** private 逐文件过滤的 AND 子树（拆出 lambda 以免复杂度计入外层方法）。 */
    private void applyPrivateLevelAnd(LambdaQueryWrapper<BizFile> w, java.util.List<Long> owners,
                                      java.util.Set<Long> grantCovered, Set<Long> visibleIds) {
        w.in(BizFile::getUploaderId, owners);
        if (!grantCovered.isEmpty()) w.or().in(BizFile::getFolderId, grantCovered);
        if (!visibleIds.isEmpty()) w.or().in(BizFile::getFolderId, visibleIds);
    }

    private void applyScopedFileLevelFilter(LambdaQueryWrapper<BizFile> wrapper, List<BizFileFolder> folders, Long userId) {
        java.util.Set<Long> deptSet = folderPermissionService.matrixDeptSet(userId, "view");
        java.util.Set<Long> upSet = folderPermissionService.matrixSet(userId, "view");
        java.util.Set<Long> orgAllowed = new java.util.HashSet<>();
        java.util.Set<Long> grantCovered = new java.util.HashSet<>();
        for (BizFileFolder f : folders) {
            if (deptSet == null || (f.getOrgId() != null && deptSet.contains(f.getOrgId()))) orgAllowed.add(f.getId());
            if (folderPermissionService.grantCoversFolderFiles(f.getFolderType(), f.getId(), f.getOrgId(), userId)) {
                grantCovered.add(f.getId());
            }
        }
        if (upSet != null || !orgAllowed.isEmpty() || !grantCovered.isEmpty()) {
            wrapper.and(w -> applyScopedLevelAnd(w, userId, orgAllowed, upSet, grantCovered));
        }
    }

    /** 非 private 逐文件过滤的 AND 子树（拆出 lambda 以免复杂度计入外层方法）。 */
    private void applyScopedLevelAnd(LambdaQueryWrapper<BizFile> w, Long userId,
                                     java.util.Set<Long> orgAllowed, java.util.Set<Long> upSet,
                                     java.util.Set<Long> grantCovered) {
        w.eq(BizFile::getUploaderId, userId);
        if (!orgAllowed.isEmpty()) w.or().in(BizFile::getFolderId, orgAllowed);
        if (upSet != null && !upSet.isEmpty()) w.or().in(BizFile::getUploaderId, upSet);
        if (!grantCovered.isEmpty()) w.or().in(BizFile::getFolderId, grantCovered);
    }

    private java.util.Set<Long> grantCoveredFolderIds(List<BizFileFolder> folders, Long userId) {
        java.util.Set<Long> grantCovered = new java.util.HashSet<>();
        for (BizFileFolder f : folders) {
            if (folderPermissionService.grantCoversFolderFiles(f.getFolderType(), f.getId(), f.getOrgId(), userId)) {
                grantCovered.add(f.getId());
            }
        }
        return grantCovered;
    }

    /** 行级按钮权限 + 上传人姓名回填。 */
    private IPage<BizFile> decoratePage(IPage<BizFile> page, Long userId) {
        // 【T2 2026-11-12】行级按钮视图(与 checkFile 同源): 根目录/夹内文件的动作判定以后端行级为准,
        // 前端 useFilePage 不再硬编码根目录写词(消费端 row.perms 优先, 缺失回退夹级 perms)。
        Map<Long, List<String>> rowPerms = folderPermissionService.permsForFiles(page.getRecords(), userId);
        for (BizFile file : page.getRecords()) {
            file.setPerms(rowPerms.getOrDefault(file.getId(), Collections.emptyList()));
            if (file.getUploaderId() != null) {
                SysUser user = userMapper.selectById(file.getUploaderId());
                if (user != null) file.setUploaderName(user.getRealName());
            }
        }
        return page;
    }

    /** 按 oss_key 查询文件记录（key 下载路由的归属校验依据，保持租户隔离） */
    public BizFile getFileByKey(String fileKey) {
        return fileMapper.selectOne(
                new LambdaQueryWrapper<BizFile>()
                        .eq(BizFile::getOssKey, fileKey)
                        .last("LIMIT 1"));
    }

    /**
     * 【P0 2026-10-17 匿名下载 404】匿名公共下载/Logo 的元数据查询。
     * 匿名请求无登录态与集团上下文，租户拦截器会把查询改写成 tenant_id = NULL 恒空，
     * 公共下载因此全部 404。改走 mapper 的 @InterceptorIgnore(tenantLine=true) 查询，
     * 由 SQL 显式限定 deleted=0 AND file_scope='public' 收口（不放开租户隔离到其他路径）。
     */
    public BizFile getPublicFileByKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) return null;
        return fileMapper.selectPublicByKeyIgnoreTenant(fileKey);
    }

    /** 仅供匿名 Logo 路径使用：只取 public 范围文件的 OSS 地址（与 downloadPublic 同授权口径） */
    public String getOssUrlByKey(String fileKey) {
        BizFile file = getPublicFileByKey(fileKey);
        if (file != null && file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {
            return file.getOssUrl();
        }
        return null;
    }

    public Map<String, Object> getStorageInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        Map<String, Object> info = new HashMap<>();
        if (user != null) {
            info.put("used", user.getStorageUsed());
            info.put("quota", user.getStorageQuota());
            info.put("usedFormatted", FileNames.formatSize(user.getStorageUsed()));
            info.put("quotaFormatted", FileNames.formatSize(user.getStorageQuota()));
        }
        return info;
    }

    /**
     * 【文件权限体系】批量计算用户可见文件夹集合：本人创建 / 上级链可见 / ACL 含 view / public（管理员全部可见）。
     * 先按 ownerId 缓存上级链结果避免重复查询，剩余文件夹复用 FolderPermissionService.permsFor 判定
     * （ACL 逻辑单一来源），供树/列表一次性过滤，避免在文件循环内逐文件 checkFile 的 N+1。
     */
    private Set<Long> visibleFolderIds(List<BizFileFolder> folders, Long userId) {
        if (folders.isEmpty()) return Collections.emptySet();
        Set<Long> visible = new HashSet<>();
        List<BizFileFolder> pending = new ArrayList<>();
        Map<Long, Boolean> ownerVisibleCache = new HashMap<>();
        for (BizFileFolder f : folders) {
            if (Objects.equals(f.getOwnerId(), userId)) {
                visible.add(f.getId());
                continue;
            }
            Boolean managerVisible = ownerVisibleCache.computeIfAbsent(f.getOwnerId(),
                    oid -> oid != null && folderPermissionService.isManagerChain(userId, oid));
            if (Boolean.TRUE.equals(managerVisible)) {
                visible.add(f.getId());
            } else {
                pending.add(f);
            }
        }
        for (BizFileFolder f : pending) {
            if (folderPermissionService.permsFor(f.getId(), userId)
                    .contains(FolderPermissionService.P_VIEW)) {
                visible.add(f.getId());
            }
        }
        return visible;
    }

    private List<BizFileFolder> buildFolderTree(List<BizFileFolder> folders, Long parentId) {
        List<BizFileFolder> children = new ArrayList<>();
        for (BizFileFolder f : folders) {
            if (Objects.equals(f.getParentId(), parentId)) {
                f.setChildren(buildFolderTree(folders, f.getId()));
                children.add(f);
            }
        }
        return children;
    }

    /** 文件访问权限校验：上传者 / 被分享者 / 管理员 / 公共文件（/download/{id} 与 /download/{key} 同权） */
    public boolean canAccessFile(BizFile file, Long userId) {
        // 【2026-11-17 P1-1】DENY 必须压过 owner/公开/分享快速放行。
        if (folderPermissionService.hasFileGrantDeny(file, FolderPermissionService.P_VIEW, userId)) return false;
        return canAccessFileNoDeny(file, userId);
    }

    /** 下载路径已用 download DENY（actionsFor(download) 含 view）覆盖，避免重复查一遍 view DENY。 */
    private boolean canAccessFileNoDeny(BizFile file, Long userId) {
        if (LIT_PUBLIC.equals(file.getFileScope())) return true;
        if (userId.equals(file.getUploaderId())) return true;
        // 检查是否被分享
        return fileShareMapper.selectCount(
                new LambdaQueryWrapper<BizFileShare>()
                        .eq(BizFileShare::getFileId, file.getId())
                        .eq(BizFileShare::getSharedToUserId, userId)) > 0;
    }

    /**
     * 【文件权限体系】下载权限判定（downloadById 与 ossKey 下载路由 /download/** 同权）：
     * canAccessFile（上传者/被分享者/管理员/公共文件）通过即可下载；
     * 夹内文件其余用户按文件夹权限判定（上级链/ACL download，公共文件夹兜底可下载）；
     * 激励文件（fileScope=incentive）额外按"列表可见即可下载"判定——shared=1 且非本人名下的
     * 激励记录中的文件，全员可下载（与 pageFiles incentive 分支同规则，销售总监可下载下属激励材料）；
     * 非管理员还受夹内文件下载开关约束（1=允许下载，0/空=仅预览，管理员全权；激励文件 folderId=null 不受开关约束）。
     */
    /** 激励"查看全部"资格: admin 或 biz_incentive_grant.can_view_all=1(与 IncentiveService.canViewAll 同语义;
     *  经 jdbc 轻查避免跨模块注入服务/加构造参——文件侧列表与下载双分支共用) */
    private boolean incentiveViewAllGranted() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) return false;
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_incentive_grant WHERE user_id = ? AND can_view_all = 1", Long.class, uid);
        return n != null && n > 0;
    }

    public boolean canDownloadFile(BizFile file, Long userId) {
        // 【2026-11-17 P1-1】下载动作的 DENY 前置；与 canAccessFile 的 view DENY 双保险。
        if (folderPermissionService.hasFileGrantDeny(file, FolderPermissionService.P_DOWNLOAD, userId)) return false;
        boolean canDownload = canAccessFileNoDeny(file, userId);
        if (!canDownload && file.getFolderId() != null) {
            canDownload = folderPermissionService.checkFile(file.getId(), FolderPermissionService.P_DOWNLOAD, userId);
        }
        if (!canDownload && LIT_INCENTIVE.equals(file.getFileScope())) {
            // 【2026-11-01 审计修复】与列表旁路同口径: admin/激励授权 can_view_all 可下载(原仅本人+shared)
            canDownload = incentiveViewAllGranted() || isIncentiveFileVisible(file.getId(), userId);
        }
        if (!canDownload && LIT_FABUSHI.equals(file.getFileScope())) {
            // 【2026-11-17 阶段二】法布施下载与列表同口径：fabushi:view + fabushi|file_view 数据范围。
            canDownload = folderPermissionService.canViewFabushiFile(file, userId);
        }
        if (!canDownload) {
            return false;
        }
        if (file.getFolderId() != null) {
            BizFileFolder folder = folderMapper.selectById(file.getFolderId());
            if (folder != null && (folder.getDownloadable() == null || folder.getDownloadable() != 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 【激励文件可见性】与 pageFiles incentive 分支同规则：
     * shared=1 且非本人名下的激励记录中，fileIds/filePreIds/fileMidIds/filePostIds/fileTrackIds
     * 任一集合包含 fileId 即视为列表可见，列表可见即可下载。
     * <p>【性能 file-3 2026-10-18】原实现每个缩略图请求都 selectList 全量共享记录并逐条解析 5 个 JSON 槽
     * （N 个缩略图 = N 次全量加载）。现改为每用户缓存可见 fileId 集合 + 每次请求先查轻量指纹。
     */
    private boolean isIncentiveFileVisible(Long fileId, Long userId) {
        return fileId != null && incentiveVisibleFileIds(userId).contains(fileId);
    }

    /**
     * 返回"他人 shared=1 激励记录"五槽文件的并集（可能为空集）。
     * <p><b>安全性说明（为什么不会越权、何时失效）：</b>
     * <ul>
     *   <li>调用前提：{@link #canDownloadFile} 已先走 canAccessFile（管理员/公开文件/上传者/被分享者快速放行），
     *       只有这些都不满足的激励文件才会进入本方法；cache 只影响"是否可见"的重复计算，不改变判定规则。</li>
     *   <li>缓存键 = userId + 当前集团；集合只含 {@code shared=1 且 user_id<>本人} 的五槽 fileId，
     *       绝不会把本人私有/未分享记录放进集合。加载时再次按 shared/userId 过滤，防御 SQL/桩异常。</li>
     *   <li>每次请求仍先执行轻量指纹查询（COUNT/MAX(id)/MAX(updated_at)，参数绑定、租户插件照常生效），
     *       指纹变化立即重载；TTL（≤30s）到期即使指纹未变也强制重载，因此撤销激励共享/修改槽位后
     *       最长 30s 内失效。失败（指纹为 null 或加载抛异常）不使用/不写入缓存。</li>
     *   <li>空集合按 TTL 缓存（避免不可见文件反复触发全量加载），最长 30s 后重新判定。</li>
     * </ul>
     */
    private Set<Long> incentiveVisibleFileIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        String fingerprint = incentiveRecordMapper.selectSharedVisibilityFingerprint(userId);
        if (fingerprint == null) {
            // 指纹不可用（异常/无结果）：退回原全量加载口径，且不写缓存
            return loadIncentiveVisibleFileIds(userId);
        }
        long now = incentiveCacheClock.get().getAsLong();
        String cacheKey = incentiveCacheKey(userId);
        IncentiveVisibilityCache cached = incentiveVisibilityCache.get(cacheKey);
        if (cached != null && cached.expiresAtNanos > now && cached.fingerprint.equals(fingerprint)) {
            return cached.visibleFileIds;
        }
        Set<Long> visible = loadIncentiveVisibleFileIds(userId);
        incentiveVisibilityCache.put(cacheKey,
                new IncentiveVisibilityCache(fingerprint, now + INCENTIVE_VISIBILITY_TTL_NANOS, visible));
        pruneExpiredIncentiveVisibility(now);
        return visible;
    }

    /** 按原口径全量加载：SELECT shared=1 AND user_id<>userId，解析五槽 JSON 并合并。 */
    private Set<Long> loadIncentiveVisibleFileIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        List<BizIncentiveRecord> sharedRecords = incentiveRecordMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveRecord>()
                        .eq(BizIncentiveRecord::getShared, 1)
                        .ne(BizIncentiveRecord::getUserId, userId)
        );
        Set<Long> ids = new java.util.HashSet<>();
        for (BizIncentiveRecord r : sharedRecords) {
            // 双重校验 shared/userId：即使 mapper 返回越界行也不得进入可见集合
            if (r == null || !Integer.valueOf(1).equals(r.getShared())
                    || userId.equals(r.getUserId())) {
                continue;
            }
            ids.addAll(r.getFileIds());
            ids.addAll(r.getFilePreIds());
            ids.addAll(r.getFileMidIds());
            ids.addAll(r.getFilePostIds());
            ids.addAll(r.getFileTrackIds());
        }
        return ids.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    /** 缓存键含当前集团：切换集团后租户过滤口径不同，禁止复用另一集团的集合。 */
    private String incentiveCacheKey(Long userId) {
        Long groupId = null;
        var current = SecurityUtils.getCurrentUser();
        if (current != null) groupId = current.getCurrentGroupId();
        return userId + ":" + (groupId == null ? "-" : groupId);
    }

    /** 条目较多时顺手清掉过期项，防止不同用户的过期集合长期驻留内存。 */
    private void pruneExpiredIncentiveVisibility(long now) {
        if (incentiveVisibilityCache.size() < INCENTIVE_CACHE_PRUNE_THRESHOLD) return;
        incentiveVisibilityCache.values().removeIf(entry -> entry.expiresAtNanos <= now);
    }

    /** 每用户可见激励文件集合快照（只读；不含任何权限/封禁结论）。 */
    private static final class IncentiveVisibilityCache {
        private final String fingerprint;
        private final long expiresAtNanos;
        private final Set<Long> visibleFileIds;

        private IncentiveVisibilityCache(String fingerprint, long expiresAtNanos, Set<Long> visibleFileIds) {
            this.fingerprint = fingerprint;
            this.expiresAtNanos = expiresAtNanos;
            this.visibleFileIds = visibleFileIds;
        }
    }

}
