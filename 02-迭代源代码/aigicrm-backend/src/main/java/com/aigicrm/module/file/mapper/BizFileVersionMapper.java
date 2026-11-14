package com.aigicrm.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.file.entity.BizFileVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface BizFileVersionMapper extends BaseMapper<BizFileVersion> {

    /** 版本列表(新→旧) */
    @Select("SELECT * FROM biz_file_version WHERE file_id = #{fileId} ORDER BY version_no DESC")
    List<BizFileVersion> listByFileId(@Param("fileId") Long fileId);

    /** 按版本号取单版(回滚/下载) */
    @Select("SELECT * FROM biz_file_version WHERE file_id = #{fileId} AND version_no = #{versionNo} LIMIT 1")
    BizFileVersion byVersionNo(@Param("fileId") Long fileId, @Param("versionNo") Integer versionNo);

    /** 【V2026.11.14.3 裁剪引用计数】同文件内其他版本行是否仍引用该 key(是→字节不可删) */
    @Select("SELECT COUNT(*) FROM biz_file_version WHERE oss_key = #{ossKey} "
            + "AND file_id = #{fileId} AND id <> #{excludeId}")
    int countOtherRefs(@Param("ossKey") String ossKey, @Param("fileId") Long fileId,
                       @Param("excludeId") Long excludeId);
}
