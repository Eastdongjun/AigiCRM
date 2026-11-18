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

    private final BizFabushiSectionMapper sectionMapper;
    private final BizFabushiBookMapper bookMapper;

    public Map<String, Object> tree(Long bookId) {
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, "书目不存在");
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
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> create(Long bookId, Map<String, Object> body) {
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, "书目不存在");
        String title = body == null || body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)).trim();
        if (title == null || title.isEmpty()) throw new BusinessException(400, "章节标题不能为空");
        BizFabushiSection row = new BizFabushiSection();
        row.setBookId(bookId);
        row.setParentId(body.get(LIT_PARENTID) == null ? 0L : Long.valueOf(String.valueOf(body.get(LIT_PARENTID))));
        row.setLevelCode(body.get(LIT_LEVELCODE) == null ? "section" : String.valueOf(body.get(LIT_LEVELCODE)));
        row.setTitle(title);
        row.setSortOrder(body.get(LIT_SORTORDER) == null ? 0 : Integer.parseInt(String.valueOf(body.get(LIT_SORTORDER))));
        sectionMapper.insert(row);
        return Map.of("id", row.getId());
    }

    @Transactional
    public void update(Long bookId, Long id, Map<String, Object> body) {
        BizFabushiSection row = sectionMapper.selectById(id);
        if (row == null || !bookId.equals(row.getBookId())) throw new BusinessException(404, "章节不存在");
        if (body != null && body.get(LIT_TITLE) != null) {
            String title = String.valueOf(body.get(LIT_TITLE)).trim();
            if (title.isEmpty()) throw new BusinessException(400, "章节标题不能为空");
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
