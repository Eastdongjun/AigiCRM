package com.aigicrm.module.file.service;

/** 文件名/大小格式化的小工具；FileService 与 FileStorageService 共用。 */
final class FileNames {

    private static final long BYTES_PER_KB = 1024L;

    private FileNames() {
    }

    /** 取文件名扩展(无扩展名返回 null); 供替换场景兜底继承。 */
    static String extOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    static String formatSize(long bytes) {
        if (bytes < BYTES_PER_KB) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
