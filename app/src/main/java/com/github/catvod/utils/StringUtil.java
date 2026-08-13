package com.github.catvod.utils;

/**
 * 字符串工具类（空安全）
 * 所有方法对 null 安全，null 视为空字符串或返回 false/0。
 */
public final class StringUtil {

    public static final String EMPTY = "";

    private StringUtil() {}

    public static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    public static boolean isBlank(CharSequence text) {
        if (text == null) return true;
        for (int i = 0, len = text.length(); i < len; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotEmpty(CharSequence text) {
        return !isEmpty(text);
    }

    public static boolean isNotBlank(CharSequence text) {
        return !isBlank(text);
    }

    public static boolean isNumeric(CharSequence text) {
        if (isEmpty(text)) return false;
        for (int i = 0, len = text.length(); i < len; i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    public static String defaultIfEmpty(String text, String defaultValue) {
        return isNotEmpty(text) ? text : defaultValue;
    }

    public static String defaultIfBlank(String text, String defaultValue) {
        return isNotBlank(text) ? text : defaultValue;
    }

    public static String join(CharSequence... parts) {
        if (parts == null || parts.length == 0) return EMPTY;
        StringBuilder sb = new StringBuilder();
        for (CharSequence part : parts) {
            if (part != null) sb.append(part);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, CharSequence... parts) {
        if (parts == null || parts.length == 0) return EMPTY;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (CharSequence part : parts) {
            if (part == null) continue;
            if (!first) sb.append(delimiter);
            sb.append(part);
            first = false;
        }
        return sb.toString();
    }

    public static String trim(String str) {
        return str == null ? EMPTY : str.trim();
    }

    public static String trimToEmpty(String str) {
        return trim(str);
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        if (a instanceof String && b instanceof String) {
            return a.equals(b);
        }
        return a.toString().equals(b.toString());
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    public static String replace(String text, CharSequence target, CharSequence replacement) {
        if (text == null) return EMPTY;
        if (target == null || replacement == null) return text;
        return text.replace(target, replacement);
    }

    public static int length(CharSequence text) {
        return text == null ? 0 : text.length();
    }

    public static boolean contains(CharSequence text, CharSequence search) {
        if (text == null || search == null) return false;
        return text.toString().contains(search);
    }

    public static boolean startsWith(CharSequence text, CharSequence prefix) {
        if (text == null || prefix == null) return false;
        return text.toString().startsWith(prefix.toString());
    }

    public static boolean endsWith(CharSequence text, CharSequence suffix) {
        if (text == null || suffix == null) return false;
        return text.toString().endsWith(suffix.toString());
    }

    public static String substring(String str, int start, int end) {
        if (str == null) return EMPTY;
        int len = str.length();
        if (start < 0) start = 0;
        if (end > len) end = len;
        if (start >= end) return EMPTY;
        return str.substring(start, end);
    }

    public static String substring(String str, int start) {
        return substring(str, start, Integer.MAX_VALUE);
    }

    /** 拼接字符串与整数，null 视为空字符串 */
    public static String append(String str, int i) {
        return (str == null ? EMPTY : str) + i;
    }

    /** 拼接字符串与长整数，null 视为空字符串 */
    public static String append(String str, long i) {
        return (str == null ? EMPTY : str) + i;
    }

    /** 拼接字符串与双精度浮点数，null 视为空字符串 */
    public static String append(String str, double i) {
        return (str == null ? EMPTY : str) + i;
    }
}
