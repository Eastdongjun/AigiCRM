package com.aigicrm.common.scope;

import com.aigicrm.common.BusinessException;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一归属校验（设计文档 §6）：写操作（PUT/DELETE/share）目标记录 owner
 * 必须 ∈ 当前用户可见范围；可见范围 = Resolver 结果 ∪ 分享授予（调用方传入）∪ 本人
 *
 * 【2026-10-27 深度检查标注】⚠️ 本组件当前无任何运行时消费者（实际归属守卫在各 Service 内联实现，
 * 本类仅作为设计落点保留并有测试覆盖）。若未来接线请注意其语义是 fail-open 家族：
 * resolver.resolve()==null（all 档）与 isVisible 的 scope==null 均按"放行"处理——
 * 对新写路径接线前必须先确认调用方已把 null 语义等价为"管理员/全量档"，
 * 否则应改用各 Service 的显式范围判定，不要默认本类兜底。
 */
@Component
@RequiredArgsConstructor
public class DataScopeGuard {

    private final OrgScopeResolver resolver;

    /** 校验目标记录是否当前用户可操作；scopeOverride=分享等额外可见集合 */
    public void checkOwner(Long ownerId, Collection<Long> scopeOverride) {
        if (ownerId == null) throw new BusinessException(403, "记录归属缺失");
        Long me = SecurityUtils.getCurrentUserId();
        if (me != null && me.equals(ownerId)) return; // 本人恒可
        List<Long> resolved = resolver.resolve(SecurityUtils.getCurrentUser());
        if (resolved == null) return; // all：不过滤
        Set<Long> scope = new LinkedHashSet<>(resolved);
        if (scopeOverride != null) scope.addAll(scopeOverride); // 并集：Resolver 结果 ∪ 分享授予
        if (!scope.contains(ownerId)) throw new BusinessException(403, "无权操作该记录");
    }

    /** 静态便捷：ownerId 是否在可见集合内（ownerIsSelf=调用方已确认本人） */
    public static boolean isVisible(Long ownerId, Collection<Long> scope, boolean ownerIsSelf) {
        if (ownerId == null) return false;
        if (ownerIsSelf) return true;
        if (scope == null) return true;
        return scope.contains(ownerId);
    }

    /** 静态便捷：checkOwnerInScope 供无 Spring 上下文的场景/测试使用 */
    public static void checkOwnerInScope(Long ownerId, Collection<Long> scope, boolean ownerIsSelf) {
        if (!isVisible(ownerId, scope, ownerIsSelf)) throw new BusinessException(403, "无权操作该记录");
    }
}
