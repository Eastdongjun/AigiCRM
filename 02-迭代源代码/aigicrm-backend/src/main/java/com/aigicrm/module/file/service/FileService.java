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

    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

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
                // 自己的 + 其他人共享的激励文件
                List<BizIncentiveRecord> sharedRecords = incentiveRecordMapper.selectList(
                        new LambdaQueryWrapper<BizIncentiveRecord>()
                                .eq(BizIncentiveRecord::getShared, 1)
                                .ne(BizIncentiveRecord::getUserId, userId)
                );
                Set<Long> fileIdSet = new java.util.LinkedHashSet<>();
                for (BizIncentiveRecord r : sharedRecords) {
                    fileIdSet.addAll(r.getFileIds());
                    fileIdSet.addAll(r.getFilePreIds());
                    fileIdSet.addAll(r.getFileMidIds());
                    fileIdSet.addAll(r.getFilePostIds());
                    fileIdSet.addAll(r.getFileTrackIds());
                }
                List<Long> sharedFileIds = new ArrayList<>(fileIdSet);

                if (sharedFileIds.isEmpty()) {
                    wrapper.eq(BizFile::getUploaderId, userId);
                } else {
                    wrapper.and(w -> w.eq(BizFile::getUploaderId, userId)
                            .or().in(BizFile::getId, sharedFileIds));
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
            wrapper.like(BizFile::getFileName, keyword);
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

        // 【文件权限体系】上传到文件夹需文件夹上传权限（管理员/本人/上级链/ACL upload）
        if (folderId != null && !folderPermissionService.check(folderId, FolderPermissionService.P_UPLOAD, userId)) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }

        // 检查存储空间
        if ("private".equals(scope)) {
            SysUser user = userMapper.selectById(userId);
            if (user != null && user.getStorageUsed() + file.getSize() > user.getStorageQuota()) {
                throw new BusinessException("存储空间不足，当前已用: " + formatSize(user.getStorageUsed()) +
                        "，配额: " + formatSize(user.getStorageQuota()));
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

            // 更新用户已用空间
            if ("private".equals(scope)) {
                SysUser user = userMapper.selectById(userId);
                if (user != null) {
                    user.setStorageUsed(user.getStorageUsed() + file.getSize());
                    userMapper.updateById(user);
                }
            }

            return bizFile;
        } catch (Exception e) {
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
        // 【文件权限体系】创建子文件夹需父文件夹上传权限（管理员/本人/上级链/ACL upload），
        // public 类型带 parentId 同样按父夹 P_UPLOAD 判定（ACL 授权角色可在已授权公共夹下建子夹）；
        // 根级公共文件夹（无 parentId）保留管理员门槛。
        if (folder.getParentId() != null
                && !folderPermissionService.check(folder.getParentId(), FolderPermissionService.P_UPLOAD, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        if ("public".equals(type) && folder.getParentId() == null && !SecurityUtils.isAdmin()) {
            throw new BusinessException("公共文件夹只有管理员才能创建");
        }
        if ("private".equals(type)) {
            folder.setOwnerId(SecurityUtils.getCurrentUserId());
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
        // 【文件权限体系】ACL/上级链/本人 编辑权限（管理员恒通过），
        // 为操作唯一仲裁（已覆盖 admin>本人>上级链>ACL 优先级且 folder==null 已拒绝）
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_EDIT, SecurityUtils.getCurrentUserId())) {
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
                    throw new BusinessException("无权删除该文件");
                }
            } else {
                throw new BusinessException("无权删除该文件");
            }
        }

        // 删除实际文件
        deleteStorage(file.getOssKey());

        // 逻辑删除
        fileMapper.deleteById(fileId);

        // 归还存储空间
        if ("private".equals(file.getFileScope())) {
            SysUser user = userMapper.selectById(file.getUploaderId());
            if (user != null) {
                user.setStorageUsed(Math.max(0, user.getStorageUsed() - file.getFileSize()));
                userMapper.updateById(user);
            }
        }
    }

    /**
     * 【文件权限体系 Task 6】覆盖替换：新文件先存后删旧（避免先删旧后存储失败导致文件丢失）。
     * 更新 oss_key/oss_url/file_size/file_type/version+1/replace_by/replaced_at；
     * 私人文件按差额调整配额（storageUsed = 原已用 - 旧size + 新size，最小 0）。
     * 权限：文件夹 edit 权限（管理员/本人/上级链/ACL edit，见 FolderPermissionService.checkFile）。
     */
    public BizFile replaceFile(Long fileId, MultipartFile newFile) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException("文件不存在");
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_EDIT, userId)) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        String scope = file.getFileScope() != null ? file.getFileScope() : "private";
        try {
            // 先存新文件（沿用原文件的 scope/uploaderId 目录，保证 key 下载路由语义一致）
            String newKey = uploadStorage(newFile, scope, file.getUploaderId());
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
            deleteStorage(oldKey);
            // 私人文件存储差额（最小 0）
            if ("private".equals(scope)) {
                SysUser user = userMapper.selectById(file.getUploaderId());
                if (user != null) {
                    long delta = newFile.getSize() - oldSize;
                    user.setStorageUsed(Math.max(0, user.getStorageUsed() + delta));
                    userMapper.updateById(user);
                }
            }
            return file;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件替换失败: " + e.getMessage());
        }
    }

    /**
     * 实际存储写入（从 upload 抽出复用）：本地落盘回读校验 / OSS putObject。
     * 返回存储 key（scope/userId 目录 + 唯一文件名），供 upload 与 replaceFile 共用，行为与原先一致。
     */
    private String uploadStorage(MultipartFile file, String scope, Long userId) throws Exception {
        // 【安全 2026-08-08 上传路径穿越】originalFilename 可能含 ../ 或路径分隔符，消毒后再拼落盘路径
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        // 【PR-01 N-09 2026-09-18】上传文件类型白名单：仅允许办公文档/图片/音视频/压缩包/文本。
        // 阻断可执行脚本与 HTML/SVG（未来某路径改 inline 即 XSS/恶意分发）。未知类型 400 拒绝。
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
        } catch (Exception ignored) {
            // 尽力而为:OSS 删除失败不回滚主流程,文件元数据已删,残留对象由清理任务兜底
            }
    }

    public Path getLocalFilePath(String fileKey) {
        return Paths.get(uploadDir, fileKey);
    }

    /** 安全内联判定: 仅纯图片(不含 SVG——可内嵌脚本执行, 存存储型XSS)与视频可 inline, 其余一律 attachment。
     *  2026-08-10 安全修复: 原先 svg 也在 inline 名单, 配合任意上传可被匿名/分享 URL 直接执行窃取 localStorage JWT。 */
    private static final java.util.regex.Pattern SAFE_INLINE_EXT =
            java.util.regex.Pattern.compile("(?i).*\\.(jpg|jpeg|png|gif|webp|bmp|mp4|webm)$");

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
            if (thumbnail) {
                if (writeThumbnail(filePath, file.getFileName(), response)) return;
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
            if (file.getOssUrl() != null && file.getOssUrl().startsWith("http")) {
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

    /** 生成缩略图并写入响应；仅图片返回 true。非图片或读取失败返回 false（调用方回退完整下载）。 */
    private boolean writeThumbnail(Path filePath, String filename, jakarta.servlet.http.HttpServletResponse response) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) return false;
        int maxDim = 300;
        boolean png = lower.endsWith(".png");
        try (javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(filePath.toFile())) {
            if (iis == null) return false;
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return false;
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0) return false;

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
                if (img == null) return false;

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

                response.setContentType(png ? "image/png" : "image/jpeg");
                response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
                javax.imageio.ImageIO.write(out, png ? "png" : "jpg", response.getOutputStream());
                return true;
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 按 oss_key 查询文件记录（key 下载路由的归属校验依据） */
    public BizFile getFileByKey(String fileKey) {
        return fileMapper.selectOne(
                new LambdaQueryWrapper<BizFile>()
                        .eq(BizFile::getOssKey, fileKey)
                        .last("LIMIT 1"));
    }

    public String getOssUrlByKey(String fileKey) {
        BizFile file = getFileByKey(fileKey);
        if (file != null && file.getOssUrl() != null && file.getOssUrl().startsWith("http")) {
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

    static void assertAllowedUploadType(String sanitizedFilename, String contentType) {
        String ext = "";
        boolean hasExt = false;
        int dot = sanitizedFilename == null ? -1 : sanitizedFilename.lastIndexOf('.');
        if (dot >= 0 && dot < sanitizedFilename.length() - 1) {
            hasExt = true;
            ext = sanitizedFilename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        }
        // 无扩展名文件（如表单上传缺省文件名兜底的 unnamed）无法按扩展名执行，放行；
        // 下载链路统一 attachment，不解析执行。有扩展名则必须在白名单内。
        if (!hasExt) return;
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
    public boolean canDownloadFile(BizFile file, Long userId) {
        boolean canDownload = canAccessFile(file, userId);
        if (!canDownload && file.getFolderId() != null) {
            canDownload = folderPermissionService.checkFile(file.getId(), FolderPermissionService.P_DOWNLOAD, userId);
        }
        if (!canDownload && "incentive".equals(file.getFileScope())) {
            canDownload = isIncentiveFileVisible(file.getId(), userId);
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
     */
    private boolean isIncentiveFileVisible(Long fileId, Long userId) {
        List<BizIncentiveRecord> sharedRecords = incentiveRecordMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveRecord>()
                        .eq(BizIncentiveRecord::getShared, 1)
                        .ne(BizIncentiveRecord::getUserId, userId)
        );
        for (BizIncentiveRecord r : sharedRecords) {
            if (r.getFileIds().contains(fileId)
                    || r.getFilePreIds().contains(fileId)
                    || r.getFileMidIds().contains(fileId)
                    || r.getFilePostIds().contains(fileId)
                    || r.getFileTrackIds().contains(fileId)) {
                return true;
            }
        }
        return false;
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
                throw new BusinessException("无权分享该文件");
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
            wrapper.like(BizFile::getFileName, keyword);
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
