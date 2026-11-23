package com.aigicrm.module.incentive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncentiveService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_TRACK = "track";
    private static final String LIT_FILEPREID = "filePreId";
    private static final String LIT_FILEMIDID = "fileMidId";
    private static final String LIT_FILEPOSTID = "filePostId";
    private static final String LIT_FILEID = "fileId";
    private static final String LIT_FILETRACKID = "fileTrackId";
    private static final String LIT_USERID = "userId";
    private static final String LIT_DEPTNAME = "deptName";
    private static final String LIT_PUBLISHEDCOUNT = "publishedCount";
    private static final String LIT_READCOUNT = "readCount";
    private static final String LIT_UNREADCOUNT = "unreadCount";
    private static final String LIT_EXCHANGE = "exchange";
    private static final String LIT_VISIT = "visit";
    private static final String LIT_REALNAME = "realName";
    private static final String LIT_TOTALSCORE = "totalScore";
    private static final String LIT_BYCATEGORY = "byCategory";

    private final BizIncentiveCategoryMapper categoryMapper;
    private final BizIncentiveRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final BizFileMapper fileMapper;
    private final BizCustomerMapper customerMapper;
    private final SysDeptMapper deptMapper;
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;
    /** S5.3 授权/可见性独立 bean；本类保留公共委托。 */
    private final IncentiveAccessService incentiveAccessService;
    /** S5.3 统计/排行/客户记录/已读独立 bean。 */
    private final IncentiveStatsService incentiveStatsService;
    /** S5.3 分类树/分类 CRUD 独立 bean。 */
    private final IncentiveCategoryService incentiveCategoryService;
    /** 记录/已读直查 SQL（删除快照、文件反查、已读统计）。 */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 【2026-11-17 阶段三】统一回收站台账；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.module.system.service.RecycleBinService recycleBinService;

    /**
     * 独立"看全部"资格：admin ∨ 授权表 can_view_all=1
     *
     * 【需求】激励模块看全部(含未分享)记录的白名单判定
     * 【方法】查用户级授权表 biz_incentive_grant（2026-08-16 替代载体角色模式）
     * 【业务规则】admin 恒可；无授权记录=默认不可看全部；can_view_all=1 才放行
     * 【参数】userId 目标用户ID（兼容旧签名，实际按当前登录人判定）
     * 【返回】true=可看全部未分享记录
     * 【注意】只读查表；不再读权限码，旧 incentive:view-all 权限码已废弃
     */
    // ====== 授权/可见性（S5.3 语义已移到 IncentiveAccessService） ======

    public boolean canViewAll() {
        return incentiveAccessService.canViewAll();
    }

    public boolean canViewShared() {
        return incentiveAccessService.canViewShared();
    }

    public boolean canEdit() {
        return incentiveAccessService.canEdit();
    }

    public List<Map<String, Object>> getUserList() {
        return incentiveAccessService.getUserList();
    }

    public boolean canDelete() {
        return incentiveAccessService.canDelete();
    }

    public Map<String, Object> grantRow(Long userId) {
        return incentiveAccessService.grantRow(userId);
    }

    public List<BizIncentiveCategory> getCategoryTree() {
        return incentiveCategoryService.getCategoryTree();
    }

    /**
     * 【2026-08-16 授权表化】看全部资格：系统管理员 ∨ can_view_all ∨ can_edit ∨ can_delete
     * （能编辑/删除他人记录隐含可看全部，对齐旧 incentive:manage 语义）。
     * 激励模块可见性统一为分享制，不再使用 data_scope/上级可见下级/负责人可见本组织/客户分享。
     */
    boolean canSeeAllIncentive() {
        return incentiveAccessService.canSeeAllIncentive();
    }

    public void applyShareVisibility(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        incentiveAccessService.applyShareVisibility(wrapper);
    }

    boolean canViewIncentiveUser(Long targetUserId) {
        return incentiveAccessService.canViewIncentiveUser(targetUserId);
    }

    void applyOrgQueryVisibility(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        incentiveAccessService.applyOrgQueryVisibility(wrapper);
    }

    public void createCategory(BizIncentiveCategory category) {
        incentiveCategoryService.createCategory(category);
    }

    public void updateCategory(BizIncentiveCategory category) {
        incentiveCategoryService.updateCategory(category);
    }

    public void deleteCategory(Long id) {
        incentiveCategoryService.deleteCategory(id);
    }

    public List<BizIncentiveRecord> getRecords(Long userId, String period, Long parentCategoryId) {
        return getRecords(userId, period, parentCategoryId, null);
    }

    /** 按客户查询激励记录（客户详情 Tab 用，customerId 可选过滤） */
    public List<BizIncentiveRecord> getRecords(Long userId, String period, Long parentCategoryId, Long customerId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (!applyUserRecordFilter(wrapper, userId)) return List.of();
        // 【2026-09-12】空周期不放行等值过滤：与 categoryStats/scoreboard 的守卫口径一致
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (customerId != null) wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
        applyParentCategoryFilter(wrapper, parentCategoryId);
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        // 批量填充关联名称(替代逐条 selectById 的 N+1,一次 IN 查询按 id 建 Map)
        incentiveStatsService.fillRecordAssociations(records);
        incentiveStatsService.fillFileNames(records);
        return records;
    }

    /** 【2026-08-08 分享制】指定人/未指定人的可见性过滤；返回 false 表示该人完全不可见（空结果）。 */
    private boolean applyUserRecordFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, Long userId) {
        if (userId == null) {
            // 未指定人时按分享制过滤：本人 + shared=1（数据范围/上级可见不再作用）。
            applyShareVisibility(wrapper);
            return true;
        }
        // 显式指定查看某人：admin/专项授权→全部；本人→自己全部；他人→仅其已分享记录。
        if (!canViewIncentiveUser(userId)) return false;
        wrapper.eq(BizIncentiveRecord::getUserId, userId);
        Long self = SecurityUtils.getCurrentUserId();
        if (!canSeeAllIncentive() && (self == null || !self.equals(userId))) {
            wrapper.eq(BizIncentiveRecord::getShared, 1);
        }
        return true;
    }

    private void applyParentCategoryFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, Long parentCategoryId) {
        if (parentCategoryId == null) return;
        List<BizIncentiveCategory> children = categoryMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, parentCategoryId)
        );
        // 【2026-11-01 审计修复】IN 含父 id 本身(直接挂大类的记录原查不出)
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(parentCategoryId);
        ids.addAll(children.stream().map(BizIncentiveCategory::getId).toList());
        wrapper.in(BizIncentiveRecord::getCategoryId, ids);
    }

    public BizIncentiveRecord getRecordById(Long id) {
        return recordMapper.selectById(id);
    }

    /**
     * 删除激励记录（admin ∨ can_delete 授权 ∨ 本人，归属校验在 Controller 完成）。
     * 【真机UAT修复 2026-09-22】先清 biz_incentive_read 子表（已读状态外键引用本记录），
     * 裸 deleteById 对已被阅读过的记录触发 fk_incentive_read_record 约束失败（测试环境实证
     * 支持编号 S-20260922235952-46683）；两步删除须原子，失败一起回滚。
     */
    @Transactional
    public void deleteRecord(Long id) {
        // 【2026-11-17 阶段三】软删进统一回收站：保留原分值与文件引用，管理员可恢复。
        // 【2026-11-17 P2-5】已读记录先快照进 bin，恢复时回填，避免“恢复后全员变未读”。
        java.util.List<java.util.Map<String, Object>> reads = jdbc.queryForList(
                "SELECT user_id FROM biz_incentive_read WHERE record_id=?", id);
        jdbc.update("UPDATE biz_incentive_record SET deleted_at=NOW(), deleted_by=?, deleted_reason=? WHERE id=?",
                SecurityUtils.getCurrentUserId(), "用户删除", id);
        jdbc.update("DELETE FROM biz_incentive_read WHERE record_id = ?", id);
        recordMapper.deleteById(id);
        if (recycleBinService != null) {
            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("id", id);
            snap.put("readUsers", reads);
            recycleBinService.record("incentive", id, snap, "用户删除", "user");
        }
        evictScoreboardCache();
    }

    /**
     * 更新激励记录（追加/删除文件、编辑业务字段时调用），自动重算学分
     *
     * 【需求】激励记录追加文件/更正信息时不重置初始上传人(userId)与上传时间(createdAt)
     * 【方法】按 id 查原记录，非 null 字段合并进原记录后按最新学分规则重算再落库
     * 【业务规则】归属校验在 Controller 完成(admin ∨ can_edit 授权 ∨ 本人)；
     *             归属客户 customerId 仅 admin/专项授权可变更，本人编辑保持原值；
     *             分享开关 shared 仅记录本人可改(admin 编辑他人记录时忽略)
     * 【参数】id 记录ID；update 待合并字段(非 null 才合并)
     * 【返回】无；记录不存在时静默返回(兼容并发删除场景)
     * 【注意】调用方须保证已通过 Controller 归属校验；@Transactional 保证重算+落库原子
     */
    @Transactional
    public void updateRecord(Long id, BizIncentiveRecord update) {
        // 【复检修复】行锁读与 removeFile 对称：PUT 无锁快照+全实体覆写会把并发 removeFile 已移除的
        // 槽位引用按旧快照写回(移除被静默回滚)；FOR UPDATE 串行化两接口对同一记录的交错
        BizIncentiveRecord existing = recordMapper.selectByIdForUpdate(id);
        if (existing == null) return;

        validateCustomerChange(update, existing);
        boolean fileTouched = isFileTouched(update);
        // 【真机UAT修复 2026-09-22】文件归属只校验"本次新引入"的引用:编辑/追加场景合并后的记录
        // 天然携带原上传人的存量文件(admin 或 can_edit 编辑他人记录、追加者≠原作者都属正常业务),
        // 存量引用在原上传时已按 create 同口径校验过。此前对合并后全量校验 uploader==当前编辑人,
        // 会把编辑他人记录整体误拒(实测 record 266: 原作者文件 869, admin 编辑被"无效文件引用"拒绝)。
        Set<Long> beforeFileIds = fileTouched ? new HashSet<>(collectFileIds(existing)) : Collections.emptySet();
        mergeBusinessFields(update, existing);
        mergeFileSlots(update, existing);
        assertNewFilesOwned(existing, beforeFileIds, fileTouched);
        applyShareIfOwner(update, existing);
        // 【五轮复检对齐】防御性留置：现行语义下 PUT 空白=未提供、合法槽位必含≥1个ID，本守卫在
        // 正常路径不可达；防的是未来 merge 语义变化或存量脏数据(五槽全空记录)被槽位编辑意外固化。
        if (fileTouched && !existing.hasAnyFile()) {
            throw new BusinessException(400, "记录至少须保留一份文件；如需删除整条记录请使用「删除」");
        }

        // 审计：记录最后修改人（updated_at 由 DB ON UPDATE 自动维护）
        existing.setUpdatedBy(SecurityUtils.getCurrentUserId());
        recalcScoreAndSave(existing);
    }

    private void validateCustomerChange(BizIncentiveRecord update, BizIncentiveRecord existing) {
        // 【2026-08-16 审查修复】归属客户变更仅 admin/can_edit 授权允许：
        // 本人记录改挂任意客户会绕过"记录-客户"绑定语义；前端编辑回传原值不受影响
        if (update.getCustomerId() != null
                && !Objects.equals(update.getCustomerId(), existing.getCustomerId())
                && !canEdit()) {
            throw new BusinessException(403, "无权修改记录归属客户");
        }
    }

    private boolean isFileTouched(BizIncentiveRecord update) {
        // 【四轮复检修复】与 mergeSlotJson"空串=未提供"对齐: 只有非空白槽位才算触碰,
        // 否则五槽全空的存量脏数据做纯业务字段编辑(前端回传空串)会被守卫误拒
        return isProvidedSlot(update.getFilePreId()) || isProvidedSlot(update.getFileMidId())
                || isProvidedSlot(update.getFilePostId()) || isProvidedSlot(update.getFileId())
                || isProvidedSlot(update.getFileTrackId());
    }

    private void mergeBusinessFields(BizIncentiveRecord update, BizIncentiveRecord existing) {
        if (update.getCustomerId() != null) existing.setCustomerId(update.getCustomerId());
        if (update.getIndustry() != null) existing.setIndustry(update.getIndustry());
        if (update.getRelatedProducts() != null) existing.setRelatedProducts(update.getRelatedProducts());
        if (update.getSpecificProduct() != null) existing.setSpecificProduct(update.getSpecificProduct());
        if (update.getPreInstallQuestion() != null) existing.setPreInstallQuestion(update.getPreInstallQuestion());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
    }

    private void mergeFileSlots(BizIncentiveRecord update, BizIncentiveRecord existing) {
        // 【三轮复检修复】槽位只接受合法 JSON 数组: 空白串一律视为"未提供"(清空槽位的唯一
        // 途径=remove-file 专用接口, 统一空槽表示为 NULL), 非法脏串直接拒绝不落库
        mergeSlotJson(update, existing, LIT_FILEPREID);
        mergeSlotJson(update, existing, LIT_FILEMIDID);
        mergeSlotJson(update, existing, LIT_FILEPOSTID);
        mergeSlotJson(update, existing, LIT_FILEID);
        mergeSlotJson(update, existing, LIT_FILETRACKID);
    }

    private void assertNewFilesOwned(BizIncentiveRecord existing, Set<Long> beforeFileIds, boolean fileTouched) {
        if (!fileTouched) return;
        // 【DeepSeek四审 P1 修复 2026-09-22】update 新增文件槽时与 create 同口径核对归属:
        // 旧代码只赋值不校验, 填他人/不存在文件 ID 也能保住上传材料分。核对对象=新引入的文件 ID
        List<Long> newFileIds = collectFileIds(existing).stream()
                .filter(fid -> !beforeFileIds.contains(fid)).toList();
        if (newFileIds.isEmpty()) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long existCount = fileMapper.selectCount(new LambdaQueryWrapper<BizFile>()
                .in(BizFile::getId, newFileIds)
                .eq(BizFile::getUploaderId, currentUserId));
        if (existCount == null || existCount != newFileIds.size()) {
            throw new BusinessException("包含无效文件引用，请重新上传");
        }
    }

    private void applyShareIfOwner(BizIncentiveRecord update, BizIncentiveRecord existing) {
        // 【2026-08-12】分享开关仅本人可改：admin/can_edit 编辑他人记录时忽略 shared，保持原值
        if (update.getShared() == null) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(existing.getUserId())) {
            existing.setShared(update.getShared());
        }
    }

    private void recalcScoreAndSave(BizIncentiveRecord existing) {
        BizIncentiveCategory category = categoryMapper.selectById(existing.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        existing.setScore(calcScore(existing, category, base));
        recordMapper.updateById(existing);
        evictScoreboardCache();
    }

    /** 【六轮复检修复】槽位共享校验: 格式正则 + 长度上限, create/update 同口径 */
    private static void assertValidSlotJson(String v) {
        String t = v.trim();
        if (t.length() > SLOT_JSON_MAX_LEN) {
            throw new BusinessException(400, "槽位文件数量超出上限");
        }
        if (!isValidSlotJson(t)) {
            throw new BusinessException(400, "安装文件槽位格式非法（应为文件ID数组）");
        }
    }

    /** 线性解析槽位 JSON（等价原正则，无回溯风险）：[1,2] / [1]。 */
    private static boolean isValidSlotJson(String t) {
        if (t.length() < 3 || t.charAt(0) != '[' || t.charAt(t.length() - 1) != ']') return false;
        String body = t.substring(1, t.length() - 1);
        if (body.isEmpty()) return false;
        int i = 0;
        while (true) {
            int next = nextCommaOrEnd(body, i);
            if (!isValidIdToken(body, i, next)) return false;
            if (next == body.length()) return true;
            i = next + 1;
            if (i == body.length()) return false;
        }
    }

    /** 下一个逗号或串尾（线性扫描，不回溯）。 */
    private static int nextCommaOrEnd(String body, int from) {
        int i = from;
        while (i < body.length() && body.charAt(i) != ',') i++;
        return i;
    }

    /** ID token：1-18 位 ASCII 数字。 */
    private static boolean isValidIdToken(String body, int start, int end) {
        int len = end - start;
        if (len < 1 || len > 18) return false;
        for (int i = start; i < end; i++) {
            char c = body.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** 槽位值是否为"本次实际提供"(非 null 且非空白)——与 mergeSlotJson 的空串=未提供语义一致 */
    private static boolean isProvidedSlot(String v) {
        return v != null && !v.isBlank();
    }

    /**
     * 槽位 JSON 合法形态: "[1,2]"（至少一个纯数字 ID；空/白串视为未提供）。
     * 【六轮复检修复】ID 限定 1-18 位(18 位以内恒在 Long 范围, 防 20 位数字落库后
     * parseJsonArray 溢出被吞成"看似有值实则恒空"的脏槽位)。
     * 【P0-5/B14 2026-11-18】原正则 ^\[\d{1,18}(,\d{1,18})*\]$ 有回溯风险(S5998)，
     * 改为线性解析（等价语义，O(n)，无回溯）。
     */
    /** 槽位列 DDL 为 varchar(500)，超长数组必须显式拒绝而非落库报 500/非严格模式截断 */
    private static final int SLOT_JSON_MAX_LEN = 500;

    /** 【三轮复检修复】槽位合并统一入口: 空白=未提供跳过; 非法格式 400; 合法则覆盖 */
    private void mergeSlotJson(BizIncentiveRecord update, BizIncentiveRecord existing, String field) {
        String v;
        switch (field) {
            case LIT_FILEPREID -> v = update.getFilePreId();
            case LIT_FILEMIDID -> v = update.getFileMidId();
            case LIT_FILEPOSTID -> v = update.getFilePostId();
            case LIT_FILEID -> v = update.getFileId();
            case LIT_FILETRACKID -> v = update.getFileTrackId();
            default -> v = null;
        }
        if (v == null || v.isBlank()) return;
        assertValidSlotJson(v);
        switch (field) {
            case LIT_FILEPREID -> existing.setFilePreId(v.trim());
            case LIT_FILEMIDID -> existing.setFileMidId(v.trim());
            case LIT_FILEPOSTID -> existing.setFilePostId(v.trim());
            case LIT_FILEID -> existing.setFileId(v.trim());
            case LIT_FILETRACKID -> existing.setFileTrackId(v.trim());
            default -> { }
        }
    }

    /**
     * 移除记录某槽位中的单个文件（【用户裁决 2026-10-10】上传错了需要修改）
     *
     * 【方法】解析槽位 JSON 数组 → 剔除该文件 ID → 显式 SET 写回（含清空为 NULL）→ 重算学分 → 盖修改人章
     * 【业务规则】物理文件保留（biz_file 原件不动），只解除记录槽位引用——文件原件留在文件库
     *             可查可重挂；操作级留痕(何时谁移除了哪个文件)为 V2 审计日志事项，当前仅盖
     *             最后修改人/时间章；
     *             全记录五槽合计移除后须 ≥1 份文件（防空壳记录：分数还在证据全无）；
     *             槽位 whitelist：file/pre/mid/post/track
     * 【参数】id 记录ID；slot 槽位；fileId 要移除的文件ID
     * 【返回】无；记录/槽位/文件不存在或不属于该槽位时抛 BusinessException
     * 【注意】归属校验在 Controller 完成（同 updateRecord：admin ∨ canEdit 授权 ∨ 本人）；
     *         显式 LambdaUpdateWrapper 而非 updateById——MP 默认策略跳过 null 字段，
     *         删空槽位（字段须置 NULL）用 updateById 会静默不生效（假成功）
     */
    @Transactional
    public void removeFile(Long id, String slot, Long fileId) {
        // 【工作流复检修复 2026-10-10】锁定读：守卫是 check-then-act，普通快照读下并发两事务
        // 各删不同槽的最后一份文件可双双通过"五槽合计≥1"（产出空壳记录）；FOR UPDATE 串行化同记录并发删除
        BizIncentiveRecord existing = recordMapper.selectByIdForUpdate(id);
        if (existing == null) throw new BusinessException(404, "记录不存在");
        if (fileId == null) throw new BusinessException(400, "缺少文件ID");

        List<Long> ids = switch (slot == null ? "" : slot) {
            case "file" -> existing.getFileIds();
            case "pre" -> existing.getFilePreIds();
            case "mid" -> existing.getFileMidIds();
            case "post" -> existing.getFilePostIds();
            case LIT_TRACK -> existing.getFileTrackIds();
            default -> null;
        };
        if (ids == null) throw new BusinessException(400, "无效槽位: " + slot);
        if (!ids.contains(fileId)) throw new BusinessException(400, "该文件不在此记录的该槽位中");

        // 全记录守卫：五槽合计移除后至少保留 1 份文件
        int total = existing.getFileIds().size() + existing.getFilePreIds().size()
                + existing.getFileMidIds().size() + existing.getFilePostIds().size()
                + existing.getFileTrackIds().size();
        if (total - 1 < 1) {
            throw new BusinessException(400, "记录至少须保留一份文件；如需删除整条记录请使用「删除」");
        }

        List<Long> next = new ArrayList<>(ids);
        next.remove(fileId);
        String nextJson = next.isEmpty() ? null
                : "[" + next.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";

        // 先在内存实体上应用变更，供 calcScore 按移除后的口径重算
        switch (slot) {
            case "file" -> existing.setFileIds(next);
            case "pre" -> existing.setFilePreIds(next);
            case "mid" -> existing.setFileMidIds(next);
            case "post" -> existing.setFilePostIds(next);
            case LIT_TRACK -> existing.setFileTrackIds(next);
            default -> { /* unreachable，上方已校验 */ }
        }
        BizIncentiveCategory category = categoryMapper.selectById(existing.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        existing.setScore(calcScore(existing, category, base));

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord>()
                        .eq(BizIncentiveRecord::getId, id)
                        .set(BizIncentiveRecord::getScore, existing.getScore())
                        .set(BizIncentiveRecord::getUpdatedBy, SecurityUtils.getCurrentUserId());
        switch (slot) {
            case "file" -> uw.set(BizIncentiveRecord::getFileId, nextJson);
            case "pre" -> uw.set(BizIncentiveRecord::getFilePreId, nextJson);
            case "mid" -> uw.set(BizIncentiveRecord::getFileMidId, nextJson);
            case "post" -> uw.set(BizIncentiveRecord::getFilePostId, nextJson);
            case LIT_TRACK -> uw.set(BizIncentiveRecord::getFileTrackId, nextJson);
            default -> { /* unreachable */ }
        }
        recordMapper.update(null, uw);
        evictScoreboardCache();
    }

    /**
     * 【BIZ-007 2026-11-13 外部审计修复】文件中心删除激励佐证后的联动重算。
     *
     * 【背景】FileService.deleteFile(incentive scope) 此前只删 biz_file 行，不触碰激励记录的
     * 五槽引用与学分——出现"佐证已删、分数还在"（证据没了分照拿，违反记录佐证不变量）。
     * 【方法】反查引用该文件的全部记录(JSON_CONTAINS 精确数字匹配, JSON_VALID 防脏格式) →
     *         逐条从对应槽位剔除 → 按移除后口径 calcScore 重算 → 显式 SET 写回（含清空 NULL）。
     * 【不变量】与 removeFile 同款空壳守卫: 剔除后五槽合计 <1 → 抛 400（调用方事务回滚,
     *          文件保留），文案指引先处理激励记录——不引入"空壳记录 0 分"新语义。
     * 【事务】REQUIRED 合并进调用方(FileService.deleteFile)事务; scoreboard 缓存失效一次。
     */
    @Transactional
    public void detachFileFromRecords(Long fileId) {
        if (fileId == null) return;
        List<Long> recordIds = findRecordsContainingFile(fileId);
        if (recordIds == null || recordIds.isEmpty()) return;
        for (Long recordId : recordIds) {
            detachFromRecord(recordId, fileId);
        }
        evictScoreboardCache();
    }

    private List<Long> findRecordsContainingFile(Long fileId) {
        return jdbc.queryForList(
                "SELECT id FROM biz_incentive_record WHERE " +
                        "(JSON_VALID(file_id) AND JSON_CONTAINS(file_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_pre_id) AND JSON_CONTAINS(file_pre_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_mid_id) AND JSON_CONTAINS(file_mid_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_post_id) AND JSON_CONTAINS(file_post_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_track_id) AND JSON_CONTAINS(file_track_id, CAST(? AS CHAR)))",
                Long.class, fileId, fileId, fileId, fileId, fileId);
    }

    private void detachFromRecord(Long recordId, Long fileId) {
        BizIncentiveRecord r = recordMapper.selectByIdForUpdate(recordId);
        if (r == null) return;
        Map<String, List<Long>> slots = loadRecordSlots(r);
        if (!removeFileFromSlots(slots, fileId)) return; // JSON_CONTAINS 命中但解析后无此 id(脏格式兜底)
        int totalAfter = slots.values().stream().mapToInt(List::size).sum();
        if (totalAfter < 1) {
            throw new BusinessException(400, "该文件是激励记录 " + recordId
                    + " 的唯一佐证，无法删除；请先在激励记录中更换佐证或删除该记录");
        }
        applySlotsToEntity(r, slots);
        recalcAfterDetach(r, recordId, slots);
    }

    private Map<String, List<Long>> loadRecordSlots(BizIncentiveRecord r) {
        Map<String, List<Long>> slots = new LinkedHashMap<>();
        slots.put("file", copyIds(r.getFileIds()));
        slots.put("pre", copyIds(r.getFilePreIds()));
        slots.put("mid", copyIds(r.getFileMidIds()));
        slots.put("post", copyIds(r.getFilePostIds()));
        slots.put(LIT_TRACK, copyIds(r.getFileTrackIds()));
        return slots;
    }

    private List<Long> copyIds(List<Long> ids) {
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    private boolean removeFileFromSlots(Map<String, List<Long>> slots, Long fileId) {
        boolean touched = false;
        for (List<Long> list : slots.values()) {
            if (list.removeIf(id -> id.equals(fileId))) touched = true;
        }
        return touched;
    }

    private void applySlotsToEntity(BizIncentiveRecord r, Map<String, List<Long>> slots) {
        r.setFileIds(slots.get("file"));
        r.setFilePreIds(slots.get("pre"));
        r.setFileMidIds(slots.get("mid"));
        r.setFilePostIds(slots.get("post"));
        r.setFileTrackIds(slots.get(LIT_TRACK));
    }

    /** 应用到内存实体供 calcScore 按移除后口径评分，再落库。 */
    private void recalcAfterDetach(BizIncentiveRecord r, Long recordId, Map<String, List<Long>> slots) {
        BizIncentiveCategory category = categoryMapper.selectById(r.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        r.setScore(calcScore(r, category, base));
        recordMapper.update(null, buildDetachUpdate(recordId, slots, r.getScore()));
    }

    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord> buildDetachUpdate(
            Long recordId, Map<String, List<Long>> slots, Integer score) {
        return new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord>()
                .eq(BizIncentiveRecord::getId, recordId)
                .set(BizIncentiveRecord::getScore, score)
                .set(BizIncentiveRecord::getUpdatedBy, SecurityUtils.getCurrentUserId())
                .set(BizIncentiveRecord::getFileId, slots.get("file").isEmpty() ? null : toJsonArray(slots.get("file")))
                .set(BizIncentiveRecord::getFilePreId, slots.get("pre").isEmpty() ? null : toJsonArray(slots.get("pre")))
                .set(BizIncentiveRecord::getFileMidId, slots.get("mid").isEmpty() ? null : toJsonArray(slots.get("mid")))
                .set(BizIncentiveRecord::getFilePostId, slots.get("post").isEmpty() ? null : toJsonArray(slots.get("post")))
                .set(BizIncentiveRecord::getFileTrackId, slots.get(LIT_TRACK).isEmpty() ? null : toJsonArray(slots.get(LIT_TRACK)));
    }

    private String toJsonArray(List<Long> ids) {
        return "[" + ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    // 【死码清理 2026-11-08】recalculateAllScores 已随 POST /records/recalculate 端点删除：
    // 双端零调用（11-08 审计确证），单记录重算由 updateRecord/removeFile 内联完成。

    /**
     * 新增激励上传记录
     *
     * 【需求】激励上传提交后落库一条记录并按分类规则计分
     * 【方法】校验入参(分类/客户/文件引用) → 查分类分值 → calcScore 计分 → 强制 userId=当前登录人
     * 【业务规则】categoryId/customerId 必填且存在；至少一个文件槽有文件；
     *             引用的文件必须存在且为当前用户上传；计分规则见 calcScore
     * 【参数】incRecord 前端提交的记录(不含 userId，由本方法强制注入)
     * 【返回】无；校验失败抛 BusinessException
     * 【注意】调用前置 Controller 已过 incentive:add 权限；@Transactional 落库原子
     */
    @Transactional
    public void addRecord(BizIncentiveRecord incRecord) {
        validateNewRecord(incRecord);
        BizIncentiveCategory category = categoryMapper.selectById(incRecord.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        incRecord.setScore(calcScore(incRecord, category, base));
        incRecord.setUserId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(incRecord);
        evictScoreboardCache();
    }

    /**
     * 校验新增激励记录的入参完整性
     *
     * 【需求】防止脏数据入库：无客户/无分类/无文件/引用失效文件的记录会污染统计与查询
     * 【方法】逐项校验：分类存在 → 客户存在 → 任一文件槽非空 → 文件 ID 批量核对存在性与归属
     * 【业务规则】categoryId/customerId 必填且存在；五个文件槽(file/filePre/fileMid/filePost/fileTrack)
     *             至少一个非空；引用的文件必须存在且 uploader_id=当前用户(激励文件只能引用本人上传的文件)
     * 【参数】r 待入库记录
     * 【返回】无；不满足任一规则抛 BusinessException
     * 【注意】只读校验不落库；先于计分执行，失败不产生任何写入
     */
    private void validateNewRecord(BizIncentiveRecord r) {
        if (r.getCategoryId() == null) {
            throw new BusinessException("请选择激励分类");
        }
        if (categoryMapper.selectById(r.getCategoryId()) == null) {
            throw new BusinessException("激励分类不存在或已删除");
        }
        if (r.getCustomerId() == null) {
            throw new BusinessException("请选择关联客户");
        }
        if (customerMapper.selectById(r.getCustomerId()) == null) {
            throw new BusinessException("关联客户不存在或已删除");
        }
        if (!r.hasAnyFile()) {
            throw new BusinessException("请至少上传一个文件");
        }
        // 【五轮复检修复】create 与 update 同口径: 槽位非空白值必须是合法 JSON 数组
        // (parseJsonArray 会把脏串静默解析为空列表绕过归属核对, 原样落库成脏数据)
        for (String v : new String[]{r.getFilePreId(), r.getFileMidId(), r.getFilePostId(), r.getFileId(), r.getFileTrackId()}) {
            if (v != null && !v.isBlank()) assertValidSlotJson(v);
        }
        // 文件引用核对：全部槽位文件 ID 必须存在且属于当前用户（防伪造引用他人文件）
        List<Long> fileIds = collectFileIds(r);
        if (!fileIds.isEmpty()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            Long existCount = fileMapper.selectCount(new LambdaQueryWrapper<BizFile>()
                    .in(BizFile::getId, fileIds)
                    .eq(BizFile::getUploaderId, currentUserId));
            if (existCount == null || existCount != fileIds.size()) {
                throw new BusinessException("包含无效文件引用，请重新上传");
            }
        }
    }

    /**
     * 汇总记录全部文件槽的 ID（去重），供存在性/归属核对
     *
     * 【需求】新增记录前核对引用的文件是否存在且属于当前用户
     * 【方法】合并五个文件槽(file/filePre/fileMid/filePost/fileTrack)解析出的 ID 并去重
     * 【业务规则】同一文件出现在多个槽只核对一次；空槽跳过
     * 【参数】r 待核对记录
     * 【返回】去重后的文件 ID 列表（可能为空）
     * 【注意】依赖实体 parseJsonArray 容忍脏数据（解析失败视为空，不计入核对）
     */
    private List<Long> collectFileIds(BizIncentiveRecord r) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(r.getFileIds());
        ids.addAll(r.getFilePreIds());
        ids.addAll(r.getFileMidIds());
        ids.addAll(r.getFilePostIds());
        ids.addAll(r.getFileTrackIds());
        return new ArrayList<>(ids);
    }

    /**
     * 学分规则（2026-08-01 单槽化改造后；2026-08-10 plan 规则修正）：
     * 1. 两谈两做方案(plan)：方案文件任一存在即 +base，不论文件数量（多文件也只积一次）
     * 2. 外出参观(visit)/交流拜访(exchange)：安装文件任一槽有文件 → +base
     * 3. 其他（含产品试点 trial）：安装前+安装中+安装后 三文件齐全 → +base
     * 4. 否则 0 分
     */
    private int calcScore(BizIncentiveRecord r, BizIncentiveCategory category, int base) {
        if (category == null || base <= 0) return 0;
        boolean hasAny = hasAnySlotFile(r);
        if (isPlanCategory(category) || isVisitExchangeCategory(category)) {
            // 两谈两做方案 / 外出参观 / 交流拜访：任一槽有文件即得分
            return hasAny ? base : 0;
        }
        // 其他（含产品试点）：三文件齐全
        return hasAllSlotFiles(r) ? base : 0;
    }

    private boolean hasAnySlotFile(BizIncentiveRecord r) {
        return r.hasFiles(r.getFilePreId() != null ? r.getFilePreId() : null)
                || r.hasFiles(r.getFileMidId() != null ? r.getFileMidId() : null)
                || r.hasFiles(r.getFilePostId() != null ? r.getFilePostId() : null);
    }

    private boolean hasAllSlotFiles(BizIncentiveRecord r) {
        return r.hasFiles(r.getFilePreId() != null ? r.getFilePreId() : null)
                && r.hasFiles(r.getFileMidId() != null ? r.getFileMidId() : null)
                && r.hasFiles(r.getFilePostId() != null ? r.getFilePostId() : null);
    }

    /** 判断子类是否属于"外出参观/交流拜访"大类（category_code = 'visit'/'exchange'） */
    private boolean isVisitExchangeCategory(BizIncentiveCategory category) {
        if (category == null) return false;
        if (LIT_VISIT.equals(category.getCategoryCode()) || LIT_EXCHANGE.equals(category.getCategoryCode())) return true;
        if (category.getParentId() != null) {
            BizIncentiveCategory parent = categoryMapper.selectById(category.getParentId());
            return parent != null && (LIT_VISIT.equals(parent.getCategoryCode()) || LIT_EXCHANGE.equals(parent.getCategoryCode()));
        }
        return false;
    }

    /** 判断子类是否属于"两谈两做方案"大类（category_code = 'plan'） */
    private boolean isPlanCategory(BizIncentiveCategory category) {
        if (category == null) return false;
        if ("plan".equals(category.getCategoryCode())) return true;
        if (category.getParentId() != null) {
            BizIncentiveCategory parent = categoryMapper.selectById(category.getParentId());
            return parent != null && "plan".equals(parent.getCategoryCode());
        }
        return false;
    }

    // ========== 【2026-11-04 用户令"四项服务/四项激励也设置上对应的数据的权限"】统计家族数据范围收口 ==========
    // 与 StatisticsQueryService.customerRankings(visibleCustomers, 2026-11-02 客户可见范围基线)同模式:
    // 按当前账号 数据范围解析 的可见人员集过滤记录; SecurityUser 缺席(系统任务/测试)/admin/解析为 null(=全部)
    // 时不过滤(fail-open 同模式)。统计页签=客户维度收口(可见客户), 排行页签=人员维度收口(可见人员)。
    // ====== 统计/排行（S5.3 语义已移到 IncentiveStatsService） ======

    public Map<String, Object> categoryStats(String period) {
        return incentiveStatsService.categoryStats(period);
    }

    public void evictScoreboardCacheAfterRecycle() {
        incentiveStatsService.evictScoreboardCacheAfterRecycle();
    }

    private void evictScoreboardCache() {
        incentiveStatsService.evictScoreboardCacheAfterRecycle();
    }

    public List<Map<String, Object>> scoreboard(String period) {
        return incentiveStatsService.scoreboard(period);
    }

    public List<Map<String, Object>> deptRanking(String period) {
        return incentiveStatsService.deptRanking(period);
    }

    public int getScoreByCustomerId(Long customerId) {
        return incentiveStatsService.getScoreByCustomerId(customerId);
    }

    public List<Map<String, Object>> getCustomerRecords(IncentiveCustomerRecordQuery q) {
        return incentiveStatsService.getCustomerRecords(q);
    }

    @Transactional
    public void markRead(Long recordId) {
        incentiveStatsService.markRead(recordId);
    }

    public boolean canViewRecord(Long recordId) {
        return incentiveStatsService.canViewRecord(recordId);
    }

    public List<Map<String, Object>> readStats(String period) {
        return incentiveStatsService.readStats(period);
    }

    public Map<String, Object> readRanking(String period) {
        return incentiveStatsService.readRanking(period);
    }
}
