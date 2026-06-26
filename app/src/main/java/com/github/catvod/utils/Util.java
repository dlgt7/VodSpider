package com.github.catvod.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.github.catvod.spider.Init;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class Util {

    private static final Pattern THUNDER = Pattern.compile("(magnet|thunder|ed2k):.*");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DOMAIN_PREFIX = Pattern.compile("^(https?://)?(www\\.)?");
    
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
    };
    
    public static final String CHROME = USER_AGENTS[0];
    
    public static final List<String> MEDIA = Arrays.asList("mp4", "mkv", "mov", "wav", "wma", "wmv", "flv", "avi", "iso", "mpg", "ts", "mp3", "aac", "flac", "m4a", "ape", "ogg", "rm", "rmvb", "asf", "webm", "m3u8", "f4v");
    public static final List<String> SUB = Arrays.asList("srt", "ass", "ssa", "vtt", "sub", "smi");
    public static final List<String> IMAGE = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");
    public static final List<String> AUDIO = Arrays.asList("mp3", "wav", "wma", "aac", "flac", "m4a", "ogg", "ape", "opus");
    public static final List<String> VIDEO = Arrays.asList("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m3u8", "ts", "f4v", "rmvb", "rm", "asf", "3gp");

    public static String getRandomUserAgent() {
        return USER_AGENTS[(int) (Math.random() * USER_AGENTS.length)];
    }

    public static boolean isThunder(String url) {
        return THUNDER.matcher(url).find() || isTorrent(url);
    }

    public static boolean isTorrent(String url) {
        return !url.startsWith("magnet") && url.split(";")[0].endsWith(".torrent");
    }

    public static boolean isSub(String text) {
        return SUB.contains(getExt(text).toLowerCase());
    }

    public static boolean isMedia(String text) {
        return MEDIA.contains(getExt(text).toLowerCase());
    }

    public static boolean isImage(String text) {
        return IMAGE.contains(getExt(text).toLowerCase());
    }

    public static boolean isAudio(String text) {
        return AUDIO.contains(getExt(text).toLowerCase());
    }

    public static boolean isVideo(String text) {
        return VIDEO.contains(getExt(text).toLowerCase());
    }

    public static String getExt(String name) {
        return name.contains(".") ? name.substring(name.lastIndexOf(".") + 1).toLowerCase() : name.toLowerCase();
    }

    public static String getSize(double size) {
        if (size <= 0) return "";
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String removeExt(String text) {
        return text.contains(".") ? text.substring(0, text.lastIndexOf(".")) : text;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) {
            return text.substring(0, text.length() - num);
        } else {
            return text;
        }
    }

    public static String getVar(String data, String param) {
        for (String var : data.split("var")) if (var.contains(param)) return checkVar(var);
        return "";
    }

    private static String checkVar(String var) {
        if (var.contains("'")) return var.split("'")[1];
        if (var.contains("\"")) return var.split("\"")[1];
        return "";
    }

    public static void copy(String text) {
        ClipboardManager manager = (ClipboardManager) Init.context().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("fongmi", text));
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
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    public static String base64UrlEncode(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64UrlDecode(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String uuid(boolean dash) {
        String uuid = UUID.randomUUID().toString();
        return dash ? uuid : uuid.replace("-", "");
    }

    public static String md5(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
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

    public static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static boolean isNotEmpty(String text) {
        return !isEmpty(text);
    }

    public static String defaultIfEmpty(String text, String defaultValue) {
        return isEmpty(text) ? defaultValue : text;
    }

    public static String trim(String text) {
        return text == null ? "" : text.trim();
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

    public static String formatNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format(Locale.getDefault(), "%.1fK", number / 1000.0);
        } else if (number < 1000000000) {
            return String.format(Locale.getDefault(), "%.1fM", number / 1000000.0);
        } else {
            return String.format(Locale.getDefault(), "%.1fB", number / 1000000000.0);
        }
    }

    public static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, secs);
        }
    }

    public static String join(String delimiter, String[] array) {
        if (array == null || array.length == 0) {
            return "";
        }
        return String.join(delimiter, array);
    }

    public static String join(String delimiter, List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(delimiter, list);
    }

    public static String replaceAll(String text, String target, String replacement) {
        if (text == null) {
            return "";
        }
        return text.replaceAll(target, replacement);
    }

    public static String replace(String text, String target, String replacement) {
        if (text == null) {
            return "";
        }
        return text.replace(target, replacement);
    }

    public static boolean contains(String text, String search) {
        if (text == null || search == null) {
            return false;
        }
        return text.contains(search);
    }

    public static boolean startsWith(String text, String prefix) {
        if (text == null || prefix == null) {
            return false;
        }
        return text.startsWith(prefix);
    }

    public static boolean endsWith(String text, String suffix) {
        if (text == null || suffix == null) {
            return false;
        }
        return text.endsWith(suffix);
    }

    public static String mapToString(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    public static Map<String, String> stringToMap(String text) {
        Map<String, String> map = new java.util.HashMap<>();
        if (isEmpty(text)) {
            return map;
        }
        String[] pairs = text.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                map.put(keyValue[0], decode(keyValue[1]));
            }
        }
        return map;
    }

    public static String extractDomain(String url) {
        if (isEmpty(url)) {
            return "";
        }
        try {
            String domain = DOMAIN_PREFIX.matcher(url).replaceAll("");
            int index = domain.indexOf('/');
            return index > 0 ? domain.substring(0, index) : domain;
        } catch (Exception e) {
            return "";
        }
    }

    public static String extractPath(String url) {
        if (isEmpty(url)) {
            return "";
        }
        try {
            int index = url.indexOf('/', url.indexOf("://") + 3);
            return index > 0 ? url.substring(index) : "/";
        } catch (Exception e) {
            return "";
        }
    }

    public static String extractQuery(String url) {
        if (isEmpty(url)) {
            return "";
        }
        try {
            int index = url.indexOf('?');
            return index > 0 ? url.substring(index + 1) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String buildUrl(String baseUrl, String path) {
        if (isEmpty(baseUrl)) {
            return path;
        }
        if (isEmpty(path)) {
            return baseUrl;
        }
        String separator = baseUrl.endsWith("/") || path.startsWith("/") ? "" : "/";
        return baseUrl + separator + path;
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
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String unescapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    public static String stripTags(String html) {
        if (html == null) {
            return "";
        }
        return HTML_TAG.matcher(html).replaceAll("");
    }

    public static String cleanWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    public static boolean isNumeric(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int toInt(String text, int defaultValue) {
        if (isNumeric(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static long toLong(String text, long defaultValue) {
        if (isNumeric(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
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
        if (isEmpty(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }
}