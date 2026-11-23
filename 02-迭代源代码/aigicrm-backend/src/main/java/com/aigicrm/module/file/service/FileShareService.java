package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.GroupScopeUtil;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileShare;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件分享：分享给指定用户、我收到的分享列表/计数。
 *
 * <p>S5.3 从 {@link FileService} 抽出的第一块。原先与上传/下载/版本/缩略图
 * 混在同一个 2285 行类里；这里只保留分享语义，权限判定仍走
 * {@link FolderPermissionService}，集团边界仍走 {@link GroupScopeUtil}。</p>
 */
@Service
@RequiredArgsConstructor
public class FileShareService {

    private static final String LIT_NO_PERMISSION = "没有访问权限，请联系管理员授权";
    private static final String LIT_FILE_NOT_FOUND = "文件不存在";

    private final BizFileMapper fileMapper;
    private final BizFileShareMapper fileShareMapper;
    private final SysUserMapper userMapper;
    private final FolderPermissionService folderPermissionService;
    private final JdbcTemplate jdbc;

    public void shareFile(Long fileId, List<Long> userIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 只有文件上传者或管理员可以分享
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_FILE_NOT_FOUND);
        // 【文件权限体系】夹内文件按文件夹权限判定分享（管理员/本人/上级链/ACL share）；
        // 无文件夹文件沿用上传者归属校验
        if (file.getFolderId() != null) {
            if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_SHARE, currentUserId)) {
                throw new BusinessException(403, LIT_NO_PERMISSION);
            }
        } else if (!currentUserId.equals(file.getUploaderId())) {
            throw new BusinessException(403, "无权分享该文件");
        }
        // 【2026-10-27 深度检查修复】文件共享不得绕过集团边界：
        // 跨集团分享会写入对方永远看不到的死行（biz_file_share 被租户拦截器按查看者集团改写）。
        if (!userIds.isEmpty()) {
            Long group = GroupScopeUtil.currentGroupOrNull();
            if (group != null) {
                List<Long> groupMembers = GroupScopeUtil.userIdsInGroup(jdbc, group);
                List<Long> foreign = userIds.stream().filter(uid -> !groupMembers.contains(uid)).toList();
                if (!foreign.isEmpty()) {
                    throw new BusinessException("不能共享给其他集团用户（目标用户不在当前集团）");
                }
            } else {
                throw new BusinessException(403, "无有效集团归属，禁止共享");
            }
        }
        for (Long uid : userIds) {
            Long count = fileShareMapper.selectCount(
                    new LambdaQueryWrapper<BizFileShare>()
                            .eq(BizFileShare::getFileId, fileId)
                            .eq(BizFileShare::getSharedToUserId, uid));
            if (count == 0) {
                BizFileShare share = new BizFileShare();
                share.setFileId(fileId);
                share.setSharedToUserId(uid);
                share.setSharedByUserId(currentUserId);
                fileShareMapper.insert(share);
            }
        }
    }

    public IPage<BizFile> pageSharedToMe(int current, int size, String keyword) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BizFileShare> shares = fileShareMapper.selectList(
                new LambdaQueryWrapper<BizFileShare>().eq(BizFileShare::getSharedToUserId, userId));
        List<Long> fileIds = shares.stream().map(BizFileShare::getFileId).toList();
        if (fileIds.isEmpty()) return new Page<>(current, size, 0);

        LambdaQueryWrapper<BizFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizFile::getId, fileIds).eq(BizFile::getDeleted, 0);
        FileQuerySupport.applyGlobalKeyword(wrapper, keyword);
        wrapper.orderByDesc(BizFile::getCreatedAt);
        IPage<BizFile> page = fileMapper.selectPage(new Page<>(current, size), wrapper);
        for (BizFile f : page.getRecords()) {
            if (f.getUploaderId() != null) {
                SysUser user = userMapper.selectById(f.getUploaderId());
                if (user != null) f.setUploaderName(user.getRealName());
            }
        }
        return page;
    }

    public long countSharedToMe() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BizFileShare> shares = fileShareMapper.selectList(
                new LambdaQueryWrapper<BizFileShare>().eq(BizFileShare::getSharedToUserId, userId));
        if (shares.isEmpty()) return 0;
        List<Long> fileIds = shares.stream().map(BizFileShare::getFileId).toList();
        return fileMapper.selectCount(new LambdaQueryWrapper<BizFile>()
                .in(BizFile::getId, fileIds)
                .eq(BizFile::getDeleted, 0));
    }
}
