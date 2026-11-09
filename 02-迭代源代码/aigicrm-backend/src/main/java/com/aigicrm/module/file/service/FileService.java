package com.aigicrm.module.file.service;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.config.OssConfig;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
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
    private final BizFolderAclMapper folderAclMapper;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final SysUserMapper userMapper;
    private final ObjectProvider<OSS> ossClientProvider;
    private final OssConfig ossConfig;
    private final FolderPermissionService folderPermissionService;
    // 【2026-11-09 类型级默认权限】建夹门槛(公共根=admin∨类型默认create)+新夹继承默认ACL
    private final FolderTypeDefaultService folderTypeDefaultService;
    // 【2026-10-27 深度检查修复】shareFile 集团边界校验需要(同 CustomerService.addShares 范式)
    private final JdbcTemplate jdbc;

    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** 缩略图磁盘缓存目录；留空则使用 java.io.tmpdir/aigicrm-thumb-cache */
    @Value("${file.thumbnail-cache-dir:}")
    private String thumbnailCacheDir;

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
        if ("public".equals(scope)) {
            wrapper.eq(BizFileFolder::getFolderType, "public");
        } else if ("private".equals(scope)) {
            // 【文件权限体系】私有树候选集扩展为全部私有文件夹，
            // 可见性（本人创建/上级链/ACL view）在下方内存过滤，支持上级与 ACL 授权用户查看他人私有树。
            wrapper.eq(BizFileFolder::getFolderType, "private");
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
        for (BizFileFolder folder : folders) {
            long count = fileMapper.selectCount(
                    new LambdaQueryWrapper<BizFile>()
                            .eq(BizFile::getFolderId, folder.getId())
                            .eq(BizFile::getDeleted, 0)
            );
            folder.setFileCount((int) count);
            folder.setPerms(permsMap.getOrDefault(folder.getId(), Collections.emptyList()));
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
        if (scope == null || !("public".equals(scope) || "private".equals(scope) || "incentive".equals(scope))) {
            return new Page<>(current, size, 0);
        }
        LambdaQueryWrapper<BizFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizFile::getDeleted, 0);

        if (folderId != null) {
            wrapper.eq(BizFile::getFolderId, folderId);
        }
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(BizFile::getFileScope, scope);
            if ("incentive".equals(scope)) {
                // 【2026-11-01 审计修复】admin/持 incentive:view-all 全量旁路——与激励侧 applyShareVisibility
                // 同口径(原文件侧无旁路: 总监在 MP 四项服务tab可见的未分享激励文件, PC 文件中心不出现,
                // 且下载分支无旁路时会出现"MP 可见 PC 下载 403"的双端分叉)
                if (incentiveViewAllGranted()) {
                    // 全量可见, 不加可见性过滤
                } else
                // 自己的 + 其他挂了分享的激励文件（与下载可见性同源，30s 缓存见 incentiveVisibleFileIds）
                {
                Set<Long> sharedFileIds = incentiveVisibleFileIds(userId);

                if (sharedFileIds.isEmpty()) {
                    wrapper.eq(BizFile::getUploaderId, userId);
                } else {
                    wrapper.and(w -> w.eq(BizFile::getUploaderId, userId)
                            .or().in(BizFile::getId, sharedFileIds));
                }
                }
            }
        }
        // 【文件权限体系】统一文件夹可见性过滤（除 incentive 外的所有 scope 生效）：
        // 夹内文件仅返回用户可见文件夹中的（本人创建/上级链/ACL view/public），上级可查看下级私有文件夹内文件；
        // private scope 的无文件夹文件仍需本人上传；未知/空 scope 候选集为空 → 夹内文件一律不可见
        // （与 getFolderTree 的 scope 默认拒绝对称，封堵"任意 file:view 用户枚举任意文件夹文件"）。
        // incentive scope 已有文件级归属约束（本人上传/被分享），不在此过滤。
        if (!"incentive".equals(scope)) {
            List<BizFileFolder> folders = folderMapper.selectList(
                    new LambdaQueryWrapper<BizFileFolder>()
                            .eq(BizFileFolder::getFolderType, scope));
            Set<Long> visibleIds = visibleFolderIds(folders, userId);
            if (folderId != null) {
                if (!visibleIds.contains(folderId)) {
                    return new Page<>(current, size, 0);
                }
            } else if (visibleIds.isEmpty()) {
                if ("private".equals(scope)) {
                    wrapper.and(w -> w.isNull(BizFile::getFolderId)
                            .and(w2 -> w2.eq(BizFile::getUploaderId, userId)));
                } else {
                    wrapper.isNull(BizFile::getFolderId);
                }
            } else if ("private".equals(scope)) {
                wrapper.and(w -> w.isNull(BizFile::getFolderId)
                        .and(w2 -> w2.eq(BizFile::getUploaderId, userId))
                        .or().in(BizFile::getFolderId, visibleIds));
            } else {
                wrapper.and(w -> w.isNull(BizFile::getFolderId)
                        .or().in(BizFile::getFolderId, visibleIds));
            }
        }
        if (keyword != null && !keyword.isEmpty()) {
            applyGlobalKeyword(wrapper, keyword);
        }
        wrapper.orderByDesc(BizFile::getCreatedAt);

        IPage<BizFile> page = fileMapper.selectPage(new Page<>(current, size), wrapper);
        for (BizFile file : page.getRecords()) {
            if (file.getUploaderId() != null) {
                SysUser user = userMapper.selectById(file.getUploaderId());
                if (user != null) file.setUploaderName(user.getRealName());
            }
        }
        return page;
    }

    public BizFile upload(MultipartFile file, Long folderId, String scope) {

        Long userId = SecurityUtils.getCurrentUserId();

        // 【安全加固】scope 白名单：仅允许 public/private/incentive，未识别 scope 直接拒绝
        // （与 pageFiles/getFolderTree 的 scope 白名单对称，防止任意 scope 落盘后绕过查询过滤/占用存储）
        if (scope == null || !("public".equals(scope) || "private".equals(scope) || "incentive".equals(scope))) {
            throw new BusinessException(403, "非法的文件范围");
        }

        // 【2026-10-27 深度检查修复】无夹 public 上传补管理员门槛: 该路径落盘 public/{userId}/ 且
        // file_scope=public 后立即进入匿名无鉴权下载通道(/api/file/download/public/**), 与
        // "根级公共文件夹仅管理员可建"门槛对称——普通用户要发公共资源, 走公共文件夹(ACL 判定)。
        if ("public".equals(scope) && folderId == null && !SecurityUtils.isAdmin()) {
            throw new BusinessException("公共根目录文件仅管理员可上传");
        }

        // 【文件权限体系】上传到文件夹需文件夹上传权限（管理员/本人/上级链/ACL upload）
        if (folderId != null && !folderPermissionService.check(folderId, FolderPermissionService.P_UPLOAD, userId)) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }

        // 【P2 2026-10-09 配额并发修复】配额检查与扣减原子化: WHERE used+size<=quota 命中才扣,
        // 0 行=配额不足——并发上传在 DB 端串行判定, 消除"两请求同过内存检查"的超配额竞态。
        if ("private".equals(scope)) {
            if (userMapper.tryConsumeQuota(userId, file.getSize()) == 0) {
                SysUser user = userMapper.selectById(userId);
                throw new BusinessException("存储空间不足，当前已用: " +
                        formatSize(user != null ? user.getStorageUsed() : 0L) +
                        "，配额: " + formatSize(user != null ? user.getStorageQuota() : 0L));
            }
        }

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "unnamed";
            }
            String fileKey = uploadStorage(file, scope, userId);
            String fileUrl;
            if (isLocal()) {
                fileUrl = "/api/file/download/" + fileKey;
            } else {
                fileUrl = "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + fileKey;
            }

            BizFile bizFile = new BizFile();
            bizFile.setFolderId(folderId);
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
            // 【P2 2026-10-09】扣减成功后存储/入库失败 → 补偿归还(GREATEST 守护)
            if ("private".equals(scope)) {
                userMapper.adjustStorageUsed(userId, -file.getSize());
            }
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    public void createFolder(BizFileFolder folder) {
        String type = folder.getFolderType();
        // 【安全修复 FEAT-20260804-FILE-AUTHZ】folderType 白名单校验，避免传入未识别类型
        // 绕过 checkFolderManagePermission 的默认拒绝逻辑（此前会被后续接口当成"未识别类型"处理）。
        if (!"public".equals(type) && !"private".equals(type)) {
            throw new BusinessException("不支持的文件夹类型");
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
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        // 【2026-11-09 类型级默认权限】根级公共建夹门槛: admin ∨ 类型默认(public, 本人角色)含 create。
        // 零配置(默认表空)时保持原行为"仅管理员"; 私人根维持"有 file:folder:manage 码即可建"。
        if ("public".equals(type) && folder.getParentId() == null
                && !SecurityUtils.isAdmin()
                && !folderTypeDefaultService.canCreateRoot("public")) {
            throw new BusinessException("公共文件夹只有管理员才能创建");
        }
        if ("private".equals(type)) {
            folder.setOwnerId(SecurityUtils.getCurrentUserId());
        }
        folderMapper.insert(folder);
        // 【2026-11-09 文件权限 v2】类型授权改为判定链实时生效(typeGranted), 不再建夹时物化进
        // biz_folder_acl——勾选/取消对该空间全部存量+未来文件夹即时同步(v1 建夹物化机制已退役)。
    }

    public void renameFolder(Long folderId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new BusinessException("文件夹名称不能为空");
        }
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【文件权限 v2】文件夹改名走独立动作 rename(从 edit 伞拆出); 判定链 admin>本人>上级链>ACL>类型授权
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_RENAME, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        folder.setFolderName(newName.trim());
        folderMapper.updateById(folder);
    }

    public void deleteFolder(Long folderId) {
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【文件权限体系】ACL/上级链/本人 删除权限（管理员恒通过），
        // 为操作唯一仲裁（已覆盖 admin>本人>上级链>ACL 优先级且 folder==null 已拒绝）
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_DELETE, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
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

        // 【文件权限体系 设计§8 删除级联】同步删除该文件夹的 ACL 记录（防孤儿）
        folderAclMapper.delete(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId));

        folderMapper.deleteById(folderId);
    }

    /**
     * 删除文件（进入回收站）。
     * 【审查 backend-5 2026-10-18】补事务 + 以逻辑删影响行数为唯一退款凭据：
     * 旧实现 selectById→deleteById(丢弃返回值)→无条件 adjustStorageUsed，两个并发删除
     * 都能读到同一行并各退一次配额(重复归还)。现在只有 deleted=0 行被本次请求真正删除
     * (affected==1)才归还；affected==0 = 重复/并发删除，幂等返回成功且不二次退款。
     */
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
        if (!SecurityUtils.isAdmin()) {
            if (file.getFolderId() != null) {
                if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_DELETE, userId)) {
                    throw new BusinessException(403, "没有访问权限，请联系管理员授权");
                }
            } else if ("public".equals(scope)) {
                throw new BusinessException("公共文件只有管理员才能删除");
            } else if ("private".equals(scope) || "incentive".equals(scope)) {
                if (!userId.equals(file.getUploaderId())) {
                    throw new BusinessException(403, "无权删除该文件");
                }
            } else {
                throw new BusinessException(403, "无权删除该文件");
            }
        }

        // 【文件回收站 2026-10-18 用户裁决】删除不再物理清除字节: 文件进入回收站,
        // 由保留期任务(FileRecycleCleanupTask)到期或管理员手动清空时再调 deleteStoredContent。
        // 逻辑删除 + 配额归还保持原语义(私有文件删除即归还配额, 恢复时重新占用)。

        // 逻辑删除
        int affected = fileMapper.deleteById(fileId);
        if (affected != 1) {
            // 并发/重复删除: 台账已不在活跃态, 不得二次归还配额; 幂等返回成功(前端不报错)
            return;
        }
        // 【性能 file-3】删除后清掉该文件所有版本的缩略图磁盘缓存（逻辑删只保留原图供回收站）
        evictThumbnailCache(fileId);

        // 【P2 2026-10-09 配额并发修复】归还改数据库端原子增减, 消除读改写丢失更新
        if ("private".equals(file.getFileScope()) && file.getFileSize() != null) {
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
        if (file == null) throw new BusinessException("文件不存在");
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_RENAME, userId)) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
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
        if (file == null) throw new BusinessException("文件不存在");
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_REPLACE, userId)) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        // 【决策5 2026-10-18 用户裁决·佐证锁定】激励计分佐证禁替换:佐证文件(安装前三槽/跟踪槽/旧单槽)
        // 是计分依据(安装前三槽齐全即得分),同 ID 换内容会无声替换计分证据(先传A得分再换B)。
        // 需更新佐证走激励模块"删除后重传"(删除触发重算,分数随新证据重出)。记录表为百级量,全量扫可接受。
        assertNotIncentiveEvidence(fileId);
        String scope = file.getFileScope() != null ? file.getFileScope() : "private";
        String newKey = null;
        try {
            // 先存新文件（沿用原文件的 scope/uploaderId 目录，保证 key 下载路由语义一致）
            // 【GPT三轮 FILE-P1-008】无扩展名拒绝后, replace 的 null 文件名场景改用旧文件扩展兜底
            // (类型继承自已过白名单的旧文件, 治理语义不破), 保持 M-2② 的 ossUrl 更新行为可测
            newKey = uploadStorage(newFile, scope, file.getUploaderId(), extOf(file.getFileName()));
            String oldKey = file.getOssKey();
            long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
            // ossUrl 与 upload 的 URL 方案一致：本地走 key 下载路由，OSS 走桶地址（保证替换后两种模式均可下载）
            String newOssUrl = isLocal()
                    ? "/api/file/download/" + newKey
                    : "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + newKey;
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
            // 【性能 file-3】版本/ossKey 已变化，旧版本缩略图不再命中新 key；主动清掉避免磁盘残留
            // (缓存清理幂等: 回滚场景只是多一次 miss, 无损)
            evictThumbnailCache(fileId);
            // 【P2 2026-10-09 配额并发修复】差额调整改数据库端原子(GREATEST 守护最小 0)——
            // 【A7】与 updateById 同事务: 任一失败整体回滚, 配额与台账不再漂移
            if ("private".equals(scope)) {
                userMapper.adjustStorageUsed(file.getUploaderId(), newFile.getSize() - oldSize);
            }
            // 【A7】旧文件物理删除必须等事务提交: 注册 afterCommit, 回滚路径不执行
            // (无事务上下文时——如直连调用/单测——退化为立即清理, 与旧行为一致)
            String oldKeyToDelete = oldKey;
            runAfterCommit(() -> deleteStorage(oldKeyToDelete));
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
            if (thumbnail) {
                if (writeThumbnail(file, filePath, response)) return;
                // 非图片/读取失败 → 回退完整下载
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                String filename = file.getFileName();
                String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mt = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                String disp = dispositionFor(filename);

                long fileSize = resource.contentLength();
                long start = 0, end = fileSize - 1;
                boolean ranged = range != null && range.startsWith("bytes=");
                if (ranged) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("bytes=(\\d*)-(\\d*)").matcher(range);
                    if (m.find()) {
                        if (!m.group(1).isEmpty()) start = Long.parseLong(m.group(1));
                        if (!m.group(2).isEmpty()) end = Math.min(Long.parseLong(m.group(2)), fileSize - 1);
                    }
                    if (start > end || start >= fileSize) {
                        response.setStatus(416);
                        response.setHeader("Content-Range", "bytes */" + fileSize);
                        return;
                    }
                }
                long length = end - start + 1;

                response.setStatus(ranged ? 206 : 200);
                response.setHeader("Accept-Ranges", "bytes");
                if (ranged) {
                    response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                }
                response.setContentType(mt.toString());
                response.setContentLengthLong(length);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename*=UTF-8''" + encoded);

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
                return;
            }
            // 【2026-10-27 深度检查修复】OSS 302 与 FileController 路由白名单同口径:
            // startsWith("http") 可被 httpx:// 绕过, 收紧为 https:// (OSS 地址均 https)
            if (file.getOssUrl() != null && file.getOssUrl().startsWith("https://")) {
                response.setStatus(302);
                response.setHeader(HttpHeaders.LOCATION, file.getOssUrl());
                return;
            }
        } catch (Exception e) {
            // S4: 下载写响应异常不得静默吞成 404——记录日志便于排查
            log.error("文件下载失败 fileId={}", fileId, e);
        }
        response.setStatus(404);
    }

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

        if (cacheFile != null) {
            byte[] cached = null;
            try {
                cached = Files.readAllBytes(cacheFile);
            } catch (Exception e) {
                log.debug("缩略图缓存读取失败 {}: {}", cacheFile.getFileName(), e.getMessage());
            }
            if (cached != null && cached.length > 0) {
                try {
                    writeThumbnailResponse(response, cached, png, cacheFile.getFileName().toString());
                } catch (Exception e) {
                    // 缓存已命中、响应可能已提交：不再重试生成/回退完整下载，避免向同一响应追加内容
                    log.warn("缩略图响应写出失败（缓存命中）file={}: {}", cacheFile.getFileName(), e.getMessage());
                }
                return true;
            }
        }

        byte[] bytes = generateThumbnailBytes(filePath, png);
        if (bytes == null) return false;

        if (cacheRoot != null) {
            if (cacheKey == null) cacheKey = thumbnailCacheKey(file, filePath, png);
            if (cacheKey != null) storeThumbnail(cacheRoot, cacheKey, bytes);
        }

        try {
            writeThumbnailResponse(response, bytes, png,
                    cacheKey != null ? cacheKey : "generated-" + file.getId());
        } catch (Exception e) {
            // 字节已生成、响应可能已提交：此时回退完整下载会向同一响应追加内容，故按成功处理并留日志
            log.warn("缩略图响应写出失败 fileId={}: {}", file.getId(), e.getMessage());
        }
        return true;
    }

    /**
     * 解码 + 缩放为缩略图字节（保留原有子采样防 OOM 设计）。
     * 返回 null 表示非图片/读取失败，调用方回退完整下载。
     */
    private byte[] generateThumbnailBytes(Path filePath, boolean png) {
        int maxDim = THUMBNAIL_MAX_DIM;
        try (javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(filePath.toFile())) {
            if (iis == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0) return null;

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

                java.awt.image.BufferedImage img = reader.read(0, param);
                if (img == null) return null;

                java.awt.image.BufferedImage out = img;
                if (needScale && (img.getWidth() != nw || img.getHeight() != nh)) {
                    // PNG 保留透明通道，其余转 JPG
                    out = new java.awt.image.BufferedImage(
                            nw, nh, png ? java.awt.image.BufferedImage.TYPE_INT_ARGB : java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(img, 0, 0, nw, nh, null);
                    g.dispose();
                }

                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(16 * 1024);
                if (!javax.imageio.ImageIO.write(out, png ? "png" : "jpg", bos)) return null;
                return bos.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return null;
        }
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
        if (file != null && file.getOssUrl() != null && file.getOssUrl().startsWith("https://")) {
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
        if (filename == null) return "unnamed";
        String safe = filename.replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        if (slash >= 0) safe = safe.substring(slash + 1); // 只取最后一段，忽略一切目录
        safe = safe.replace("..", "");                     // 移除 .. 序列
        safe = safe.replaceAll("[\\x00-\\x1f]", "");       // 移除控制字符
        return safe.isBlank() ? "unnamed" : safe;
    }

    /**
     * 【PR-01 N-09 2026-09-18】上传文件类型白名单（扩展名判定为主，MIME 仅作参考不单独信任）。
     * 允许：办公文档/表格/演示/文本/图片/音视频/压缩包；拒绝：可执行脚本(js/jar/sh/exe/bat/
     * ps1/py 等)与可在浏览器直接执行的 html/htm/svg/xml/xhtml。
     * 无扩展名文件默认拒绝（历史"unnamed"兜底不再放行未知类型）。
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
        if (SecurityUtils.isAdmin()) return true;
        if ("public".equals(file.getFileScope())) return true;
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
        if (SecurityUtils.isAdmin()) return true;
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) return false;
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_incentive_grant WHERE user_id = ? AND can_view_all = 1", Long.class, uid);
        return n != null && n > 0;
    }

    public boolean canDownloadFile(BizFile file, Long userId) {
        boolean canDownload = canAccessFile(file, userId);
        if (!canDownload && file.getFolderId() != null) {
            canDownload = folderPermissionService.checkFile(file.getId(), FolderPermissionService.P_DOWNLOAD, userId);
        }
        if (!canDownload && "incentive".equals(file.getFileScope())) {
            // 【2026-11-01 审计修复】与列表旁路同口径: admin/激励授权 can_view_all 可下载(原仅本人+shared)
            canDownload = incentiveViewAllGranted() || isIncentiveFileVisible(file.getId(), userId);
        }
        if (!canDownload) {
            return false;
        }
        if (file.getFolderId() != null && !SecurityUtils.isAdmin()) {
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
        if (file == null) throw new BusinessException("文件不存在");
        // 【文件权限体系】夹内文件按文件夹权限判定分享（管理员/本人/上级链/ACL share）；
        // 无文件夹文件沿用下方上传者归属校验
        if (!SecurityUtils.isAdmin()) {
            if (file.getFolderId() != null) {
                if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_SHARE, currentUserId)) {
                    throw new BusinessException(403, "没有访问权限，请联系管理员授权");
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
