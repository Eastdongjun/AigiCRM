package com.aigicrm.module.fabushi.mapper;

import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BizFabushiPostMapper extends BaseMapper<BizFabushiPost> {

    /**
     * 【D13 2026-11-23】治理侧"已删除"分页：逻辑删除(@TableLogic)会使常规查询强制滤 deleted=0,
     * 已删帖列表必须走自定义 SQL 绕过。仅 content:manage 门消费（service 层判定）。
     *
     * 【SMOKE-20261123 F1】原签名收一个 Wrapper 并拼 ${ew.customSqlSegment}，而 service 传的是 null
     * ⇒ 片段为空 ⇒ 生成的 SQL 没有 WHERE ⇒「已删除」页签把全部内容都列出来
     * （2026-11-23 测试环境实测：total=6，6 条 deleted 全为 0）。
     * 改为在 SQL 内硬编码 `deleted = 1`：筛选条件不再依赖调用方传参，
     * 同时消掉 ${} 字符串拼接这一注入面。
     */
    @Select("SELECT * FROM biz_fabushi_post WHERE deleted = 1 ORDER BY updated_at DESC")
    IPage<BizFabushiPost> selectDeletedPage(Page<BizFabushiPost> page);

    /** 已删帖详情（治理弹窗）。 */
    @Select("SELECT * FROM biz_fabushi_post WHERE id = #{id} AND deleted = 1")
    BizFabushiPost selectDeletedById(@Param("id") Long id);

    /** 恢复已删帖：逻辑删 where 条件会拦住 LambdaUpdate（WHERE deleted=0 恒假），必须自定义 SQL。 */
    @Update("UPDATE biz_fabushi_post SET deleted = 0, status = 'published' WHERE id = #{id} AND deleted = 1")
    int restoreDeleted(@Param("id") Long id);
}
