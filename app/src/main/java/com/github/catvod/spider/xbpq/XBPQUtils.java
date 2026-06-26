package com.github.catvod.spider.xbpq;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Pattern;

/**
 * XBPQUtils - 通用工具类
 * 
 * <p>提供常用的工具方法，包括：</p>
 * <ul>
 *   <li>URL 编码/解码</li>
 *   <li>Base64 编码/解码</li>
 *   <li>视频格式判断</li>
 *   <li>字符串解析</li>
 *   <li>URL 拼接</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQUtils {

    // ==================== 常量定义 ====================
    
    /** 视频格式正则表达式 */
    private static final Pattern VIDEO_FORMAT_PATTERN = Pattern.compile(
        "(\\.m3u8|\\.mp4|\\.flv|\\.avi|\\.mkv|\\.wmv|\\.mov|\\.webm|\\.ts|video|stream)",
        Pattern.CASE_INSENSITIVE
    );
    
    /** 默认编码 */
    private static final String DEFAULT_ENCODING = "UTF-8";
    
    // ==================== URL 编码/解码 ====================
    
    /**
     * URL 编码
     * 
     * @param text 待编码的文本
     * @return 编码后的文本
     */
    public static String encodeUrl(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        try {
            return URLEncoder.encode(text, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            SpiderDebug.error("[XBPQ] encodeUrl error: " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * URL 解码
     * 
     * @param text 待解码的文本
     * @return 解码后的文本
     */
    public static String decodeUrl(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        try {
            return URLDecoder.decode(text, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            SpiderDebug.error("[XBPQ] decodeUrl error: " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * URL 解码（指定编码）
     * 
     * @param text 待解码的文本
     * @param encoding 编码格式
     * @return 解码后的文本
     */
    public static String decodeUrl(String text, String encoding) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        if (TextUtils.isEmpty(encoding)) {
            encoding = DEFAULT_ENCODING;
        }
        
        try {
            return URLDecoder.decode(text, encoding);
        } catch (UnsupportedEncodingException e) {
            SpiderDebug.error("[XBPQ] decodeUrl error: encoding=" + encoding + ", " + e.getMessage(), e);
            return text;
        }
    }
    
    // ==================== Base64 编码/解码 ====================
    
    /**
     * Base64 编码
     * 
     * @param text 待编码的文本
     * @return 编码后的文本
     */
    public static String encodeBase64(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        try {
            byte[] bytes = text.getBytes(DEFAULT_ENCODING);
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            SpiderDebug.error("[XBPQ] encodeBase64 error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Base64 编码（URL 安全）
     * 
     * @param text 待编码的文本
     * @return 编码后的文本
     */
    public static String encodeBase64UrlSafe(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        try {
            byte[] bytes = text.getBytes(DEFAULT_ENCODING);
            return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (UnsupportedEncodingException e) {
            SpiderDebug.error("[XBPQ] encodeBase64UrlSafe error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Base64 解码
     * 
     * @param text 待解码的文本
     * @return 解码后的文本
     */
    public static String decodeBase64(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        try {
            byte[] bytes = Base64.decode(text, Base64.DEFAULT);
            return new String(bytes, DEFAULT_ENCODING);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] decodeBase64 error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Base64 解码（URL 安全）
     * 
     * @param text 待解码的文本
     * @return 解码后的文本
     */
    public static String decodeBase64UrlSafe(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        try {
            byte[] bytes = Base64.decode(text, Base64.URL_SAFE | Base64.NO_WRAP);
            return new String(bytes, DEFAULT_ENCODING);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] decodeBase64UrlSafe error: " + e.getMessage(), e);
            return "";
        }
    }
    
    // ==================== 视频格式判断 ====================
    
    /**
     * 判断是否为视频格式
     * 
     * @param url URL 地址
     * @return 是否为视频格式
     */
    public static boolean isVideoFormat(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        
        return VIDEO_FORMAT_PATTERN.matcher(url).find();
    }
    
    /**
     * 判断是否为 M3U8 格式
     * 
     * @param url URL 地址
     * @return 是否为 M3U8 格式
     */
    public static boolean isM3u8(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        
        return url.toLowerCase().contains(".m3u8");
    }
    
    /**
     * 判断是否为磁力链接
     * 
     * @param url URL 地址
     * @return 是否为磁力链接
     */
    public static boolean isMagnetLink(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        
        return url.startsWith("magnet:");
    }
    
    // ==================== URL 拼接 ====================
    
    /**
     * 拼接 URL
     * 
     * @param base 基础 URL
     * @param relative 相对路径
     * @return 完整 URL
     */
    public static String joinUrl(String base, String relative) {
        if (TextUtils.isEmpty(relative)) {
            return base;
        }
        
        if (TextUtils.isEmpty(base)) {
            return relative;
        }
        
        // 如果 relative 已经是完整 URL，直接返回
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        
        // 处理相对路径
        if (relative.startsWith("/")) {
            // 绝对路径
            Uri uri = Uri.parse(base);
            return uri.getScheme() + "://" + uri.getHost() + 
                   (uri.getPort() > 0 ? ":" + uri.getPort() : "") + relative;
        } else {
            // 相对路径
            if (base.endsWith("/")) {
                return base + relative;
            } else {
                return base + "/" + relative;
            }
        }
    }
    
    /**
     * 获取 URL 基础路径
     * 
     * @param url URL 地址
     * @return 基础路径
     */
    public static String getBaseUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        
        try {
            Uri uri = Uri.parse(url);
            return uri.getScheme() + "://" + uri.getHost() + 
                   (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] getBaseUrl error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 获取 URL 域名
     * 
     * @param url URL 地址
     * @return 域名
     */
    public static String getHost(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        
        try {
            Uri uri = Uri.parse(url);
            return uri.getHost();
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] getHost error: " + e.getMessage(), e);
            return "";
        }
    }
    
    // ==================== 字符串解析 ====================
    
    /**
     * 解析整数
     * 
     * @param str 字符串
     * @param defaultValue 默认值
     * @return 整数值
     */
    public static int parseInt(String str, int defaultValue) {
        if (TextUtils.isEmpty(str)) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            SpiderDebug.error("[XBPQ] parseInt error: str=" + str + ", " + e.getMessage(), e);
            return defaultValue;
        }
    }
    
    /**
     * 解析长整数
     * 
     * @param str 字符串
     * @param defaultValue 默认值
     * @return 长整数值
     */
    public static long parseLong(String str, long defaultValue) {
        if (TextUtils.isEmpty(str)) {
            return defaultValue;
        }
        
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            SpiderDebug.error("[XBPQ] parseLong error: str=" + str + ", " + e.getMessage(), e);
            return defaultValue;
        }
    }
    
    /**
     * 解析布尔值
     * 
     * @param str 字符串
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public static boolean parseBoolean(String str, boolean defaultValue) {
        if (TextUtils.isEmpty(str)) {
            return defaultValue;
        }
        
        String lowerStr = str.trim().toLowerCase();
        if ("true".equals(lowerStr) || "1".equals(lowerStr) || "yes".equals(lowerStr)) {
            return true;
        }
        if ("false".equals(lowerStr) || "0".equals(lowerStr) || "no".equals(lowerStr)) {
            return false;
        }
        
        return defaultValue;
    }
    
    // ==================== 字符串处理 ====================
    
    /**
     * 截取字符串
     * 
     * @param str 原字符串
     * @param start 开始位置
     * @param end 结束位置
     * @return 截取后的字符串
     */
    public static String substring(String str, int start, int end) {
        if (TextUtils.isEmpty(str)) {
            return "";
        }
        
        if (start < 0) {
            start = 0;
        }
        
        if (end > str.length()) {
            end = str.length();
        }
        
        if (start >= end) {
            return "";
        }
        
        return str.substring(start, end);
    }
    
    /**
     * 空值处理
     * 
     * @param str 原字符串
     * @param defaultValue 默认值
     * @return 处理后的字符串
     */
    public static String nullToDefault(String str, String defaultValue) {
        if (TextUtils.isEmpty(str)) {
            return defaultValue != null ? defaultValue : "";
        }
        return str;
    }
    
    /**
     * 移除 HTML 标签
     * 
     * @param html HTML 内容
     * @return 纯文本内容
     */
    public static String removeHtmlTags(String html) {
        if (TextUtils.isEmpty(html)) {
            return "";
        }
        
        return html.replaceAll("<[^>]+>", "");
    }
    
    /**
     * 移除空白字符
     * 
     * @param str 原字符串
     * @return 处理后的字符串
     */
    public static String removeWhitespace(String str) {
        if (TextUtils.isEmpty(str)) {
            return "";
        }
        
        return str.replaceAll("\\s+", "");
    }
    
    /**
     * 获取时间戳
     * 
     * @return 当前时间戳（毫秒）
     */
    public static long timestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * 获取时间戳（秒）
     * 
     * @return 当前时间戳（秒）
     */
    public static long timestampSeconds() {
        return System.currentTimeMillis() / 1000;
    }
}