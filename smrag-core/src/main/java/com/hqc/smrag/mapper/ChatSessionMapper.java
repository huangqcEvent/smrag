package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatSession> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询指定会话和用户的历史记录
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 对话记录列表
     */
    @Select("SELECT * FROM chat_session WHERE session_id = #{sessionId} AND user_id = #{userId} ORDER BY create_time ASC")
    List<ChatSession> findBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    /**
     * 查询用户的所有会话
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    @Select("""
        SELECT * FROM chat_session WHERE user_id = #{userId}
        AND id IN (
            SELECT MAX(id) FROM chat_session WHERE user_id = #{userId} GROUP BY session_id
        )
        ORDER BY create_time DESC
        """)
    List<ChatSession> findSessionsByUserId(@Param("userId") Long userId);
}
