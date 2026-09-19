package com.aigicrm.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.approval.entity.BizApprovalConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface BizApprovalConfigMapper extends BaseMapper<BizApprovalConfig> {

    @Select("SELECT * FROM biz_approval_config WHERE flow_type = #{flowType} AND status = 1 ORDER BY sort_order")
    List<BizApprovalConfig> selectByFlowType(String flowType);
}
