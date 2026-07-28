package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncentiveService {

    private final BizIncentiveCategoryMapper categoryMapper;
    private final BizIncentiveRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final BizFileMapper fileMapper;
    private final BizCustomerMapper customerMapper;
    private final SysDeptMapper deptMapper;

    public List<BizIncentiveCategory> getCategoryTree() {
        List<BizIncentiveCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveCategory>()
                        .eq(BizIncentiveCategory::getStatus, 1)
                        .orderByAsc(BizIncentiveCategory::getSortOrder)
        );
        return buildTree(all, 0L);
    }

    public void createCategory(BizIncentiveCategory category) {
        categoryMapper.insert(category);
    }

    public void updateCategory(BizIncentiveCategory category) {
        categoryMapper.updateById(category);
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    public List<BizIncentiveRecord> getRecords(Long userId, String period, Long parentCategoryId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizIncentiveRecord::getUserId, userId);
        if (period != null) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (parentCategoryId != null) {
            List<BizIncentiveCategory> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, parentCategoryId)
            );
            List<Long> childIds = children.stream().map(BizIncentiveCategory::getId).toList();
            if (!childIds.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getCategoryId, childIds);
            }
        }
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        // 填充关联名称
        for (BizIncentiveRecord r : records) {
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = categoryMapper.selectById(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getFileId() != null) {
                BizFile file = fileMapper.selectById(r.getFileId());
                if (file != null) r.setFileName(file.getFileName());
            }
            if (r.getFilePreId() != null) {
                BizFile file = fileMapper.selectById(r.getFilePreId());
                if (file != null) r.setFilePreName(file.getFileName());
            }
            if (r.getFileMidId() != null) {
                BizFile file = fileMapper.selectById(r.getFileMidId());
                if (file != null) r.setFileMidName(file.getFileName());
            }
            if (r.getFilePostId() != null) {
                BizFile file = fileMapper.selectById(r.getFilePostId());
                if (file != null) r.setFilePostName(file.getFileName());
            }
            if (r.getUserId() != null) {
                SysUser user = userMapper.selectById(r.getUserId());
                if (user != null) r.setUserName(user.getRealName());
            }
            if (r.getCustomerId() != null) {
                BizCustomer cust = customerMapper.selectById(r.getCustomerId());
                if (cust != null) r.setCustomerName(cust.getCustomerName());
            }
        }
        return records;
    }

    public void deleteRecord(Long id) {
        recordMapper.deleteById(id);
    }

    @Transactional
    public void addRecord(BizIncentiveRecord record) {
        BizIncentiveCategory category = categoryMapper.selectById(record.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        // 学分按文件数计：子类分值 × 本条记录的文件数(安装前/中/后中非空者)
        record.setScore(base * fileCountOf(record));
        record.setUserId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(record);
    }

    /** 单条记录的文件数(安装前/中/后非空者; 兼容旧版单文件 fileId 算 1) */
    private int fileCountOf(BizIncentiveRecord r) {
        int n = 0;
        if (r.getFilePreId() != null) n++;
        if (r.getFileMidId() != null) n++;
        if (r.getFilePostId() != null) n++;
        if (n == 0 && r.getFileId() != null) n = 1;
        return n;
    }

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分, 并给出周期总文件数/总学分 */
    public Map<String, Object> categoryStats(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        Map<Long, int[]> byCat = new LinkedHashMap<>(); // categoryId -> [fileCount, totalScore]
        int totalFiles = 0, totalScore = 0;
        for (BizIncentiveRecord r : records) {
            int fc = fileCountOf(r);
            int sc = r.getScore() != null ? r.getScore() : 0;
            int[] agg = byCat.computeIfAbsent(r.getCategoryId(), k -> new int[2]);
            agg[0] += fc;
            agg[1] += sc;
            totalFiles += fc;
            totalScore += sc;
        }
        List<Map<String, Object>> byCategory = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : byCat.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("categoryId", e.getKey());
            m.put("fileCount", e.getValue()[0]);
            m.put("totalScore", e.getValue()[1]);
            byCategory.add(m);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("byCategory", byCategory);
        result.put("totalFiles", totalFiles);
        result.put("totalScore", totalScore);
        return result;
    }

    // 个人积分榜
    public List<Map<String, Object>> scoreboard(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        Map<Long, Integer> userScores = new HashMap<>();
        Map<Long, Integer> userCounts = new HashMap<>();
        for (BizIncentiveRecord r : records) {
            userScores.merge(r.getUserId(), r.getScore() != null ? r.getScore() : 0, Integer::sum);
            userCounts.merge(r.getUserId(), 1, Integer::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(userScores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        for (Map.Entry<Long, Integer> e : sorted) {
            rank++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank);
            item.put("userId", e.getKey());
            item.put("totalScore", e.getValue());
            item.put("recordCount", userCounts.getOrDefault(e.getKey(), 0));
            SysUser user = userMapper.selectById(e.getKey());
            if (user != null) {
                item.put("realName", user.getRealName());
                item.put("deptId", user.getDeptId());
                String officeName = "未知";
                if (user.getOfficeId() != null) {
                    SysDept office = deptMapper.selectById(user.getOfficeId());
                    if (office != null) officeName = office.getDeptName();
                }
                item.put("officeName", officeName);
            }
            result.add(item);
        }
        return result;
    }

    // 部门排行
    public List<Map<String, Object>> deptRanking(String period) {
        List<Map<String, Object>> personal = scoreboard(period);
        Map<String, int[]> deptAgg = new LinkedHashMap<>();
        for (Map<String, Object> p : personal) {
            String dept = (String) p.getOrDefault("officeName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dept, k -> new int[]{0, 0});
            agg[0] += ((Number) p.getOrDefault("totalScore", 0)).intValue();
            agg[1] += 1;
        }

        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(deptAgg.entrySet());
        sorted.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, int[]> e : sorted) {
            rank++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put("deptName", e.getKey());
            row.put("totalScore", e.getValue()[0]);
            row.put("participantCount", e.getValue()[1]);
            row.put("avgScore", e.getValue()[1] > 0
                    ? Math.round((double) e.getValue()[0] / e.getValue()[1] * 10) / 10.0 : 0);
            result.add(row);
        }
        return result;
    }

    // 排行统计概览
    public Map<String, Object> rankingStats(String period) {
        List<Map<String, Object>> personal = scoreboard(period);
        Map<String, Object> stats = new HashMap<>();
        stats.put("participantCount", personal.size());

        Set<String> depts = new HashSet<>();
        int topScore = 0;
        String topName = "";
        int topRecordCount = 0;
        int totalRecordCount = 0;

        for (Map<String, Object> p : personal) {
            depts.add((String) p.getOrDefault("officeName", "未知"));
            int score = ((Number) p.getOrDefault("totalScore", 0)).intValue();
            int rc = ((Number) p.getOrDefault("recordCount", 0)).intValue();
            totalRecordCount += rc;
            if (score > topScore) {
                topScore = score;
                topName = (String) p.getOrDefault("realName", "");
                topRecordCount = rc;
            }
        }
        stats.put("deptCount", depts.size());
        stats.put("topScore", topScore);
        stats.put("topScoreName", topName);
        stats.put("topRecordCount", topRecordCount);
        stats.put("totalRecordCount", totalRecordCount);
        return stats;
    }

    /**
     * 获取客户关联的激励积分总分
     */
    public int getScoreByCustomerId(Long customerId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        return records.stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
    }

    private List<BizIncentiveCategory> buildTree(List<BizIncentiveCategory> list, Long parentId) {
        return list.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .peek(c -> c.setChildren(buildTree(list, c.getId())))
                .collect(Collectors.toList());
    }
}
