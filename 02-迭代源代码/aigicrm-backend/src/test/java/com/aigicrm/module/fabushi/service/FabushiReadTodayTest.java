package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostAttachmentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 【F4a 提醒触达·在线自检】/reads/today 日界钉:
 * 查询窗口必须=Asia/Shanghai 今日 [0点, 明日0点)(与 C4 时区结论同口径), 按当前用户过滤;
 * 无记录 false; userId=null 不触库直接 false。
 */
class FabushiReadTodayTest {

    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFabushiCommentMapper commentMapper = mock(BizFabushiCommentMapper.class);
    private final BizFabushiLikeMapper likeMapper = mock(BizFabushiLikeMapper.class);
    private final BizFabushiReadStatusMapper readStatusMapper = mock(BizFabushiReadStatusMapper.class);
    private final BizFabushiPostAttachmentMapper attachmentMapper = mock(BizFabushiPostAttachmentMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final FabushiPostService svc = new FabushiPostService(
            postMapper, commentMapper, likeMapper, readStatusMapper, attachmentMapper,
            userMapper, fileMapper, auditService);

    @BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiReadStatus.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), BizFabushiPost.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryWindow_isTodayShanghai_byUser() {
        when(readStatusMapper.selectCount(any())).thenReturn(1L);
        assertTrue(svc.hasReadToday(5L), "窗口内有记录 → 今日已读");

        ArgumentCaptor<LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiReadStatus>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(readStatusMapper).selectCount(cap.capture());
        // 新版 MyBatis-Plus 条件 SQL 懒渲染——先触发 getSqlSegment() 再读参数表
        String sqlSegment = cap.getValue().getSqlSegment();
        Map<String, Object> params = cap.getValue().getParamNameValuePairs();
        assertFalse(sqlSegment.isEmpty());
        assertTrue(sqlSegment.contains("read_at"), "过滤列=读时间 read_at: " + sqlSegment);
        LocalDateTime start = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay();
        assertTrue(params.containsValue(start), "窗口起点=Asia/Shanghai 今日 0 点");
        assertTrue(params.containsValue(start.plusDays(1)), "窗口终点=明日 0 点(左闭右开)");
        assertTrue(params.containsValue(5L), "按当前用户过滤");
    }

    @Test
    void noRow_false() {
        when(readStatusMapper.selectCount(any())).thenReturn(0L);
        assertFalse(svc.hasReadToday(5L), "窗口内无记录 → 今日未读");
    }

    @Test
    void nullUser_false_withoutQuery() {
        assertFalse(svc.hasReadToday(null));
        verifyNoInteractions(readStatusMapper);
    }
}
