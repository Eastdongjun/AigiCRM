package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.system.entity.SysConfig;
import com.aigicrm.module.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper configMapper;
    private final com.aigicrm.module.system.mapper.SysRuleVersionMapper ruleVersionMapper;

    public List<SysConfig> list() {
        return configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
    }

    public String getValue(String key) {
        SysConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key)
        );
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 布尔配置读取：缺失/空白 → 返回 defaultValue（用于"开关缺失视为默认开启"）。
     * 兼容 "true"/"1"/"on"/"yes" 任一真值写法。
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String v = getValue(key);
        if (v == null || v.isBlank()) return defaultValue;
        return "1".equals(v) || "true".equalsIgnoreCase(v)
                || "on".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    public void update(SysConfig config) {
        assertConfigWriteAllowed(config.getConfigKey());
        configMapper.updateById(config);
    }

    public void batchUpdate(List<SysConfig> configs) {
        for (SysConfig config : configs) {
            if (config.getConfigKey() == null) continue;
            assertConfigWriteAllowed(config.getConfigKey());
            // 按 configKey upsert:存在则更新值,不存在则插入(兼容生产缺失的配置键,如 system.logo)
            SysConfig existing = configMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, config.getConfigKey()));
            if (existing != null) {
                // 【PR-24 2026-09-19】规则键值变更自动记录版本(append-only,可追溯)
                if (isRuleKey(config.getConfigKey())
                        && existing.getConfigValue() != null
                        && !existing.getConfigValue().equals(config.getConfigValue())) {
                    recordRuleVersion(config.getConfigKey(), existing.getConfigValue(), config.getConfigValue());
                }
                // 【WP1.4】上级/负责人开关变更审计(旧值/新值/操作人)——权限语义键不静默变更;
                // 旧值必须在 set 前捕获
                String oldVal = existing.getConfigValue();
                existing.setConfigValue(config.getConfigValue());
                if (config.getDescription() != null) existing.setDescription(config.getDescription());
                configMapper.updateById(existing);
                if (ADMIN_ONLY_KEYS.contains(config.getConfigKey())
                        && oldVal != null
                        && !oldVal.equals(config.getConfigValue())) {
                    securityAudit.permissionChange("perm_switch_changed",
                            "key=" + config.getConfigKey()
                                    + " old=" + oldVal
                                    + " new=" + config.getConfigValue());
                }
            } else {
                config.setId(null);
                configMapper.insert(config);
            }
        }
    }

    /** 【WP1.4 2026-11-13 锁定决策#8】上级/负责人开关 admin-only: 非 admin 写入即 400, 并写审计 */
    private static final java.util.Set<String> ADMIN_ONLY_KEYS = java.util.Set.of(
            com.aigicrm.common.scope.OrgScopeResolver.KEY_SUPERIOR_SEE_SUBORDINATE,
            com.aigicrm.common.scope.OrgScopeResolver.KEY_ORG_LEADER_SEE_ORG);

    private void assertConfigWriteAllowed(String key) {
        if (key == null || !ADMIN_ONLY_KEYS.contains(key)) return;
        var user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user != null && com.aigicrm.security.SecurityUtils.isAdmin()) return;
        securityAudit.permissionChange("perm_switch_denied_non_admin",
                "key=" + key + " actor=" + (user != null ? user.getUsername() : "anonymous"));
        throw new com.aigicrm.common.BusinessException("该开关仅系统管理员可修改");
    }

    private final com.aigicrm.security.SecurityAuditService securityAudit;

    /** 规则键前缀(变更需记版本):客户规则/评分规则等 */
    private static boolean isRuleKey(String key) {
        return key != null && (key.startsWith("customer.") || key.startsWith("score."));
    }

    private void recordRuleVersion(String ruleKey, String oldValue, String newValue) {
        try {
            com.aigicrm.module.system.entity.SysRuleVersion rv = new com.aigicrm.module.system.entity.SysRuleVersion();
            rv.setRuleKey(ruleKey);
            rv.setOldValue(oldValue);
            rv.setNewValue(newValue);
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
            // 版本记录失败不阻断配置变更(旁路降级)
            org.slf4j.LoggerFactory.getLogger(SysConfigService.class)
                    .warn("规则版本记录失败 key={}: {}", ruleKey, e.getMessage());
        }
    }
}
