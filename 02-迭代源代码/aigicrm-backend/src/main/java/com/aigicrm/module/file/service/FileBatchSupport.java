package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;

import java.util.List;

/** 批量操作共用护栏。 */
final class FileBatchSupport {

    private FileBatchSupport() {
    }

    /** 【V2026.11.14.3 P2】批量上限(防误操作与超大请求), 超出直接 400。 */
    static void requireBatchLimit(List<Long> ids) {
        if (ids != null && ids.size() > 100) {
            throw new BusinessException("批量操作一次最多 100 项，请分批进行");
        }
    }
}
