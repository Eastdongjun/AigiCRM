package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFileGrant;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 【A2 §2.5 2026-11-18】旧配置入口双写 biz_file_grant。
 *
 * <p>旧表 {@code biz_folder_type_default} 仍由“文件权限”页保存；切换期（mode=legacy/dual）必须双写，
 * 否则管理员新配置只进旧表、新引擎看不到。映射与迁移草案 V2026.11.18.2 逐口径一致（含附录 A 修订）：</p>
 *
 * <ul>
 *   <li>FILE/FOLDER + folder_id NULL → SPACE（object_id=NULL；space_type=folder_type，blank→NULL）；</li>
 *   <li>folder_id 非空 → FOLDER（object_id=folder_id，inherit=1）；SPACE 行 inherit=0；</li>
 *   <li>perms 展开：create 保留；rename/replace 归并 edit；去重；</li>
 *   <li>scope_level NULL/blank/all → scope_mode=ANY、scope_level=NULL；self/dept/office/region → RELATIVE 保留；</li>
 *   <li>旧 scope_org_id 被旧引擎忽略 → 新行 scope_org_id=NULL；effect=ALLOW；过期原样；</li>
 *   <li>legacy_ref='ftd:&lt;旧id&gt;:&lt;动作&gt;'——已存在（迁移/上次双写）则按行更新并保留 source
 *       （legacy/dual_write），不存在插 source='dual_write'；动作集合变化先删前缀下已不存在的动作行。</li>
 * </ul>
 *
 * <p>事务由调用方（FolderTypeDefaultService 保存/续期/删除入口 @Transactional）保证：旧表与新表同数据源同事务，
 * 双写失败回滚整个保存，不允许“旧表成功/新表失败”。</p>
 */
@Service
@RequiredArgsConstructor
public class LegacyFileGrantSyncService {

    /** perms 展开词表（与迁移 SQL JOIN 集一致） */
    private static final List<String> ACTION_VOCAB = List.of(
            "view", "download", "upload", "edit", "rename", "replace",
            "delete", "share", "manage", "create");
    private static final Set<String> LEGACY_SOURCES = Set.of("legacy", "dual_write");

    private final BizFileGrantMapper grantMapper;

    /** 保存/续期后按新旧行内容 upsert 双写行。 */
    public void syncUpsert(BizFolderTypeDefault old) {
        if (old == null || old.getId() == null) return;
        Set<String> actions = expandActions(old.getPerms());
        Long tenant = tenantId();
        // 动作集合变化：先删该 ftd:<id>: 前缀下已不在当前动作集的 legacy/dual_write 行
        for (BizFileGrant existing : rowsOf(old.getId())) {
            String action = existing.getLegacyRef() == null ? null
                    : existing.getLegacyRef().substring(existing.getLegacyRef().lastIndexOf(':') + 1);
            if (!actions.contains(action)) {
                grantMapper.deleteById(existing.getId());
            }
        }
        for (String action : actions) {
            String ref = refOf(old.getId(), action);
            BizFileGrant existing = selectByRef(ref);
            if (existing != null && LEGACY_SOURCES.contains(existing.getSource() == null ? "" : existing.getSource())) {
                updateByRef(existing.getId(), old, action, tenant);
            } else if (existing == null) {
                BizFileGrant g = mapRow(old, action, ref, tenant);
                grantMapper.insert(g);
            }
            // source=manual 的同 legacy_ref 行不应存在（手工行 legacy_ref=NULL），防御性跳过不触碰
        }
    }

    /** 旧表行删除后清空该前缀下全部 legacy/dual_write 行。 */
    public void syncDelete(Long oldId) {
        if (oldId == null) return;
        for (BizFileGrant existing : rowsOf(oldId)) {
            grantMapper.deleteById(existing.getId());
        }
    }

    // ==================== 映射（与 V2026.11.18.2 迁移草案逐口径一致） ====================

    private BizFileGrant mapRow(BizFolderTypeDefault old, String action, String ref, Long tenant) {
        BizFileGrant g = new BizFileGrant();
        g.setTenantId(tenant);
        g.setSubjectType(old.getSubjectType());
        g.setSubjectId(old.getSubjectId());
        g.setObjectType(objectTypeOf(old));
        g.setObjectId(old.getFolderId());
        g.setSpaceType(spaceTypeOf(old));
        g.setAction(action);
        g.setEffect("ALLOW");
        g.setInherit(old.getFolderId() != null ? 1 : 0);
        g.setScopeOrgId(null);                      // 旧引擎忽略旧 scope_org_id，迁移不得当绝对组织
        g.setScopeLevel(scopeLevelOf(old));
        g.setScopeMode(scopeLevelOf(old) == null ? FileGrantService.SCOPE_ANY : FileGrantService.SCOPE_RELATIVE);
        g.setExpireAt(old.getExpiresAt());
        g.setCreatedBy(old.getCreatedBy());
        g.setCreatedAt(old.getCreatedAt());
        g.setLegacyRef(ref);
        g.setSource("dual_write");
        return g;
    }

