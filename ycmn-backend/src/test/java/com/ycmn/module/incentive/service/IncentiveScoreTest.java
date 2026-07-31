package com.ycmn.module.incentive.service;

import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncentiveScoreTest {

    private IncentiveService newService(BizIncentiveCategoryMapper categoryMapper) {
        return new IncentiveService(
                categoryMapper,
                mock(BizIncentiveRecordMapper.class),
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(SysDeptMapper.class),
                mock(SysOrgMapper.class),
                mock(JdbcTemplate.class));
    }

    private int calcScore(IncentiveService service, BizIncentiveRecord record,
                          BizIncentiveCategory category, int base) {
        Integer result = (Integer) ReflectionTestUtils.invokeMethod(
                service, "calcScore", record, category, base);
        return result == null ? -1 : result;
    }

    private BizIncentiveCategory category(String code, Integer score) {
        BizIncentiveCategory c = new BizIncentiveCategory();
        c.setCategoryCode(code);
        c.setScore(score);
        return c;
    }

    private BizIncentiveRecord record(String pre, String mid, String post) {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setFilePreId(pre);
        r.setFileMidId(mid);
        r.setFilePostId(post);
        return r;
    }

    @Test
    void planCategory_anySingleFileScoresBase() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        BizIncentiveCategory plan = category("plan", 100);
        assertEquals(100, calcScore(service, record("[1]", null, null), plan, 100));
        assertEquals(100, calcScore(service, record(null, "[1]", null), plan, 100));
        assertEquals(100, calcScore(service, record(null, null, "[1]"), plan, 100));
    }

    @Test
    void planCategory_noFilesScoresZero() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertEquals(0, calcScore(service, record(null, null, null), category("plan", 100), 100));
    }

    @Test
    void otherCategory_requiresAllThreeFiles() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        BizIncentiveCategory promo = category("promotion", 100);
        assertEquals(100, calcScore(service, record("[1]", "[2]", "[3]"), promo, 100));
        assertEquals(0, calcScore(service, record("[1]", "[2]", null), promo, 100));
        assertEquals(0, calcScore(service, record("[1]", null, "[3]"), promo, 100));
        assertEquals(0, calcScore(service, record(null, "[2]", "[3]"), promo, 100));
    }

    @Test
    void nullCategoryOrZeroBaseScoresZero() {
        IncentiveService service = newService(mock(BizIncentiveCategoryMapper.class));
        assertEquals(0, calcScore(service, record("[1]", "[2]", "[3]"), null, 100));
        assertEquals(0, calcScore(service, record("[1]", "[2]", "[3]"), category("plan", 0), 0));
    }

    @Test
    void childOfPlanParentCountsAsPlan() {
        BizIncentiveCategoryMapper mapper = mock(BizIncentiveCategoryMapper.class);
        BizIncentiveCategory parent = category("plan", 100);
        parent.setId(1L);
        when(mapper.selectById(1L)).thenReturn(parent);
        IncentiveService service = newService(mapper);

        BizIncentiveCategory child = new BizIncentiveCategory();
        child.setParentId(1L);
        child.setScore(100);
        assertEquals(100, calcScore(service, record("[1]", null, null), child, 100));
    }
}
