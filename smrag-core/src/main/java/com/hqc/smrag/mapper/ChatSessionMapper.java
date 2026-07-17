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
}
