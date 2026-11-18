package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiActivity;
import com.aigicrm.module.fabushi.entity.BizFabushiActivityJoin;
import com.aigicrm.module.fabushi.mapper.BizFabushiActivityJoinMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiActivityMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 共读活动（二期）：浏览/参与=view，创建/结束=activity:manage。 */
@Service
@RequiredArgsConstructor
public class FabushiActivityService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_BOOKID = "bookId";
    private static final String LIT_TITLE = "title";
    private static final String LIT_DESCRIPTION = "description";
    private static final String LIT_STARTDATE = "startDate";
    private static final String LIT_ENDDATE = "endDate";

    private final BizFabushiActivityMapper activityMapper;
    private final BizFabushiActivityJoinMapper joinMapper;
    private final BizFabushiBookMapper bookMapper;

    public Map<String, Object> list() {
        Long uid = SecurityUtils.getCurrentUserId();
        List<BizFabushiActivity> rows = activityMapper.selectList(new LambdaQueryWrapper<BizFabushiActivity>()
                .orderByDesc(BizFabushiActivity::getStartDate).orderByDesc(BizFabushiActivity::getId));
        Set<Long> ids = rows.stream().map(BizFabushiActivity::getId).collect(Collectors.toSet());
        Set<Long> joined = ids.isEmpty() ? Set.of() : joinMapper.selectList(new LambdaQueryWrapper<BizFabushiActivityJoin>()
                        .eq(BizFabushiActivityJoin::getUserId, uid).in(BizFabushiActivityJoin::getActivityId, ids))
                .stream().map(BizFabushiActivityJoin::getActivityId).collect(Collectors.toSet());
        Set<Long> bookIds = rows.stream().map(BizFabushiActivity::getBookId).collect(Collectors.toSet());
        Map<Long, String> titles = bookIds.isEmpty() ? Collections.emptyMap() : bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> b.getTitle()));
        List<Map<String, Object>> records = rows.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put(LIT_TITLE, a.getTitle()); m.put(LIT_BOOKID, a.getBookId());
            m.put("bookTitle", titles.get(a.getBookId())); m.put(LIT_DESCRIPTION, a.getDescription());
            m.put(LIT_STARTDATE, a.getStartDate()); m.put(LIT_ENDDATE, a.getEndDate()); m.put("status", a.getStatus());
            m.put("joined", joined.contains(a.getId()));
            m.put("joinCount", joinMapper.selectCount(new LambdaQueryWrapper<BizFabushiActivityJoin>().eq(BizFabushiActivityJoin::getActivityId, a.getId())));
            return m;
        }).collect(Collectors.toList());
        return Map.of("records", records);
    }

    @Transactional
    public void join(Long activityId) {
        BizFabushiActivity a = activityMapper.selectById(activityId);
        if (a == null || !"active".equals(a.getStatus())) throw new BusinessException(404, "活动不存在或已结束");
        Long uid = SecurityUtils.getCurrentUserId();
        Long exists = joinMapper.selectCount(new LambdaQueryWrapper<BizFabushiActivityJoin>()
                .eq(BizFabushiActivityJoin::getActivityId, activityId).eq(BizFabushiActivityJoin::getUserId, uid));
        if (exists > 0) return;
        BizFabushiActivityJoin row = new BizFabushiActivityJoin();
        row.setActivityId(activityId); row.setUserId(uid); row.setJoinedAt(java.time.LocalDateTime.now());
        joinMapper.insert(row);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        String title = body == null || body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)).trim();
        Long bookId = body == null || body.get(LIT_BOOKID) == null ? null : Long.valueOf(String.valueOf(body.get(LIT_BOOKID)));
        if (title == null || title.isEmpty() || bookId == null) throw new BusinessException(400, "活动标题和书目不能为空");
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, "书目不存在");
        BizFabushiActivity a = new BizFabushiActivity();
        a.setTitle(title); a.setBookId(bookId);
        a.setDescription(body.get(LIT_DESCRIPTION) == null ? null : String.valueOf(body.get(LIT_DESCRIPTION)));
        a.setStartDate(body.get(LIT_STARTDATE) == null ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get(LIT_STARTDATE))));
        a.setEndDate(body.get(LIT_ENDDATE) == null ? LocalDate.now().plusDays(30) : LocalDate.parse(String.valueOf(body.get(LIT_ENDDATE))));
        if (a.getEndDate().isBefore(a.getStartDate())) throw new BusinessException(400, "结束日期不能早于开始日期");
        a.setStatus("active"); a.setCreatedBy(SecurityUtils.getCurrentUserId());
        activityMapper.insert(a);
        return Map.of("id", a.getId());
    }

    @Transactional
    public void end(Long id) {
        BizFabushiActivity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(404, "活动不存在");
        a.setStatus("ended"); activityMapper.updateById(a);
    }
}
