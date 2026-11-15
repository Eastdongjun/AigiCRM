package com.aigicrm.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行(2026-11-15 权限写一致性)。
 *
 * <p>权限缓存失效必须在数据库提交之后执行: 事务内提前 evict 会让并发请求在提交前
 * 重新加载旧数据并写回缓存, 造成“保存成功但下一次请求仍读旧范围”。无事务时直接执行。</p>
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (action == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
