package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.module.file.entity.BizCustomerFile;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizCustomerFileMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerFileRelationServiceTest {
    private final BizCustomerFileMapper relationMapper = mock(BizCustomerFileMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final CustomerAccessService customerAccessService = mock(CustomerAccessService.class);
    private final CustomerFileRelationService service = new CustomerFileRelationService(
            relationMapper, fileMapper, fileService, customerAccessService);

    @Test
    void attach_rejectsFileOutsideCurrentFileScope() {
        BizFile file = new BizFile();
        file.setId(9L);
        when(fileMapper.selectById(9L)).thenReturn(file);
        when(fileService.canDownloadFile(file, 7L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            BusinessException error = assertThrows(BusinessException.class, () -> service.attach(11L, 9L));
            assertEquals(403, error.getCode());
        }
        verify(customerAccessService).assertCanEdit(11L);
        verify(relationMapper, never()).insert(any(BizCustomerFile.class));
    }

    @Test
    void attach_isIdempotentForExistingActiveRelation() {
        BizFile file = new BizFile();
        file.setId(9L);
        when(fileMapper.selectById(9L)).thenReturn(file);
        when(fileService.canDownloadFile(file, 7L)).thenReturn(true);
        when(relationMapper.selectOne(any())).thenReturn(new BizCustomerFile());

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            service.attach(11L, 9L);
        }
        verify(relationMapper, never()).insert(any(BizCustomerFile.class));
    }

    @Test
    void list_batchLoadsFilesAndKeepsOnlyDownloadableRows() {
        BizCustomerFile first = relation(1L, 9L);
        BizCustomerFile second = relation(2L, 10L);
        BizFile visible = file(9L, "可见.pdf");
        BizFile hidden = file(10L, "不可见.pdf");
        when(relationMapper.selectList(any())).thenReturn(List.of(first, second));
        when(fileMapper.selectByIds(any())).thenReturn(List.of(visible, hidden));
        when(fileService.canDownloadFile(visible, 7L)).thenReturn(true);
        when(fileService.canDownloadFile(hidden, 7L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            assertEquals(List.of(visible), service.list(11L));
        }
        verify(fileMapper).selectByIds(any());
        verify(fileMapper, never()).selectById(anyLong());
    }

    @Test
    void list_toleratesDuplicateFileRowsFromDifferentRelationTypes() {
        BizCustomerFile attachment = relation(1L, 9L);
        BizCustomerFile reference = relation(2L, 9L);
        reference.setRelationType("reference");
        BizFile file = file(9L, "同一文件.pdf");
        when(relationMapper.selectList(any())).thenReturn(List.of(attachment, reference));
        when(fileMapper.selectByIds(any())).thenReturn(List.of(file));
        when(fileService.canDownloadFile(file, 7L)).thenReturn(true);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            assertEquals(List.of(file), service.list(11L));
        }
    }

    private static BizCustomerFile relation(Long id, Long fileId) {
        BizCustomerFile relation = new BizCustomerFile();
        relation.setId(id);
        relation.setCustomerId(11L);
        relation.setFileId(fileId);
        relation.setDeleted(0);
        relation.setRelationType("attachment");
        return relation;
    }

    private static BizFile file(Long id, String name) {
        BizFile file = new BizFile();
        file.setId(id);
        file.setFileName(name);
        return file;
    }
}
