package com.aigicrm.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.approval.entity.BizApproval;
import org.apache.ibatis.annotations.Select;

public interface BizApprovalMapper extends BaseMapper<BizApproval> {

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(approval_code, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) FROM biz_approval WHERE approval_code LIKE CONCAT(#{prefix}, '%')")
    int selectMaxCodeByPrefix(String prefix);
}
