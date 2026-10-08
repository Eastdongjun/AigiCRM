package com.aigicrm.module.common.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.common.entity.BizFormDraft;
import com.aigicrm.module.common.mapper.BizFormDraftMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 【议题四裁决 2026-09-19】表单草稿服务。
 * <ul>
 *   <li><b>隔离</b>:按 账号+集团+草稿类型 唯一;切集团后旧集团草稿不可见;</li>
 *   <li><b>保留期 7 天</b>:懒过期(读取时判断,过期即删并返回空);</li>
 *   <li><b>冲突检测</b>:客户端提交 version(上次读到的),不匹配 → 409 + 服务器当前草稿,
 *       客户端提示用户选择覆盖或使用服务器版本(裁决:提示+选择);</li>
 *   <li><b>立即清理</b>:用户停用/删除 → deleteByUserId;切集团 → 旧集团草稿即时失效
 *       (通过隔离键自然隔离,无需额外删;停用钩子在 SysUserService 调用)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormDraftService {

    private static final int RETENTION_DAYS = 7;

    private final BizFormDraftMapper draftMapper;

    /** 读取当前用户在当前集团的指定类型草稿;7 天过期返回 null(懒清理)。 */
    public BizFormDraft getDraft(String draftType, Long userId, Long groupId) {
        BizFormDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<BizFormDraft>()
                .eq(BizFormDraft::getUserId, userId)
                .eq(BizFormDraft::getGroupId, groupId)
                .eq(BizFormDraft::getDraftType, draftType));
        if (draft == null) return null;
        if (draft.getUpdatedAt() != null
                && draft.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(RETENTION_DAYS))) {
            draftMapper.deleteById(draft.getId());
            return null;
        }
        return draft;
    }

    /**
     * 保存/更新草稿(乐观锁)。
     * @param clientVersion 客户端持有的版本(首次保存传 0 或 null;恢复草稿后保存传服务端 version)
     * @return 保存后的草稿(含新 version)
     * @throws BusinessException 409 当客户端版本与服务器不一致(他端已修改)——
     *         附带当前服务器草稿供客户端展示"覆盖/使用服务器版本"选择
     */
    public BizFormDraft saveDraft(String draftType, Long userId, Long groupId,
                                  String draftData, Integer clientVersion) {
        BizFormDraft existing = draftMapper.selectOne(new LambdaQueryWrapper<BizFormDraft>()
                .eq(BizFormDraft::getUserId, userId)
                .eq(BizFormDraft::getGroupId, groupId)
                .eq(BizFormDraft::getDraftType, draftType));
        int clientV = clientVersion != null ? clientVersion : 0;
        if (existing != null) {
            int serverV = existing.getVersion() != null ? existing.getVersion() : 1;
            if (serverV != clientV) {
                // 冲突:客户端持有的版本与服务端不一致(另一端已保存过)
                throw new ConflictException(existing);
            }
            // 【P2 2026-10-08 复审修复·乐观锁原子化】旧实现"查版本→updateById"两步非原子:
            // 两请求同读同版本都过判, 后写静默覆盖先写。改为 WHERE id=? AND version=? 条件更新
            // (MyBatis-Plus wrapper 参数化绑定), 影响行数 0 = 并发写者已胜出 → 回读最新草稿抛 409。
            int rows = draftMapper.update(null, new LambdaUpdateWrapper<BizFormDraft>()
                    .eq(BizFormDraft::getId, existing.getId())
                    .eq(BizFormDraft::getVersion, serverV)
                    .set(BizFormDraft::getDraftData, draftData)
                    .set(BizFormDraft::getVersion, serverV + 1));
            if (rows == 0) {
                BizFormDraft current = draftMapper.selectById(existing.getId());
                throw new ConflictException(current != null ? current : existing);
            }
            existing.setDraftData(draftData);
            existing.setVersion(serverV + 1);
            return existing;
        }
        if (clientV > 0) {
            // 客户端以为有草稿但服务端已无(过期清理或被删)——按新草稿处理,不视为冲突
            log.debug("草稿已不存在(过期/清理),按新建处理 type={} userId={}", draftType, userId);
        }
        BizFormDraft draft = new BizFormDraft();
        draft.setUserId(userId);
        draft.setGroupId(groupId);
        draft.setDraftType(draftType);
        draft.setDraftData(draftData);
        draft.setVersion(1);
        draftMapper.insert(draft);
        return draft;
    }

    /** 强制覆盖保存(用户在冲突弹窗选择"使用我的版本"时调用)。 */
    public BizFormDraft forceSaveDraft(String draftType, Long userId, Long groupId, String draftData) {
        BizFormDraft existing = draftMapper.selectOne(new LambdaQueryWrapper<BizFormDraft>()
                .eq(BizFormDraft::getUserId, userId)
                .eq(BizFormDraft::getGroupId, groupId)
                .eq(BizFormDraft::getDraftType, draftType));
        if (existing != null) {
            existing.setDraftData(draftData);
            existing.setVersion((existing.getVersion() != null ? existing.getVersion() : 1) + 1);
            draftMapper.updateById(existing);
            return existing;
        }
        return saveDraft(draftType, userId, groupId, draftData, 0);
    }

    /** 删除草稿(表单成功提交后调用)。 */
    public void deleteDraft(String draftType, Long userId, Long groupId) {
        draftMapper.delete(new LambdaQueryWrapper<BizFormDraft>()
                .eq(BizFormDraft::getUserId, userId)
                .eq(BizFormDraft::getGroupId, groupId)
                .eq(BizFormDraft::getDraftType, draftType));
    }

    /** 【议题四裁决】用户停用/删除时立即清理全部草稿(SysUserService 钩子调用)。 */
    public void purgeByUser(Long userId) {
        draftMapper.deleteByUserId(userId);
    }

    /** 批量过期清理(可由定时任务或任意读取触发;懒过期已兜底,此方法为主动清扫)。 */
    public int purgeExpired() {
        return draftMapper.purgeExpired(RETENTION_DAYS);
    }

    /** entity → Map(控制器不依赖 entity,分层约束) */
    public static java.util.Map<String, Object> toRow(BizFormDraft d) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        if (d == null) return row;
        row.put("draftType", d.getDraftType());
        row.put("draftData", d.getDraftData());
        row.put("version", d.getVersion());
        row.put("updatedAt", d.getUpdatedAt());
        return row;
    }

    /** 冲突异常:携带服务器当前草稿供客户端展示选择。 */
    public static class ConflictException extends BusinessException {
        private final BizFormDraft serverDraft;

        public ConflictException(BizFormDraft serverDraft) {
            super(409, "草稿已在其他设备修改");
            this.serverDraft = serverDraft;
        }

        public BizFormDraft getServerDraft() {
            return serverDraft;
        }

        public Map<String, Object> toBody() {
            return Map.of(
                    "code", 409,
                    "message", "草稿已在其他设备修改",
                    "serverVersion", serverDraft.getVersion(),
                    "serverDraftData", serverDraft.getDraftData() != null ? serverDraft.getDraftData() : "");
        }
    }
}
