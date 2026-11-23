package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.FabushiScopeType;
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
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** 二期阅读链：书架 / PDF 进度 / 阅读记录 / 提醒设置（本人维度）。切片 6 扩 D7 觉醒聚合 + D20/D23 书架管理。 */
@Service
@RequiredArgsConstructor
public class FabushiReadingService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_LASTSECTIONID = "lastSectionId";
    private static final String LIT_LASTPAGE = "lastPage";
    private static final String LIT_TOTALPAGES = "totalPages";
    private static final String LIT_REMINDERENABLED = "reminderEnabled";
    private static final String LIT_DAILY = "daily";
    private static final String LIT_FREQCODE = "freqCode";
    private static final String LIT_REMINDTIME = "remindTime";
    private static final String LIT_SOUNDENABLED = "soundEnabled";

    private static final String STATUS_WISH = "wish";
    private static final String STATUS_READING = "reading";
    private static final String STATUS_FINISHED = "finished";
    private static final String STATUS_FAVORITE = "favorite";
    private static final Set<String> SHELF_STATUSES = Set.of(
            STATUS_WISH, STATUS_READING, STATUS_FINISHED, STATUS_FAVORITE);

    private static final String GROUP_AUTO = "auto";
    private static final String GROUP_CUSTOM = "custom";
    // 【CODE-REVIEW-20261123 R1-03】原为裸字面量 "songdu"/"suishi"，与 FabushiScopeType 双源。
    // 改引用枚举：保留常量名不动（调用点无需改），但取值单源化，D1 再改 code 时不会静默失效。
    private static final String SCOPE_SONGDU = FabushiScopeType.SONGDU.getCode();
    private static final String SCOPE_SUISHI = FabushiScopeType.SUISHI.getCode();
    private static final String POST_PUBLISHED = "published";
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final String LIT_RECORDS = "records";
    private static final String LIT_GROUPS = "groups";
    private static final String LIT_SUMMARY = "summary";
    private static final String LIT_CURRENTBOOK = "currentBook";
    private static final String LIT_BOOKID = "bookId";
    private static final String LIT_TITLE = "title";
    private static final String LIT_AUTHOR = "author";
    private static final String LIT_PAGES = "pages";
    private static final String LIT_PDFFILEID = "pdfFileId";
    private static final String LIT_STATUS = "status";
    private static final String LIT_PINNED = "pinned";
    private static final String LIT_SORTNO = "sortNo";
    private static final String LIT_GROUPID = "groupId";
    private static final String LIT_FINISHEDAT = "finishedAt";
    private static final String LIT_CREATEDAT = "createdAt";
    private static final String LIT_UPDATEDAT = "updatedAt";
    private static final String LIT_CATEGORYCODE = "categoryCode";
    private static final String LIT_COVER = "cover";
    private static final String LIT_PROGRESSPCT = "progressPct";
    private static final String LIT_NAME = "name";
    private static final String LIT_GROUPTYPE = "groupType";
    private static final String LIT_BOOKCOUNT = "bookCount";
    private static final String LIT_TOTAL = "total";
    private static final String LIT_WISH = "wish";
    private static final String LIT_FINISHEDTHISMONTH = "finishedThisMonth";
    private static final String LIT_TODAYRECITATIONS = "todayRecitations";
    private static final String LIT_TODAYGIFTS = "todayGifts";
    private static final String LIT_TODAYPOSTS = "todayPosts";
    private static final String LIT_REREADINGBOOKS = "rereadingBooks";
    private static final String LIT_TOTALREADDAYS = "totalReadDays";
    private static final String LIT_DATE = "date";
    private static final String LIT_ACTION = "action";
    private static final String MSG_SHELF_NOT_FOUND = "书架记录不存在";

    private final BizFabushiShelfMapper shelfMapper;
    private final BizFabushiShelfGroupMapper groupMapper;
    private final BizFabushiReadingProgressMapper progressMapper;
    private final BizFabushiReadStatusMapper readStatusMapper;
    private final BizFabushiBookMapper bookMapper;
    private final BizFabushiUserSettingMapper settingMapper;
    private final BizFabushiPostMapper postMapper;

    private Long uid() {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new BusinessException(401, "未登录");
        return id;
    }

    // ===== 书架 + 我的觉醒聚合 =====

    public Map<String, Object> shelfData() {
        Long uid = uid();
        List<BizFabushiShelf> rows = myShelves(uid);
        Map<Long, BizFabushiBook> books = booksOf(rows);
        Map<Long, BizFabushiReadingProgress> progresses = progressesOf(uid, rows);
        List<Map<String, Object>> records = rows.stream()
                .map(row -> shelfRow(row, books.get(row.getBookId()), progresses.get(row.getBookId())))
                .toList();

        Map<Long, Long> groupCounts = rows.stream().filter(r -> r.getGroupId() != null)
                .collect(Collectors.groupingBy(BizFabushiShelf::getGroupId, Collectors.counting()));
        Map<String, Long> categoryCounts = rows.stream()
                .filter(row -> books.get(row.getBookId()) != null && books.get(row.getBookId()).getCategoryCode() != null)
                .collect(Collectors.groupingBy(row -> books.get(row.getBookId()).getCategoryCode(), Collectors.counting()));
        List<Map<String, Object>> groups = groupRows(uid, groupCounts, categoryCounts);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put(LIT_RECORDS, records);
        out.put(LIT_GROUPS, groups);
        out.put(LIT_SUMMARY, summary(rows, books, progresses));
        return out;
    }

    public Map<String, Object> awakenStatus() {
        Long uid = uid();
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<BizFabushiPost> posts = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getUserId, uid)
                .eq(BizFabushiPost::getStatus, POST_PUBLISHED)
                .ge(BizFabushiPost::getCreatedAt, from)
                .lt(BizFabushiPost::getCreatedAt, to));
        long recitations = posts.stream().filter(p -> SCOPE_SONGDU.equals(p.getScopeType())).count();
        long gifts = posts.stream().filter(p -> SCOPE_SUISHI.equals(p.getScopeType())).count();

        List<BizFabushiShelf> rows = myShelves(uid);
        Map<Long, BizFabushiBook> books = booksOf(rows);
        Map<Long, BizFabushiReadingProgress> progresses = progressesOf(uid, rows);
        List<BizFabushiReadStatus> reads = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .eq(BizFabushiReadStatus::getUserId, uid));
        long readDays = reads.stream().map(r -> r.getReadAt().atZone(ZONE_SHANGHAI).toLocalDate())
                .distinct().count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put(LIT_DATE, today.toString());
        out.put(LIT_TODAYRECITATIONS, recitations);
        out.put(LIT_TODAYGIFTS, gifts);
        out.put(LIT_TODAYPOSTS, (long) posts.size());
        out.put(LIT_REREADINGBOOKS, rows.stream().filter(r -> STATUS_READING.equals(r.getStatus())).count());
        out.put(LIT_FINISHEDTHISMONTH, finishedThisMonth(rows));
        out.put(LIT_TOTALREADDAYS, readDays);
        out.put(LIT_CURRENTBOOK, currentBook(rows, books, progresses));
        return out;
    }

    private List<BizFabushiShelf> myShelves(Long uid) {
        return shelfMapper.selectList(new LambdaQueryWrapper<BizFabushiShelf>()
                .eq(BizFabushiShelf::getUserId, uid)
                .orderByDesc(BizFabushiShelf::getPinned)
                .orderByAsc(BizFabushiShelf::getSortNo)
                .orderByDesc(BizFabushiShelf::getUpdatedAt));
    }

    private Map<Long, BizFabushiBook> booksOf(List<BizFabushiShelf> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<Long> bookIds = rows.stream().map(BizFabushiShelf::getBookId).collect(Collectors.toSet());
        return bookMapper.selectList(new LambdaQueryWrapper<BizFabushiBook>().in(BizFabushiBook::getId, bookIds))
                .stream().collect(Collectors.toMap(BizFabushiBook::getId, b -> b, (a, b) -> a));
    }

    private Map<Long, BizFabushiReadingProgress> progressesOf(Long uid, List<BizFabushiShelf> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<Long> bookIds = rows.stream().map(BizFabushiShelf::getBookId).collect(Collectors.toSet());
        return progressMapper.selectList(new LambdaQueryWrapper<BizFabushiReadingProgress>()
                        .eq(BizFabushiReadingProgress::getUserId, uid)
                        .in(BizFabushiReadingProgress::getBookId, bookIds))
                .stream().collect(Collectors.toMap(BizFabushiReadingProgress::getBookId, p -> p, (a, b) -> a));
    }

    private Map<String, Object> shelfRow(BizFabushiShelf row, BizFabushiBook book,
                                         BizFabushiReadingProgress progress) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put(LIT_BOOKID, row.getBookId());
        m.put(LIT_TITLE, book == null ? "已下架书目" : book.getTitle());
        m.put(LIT_AUTHOR, book == null ? null : book.getAuthor());
        m.put(LIT_PAGES, book == null ? null : book.getPages());
        m.put(LIT_PDFFILEID, book == null ? null : book.getPdfFileId());
        m.put(LIT_CATEGORYCODE, book == null ? null : book.getCategoryCode());
        m.put(LIT_STATUS, row.getStatus());
        m.put(LIT_PINNED, value(row.getPinned(), 0));
        m.put(LIT_SORTNO, value(row.getSortNo(), 0));
        m.put(LIT_GROUPID, row.getGroupId());
        m.put(LIT_FINISHEDAT, row.getFinishedAt());
        m.put(LIT_CREATEDAT, row.getCreatedAt());
        m.put(LIT_UPDATEDAT, row.getUpdatedAt());
        m.put(LIT_LASTSECTIONID, row.getLastSectionId());
        m.put(LIT_LASTPAGE, progress == null || progress.getLastPage() == null ? 1 : progress.getLastPage());
        m.put(LIT_TOTALPAGES, progress == null ? null : progress.getTotalPages());
        m.put(LIT_PROGRESSPCT, progressPct(progress, book == null ? null : book.getPages()));
        m.put(LIT_COVER, m.get(LIT_TITLE) == null ? "书" : String.valueOf(m.get(LIT_TITLE)).substring(0,
                Math.min(4, String.valueOf(m.get(LIT_TITLE)).length())));
        return m;
    }

    private Map<String, Object> currentBook(List<BizFabushiShelf> rows, Map<Long, BizFabushiBook> books,
                                            Map<Long, BizFabushiReadingProgress> progresses) {
        BizFabushiShelf current = null;
        for (BizFabushiShelf row : rows) {
            if (!STATUS_READING.equals(row.getStatus())) continue;
            if (current == null || later(row.getUpdatedAt(), current.getUpdatedAt())) current = row;
        }
        if (current == null) return Map.of();
        return shelfRow(current, books.get(current.getBookId()), progresses.get(current.getBookId()));
    }

    private Map<String, Object> summary(List<BizFabushiShelf> rows, Map<Long, BizFabushiBook> books,
                                        Map<Long, BizFabushiReadingProgress> progresses) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(LIT_TOTAL, rows.size());
        out.put(STATUS_WISH, countStatus(rows, STATUS_WISH));
        out.put(STATUS_READING, countStatus(rows, STATUS_READING));
        out.put(STATUS_FINISHED, countStatus(rows, STATUS_FINISHED));
        out.put(STATUS_FAVORITE, countStatus(rows, STATUS_FAVORITE));
        out.put(LIT_FINISHEDTHISMONTH, finishedThisMonth(rows));
        out.put(LIT_CURRENTBOOK, currentBook(rows, books, progresses));
        return out;
    }

    private int countStatus(List<BizFabushiShelf> rows, String status) {
        return (int) rows.stream().filter(row -> status.equals(row.getStatus())).count();
    }

    private long finishedThisMonth(List<BizFabushiShelf> rows) {
        YearMonth current = YearMonth.now(ZONE_SHANGHAI);
        return rows.stream()
                .filter(r -> STATUS_FINISHED.equals(r.getStatus()) && r.getFinishedAt() != null)
                .filter(r -> current.equals(YearMonth.from(r.getFinishedAt().atZone(ZONE_SHANGHAI).toLocalDate())))
                .count();
    }

    private boolean later(LocalDateTime candidate, LocalDateTime current) {
        if (current == null) return true;
        if (candidate == null) return false;
        return candidate.isAfter(current);
    }

    private int value(Integer number, int fallback) {
        return number == null ? fallback : number;
    }

    private int progressPct(BizFabushiReadingProgress progress, Integer bookPages) {
        Integer total = progress != null && progress.getTotalPages() != null ? progress.getTotalPages() : bookPages;
        int last = progress == null || progress.getLastPage() == null ? 1 : progress.getLastPage();
        if (total == null || total <= 0) return 0;
        return (int) Math.round(Math.clamp(last * 100.0 / total, 0.0, 100.0));
    }

    // ===== 分组 =====

    private List<Map<String, Object>> groupRows(Long uid, Map<Long, Long> groupCounts,
                                                Map<String, Long> categoryCounts) {
        List<BizFabushiShelfGroup> groups = new ArrayList<>();
        groups.addAll(groupMapper.selectList(new LambdaQueryWrapper<BizFabushiShelfGroup>()
                .eq(BizFabushiShelfGroup::getUserId, 0L)
                .eq(BizFabushiShelfGroup::getGroupType, GROUP_AUTO)
                .orderByAsc(BizFabushiShelfGroup::getSortNo)));
        groups.addAll(groupMapper.selectList(new LambdaQueryWrapper<BizFabushiShelfGroup>()
                .eq(BizFabushiShelfGroup::getUserId, uid)
                .eq(BizFabushiShelfGroup::getGroupType, GROUP_CUSTOM)
                .orderByAsc(BizFabushiShelfGroup::getSortNo)));
        return groups.stream().map(g -> groupRow(g, groupCounts, categoryCounts)).toList();
    }

    private Map<String, Object> groupRow(BizFabushiShelfGroup group, Map<Long, Long> groupCounts,
                                         Map<String, Long> categoryCounts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", group.getId());
        m.put(LIT_NAME, group.getName());
        m.put(LIT_GROUPTYPE, group.getGroupType());
        m.put(LIT_CATEGORYCODE, group.getCategoryCode());
        m.put(LIT_SORTNO, value(group.getSortNo(), 0));
        long count = GROUP_AUTO.equals(group.getGroupType())
                ? categoryCounts.getOrDefault(group.getCategoryCode(), 0L)
                : groupCounts.getOrDefault(group.getId(), 0L);
        m.put(LIT_BOOKCOUNT, count);
        return m;
    }

    public List<Map<String, Object>> groups() {
        return groupRows(uid(), Map.of(), Map.of());
    }

    @Transactional
    public Map<String, Object> createGroup(String name) {
        Long uid = uid();
        String normalized = requireGroupName(name);
        if (groupMapper.selectCount(new LambdaQueryWrapper<BizFabushiShelfGroup>()
                .eq(BizFabushiShelfGroup::getUserId, uid)
                .eq(BizFabushiShelfGroup::getName, normalized)) > 0) {
            throw new BusinessException(400, "分组名已存在");
        }
        BizFabushiShelfGroup row = new BizFabushiShelfGroup();
        row.setUserId(uid);
        row.setName(normalized);
        row.setGroupType(GROUP_CUSTOM);
        row.setSortNo(0);
        groupMapper.insert(row);
        return groupRow(row, Map.of(), Map.of());
    }

    @Transactional
    public void renameGroup(Long id, String name) {
        Long uid = uid();
        BizFabushiShelfGroup row = requireCustomGroup(uid, id);
        row.setName(requireGroupName(name));
        groupMapper.updateById(row);
    }

    @Transactional
    public void deleteGroup(Long id) {
        Long uid = uid();
        requireCustomGroup(uid, id);
        List<BizFabushiShelf> rows = shelfMapper.selectList(new LambdaQueryWrapper<BizFabushiShelf>()
                .eq(BizFabushiShelf::getUserId, uid)
                .eq(BizFabushiShelf::getGroupId, id));
        for (BizFabushiShelf row : rows) {
            row.setGroupId(null);
            shelfMapper.updateById(row);
        }
        groupMapper.deleteById(id);
    }

    private BizFabushiShelfGroup requireCustomGroup(Long uid, Long id) {
        BizFabushiShelfGroup row = groupMapper.selectById(id);
        if (row == null || !uid.equals(row.getUserId()) || !GROUP_CUSTOM.equals(row.getGroupType())) {
            throw new BusinessException(404, "自建分组不存在");
        }
        return row;
    }

    private String requireGroupName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 64) throw new BusinessException(400, "分组名长度应为 1-64");
        return normalized;
    }

    private void requireGroup(Long uid, Long groupId) {
        if (groupId == null) return;
        requireCustomGroup(uid, groupId);
    }

    // ===== 书架写操作 =====

    @Transactional
    public void addShelf(Long bookId, Map<String, Object> body) {
        if (bookId == null) throw new BusinessException(400, "书目不能为空");
        BizFabushiBook book = bookMapper.selectById(bookId);
        if (book == null || !"approved".equals(book.getStatus())) throw new BusinessException(404, "书目不存在或未通过审核");
        Long uid = uid();
        List<BizFabushiShelf> rows = shelfMapper.selectList(new LambdaQueryWrapper<BizFabushiShelf>()
                .eq(BizFabushiShelf::getUserId, uid));
        if (rows.stream().anyMatch(r -> bookId.equals(r.getBookId()))) return;
        Long groupId = optionalLong(body == null ? null : body.get(LIT_GROUPID));
        requireGroup(uid, groupId);
        int nextSort = rows.stream().mapToInt(r -> value(r.getSortNo(), 0)).max().orElse(0) + 1;

        BizFabushiShelf row = new BizFabushiShelf();
        row.setUserId(uid);
        row.setBookId(bookId);
        row.setStatus(body == null ? STATUS_READING : shelfStatus(body.get(LIT_STATUS), STATUS_READING));
        row.setPinned(0);
        row.setSortNo(nextSort);
        row.setGroupId(groupId);
        if (STATUS_FINISHED.equals(row.getStatus())) row.setFinishedAt(LocalDateTime.now(ZONE_SHANGHAI));
        shelfMapper.insert(row);
    }

    @Transactional
    public void updateShelf(Long id, Map<String, Object> body) {
        BizFabushiShelf row = requireMyShelf(id);
        if (body == null) return;
        if (body.containsKey(LIT_STATUS)) {
            String status = shelfStatus(body.get(LIT_STATUS), row.getStatus());
            if (STATUS_FINISHED.equals(status) && !STATUS_FINISHED.equals(row.getStatus())) {
                row.setFinishedAt(LocalDateTime.now(ZONE_SHANGHAI));
            } else if (!STATUS_FINISHED.equals(status)) {
                row.setFinishedAt(null);
            }
            row.setStatus(status);
        }
        if (body.containsKey(LIT_PINNED)) row.setPinned(boolValue(body.get(LIT_PINNED)) ? 1 : 0);
        if (body.containsKey(LIT_SORTNO)) row.setSortNo(intValue(body.get(LIT_SORTNO), value(row.getSortNo(), 0)));
        if (body.containsKey(LIT_GROUPID)) {
            Long groupId = optionalLong(body.get(LIT_GROUPID));
            requireGroup(row.getUserId(), groupId);
            row.setGroupId(groupId);
        }
        if (body.get(LIT_LASTSECTIONID) != null) {
            row.setLastSectionId(Long.valueOf(String.valueOf(body.get(LIT_LASTSECTIONID))));
        }
        shelfMapper.updateById(row);
    }

    @Transactional
    public void removeShelf(Long id) {
        requireMyShelf(id);
        shelfMapper.deleteById(id); // 阅读进度与上传内容保留（D20 批量移出口径）
    }

    @Transactional
    public void batchShelf(List<Long> ids, Map<String, Object> body) {
        if (ids == null || ids.isEmpty()) throw new BusinessException(400, "请先选择书目");
        if (body == null) throw new BusinessException(400, "批量操作参数不能为空");
        String action = String.valueOf(body.getOrDefault(LIT_ACTION, ""));
        List<BizFabushiShelf> rows = shelfMapper.selectList(new LambdaQueryWrapper<BizFabushiShelf>()
                .in(BizFabushiShelf::getId, ids));
        if (rows.size() != new HashSet<>(ids).size() || rows.stream().anyMatch(r -> !uid().equals(r.getUserId()))) {
            throw new BusinessException(404, MSG_SHELF_NOT_FOUND);
        }
        switch (action) {
            case "pin" -> setPinned(rows, 1);
            case "unpin" -> setPinned(rows, 0);
            case "remove" -> shelfMapper.delete(new LambdaQueryWrapper<BizFabushiShelf>()
                    .in(BizFabushiShelf::getId, ids));
            case "group" -> {
                Long groupId = optionalLong(body.get(LIT_GROUPID));
                requireGroup(uid(), groupId);
                rows.forEach(r -> r.setGroupId(groupId));
                rows.forEach(shelfMapper::updateById);
            }
            case "reorder" -> {
                int index = 1;
                for (Long id : ids) {
                    BizFabushiShelf row = rows.stream().filter(r -> id.equals(r.getId())).findFirst()
                            .orElseThrow(() -> new BusinessException(404, MSG_SHELF_NOT_FOUND));
                    row.setSortNo(index++);
                    shelfMapper.updateById(row);
                }
            }
            default -> throw new BusinessException(400, "不支持的批量操作");
        }
    }

    private void setPinned(List<BizFabushiShelf> rows, int pinned) {
        rows.forEach(r -> r.setPinned(pinned));
        rows.forEach(shelfMapper::updateById);
    }

    private BizFabushiShelf requireMyShelf(Long id) {
        BizFabushiShelf row = shelfMapper.selectById(id);
        if (row == null || !uid().equals(row.getUserId())) throw new BusinessException(404, MSG_SHELF_NOT_FOUND);
        return row;
    }

    private String shelfStatus(Object raw, String fallback) {
        String status = raw == null ? fallback : String.valueOf(raw);
        if (!SHELF_STATUSES.contains(status)) throw new BusinessException(400, "书架状态不合法");
        return status;
    }

    private Long optionalLong(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        return Long.valueOf(String.valueOf(raw));
    }

    private int intValue(Object raw, int fallback) {
        if (raw == null) return fallback;
        return Integer.parseInt(String.valueOf(raw));
    }

    private boolean boolValue(Object raw) {
        if (raw instanceof Boolean b) return b;
        String s = String.valueOf(raw);
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    // ===== PDF 进度 / 记录 / 提醒设置 =====

    public Map<String, Object> progress(Long bookId) {
        Long uid = uid();
        BizFabushiReadingProgress row = progressMapper.selectOne(new LambdaQueryWrapper<BizFabushiReadingProgress>()
                .eq(BizFabushiReadingProgress::getUserId, uid).eq(BizFabushiReadingProgress::getBookId, bookId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(LIT_BOOKID, bookId);
        m.put(LIT_LASTPAGE, row == null || row.getLastPage() == null ? 1 : row.getLastPage());
        m.put(LIT_TOTALPAGES, row == null ? null : row.getTotalPages());
        return m;
    }

    @Transactional
    public void saveProgress(Long bookId, Map<String, Object> body) {
        Long uid = uid();
        int lastPage = body != null && body.get(LIT_LASTPAGE) != null ? Integer.parseInt(String.valueOf(body.get(LIT_LASTPAGE))) : 1;
        if (lastPage < 1) throw new BusinessException(400, "页码不合法");
        Integer totalPages = body != null && body.get(LIT_TOTALPAGES) != null ? Integer.parseInt(String.valueOf(body.get(LIT_TOTALPAGES))) : null;
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
        Map<LocalDate, Long> counts = rows.stream().collect(Collectors.groupingBy(
                r -> r.getReadAt().toLocalDate(), TreeMap::new, Collectors.counting()));
        List<Map<String, Object>> days = counts.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey().toString()); m.put("count", e.getValue()); return m;
        }).toList();
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
        m.put(LIT_REMINDERENABLED, row == null ? 0 : row.getReminderEnabled());
        m.put(LIT_FREQCODE, row == null ? LIT_DAILY : row.getFreqCode());
        m.put(LIT_REMINDTIME, row == null ? "20:00" : row.getRemindTime());
        m.put(LIT_SOUNDENABLED, row == null ? 1 : row.getSoundEnabled());
        return m;
    }

    @Transactional
    public void saveSettings(Map<String, Object> body) {
        Long uid = uid();
        String freq = body != null && body.get(LIT_FREQCODE) != null ? String.valueOf(body.get(LIT_FREQCODE)) : LIT_DAILY;
        if (!Set.of(LIT_DAILY, "weekday", "weekend").contains(freq)) throw new BusinessException(400, "提醒频率不合法");
        String time = body != null && body.get(LIT_REMINDTIME) != null ? String.valueOf(body.get(LIT_REMINDTIME)) : "20:00";
        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) throw new BusinessException(400, "提醒时间不合法");
        int enabled = body != null && body.get(LIT_REMINDERENABLED) != null ? Integer.parseInt(String.valueOf(body.get(LIT_REMINDERENABLED))) : 0;
        int sound = body != null && body.get(LIT_SOUNDENABLED) != null ? Integer.parseInt(String.valueOf(body.get(LIT_SOUNDENABLED))) : 1;
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
