package com.ycmn.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.approval.entity.BizApprovalConfig;
import com.ycmn.module.approval.entity.BizApprovalNode;
import com.ycmn.module.approval.mapper.BizApprovalConfigMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ApprovalConfigService {

    private final BizApprovalConfigMapper configMapper;
    private final BizApprovalNodeMapper nodeMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApprovalConfigService.class);

    public List<BizApprovalConfig> list() {
        return configMapper.selectList(
            new LambdaQueryWrapper<BizApprovalConfig>()
                .eq(BizApprovalConfig::getStatus, 1)
                .orderByAsc(BizApprovalConfig::getSortOrder));
    }

    @Transactional
    public BizApprovalConfig create(BizApprovalConfig config) {
        configMapper.insert(config);
        return config;
    }

    @Transactional
    public void update(BizApprovalConfig config) {
        configMapper.updateById(config);
    }

    @Transactional
    public void delete(Long id) {
        BizApprovalConfig config = new BizApprovalConfig();
        config.setId(id);
        config.setStatus(0);
        configMapper.updateById(config);
    }

    /**
     * 根据审批类型创建审批节点
     * @return 审批人ID列表
     */
    public List<Long> createNodes(Long approvalId, String flowType) {
        List<BizApprovalConfig> configs = configMapper.selectByFlowType(flowType);
        if (configs.isEmpty()) return Collections.emptyList();

        List<Long> allApproverIds = new ArrayList<>();

        for (BizApprovalConfig cfg : configs) {
            List<Long> approverIds = resolveApproverIds(cfg);
            if (approverIds.isEmpty()) continue;

            for (Long approverId : approverIds) {
                BizApprovalNode node = new BizApprovalNode();
                node.setApprovalId(approvalId);
                node.setApproverId(approverId);
                node.setNodeOrder(cfg.getSortOrder());
                node.setStatus("pending");
                node.setConfigId(cfg.getId());
                node.setSignMode(cfg.getSignMode());
                nodeMapper.insert(node);
            }
            allApproverIds.addAll(approverIds);
        }
        return allApproverIds;
    }

    /** 解析配置中的审批人ID */
    private List<Long> resolveApproverIds(BizApprovalConfig cfg) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> ids = objectMapper.readValue(cfg.getApproverIds(), List.class);
            if (ids == null || ids.isEmpty()) return Collections.emptyList();

            switch (cfg.getApproverType()) {
                case "USER":
                    return ids.stream().map(Long::valueOf).toList();
                case "ROLE": {
                    // 通过 sys_user_role 关联表找用户(单条 JOIN 查询,消除"全量用户+逐用户查角色"的 N+1 全表扫描)
                    // 【安全 2026-08-11 P1 关联修复】过滤 sys_role.status=1, 停用角色不产生审批人
                    java.util.Set<Long> uids = new java.util.LinkedHashSet<>();
                    for (Integer rid : ids) {
                        try {
                            List<Long> matched = roleMapper.selectActiveUserIdsByRoleId(rid.longValue());
                            if (matched != null) uids.addAll(matched);
                        } catch (Exception e) {
                            log.warn("解析审批人 ROLE 失败 roleId={}: {}", rid, e.getMessage());
                        }
                    }
                    return new ArrayList<>(uids);
                }
                case "DEPT":
                    // 找出该部门下的所有用户
                    List<Long> deptUserIds = new ArrayList<>();
                    for (Integer did : ids) {
                        List<SysUser> deptUsers = userMapper.selectList(
                            new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getDeptId, did.longValue())
                                .eq(SysUser::getStatus, 1)
                                .eq(SysUser::getDeleted, 0));
                        deptUsers.forEach(u -> deptUserIds.add(u.getId()));
                    }
                    return deptUserIds;
                default:
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
