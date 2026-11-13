package com.klay.charge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.klay.charge.entity.Card;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CardMapper extends BaseMapper<Card> {

    /** 行锁防并发超扣：数据库行锁保证余额一致，不超扣。 */
    @Select("SELECT * FROM card WHERE card_no = #{cardNo} FOR UPDATE")
    Card selectByCardNoForUpdate(@Param("cardNo") String cardNo);
}
