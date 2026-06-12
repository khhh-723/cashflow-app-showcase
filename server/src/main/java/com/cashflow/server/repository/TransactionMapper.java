package com.cashflow.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cashflow.server.model.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

    @Select("SELECT * FROM `transaction` WHERE user_id = #{userId} AND client_id = #{clientId}")
    Transaction findByClientId(@Param("userId") Long userId, @Param("clientId") String clientId);

    @Select("SELECT * FROM `transaction` WHERE user_id = #{userId} AND occurred_at >= #{startTime} AND occurred_at <= #{endTime} ORDER BY occurred_at DESC")
    List<Transaction> findByTimeRange(@Param("userId") Long userId, @Param("startTime") Long startTime, @Param("endTime") Long endTime);

    @Select("SELECT * FROM `transaction` WHERE user_id = #{userId} AND updated_at > #{since} ORDER BY updated_at ASC")
    List<Transaction> findModifiedSince(@Param("userId") Long userId, @Param("since") Long since);

    @Select({
        "SELECT COALESCE(SUM(amount_cents), 0) FROM `transaction`",
        "WHERE user_id = #{userId} AND transaction_type = #{type} AND review_state = 'CONFIRMED'",
        "AND occurred_at >= #{startTime} AND occurred_at <= #{endTime}"
    })
    Long sumAmountByType(@Param("userId") Long userId, @Param("type") String type,
                         @Param("startTime") Long startTime, @Param("endTime") Long endTime);
}
