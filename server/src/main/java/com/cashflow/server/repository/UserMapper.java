package com.cashflow.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cashflow.server.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    default Optional<User> findByEmail(String email) {
        return Optional.ofNullable(selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)));
    }

    default Optional<User> findByUsername(String username) {
        return Optional.ofNullable(selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)));
    }
}
