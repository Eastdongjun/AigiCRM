package com.aigicrm.common.util;

import com.aigicrm.common.BusinessException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 【BS-D 2026-09-19】Excel 批量导入的资源上限(拒绝服务防护)。
 *
 * <p>第二轮深度验证 STRIDE-D 盲区:导入未评估文件大小/行数——超大文件或百万行
 * 会把解析与逐行建客拖垮整个实例(多集团共享同一进程)。统一上限:</p>
 *
 * <ul>
 *   <li>文件大小 ≤ 5MB(xlsx 压缩比下已远超 5000 行模板量级);</li>
 *   <li>数据行数 ≤ 5000(监听器每行回调时检查,超限即中断读取并整体报错,
 *       不产生"半量已入库"的部分状态——异常随事务/调用方语义回滚或整体失败);</li>
 *   <li>扩展名仅 .xlsx/.xls(既有口径,收口到一处)。</li>
 * </ul>
 */
public final class ImportGuard {

    public static final long MAX_IMPORT_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_IMPORT_ROWS = 5000;

    private ImportGuard() {
    }

    /** 导入端点入口统一校验:非空/扩展名/大小。 */
    public static void checkFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 格式的文件");
        }
        if (file.getSize() > MAX_IMPORT_FILE_BYTES) {
            throw new BusinessException("导入文件过大（上限 5MB），请拆分后分批导入");
        }
    }

    /** 监听器逐行回调时检查累计行数,超限抛出中断解析。 */
    public static void checkRow(int rowNum) {
        if (rowNum > MAX_IMPORT_ROWS) {
            throw new BusinessException("导入数据超过 " + MAX_IMPORT_ROWS + " 行上限，请拆分后分批导入");
        }
    }
}
