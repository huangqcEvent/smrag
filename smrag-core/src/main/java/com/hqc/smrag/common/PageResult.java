package com.hqc.smrag.common;

import java.util.List;

public record PageResult<T>(
    List<T> records,
    long total,
    long pageNum,
    long pageSize,
    boolean hasMore
) {

    public PageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
