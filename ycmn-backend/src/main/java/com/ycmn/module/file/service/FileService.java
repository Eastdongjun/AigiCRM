package com.ycmn.module.file.service;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.common.BusinessException;
import com.ycmn.config.OssConfig;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.entity.BizFileShare;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFileShareMapper;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final BizFileMapper fileMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileShareMapper fileShareMapper;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final SysUserMapper userMapper;
    private final OSS ossClient;
    private final OssConfig ossConfig;
    private final FolderPermissionService folderPermissionService;

    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private boolean isLocal() {
        return "local".equalsIgnoreCase(storageType);
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

        for (BizFileFolder folder : folders) {
            long count = fileMapper.selectCount(
                    new LambdaQueryWrapper<BizFile>()
                            .eq(BizFile::getFolderId, folder.getId())
                            .eq(BizFile::getDeleted, 0)
            );
            folder.setFileCount((int) count);
        }

        return buildFolderTree(folders, 0L);
    }

    public IPage<BizFile> pageFiles(int current, int size, Long folderId, String scope, String keyword, Long userId) {
        LambdaQueryWrapper<BizFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizFile::getDeleted, 0);

        if (folderId != null) {
            wrapper.eq(BizFile::getFolderId, folderId);
        }
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(BizFile::getFileScope, scope);
            if ("private".equals(scope)) {
                wrapper.eq(BizFile::getUploaderId, userId);
            } else if ("incentive".equals(scope)) {
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
        // 【文件权限体系】可见性过滤：夹内文件仅返回用户可见文件夹中的（本人创建/上级链/ACL view/public），
        // 无文件夹文件保持原 scope 规则不变；folderId 指定查询时若文件夹不可见则返回空页
        if ("public".equals(scope) || "private".equals(scope)) {
            List<BizFileFolder> folders = folderMapper.selectList(
                    new LambdaQueryWrapper<BizFileFolder>()
                            .eq(BizFileFolder::getFolderType, scope));
            Set<Long> visibleIds = visibleFolderIds(folders, userId);
            if (folderId != null) {
                if (!visibleIds.contains(folderId)) {
                    return new Page<>(current, size, 0);
                }
            } else if (visibleIds.isEmpty()) {
                wrapper.isNull(BizFile::getFolderId);
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
            String dir = scope + "/" + userId;
            String fileKey = dir + "/" + resolveUniqueFilename(dir, originalFilename);
            String fileUrl;

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
                fileUrl = "/api/file/download/" + fileKey;
            } else {
                // OSS 存储
                InputStream inputStream = file.getInputStream();
                ossClient.putObject(ossConfig.getBucketName(), fileKey, inputStream);
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
        // 【文件权限体系】创建子文件夹需父文件夹上传权限（管理员/本人/上级链/ACL upload）
        if (folder.getParentId() != null
                && !folderPermissionService.check(folder.getParentId(), FolderPermissionService.P_UPLOAD, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        if ("public".equals(type) && !SecurityUtils.isAdmin()) {
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
        // 【文件权限体系】ACL/上级链/本人 编辑权限（管理员恒通过），与 checkFolderManagePermission 并存
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_EDIT, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        checkFolderManagePermission(folder);
        folder.setFolderName(newName.trim());
        folderMapper.updateById(folder);
    }

    public void deleteFolder(Long folderId) {
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【文件权限体系】ACL/上级链/本人 删除权限（管理员恒通过），与 checkFolderManagePermission 并存
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_DELETE, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
        checkFolderManagePermission(folder);

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

        folderMapper.deleteById(folderId);
    }

    /**
     * 文件夹改名/删除权限：公共文件夹仅管理员，私人文件夹仅所有者本人
     */
    private void checkFolderManagePermission(BizFileFolder folder) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if ("public".equals(folder.getFolderType())) {
            throw new BusinessException("公共文件夹只有管理员才能管理");
        } else if ("private".equals(folder.getFolderType())) {
            if (!Objects.equals(folder.getOwnerId(), SecurityUtils.getCurrentUserId())) {
                throw new BusinessException("只能管理自己的文件夹");
            }
        } else {
            // 【安全修复 FEAT-20260804-FILE-AUTHZ】未识别的文件夹类型默认拒绝，而不是默认放行。
            throw new BusinessException("无权管理该文件夹");
        }
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
        try {
            if (isLocal()) {
                Path filePath = Paths.get(uploadDir, file.getOssKey());
                Files.deleteIfExists(filePath);
            } else {
                ossClient.deleteObject(ossConfig.getBucketName(), file.getOssKey());
            }
        } catch (Exception ignored) {}

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

    public Path getLocalFilePath(String fileKey) {
        return Paths.get(uploadDir, fileKey);
    }

    public void downloadById(Long fileId, String range, jakarta.servlet.http.HttpServletResponse response) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) {
            response.setStatus(404);
            return;
        }

        // 归属校验：只有上传者、被分享者、管理员可以下载
        Long userId = SecurityUtils.getCurrentUserId();
        if (!canAccessFile(file, userId)) {
            response.setStatus(403);
            return;
        }
        // 【文件权限体系】夹内文件追加文件夹下载权限与下载开关校验（开关 1=允许下载，0/空=仅预览）
        if (file.getFolderId() != null) {
            if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_DOWNLOAD, userId)) {
                response.setStatus(403);
                return;
            }
            BizFileFolder folder = folderMapper.selectById(file.getFolderId());
            if (folder != null && (folder.getDownloadable() == null || folder.getDownloadable() != 1)) {
                response.setStatus(403);
                return;
            }
        }
        try {
            Path filePath = Paths.get(uploadDir, file.getOssKey());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                String filename = file.getFileName();
                String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mt = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                String disp = filename.matches("(?i).*\\.(jpg|jpeg|png|gif|webp|bmp|svg|mp4|webm)$") ? "inline" : "attachment";

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
        } catch (Exception ignored) {
        }
        response.setStatus(404);
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
            return ossClient.doesObjectExist(ossConfig.getBucketName(), dir + "/" + filename);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
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
        wrapper.in(BizFile::getId, fileIds);
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
        return fileMapper.selectCount(new LambdaQueryWrapper<BizFile>().in(BizFile::getId, fileIds));
    }
}
