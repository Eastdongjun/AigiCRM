package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysDictData;
import com.ycmn.module.system.entity.SysDictType;
import com.ycmn.module.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    // ===== 字典类型 =====

    @GetMapping("/types")
    public Result<List<SysDictType>> listTypes() {
        return Result.ok(dictService.listTypes());
    }

    @PostMapping("/types")
    public Result<Void> createType(@RequestBody SysDictType type) {
        dictService.createType(type);
        return Result.ok();
    }

    @PutMapping("/types/{id}")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody SysDictType type) {
        type.setId(id);
        dictService.updateType(type);
        return Result.ok();
    }

    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    // ===== 字典数据 =====

    @GetMapping("/data/{dictCode}")
    public Result<List<SysDictData>> listData(@PathVariable String dictCode) {
        return Result.ok(dictService.listDataByCode(dictCode));
    }

    @GetMapping("/data/type/{typeId}")
    public Result<List<SysDictData>> listDataByTypeId(@PathVariable Long typeId) {
        return Result.ok(dictService.listDataByTypeId(typeId));
    }

    @PostMapping("/data")
    public Result<Void> createData(@RequestBody SysDictData data) {
        dictService.createData(data);
        return Result.ok();
    }

    @PutMapping("/data/{id}")
    public Result<Void> updateData(@PathVariable Long id, @RequestBody SysDictData data) {
        data.setId(id);
        dictService.updateData(data);
        return Result.ok();
    }

    @DeleteMapping("/data/{id}")
    public Result<Void> deleteData(@PathVariable Long id) {
        dictService.deleteData(id);
        return Result.ok();
    }
}
