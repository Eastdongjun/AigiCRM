package com.aigicrm.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.entity.BizApprovalFlow;
import com.aigicrm.module.approval.entity.BizApprovalNode;
import com.aigicrm.module.approval.entity.BizApprovalConfig;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalFlowMapper;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.entity.SysUserRole;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.service.SysUserService;
import com.aigicrm.security.SecurityUtils;
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
    /** 【PR-02】节点实例化收敛到 ApprovalConfigService.createNodes(唯一的实例化入口) */
    private final ApprovalConfigService approvalConfigService;
    /** 【议题一裁决】延期/保护期天数走系统配置(管理员可改) */
    private final com.aigicrm.module.system.service.SysConfigService configService;
    /** 【PR-09】集团成员查询(转移边界校验) */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    /** 【议题二.1 裁决】转交审计 */
    private final com.aigicrm.security.SecurityAuditService securityAudit;

    public IPage<BizApproval> page(int current, int size, String tab, String type, String keyword, Long userId, Long deptId) {
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
        } else {
            // 【DeepSeek四审 P1 修复 2026-09-22】未知/空 tab 不再落入"无范围过滤"分支
            // (旧代码传 tab=x 可越过个人过滤看全集团列表)。白名单外按最窄口径: 非 admin 套本人相关范围
            if (!SecurityUtils.isAdmin()) {
                applyDataScope(wrapper, userId);
            }
        }

        if (StringUtils.hasText(type)) {
            wrapper.eq(BizApproval::getApprovalType, type);
        }
        applyKeywordFilter(wrapper, keyword);
        if (deptId != null) {
            // 部门筛选：申请人所属组织在子树内（含全部子部门）
            java.util.List<Long> deptIds = com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId);
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

    private void applyKeywordFilter(LambdaQueryWrapper<BizApproval> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String normalized = keyword.trim();
        List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                        .like(BizCustomer::getCustomerName, normalized)
                        .or()
                        .like(BizCustomer::getCustomerCode, normalized))
                .stream()
                .map(BizCustomer::getId)
                .toList();
        List<Long> applicantIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .like(SysUser::getRealName, normalized)
                        .or()
                        .like(SysUser::getUsername, normalized))
                .stream()
                .map(SysUser::getId)
                .toList();
        wrapper.and(matched -> {
            matched.like(BizApproval::getApprovalCode, normalized);
            if (!customerIds.isEmpty()) {
                matched.or().in(BizApproval::getCustomerId, customerIds);
            }
            if (!applicantIds.isEmpty()) {
                matched.or().in(BizApproval::getApplicantId, applicantIds);
            }
        });
    }

    public Map<String, Object> detail(Long approvalId, Long userId) {
        BizApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null || (!SecurityUtils.isAdmin() && !canViewApproval(approval, userId))) {
            throw new BusinessException("资源不存在或无权访问");
        }
        fillApprovalNames(approval);
        List<BizApprovalNode> nodes = approvalNodeMapper.selectList(new LambdaQueryWrapper<BizApprovalNode>()
                .eq(BizApprovalNode::getApprovalId, approvalId)
                .orderByAsc(BizApprovalNode::getNodeOrder).orderByAsc(BizApprovalNode::getId));
        for (BizApprovalNode node : nodes) {
            if (node.getApproverId() != null) {
                SysUser approver = userMapper.selectById(node.getApproverId());
                if (approver != null) {
                    node.setApproverName(approver.getRealName());
                    // 【PR-03 2026-09-19】在途卡单可见性:待办节点归属停用/删除用户 → 标记,
                    // 前端提示"审批人已停用",异常可定位(处置规则待业务确认,引擎不自动跳过/批准)
                    boolean invalid = (approver.getStatus() == null || approver.getStatus() != 1)
                            || (approver.getDeleted() != null && approver.getDeleted() == 1);
                    if (invalid && ("pending".equals(node.getStatus()) || "waiting".equals(node.getStatus()))) {
                        node.setApproverInvalid(true);
                    }
                } else {
                    // 审批人已被物理删除:名字无可展示,同样标记
                    if ("pending".equals(node.getStatus()) || "waiting".equals(node.getStatus())) {
                        node.setApproverInvalid(true);
                    }
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("approval", approval);
        result.put("nodes", nodes);
        return result;
    }

    private boolean canViewApproval(BizApproval approval, Long userId) {
        if (userId.equals(approval.getApplicantId())) return true;
        if (approvalNodeMapper.selectCount(new LambdaQueryWrapper<BizApprovalNode>()
                .eq(BizApprovalNode::getApprovalId, approval.getId())
                .eq(BizApprovalNode::getApproverId, userId)) > 0) return true;
        // 【2026-09-19 接入权限矩阵】(approval,view) 显式配置优先，未配置回退角色数据范围
        List<Long> visible = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "approval");
        return visible == null || visible.contains(approval.getApplicantId());
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
            // 【体检 H5 2026-10-12】锁定同时写入 claim_date——超时释放任务(CustomerExpireTask
            // .releaseStaleClaimLocks)以 lt(claim_date, 截止) 筛超时行, 不写时间戳则 NULL 永不命中,
            // 无人审批的锁永久 pending_approval 锁死池行(此前生产 0 行受害, 纯代码修复)
            int locked = seaPoolMapper.update(null, new LambdaUpdateWrapper<BizSeaPool>()
                    .set(BizSeaPool::getStatus, "pending_approval")
                    .set(BizSeaPool::getClaimUserId, applicantId)
                    .set(BizSeaPool::getClaimDate, LocalDateTime.now())
                    .eq(BizSeaPool::getId, pool.getId())
                    .eq(BizSeaPool::getStatus, "available"));
            if (locked != 1) {
                throw new BusinessException("该客户已被他人申请捞取，正在审批中");
            }
        }

        // 【2026-09-23 转移校验前移】接收人有效性+集团边界原先只在审批通过时校验(applyApprovalAction),
        // 接收人失效(停用/删除/跨集团)时错误在审批人端爆发、发起人无感知——单据永远无法生效却显示"已提交"。
        // 前移到提交时 fail-fast,校验口径与审批执行侧逐字一致(消息复用);审批执行侧校验保留作最终防线。
        if ("transfer".equals(type)) {
            if (customerId == null) throw new BusinessException("转移必须指定客户");
            Long newOwnerId = parseTransferNewOwner(extraData);
            SysUser newOwner = userMapper.selectById(newOwnerId);
            if (newOwner == null || newOwner.getStatus() == null || newOwner.getStatus() != 1
                    || (newOwner.getDeleted() != null && newOwner.getDeleted() == 1)) {
                throw new BusinessException("接收人不存在或已停用,转移失败;请转交后重试");
            }
            if (!SecurityUtils.isAdmin()) {
                Long sessionGroup = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
                if (sessionGroup != null) {
                    List<Long> groupMembers = com.aigicrm.common.scope.GroupScopeUtil
                            .userIdsInGroup(jdbc, sessionGroup);
                    if (!groupMembers.contains(newOwnerId)) {
                        throw new BusinessException("接收人不在当前集团,跨集团转移需平台管理员处理");
                    }
                }
            }
        }

        String prefix = "P" + Year.now().getValue() + "-";
        int maxCode = approvalMapper.selectMaxCodeByPrefix(prefix, com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull());
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

        // 1. 优先查找管理员配置的审批流程（BizApprovalConfig，多行=串行阶段）
        Long activeConfigCount = approvalConfigMapper.selectCount(
                new LambdaQueryWrapper<BizApprovalConfig>()
                        .eq(BizApprovalConfig::getFlowType, type)
                        .eq(BizApprovalConfig::getStatus, 1)
        );

        if (activeConfigCount != null && activeConfigCount > 0) {
            // 【PR-02 2026-09-19】节点实例化收敛到 ApprovalConfigService.createNodes:
            // 串行阶段(首阶段 pending、后续 waiting)+ 阶段内或签/会签(signMode 落节点)。
            List<Long> approverUserIds = approvalConfigService.createNodes(approval.getId(), type);
            if (approverUserIds.isEmpty()) {
                // 【PR-02/PR-03 前置】配置了流程但解析不出任何有效审批人:拒绝提交并随事务回滚,
                // 不产生无人可办的"审批中"假成功单据(完整提交预检属 PR-03,此处只堵串行引擎死锁)
                throw new BusinessException("审批流程已配置但无有效审批人，请联系管理员检查流程配置");
            }
            BizApprovalConfig first = approvalConfigMapper.selectOne(
                    new LambdaQueryWrapper<BizApprovalConfig>()
                            .eq(BizApprovalConfig::getFlowType, type)
                            .eq(BizApprovalConfig::getStatus, 1)
                            .orderByAsc(BizApprovalConfig::getSortOrder)
                            .last("LIMIT 1")
            );
            approval.setCurrentNode(first != null ? first.getFlowName() : "");
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

            int createdNodes = 0;
            for (BizApprovalFlow flow : flows) {
                if (leaderId != null) {
                    BizApprovalNode node = new BizApprovalNode();
                    node.setApprovalId(approval.getId());
                    node.setApproverId(leaderId);
                    node.setNodeOrder(flow.getSortOrder());
                    node.setStatus("pending");
                    approvalNodeMapper.insert(node);
                    createdNodes++;
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
                        createdNodes++;
                    }
                }
            }
            // 【PR-03 2026-09-19 零审批人封堵】legacy 路径与配置路径同口径:解析不出任何节点
            // 直接拒绝并随事务回滚(含公海锁定释放)——此前会静默落一张 reviewing 且零节点的
            // 永久卡死单(诊断 F03"假成功单据")。
            if (createdNodes == 0) {
                throw new BusinessException("审批流程未配置或无有效审批人，请联系管理员配置后再提交");
            }
            approval.setCurrentNode(flows.isEmpty() ? "" : flows.get(0).getFlowName());
        }
        approvalMapper.updateById(approval);
    }

    /**
     * 【议题二.1 裁决 2026-09-19】管理员手动转交:把待办(pending/waiting)节点的审批人
     * 换成指定有效用户。仅平台 admin;审批主行锁(与 approve/reject 同款防死锁);
     * 新审批人须存在且启用;审计留痕。
     */
    @Transactional
    public void transferNode(Long approvalId, Long nodeId, Long newApproverId) {
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "仅平台管理员可转交审批节点");
        }
        BizApproval approval = approvalMapper.selectByIdForUpdate(approvalId);
        if (approval == null
                || "approved".equals(approval.getStatus()) || "rejected".equals(approval.getStatus())) {
            throw new BusinessException("审批不存在或已结束,无法转交");
        }
        BizApprovalNode node = approvalNodeMapper.selectById(nodeId);
        if (node == null || !approvalId.equals(node.getApprovalId())) {
            throw new BusinessException("审批节点不存在或不属于该审批单");
        }
        if (!"pending".equals(node.getStatus()) && !"waiting".equals(node.getStatus())) {
            throw new BusinessException("仅待审批(pending/waiting)节点可转交");
        }
        SysUser target = userMapper.selectById(newApproverId);
        if (target == null || target.getStatus() == null || target.getStatus() != 1
                || (target.getDeleted() != null && target.getDeleted() == 1)) {
            throw new BusinessException("目标审批人不存在或已停用");
        }
        Long oldApproverId = node.getApproverId();
        node.setApproverId(newApproverId);
        approvalNodeMapper.updateById(node);
        securityAudit.userAdminEvent("approval_transfer", newApproverId, target.getUsername());
        org.slf4j.LoggerFactory.getLogger(ApprovalService.class).info(
                "审批节点转交 approvalId={} nodeId={} {} -> {} actor={}",
                approvalId, nodeId, oldApproverId, newApproverId, SecurityUtils.getCurrentUserId());
    }

    @Transactional
    public void approve(Long approvalId, String comment) {
        Long approverId = SecurityUtils.getCurrentUserId();
        // 【PR-22 活体发现 2026-09-19】先锁审批主行再操作节点:串行化同单并发,
        // 消除双人各自锁自己节点再互取消对方的 AB-BA 死锁;败者等锁后看到节点已被取消,
        // 走统一 404 文案而非 500 死锁
        BizApproval approval = approvalMapper.selectByIdForUpdate(approvalId);
        // 【2026-08-14 404 防探测】单条语义接口统一文案: 不存在/已结束/无审批节点均返回同一消息, 消除存在性 oracle
        if (approval == null) throw new BusinessException("资源不存在或无权访问");

        // 【P0 审批状态机】终态守卫：已驳回/已通过的审批不得再操作（防驳回后复活）。
        // 合法态含 pending(库默认值/历史存量) 与 reviewing(发起时写入)，终态仅 approved/rejected。
        if ("approved".equals(approval.getStatus()) || "rejected".equals(approval.getStatus())) {
            throw new BusinessException("资源不存在或无权访问");
        }

        BizApprovalNode node = approvalNodeMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approvalId)
                        .eq(BizApprovalNode::getApproverId, approverId)
                        .eq(BizApprovalNode::getStatus, "pending")
        );
        if (node == null) throw new BusinessException("资源不存在或无权访问");

        // 【PR-02 2026-09-19 并发/重复点击】条件流转:仅 pending 可被审批。affected=0 说明
        // 重复点击或并发竞争中他人已处理,直接拒绝——多人同时通过结果唯一。
        int transitioned = approvalNodeMapper.update(null, new LambdaUpdateWrapper<BizApprovalNode>()
                .set(BizApprovalNode::getAction, "approve")
                .set(BizApprovalNode::getComment, comment)
                .set(BizApprovalNode::getOperatedAt, LocalDateTime.now())
                .set(BizApprovalNode::getStatus, "approved")
                .eq(BizApprovalNode::getId, node.getId())
                .eq(BizApprovalNode::getStatus, "pending"));
        if (transitioned != 1) {
            throw new BusinessException("该审批已处理，请刷新后查看");
        }
        node.setStatus("approved");
        advanceApproval(approval, node);
    }

    /**
     * 【PR-02 2026-09-19】阶段感知推进(或签/会签/串行统一执行):
     * <ul>
     *   <li>存量在途单(节点均无 configId)维持原扁平会签口径:全部 pending 清零即整单通过;</li>
     *   <li>新式节点按 nodeOrder 分阶段:阶段内 ANY(或签)任一合格审批人通过即完成该节点组,
     *       其余待办取消;ALL(会签)需本阶段全部通过;阶段完成后激活下一 waiting 阶段(串行按序),
     *       无下一阶段则整单通过并回写业务(条件更新保证并发下只回写一次)。</li>
     * </ul>
     */
    private void advanceApproval(BizApproval approval, BizApprovalNode approvedNode) {
        List<BizApprovalNode> nodes = approvalNodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approval.getId())
                        .orderByAsc(BizApprovalNode::getNodeOrder).orderByAsc(BizApprovalNode::getId));

        boolean legacy = nodes.stream().noneMatch(n -> n.getConfigId() != null);
        if (legacy) {
            long pendingCount = nodes.stream().filter(n -> "pending".equals(n.getStatus())).count();
            if (pendingCount == 0) {
                finalizeApproval(approval);
            }
            return;
        }

        int myStage = approvedNode.getNodeOrder() != null ? approvedNode.getNodeOrder() : 1;
        boolean anySignMode = "ANY".equalsIgnoreCase(approvedNode.getSignMode());
        List<BizApprovalNode> stageNodes = nodes.stream()
                .filter(n -> n.getNodeOrder() != null && n.getNodeOrder() == myStage).toList();
        // 或签:本人已通过(条件流转保证唯一)即阶段完成;会签:本阶段 pending 清零
        boolean stageDone = anySignMode
                || stageNodes.stream().noneMatch(n -> "pending".equals(n.getStatus()));
        if (!stageDone) return;

        for (BizApprovalNode s : stageNodes) {
            if ("pending".equals(s.getStatus())) {
                approvalNodeMapper.update(null, new LambdaUpdateWrapper<BizApprovalNode>()
                        .set(BizApprovalNode::getStatus, "rejected")
                        .set(BizApprovalNode::getAction, "cancelled")
                        .set(BizApprovalNode::getComment, "或签已完成，本节点自动取消")
                        .set(BizApprovalNode::getOperatedAt, LocalDateTime.now())
                        .eq(BizApprovalNode::getId, s.getId())
                        .eq(BizApprovalNode::getStatus, "pending"));
                s.setStatus("rejected"); // 内存态与库同步,后续"全单是否完结"判定不吃过期快照
            }
        }

        int nextStage = Integer.MAX_VALUE;
        for (BizApprovalNode n : nodes) {
            if (n.getNodeOrder() != null && n.getNodeOrder() > myStage && n.getNodeOrder() < nextStage) {
                nextStage = n.getNodeOrder();
            }
        }
        if (nextStage == Integer.MAX_VALUE) {
            // 无后续阶段:确认全单无未完结节点(防御性,串行下自然成立)
            boolean anyOpen = nodes.stream()
                    .anyMatch(n -> "pending".equals(n.getStatus()) || "waiting".equals(n.getStatus()));
            if (!anyOpen) {
                finalizeApproval(approval);
            }
            return;
        }
        for (BizApprovalNode n : nodes) {
            if (n.getNodeOrder() == nextStage && "waiting".equals(n.getStatus())) {
                approvalNodeMapper.update(null, new LambdaUpdateWrapper<BizApprovalNode>()
                        .set(BizApprovalNode::getStatus, "pending")
                        .eq(BizApprovalNode::getId, n.getId())
                        .eq(BizApprovalNode::getStatus, "waiting"));
            }
        }
        final int activatedStage = nextStage;
        Long nextConfigId = nodes.stream()
                .filter(n -> n.getNodeOrder() != null && n.getNodeOrder() == activatedStage && n.getConfigId() != null)
                .map(BizApprovalNode::getConfigId).findFirst().orElse(null);
        if (nextConfigId != null) {
            BizApprovalConfig cfg = approvalConfigMapper.selectById(nextConfigId);
            if (cfg != null) {
                approval.setCurrentNode(cfg.getFlowName());
                approvalMapper.updateById(approval);
            }
        }
    }

    /** 【PR-02】整单通过:reviewing→approved 条件更新,并发竞争下仅一个事务执行业务回写(只回写一次) */
    private void finalizeApproval(BizApproval approval) {
        int won = approvalMapper.update(null, new LambdaUpdateWrapper<BizApproval>()
                .set(BizApproval::getStatus, "approved")
                .eq(BizApproval::getId, approval.getId())
                .in(BizApproval::getStatus, "reviewing", "pending"));
        if (won == 1) {
            approval.setStatus("approved");
            executeApprovalAction(approval);
        }
    }

    @Transactional
    public void reject(Long approvalId, String comment) {
        Long approverId = SecurityUtils.getCurrentUserId();
        // 【PR-22 活体发现】与 approve 同款主行锁:同单驳回/通过并发也须串行
        BizApproval approval = approvalMapper.selectByIdForUpdate(approvalId);
        // 【2026-08-14 404 防探测】与 approve 同款统一文案(不存在/已结束/无审批节点), 防存在性探测
        if (approval == null) throw new BusinessException("资源不存在或无权访问");

        // 【P0 审批状态机】终态守卫：已通过/已驳回的审批不得再操作（防反向状态翻转）
        if ("approved".equals(approval.getStatus()) || "rejected".equals(approval.getStatus())) {
            throw new BusinessException("资源不存在或无权访问");
        }

        BizApprovalNode node = approvalNodeMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approvalId)
                        .eq(BizApprovalNode::getApproverId, approverId)
                        .eq(BizApprovalNode::getStatus, "pending")
        );
        if (node == null) throw new BusinessException("资源不存在或无权访问");

        // 【PR-02 并发/重复点击】与 approve 同款条件流转
        int transitioned = approvalNodeMapper.update(null, new LambdaUpdateWrapper<BizApprovalNode>()
                .set(BizApprovalNode::getAction, "reject")
                .set(BizApprovalNode::getComment, comment)
                .set(BizApprovalNode::getOperatedAt, LocalDateTime.now())
                .set(BizApprovalNode::getStatus, "rejected")
                .eq(BizApprovalNode::getId, node.getId())
                .eq(BizApprovalNode::getStatus, "pending"));
        if (transitioned != 1) {
            throw new BusinessException("该审批已处理，请刷新后查看");
        }

        // 【PR-02】整单驳回条件更新:并发下仅一个事务执行公海释放等业务回写
        int won = approvalMapper.update(null, new LambdaUpdateWrapper<BizApproval>()
                .set(BizApproval::getStatus, "rejected")
                .eq(BizApproval::getId, approval.getId())
                .in(BizApproval::getStatus, "reviewing", "pending"));
        if (won == 1) {
            approval.setStatus("rejected");

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
                    // 【体检 H5 2026-10-12】同步清 claim_date(锁定时已写入), 与超时释放口径一致,
                    // 防 available 行残留旧时间戳干扰排查
                    pool.setClaimDate(null);
                    seaPoolMapper.updateById(pool);
                }
            }
        }

        // 【P0 审批状态机】级联作废其他待审批节点，防终态后残留 pending 脏数据;
        // 【PR-02】串行未激活的 waiting 节点一并作废
        List<BizApprovalNode> siblings = approvalNodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getApprovalId, approvalId)
                        .in(BizApprovalNode::getStatus, "pending", "waiting"));
        for (BizApprovalNode s : siblings) {
            s.setStatus("rejected");
            s.setAction("cancelled");
            s.setComment("审批已驳回，本节点自动作废");
            s.setOperatedAt(LocalDateTime.now());
            approvalNodeMapper.updateById(s);
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
        // 【2026-09-19 接入权限矩阵】(approval,view) 显式配置优先，未配置回退角色数据范围
        List<Long> visible = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "approval");
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
        // 【工作流复核确认修复】补全量口径键: 页签角标原用 ThisMonth 而列表 tab=approved/rejected 返回
        // 全部历史,凡有历史单据角标与分页 total 必对不上(INV-12)。前端角标换用全量键,ThisMonth 保留给指标条。
        stats.put("approvedTotal", approvalMapper.selectCount(
                scoped.clone().eq(BizApproval::getStatus, "approved")));
        stats.put("rejectedTotal", approvalMapper.selectCount(
                scoped.clone().eq(BizApproval::getStatus, "rejected")));
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
                // 【议题一裁决 2026-09-19 保护期决策表】延期天数走 sys_config(customer.extend.days,
                // 管理员可改,与保护期同口径);approved_extend_days 累加后由统一公式重算到期日
                // (=开票推导+累计已批延期)——删票回算开票部分不收回已批延期。
                BizCustomer customer = customerMapper.selectById(approval.getCustomerId());
                if (customer != null) {
                    int extendDays = getConfigInt("customer.extend.days", 360);
                    int accumulated = customer.getApprovedExtendDays() != null
                            ? customer.getApprovedExtendDays() : 0;
                    customer.setApprovedExtendDays(accumulated + extendDays);
                    // 重算开票推导部分并叠加累计延期(唯一公式,与开票增删路径同源)
                    // 【回归修复 2026-10-09 公海死循环】基准日=max(开票日,注册日)——COALESCE(开票日优先)
                    // 使 legacy 老票(生产实证 2015/2024 年)把基准拖回过去,延期审批一通过到期日即变
                    // 过去日期,凌晨任务踢回公海(302/303 "捞取→掉海→再捞"死循环)。与
                    // CustomerService.calculateExpireDates 同口径。
                    java.time.LocalDate baseDate = customer.getLastInvoiceDate() != null && customer.getRegisterDate() != null
                            ? (customer.getLastInvoiceDate().isAfter(customer.getRegisterDate())
                                    ? customer.getLastInvoiceDate() : customer.getRegisterDate())
                            : (customer.getLastInvoiceDate() != null ? customer.getLastInvoiceDate() : customer.getRegisterDate());
                    java.time.LocalDate invoiceExpiry = baseDate != null
                            ? baseDate.plusDays(getConfigInt("customer.protection.days", 360)) : null;
                    if (invoiceExpiry != null) {
                        customer.setExpireDate(invoiceExpiry.plusDays(customer.getApprovedExtendDays()));
                    } else {
                        // 无基准日:以当前日期起算(业务口径: 从未设置保护期=即日起延期)
                        customer.setExpireDate(java.time.LocalDate.now().plusDays(customer.getApprovedExtendDays()));
                    }
                    customer.setProtectionStatus("active");
                    customer.setStatus("normal");
                    customerMapper.updateById(customer);
                }
            }
            case "transfer" -> {
                // 转移 - 负责人变更（解析失败必须显式报错，随事务回滚，不得静默成功）
                // 【PR-09 2026-09-19 议题二/五裁决衔接】接收人有效性+集团边界+部门同步
                if (approval.getExtraData() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = om.readValue(approval.getExtraData(), Map.class);
                        Long newOwnerId = Long.valueOf(data.get("newOwnerId").toString());
                        BizCustomer customer = customerMapper.selectById(approval.getCustomerId());
                        if (customer != null) {
                            // 【PR-09】接收人有效性:必须存在、启用、未删除
                            SysUser newOwner = userMapper.selectById(newOwnerId);
                            if (newOwner == null || newOwner.getStatus() == null || newOwner.getStatus() != 1
                                    || (newOwner.getDeleted() != null && newOwner.getDeleted() == 1)) {
                                throw new BusinessException("接收人不存在或已停用,转移失败;请转交后重试");
                            }
                            // 【PR-09】集团边界:非 admin 时,接收人须与当前会话同集团
                            // (客户 tenant_id 由拦截器管理,实体无字段;用会话集团做边界判定)
                            if (!SecurityUtils.isAdmin()) {
                                Long sessionGroup = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
                                if (sessionGroup != null) {
                                    List<Long> groupMembers = com.aigicrm.common.scope.GroupScopeUtil
                                            .userIdsInGroup(jdbc, sessionGroup);
                                    if (!groupMembers.contains(newOwnerId)) {
                                        throw new BusinessException("接收人不在当前集团,跨集团转移需平台管理员处理");
                                    }
                                }
                            }
                            customer.setOwnerId(newOwnerId);
                            customer.setTransferDate(LocalDate.now());
                            // 部门同步:客户归属部门=新负责人部门(与创建/编辑 persistCustomerDeptId 同口径)。
                            // 【修复 2026-10-08 需求对齐】接收人无部门时必须显式清空——updateById 的
                            // NOT_NULL 策略不写 null 字段,仅条件 set 会残留旧负责人部门,ORG 锚点
                            // (biz_customer.dept_id)据此错误关联;2026-08-12 A3 已在创建/编辑路径补齐
                            // 同款问题,本处为转移分支补齐。
                            customerMapper.update(null, new LambdaUpdateWrapper<BizCustomer>()
                                    .eq(BizCustomer::getId, customer.getId())
                                    .set(BizCustomer::getOwnerId, newOwnerId)
                                    .set(BizCustomer::getTransferDate, LocalDate.now())
                                    .set(BizCustomer::getDeptId, newOwner.getDeptId()));
                        }
                    } catch (BusinessException be) {
                        throw be; // 业务校验失败直接透传(不走通用 catch 拼接)
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

    private int getConfigInt(String key, int defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值
            }
        }
        return defaultValue;
    }

    /**
     * 解析转移审批 extraData 中的 newOwnerId(缺失/脏值显式报错,不静默放行到审批执行侧才失败)。
     * 与 applyApprovalAction transfer 分支的解析口径一致,仅位置前移。
     */
    private Long parseTransferNewOwner(String extraData) {
        if (extraData == null || extraData.isBlank()) {
            throw new BusinessException("转移申请缺少接收人信息,请重新发起");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(extraData, Map.class);
            Object v = data.get("newOwnerId");
            if (v == null || v.toString().isBlank()) {
                throw new BusinessException("转移申请缺少接收人信息,请重新发起");
            }
            return Long.valueOf(v.toString());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("转移申请接收人信息格式错误,请重新发起");
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
