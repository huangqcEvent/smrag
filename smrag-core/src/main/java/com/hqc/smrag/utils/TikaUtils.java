package com.hqc.smrag.utils;

import org.apache.tika.Tika;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;

@Slf4j
public class TikaUtils {

    private static final Tika tika = new Tika();

    public static String extractText(String filePath) throws IOException {
        File file = new File(filePath);
        String text = tika.parseToString(file);
        log.info("提取文本完成，长度: {}", text.length());
        return text;
    }
}