package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RecycleBinService;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 文件删除（进回收站）与改名。
 *
 * <p>S5.3 从 {@link FileService} 抽出：删除涉及权限/激励佐证/回收站/审计/配额/缩略图失效，
 * 改名涉及 P_RENAME 与扩展名铁律。文件列表/上传/版本等不在此类。</p>
 */
@Service
@RequiredArgsConstructor
public class FileMutationService {

    private static final Logger log = LoggerFactory.getLogger(FileMutationService.class);

    private static final String LIT_PUBLIC = "public";
    private static final String LIT_PRIVATE = "private";
    private static final String LIT_INCENTIVE = "incentive";
    private static final String LIT_FABUSHI = "fabushi";
    private static final String LIT_X = "没有访问权限，请联系管理员授权";
    private static final String LIT_X_2 = "文件不存在";

    private final BizFileMapper fileMapper;
    private final FolderPermissionService folderPermissionService;
    private final SecurityAuditService securityAudit;
    private final IncentiveService incentiveService;
    private final ObjectProvider<RecycleBinService> recycleBinServiceProvider;
    private final FileThumbnailService fileThumbnailService;
    private final SysUserMapper userMapper;

    /**
     * 删除文件（进入回收站）。
     * 【审查 backend-5 2026-10-18】补事务 + 以逻辑删影响行数为唯一退款凭据：
     * 旧实现 selectById→deleteById(丢弃返回值)→无条件 adjustStorageUsed，两个并发删除
     * 都能读到同一行并各退一次配额(重复归还)。现在只有 deleted=0 行被本次请求真正删除
     * (affected==1)才归还；affected==0 = 重复/并发删除，幂等返回成功且不二次退款。
     */
    private void assertCanDeleteFile(Long fileId, BizFile file, Long userId, String scope) {
        if (file.getFolderId() != null) {
            if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_DELETE, userId)) {
                throw new BusinessException(403, LIT_X);
            }
        } else if (LIT_PUBLIC.equals(scope)) {
            if (!SecurityUtils.hasAuthority("file:delete")) {
                throw new BusinessException(403, "删除公共文件需要 file:delete 权限");
            }
        } else if (LIT_PRIVATE.equals(scope) || LIT_INCENTIVE.equals(scope)) {
            if (!userId.equals(file.getUploaderId())) {
                throw new BusinessException(403, "无权删除该文件");
            }
        } else if (LIT_FABUSHI.equals(scope)) {
            if (!folderPermissionService.canDeleteFabushiFile(file, userId)) {
                throw new BusinessException(403, "删除法布殊文件需要 fabushi:file:delete 权限且在上传人数据范围内");
            }
        } else {
            throw new BusinessException(403, "无权删除该文件");
        }
    }

    @Transactional
    public void deleteFile(Long fileId) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return;

        Long userId = SecurityUtils.getCurrentUserId();
        String scope = file.getFileScope();

        // 【安全修复 FEAT-20260804-FILE-AUTHZ】此前只对 public/private 两种 scope 做归属校验，
        // incentive 等其它 scope 完全未校验，导致任何拥有 file:delete 权限的用户都能删除他人的
        // 激励文件（IDOR）。现在改为白名单放行、默认拒绝，且新增 incentive scope 的归属校验。
        // 【文件权限体系】夹内文件（folderId 非空）改按文件夹权限判定（管理员/本人/上级链/ACL delete），
        // 无文件夹文件沿用下方 scope 白名单归属校验。
        assertCanDeleteFile(fileId, file, userId, scope);

        // 【BIZ-007 2026-11-13 外部审计修复】删除激励佐证必须联动重算学分:
        // 反查引用该文件的激励记录 → 剔除槽位引用 → calcScore 重算(与记录侧 removeFile 同一套
        // 不变量与空壳守卫)。守卫抛 400 时本事务回滚、文件保留——修前"佐证已删、分数还在"。
        if (LIT_INCENTIVE.equals(scope)) {
            incentiveService.detachFileFromRecords(fileId);
        }

        // 【文件回收站 2026-10-18 用户裁决】删除不再物理清除字节: 文件进入回收站,
        // 由保留期任务(FileRecycleCleanupTask)到期或管理员手动清空时再调 deleteStoredContent。
        // 逻辑删除 + 配额归还保持原语义(私有文件删除即归还配额, 恢复时重新占用)。

        // 逻辑删除(【V2026.11.14.1】先填删除人/时间——"我的回收站"归属口径, deleteById 只翻标志)
        fileMapper.update(null, new LambdaUpdateWrapper<BizFile>()
                .eq(BizFile::getId, fileId)
                .set(BizFile::getDeletedAt, LocalDateTime.now())
                .set(BizFile::getDeletedBy, userId));
        int affected = fileMapper.deleteById(fileId);
        if (affected != 1) {
            // 并发/重复删除: 台账已不在活跃态, 不得二次归还配额; 幂等返回成功(前端不报错)
            return;
        }
        RecycleBinService recycleBinService = recycleBinServiceProvider == null
                ? null : recycleBinServiceProvider.getIfAvailable();
        if (recycleBinService != null) {
            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("id", fileId);
            snap.put("fileName", file.getFileName());
            snap.put("fileSize", file.getFileSize());
            snap.put("fileScope", file.getFileScope());
            snap.put("uploaderId", file.getUploaderId());
            snap.put("folderId", file.getFolderId());
            recycleBinService.record("file", fileId, snap, "用户删除", "user");
        }
        // 【2026-11-10 拍板③】删除审计(高敏动作, 失败不阻断)
        try {
            securityAudit.permissionChange("file_delete",
                    "file:" + fileId + " name:" + file.getFileName() + " scope:" + file.getFileScope()
                            + " by:" + SecurityUtils.getCurrentUsername());
        } catch (Exception e) {
            log.warn("权限审计留痕失败(不阻断业务) file_delete fileId={}", fileId, e);
        }
        // 【性能 file-3】删除后清掉该文件所有版本的缩略图磁盘缓存（逻辑删只保留原图供回收站）
        fileThumbnailService.evictThumbnailCache(fileId);

        // 【P2 2026-10-09 配额并发修复】归还改数据库端原子增减, 消除读改写丢失更新
        // 【T2 2026-11-11】归还覆盖全 scope(与扣减对称); 历史脏 scope 行不还
        if (FileService.quotaBearing(file.getFileScope())
                && file.getFileSize() != null && file.getUploaderId() != null) {
            userMapper.adjustStorageUsed(file.getUploaderId(), -file.getFileSize());
        }
    }

    /**
     * 【2026-11-06 用户令"加一个可以修改文件名称的"】文件重命名: 只改 file_name 展示名,
     * oss_key/oss_url/file_type/version 不动——物理对象与下载路由零变化。
     * 【2026-11-09 文件权限 v2】动作细分: 改名走独立动作 rename(全局码仍 file:edit 伞),
     * 判定链=管理员/本人/上级链/ACL/类型授权(FolderPermissionService.checkFile P_RENAME,
     * 存量 ACL 的 edit 授权由判定层读时展开, 老授权继续有效)。
     * 校验: 去首尾空格非空、≤200 字符(与 biz_file.file_name 同宽); 不做扩展名强约束
     * (类型字段沿用上传时白名单结果, 改名不重算类型)。
     */
    public void renameFile(Long fileId, String fileName) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        if (LIT_FABUSHI.equals(file.getFileScope())) {
            throw new BusinessException(403, "法布施文件只支持查看/下载/删除，不支持改名");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_RENAME, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty()) throw new BusinessException("文件名不能为空");
        if (name.length() > 200) throw new BusinessException("文件名过长（不超过 200 字符）");
        // 【2026-11-08 用户令"改名不更改格式"】扩展名铁律守卫: 无论用户输入是否带/换扩展名,
        // 一律剥掉输入中的扩展名后拼回原扩展名——photo.jpg → 输入"photo2"/"photo2.png"均得 photo2.jpg。
        // 原名无扩展名则保持无扩展; 主名被剥空(只输了个扩展名)按"文件名不能为空"拒绝。
        name = keepOriginalExtension(name, file.getFileName());
        if (name.equals(file.getFileName())) return;
        BizFile patch = new BizFile();
        patch.setId(fileId);
        patch.setFileName(name);
        fileMapper.updateById(patch);
    }

    /** 改名保格式: 剥掉输入自带的后缀, 拼回原名扩展名。 */
    private static String keepOriginalExtension(String input, String originalName) {
        String originalExt = FileNames.extOf(originalName); // null=原名无扩展名
        String inputExt = FileNames.extOf(input);
        String main = input;
        if (inputExt != null) {
            // 输入带了后缀(与原一致或想换格式): 一律剥掉, 稍后统一拼回原扩展名
            main = input.substring(0, input.length() - inputExt.length() - 1);
        }
        if (main.isBlank()) throw new BusinessException("文件名不能为空");
        return originalExt == null ? main : main + "." + originalExt;
    }
}
