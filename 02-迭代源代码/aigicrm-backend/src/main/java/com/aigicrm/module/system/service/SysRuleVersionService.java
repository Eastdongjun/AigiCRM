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

    /**
     * 【2026-11-03 权限中心 B2】通用版本记录入口(供各模块服务/控制器记审计, 遵守"控制器不依赖 mapper"分层约束):
     * operator 取当前会话(无会话上下文时留空); 失败不阻断调用方主流程(旁路降级)。
     * 与 SysConfigService.recordRuleVersion 同口径, 收敛为单一可复用方法。
     */
    public void record(String ruleKey, String oldValue, String newValue, String remark) {
        try {
            SysRuleVersion rv = new SysRuleVersion();
            rv.setRuleKey(ruleKey);
            rv.setOldValue(oldValue);
            rv.setNewValue(newValue);
            rv.setRemark(remark);
            try {
                var user = com.aigicrm.security.SecurityUtils.getCurrentUser();
                if (user != null) {
                    rv.setOperatorId(user.getUserId());
                    rv.setOperatorName(user.getUsername());
                }
            } catch (Exception ignore) {
                // 无会话上下文(启动初始化等)时 operator 为空
            }
            ruleVersionMapper.insert(rv);
        } catch (Exception e) {
            // 版本记录失败不阻断调用方主流程(旁路降级)
        }
    }
}
