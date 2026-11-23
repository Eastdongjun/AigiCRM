package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiBookmark;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookmarkMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【D19/D24 切片 7】阅读器页码书签：标记（同页幂等）/跳转列表/删除，全部本人维度。
 * 不做 OCR、不做书内搜索——影印版无文本层，批 2 前如实标注不可搜。
 */
@Service
@RequiredArgsConstructor
public class FabushiBookmarkService {
    private static final String LIT_ID = "id";
    private static final String LIT_BOOKID = "bookId";
    private static final String LIT_PAGE = "page";
    private static final String LIT_NOTE = "note";
    private static final String LIT_CREATEDAT = "createdAt";
    private static final int MAX_NOTE = 200;
    private static final String MSG_NOT_FOUND = "书签不存在";

    private final BizFabushiBookmarkMapper bookmarkMapper;
    private final BizFabushiBookMapper bookMapper;

    private Long uid() {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new BusinessException(401, "未登录");
        return id;
    }

    /** 按书列出本人书签，页码升序；书目不存在返回空列表（阅读器首屏无书签不是错误）。 */
    public List<Map<String, Object>> list(Long bookId) {
        if (bookId == null) throw new BusinessException(400, "书目ID不能为空");
        Long uid = uid();
        List<BizFabushiBookmark> rows = bookmarkMapper.selectList(new LambdaQueryWrapper<BizFabushiBookmark>()
                .eq(BizFabushiBookmark::getUserId, uid)
                .eq(BizFabushiBookmark::getBookId, bookId)
                .orderByAsc(BizFabushiBookmark::getPage)
                .orderByDesc(BizFabushiBookmark::getId));
        return rows.stream().map(this::row).toList();
    }

    /**
     * 【PROTO-AUDIT-20261123 P9 / D23】「我的书摘」：跨书列出本人全部书签。
     *
     * <p>原型 P9 的「我的本月」卡上有「我的书摘」入口（D23），但原接口只支持
     * `GET /bookmarks?bookId=`，前端拿不到"我所有书签"。这里补一个本人维度的聚合列表，
     * 顺带带上书名，避免前端为每条书签再查一次书目。
     */
    public List<Map<String, Object>> listMine() {
        Long uid = uid();
        List<BizFabushiBookmark> rows = bookmarkMapper.selectList(new LambdaQueryWrapper<BizFabushiBookmark>()
                .eq(BizFabushiBookmark::getUserId, uid)
                .orderByDesc(BizFabushiBookmark::getCreatedAt)
                .orderByDesc(BizFabushiBookmark::getId));
        if (rows.isEmpty()) return List.of();
        Map<Long, String> titles = bookMapper.selectBatchIds(
                        rows.stream().map(BizFabushiBookmark::getBookId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.aigicrm.module.fabushi.entity.BizFabushiBook::getId,
                        com.aigicrm.module.fabushi.entity.BizFabushiBook::getTitle, (a, b) -> a));
        return rows.stream().map(r -> {
            Map<String, Object> m = row(r);
            m.put("bookTitle", titles.getOrDefault(r.getBookId(), "—"));
            return m;
        }).toList();
    }

    /** 同人同书同页重复标记直接返回既有行（幂等），不产生重复书签。 */
    @Transactional
    public Map<String, Object> add(Long bookId, Map<String, Object> body) {
        if (bookId == null) throw new BusinessException(400, "书目ID不能为空");
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, "书目不存在");
        int page = parsePage(body);
        String note = parseNote(body);
        Long uid = uid();
        BizFabushiBookmark existing = bookmarkMapper.selectOne(new LambdaQueryWrapper<BizFabushiBookmark>()
                .eq(BizFabushiBookmark::getUserId, uid)
                .eq(BizFabushiBookmark::getBookId, bookId)
                .eq(BizFabushiBookmark::getPage, page));
        if (existing != null) {
            if (note != null && !note.equals(existing.getNote())) {
                existing.setNote(note);
                bookmarkMapper.updateById(existing);
            }
            return row(existing);
        }
        BizFabushiBookmark row = new BizFabushiBookmark();
        row.setUserId(uid);
        row.setBookId(bookId);
        row.setPage(page);
        row.setNote(note);
        bookmarkMapper.insert(row);
        return row(row);
    }

    /** 只删本人书签；逻辑删保留留痕，删除后同页可再次标记。 */
    @Transactional
    public void remove(Long id) {
        if (id == null) throw new BusinessException(400, "书签ID不能为空");
        BizFabushiBookmark row = bookmarkMapper.selectById(id);
        if (row == null || !row.getUserId().equals(uid())) throw new BusinessException(404, MSG_NOT_FOUND);
        bookmarkMapper.deleteById(id);
    }

    private static int parsePage(Map<String, Object> body) {
        Object raw = body == null ? null : body.get(LIT_PAGE);
        if (raw == null) throw new BusinessException(400, "页码不能为空");
        int page;
        try {
            page = Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "页码不合法");
        }
        if (page < 1) throw new BusinessException(400, "页码不合法");
        return page;
    }

    private static String parseNote(Map<String, Object> body) {
        Object raw = body == null ? null : body.get(LIT_NOTE);
        if (raw == null) return null;
        String note = String.valueOf(raw).trim();
        if (note.length() > MAX_NOTE) throw new BusinessException(400, "书签备注不能超过 " + MAX_NOTE + " 字");
        return note.isEmpty() ? null : note;
    }

    private Map<String, Object> row(BizFabushiBookmark row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(LIT_ID, row.getId());
        out.put(LIT_BOOKID, row.getBookId());
        out.put(LIT_PAGE, row.getPage());
        out.put(LIT_NOTE, row.getNote());
        out.put(LIT_CREATEDAT, row.getCreatedAt());
        return out;
    }
}
