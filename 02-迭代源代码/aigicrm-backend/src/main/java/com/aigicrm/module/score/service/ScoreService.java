package com.aigicrm.module.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.score.entity.BizScoreEntry;
import com.aigicrm.module.score.entity.BizScoreItem;
import com.aigicrm.module.score.entity.BizScoreParticipant;
import com.aigicrm.module.score.mapper.BizScoreEntryMapper;
import com.aigicrm.module.score.mapper.BizScoreItemMapper;
import com.aigicrm.module.score.mapper.BizScoreParticipantMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.aigicrm.module.system.entity.SysDictData;
import com.aigicrm.module.system.service.SysDictService;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final BizScoreItemMapper itemMapper;
    private final BizScoreEntryMapper entryMapper;
    private final BizScoreParticipantMapper participantMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysDictService dictService;
    private final OrgScopeResolver scopeResolver;

    /** 【2026-09-19 接入权限矩阵】学分可见用户集合：矩阵 (score,view) 显式配置优先，未配置回退角色数据范围 */
    private List<Long> visibleUserIds() {
        // 【GPT三轮 ACL-P1-001 2026-09-22】sys_user 非租户表, null(all) 必须集团收口, 否则放大为全平台
        return scopeResolver.resolveModuleUsersWithinGroup(SecurityUtils.getCurrentUser(), "score");
    }

    // ====== 学分项 CRUD(2026-11-01 模块更名: 学分→学分, 仅文案; 权限码/表/接口不动) ======

    public List<BizScoreItem> listItems() {
        LambdaQueryWrapper<BizScoreItem> w = new LambdaQueryWrapper<>();
        w.orderByAsc(BizScoreItem::getSortOrder);
        return itemMapper.selectList(w);
    }

    public BizScoreItem addItem(BizScoreItem item) {
        if (item.getSortOrder() == null) {
            // 默认排到最后
            Long maxSort = itemMapper.selectCount(null);
            item.setSortOrder(maxSort.intValue() + 1);
        }
        itemMapper.insert(item);
        return item;
    }

    public void updateItem(BizScoreItem item) {
        itemMapper.updateById(item);
    }

    public void deleteItem(Long id) {
        // 【体检 P1 2026-10-12】级联删除该学分项的全部评分明细: 前端确认弹窗承诺"所有周期数据
        // 一并删除", 此前只删 item 行——孤儿 entry 继续进排行(幽灵分数), 且仅当他人再保存同周期
        // 才被顺带清扫; 无 FK 无级联(DDL), 须业务层显式删
        entryMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aigicrm.module.score.entity.BizScoreEntry>()
                .eq(com.aigicrm.module.score.entity.BizScoreEntry::getItemId, id));
        itemMapper.deleteById(id);
    }

    // ====== 评分表格 ======

    public Map<String, Object> getGrid(String period) {
        Map<String, Object> result = new HashMap<>();

        // 所有学分项
        List<BizScoreItem> items = listItems();
        // 【2026-11-07 用户令 A=理顺码系】大类中文名入 grid: category 存 score_category 字典的
        // dict_value(拼音码), 中文名回填 categoryName——PC 录入页本就从字典取名, MP 明细页
        // 行名/分组同源; 此前 categoryName 恒空, MP 只能拿 product 当名字
        Map<String, String> catNameMap = new HashMap<>();
        for (SysDictData d : dictService.listDataByCode("score_category")) {
            catNameMap.put(d.getDictValue(), d.getDictLabel());
        }
        for (BizScoreItem it : items) {
            if (it.getCategory() != null && catNameMap.containsKey(it.getCategory())) {
                it.setCategoryName(catNameMap.get(it.getCategory()));
            }
        }
        result.put("items", items);

        // 全部启用用户（用于"添加同事"下拉的候选池）【2026-08-08 按账号数据范围过滤】
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1);
        List<Long> visible = visibleUserIds();
        if (visible != null) userWrapper.in(SysUser::getId, visible);
        List<SysUser> activeUsers = userMapper.selectList(userWrapper);
        result.put("pool", activeUsers.stream().map(this::toUserMap).collect(Collectors.toList()));

        // 该周期已保存的参与同事名单
        List<BizScoreParticipant> participants = Collections.emptyList();
        if (period != null && !period.isEmpty()) {
            participants = participantMapper.selectList(
                    new LambdaQueryWrapper<BizScoreParticipant>()
                            .eq(BizScoreParticipant::getPeriod, period)
                            .orderByAsc(BizScoreParticipant::getSortOrder));
            // 【GPT复审 P1-02 修复 2026-09-21】名单随数据范围收口: 窄范围用户不得借历史名单看到
            // 范围外人员姓名(null=全量不过滤); 范围外名单行连同其分数一并不可见
            if (visible != null) {
                participants = participants.stream()
                        .filter(p -> visible.contains(p.getUserId()))
                        .collect(Collectors.toList());
            }
        }

        List<Map<String, Object>> users;
        if (!participants.isEmpty()) {
            // 已保存过名单：严格按名单顺序还原这些列（即使同事后来被停用也保留，保证数量一致）
            users = new ArrayList<>();
            for (BizScoreParticipant p : participants) {
                SysUser u = userMapper.selectById(p.getUserId());
                if (u != null) users.add(toUserMap(u));
            }
        } else {
            // 首次打开（无名单）：默认全体启用用户
            users = activeUsers.stream().map(this::toUserMap).collect(Collectors.toList());
        }
        result.put("users", users);

        // 该周期的所有打分
        Map<String, Integer> scores = new HashMap<>(); // "itemId_userId" -> score
        if (period != null && !period.isEmpty()) {
            LambdaQueryWrapper<BizScoreEntry> ew = new LambdaQueryWrapper<>();
            ew.eq(BizScoreEntry::getPeriod, period);
            List<BizScoreEntry> entries = entryMapper.selectList(ew);
            for (BizScoreEntry e : entries) {
                // 【GPT复审 P1-02】分数矩阵随同一可见集合收口, 范围外人员分数不可见
                if (visible != null && !visible.contains(e.getUserId())) continue;
                scores.put(e.getItemId() + "_" + e.getUserId(), e.getScore());
            }
        }
        result.put("scores", scores);

        return result;
    }

    @Transactional
    public void batchSave(String period, List<Map<String, Object>> entries, List<Long> participantIds) {
        Long scorerId = SecurityUtils.getCurrentUserId();

        // 【修复 FEAT-20260804-SCORE-DATA-LOSS】此前会整体物理删除该周期所有分数记录、再按前端
        // 当前可见的同事名单重新插入。一旦管理员把某同事临时移出可见列表后保存，该同事本期已录入的
        // 历史分数会被永久删除且不可恢复；两个管理员并发编辑不同子集时也会互相覆盖对方的保存。
        // 改为增量 upsert：只处理"当前参与名单"范围内的记录，名单外（被移出可见列表）的同事历史
        // 数据保持不动；名单内但本次提交未包含分数的单元格（被显式清空）才删除对应记录。
        Set<Long> participantSet = new HashSet<>();
        if (participantIds != null) {
            for (Long uid : participantIds) if (uid != null) participantSet.add(uid);
        }

        Map<String, Integer> submitted = new LinkedHashMap<>();
        for (Map<String, Object> e : entries) {
            Long itemId = toLong(e.get("itemId"));
            Long userId = toLong(e.get("userId"));
            Integer score = toInt(e.get("score"));
            if (itemId == null || userId == null) continue;
            submitted.put(itemId + "_" + userId, score != null ? score : 0);
        }

        // 【GPT复审 P1-03 修复 2026-09-21】服务端强制校验: 目标人员在可管理范围、学分项存在、
        // 分数落在学分项 min~max —— 此前信任客户端, 直接 API 调用可写范围外人员与异常分值
        List<Long> saveVisible = visibleUserIds();
        // 【GPT三轮 SCORE-P1-002 2026-09-22】participantIds 独立于 entries 构造并直接落库,
        // 必须与 submitted 同口径逐一校验: 旧代码只校验 submitted, 范围外 userId 不提交 entry
        // 即可混入参与名单(名单污染显示顺序与后续口径)。saveVisible 经 WithinGroup 收口, 非 admin 恒非 null
        for (Long uid : participantSet) {
            if (saveVisible != null && !saveVisible.contains(uid)) {
                throw new BusinessException("参与人员不在可管理范围: " + uid);
            }
        }
        Map<Long, BizScoreItem> itemMap = new HashMap<>();
        for (BizScoreItem it : listItems()) itemMap.put(it.getId(), it);
        for (Map.Entry<String, Integer> chk : submitted.entrySet()) {
            String[] cp = chk.getKey().split("_");
            Long chkItemId = Long.parseLong(cp[0]);
            Long chkUserId = Long.parseLong(cp[1]);
            if (saveVisible != null && !saveVisible.contains(chkUserId)) {
                throw new BusinessException("目标人员不在可管理范围: " + chkUserId);
            }
            BizScoreItem chkItem = itemMap.get(chkItemId);
            if (chkItem == null) {
                throw new BusinessException("学分项不存在: " + chkItemId);
            }
            Integer v = chk.getValue();
            if (v < chkItem.getScoreMin() || v > chkItem.getScoreMax()) {
                throw new BusinessException("分数超出学分项范围: " + chkItem.getCategory() + "/" + chkItem.getProduct()
                        + " 允许 " + chkItem.getScoreMin() + "~" + chkItem.getScoreMax() + ", 实得 " + v);
            }
        }

        List<BizScoreEntry> existingEntries = entryMapper.selectList(
                new LambdaQueryWrapper<BizScoreEntry>().eq(BizScoreEntry::getPeriod, period));
        Map<String, BizScoreEntry> existingMap = new HashMap<>();
        for (BizScoreEntry ex : existingEntries) {
            existingMap.put(ex.getItemId() + "_" + ex.getUserId(), ex);
        }

        // 1) upsert 本次提交的分数
        for (Map.Entry<String, Integer> se : submitted.entrySet()) {
            String[] parts = se.getKey().split("_");
            Long itemId = Long.parseLong(parts[0]);
            Long userId = Long.parseLong(parts[1]);
            BizScoreEntry existing = existingMap.get(se.getKey());
            if (existing != null) {
                existing.setScore(se.getValue());
                existing.setScorerId(scorerId);
                entryMapper.updateById(existing);
            } else {
                BizScoreEntry entry = new BizScoreEntry();
                entry.setPeriod(period);
                entry.setItemId(itemId);
                entry.setUserId(userId);
                entry.setScore(se.getValue());
                entry.setScorerId(scorerId);
                entry.setDeleted(0);
                entryMapper.insert(entry);
            }
        }

        // 2) 参与名单内、本次提交未包含（即被显式清空）的记录 → 删除该条；
        //    不在参与名单内（被移出可见列表）的同事历史记录保持不动
        for (BizScoreEntry ex : existingEntries) {
            String key = ex.getItemId() + "_" + ex.getUserId();
            if (participantSet.contains(ex.getUserId()) && !submitted.containsKey(key)) {
                entryMapper.deleteById(ex.getId());
            }
        }

        // 持久化该周期参与同事名单（仅用于列显示顺序，不再联动删除分数记录）
        // 【G2 2026-09-21】物理删按当前集团收口, 不再跨租户清参与名单(仅 participant, 分数记录本就不删)
        // 【2026-11-09 用户令"学分录入人名重复"修复】改用去重后的 participantSet 插入——
        // 原代码遍历原始 participantIds List(可含重复 id), 同一人同期插多条→网格人名重复
        Long groupForDelete = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        participantMapper.physicalDeleteByPeriod(period, groupForDelete);
        int sort = 0;
        for (Long uid : participantSet) {
            BizScoreParticipant p = new BizScoreParticipant();
            p.setPeriod(period);
            p.setUserId(uid);
            p.setSortOrder(sort++);
            participantMapper.insert(p);
        }
    }

    private Map<String, Object> toUserMap(SysUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("realName", u.getRealName());
        return m;
    }

    // ====== 排行榜 ======

    public List<Map<String, Object>> ranking(String period) {
        // 【2026-08-08 设计规则：学分排行全员可见】查全部条目，不做数据范围过滤（公司级排行）
        LambdaQueryWrapper<BizScoreEntry> w = new LambdaQueryWrapper<>();
        if (period != null && !period.isEmpty() && !"全部".equals(period)) {
            w.eq(BizScoreEntry::getPeriod, period);
        }
        List<BizScoreEntry> entries = entryMapper.selectList(w);

        // 按 userId 汇总
        Map<Long, Integer> totalScores = new HashMap<>();
        Map<Long, Integer> itemCounts = new HashMap<>(); // 非零得分项数
        for (BizScoreEntry e : entries) {
            totalScores.merge(e.getUserId(), e.getScore(), Integer::sum);
            if (e.getScore() != null && e.getScore() > 0) {
                itemCounts.merge(e.getUserId(), 1, Integer::sum);
            }
        }

        // 上一期排名（用于计算趋势）
        Map<Long, Integer> prevRanks = getPrevRanks(period);

        // 排序：总分降序 → 项数降序 → 姓名
        // 【11-08 逐点核对修复】第三键"姓名"此前仅存在于注释与前端 footer 文案, 未落进比较器,
        // 同分同名次取决于 HashMap 迭代序(不确定)——补姓名兜底使排序确定化, 与文档/文案口径对齐。
        Map<Long, String> userNames = new HashMap<>();
        for (SysUser u : userMapper.selectBatchIds(totalScores.keySet())) {
            userNames.put(u.getId(), u.getRealName());
        }
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(totalScores.entrySet());
        sorted.sort((a, b) -> {
            int cmp = b.getValue().compareTo(a.getValue());
            if (cmp != 0) return cmp;
            cmp = (itemCounts.getOrDefault(b.getKey(), 0)).compareTo(
                    itemCounts.getOrDefault(a.getKey(), 0));
            if (cmp != 0) return cmp;
            String nameA = userNames.getOrDefault(a.getKey(), "");
            String nameB = userNames.getOrDefault(b.getKey(), "");
            return nameA.compareTo(nameB);
        });

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<Long, Integer> entry : sorted) {
            Long uid = entry.getKey();
            SysUser user = userMapper.selectById(uid);
            // 【P2 修复 2026-11-02】rank++ 先于用户判空: 软删用户被 continue 后名次跳号(1,3,4)。
            // 判空通过才占名次, 趋势(prevRanks)口径同步不受影响。
            if (user == null) continue;
            rank++;

            String officeName = "";
            if (user.getDeptId() != null) {
                var dept = deptMapper.selectById(user.getDeptId());
                if (dept != null) officeName = dept.getDeptName();
            }

            // 趋势
            String trend;
            if (rank == 1) {
                trend = "领先";
            } else if (prevRanks.containsKey(uid)) {
                int prevRank = prevRanks.get(uid);
                trend = rank < prevRank ? "上升" : "追赶";
            } else {
                trend = "追赶";
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put("userId", uid);
            row.put("realName", user.getRealName());
            row.put("officeName", officeName);
            row.put("totalScore", entry.getValue());
            row.put("itemCount", itemCounts.getOrDefault(uid, 0));
            row.put("trend", trend);
            result.add(row);
        }
        return result;
    }

    /** 【2026-11-07 用户令"部门排行做成明细, PC端也一样"】部门成员明细: 复用个人排行按 officeName
     *  收口(与 deptRanking 同一聚合口径), 返回部门摘要+成员列表(总分降序, 带公司名次)——
     *  零新查询面, MP 明细页 dept 模式与 PC 排行页部门行弹窗共用 */
    public Map<String, Object> deptMembers(String period, String deptName) {
        if (deptName == null || deptName.isBlank()) throw new BusinessException("部门名不能为空");
        List<Map<String, Object>> personal = ranking(period);
        List<Map<String, Object>> members = new ArrayList<>();
        int total = 0;
        for (Map<String, Object> p : personal) {
            if (!deptName.equals(p.getOrDefault("officeName", "未知"))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", p.get("userId"));
            m.put("realName", p.get("realName"));
            m.put("totalScore", p.get("totalScore"));
            m.put("itemCount", p.get("itemCount"));
            m.put("companyRank", p.get("rank"));
            members.add(m);
            total += (int) p.getOrDefault("totalScore", 0);
        }
        members.sort((a, b) -> (int) b.get("totalScore") - (int) a.get("totalScore"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deptName", deptName);
        result.put("period", period);
        result.put("participantCount", members.size());
        result.put("totalScore", total);
        result.put("avgScore", members.isEmpty() ? 0 : Math.round((double) total / members.size() * 10) / 10.0);
        result.put("members", members);
        return result;
    }

    public List<Map<String, Object>> deptRanking(String period) {
        List<Map<String, Object>> personal = ranking(period);
        // 按部门汇总
        Map<String, int[]> deptAgg = new LinkedHashMap<>(); // deptName -> [totalScore, participants]
        for (Map<String, Object> p : personal) {
            String dept = (String) p.getOrDefault("officeName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dept, k -> new int[]{0, 0});
            agg[0] += (int) p.getOrDefault("totalScore", 0);
            agg[1] += 1;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(deptAgg.entrySet());
        sorted.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);

        for (Map.Entry<String, int[]> e : sorted) {
            rank++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put("deptName", e.getKey());
            row.put("totalScore", e.getValue()[0]);
            row.put("participantCount", e.getValue()[1]);
            row.put("avgScore", e.getValue()[1] > 0 ? Math.round((double) e.getValue()[0] / e.getValue()[1] * 10) / 10.0 : 0);
            result.add(row);
        }
        return result;
    }

    public Map<String, Object> rankingStats(String period) {
        List<Map<String, Object>> personal = ranking(period);
        Map<String, Object> stats = new HashMap<>();
        stats.put("participantCount", personal.size());

        Set<String> depts = new HashSet<>();
        int topScore = 0;
        String topName = "";
        int totalItems = 0;
        for (Map<String, Object> p : personal) {
            depts.add((String) p.getOrDefault("officeName", ""));
            int score = (int) p.getOrDefault("totalScore", 0);
            if (score > topScore) {
                topScore = score;
                topName = (String) p.getOrDefault("realName", "");
            }
            totalItems += (int) p.getOrDefault("itemCount", 0);
        }
        stats.put("deptCount", depts.size());
        stats.put("topScore", topScore);
        stats.put("topScoreName", topName);
        stats.put("totalItemCount", totalItems);
        // 最高学分者的项数
        if (!personal.isEmpty()) {
            // 【2026-11-01 学分改名】导出文件名与"最高学分者"注释同步更名
        stats.put("topItemCount", personal.get(0).getOrDefault("itemCount", 0));
        }
        return stats;
    }

    // ====== 导出 ======

    public void exportGrid(String period, HttpServletResponse response) throws Exception {
        // 加载数据【2026-08-08 按账号数据范围导出】
        List<BizScoreItem> items = listItems();
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1);
        List<Long> visible = visibleUserIds();
        if (visible != null) userWrapper.in(SysUser::getId, visible);
        List<SysUser> allUsers = userMapper.selectList(userWrapper);
        Map<String, Integer> scoreMap = new HashMap<>();
        if (period != null && !period.isEmpty()) {
            LambdaQueryWrapper<BizScoreEntry> ew = new LambdaQueryWrapper<>();
            ew.eq(BizScoreEntry::getPeriod, period);
            if (visible != null) ew.in(BizScoreEntry::getUserId, visible);
            for (BizScoreEntry e : entryMapper.selectList(ew)) {
                scoreMap.put(e.getItemId() + "_" + e.getUserId(), e.getScore());
            }
        }

        // 大类 code → 中文名映射
        Map<String, String> catNames = new HashMap<>();
        for (SysDictData d : dictService.listDataByCode("score_category")) {
            catNames.put(d.getDictValue(), d.getDictLabel());
        }

        // 构建动态表头: #, 大类, 产品, 区间, 用户1, 用户2, ...
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("#"));
        head.add(List.of("大类"));
        head.add(List.of("产品"));
        head.add(List.of("区间"));
        for (SysUser u : allUsers) {
            head.add(List.of(u.getRealName()));
        }

        // 构建数据行
        List<List<Object>> rows = new ArrayList<>();
        Map<Long, Integer> userTotals = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            BizScoreItem item = items.get(i);
            List<Object> row = new ArrayList<>();
            row.add(i + 1);
            row.add(catNames.getOrDefault(item.getCategory(), item.getCategory()));
            row.add(item.getProduct());
            row.add(item.getScoreMin() + "-" + item.getScoreMax());
            for (SysUser u : allUsers) {
                int score = scoreMap.getOrDefault(item.getId() + "_" + u.getId(), 0);
                row.add(score); // 【P2 修复 2026-11-02】0 分导出为 0 而非空白(原 score>0?score:null 把 0 分当无分)
                userTotals.merge(u.getId(), score, Integer::sum);
            }
            rows.add(row);
        }

        // 合计行
        List<Object> totalRow = new ArrayList<>();
        totalRow.add("");
        totalRow.add("合计");
        totalRow.add("");
        totalRow.add("");
        for (SysUser u : allUsers) {
            totalRow.add(userTotals.getOrDefault(u.getId(), 0));
        }
        rows.add(totalRow);

        // 写出
        String fileName = URLEncoder.encode(period + "学分录入.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        EasyExcel.write(response.getOutputStream())
                .head(head)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet(period)
                .doWrite(rows);
    }

    // ====== 辅助 ======

    private Map<Long, Integer> getPrevRanks(String period) {
        String prev = getPrevPeriod(period);
        if (prev == null) return Collections.emptyMap();

        LambdaQueryWrapper<BizScoreEntry> w = new LambdaQueryWrapper<>();
        w.eq(BizScoreEntry::getPeriod, prev);
        List<BizScoreEntry> entries = entryMapper.selectList(w);

        Map<Long, Integer> totals = new HashMap<>();
        for (BizScoreEntry e : entries) {
            totals.merge(e.getUserId(), e.getScore(), Integer::sum);
        }

        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Map<Long, Integer> ranks = new HashMap<>();
        int r = 0;
        for (Map.Entry<Long, Integer> e : sorted) {
            r++;
            ranks.put(e.getKey(), r);
        }
        return ranks;
    }

    private String getPrevPeriod(String period) {
        if (period == null || period.isEmpty() || "全部".equals(period)) return null;
        try {
            // 【P2 清理 2026-11-02】半年口径已退役(V2026.11.01.2 存量并入全年), 半年分支删除;
            // 周期唯一形态="XXXX全年", 上一期=上年全年; 其他历史脏格式回退 null(无趋势)
            if (period.length() >= 4 && period.endsWith("全年")) {
                int year = Integer.parseInt(period.substring(0, 4));
                return (year - 1) + "全年";
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
