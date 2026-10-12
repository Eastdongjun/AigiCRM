package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【PR-19 2026-09-19】附件核对:元数据—本体—引用对账。
 *
 * <p>总册 PR-19:"按目标环境核对文件元数据、本体和业务引用,区分可用、缺失、损坏"。
 * 本服务遍历 biz_file 元数据,逐条检查磁盘文件是否存在,输出结构化对账报告;
 * 不删除任何引用,不掩盖缺失(总册:"不删除引用掩盖缺失")。</p>
 *
 * <p>权限:仅 file:folder:manage 或 admin(核对是管理操作,非普通用户自服务;体检 2026-10-12
 * 修正——原注释的 file:manage 权限码全库不存在)。另:存在性检查只查本地磁盘,STORAGE_TYPE
 * 切 oss 后需补 OSS 探测,当前 local 部署未触发(体检登记的潜伏项)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileReconciliationService {

    private final BizFileMapper fileMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 全量对账:遍历全部 biz_file,逐条检查磁盘存在性。
     * 返回:汇总(total/present/missing/missingRate)+ 缺失清单(前 200 条,含恢复线索)。
     */
    public Map<String, Object> reconcile(int detailLimit) {
        List<BizFile> allFiles = fileMapper.selectList(new LambdaQueryWrapper<BizFile>()
                .eq(BizFile::getDeleted, 0));

        long total = allFiles.size();
        long present = 0;
        List<Map<String, Object>> missingList = new ArrayList<>();

        for (BizFile f : allFiles) {
            boolean exists = fileExists(f.getOssKey());
            if (exists) {
                present++;
            } else if (missingList.size() < detailLimit) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fileId", f.getId());
                m.put("fileName", f.getFileName());
                m.put("fileScope", f.getFileScope());
                m.put("ossKey", f.getOssKey());
                m.put("fileSize", f.getFileSize());
                m.put("uploaderId", f.getUploaderId());
                m.put("createdAt", f.getCreatedAt());
                m.put("status", "missing");
                missingList.add(m);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalFiles", total);
        result.put("presentFiles", present);
        result.put("missingFiles", total - present);
        result.put("missingRate", total > 0 ? String.format("%.1f%%", (total - present) * 100.0 / total) : "0%");
        result.put("uploadDir", uploadDir);
        result.put("reconciledAt", java.time.LocalDateTime.now().toString());
        result.put("note", "缺失文件不删除引用(总册:不掩盖缺失);恢复需目标环境备份源");
        if (!missingList.isEmpty()) {
            result.put("missingDetail", missingList);
        }
        return result;
    }

    /**
     * 单文件状态检查(文件列表/详情用):返回 present/missing。
     */
    public String checkFileStatus(String ossKey) {
        return fileExists(ossKey) ? "present" : "missing";
    }

    private boolean fileExists(String ossKey) {
        if (ossKey == null || ossKey.isBlank()) return false;
        try {
            Path path = Paths.get(uploadDir, ossKey);
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            log.warn("文件存在性检查失败 ossKey={}: {}", ossKey, e.getMessage());
            return false;
        }
    }
}
