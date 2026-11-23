package com.aigicrm.module.file.service;

import com.aigicrm.module.file.mapper.BizFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量删除编排：逐条经注入的 {@link FileService} 代理调用 {@code deleteFile}，
 * 让 {@code @Transactional} 真正生效。
 *
 * <p>【S2229/S6809 2026-11-23】原实现把 {@code this.deleteFile(id)} 包在
 * {@code TransactionTemplate} 里，自调用绕过 Spring 代理，Sonar 在
 * {@code FileService:1294} 钉了一个 BLOCKER。拆成本 bean 后改为注入代理调用，
 * 每条仍是独立事务；不包整批，保留「部分成功不回滚已成功项」的原语义。</p>
 */
@Service
@RequiredArgsConstructor
public class FileBatchDeleteService {

    private static final String LIT_FILE_NOT_FOUND = "文件不存在";
    private static final String LIT_DELETE_FAILED = "删除失败";

    private final FileService fileService;
    private final BizFileMapper fileMapper;

    /**
     * 逐条删除；返回 {id: "OK"|失败原因}，前端逐行展示。
     * 权限/佐证守卫/配额归还在 {@link FileService#deleteFile(Long)} 的单条事务内完成。
     */
    public Map<Long, String> batchDeleteFiles(List<Long> fileIds) {
        FileBatchSupport.requireBatchLimit(fileIds);
        Map<Long, String> out = new LinkedHashMap<>();
        for (Long id : fileIds == null ? List.<Long>of() : fileIds) {
            try {
                if (fileMapper.selectById(id) == null) {
                    out.put(id, LIT_FILE_NOT_FOUND);
                    continue;
                }
                fileService.deleteFile(id);
                out.put(id, "OK");
            } catch (Exception e) {
                out.put(id, e.getMessage() == null ? LIT_DELETE_FAILED : e.getMessage());
            }
        }
        return out;
    }
}
