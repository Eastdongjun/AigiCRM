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
            m.put("id", s.getId()); m.put("bookId", s.getBookId()); m.put("parentId", s.getParentId());
            m.put("levelCode", s.getLevelCode()); m.put("title", s.getTitle()); m.put("sortOrder", s.getSortOrder());
            m.put("children", build(children, s.getId()));
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> create(Long bookId, Map<String, Object> body) {
        if (bookMapper.selectById(bookId) == null) throw new BusinessException(404, "书目不存在");
        String title = body == null || body.get("title") == null ? null : String.valueOf(body.get("title")).trim();
        if (title == null || title.isEmpty()) throw new BusinessException(400, "章节标题不能为空");
        BizFabushiSection row = new BizFabushiSection();
        row.setBookId(bookId);
        row.setParentId(body.get("parentId") == null ? 0L : Long.valueOf(String.valueOf(body.get("parentId"))));
        row.setLevelCode(body.get("levelCode") == null ? "section" : String.valueOf(body.get("levelCode")));
        row.setTitle(title);
        row.setSortOrder(body.get("sortOrder") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sortOrder"))));
        sectionMapper.insert(row);
        return Map.of("id", row.getId());
    }

    @Transactional
    public void update(Long bookId, Long id, Map<String, Object> body) {
        BizFabushiSection row = sectionMapper.selectById(id);
        if (row == null || !bookId.equals(row.getBookId())) throw new BusinessException(404, "章节不存在");
        if (body != null && body.get("title") != null) {
            String title = String.valueOf(body.get("title")).trim();
            if (title.isEmpty()) throw new BusinessException(400, "章节标题不能为空");
            row.setTitle(title);
        }
        if (body != null && body.get("sortOrder") != null) row.setSortOrder(Integer.parseInt(String.valueOf(body.get("sortOrder"))));
        if (body != null && body.get("levelCode") != null) row.setLevelCode(String.valueOf(body.get("levelCode")));
        sectionMapper.updateById(row);
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        BizFabushiSection row = sectionMapper.selectById(id);
        if (row == null || !bookId.equals(row.getBookId())) throw new BusinessException(404, "章节不存在");
        sectionMapper.deleteById(id);
    }
}
