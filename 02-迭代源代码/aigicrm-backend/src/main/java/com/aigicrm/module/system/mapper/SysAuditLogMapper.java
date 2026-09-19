package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/** 【BS-R】安全审计日志 mapper——只允许 insert/select(表级触发器拒绝 UPDATE/DELETE)。 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
