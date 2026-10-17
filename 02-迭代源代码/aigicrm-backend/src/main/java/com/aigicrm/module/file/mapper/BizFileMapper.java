package com.aigicrm.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.file.entity.BizFile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface BizFileMapper extends BaseMapper<BizFile> {

    // ===== 【回收站 2026-10-18】已删除文件(显式 deleted=1 绕过 @TableLogic) =====

    @Select("SELECT * FROM biz_file WHERE deleted = 1 "
            + "AND (file_name LIKE #{pattern} OR oss_key LIKE #{pattern}) "
            + "ORDER BY updated_at DESC LIMIT #{offset}, #{size}")
    List<BizFile> selectDeletedPage(@Param("pattern") String pattern,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    @Select("SELECT COUNT(*) FROM biz_file WHERE deleted = 1 "
            + "AND (file_name LIKE #{pattern} OR oss_key LIKE #{pattern})")
    long countDeleted(@Param("pattern") String pattern);

    /** 含已删除行按 id 取(selectById 被 @TableLogic 过滤, 恢复流程需读原行) */
    @Select("SELECT * FROM biz_file WHERE id = #{id}")
    BizFile selectByIdIncludingDeleted(@Param("id") Long id);

    /** 恢复文件元数据(只更新 deleted=1 的行, 并发重复恢复返回 0) */
    @Update("UPDATE biz_file SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreDeleted(@Param("id") Long id);
}
