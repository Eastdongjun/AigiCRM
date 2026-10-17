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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
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
 *   <li>文件: 内容已被 deleteStorage 物理清除 → 拒绝(仅保留删除记录); 原文件夹不存在/私有文件
 *       上传人已删除/存储配额不足 → 拒绝。</li>
 * </ul>
 *
 * <p>权限: 控制器层 @PreAuthorize 校验 system:recycle:view / system:recycle:restore, 默认仅系统管理员。</p>
 */
@Service
@RequiredArgsConstructor
public class RecycleService {

    private static final int MAX_PAGE_SIZE = 100;

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
                        f.getUpdatedAt(), fileService.contentExists(f.getOssKey())))
                .toList();
        return new PageResult<>(records, total);
    }

    @Transactional
    public String restoreFile(Long id) {
        BizFile f = fileMapper.selectByIdIncludingDeleted(id);
        if (f == null || !Objects.equals(f.getDeleted(), 1)) {
            throw new BusinessException("该文件不在回收站（可能已被恢复或已被彻底删除）");
        }
        // 删除文件时 deleteStorage 已物理清除字节: 内容不在则元数据恢复出来也是坏链接, fail-closed
        if (!fileService.contentExists(f.getOssKey())) {
            throw new BusinessException("文件内容已在删除时物理清除，无法恢复（仅保留删除记录，如需找回请联系管理员从备份还原）");
        }
        if (f.getFolderId() != null && fileFolderMapper.selectById(f.getFolderId()) == null) {
            throw new BusinessException("原文件夹已删除，无法恢复；请先重建文件夹后再恢复");
        }
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
        }
        if (fileMapper.restoreDeleted(id) != 1) {
            throw new BusinessException("恢复失败：数据状态已变化，请刷新后重试");
        }
        writeAuditLog("恢复文件", "biz_file", id,
                "文件名=" + f.getFileName() + ", scope=" + f.getFileScope() + ", 大小=" + f.getFileSize());
        return "文件已恢复";
    }

    // ==================== 文件回收站清理 ====================

    // native-sql-audit: 回收站保留期清理是系统级维护任务(跨全部集团), 按保留期/台账状态过滤而非会话集团;
    // 只做物理内容删除与台账硬删, 不经任何业务读接口, 不放大角色可见范围。
    /**
     * 清理超过保留期的已删除文件(物理内容+台账行)。retentionDays<=0 视为不自动清理。
     * 用 JdbcTemplate 直连 SQL 跨租户执行: 系统级维护任务不受会话租户拦截器影响。
     */
    @Transactional
    public int purgeExpiredFiles(int retentionDays) {
        if (retentionDays <= 0) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, oss_key FROM biz_file WHERE deleted = 1 AND updated_at < ?", cutoff);
        int purged = purgeFileRows(rows);
        if (purged > 0) {
            writeAuditLog("清理文件回收站", "biz_file", null,
                    "保留期" + retentionDays + "天, 清理" + purged + "个文件");
        }
        return purged;
    }

    /** 管理员手动清空文件回收站: 立即清除全部已删除文件的物理内容与台账行 */
    @Transactional
    public int purgeAllDeletedFiles() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, oss_key FROM biz_file WHERE deleted = 1");
        int purged = purgeFileRows(rows);
        if (purged > 0) {
            writeAuditLog("清空文件回收站", "biz_file", null, "手动清空" + purged + "个文件");
        }
        return purged;
    }

    private int purgeFileRows(List<Map<String, Object>> rows) {
        int purged = 0;
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("id");
            if (idObj == null) continue;
            Object keyObj = row.get("oss_key");
            if (keyObj != null) {
                fileService.deleteStoredContent(String.valueOf(keyObj));
            }
            purged += jdbc.update("DELETE FROM biz_file WHERE id = ? AND deleted = 1",
                    ((Number) idObj).longValue());
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
}
