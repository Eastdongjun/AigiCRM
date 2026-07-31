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
    }

    @Transactional
    public void update(SysPermissionRule rule) {
        ruleMapper.updateById(rule);
    }

    @Transactional
    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }
}
