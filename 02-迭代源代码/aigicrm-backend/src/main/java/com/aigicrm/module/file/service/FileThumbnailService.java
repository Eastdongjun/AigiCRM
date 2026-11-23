package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * 图片缩略图生成与磁盘缓存。
 *
 * <p>S5.3 从 {@link FileService} 抽出。缓存键含 fileId/version/ossKey 哈希/原图 size+mtime/
 * 目标边长/编码，替换后自然失效；授权仍由调用方 downloadById 每次请求先行执行，
 * 缓存不参与鉴权。视频预览复用 {@link #thumbnailCacheRoot()} 作为缓存目录。</p>
 */
@Service
public class FileThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(FileThumbnailService.class);

    /** 缩略图最长边（列表预览 ≤300px） */
    private static final int THUMBNAIL_MAX_DIM = 300;
    /** 缩略图磁盘缓存条目上限，超过后按最旧 last-modified 清理 */
    private static final int THUMBNAIL_CACHE_MAX_ENTRIES = 2000;
    /** 每写入 N 个缩略图触发一次容量巡检（摊销目录遍历成本） */
    private static final int THUMBNAIL_CACHE_PRUNE_INTERVAL = 200;
    private static final String THUMBNAIL_CACHE_DIR_NAME = "aigicrm-thumb-cache";

    /** 缩略图磁盘缓存目录；留空则使用 java.io.tmpdir/aigicrm-thumb-cache */
    @Value("${file.thumbnail-cache-dir:}")
    private String thumbnailCacheDir;

    /** 写缓存次数计数（用于摊销容量巡检，非业务状态） */
    private final java.util.concurrent.atomic.AtomicInteger thumbnailCacheOps =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 生成缩略图并写入响应；仅图片返回 true。非图片或读取失败返回 false（调用方回退完整下载）。
     * 缓存命中时只读缓存文件，不再打开/解码原图。
     */
    boolean writeThumbnail(BizFile file, Path filePath, jakarta.servlet.http.HttpServletResponse response) {
        String filename = file.getFileName();
        String lower = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (!lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) return false;
        boolean png = lower.endsWith(".png");

        ThumbnailCache cache = resolveThumbnailCache(file, filePath, png);
        if (writeCachedThumbnail(cache.cacheFile(), png, response)) return true;

        byte[] bytes = generateThumbnailBytes(filePath, png).orElse(null);
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
    private Optional<byte[]> generateThumbnailBytes(Path filePath, boolean png) {
        try (javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(filePath.toFile())) {
            if (iis == null) return Optional.empty();
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return Optional.empty();
            javax.imageio.ImageReader reader = readers.next();
            try (AutoCloseable readerClose = reader::dispose) {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) return Optional.empty();
                ThumbnailPlan plan = thumbnailPlan(reader, w, h);
                java.awt.image.BufferedImage img = reader.read(0, plan.param());
                if (img == null) return Optional.empty();

                java.awt.image.BufferedImage out = img;
                if (plan.needScale() && (img.getWidth() != plan.width() || img.getHeight() != plan.height())) {
                    out = scaleThumbnail(img, plan.width(), plan.height(), png);
                }
                return thumbnailBytes(out, png);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 计算目标尺寸与子采样参数（S3776 拆分，语义与原内联逻辑一致）。 */
    private ThumbnailPlan thumbnailPlan(javax.imageio.ImageReader reader, int w, int h) {
        int maxDim = THUMBNAIL_MAX_DIM;
        // 大图子采样解码：只解码部分像素（解码尺寸≈2×目标），避免整图解码——5MB+ JPEG 解码后
        // 原始像素 ~96MB/张，激励查询页首屏并发生成缩略图时撑爆 -Xmx512m 堆（2026-08-09 OOM 复盘）。
        int nw = w;
        int nh = h;
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

    private Optional<byte[]> thumbnailBytes(java.awt.image.BufferedImage out, boolean png) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(16 * 1024);
        if (!javax.imageio.ImageIO.write(out, png ? "png" : "jpg", bos)) return Optional.empty();
        return Optional.of(bos.toByteArray());
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

    /** 视频预览与缩略图共用的缓存根目录。 */
    Path thumbnailCacheRoot() {
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
            moveIntoPlace(tmp, target);
            tmp = null;
        } catch (Exception e) {
            log.debug("缩略图缓存写入失败 key={}: {}", key, e.getMessage());
        } finally {
            if (tmp != null) deleteQuietly(tmp);
        }
        pruneThumbnailCacheIfNeeded(cacheRoot);
    }

    /** 优先原子 move；文件系统不支持/目标已存在时退回 REPLACE_EXISTING。 */
    private static void moveIntoPlace(Path tmp, Path target) throws java.io.IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 删除缓存文件；失败只留日志，不影响响应。 */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.debug("缩略图缓存删除失败 {}: {}", path, e.getMessage());
        }
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
                    .toList();
            int excess = entries.size() - THUMBNAIL_CACHE_MAX_ENTRIES;
            for (int i = 0; i < excess; i++) {
                deleteQuietly(entries.get(i));
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
                    .forEach(this::deleteQuietly);
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
}
