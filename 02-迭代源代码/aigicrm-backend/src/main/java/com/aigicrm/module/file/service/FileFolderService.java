package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件夹写侧：建夹/改名/软删，以及法布施系统夹判定。
 *
 * <p>S5.3 从 {@link FileService} 抽出。文件夹树查询/可见性仍留在 FileService
 * （依赖可见夹集合与文件计数），本类只负责写侧与夹级权限判定。</p>
 */
@Service
@RequiredArgsConstructor
public class FileFolderService {

    private static final String LIT_NO_PERMISSION = "没有访问权限，请联系管理员授权";
    private static final String LIT_PUBLIC = "public";
    private static final String LIT_PRIVATE = "private";

    private final BizFileFolderMapper folderMapper;
    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final FolderPermissionService folderPermissionService;

    /** 【FILE-FABU-FOLDER-001】folderId 是否法布施系统夹(folder_type='fabushi')。 */
    boolean isFabushiSystemFolder(Long folderId) {
        if (folderId == null) return false;
        BizFileFolder f = folderMapper.selectById(folderId);
        return f != null && FabushiFolderService.FOLDER_TYPE.equals(f.getFolderType());
    }

    public void createFolder(BizFileFolder folder) {
        String type = folder.getFolderType();
        // 【安全修复 FEAT-20260804-FILE-AUTHZ】folderType 白名单校验，避免传入未识别类型
        // 绕过 checkFolderManagePermission 的默认拒绝逻辑（此前会被后续接口当成"未识别类型"处理）。
        if (!LIT_PUBLIC.equals(type) && !LIT_PRIVATE.equals(type)) {
            throw new BusinessException("不支持的文件夹类型");
        }
        // 【FILE-FABU-FOLDER-001】系统夹禁建子夹(folder_type='fabushi' 下不允许任何用户建夹)
        if (folder.getParentId() != null && isFabushiSystemFolder(folder.getParentId())) {
            throw new BusinessException(403, "法布施系统文件夹下不允许创建子文件夹");
        }
        // 【体检 H3 2026-10-12】parentId=0 与 null 同为根级（0 是 DDL 哨兵，buildFolderTree 以 0 为根）。
        // PC(CreateFolderDialog selectedFolderId||0) 与 MP(建夹恒传 0) 均传 0，此前 0 被当真实父夹
        // 做 P_UPLOAD 校验（selectById(0)=null→403），非 admin 建根夹恒败。归一化后走根级规则：
        // 私人根免门槛，公共根保留管理员门槛；insert 省略 null 字段落列默认 0，与存量根夹一致。
        if (folder.getParentId() != null && folder.getParentId() == 0L) {
            folder.setParentId(null);
        }
        // 【文件权限体系】创建子文件夹需父文件夹上传权限（管理员/本人/上级链/ACL upload），
        // public 类型带 parentId 同样按父夹 P_UPLOAD 判定（ACL 授权角色可在已授权公共夹下建子夹）；
        // 根级公共文件夹（无 parentId）保留管理员门槛。
        if (folder.getParentId() != null
                && !folderPermissionService.check(folder.getParentId(), FolderPermissionService.P_UPLOAD, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, LIT_NO_PERMISSION);
        }
        // 【2026-11-10 文件权限终版】建夹门槛=判定链 canCreateFolder(admin ∨ 例外授权含 FOLDER:create),
        // 公共/私人分列配置; 归属: owner=创建人(公共私人同, 治存量公共夹 owner 全 NULL 的结构缺失),
        // org_id=创建人当时组织快照(转岗不漂移)。
        Long creator = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.canCreateFolder(type, creator)) {
            throw new BusinessException("没有创建该类型文件夹的权限，请联系管理员授权");
        }
        folder.setOwnerId(creator);
        // 【拍板④】公共夹归属=创建人所辖最高组织(负责人建夹=给本组织开抽屉); 私人夹/非负责人=创建人组织
        Long leaderOrg = folderPermissionService.leaderOrgId(creator);
        SysUser creatorUser = userMapper.selectById(creator);
        if (LIT_PUBLIC.equals(type) && leaderOrg != null && leaderOrg > 0) {
            folder.setOrgId(leaderOrg);
        } else {
            folder.setOrgId(creatorUser != null ? java.util.Objects.requireNonNullElse(creatorUser.getOfficeId(), creatorUser.getDeptId()) : null);
        }
        folderMapper.insert(folder);
    }

    public void renameFolder(Long folderId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new BusinessException("文件夹名称不能为空");
        }
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【FILE-FABU-FOLDER-001】系统夹禁改名
        if (FabushiFolderService.FOLDER_TYPE.equals(folder.getFolderType())) {
            throw new BusinessException(403, "法布施系统文件夹不允许改名");
        }
        // 【文件权限 v2】文件夹改名走独立动作 rename(从 edit 伞拆出); 判定链 admin>本人>上级链>ACL>类型授权
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_RENAME, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, LIT_NO_PERMISSION);
        }
        folder.setFolderName(newName.trim());
        folderMapper.updateById(folder);
    }

    @Transactional
    public void deleteFolder(Long folderId) {
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        // 【FILE-FABU-FOLDER-001】系统夹禁删(书籍/分享心得出入口, 删除=两端失联)
        if (FabushiFolderService.FOLDER_TYPE.equals(folder.getFolderType())) {
            throw new BusinessException(403, "法布施系统文件夹不允许删除");
        }
        // 【文件权限体系】ACL/上级链/本人 删除权限（管理员恒通过），
        // 为操作唯一仲裁（已覆盖 admin>本人>上级链>ACL 优先级且 folder==null 已拒绝）
        if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_DELETE, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, LIT_NO_PERMISSION);
        }

        // 夹内有文件 → 拒绝删除
        long fileCount = fileMapper.selectCount(
                new LambdaQueryWrapper<BizFile>()
                        .eq(BizFile::getFolderId, folderId)
                        .eq(BizFile::getDeleted, 0));
        if (fileCount > 0) {
            throw new BusinessException("文件夹内还有文件，请先清空后再删除");
        }
        // 含子文件夹 → 拒绝删除
        long subCount = folderMapper.selectCount(
                new LambdaQueryWrapper<BizFileFolder>().eq(BizFileFolder::getParentId, folderId));
        if (subCount > 0) {
            throw new BusinessException("文件夹内还有子文件夹，请先清空后再删除");
        }

        // 【V2026.11.14.1 回收站补全】夹删除改软删(此前物理删, 误删无救): @TableLogic 令
        // deleteById=UPDATE deleted=1, MP 全查询自动过滤已删夹(树/列表/子夹计数零改动)。
        // ACL 与类型授权行**保留**(授权跟随夹生命周期——恢复即原授权生效; 彻底删除时才清 ACL)。
        // deleted_at/by 显式填(我的回收站归属口径), deleteById 只翻 deleted 标志。
        folderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFileFolder>()
                .eq(BizFileFolder::getId, folderId)
                .set(BizFileFolder::getDeletedAt, java.time.LocalDateTime.now())
                .set(BizFileFolder::getDeletedBy, SecurityUtils.getCurrentUserId()));
        folderMapper.deleteById(folderId);
    }
}
