package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 【FILE-FABU-FOLDER-001 2026-11-19】法布施系统文件夹（书籍/分享心得）解析器。
 * 两夹由迁移 V2026.11.19.4 建（folder_type='fabushi', sort_order 1=书籍 2=分享心得），
 * 不可删/不可改名/不可建子夹（FileService 变更入口守卫）。fail-closed：缺失即 500，
 * 不静默回退平铺（迁移是前置）。
 */
@Service
@RequiredArgsConstructor
public class FabushiFolderService {

    public static final String FOLDER_TYPE = "fabushi";
    public static final int SORT_BOOK = 1;
    public static final int SORT_SHARE = 2;

    private final BizFileFolderMapper folderMapper;

    /** 书籍夹 id（挂书 PDF 通道唯一落点）。 */
    public Long bookFolderId() {
        return require(SORT_BOOK, "书籍");
    }

    /** 分享心得夹 id（帖子附件通道默认落点）。 */
    public Long shareFolderId() {
        return require(SORT_SHARE, "分享心得");
    }

    /** 该 id 是否法布施系统夹（upload 权限旁路/变更守卫用）。 */
    public boolean isSystemFolder(Long folderId) {
        if (folderId == null) return false;
        BizFileFolder f = folderMapper.selectById(folderId);
        return f != null && FOLDER_TYPE.equals(f.getFolderType());
    }

    private Long require(int sortOrder, String name) {
        BizFileFolder folder = folderMapper.selectOne(new LambdaQueryWrapper<BizFileFolder>()
                .eq(BizFileFolder::getFolderType, FOLDER_TYPE)
                .eq(BizFileFolder::getSortOrder, sortOrder)
                .last("LIMIT 1"));
        if (folder == null) {
            throw new BusinessException(500, "法布施系统文件夹缺失（" + name + "），请先执行迁移 V2026.11.19.4");
        }
        return folder.getId();
    }
}
