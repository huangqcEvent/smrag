package com.hqc.smrag.service.knowledge;

import java.util.Locale;
import java.util.regex.Pattern;

public final class KnowledgeBaseDefaults {

    public static final String MAIN_CODE = "company-main";
    public static final String MAIN_NAME = "企业知识库";
    private static final String LEGACY_DEFAULT_CODE = "default";
    private static final Pattern CODE_PATTERN =
        Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private KnowledgeBaseDefaults() {
    }

    public static String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || LEGACY_DEFAULT_CODE.equals(normalized)) {
            return MAIN_CODE;
        }
        if (normalized.length() > 64 || !CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "知识库 code 只能包含小写字母、数字和连字符，且长度不能超过 64");
        }
        return normalized;
    }
}
