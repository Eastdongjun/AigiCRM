package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 法布施书目端点(一期 2026-11-15): 浏览=view / 人人提交=create / 审核·挂载·停用=book:manage。
 * 【ArchUnit controller_must_not_depend_on_entity】本类不 import 实体——body/返回全走 Map,
 * 组装与转换收在 service(实体类型不出现在 controller 字节码)。
 */
@RestController
@RequestMapping("/api/fabushi/books")
@RequiredArgsConstructor
public class FabushiBookController {

    private final FabushiBookService bookService;

    /** 书目列表: 默认 approved(全员书库); manage 门可传 status 查其他状态。 */
    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(bookService.pageData(categoryCode, keyword, status, current, size));
    }

    /** 书目详情(view; 非 approved 仅 book:manage)。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(bookService.detailData(id));
    }

    /** 待审核队列(审核制专用)。 */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Map<String, Object>> pending() {
        return Result.ok(bookService.pendingData());
    }

    /** 人人提交书目(待审核态)。 */
    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return Result.ok(bookService.submitData(body));
    }

    /** 审核通过。 */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> approve(@PathVariable Long id) {
        bookService.approve(id);
        return Result.ok();
    }

    /** 驳回(原因必填)。 */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        bookService.reject(id, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    /** 停用(不删内容)。 */
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> disable(@PathVariable Long id) {
        bookService.disable(id);
        return Result.ok();
    }

    /** 电子书 PDF 挂载/替换(fileId=已上传到 scope=fabushi 的文件)。 */
    @PostMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    @com.aigicrm.common.annotation.AigicrmLog(module = "法布施", action = "挂载PDF", targetType = "file")
    public Result<Void> attachPdf(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        bookService.attachPdf(id, body == null ? null : body.get("fileId"));
        return Result.ok();
    }

    /** 【D19 切片 7】管理员确认版式标记：text=文本版 / scan=影印版（影响阅读器默认版式）。 */
    @PutMapping("/{id}/layout")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> updateLayout(@PathVariable Long id, @RequestBody Map<String, String> body) {
        bookService.updateLayoutType(id, body == null ? null : body.get("layoutType"));
        return Result.ok();
    }

    /** 删除书目(软删, @TableLogic; 关联心得/书架/进度保留留痕, 列表自动过滤)。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return Result.ok();
    }
}
