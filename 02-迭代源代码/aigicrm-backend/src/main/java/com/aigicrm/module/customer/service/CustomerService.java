package com.aigicrm.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcelFactory;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.PermissionRuleEngine;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.entity.*;
import com.aigicrm.module.customer.listener.CustomerImportListener;
import com.aigicrm.module.customer.mapper.*;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_CUSTOMER = "customer";
    private static final String LIT_ARCHIVED = "archived";
    private static final String LIT_NORMAL = "normal";
    private static final String LIT_ACTIVE = "active";
    private static final String LIT_PENDING = "pending";
    private static final String LIT_X = "客户不存在";
    private static final String LIT_SHARE_TO = "share_to";
    private static final String LIT_VOIDED = "voided";
    private static final String LIT_X_2 = "发票不存在或不属于该客户";
    private static final String LIT_X_3 = "回款不存在或不属于该客户";
    private static final String LIT_LIMIT_1 = "LIMIT 1";


    /** 「所属集团」下拉候选返回上限（防大数据量下拉过长，超出的集团名仍可手工输入新建） */
    private static final int GROUP_NAME_LIMIT = 200;

    private final BizCustomerMapper customerMapper;
    private final BizCustomerLogMapper customerLogMapper;
    private final BizInvoiceMapper invoiceMapper;
    private final BizCustomerIndicatorMapper indicatorMapper;
    private final BizPaymentMapper paymentMapper;
    private final BizCustomerStageAmountMapper stageAmountMapper;
    private final BizSeaPoolMapper seaPoolMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final JdbcTemplate jdbc;
    private final SysConfigService configService;
    private final com.aigicrm.common.engine.CustomerAccessService customerAccessService;
    private final com.aigicrm.module.approval.mapper.BizApprovalMapper approvalMapper;
    // 【裁决 2026-09-24】财务删除审计(sys_log 快照留痕)
    private final com.aigicrm.module.system.mapper.SysLogMapper financeAuditLogMapper;

    /** 【2026-11-17 阶段三】统一回收站台账；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.module.system.service.RecycleBinService recycleBinService;
    /** 【生命线 2026-11-02 裁决D】客户日期事件追加记录 */
    private final CustomerEventService customerEventService;
    /** S5.3 分享/联系人独立 bean；本类保留公共委托。 */
    private final CustomerContactShareService customerContactShareService;
    /** S5.3 数据范围/自动共享独立 bean；本类保留条件拼装委托。 */
    private final CustomerScopeService customerScopeService;
    /** S5.3 列表/计数/导出/名称回填独立 bean；本类保留公共委托。 */
    private final CustomerQueryService customerQueryService;

    /**
     * 【容量加固·客户列表服务端分页 2026-10-05】列表统一查询条件对象:
     * 分页(pageList)/类型页签计数(countByDevType)/导出(exportList)三口径共用一份条件拼装,
     * 取代"PC 前端一次拉 500 条 + 本地三层过滤"模式(容量评估流量模型高危项)。
     * userIds 与 ownerName 为同列的两个独立 AND 条件(对齐本地 filterCustomers 语义);
     * officeIds 对齐本地 orgIds:所属组织单元命中 office 或 dept 即算。
     */
    /** 服务端分页查询(新口径入口) */
    // ====== 列表/计数/导出（S5.3 语义已移到 CustomerQueryService） ======

    public IPage<BizCustomer> pageList(CustomerListFilter f, int current, int size) {
        return customerQueryService.pageList(f, current, size);
    }

    public Map<String, Long> countByDevType(CustomerListFilter f) {
        return customerQueryService.countByDevType(f);
    }

    public List<BizCustomer> exportList(CustomerListFilter f) {
        return customerQueryService.exportList(f);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, null, null, null, null);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, null, null, null);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, devType, groupName, null);
    }

    @SuppressWarnings("unused") // else-if(false) 设计历史分支为监督面板强制保留(见下), 抑制 Dead code 告警
    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName, Long deptId) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, devType, groupName, deptId, null, null, null);
    }

    /**
     * 【对齐 PC 高级筛选 2026-09-21】protectionStatus 精确 / province / city LIKE。
     * 【容量加固 2026-10-05】本重载保留作兼容入口(条件拼装已统一迁入 buildListWrapper/applyFilterConditions,
     * 供 pageList/countByDevType/exportList 三口径共用;shared_to_me/shared_by_me 分支亦在其中统一处理)。
     */
    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName, Long deptId,
                                    String protectionStatus, String province, String city) {
        return pageList(new CustomerListFilter(keyword, industry, officeId, regionId, deptId, status, ownerId, scope,
                devType, groupName, protectionStatus, province, city,
                null, null, null, null, null, null, null), current, size);
    }

    // 【容量加固 2026-10-05】pageSharedToMe/pageSharedByMe/applyCommonFilters 三方法已删除:
    // 分享视图的 scope 分支与全量条件统一并入 buildListWrapper/applyFilterConditions(三口径共用)。

    public Map<String, Long> listScopeCounts() {
        return customerQueryService.listScopeCounts();
    }

    public List<String> listGroupNames() {
        return customerQueryService.listGroupNames();
    }

    public List<Map<String, Object>> getSimpleUserList() {
        // 【修复 2026-08-08 全量用户下拉】按数据范围(含系统级开关)过滤，不再返回全部用户
        // 【PR-01 P1-09 2026-09-18】all(=resolve null)对非 admin 收敛为当前集团成员；
        // 无集团归属直接空集（sys_user 非租户表，拦截器管不到，必须显式边界）。
        // 【2026-09-23 选人边界修正】选人(转移/分享接收人)≠数据可见性:按数据范围(SELF/team)过滤会让
        // 同集团跨团队的合法转移在发起端就选不到接收人(实测:太原销售员下拉仅 1 名队友,内蒙同事不可选;
        // 队友恰为现任负责人时下拉恒空——即用户所报"转移客户接收人看不到数据")。与 PR-09 转移集团边界
        // (审批执行侧仅拦跨集团)对齐:非 admin 一律=当前集团成员,数据范围语义留给各业务数据列表查询。
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .eq(SysUser::getDeleted, 0)
                .orderByAsc(SysUser::getId);
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        List<Long> groupMembers = (g == null)
                ? List.of()
                : com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
        // 空集合显式空集返回（不依赖各版本对 IN() 空集合的拼接语义）
        if (groupMembers.isEmpty()) return List.of();
        wrapper.in(SysUser::getId, groupMembers);
        List<SysUser> users = userMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("realName", u.getRealName());
            result.add(m);
        }
        return result;
    }

    public BizCustomer getById(Long id) {
        BizCustomer customer = customerMapper.selectById(id);
        // 【2026-08-14 404 防探测】不存在与无权访问统一文案, 消除存在性 oracle(GlobalExceptionHandler 按 code=404 转 HTTP 404)
        if (customer == null) {
            throw new BusinessException(404, "客户不存在或无权访问");
        }
        customerQueryService.fillCustomerNames(java.util.List.of(customer));
        return customer;
    }

    @Transactional
    public void create(BizCustomer customer) {
        // 【2026-11-17 客户名称全集团唯一】归一化后查重（含历史豁免行，防“新客绕过旧同名”），
        // 写入 name_norm；dup_exempt=0。展示名只 trim，不改变用户输入形态。
        // 兼容服务层单测/历史内部调用：name 为 null 时交由调用方/DB 约束处理（Controller/导入已前置校验）。
        if (customer.getCustomerName() != null) {
            String nameNorm = CustomerNameNormalizer.normalizeOrThrow(customer.getCustomerName());
            if (existsByNameGlobal(nameNorm, null)) {
                throw new BusinessException(400, "客户名称已存在");
            }
            customer.setCustomerName(CustomerNameNormalizer.displayName(customer.getCustomerName()));
            customer.setNameNorm(nameNorm);
            customer.setDupExempt(0);
        }
        // 【A1 2026-10-20】approved_extend_days 是审批域服务端派生字段, 新建一律强制置 0:
        // 请求体显式携带的值(接口直调可注入, 全仓 PC/小程序表单均无此字段)不得进入到期日公式。
        // 合法写入点仅三类: 审批通过累加(ApprovalService.executeApprovalAction)、捞取回私海清零
        // (SeaService.completeClaim)、开票基准前移清零(advanceLastInvoiceDate)。
        customer.setApprovedExtendDays(0);
        // 【D3 2026-10-18 用户裁决】新建/导入未填注册日时服务端兜底为当天,
        // 再由统一公式算出 expireDate/adjustDate——不硬拒绝,但不再产生无保护期基准的新客户。
        // 存量无日期客户按 D2 裁决维持现状, 不在此修复(仅新建路径)。
        if (customer.getRegisterDate() == null) {
            customer.setRegisterDate(LocalDate.now());
        }
        // 【裁决 2026-09-24】最近开票日=纯推导字段(最后一次非作废开票的日期): 创建时一律清空,
        // 不接受表单直带——历史开票请在客户财务页补录发票记录(自动驱动保护期,全程留痕)
        customer.setLastInvoiceDate(null);
        // 【2026-11-02 裁决B】捞取日期=审批域派生字段(仅 applyClaimReset 写入), 创建时一律清空
        customer.setClaimDate(null);
        calculateExpireDates(customer);
        customer.setStatus(LIT_NORMAL);
        customer.setProtectionStatus(LIT_ACTIVE);
        // 【M1 2026-08-10】代建规则: 仅 admin 可指定负责人(代建/挂单); 其他用户 ownerId 强制=当前用户——
        // 原代码非空即采用请求体 ownerId, 任何持 customer:add 者都可把客户挂到任意销售/办事处名下
        if (customer.getOwnerId() == null || !SecurityUtils.isAdmin()) {
            customer.setOwnerId(SecurityUtils.getCurrentUserId());
        }
        if (customer.getOfficeId() == null) {
            customer.setOfficeId(SecurityUtils.getCurrentOfficeId());
        }
        // 【P2-2 2026-08-11】ORG 锚点依赖 biz_customer.dept_id, 此前从不落库导致 ORG 范围配置不可用。
        // 客户归属部门=负责人(owner)的部门(与"数据范围按负责人部门归属"锚点决策一致)。
        syncCustomerDeptId(customer, customer.getOwnerId());
        // 【A3 2026-10-20/21 复核修复】取号+插入改为"集团行 FOR UPDATE 取号锁 + 唯一键冲突
        // 有限重试"(编号生成移到插入前一刻, prefix 只依赖 regionId/officeId, 与原首行生成等价):
        // MAX+1 取号原本无行锁, 并发建客户算出同一编号, 后提交方撞 uk_tenant_customer_code
        // 直接 500; 详见 insertWithCodeGenerationRetry 注释(死锁刻意不捕获: InnoDB 死锁回滚
        // 整个事务, 事务内重试会造成部分提交)。
        insertWithCodeGenerationRetry(customer);
        // 【生命线 2026-11-02 裁决D】建档事件(create 为创建唯一入口, 手工/导入/代建均经此)
        customerEventService.record(customer.getId(), CustomerEventService.TYPE_CREATED,
                customer.getRegisterDate() != null ? customer.getRegisterDate() : LocalDate.now(),
                "客户建档", null, customer.getCustomerCode(), SecurityUtils.getCurrentUserId());
    }

    /** 【A3 2026-10-20】取号重试上限: 首次 + 2 次退让(并发建客常见为 2~3 方同抢一号) */
    private static final int CODE_GEN_MAX_ATTEMPTS = 3;

    /**
     * 【A3 2026-10-20 取号行锁 + 有限重试；2026-10-21 复核修复】客户编号 MAX+1 取号并发兜底:
     * <ul>
     *   <li>【取号行锁】先对集团行 SELECT ... FOR UPDATE(取号前把同集团并发建客串行化,
     *       锁粒度=集团(tenant), 与唯一键 (tenant_id, customer_code) 同维; 写法沿用
     *       ApprovalService 的 jdbc 行锁先例)。同集团 contenders 在锁上排队, 取号读数即最新
     *       已提交值, 正常路径不再产生 1062。无集团上下文(null)时不加锁, 退让重试兜底。</li>
     *   <li>【有限重试】捕获 DuplicateKeyException(1062 唯一键冲突, MySQL 语句级失败,
     *       事务保持完整, 同事务内重试安全)后按 bump 递增退让重试, 上限
     *       {@link #CODE_GEN_MAX_ATTEMPTS} 次, 超限抛原始异常(不吞错)。
     *       <b>刻意不捕获 ConcurrencyFailureException(死锁 1213/锁等待超时 1205)</b>:
     *       InnoDB 死锁回滚的是整个事务而非单条语句, 在 @Transactional 内捕获继续执行
     *       会造成"前序写已回滚、后续写随提交重新生效"的部分提交, 故死锁必须原样传播
     *       (与修复前行为一致), 不做事务内重试。</li>
     *   <li>biz_customer 仅有 (tenant_id, customer_code) 一个唯一键(V2026.09.21.3),
     *       DuplicateKeyException 即编号冲突, 退让重试恰对症。</li>
     * </ul>
     * 真实并发集成测试受限于仓库无 Testcontainers/H2(审计 F7, P2): 已补真实多线程 +
     * 模拟唯一索引(READ COMMITTED 语义)的并发单测钉"各方全部成功且编号互不相同"。
     */
    private void insertWithCodeGenerationRetry(BizCustomer customer) {
        // 【A3 2026-10-21 取号行锁】集团行锁(同租户串行取号); 集团行不存在/无集团上下文时
        // 查询返回空或不执行, 无锁降级 → 由下方唯一键冲突退让重试兜底, 不阻断创建。
        Long tenantId = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (tenantId != null) {
            jdbc.queryForList("SELECT id FROM sys_group WHERE id = ? FOR UPDATE", Long.class, tenantId);
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= CODE_GEN_MAX_ATTEMPTS; attempt++) {
            try {
                customer.setCustomerCode(generateCustomerCode(customer, attempt - 1));
                customerMapper.insert(customer);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                last = e;
            }
        }
        if (last != null) throw last;
    }

    /**
     * 【P2-2 2026-08-11】同步客户 dept_id=负责人(owner)的部门, 使 ORG 锚点数据范围可用。
     * owner 缺失或 owner 无部门时显式清空 dept_id(null)——避免遗留旧负责人的部门导致
     * ORG 锚点数据范围错误关联(2026-08-12 A3 补齐: 原实现仅 owner 有部门才 set,
     * 无部门时旧 dept_id 残留, updateById 又只写非 null 字段, 需在调用处显式清空)。
     * 返回 owner 的部门(可为 null), 供调用方决定如何落库。
     */
    private Long syncCustomerDeptId(BizCustomer customer, Long ownerId) {
        if (ownerId == null) return null;
        try {
            com.aigicrm.module.system.entity.SysUser owner = userMapper.selectById(ownerId);
            Long deptId = owner != null ? owner.getDeptId() : null;
            customer.setDeptId(deptId);
            return deptId;
        } catch (Exception e) {
            // 部门同步失败不阻断创建(数据范围 ORG 分支降级为空/本人, 安全性不劣化)
            org.slf4j.LoggerFactory.getLogger(CustomerService.class)
                    .warn("同步客户归属部门失败 ownerId={}", ownerId, e);
            return null;
        }
    }

    /**
     * 【P2-2 2026-08-11】将客户 dept_id 强制落库(含 null 清空)。
     * MyBatis-Plus updateById 只写非 null 字段, 无法用 null 清空 dept_id;
     * 此方法用 LambdaUpdateWrapper.set 显式写(含 SET dept_id=NULL), 保证与 owner 部门精确一致。
     */
    private void persistCustomerDeptId(Long customerId, Long deptId) {
        if (customerId == null) return;
        customerMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizCustomer>()
                .eq(BizCustomer::getId, customerId)
                .set(BizCustomer::getDeptId, deptId));
    }

    /** 【2026-11-17 客户名称全集团唯一】归一化查重：包含历史 dup_exempt 行（防新客绕过旧同名），
     *  排除自身 id；不做数据范围限制——唯一性是集团级不变量，不因调用者可见范围而改变。 */
    public boolean existsByNameGlobal(String customerName, Long excludeId) {
        String norm = CustomerNameNormalizer.normalize(customerName);
        if (norm == null || norm.isBlank()) return false;
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getNameNorm, norm);
        if (excludeId != null) {
            w.ne(BizCustomer::getId, excludeId);
        }
        return customerMapper.selectCount(w) > 0;
    }

    public boolean existsByName(String customerName) {
        return existsByName(customerName, null);
    }

    /** 【工作流复核确认修复】excludeId 非空时排除该客户(编辑场景查重不命中自身) */
    public boolean existsByName(String customerName, Long excludeId) {
        // 【修复 2026-08-10 P2-01】重名探测限定在当前用户数据范围内——
        // 此前任何登录用户可探测全公司任意客户名并获知创建人(跨范围信息泄露)
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getCustomerName, customerName);
        if (excludeId != null) {
            w.ne(BizCustomer::getId, excludeId);
        }
        List<Long> scopeUserIds = getCurrentScopeUserIds();
        if (scopeUserIds != null) {
            // 【整改残余 B1 2026-10-21】空集守卫(fail-closed): A6 后 ORG 锚点+可见部门零在职
            // 用户时 getCurrentScopeUserIds 返回非 null 空集, w.in(空集) 会生成 IN () 触发 SQL
            // 500(建客查重路径)。空范围=无任何可见属主, 查重必然不命中, 直接短路不触库。
            if (scopeUserIds.isEmpty()) {
                return false;
            }
            w.in(BizCustomer::getOwnerId, scopeUserIds);
        }
        return customerMapper.selectCount(w) > 0;
    }

    public CustomerImportResult importCustomers(MultipartFile file) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentOfficeId = SecurityUtils.getCurrentOfficeId();

        CustomerImportResult result = new CustomerImportResult();
        CustomerImportListener listener = new CustomerImportListener(
                this, currentUserId, currentOfficeId, result);

        try {
            EasyExcelFactory.read(file.getInputStream(), CustomerImportDTO.class, listener)
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        return listener.getResult();
    }

    private String generateCustomerCode(BizCustomer customer) {
        return generateCustomerCode(customer, 0);
    }

    /**
     * @param bump 取号退让偏移(A3 并发兜底): 0=首次取号 max+1; 重试时按 max+1+bump
     *             递增, 避开已被并发事务抢占的号码(见 {@link #insertWithCodeGenerationRetry})。
     */
    private String generateCustomerCode(BizCustomer customer, int bump) {
        String rule = configService.getValue("customer.code.rule");
        if (rule == null) rule = "region_date_seq";

        // 获取大区编码: 优先用客户所属大区(regionId), 未选则回退到按办事处反查
        String regionCode = getRegionCode(customer.getRegionId() != null
                ? customer.getRegionId() : customer.getOfficeId());
        Long tenantId = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();

        if ("region_seq".equals(rule)) {
            // 大区首字母-顺序号: NM-001
            String prefix = regionCode + "-";
            return prefix + String.format("%03d", currentMaxSeq(prefix, prefix.length(), tenantId) + 1 + bump);
        } else {
            // region_date_seq: 大区首字母-年月-顺序号: NM-202606-001
            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String prefix = regionCode + "-" + yearMonth + "-";
            return prefix + String.format("%03d", currentMaxSeq(prefix, prefix.length(), tenantId) + 1 + bump);
        }
    }

    /**
     * 【A3 2026-10-21】当前前缀最大序号: 有集团上下文时走 FOR UPDATE 锁定读(读最新已提交值,
     * 不受本事务 REPEATABLE READ 旧快照影响——普通 SELECT 在同事务内取号会复用并发事务已
     * 提交的号码); 无集团上下文时退回普通读(无锁, 由调用方唯一键冲突退让重试兜底)。
     * 调用方须已持有同集团取号串行化锁({@link #insertWithCodeGenerationRetry})。
     */
    private int currentMaxSeq(String prefix, int prefixLen, Long tenantId) {
        if (tenantId != null) {
            Integer max = customerMapper.selectMaxCodeByPrefixForUpdate(prefix, prefixLen, tenantId);
            return max != null ? max : 0;
        }
        return customerMapper.selectMaxCodeByPrefix(prefix, prefixLen, tenantId);
    }

    private String getRegionCode(Long officeId) {
        if (officeId == null) {
            officeId = SecurityUtils.getCurrentOfficeId();
        }
        // 查找办事处所属大区
        SysDept office = deptMapper.selectById(officeId);
        if (office != null) {
            // 如果本身就是大区(type=2)，直接用
            if (office.getDeptType() != null && office.getDeptType() == 2
                    && office.getDeptCode() != null) {
                return office.getDeptCode();
            }
            // 向上找大区
            if (office.getParentId() != null) {
                SysDept parent = deptMapper.selectById(office.getParentId());
                if (parent != null && parent.getDeptCode() != null) {
                    return parent.getDeptCode();
                }
            }
        }
        return "C"; // 兜底
    }

    @Transactional
    public void update(BizCustomer customer) {
        // 【A1 2026-10-20】读现值改用悲观锁(ApprovalService 延期执行同款先例):
        // update 全程复制库内 approved_extend_days, 若无行锁, "读到旧值→并发审批累加提交→
        // 本单 updateById 写回旧值"会丢一次累加; 锁内读改写保证与审批路径串行。
        BizCustomer existing = customerMapper.selectByIdForUpdate(customer.getId());
        if (existing == null) throw new BusinessException(LIT_X);
        assertUpdateAccess(existing, customer);
        applyNameValidation(existing, customer);
        boolean invoiceDateManuallyChanged = protectCustomerDateFields(existing, customer);
        // 【P2-2 2026-08-12 A3】始终按最终负责人(请求显式或库内现有)重同步 dept_id——
        // 负责人变更(admin 转交)与负责人部门调整都跟随; owner 无部门时显式清空,
        // 不再遗留旧负责人部门(原实现仅 admin 且 owner 变更时才同步, 且空部门跳过)。
        Long effectiveOwnerId = customer.getOwnerId() != null ? customer.getOwnerId() : existing.getOwnerId();
        Long effectiveDeptId = syncCustomerDeptId(customer, effectiveOwnerId);
        freezeUpdateFields(existing, customer, invoiceDateManuallyChanged);
        customerMapper.updateById(customer);
        // 【生命线 2026-11-02 裁决D】人工维护最近开票日事件(customer:invoice-date:edit 持码者操作)
        if (invoiceDateManuallyChanged) {
            customerEventService.record(customer.getId(), CustomerEventService.TYPE_INVOICE_MANUAL,
                    customer.getLastInvoiceDate(), "人工维护最近开票日(新基准重算保护期)",
                    existing.getLastInvoiceDate() != null ? existing.getLastInvoiceDate().toString() : null,
                    customer.getLastInvoiceDate() != null ? customer.getLastInvoiceDate().toString() : null,
                    SecurityUtils.getCurrentUserId());
        }
        // updateById 只写非 null 字段: owner 无部门时显式清空 dept_id, 防旧部门残留
        if (effectiveDeptId == null && existing.getDeptId() != null) {
            persistCustomerDeptId(customer.getId(), null);
        }
    }

    private void assertUpdateAccess(BizCustomer existing, BizCustomer customer) {
        // 【2026-08-10 池级权限】编辑校验: 客户在公海池内用池编辑权限, 否则负责人/管理员(checkOwnerOrAdmin 语义由 canEdit 覆盖)
        if (!customerAccessService.canEdit(customer.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("只能编辑自己的客户");
        }
        // 【2026-08-10 安全】公海池内客户禁止经编辑接口改属主/状态——防止池编辑者直接 ownerId=self 自领客户绕过捞取审批。
        // 属主/状态变更只能走释放/捞取流程(release/claim)。请求未显式传该字段(null)视为不修改, 以库内值为准。
        if (customerAccessService.isInSeaPool(customer.getId())) {
            if (fieldChanged(existing.getOwnerId(), customer.getOwnerId())
                    || fieldChanged(existing.getStatus(), customer.getStatus())
                    || fieldChanged(existing.getProtectionStatus(), customer.getProtectionStatus())) {
                throw new BusinessException("公海池内客户不允许修改负责人/状态，请通过捞取/释放流程操作");
            }
            customer.setOwnerId(existing.getOwnerId());
            customer.setStatus(existing.getStatus());
            customer.setProtectionStatus(existing.getProtectionStatus());
        } else if (!SecurityUtils.isAdmin()) {
            // 【M1 2026-08-10】非池客户负责人变更: 非 admin 只能走转移/审批流程, 防无审批转交
            if (fieldChanged(existing.getOwnerId(), customer.getOwnerId())) {
                throw new BusinessException("客户负责人变更请通过转移/审批流程操作");
            }
            // 【P2 2026-09-30 复审修复·流程字段冻结】非 admin 经通用编辑不得改 status/protectionStatus
            // ——状态迁移各有专门流程(释放/捞取/归档/恢复/保护期机制), 通用编辑绕过即越权;
            // 值未变(表单原样回传)不算变更放行, 与池分支 fieldChanged 口径一致。
            if (fieldChanged(existing.getStatus(), customer.getStatus())
                    || fieldChanged(existing.getProtectionStatus(), customer.getProtectionStatus())) {
                throw new BusinessException("客户状态/保护状态变更请通过归档、恢复、释放或保护期机制操作");
            }
        }
    }

    private void applyNameValidation(BizCustomer existing, BizCustomer customer) {
        // 【2026-11-17 客户名称全集团唯一】改名才查重；未改名保留历史 dup_exempt（祖父条款）。
        // 改名后 dup_exempt=0，进入严格唯一集合；归一化只影响 name_norm，展示名仅 trim。
        if (customer.getCustomerName() == null) return;
        String nameNorm = CustomerNameNormalizer.normalizeOrThrow(customer.getCustomerName());
        String existingNorm = existing.getNameNorm() != null
                ? existing.getNameNorm()
                : CustomerNameNormalizer.normalize(existing.getCustomerName());
        boolean nameChanged = existingNorm == null || !existingNorm.equals(nameNorm);
        if (nameChanged && existsByNameGlobal(nameNorm, customer.getId())) {
            throw new BusinessException(400, "客户名称已存在");
        }
        customer.setCustomerName(CustomerNameNormalizer.displayName(customer.getCustomerName()));
        customer.setNameNorm(nameNorm);
        customer.setDupExempt(nameChanged ? 0 : (existing.getDupExempt() == null ? 0 : existing.getDupExempt()));
    }

    private void freezeUpdateFields(BizCustomer existing, BizCustomer customer, boolean invoiceDateManuallyChanged) {
        // 【A1 2026-10-20】延期/到期三字段冻结为服务端派生, 普通编辑"不可写也不可静默清零":
        // ① approved_extend_days 一律以库内值覆盖请求值——表单不传(null)时公式按 0 重算会把
        //   已批延期从 expireDate 静默清零(最常触发), 显式传值则可任意拉长保护期;
        // ② expireDate/adjustDate 先剥掉请求体携带值, 再统一由 calculateExpireDates 公式产生;
        //   无基准日(极端 legacy 行)时保持 null, updateById 只写非 null 字段即保留库内值,
        //   请求体任何情况下都不可直写到期日。
        // 合法写入点(不受本冻结影响): 审批通过累加(ApprovalService)、捞取回私海清零(SeaService)、
        // 开票基准前移清零(advanceLastInvoiceDate)、人工维护新基准清零(customer:invoice-date:edit, 2026-11-02)。
        // Integer.valueOf 防三元 int/Integer 混合拆箱 NPE(既有用例 existing.approvedExtendDays=null 常态)
        customer.setApprovedExtendDays(invoiceDateManuallyChanged ? Integer.valueOf(0) : existing.getApprovedExtendDays());
        customer.setExpireDate(null);
        customer.setAdjustDate(null);
        // 【2026-11-02 裁决B】捞取日期=审批域派生字段(仅 applyClaimReset 写入), 通用编辑不得直改
        customer.setClaimDate(existing.getClaimDate());
        calculateExpireDates(customer);
    }

    /** 请求字段是否显式变更(null 视为未指定, 不判定为变更) */
    private boolean fieldChanged(Object existing, Object requested) {
        return requested != null && !requested.equals(existing);
    }

    /**
     * @return true=本次请求人工改了最近开票日(调用方据此清零已批延期——新基准 360 天已覆盖旧延期,
     *         叠加会双重延长; 到期日统一由 update 末尾的 calculateExpireDates 重算, 此处不重复算)
     */
    private boolean protectCustomerDateFields(BizCustomer existing, BizCustomer requested) {
        // 【裁决 2026-11-02 用户名单】最近开票日人工维护: 持 customer:invoice-date:edit(行政/总监/admin
        // 绑定, 持码角色=行政/总监/admin, 名单以权限中心绑定为准)可直改——覆盖 2026-09-24"纯推导任何角色不可直改"裁决。
        // 人工设定=新基准语义(与开票前移同款); 自动推导"只前进"与其共存(更晚开票照常前移, 作废回算跟随剩余真实票)。
        boolean invoiceDateManuallyChanged = fieldChanged(existing.getLastInvoiceDate(), requested.getLastInvoiceDate());
        if (invoiceDateManuallyChanged) {
            FieldPermissionService.requireInvoiceDate();
        }
        if (!invoiceDateManuallyChanged) {
            requested.setLastInvoiceDate(existing.getLastInvoiceDate());
        }
        // 注册日仍为受控可改字段: customer:date:edit 权限(总监0/行政05/admin 绑定)
        if (!FieldPermissionService.canEditRegisterDate()) {
            if (fieldChanged(existing.getRegisterDate(), requested.getRegisterDate())) {
                FieldPermissionService.requireRegisterDate();
            }
            requested.setRegisterDate(existing.getRegisterDate());
        }
        return invoiceDateManuallyChanged;
    }

    /**
     * 【A5 2026-10-20】客户列表删除入口补事务: 删除内核是"软删客户→删分享→清 pending 提醒→
     * 删除快照"一组关联写, 原实现无事务, 任一步失败留下"客户已删、分享/提醒还在"的半删状态
     * (每语句自动提交); 公海入口 deleteFromSeaPool 已有 @Transactional, 两入口口径必须一致。
     * 自调用核查: 全仓仅 CustomerController 经代理调用本方法(grep 无 CustomerService 内部
     * this.delete 调用), 注解经代理生效; 内核 deleteCustomerRecord 是私有方法, 事务边界
     * 落在两个公共入口(delete/deleteFromSeaPool)上, 二者均已 @Transactional。
     */
    @Transactional
    public void delete(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) return;
        // 【2026-08-10 权限体系重构】删除校验统一走 canDelete(池级PERM_DELETE / 非池按"删除范围"):
        // 取代旧的 checkOwnerOrAdmin(仅负责人/admin)——否则删除范围配置不生效
        if (!customerAccessService.canDelete(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能删除自己范围内的客户");
        }
        deleteCustomerRecord(id, existing);
    }

    /**
     * 【审查 biz-3 2026-10-18】公海池删除的客户侧统一入口。
     * 调用方(SeaService.deletePool)已完成池级 PERM_DELETE 与池行结束/锁；这里仍复跑
     * "无活跃池行 + 票款/在途审批保护 + 分享/提醒清理 + 删除快照"完整流程，
     * 与客户列表删除共用 {@link #deleteCustomerRecord}，避免公海入口绕过快照留痕。
     */
    @Transactional
    public void deleteFromSeaPool(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) return; // 已被并发删除: 幂等
        deleteCustomerRecord(id, existing);
    }

    /** 客户删除统一内核（调用方已完成权限/入口校验）: 池行守卫→业务保护→软删→分享/提醒→快照 */
    private void deleteCustomerRecord(Long id, BizCustomer existing) {
        // 【审计 data-2 修复 2026-10-18】客户列表删除必须尊重公海归属:存在未捞取池行
        // (available/pending_approval)时拒绝,引导到公海管理删除(按池级 sea:pool:delete 授权)。
        // 不在此静默删除池行——那会绕过池级权限,且让审批中的捞取单指向已删客户。
        // 【审查 biz-3 2026-10-18】公海入口调用前已结束池行，因此同一守卫仍成立且不用放开。
        Long activeSeaPoolRows = seaPoolMapper.selectCount(
                new LambdaQueryWrapper<BizSeaPool>()
                        .eq(BizSeaPool::getCustomerId, id)
                        .in(BizSeaPool::getStatus, "available", "pending_approval"));
        if (activeSeaPoolRows != null && activeSeaPoolRows > 0) {
            throw new BusinessException("该客户仍在公海池中（待捞取或审批中），请先在公海管理删除");
        }
        // 【议题五.3 裁决 2026-09-19】有业务数据的客户不允许删,转停用:
        assertDeletable(id);
        customerMapper.deleteById(id);
        if (recycleBinService != null) {
            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("id", id);
            snap.put("customerName", existing.getCustomerName());
            snap.put("customerCode", existing.getCustomerCode());
            snap.put("ownerId", existing.getOwnerId());
            snap.put("deptId", existing.getDeptId());
            snap.put("officeId", existing.getOfficeId());
            snap.put("status", existing.getStatus());
            recycleBinService.record(LIT_CUSTOMER, id, snap, "用户删除", "user");
        }
        removeAllShares(id);
        // 【体检 P1 2026-10-12】INV-05 全量口径: 删除是终态, 该客户全部类型的 pending 提醒
        // (expiring/invoice_*)一并清理, 防死行重新积压(摸底 59 条的主供给源)
        cleanPendingReminders(id);
        // 【裁决 2026-09-24】客户删除留痕(三态不变量: 名下/公海/已删除, 删除是终态必须可追溯):
        // 完整快照落 sys_log, 管理员经 系统管理-系统日志 查看; 行本身 @TableLogic 逻辑删保留
        logRecordDeletion("客户", "删除客户", LIT_CUSTOMER, id,
                "客户名=" + existing.getCustomerName() + ", 编码=" + existing.getCustomerCode()
                        + ", 负责人ID=" + existing.getOwnerId() + ", status=" + existing.getStatus()
                        + "/" + existing.getProtectionStatus() + ", deptId=" + existing.getDeptId());
    }

    /**
     * 【B4 2026-09-22 抽取复用】客户删除的业务保护统一入口: 有效开票/回款/在途审批任一存在即拒删。
     * 原本只服务于 delete(); 公海 deletePool 曾绕过此保护直接删客户(GPT 报告 BUS-P1-009),
     * 现由 SeaService 复用, 保证"同一删除动作同一规则"。
     */
    public void assertDeletable(Long id) {
        // 有效开票/回款/在途审批任一存在 → 拒绝(删前展示关联影响的最低实现)
        // 【PR-18 口径】此处计数刻意含已作废(voided)记录:作废仅取消金额效力,台账记录按
        // "保留原值、原因及操作者"留存,仍属客户的历史业务痕迹,不因作废而放开物理删除。
        // 【P0-1 2026-11-12 外部审计 CRITICAL 修复】补入现行开票/回款主载体 biz_customer_stage_amount
        // (2026-10-10 财务载体迁移后逐笔录入走阶段金额, legacy invoice/payment 对新建客户恒空——
        // 原判定使"有票款拒删"(INV-02)对现行链路失效; 对照组 RecycleService:569 早已计入, 两口收口一致)
        long stageCount = stageAmountMapper.selectCount(
                new LambdaQueryWrapper<BizCustomerStageAmount>()
                        .eq(BizCustomerStageAmount::getCustomerId, id));
        long invoiceCount = invoiceMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizInvoice>()
                        .eq(com.aigicrm.module.customer.entity.BizInvoice::getCustomerId, id));
        long paymentCount = paymentMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizPayment>()
                        .eq(com.aigicrm.module.customer.entity.BizPayment::getCustomerId, id));
        if (invoiceCount > 0 || paymentCount > 0 || stageCount > 0) {
            // 【文案修复 2026-10-09 用户反馈】原指引"请使用'停用'"是断头路——系统客户域没有
            // 叫"停用"的功能,实际停止跟进的入口是"归档"(议题五.4:默认隐藏+不参与统计提醒,可恢复)
            throw new BusinessException(String.format(
                    "该客户有 %d 笔开票、%d 笔回款、%d 条阶段金额记录,不允许删除;如需停止跟进请使用\"归档\"",
                    invoiceCount, paymentCount, stageCount));
        }
        // 【议题五.3 裁决 2026-09-20 补齐】在途审批(reviewing/pending)任一存在同样拒删——
        // 审批通过后的动作(延期/转移/捞取)作用于已删客户会产生悬挂回写
        long activeApprovals = approvalMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.approval.entity.BizApproval>()
                        .eq(com.aigicrm.module.approval.entity.BizApproval::getCustomerId, id)
                        .in(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing", LIT_PENDING));
        if (activeApprovals > 0) {
            throw new BusinessException(String.format(
                    "该客户有 %d 条在途审批,不允许删除;请先完成或驳回审批", activeApprovals));
        }
    }

    /**
     * 【体检 P1 2026-10-12】在途审批守卫(释放口): 与 assertDeletable 同判据——
     * 释放进公海后, 在途的延期/转移审批若再通过会把客户拉回私海/盲改属主(双归属),
     * 须先处理审批再释放。到期自动入海(CustomerExpireTask)不经此口, 由执行侧守卫兜底。
     */
    public void assertNoActiveApprovals(Long id) {
        long activeApprovals = approvalMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.approval.entity.BizApproval>()
                        .eq(com.aigicrm.module.approval.entity.BizApproval::getCustomerId, id)
                        .in(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing", LIT_PENDING));
        if (activeApprovals > 0) {
            throw new BusinessException(String.format(
                    "该客户有 %d 条在途审批,不允许释放进公海;请先完成或驳回审批", activeApprovals));
        }
    }

    /** 【议题五.4 裁决】归档:默认隐藏,可筛"含归档";归档客户不参与统计与到期提醒 */
    public void archive(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) throw new BusinessException(LIT_X);
        if (!customerAccessService.canEdit(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能归档自己范围内的客户");
        }
        existing.setStatus(LIT_ARCHIVED);
        customerMapper.updateById(existing);
        // 【体检 P1 2026-10-12】归档客户不参与提醒(议题五.4 裁决口径), 且 INV-05 全量口径:
        // 全部类型 pending 一并清(死 pending 判据同 V2026.09.23.8); 分享行保留备 restore,
        // 恢复后按各自规则重新生成, 不回填
        cleanPendingReminders(id);
    }

    /** 【议题五.4】恢复(从归档恢复正常) */
    public void restore(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) throw new BusinessException(LIT_X);
        if (!LIT_ARCHIVED.equals(existing.getStatus())) {
            throw new BusinessException("该客户不在归档状态");
        }
        if (!customerAccessService.canEdit(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能恢复自己范围内的客户");
        }
        existing.setStatus(LIT_NORMAL);
        customerMapper.updateById(existing);
    }

    // ====== 自动共享规则（S5.3 语义已移到 CustomerScopeService） ======

    private void applyDataScopeAndAutoShareFor(LambdaQueryWrapper<BizCustomer> wrapper,
                                               com.aigicrm.security.SecurityUser scopeUser) {
        customerScopeService.applyDataScopeAndAutoShareFor(wrapper, scopeUser);
    }

    public List<BizCustomerLog> getLogs(Long customerId) {
        LambdaQueryWrapper<BizCustomerLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomerLog::getCustomerId, customerId)
                .orderByDesc(BizCustomerLog::getCreatedAt);
        List<BizCustomerLog> logs = customerLogMapper.selectList(wrapper);
        for (BizCustomerLog log : logs) {
            SysUser user = userMapper.selectById(log.getUserId());
            if (user != null) log.setUserName(user.getRealName());
        }
        return logs;
    }

    public void addLog(BizCustomerLog log) {
        log.setUserId(SecurityUtils.getCurrentUserId());
        customerLogMapper.insert(log);
    }

    // 统计数据 for 工作台（受数据范围控制）
    public long countByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /**
     * 【2026-11-03 权限中心·生效预览】按任意目标用户的 customer×view 范围数客户(排 sea/archived, 与 countByScope 同口径)。
     * 与 applyDataScope 的差异: ①目标用户作参数(不绑当前登录人) ②不含运行时规则引擎追加条件
     * (预览只表达范围层, 规则条件属另一维度, 不在此复刻)——预览数字为范围口径的如实呈现。
     */
    public long countByScopeFor(com.aigicrm.security.SecurityUser target) {
        if (target == null) return 0;
        // 【D2 修复 2026-11-16】旧实现自己拼 ScopeRequest，与列表路径(applyDataScope+autoShare+规则引擎)
        // 是两套口径：TY005(data_scope_override=office) 预览 121 vs 列表 17。现改为复用执行侧同一方法，
        // 预览数字=列表真实可见数（执行侧为准）。
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        applyDataScopeAndAutoShareFor(w, target);
        return customerMapper.selectCount(w);
    }

    public long countInProtectionByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        // 【2026-11-01 审计修复】排公海/归档——归档只改 status 不重置 protectionStatus, 不排则
        // 工作台"在保护期/即将到期"与统计页同指标(已排)数字错位; 口径与同文件已超期统计对齐
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, LIT_ACTIVE)
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countExpiringByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "expiring")
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 当日到期客户数（受数据范围控制） */
    public long countExpiringTodayByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0)
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .eq(BizCustomer::getExpireDate, java.time.LocalDate.now());
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countExpiredByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        // 【口径统一 2026-10-09 用户裁决】工作台「已超期」= expireDate<今日 且排除公海/归档——
        // 与统计页 StatisticsQueryService 同判据(此前 protection_status='expired' 不排公海,
        // 工作台29 vs 统计页2 两页数字不一致; 且到期客户实际已被凌晨任务转 sea+expired,
        // 排除公海后 protection_status 等值与日期判据唯一差异是"任务跑过没有", 日期版确定性更高)
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED)
                .lt(BizCustomer::getExpireDate, java.time.LocalDate.now());
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 本月新增客户数（受数据范围控制） */
    public long countNewThisMonth() {
        java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .ge(BizCustomer::getCreatedAt, monthStart);
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 按周统计本月新增客户（受数据范围控制） */
    public java.util.List<java.util.Map<String, Object>> weeklyNewCustomers() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate start = today.withDayOfMonth(1);
        java.util.List<java.util.Map<String, Object>> weeks = new java.util.ArrayList<>();
        int wn = 1;
        while (!start.isAfter(today)) {
            java.time.LocalDate end = start.plusDays(6);
            boolean isCurrent = !end.isBefore(today);
            if (end.isAfter(today)) end = today;
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                    .ge(BizCustomer::getCreatedAt, start.atStartOfDay())
                    .lt(BizCustomer::getCreatedAt, end.plusDays(1).atStartOfDay());
            applyDataScope(w);
            long count = customerMapper.selectCount(w);
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("label", isCurrent ? "本周" : "W" + wn);
            item.put("value", count);
            weeks.add(item);
            start = start.plusDays(7);
            wn++;
        }
        return weeks;
    }

    /** 按天统计本月新增客户（受数据范围控制） */
    public java.util.List<java.util.Map<String, Object>> dailyNewCustomers() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate start = today.withDayOfMonth(1);
        java.util.List<java.util.Map<String, Object>> days = new java.util.ArrayList<>();
        java.time.LocalDate cursor = start;
        while (!cursor.isAfter(today)) {
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                    .ge(BizCustomer::getCreatedAt, cursor.atStartOfDay())
                    .lt(BizCustomer::getCreatedAt, cursor.plusDays(1).atStartOfDay());
            applyDataScope(w);
            long count = customerMapper.selectCount(w);
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("day", cursor.getDayOfMonth());
            item.put("date", cursor.toString());
            item.put("value", count);
            days.add(item);
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    /**
     * 获取范围内即将到期的客户（前5）
     */
    public List<BizCustomer> getExpiringByScope(int limit) {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "expiring")
                .orderByAsc(BizCustomer::getExpireDate).last("LIMIT " + limit);
        applyDataScope(w);
        List<BizCustomer> list = customerMapper.selectList(w);
        customerQueryService.fillCustomerNames(list);
        return list;
    }

    /**
     * 获取当前用户数据范围内的有效客户（未删除、非公海）
     * 【修复 FEAT-20260804-INDICATOR-SCOPE】供 StatisticsQueryService 等跨模块聚合报表复用，
     * 避免各处各自直接 selectList(null) 绕过数据范围导致全公司数据越权可见
     */
    public List<BizCustomer> getScopedActiveCustomers() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        applyDataScope(wrapper);
        return customerMapper.selectList(wrapper);
    }

    /** 【2026-08-08 全员可见】全部启用客户（核心客户六项指标等公司级展示用，不做数据范围过滤） */
    public List<BizCustomer> getAllActiveCustomers() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        return customerMapper.selectList(wrapper);
    }

    public Map<String, Object> getStatistics() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        // 【2026-11-21 B3-PERF-001】只取聚合所需两列，避免 5 万客户整行/大字段加载；
        // 部门名称改为一次 selectBatchIds，消除按 office 的 N+1 查询。
        wrapper.select(BizCustomer::getIndustry, BizCustomer::getOfficeId)
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived

        // 数据范围过滤（统计也受范围控制）
        applyDataScope(wrapper);

        List<BizCustomer> customers = customerMapper.selectList(wrapper);

        // 空白行业归类为"未填写",避免统计出现无前缀空键(与 StatisticsQueryService 口径一致)
        Map<String, Long> industryStats = customers.stream()
                .map(c -> (c.getIndustry() == null || c.getIndustry().isBlank()) ? "未填写" : c.getIndustry().trim())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        Map<Long, Long> officeStats = customers.stream()
                .filter(c -> c.getOfficeId() != null)
                .collect(Collectors.groupingBy(BizCustomer::getOfficeId, Collectors.counting()));

        List<Map<String, Object>> officeList = new ArrayList<>();
        Map<Long, String> officeNames = new HashMap<>();
        if (!officeStats.isEmpty()) {
            for (var dept : deptMapper.selectBatchIds(officeStats.keySet())) {
                officeNames.put(dept.getId(), dept.getDeptName());
            }
        }
        officeStats.forEach((oid, count) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("officeId", oid);
            item.put("officeName", officeNames.getOrDefault(oid, "未知"));
            item.put("count", count);
            officeList.add(item);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("total", customers.size());
        result.put("industryStats", industryStats);
        result.put("officeStats", officeList);
        return result;
    }

    // 【P0-2 2026-11-12 外部审计 HIGH 退役】customerRankings 及三段排行原生 SQL(激励/客情/核心指标)
    // 已随休眠端点 GET /api/customer/rankings 整体删除——仅集团过滤无数据范围/分享制收口(越权面);
    // 现行排行=StatisticsQueryService.customerRankings(applyDataScope 收口)。
    // groupCondition 集团条件片段随之失去唯一调用方, 一并退役(防死代码)。

    // ====== 数据范围（S5.3 语义已移到 CustomerScopeService） ======

    private void applyDataScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        customerScopeService.applyDataScope(wrapper);
    }

    public List<Long> getCurrentScopeUserIds() {
        return customerScopeService.getCurrentScopeUserIds();
    }

    /**
     * 【2026-11-02 口径批·用户裁决 A/B】三线取最晚公式:
     * expireDate = MAX(建档线, 开票线, 捞取线) + approved_extend_days
     * <ul>
     *   <li>建档线 = 注册日 + customer.protection.days(365) —— 有捞取线时不参与:
     *       存量捞取户的注册日已被旧捞取逻辑覆写为捞取日(不可信); 新流程下捞取线(claim+180)
     *       数学上恒压建档线(捞取必然发生在建档 365 天保护期耗尽之后), 忽略无损;</li>
     *   <li>开票线 = 最近开票日 + protection.days(365) —— 开票不断续命; 捞取时最近开票日置空
     *       (老票基准不延续新周期), advanceLastInvoiceDate 守卫防老票经删票回算回填;</li>
     *   <li>捞取线 = 捞取日 + customer.adjust.days(180) —— 公海捞取后的短保护期(裁决B);</li>
     *   <li>adjustDate 语义改造(裁决C): 捞取到期日 = 捞取日 + adjust.days, 仅捞取客户有值
     *       (原"调整日"=基准+180 纯展示字段, 全系统无业务消费, 生产列表列头同步改名);</li>
     *   <li>三线全空(极端 legacy 行)时保持库内值不动(updateById 只写非 null 字段);</li>
     *   <li>【延期规则收敛 2026-10-18】仅当新开票/阶段总额把最近开票日真正前移(新值晚于旧值)时
     *       清零 approved_extend_days; 补录旧票不清零。删票/作废回算不重置;</li>
     *   <li>天数走 sys_config: customer.protection.days(365)/customer.adjust.days(180),
     *       管理员可在系统设置修改——延期天数自 2026-10-17 延期B 起进审批单 extraData.extendDays
     *       (提交快照+审批人可调),存量单回退 customer.extend.days(ApprovalService 消费)。</li>
     *   <li>【回归免疫 2026-10-09 公海死循环】三线 MAX 语义天然覆盖旧 max(开票日,注册日) 口径:
     *       legacy 老票(2015/2024 年)的开票线永远早于捞取线/建档线, 不会把基准拖回过去。</li>
     * </ul>
     */
    private void calculateExpireDates(BizCustomer customer) {
        LocalDate invoice = customer.getLastInvoiceDate();
        LocalDate register = customer.getRegisterDate();
        LocalDate claim = customer.getClaimDate();
        int protectionDays = getConfigInt("customer.protection.days", 365);
        int adjustDays = getConfigInt("customer.adjust.days", 180);
        int extendDays = customer.getApprovedExtendDays() != null ? customer.getApprovedExtendDays() : 0;

        LocalDate expireBase = null;
        // 开票线
        if (invoice != null) {
            expireBase = invoice.plusDays(protectionDays);
        }
        // 建档线(仅有捞取线时不参与, 见方法注释)
        if (claim == null && register != null) {
            LocalDate registerLine = register.plusDays(protectionDays);
            expireBase = expireBase == null || registerLine.isAfter(expireBase) ? registerLine : expireBase;
        }
        // 捞取线(裁决B: 捞取后 180 天短保护)
        if (claim != null) {
            LocalDate claimLine = claim.plusDays(adjustDays);
            expireBase = expireBase == null || claimLine.isAfter(expireBase) ? claimLine : expireBase;
        }
        if (expireBase != null) {
            customer.setExpireDate(expireBase.plusDays(extendDays));
        }
        // 捞取到期日(裁决C): 仅捞取客户有值; 非捞取客户置 null(存量已由 V2026.11.02.4 清理)
        customer.setAdjustDate(claim != null ? claim.plusDays(adjustDays) : null);
    }

    /** 【2026-11-02 裁决B】捞取成功后的日期重置(SeaService.completeClaim 唯一调用, 单一公式源):
     *  写捞取日期、清零已批延期(原负责人任内审批不跨周期残留)、按三线公式算到期/捞取到期日;
     *  并把最近开票日显式置空(老票基准不延续新周期, updateById 的 NOT_NULL 策略不写 null 必须显式 set)。
     *  调用方需先 customer.setLastInvoiceDate(null) 保证公式按新周期计算。 */
    @Transactional
    public void applyClaimReset(BizCustomer customer, LocalDate claimDate) {
        customer.setClaimDate(claimDate);
        customer.setApprovedExtendDays(0);
        calculateExpireDates(customer);
        customerMapper.updateById(customer);
        customerMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizCustomer>()
                .eq(BizCustomer::getId, customer.getId())
                .set(BizCustomer::getLastInvoiceDate, null));
    }

    /** 【2026-11-02 副本收敛】延期审批执行(ApprovalService.extend case 调用, 单一公式源):
     *  累加已批延期天数并按三线公式重算; 三线全空时以当天起算(业务口径: 从未设置保护期=即日起延期)。 */
    @Transactional
    public void applyExtendApproval(BizCustomer customer, int extendDays) {
        int accumulated = customer.getApprovedExtendDays() != null ? customer.getApprovedExtendDays() : 0;
        customer.setApprovedExtendDays(accumulated + extendDays);
        calculateExpireDates(customer);
        if (customer.getExpireDate() == null) {
            customer.setExpireDate(LocalDate.now().plusDays(customer.getApprovedExtendDays()));
        }
        customer.setProtectionStatus(LIT_ACTIVE);
        customer.setStatus(LIT_NORMAL);
        customerMapper.updateById(customer);
    }

    private int getConfigInt(String key, int defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }

    /**
     * 批量填充客户关联名称
     *
     * 【需求】列表页展示负责人/上级/办事处/大区名称；原逐条查询导致 N+1（15 条=60 次查询）
     * 【方法】收集页面全部 userId/deptId，IN 批量查询两次，内存映射填充
     * 【业务规则】null id 跳过；查不到的名称保持 null（前端兜底显示空）
     */
    // ====== 开票记录 ======

    public List<BizInvoice> getInvoices(Long customerId) {
        return invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .orderByDesc(BizInvoice::getInvoiceDate));
    }

    public Map<String, Object> getInvoiceSummary(Long customerId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", invoiceMapper.countByCustomerId(customerId));
        summary.put("totalAmount", invoiceMapper.sumAmountByCustomerId(customerId));
        // 【PR-18 统计口径】最近开票日排除已作废发票(金额/笔数已由 Mapper 排除)
        List<BizInvoice> latest = invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .ne(BizInvoice::getStatus, LIT_VOIDED)
                        .orderByDesc(BizInvoice::getInvoiceDate)
                        .last(LIT_LIMIT_1));
        summary.put("latestDate", latest.isEmpty() ? null : latest.get(0).getInvoiceDate());
        return summary;
    }

    @Transactional
    public void addInvoice(Long customerId, BizInvoice invoice) {
        // 【审查 backend-4 2026-10-18】财务写入口自守: 客户必须存在/未删/同集团,
        // 不依赖控制器层 finance:manage 分支的放行(代录也不能对已删/跨集团客户落台账)
        customerAccessService.assertFinanceCustomerWritable(customerId);
        invoice.setCustomerId(customerId);
        invoice.setCreatedBy(SecurityUtils.getCurrentUserId());
        invoiceMapper.insert(invoice);
        // 【延期规则收敛 2026-10-18】新开票是否重置已批准延期,与阶段总额共用同一判定:
        // 仅当最近开票日真正前移(新值晚于旧值)才清零;补录早于当前基准的旧票不清零。
        // 旧实现无条件清零,补录历史票也会误伤已批延期。判定/清零/重算集中在
        // advanceLastInvoiceDate,本方法只负责刷新最近开票日并触发一次到期日重算。
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            advanceLastInvoiceDate(customer, latestActiveInvoiceDate(customerId));
        }
        recalculateCoreStatus(customerId);
    }

    /** 【PR-18 2026-09-19】台账确认:pending → confirmed(锁定,不可直接删) */
    @Transactional
    public void confirmInvoice(Long customerId, Long invoiceId) {
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException(LIT_X_2);
        }
        if (!LIT_PENDING.equals(invoice.getStatus())) {
            throw new BusinessException("仅待核对状态可确认");
        }
        invoice.setStatus("confirmed");
        invoice.setConfirmedBy(SecurityUtils.getCurrentUserId());
        invoice.setConfirmedAt(java.time.LocalDateTime.now());
        invoiceMapper.updateById(invoice);
        recalculateCoreStatus(customerId);
    }

    /** 【PR-18】台账作废:confirmed/pending → voided(保留记录,不物理删) */
    @Transactional
    public void voidInvoice(Long customerId, Long invoiceId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("作废必须填写原因");
        }
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException(LIT_X_2);
        }
        if (LIT_VOIDED.equals(invoice.getStatus())) {
            throw new BusinessException("该发票已作废");
        }
        invoice.setStatus(LIT_VOIDED);
        invoice.setVoidedReason(reason.trim());
        invoiceMapper.updateById(invoice);
        refreshLastInvoiceDate(customerId);
        recalculateCoreStatus(customerId);
    }

    /** 【PR-18】回款确认 */
    @Transactional
    public void confirmPayment(Long customerId, Long paymentId) {
        BizPayment payment = paymentMapper.selectById(paymentId);
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException(LIT_X_3);
        }
        if (!LIT_PENDING.equals(payment.getStatus())) {
            throw new BusinessException("仅待核对状态可确认");
        }
        payment.setStatus("confirmed");
        payment.setConfirmedBy(SecurityUtils.getCurrentUserId());
        payment.setConfirmedAt(java.time.LocalDateTime.now());
        paymentMapper.updateById(payment);
        recalculateCoreStatus(customerId);
    }

    /** 【PR-18】回款作废 */
    @Transactional
    public void voidPayment(Long customerId, Long paymentId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("作废必须填写原因");
        }
        BizPayment payment = paymentMapper.selectById(paymentId);
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException(LIT_X_3);
        }
        if (LIT_VOIDED.equals(payment.getStatus())) {
            throw new BusinessException("该回款已作废");
        }
        payment.setStatus(LIT_VOIDED);
        payment.setVoidedReason(reason.trim());
        paymentMapper.updateById(payment);
        recalculateCoreStatus(customerId);
    }

    @Transactional
    public void deleteInvoice(Long customerId, Long invoiceId, String reason) {
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        // 归属校验：记录必须属于请求路径中的客户，防止跨客户删除（IDOR）
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException(LIT_X_2);
        }
        // 【裁决 2026-09-24 更新】"发票可以删除,但要有删除记录"——任何状态可删(替换 PR-18 仅待核对可删):
        // 必填删除原因 + sys_log 审计快照(票号/日期/金额/状态/原因/操作人) + @TableLogic 逻辑删(行保留可回查);
        // 保护期由 refreshLastInvoiceDate 自动回算。
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请填写删除原因（财务记录删除必须留痕）");
        }
        logFinanceDeletion("发票", customerId, invoiceId, reason.trim(),
                "票号=" + invoice.getInvoiceNumber() + ", 日期=" + invoice.getInvoiceDate()
                        + ", 金额=" + invoice.getAmount() + ", 状态=" + invoice.getStatus());
        invoiceMapper.deleteById(invoiceId);
        refreshLastInvoiceDate(invoice.getCustomerId());
        recalculateCoreStatus(invoice.getCustomerId());
    }

    /** 记录删除审计:完整快照落 sys_log(管理员经系统日志页可查);尽力而为不阻断主流程(LogAspect 层另有 URI 级记录带 IP 兜底) */
    private void logRecordDeletion(String module, String action, String targetType, Long recordId, String detail) {
        try {
            com.aigicrm.module.system.entity.SysLog audit = new com.aigicrm.module.system.entity.SysLog();
            com.aigicrm.security.SecurityUser current = SecurityUtils.getCurrentUser();
            audit.setUserId(current != null ? current.getUserId() : null);
            audit.setUsername(current != null ? current.getUsername() : null);
            audit.setModule(module);
            audit.setAction(action);
            audit.setTargetType(targetType);
            audit.setTargetId(recordId);
            audit.setDetail(detail);
            financeAuditLogMapper.insert(audit);
        } catch (Exception e) {
            // 【P2-3 2026-10-18】尽力而为语义保持(不阻断删除主流程), 但失败必须可见——
            // 空吞=财务删除留痕静默丢失且不可察觉(LogAspect 的 URI 级兜底不含票号/原因快照)
            org.slf4j.LoggerFactory.getLogger(CustomerService.class)
                    .warn("财务删除审计落库失败 module={} action={} recordId={}", module, action, recordId, e);
        }
    }

    private void logFinanceDeletion(String kind, Long customerId, Long recordId, String reason, String snapshot) {
        logRecordDeletion("财务", "删除" + kind, "finance_record", recordId,
                snapshot + "; 原因: " + reason + "; customerId=" + customerId);
    }

    private void refreshLastInvoiceDate(Long customerId) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            customer.setLastInvoiceDate(latestActiveInvoiceDate(customerId));
            calculateExpireDates(customer);
            customerMapper.updateById(customer);
        }
    }

    /** 【延期规则收敛 2026-10-18】最近开票日推导查询(排除已作废): addInvoice 与 refresh 共用同一口径 */
    private LocalDate latestActiveInvoiceDate(Long customerId) {
        // 【PR-18 统计口径】最近开票日排除已作废发票——作废发票不得驱动保护期/到期日计算
        List<BizInvoice> latest = invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .ne(BizInvoice::getStatus, LIT_VOIDED)
                        .orderByDesc(BizInvoice::getInvoiceDate)
                        .last(LIT_LIMIT_1));
        return latest.isEmpty() ? null : latest.get(0).getInvoiceDate();
    }

    /**
     * 【延期规则收敛 2026-10-18 · 审计 biz-0】开票驱动保护期基准前移的唯一入口:
     * addInvoice(逐笔补录)与 saveStageAmount(阶段总额)的前移路径都只经本方法写 lastInvoiceDate。
     * <ul>
     *   <li>仅当候选日期晚于当前最近开票日(含当前为空的首个基准)才算"前移":清零
     *       approved_extend_days 并重算到期日一次——新基准上的保护期已覆盖旧延期,
     *       继续叠加会造成双重延长(新基准 360 天 + 旧基准批出的延期);</li>
     *   <li>补录早于/等于当前基准的旧票不算前移:保持基准与已批延期,不清零、不重算
     *       (旧实现 addInvoice 无条件清零,补录历史票会误伤延期);</li>
     *   <li>删票/作废仍走 refreshLastInvoiceDate 回算(可能后退),不收回已批延期——
     *       与 2026-09-19 议题一裁决一致。</li>
     * </ul>
     *
     * @return true=发生前移并已落库(调用方据此决定是否清 invoice 类 pending 提醒)
     */
    private boolean advanceLastInvoiceDate(BizCustomer customer, LocalDate candidateDate) {
        if (customer == null || candidateDate == null) return false;
        // 【2026-11-02 裁决B 守卫】捞取后新周期: 早于捞取日的老票日期不得经删票/作废回算回填
        // 最近开票日(捞取置空后 current=null, 无此守卫会把上一周期的老票日期写回, 开票线复活
        // 拉长保护期, 架空"捞取重新起算")。
        if (customer.getClaimDate() != null && candidateDate.isBefore(customer.getClaimDate())) return false;
        LocalDate current = customer.getLastInvoiceDate();
        if (current != null && !candidateDate.isAfter(current)) return false;
        customer.setLastInvoiceDate(candidateDate);
        customer.setApprovedExtendDays(0);
        calculateExpireDates(customer);
        customerMapper.updateById(customer);
        // 【生命线 2026-11-02 裁决D】开票推进事件(逐笔 addInvoice 与阶段 saveStageAmount 共用本口)
        customerEventService.record(customer.getId(), CustomerEventService.TYPE_INVOICE_ADV,
                candidateDate, "开票推进最近开票日",
                current != null ? current.toString() : null, candidateDate.toString(),
                com.aigicrm.security.SecurityUtils.getCurrentUserId());
        return true;
    }

    // ====== 客户指标 ======

    public BizCustomerIndicator getIndicator(Long customerId) {
        return indicatorMapper.selectOne(
                new LambdaQueryWrapper<BizCustomerIndicator>()
                        .eq(BizCustomerIndicator::getCustomerId, customerId));
    }

    @Transactional
    public void saveIndicator(Long customerId, BizCustomerIndicator data) {
        data.setCustomerId(customerId);
        data.setUpdatedBy(SecurityUtils.getCurrentUserId());
        // 【L2 2026-10-12 测试报告】五项指标分值域校验(0-3, 字典四档): 前端仅给四选项,
        // 直连接口可越界入库, 污染排行/核心判定的求和口径
        validateIndicatorScores(data);
        // 【裁决 2026-10-10】核心三态只能由 recalculateCoreStatus 判定写入;
        // updateById 跳过 null 字段, 显式置空即屏蔽域外输入对 core_type/is_core 的覆盖
        data.setCoreType(null);
        data.setIsCore(null);

        BizCustomerIndicator existing = getIndicator(customerId);
        if (existing != null) {
            data.setId(existing.getId());
            indicatorMapper.updateById(data);
        } else {
            indicatorMapper.insert(data);
        }
        recalculateCoreStatus(customerId);
    }

    /** 【L2】五项指标分值域校验: 每项四档(0-3), null 跳过(updateById 不覆盖 null 字段) */
    private void validateIndicatorScores(BizCustomerIndicator data) {
        if (outOfRange(data.getRelationshipScore()) || outOfRange(data.getIndicator02())
                || outOfRange(data.getIndicator03()) || outOfRange(data.getIndicator04())
                || outOfRange(data.getIndicator05())) {
            throw new BusinessException("五项指标分值须在 0-3 之间(每项四档)");
        }
    }

    private boolean outOfRange(Integer v) {
        return v != null && (v < 0 || v > 3);
    }

    /**
     * 【PR-05 2026-09-19】客户与本次客情指标同事务统一提交(新建)。
     * 原两步保存(建客户→存指标)在第二步失败时产生"部分成功、界面报保存失败"，
     * 用户重试可能重复建客户。组合方法单一事务：任一步失败整体回滚，界面失败即全未保存。
     */
    @Transactional
    public Long createWithIndicator(BizCustomer customer, BizCustomerIndicator indicator) {
        create(customer);
        if (indicator != null) {
            saveIndicator(customer.getId(), indicator);
        }
        return customer.getId();
    }

    /** 【PR-05】同上，编辑态组合提交。 */
    @Transactional
    public void updateWithIndicator(Long id, BizCustomer customer, BizCustomerIndicator indicator) {
        customer.setId(id);
        update(customer);
        if (indicator != null) {
            saveIndicator(id, indicator);
        }
    }

    @Transactional
    public void saveRelationshipScore(Long customerId, int score) {
        // 【L2 2026-10-12 测试报告】与 saveIndicator 同口径的 0-3 域校验
        if (score < 0 || score > 3) {
            throw new BusinessException("客情·商务关系深度分值须在 0-3 之间");
        }
        BizCustomerIndicator existing = getIndicator(customerId);
        if (existing != null) {
            existing.setRelationshipScore(score);
            existing.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.updateById(existing);
        } else {
            BizCustomerIndicator ind = new BizCustomerIndicator();
            ind.setCustomerId(customerId);
            ind.setRelationshipScore(score);
            ind.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.insert(ind);
        }
        // 【PR-17 2026-09-19 议题三裁决"即时计算"】客情分是核心客户五项之一,
        // 只改客情分跨过 12 分门槛时核心标记必须同步刷新——此前 saveIndicator 触发
        // 重算而本方法不触发,是口径双轨(诊断 F13 额外静态缺口,业务会裁决后为必做)。
        recalculateCoreStatus(customerId);
    }

    /** 【2026-11-03 用户令门槛配置化】核心客户判定双门槛 sys_config 键(万元):
     *  minor=争核心门槛(默认 50), major=真核心门槛(默认 100); seed 见迁移 V2026.11.03.1。
     *  键名跨包共用(StatisticsQueryService/SysConfigController), 判定与统计同源。 */
    public static final String CORE_MINOR_WAN_KEY = "customer.core.minor.wan";
    public static final String CORE_MAJOR_WAN_KEY = "customer.core.major.wan";
    private static final BigDecimal CORE_MINOR_WAN_DEFAULT = new BigDecimal("50");
    private static final BigDecimal CORE_MAJOR_WAN_DEFAULT = new BigDecimal("100");

    /** 读双门槛配置; 脏值/非正数回退默认(与 getConfigInt 同款回退模式, 门槛永不因配置脏值失效)。 */
    private BigDecimal[] coreThresholds() {
        return new BigDecimal[]{
                configDecimal(CORE_MINOR_WAN_KEY, CORE_MINOR_WAN_DEFAULT),
                configDecimal(CORE_MAJOR_WAN_KEY, CORE_MAJOR_WAN_DEFAULT)};
    }

    private BigDecimal configDecimal(String key, BigDecimal defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                BigDecimal parsed = new BigDecimal(val.trim());
                if (parsed.signum() > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败/非正数返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }

    /**
     * 【裁决 2026-10-10 纯金额三态 + 2026-11-03 双门槛配置化】核心客户三态判定(五项评分不参与, 保留为客情参考)。
     * 年度口径 = 最近有数据年(全年行有数据时优先取全年行, 否则上半年+下半年合计):
     *   core  = 真核心: 基准年开票、回款均 >= 真核心门槛(customer.core.major.wan, 默认 100 万);
     *   quasi = 争核心: 均达标争核心门槛(customer.core.minor.wan, 默认 50 万), 未双达真核心门槛;
     *   ''    = 非核心: 任一低于争核心门槛(含无任何有数据阶段行; 开票、回款均 <= 0 的行不算数据)。
     * 门槛由管理员在系统配置修改, 保存后由 SysConfigController 触发 recalculateAllCoreStatuses 全量刷新。
     * 与 StatisticsQueryService.coreTypeOf / 迁移 V2026.11.03.1 回填同口径, 改动须三处同步。
     */
    private String computeCoreType(Long customerId) {
        List<BizCustomerStageAmount> rows = stageAmountMapper.selectList(
                new LambdaQueryWrapper<BizCustomerStageAmount>()
                        .eq(BizCustomerStageAmount::getCustomerId, customerId));
        BigDecimal[] thresholds = coreThresholds();
        return computeCoreTypeFromRows(rows, thresholds[0], thresholds[1]);
    }

    /** computeCoreType 的纯函数核(单户与全量刷新共用): rows = 该客户全部阶段行, 门槛为参数不落死值。 */
    static String computeCoreTypeFromRows(List<BizCustomerStageAmount> rows, BigDecimal minorWan, BigDecimal majorWan) {
        int baseYear = baseYearOf(rows);
        if (baseYear == 0) return "";
        BigDecimal[] amounts = annualAmountsOf(rows, baseYear);
        return coreTypeOf(amounts[0], amounts[1], minorWan, majorWan);
    }

    private static int baseYearOf(List<BizCustomerStageAmount> rows) {
        return rows.stream()
                .filter(r -> isPositiveAmount(r.getInvoiceAmount()) || isPositiveAmount(r.getPaymentAmount()))
                .mapToInt(BizCustomerStageAmount::getPeriodYear).max().orElse(0);
    }

    private static BigDecimal[] annualAmountsOf(List<BizCustomerStageAmount> rows, int baseYear) {
        BizCustomerStageAmount fy = rows.stream()
                .filter(r -> r.getPeriodYear() == baseYear && "FY".equals(r.getPeriodType())
                        && (isPositiveAmount(r.getInvoiceAmount()) || isPositiveAmount(r.getPaymentAmount())))
                .findFirst().orElse(null);
        if (fy != null) return new BigDecimal[]{fy.getInvoiceAmount(), fy.getPaymentAmount()};
        return rows.stream()
                .filter(r -> r.getPeriodYear() == baseYear && ("H1".equals(r.getPeriodType()) || "H2".equals(r.getPeriodType())))
                .reduce(new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO},
                        (acc, r) -> {
                            acc[0] = acc[0].add(r.getInvoiceAmount() == null ? BigDecimal.ZERO : r.getInvoiceAmount());
                            acc[1] = acc[1].add(r.getPaymentAmount() == null ? BigDecimal.ZERO : r.getPaymentAmount());
                            return acc;
                        },
                        (a, b) -> a);
    }

    private static String coreTypeOf(BigDecimal invoice, BigDecimal payment, BigDecimal minorWan, BigDecimal majorWan) {
        boolean invoiceMet = invoice != null && invoice.compareTo(majorWan) >= 0;
        boolean paymentMet = payment != null && payment.compareTo(majorWan) >= 0;
        if (invoiceMet && paymentMet) return "core";
        boolean invoiceQuasi = invoice != null && invoice.compareTo(minorWan) >= 0;
        boolean paymentQuasi = payment != null && payment.compareTo(minorWan) >= 0;
        return invoiceQuasi && paymentQuasi ? "quasi" : "";
    }

    private static boolean isPositiveAmount(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /** 核心三态落库(含 is_core 兼容列同步)。判定与五项解耦后, 无指标行的客户也自动建行落标记。 */
    private void recalculateCoreStatus(Long customerId) {
        String coreType = computeCoreType(customerId);
        BizCustomerIndicator indicator = getIndicator(customerId);
        if (indicator == null) {
            indicator = new BizCustomerIndicator();
            indicator.setCustomerId(customerId);
            indicator.setCoreType(coreType);
            indicator.setIsCore("core".equals(coreType) ? 1 : 0);
            indicator.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.insert(indicator);
            return;
        }
        indicator.setCoreType(coreType);
        indicator.setIsCore("core".equals(coreType) ? 1 : 0);
        indicatorMapper.updateById(indicator);
    }

    /**
     * 【2026-11-03 用户令"改门槛后自动刷新"】按当前双门槛配置全量刷新核心三态落库。
     * 触发: SysConfigController 保存双门槛配置变化后调用; 幂等可重放(只写有变化的行)。
     * 全量阶段行一次载入内存分组(数百户规模无需分页); 无指标行的客户自动建行、
     * 已无阶段数据的老行归位非核心(与单客户 recalculateCoreStatus 同语义)。
     * @return 发生变化的客户数
     */
    @Transactional
    public int recalculateAllCoreStatuses() {
        BigDecimal[] thresholds = coreThresholds();
        Map<Long, List<BizCustomerStageAmount>> rowsByCustomer = stageAmountMapper.selectList(null).stream()
                .filter(r -> r.getCustomerId() != null)
                .collect(Collectors.groupingBy(BizCustomerStageAmount::getCustomerId));
        Map<Long, BizCustomerIndicator> indicatorByCustomer = indicatorMapper.selectList(null).stream()
                .filter(i -> i.getCustomerId() != null)
                .collect(Collectors.toMap(BizCustomerIndicator::getCustomerId, i -> i, (a, b) -> a));
        Long userId = SecurityUtils.getCurrentUserId();
        int changed = 0;
        for (var entry : rowsByCustomer.entrySet()) {
            String coreType = computeCoreTypeFromRows(entry.getValue(), thresholds[0], thresholds[1]);
            changed += upsertCoreStatus(indicatorByCustomer.get(entry.getKey()), entry.getKey(), coreType, userId);
        }
        // 阶段数据已被清空的老行(如阶段行全删): 归位非核心, 防旧标签残留
        for (var entry : indicatorByCustomer.entrySet()) {
            if (rowsByCustomer.containsKey(entry.getKey())) continue;
            changed += upsertCoreStatus(entry.getValue(), entry.getKey(), "", userId);
        }
        return changed;
    }

    /** 单户核心三态落库(缺行插入/有变化更新, 返回 1=有变化 0=无变化)。 */
    private int upsertCoreStatus(BizCustomerIndicator indicator, Long customerId, String coreType, Long userId) {
        int isCore = "core".equals(coreType) ? 1 : 0;
        if (indicator == null) {
            indicator = new BizCustomerIndicator();
            indicator.setCustomerId(customerId);
            indicator.setCoreType(coreType);
            indicator.setIsCore(isCore);
            indicator.setUpdatedBy(userId);
            indicatorMapper.insert(indicator);
            return 1;
        }
        if (Objects.equals(indicator.getCoreType(), coreType) && Objects.equals(indicator.getIsCore(), isCore)) return 0;
        indicator.setCoreType(coreType);
        indicator.setIsCore(isCore);
        indicator.setUpdatedBy(userId);
        indicatorMapper.updateById(indicator);
        return 1;
    }

    private int zeroIfNull(Integer val) { return val == null ? 0 : val; }

    // ====== 阶段开票回款(2026-10-10 半年/全年总额录入制) ======

    private static final Set<String> PERIOD_TYPES = Set.of("H1", "H2", "FY");
    private static final int PERIOD_YEAR_MIN = 2000;

    /** 周期展示名: 与用户口径一致——2026上半年 / 2026下半年 / 2026全年 */
    private String periodLabel(int year, String type) {
        String suffix = switch (type) {
            case "H1" -> "上半年";
            case "H2" -> "下半年";
            default -> "全年";
        };
        return year + suffix;
    }

    /**
     * 阶段列表 + 判定摘要(一处算好, PC/MP 两端直接展示, 避免口径两端复刻漂移)。
     * 返回: rows(年倒序, 年内 全年>下半年>上半年), baseYear/annualInvoiceAmount/annualPaymentAmount/coreType,
     * 以及当前双门槛 coreMinorWan/coreMajorWan(万元, 前端门槛文案不落死值)。
     */
    public Map<String, Object> getStageAmountSummary(Long customerId) {
        List<BizCustomerStageAmount> rows = stageAmountMapper.selectList(
                new LambdaQueryWrapper<BizCustomerStageAmount>()
                        .eq(BizCustomerStageAmount::getCustomerId, customerId));
        Map<String, String> nameById = new HashMap<>();
        rows.stream().map(BizCustomerStageAmount::getUpdatedBy).filter(Objects::nonNull).distinct().forEach(uid -> {
            SysUser u = userMapper.selectById(uid);
            if (u != null) nameById.put(String.valueOf(uid), u.getRealName());
        });
        rows.forEach(r -> {
            r.setPeriodLabel(periodLabel(r.getPeriodYear(), r.getPeriodType()));
            r.setUpdatedByName(r.getUpdatedBy() == null ? null : nameById.get(String.valueOf(r.getUpdatedBy())));
        });
        // 年倒序; 年内 全年(FY) > 下半年(H2) > 上半年(H1)
        rows.sort((a, b) -> {
            int byYear = Integer.compare(b.getPeriodYear(), a.getPeriodYear());
            if (byYear != 0) return byYear;
            return Integer.compare(periodRank(b.getPeriodType()), periodRank(a.getPeriodType()));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        // 复用已载入的 rows 判定(免二次查询); 双门槛一并回传, 前端门槛文案不落死值
        BigDecimal[] coreThresholds = coreThresholds();
        result.put("coreType", computeCoreTypeFromRows(rows, coreThresholds[0], coreThresholds[1]));
        result.put("coreMinorWan", coreThresholds[0]);
        result.put("coreMajorWan", coreThresholds[1]);
        result.put("baseYear", 0);
        result.put("annualInvoiceAmount", BigDecimal.ZERO);
        result.put("annualPaymentAmount", BigDecimal.ZERO);
        // 基准年与年度金额摘要(与 computeCoreType 同口径, 展示判定头用)
        int baseYear = rows.stream()
                .filter(r -> isPositiveAmount(r.getInvoiceAmount()) || isPositiveAmount(r.getPaymentAmount()))
                .mapToInt(BizCustomerStageAmount::getPeriodYear).max().orElse(0);
        if (baseYear > 0) {
            result.put("baseYear", baseYear);
            List<BizCustomerStageAmount> yearRows = rows.stream()
                    .filter(r -> r.getPeriodYear() == baseYear).toList();
            boolean fyExists = yearRows.stream().anyMatch(r -> "FY".equals(r.getPeriodType())
                    && (isPositiveAmount(r.getInvoiceAmount()) || isPositiveAmount(r.getPaymentAmount())));
            List<BizCustomerStageAmount> basis = fyExists
                    ? yearRows.stream().filter(r -> "FY".equals(r.getPeriodType())).toList()
                    : yearRows.stream().filter(r -> "H1".equals(r.getPeriodType()) || "H2".equals(r.getPeriodType())).toList();
            result.put("annualInvoiceAmount", basis.stream().map(r -> nvl(r.getInvoiceAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            result.put("annualPaymentAmount", basis.stream().map(r -> nvl(r.getPaymentAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return result;
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private int periodRank(String type) {
        return switch (type == null ? "" : type) { case "H1" -> 1; case "H2" -> 2; default -> 3; };
    }

    /**
     * 保存(录入/修改)一个周期的阶段总额。编辑制 upsert: 唯一键(customer, year, type)+FOR UPDATE 行锁,
     * 并发下读到-写入串行化。金额单位万元, 至少一项 > 0; 未填的一项按 0(即"该周期无回款"也是合法录入)。
     * 审计: 改动快照落 sys_log。保护期接力: 开票额>0 时以阶段末日期(H1→6-30, H2/FY→12-31, 不晚于今天)
     * 前移刷新 lastInvoiceDate(只前进不后退) 并重算到期日——逐笔录入下线后保护期由阶段录入接力。
     */
    @Transactional
    public void saveStageAmount(Long customerId, com.aigicrm.module.customer.dto.StageAmountRequest req) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) throw new BusinessException(LIT_X);
        StageInput input = validateStageInput(req);
        BizCustomerStageAmount existing = stageAmountMapper.selectByPeriodForUpdate(customerId, input.year(), input.type());
        String before = existing == null ? "无(首次录入)"
                : "开票 " + existing.getInvoiceAmount() + " 万 / 回款 " + existing.getPaymentAmount() + " 万";
        Long uid = SecurityUtils.getCurrentUserId();
        upsertStageAmount(customerId, input, existing, uid);
        logRecordDeletion("客户", "保存阶段开票回款", "stage_amount", customerId,
                periodLabel(input.year(), input.type()) + ": " + before + " → 开票 " + input.invoice() + " 万 / 回款 " + input.payment() + " 万; customerId=" + customerId);
        if (input.invoice().signum() > 0) advanceInvoicePeriod(customer, customerId, input.year(), input.type());
        recalculateCoreStatus(customerId);
    }

    private record StageInput(String type, int year, BigDecimal invoice, BigDecimal payment) {}

    private StageInput validateStageInput(com.aigicrm.module.customer.dto.StageAmountRequest req) {
        String type = req.periodType() == null ? "" : req.periodType().trim();
        if (!PERIOD_TYPES.contains(type)) throw new BusinessException("周期类型无效(须为 上半年/下半年/全年)");
        int year = req.periodYear() == null ? 0 : req.periodYear();
        if (year < PERIOD_YEAR_MIN || year > LocalDate.now().getYear() + 1)
            throw new BusinessException("周期年份无效");
        BigDecimal invoice = nvl(req.invoiceAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal payment = nvl(req.paymentAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
        if (invoice.signum() < 0 || payment.signum() < 0) throw new BusinessException("金额不能为负数");
        if (invoice.signum() == 0 && payment.signum() == 0)
            throw new BusinessException("阶段内开票额与阶段性回款额至少填一项且大于 0");
        if (invoice.compareTo(new BigDecimal("999999999999.99")) > 0
                || payment.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new BusinessException("金额超出上限");
        return new StageInput(type, year, invoice, payment);
    }

    private void upsertStageAmount(Long customerId, StageInput input, BizCustomerStageAmount existing, Long uid) {
        if (existing == null) {
            BizCustomerStageAmount row = new BizCustomerStageAmount();
            row.setCustomerId(customerId);
            row.setPeriodYear(input.year());
            row.setPeriodType(input.type());
            row.setInvoiceAmount(input.invoice());
            row.setPaymentAmount(input.payment());
            row.setCreatedBy(uid);
            row.setUpdatedBy(uid);
            stageAmountMapper.insert(row);
            return;
        }
        existing.setInvoiceAmount(input.invoice());
        existing.setPaymentAmount(input.payment());
        existing.setUpdatedBy(uid);
        stageAmountMapper.updateById(existing);
    }

    private void advanceInvoicePeriod(BizCustomer customer, Long customerId, int year, String type) {
        LocalDate periodEnd = "H1".equals(type) ? LocalDate.of(year, 6, 30) : LocalDate.of(year, 12, 31);
        // 提前录入进行中的周期时按今天封顶, 防止把保护期推到未来
        LocalDate effective = periodEnd.isAfter(LocalDate.now()) ? LocalDate.now() : periodEnd;
        // 【延期规则收敛 2026-10-18】与 addInvoice 同口:仅前移才清零已批延期并重算一次;
        // 补录早于当前基准的旧周期不清零、不重算(原实现阶段入口从不清零,与逐笔入口双轨)。
        if (advanceLastInvoiceDate(customer, effective)) {
            // 【体检 P1 2026-10-12】治愈回收: 重新开票(开票日前移)后, pending 的
            // invoice_never/invoice_overdue 提醒即清——凌晨任务按新事实重建
            customerContactShareService.cleanInvoicePendingReminders(customerId);
        }
    }

    // ====== 客户分享/联系人（S5.3 语义已移到 CustomerContactShareService） ======

    public List<BizCustomerShare> getShares(Long customerId) {
        return customerContactShareService.getShares(customerId);
    }

    public void addShares(Long customerId, List<Long> userIds) {
        customerContactShareService.addShares(customerId, userIds);
    }

    public void removeShare(Long customerId, Long userId) {
        customerContactShareService.removeShare(customerId, userId);
    }

    public void removeAllShares(Long customerId) {
        customerContactShareService.removeAllShares(customerId);
    }

    public void cleanPendingReminders(Long customerId) {
        customerContactShareService.cleanPendingReminders(customerId);
    }

    public List<BizContact> getContacts(Long customerId) {
        return customerContactShareService.getContacts(customerId);
    }

    public void addContact(Long customerId, BizContact contact) {
        customerContactShareService.addContact(customerId, contact);
    }

    public void updateContact(Long customerId, Long contactId, BizContact contact) {
        customerContactShareService.updateContact(customerId, contactId, contact);
    }

    public void deleteContact(Long customerId, Long contactId) {
        customerContactShareService.deleteContact(customerId, contactId);
    }

    // ====== 回款记录 ======

    public List<BizPayment> getPayments(Long customerId) {
        List<BizPayment> payments = paymentMapper.selectList(
                new LambdaQueryWrapper<BizPayment>()
                        .eq(BizPayment::getCustomerId, customerId)
                        .orderByDesc(BizPayment::getPaymentDate));
        for (BizPayment p : payments) {
            if (p.getCreatedBy() != null) {
                SysUser user = userMapper.selectById(p.getCreatedBy());
                if (user != null) p.setCreatedByName(user.getRealName());
            }
        }
        return payments;
    }

    public Map<String, Object> getPaymentSummary(Long customerId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", paymentMapper.countByCustomerId(customerId));
        summary.put("totalAmount", paymentMapper.sumAmountByCustomerId(customerId));
        List<BizPayment> latest = paymentMapper.selectList(
                new LambdaQueryWrapper<BizPayment>()
                        .eq(BizPayment::getCustomerId, customerId)
                        .orderByDesc(BizPayment::getPaymentDate)
                        .last(LIT_LIMIT_1));
        summary.put("latestDate", latest.isEmpty() ? null : latest.get(0).getPaymentDate());
        return summary;
    }

    @Transactional
    public void addPayment(Long customerId, BizPayment payment) {
        // 【审查 backend-4 2026-10-18】与 addInvoice 同口径: 客户存在/未删/同集团
        customerAccessService.assertFinanceCustomerWritable(customerId);
        payment.setCustomerId(customerId);
        payment.setCreatedBy(SecurityUtils.getCurrentUserId());
        paymentMapper.insert(payment);
        recalculateCoreStatus(customerId);
    }

    @Transactional
    public void deletePayment(Long customerId, Long paymentId, String reason) {
        BizPayment payment = paymentMapper.selectById(paymentId);
        // 归属校验：记录必须属于请求路径中的客户，防止跨客户删除（IDOR）
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException(LIT_X_3);
        }
        // 【裁决 2026-09-24 更新】与发票同口径: 任何状态可删+必填原因+审计快照+逻辑删
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请填写删除原因（财务记录删除必须留痕）");
        }
        logFinanceDeletion("回款", customerId, paymentId, reason.trim(),
                "日期=" + payment.getPaymentDate() + ", 金额=" + payment.getPaymentAmount()
                        + ", 方式=" + payment.getPaymentMethod() + ", 状态=" + payment.getStatus());
        paymentMapper.deleteById(paymentId);
        recalculateCoreStatus(payment.getCustomerId());
    }

    public java.util.Map<String, Object> getDuplicateInfo(String customerName) {
        // 【议题五.1】先按主名查,未命中再按别名查(裁决:查重仅提示,不自动合并)
        // 【修复 2026-08-10 P2-01】创建人/创建时间信息同样限定在数据范围内(与 existsByName 同口径)
        // 【PR-01 N-02 2026-09-18】原生 SQL 绕过租户拦截器：补 c.tenant_id 条件（biz_customer 在
        // 租户白名单内）；无集团归属直接返回 null（fail-closed，不泄露他租户创建人）。
        try {
            List<Long> scopeUserIds = getCurrentScopeUserIds();
            String tenantFrag;
            java.util.List<Object> tenantArgs = new java.util.ArrayList<>();
            Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            if (g == null) return null;
            tenantFrag = " AND c.tenant_id = ?";
            tenantArgs.add(g);
            java.util.List<java.util.Map<String, Object>> rows;
            if (scopeUserIds == null) {
                java.util.List<Object> args = new java.util.ArrayList<>();
                args.add(customerName);
                args.addAll(tenantArgs);
                rows = jdbc.queryForList(
                    "SELECT u.real_name AS createdBy, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i') AS createdAt " +
                    "FROM biz_customer c JOIN sys_user u ON c.owner_id = u.id " +
                    "WHERE c.customer_name = ? AND c.deleted = 0" + tenantFrag + " ORDER BY c.created_at DESC LIMIT 1",
                    args.toArray());
            } else {
                java.util.List<Object> args = new java.util.ArrayList<>();
                args.add(customerName);
                args.addAll(tenantArgs);
                args.addAll(scopeUserIds);
                rows = jdbc.queryForList(
                    "SELECT u.real_name AS createdBy, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i') AS createdAt " +
                    "FROM biz_customer c JOIN sys_user u ON c.owner_id = u.id " +
                    "WHERE c.customer_name = ? AND c.deleted = 0" + tenantFrag + " AND c.owner_id IN (" +
                    String.join(",", java.util.Collections.nCopies(scopeUserIds.size(), "?")) +
                    ") ORDER BY c.created_at DESC LIMIT 1",
                    args.toArray());
            }
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
