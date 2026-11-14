package com.aigicrm.module.file.service;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.aigicrm.config.OssConfig;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 【P0 2026-10-17 匿名公共下载 404】公共元数据查询必须走忽略租户拦截器的 mapper 方法。
 *
 * 背景：匿名请求没有登录态，TenantLineInnerInterceptor 会把 selectOne 改写为
 * tenant_id = NULL（恒假），导致 /api/file/download/public/** 全部 404。
 * 修复：新增 BizFileMapper.selectPublicByKeyIgnoreTenant，SQL 显式限定
 * deleted=0 AND file_scope='public'；FileService.getPublicFileByKey 只调用该方法。
 *
 * 本测试直接 mock BizFileMapper（不只 mock FileService），证明公共查询确实落到该 mapper 方法。
 */
class FilePublicDownloadMapperTest {

    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final FileService svc = new FileService(
            fileMapper,
            mock(BizFileFolderMapper.class),
            mock(BizFileShareMapper.class),
            mock(BizFolderAclMapper.class),
            mock(com.aigicrm.module.file.mapper.BizFileVersionMapper.class),
            mock(BizIncentiveRecordMapper.class),
            mock(SysUserMapper.class),
            providerOf(mock(OSS.class)),
            mock(OssConfig.class),
            mock(FolderPermissionService.class),
            mock(com.aigicrm.security.SecurityAuditService.class),
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            mock(com.aigicrm.module.incentive.service.IncentiveService.class)); // BIZ-007 删佐证联动重算 // 2026-10-27 shareFile 集团边界校验新增依赖

    private static ObjectProvider<OSS> providerOf(OSS client) {
        return new ObjectProvider<>() {
            @Override public OSS getObject(Object... args) { return client; }
            @Override public OSS getIfAvailable() { return client; }
            @Override public OSS getIfUnique() { return client; }
            @Override public OSS getIfAvailable(java.util.function.Supplier<OSS> supplier) { return client; }
            @Override public OSS getIfUnique(java.util.function.Supplier<OSS> supplier) { return client; }
        };
    }

    @Test
    void publicLookupUsesTenantIgnoringMapperMethod() {
        BizFile publicFile = new BizFile();
        publicFile.setId(9L);
        publicFile.setOssKey("public/1/logo.png");
        publicFile.setFileScope("public");
        when(fileMapper.selectPublicByKeyIgnoreTenant("public/1/logo.png")).thenReturn(publicFile);

        BizFile result = svc.getPublicFileByKey("public/1/logo.png");

        assertSame(publicFile, result);
        verify(fileMapper).selectPublicByKeyIgnoreTenant("public/1/logo.png");
        // 公共查询不得回落到受租户拦截器影响的 selectOne
        verify(fileMapper, never()).selectOne(any());
    }

    @Test
    void publicLookupWithBlankKeyDoesNotHitDatabase() {
        assertNull(svc.getPublicFileByKey(null));
        assertNull(svc.getPublicFileByKey("  "));
        verifyNoInteractions(fileMapper);
    }

    @Test
    void mapperMethodDeclaresTenantIgnoreAndPublicScopeSql() throws Exception {
        Method method = BizFileMapper.class.getMethod("selectPublicByKeyIgnoreTenant", String.class);
        InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
        assertNotNull(ignore, "公共查询必须标注 @InterceptorIgnore，否则匿名请求被租户条件过滤为空");
        assertEquals("true", ignore.tenantLine(), "必须忽略租户条件");

        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, "必须保留显式 SQL");
        String sql = String.join(" ", select.value());
        assertTrue(sql.contains("deleted = 0"), "必须过滤逻辑删除行: " + sql);
        assertTrue(sql.contains("file_scope = 'public'"), "必须限定 public 范围: " + sql);
        assertTrue(sql.contains("oss_key = #{key}"), "必须按 oss_key 精确查询: " + sql);
    }
}
