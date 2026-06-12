package com.cashflow.server.service;

import com.cashflow.server.model.entity.Category;
import com.cashflow.server.repository.CategoryMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Category> listByType(Boolean isIncome) {
        return categoryMapper.findByType(isIncome);
    }
}
