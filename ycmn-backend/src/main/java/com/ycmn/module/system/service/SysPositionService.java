package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.*;
import com.ycmn.module.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPositionService {

    private final SysPositionMapper positionMapper;
    private final SysPositionRuleMapper positionRuleMapper;
    private final SysUserPositionMapper userPositionMapper;
    private final SysPermissionRuleMapper ruleMapper;

    // ==================== 岗位 CRUD ====================

    public List<SysPosition> list() {
        return positionMapper.selectList(
                new LambdaQueryWrapper<SysPosition>().orderByAsc(SysPosition::getSort));
    }

    public SysPosition getById(Long id) {
        SysPosition pos = positionMapper.selectById(id);
        if (pos != null) {
            List<SysPositionRule> rules = positionRuleMapper.selectList(
                    new LambdaQueryWrapper<SysPositionRule>().eq(SysPositionRule::getPositionId, id));
            pos.setRuleIds(rules.stream().map(SysPositionRule::getRuleId).collect(Collectors.toList()));
        }
        return pos;
    }

    @Transactional
    public void create(SysPosition position) {
        Long count = positionMapper.selectCount(
                new LambdaQueryWrapper<SysPosition>().eq(SysPosition::getCode, position.getCode()));
        if (count > 0) throw new BusinessException("岗位编码已存在");
        positionMapper.insert(position);
    }

    @Transactional
    public void update(SysPosition position) {
        positionMapper.updateById(position);
        if (position.getRuleIds() != null) {
            saveRules(position.getId(), position.getRuleIds());
        }
    }

    @Transactional
    public void delete(Long id) {
        positionRuleMapper.delete(new LambdaQueryWrapper<SysPositionRule>().eq(SysPositionRule::getPositionId, id));
        userPositionMapper.delete(new LambdaQueryWrapper<SysUserPosition>().eq(SysUserPosition::getPositionId, id));
        positionMapper.deleteById(id);
    }

    // ==================== 岗位-规则绑定 ====================

    public List<SysPermissionRule> getRules(Long positionId) {
        return ruleMapper.selectByPositionId(positionId);
    }

    @Transactional
    public void saveRules(Long positionId, List<Long> ruleIds) {
        positionRuleMapper.delete(new LambdaQueryWrapper<SysPositionRule>().eq(SysPositionRule::getPositionId, positionId));
        if (ruleIds == null || ruleIds.isEmpty()) return;
        for (Long ruleId : ruleIds) {
            SysPositionRule pr = new SysPositionRule();
            pr.setPositionId(positionId);
            pr.setRuleId(ruleId);
            positionRuleMapper.insert(pr);
        }
    }

    // ==================== 用户-岗位分配 ====================

    public List<SysPosition> getUserPositions(Long userId) {
        return positionMapper.selectByUserId(userId);
    }

    @Transactional
    public void assignUserPositions(Long userId, List<Long> positionIds) {
        userPositionMapper.delete(new LambdaQueryWrapper<SysUserPosition>().eq(SysUserPosition::getUserId, userId));
        if (positionIds == null || positionIds.isEmpty()) return;
        for (int i = 0; i < positionIds.size(); i++) {
            SysUserPosition up = new SysUserPosition();
            up.setUserId(userId);
            up.setPositionId(positionIds.get(i));
            up.setIsPrimary(i == 0 ? 1 : 0); // 第一个设为主岗位
            userPositionMapper.insert(up);
        }
    }
}
