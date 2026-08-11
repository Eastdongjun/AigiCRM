package com.ycmn.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.common.BusinessException;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.entity.BizApprovalFlow;
import com.ycmn.module.approval.entity.BizApprovalNode;
import com.ycmn.module.approval.entity.BizApprovalConfig;
import com.ycmn.module.approval.mapper.BizApprovalConfigMapper;
import com.ycmn.module.approval.mapper.BizApprovalFlowMapper;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.sea.service.SeaConfigService;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.entity.SysUserRole;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import com.ycmn.module.system.service.SysUserService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final BizApprovalConfigMapper approvalConfigMapper;
    private final BizApprovalMapper approvalMapper;
    private final BizApprovalNodeMapper approvalNodeMapper;
    private final BizApprovalFlowMapper approvalFlowMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final BizSeaPoolMapper seaPoolMapper;
    private final SeaService seaService;
    private final OrgScopeResolver scopeResolver;
    private final SeaConfigService seaConfigService;
    private final SysUserService userService;

    public IPage<BizApproval> page(int current, int size, String tab, String type, Long userId, Long deptId) {
        LambdaQueryWrapper<BizApproval> wrapper = new LambdaQueryWrapper<>();

        if ("pending".equals(tab)) {
            // 待我审批的
            List<BizApprovalNode> nodes = approvalNodeMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalNode>()
                            .eq(BizApprovalNode::getApproverId, userId)
                            .eq(BizApprovalNode::getStatus, "pending")
            );
            if (nodes.isEmpty()) {
                return new Page<>(current, size);
            }
            List<Long> approvalIds = nodes.stream().map(BizApprovalNode::getApprovalId).toList();
            wrapper.in(BizApproval::getId, approvalIds);
        } else if ("mine".equals(tab)) {
            wrapper.eq(BizApproval::getApplicantId, userId);
        } else if ("reviewing".equals(tab) || "approved".equals(tab) || "rejected".equals(tab)) {
            wrapper.eq(BizApproval::getStatus, tab);
            // 历史/进行中数据范围：非 admin 仅见本人申请或本人参与审批的记录
            if (!SecurityUtils.isAdmin()) {
                applyDataScope(wrapper, userId);
            }
        } else if ("all".equals(tab)) {
            // 非 admin 仅见本人申请或本人参与审批的记录
            if (!SecurityUtils.isAdmin()) {
                applyDataScope(wrapper, userId);
            }
        }

        if (StringUtils.hasText(type)) {
            wrapper.eq(BizApproval::getApprovalType, type);
        }
        if (deptId != null) {
            // 部门筛选：申请人所属组织在子树内（含全部子部门）
            java.util.List<Long> deptIds = com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId);
            java.util.List<Long> uids = userMapper.selectList(
                    new LambdaQueryWrapper<SysUser>().in(SysUser::getDeptId, deptIds))
                    .stream().map(SysUser::getId).toList();
            if (uids.isEmpty()) return new Page<>(current, size);
            wrapper.in(BizApproval::getApplicantId, uids);
        }
        wrapper.orderByDesc(BizApproval::getCreatedAt);

        IPage<BizApproval> page = approvalMapper.selectPage(new Page<>(current, size), wrapper);
        for (BizApproval a : page.getRecords()) {
            fillApprovalNames(a);
        }
        return page;
    }

    @Transactional
    public void submit(String type, Long customerId, String actionAfterApprove, String extraData) {
        Long applicantId = SecurityUtils.getCurrentUserId();

        // 公海捞取：检查是否已被他人申请
        if ("claim".equals(type) && customerId != null) {
            BizSeaPool pool = seaPoolMapper.selectOne(
                    new LambdaQueryWrapper<BizSeaPool>()
                            .eq(BizSeaPool::getCustomerId, customerId)
                            .in(BizSeaPool::getStatus, List.of("available", "pending_approval"))
                            .orderByDesc(BizSeaPool::getEnterDate)
                            .last("LIMIT 1")
            );
            if (pool == null) {
                throw new BusinessException("该客户不在公海中");
            }
            // 【2026-08-10 池级权限】捞取权限按池判定(admin 恒有; 兼容存量 sea:claim 角色码)
            if (!seaConfigService.hasPoolPerm(pool.getPoolId(), SeaConfigService.PERM_CLAIM, SecurityUtils.getCurrentUser())
                    && !SecurityUtils.hasAuthority("sea:claim")) {
                throw new BusinessException(403, "没有公海捞取权限");
            }
            // 可见范围校验：非 all 数据范围用户，必须可见该客户所在公海池，防止绕过前端捞取不可见池客户
            if (!"all".equals(SecurityUtils.getCurrentDataScope())
                    && (pool.getPoolId() == null || seaConfigService.listVisible().stream()
                            .noneMatch(c -> c.getId().equals(pool.getPoolId())))) {
                throw new BusinessException("您没有该公海池的可见权限，无法捞取");
            }
            if ("pending_approval".equals(pool.getStatus())) {
                throw new BusinessException("该客户已被他人申请捞取，正在审批中");
            }
            // 原子锁定：条件更新防止并发双捞（仅 status=available 可更新，affected==1 才成功）
            int locked = seaPoolMapper.update(null, new LambdaUpdateWrapper<BizSeaPool>()
                    .set(BizSeaPool::getStatus, "pending_approval")
                    .set(BizSeaPool::getClaimUserId, applicantId)
                    .eq(BizSeaPool::getId, pool.getId())
                    .eq(BizSeaPool::getStatus, "available"));
            if (locked != 1) {
                throw new BusinessException("该客户已被他人申请捞取，正在审批中");
            }
        }

        // 生成审批编号
        String prefix = "P" + Year.now().getValue() + "-";
        int maxCode = approvalMapper.selectMaxCodeByPrefix(prefix);
        String approvalCode = prefix + String.format("%03d", maxCode + 1);

        BizApproval approval = new BizApproval();
        approval.setApprovalCode(approvalCode);
        approval.setApprovalType(type);
        approval.setApplicantId(applicantId);
        approval.setCustomerId(customerId);
        approval.setActionAfterApprove(actionAfterApprove);
        approval.setExtraData(extraData);
        approval.setStatus("reviewing");
        approvalMapper.insert(approval);

        // 1. 优先查找管理员配置的审批流程（BizApprovalConfig）
        BizApprovalConfig config = approvalConfigMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalConfig>()
                        .eq(BizApprovalConfig::getFlowType, type)
                        .eq(BizApprovalConfig::getStatus, 1)
                        .orderByAsc(BizApprovalConfig::getSortOrder)
                        .last("LIMIT 1")
        );

        if (config != null) {
            // 管理员配置模式：按 approverType + approverIds 确定审批人
            List<Long> approverUserIds = resolveApproverUserIds(config);
            for (int i = 0; i < approverUserIds.size(); i++) {
                BizApprovalNode node = new BizApprovalNode();
                node.setApprovalId(approval.getId());
                node.setApproverId(approverUserIds.get(i));
                node.setNodeOrder(i + 1);
                node.setStatus("pending");
                approvalNodeMapper.insert(node);
            }
            approval.setCurrentNode(config.getFlowName());
        } else {
            // 2. 兜底：旧版流程配置（BizApprovalFlow），按角色+直属领导
            List<BizApprovalFlow> flows = approvalFlowMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalFlow>()
                            .eq(BizApprovalFlow::getFlowType, type)
                            .eq(BizApprovalFlow::getStatus, 1)
                            .orderByAsc(BizApprovalFlow::getSortOrder)
            );

            SysUser applicant = userMapper.selectById(applicantId);
            Long leaderId = findDirectLeader(applicant != null ? applicant.getDeptId() : null);

            for (BizApprovalFlow flow : flows) {
                if (leaderId != null) {
                    BizApprovalNode node = new BizApprovalNode();
                    node.setApprovalId(approval.getId());
                    node.setApproverId(leaderId);
                    node.setNodeOrder(flow.getSortOrder());
                    node.setStatus("pending");
                    approvalNodeMapper.insert(node);
                } else {
                    List<SysUserRole> userRoles = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, flow.getApproverRoleId())
                    );
                    for (SysUserRole ur : userRoles) {
                        BizApprovalNode node = new BizApprovalNode();
                        node.setApprovalId(approval.getId());
                        node.setApproverId(ur.getUserId());
                        node.setNodeOrder(flow.getSortOrder());
                        node.setStatus("pending");
                        approvalNodeMapper.insert(node);
                    }
                }
            }
            approval.setCurrentNode(flows.isEmpty() ? "" : flows.get(0).getFlowName());
        }
        approvalMapper.updateById(approval);
    }

    @Transactional
    /** 根据审批配置解析出实际审批人用户ID列表 */
    private List<Long> resolveApproverUserIds(BizApprovalConfig config) {
        List<Long> ids = parseJsonArray(config.getApproverIds());
        if (ids.isEmpty()) return List.of();

        return switch (config.getApproverType()) {
            case "USER" -> ids; // 直接指定用户
            case "ROLE" -> {
                // 按角色找所有该角色下的用户
                List<SysUserRole> userRoles = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, ids)
                );
                yield userRoles.stream().map(SysUserRole::getUserId).distinct().toList();
            }
            case "DEPT" -> {
                // 按部门找所有该部门下的用户
                List<SysUser> deptUsers = userMapper.selectList(
                        new LambdaQueryWrapper<SysUser>().in(SysUser::getDeptId, ids)
                );
                yield deptUsers.stream().map(SysUser::getId).distinct().toList();
            }
            default -> ids;
        };
    }

    /** 简单 JSON 数组解析: "[1,2,3]" → [1,2,3] */
    private List<Long> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return java.util.Arrays.stream(
                    json.replaceAll("[\\[\\]\\s]", "").split(","))
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional
    public void approve(Long approvalId, String comment) {
        Long approverId = SecurityUtils.getCurrentUserId();
        BizApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) throw new BusinessException("审批不存在");

        // 更新审批节点
        BizApprovalNode node = approvalNodeMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approvalId)
                        .eq(BizApprovalNode::getApproverId, approverId)
                        .eq(BizApprovalNode::getStatus, "pending")
        );
        if (node == null) throw new BusinessException("无权审批");

        node.setAction("approve");
        node.setComment(comment);
        node.setOperatedAt(LocalDateTime.now());
        node.setStatus("approved");
        approvalNodeMapper.updateById(node);

        // 检查是否所有节点都已审批
        long pendingCount = approvalNodeMapper.selectCount(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approvalId)
                        .eq(BizApprovalNode::getStatus, "pending")
        );

        if (pendingCount == 0) {
            approval.setStatus("approved");
            approvalMapper.updateById(approval);
            executeApprovalAction(approval);
        }
    }

    @Transactional
    public void reject(Long approvalId, String comment) {
        Long approverId = SecurityUtils.getCurrentUserId();
        BizApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) throw new BusinessException("审批不存在");

        BizApprovalNode node = approvalNodeMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approvalId)
                        .eq(BizApprovalNode::getApproverId, approverId)
                        .eq(BizApprovalNode::getStatus, "pending")
        );
        if (node == null) throw new BusinessException("无权审批");

        node.setAction("reject");
        node.setComment(comment);
        node.setOperatedAt(LocalDateTime.now());
        node.setStatus("rejected");
        approvalNodeMapper.updateById(node);

        approval.setStatus("rejected");
        approvalMapper.updateById(approval);

        // 捞取被拒绝时，释放公海锁定，其他人可以再捞取
        if ("claim".equals(approval.getApprovalType()) && approval.getCustomerId() != null) {
            BizSeaPool pool = seaPoolMapper.selectOne(
                    new LambdaQueryWrapper<BizSeaPool>()
                            .eq(BizSeaPool::getCustomerId, approval.getCustomerId())
                            .eq(BizSeaPool::getStatus, "pending_approval")
            );
            if (pool != null) {
                pool.setStatus("available");
                pool.setClaimUserId(null);
                seaPoolMapper.updateById(pool);
            }
        }
    }

    /**
     * 应用审批数据范围
     *
     * 【需求】非 admin 仅见本人申请或本人参与的审批；空范围时不生成 IN () 语法错误
     * 【方法】本人参与列表为空时退化为"仅本人申请"，保证 SQL 合法且语义正确
     * 【业务规则】admin 不走本方法（全量可见）
     */
    private void applyDataScope(LambdaQueryWrapper<BizApproval> w, Long userId) {
        // 【修复 2026-08-08 审批范围】在"本人申请 ∨ 本人参与"基础上并入数据范围可见用户(含系统级开关)，
        // 上级/组织负责人可见下级审批；all 范围(null)不过滤。
        List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visible == null) return; // all 范围：全量可见
        List<Long> involved = myInvolvedApprovalIds(userId);
        boolean hasVisible = !visible.isEmpty();
        boolean hasInvolved = !involved.isEmpty();
        if (!hasVisible && !hasInvolved) return; // 防御：两者皆空时不生成 IN () 语法错误
        w.and(x -> {
            if (hasVisible) x.in(BizApproval::getApplicantId, visible); // 本人恒在 visible 中
            if (hasInvolved) {
                if (hasVisible) x.or().in(BizApproval::getId, involved); // 本人参与的审批
                else x.in(BizApproval::getId, involved);
            }
        });
    }

    public Map<String, Long> statistics(Long userId) {
        Map<String, Long> stats = new HashMap<>();

        long pending = approvalNodeMapper.selectCount(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApproverId, userId)
                        .eq(BizApprovalNode::getStatus, "pending")
        );
        stats.put("pending", pending);

        stats.put("mine", approvalMapper.selectCount(
                new LambdaQueryWrapper<BizApproval>().eq(BizApproval::getApplicantId, userId)));

        // 数据范围：非 admin 仅统计本人申请或本人参与审批的记录（与列表口径一致）
        LambdaQueryWrapper<BizApproval> scoped = new LambdaQueryWrapper<>();
        if (!SecurityUtils.isAdmin()) {
            applyDataScope(scoped, userId);
        }

        stats.put("reviewing", approvalMapper.selectCount(
                scoped.clone().eq(BizApproval::getStatus, "reviewing")));

        stats.put("all", approvalMapper.selectCount(scoped.clone()));

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        stats.put("approvedThisMonth", approvalMapper.selectCount(
                scoped.clone().eq(BizApproval::getStatus, "approved")
                        .ge(BizApproval::getUpdatedAt, monthStart)));
        stats.put("rejectedThisMonth", approvalMapper.selectCount(
                scoped.clone().eq(BizApproval::getStatus, "rejected")
                        .ge(BizApproval::getUpdatedAt, monthStart)));
        return stats;
    }

    /** 当前用户参与过的审批ID（任一审批节点归属），用于数据范围过滤 */
    private List<Long> myInvolvedApprovalIds(Long userId) {
        return approvalNodeMapper.selectList(
                        new LambdaQueryWrapper<BizApprovalNode>()
                                .eq(BizApprovalNode::getApproverId, userId))
                .stream().map(BizApprovalNode::getApprovalId).distinct().toList();
    }

    private void executeApprovalAction(BizApproval approval) {
        switch (approval.getApprovalType()) {
            case "claim" -> {
                // 公海捞取 - 通过后客户归属变更（completeClaim 内部按 customerId 定位公海行）
                seaService.completeClaim(approval.getCustomerId(), approval.getApplicantId());
            }
            case "extend" -> {
                // 延期 - 保护期延长360天
                BizCustomer customer = customerMapper.selectById(approval.getCustomerId());
                if (customer != null) {
                    customer.setExpireDate(customer.getExpireDate().plusDays(360));
                    customer.setProtectionStatus("active");
                    customer.setStatus("normal");
                    customerMapper.updateById(customer);
                }
            }
            case "transfer" -> {
                // 转移 - 负责人变更（解析失败必须显式报错，随事务回滚，不得静默成功）
                if (approval.getExtraData() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = om.readValue(approval.getExtraData(), Map.class);
                        Long newOwnerId = Long.valueOf(data.get("newOwnerId").toString());
                        BizCustomer customer = customerMapper.selectById(approval.getCustomerId());
                        if (customer != null) {
                            customer.setOwnerId(newOwnerId);
                            customer.setTransferDate(LocalDate.now());
                            customerMapper.updateById(customer);
                        }
                    } catch (Exception e) {
                        throw new BusinessException("审批通过后客户转移处理失败: " + e.getMessage());
                    }
                }
            }
            case "storage" -> {
                // 存储空间 - 配额调整（解析失败必须显式报错，随事务回滚，不得静默成功）
                if (approval.getExtraData() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = om.readValue(approval.getExtraData(), Map.class);
                        Long newQuota = Long.valueOf(data.get("quota").toString());
                        userService.updateStorageQuota(approval.getApplicantId(), newQuota);
                    } catch (Exception e) {
                        throw new BusinessException("审批通过后存储配额调整失败: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 沿部门树向上查找直属领导：本部门leader → 父部门leader → ...
     */
    private Long findDirectLeader(Long deptId) {
        if (deptId == null) return null;
        int maxDepth = 5; // 防无限循环
        Long currentDeptId = deptId;
        while (currentDeptId != null && currentDeptId > 0 && maxDepth-- > 0) {
            SysDept dept = deptMapper.selectById(currentDeptId);
            if (dept == null) break;
            if (dept.getLeaderId() != null) {
                return dept.getLeaderId();
            }
            currentDeptId = dept.getParentId();
        }
        return null;
    }

    private void fillApprovalNames(BizApproval approval) {
        if (approval.getApplicantId() != null) {
            SysUser user = userMapper.selectById(approval.getApplicantId());
            if (user != null) approval.setApplicantName(user.getRealName());
        }
        if (approval.getCustomerId() != null) {
            BizCustomer customer = customerMapper.selectById(approval.getCustomerId());
            if (customer != null) approval.setCustomerName(customer.getCustomerName());
        }
    }
}
