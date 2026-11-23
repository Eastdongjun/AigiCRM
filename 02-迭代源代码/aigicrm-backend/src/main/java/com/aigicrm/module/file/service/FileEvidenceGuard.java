package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文件是否被激励记录引用（计分佐证）的判定。
 *
 * <p>S5.3 从 {@link FileService} 抽出：替换、回滚、回收站彻底删除三处都要用同一判定，
 * 抽成独立 bean 避免版本服务反向依赖 FileService。</p>
 */
@Service
@RequiredArgsConstructor
public class FileEvidenceGuard {

    private final BizIncentiveRecordMapper incentiveRecordMapper;

    /** 【决策5 2026-10-18 用户裁决·佐证锁定】被任一激励记录五槽引用即禁替换/回滚。 */
    public void assertNotIncentiveEvidence(Long fileId) {
        if (isIncentiveEvidence(fileId)) {
            throw new BusinessException("该文件是激励计分佐证，不能替换；请在激励模块删除后重新上传（将重新计分）");
        }
    }

    /** 【回收站 2026-10-18】文件是否被任一激励记录五槽引用(供彻底删除守卫复用, 与替换守卫同源)。 */
    public boolean isIncentiveEvidence(Long fileId) {
        for (BizIncentiveRecord r : incentiveRecordMapper.selectList(new LambdaQueryWrapper<>())) {
            if (r.getFileIds().contains(fileId) || r.getFilePreIds().contains(fileId)
                    || r.getFileMidIds().contains(fileId) || r.getFilePostIds().contains(fileId)
                    || r.getFileTrackIds().contains(fileId)) {
                return true;
            }
        }
        return false;
    }
}
