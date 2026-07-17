package com.hqc.smrag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentIngestionOutboxMapper extends BaseMapper<DocumentIngestionOutbox> {

    @Select("""
        SELECT id, document_id, payload, status, attempt_count, next_attempt_at,
               last_error, created_at, published_at
        FROM document_ingestion_outbox
        WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
        ORDER BY created_at
        LIMIT #{limit}
        FOR UPDATE SKIP LOCKED
        """)
    List<DocumentIngestionOutbox> lockPendingBatch(@Param("limit") int limit);
}
