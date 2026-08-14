package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.module.system.mapper.SysPermissionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysPermissionRuleService {

    private final SysPermissionRuleMapper ruleMapper;
    private final com.ycmn.security.PermissionCacheService permissionCacheService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 【2026-08-14 缓存自愈】全局权限版本递增：规则影响面为全部用户，evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈 */
    private void bumpAllPermVersions() {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1");
    }

    public List<SysPermissionRule> list() {
        return ruleMapper.selectList(
                new LambdaQueryWrapper<SysPermissionRule>().orderByAsc(SysPermissionRule::getPriority));
    }

    public SysPermissionRule getById(Long id) {
        return ruleMapper.selectById(id);
    }

    @Transactional
    public void create(SysPermissionRule rule) {
        Long count = ruleMapper.selectCount(
                new LambdaQueryWrapper<SysPermissionRule>().eq(SysPermissionRule::getCode, rule.getCode()));
        if (count > 0) throw new BusinessException("规则编码已存在");
        ruleMapper.insert(rule);
        // 【Redis 权限缓存】规则定义影响所有引用用户，全量失效（低频操作）
        permissionCacheService.evictAll();
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpAllPermVersions();
    }

    @Transactional
    public void update(SysPermissionRule rule) {
        ruleMapper.updateById(rule);
        permissionCacheService.evictAll();
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpAllPermVersions();
    }

    @Transactional
    public void delete(Long id) {
        ruleMapper.deleteById(id);
        permissionCacheService.evictAll();
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpAllPermVersions();
    }
}
