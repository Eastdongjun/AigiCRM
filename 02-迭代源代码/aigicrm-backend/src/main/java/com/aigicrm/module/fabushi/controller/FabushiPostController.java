package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiPostService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 法布施内容端点(一期 2026-11-15): 浏览/详情/评论列表=view, 发布/评论/木鱼=create,
 * 下架=content:manage, 已读=view(浏览行为)。
 * 【ArchUnit controller_must_not_depend_on_entity】本类不 import 实体——body/返回全走 Map。
 */
@RestController
@RequestMapping("/api/fabushi/posts")
@RequiredArgsConstructor
public class FabushiPostController {

    private final FabushiPostService postService;

    /**
     * 内容分页(每日诵读/每日随施/再读一本, 全员社区流)。status 传参仅治理门生效
     * (fabushi:content:manage 可查下架记录, 其余强制 published 防越权翻看下架内容)。
     * 【D16 2026-11-23】keyword=服务端搜索(人名/书籍/具体文字), 已删帖由逻辑删自动滤除。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        boolean manageAllowed = SecurityUtils.hasAuthority("fabushi:content:manage");
        return Result.ok(postService.pageData(scopeType, bookId, status, manageAllowed,
                SecurityUtils.getCurrentUserId(), keyword, current, size));
    }

    /** 内容详情(帖+附件+互动计数; service 返回 Map)。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(id, SecurityUtils.getCurrentUserId()));
    }

    /** 发布(人人; scope_type 白名单校验在服务层)。 */
    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(postService.createData(body));
    }

    // ===== D13 2026-11-23 本人修改/删除 + 治理已删除（计划切片 2） =====

    /** 本人编辑(仅作者本人，admin 不代编辑；可改 title/content/sectionId，分型规则保持)。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Void> updateOwn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        postService.updateOwn(id, body);
        return Result.ok();
    }

    /** 本人删除(作者本人或 admin)：逻辑删除，附件文件保留文件中心。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Void> deleteOwn(@PathVariable Long id) {
        postService.deleteOwn(id);
        return Result.ok();
    }

    /** 治理侧"已删除"分页(仅 content:manage；自定义 SQL 绕过逻辑删)。 */
    @GetMapping("/deleted")
    @PreAuthorize("hasAuthority('fabushi:content:manage')")
    public Result<Map<String, Object>> deletedPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(postService.deletedPageData(current, size));
    }

    /** 治理侧恢复已删帖(content:manage)：回到 published。 */
    @PutMapping("/{id}/restore-deleted")
    @PreAuthorize("hasAuthority('fabushi:content:manage')")
    public Result<Void> restoreDeleted(@PathVariable Long id) {
        postService.restoreDeleted(id);
        return Result.ok();
    }

    /** 内容附件(二期, 作者/管理员; 文件须 scope=fabushi)。 */
    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> addAttachment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(postService.addAttachment(id, body));
    }

    @DeleteMapping("/{postId}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Void> removeAttachment(@PathVariable Long postId, @PathVariable Long attachmentId) {
        postService.removeAttachment(postId, attachmentId);
        return Result.ok();
    }

    /** 下架(治理, 原因必填留痕)。 */
    @PutMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('fabushi:content:manage')")
    public Result<Void> offline(@PathVariable Long id, @RequestBody Map<String, String> body) {
        postService.offline(id, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    /** 恢复已下架内容(治理, 全量留痕)。 */
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('fabushi:content:manage')")
    public Result<Void> restore(@PathVariable Long id) {
        postService.restore(id);
        return Result.ok();
    }

    /** 评论(文字; 二期扩 highlight/audio)。 */
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(postService.addCommentData(id, body));
    }

    /** 评论列表(service 返回 List<Map>; 编译期无实体引用)。 */
    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<List<Map<String, Object>>> listComments(@PathVariable Long id) {
        return Result.ok(postService.commentData(id));
    }

    /** 木鱼点赞 toggle(点赞=敲木鱼; 唯一键幂等)。 */
    @PostMapping("/{id}/like")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long id) {
        return Result.ok(postService.toggleLike(id, SecurityUtils.getCurrentUserId()));
    }
}
