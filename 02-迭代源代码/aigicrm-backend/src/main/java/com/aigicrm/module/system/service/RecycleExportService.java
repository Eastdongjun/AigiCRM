package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 【A3 2026-11-18】回收站 ZIP 导出（用户拍板 #1/#2/#3 + QC 挂载方案）。
 *
 * <p>边界与红线：</p>
 * <ul>
 *   <li>仅 admin（功能码 system:recycle:export + 服务层 isAdmin 双收口，拍板 #2 ADMIN_ONLY）；</li>
 *   <li>只读 deleted=1 行（显式 SQL，不触碰恢复链）；</li>
 *   <li>上限：10 万行/类型、文件总量 20GB——超限 400 提示缩小范围（拍板 #1）；</li>
 *   <li>ZIP 不加密（用户拍板）；先写 .tmp 再原子 rename（QC 方案 #3，防半包被下载）；</li>
 *   <li>下载 7 天过期（410），不因下载续期；过期清理=定时任务删 ZIP+置 expired；</li>
 *   <li>创建/下载/删除全审计；导出含个人信息，admin-only+7 天+审计三重收口；</li>
 *   <li>ZIP 条目名 sanitize（防 ../ 路径穿越，复用文件模块规则）；CSV UTF-8 BOM；
 *       不导出密码哈希/JWT/密钥/session，人员仅必要字段。</li>
 * </ul>
 *
 * <p>状态机：pending→running→ready/failed；ready+expires_at<now→expired。
 * 领取防重=单条件 UPDATE（等价 SKIP LOCKED 语义：仅 pending 可被领取，多 worker 竞争时恰好一个成功）。</p>
 */
@Service
@RequiredArgsConstructor
public class RecycleExportService {

    private static final Logger log = LoggerFactory.getLogger(RecycleExportService.class);
    private static final Set<String> TYPES = Set.of("customer", "user", "file", "task", "incentive", "reminder");
    private static final long MAX_ROWS_PER_TYPE = 100_000;   // 拍板 #1
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024 * 1024; // 20GB
    private static final int BATCH = 500;                    // 计划 §1.5 分批
    private static final int EXPIRE_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final com.aigicrm.security.SecurityAuditService securityAudit;

    /** 【QC 方案 #5】本地/dev/测试服用本地路径兜底；生产 NAS 挂 /app/exports。 */
    @Value("${file.export-dir:${EXPORT_DIR:./exports}}")
    private String exportDir;

