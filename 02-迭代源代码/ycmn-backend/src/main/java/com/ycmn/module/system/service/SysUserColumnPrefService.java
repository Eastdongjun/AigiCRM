package com.ycmn.module.system.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysUserColumnPref;
import com.ycmn.module.system.mapper.SysUserColumnPrefMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户表格列显示偏好服务（按人持久化，跨浏览器/设备生效）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserColumnPrefService {

    private final SysUserColumnPrefMapper columnPrefMapper;

    /**
     * 读取某用户某页面的列偏好
     *
     * 【需求】表格"列设置"功能：用户自定义显示列，换浏览器仍生效
     * 【方法】按 (user_id, page_key) 唯一键查一行，columns 字段存 JSON 数组字符串
     * 【业务规则】无记录返回 null（前端使用默认全显列）
     * 【参数】userId 用户ID；pageKey 页面标识（如 incentive-record）
     * 【返回】显示列 key 的 JSON 数组字符串；无记录 null
     * 【注意】只读；不做权限校验（本人偏好，controller 层已取当前登录人）
     */
    public String getColumns(Long userId, String pageKey) {
        SysUserColumnPref pref = columnPrefMapper.selectOne(
                new LambdaQueryWrapper<SysUserColumnPref>()
                        .eq(SysUserColumnPref::getUserId, userId)
                        .eq(SysUserColumnPref::getPageKey, pageKey)
                        .last("LIMIT 1"));
        return pref != null ? pref.getColumns() : null;
    }

    /**
     * 保存某用户某页面的列偏好（存在则更新，不存在则插入）
     *
     * 【需求】表格"列设置"保存
     * 【方法】按 (user_id, page_key) 查已有行，有则 updateById，无则 insert（唯一键兜底并发）
     * 【业务规则】columns 传 JSON 数组字符串；空数组表示全部隐藏（前端会兜底保留操作列）
     * 【参数】userId 用户ID；pageKey 页面标识；columns JSON 数组字符串
     * 【返回】无
     * 【注意】唯一键 uk_user_page 保证每人每页一行；并发下 insert 撞唯一键抛异常由前端重试
     */
    public void saveColumns(Long userId, String pageKey, String columns) {
        SysUserColumnPref existing = columnPrefMapper.selectOne(
                new LambdaQueryWrapper<SysUserColumnPref>()
                        .eq(SysUserColumnPref::getUserId, userId)
                        .eq(SysUserColumnPref::getPageKey, pageKey)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setColumns(columns);
            columnPrefMapper.updateById(existing);
        } else {
            SysUserColumnPref pref = new SysUserColumnPref();
            pref.setUserId(userId);
            pref.setPageKey(pageKey);
            pref.setColumns(columns);
            columnPrefMapper.insert(pref);
        }
        log.info("列偏好已保存: userId={}, pageKey={}, columns={}", userId, pageKey, columns);
    }
}
