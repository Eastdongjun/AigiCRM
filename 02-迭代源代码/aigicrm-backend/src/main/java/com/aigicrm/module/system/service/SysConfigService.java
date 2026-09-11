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
        configMapper.updateById(config);
    }

    public void batchUpdate(List<SysConfig> configs) {
        for (SysConfig config : configs) {
            if (config.getConfigKey() == null) continue;
            // 按 configKey upsert:存在则更新值,不存在则插入(兼容生产缺失的配置键,如 system.logo)
            SysConfig existing = configMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, config.getConfigKey()));
            if (existing != null) {
                existing.setConfigValue(config.getConfigValue());
                if (config.getDescription() != null) existing.setDescription(config.getDescription());
                configMapper.updateById(existing);
            } else {
                config.setId(null);
                configMapper.insert(config);
            }
        }
    }
}