    /** 专用单线程池：串行生成防资源挤占；@PreDestroy 优雅关闭。 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "recycle-export");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdownWorker() {
        worker.shutdown();
    }

    // ==================== 端点服务 ====================

    /** 创建导出（admin）：校验→插 pending→异步生成。 */
    public Map<String, Object> create(List<String> types, Map<String, Object> filter) {
        requireAdmin();
        if (types == null || types.isEmpty()) throw new BusinessException("导出类型不能为空");
        for (String t : types) {
            if (!TYPES.contains(t)) throw new BusinessException("非法导出类型: " + t);
        }
        // 拍板 #1 上限前置校验（行数；文件量在生成中累计并熔断）
        for (String t : types) {
            long n = countDeleted(t, filter);
            if (n > MAX_ROWS_PER_TYPE) {
                throw new BusinessException("类型 " + t + " 已删除行数 " + n + " 超过单类型上限 10 万，请缩小过滤范围");
            }
        }
        // 【QC6 缺陷2】导出目录 fail-fast：不可写立即报错并给配置指引（防异步 failed 哑弹）
        try {
            Path dir = Paths.get(exportDir, String.valueOf(tenantId()));
            Files.createDirectories(dir);
            if (!Files.isWritable(dir)) {
                throw new IOException("目录不可写: " + dir);
            }
        } catch (Exception e) {
            throw new BusinessException("导出目录不可用(" + e.getMessage() + ")——请检查 EXPORT_DIR 配置与目录属主(容器内需 appuser 可写, NAS 初始化用宿主侧 chown)");
        }
        String exportNo = "EXP" + LocalDateTime.now().withNano(0).toString().replace("-", "").replace(":", "").replace("T", "").replace(" ", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        Long uid = SecurityUtils.getCurrentUserId();
        String typesJson = toJson(types);
        String filterJson = filter == null || filter.isEmpty() ? null : toJson(filter);
        jdbc.update("INSERT INTO biz_recycle_export (tenant_id, export_no, requested_by, types_json, filter_json, status, expires_at) "
                        + "VALUES (?,?,?,?,?,'pending', TIMESTAMPADD(DAY, " + EXPIRE_DAYS + ", NOW()))",
                tenantId(), exportNo, uid, typesJson, filterJson);
        Long id = jdbc.queryForObject("SELECT id FROM biz_recycle_export WHERE export_no=? AND tenant_id=?", Long.class, exportNo, tenantId());
        audit("recycle_export_create", "exportNo=" + exportNo + " types=" + typesJson + " by=" + uid);
        // worker 线程无 SecurityContext: 主线程先捕获 tenantId 再传入
        Long tenant = tenantId();
        worker.submit(() -> runSafely(id, exportNo, new ArrayList<>(types), filter == null ? Map.of() : filter, tenant));
        return view(jdbc.queryForMap("SELECT * FROM biz_recycle_export WHERE id=?", id));
    }

    /** 列表（admin）。 */
    public List<Map<String, Object>> list() {
        requireAdmin();
        return jdbc.queryForList("SELECT * FROM biz_recycle_export WHERE tenant_id=? ORDER BY id DESC LIMIT 100", tenantId())
                .stream().map(this::view).toList();
    }

    /** 下载（admin + 未过期）：返回 {filePath, fileName, checksum}，流式由 controller 落地。 */
    public Map<String, Object> download(Long id) {
        requireAdmin();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM biz_recycle_export WHERE id=? AND tenant_id=?", id, tenantId());
        if (row == null || row.isEmpty()) throw new BusinessException("导出记录不存在");
        if (!"ready".equals(row.get("status"))) throw new BusinessException("导出未就绪或已失效: " + row.get("status"));
        LocalDateTime exp = toLdt(row.get("expires_at"));
        if (exp != null && exp.isBefore(LocalDateTime.now())) {
            jdbc.update("UPDATE biz_recycle_export SET status='expired' WHERE id=?", id);
            throw new BusinessException(410, "下载链接已过期（生成后 7 天），请重新导出");
        }
        jdbc.update("UPDATE biz_recycle_export SET downloaded_at=NOW() WHERE id=?", id);
        audit("recycle_export_download", "id=" + id + " exportNo=" + row.get("export_no"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("filePath", String.valueOf(row.get("file_path")));
        out.put("fileName", row.get("export_no") + ".zip");
        out.put("checksumSha256", row.get("checksum_sha256"));
        out.put("fileSize", row.get("file_size"));
        return out;
    }

    /** 删除（admin）：物理删 ZIP + 台账行。 */
    @Transactional
    public void delete(Long id) {
        requireAdmin();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM biz_recycle_export WHERE id=? AND tenant_id=?", id, tenantId());
        if (row == null || row.isEmpty()) throw new BusinessException("导出记录不存在");
        Object fp = row.get("file_path");
        if (fp != null) {
            try {
                Files.deleteIfExists(Paths.get(String.valueOf(fp)));
            } catch (IOException e) {
                log.warn("导出 ZIP 物理删除失败(继续删台账) path={}", fp, e);
            }
        }
        jdbc.update("DELETE FROM biz_recycle_export WHERE id=?", id);
        audit("recycle_export_delete", "id=" + id + " exportNo=" + row.get("export_no"));
    }

    /** 过期清理（每小时）：删 ZIP + 置 expired。 */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 600_000)
    public void purgeExpired() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, file_path FROM biz_recycle_export WHERE status='ready' AND expires_at < NOW() LIMIT 50");
        for (Map<String, Object> r : rows) {
            Object fp = r.get("file_path");
            if (fp != null) {
                try {
                    Files.deleteIfExists(Paths.get(String.valueOf(fp)));
                } catch (IOException e) {
                    log.warn("过期 ZIP 删除失败 id={} path={}", r.get("id"), fp, e);
                }
            }
            jdbc.update("UPDATE biz_recycle_export SET status='expired', file_path=NULL WHERE id=?", r.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("[recycle-export] 过期清理 {} 条", rows.size());
            audit("recycle_export_purge", "count=" + rows.size());
        }
    }

    // ==================== 生成（异步 worker） ====================

    private void runSafely(Long id, String exportNo, List<String> types, Map<String, Object> filter, Long tenant) {
        try {
            // 状态机领取：仅 pending 可被领取（等价 SKIP LOCKED 的互斥语义，多 worker 安全）
            int claimed = jdbc.update("UPDATE biz_recycle_export SET status='running' WHERE id=? AND status='pending'", id);
            if (claimed != 1) {
                log.warn("[recycle-export] 领取失败(非 pending) id={}", id);
                return;
            }
            Path dir = Paths.get(exportDir, String.valueOf(tenant));
            Files.createDirectories(dir);
            Path tmp = dir.resolve("." + exportNo + ".zip.tmp");
            Path fin = dir.resolve(exportNo + ".zip");
            long bytes = generateZip(tmp, exportNo, types, filter);
            Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); // QC 方案 #3 原子 rename
            String sha = sha256(fin);
            jdbc.update("UPDATE biz_recycle_export SET status='ready', file_path=?, file_size=?, checksum_sha256=?, "
                            + "expires_at=TIMESTAMPADD(DAY, " + EXPIRE_DAYS + ", NOW()) WHERE id=?",
                    fin.toString(), bytes, sha, id);
            log.info("[recycle-export] ready id={} no={} bytes={} sha={}", id, exportNo, bytes, sha);
        } catch (Exception e) {
            log.error("[recycle-export] 生成失败 id={} no={}", id, exportNo, e);
            jdbc.update("UPDATE biz_recycle_export SET status='failed', error_msg=? WHERE id=?",
                    (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(490, e.getMessage().length()))), id);
        }
    }

