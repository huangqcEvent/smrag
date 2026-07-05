package com.hqc.smrag.controller;

import com.hqc.smrag.entity.FundManager;
import com.hqc.smrag.mapper.FundManagerMapper;
import com.hqc.smrag.vo.ManagerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final FundManagerMapper managerMapper;

    @GetMapping("/{id}")
    public ManagerVO getManager(@PathVariable Long id) {
        FundManager manager = managerMapper.selectById(id);
        if (manager == null) {
            return null;
        }

        ManagerVO vo = new ManagerVO();
        vo.setId(manager.getId());
        vo.setManagerCode(manager.getManagerCode());
        vo.setManagerName(manager.getManagerName());
        vo.setCompanyName(manager.getCompanyName());
        vo.setAssetsScale(manager.getAssetsScale());
        vo.setQualification(manager.getQualification());

        return vo;
    }

    @GetMapping("/search")
    public List<ManagerVO> searchManagers(@RequestParam String managerName) {
        List<FundManager> managers = managerMapper.searchByName(managerName);
        return managers.stream().map(m -> {
            ManagerVO vo = new ManagerVO();
            vo.setId(m.getId());
            vo.setManagerCode(m.getManagerCode());
            vo.setManagerName(m.getManagerName());
            vo.setCompanyName(m.getCompanyName());
            vo.setAssetsScale(m.getAssetsScale());
            vo.setQualification(m.getQualification());
            return vo;
        }).collect(Collectors.toList());
    }

    @GetMapping("/list")
    public List<ManagerVO> listManagers() {
        List<FundManager> managers = managerMapper.selectList(null);
        return managers.stream().map(m -> {
            ManagerVO vo = new ManagerVO();
            vo.setId(m.getId());
            vo.setManagerCode(m.getManagerCode());
            vo.setManagerName(m.getManagerName());
            vo.setCompanyName(m.getCompanyName());
            vo.setAssetsScale(m.getAssetsScale());
            vo.setQualification(m.getQualification());
            return vo;
        }).collect(Collectors.toList());
    }
}