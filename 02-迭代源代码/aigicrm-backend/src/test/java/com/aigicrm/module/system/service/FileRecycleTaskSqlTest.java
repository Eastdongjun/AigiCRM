package com.aigicrm.module.system.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 【FILE-RECYCLE-TASK-001 2026-11-20】列存在性钉: RecycleService 夹清理 SQL 只准引用
 * biz_file_folder 实有的列(V1 DDL 无 updated_at; 11-14.1 补 deleted_at/deleted_by)——
 * 防止 COALESCE 型"列不存在"SQL 再度静默上线(该缺陷每晚 03:30 报错, 11-14 起未被察觉)。
 * 方式: 解析迁移 DDL 汇总 biz_file_folder 实有列集, 再扫描源码夹查询字符串逐列核对。
 */
class FileRecycleTaskSqlTest {

    private static final List<String> MIGRATION_DIRS = List.of(
            "src/main/resources/db/migration");

    @Test
    void folderCleanupSql_referencesOnlyExistingColumns() throws Exception {
        // ① 汇总 biz_file_folder 实有列: baseline DDL 建表块 + ADD COLUMN 语句
        Pattern createBlock = Pattern.compile(
                "CREATE TABLE `biz_file_folder` \\((.*?)\\) ENGINE", Pattern.DOTALL);
        Pattern addColumn = Pattern.compile(
                "ADD COLUMN (?:IF NOT EXISTS )?`(\\w+)`|ADD COLUMN (\\w+)", Pattern.CASE_INSENSITIVE);
        java.util.Set<String> columns = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String dir : MIGRATION_DIRS) {
            try (var files = Files.list(Path.of(dir))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".sql")).collect(Collectors.toList())) {
                    String sql = Files.readString(p);
                    Matcher m = createBlock.matcher(sql);
                    if (m.find()) {
                        for (String colDef : m.group(1).split(",")) {
                            String t = colDef.trim();
                            if (t.startsWith("`")) {
                                columns.add(t.substring(1, t.indexOf('`', 1)));
                            }
                        }
                    }
                    Matcher a = addColumn.matcher(sql);
                    boolean inFolderStmt = sql.contains("biz_file_folder");
                    while (inFolderStmt && a.find()) {
                        String col = a.group(1) != null ? a.group(1) : a.group(2);
                        // 仅当该迁移文件操作 biz_file_folder 才计入(粗粒度防误纳他表列)
                        columns.add(col);
                    }
                }
            }
        }
        assertTrue(columns.contains("deleted_at"), "deleted_at 应在列集(V2026.11.14.1)");
        assertFalse(columns.contains("updated_at"), "biz_file_folder 不应有 updated_at(缺陷根因)");

        // ② 扫描 RecycleService 源码中含 biz_file_folder 的 SQL 语句逐列核对(先剥注释防文档误报)
        String raw = Files.readString(
                Path.of("src/main/java/com/aigicrm/module/system/service/RecycleService.java"));
        String src = raw.replaceAll("//[^\\n]*", " ").replaceAll("/\\*.*?\\*/", " ");
        for (String stmt : src.split(";")) {
            if (!stmt.contains("biz_file_folder")) continue;
            for (String token : stmt.split("[^\\w`]+")) {
                String col = token.replace("`", "");
                // 跳过表名/关键字/绑定参数, 只核对疑似列名(字母开头且出现在列集同形词)
                if (col.equals("biz_file_folder") || col.equals("DELETE") || col.equals("FROM")
                        || col.equals("WHERE") || col.equals("AND") || col.equals("SELECT")
                        || col.equals("id") || col.equals("deleted") || col.matches("\\d+.*")
                        || col.isEmpty() || col.equals("folder_id") || col.equals("COALESCE")
                        || col.equals("queryForList") || col.equals("jdbc") || col.equals("update")) {
                    continue;
                }
                // 形如列名的标识符若形似 updated_at 但不在实有列集 → 红
                if (col.endsWith("_at") && !columns.contains(col)) {
                    assertFalse(true, "RecycleService 夹 SQL 引用了 biz_file_folder 不存在的列: " + col);
                }
            }
        }
    }
}
