package com.hqc.smrag.utils;

import java.util.ArrayList;
import java.util.List;

public class ChunkUtils {

    private static final int DEFAULT_CHUNK_SIZE = 500;

    public static List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }

    public static List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE);
    }
}