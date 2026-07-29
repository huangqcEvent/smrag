package com.hqc.smrag.enums;

import java.util.Locale;

/**
 * Supported document chunking strategies.
 */
public enum ChunkingStrategy {

    TOKEN,
    RECURSIVE;

    public static ChunkingStrategy fromRequest(String value) {
        if (value == null || value.isBlank()) {
            return TOKEN;
        }
        return parse(value);
    }

    public static ChunkingStrategy fromPersisted(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("文档未记录切块策略");
        }
        try {
            return parse(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("文档切块策略不受支持: " + value, exception);
        }
    }

    private static ChunkingStrategy parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "切块策略不合法，仅支持 TOKEN、RECURSIVE",
                exception
            );
        }
    }
}