    /** 流式生成 ZIP（每类 500 行分批；文件内容流式入包；20GB 熔断）。返回累计字节。 */
    private long generateZip(Path tmp, String exportNo, List<String> types, Map<String, Object> filter) throws IOException {
        long total = 0;
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("exportNo", exportNo);
        manifest.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        manifest.put("types", types);
        manifest.put("filter", filter);
        manifest.put("version", 1);
        Map<String, Long> counts = new LinkedHashMap<>();
        StringBuilder checksums = new StringBuilder();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
            for (String t : types) {
                long n = writeTypeCsv(zip, t, filter, checksums);
                counts.put(t, n);
                total += n * 128; // CSV 行粗略计入
            }
            // 文件内容（仅 file 类型被勾选时打包 deleted=1 且内容仍存在的文件）
            List<Map<String, Object>> files = types.contains("file")
                    ? jdbc.queryForList("SELECT id, file_name, oss_key FROM biz_file WHERE deleted=1 ORDER BY id LIMIT " + MAX_ROWS_PER_TYPE)
                    : List.of();
            long packed = 0;
            for (Map<String, Object> f : files) {
                long fid = ((Number) f.get("id")).longValue();
                String safe = sanitizeZipEntryName(String.valueOf(f.get("file_name")), fid);
                byte[] content = readContent(String.valueOf(f.get("oss_key")));
                if (content == null) continue; // contentMissing: 清单已含行, 不打包内容
                total += content.length;
                if (total > MAX_FILE_BYTES) {
                    throw new BusinessException("导出文件总量超过 20GB 上限，请缩小范围（去掉文件内容或分批导出）");
                }
                zip.putNextEntry(new ZipEntry("files/" + fid + "/" + safe));
                zip.write(content);
                zip.closeEntry();
                packed++;
            }
            counts.put("filesPacked", packed);
            manifest.put("counts", counts);
            manifest.put("restoreHint", Map.of(
                    "file", "按原 customer/folder 恢复; 若原客户已删除请先恢复客户",
                    "task/incentive/reminder", "按原 customer_id 恢复; 原客户已删除先恢复客户",
                    "customer/user", "按原 owner/dept/office 恢复"));
            byte[] mb = (toJson(manifest) + "\n").getBytes(StandardCharsets.UTF_8);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(mb);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("checksums.sha256"));
            zip.write(checksums.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return Files.size(tmp);
    }

