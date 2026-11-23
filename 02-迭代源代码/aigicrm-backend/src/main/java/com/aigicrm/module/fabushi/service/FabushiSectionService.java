package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiSection;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** 书目章节树（二期）：浏览=view，增改删=book:manage。 */
@Service
@RequiredArgsConstructor
public class FabushiSectionService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_PARENTID = "parentId";
    private static final String LIT_SORTORDER = "sortOrder";
    private static final String LIT_LEVELCODE = "levelCode";
    private static final String LIT_TITLE = "title";
    // 【S1192 2026-11-23】两段提示各出现 3 次，抽常量；文案与前端提示保持一致，改一处即全改。
    private static final String MSG_BOOK_NOT_FOUND = "书目不存在";
    private static final String MSG_TITLE_REQUIRED = "章节标题不能为空";

    private final BizFabushiSectionMapper sectionMapper;
    private final BizFabushiBookMapper bookMapper;

    public Map<String, Object> tree(Long bookId) {
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, MSG_BOOK_NOT_FOUND);
        List<BizFabushiSection> rows = sectionMapper.selectList(new LambdaQueryWrapper<BizFabushiSection>()
                .eq(BizFabushiSection::getBookId, bookId)
                .orderByAsc(BizFabushiSection::getParentId).orderByAsc(BizFabushiSection::getSortOrder));
        Map<Long, List<BizFabushiSection>> children = rows.stream().collect(Collectors.groupingBy(s -> s.getParentId() == null ? 0L : s.getParentId()));
        List<Map<String, Object>> tree = build(children, 0L);
        return Map.of("bookId", bookId, "sections", tree);
    }

    private List<Map<String, Object>> build(Map<Long, List<BizFabushiSection>> children, Long parent) {
        return children.getOrDefault(parent, List.of()).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId()); m.put("bookId", s.getBookId()); m.put(LIT_PARENTID, s.getParentId());
            m.put(LIT_LEVELCODE, s.getLevelCode()); m.put(LIT_TITLE, s.getTitle()); m.put(LIT_SORTORDER, s.getSortOrder());
            m.put("children", build(children, s.getId()));
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> create(Long bookId, Map<String, Object> body) {
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, MSG_BOOK_NOT_FOUND);
        String title = body == null || body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)).trim();
        if (title == null || title.isEmpty()) throw new BusinessException(400, MSG_TITLE_REQUIRED);
        BizFabushiSection row = new BizFabushiSection();
        row.setBookId(bookId);
        row.setParentId(body.get(LIT_PARENTID) == null ? 0L : Long.valueOf(String.valueOf(body.get(LIT_PARENTID))));
        row.setLevelCode(body.get(LIT_LEVELCODE) == null ? "section" : String.valueOf(body.get(LIT_LEVELCODE)));
        row.setTitle(title);
        row.setSortOrder(body.get(LIT_SORTORDER) == null ? 0 : Integer.parseInt(String.valueOf(body.get(LIT_SORTORDER))));
        sectionMapper.insert(row);
        return Map.of("id", row.getId());
    }

    /**
     * 【PROTO-AUDIT-20261123 P8】发布时「自我录入章节」。
     *
     * <p>原型 P8 的「② 确定章节」给两条路：选择已有章节 / 自我录入章节。自我录入若走
     * {@link #create} 需要 {@code fabushi:book:manage}（那是书目管理员的权限），普通发布人
     * 没有，于是只能被迫"选不到就发不了"。这里单开一个**只追加叶子章节**的入口：
     * 权限降到 {@code fabushi:create}（谁发布谁能建），且**同名幂等**——同一本书里已存在
     * 同名章节就直接复用，不会因为多人上传刷出一堆重复章节。
     *
     * <p>刻意不允许指定 parentId / levelCode：自录只能挂在根下当叶子（levelCode=section），
     * 结构树的编排仍归 book:manage。
     */
    @Transactional
    public Map<String, Object> selfCreate(Long bookId, String rawTitle) {
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, MSG_BOOK_NOT_FOUND);
        String title = rawTitle == null ? null : rawTitle.trim();
        if (title == null || title.isEmpty()) throw new BusinessException(400, MSG_TITLE_REQUIRED);
        if (title.length() > 100) throw new BusinessException(400, "章节标题不能超过 100 字");
        BizFabushiSection existing = sectionMapper.selectOne(new LambdaQueryWrapper<BizFabushiSection>()
                .eq(BizFabushiSection::getBookId, bookId)
                .eq(BizFabushiSection::getTitle, title)
                .last("limit 1"));
        if (existing != null) return Map.of("id", existing.getId(), "reused", true);
        BizFabushiSection row = new BizFabushiSection();
        row.setBookId(bookId);
        row.setParentId(0L);
        row.setLevelCode("section");
        row.setTitle(title);
        row.setSortOrder(0);
        sectionMapper.insert(row);
        return Map.of("id", row.getId(), "reused", false);
    }

    @Transactional
    public void update(Long bookId, Long id, Map<String, Object> body) {
        BizFabushiSection row = sectionMapper.selectById(id);
        if (row == null || !bookId.equals(row.getBookId())) throw new BusinessException(404, "章节不存在");
        if (body != null && body.get(LIT_TITLE) != null) {
            String title = String.valueOf(body.get(LIT_TITLE)).trim();
            if (title.isEmpty()) throw new BusinessException(400, MSG_TITLE_REQUIRED);
            row.setTitle(title);
        }
        if (body != null && body.get(LIT_SORTORDER) != null) row.setSortOrder(Integer.parseInt(String.valueOf(body.get(LIT_SORTORDER))));
        if (body != null && body.get(LIT_LEVELCODE) != null) row.setLevelCode(String.valueOf(body.get(LIT_LEVELCODE)));
        sectionMapper.updateById(row);
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        BizFabushiSection row = sectionMapper.selectById(id);
        if (row == null || !bookId.equals(row.getBookId())) throw new BusinessException(404, "章节不存在");
        sectionMapper.deleteById(id);
    }
}
