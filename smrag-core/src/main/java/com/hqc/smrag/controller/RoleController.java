package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.CreateRoleRequest;
import com.hqc.smrag.dto.UpdateRoleRequest;
import com.hqc.smrag.service.RoleService;
import com.hqc.smrag.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping public Result<List<RoleVO>> list() { return Result.success(roleService.list()); }
    @PostMapping public Result<Long> create(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(roleService.create(request));
    }
    @PutMapping("/{id}") public Result<Void> update(@PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        roleService.update(id, request); return Result.success();
    }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id); return Result.success();
    }
}
