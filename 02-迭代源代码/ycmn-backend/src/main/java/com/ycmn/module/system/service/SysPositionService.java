package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.scope.GroupScopeUtil;
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
    private final com.ycmn.security.PermissionCacheService permissionCacheService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ==================== 缓存自愈：权限版本递增 ====================

    /** 【2026-08-14 缓存自愈】岗位级权限版本递增：evict 漏网/Redis 写失败时，缓存与 DB 版本比对自愈 */
    private void bumpUserPermVersionsByPosition(Long positionId) {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 " +
                "WHERE id IN (SELECT user_id FROM sys_user_position WHERE position_id=?)", positionId);
    }

    /** 【2026-08-14 缓存自愈】用户级权限版本递增 */
    private void bumpPermVersion(Long userId) {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 WHERE id=?", userId);
    }

    // ==================== 岗位 CRUD ====================

    /**
     * 岗位列表（按集团边界过滤）。
     * 【修复 岗位列表集团边界】非 admin 仅见本集团岗位;admin 全量。
     * 口径见 {@link GroupScopeUtil}（与人员列表 applyGroupScope 一致）。
     */
    public List<SysPosition> list() {
        LambdaQueryWrapper<SysPosition> wrapper = new LambdaQueryWrapper<SysPosition>().orderByAsc(SysPosition::getSort);
        List<Long> groupIds = GroupScopeUtil.visibleGroupIds();
        if (groupIds != null) {
            wrapper.in(SysPosition::getGroupId, groupIds);
        }
        return positionMapper.selectList(wrapper);
    }

    /** 岗位详情（含关联规则ID列表；非 admin 越权集团返回 null，防 id 枚举） */
    public SysPosition getById(Long id) {
        SysPosition pos = positionMapper.selectById(id);
        if (pos != null) {
            // 【修复 岗位详情集团边界】与列表同口径：非 admin 无权读取其他集团岗位
            if (!GroupScopeUtil.canAccessGroup(pos.getGroupId())) {
                return null;
            }
            List<SysPositionRule> rules = positionRuleMapper.selectList(
                    new LambdaQueryWrapper<SysPositionRule>().eq(SysPositionRule::getPositionId, id));
            pos.setRuleIds(rules.stream().map(SysPositionRule::getRuleId).collect(Collectors.toList()));
        }
        return pos;
    }

    /** 新增岗位（编码唯一校验；集团归属兜底，越权集团拒绝） */
    @Transactional
    public void create(SysPosition position) {
        // 【修复 岗位创建集团边界】未传 groupId 时显式兜底：非 admin=当前用户集团，admin=主集团 1；越权拒绝
        if (position.getGroupId() == null) {
            List<Long> g = GroupScopeUtil.visibleGroupIds();
            position.setGroupId(g == null ? 1L : g.get(0));
        } else if (!GroupScopeUtil.canAccessGroup(position.getGroupId())) {
            throw new BusinessException("无权在该集团创建岗位");
        }
        Long count = positionMapper.selectCount(
                new LambdaQueryWrapper<SysPosition>().eq(SysPosition::getCode, position.getCode()));
        if (count > 0) throw new BusinessException("岗位编码已存在");
        positionMapper.insert(position);
    }

    /** 更新岗位（规则列表变更时同步重建绑定；越权集团拒绝） */
    @Transactional
    public void update(SysPosition position) {
        // 【修复 岗位更新集团边界】目标岗位越权不可操作；新 groupId 越权不可归属
        if (position.getId() != null) {
            SysPosition existing = positionMapper.selectById(position.getId());
            if (existing != null && !GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
                throw new BusinessException("无权操作其他集团的岗位");
            }
        }
        if (position.getGroupId() != null && !GroupScopeUtil.canAccessGroup(position.getGroupId())) {
            throw new BusinessException("无权将岗位归属到其他集团");
        }
        positionMapper.updateById(position);
        if (position.getRuleIds() != null) {
            saveRules(position.getId(), position.getRuleIds());
        }
        // 【Redis 权限缓存】岗位字段/规则变更影响该岗位用户，立即失效
        permissionCacheService.evictByPosition(position.getId());
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpUserPermVersionsByPosition(position.getId());
    }

    /** 删除岗位（连带清理规则绑定与用户分配；越权集团拒绝） */
    @Transactional
    public void delete(Long id) {
        // 【修复 岗位删除集团边界】非 admin 无权删除其他集团岗位
        SysPosition existing = positionMapper.selectById(id);
        if (existing != null && !GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
            throw new BusinessException("无权删除其他集团的岗位");
        }
        // 【复核修复 2026-08-09】evictByPosition 反查 sys_user_position 表——必须先于删除绑定行执行，
        // 否则反查返回空导致 evict 失效(已删岗位的规则最长残留 10min TTL)
        permissionCacheService.evictByPosition(id);
        // 【2026-08-14 缓存自愈】版本递增同样依赖 sys_user_position 子查询——必须删绑定前执行，否则反查为空、bump 静默失效
        bumpUserPermVersionsByPosition(id);
        positionRuleMapper.delete(new LambdaQueryWrapper<SysPositionRule>().eq(SysPositionRule::getPositionId, id));
        userPositionMapper.delete(new LambdaQueryWrapper<SysUserPosition>().eq(SysUserPosition::getPositionId, id));
        positionMapper.deleteById(id);
    }

    // ==================== 岗位-规则绑定 ====================

    /** 岗位关联的权限规则列表 */
    public List<SysPermissionRule> getRules(Long positionId) {
        return ruleMapper.selectByPositionId(positionId);
    }

    /** 保存岗位-规则绑定（清空重建，幂等） */
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
        // 【Redis 权限缓存】规则绑定变更立即失效该岗位用户缓存
        permissionCacheService.evictByPosition(positionId);
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpUserPermVersionsByPosition(positionId);
    }

    // ==================== 用户-岗位分配 ====================

    /** 用户已分配的岗位列表 */
    public List<SysPosition> getUserPositions(Long userId) {
        return positionMapper.selectByUserId(userId);
    }

    /** 分配用户岗位（清空重建；第一个设为主岗位） */
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
        // 【Redis 权限缓存】用户岗位分配变更立即失效该用户
        permissionCacheService.evict(userId);
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpPermVersion(userId);
    }
}
