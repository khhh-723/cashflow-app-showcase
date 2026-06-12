package com.cashflow.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cashflow.server.model.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT * FROM category WHERE is_income = #{isIncome} ORDER BY code ASC")
    List<Category> findByType(Boolean isIncome);

    @Select("SELECT * FROM category WHERE code = #{code}")
    Category findByCode(String code);
}
