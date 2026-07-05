package com.hqc.smrag.controller;

import com.hqc.smrag.entity.FundProduct;
import com.hqc.smrag.entity.FundManager;
import com.hqc.smrag.mapper.FundProductMapper;
import com.hqc.smrag.mapper.FundManagerMapper;
import com.hqc.smrag.vo.ProductVO;
import com.hqc.smrag.vo.ManagerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final FundProductMapper productMapper;
    private final FundManagerMapper managerMapper;

    @GetMapping("/{id}")
    public ProductVO getProduct(@PathVariable Long id) {
        FundProduct product = productMapper.selectById(id);
        if (product == null) {
            return null;
        }

        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setProductCode(product.getProductCode());
        vo.setProductName(product.getProductName());
        vo.setRiskLevel(product.getRiskLevel());
        vo.setReturnRate(product.getReturnRate());
        vo.setInvestmentStrategy(product.getInvestmentStrategy());
        vo.setMinAmount(product.getMinAmount());

        // 获取管理人名称
        if (product.getManagerId() != null) {
            FundManager manager = managerMapper.selectById(product.getManagerId());
            if (manager != null) {
                vo.setManagerName(manager.getManagerName());
            }
        }

        return vo;
    }

    @GetMapping("/search")
    public List<ProductVO> searchProducts(@RequestParam String productName) {
        List<FundProduct> products = productMapper.searchByName(productName);
        return products.stream().map(p -> {
            ProductVO vo = new ProductVO();
            vo.setId(p.getId());
            vo.setProductCode(p.getProductCode());
            vo.setProductName(p.getProductName());
            vo.setRiskLevel(p.getRiskLevel());
            vo.setReturnRate(p.getReturnRate());
            vo.setMinAmount(p.getMinAmount());

            if (p.getManagerId() != null) {
                FundManager manager = managerMapper.selectById(p.getManagerId());
                if (manager != null) {
                    vo.setManagerName(manager.getManagerName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @GetMapping("/list")
    public List<ProductVO> listProducts() {
        List<FundProduct> products = productMapper.selectList(null);
        return products.stream().map(p -> {
            ProductVO vo = new ProductVO();
            vo.setId(p.getId());
            vo.setProductCode(p.getProductCode());
            vo.setProductName(p.getProductName());
            vo.setRiskLevel(p.getRiskLevel());
            vo.setReturnRate(p.getReturnRate());
            vo.setMinAmount(p.getMinAmount());

            if (p.getManagerId() != null) {
                FundManager manager = managerMapper.selectById(p.getManagerId());
                if (manager != null) {
                    vo.setManagerName(manager.getManagerName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }
}