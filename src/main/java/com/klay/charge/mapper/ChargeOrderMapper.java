package com.klay.charge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.klay.charge.entity.ChargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChargeOrderMapper extends BaseMapper<ChargeOrder> {

    /** 事务内行锁：串行化同一流水号的收费/退费，配合状态机做幂等。 */
    @Select("SELECT * FROM charge_order WHERE serial_no = #{serialNo} FOR UPDATE")
    ChargeOrder selectBySerialForUpdate(@Param("serialNo") String serialNo);
}
