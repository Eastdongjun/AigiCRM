package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.system.entity.SysDictData;
import com.ycmn.module.system.entity.SysDictType;
import com.ycmn.module.system.mapper.SysDictDataMapper;
import com.ycmn.module.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final BizCustomerMapper customerMapper;

    // 字典code → 客户表字段的映射
    private static final Map<String, String> DICT_COLUMN_MAP = Map.of(
            "industry", "industry",
            "dev_type", "dev_type"
    );

    // ===== 字典类型 =====

    public List<SysDictType> listTypes() {
        return dictTypeMapper.selectList(
                new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId));
    }

    public void createType(SysDictType type) {
        Long count = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getDictCode, type.getDictCode()));
        if (count > 0) {
            throw new BusinessException("字典编码已存在");
        }
        dictTypeMapper.insert(type);
    }

    public void updateType(SysDictType type) {
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
        dictDataMapper.insert(data);
    }

    @Transactional
    public void updateData(SysDictData data) {
        // 查旧值，判断 dictValue 是否变了
        SysDictData old = dictDataMapper.selectById(data.getId());
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
