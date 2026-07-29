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
        // 三文件齐全才计分，积分 = 子类分值（不乘文件数）
        record.setScore(fileCountOf(record) > 0 ? base : 0);
        record.setUserId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(record);
    }

    /** 单条记录是否计分(安装前/中/后全部非空才算; 兼容旧版单文件 fileId) */
    private int fileCountOf(BizIncentiveRecord r) {
        if (r.getFilePreId() != null && r.getFileMidId() != null && r.getFilePostId() != null) return 1;
        if (r.getFileId() != null) return 1;
        return 0;
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
            int sc = fc > 0 ? (r.getScore() != null ? r.getScore() : 0) : 0;
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
            int sc = fileCountOf(r) > 0 ? (r.getScore() != null ? r.getScore() : 0) : 0;
            userScores.merge(r.getUserId(), sc, Integer::sum);
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

    /** 激励统计查询（全员可见）：支持大类/子类/行业/产品/周期筛选，返回完整明细 */
    public List<BizIncentiveRecord> queryRecords(Long mainCategoryId, Long subCategoryId,
                                                  String industry, String product, String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
        if (product != null && !product.isBlank()) wrapper.like(BizIncentiveRecord::getRelatedProducts, product);
        if (subCategoryId != null) {
            wrapper.eq(BizIncentiveRecord::getCategoryId, subCategoryId);
        } else if (mainCategoryId != null) {
            List<BizIncentiveCategory> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, mainCategoryId)
            );
            List<Long> childIds = new ArrayList<>();
            for (BizIncentiveCategory child : children) {
                childIds.add(child.getId());
                // 三级结构：再查孙级
                List<BizIncentiveCategory> grands = categoryMapper.selectList(
                        new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, child.getId())
                );
                for (BizIncentiveCategory g : grands) childIds.add(g.getId());
            }
            if (!childIds.isEmpty()) wrapper.in(BizIncentiveRecord::getCategoryId, childIds);
        }
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
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
                if (cust != null) {
                    r.setCustomerName(cust.getCustomerName());
                    r.setLastInvoiceDate(cust.getLastInvoiceDate());
                    if (cust.getLastInvoiceDate() != null) {
                        r.setOverdueDays(java.time.LocalDate.now().toEpochDay() - cust.getLastInvoiceDate().toEpochDay());
                    }
                }
            }
        }
        return records;
    }

    /** 时间线查询：按上传时间排序的扁平列表 */
    public List<Map<String, Object>> timeline(String customerName, String salesperson,
            String industry, Long mainCategoryId, String startDate, String endDate) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (customerName != null && !customerName.isBlank()) {
            List<BizCustomer> m = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName));
            if (!m.isEmpty()) wrapper.in(BizIncentiveRecord::getCustomerId, m.stream().map(BizCustomer::getId).toList());
            else return List.of();
        }
        if (salesperson != null && !salesperson.isBlank()) {
            List<SysUser> u = userMapper.selectList(new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, salesperson));
            if (!u.isEmpty()) wrapper.in(BizIncentiveRecord::getUserId, u.stream().map(SysUser::getId).toList());
            else return List.of();
        }
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
        if (mainCategoryId != null) {
            Set<Long> ids = new HashSet<>(); ids.add(mainCategoryId);
            categoryMapper.selectList(new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, mainCategoryId))
                .forEach(c -> { ids.add(c.getId()); categoryMapper.selectList(new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, c.getId())).forEach(g -> ids.add(g.getId())); });
            wrapper.in(BizIncentiveRecord::getCategoryId, ids);
        }
        if (startDate != null && !startDate.isBlank()) wrapper.ge(BizIncentiveRecord::getCreatedAt, startDate + " 00:00:00");
        if (endDate != null && !endDate.isBlank()) wrapper.le(BizIncentiveRecord::getCreatedAt, endDate + " 23:59:59");
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        // 填充
        Set<Long> cids = records.stream().map(BizIncentiveRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizCustomer> cmap = new HashMap<>();
        if (!cids.isEmpty()) customerMapper.selectBatchIds(cids).forEach(c -> cmap.put(c.getId(), c));
        Map<Long, BizIncentiveCategory> catmap = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> catmap.put(c.getId(), c));
        for (BizIncentiveRecord r : records) {
            BizCustomer c = cmap.get(r.getCustomerId()); if (c != null) r.setCustomerName(c.getCustomerName());
            BizIncentiveCategory cat = catmap.get(r.getCategoryId()); if (cat != null) r.setCategoryName(cat.getCategoryName());
            if (r.getUserId() != null) { SysUser u = userMapper.selectById(r.getUserId()); if (u != null) r.setUserName(u.getRealName()); }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizIncentiveRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("customerId", r.getCustomerId()); m.put("customerName", r.getCustomerName());
            m.put("categoryId", r.getCategoryId()); m.put("categoryName", r.getCategoryName());
            m.put("period", r.getPeriod()); m.put("industry", r.getIndustry()); m.put("relatedProducts", r.getRelatedProducts());
            m.put("score", r.getScore()); m.put("userName", r.getUserName());
            m.put("fileId", r.getFileId()); m.put("filePreId", r.getFilePreId()); m.put("fileMidId", r.getFileMidId()); m.put("filePostId", r.getFilePostId()); m.put("fileTrackId", r.getFileTrackId());
            m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            result.add(m);
        }
        return result;
    }

    /** 按客户分组查询激励记录 — 多维度筛选 */
    public List<Map<String, Object>> getCustomerRecords(String industry, Long mainCategoryId,
            String customerName, String salesperson, String startDate, String endDate) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BizIncentiveRecord::getCustomerId);
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
        if (customerName != null && !customerName.isBlank()) {
            // 需要JOIN客户表，这里先查出匹配的客户ID
            List<BizCustomer> matched = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName)
            );
            if (!matched.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getCustomerId, matched.stream().map(BizCustomer::getId).toList());
            } else { return List.of(); }
        }
        if (salesperson != null && !salesperson.isBlank()) {
            List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, salesperson)
            );
            if (!users.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getUserId, users.stream().map(SysUser::getId).toList());
            } else { return List.of(); }
        }
        if (mainCategoryId != null) {
            Set<Long> catIds = new HashSet<>();
            catIds.add(mainCategoryId);
            List<BizIncentiveCategory> children = categoryMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, mainCategoryId));
            for (BizIncentiveCategory child : children) {
                catIds.add(child.getId());
                List<BizIncentiveCategory> grands = categoryMapper.selectList(
                    new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, child.getId()));
                for (BizIncentiveCategory g : grands) catIds.add(g.getId());
            }
            wrapper.in(BizIncentiveRecord::getCategoryId, catIds);
        }
        if (startDate != null && !startDate.isBlank()) wrapper.ge(BizIncentiveRecord::getCreatedAt, startDate + " 00:00:00");
        if (endDate != null && !endDate.isBlank()) wrapper.le(BizIncentiveRecord::getCreatedAt, endDate + " 23:59:59");
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        // 批量查客户
        Set<Long> cids = records.stream().map(BizIncentiveRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizCustomer> custMap = new HashMap<>();
        if (!cids.isEmpty()) {
            List<BizCustomer> custs = customerMapper.selectBatchIds(cids);
            for (BizCustomer c : custs) custMap.put(c.getId(), c);
        }
        // 批量查分类
        Set<Long> catIds = records.stream().map(BizIncentiveRecord::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizIncentiveCategory> catMap2 = new HashMap<>();
        if (!catIds.isEmpty()) {
            List<BizIncentiveCategory> cats = categoryMapper.selectBatchIds(catIds);
            for (BizIncentiveCategory c : cats) catMap2.put(c.getId(), c);
        }

        // 批量查文件
        Set<Long> fids = new HashSet<>();
        for (BizIncentiveRecord r : records) {
            if (r.getFileId() != null) fids.add(r.getFileId());
            if (r.getFilePreId() != null) fids.add(r.getFilePreId());
            if (r.getFileMidId() != null) fids.add(r.getFileMidId());
            if (r.getFilePostId() != null) fids.add(r.getFilePostId());
            if (r.getFileTrackId() != null) fids.add(r.getFileTrackId());
        }
        Map<Long, BizFile> fileMap = new HashMap<>();
        if (!fids.isEmpty()) {
            for (BizFile f : fileMapper.selectBatchIds(fids)) fileMap.put(f.getId(), f);
        }

        // 填充关联信息
        for (BizIncentiveRecord r : records) {
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = catMap2.get(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getUserId() != null) { SysUser u = userMapper.selectById(r.getUserId()); if (u != null) r.setUserName(u.getRealName()); }
            if (r.getCustomerId() != null) {
                BizCustomer c = custMap.get(r.getCustomerId());
                if (c != null) r.setCustomerName(c.getCustomerName());
            }
            BizFile f = fileMap.get(r.getFileId());
            if (f != null) r.setFileName(f.getFileName());
            f = fileMap.get(r.getFilePreId());
            if (f != null) r.setFilePreName(f.getFileName());
            f = fileMap.get(r.getFileMidId());
            if (f != null) r.setFileMidName(f.getFileName());
            f = fileMap.get(r.getFilePostId());
            if (f != null) r.setFilePostName(f.getFileName());
            f = fileMap.get(r.getFileTrackId());
            if (f != null) r.setFileTrackName(f.getFileName());
        }

        // 按客户分组
        Map<Long, List<BizIncentiveRecord>> grouped = new LinkedHashMap<>();
        for (BizIncentiveRecord r : records) {
            Long cid = r.getCustomerId();
            if (cid == null) continue;
            grouped.computeIfAbsent(cid, k -> new ArrayList<>()).add(r);
        }

        // 行业排序
        List<String> industryOrder = List.of("冶金","煤矿","化工","电力","有色金属","机械","工程公司","维修厂","其他终端客户");
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < industryOrder.size(); i++) orderMap.put(industryOrder.get(i), i);

        // 每个客户一个条目
        List<Map<String, Object>> flatList = new ArrayList<>();
        for (Map.Entry<Long, List<BizIncentiveRecord>> e : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerId", e.getKey());
            BizCustomer cust = custMap.get(e.getKey());
            String ind = cust != null ? cust.getIndustry() : "其他";
            item.put("customerName", cust != null ? cust.getCustomerName() : null);
            item.put("industry", ind);
            item.put("industryOrder", orderMap.getOrDefault(ind, 99));
            item.put("count", e.getValue().size());
            int totalScore = e.getValue().stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
            item.put("totalScore", totalScore);
            item.put("records", e.getValue());
            flatList.add(item);
        }
        flatList.sort(Comparator.comparingInt(m -> (int) m.get("industryOrder")));
        return flatList;
    }

    private List<BizIncentiveCategory> buildTree(List<BizIncentiveCategory> list, Long parentId) {
        return list.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .peek(c -> c.setChildren(buildTree(list, c.getId())))
                .collect(Collectors.toList());
    }
}
