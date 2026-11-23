package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 文件查询的共享条件拼装。
 *
 * <p>原为 {@link FileService#applyGlobalKeyword} 私有方法；S5.3 拆分后
 * 文件列表与“共享给我”两个查询都要用，抽成包内静态助手，避免复制口径。</p>
 */
final class FileQuerySupport {

    private FileQuerySupport() {
    }

    /**
     * 全局关键字：文件名 ∨ 上传人姓名 ∨ 所在文件夹名。
     * {0} 占位由 MyBatis 参数绑定，无字符串拼接注入面；可见面过滤由调用方先行完成。
     */
    static void applyGlobalKeyword(LambdaQueryWrapper<BizFile> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        wrapper.and(w -> w.like(BizFile::getFileName, keyword)
                .or().apply("EXISTS (SELECT 1 FROM sys_user u WHERE u.id = uploader_id AND u.real_name LIKE CONCAT('%', {0}, '%'))", keyword)
                .or().apply("EXISTS (SELECT 1 FROM biz_file_folder f WHERE f.id = folder_id AND f.folder_name LIKE CONCAT('%', {0}, '%'))", keyword));
    }
}
