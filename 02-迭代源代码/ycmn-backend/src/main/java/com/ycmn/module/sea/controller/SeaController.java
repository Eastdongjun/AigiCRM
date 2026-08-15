package com.ycmn.module.sea.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.common.BusinessException;
import com.ycmn.common.Result;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.dto.CustomerImportResult;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.entity.BizApprovalNode;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.sea.entity.BizSeaConfig;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.service.SeaConfigService;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sea")
@RequiredArgsConstructor
public class SeaController {

    private final SeaService seaService;
    private final SeaConfigService seaConfigService;
    private final BizApprovalMapper approvalMapper;
    private final BizApprovalNodeMapper approvalNodeMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final OrgScopeResolver scopeResolver;

    // ===== 公海池列表 =====

    @PreAuthorize("hasAuthority('sea:view')")
    @GetMapping("/list")
    public Result<IPage<BizSeaPool>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String enterReason,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long poolId,
            @RequestParam(required = false) String status) {
        return Result.ok(seaService.page(current, size, industry, officeId, enterReason, keyword, poolId, deptId, status));
    }

    @PreAuthorize("hasAuthority('sea:view')")
    @GetMapping("/statistics")
    public Result<Map<String, Long>> statistics() {
        return Result.ok(seaService.statistics());
    }

    /** 删除公海池客户（仅待捞取状态可删，逻辑删客户+删公海记录） */
    @DeleteMapping("/pool/{id}")
    // 【2026-08-10 池级权限】角色码 sea:pool:delete 不再作硬门——池级删除权限在 SeaService.deletePool 内判定(admin 恒有)
    public Result<Void> deletePool(@PathVariable Long id) {
        seaService.deletePool(id);
        return Result.ok();
    }

    /** 公海池批量导入（模板同客户导入，导入即新建客户并直接入公海） */
    @PostMapping("/import")
    // 【权限修复 2026-08-09】统一导入权限码为 sea:import（0805 新增码），废弃旧的 sea:pool:import 双轨
    @PreAuthorize("hasAuthority('sea:import')")
    public Result<CustomerImportResult> importToSea(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 格式的文件");
        }
        return Result.ok(seaService.importToSea(file));
    }

    // ===== 公海池配置 =====

    @PreAuthorize("hasAuthority('sea:config:view')")
    @GetMapping("/configs")
    public Result<List<BizSeaConfig>> listConfigs() {
        return Result.ok(seaConfigService.list());
    }

    /** 当前用户可见的公海池(公海池页面选择器/列表用)
     *  【修复 2026-08-10】sea:config:view → sea:view——此接口是公海页(销售员用)的池选择器,
     *  非配置管理; 原 sea:config:view 门槛使销售员(无配置权限)公海页池下拉 403/空 */
    @PreAuthorize("hasAuthority('sea:view')")
    @GetMapping("/configs/visible")
    public Result<List<BizSeaConfig>> listVisibleConfigs() {
        return Result.ok(seaConfigService.listVisible());
    }

    /** 组织机构树(部门+人员可选,办事处/总部灰显),供"可见部门/人员"树选择器。
     *  【修复 2026-08-09 B3】仅公海配置编辑者可用——返回全组织+全人员,原 sea:view 会泄露给普通销售员 */
    @PreAuthorize("hasAuthority('sea:config:view')")
    @GetMapping("/org-tree")
    public Result<List<Map<String, Object>>> orgTree() {
        return Result.ok(seaConfigService.buildOrgTree());
    }

    @PreAuthorize("hasAuthority('sea:config:edit')")
    @PostMapping("/configs")
    public Result<Void> createConfig(@RequestBody BizSeaConfig config) {
        seaConfigService.create(config);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('sea:config:edit')")
    @PutMapping("/configs/{id}")
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody BizSeaConfig config) {
        config.setId(id);
        seaConfigService.update(config);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('sea:config:edit')")
    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        seaConfigService.delete(id);
        return Result.ok();
    }

    // ===== 捞取记录 =====

    @PreAuthorize("hasAuthority('sea:view')")
    @GetMapping("/claim-records")
    public Result<Map<String, Object>> claimRecords(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<BizApproval> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizApproval::getApprovalType, "claim")
                .eq(BizApproval::getApplicantId, SecurityUtils.getCurrentUserId())
                .orderByDesc(BizApproval::getCreatedAt);

        if (StringUtils.hasText(status)) {
            wrapper.eq(BizApproval::getStatus, status);
        }
        if (StringUtils.hasText(dateFrom)) {
            wrapper.ge(BizApproval::getCreatedAt, dateFrom + " 00:00:00");
        }
        if (StringUtils.hasText(dateTo)) {
            wrapper.le(BizApproval::getCreatedAt, dateTo + " 23:59:59");
        }

        IPage<BizApproval> page = approvalMapper.selectPage(new Page<>(current, size), wrapper);
        // S7: keyword 涉及申请人/客户/审批人名(关联字段+嵌套审批节点),无法下推 SQL;本接口仅"我的捞取申请"(数据量小),
        //      keyword 场景改为全量构建→内存过滤→手工分页,保证 total 与记录一致
        List<BizApproval> source = StringUtils.hasText(keyword) ? approvalMapper.selectList(wrapper) : page.getRecords();

        List<Map<String, Object>> records = new ArrayList<>();
        for (BizApproval a : source) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", a.getId());
            row.put("applyDate", a.getCreatedAt());
            row.put("status", a.getStatus());

            // 客户信息
            if (a.getCustomerId() != null) {
                BizCustomer c = customerMapper.selectById(a.getCustomerId());
                if (c != null) {
                    row.put("customerName", c.getCustomerName());
                    row.put("industry", c.getIndustry());
                }
            }

            // 申请人
            if (a.getApplicantId() != null) {
                SysUser applicant = userMapper.selectById(a.getApplicantId());
                if (applicant != null) row.put("applicantName", applicant.getRealName());
            }

            // 审批人（取第一个审批节点）
            List<BizApprovalNode> nodes = approvalNodeMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalNode>()
                            .eq(BizApprovalNode::getApprovalId, a.getId())
                            .orderByAsc(BizApprovalNode::getNodeOrder));
            if (!nodes.isEmpty()) {
                BizApprovalNode node = nodes.get(0);
                if (node.getApproverId() != null) {
                    SysUser approver = userMapper.selectById(node.getApproverId());
                    if (approver != null) row.put("approverName", approver.getRealName());
                }
                row.put("approveTime", node.getOperatedAt());
            }

            records.add(row);
        }

        // 关键字过滤
        if (StringUtils.hasText(keyword)) {
            records.removeIf(r -> {
                String cn = (String) r.get("customerName");
                String an = (String) r.get("applicantName");
                String avn = (String) r.get("approverName");
                return (cn == null || !cn.contains(keyword))
                        && (an == null || !an.contains(keyword))
                        && (avn == null || !avn.contains(keyword));
            });
            // 手工分页（全量过滤后）：total = 匹配数
            int from = (current - 1) * size;
            int total = records.size();
            records = from >= total ? new ArrayList<>()
                    : new ArrayList<>(records.subList(from, Math.min(from + size, total)));
            page = new Page<>(current, size, total);
        }

        // 统计（【修复 2026-08-08 统计泄漏】按数据范围过滤申请人，上级/负责人可见下级捞取统计）
        LambdaQueryWrapper<BizApproval> claimBase = new LambdaQueryWrapper<BizApproval>()
                .eq(BizApproval::getApprovalType, "claim");
        List<Long> visibleApplicants = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visibleApplicants != null) claimBase.in(BizApproval::getApplicantId, visibleApplicants);
        long totalAll = approvalMapper.selectCount(claimBase);
        long approved = approvalMapper.selectCount(new LambdaQueryWrapper<BizApproval>()
                .eq(BizApproval::getApprovalType, "claim").eq(BizApproval::getStatus, "approved")
                .in(visibleApplicants != null, BizApproval::getApplicantId, visibleApplicants));
        long rejected = approvalMapper.selectCount(new LambdaQueryWrapper<BizApproval>()
                .eq(BizApproval::getApprovalType, "claim").eq(BizApproval::getStatus, "rejected")
                .in(visibleApplicants != null, BizApproval::getApplicantId, visibleApplicants));
        long pending = approvalMapper.selectCount(new LambdaQueryWrapper<BizApproval>()
                .eq(BizApproval::getApprovalType, "claim").eq(BizApproval::getStatus, "reviewing")
                .in(visibleApplicants != null, BizApproval::getApplicantId, visibleApplicants));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("stats", Map.of("total", totalAll, "approved", approved, "rejected", rejected, "pending", pending));

        return Result.ok(result);
    }
}
