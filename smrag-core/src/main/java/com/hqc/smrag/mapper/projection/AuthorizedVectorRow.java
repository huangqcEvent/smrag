package com.hqc.smrag.mapper.projection;

import java.util.Map;

public record AuthorizedVectorRow(
    String vectorId,
    String content,
    Map<String, Object> metadata,
    Double score,
    Long knowledgeBaseId,
    String knowledgeBaseCode,
    String knowledgeBaseName
) {
}
