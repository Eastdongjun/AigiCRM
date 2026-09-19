package com.aigicrm.module.system.service;

import com.aigicrm.module.system.entity.SysRuleVersion;
import com.aigicrm.module.system.mapper.SysRuleVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 【PR-24】规则版本只读查询(控制器不依赖 mapper,分层约束)。 */
@Service
@RequiredArgsConstructor
public class SysRuleVersionService {

    private final SysRuleVersionMapper ruleVersionMapper;

    public IPage<SysRuleVersion> page(int current, int size, String ruleKey) {
        return ruleVersionMapper.selectPage(new Page<>(current, Math.min(size, 100)),
                new LambdaQueryWrapper<SysRuleVersion>()
                        .eq(ruleKey != null && !ruleKey.isBlank(), SysRuleVersion::getRuleKey, ruleKey)
                        .orderByDesc(SysRuleVersion::getId));
    }

    /** entity → Map(控制器不依赖 entity,分层约束) */
    public static java.util.Map<String, Object> toRow(com.aigicrm.module.system.entity.SysRuleVersion rv) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        if (rv == null) return row;
        row.put("id", rv.getId());
        row.put("ruleKey", rv.getRuleKey());
        row.put("oldValue", rv.getOldValue());
        row.put("newValue", rv.getNewValue());
        row.put("operatorName", rv.getOperatorName());
        row.put("createdAt", rv.getCreatedAt());
        return row;
    }
}
