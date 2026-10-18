package com.aigicrm.module.incentive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizIncentiveRecordMapper extends BaseMapper<BizIncentiveRecord> {

    /**
     * 行锁读（SELECT ... FOR UPDATE）：removeFile 的"五槽合计≥1"守卫必须是锁定读，
     * 否则并发两事务各删不同槽的最后一份文件可双双通过守卫（工作流复检 2026-10-10 证实）。
     * 租户条件由 TenantLineInnerInterceptor 对 @Select 同样生效，保持租户隔离。
     */
    @Select("SELECT * FROM biz_incentive_record WHERE id = #{id} FOR UPDATE")
    BizIncentiveRecord selectByIdForUpdate(Long id);

    /**
     * 【性能 file-3 2026-10-18】"他人共享激励记录"轻量指纹，供 FileService 按用户缓存可见文件集合。
     * <p>过滤口径必须与加载可见集合的查询完全一致：{@code shared=1 AND user_id <> 当前用户}。
     * 返回 {@code "记录数:MAX(id):MAX(updated_at) 的 epoch 秒"}：新增/删除记录改变前两段，
     * 切换 shared 或修改五槽改变记录数与 updated_at；参数绑定，租户条件由
     * {@code MybatisPlusConfig} 的 TenantLineInnerInterceptor 照常附加。
     * <p>指纹只用于判断"是否需要重载集合"，不参与授权结论本身；加载失败/指纹为空时不使用缓存。
     */
    @Select("SELECT CONCAT(COUNT(*), ':', COALESCE(MAX(id), 0), ':', "
            + "COALESCE(UNIX_TIMESTAMP(MAX(updated_at)), 0)) "
            + "FROM biz_incentive_record WHERE shared = 1 AND user_id <> #{userId}")
    String selectSharedVisibilityFingerprint(@Param("userId") Long userId);
}
