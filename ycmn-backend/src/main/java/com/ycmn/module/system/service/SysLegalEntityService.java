package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.SysLegalEntity;
import com.ycmn.module.system.mapper.SysLegalEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysLegalEntityService {

    private final SysLegalEntityMapper entityMapper;

    public List<SysLegalEntity> list() {
        return entityMapper.selectList(
                new LambdaQueryWrapper<SysLegalEntity>().orderByAsc(SysLegalEntity::getSortOrder));
    }

    public List<SysLegalEntity> listByGroupId(Long groupId) {
        return entityMapper.selectByGroupId(groupId);
    }

    public SysLegalEntity getById(Long id) {
        return entityMapper.selectById(id);
    }

    @Transactional
    public void create(SysLegalEntity entity) {
        Long count = entityMapper.selectCount(
                new LambdaQueryWrapper<SysLegalEntity>().eq(SysLegalEntity::getEntityCode, entity.getEntityCode()));
        if (count > 0) throw new BusinessException("法人公司编码已存在");
        if (entity.getGroupId() == null) throw new BusinessException("所属集团不能为空");
        entityMapper.insert(entity);
    }

    @Transactional
    public void update(SysLegalEntity entity) {
        entityMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        SysLegalEntity entity = new SysLegalEntity();
        entity.setId(id);
        entity.setStatus(0);
        entityMapper.updateById(entity);
    }
}
