package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 【回收站 2026-10-18】已删除客户/人员/文件的查看与恢复。
 *
 * <p>设计边界(不影响现有功能): 本服务只读 deleted=1 的行(显式 SQL 绕过 @TableLogic), 不改动任何
 * 现有列表/查询口径; 恢复动作携带闸门, 防止"恢复即脏数据":</p>
 * <ul>
 *   <li>客户: 同名活跃客户存在 → 拒绝(防重复卡, 如 290 条旧数据中 158 条重复);</li>
 *   <li>客户: 原负责人已被删除/不存在 → 拒绝(恢复后无人可见, 应先恢复人员或改派);</li>
 *   <li>客户: 公海池仍有 available/pending_approval 残留 → 拒绝(防私海+池内双归属);</li>
 *   <li>人员: 账号名已被活跃用户占用 → 拒绝; 恢复后角色/岗位/组织归属需重新分配(删除时已清除);</li>
 *   <li>文件: 内容已被 deleteStorage 物理清除 → 拒绝(仅保留删除记录); 原文件夹已删除 →
 *       恢复到根目录(folder_id 置空, 文件夹硬删且 ID 不复用, 不要求重建旧夹);
 *       私有文件上传人已删除/存储配额不足 → 拒绝。</li>
 * </ul>
 *
 * <p>权限: 控制器层 @PreAuthorize 之外再统一校验 {@link SecurityUtils#isAdmin()}, 回收站 HTTP 端点
 * 仅平台管理员可用(定时任务直调 service, 不受控制器守卫影响)。</p>
 *
 * <p>清理并发与数据保全: 恢复/彻底删除/批量清理先对 biz_file 行做 {@code SELECT ... FOR UPDATE},
 * 锁内复核 deleted=1 与内容存在性; 批量清理跳过激励佐证文件; 同一 oss_key 仍有活跃行时只删台账;
 * 物理内容删除失败时保留台账待下次重试。</p>
 */
@Service
@RequiredArgsConstructor
public class RecycleService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 客户彻底删除时随删的辅助关联(不阻断删除); 其余业务关联(含任务)存在即拒绝 */
    private static final Set<String> CUSTOMER_AUX_REFS = Set.of("分享", "提醒", "公海池记录", "联系人");
    /** 人员彻底删除时随删的辅助关联(激励已读行有 NO ACTION 外键); 其余关联存在即拒绝 */
    private static final Set<String> USER_AUX_REFS = Set.of("激励已读");
    /** 回收站清理的锁定读(与 BizFileMapper.selectByIdIncludingDeleted 同口径, 额外加行锁) */
    private static final RowMapper<BizFile> FILE_ROW_MAPPER = new BeanPropertyRowMapper<>(BizFile.class);

    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysLogMapper logMapper;
    private final BizFileMapper fileMapper;
    private final BizFileFolderMapper fileFolderMapper;
    private final FileService fileService;
    private final JdbcTemplate jdbc;
    private final com.aigicrm.security.PermissionCacheService permissionCacheService;

    // ==================== 已删除客户 ====================

    public PageResult<DeletedCustomerRow> deletedCustomers(int current, int size, String keyword) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safeCurrent = Math.max(current, 1);
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<BizCustomer> rows = customerMapper.selectDeletedPage(pattern, (safeCurrent - 1) * safeSize, safeSize);
        long total = customerMapper.countDeleted(pattern);
        Map<Long, String> ownerNames = ownerNamesOf(rows);
        List<DeletedCustomerRow> records = rows.stream()
                .map(c -> new DeletedCustomerRow(
                        c.getId(), c.getCustomerCode(), c.getCustomerName(),
                        c.getOwnerId(), ownerNames.get(c.getOwnerId()),
                        c.getIndustry(), c.getDevType(), c.getStatus(), c.getUpdatedAt()))
                .toList();
        return new PageResult<>(records, total);
    }

    @Transactional
    public String restoreCustomer(Long id) {
        BizCustomer c = customerMapper.selectByIdIncludingDeleted(id);
        if (c == null || !Objects.equals(c.getDeleted(), 1)) {
            throw new BusinessException("该客户不在回收站（可能已被恢复或已被彻底删除）");
        }
        if (customerMapper.countActiveByName(c.getCustomerName()) > 0) {
            throw new BusinessException("存在同名活跃客户，无法恢复（请先合并或修改活跃客户名称）");
        }
        if (c.getOwnerId() != null) {
            SysUser owner = userMapper.selectByIdIncludingDeleted(c.getOwnerId());
            if (owner == null || Objects.equals(owner.getDeleted(), 1)) {
                throw new BusinessException("原负责人已被删除，请先在回收站恢复该人员，再恢复该客户");
            }
        }
        if (customerMapper.countActivePoolRows(id) > 0) {
            throw new BusinessException("该客户仍有公海池记录未结束，请先在公海模块处理后恢复");
        }
        if (customerMapper.restoreDeleted(id) != 1) {
            throw new BusinessException("恢复失败：数据状态已变化，请刷新后重试");
        }
        writeAuditLog("恢复客户", "customer", id,
                "客户名=" + c.getCustomerName() + ", 编码=" + c.getCustomerCode()
                        + ", 原负责人ID=" + c.getOwnerId());
        return "客户已恢复" + (c.getOwnerId() == null ? "（无负责人，请到客户列表分配）" : "");
    }

    // ==================== 已删除人员 ====================

    public PageResult<DeletedUserRow> deletedUsers(int current, int size, String keyword) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safeCurrent = Math.max(current, 1);
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<SysUser> rows = userMapper.selectDeletedPage(pattern, (safeCurrent - 1) * safeSize, safeSize);
        long total = userMapper.countDeleted(pattern);
        Map<Long, String> deptNames = deptNamesOf(rows);
        List<DeletedUserRow> records = rows.stream()
                .map(u -> new DeletedUserRow(
                        u.getId(), u.getUsername(), u.getRealName(), u.getDeptId(),
                        deptNames.get(u.getDeptId()), u.getPhone(), u.getStatus(), u.getUpdatedAt()))
                .toList();
        return new PageResult<>(records, total);
    }

    @Transactional
    public String restoreUser(Long id) {
        SysUser u = userMapper.selectByIdIncludingDeleted(id);
        if (u == null || !Objects.equals(u.getDeleted(), 1)) {
            throw new BusinessException("该人员不在回收站（可能已被恢复或已被彻底删除）");
        }
        if (userMapper.countActiveByUsername(u.getUsername()) > 0) {
            throw new BusinessException("账号 " + u.getUsername() + " 已被活跃用户占用，无法恢复");
        }
        if (u.getDeptId() != null && deptMapper.selectById(u.getDeptId()) == null) {
            throw new BusinessException("原部门已不存在，无法恢复；请先恢复部门或由管理员在回收站外处理");
        }
        if (userMapper.restoreDeleted(id) != 1) {
            throw new BusinessException("恢复失败：数据状态已变化，请刷新后重试");
        }
        // 权限即时生效: 版本递增 + 缓存失效(与 SysUserService 删除/改权限同口径)
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 WHERE id = ?", id);
        permissionCacheService.evict(id);
        writeAuditLog("恢复人员", "sys_user", id,
                "账号=" + u.getUsername() + ", 姓名=" + u.getRealName() + ", 部门ID=" + u.getDeptId());
        return "人员已恢复；删除时已清除其角色/岗位/组织归属，请到「人员管理」重新分配角色后再通知本人登录";
    }

    // ==================== 详情 ====================

    public DeletedCustomerDetail deletedCustomerDetail(Long id) {
        BizCustomer c = customerMapper.selectByIdIncludingDeleted(id);
        if (c == null || !Objects.equals(c.getDeleted(), 1)) {
            throw new BusinessException("该客户不在回收站（可能已被恢复或已被彻底删除）");
        }
        String ownerName = c.getOwnerId() == null ? null
                : namesOf(Set.of(c.getOwnerId())).get(c.getOwnerId());
        return new DeletedCustomerDetail(c, ownerName, customerRelated(id));
    }

    public DeletedUserDetail deletedUserDetail(Long id) {
        SysUser u = userMapper.selectByIdIncludingDeleted(id);
        if (u == null || !Objects.equals(u.getDeleted(), 1)) {
            throw new BusinessException("该人员不在回收站（可能已被恢复或已被彻底删除）");
        }
        Map<String, Object> safe = new java.util.LinkedHashMap<>();
        safe.put("id", u.getId());
        safe.put("username", u.getUsername());
        safe.put("realName", u.getRealName());
        safe.put("phone", u.getPhone());
        safe.put("email", u.getEmail());
        safe.put("deptId", u.getDeptId());
        safe.put("status", u.getStatus());
        safe.put("dataScopeOverride", u.getDataScopeOverride());
        safe.put("leaveStatus", u.getLeaveStatus());
        safe.put("createdAt", u.getCreatedAt());
        safe.put("updatedAt", u.getUpdatedAt());
        SysDept dept = u.getDeptId() == null ? null : deptMapper.selectById(u.getDeptId());
        return new DeletedUserDetail(safe, dept == null ? null : dept.getDeptName(), userRelated(id));
    }

    public DeletedFileDetail deletedFileDetail(Long id) {
        BizFile f = requireDeletedFile(id);
        Map<Long, String> uploader = f.getUploaderId() == null ? Map.of()
                : namesOf(Set.of(f.getUploaderId()));
        Map<Long, String> folders = folderNamesOf(List.of(f));
        DeletedFileRow row = new DeletedFileRow(f.getId(), f.getFileName(), f.getFileSize(), f.getFileScope(),
                f.getUploaderId(), uploader.get(f.getUploaderId()),
                f.getFolderId(), folders.get(f.getFolderId()), f.getUpdatedAt(),
                fileService.contentExists(f.getOssKey()));
        return new DeletedFileDetail(row, f.getFileType(), f.getCreatedAt(), f.getOssUrl());
    }

    // ==================== 彻底删除(不可恢复) ====================

    /** 客户彻底删除: 财务/激励/跟进/指标/阶段金额/审批/任务等业务关联存在时拒绝; 分享/提醒/公海/联系人随删 */
    @Transactional
    public String hardDeleteCustomer(Long id) {
        BizCustomer c = customerMapper.selectByIdIncludingDeleted(id);
        if (c == null || !Objects.equals(c.getDeleted(), 1)) {
            throw new BusinessException("该客户不在回收站（可能已被恢复或已被彻底删除）");
        }
        Map<String, Long> related = customerRelated(id);
        List<String> blocked = related.entrySet().stream()
                .filter(e -> !CUSTOMER_AUX_REFS.contains(e.getKey()) && e.getValue() > 0)
                .map(e -> e.getKey() + " " + e.getValue())
                .toList();
        if (!blocked.isEmpty()) {
            throw new BusinessException("存在关联数据（" + String.join("、", blocked)
                    + "），不能彻底删除；请先处理这些记录");
        }
        jdbc.update("DELETE FROM biz_customer_share WHERE customer_id = ?", id);
        jdbc.update("DELETE FROM biz_reminder WHERE customer_id = ?", id);
        jdbc.update("DELETE FROM biz_sea_pool WHERE customer_id = ?", id);
        // 联系人随客户硬删(无外键, 软删行一并清掉, 防孤儿)
        jdbc.update("DELETE FROM biz_contact WHERE customer_id = ?", id);
        int deleted = jdbc.update("DELETE FROM biz_customer WHERE id = ? AND deleted = 1", id);
        if (deleted != 1) {
            throw new BusinessException("彻底删除失败：数据状态已变化，请刷新后重试");
        }
        writeAuditLog("彻底删除客户", "customer", id,
                "客户名=" + c.getCustomerName() + ", 编码=" + c.getCustomerCode());
        return "客户已彻底删除（不可恢复）";
    }

    /** 人员彻底删除: 仍被业务数据引用时拒绝; 角色/岗位/组织关联随删 */
    @Transactional
    public String hardDeleteUser(Long id) {
        SysUser u = userMapper.selectByIdIncludingDeleted(id);
        if (u == null || !Objects.equals(u.getDeleted(), 1)) {
            throw new BusinessException("该人员不在回收站（可能已被恢复或已被彻底删除）");
        }
        Map<String, Long> related = userRelated(id);
        List<String> blocked = related.entrySet().stream()
                .filter(e -> !USER_AUX_REFS.contains(e.getKey()) && e.getValue() > 0)
                .map(e -> e.getKey() + " " + e.getValue())
                .toList();
        if (!blocked.isEmpty()) {
            throw new BusinessException("存在业务关联（" + String.join("、", blocked)
                    + "），不能彻底删除；请先处理这些记录");
        }
        // biz_incentive_read.user_id 为 NO ACTION 外键, 必须随人员行先删, 否则 500
        jdbc.update("DELETE FROM biz_incentive_read WHERE user_id = ?", id);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
        jdbc.update("DELETE FROM sys_user_position WHERE user_id = ?", id);
        jdbc.update("DELETE FROM sys_user_org WHERE user_id = ?", id);
        jdbc.update("DELETE FROM sys_org_admin WHERE user_id = ?", id);
        int deleted = jdbc.update("DELETE FROM sys_user WHERE id = ? AND deleted = 1", id);
        if (deleted != 1) {
            throw new BusinessException("彻底删除失败：数据状态已变化，请刷新后重试");
        }
        permissionCacheService.evict(id);
        writeAuditLog("彻底删除人员", "sys_user", id,
                "账号=" + u.getUsername() + ", 姓名=" + u.getRealName());
        return "人员已彻底删除（不可恢复）";
    }

    /** 文件彻底删除: 激励记录仍引用时拒绝; 行锁内复核后清分享/内容/台账 */
    @Transactional
    public String hardDeleteFile(Long id) {
        // 与恢复/批量清理共用行锁口径: 锁内复核 deleted=1, 防并发恢复后误删活跃文件
        BizFile f = lockDeletedFile(id);
        if (f == null) {
            throw new BusinessException("该文件不在回收站（可能已被恢复或已被彻底删除）");
        }
        if (isIncentiveEvidence(id)) {
            throw new BusinessException("该文件仍被激励记录引用，不能彻底删除；请先在激励模块处理记录");
        }
        // 【V2026.11.14.2 P0 修正】级联改走 purgeFileCascade: 主行+全部版本+分享+台账,
        // 引用计数防误删——此前只删当前 ossKey, 版本行与旧字节全留(隐私/存储双重泄漏)。
        purgeFileCascade(f);
        writeAuditLog("彻底删除文件", "biz_file", id,
                "文件名=" + f.getFileName() + ", scope=" + f.getFileScope());
        return "文件已彻底删除（不可恢复）";
    }

    /** 回收站文件内容下载(仅详情查看, 已由控制器校验平台管理员 + system:recycle:view) */
    public void writeFileContent(Long id, jakarta.servlet.http.HttpServletResponse response) {
        BizFile f;
        try {
            f = requireDeletedFile(id);
        } catch (BusinessException e) {
            response.setStatus(404);
            return;
        }
        if (!fileService.contentExists(f.getOssKey())) {
            response.setStatus(404);
            return;
        }
        fileService.writeStoredContent(f.getOssKey(), f.getFileName(), f.getFileType(), response);
    }

    // ==================== 已删除文件 ====================

    public PageResult<DeletedFileRow> deletedFiles(int current, int size, String keyword) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safeCurrent = Math.max(current, 1);
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<BizFile> rows = fileMapper.selectDeletedPage(pattern, (safeCurrent - 1) * safeSize, safeSize);
        long total = fileMapper.countDeleted(pattern);
        Map<Long, String> uploaderNames = namesOf(rows.stream().map(BizFile::getUploaderId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> folderNames = folderNamesOf(rows);
        List<DeletedFileRow> records = rows.stream()
                .map(f -> new DeletedFileRow(
                        f.getId(), f.getFileName(), f.getFileSize(), f.getFileScope(),
                        f.getUploaderId(), uploaderNames.get(f.getUploaderId()),
                        f.getFolderId(), folderNames.get(f.getFolderId()),
                        f.getDeletedAt() != null ? f.getDeletedAt() : f.getUpdatedAt(),
                        fileService.contentExists(f.getOssKey())))
                .toList();
        return new PageResult<>(records, total);
    }

    @Transactional
    public String restoreFile(Long id) {
        // 【TOCTOU】锁住台账行再复核: 与彻底删除/批量清理互斥, 防"恢复成功的同时被清理"。
        // 行锁持有到事务提交, 期间 contentExists 检查 + 条件恢复 UPDATE 在同锁内完成;
        // 并发清理(同样 FOR UPDATE 同一行)会等待, 提交后复核 deleted=1 失败则跳过。
        BizFile f = lockDeletedFile(id);
        if (f == null) {
            throw new BusinessException("该文件不在回收站（可能已被恢复或已被彻底删除）");
        }
        // 删除文件时 deleteStorage 已物理清除字节: 内容不在则元数据恢复出来也是坏链接, fail-closed
        if (!fileService.contentExists(f.getOssKey())) {
            throw new BusinessException("文件内容已在删除时物理清除，无法恢复（仅保留删除记录，如需找回请联系管理员从备份还原）");
        }
        // 【审查 biz-2 2026-10-18】文件夹为硬删除(仅空夹可删), ID 不会重建复用:
        // 原文件夹已不存在时恢复到根目录(folder_id 置空), 不再要求用户"重建同 ID 文件夹"(不可达指引)。
        boolean folderMissing = f.getFolderId() != null
                && fileFolderMapper.selectById(f.getFolderId()) == null;
        if ("private".equals(f.getFileScope())) {
            if (f.getUploaderId() == null) {
                throw new BusinessException("私有文件缺少上传人，无法恢复");
            }
            SysUser uploader = userMapper.selectByIdIncludingDeleted(f.getUploaderId());
            if (uploader == null || Objects.equals(uploader.getDeleted(), 1)) {
                throw new BusinessException("上传人已被删除，请先在回收站恢复该人员，再恢复其私有文件");
            }
            long size = f.getFileSize() != null ? f.getFileSize() : 0L;
            if (size > 0 && userMapper.tryConsumeQuota(f.getUploaderId(), size) == 0) {
                throw new BusinessException("上传者存储配额不足，无法恢复该文件");
            }
        } else if (com.aigicrm.module.file.service.FileService.quotaBearing(f.getFileScope())
                && f.getUploaderId() != null) {
            // 【T2 2026-11-11 审计整改】配额覆盖全 scope: public/incentive 文件恢复同样重新
            // 占用(上传者存在性校验是私有文件专属语义——非私有文件不随上传人账号状态判生死;
            // 历史孤儿行 uploader 缺失时无从归属配额, 免检恢复)。
            long size = f.getFileSize() != null ? f.getFileSize() : 0L;
            if (size > 0 && userMapper.tryConsumeQuota(f.getUploaderId(), size) == 0) {
                throw new BusinessException("上传者存储配额不足，无法恢复该文件");
            }
        }
        int restored = folderMissing ? fileMapper.restoreDeletedToRoot(id) : fileMapper.restoreDeleted(id);
        if (restored != 1) {
            throw new BusinessException("恢复失败：数据状态已变化，请刷新后重试");
        }
        String message = folderMissing
                ? "文件已恢复到“" + ("private".equals(f.getFileScope()) ? "我的文件" : "公共文件")
                        + "”根目录（原文件夹已删除）"
                : "文件已恢复";
        writeAuditLog("恢复文件", "biz_file", id,
                "文件名=" + f.getFileName() + ", scope=" + f.getFileScope() + ", 大小=" + f.getFileSize()
                        + (folderMissing ? ", 原文件夹已删除→根目录" : ""));
        return message;
    }

    // ==================== 文件回收站清理 ====================

    // native-sql-audit: 回收站保留期清理是系统级维护任务(跨全部集团), 按保留期/台账状态过滤而非会话集团;
    // 只做物理内容删除与台账硬删, 不经任何业务读接口, 不放大角色可见范围。
    /**
     * 清理超过保留期的已删除文件(物理内容+台账行)。retentionDays<=0 视为不自动清理。
     * 用 JdbcTemplate 直连 SQL 跨租户执行: 系统级维护任务不受会话租户拦截器影响。
     *
     * <p>保留 int 返回值供 {@code FileRecycleCleanupTask} 使用; 需要跳过/失败计数时调用
     * {@link #purgeExpiredFilesWithStats(int)}。</p>
     */
    @Transactional
    public int purgeExpiredFiles(int retentionDays) {
        return purgeExpiredFilesWithStats(retentionDays).purged();
    }

    /** 保留期清理的完整结果(清理/跳过/失败), 定时任务与手动清空共用同一清理内核 */
    @Transactional
    public PurgeFilesResult purgeExpiredFilesWithStats(int retentionDays) {
        if (retentionDays <= 0) return new PurgeFilesResult(0, 0, 0);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        // 【V2026.11.14.2 P0 修正】保留期只认 deleted_at(进回收站时刻)——原 updated_at 是
        // "最后修改时间"(改名/替换都会刷新), 老文件今天删除明天即被清空, 回收站承诺失效。
        // COALESCE 兜底存量 NULL 行(V2026.11.14.2 已回填, 双保险)。
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM biz_file WHERE deleted = 1 "
                        + "AND COALESCE(deleted_at, updated_at) < ?", Long.class, cutoff);
        PurgeFilesResult result = purgeFileRows(ids);
        if (!result.isEmpty()) {
            writeAuditLog("清理文件回收站", "biz_file", null,
                    "保留期" + retentionDays + "天, 清理" + result.purged()
                            + "个, 跳过" + result.skipped() + "个, 失败" + result.failed() + "个");
        }
        return result;
    }

    /** 管理员手动清空文件回收站: 立即清除全部已删除文件的物理内容与台账行 */
    @Transactional
    public PurgeFilesResult purgeAllDeletedFiles() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM biz_file WHERE deleted = 1", Long.class);
        PurgeFilesResult result = purgeFileRows(ids);
        if (!result.isEmpty()) {
            writeAuditLog("清空文件回收站", "biz_file", null,
                    "手动清空: 清理" + result.purged() + "个, 跳过" + result.skipped()
                            + "个, 失败" + result.failed() + "个");
        }
        return result;
    }

    /**
     * 逐行清理(每行独立判定, 单行失败不阻断其余):
     * <ol>
     *   <li>先对 biz_file 行 {@code FOR UPDATE}, 锁内复核 deleted=1(并发恢复则跳过);</li>
     *   <li>激励佐证文件跳过(保留台账与内容, 走激励模块处理);</li>
     *   <li>同一 oss_key 仍有 deleted=0 活跃行时只删已删台账, 不动物理内容;</li>
     *   <li>删除内容后仍 contentExists=true 视为物理删除失败, 保留台账待下次重试;</li>
     *   <li>成功才随删 biz_file_share 与台账行。</li>
     * </ol>
     */
    private PurgeFilesResult purgeFileRows(List<Long> ids) {
        int purged = 0;
        int skipped = 0;
        int failed = 0;
        for (Long id : ids) {
            if (id == null) continue;
            BizFile f = lockDeletedFile(id);
            if (f == null) {
                skipped++;
                continue;
            }
            if (isIncentiveEvidence(id)) {
                skipped++;
                continue;
            }
            try {
                purgeFileCascade(f);
                purged++;
            } catch (BusinessException e) {
                failed++;
            }
        }
        return new PurgeFilesResult(purged, skipped, failed);
    }

    /**
     * 【V2026.11.14.2 P0 修正】彻底删除级联 = 主行 + **全部版本历史** + 分享 + 台账。
     * 此前只删当前 ossKey——biz_file_version 行与其旧字节全留(隐私+存储双重泄漏);
     * 回滚产生的"主行 key=版本行 key"场景按 key 去重, 引用计数防误删他文件仍在用的对象。
     * 先删内容再删台账: 内容删不掉则保留记录待重试(绝不"台账没了字节还在")。
     * hardDeleteFile 与 purgeFileRows(保留期清理)共用本方法。
     */
    private void purgeFileCascade(BizFile f) {
        Long id = f.getId();
        // ① 收集本文件全部存储 key(主行+版本行, 去重)
        Set<String> keys = new LinkedHashSet<>();
        if (f.getOssKey() != null && !f.getOssKey().isBlank()) keys.add(f.getOssKey());
        jdbc.query("SELECT oss_key FROM biz_file_version WHERE file_id = ?", rs -> {
            String k = rs.getString("oss_key");
            if (k != null && !k.isBlank()) keys.add(k);
        }, id);
        // ② 逐 key 引用计数后删内容(其他活跃文件或其他文件的版本仍引用 → 跳过)
        for (String key : keys) {
            if (hasActiveDuplicateKey(key, id) || hasVersionDuplicateKey(key, id)) continue;
            fileService.deleteStoredContent(key);
            if (fileService.contentExists(key)) {
                throw new BusinessException("版本内容物理删除失败，已保留记录，请稍后重试");
            }
        }
        // ③ 内容清完才动台账: 版本行 → 分享 → 主行(顺序保证"删失败可重试"不产生孤儿表)
        jdbc.update("DELETE FROM biz_file_version WHERE file_id = ?", id);
        jdbc.update("DELETE FROM biz_file_share WHERE file_id = ?", id);
        int deleted = jdbc.update("DELETE FROM biz_file WHERE id = ? AND deleted = 1", id);
        if (deleted != 1) {
            throw new BusinessException("彻底删除失败：数据状态已变化，请刷新后重试");
        }
    }

    /** 版本 key 引用计数: 其他文件的版本行是否仍引用该 key(是→不可物理删) */
    private boolean hasVersionDuplicateKey(String key, Long excludeFileId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file_version WHERE oss_key = ? AND file_id <> ?",
                Long.class, key, excludeFileId);
        return n != null && n > 0;
    }

    /**
     * 【V2026.11.14.3 P1】版本历史过期清理(每日任务): 归档超 retention-days 的版本行
     * 删内容+删行。字节保护三重守卫: 活跃主行仍引用 / 同 key 较新版本行仍引用 / 其他文件
     * 版本仍引用——任一命中只删行不删字节。版本计入用户配额为第二版产品决策。
     */
    public int purgeExpiredVersions(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        record VRow(Long id, String ossKey) {}
        List<VRow> rows = jdbc.query(
                "SELECT v.id, v.oss_key FROM biz_file_version v "
                        + "WHERE v.created_at < ? "
                        + "AND NOT EXISTS (SELECT 1 FROM biz_file b WHERE b.oss_key = v.oss_key AND b.deleted = 0) "
                        + "AND NOT EXISTS (SELECT 1 FROM biz_file_version v2 WHERE v2.oss_key = v.oss_key AND v2.created_at >= ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM biz_file_version v3 WHERE v3.oss_key = v.oss_key AND v3.file_id <> v.file_id)",
                (rs, i) -> new VRow(rs.getLong("id"), rs.getString("oss_key")),
                cutoff, cutoff);
        int purged = 0;
        for (VRow row : rows) {
            String key = row.ossKey();
            if (key != null && !key.isBlank() && fileService.contentExists(key)) {
                fileService.deleteStoredContent(key);
                if (fileService.contentExists(key)) continue; // 字节删失败→保留行待重试
            }
            purged += jdbc.update("DELETE FROM biz_file_version WHERE id = ?", row.id());
        }
        return purged;
    }

    // ==================== 内部 ====================

    private Map<Long, String> ownerNamesOf(List<BizCustomer> rows) {
        Set<Long> ids = rows.stream().map(BizCustomer::getOwnerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return namesOf(ids);
    }

    private Map<Long, String> namesOf(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) return names;
        userMapper.selectNamesByIds(ids).forEach(u -> names.put(u.getId(), u.getRealName()));
        return names;
    }

    private Map<Long, String> deptNamesOf(List<SysUser> rows) {
        Map<Long, String> names = new HashMap<>();
        Set<Long> ids = rows.stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return names;
        for (SysDept d : deptMapper.selectBatchIds(ids)) {
            names.put(d.getId(), d.getDeptName());
        }
        return names;
    }

    /** 文件夹为硬删除(仅允许空夹删除), 查不到即原文件夹已不存在 */
    private Map<Long, String> folderNamesOf(List<BizFile> rows) {
        Map<Long, String> names = new HashMap<>();
        Set<Long> ids = rows.stream().map(BizFile::getFolderId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return names;
        for (BizFileFolder folder : fileFolderMapper.selectBatchIds(ids)) {
            names.put(folder.getId(), folder.getFolderName());
        }
        return names;
    }

    private BizFile requireDeletedFile(Long id) {
        BizFile f = fileMapper.selectByIdIncludingDeleted(id);
        if (f == null || !Objects.equals(f.getDeleted(), 1)) {
            throw new BusinessException("该文件不在回收站（可能已被恢复或已被彻底删除）");
        }
        return f;
    }

    /** 锁定读: 只返回 deleted=1 的行; 必须在事务内调用(恢复/彻底删除/批量清理共用) */
    private BizFile lockDeletedFile(Long id) {
        List<BizFile> rows = jdbc.query(
                "SELECT * FROM biz_file WHERE id = ? AND deleted = 1 FOR UPDATE", FILE_ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 同一 oss_key 是否仍被活跃台账行引用: 有则只能删已删台账, 不得物理删除共享内容 */
    private boolean hasActiveDuplicateKey(String ossKey, Long excludeId) {
        if (ossKey == null || ossKey.isBlank()) return false;
        return countRows("SELECT COUNT(*) FROM biz_file WHERE oss_key = ? AND deleted = 0 AND id <> ?",
                ossKey, excludeId) > 0;
    }

    /**
     * 清理前的激励佐证判定。优先复用 FileService 口径(五槽引用); 定时任务线程无会话上下文,
     * 开启租户插件后 mapper 查询会被改写为恒空, 故再用 JdbcTemplate 保守核对一次
     * (只会多跳过、不会多删)。file_* 列形如 "[1,2]"/"1,2"。
     */
    private boolean isIncentiveEvidence(Long fileId) {
        if (fileService.isIncentiveEvidence(fileId)) return true;
        String sql = "SELECT COUNT(*) FROM biz_incentive_record WHERE "
                + "FIND_IN_SET(?, " + jsonIdList("file_id") + ") > 0 "
                + "OR FIND_IN_SET(?, " + jsonIdList("file_pre_id") + ") > 0 "
                + "OR FIND_IN_SET(?, " + jsonIdList("file_mid_id") + ") > 0 "
                + "OR FIND_IN_SET(?, " + jsonIdList("file_post_id") + ") > 0 "
                + "OR FIND_IN_SET(?, " + jsonIdList("file_track_id") + ") > 0";
        return countRows(sql, fileId, fileId, fileId, fileId, fileId) > 0;
    }

    /** 把 "[1,2]"/"1,2" 归一为 FIND_IN_SET 可用的逗号串 */
    private static String jsonIdList(String column) {
        return "REPLACE(REPLACE(REPLACE(COALESCE(" + column + ", ''), '[', ''), ']', ''), ' ', '')";
    }

    /** 客户关联数据计数(与彻底删除守卫同源; 含软删行, 因为删客户会孤立它们) */
    private Map<String, Long> customerRelated(Long customerId) {
        Map<String, Long> m = new java.util.LinkedHashMap<>();
        m.put("发票", countRows("SELECT COUNT(*) FROM biz_invoice WHERE customer_id = ?", customerId));
        m.put("回款", countRows("SELECT COUNT(*) FROM biz_payment WHERE customer_id = ?", customerId));
        m.put("激励记录", countRows("SELECT COUNT(*) FROM biz_incentive_record WHERE customer_id = ?", customerId));
        m.put("六项指标", countRows("SELECT COUNT(*) FROM biz_customer_indicator WHERE customer_id = ?", customerId));
        m.put("跟进日志", countRows("SELECT COUNT(*) FROM biz_customer_log WHERE customer_id = ?", customerId));
        m.put("阶段金额", countRows("SELECT COUNT(*) FROM biz_customer_stage_amount WHERE customer_id = ?", customerId));
        m.put("审批单", countRows("SELECT COUNT(*) FROM biz_approval WHERE customer_id = ?", customerId));
        m.put("分享", countRows("SELECT COUNT(*) FROM biz_customer_share WHERE customer_id = ?", customerId));
        m.put("提醒", countRows("SELECT COUNT(*) FROM biz_reminder WHERE customer_id = ?", customerId));
        m.put("公海池记录", countRows("SELECT COUNT(*) FROM biz_sea_pool WHERE customer_id = ?", customerId));
        m.put("联系人", countRows("SELECT COUNT(*) FROM biz_contact WHERE customer_id = ?", customerId));
        // 任务未软删/已软删都算(删客户会孤立台账), 存在即阻断
        m.put("任务", countRows("SELECT COUNT(*) FROM biz_task WHERE customer_id = ?", customerId));
        return m;
    }

    /** 人员业务关联计数(除 USER_AUX_REFS 外全部阻断彻底删除; 审计日志 sys_log 保留 username 不在此列) */
    private Map<String, Long> userRelated(Long userId) {
        Map<String, Long> m = new java.util.LinkedHashMap<>();
        m.put("名下客户", countRows("SELECT COUNT(*) FROM biz_customer WHERE owner_id = ?", userId));
        m.put("审批申请", countRows("SELECT COUNT(*) FROM biz_approval WHERE applicant_id = ?", userId));
        m.put("审批节点", countRows("SELECT COUNT(*) FROM biz_approval_node WHERE approver_id = ?", userId));
        m.put("激励记录", countRows("SELECT COUNT(*) FROM biz_incentive_record WHERE user_id = ?", userId));
        m.put("上传文件", countRows("SELECT COUNT(*) FROM biz_file WHERE uploader_id = ?", userId));
        m.put("跟进日志", countRows("SELECT COUNT(*) FROM biz_customer_log WHERE user_id = ?", userId));
        m.put("提醒", countRows("SELECT COUNT(*) FROM biz_reminder WHERE owner_id = ?", userId));
        m.put("被分享", countRows("SELECT COUNT(*) FROM biz_customer_share WHERE shared_to_user_id = ?", userId));
        m.put("阶段金额", countRows("SELECT COUNT(*) FROM biz_customer_stage_amount WHERE created_by = ? OR updated_by = ?", userId, userId));
        m.put("转移记录", countRows("SELECT COUNT(*) FROM biz_customer_transfer WHERE from_user_id = ? OR to_user_id = ? OR operator_id = ?", userId, userId, userId));
        // 激励已读: NO ACTION 外键, 彻底删除时随删(见 USER_AUX_REFS)
        m.put("激励已读", countRows("SELECT COUNT(*) FROM biz_incentive_read WHERE user_id = ?", userId));
        // 文件目录/文件分享: 存在即阻断(防目录/分享指向已不存在的人)
        m.put("文件目录", countRows("SELECT COUNT(*) FROM biz_file_folder WHERE owner_id = ?", userId));
        m.put("文件分享", countRows("SELECT COUNT(*) FROM biz_file_share WHERE shared_to_user_id = ? OR shared_by_user_id = ?", userId, userId));
        return m;
    }

    private long countRows(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    /** 恢复留痕: 与删除同口径写 sys_log(管理员可在 系统管理-日志管理 回查) */
    private void writeAuditLog(String action, String targetType, Long targetId, String detail) {
        SysLog log = new SysLog();
        SecurityUser current = SecurityUtils.getCurrentUser();
        if (current != null) {
            log.setUserId(current.getUserId());
            log.setUsername(current.getUsername());
        }
        log.setModule("系统");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    public record PageResult<T>(List<T> records, long total) {
    }

    /** 文件回收站清理结果: 已清理/跳过(激励佐证、并发状态变化)/失败(物理删除失败保留台账) */
    public record PurgeFilesResult(int purged, int skipped, int failed) {
        public boolean isEmpty() {
            return purged == 0 && skipped == 0 && failed == 0;
        }
    }

    public record DeletedCustomerRow(Long id, String customerCode, String customerName, Long ownerId,
                                     String ownerName, String industry, String devType, String status,
                                     LocalDateTime deletedAt) {
    }

    public record DeletedUserRow(Long id, String username, String realName, Long deptId, String deptName,
                                 String phone, Integer status, LocalDateTime deletedAt) {
    }

    public record DeletedFileRow(Long id, String fileName, Long fileSize, String fileScope,
                                 Long uploaderId, String uploaderName, Long folderId, String folderName,
                                 LocalDateTime deletedAt, boolean contentAvailable) {
    }

    public record DeletedCustomerDetail(BizCustomer customer, String ownerName, Map<String, Long> related) {
    }

    public record DeletedUserDetail(Map<String, Object> user, String deptName, Map<String, Long> related) {
    }

    public record DeletedFileDetail(DeletedFileRow file, String fileType, LocalDateTime createdAt, String url) {
    }

    // ==================== 【V2026.11.14.1 文件夹回收站 + 我的回收站】(用户拍板 2026-11-14) ====================

    public record DeletedFolderRow(Long id, String folderName, String folderType, Long ownerId,
                                   Long deletedBy, LocalDateTime deletedAt) {
    }

    private static final RowMapper<BizFileFolder> FOLDER_ROW_MAPPER = new BeanPropertyRowMapper<>(BizFileFolder.class);

    /** 回收站归属查询(归属闸门用): 文件的删除人(未填=存量行 NULL, 仅 admin 可动; 不在回收站=null) */
    public Long fileDeletedBy(Long id) {
        List<Long> rows = jdbc.queryForList(
                "SELECT deleted_by FROM biz_file WHERE id = ? AND deleted = 1", Long.class, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 回收站归属查询(归属闸门用): 夹的删除人(不在回收站=null) */
    public Long folderDeletedBy(Long id) {
        List<Long> rows = jdbc.queryForList(
                "SELECT deleted_by FROM biz_file_folder WHERE id = ? AND deleted = 1", Long.class, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** admin 全量夹回收站分页(与 deletedFiles 同口径; system/recycle 页用) */
    public PageResult<DeletedFolderRow> deletedFolders(int current, int size, String keyword) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(current, 1) - 1) * pageSize;
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<DeletedFolderRow> rows = jdbc.query(
                "SELECT id, folder_name, folder_type, owner_id, deleted_by, deleted_at "
                        + "FROM biz_file_folder WHERE deleted = 1 AND folder_name LIKE ? "
                        + "ORDER BY deleted_at DESC LIMIT ?, ?",
                (rs, i) -> new DeletedFolderRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toLocalDateTime()),
                pattern, offset, pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file_folder WHERE deleted = 1 AND folder_name LIKE ?",
                Long.class, pattern);
        return new PageResult<>(rows, total);
    }

    private BizFileFolder lockDeletedFolder(Long id) {
        List<BizFileFolder> rows = jdbc.query(
                "SELECT * FROM biz_file_folder WHERE id = ? AND deleted = 1 FOR UPDATE", FOLDER_ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 我的回收站·夹(非 admin 视角): 只列本人删除的(deleted_by 归属口径); admin 全量走 system/recycle 页 */
    public PageResult<DeletedFolderRow> deletedFoldersBy(Long userId, int current, int size, String keyword) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(current, 1) - 1) * pageSize;
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<DeletedFolderRow> rows = jdbc.query(
                "SELECT id, folder_name, folder_type, owner_id, deleted_by, deleted_at "
                        + "FROM biz_file_folder WHERE deleted = 1 AND deleted_by = ? "
                        + "AND folder_name LIKE ? ORDER BY deleted_at DESC LIMIT ?, ?",
                (rs, i) -> new DeletedFolderRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toLocalDateTime()),
                userId, pattern, offset, pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file_folder WHERE deleted = 1 AND deleted_by = ? "
                        + "AND folder_name LIKE ?", Long.class, userId, pattern);
        return new PageResult<>(rows, total);
    }

    /** 恢复夹(锁内复核 deleted=1): 父夹已删/不存在 → 挂根(parentId=0)。空夹软删, 无文件级联问题 */
    public String restoreFolder(Long id) {
        BizFileFolder f = lockDeletedFolder(id);
        if (f == null) throw new BusinessException("该文件夹不在回收站（可能已被恢复或已被彻底删除）");
        Long parentId = f.getParentId();
        if (parentId != null && parentId != 0) {
            // MP @TableLogic: selectById 恒过滤已删父夹 → 非 null 即父夹在用
            if (fileFolderMapper.selectById(parentId) == null) parentId = 0L;
        }
        int updated = jdbc.update(
                "UPDATE biz_file_folder SET deleted = 0, deleted_at = NULL, deleted_by = NULL, parent_id = ? "
                        + "WHERE id = ? AND deleted = 1",
                parentId == null ? 0L : parentId, id);
        if (updated == 0) throw new BusinessException("恢复失败（并发状态已变化），请刷新后重试");
        return "文件夹「" + f.getFolderName() + "」已恢复";
    }

    /** 彻底删除夹(空夹软删, 无文件级联): 清 ACL 与逐夹定向授权行(授权随夹消亡)后删台账行 */
    public String hardDeleteFolder(Long id) {
        BizFileFolder f = lockDeletedFolder(id);
        if (f == null) throw new BusinessException("该文件夹不在回收站（可能已被恢复或已被彻底删除）");
        jdbc.update("DELETE FROM biz_folder_acl WHERE folder_id = ?", id);
        jdbc.update("DELETE FROM biz_folder_type_default WHERE folder_id = ?", id);
        int deleted = jdbc.update("DELETE FROM biz_file_folder WHERE id = ? AND deleted = 1", id);
        if (deleted == 0) throw new BusinessException("彻底删除失败（并发状态已变化），请刷新后重试");
        return "文件夹「" + f.getFolderName() + "」已彻底删除";
    }

    /** 我的回收站·文件(非 admin 视角): 只列本人删除的(deleted_by 归属口径; V2026.11.14.1 补列) */
    public PageResult<DeletedFileRow> deletedFilesBy(Long userId, int current, int size, String keyword) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(current, 1) - 1) * pageSize;
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<DeletedFileRow> rows = jdbc.query(
                "SELECT f.id, f.file_name, f.file_size, f.file_scope, f.uploader_id, u.real_name, "
                        + "f.folder_id, fo.folder_name, f.deleted_at "
                        + "FROM biz_file f "
                        + "LEFT JOIN sys_user u ON u.id = f.uploader_id "
                        + "LEFT JOIN biz_file_folder fo ON fo.id = f.folder_id "
                        + "WHERE f.deleted = 1 AND f.deleted_by = ? "
                        + "AND (f.file_name LIKE ? OR f.oss_key LIKE ?) "
                        + "ORDER BY f.deleted_at DESC LIMIT ?, ?",
                (rs, i) -> new DeletedFileRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getLong(5), rs.getString(6), rs.getObject(7) == null ? null : rs.getLong(7),
                        rs.getString(8),
                        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toLocalDateTime(), true),
                userId, pattern, pattern, offset, pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file WHERE deleted = 1 AND deleted_by = ? "
                        + "AND (file_name LIKE ? OR oss_key LIKE ?)",
                Long.class, userId, pattern, pattern);
        return new PageResult<>(rows, total);
    }
}
