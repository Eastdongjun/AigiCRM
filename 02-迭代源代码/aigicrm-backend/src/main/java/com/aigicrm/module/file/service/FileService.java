package com.aigicrm.module.file.service;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.config.OssConfig;
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
import org.springframework.beans.factory.ObjectProvider;

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
    private static final String LIT_API_FILE_DOWNLOAD = "/api/file/download/";
    private static final String LIT_X_2 = "文件不存在";
    private static final String LIT_UNNAMED = "unnamed";

    /** 字节换算单位:1KB=1024B(文件大小格式化用) */
    private static final long BYTES_PER_KB = 1024L;

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

    /** 缩略图最长边（与原实现一致：列表预览 ≤300px） */
    private static final int THUMBNAIL_MAX_DIM = 300;
    /** 缩略图磁盘缓存条目上限，超过后按最旧 last-modified 清理 */
    private static final int THUMBNAIL_CACHE_MAX_ENTRIES = 2000;
    /** 每写入 N 个缩略图触发一次容量巡检（摊销目录遍历成本） */
    private static final int THUMBNAIL_CACHE_PRUNE_INTERVAL = 200;
    private static final String THUMBNAIL_CACHE_DIR_NAME = "aigicrm-thumb-cache";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileService.class);

    private final BizFileMapper fileMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileShareMapper fileShareMapper;
    /** 【V2026.11.14.1 版本历史】replace 归档/版本列表/回滚 */
    private final com.aigicrm.module.file.mapper.BizFileVersionMapper fileVersionMapper;
    /** 【V2026.11.14.3 P1】批量删除逐条事务(内部 this 调用绕过 @Transactional 代理, 须显式模板) */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    /** 【V2026.11.14.3 P1】版本上限/保留期配置读取 */
    private final com.aigicrm.module.system.service.SysConfigService configService;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final SysUserMapper userMapper;
    private final ObjectProvider<OSS> ossClientProvider;
    private final OssConfig ossConfig;
    private final FolderPermissionService folderPermissionService;
    // 【2026-11-10 拍板③】删除=高敏动作, 全程走审计
    private final com.aigicrm.security.SecurityAuditService securityAudit;
    // 【2026-11-09 类型级默认权限】建夹门槛(公共根=admin∨类型默认create)+新夹继承默认ACL
    // 【2026-10-27 深度检查修复】shareFile 集团边界校验需要(同 CustomerService.addShares 范式)
    private final JdbcTemplate jdbc;
    // 【BIZ-007 2026-11-13】删除激励佐证联动: 反查引用记录→剔除槽位→重算学分(与 removeFile 同不变量)
    private final com.aigicrm.module.incentive.service.IncentiveService incentiveService;

    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** 缩略图磁盘缓存目录；留空则使用 java.io.tmpdir/aigicrm-thumb-cache */
    @Value("${file.thumbnail-cache-dir:}")
    private String thumbnailCacheDir;

    /** 【2026-11-17 阶段三】视频 5 秒预览：ffmpeg 路径/开关（默认开，失败回退 503，不泄露原文件）。 */
    @Value("${file.video-preview-enabled:true}")
    private boolean videoPreviewEnabled;
    @Value("${file.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** 【2026-11-17 阶段三】统一回收站台账；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.module.system.service.RecycleBinService recycleBinService;

    /** 【2026-11-17 阶段三】上传病毒扫描钩子；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FileScanService fileScanService;

    /** 写缓存次数计数（用于摊销容量巡检，非业务状态） */
    private final java.util.concurrent.atomic.AtomicInteger thumbnailCacheOps = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 【性能 file-3 2026-10-18】激励可见 fileId 集合缓存：key=userId:currentGroupId。
     * 只存"他人 shared=1 记录的五个槽位并集"，失败不写；条目含指纹+过期时间，见
     * {@link #incentiveVisibleFileIds(Long)} 的安全说明。
     */
    private final Map<String, IncentiveVisibilityCache> incentiveVisibilityCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 可替换的单调时钟（单测控制 TTL）；生产使用 System.nanoTime */
    private volatile java.util.function.LongSupplier incentiveCacheClock = System::nanoTime;

    private boolean isLocal() {
        return "local".equalsIgnoreCase(storageType);
    }

    private OSS ossClient() {
        return ossClientProvider.getIfAvailable(() -> {
            throw new BusinessException("OSS 存储未配置");
        });
    }

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

    /**
     * 【2026-11-06 用户令"小程序和PC都需要全局检索"】文件关键字多字段全局匹配:
     * 文件名 ∨ 上传人姓名 ∨ 所在文件夹名——此前仅 LIKE 文件名, 与 MP 四项服务 tab 的多字段本地
     * 筛选口径分裂(10-26 登记, 用户感知为"搜不到"); 双端 /file/list 与 shared-to-me 同走本助手。
     * {0} 占位由 MyBatis 参数绑定, 无字符串拼接注入面; 数据范围过滤(可见文件夹/归属/分享集)
     * 在调用方先行生效, 此处只扩匹配面不扩可见面。
     */
    private void applyGlobalKeyword(LambdaQueryWrapper<BizFile> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        wrapper.and(w -> w.like(BizFile::getFileName, keyword)
                .or().apply("EXISTS (SELECT 1 FROM sys_user u WHERE u.id = uploader_id AND u.real_name LIKE CONCAT('%', {0}, '%'))", keyword)
                .or().apply("EXISTS (SELECT 1 FROM biz_file_folder f WHERE f.id = folder_id AND f.folder_name LIKE CONCAT('%', {0}, '%'))", keyword));
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
        if (LIT_INCENTIVE.equals(scope) && !applyIncentiveVisibility(wrapper, userId)) {
            return new Page<>(current, size, 0);
        }
        if (LIT_FABUSHI.equals(scope) && !applyFabushiVisibility(wrapper, userId)) {
            return new Page<>(current, size, 0);
        }
        if (!LIT_INCENTIVE.equals(scope) && !LIT_FABUSHI.equals(scope)
                && !applyFolderVisibility(wrapper, scope, folderId, userId)) {
            return new Page<>(current, size, 0);
        }
        if (keyword != null && !keyword.isEmpty()) {
            applyGlobalKeyword(wrapper, keyword);
        }
        wrapper.orderByDesc(BizFile::getCreatedAt);
        return decoratePage(fileMapper.selectPage(new Page<>(current, size), wrapper), userId);
    }

    /** public/private/incentive/fabushi 四类合法 scope。 */
    private static boolean isKnownScope(String scope) {
        return scope != null && (LIT_PUBLIC.equals(scope) || LIT_PRIVATE.equals(scope)
                || LIT_INCENTIVE.equals(scope) || LIT_FABUSHI.equals(scope));
    }

    /** 激励空间可见性过滤；返回 false 表示候选集必空（直接回空页）。 */
    private boolean applyIncentiveVisibility(LambdaQueryWrapper<BizFile> wrapper, Long userId) {
        // 【2026-11-01 审计修复】admin/持 incentive:view-all 全量旁路——与激励侧 applyShareVisibility
        // 同口径(原文件侧无旁路: 总监在 MP 四项服务tab可见的未分享激励文件, PC 文件中心不出现,
        // 且下载分支无旁路时会出现"MP 可见 PC 下载 403"的双端分叉)
        if (incentiveViewAllGranted()) {
            return true; // 全量可见, 不加可见性过滤
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
            return true;
        }
        java.util.List<Long> allow = new java.util.ArrayList<>(orgVisible);
        if (!allow.contains(userId)) allow.add(userId);
        if (sharedFileIds.isEmpty()) {
            wrapper.in(BizFile::getUploaderId, allow);
        } else {
            wrapper.and(w -> w.in(BizFile::getUploaderId, allow)
                    .or().in(BizFile::getId, sharedFileIds));
        }
        return true;
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

    /** scope 白名单 + 无夹公共上传门槛（S3776 拆分，语义与原内联校验一致）。 */
    private void validateUploadScope(String scope, Long folderId) {
        // 【安全加固】scope 白名单：允许 public/private/incentive/fabushi，未识别 scope 直接拒绝
        // （与 pageFiles/getFolderTree 的 scope 白名单对称，防止任意 scope 落盘后绕过查询过滤/占用存储）
        if (scope == null || !(LIT_PUBLIC.equals(scope) || LIT_PRIVATE.equals(scope) || LIT_INCENTIVE.equals(scope) || LIT_FABUSHI.equals(scope))) {
            throw new BusinessException(403, "非法的文件范围");
        }
        // 【2026-10-27 深度检查修复】无夹 public 上传补管理员门槛: 该路径落盘 public/{userId}/ 且
        // file_scope=public 后立即进入匿名无鉴权下载通道(/api/file/download/public/**), 与
        // "根级公共文件夹仅管理员可建"门槛对称——普通用户要发公共资源, 走公共文件夹(ACL 判定)。
        if (LIT_PUBLIC.equals(scope) && folderId == null
                && !SecurityUtils.hasAuthority("file:folder:manage")) {
            throw new BusinessException(403, "公共根目录上传需要 file:folder:manage 权限");
        }
    }

    /** 激励/法布施/文件夹上传权限校验（S3776 拆分，语义与原内联校验一致）。 */
    private void validateUploadPermissions(String scope, Long folderId, Long userId) {
        // 【T2 2026-11-11 审计整改】激励空间上传=激励计分动作: 与 IncentiveController /records
        // 同码门槛(incentive:add, 设计 §3.2 上传语义), 防无激励权限者借文件上传端点向激励空间
        // 无限落盘(此前仅 private 扣配额, incentive 是绕过面, 审计 P1-2)。
        if (LIT_INCENTIVE.equals(scope) && !SecurityUtils.hasAuthority("incentive:add")) {
            throw new BusinessException(403, LIT_X);
        }
        // 【法布施一期 2026-11-15】法布施空间上传=发布动作: 与 FabushiPostController /posts 同码门槛
        // (fabushi:create, 需求 G2 独立命名空间裁决——附件不进文件管理列表, 但计个人配额)。
        // 注意 scope 判定与 hasAuthority 必须拆行: 同行会让命令式码提取把裸 LIT_FABUSHI 串
        // 混进 binding-coverage 码集, 造成"码未绑定"假红(2026-11-15 实测)。
        boolean fabushiScope = LIT_FABUSHI.equals(scope);
        if (fabushiScope && !SecurityUtils.hasAuthority("fabushi:create")) {
            throw new BusinessException(403, LIT_X);
        }
        // 【文件权限体系】上传到文件夹需文件夹上传权限（管理员/本人/上级链/ACL upload）。
        // 【FILE-FABU-FOLDER-001】法布施系统夹例外: 无 ACL/无属主, 通道门槛已在 FileController.upload
        // 路由层判定(书籍=fabushi:book:manage; 分享=fabushi:create 上面同码门), 此处不重复走夹级判定。
        if (folderId != null && !isFabushiSystemFolder(folderId)
                && !folderPermissionService.check(folderId, FolderPermissionService.P_UPLOAD, userId)) {
            throw new BusinessException(403, LIT_X);
        }
    }

    /** 【FILE-FABU-FOLDER-001】folderId 是否法布施系统夹(folder_type='fabushi')。 */
    private boolean isFabushiSystemFolder(Long folderId) {
        if (folderId == null) return false;
        BizFileFolder f = folderMapper.selectById(folderId);
        return f != null && FabushiFolderService.FOLDER_TYPE.equals(f.getFolderType());
    }

    public BizFile upload(MultipartFile file, Long folderId, String scope) {

        Long userId = SecurityUtils.getCurrentUserId();
        validateUploadScope(scope, folderId);
        validateUploadPermissions(scope, folderId, userId);

        // 【P2 2026-10-09 配额并发修复】配额检查与扣减原子化: WHERE used+size<=quota 命中才扣,
        // 0 行=配额不足——并发上传在 DB 端串行判定, 消除"两请求同过内存检查"的超配额竞态。
        // 【T2 2026-11-11 审计整改】配额覆盖全部 scope(public/private/incentive): 激励佐证是
        // 存储大头(生产 2.5k+ 条), 仅 private 扣配额=激励空间无限上传; 存量已由
        // V2026.11.11.3 一次性回溯对账(全员 10GB 配额, 回溯峰值 943MB, 零超配)。
        if (userMapper.tryConsumeQuota(userId, file.getSize()) == 0) {
            SysUser user = userMapper.selectById(userId);
            throw new BusinessException("存储空间不足，当前已用: " +
                    formatSize(user != null ? user.getStorageUsed() : 0L) +
                    "，配额: " + formatSize(user != null ? user.getStorageQuota() : 0L));
        }

        String uploadedKey = null;
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = LIT_UNNAMED;
            }
            String fileKey = uploadStorage(file, scope, userId);
            uploadedKey = fileKey;
            // 【2026-11-17 阶段三】落盘后、台账前扫描；命中/扫描失败即删除新字节并由 catch 归还配额。
            scanStored(fileKey);
            String fileUrl;
            if (isLocal()) {
                fileUrl = LIT_API_FILE_DOWNLOAD + fileKey;
            } else {
                fileUrl = LIT_HTTPS + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + fileKey;
            }

            BizFile bizFile = new BizFile();
            bizFile.setFolderId(folderId);
            // 【2026-11-10 终版】归属组织快照(上传人当时组织节点, 转岗不漂移)
            SysUser uploader = userMapper.selectById(userId);
            bizFile.setOrgId(uploader != null ? java.util.Objects.requireNonNullElse(uploader.getOfficeId(), uploader.getDeptId()) : null);
            bizFile.setFileName(originalFilename);
            bizFile.setFileSize(file.getSize());
            int dotIdx = originalFilename.lastIndexOf('.');
            String fileType = dotIdx > 0 ? originalFilename.substring(dotIdx + 1).toUpperCase() : "";
            bizFile.setFileType(fileType);
            bizFile.setOssKey(fileKey);
            bizFile.setOssUrl(fileUrl);
            bizFile.setFileScope(scope);
            bizFile.setUploaderId(userId);
            fileMapper.insert(bizFile);

            return bizFile;
        } catch (Exception e) {
            // 【2026-11-17 阶段三】扫描/入库失败时清理已落盘新字节，避免病毒样本/孤儿文件留存。
            if (uploadedKey != null) deleteStorage(uploadedKey);
            // 【P2 2026-10-09】扣减成功后存储/入库失败 → 补偿归还(GREATEST 守护)
            // 【T2 2026-11-11】全 scope 扣减 → 补偿归还同步去 scope 条件
            userMapper.adjustStorageUsed(userId, -file.getSize());
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
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

    public void createFolder(BizFileFolder folder) {
        String type = folder.getFolderType();
        // 【安全修复 FEAT-20260804-FILE-AUTHZ】folderType 白名单校验，避免传入未识别类型
        // 绕过 checkFolderManagePermission 的默认拒绝逻辑（此前会被后续接口当成"未识别类型"处理）。
        if (!LIT_PUBLIC.equals(type) && !LIT_PRIVATE.equals(type)) {
            throw new BusinessException("不支持的文件夹类型");
        }
        // 【FILE-FABU-FOLDER-001】系统夹禁建子夹(folder_type='fabushi' 下不允许任何用户建夹)
        if (folder.getParentId() != null && isFabushiSystemFolder(folder.getParentId())) {
            throw new BusinessException(403, "法布施系统文件夹下不允许创建子文件夹");
        }
        // 【体检 H3 2026-10-12】parentId=0 与 null 同为根级（0 是 DDL 哨兵，buildFolderTree 以 0 为根）。
        // PC(CreateFolderDialog selectedFolderId||0) 与 MP(建夹恒传 0) 均传 0，此前 0 被当真实父夹
        // 做 P_UPLOAD 校验（selectById(0)=null→403），非 admin 建根夹恒败。归一化后走根级规则：
        // 私人根免门槛，公共根保留管理员门槛；insert 省略 null 字段落列默认 0，与存量根夹一致。
        if (folder.getParentId() != null && folder.getParentId() == 0L) {
            folder.setParentId(null);
        }
        // 【文件权限体系】创建子文件夹需父文件夹上传权限（管理员/本人/上级链/ACL upload），
        // public 类型带 parentId 同样按父夹 P_UPLOAD 判定（ACL 授权角色可在已授权公共夹下建子夹）；
        // 根级公共文件夹（无 parentId）保留管理员门槛。
        if (folder.getParentId() != null
                && !folderPermissionService.check(folder.getParentId(), FolderPermissionService.P_UPLOAD, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, LIT_X);
        }
        // 【2026-11-10 文件权限终版】建夹门槛=判定链 canCreateFolder(admin ∨ 例外授权含 FOLDER:create),
        // 公共/私人分列配置; 归属: owner=创建人(公共私人同, 治存量公共夹 owner 全 NULL 的结构缺失),
        // org_id=创建人当时组织快照(转岗不漂移)。
        Long creator = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.canCreateFolder(type, creator)) {
            throw new BusinessException("没有创建该类型文件夹的权限，请联系管理员授权");
        }
        folder.setOwnerId(creator);
        // 【拍板④】公共夹归属=创建人所辖最高组织(负责人建夹=给本组织开抽屉); 私人夹/非负责人=创建人组织
        Long leaderOrg = folderPermissionService.leaderOrgId(creator);
        SysUser creatorUser = userMapper.selectById(creator);
        if (LIT_PUBLIC.equals(type) && leaderOrg != null && leaderOrg > 0) {
            folder.setOrgId(leaderOrg);
        } else {
            folder.setOrgId(creatorUser != null ? java.util.Objects.requireNonNullElse(creatorUser.getOfficeId(), creatorUser.getDeptId()) : null);
        }
        folderMapper.insert(folder);
    }

    public void renameFolder(Long folderId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new BusinessException("文件夹名称不能为空");
        }
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【FILE-FABU-FOLDER-001】系统夹禁改名
        if (FabushiFolderService.FOLDER_TYPE.equals(folder.getFolderType())) {
            throw new BusinessException(403, "法布施系统文件夹不允许改名");
        }
        // 【文件权限 v2】文件夹改名走独立动作 rename(从 edit 伞拆出); 判定链 admin>本人>上级链>ACL>类型授权
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_RENAME, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, LIT_X);
        }
        folder.setFolderName(newName.trim());
        folderMapper.updateById(folder);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteFolder(Long folderId) {
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【FILE-FABU-FOLDER-001】系统夹禁删(书籍/分享心得出入口, 删除=两端失联)
        if (FabushiFolderService.FOLDER_TYPE.equals(folder.getFolderType())) {
            throw new BusinessException(403, "法布施系统文件夹不允许删除");
        }
        // 【文件权限体系】ACL/上级链/本人 删除权限（管理员恒通过），
        // 为操作唯一仲裁（已覆盖 admin>本人>上级链>ACL 优先级且 folder==null 已拒绝）
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_DELETE, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, LIT_X);
        }

        // 夹内有文件 → 拒绝删除
        long fileCount = fileMapper.selectCount(
                new LambdaQueryWrapper<BizFile>()
                        .eq(BizFile::getFolderId, folderId)
                        .eq(BizFile::getDeleted, 0));
        if (fileCount > 0) {
            throw new BusinessException("文件夹内还有文件，请先清空后再删除");
        }
        // 含子文件夹 → 拒绝删除
        long subCount = folderMapper.selectCount(
                new LambdaQueryWrapper<BizFileFolder>().eq(BizFileFolder::getParentId, folderId));
        if (subCount > 0) {
            throw new BusinessException("文件夹内还有子文件夹，请先清空后再删除");
        }

        // 【V2026.11.14.1 回收站补全】夹删除改软删(此前物理删, 误删无救): @TableLogic 令
        // deleteById=UPDATE deleted=1, MP 全查询自动过滤已删夹(树/列表/子夹计数零改动)。
        // ACL 与类型授权行**保留**(授权跟随夹生命周期——恢复即原授权生效; 彻底删除时才清 ACL)。
        // deleted_at/by 显式填(我的回收站归属口径), deleteById 只翻 deleted 标志。
        folderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFileFolder>()
                .eq(BizFileFolder::getId, folderId)
                .set(BizFileFolder::getDeletedAt, java.time.LocalDateTime.now())
                .set(BizFileFolder::getDeletedBy, SecurityUtils.getCurrentUserId()));
        folderMapper.deleteById(folderId);
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
        String originalExt = extOf(originalName); // null=原名无扩展名
        String inputExt = extOf(input);
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
            newKey = uploadStorage(newFile, scope, file.getUploaderId(), extOf(file.getFileName()));
            scanStored(newKey);
            String oldKey = file.getOssKey();
            long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
            // 【V2026.11.14.1 版本历史】主行覆盖前先快照旧版本(旧 key/type/size/version):
            // 归档行与主行更新同事务, 台账与归档原子一致; 旧字节不再物理删除(下方 deleteStorage 拔除)。
            BizFileVersion archived = new BizFileVersion();
            archived.setTenantId(1L);
            archived.setFileId(fileId);
            archived.setVersionNo(file.getVersion() == null ? 1 : file.getVersion());
            archived.setOssKey(oldKey);
            archived.setOssUrl(isLocal() ? LIT_API_FILE_DOWNLOAD + oldKey
                    : LIT_HTTPS + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + oldKey);
            archived.setFileSize(oldSize);
            archived.setFileType(file.getFileType());
            archived.setFileName(file.getFileName());
            archived.setUploadedBy(userId);
            fileVersionMapper.insert(archived);
            // ossUrl 与 upload 的 URL 方案一致：本地走 key 下载路由，OSS 走桶地址（保证替换后两种模式均可下载）
            String newOssUrl = isLocal()
                    ? LIT_API_FILE_DOWNLOAD + newKey
                    : LIT_HTTPS + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + newKey;
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
            deleteStorage(newKey);
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

    /**
     * 实际存储写入（从 upload 抽出复用）：本地落盘回读校验 / OSS putObject。
     * 返回存储 key（scope/userId 目录 + 唯一文件名），供 upload 与 replaceFile 共用，行为与原先一致。
     */
    private String uploadStorage(MultipartFile file, String scope, Long userId) throws Exception {
        return uploadStorage(file, scope, userId, null);
    }

    /** 本地存储路径扫描；OSS 模式暂无本地字节，扫描器在下载/转存链路上补（部署期配置）。 */
    private void scanStored(String key) {
        if (fileScanService == null || key == null) return;
        fileScanService.scan(Paths.get(uploadDir, key));
    }

    /**
     * @param fallbackExt 替换场景的旧文件扩展兜底: 客户端未提供文件名(消毒后无扩展名)时,
     *                    继承旧文件扩展再过白名单——避免"必须带文件名"破坏既有替换链路
     */
    private String uploadStorage(MultipartFile file, String scope, Long userId, String fallbackExt) throws Exception {
        // 【安全 2026-08-08 上传路径穿越】originalFilename 可能含 ../ 或路径分隔符，消毒后再拼落盘路径
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        // 【PR-01 N-09 2026-09-18】上传文件类型白名单：仅允许办公文档/图片/音视频/压缩包/文本。
        // 阻断可执行脚本与 HTML/SVG（未来某路径改 inline 即 XSS/恶意分发）。未知类型 400 拒绝。
        if (fallbackExt != null && !fallbackExt.isBlank() && originalFilename.lastIndexOf('.') < 0) {
            originalFilename = originalFilename + "." + fallbackExt;
        }
        assertAllowedUploadType(originalFilename, file.getContentType());
        // 【U1 2026-11-17】.pdf 扩展名须含 %PDF- 魔数(前 1024 字节内, PDF 规范 7.5.1)——
        // 挡"任意内容改名 .pdf"的内容伪装(T5f 实测: 纯文本 fake.pdf 可上传挂载致阅读器打不开)
        if ("pdf".equals(extOf(originalFilename))) {
            assertPdfMagic(file);
        }
        String dir = scope + "/" + userId;
        String fileKey = dir + "/" + resolveUniqueFilename(dir, originalFilename);
        if (isLocal()) {
            // 本地存储
            Path filePath = Paths.get(uploadDir, fileKey);
            Files.createDirectories(filePath.getParent());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            // 【需求】落盘校验:防止"数据库有记录但磁盘无文件"的静默失败(FEAT-20260804-FILE-INTEGRITY)
            // 【方法】写盘后回读文件大小,与上传大小不一致说明写盘异常,删除已写文件并报错
            if (Files.size(filePath) != file.getSize()) {
                Files.deleteIfExists(filePath);
                throw new BusinessException("文件写入校验失败,请重试");
            }
        } else {
            // OSS 存储
            InputStream inputStream = file.getInputStream();
            ossClient().putObject(ossConfig.getBucketName(), fileKey, inputStream);
        }
        return fileKey;
    }

    /**
     * 物理删除存储中的文件（从 deleteFile 抽出复用）：本地 deleteIfExists / OSS deleteObject，
     * 异常吞掉与 deleteFile 原行为一致；null/空 key 直接跳过。
     */
    private void deleteStorage(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) return;
        try {
            if (isLocal()) {
                Path filePath = Paths.get(uploadDir, fileKey);
                Files.deleteIfExists(filePath);
            } else {
                ossClient().deleteObject(ossConfig.getBucketName(), fileKey);
            }
        } catch (Exception e) {
            // 尽力而为:删除失败不回滚主流程(文件元数据已删)。
            // 当前无自动清理任务(2026-09-22 GPT外审确认): 物理残留需按 OSS 生命周期策略/人工核对处置,
            // 在此 log.warn 留痕;引入 FileDeletionOutbox 台账列产品批次
            log.warn("物理存储删除失败 fileKey={}, 元数据已删, 残留待处置(无自动清理任务)", fileKey, e);
            }
    }

    public Path getLocalFilePath(String fileKey) {
        return Paths.get(uploadDir, fileKey);
    }

    /**
     * 【文件回收站 2026-10-18】物理清除存储内容。
     * 仅供回收站保留期到期/管理员手动清空调用; deleteFile 本身不再物理删除(先进回收站)。
     * 尽力而为: 清除失败不回滚台账删除, 残留由日志留痕(与既有 deleteStorage 语义一致)。
     */
    public void deleteStoredContent(String fileKey) {
        deleteStorage(fileKey);
    }

    /**
     * 【回收站 2026-10-18】把存储内容写回响应(仅供回收站详情下载, 已按权限校验)。
     * 本地直接读盘; OSS 走 getObject 流式写出。失败置 500, 不抛异常穿透响应。
     */
    public void writeStoredContent(String fileKey, String fileName, String contentType,
                                   jakarta.servlet.http.HttpServletResponse response) {
        try {
            response.setContentType(contentType != null && !contentType.isBlank()
                    ? contentType : "application/octet-stream");
            String name = fileName != null && !fileName.isBlank() ? fileName : "file";
            String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            if (isLocal()) {
                Path path = Paths.get(uploadDir, fileKey);
                if (!Files.exists(path)) {
                    response.setStatus(404);
                    return;
                }
                response.setContentLengthLong(Files.size(path));
                Files.copy(path, response.getOutputStream());
            } else {
                com.aliyun.oss.model.OSSObject obj =
                        ossClient().getObject(ossConfig.getBucketName(), fileKey);
                try (java.io.InputStream in = obj.getObjectContent()) {
                    in.transferTo(response.getOutputStream());
                } finally {
                    obj.close();
                }
            }
        } catch (Exception e) {
            log.warn("回收站文件内容写出失败 fileKey={}: {}", fileKey, e.getMessage());
            response.setStatus(500);
        }
    }

    /** 存储对象字节写出到任意流(版本 zip 打包通道; 本地 Files.copy / OSS 流拷贝) */
    private void writeStoredContentTo(String fileKey, java.io.OutputStream out) throws java.io.IOException {
        if (isLocal()) {
            Path path = Paths.get(uploadDir, fileKey);
            if (!Files.exists(path)) throw new java.io.FileNotFoundException(fileKey);
            Files.copy(path, out);
        } else {
            com.aliyun.oss.model.OSSObject obj = ossClient().getObject(ossConfig.getBucketName(), fileKey);
            try (java.io.InputStream in = obj.getObjectContent()) {
                in.transferTo(out);
            } finally {
                obj.close();
            }
        }
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

    /** 【V2026.11.14.3 P2】批量上限(防误操作与超大请求), 超出直接 400 */
    private void requireBatchLimit(List<Long> ids) {
        if (ids != null && ids.size() > 100) {
            throw new BusinessException("批量操作一次最多 100 项，请分批进行");
        }
    }

    /**
     * 批量删除: 逐条走 deleteFile(权限/佐证守卫/配额归还全部单条同链), 部分成功不回滚已成功项——
     * 返回逐条结果 {id: "OK"|"失败原因"}, 前端逐行展示。激励佐证文件按单条守卫报 400 跳过。
     */
    public java.util.Map<Long, String> batchDeleteFiles(List<Long> fileIds) {
        requireBatchLimit(fileIds);
        java.util.Map<Long, String> out = new java.util.LinkedHashMap<>();
        for (Long id : fileIds == null ? List.<Long>of() : fileIds) {
            try {
                if (fileMapper.selectById(id) == null) {
                    out.put(id, LIT_X_2);
                    continue;
                }
                // 【V2026.11.14.3 P1】逐条事务: 内部 this 调用不走 @Transactional 代理(自调用失效),
                // 原"每条自动提交"会在配额更新失败时留下'文件已删未退款'的半删态——
                // TransactionTemplate 保证单条内部原子; 不包整批=保留"部分成功不回滚已成功项"。
                transactionTemplate.executeWithoutResult(status -> deleteFile(id));
                out.put(id, "OK");
            } catch (Exception e) {
                out.put(id, e.getMessage() == null ? "删除失败" : e.getMessage());
            }
        }
        return out;
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
        if (fileKey == null || fileKey.isBlank()) return false;
        try {
            if (isLocal()) {
                return Files.exists(Paths.get(uploadDir, fileKey));
            }
            return ossClient().doesObjectExist(ossConfig.getBucketName(), fileKey);
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
        Path source = Paths.get(uploadDir, file.getOssKey());
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
        Path cacheRoot = thumbnailCacheRoot();
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
            Path filePath = Paths.get(uploadDir, file.getOssKey());
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

    /**
     * 生成缩略图并写入响应；仅图片返回 true。非图片或读取失败返回 false（调用方回退完整下载）。
     * <p>【性能 file-3 2026-10-18】生成的缩略图字节缓存在本地临时目录，键含 fileId/version/ossKey 哈希/
     * 原文件 size+mtime/目标边长/编码：文件替换后 version/ossKey/mtime 变化自然失效。缓存命中时只读
     * 缓存文件，不再打开/解码原图；授权在调用方 {@link #downloadById} 中每次请求都先行执行，缓存不参与鉴权。
     */
    private boolean writeThumbnail(BizFile file, Path filePath, jakarta.servlet.http.HttpServletResponse response) {
        String filename = file.getFileName();
        String lower = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (!lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) return false;
        boolean png = lower.endsWith(".png");

        ThumbnailCache cache = resolveThumbnailCache(file, filePath, png);
        if (writeCachedThumbnail(cache.cacheFile(), png, response)) return true;

        byte[] bytes = generateThumbnailBytes(filePath, png);
        if (bytes == null) return false;

        String cacheKey = storeGeneratedThumbnail(cache, file, filePath, png, bytes);
        writeGeneratedThumbnail(response, bytes, png, cacheKey, file);
        return true;
    }

    private record ThumbnailCache(Path cacheRoot, Path cacheFile, String cacheKey) {}

    private ThumbnailCache resolveThumbnailCache(BizFile file, Path filePath, boolean png) {
        Path cacheRoot = thumbnailCacheRoot();
        Path cacheFile = null;
        String cacheKey = null;
        try {
            Files.createDirectories(cacheRoot);
            // 原图存在时用精确键（含 size+mtime）；原图已删/不可读时退回同 fileId+version+ossKey 的最新缓存，
            // 使"已生成过缩略图的原图丢失"仍可命中缓存而不必重新解码（授权已通过，不存在越权面）。
            cacheKey = thumbnailCacheKey(file, filePath, png);
            if (cacheKey != null) {
                Path candidate = cacheRoot.resolve(cacheKey);
                if (Files.isRegularFile(candidate)) cacheFile = candidate;
            } else {
                String prefix = thumbnailCachePrefix(file);
                if (prefix != null) cacheFile = findCachedThumbnailByPrefix(cacheRoot, prefix, png);
            }
        } catch (Exception e) {
            log.debug("缩略图缓存目录不可用，按无缓存处理: {}", e.getMessage());
            cacheRoot = null;
            cacheFile = null;
        }
        return new ThumbnailCache(cacheRoot, cacheFile, cacheKey);
    }

    private boolean writeCachedThumbnail(Path cacheFile, boolean png,
                                         jakarta.servlet.http.HttpServletResponse response) {
        if (cacheFile == null) return false;
        byte[] cached = null;
        try {
            cached = Files.readAllBytes(cacheFile);
        } catch (Exception e) {
            log.debug("缩略图缓存读取失败 {}: {}", cacheFile.getFileName(), e.getMessage());
        }
        if (cached == null || cached.length == 0) return false;
        try {
            writeThumbnailResponse(response, cached, png, cacheFile.getFileName().toString());
        } catch (Exception e) {
            // 缓存已命中、响应可能已提交：不再重试生成/回退完整下载，避免向同一响应追加内容
            log.warn("缩略图响应写出失败（缓存命中）file={}: {}", cacheFile.getFileName(), e.getMessage());
        }
        return true;
    }

    private String storeGeneratedThumbnail(ThumbnailCache cache, BizFile file, Path filePath,
                                           boolean png, byte[] bytes) {
        if (cache.cacheRoot() != null) {
            String cacheKey = cache.cacheKey();
            if (cacheKey == null) cacheKey = thumbnailCacheKey(file, filePath, png);
            if (cacheKey != null) storeThumbnail(cache.cacheRoot(), cacheKey, bytes);
            return cacheKey;
        }
        return cache.cacheKey();
    }

    private void writeGeneratedThumbnail(jakarta.servlet.http.HttpServletResponse response, byte[] bytes,
                                         boolean png, String cacheKey, BizFile file) {
        try {
            writeThumbnailResponse(response, bytes, png,
                    cacheKey != null ? cacheKey : "generated-" + file.getId());
        } catch (Exception e) {
            // 字节已生成、响应可能已提交：此时回退完整下载会向同一响应追加内容，故按成功处理并留日志
            log.warn("缩略图响应写出失败 fileId={}: {}", file.getId(), e.getMessage());
        }
    }

    private record ThumbnailPlan(javax.imageio.ImageReadParam param, int width, int height, boolean needScale) {}

    /**
     * 解码 + 缩放为缩略图字节（保留原有子采样防 OOM 设计）。
     * 返回 null 表示非图片/读取失败，调用方回退完整下载。
     */
    private byte[] generateThumbnailBytes(Path filePath, boolean png) {
        try (javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(filePath.toFile())) {
            if (iis == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            javax.imageio.ImageReader reader = readers.next();
            try (AutoCloseable readerClose = reader::dispose) {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0) return null;
                ThumbnailPlan plan = thumbnailPlan(reader, w, h);
                java.awt.image.BufferedImage img = reader.read(0, plan.param());
                if (img == null) return null;

                java.awt.image.BufferedImage out = img;
                if (plan.needScale() && (img.getWidth() != plan.width() || img.getHeight() != plan.height())) {
                    out = scaleThumbnail(img, plan.width(), plan.height(), png);
                }
                return thumbnailBytes(out, png);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 计算目标尺寸与子采样参数（S3776 拆分，语义与原内联逻辑一致）。 */
    private ThumbnailPlan thumbnailPlan(javax.imageio.ImageReader reader, int w, int h) {
        int maxDim = THUMBNAIL_MAX_DIM;
        // 大图子采样解码：只解码部分像素（解码尺寸≈2×目标），避免整图解码——5MB+ JPEG 解码后
        // 原始像素 ~96MB/张，激励查询页首屏并发生成缩略图时撑爆 -Xmx512m 堆（2026-08-09 OOM 复盘）。
        int nw = w, nh = h;
        boolean needScale = w > maxDim || h > maxDim;
        javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
        if (needScale) {
            double scale = Math.min((double) maxDim / w, (double) maxDim / h);
            nw = Math.max(1, (int) (w * scale));
            nh = Math.max(1, (int) (h * scale));
            int sub = 1;
            while (Math.max(w, h) / sub > maxDim * 2 && sub < 64) sub <<= 1;
            if (sub > 1) param.setSourceSubsampling(sub, sub, 0, 0);
        }
        return new ThumbnailPlan(param, nw, nh, needScale);
    }

    private java.awt.image.BufferedImage scaleThumbnail(java.awt.image.BufferedImage img, int nw, int nh, boolean png) {
        // PNG 保留透明通道，其余转 JPG
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                nw, nh, png ? java.awt.image.BufferedImage.TYPE_INT_ARGB : java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] thumbnailBytes(java.awt.image.BufferedImage out, boolean png) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(16 * 1024);
        if (!javax.imageio.ImageIO.write(out, png ? "png" : "jpg", bos)) return null;
        return bos.toByteArray();
    }

    /** 写缩略图响应：私有缓存（不得被共享代理缓存已授权内容）+ 基于缓存键的强 ETag。 */
    private void writeThumbnailResponse(jakarta.servlet.http.HttpServletResponse response, byte[] bytes,
                                        boolean png, String etagKey) throws java.io.IOException {
        response.setContentType(png ? "image/png" : "image/jpeg");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        response.setHeader(HttpHeaders.ETAG, "\"" + etagKey + "\"");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private Path thumbnailCacheRoot() {
        if (thumbnailCacheDir != null && !thumbnailCacheDir.isBlank()) {
            return Paths.get(thumbnailCacheDir);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), THUMBNAIL_CACHE_DIR_NAME);
    }

    /**
     * 缓存键：fileId + version + ossKey 哈希 + 原文件 size + mtime + 目标边长 + 编码。
     * 文件名只由数字/安全哈希/固定标记拼成，不含任何未净化用户输入；原图不可读时返回 null。
     */
    private String thumbnailCacheKey(BizFile file, Path original, boolean png) {
        if (file.getId() == null || file.getOssKey() == null) return null;
        try {
            long size = Files.size(original);
            long mtime = Files.getLastModifiedTime(original).toMillis();
            int version = file.getVersion() == null ? 0 : file.getVersion();
            return thumbnailCachePrefix(file) + size + "-" + mtime + "-" + THUMBNAIL_MAX_DIM
                    + (png ? ".png" : ".jpg");
        } catch (Exception e) {
            // 原图缺失/不可读：由调用方退回"同 fileId+version+ossKey"前缀缓存
            return null;
        }
    }

    private String thumbnailCachePrefix(BizFile file) {
        if (file.getId() == null || file.getOssKey() == null) return null;
        int version = file.getVersion() == null ? 0 : file.getVersion();
        return file.getId() + "-" + version + "-" + sha256Hex(file.getOssKey()).substring(0, 24)
                + "-";
    }

    private Path findCachedThumbnailByPrefix(Path cacheRoot, String prefix, boolean png) {
        String suffix = png ? ".png" : ".jpg";
        try (java.util.stream.Stream<Path> stream = Files.list(cacheRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .max(java.util.Comparator.comparingLong(this::lastModifiedMillis))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 临时文件 + 原子 move 落缓存，避免并发读到半文件；失败只降级为不使用缓存。 */
    private void storeThumbnail(Path cacheRoot, String key, byte[] bytes) {
        Path tmp = null;
        try {
            Files.createDirectories(cacheRoot);
            tmp = Files.createTempFile(cacheRoot, "tmp-", ".part");
            Files.write(tmp, bytes);
            Path target = cacheRoot.resolve(key);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (Exception e) {
            log.debug("缩略图缓存写入失败 key={}: {}", key, e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响响应
                }
            }
        }
        pruneThumbnailCacheIfNeeded(cacheRoot);
    }

    /** 条目上限 + 最旧清理：每 {@value #THUMBNAIL_CACHE_PRUNE_INTERVAL} 次写入巡检一次，避免目录无限增长。 */
    private void pruneThumbnailCacheIfNeeded(Path cacheRoot) {
        int ops = thumbnailCacheOps.incrementAndGet();
        if (ops != 1 && ops % THUMBNAIL_CACHE_PRUNE_INTERVAL != 0) return;
        try (java.util.stream.Stream<Path> stream = Files.list(cacheRoot)) {
            List<Path> entries = stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jpg") || name.endsWith(".png");
                    })
                    .sorted(java.util.Comparator.comparingLong(this::lastModifiedMillis))
                    .collect(Collectors.toList());
            int excess = entries.size() - THUMBNAIL_CACHE_MAX_ENTRIES;
            for (int i = 0; i < excess; i++) {
                try {
                    Files.deleteIfExists(entries.get(i));
                } catch (Exception ignored) {
                    // 单条清理失败不阻断其余
                }
            }
        } catch (Exception e) {
            log.debug("缩略图缓存容量巡检失败: {}", e.getMessage());
        }
    }

    /**
     * 清掉该 fileId 的所有版本缩略图缓存（替换/删除时调用，尽力而为）。
     * 文件名以 {@code <fileId>-} 开头，天然按文件隔离，不会误删其他文件条目。
     */
    void evictThumbnailCache(Long fileId) {
        if (fileId == null) return;
        Path root = thumbnailCacheRoot();
        if (!Files.isDirectory(root)) return;
        String prefix = fileId + "-";
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 清理失败仅留下孤立缓存，容量巡检会兜底
                        }
                    });
        } catch (Exception e) {
            log.debug("缩略图缓存清理失败 fileId={}: {}", fileId, e.getMessage());
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
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
            info.put("usedFormatted", formatSize(user.getStorageUsed()));
            info.put("quotaFormatted", formatSize(user.getStorageQuota()));
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

    /**
     * 【安全 2026-08-08 上传路径穿越】文件名消毒：只保留最后一段文件名，去掉路径分隔符(/ \)、
     * .. 序列与控制字符，防止 originalFilename 含 ../../ 时写出 uploadDir 之外。
     * 与下载路径的 normalize+前缀校验（FileController）同等级防护。
     */
    static String sanitizeFilename(String filename) {
        if (filename == null) return LIT_UNNAMED;
        String safe = filename.replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        if (slash >= 0) safe = safe.substring(slash + 1); // 只取最后一段，忽略一切目录
        safe = safe.replace("..", "");                     // 移除 .. 序列
        safe = safe.replaceAll("[\\x00-\\x1f]", "");       // 移除控制字符
        return safe.isBlank() ? LIT_UNNAMED : safe;
    }

    /**
     * 【PR-01 N-09 2026-09-18】上传文件类型白名单（扩展名判定为主，MIME 仅作参考不单独信任）。
     * 允许：办公文档/表格/演示/文本/图片/音视频/压缩包；拒绝：可执行脚本(js/jar/sh/exe/bat/
     * ps1/py 等)与可在浏览器直接执行的 html/htm/svg/xml/xhtml。
     * 无扩展名文件默认拒绝（历史LIT_UNNAMED兜底不再放行未知类型）。
     */
    static final Set<String> ALLOWED_UPLOAD_EXTS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic",
            "mp3", "wav", "m4a", "mp4", "mov", "avi", "mkv",
            "zip", "rar", "7z", "tar", "gz");

    /** 取文件名扩展(无扩展名返回 null); 供替换场景兜底继承 */
    private static String extOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    static void assertAllowedUploadType(String sanitizedFilename, String contentType) {
        String ext = "";
        boolean hasExt = false;
        int dot = sanitizedFilename == null ? -1 : sanitizedFilename.lastIndexOf('.');
        if (dot >= 0 && dot < sanitizedFilename.length() - 1) {
            hasExt = true;
            ext = sanitizedFilename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        }
        // 【GPT三轮 FILE-P1-008 2026-09-22】无扩展名文件拒绝——对齐上方 javadoc 声明与白名单哲学:
        // 白名单体系下"未知类型"不默认放行; 旧代码此处 return 放行与注释相反(FILE-P2-004 挂账清偿)。
        // 前端/小程序无 unnamed 兜底依赖(仅 sanitizeFilename 对穿越序列的消毒产物, 该产物在类型层拒绝属预期)
        if (!hasExt) {
            throw new BusinessException(400, "不支持的文件类型: 缺少扩展名");
        }
        if (!ALLOWED_UPLOAD_EXTS.contains(ext)) {
            throw new BusinessException(400, "不支持的文件类型");
        }
    }

    /**
     * 【U1 2026-11-17】pdf 内容魔数校验：前 1024 字节内须含 "%PDF-"（PDF 规范 7.5.1 允许头
     * 位于文件起始处，部分工具会前置少量字节；1024 字节窗口与主流解析器一致）。
     * 只依赖内容不信任扩展名——任何真 PDF 100% 有此头，误拒风险为零。
     * 调用点：uploadStorage（.pdf 扩展名上传时），MultipartFile#getInputStream 可重复获取新流，
     * 此处读头不影响后续落盘/OSS 写入。
     */
    static void assertPdfMagic(MultipartFile file) {
        byte[] head;
        try (java.io.InputStream in = file.getInputStream()) {
            head = in.readNBytes(1024);
        } catch (java.io.IOException e) {
            throw new BusinessException(400, "PDF 文件读取失败, 请重试");
        }
        for (int i = 0; i + 5 <= head.length; i++) {
            if (head[i] == '%' && head[i + 1] == 'P' && head[i + 2] == 'D'
                    && head[i + 3] == 'F' && head[i + 4] == '-') {
                return;
            }
        }
        throw new BusinessException(400, "PDF 文件内容校验失败(缺少 %PDF- 标识), 请确认文件是真实的 PDF");
    }

    /**
     * 保留原文件名，重名时加后缀：报告.pdf → 报告(1).pdf → 报告(2).pdf
     */
    private String resolveUniqueFilename(String dir, String filename) {
        String baseName;
        String ext;
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = filename.substring(0, dotIdx);
            ext = filename.substring(dotIdx);
        } else {
            baseName = filename;
            ext = "";
        }

        String candidate = filename;
        int seq = 1;
        while (fileExists(dir, candidate)) {
            candidate = baseName + "(" + seq + ")" + ext;
            seq++;
        }
        return candidate;
    }

    private boolean fileExists(String dir, String filename) {
        if (isLocal()) {
            return Files.exists(Paths.get(uploadDir, dir, filename));
        } else {
            return ossClient().doesObjectExist(ossConfig.getBucketName(), dir + "/" + filename);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < BYTES_PER_KB) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
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

    // ====== 文件分享 ======

    public void shareFile(Long fileId, List<Long> userIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 只有文件上传者或管理员可以分享
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        // 【文件权限体系】夹内文件按文件夹权限判定分享（管理员/本人/上级链/ACL share）；
        // 无文件夹文件沿用下方上传者归属校验
        {
            if (file.getFolderId() != null) {
                if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_SHARE, currentUserId)) {
                    throw new BusinessException(403, LIT_X);
                }
            } else if (!currentUserId.equals(file.getUploaderId())) {
                throw new BusinessException(403, "无权分享该文件");
            }
        }
        // 【2026-10-27 深度检查修复·PR-22 批次四同款】文件共享不得绕过集团边界: 此前跨集团分享
        // 静默成功——写入乙集团用户永远看不到的死行(biz_file_share 被租户拦截器按查看者集团改写)。
        if (!userIds.isEmpty()) {
            Long group = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            if (group != null) {
                List<Long> groupMembers = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, group);
                List<Long> foreign = userIds.stream().filter(uid -> !groupMembers.contains(uid)).toList();
                if (!foreign.isEmpty()) {
                    throw new BusinessException("不能共享给其他集团用户（目标用户不在当前集团）");
                }
            } else {
                throw new BusinessException(403, "无有效集团归属，禁止共享");
            }
        }
        for (Long uid : userIds) {
            Long count = fileShareMapper.selectCount(
                    new LambdaQueryWrapper<BizFileShare>()
                            .eq(BizFileShare::getFileId, fileId)
                            .eq(BizFileShare::getSharedToUserId, uid));
            if (count == 0) {
                BizFileShare share = new BizFileShare();
                share.setFileId(fileId);
                share.setSharedToUserId(uid);
                share.setSharedByUserId(currentUserId);
                fileShareMapper.insert(share);
            }
        }
    }
    public IPage<BizFile> pageSharedToMe(int current, int size, String keyword) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BizFileShare> shares = fileShareMapper.selectList(
                new LambdaQueryWrapper<BizFileShare>().eq(BizFileShare::getSharedToUserId, userId));
        List<Long> fileIds = shares.stream().map(BizFileShare::getFileId).toList();
        if (fileIds.isEmpty()) return new Page<>(current, size, 0);

        LambdaQueryWrapper<BizFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizFile::getId, fileIds).eq(BizFile::getDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            applyGlobalKeyword(wrapper, keyword);
        }
        wrapper.orderByDesc(BizFile::getCreatedAt);
        IPage<BizFile> page = fileMapper.selectPage(new Page<>(current, size), wrapper);
        for (BizFile f : page.getRecords()) {
            if (f.getUploaderId() != null) {
                SysUser user = userMapper.selectById(f.getUploaderId());
                if (user != null) f.setUploaderName(user.getRealName());
            }
        }
        return page;
    }

    public long countSharedToMe() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BizFileShare> shares = fileShareMapper.selectList(
                new LambdaQueryWrapper<BizFileShare>().eq(BizFileShare::getSharedToUserId, userId));
        if (shares.isEmpty()) return 0;
        List<Long> fileIds = shares.stream().map(BizFileShare::getFileId).toList();
        return fileMapper.selectCount(new LambdaQueryWrapper<BizFile>()
                .in(BizFile::getId, fileIds)
                .eq(BizFile::getDeleted, 0));
    }
}