    /** 单类型 CSV 分批写出（UTF-8 BOM；显式 deleted=1；返回行数）。 */
    private long writeTypeCsv(ZipOutputStream zip, String type, Map<String, Object> filter, StringBuilder checksums) throws IOException {
        String sql = selectSql(type, filter);
        String header = headerOf(type);
        zip.putNextEntry(new ZipEntry("data/" + csvName(type)));
        zip.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM 三字节(write(int) 只写低8位)
        zip.write((header + "\n").getBytes(StandardCharsets.UTF_8));
        long lastId = 0;
        long rows = 0;
        while (true) {
            List<Map<String, Object>> batch = jdbc.queryForList(sql + " AND id > ? ORDER BY id LIMIT " + BATCH, lastId);
            if (batch.isEmpty()) break;
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : batch) {
                sb.append(csvRow(type, r)).append('\n');
                lastId = ((Number) r.get("id")).longValue();
                rows++;
            }
            byte[] b = sb.toString().getBytes(StandardCharsets.UTF_8);
            zip.write(b);
        }
        zip.closeEntry();
        checksums.append("data/").append(csvName(type)).append(": ").append(rows).append(" rows\n");
        return rows;
    }

    // ==================== 类型元数据（列/行；不导敏感字段） ====================

    private String selectSql(String t, Map<String, Object> filter) {
        String range = "";
        if (filter != null && filter.get("deletedFrom") != null) {
            range = " AND deleted_at >= '" + String.valueOf(filter.get("deletedFrom")).replace("'", "") + "'";
        }
        if (filter != null && filter.get("deletedTo") != null) {
            range += " AND deleted_at <= '" + String.valueOf(filter.get("deletedTo")).replace("'", "") + "'";
        }
        return switch (t) {
            case "customer" -> "SELECT id, customer_name, owner_id, dept_id, deleted_at, deleted_by, deleted_reason FROM biz_customer WHERE deleted=1" + range;
            case "user" -> "SELECT id, username, real_name, dept_id, status, leave_status, deleted_at, deleted_by FROM sys_user WHERE deleted=1" + range;
            case "file" -> "SELECT id, file_name, file_scope, folder_id, uploader_id, oss_key, deleted_at, deleted_by, deleted_reason FROM biz_file WHERE deleted=1" + range;
            case "task" -> "SELECT id, title, assignee_id, customer_id, deleted_at, deleted_by, deleted_reason FROM biz_task WHERE deleted=1" + range;
            case "incentive" -> "SELECT id, user_id, customer_id, category_id, deleted_at, deleted_by, deleted_reason FROM biz_incentive_record WHERE deleted=1" + range;
            case "reminder" -> "SELECT id, customer_id, owner_id, reminder_type, deleted_at, deleted_by, deleted_reason FROM biz_reminder WHERE deleted=1" + range;
            default -> throw new BusinessException("非法类型");
        };
    }

    private String headerOf(String t) {
        return switch (t) {
            case "customer" -> "id,customer_name,owner_id,dept_id,deleted_at,deleted_by,deleted_reason";
            case "user" -> "id,username,real_name,dept_id,status,leave_status,deleted_at,deleted_by";
            case "file" -> "id,file_name,file_scope,folder_id,uploader_id,oss_key,contentMissing,deleted_at,deleted_by,deleted_reason";
            case "task" -> "id,title,assignee_id,customer_id,deleted_at,deleted_by,deleted_reason";
            case "incentive" -> "id,user_id,customer_id,category_id,deleted_at,deleted_by,deleted_reason";
            case "reminder" -> "id,customer_id,owner_id,reminder_type,deleted_at,deleted_by,deleted_reason";
            default -> throw new BusinessException("非法类型");
        };
    }

    private String csvName(String t) {
        return switch (t) {
            case "customer" -> "customers.csv";
            case "user" -> "users.csv";
            case "file" -> "files.csv";
            case "task" -> "tasks.csv";
            case "incentive" -> "incentives.csv";
            case "reminder" -> "reminders.csv";
            default -> throw new BusinessException("非法类型");
        };
    }

    private String csvRow(String t, Map<String, Object> r) {
        return switch (t) {
            case "customer" -> csv(r.get("id"), r.get("customer_name"), r.get("owner_id"), r.get("dept_id"),
                    r.get("deleted_at"), r.get("deleted_by"), r.get("deleted_reason"));
            case "user" -> csv(r.get("id"), r.get("username"), r.get("real_name"), r.get("dept_id"),
                    r.get("status"), r.get("leave_status"), r.get("deleted_at"), r.get("deleted_by"));
            case "file" -> csv(r.get("id"), r.get("file_name"), r.get("file_scope"), r.get("folder_id"),
                    r.get("uploader_id"), r.get("oss_key"), r.get("oss_key") == null, r.get("deleted_at"), r.get("deleted_by"), r.get("deleted_reason"));
            case "task" -> csv(r.get("id"), r.get("title"), r.get("assignee_id"), r.get("customer_id"),
                    r.get("deleted_at"), r.get("deleted_by"), r.get("deleted_reason"));
            case "incentive" -> csv(r.get("id"), r.get("user_id"), r.get("customer_id"), r.get("category_id"),
                    r.get("deleted_at"), r.get("deleted_by"), r.get("deleted_reason"));
            case "reminder" -> csv(r.get("id"), r.get("customer_id"), r.get("owner_id"), r.get("reminder_type"),
                    r.get("deleted_at"), r.get("deleted_by"), r.get("deleted_reason"));
            default -> throw new BusinessException("非法类型");
        };
    }

    private String csv(Object... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            String v = cols[i] == null ? "" : String.valueOf(cols[i]);
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    // ==================== 工具 ====================

    private long countDeleted(String t, Map<String, Object> filter) {
        String tbl = switch (t) {
            case "customer" -> "biz_customer";
            case "user" -> "sys_user";
            case "file" -> "biz_file";
            case "task" -> "biz_task";
            case "incentive" -> "biz_incentive_record";
            case "reminder" -> "biz_reminder";
            default -> throw new BusinessException("非法类型");
        };
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tbl + " WHERE deleted=1", Long.class);
        return n == null ? 0 : n;
    }

    /** 读已删文件内容（本地存储按 oss_key 路径；不存在→null=contentMissing）。 */
    private byte[] readContent(String ossKey) {
        if (ossKey == null || ossKey.isBlank()) return null;
        try {
            Path p = Paths.get(ossKey.startsWith("/") ? ossKey : Paths.get(exportDir, ossKey).toString());
            if (!p.isAbsolute()) p = Paths.get(ossKey);
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null; // 内容已物理清除(被 purge) → 清单行保留, contentMissing
        }
    }

    /** 【复用 FileService 规则】路径穿越/分隔符/控制字符清洗。 */
    static String sanitizeZipEntryName(String name, Long fallbackId) {
        String n = name == null || name.isBlank() ? ("file-" + fallbackId) : name;
        n = n.replaceAll("\\p{Cntrl}", "_").replace("..", "_").replace("/", "_").replace("\\", "_").trim();
        return n.isBlank() ? ("file-" + fallbackId) : n;
    }

    private String sha256(Path f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(f)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("SHA-256 计算失败: " + e.getMessage(), e);
        }
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        LocalDateTime exp = toLdt(row.get("expires_at"));
        out.put("expired", "ready".equals(row.get("status")) && exp != null && exp.isBefore(LocalDateTime.now()));
        return out;
    }

    /** DATETIME 兼容读取（MySQL 驱动=LocalDateTime / H2=Timestamp）。 */
    private static LocalDateTime toLdt(Object v) {
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private void requireAdmin() {
        if (!SecurityUtils.isAdmin()) throw new BusinessException(403, "回收站导出仅系统管理员可操作");
    }

    private Long tenantId() {
        var u = SecurityUtils.getCurrentUser();
        Long id = u == null ? null : u.getCurrentGroupId();
        return id == null ? 1L : id;
    }

    private void audit(String action, String detail) {
        if (securityAudit == null) return;
        try {
            securityAudit.permissionChange(action, detail);
        } catch (Exception ignored) {
            // 审计失败不阻断
        }
    }

}
