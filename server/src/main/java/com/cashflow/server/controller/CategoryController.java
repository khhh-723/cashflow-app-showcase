package com.cashflow.server.controller;

import com.cashflow.server.model.entity.Category;
import com.cashflow.server.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<Category>> list(@RequestParam(defaultValue = "false") boolean isIncome) {
        return ResponseEntity.ok(categoryService.listByType(isIncome));
    }
}
