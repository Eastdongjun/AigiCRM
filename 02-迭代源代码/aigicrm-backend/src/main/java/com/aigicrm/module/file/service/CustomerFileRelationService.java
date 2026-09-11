package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.module.file.entity.BizCustomerFile;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizCustomerFileMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerFileRelationService {
    private final BizCustomerFileMapper relationMapper;
    private final BizFileMapper fileMapper;
    private final FileService fileService;
    private final CustomerAccessService customerAccessService;

    public List<BizFile> list(Long customerId) {
        customerAccessService.assertCanView(customerId);
        List<BizCustomerFile> relations = relationMapper.selectList(new LambdaQueryWrapper<BizCustomerFile>()
                .eq(BizCustomerFile::getCustomerId, customerId)
                .eq(BizCustomerFile::getDeleted, 0)
                .orderByDesc(BizCustomerFile::getCreatedAt));
        if (relations.isEmpty()) return Collections.emptyList();
        Map<Long, BizFile> filesById = fileMapper.selectByIds(relations.stream()
                        .map(BizCustomerFile::getFileId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(BizFile::getId, Function.identity(), (first, ignored) -> first));
        Set<Long> emittedFileIds = new HashSet<>();
        return relations.stream()
                .map(relation -> filesById.get(relation.getFileId()))
                .filter(file -> file != null && fileService.canDownloadFile(file, SecurityUtils.getCurrentUserId()))
                .filter(file -> emittedFileIds.add(file.getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void attach(Long customerId, Long fileId) {
        customerAccessService.assertCanEdit(customerId);
        BizFile file = fileMapper.selectById(fileId);
        if (file == null || !fileService.canDownloadFile(file, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "无权关联该文件");
        }
        BizCustomerFile existing = relationMapper.selectOne(new LambdaQueryWrapper<BizCustomerFile>()
                .eq(BizCustomerFile::getCustomerId, customerId)
                .eq(BizCustomerFile::getFileId, fileId)
                .eq(BizCustomerFile::getRelationType, "attachment")
                .eq(BizCustomerFile::getDeleted, 0));
        if (existing != null) return;
        BizCustomerFile relation = new BizCustomerFile();
        relation.setCustomerId(customerId);
        relation.setFileId(fileId);
        relation.setRelationType("attachment");
        relation.setCreatedBy(SecurityUtils.getCurrentUserId());
        relation.setDeleted(0);
        relationMapper.insert(relation);
    }

    @Transactional
    public void detach(Long customerId, Long fileId) {
        customerAccessService.assertCanEdit(customerId);
        relationMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizCustomerFile>()
                .eq(BizCustomerFile::getCustomerId, customerId)
                .eq(BizCustomerFile::getFileId, fileId)
                .eq(BizCustomerFile::getRelationType, "attachment")
                .eq(BizCustomerFile::getDeleted, 0)
                .set(BizCustomerFile::getDeleted, 1));
    }
}
