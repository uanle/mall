package com.resume.mall.observability;

public final class LogValues {
    private static final int MAX_VALUE_LENGTH = 128;

    private LogValues() {
    }

    public static String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.trim()
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        return sanitized.length() <= MAX_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_VALUE_LENGTH);
    }
}
