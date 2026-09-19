package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysDictData;
import com.aigicrm.module.system.entity.SysDictType;
import com.aigicrm.module.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    // ===== 字典类型 =====

    @GetMapping("/types")
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<List<SysDictType>> listTypes() {
        return Result.ok(dictService.listTypes());
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/types")
    public Result<Void> createType(@RequestBody SysDictType type) {
        dictService.createType(type);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @PutMapping("/types/{id}")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody SysDictType type) {
        type.setId(id);
        dictService.updateType(type);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    // ===== 字典数据 =====
    // 【修复 FEAT-20260804-DICT-READ-SCOPE】listData/listDataByTypeId 是给普通业务表单(新增客户/
    // 四项激励/学分录入/公海等 7+ 个页面)读字典下拉选项用的，此前要求 system:config:view——这是"系统
    // 设置"模块的权限，销售员/行政等非管理岗角色只要没被额外补授权(通过菜单99这个 workaround)就会在打开
    // 新增客户等页面时,组件里 getDictData() 静默 403,但全局响应拦截器仍会弹出"没有访问权限,请联系管理
    // 员授权"的提示——这正是本次收到的生产报错。读字典值本质和读部门列表(system:depts,isAuthenticated
    // 即可)一样,是任何已登录用户都该能读的基础参考数据,不应该绑定管理员专属权限。改为仅要求登录态;
    // 字典类型/数据的增删改仍保持 system:config:view/edit 管理员权限不变。

    @GetMapping("/data/{dictCode}")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysDictData>> listData(@PathVariable String dictCode) {
        return Result.ok(dictService.listDataByCode(dictCode));
    }

    @GetMapping("/data/type/{typeId}")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysDictData>> listDataByTypeId(@PathVariable Long typeId) {
        return Result.ok(dictService.listDataByTypeId(typeId));
    }

    @PostMapping("/data")
    @PreAuthorize("hasAuthority('system:config:edit')")
    public Result<Void> createData(@RequestBody SysDictData data) {
        dictService.createData(data);
        return Result.ok();
    }

    @PutMapping("/data/{id}")
    @PreAuthorize("hasAuthority('system:config:edit')")
    public Result<Void> updateData(@PathVariable Long id, @RequestBody SysDictData data) {
        data.setId(id);
        dictService.updateData(data);
        return Result.ok();
    }

    @DeleteMapping("/data/{id}")
    @PreAuthorize("hasAuthority('system:config:edit')")
    public Result<Void> deleteData(@PathVariable Long id) {
        dictService.deleteData(id);
        return Result.ok();
    }
}
