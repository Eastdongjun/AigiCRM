package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysConfig;
import com.ycmn.module.system.mapper.SysConfigMapper;
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
