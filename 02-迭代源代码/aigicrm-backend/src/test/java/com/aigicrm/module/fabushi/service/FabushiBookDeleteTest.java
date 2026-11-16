package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysDictService;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.security.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 书目删除语义钉(2026-11-16 用户令"书籍可以删除"): 软删走 @TableLogic deleteById + 审计留痕; 已删/不存在=404。 */
class FabushiBookDeleteTest {

    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDictService dictService = mock(SysDictService.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final FabushiBookService svc = new FabushiBookService(
            bookMapper, postMapper, fileMapper, userMapper, dictService, auditService);

    @BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiPost.class);
    }

    private BizFabushiBook book(long id) {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(id);
        b.setTitle("了凡四训");
        b.setStatus("disabled");
        return b;
    }

    /** 正例: 软删走 deleteById(@TableLogic→UPDATE deleted=1) + 审计留痕带书名。 */
    @Test
    void deleteBook_softDeletesViaLogicDelete_andAudits() {
        when(bookMapper.selectById(3L)).thenReturn(book(3L));

        svc.deleteBook(3L);

        verify(bookMapper).deleteById((java.io.Serializable) 3L); // @TableLogic: 物理被禁, 实际 UPDATE deleted=1
        verify(auditService).permissionChange(eq("fabushi_book_delete"), contains("book:3:了凡四训"));
        // requireBook 的 selectById 为合法前置交互, 不做 noMoreInteractions 全禁
        verifyNoMoreInteractions(auditService);
    }

    /** 负例: 已删/不存在(@TableLogic selectById 自动过滤)=404, 不重复执行删除/审计。 */
    @Test
    void deleteBook_missingOrAlreadyDeleted_throws404_noDoubleDelete() {
        when(bookMapper.selectById(999L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> svc.deleteBook(999L));
        assertEquals(404, e.getCode());
        verify(bookMapper, never()).deleteById(any(java.io.Serializable.class));
        verifyNoInteractions(auditService);
    }
}
