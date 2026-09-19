package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.entity.SysGroup;
import com.aigicrm.module.system.mapper.SysGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysGroupService {

    private final SysGroupMapper groupMapper;

    public List<SysGroup> list() {
        return groupMapper.selectList(
                new LambdaQueryWrapper<SysGroup>().orderByAsc(SysGroup::getSortOrder));
    }

    public SysGroup getById(Long id) {
        return groupMapper.selectById(id);
    }

    @Transactional
    public void create(SysGroup group) {
        Long count = groupMapper.selectCount(
                new LambdaQueryWrapper<SysGroup>().eq(SysGroup::getGroupCode, group.getGroupCode()));
        if (count > 0) throw new BusinessException("集团编码已存在");
        groupMapper.insert(group);
    }

    @Transactional
    public void update(SysGroup group) {
        groupMapper.updateById(group);
    }

    @Transactional
    public void delete(Long id) {
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setStatus(0);
        groupMapper.updateById(group);
    }
}
