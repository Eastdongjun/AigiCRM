package com.ycmn.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.common.BusinessException;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.entity.BizApprovalFlow;
import com.ycmn.module.approval.entity.BizApprovalNode;
import com.ycmn.module.approval.mapper.BizApprovalFlowMapper;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
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

    private final BizApprovalMapper approvalMapper;
    private final BizApprovalNodeMapper approvalNodeMapper;
    private final BizApprovalFlowMapper approvalFlowMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final BizSeaPoolMapper seaPoolMapper;
    private final SeaService seaService;
    private final SysUserService userService;

    public IPage<BizApproval> page(int current, int size, String tab, String type, Long userId) {
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
        } else if ("reviewing".equals(tab)) {
            wrapper.eq(BizApproval::getStatus, "reviewing");
        } else if ("approved".equals(tab)) {
            wrapper.eq(BizApproval::getStatus, "approved");
        } else if ("rejected".equals(tab)) {
            wrapper.eq(BizApproval::getStatus, "rejected");
        }
        // "all" tab - no additional filter

        if (StringUtils.hasText(type)) {
            wrapper.eq(BizApproval::getApprovalType, type);
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
            if ("pending_approval".equals(pool.getStatus())) {
                throw new BusinessException("该客户已被他人申请捞取，正在审批中");
            }
            // 锁定：改为审批中状态
            pool.setStatus("pending_approval");
            pool.setClaimUserId(applicantId);
            seaPoolMapper.updateById(pool);
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

        // 查找审批流程
        List<BizApprovalFlow> flows = approvalFlowMapper.selectList(
                new LambdaQueryWrapper<BizApprovalFlow>()
                        .eq(BizApprovalFlow::getFlowType, type)
                        .eq(BizApprovalFlow::getStatus, 1)
                        .orderByAsc(BizApprovalFlow::getSortOrder)
        );

        // 优先找申请人的直属领导作为审批人
        SysUser applicant = userMapper.selectById(applicantId);
        Long leaderId = findDirectLeader(applicant != null ? applicant.getDeptId() : null);

        for (BizApprovalFlow flow : flows) {
            if (leaderId != null) {
                // 直属领导审批
                BizApprovalNode node = new BizApprovalNode();
                node.setApprovalId(approval.getId());
                node.setApproverId(leaderId);
                node.setNodeOrder(flow.getSortOrder());
                node.setStatus("pending");
                approvalNodeMapper.insert(node);
            } else {
                // 兜底: 没有直属领导时，按角色找审批人（原逻辑）
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
        approvalMapper.updateById(approval);
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

        stats.put("reviewing", approvalMapper.selectCount(
                new LambdaQueryWrapper<BizApproval>().eq(BizApproval::getStatus, "reviewing")));

        stats.put("all", approvalMapper.selectCount(new LambdaQueryWrapper<>()));

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        stats.put("approvedThisMonth", approvalMapper.selectCount(
                new LambdaQueryWrapper<BizApproval>()
                        .eq(BizApproval::getStatus, "approved")
                        .ge(BizApproval::getUpdatedAt, monthStart)));
        stats.put("rejectedThisMonth", approvalMapper.selectCount(
                new LambdaQueryWrapper<BizApproval>()
                        .eq(BizApproval::getStatus, "rejected")
                        .ge(BizApproval::getUpdatedAt, monthStart)));
        return stats;
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
                // 转移 - 负责人变更
                if (approval.getExtraData() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, Object> data = om.readValue(approval.getExtraData(), Map.class);
                        Long newOwnerId = Long.valueOf(data.get("newOwnerId").toString());
                        BizCustomer customer = customerMapper.selectById(approval.getCustomerId());
                        if (customer != null) {
                            customer.setOwnerId(newOwnerId);
                            customer.setTransferDate(LocalDate.now());
                            customerMapper.updateById(customer);
                        }
                    } catch (Exception ignored) {}
                }
            }
            case "storage" -> {
                // 存储空间 - 配额调整
                if (approval.getExtraData() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, Object> data = om.readValue(approval.getExtraData(), Map.class);
                        Long newQuota = Long.valueOf(data.get("quota").toString());
                        userService.updateStorageQuota(approval.getApplicantId(), newQuota);
                    } catch (Exception ignored) {}
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
