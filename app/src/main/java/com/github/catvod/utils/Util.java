package com.github.catvod.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Util {

    private static final Pattern THUNDER = Pattern.compile("(magnet|thunder|ed2k):.*");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
    };

    public static final String CHROME = USER_AGENTS[0];
    private static final String CLIPBOARD_TAG = "fongmi";

    public static final List<String> MEDIA = Arrays.asList("mp4", "mkv", "mov", "wav", "wma", "wmv", "flv", "avi", "iso", "mpg", "ts", "mp3", "aac", "flac", "m4a", "ape", "ogg", "rm", "rmvb", "asf", "webm", "m3u8", "f4v");
    public static final List<String> SUB = Arrays.asList("srt", "ass", "ssa", "vtt", "sub", "smi");
    public static final List<String> IMAGE = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");
    public static final List<String> AUDIO = Arrays.asList("mp3", "wav", "wma", "aac", "flac", "m4a", "ogg", "ape", "opus");
    public static final List<String> VIDEO = Arrays.asList("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m3u8", "ts", "f4v", "rmvb", "rm", "asf", "3gp");

    public static String getRandomUserAgent() {
        return USER_AGENTS[(int) (Math.random() * USER_AGENTS.length)];
    }

    /**
     * 随机选取一个浏览器 UA（别名，供 UAConfig 迁移调用方使用）
     */
    public static String randomUA() {
        return getRandomUserAgent();
    }

    /**
     * 将扁平 header Map 转为有序 LinkedHashMap（保留插入顺序，便于 WAF 指纹识别）
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

    /**
     * 合并两个 Cookie 字符串，新 Cookie 中的同 Key 覆盖旧值
     */
    public static String mergeCookies(String oldCookie, String newCookie) {
        if (isEmpty(oldCookie)) return newCookie == null ? "" : newCookie;
        if (isEmpty(newCookie)) return oldCookie;
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

    public static boolean isThunder(String url) {
        if (isEmpty(url)) return false;
        return THUNDER.matcher(url).find() || isTorrent(url);
    }

    public static boolean isTorrent(String url) {
        if (isEmpty(url)) return false;
        if (url.toLowerCase().startsWith("magnet:")) return false;
        return getExt(url).equals("torrent");
    }

    /** VIP 视频域名列表 */
    private static final String[] VIP_DOMAINS = {
            "iqiyi.com", "youku.com", "tudou.com", "v.qq.com", "mgtv.com",
            "sohu.com", "le.com", "pptv.com", "vip.bd.com"
    };

    /** 视频格式扩展名（用于 isVideoFormat 关键词匹配） */
    private static final String[] VIDEO_FORMAT_EXTS = {
            ".m3u8", ".mp4", ".flv", ".avi", ".mkv", ".rm", ".wmv", ".mpg", ".m4a", ".mp3"
    };

    /**
     * 判断 URL 是否指向 VIP 视频站点。
     */
    public static boolean isVip(String url) {
        if (isEmpty(url)) return false;
        for (String domain : VIP_DOMAINS) {
            if (url.contains(domain)) return true;
        }
        return false;
    }

    /**
     * 判断 URL 是否为常见视频格式直链（关键词包含视频扩展名）。
     */
    public static boolean isVideoFormat(String url) {
        if (isEmpty(url)) return false;
        String lower = url.toLowerCase();
        for (String ext : VIDEO_FORMAT_EXTS) {
            if (lower.contains(ext)) return true;
        }
        return false;
    }

    public static boolean isSub(String text) {
        return SUB.contains(getExt(text));
    }

    public static boolean isMedia(String text) {
        return MEDIA.contains(getExt(text));
    }

    public static boolean isImage(String text) {
        return IMAGE.contains(getExt(text));
    }

    public static boolean isAudio(String text) {
        return AUDIO.contains(getExt(text));
    }

    public static boolean isVideo(String text) {
        return VIDEO.contains(getExt(text));
    }

    public static String getExt(String name) {
        if (isEmpty(name)) return "";
        int q = name.indexOf('?');
        if (q != -1) name = name.substring(0, q);
        int hash = name.indexOf('#');
        if (hash != -1) name = name.substring(0, hash);
        int slash = name.lastIndexOf('/');
        String filename = slash != -1 ? name.substring(slash + 1) : name;
        int dot = filename.lastIndexOf('.');
        return (dot != -1 && dot < filename.length() - 1) ? filename.substring(dot + 1).toLowerCase() : "";
    }

    public static String getSize(double size) {
        if (size <= 0) return "0 bytes";
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups < 0) digitGroups = 0;
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getDigit(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 将字节数组解码为 UTF-8 字符串
     */
    public static String bytesToUtf8(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    public static String removeExt(String text) {
        if (isEmpty(text)) return "";
        int q = text.indexOf('?');
        if (q != -1) text = text.substring(0, q);
        int hash = text.indexOf('#');
        if (hash != -1) text = text.substring(0, hash);
        int slash = text.lastIndexOf('/');
        String filename = slash != -1 ? text.substring(slash + 1) : text;
        int dot = filename.lastIndexOf('.');
        return dot != -1 ? text.substring(0, text.length() - filename.length() + dot) : text;
    }

    /** 去掉末尾 1 个字符，保留单参数签名以兼容已有调用（如 Wbi、jianpian） */
    public static String substring(String text) {
        return stripLast(text, 1);
    }

    /** 去掉末尾 n 个字符，null/空/num≤0 时原样返回 */
    public static String stripLast(String text, int num) {
        if (isEmpty(text) || num <= 0) return text;
        if (text.length() > num) {
            return text.substring(0, text.length() - num);
        }
        return text;
    }

    public static String substring(String text, int start, int end) {
        if (text == null) return "";
        int len = text.length();
        if (start < 0) start = 0;
        if (end > len) end = len;
        if (start >= end) return "";
        return text.substring(start, end);
    }

    public static String getVar(String data, String param) {
        if (isEmpty(data) || isEmpty(param)) return "";
        Pattern pattern = Pattern.compile("(?:var|let|const)\\s+" + Pattern.quote(param) + "\\s*=\\s*(['\"])(.*?)\\1", Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(data);
        return matcher.find() ? matcher.group(2) : "";
    }

    public static void copy(String text) {
        ClipboardManager manager = (ClipboardManager) Init.context().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_TAG, text));
        Notify.show("已複製 " + text);
    }

    public static String encode(String text) {
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    public static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    public static String base64Encode(String text) {
        if (isEmpty(text)) return "";
        return Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public static String base64Decode(String text) {
        if (isEmpty(text)) return "";
        try {
            return new String(Base64.decode(text, Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }

    public static String base64UrlEncode(String text) {
        if (isEmpty(text)) return "";
        return Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
    }

    public static String base64UrlDecode(String text) {
        if (isEmpty(text)) return "";
        try {
            return new String(Base64.decode(text, Base64.NO_WRAP | Base64.URL_SAFE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String uuid(boolean dash) {
        String uuid = UUID.randomUUID().toString();
        return dash ? uuid : uuid.replace("-", "");
    }

    public static String md5(String text) {
        return md5(text, false);
    }

    /**
     * MD5哈希（可指定大小写）
     */
    public static String md5(String text, boolean upperCase) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                String hex = Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3);
                sb.append(upperCase ? hex.toUpperCase() : hex);
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static String sha256(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] array = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    // ==================== 字符串判断 ====================

    public static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence text) {
        return !isEmpty(text);
    }

    public static boolean isBlank(CharSequence text) {
        if (text == null) return true;
        for (int i = 0, len = text.length(); i < len; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence text) {
        return !isBlank(text);
    }

    public static boolean isNumeric(String text) {
        if (isEmpty(text)) return false;
        int len = text.length();
        int i = text.charAt(0) == '-' ? 1 : 0;
        if (i == len) return false;

        boolean hasDot = false;
        boolean hasDigit = false;
        for (; i < len; i++) {
            char c = text.charAt(i);
            if (c == '.') {
                if (hasDot) return false;
                hasDot = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else {
                return false;
            }
        }
        return hasDigit;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        if (a instanceof String && b instanceof String) return a.equals(b);
        return a.toString().equals(b.toString());
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    // ==================== 字符串转换 ====================

    public static String defaultIfEmpty(String text, String defaultValue) {
        return isNotEmpty(text) ? text : defaultValue;
    }

    public static String defaultIfBlank(String text, String defaultValue) {
        return isNotBlank(text) ? text : defaultValue;
    }

    public static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    public static String trimToEmpty(String text) {
        return trim(text);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    public static String truncate(String text, int maxLength, String suffix) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + suffix;
    }

    public static String capitalize(String text) {
        if (isEmpty(text)) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    public static String lowercase(String text) {
        return text == null ? "" : text.toLowerCase(Locale.getDefault());
    }

    public static String uppercase(String text) {
        return text == null ? "" : text.toUpperCase(Locale.getDefault());
    }

    // ==================== 字符串拼接 ====================

    public static String join(CharSequence... parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (CharSequence part : parts) {
            if (part != null) sb.append(part);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, CharSequence... parts) {
        if (parts == null || parts.length == 0) return "";
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

    public static String join(String delimiter, String[] array) {
        if (array == null || array.length == 0) return "";
        return String.join(delimiter, array);
    }

    public static String join(String delimiter, List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(delimiter, list);
    }

    public static String replace(String text, CharSequence target, CharSequence replacement) {
        if (text == null) return "";
        if (target == null || replacement == null) return text;
        return text.replace(target, replacement);
    }

    public static String replaceAll(String text, String target, String replacement) {
        if (text == null) return "";
        return text.replaceAll(target, replacement);
    }

    /** 拼接字符串与整数，null 视为空字符串 */
    public static String append(String str, int i) {
        return (str == null ? "" : str) + i;
    }

    /** 拼接字符串与长整数，null 视为空字符串 */
    public static String append(String str, long i) {
        return (str == null ? "" : str) + i;
    }

    /** 拼接字符串与双精度浮点数，null 视为空字符串 */
    public static String append(String str, double i) {
        return (str == null ? "" : str) + i;
    }

    /** 拼接两个字符串，null 视为空字符串 */
    public static String concat(String a, String b) {
        return a == null ? (b == null ? "" : b) : (b == null ? a : a + b);
    }

    /** 创建并初始化 StringBuilder，null 视为空字符串 */
    public static StringBuilder createBuilder(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append(str == null ? "" : str);
        return sb;
    }

    // ==================== 字符串查询 ====================

    public static int length(CharSequence text) {
        return text == null ? 0 : text.length();
    }

    public static boolean contains(String text, String search) {
        if (text == null || search == null) return false;
        return text.contains(search);
    }

    public static boolean contains(CharSequence text, CharSequence search) {
        if (text == null || search == null) return false;
        return text.toString().contains(search);
    }

    public static boolean startsWith(String text, String prefix) {
        if (text == null || prefix == null) return false;
        return text.startsWith(prefix);
    }

    public static boolean startsWith(CharSequence text, CharSequence prefix) {
        if (text == null || prefix == null) return false;
        return text.toString().startsWith(prefix.toString());
    }

    public static boolean endsWith(String text, String suffix) {
        if (text == null || suffix == null) return false;
        return text.endsWith(suffix);
    }

    public static boolean endsWith(CharSequence text, CharSequence suffix) {
        if (text == null || suffix == null) return false;
        return text.toString().endsWith(suffix.toString());
    }

    // ==================== 业务方法 ====================

    public static String mapToString(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            String val = entry.getValue();
            sb.append(encode(entry.getKey())).append("=").append(val == null ? "" : encode(val));
        }
        return sb.toString();
    }

    public static Map<String, String> stringToMap(String text) {
        Map<String, String> map = new java.util.HashMap<>();
        if (isEmpty(text)) return map;
        String[] pairs = text.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx != -1) {
                map.put(pair.substring(0, idx), decode(pair.substring(idx + 1)));
            } else if (!pair.isEmpty()) {
                map.put(pair, "");
            }
        }
        return map;
    }

    public static String extractDomain(String url) {
        if (isEmpty(url)) return "";
        if (!url.contains("://")) {
            url = url.startsWith("//") ? "http:" + url : "http://" + url;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return "";
            int port = uri.getPort();
            return port > 0 ? host + ":" + port : host;
        } catch (Exception e) {
            // 降级回退：字符串截取，应对未转义字符的 URL
            int schemeEnd = url.indexOf("://");
            int restStart = schemeEnd >= 0 ? schemeEnd + 3 : 0;
            int hostEnd = url.indexOf('/', restStart);
            int queryStart = url.indexOf('?', restStart);
            if (queryStart == -1 || (hostEnd != -1 && hostEnd < queryStart)) queryStart = hostEnd;
            if (queryStart == -1) queryStart = url.length();
            String rawHost = url.substring(restStart, queryStart);
            int bracketEnd = rawHost.indexOf(']');
            if (bracketEnd != -1) {
                int portIdx = rawHost.indexOf(':', bracketEnd + 1);
                return portIdx != -1 ? rawHost.substring(0, portIdx) : rawHost;
            }
            return rawHost;
        }
    }

    public static String extractPath(String url) {
        if (isEmpty(url)) return "";
        if (!url.contains("://")) {
            url = url.startsWith("//") ? "http:" + url : "http://" + url;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (Exception e) {
            // 降级回退：字符串截取，保证不丢失 Path 数据
            int schemeEnd = url.indexOf("://");
            int hostEnd = schemeEnd >= 0 ? url.indexOf('/', schemeEnd + 3) : url.indexOf('/');
            int pathStart = hostEnd >= 0 ? hostEnd : (schemeEnd >= 0 ? schemeEnd + 3 : 0);
            int pathEnd = url.length();
            int q = url.indexOf('?');
            int hash = url.indexOf('#');
            if (q != -1) pathEnd = q;
            if (hash != -1 && hash < pathEnd) pathEnd = hash;
            String fallback = url.substring(pathStart, pathEnd);
            return fallback.isEmpty() ? "/" : fallback;
        }
    }

    public static String extractQuery(String url) {
        if (isEmpty(url)) return "";
        if (!url.contains("://")) {
            url = url.startsWith("//") ? "http:" + url : "http://" + url;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String query = uri.getQuery();
            return query == null ? "" : query;
        } catch (Exception e) {
            // 降级回退：应对包含未转义字符的 URL
            int q = url.indexOf('?');
            if (q == -1) return "";
            int hash = url.indexOf('#', q);
            return hash != -1 ? url.substring(q + 1, hash) : url.substring(q + 1);
        }
    }

    public static String buildUrl(String baseUrl, String path) {
        if (isEmpty(baseUrl)) return path;
        if (isEmpty(path)) return baseUrl;
        String separator = baseUrl.endsWith("/") || path.startsWith("/") ? "" : "/";
        return baseUrl + separator + path;
    }

    /**
     * 修复相对URL为绝对URL
     */
    public static String repairUrl(String base, String url) {
        try {
            // 已是完整 URL
            if (url.startsWith("http://") || url.startsWith("https://")) return url;
            // 协议相对 URL
            if (url.startsWith("//")) return (base != null && base.contains("://") ? base.split(":", 2)[0] : "http") + ":" + url;
            android.net.Uri uri = android.net.Uri.parse(base);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String hostPart = host != null ? (port > 0 ? host + ":" + port : host) : "";
            // 根相对路径
            if (url.startsWith("/")) return scheme + "://" + hostPart + url;
            // 相对路径：拼上 base 的目录部分
            String basePath = uri.getPath();
            if (basePath != null) {
                int lastSlash = basePath.lastIndexOf('/');
                if (lastSlash >= 0) basePath = basePath.substring(0, lastSlash + 1);
                else basePath = "/";
            } else {
                basePath = "/";
            }
            return scheme + "://" + hostPart + basePath + url;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }

    /**
     * 验证URL格式是否有效
     */
    public static boolean isValidUrl(String url) {
        if (isEmpty(url)) return false;
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            return host != null && !host.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证域名是否有效（排除google/facebook）
     */
    public static boolean isDomainValid(String url) {
        if (isEmpty(url)) return false;
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return false;
            return !host.contains("google") && !host.contains("facebook");
        } catch (Exception e) {
            return false;
        }
    }

    public static String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    public static int randomInt(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }

    public static long randomLong(long min, long max) {
        return (long) (Math.random() * (max - min + 1)) + min;
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String unescapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    public static String stripTags(String html) {
        if (html == null) return "";
        return HTML_TAG.matcher(html).replaceAll("");
    }

    public static String cleanWhitespace(String text) {
        if (text == null) return "";
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    // ==================== CSS选择器HTML解析 ====================

    /**
     * 将 dp 单位转换为屏幕像素(px)
     */
    public static int dp2px(int dp) {
        float density = Init.context().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    /**
     * 根据CSS选择器提取元素并获取属性值，支持:eq(index)定位
     *
     * @param html HTML内容
     * @param css  CSS选择器，支持 :eq(n) 取第 n+1 个元素
     * @param attr 要提取的属性名，为空则提取文本
     */
    public static ArrayList<String> cssSelect(String html, String css, String attr) {
        ArrayList<String> result = new ArrayList<>();
        if (html == null || css == null) {
            result.add("");
            return result;
        }
        int eqIndex = css.indexOf(":eq(");
        int targetIndex = -1;
        if (eqIndex >= 0) {
            int closeParen = css.indexOf(')', eqIndex);
            if (closeParen > eqIndex) {
                try {
                    targetIndex = Integer.parseInt(css.substring(eqIndex + 4, closeParen));
                } catch (NumberFormatException ignored) { }
                css = css.substring(0, eqIndex).trim() + css.substring(closeParen + 1).trim();
            }
        }
        String cssTrim = css.trim();
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = cssTrim.isEmpty() ? new Elements(doc.body()) : doc.select(cssTrim);
            if (elements.isEmpty()) { result.add(""); return result; }
            if (targetIndex >= 0 && targetIndex < elements.size()) {
                Element el = elements.get(targetIndex);
                String value = cssExtract(el, attr);
                if (value != null && !value.isEmpty()) result.add(value);
            } else if (targetIndex < 0) {
                for (Element el : elements) {
                    String value = cssExtract(el, attr);
                    if (value != null && !value.isEmpty()) result.add(value);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("Jsoup error: " + cssTrim + " | " + e.getMessage());
        }
        if (result.isEmpty()) result.add("");
        return result;
    }

    private static String cssExtract(Element el, String attr) {
        if (attr == null || attr.isEmpty()) return el.text();
        if ("*".equals(attr)) return el.text().trim();
        try { return el.attr(attr); }
        catch (Exception e) { return el.text().trim(); }
    }

    public static int toInt(String text, int defaultValue) {
        if (!isNumeric(text)) return defaultValue;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            try {
                return (int) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
    }

    public static long toLong(String text, long defaultValue) {
        if (!isNumeric(text)) return defaultValue;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
    }

    public static double toDouble(String text, double defaultValue) {
        if (isNumeric(text)) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static boolean toBoolean(String text, boolean defaultValue) {
        if (isEmpty(text)) return defaultValue;
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    public static String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        if (number < 1000000) return String.format(Locale.US, "%.1fK", number / 1000.0);
        if (number < 1000000000) return String.format(Locale.US, "%.1fM", number / 1000000.0);
        return String.format(Locale.US, "%.1fB", number / 1000000000.0);
    }

    public static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.US, "%d:%02d", minutes, secs);
    }

    // ==================== Unicode转义处理 ====================

    private static final HashMap<String, String> HTML_ENTITY_MAP = new HashMap<>();

    static {
        HTML_ENTITY_MAP.put("amp", "&");
        HTML_ENTITY_MAP.put("lt", "<");
        HTML_ENTITY_MAP.put("gt", ">");
        HTML_ENTITY_MAP.put("quot", "\"");
        HTML_ENTITY_MAP.put("apos", "'");
        HTML_ENTITY_MAP.put("nbsp", "\u00A0");
        HTML_ENTITY_MAP.put("copy", "\u00A9");
        HTML_ENTITY_MAP.put("reg", "\u00AE");
        HTML_ENTITY_MAP.put("trade", "\u2122");
        HTML_ENTITY_MAP.put("Euro", "\u20AC");
        HTML_ENTITY_MAP.put("laquo", "\u00AB");
        HTML_ENTITY_MAP.put("raquo", "\u00BB");
        HTML_ENTITY_MAP.put("middot", "\u00B7");
        HTML_ENTITY_MAP.put("hellip", "\u2026");
        HTML_ENTITY_MAP.put("ndash", "\u2013");
        HTML_ENTITY_MAP.put("mdash", "\u2014");
        HTML_ENTITY_MAP.put("lsquo", "\u2018");
        HTML_ENTITY_MAP.put("rsquo", "\u2019");
        HTML_ENTITY_MAP.put("ldquo", "\u201C");
        HTML_ENTITY_MAP.put("rdquo", "\u201D");
        HTML_ENTITY_MAP.put("bull", "\u2022");
        HTML_ENTITY_MAP.put("permil", "\u2030");
        HTML_ENTITY_MAP.put("lsaquo", "\u2039");
        HTML_ENTITY_MAP.put("rsaquo", "\u203A");
        HTML_ENTITY_MAP.put("oline", "\u203E");
        HTML_ENTITY_MAP.put("frasl", "\u2044");
        HTML_ENTITY_MAP.put("aleph", "\u2135");
        HTML_ENTITY_MAP.put("alpha", "\u03B1");
        HTML_ENTITY_MAP.put("beta", "\u03B2");
        HTML_ENTITY_MAP.put("gamma", "\u03B3");
        HTML_ENTITY_MAP.put("delta", "\u03B4");
        HTML_ENTITY_MAP.put("epsilon", "\u03B5");
        HTML_ENTITY_MAP.put("zeta", "\u03B6");
        HTML_ENTITY_MAP.put("eta", "\u03B7");
        HTML_ENTITY_MAP.put("theta", "\u03B8");
        HTML_ENTITY_MAP.put("iota", "\u03B9");
        HTML_ENTITY_MAP.put("kappa", "\u03BA");
        HTML_ENTITY_MAP.put("lambda", "\u03BB");
        HTML_ENTITY_MAP.put("mu", "\u03BC");
        HTML_ENTITY_MAP.put("nu", "\u03BD");
        HTML_ENTITY_MAP.put("xi", "\u03BE");
        HTML_ENTITY_MAP.put("omicron", "\u03BF");
        HTML_ENTITY_MAP.put("pi", "\u03C0");
        HTML_ENTITY_MAP.put("rho", "\u03C1");
        HTML_ENTITY_MAP.put("sigma", "\u03C3");
        HTML_ENTITY_MAP.put("tau", "\u03C4");
        HTML_ENTITY_MAP.put("upsilon", "\u03C5");
        HTML_ENTITY_MAP.put("phi", "\u03C6");
        HTML_ENTITY_MAP.put("chi", "\u03C7");
        HTML_ENTITY_MAP.put("psi", "\u03C8");
        HTML_ENTITY_MAP.put("omega", "\u03C9");
        HTML_ENTITY_MAP.put("thetasym", "\u03D1");
        HTML_ENTITY_MAP.put("upsih", "\u03D2");
        HTML_ENTITY_MAP.put("piv", "\u03D6");
        HTML_ENTITY_MAP.put("OElig", "\u0152");
        HTML_ENTITY_MAP.put("oelig", "\u0153");
        HTML_ENTITY_MAP.put("Scaron", "\u0160");
        HTML_ENTITY_MAP.put("scaron", "\u0161");
        HTML_ENTITY_MAP.put("Yuml", "\u0178");
        HTML_ENTITY_MAP.put("fnof", "\u0192");
        HTML_ENTITY_MAP.put("circ", "\u02C6");
        HTML_ENTITY_MAP.put("tilde", "\u02DC");
        HTML_ENTITY_MAP.put("ensp", "\u2002");
        HTML_ENTITY_MAP.put("emsp", "\u2003");
        HTML_ENTITY_MAP.put("thinsp", "\u2009");
        HTML_ENTITY_MAP.put("zwnj", "\u200C");
        HTML_ENTITY_MAP.put("zwj", "\u200D");
        HTML_ENTITY_MAP.put("lrm", "\u200E");
        HTML_ENTITY_MAP.put("rlm", "\u200F");
        HTML_ENTITY_MAP.put("sbquo", "\u201A");
        HTML_ENTITY_MAP.put("bdquo", "\u201E");
        HTML_ENTITY_MAP.put("dagger", "\u2020");
        HTML_ENTITY_MAP.put("Dagger", "\u2021");
        HTML_ENTITY_MAP.put("euro", "\u20AC");
        HTML_ENTITY_MAP.put("forall", "\u2200");
        HTML_ENTITY_MAP.put("part", "\u2202");
        HTML_ENTITY_MAP.put("exist", "\u2203");
        HTML_ENTITY_MAP.put("empty", "\u2205");
        HTML_ENTITY_MAP.put("nabla", "\u2207");
        HTML_ENTITY_MAP.put("isin", "\u2208");
        HTML_ENTITY_MAP.put("notin", "\u2209");
        HTML_ENTITY_MAP.put("ni", "\u220B");
        HTML_ENTITY_MAP.put("prod", "\u220F");
        HTML_ENTITY_MAP.put("sum", "\u2211");
        HTML_ENTITY_MAP.put("minus", "\u2212");
        HTML_ENTITY_MAP.put("lowast", "\u2217");
        HTML_ENTITY_MAP.put("radic", "\u221A");
        HTML_ENTITY_MAP.put("prop", "\u221D");
        HTML_ENTITY_MAP.put("infin", "\u221E");
        HTML_ENTITY_MAP.put("ang", "\u2220");
        HTML_ENTITY_MAP.put("and", "\u2227");
        HTML_ENTITY_MAP.put("or", "\u2228");
        HTML_ENTITY_MAP.put("cap", "\u2229");
        HTML_ENTITY_MAP.put("cup", "\u222A");
        HTML_ENTITY_MAP.put("int", "\u222B");
        HTML_ENTITY_MAP.put("there4", "\u2234");
        HTML_ENTITY_MAP.put("sim", "\u223C");
        HTML_ENTITY_MAP.put("cong", "\u2245");
        HTML_ENTITY_MAP.put("asymp", "\u2248");
        HTML_ENTITY_MAP.put("neq", "\u2260");
        HTML_ENTITY_MAP.put("equiv", "\u2261");
        HTML_ENTITY_MAP.put("le", "\u2264");
        HTML_ENTITY_MAP.put("ge", "\u2265");
        HTML_ENTITY_MAP.put("sub", "\u2282");
        HTML_ENTITY_MAP.put("sup", "\u2283");
        HTML_ENTITY_MAP.put("nsub", "\u2284");
        HTML_ENTITY_MAP.put("sube", "\u2286");
        HTML_ENTITY_MAP.put("supe", "\u2287");
        HTML_ENTITY_MAP.put("oplus", "\u2295");
        HTML_ENTITY_MAP.put("otimes", "\u2297");
        HTML_ENTITY_MAP.put("perp", "\u22A5");
        HTML_ENTITY_MAP.put("sdot", "\u22C5");
        HTML_ENTITY_MAP.put("lceil", "\u2308");
        HTML_ENTITY_MAP.put("rceil", "\u2309");
        HTML_ENTITY_MAP.put("lfloor", "\u230A");
        HTML_ENTITY_MAP.put("rfloor", "\u230B");
        HTML_ENTITY_MAP.put("lang", "\u2329");
        HTML_ENTITY_MAP.put("rang", "\u232A");
        HTML_ENTITY_MAP.put("loz", "\u25CA");
        HTML_ENTITY_MAP.put("spades", "\u2660");
        HTML_ENTITY_MAP.put("clubs", "\u2663");
        HTML_ENTITY_MAP.put("hearts", "\u2665");
        HTML_ENTITY_MAP.put("diams", "\u2666");
        HTML_ENTITY_MAP.put("Agrave", "\u00C0");
        HTML_ENTITY_MAP.put("Aacute", "\u00C1");
        HTML_ENTITY_MAP.put("Acirc", "\u00C2");
        HTML_ENTITY_MAP.put("Atilde", "\u00C3");
        HTML_ENTITY_MAP.put("Auml", "\u00C4");
        HTML_ENTITY_MAP.put("AElig", "\u00C6");
        HTML_ENTITY_MAP.put("Ccedil", "\u00C7");
        HTML_ENTITY_MAP.put("Egrave", "\u00C8");
        HTML_ENTITY_MAP.put("Eacute", "\u00C9");
        HTML_ENTITY_MAP.put("Ecirc", "\u00CA");
        HTML_ENTITY_MAP.put("Euml", "\u00CB");
        HTML_ENTITY_MAP.put("Igrave", "\u00CC");
        HTML_ENTITY_MAP.put("Iacute", "\u00CD");
        HTML_ENTITY_MAP.put("Icirc", "\u00CE");
        HTML_ENTITY_MAP.put("Iuml", "\u00CF");
        HTML_ENTITY_MAP.put("ETH", "\u00D0");
        HTML_ENTITY_MAP.put("Ntilde", "\u00D1");
        HTML_ENTITY_MAP.put("Ograve", "\u00D2");
        HTML_ENTITY_MAP.put("Oacute", "\u00D3");
        HTML_ENTITY_MAP.put("Ocirc", "\u00D4");
        HTML_ENTITY_MAP.put("Otilde", "\u00D5");
        HTML_ENTITY_MAP.put("Ouml", "\u00D6");
        HTML_ENTITY_MAP.put("times", "\u00D7");
        HTML_ENTITY_MAP.put("Oslash", "\u00D8");
        HTML_ENTITY_MAP.put("Ugrave", "\u00D9");
        HTML_ENTITY_MAP.put("Uacute", "\u00DA");
        HTML_ENTITY_MAP.put("Ucirc", "\u00DB");
        HTML_ENTITY_MAP.put("Uuml", "\u00DC");
        HTML_ENTITY_MAP.put("Yacute", "\u00DD");
        HTML_ENTITY_MAP.put("THORN", "\u00DE");
        HTML_ENTITY_MAP.put("szlig", "\u00DF");
        HTML_ENTITY_MAP.put("agrave", "\u00E0");
        HTML_ENTITY_MAP.put("aacute", "\u00E1");
        HTML_ENTITY_MAP.put("acirc", "\u00E2");
        HTML_ENTITY_MAP.put("atilde", "\u00E3");
        HTML_ENTITY_MAP.put("auml", "\u00E4");
        HTML_ENTITY_MAP.put("aring", "\u00E5");
        HTML_ENTITY_MAP.put("Aring", "\u00C5");
        HTML_ENTITY_MAP.put("aelig", "\u00E6");
        HTML_ENTITY_MAP.put("ccedil", "\u00E7");
        HTML_ENTITY_MAP.put("egrave", "\u00E8");
        HTML_ENTITY_MAP.put("eacute", "\u00E9");
        HTML_ENTITY_MAP.put("ecirc", "\u00EA");
        HTML_ENTITY_MAP.put("euml", "\u00EB");
        HTML_ENTITY_MAP.put("igrave", "\u00EC");
        HTML_ENTITY_MAP.put("iacute", "\u00ED");
        HTML_ENTITY_MAP.put("icirc", "\u00EE");
        HTML_ENTITY_MAP.put("iuml", "\u00EF");
        HTML_ENTITY_MAP.put("eth", "\u00F0");
        HTML_ENTITY_MAP.put("ntilde", "\u00F1");
        HTML_ENTITY_MAP.put("ograve", "\u00F2");
        HTML_ENTITY_MAP.put("oacute", "\u00F3");
        HTML_ENTITY_MAP.put("ocirc", "\u00F4");
        HTML_ENTITY_MAP.put("otilde", "\u00F5");
        HTML_ENTITY_MAP.put("ouml", "\u00F6");
        HTML_ENTITY_MAP.put("divide", "\u00F7");
        HTML_ENTITY_MAP.put("oslash", "\u00F8");
        HTML_ENTITY_MAP.put("ugrave", "\u00F9");
        HTML_ENTITY_MAP.put("uacute", "\u00FA");
        HTML_ENTITY_MAP.put("ucirc", "\u00FB");
        HTML_ENTITY_MAP.put("uuml", "\u00FC");
        HTML_ENTITY_MAP.put("yacute", "\u00FD");
        HTML_ENTITY_MAP.put("thorn", "\u00FE");
        HTML_ENTITY_MAP.put("yuml", "\u00FF");
    }

    /**
     * 解码 \\uXXXX、八进制转义及HTML实体
     */
    public static String decodeUnicode(String str) {
        if (str == null || str.isEmpty()) return str;
        try {
            StringWriter writer = new StringWriter(str.length() * 2);
            int length = str.length();
            int i = 0;
            while (i < length) {
                int processed = processDecodeUnicodeSequence(str, i, writer);
                if (processed == 0) {
                    char c = str.charAt(i);
                    writer.write(c);
                    i++;
                    if (Character.isHighSurrogate(c) && i < length) {
                        char c2 = str.charAt(i);
                        if (Character.isLowSurrogate(c2)) {
                            writer.write(c2);
                            i++;
                        }
                    }
                } else {
                    i += processed;
                }
            }
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Unicode escape decoding failed", e);
        }
    }

    /**
     * 将字符串编码为 \\uXXXX 格式
     */
    public static String encodeUnicode(String str) {
        if (str == null || str.isEmpty()) return str;
        try {
            StringWriter writer = new StringWriter(str.length() * 2);
            int length = str.length();
            int i = 0;
            while (i < length) {
                int codePoint = str.codePointAt(i);
                if (codePoint >= 32 && codePoint <= 126) {
                    writer.write(codePoint);
                } else if (codePoint <= 0xFFFF) {
                    writer.write("\\u");
                    writer.write(padLeft(Integer.toHexString(codePoint).toUpperCase(Locale.ENGLISH), 4, '0'));
                } else {
                    char[] chars = Character.toChars(codePoint);
                    for (char c : chars) {
                        writer.write("\\u");
                        writer.write(padLeft(Integer.toHexString(c).toUpperCase(Locale.ENGLISH), 4, '0'));
                    }
                }
                i += Character.charCount(codePoint);
            }
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Unicode escape encoding failed", e);
        }
    }

    private static int processDecodeUnicodeSequence(CharSequence seq, int index, Writer writer) throws IOException {
        int result = decodeHtmlEntity(seq, index, writer);
        if (result != 0) return result;
        result = decodeOctalEscape(seq, index, writer);
        if (result != 0) return result;
        return decodeUnicode(seq, index, writer);
    }

    private static int decodeHtmlEntity(CharSequence seq, int index, Writer writer) throws IOException {
        if (seq.length() <= index || seq.charAt(index) != '&') return 0;
        int semi = seq.indexOf(';', index + 1);
        if (semi == -1 || semi > index + 10) return 0;
        String name = seq.subSequence(index + 1, semi).toString();
        String value = HTML_ENTITY_MAP.get(name);
        if (value != null) {
            writer.write(value);
            return semi - index + 1;
        }
        if (name.startsWith("#")) {
            try {
                String numStr = name.substring(1);
                int codePoint;
                if (numStr.startsWith("x") || numStr.startsWith("X")) {
                    codePoint = Integer.parseUnsignedInt(numStr.substring(1), 16);
                } else {
                    codePoint = Integer.parseUnsignedInt(numStr, 10);
                }
                writer.write(Character.toChars(codePoint));
                return semi - index + 1;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static int decodeOctalEscape(CharSequence seq, int index, Writer writer) throws IOException {
        if (seq.length() <= index || seq.charAt(index) != '\\') return 0;
        int len = seq.length() - index - 1;
        if (len <= 0) return 0;
        char c1 = seq.charAt(index + 1);
        if (c1 < '0' || c1 > '7') return 0;
        StringBuilder sb = new StringBuilder();
        sb.append(c1);
        if (len > 1 && isOctalDigit(seq.charAt(index + 2))) {
            sb.append(seq.charAt(index + 2));
            if (len > 2 && c1 <= '3' && isOctalDigit(seq.charAt(index + 3))) {
                sb.append(seq.charAt(index + 3));
            }
        }
        try {
            writer.write(Integer.parseInt(sb.toString(), 8));
            return sb.length() + 1;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int decodeUnicode(CharSequence seq, int index, Writer writer) throws IOException {
        if (seq.length() <= index || seq.charAt(index) != '\\') return 0;
        int i = index + 1;
        if (i >= seq.length() || seq.charAt(i) != 'u') return 0;
        int uCount = 1;
        while (i + uCount < seq.length() && seq.charAt(i + uCount) == 'u') uCount++;
        int hexStart = i + uCount;
        int hexEnd = hexStart + 4;
        if (hexEnd > seq.length()) throw new IllegalArgumentException("Invalid unicode escape");
        String hex = seq.subSequence(hexStart, hexEnd).toString();
        try {
            int codePoint = Integer.parseUnsignedInt(hex, 16);
            writer.write(Character.toChars(codePoint));
            return uCount + 5;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid unicode escape: \\u" + hex, e);
        }
    }

    private static boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }

    private static String padLeft(String str, int length, char padChar) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = str.length(); i < length; i++) sb.append(padChar);
        sb.append(str);
        return sb.toString();
    }
}
