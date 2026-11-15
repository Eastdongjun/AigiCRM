package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.system.entity.SysDictType;
import com.aigicrm.module.system.mapper.SysDictDataMapper;
import com.aigicrm.module.system.mapper.SysDictTypeMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 【2026-11-16】字典模块固定枚举/默认值/非法值回归 */
class SysDictServiceModuleTest {

    private final SysDictTypeMapper typeMapper = mock(SysDictTypeMapper.class);
    private final SysDictService svc = new SysDictService(
            typeMapper, mock(SysDictDataMapper.class), mock(BizCustomerMapper.class));

    @Test
    void listModules_containsBusinessAndEmptyModules() {
        List<Map<String, Object>> modules = svc.listModules();
        List<String> codes = modules.stream().map(m -> String.valueOf(m.get("code"))).toList();
        assertTrue(codes.contains("customer"));
        assertTrue(codes.contains("score"));
        assertTrue(codes.contains("fabushi"));
        assertTrue(codes.contains("workflow"));
        assertTrue(codes.contains("common"));
        assertEquals(11, codes.size());
    }

    @Test
    void createType_defaultsToCommonWhenBlank() {
        when(typeMapper.selectCount(any())).thenReturn(0L);
        SysDictType type = new SysDictType();
        type.setDictName("测试");
        type.setDictCode("probe");
        type.setModuleCode(" ");
        svc.createType(type);
        assertEquals("common", type.getModuleCode());
        verify(typeMapper).insert(type);
    }

    @Test
    void createType_rejectsUnknownModule() {
        when(typeMapper.selectCount(any())).thenReturn(0L);
        SysDictType type = new SysDictType();
        type.setDictName("测试");
        type.setDictCode("probe");
        type.setModuleCode("unknown_module");
        BusinessException e = assertThrows(BusinessException.class, () -> svc.createType(type));
        assertTrue(e.getMessage().contains("未知字典模块"));
    }

    @Test
    void updateType_keepsModuleWhenNotProvided() {
        SysDictType type = new SysDictType();
        type.setId(1L);
        type.setDictName("测试");
        svc.updateType(type);
        verify(typeMapper).updateById(type);
    }
}
