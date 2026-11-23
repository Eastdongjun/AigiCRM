package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFileVersion;
import com.aigicrm.module.file.entity.BizFileShare;
import com.aigicrm.module.file.entity.BizFolderAcl;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_FABUSHI = "fabushi";
    private static final String LIT_INCENTIVE = "incentive";
    private static final String LIT_HTTPS = "https://";
    private static final String LIT_PUBLIC = "public";
    private static final String LIT_PRIVATE = "private";
    private static final String LIT_X = "没有访问权限，请联系管理员授权";
    private static final String LIT_X_2 = "文件不存在";

    /**
     * 【性能 file-3 2026-10-18】激励可见集合缓存 TTL：硬上限 30s。
     * 即使指纹查询漏掉某种变化（例如同一秒内更新且 MAX(updated_at) 未变），
     * 到期也强制全量重载，撤销/新增激励共享的可见性延迟不超过该值。
     * 用纳秒单调时钟计时，避免墙上时钟回拨把 30s 窗口意外拉长。
     */
    private static final long INCENTIVE_VISIBILITY_TTL_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    /** 激励缓存条目超过该阈值时顺手清理过期项，避免长期驻留 */
    private static final int INCENTIVE_CACHE_PRUNE_THRESHOLD = 256;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileService.class);

    private final BizFileMapper fileMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileShareMapper fileShareMapper;
    /** 【V2026.11.14.1 版本历史】replace 归档/版本列表/回滚 */
    private final com.aigicrm.module.file.mapper.BizFileVersionMapper fileVersionMapper;
    /** 【V2026.11.14.3 P1】版本上限/保留期配置读取 */
    private final com.aigicrm.module.system.service.SysConfigService configService;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final SysUserMapper userMapper;
    private final FolderPermissionService folderPermissionService;
    // 【2026-11-10 拍板③】删除=高敏动作, 全程走审计
    private final com.aigicrm.security.SecurityAuditService securityAudit;
    // 【2026-11-09 类型级默认权限】建夹门槛(公共根=admin∨类型默认create)+新夹继承默认ACL
    // 【2026-10-27 深度检查修复】shareFile 集团边界校验需要(同 CustomerService.addShares 范式)
    private final JdbcTemplate jdbc;
    // 【BIZ-007 2026-11-13】删除激励佐证联动: 反查引用记录→剔除槽位→重算学分(与 removeFile 同不变量)
    private final com.aigicrm.module.incentive.service.IncentiveService incentiveService;
    /** S5.3 分享语义独立 bean；本类保留公共委托，控制器/测试调用方不变。 */
    private final FileShareService fileShareService;
    /** S5.3 文件夹写侧独立 bean；本类保留公共委托。 */
    private final FileFolderService fileFolderService;
    /** S5.3 字节存储独立 bean；本类保留公共委托。 */
    private final FileStorageService fileStorageService;
    /** S5.3 缩略图生成/缓存独立 bean；本类保留公共委托。 */
    private final FileThumbnailService fileThumbnailService;

    /** 【2026-11-17 阶段三】视频 5 秒预览：ffmpeg 路径/开关（默认开，失败回退 503，不泄露原文件）。 */
    @Value("${file.video-preview-enabled:true}")
    private boolean videoPreviewEnabled;
    @Value("${file.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** 【2026-11-17 阶段三】统一回收站台账；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.module.system.service.RecycleBinService recycleBinService;

    /**
     * 【性能 file-3 2026-10-18】激励可见 fileId 集合缓存：key=userId:currentGroupId。
     * 只存"他人 shared=1 记录的五个槽位并集"，失败不写；条目含指纹+过期时间，见
     * {@link #incentiveVisibleFileIds(Long)} 的安全说明。
     */
    private final Map<String, IncentiveVisibilityCache> incentiveVisibilityCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 可替换的单调时钟（单测控制 TTL）；生产使用 System.nanoTime */
    private volatile java.util.function.LongSupplier incentiveCacheClock = System::nanoTime;

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
                .collect(Collectors.toList());

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
        Set<Long> visibleUploaders = folderPermissionService.fabushiVisibleUploaderIds(userId);
        if (visibleUploaders != null) {
            if (visibleUploaders.isEmpty()) return false;
            wrapper.in(BizFile::getUploaderId, visibleUploaders);
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

    // ====== 上传/字节存储（S5.3 语义已移到 FileStorageService，本类保留公共委托） ======

    public BizFile upload(MultipartFile file, Long folderId, String scope) {
        return fileStorageService.upload(file, folderId, scope);
    }

    /** 【2026-11-17 阶段三】分片上传完成回读/回填 md5。 */
    public BizFile findById(Long id) {
        return id == null ? null : fileMapper.selectById(id);
    }

    public void updateMd5(Long id, String md5) {
        if (id == null || md5 == null) return;
        fileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFile>()
                .eq(BizFile::getId, id).set(BizFile::getMd5, md5));
    }

    /**
     * 【T2 2026-11-11 审计整改】占配额的文件范围判定: 与 upload 的 scope 白名单对称
     * (public/private/incentive)。删除归还/替换差额/回收站恢复共用, 保证占用与归还账面对称;
     * 历史脏 scope 行(白名单外)不还不扣, 防账面漂移。
     */
    public static boolean quotaBearing(String scope) {
        // 【法布施一期 2026-11-15】fabushi 计个人配额(用户已裁决"计入现有个人配额"), 与 scope 门禁对称
        return LIT_PUBLIC.equals(scope) || LIT_PRIVATE.equals(scope) || LIT_INCENTIVE.equals(scope)
                || LIT_FABUSHI.equals(scope);
    }

    // ====== 文件夹写侧（S5.3 语义已移到 FileFolderService，本类保留公共委托） ======

    public void createFolder(BizFileFolder folder) {
        fileFolderService.createFolder(folder);
    }

    public void renameFolder(Long folderId, String newName) {
        fileFolderService.renameFolder(folderId, newName);
    }

    public void deleteFolder(Long folderId) {
        fileFolderService.deleteFolder(folderId);
    }

    /**
     * 删除文件（进入回收站）。
     * 【审查 backend-5 2026-10-18】补事务 + 以逻辑删影响行数为唯一退款凭据：
     * 旧实现 selectById→deleteById(丢弃返回值)→无条件 adjustStorageUsed，两个并发删除
     * 都能读到同一行并各退一次配额(重复归还)。现在只有 deleted=0 行被本次请求真正删除
     * (affected==1)才归还；affected==0 = 重复/并发删除，幂等返回成功且不二次退款。
     */
    private void assertCanDeleteFile(Long fileId, BizFile file, Long userId, String scope) {
        if (file.getFolderId() != null) {
            if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_DELETE, userId)) {
                throw new BusinessException(403, LIT_X);
            }
        } else if (LIT_PUBLIC.equals(scope)) {
            if (!SecurityUtils.hasAuthority("file:delete")) {
                throw new BusinessException(403, "删除公共文件需要 file:delete 权限");
            }
        } else if (LIT_PRIVATE.equals(scope) || LIT_INCENTIVE.equals(scope)) {
            if (!userId.equals(file.getUploaderId())) {
                throw new BusinessException(403, "无权删除该文件");
            }
        } else if (LIT_FABUSHI.equals(scope)) {
            if (!folderPermissionService.canDeleteFabushiFile(file, userId)) {
                throw new BusinessException(403, "删除法布殊文件需要 fabushi:file:delete 权限且在上传人数据范围内");
            }
        } else {
            throw new BusinessException(403, "无权删除该文件");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteFile(Long fileId) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return;

        Long userId = SecurityUtils.getCurrentUserId();
        String scope = file.getFileScope();

        // 【安全修复 FEAT-20260804-FILE-AUTHZ】此前只对 public/private 两种 scope 做归属校验，
        // incentive 等其它 scope 完全未校验，导致任何拥有 file:delete 权限的用户都能删除他人的
        // 激励文件（IDOR）。现在改为白名单放行、默认拒绝，且新增 incentive scope 的归属校验。
        // 【文件权限体系】夹内文件（folderId 非空）改按文件夹权限判定（管理员/本人/上级链/ACL delete），
        // 无文件夹文件沿用下方 scope 白名单归属校验。
        assertCanDeleteFile(fileId, file, userId, scope);

        // 【BIZ-007 2026-11-13 外部审计修复】删除激励佐证必须联动重算学分:
        // 反查引用该文件的激励记录 → 剔除槽位引用 → calcScore 重算(与记录侧 removeFile 同一套
        // 不变量与空壳守卫)。守卫抛 400 时本事务回滚、文件保留——修前"佐证已删、分数还在"。
        if (LIT_INCENTIVE.equals(scope)) {
            incentiveService.detachFileFromRecords(fileId);
        }

        // 【文件回收站 2026-10-18 用户裁决】删除不再物理清除字节: 文件进入回收站,
        // 由保留期任务(FileRecycleCleanupTask)到期或管理员手动清空时再调 deleteStoredContent。
        // 逻辑删除 + 配额归还保持原语义(私有文件删除即归还配额, 恢复时重新占用)。

        // 逻辑删除(【V2026.11.14.1】先填删除人/时间——"我的回收站"归属口径, deleteById 只翻标志)
        fileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFile>()
                .eq(BizFile::getId, fileId)
                .set(BizFile::getDeletedAt, LocalDateTime.now())
                .set(BizFile::getDeletedBy, userId));
        int affected = fileMapper.deleteById(fileId);
        if (affected != 1) {
            // 并发/重复删除: 台账已不在活跃态, 不得二次归还配额; 幂等返回成功(前端不报错)
            return;
        }
        if (recycleBinService != null) {
            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("id", fileId);
            snap.put("fileName", file.getFileName());
            snap.put("fileSize", file.getFileSize());
            snap.put("fileScope", file.getFileScope());
            snap.put("uploaderId", file.getUploaderId());
            snap.put("folderId", file.getFolderId());
            recycleBinService.record("file", fileId, snap, "用户删除", "user");
        }
        // 【2026-11-10 拍板③】删除审计(高敏动作, 失败不阻断)
        try { securityAudit.permissionChange("file_delete",
                "file:" + fileId + " name:" + file.getFileName() + " scope:" + file.getFileScope()
                        + " by:" + SecurityUtils.getCurrentUsername()); }
        catch (Exception e) { log.warn("权限审计留痕失败(不阻断业务) file_delete fileId={}", fileId, e); }
        // 【性能 file-3】删除后清掉该文件所有版本的缩略图磁盘缓存（逻辑删只保留原图供回收站）
        evictThumbnailCache(fileId);

        // 【P2 2026-10-09 配额并发修复】归还改数据库端原子增减, 消除读改写丢失更新
        // 【T2 2026-11-11】归还覆盖全 scope(与扣减对称); 历史脏 scope 行不还
        if (quotaBearing(file.getFileScope()) && file.getFileSize() != null && file.getUploaderId() != null) {
            userMapper.adjustStorageUsed(file.getUploaderId(), -file.getFileSize());
        }
    }

    /**
     * 【2026-11-06 用户令"加一个可以修改文件名称的"】文件重命名: 只改 file_name 展示名,
     * oss_key/oss_url/file_type/version 不动——物理对象与下载路由零变化。
     * 【2026-11-09 文件权限 v2】动作细分: 改名走独立动作 rename(全局码仍 file:edit 伞),
     * 判定链=管理员/本人/上级链/ACL/类型授权(FolderPermissionService.checkFile P_RENAME,
     * 存量 ACL 的 edit 授权由判定层读时展开, 老授权继续有效)。
     * 校验: 去首尾空格非空、≤200 字符(与 biz_file.file_name 同宽); 不做扩展名强约束
     * (类型字段沿用上传时白名单结果, 改名不重算类型)。
     */
    public void renameFile(Long fileId, String fileName) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        if (LIT_FABUSHI.equals(file.getFileScope())) {
            throw new BusinessException(403, "法布施文件只支持查看/下载/删除，不支持改名");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_RENAME, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty()) throw new BusinessException("文件名不能为空");
        if (name.length() > 200) throw new BusinessException("文件名过长（不超过 200 字符）");
        // 【2026-11-08 用户令"改名不更改格式"】扩展名铁律守卫: 无论用户输入是否带/换扩展名,
        // 一律剥掉输入中的扩展名后拼回原扩展名——photo.jpg → 输入"photo2"/"photo2.png"均得 photo2.jpg。
        // 原名无扩展名则保持无扩展; 主名被剥空(只输了个扩展名)按"文件名不能为空"拒绝。
        name = keepOriginalExtension(name, file.getFileName());
        if (name.equals(file.getFileName())) return;
        BizFile patch = new BizFile();
        patch.setId(fileId);
        patch.setFileName(name);
        fileMapper.updateById(patch);
    }

    /** 改名保格式: 剥掉输入自带的后缀, 拼回原名扩展名——复用本类既有 extOf(:1231, 返回无点小写/null) */
    private static String keepOriginalExtension(String input, String originalName) {
        String originalExt = FileNames.extOf(originalName); // null=原名无扩展名
        String inputExt = FileNames.extOf(input);
        String main = input;
        if (inputExt != null) {
            // 输入带了后缀(与原一致或想换格式): 一律剥掉, 稍后统一拼回原扩展名
            main = input.substring(0, input.length() - inputExt.length() - 1);
        }
        if (main.isBlank()) throw new BusinessException("文件名不能为空");
        return originalExt == null ? main : main + "." + originalExt;
    }

    /**
     * 【文件权限体系 Task 6】覆盖替换：新文件先存后删旧（避免先删旧后存储失败导致文件丢失）。
     * 更新 oss_key/oss_url/file_size/file_type/version+1/replace_by/replaced_at；
     * 私人文件按差额调整配额（storageUsed = 原已用 - 旧size + 新size，最小 0）。
     * 【2026-11-09 文件权限 v2】动作细分: 替换走独立动作 replace(从 edit 伞拆出, 全局码仍 file:edit),
     * 判定链见 FolderPermissionService.checkFile(存量 ACL edit 授权读时展开继续有效)。
     * 【A7 2026-10-20 审计·事务化】旧实现 updateById→删旧文件→调配额三步无事务: 配额调整失败时
     * 接口报"替换失败"但台账已提交、旧文件可能已物理删除, 用户重试会再次覆盖并少扣配额。
     * 现口径: ①方法加 @Transactional, 台账更新与配额差额同事务提交/回滚;
     * ②旧文件物理删除挪到 afterCommit——回滚时台账仍指向旧 key, 事务内先删旧文件会造成
     * "返回失败但文件已丢"(不可逆); deleteStorage 自身吞异常(:540-555), 只能作 best-effort;
     * ③新媒体(uploadStorage 在事务前段已落盘)在失败路径上 best-effort 清理, 防止回滚后留孤儿。
     */
    @org.springframework.transaction.annotation.Transactional
    public BizFile replaceFile(Long fileId, MultipartFile newFile) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        if (LIT_FABUSHI.equals(file.getFileScope())) {
            throw new BusinessException(403, "法布施文件只支持查看/下载/删除，不支持替换");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_REPLACE, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        // 【决策5 2026-10-18 用户裁决·佐证锁定】激励计分佐证禁替换:佐证文件(安装前三槽/跟踪槽/旧单槽)
        // 是计分依据(安装前三槽齐全即得分),同 ID 换内容会无声替换计分证据(先传A得分再换B)。
        // 需更新佐证走激励模块"删除后重传"(删除触发重算,分数随新证据重出)。记录表为百级量,全量扫可接受。
        assertNotIncentiveEvidence(fileId);
        String scope = file.getFileScope() != null ? file.getFileScope() : LIT_PRIVATE;
        String newKey = null;
        try {
            // 先存新文件（沿用原文件的 scope/uploaderId 目录，保证 key 下载路由语义一致）
            // 【GPT三轮 FILE-P1-008】无扩展名拒绝后, replace 的 null 文件名场景改用旧文件扩展兜底
            // (类型继承自已过白名单的旧文件, 治理语义不破), 保持 M-2② 的 ossUrl 更新行为可测
            newKey = fileStorageService.uploadStorage(newFile, scope, file.getUploaderId(), FileNames.extOf(file.getFileName()));
            fileStorageService.scanStored(newKey);
            String oldKey = file.getOssKey();
            long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
            // 【V2026.11.14.1 版本历史】主行覆盖前先快照旧版本(旧 key/type/size/version):
            // 归档行与主行更新同事务, 台账与归档原子一致; 旧字节不再物理删除(下方 deleteStorage 拔除)。
            BizFileVersion archived = new BizFileVersion();
            archived.setTenantId(1L);
            archived.setFileId(fileId);
            archived.setVersionNo(file.getVersion() == null ? 1 : file.getVersion());
            archived.setOssKey(oldKey);
            archived.setOssUrl(fileStorageService.fileUrl(oldKey));
            archived.setFileSize(oldSize);
            archived.setFileType(file.getFileType());
            archived.setFileName(file.getFileName());
            archived.setUploadedBy(userId);
            fileVersionMapper.insert(archived);
            // ossUrl 与 upload 的 URL 方案一致：本地走 key 下载路由，OSS 走桶地址（保证替换后两种模式均可下载）
            String newOssUrl = fileStorageService.fileUrl(newKey);
            file.setOssKey(newKey);
            // 【安全修复】ossUrl 无条件更新为新 key 的 URL：旧对象已随 deleteStorage 删除，
            // 保留旧 URL 会指向已删除文件（M-2②：originalFilename==null 时同样更新）
            file.setOssUrl(newOssUrl);
            file.setFileSize(newFile.getSize());
            file.setFileType(newFile.getContentType());
            file.setVersion((file.getVersion() == null ? 0 : file.getVersion()) + 1);
            file.setReplaceBy(userId);
            file.setReplacedAt(LocalDateTime.now());
            fileMapper.updateById(file);
            // 【V2026.11.14.3 P1】归档后按上限裁剪最旧版本(默认 20, 配置可调)
            pruneVersionsIfNeeded(fileId);
            // 【性能 file-3】版本/ossKey 已变化，旧版本缩略图不再命中新 key；主动清掉避免磁盘残留
            // (缓存清理幂等: 回滚场景只是多一次 miss, 无损)
            evictThumbnailCache(fileId);
            // 【P2 2026-10-09 配额并发修复】差额调整改数据库端原子(GREATEST 守护最小 0)——
            // 【A7】与 updateById 同事务: 任一失败整体回滚, 配额与台账不再漂移
            // 【T2 2026-11-11】差额调整覆盖全 scope(与扣减对称)
            if (quotaBearing(scope) && file.getUploaderId() != null) {
                userMapper.adjustStorageUsed(file.getUploaderId(), newFile.getSize() - oldSize);
            }
            // 【V2026.11.14.1 版本历史】旧字节不再物理删除——已归档 biz_file_version, 供版本下载/回滚。
            // 原 afterCommit deleteStorage(oldKey) 拔除; 旧对象生命周期归回收站保留期任务管理。
            return file;
        } catch (BusinessException e) {
            cleanupOrphanMedia(newKey);
            throw e;
        } catch (Exception e) {
            cleanupOrphanMedia(newKey);
            throw new BusinessException("文件替换失败: " + e.getMessage());
        }
    }

    /**
     * 【A7】把动作注册到当前事务的 afterCommit; 无活动事务同步(直调/单测环境)时立即执行。
     * 只在提交后执行——afterCompletion(STATUS_ROLLED_BACK) 不会触发本回调, 回滚时旧文件保持原样。
     */
    private void runAfterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    /**
     * 【A7】替换失败补偿: uploadStorage 已落盘的新媒体在事务回滚后成为孤儿(台账仍指向旧 key),
     * best-effort 物理清理; deleteStorage 自身吞异常, 清理失败仅留孤儿文件(无数据正确性影响)。
     * newKey 为 null 表示尚未写入新媒体(权限/佐证/白名单拦截), 无需清理。
     */
    private void cleanupOrphanMedia(String newKey) {
        if (newKey == null || newKey.isEmpty()) return;
        try {
            fileStorageService.deleteStorage(newKey);
        } catch (Exception e) {
            log.warn("替换失败后的新媒体清理异常 fileKey={}", newKey, e);
        }
    }

    /** 【决策5 2026-10-18 用户裁决·佐证锁定】fileId 被任一激励记录引用(五槽任一)即为计分佐证,禁替换;
     *  槽解析与上方共享激励文件的五槽读取同源(getFileIds/Pre/Mid/Post/Track) */
    private void assertNotIncentiveEvidence(Long fileId) {
        if (isIncentiveEvidence(fileId)) {
            throw new BusinessException("该文件是激励计分佐证，不能替换；请在激励模块删除后重新上传（将重新计分）");
        }
    }

    /** 【回收站 2026-10-18】文件是否被任一激励记录五槽引用(供彻底删除守卫复用, 与替换守卫同源) */
    public boolean isIncentiveEvidence(Long fileId) {
        for (BizIncentiveRecord r : incentiveRecordMapper.selectList(new LambdaQueryWrapper<>())) {
            if (r.getFileIds().contains(fileId) || r.getFilePreIds().contains(fileId)
                    || r.getFileMidIds().contains(fileId) || r.getFilePostIds().contains(fileId)
                    || r.getFileTrackIds().contains(fileId)) {
                return true;
            }
        }
        return false;
    }

    // ====== 字节存储（S5.3 语义已移到 FileStorageService，本类保留公共委托） ======

    public Path getLocalFilePath(String fileKey) {
        return fileStorageService.getLocalFilePath(fileKey);
    }

    public void deleteStoredContent(String fileKey) {
        fileStorageService.deleteStoredContent(fileKey);
    }

    public void writeStoredContent(String fileKey, String fileName, String contentType,
                                   jakarta.servlet.http.HttpServletResponse response) {
        fileStorageService.writeStoredContent(fileKey, fileName, contentType, response);
    }

    void writeStoredContentTo(String fileKey, java.io.OutputStream out) throws java.io.IOException {
        fileStorageService.writeStoredContentTo(fileKey, out);
    }

    // ==================== 【V2026.11.14.1 版本历史】(用户拍板 2026-11-14) ====================

    /** 【V2026.11.14.3 P1】版本上限裁剪: 保留最新 max-per-file 条, 超出旧版删内容+行(引用计数防误删)。
     *  止住"无限替换=无限磁盘"; 版本计入用户配额为第二版产品决策(本版不改配额口径)。
     *
     *  【本次修复 2026-11-14】原实现有两个数据风险:
     *  ① 在 replace/rollback 事务内直接 deleteStoredContent: 后续配额更新失败回滚时,
     *     版本行回来了但字节已被删, 历史版本变成坏链接; 改为"事务内只删行, afterCommit 删字节"。
     *  ② key 等于主行 key / 被同文件其他版本引用时直接 continue, 行不删——max 上限被绕过,
     *     回滚产生的共享 key 版本会永久堆积; 改为"行始终删, 字节按引用计数决定是否删"。
     */
    private void pruneVersionsIfNeeded(Long fileId) {
        int max = resolveIntConfig("file.version.max-per-file", 20);
        if (max <= 0) return; // 0/负 = 不限制
        List<BizFileVersion> all = fileVersionMapper.listByFileId(fileId); // 新→旧
        if (all.size() <= max) return;
        BizFile main = fileMapper.selectById(fileId);
        String mainKey = main != null ? main.getOssKey() : null;
        for (int i = max; i < all.size(); i++) {
            BizFileVersion v = all.get(i);
            String key = v.getOssKey();
            boolean contentShared = key == null || key.isBlank()
                    || key.equals(mainKey)
                    || versionContentReferencedElsewhere(key, fileId, v.getId());
            // 行必须删: 否则共享 key 的旧版本会让 max-per-file 永远降不下来
            fileVersionMapper.deleteById(v.getId());
            if (!contentShared) deleteStoredContentAfterCommit(key);
        }
    }

    /** 版本字节是否仍被"当前主行之外"的活跃文件/其他版本引用(含同文件其他版本、其他文件版本)。 */
    private boolean versionContentReferencedElsewhere(String key, Long fileId, Long excludeVersionId) {
        Long active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file WHERE oss_key = ? AND deleted = 0 AND id <> ?",
                Long.class, key, fileId);
        if (active != null && active > 0) return true;
        Long otherVersion = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file_version WHERE oss_key = ? AND id <> ?",
                Long.class, key, excludeVersionId);
        return otherVersion != null && otherVersion > 0;
    }

    /** 事务内登记 afterCommit 删除字节; 无事务(直调/单测)时立即删除。 */
    private void deleteStoredContentAfterCommit(String key) {
        if (key == null || key.isBlank()) return;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deleteStoredContent(key);
                        }
                    });
        } else {
            deleteStoredContent(key);
        }
    }

    private int resolveIntConfig(String key, int def) {
        try {
            String v = configService.getValue(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 版本列表行(对外 DTO, 【V2026.11.14.3 P2】不暴露 ossKey/ossUrl——存储路由面不外泄) */
    public record VersionRow(Long id, Integer versionNo, Long fileSize, String fileType,
                             String fileName, Long uploadedBy, LocalDateTime createdAt) {
        static VersionRow of(BizFileVersion v) {
            return new VersionRow(v.getId(), v.getVersionNo(), v.getFileSize(), v.getFileType(),
                    v.getFileName(), v.getUploadedBy(), v.getCreatedAt());
        }
    }

    /** 版本列表(新→旧)。查看权限=checkFile view; 当前主行不在列表内(它就是"现行版") */
    public List<VersionRow> listVersions(Long fileId) {
        if (fileMapper.selectById(fileId) == null) throw new BusinessException(LIT_X_2);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_VIEW, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        return fileVersionMapper.listByFileId(fileId).stream().map(VersionRow::of).toList();
    }

    /** 版本内容下载(字节来自归档行, 与回收站详情下载同一存储通道) */
    public void downloadVersion(Long fileId, Integer versionNo, jakarta.servlet.http.HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_DOWNLOAD, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        BizFileVersion v = fileVersionMapper.byVersionNo(fileId, versionNo);
        if (v == null) throw new BusinessException("版本不存在");
        if (!contentExists(v.getOssKey())) throw new BusinessException("该版本内容已过保留期清除");
        writeStoredContent(v.getOssKey(),
                v.getFileName() != null ? v.getFileName() : "version-" + versionNo, v.getFileType(), response);
    }

    /**
     * 回滚到指定版本(用户拍板"文件版本历史加上"的回滚面): 当前主行内容**先归档**(回滚前的样子不丢),
     * 再把目标版本内容写回主行(version+1, 配额差额原子调整)。佐证文件禁回滚(与 replace 同一锁定裁决)。
     */
    @org.springframework.transaction.annotation.Transactional
    public BizFile rollbackToVersion(Long fileId, Integer versionNo) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.canRollbackVersion(file, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        assertNotIncentiveEvidence(fileId);
        BizFileVersion v = fileVersionMapper.byVersionNo(fileId, versionNo);
        if (v == null) throw new BusinessException("版本不存在");
        if (!contentExists(v.getOssKey())) throw new BusinessException("该版本内容已过保留期清除，无法回滚");
        int curVersion = file.getVersion() == null ? 1 : file.getVersion();
        // ① 回滚前的现行内容归档(数据不丢; 与 replace 归档同构)
        BizFileVersion cur = new BizFileVersion();
        cur.setTenantId(1L);
        cur.setFileId(fileId);
        cur.setVersionNo(curVersion);
        cur.setOssKey(file.getOssKey());
        cur.setOssUrl(file.getOssUrl());
        cur.setFileSize(file.getFileSize());
        cur.setFileType(file.getFileType());
        cur.setFileName(file.getFileName());
        cur.setUploadedBy(userId);
        fileVersionMapper.insert(cur);
        // ② 目标版本写回主行(存储对象沿用归档行的 key——它自归档起一直存活)
        long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
        file.setOssKey(v.getOssKey());
        file.setOssUrl(v.getOssUrl());
        file.setFileSize(v.getFileSize());
        file.setFileType(v.getFileType());
        file.setVersion(curVersion + 1);
        file.setReplaceBy(userId);
        file.setReplacedAt(LocalDateTime.now());
        fileMapper.updateById(file);
        evictThumbnailCache(fileId);
        // 【V2026.11.14.3 P1】回滚归档后同样按上限裁剪
        pruneVersionsIfNeeded(fileId);
        if (quotaBearing(file.getFileScope()) && file.getUploaderId() != null) {
            long newSize = v.getFileSize() != null ? v.getFileSize() : 0L;
            userMapper.adjustStorageUsed(file.getUploaderId(), newSize - oldSize);
        }
        return file;
    }

    // ==================== 【V2026.11.14.1 批量操作】(用户拍板 2026-11-14) ====================

    /** 【V2026.11.14.3 P2】zip 条目名清洗: 路径穿越(..)/分隔符/控制字符 → '_'; 空名回退 file-{id} */
    private String sanitizeZipEntryName(String name, Long fallbackId) {
        String n = name == null || name.isBlank() ? ("file-" + fallbackId) : name;
        n = n.replaceAll("\\p{Cntrl}", "_").replace("..", "_").replace("/", "_").replace("\\", "_").trim();
        return n.isBlank() ? ("file-" + fallbackId) : n;
    }

    /** 【V2026.11.14.3 P2】批量上限(防误操作与超大请求), 超出直接 400；批量服务共用 */
    static void requireBatchLimit(List<Long> ids) {
        if (ids != null && ids.size() > 100) {
            throw new BusinessException("批量操作一次最多 100 项，请分批进行");
        }
    }

    /**
     * 批量下载(zip 流式打包): 逐个过 checkFile download 校验+内容存在性, 无权限/缺内容的静默剔除;
     * 同名文件 zip 内自动加序号后缀。全被剔除 → 400。
     */
    public void batchDownload(List<Long> fileIds, jakarta.servlet.http.HttpServletResponse response) {
        requireBatchLimit(fileIds);
        List<BizFile> allowed = collectDownloadable(fileIds, SecurityUtils.getCurrentUserId());
        if (allowed.isEmpty()) throw new BusinessException("无可下载文件（不存在或无下载权限）");
        try {
            writeZip(allowed, response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("批量下载打包失败: {}", e.getMessage());
            throw new BusinessException("批量下载失败，请重试");
        }
    }

    private List<BizFile> collectDownloadable(List<Long> fileIds, Long userId) {
        List<BizFile> allowed = new ArrayList<>();
        for (Long id : fileIds == null ? List.<Long>of() : fileIds) {
            BizFile f = fileMapper.selectById(id);
            if (f == null) continue;
            if (!folderPermissionService.checkFile(id, FolderPermissionService.P_DOWNLOAD, userId)) continue;
            if (!contentExists(f.getOssKey())) continue;
            allowed.add(f);
        }
        return allowed;
    }

    private void writeZip(List<BizFile> allowed, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("application/zip");
        String zipName = "files-" + java.time.LocalDate.now() + ".zip";
        String encoded = java.net.URLEncoder.encode(zipName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        java.util.Set<String> used = new java.util.HashSet<>();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            for (BizFile f : allowed) {
                // 【V2026.11.14.3 P2】条目名清洗: 路径穿越(..)/分隔符/控制字符 → '_'(zip slip 防线)
                String unique = uniqueEntryName(sanitizeZipEntryName(f.getFileName(), f.getId()), used);
                zos.putNextEntry(new java.util.zip.ZipEntry(unique));
                writeStoredContentTo(f.getOssKey(), zos);
                zos.closeEntry();
            }
        }
    }

    private String uniqueEntryName(String base, java.util.Set<String> used) {
        String unique = base;
        int i = 1;
        while (!used.add(unique)) {
            unique = base.replaceFirst("(\\.[^.]+)?$",
                    base.matches(".*\\.[^.]+") ? "-" + i + "$1" : "-" + i);
            i++;
        }
        return unique;
    }

    /**
     * 【回收站 2026-10-18】文件内容是否仍在存储中。
     * 删除文件时 deleteStorage 会物理清除字节, 仅保留元数据软删行; 只有内容仍存在
     * (删除失败残留/OSS 版本保留等) 时才允许恢复元数据, 否则恢复出来也是坏链接。
     * fail-closed: 检查异常一律按不可恢复处理。
     */
    public boolean contentExists(String fileKey) {
        try {
            return fileStorageService.contentExists(fileKey);
        } catch (Exception e) {
            log.warn("文件内容存在性检查失败 fileKey={}", fileKey, e);
            return false;
        }
    }

    /** 安全内联判定: 仅纯图片(不含 SVG——可内嵌脚本执行, 存存储型XSS)、视频与 PDF 可 inline, 其余一律 attachment。
     *  2026-08-10 安全修复: 原先 svg 也在 inline 名单, 配合任意上传可被匿名/分享 URL 直接执行窃取 localStorage JWT。
     *  2026-10-17 PDF 加入 inline: 浏览器内建 PDF 阅读器需 inline+Range 才能边下边看(前端阅读器直链流式,
     *  23.5MB PDF 首屏从"全量下载完"降到秒级); PDF 阅读器在浏览器内沙箱渲染, 无 SVG/HTML 的脚本执行面。
     *  SVG/HTML 等活性内容仍强制 attachment。 */
    private static final java.util.regex.Pattern SAFE_INLINE_EXT =
            java.util.regex.Pattern.compile("(?i).*\\.(jpg|jpeg|png|gif|webp|bmp|mp4|webm|pdf)$");

    public static String dispositionFor(String filename) {
        return filename != null && SAFE_INLINE_EXT.matcher(filename).matches() ? "inline" : "attachment";
    }

    /** 【2026-11-17 阶段三】视频 5 秒预览（ffmpeg 转码缓存）；非视频返回 415。 */
    public void writeVideoPreview(Long fileId, jakarta.servlet.http.HttpServletResponse response) {
        BizFile file = fileMapper.selectById(fileId);
        Long userId = SecurityUtils.getCurrentUserId();
        Path source = resolveVideoSource(file, userId, response);
        if (source == null) return;
        try {
            Path cached = generateCachedPreview(source, file);
            if (cached == null) { response.setStatus(503); return; }
            response.setStatus(200);
            response.setContentType("video/mp4");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            response.setContentLengthLong(Files.size(cached));
            try (InputStream in = Files.newInputStream(cached)) {
                in.transferTo(response.getOutputStream());
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("视频预览生成失败 fileId={}: {}", fileId, e.getMessage());
            response.setStatus(503);
        }
    }

    private Path resolveVideoSource(BizFile file, Long userId, jakarta.servlet.http.HttpServletResponse response) {
        if (file == null) { response.setStatus(404); return null; }
        if (!canDownloadFile(file, userId)) { response.setStatus(403); return null; }
        String name = file.getFileName() == null ? "" : file.getFileName().toLowerCase(java.util.Locale.ROOT);
        if (!name.matches(".*\\.(mp4|webm|mov|avi|mkv|m4v)$")) { response.setStatus(415); return null; }
        if (!videoPreviewEnabled) { response.setStatus(503); return null; }
        if (file.getOssKey() == null) { response.setStatus(404); return null; }
        Path source = fileStorageService.getLocalFilePath(file.getOssKey());
        if (Files.isRegularFile(source)) return source;
        if (file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {
            response.setStatus(302);
            response.setHeader(HttpHeaders.LOCATION, file.getOssUrl());
            return null;
        }
        response.setStatus(404);
        return null;
    }

    private Path generateCachedPreview(Path source, BizFile file) throws java.io.IOException, InterruptedException {
        Path cacheRoot = fileThumbnailService.thumbnailCacheRoot();
        Files.createDirectories(cacheRoot);
        String key = "preview-" + file.getId() + "-" + (file.getVersion() == null ? 1 : file.getVersion())
                + "-" + Integer.toHexString(Objects.hash(file.getOssKey(), file.getFileSize(),
                Files.getLastModifiedTime(source).toMillis())) + ".mp4";
        Path cached = cacheRoot.resolve(key);
        if (Files.isRegularFile(cached)) return cached;
        Path tmp = Files.createTempFile(cacheRoot, "preview-", ".tmp");
        Process p = new ProcessBuilder(ffmpegPath, "-y", "-ss", "0", "-i", source.toString(),
                "-t", "5", "-an", "-vf", "scale='min(1280,iw)':-2",
                "-c:v", "libx264", "-preset", "veryfast", "-movflags", "+faststart",
                tmp.toString()).redirectErrorStream(true).start();
        boolean done = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) p.destroyForcibly();
        if (!done || p.exitValue() != 0 || Files.size(tmp) == 0) {
            Files.deleteIfExists(tmp);
            return null;
        }
        Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
        return cached;
    }

    public void downloadById(Long fileId, String range, boolean thumbnail, jakarta.servlet.http.HttpServletResponse response) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) {
            response.setStatus(404);
            return;
        }

        // 归属校验：只有上传者、被分享者、管理员可以下载
        Long userId = SecurityUtils.getCurrentUserId();
        if (!canDownloadFile(file, userId)) {
            response.setStatus(403);
            return;
        }
        try {
            Path filePath = fileStorageService.getLocalFilePath(file.getOssKey());
            // 【缩略图 2026-08-09】列表预览场景：图片返回 ≤300px 缩略图，避免激励查询等页首屏全尺寸下载
            // 【性能 file-3 2026-10-18】授权（上方 canDownloadFile）每次请求都先执行；命中字节缓存后不再读原图
            // 非图片/读取失败 → 回退完整下载
            if (thumbnail && writeThumbnail(file, filePath, response)) return;
            if (serveLocalFile(file, filePath, range, response)) return;
            if (redirectToOss(file, response)) return;
        } catch (Exception e) {
            // S4: 下载写响应异常不得静默吞成 404——记录日志便于排查
            log.error("文件下载失败 fileId={}", fileId, e);
        }
        response.setStatus(404);
    }

    /** 本地文件存在则写完整响应并返回 true；不存在返回 false（调用方回退 OSS 302）。 */
    private boolean serveLocalFile(BizFile file, Path filePath, String range,
                                   jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) return false;
        String filename = file.getFileName();
        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mt = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
        String disp = dispositionFor(filename);

        long fileSize = resource.contentLength();
        RangeSpan span = parseRange(range, fileSize);
        if (span.invalid()) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + fileSize);
            return true;
        }
        long length = span.end() - span.start() + 1;

        response.setStatus(span.ranged() ? 206 : 200);
        response.setHeader("Accept-Ranges", "bytes");
        if (span.ranged()) {
            response.setHeader("Content-Range", "bytes " + span.start() + "-" + span.end() + "/" + fileSize);
        }
        response.setContentType(mt.toString());
        response.setContentLengthLong(length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename*=UTF-8''" + encoded);

        streamResource(resource, span.start(), length, response);
        return true;
    }

    /** 解析 Range 头；非 bytes= 视为整段。invalid=true 表示越界（应回 416）。 */
    private static RangeSpan parseRange(String range, long fileSize) {
        long start = 0;
        long end = fileSize - 1;
        boolean ranged = range != null && range.startsWith("bytes=");
        if (!ranged) return new RangeSpan(false, start, end, false);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("bytes=(\\d*)-(\\d*)").matcher(range);
        if (m.find()) {
            if (!m.group(1).isEmpty()) start = Long.parseLong(m.group(1));
            if (!m.group(2).isEmpty()) end = Math.min(Long.parseLong(m.group(2)), fileSize - 1);
        }
        boolean invalid = start > end || start >= fileSize;
        return new RangeSpan(true, start, end, invalid);
    }

    private static void streamResource(Resource resource, long start, long length,
                                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        try (java.io.InputStream is = resource.getInputStream()) {
            is.skipNBytes(start);
            java.io.OutputStream out = response.getOutputStream();
            byte[] buf = new byte[8192];
            long remaining = length;
            int n;
            while (remaining > 0 && (n = is.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    /** OSS 直链可 302 时写响应并返回 true；否则 false 由调用方落 404。 */
    private static boolean redirectToOss(BizFile file, jakarta.servlet.http.HttpServletResponse response) {
        // 【2026-10-27 深度检查修复】OSS 302 与 FileController 路由白名单同口径:
        // startsWith("http") 可被 httpx:// 绕过, 收紧为 https:// (OSS 地址均 https)
        if (file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {
            response.setStatus(302);
            response.setHeader(HttpHeaders.LOCATION, file.getOssUrl());
            return true;
        }
        return false;
    }

    /** Range 解析结果：ranged=客户端传了 bytes=；invalid=越界需 416。 */
    private record RangeSpan(boolean ranged, long start, long end, boolean invalid) {}

    // ====== 缩略图（S5.3 语义已移到 FileThumbnailService，本类保留调用点） ======

    private boolean writeThumbnail(BizFile file, Path filePath,
                                   jakarta.servlet.http.HttpServletResponse response) {
        return fileThumbnailService.writeThumbnail(file, filePath, response);
    }

    void evictThumbnailCache(Long fileId) {
        fileThumbnailService.evictThumbnailCache(fileId);
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
        return folders.stream()
                .filter(f -> Objects.equals(f.getParentId(), parentId))
                .peek(f -> f.setChildren(buildFolderTree(folders, f.getId())))
                .collect(Collectors.toList());
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
        long now = incentiveCacheClock.getAsLong();
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

    // ====== 文件分享（S5.3 语义已移到 FileShareService，本类保留公共委托） ======

    public void shareFile(Long fileId, List<Long> userIds) {
        fileShareService.shareFile(fileId, userIds);
    }

    public IPage<BizFile> pageSharedToMe(int current, int size, String keyword) {
        return fileShareService.pageSharedToMe(current, size, keyword);
    }

    public long countSharedToMe() {
        return fileShareService.countSharedToMe();
    }
}
