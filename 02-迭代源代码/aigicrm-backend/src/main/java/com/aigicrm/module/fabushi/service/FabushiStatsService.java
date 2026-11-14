package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.fabushi.mapper.*;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 法布施团队/个人统计(一期 2026-11-15, 技术设计方案 §4.2 统计端点)。
 *
 * 数据范围收口(WP7 双轨入口): resolveModuleUsers(user, "fabushi", "stats_view")——
 *   null = 全部档(契约语义); 空集 = 不可见 → 返回零数据(等价 1=0, fail-closed)。
 * 【架构评审 R2 教训落地 2026-11-13】本服务是 fail-closed 哨兵样板: 不写 "user==null→null(全量)"
 * 与 "ownerId==null→可见" 的 fail-open 家族——user 缺席只在未认证场景, 端点已有 authenticated 门,
 * 走不到这里; 走到了(定时任务误用)宁可返回零数据也不放行全量。
 */
@Service
@RequiredArgsConstructor
public class FabushiStatsService {

    private final OrgScopeResolver scopeResolver;
    private final BizFabushiPostMapper postMapper;
    private final BizFabushiCommentMapper commentMapper;
    private final BizFabushiLikeMapper likeMapper;
    private final BizFabushiReadStatusMapper readStatusMapper;

    /**
     * 团队统计可见人员集。admin → null(全量); 非 admin → 双轨解析:
     * null=全部档(契约), 空集=不可见(fail-closed→零数据), 非空=收口过滤。
     */
    private Set<Long> visibleUserIdsOrNull(com.aigicrm.security.SecurityUser user) {
        if (user == null) {
            return Collections.emptySet(); // fail-closed: 无会话宁可零数据也不放行全量(R2 教训)
        }
        if (com.aigicrm.security.SecurityUtils.isAdmin()) {
            return null;
        }
        List<Long> visible = scopeResolver.resolveModuleUsers(user, "fabushi", "stats_view");
        if (visible == null) {
            return null; // 双轨解析=全部档(WP7 契约语义, 非 fail-open: 该用户范围档本就是 all)
        }
        return new HashSet<>(visible);
    }

    /** 团队统计(fabushi:stats:view 门): 今日已读人数/覆盖率/内容量/木鱼量/评论量。 */
    public Map<String, Object> team() {
        Set<Long> visible = visibleUserIdsOrNull(com.aigicrm.security.SecurityUtils.getCurrentUser());

        Map<String, Object> data = new LinkedHashMap<>();
        // 可见人数: null=全部(标记 -1, 前端显示"全部"); 空集=0(不可见, 全指标零)
        data.put("memberCount", visible == null ? -1 : visible.size());

        long totalPosts;
        long likesTotal;
        long commentsTotal;
        long todayReadUsers;

        if (visible != null && visible.isEmpty()) {
            // fail-closed: 不可见集 → 全指标零(等价 SQL 1=0), 不发查询
            totalPosts = likesTotal = commentsTotal = todayReadUsers = 0;
        } else {
            LambdaQueryWrapper<BizFabushiPost> pq = new LambdaQueryWrapper<BizFabushiPost>()
                    .eq(BizFabushiPost::getStatus, "published");
            if (visible != null) {
                pq.in(BizFabushiPost::getUserId, visible);
            }
            totalPosts = postMapper.selectCount(pq);

            LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiLike> lq =
                    new LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiLike>();
            if (visible != null) {
                lq.in(com.aigicrm.module.fabushi.entity.BizFabushiLike::getUserId, visible);
            }
            likesTotal = likeMapper.selectCount(lq);

            LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiComment> cq =
                    new LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiComment>()
                            .eq(com.aigicrm.module.fabushi.entity.BizFabushiComment::getStatus, "published");
            if (visible != null) {
                cq.in(com.aigicrm.module.fabushi.entity.BizFabushiComment::getUserId, visible);
            }
            commentsTotal = commentMapper.selectCount(cq);

            // 今日已读人数(distinct user): 读状态按行拉当日记录内存去重(全仓内存聚合惯例, 行数=人×当日内容可控)
            LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiReadStatus> rq =
                    new LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiReadStatus>()
                            .ge(com.aigicrm.module.fabushi.entity.BizFabushiReadStatus::getReadAt, LocalDate.now().atStartOfDay());
            List<Long> todayReaders = readStatusMapper.selectList(rq).stream()
                    .map(com.aigicrm.module.fabushi.entity.BizFabushiReadStatus::getUserId)
                    .filter(uid -> visible == null || visible.contains(uid))
                    .distinct()
                    .collect(Collectors.toList());
            todayReadUsers = todayReaders.size();
        }

        data.put("totalPosts", totalPosts);
        data.put("likesTotal", likesTotal);
        data.put("commentsTotal", commentsTotal);
        data.put("todayReadUsers", todayReadUsers);
        // 覆盖率: 全部(-1)时不计百分比(前端隐藏), 否则 今日已读/可见人数
        data.put("coveragePct", (visible == null || visible.isEmpty())
                ? null
                : Math.round(todayReadUsers * 1000.0 / visible.size()) / 10.0);
        return data;
    }

    /** 个人统计(fabushi:view 门): 本人内容/获木鱼/评论/阅读数(本人维度无需范围收口)。 */
    public Map<String, Object> personal() {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        Long uid = user == null ? null : user.getUserId();

        Map<String, Object> data = new LinkedHashMap<>();
        if (uid == null) {
            data.put("myPosts", 0);
            data.put("myComments", 0);
            data.put("myReadCount", 0);
            data.put("myLikesReceived", 0);
            return data;
        }

        data.put("myPosts", postMapper.selectCount(new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getUserId, uid).eq(BizFabushiPost::getStatus, "published")));

        data.put("myComments", commentMapper.selectCount(new LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiComment>()
                .eq(com.aigicrm.module.fabushi.entity.BizFabushiComment::getUserId, uid)
                .eq(com.aigicrm.module.fabushi.entity.BizFabushiComment::getStatus, "published")));

        data.put("myReadCount", readStatusMapper.selectCount(new LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiReadStatus>()
                .eq(com.aigicrm.module.fabushi.entity.BizFabushiReadStatus::getUserId, uid)));

        // 获木鱼: 本人已发布内容的木鱼总数(先取本人 post ids, 再 count likes in ids)
        List<Long> myPostIds = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                        .eq(BizFabushiPost::getUserId, uid).eq(BizFabushiPost::getStatus, "published")
                        .select(BizFabushiPost::getId))
                .stream().map(BizFabushiPost::getId).collect(Collectors.toList());
        data.put("myLikesReceived", myPostIds.isEmpty() ? 0
                : likeMapper.selectCount(new LambdaQueryWrapper<com.aigicrm.module.fabushi.entity.BizFabushiLike>()
                        .in(com.aigicrm.module.fabushi.entity.BizFabushiLike::getPostId, myPostIds)));
        return data;
    }
}
