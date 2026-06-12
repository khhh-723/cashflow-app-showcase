package com.cashflow.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cashflow.server.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<ChatSession> findByUserId(Long userId);
}
