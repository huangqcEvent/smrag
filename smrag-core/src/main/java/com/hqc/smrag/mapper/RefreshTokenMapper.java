package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

/**
 * 刷新令牌 Mapper 接口
 *
 * @see RefreshToken 刷新令牌实体
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    /**
     * 删除用户的所有刷新令牌
     *
     * @param userId 用户ID
     * @return 删除数量
     */
    @Update("DELETE FROM refresh_token WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除过期的刷新令牌
     *
     * @param now 当前时间
     * @return 删除数量
     */
    @Update("DELETE FROM refresh_token WHERE expires_at < #{now}")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}