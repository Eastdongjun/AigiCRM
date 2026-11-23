package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.config.OssConfig;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * 文件存储：上传落盘/OSS 写入、读回、删除、内容存在性、下载路径。
 *
 * <p>S5.3 从 {@link FileService} 抽出。原类同时承担查询、权限、版本、缩略图、
 * 批量、分享与存储，2285 行；本类只保留字节存储与上传校验，
 * 不依赖 {@link FileService}（依赖方向单向）。</p>
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final String LIT_PUBLIC = "public";
    private static final String LIT_PRIVATE = "private";
    private static final String LIT_INCENTIVE = "incentive";
    private static final String LIT_FABUSHI = "fabushi";
    private static final String LIT_HTTPS = "https://";
    private static final String LIT_API_FILE_DOWNLOAD = "/api/file/download/";
    private static final String LIT_UNNAMED = "unnamed";
    private static final String LIT_NO_PERMISSION = "没有访问权限，请联系管理员授权";

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** 上传类型白名单（扩展名判定为主，MIME 仅作参考不单独信任）。 */
    static final Set<String> ALLOWED_UPLOAD_EXTS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic",
            "mp3", "wav", "m4a", "mp4", "mov", "avi", "mkv",
            "zip", "rar", "7z", "tar", "gz");

    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final FolderPermissionService folderPermissionService;
    private final FileFolderService fileFolderService;
    private final ObjectProvider<OSS> ossClientProvider;
    private final OssConfig ossConfig;
    /** 【2026-11-17 阶段三】上传病毒扫描钩子；ObjectProvider 保持可选，单测可传 null。 */
    private final ObjectProvider<FileScanService> fileScanServiceProvider;

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

    /** 本地下载路径 / OSS URL（上传与替换共用，避免两处各拼一次）。 */
    public String fileUrl(String fileKey) {
        if (isLocal()) {
            return LIT_API_FILE_DOWNLOAD + fileKey;
        }
        return LIT_HTTPS + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + fileKey;
    }

    /** scope 白名单 + 无夹公共上传门槛（S3776 拆分，语义与原内联校验一致）。 */
    private void validateUploadScope(String scope, Long folderId) {
        // 【安全加固】scope 白名单：允许 public/private/incentive/fabushi，未识别 scope 直接拒绝
        // （与 pageFiles/getFolderTree 的 scope 白名单对称，防止任意 scope 落盘后绕过查询过滤/占用存储）
        if (scope == null || !(LIT_PUBLIC.equals(scope) || LIT_PRIVATE.equals(scope)
                || LIT_INCENTIVE.equals(scope) || LIT_FABUSHI.equals(scope))) {
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
            throw new BusinessException(403, LIT_NO_PERMISSION);
        }
        // 【法布施一期 2026-11-15】法布施空间上传=发布动作: 与 FabushiPostController /posts 同码门槛
        // (fabushi:create, 需求 G2 独立命名空间裁决——附件不进文件管理列表, 但计个人配额)。
        // 注意 scope 判定与 hasAuthority 必须拆行: 同行会让命令式码提取把裸 LIT_FABUSHI 串
        // 混进 binding-coverage 码集, 造成"码未绑定"假红(2026-11-15 实测)。
        boolean fabushiScope = LIT_FABUSHI.equals(scope);
        if (fabushiScope && !SecurityUtils.hasAuthority("fabushi:create")) {
            throw new BusinessException(403, LIT_NO_PERMISSION);
        }
        // 【文件权限体系】上传到文件夹需文件夹上传权限（管理员/本人/上级链/ACL upload）。
        // 【FILE-FABU-FOLDER-001】法布施系统夹例外: 无 ACL/无属主, 通道门槛已在 FileController.upload
        // 路由层判定(书籍=fabushi:book:manage; 分享=fabushi:create 上面同码门), 此处不重复走夹级判定。
        if (folderId != null && !fileFolderService.isFabushiSystemFolder(folderId)
                && !folderPermissionService.check(folderId, FolderPermissionService.P_UPLOAD, userId)) {
            throw new BusinessException(403, LIT_NO_PERMISSION);
        }
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
                    FileNames.formatSize(user != null ? user.getStorageUsed() : 0L) +
                    "，配额: " + FileNames.formatSize(user != null ? user.getStorageQuota() : 0L));
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
            String url = fileUrl(fileKey);

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
            bizFile.setOssUrl(url);
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

    /**
     * 实际存储写入（从 upload 抽出复用）：本地落盘回读校验 / OSS putObject。
     * 返回存储 key（scope/userId 目录 + 唯一文件名），供 upload 与 replaceFile 共用。
     */
    String uploadStorage(MultipartFile file, String scope, Long userId) throws IOException {
        return uploadStorage(file, scope, userId, null);
    }

    /** 本地存储路径扫描；OSS 模式暂无本地字节，扫描器在下载/转存链路上补（部署期配置）。 */
    void scanStored(String key) {
        FileScanService scanner = fileScanServiceProvider == null
                ? null : fileScanServiceProvider.getIfAvailable();
        if (scanner == null || key == null) return;
        scanner.scan(Paths.get(uploadDir, key));
    }

    /**
     * @param fallbackExt 替换场景的旧文件扩展兜底: 客户端未提供文件名(消毒后无扩展名)时,
     *                    继承旧文件扩展再过白名单——避免"必须带文件名"破坏既有替换链路
     */
    String uploadStorage(MultipartFile file, String scope, Long userId, String fallbackExt) throws IOException {
        // 【安全 2026-08-08 上传路径穿越】originalFilename 可能含 ../ 或路径分隔符，消毒后再拼落盘路径
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        // 【PR-01 N-09 2026-09-18】上传文件类型白名单：仅允许办公文档/图片/音视频/压缩包/文本。
        // 阻断可执行脚本与 HTML/SVG（未来某路径改 inline 即 XSS/恶意分发）。未知类型 400 拒绝。
        if (fallbackExt != null && !fallbackExt.isBlank() && originalFilename.lastIndexOf('.') < 0) {
            originalFilename = originalFilename + "." + fallbackExt;
        }
        assertAllowedUploadType(originalFilename);
        // 【U1 2026-11-17】.pdf 扩展名须含 %PDF- 魔数(前 1024 字节内, PDF 规范 7.5.1)——
        // 挡"任意内容改名 .pdf"的内容伪装(T5f 实测: 纯文本 fake.pdf 可上传挂载致阅读器打不开)
        if ("pdf".equals(FileNames.extOf(originalFilename))) {
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
    void deleteStorage(String fileKey) {
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
                OSSObject obj = ossClient().getObject(ossConfig.getBucketName(), fileKey);
                try (InputStream in = obj.getObjectContent()) {
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
    void writeStoredContentTo(String fileKey, OutputStream out) throws IOException {
        if (isLocal()) {
            Path path = Paths.get(uploadDir, fileKey);
            if (!Files.exists(path)) throw new FileNotFoundException(fileKey);
            Files.copy(path, out);
        } else {
            OSSObject obj = ossClient().getObject(ossConfig.getBucketName(), fileKey);
            try (InputStream in = obj.getObjectContent()) {
                in.transferTo(out);
            } finally {
                obj.close();
            }
        }
    }

    public boolean contentExists(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) return false;
        if (isLocal()) {
            return Files.exists(Paths.get(uploadDir, fileKey));
        }
        return ossClient().doesObjectExist(ossConfig.getBucketName(), fileKey);
    }

    /**
     * 【安全 2026-08-08 上传路径穿越】文件名消毒：只保留最后一段文件名，去掉路径分隔符(/ \)、
     * .. 序列与控制字符，防止 originalFilename 含 ../../ 时写出 uploadDir 之外。
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

    static void assertAllowedUploadType(String sanitizedFilename) {
        // 【GPT三轮 FILE-P1-008 2026-09-22】无扩展名文件拒绝——白名单体系下"未知类型"不默认放行。
        // 前端/小程序无 unnamed 兜底依赖(仅 sanitizeFilename 对穿越序列的消毒产物, 该产物在类型层拒绝属预期)
        if (sanitizedFilename == null || sanitizedFilename.isBlank()) {
            throw new BusinessException(400, "不支持的文件类型: 缺少扩展名");
        }
        int dot = sanitizedFilename.lastIndexOf('.');
        if (dot < 0 || dot == sanitizedFilename.length() - 1) {
            throw new BusinessException(400, "不支持的文件类型: 缺少扩展名");
        }
        String ext = sanitizedFilename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
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
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(1024);
        } catch (IOException e) {
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

    /** 保留原文件名，重名时加后缀：报告.pdf → 报告(1).pdf → 报告(2).pdf */
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
        }
        return ossClient().doesObjectExist(ossConfig.getBucketName(), dir + "/" + filename);
    }
}
