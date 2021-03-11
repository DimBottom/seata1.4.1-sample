package com.dimbottom.cloud.sample.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dimbottom.cloud.sample.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface AccountDao extends BaseMapper<Account> {

    /**
     * 扣减账户余额
     */
    void decrease(@Param("userId") Long userId, @Param("money") BigDecimal money);
}