    /** 按行更新（显式逐列 set，null 也写入——与 FolderTypeDefaultService C区修复同模式）。 */
    private void updateByRef(Long id, BizFolderTypeDefault old, String action, Long tenant) {
        grantMapper.update(null, new LambdaUpdateWrapper<BizFileGrant>()
                .eq(BizFileGrant::getId, id)
                .set(BizFileGrant::getTenantId, tenant)
                .set(BizFileGrant::getSubjectType, old.getSubjectType())
                .set(BizFileGrant::getSubjectId, old.getSubjectId())
                .set(BizFileGrant::getObjectType, objectTypeOf(old))
                .set(BizFileGrant::getObjectId, old.getFolderId())
                .set(BizFileGrant::getSpaceType, spaceTypeOf(old))
                .set(BizFileGrant::getAction, action)
                .set(BizFileGrant::getEffect, "ALLOW")
                .set(BizFileGrant::getInherit, old.getFolderId() != null ? 1 : 0)
                .set(BizFileGrant::getScopeOrgId, null)
                .set(BizFileGrant::getScopeLevel, scopeLevelOf(old))
                .set(BizFileGrant::getScopeMode,
                        scopeLevelOf(old) == null ? FileGrantService.SCOPE_ANY : FileGrantService.SCOPE_RELATIVE)
                .set(BizFileGrant::getExpireAt, old.getExpiresAt()));
    }

    private String objectTypeOf(BizFolderTypeDefault old) {
        return old.getFolderId() == null ? FileGrantService.OBJ_SPACE : FileGrantService.OBJ_FOLDER;
    }

    /** B-3 同款 blank 归一：空串/纯空白 → NULL（=任意空间）。 */
    private String spaceTypeOf(BizFolderTypeDefault old) {
        if (old.getFolderId() != null) return null;
        String t = old.getFolderType();
        return t == null || t.trim().isEmpty() ? null : t.trim();
    }

    /** NULL/blank/all → NULL；其余原样（self/dept/office/region）。 */
    private String scopeLevelOf(BizFolderTypeDefault old) {
        String lv = old.getScopeLevel();
        return lv == null || lv.isBlank() || "all".equals(lv) ? null : lv;
    }

    /** perms CSV 展开：词表过滤 + rename/replace→edit 归并 + 保序去重（create 保留）。 */
    Set<String> expandActions(String perms) {
        Set<String> out = new LinkedHashSet<>();
        if (perms == null || perms.isBlank()) return out;
        String csv = perms.replace(" ", "");
        for (String a : ACTION_VOCAB) {
            if (!indexInCsv(csv, a)) continue;
            // rename/replace 归并 edit；LinkedHashSet 保序去重（edit 仅占一席）
            out.add("rename".equals(a) || "replace".equals(a) ? "edit" : a);
        }
        return out;
    }

    /** CSV 词级匹配（与 SQL FIND_IN_SET 等价）：按逗号切分精确比较。 */
    private boolean indexInCsv(String csv, String action) {
        for (String token : csv.split(",")) {
            if (action.equals(token)) return true;
        }
        return false;
    }

    private String refOf(Long oldId, String action) {
        return "ftd:" + oldId + ":" + action;
    }

    private List<BizFileGrant> rowsOf(Long oldId) {
        return grantMapper.selectList(new LambdaQueryWrapper<BizFileGrant>()
                .likeRight(BizFileGrant::getLegacyRef, "ftd:" + oldId + ":")
                .in(BizFileGrant::getSource, LEGACY_SOURCES));
    }

    private BizFileGrant selectByRef(String ref) {
        return grantMapper.selectOne(new LambdaQueryWrapper<BizFileGrant>()
                .eq(BizFileGrant::getLegacyRef, ref));
    }

    /** 与 FileGrantService 同口径：当前集团，缺席默认 1（旧表 tenant DB 默认 1）。 */
    private Long tenantId() {
        SecurityUser u = SecurityUtils.getCurrentUser();
        Long id = u == null ? null : u.getCurrentGroupId();
        return id == null ? 1L : id;
    }
}
