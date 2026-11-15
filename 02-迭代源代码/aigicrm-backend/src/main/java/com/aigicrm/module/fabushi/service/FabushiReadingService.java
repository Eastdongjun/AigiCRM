package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.*;
import com.aigicrm.module.fabushi.mapper.*;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** 二期阅读链：书架 / PDF 进度 / 阅读记录 / 提醒设置（本人维度）。 */
@Service
@RequiredArgsConstructor
public class FabushiReadingService {
    private final BizFabushiShelfMapper shelfMapper;
    private final BizFabushiReadingProgressMapper progressMapper;
    private final BizFabushiReadStatusMapper readStatusMapper;
    private final BizFabushiPostMapper postMapper;
    private final BizFabushiBookMapper bookMapper;
    private final BizFabushiUserSettingMapper settingMapper;

    private Long uid() {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new BusinessException(401, "未登录");
        return id;
    }

    public Map<String, Object> shelfData() {
        Long uid = uid();
        List<BizFabushiShelf> rows = shelfMapper.selectList(new LambdaQueryWrapper<BizFabushiShelf>()
                .eq(BizFabushiShelf::getUserId, uid).orderByDesc(BizFabushiShelf::getUpdatedAt));
        if (rows.isEmpty()) return Map.of("records", List.of());
        Set<Long> bookIds = rows.stream().map(BizFabushiShelf::getBookId).collect(Collectors.toSet());
        Map<Long, BizFabushiBook> books = bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(BizFabushiBook::getId, b -> b));
        Map<Long, BizFabushiReadingProgress> progresses = progressMapper.selectList(new LambdaQueryWrapper<BizFabushiReadingProgress>()
                        .eq(BizFabushiReadingProgress::getUserId, uid).in(BizFabushiReadingProgress::getBookId, bookIds))
                .stream().collect(Collectors.toMap(BizFabushiReadingProgress::getBookId, p -> p));
        List<Map<String, Object>> records = rows.stream().map(row -> {
            BizFabushiBook b = books.get(row.getBookId());
            BizFabushiReadingProgress p = progresses.get(row.getBookId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.getId());
            m.put("bookId", row.getBookId());
            m.put("title", b == null ? "已下架书目" : b.getTitle());
            m.put("author", b == null ? null : b.getAuthor());
            m.put("pages", b == null ? null : b.getPages());
            m.put("pdfFileId", b == null ? null : b.getPdfFileId());
            m.put("status", row.getStatus());
            m.put("lastSectionId", row.getLastSectionId());
            m.put("lastPage", p == null ? 1 : p.getLastPage());
            m.put("totalPages", p == null ? null : p.getTotalPages());
            return m;
        }).collect(Collectors.toList());
        return Map.of("records", records);
    }

    @Transactional
    public void addShelf(Long bookId) {
        if (bookId == null) throw new BusinessException(400, "书目不能为空");
        BizFabushiBook book = bookMapper.selectById(bookId);
        if (book == null || !"approved".equals(book.getStatus())) throw new BusinessException(404, "书目不存在或未通过审核");
        Long uid = uid();
        Long exists = shelfMapper.selectCount(new LambdaQueryWrapper<BizFabushiShelf>()
                .eq(BizFabushiShelf::getUserId, uid).eq(BizFabushiShelf::getBookId, bookId));
        if (exists > 0) return;
        BizFabushiShelf row = new BizFabushiShelf();
        row.setUserId(uid); row.setBookId(bookId); row.setStatus("reading");
        shelfMapper.insert(row);
    }

    @Transactional
    public void updateShelf(Long id, Map<String, Object> body) {
        BizFabushiShelf row = shelfMapper.selectById(id);
        if (row == null || !uid().equals(row.getUserId())) throw new BusinessException(404, "书架记录不存在");
        String status = body == null ? null : String.valueOf(body.getOrDefault("status", ""));
        if (!Set.of("reading", "finished", "favorite").contains(status)) throw new BusinessException(400, "书架状态不合法");
        row.setStatus(status);
        if (body.get("lastSectionId") != null) row.setLastSectionId(Long.valueOf(String.valueOf(body.get("lastSectionId"))));
        shelfMapper.updateById(row);
    }

    @Transactional
    public void removeShelf(Long id) {
        BizFabushiShelf row = shelfMapper.selectById(id);
        if (row == null || !uid().equals(row.getUserId())) throw new BusinessException(404, "书架记录不存在");
        shelfMapper.deleteById(id);
    }

    public Map<String, Object> progress(Long bookId) {
        Long uid = uid();
        BizFabushiReadingProgress row = progressMapper.selectOne(new LambdaQueryWrapper<BizFabushiReadingProgress>()
                .eq(BizFabushiReadingProgress::getUserId, uid).eq(BizFabushiReadingProgress::getBookId, bookId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookId", bookId);
        m.put("lastPage", row == null || row.getLastPage() == null ? 1 : row.getLastPage());
        m.put("totalPages", row == null ? null : row.getTotalPages());
        return m;
    }

    @Transactional
    public void saveProgress(Long bookId, Map<String, Object> body) {
        Long uid = uid();
        int lastPage = body != null && body.get("lastPage") != null ? Integer.parseInt(String.valueOf(body.get("lastPage"))) : 1;
        if (lastPage < 1) throw new BusinessException(400, "页码不合法");
        Integer totalPages = body != null && body.get("totalPages") != null ? Integer.parseInt(String.valueOf(body.get("totalPages"))) : null;
        BizFabushiReadingProgress row = progressMapper.selectOne(new LambdaQueryWrapper<BizFabushiReadingProgress>()
                .eq(BizFabushiReadingProgress::getUserId, uid).eq(BizFabushiReadingProgress::getBookId, bookId));
        if (row == null) {
            row = new BizFabushiReadingProgress();
            row.setUserId(uid); row.setBookId(bookId); row.setLastPage(lastPage); row.setTotalPages(totalPages);
            progressMapper.insert(row);
        } else {
            row.setLastPage(lastPage); row.setTotalPages(totalPages); progressMapper.updateById(row);
        }
    }

    public Map<String, Object> records(String month) {
        Long uid = uid();
        YearMonth ym;
        try { ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month); }
        catch (Exception e) { throw new BusinessException(400, "月份格式应为 YYYY-MM"); }
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();
        List<BizFabushiReadStatus> rows = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .eq(BizFabushiReadStatus::getUserId, uid)
                .ge(BizFabushiReadStatus::getReadAt, from)
                .lt(BizFabushiReadStatus::getReadAt, to)
                .orderByDesc(BizFabushiReadStatus::getReadAt));
        Map<LocalDate, Long> counts = rows.stream().collect(Collectors.groupingBy(r -> r.getReadAt().toLocalDate(), TreeMap::new, Collectors.counting()));
        List<Map<String, Object>> days = counts.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey().toString()); m.put("count", e.getValue()); return m;
        }).collect(Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", ym.toString());
        out.put("days", days);
        out.put("readDays", days.size());
        out.put("readCount", rows.size());
        return out;
    }

    public Map<String, Object> settings() {
        Long uid = uid();
        BizFabushiUserSetting row = settingMapper.selectOne(new LambdaQueryWrapper<BizFabushiUserSetting>()
                .eq(BizFabushiUserSetting::getUserId, uid));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reminderEnabled", row == null ? 0 : row.getReminderEnabled());
        m.put("freqCode", row == null ? "daily" : row.getFreqCode());
        m.put("remindTime", row == null ? "20:00" : row.getRemindTime());
        m.put("soundEnabled", row == null ? 1 : row.getSoundEnabled());
        return m;
    }

    @Transactional
    public void saveSettings(Map<String, Object> body) {
        Long uid = uid();
        String freq = body != null && body.get("freqCode") != null ? String.valueOf(body.get("freqCode")) : "daily";
        if (!Set.of("daily", "weekday", "weekend").contains(freq)) throw new BusinessException(400, "提醒频率不合法");
        String time = body != null && body.get("remindTime") != null ? String.valueOf(body.get("remindTime")) : "20:00";
        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) throw new BusinessException(400, "提醒时间不合法");
        int enabled = body != null && body.get("reminderEnabled") != null ? Integer.parseInt(String.valueOf(body.get("reminderEnabled"))) : 0;
        int sound = body != null && body.get("soundEnabled") != null ? Integer.parseInt(String.valueOf(body.get("soundEnabled"))) : 1;
        BizFabushiUserSetting row = settingMapper.selectOne(new LambdaQueryWrapper<BizFabushiUserSetting>()
                .eq(BizFabushiUserSetting::getUserId, uid));
        if (row == null) {
            row = new BizFabushiUserSetting();
            row.setUserId(uid); row.setReminderEnabled(enabled); row.setFreqCode(freq); row.setRemindTime(time); row.setSoundEnabled(sound);
            settingMapper.insert(row);
        } else {
            row.setReminderEnabled(enabled); row.setFreqCode(freq); row.setRemindTime(time); row.setSoundEnabled(sound);
            settingMapper.updateById(row);
        }
    }
}
