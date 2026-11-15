package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.system.entity.SysDictData;
import com.aigicrm.module.system.entity.SysDictType;
import com.aigicrm.module.system.mapper.SysDictDataMapper;
import com.aigicrm.module.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final BizCustomerMapper customerMapper;

    /** 【2026-11-16】字典模块固定枚举：与菜单/权限模块对齐，空模块也可在新增下拉选择 */
    private static final Map<String, String> DICT_MODULES = new LinkedHashMap<>();
    static {
        DICT_MODULES.put("customer", "客户管理");
        DICT_MODULES.put("file", "文件管理");
        DICT_MODULES.put("incentive", "四项激励");
        DICT_MODULES.put("service", "四项服务");
        DICT_MODULES.put("sea", "公海管理");
        DICT_MODULES.put("workflow", "流程协同");
        DICT_MODULES.put("score", "学分管理");
        DICT_MODULES.put("fabushi", "法布施");
        DICT_MODULES.put("statistics", "统计分析");
        DICT_MODULES.put("system", "系统管理");
        DICT_MODULES.put("common", "通用");
    }

    private String normalizeModuleCode(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) return "common";
        if (!DICT_MODULES.containsKey(moduleCode)) {
            throw new BusinessException(400, "未知字典模块: " + moduleCode);
        }
        return moduleCode;
    }

    /** 模块下拉选项（只读；空模块保留，便于未来扩展） */
    public List<Map<String, Object>> listModules() {
        List<Map<String, Object>> out = new ArrayList<>();
        int sort = 1;
        for (Map.Entry<String, String> e : DICT_MODULES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", e.getKey());
            row.put("name", e.getValue());
            row.put("sort", sort++);
            out.add(row);
        }
        return out;
    }

    /** 【P2 修复 2026-11-02】同一字典类型内 dictLabel/dictValue 唯一(编辑时排除自身) */
    private void assertDictDataUnique(SysDictData data) {
        if (data.getDictTypeId() == null) return;
        for (SysDictData existing : dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeId, data.getDictTypeId()))) {
            if (data.getId() != null && data.getId().equals(existing.getId())) continue;
            if (data.getDictLabel() != null && data.getDictLabel().equals(existing.getDictLabel())) {
                throw new BusinessException("字典标签「" + data.getDictLabel() + "」在该类型下已存在");
            }
            if (data.getDictValue() != null && data.getDictValue().equals(existing.getDictValue())) {
                throw new BusinessException("字典键值「" + data.getDictValue() + "」在该类型下已存在");
            }
        }
    }

    // 字典code → 客户表字段的映射
    private static final Map<String, String> DICT_COLUMN_MAP = Map.of(
            "industry", "industry",
            "dev_type", "dev_type"
    );

    // ===== 字典类型 =====

    public List<SysDictType> listTypes() {
        return dictTypeMapper.selectList(
                new LambdaQueryWrapper<SysDictType>()
                        .orderByAsc(SysDictType::getModuleCode)
                        .orderByAsc(SysDictType::getId));
    }

    public void createType(SysDictType type) {
        Long count = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getDictCode, type.getDictCode()));
        if (count > 0) {
            throw new BusinessException("字典编码已存在");
        }
        type.setModuleCode(normalizeModuleCode(type.getModuleCode()));
        dictTypeMapper.insert(type);
    }

    public void updateType(SysDictType type) {
        // 未传 moduleCode 时保留原值（MyBatis-Plus 忽略 null）；显式空串归一 common
        if (type.getModuleCode() != null) {
            type.setModuleCode(normalizeModuleCode(type.getModuleCode()));
        }
        dictTypeMapper.updateById(type);
    }

    @Transactional
    public void deleteType(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type != null && Integer.valueOf(1).equals(type.getBuiltIn())) {
            throw new BusinessException("系统内置字典不允许删除");
        }
        dictDataMapper.delete(
                new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictTypeId, id));
        dictTypeMapper.deleteById(id);
    }

    // ===== 字典数据 =====

    public List<SysDictData> listDataByCode(String dictCode) {
        SysDictType type = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getDictCode, dictCode));
        if (type == null) return List.of();
        return dictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictTypeId, type.getId())
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getSortOrder));
    }

    public List<SysDictData> listDataByTypeId(Long typeId) {
        return dictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictTypeId, typeId)
                        .orderByAsc(SysDictData::getSortOrder));
    }

    public void createData(SysDictData data) {
        // 【P2 修复 2026-11-02】字典项查重: 同一字典类型内 dictLabel/dictValue 均不得重复
        // (原无查重, 可录重复项致下拉同名/引用键歧义)
        assertDictDataUnique(data);
        dictDataMapper.insert(data);
    }

    @Transactional
    public void updateData(SysDictData data) {
        // 查旧值，判断 dictValue 是否变了
        SysDictData old = dictDataMapper.selectById(data.getId());
        assertDictDataUnique(data);
        dictDataMapper.updateById(data);

        if (old != null && old.getDictValue() != null
                && !old.getDictValue().equals(data.getDictValue())) {
            // 找到该字典项所属的 dictCode
            SysDictType type = dictTypeMapper.selectById(old.getDictTypeId());
            if (type != null) {
                String column = DICT_COLUMN_MAP.get(type.getDictCode());
                if (column != null) {
                    // 同步更新客户表中所有旧值
                    customerMapper.update(null,
                            new LambdaUpdateWrapper<BizCustomer>()
                                    .eq("industry".equals(column)
                                            ? BizCustomer::getIndustry
                                            : BizCustomer::getDevType, old.getDictValue())
                                    .set("industry".equals(column)
                                            ? BizCustomer::getIndustry
                                            : BizCustomer::getDevType, data.getDictValue()));
                }
            }
        }
    }

    public void deleteData(Long id) {
        dictDataMapper.deleteById(id);
    }
}
