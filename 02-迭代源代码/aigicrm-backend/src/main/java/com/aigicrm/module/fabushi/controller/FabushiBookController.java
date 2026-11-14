package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.service.FabushiBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 法布施书目端点(一期 2026-11-15): 浏览=view / 人人提交=create / 审核·挂载·停用=book:manage。 */
@RestController
@RequestMapping("/api/fabushi/books")
@RequiredArgsConstructor
public class FabushiBookController {

    private final FabushiBookService bookService;

    /** 书目列表: 默认 approved(全员书库); manage 门可传 status 查其他状态。 */
    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<BizFabushiBook>> page(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(bookService.page(categoryCode, keyword, status, current, size));
    }

    /** 待审核队列(审核制专用)。 */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<List<BizFabushiBook>> pending() {
        return Result.ok(bookService.pendingList());
    }

    /** 人人提交书目(待审核态)。 */
    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<BizFabushiBook> submit(@RequestBody BizFabushiBook book) {
        return Result.ok(bookService.submit(book));
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
    public Result<Void> attachPdf(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        bookService.attachPdf(id, body == null ? null : body.get("fileId"));
        return Result.ok();
    }
}
