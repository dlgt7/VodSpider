package com.github.catvod.utils;

import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UA 与默认 Header 管理工具
 *
 * 功能：
 * 1. 从 Util.USER_AGENTS 列表随机选取 UA
 * 2. 将扁平 Map 转为有序 LinkedHashMap（保留插入顺序，便于 WAF 指纹识别）
 * 3. 内置公共 Header 模板（Accept、Accept-Language 等）
 * 4. Cookie 合并：旧 Cookie + 新 Cookie，去重 Key
 */
public class UAConfig {

    private UAConfig() {}

    // ==================== UA ====================

    /**
     * 随机选取一个浏览器 UA
     */
    public static String randomUA() {
        return Util.getRandomUserAgent();
    }

    // ==================== Header 构建 ====================

    /**
     * 将扁平 header Map 转为有序 Map（LinkedHashMap）
     * 保留插入顺序，避免 HashMap 随机重排导致 WAF 指纹异常
     */
    public static LinkedHashMap<String, String> toOrderedMap(Map<String, String> header) {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        if (header != null) {
            for (Map.Entry<String, String> entry : header.entrySet()) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }
        return ordered;
    }

    /**
     * 构建带默认公共 Header 的 Map
     * 未设置 UA 时自动补充随机浏览器 UA
     * 未设置 Accept-Language 时自动补充 zh-CN
     */
    public static LinkedHashMap<String, String> buildDefaultHeaders(Map<String, String> extra) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", randomUA());
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Connection", "keep-alive");
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        return headers;
    }

    // ==================== Cookie 处理 ====================

    /**
     * 合并两个 Cookie 字符串
     * 新 Cookie 中的同 Key 会覆盖旧值
     *
     * @param oldCookie 原始 Cookie
     * @param newCookie 新的 Cookie（通常来自 Set-Cookie 响应头）
     * @return 合并后的 Cookie 字符串
     */
    public static String mergeCookies(String oldCookie, String newCookie) {
        if (TextUtils.isEmpty(oldCookie)) return newCookie == null ? "" : newCookie;
        if (TextUtils.isEmpty(newCookie)) return oldCookie;
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String pair : oldCookie.split(";")) {
            String trimmed = pair.trim();
            int idx = trimmed.indexOf('=');
            if (idx > 0) map.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
        }
        for (String pair : newCookie.split(";")) {
            String trimmed = pair.trim();
            int idx = trimmed.indexOf('=');
            if (idx > 0) map.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}
