package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.system.entity.SysDictData;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysDictService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 法布施书目服务(一期 2026-11-15): 人人提交(pending) + admin 审核制(需求 A2 防泛滥)。
 * 类别校验走字典 fabushi_category(字典驱动裁决: admin 在字典管理改类别, 停用不删内容)。
 */
@Service
@RequiredArgsConstructor
public class FabushiBookService {

    private final BizFabushiBookMapper bookMapper;
    private final BizFabushiPostMapper postMapper;
    private final com.aigicrm.module.file.mapper.BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysDictService dictService;
    private final com.aigicrm.security.SecurityAuditService securityAuditService;

    /** 挂载 PDF 大小上限: 与前端文案一致(挂载电子书 PDF「≤100MB」, book.vue)。 */
    static final long MAX_PDF_BYTES = 100L * 1024 * 1024;
    /** file_type 存储口径 = 扩展名大写(FileService 上传时 toUpperCase), 无扩展名=空串。 */
    private static final String FILE_TYPE_PDF = "PDF";

    /** 提交人/类别名联显(字典 label + sys_user real_name)。 */
    private void fillDisplay(List<BizFabushiBook> books) {
        if (books.isEmpty()) {
            return;
        }
        Map<String, String> catNames = dictService.listDataByCode("fabushi_category").stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel, (a, b) -> a));
        Set<Long> userIds = books.stream().map(BizFabushiBook::getCreatedBy).collect(Collectors.toSet());
        Map<Long, String> userNames = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        for (BizFabushiBook b : books) {
            b.setCategoryName(catNames.get(b.getCategoryCode()));
            b.setSubmitterName(userNames.get(b.getCreatedBy()));
        }
        List<BizFabushiPost> posts = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                .in(BizFabushiPost::getBookId, books.stream().map(BizFabushiBook::getId).toList())
                .isNotNull(BizFabushiPost::getBookId)
                .eq(BizFabushiPost::getStatus, "published"));
        Map<Long, List<BizFabushiPost>> byBook = posts.stream().collect(Collectors.groupingBy(BizFabushiPost::getBookId));
        for (BizFabushiBook b : books) {
            List<BizFabushiPost> items = byBook.getOrDefault(b.getId(), List.of());
            b.setPostCount((long) items.size());
            b.setHeartCount(items.stream().filter(p -> "heart".equals(p.getScopeType())).count());
            b.setRecitationCount(items.stream().filter(p -> "recitation".equals(p.getScopeType())).count());
        }
    }

    /** 书目详情(fabushi:view 门): 非 approved 仅 book:manage 可看。 */
    public Map<String, Object> detailData(Long id) {
        BizFabushiBook book = requireBook(id);
        if (!"approved".equals(book.getStatus())
                && !com.aigicrm.security.SecurityUtils.hasAuthority("fabushi:book:manage")) {
            throw new BusinessException(404, "书目不存在");
        }
        fillDisplay(List.of(book));
        return Map.of("book", book);
    }

    /** 书目列表(fabushi:view 门): approved 全员可见; manage 门可用 status 传参查全态。 */
    public Page<BizFabushiBook> page(String categoryCode, String keyword, String status, long current, long size) {
        String st = (status == null || status.isBlank()) ? "approved" : status;
        boolean canManage = com.aigicrm.security.SecurityUtils.hasAuthority("fabushi:book:manage");
        if (!"approved".equals(st) && !canManage) st = "approved";
        LambdaQueryWrapper<BizFabushiBook> qw = new LambdaQueryWrapper<BizFabushiBook>()
                .eq(!"all".equals(st), BizFabushiBook::getStatus, st)
                .eq(categoryCode != null && !categoryCode.isBlank(), BizFabushiBook::getCategoryCode, categoryCode)
                .like(keyword != null && !keyword.isBlank(), BizFabushiBook::getTitle, keyword)
                .orderByDesc(BizFabushiBook::getCreatedAt);
        Page<BizFabushiBook> page = bookMapper.selectPage(new Page<>(current, size), qw);
        fillDisplay(page.getRecords());
        return page;
    }

    /** 人人提交书目(fabushi:create 门): 一律 pending 待审核。 */
    public BizFabushiBook submit(BizFabushiBook book) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        validateCategory(book.getCategoryCode());
        if (book.getTitle() == null || book.getTitle().isBlank()) {
            throw new BusinessException(400, "书名不能为空");
        }
        book.setId(null);
        book.setStatus("pending");
        book.setCreatedBy(user.getUserId());
        book.setApprovedBy(null);
        book.setApprovedAt(null);
        book.setRejectReason(null);
        bookMapper.insert(book);
        return book;
    }

    /** 审核通过(fabushi:book:manage 门; G3 留痕: LogAspect 切面 + PERMISSION_CHANGE 时间线)。 */
    @Transactional
    public void approve(Long id) {
        BizFabushiBook book = requireBook(id);
        book.setStatus("approved");
        book.setApprovedBy(com.aigicrm.security.SecurityUtils.getCurrentUserId());
        book.setApprovedAt(LocalDateTime.now());
        book.setRejectReason(null);
        bookMapper.updateById(book);
        securityAuditService.permissionChange("fabushi_book_approve", "book:" + id + ":" + book.getTitle());
    }

    /** 驳回(fabushi:book:manage 门; 驳回原因必填留痕)。 */
    @Transactional
    public void reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "驳回原因不能为空");
        }
        BizFabushiBook book = requireBook(id);
        book.setStatus("rejected");
        book.setRejectReason(reason);
        book.setApprovedBy(com.aigicrm.security.SecurityUtils.getCurrentUserId());
        bookMapper.updateById(book);
        securityAuditService.permissionChange("fabushi_book_reject", "book:" + id + ":" + book.getTitle());
    }

    /** 停用(fabushi:book:manage 门; 停用不删内容, 书目从全员列表消失)。 */
    @Transactional
    public void disable(Long id) {
        BizFabushiBook book = requireBook(id);
        book.setStatus("disabled");
        bookMapper.updateById(book);
        securityAuditService.permissionChange("fabushi_book_disable", "book:" + id + ":" + book.getTitle());
    }

    /** PDF 挂载/替换(fabushi:book:manage 门; fileId 须为 scope=fabushi 的已上传 PDF 文件, ≤100MB)。 */
    @Transactional
    public void attachPdf(Long id, Long fileId) {
        BizFabushiBook book = requireBook(id);
        if (fileId == null) {
            throw new BusinessException(400, "PDF 文件 id 不能为空");
        }
        com.aigicrm.module.file.entity.BizFile f = fileMapper.selectById(fileId);
        if (f == null || (f.getDeleted() != null && f.getDeleted() == 1)) {
            throw new BusinessException(404, "文件不存在");
        }
        if (!"fabushi".equals(f.getFileScope())) {
            throw new BusinessException(400, "PDF 须上传到法布施空间(scope=fabushi)");
        }
        // 2026-11-17 U1/U2 缺口修复: 类型/大小校验收口在挂载点(单点治本, 不影响 feed 附件上传)
        if (!FILE_TYPE_PDF.equals(f.getFileType())) {
            throw new BusinessException(400, "仅支持 PDF 文件(当前文件类型: "
                    + (f.getFileType() == null || f.getFileType().isEmpty() ? "未知" : f.getFileType()) + ")");
        }
        if (f.getFileSize() != null && f.getFileSize() > MAX_PDF_BYTES) {
            throw new BusinessException(400, "PDF 不能超过 100MB(当前 "
                    + (f.getFileSize() / (1024 * 1024)) + "MB)");
        }
        book.setPdfFileId(fileId);
        bookMapper.updateById(book);
    }

    /** 待审核队列(fabushi:book:manage 门)。 */
    public List<BizFabushiBook> pendingList() {
        List<BizFabushiBook> books = bookMapper.selectList(new LambdaQueryWrapper<BizFabushiBook>()
                .eq(BizFabushiBook::getStatus, "pending")
                .orderByAsc(BizFabushiBook::getCreatedAt));
        fillDisplay(books);
        return books;
    }

    // ===== Controller 层 Map 化输出(ArchUnit: controller_must_not_depend_on_entity——
    //      Controller 不直用 Entity, 实体组装/转换收在 service; records 元素运行时实体、编译期 Map) =====

    /** 书目分页 Map 化(records/total)。 */
    public Map<String, Object> pageData(String categoryCode, String keyword, String status, long current, long size) {
        Page<BizFabushiBook> p = page(categoryCode, keyword, status, current, size);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return data;
    }

    /** 提交书目 Map 化(前端 body → 实体组装收在 service)。 */
    public Map<String, Object> submitData(Map<String, Object> body) {
        BizFabushiBook book = new BizFabushiBook();
        book.setCategoryCode(body.get("categoryCode") == null ? null : String.valueOf(body.get("categoryCode")));
        book.setTitle(body.get("title") == null ? null : String.valueOf(body.get("title")));
        book.setAuthor(body.get("author") == null ? null : String.valueOf(body.get("author")));
        book.setPublisher(body.get("publisher") == null ? null : String.valueOf(body.get("publisher")));
        book.setEditionType(body.get("editionType") == null ? null : String.valueOf(body.get("editionType")));
        if (body.get("pages") != null) {
            book.setPages(Integer.valueOf(String.valueOf(body.get("pages"))));
        }
        BizFabushiBook created = submit(book);
        return Map.of("id", created.getId(), "status", created.getStatus());
    }

    /** 待审核队列 Map 化。 */
    public Map<String, Object> pendingData() {
        return Map.of("records", pendingList());
    }

    private void validateCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new BusinessException(400, "类别不能为空");
        }
        boolean valid = dictService.listDataByCode("fabushi_category").stream()
                .anyMatch(d -> categoryCode.equals(d.getDictValue()) && Integer.valueOf(1).equals(d.getStatus()));
        if (!valid) {
            throw new BusinessException(400, "类别不存在或已停用");
        }
    }

    /** 删除书目(fabushi:book:manage 门; @TableLogic 软删, 关联心得/书架/进度保留留痕, 全部查询自动过滤)。 */
    public void deleteBook(Long id) {
        BizFabushiBook book = requireBook(id);
        bookMapper.deleteById(id);
        securityAuditService.permissionChange("fabushi_book_delete", "book:" + id + ":" + book.getTitle());
    }

    private BizFabushiBook requireBook(Long id) {
        BizFabushiBook book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException(404, "书目不存在");
        }
        return book;
    }
}
