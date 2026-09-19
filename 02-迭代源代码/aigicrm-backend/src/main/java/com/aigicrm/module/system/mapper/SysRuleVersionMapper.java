package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysRuleVersion;
import org.apache.ibatis.annotations.Mapper;

/** 【PR-24】规则版本 mapper(append-only,仅 insert/select)。 */
@Mapper
public interface SysRuleVersionMapper extends BaseMapper<SysRuleVersion> {
}
