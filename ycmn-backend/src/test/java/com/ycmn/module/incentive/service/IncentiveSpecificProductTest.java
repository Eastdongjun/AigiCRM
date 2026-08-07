package com.ycmn.module.incentive.service;

import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IncentiveSpecificProductTest {

    private IncentiveService newService(BizIncentiveRecordMapper recMapper, BizIncentiveCategoryMapper catMapper) {
        return new IncentiveService(
                catMapper, recMapper,
                mock(com.ycmn.module.system.mapper.SysUserMapper.class),
                mock(com.ycmn.module.file.mapper.BizFileMapper.class),
                mock(com.ycmn.module.customer.mapper.BizCustomerMapper.class),
                mock(com.ycmn.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.ycmn.module.system.mapper.SysDeptMapper.class),
                mock(com.ycmn.module.system.mapper.SysOrgMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.ycmn.common.scope.OrgScopeResolver.class));
    }

    @Test
    void updateRecordMergesSpecificProduct() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);

        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(10L);
        existing.setCategoryId(1L);
        existing.setFilePreId("[1]");
        existing.setRemark("old");
        when(recMapper.selectById(10L)).thenReturn(existing);
        when(catMapper.selectById(eq(1L))).thenReturn(null); // 积分 base=0，不干扰

        IncentiveService svc = newService(recMapper, catMapper);
        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setSpecificProduct("XX密封圈");

        svc.updateRecord(10L, update);

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recMapper).updateById(cap.capture());
        assertEquals("XX密封圈", cap.getValue().getSpecificProduct(), "具体产品应被合并保存");
        assertEquals("[1]", cap.getValue().getFilePreId(), "其他字段不受影响");
        assertEquals("old", cap.getValue().getRemark(), "其他字段不受影响");
    }
}
