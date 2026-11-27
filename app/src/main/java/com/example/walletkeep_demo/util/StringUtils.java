package com.example.walletkeep_demo.util;

/**
 * 字符串工具：按 Unicode 码点取首字符，避免把 emoji 等代理对截断成非法字符。
 */
public final class StringUtils {
    private StringUtils() {
    }

    public static String firstCodePointOr(String text, String fallback) {
        if (text == null) {
            return fallback;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        int codePoint = trimmed.codePointAt(0);
        return new String(Character.toChars(codePoint));
    }
}
