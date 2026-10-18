package com.aigicrm.module.file.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.file.entity.BizFile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface BizFileMapper extends BaseMapper<BizFile> {

    /**
     * 【P0 2026-10-17 匿名下载 404】按 oss_key 查询 public 文件元数据。
     * 匿名请求无登录态/集团上下文, 租户拦截器会把查询改写成 tenant_id = NULL 恒空
     * （见 MybatisPlusConfig.buildTenantInterceptor）, 导致公共下载全部 404。
     * 公共文件按"公开范围"授权, 不做租户隔离; SQL 显式收口 file_scope='public' + deleted=0,
     * 避免绕过拦截器后读出私密/已删除行。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM biz_file WHERE deleted = 0 AND file_scope = 'public' AND oss_key = #{key} LIMIT 1")
    BizFile selectPublicByKeyIgnoreTenant(@Param("key") String key);

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

    /**
     * 【审查 biz-2 2026-10-18】原文件夹已删除时恢复到根目录:
     * 文件夹是硬删除且 ID 不会复用, 保留旧 folder_id 会让恢复出的文件挂在不存在目录下;
     * 同一条件更新内清空 folder_id, 原子恢复且不要求用户重建旧 ID。
     */
    @Update("UPDATE biz_file SET deleted = 0, folder_id = NULL WHERE id = #{id} AND deleted = 1")
    int restoreDeletedToRoot(@Param("id") Long id);
}
