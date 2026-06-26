package com.github.catvod.js.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSUtil {

    private static Proxy proxy;

    public static Proxy getProxy() {
        return proxy;
    }

    public static void setProxy(String host, int port) {
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        SpiderDebug.log("Proxy set: " + host + ":" + port);
    }

    public static void clearProxy() {
        proxy = null;
        SpiderDebug.log("Proxy cleared");
    }

    public static boolean hasProxy() {
        return proxy != null;
    }

    public static String base64Encode(String str) {
        try {
            return android.util.Base64.encodeToString(str.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String base64Decode(String str) {
        try {
            return new String(android.util.Base64.decode(str, android.util.Base64.NO_WRAP), "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String base64EncodeUrl(String str) {
        try {
            return android.util.Base64.encodeToString(str.getBytes("UTF-8"), android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String base64DecodeUrl(String str) {
        try {
            return new String(android.util.Base64.decode(str, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE), "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String urlEncode(String str) {
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return str;
        }
    }

    public static String urlDecode(String str) {
        try {
            return java.net.URLDecoder.decode(str, "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return str;
        }
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (TextUtils.isEmpty(query)) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(urlDecode(pair[0]), urlDecode(pair[1]));
            } else if (pair.length == 1) {
                params.put(urlDecode(pair[0]), "");
            }
        }
        return params;
    }

    public static String buildQuery(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    public static String join(String delimiter, List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return TextUtils.join(delimiter, list);
    }

    public static String join(String delimiter, String[] array) {
        if (array == null || array.length == 0) return "";
        return TextUtils.join(delimiter, array);
    }

    public static List<String> split(String str, String delimiter) {
        List<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(str)) return list;
        String[] parts = str.split(delimiter);
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                list.add(part.trim());
            }
        }
        return list;
    }

    public static String[] splitArray(String str, String delimiter) {
        if (TextUtils.isEmpty(str)) return new String[0];
        return str.split(delimiter);
    }

    public static String regexExtract(String str, String pattern) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(str);
            if (m.find()) {
                return m.groupCount() > 0 ? m.group(1) : m.group(0);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    public static List<String> regexExtractAll(String str, String pattern) {
        List<String> results = new ArrayList<>();
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(str);
            while (m.find()) {
                results.add(m.groupCount() > 0 ? m.group(1) : m.group(0));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return results;
    }

    public static boolean regexTest(String str, String pattern) {
        try {
            return Pattern.compile(pattern).matcher(str).find();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return false;
        }
    }

    public static String regexReplace(String str, String pattern, String replacement) {
        try {
            return str.replaceAll(pattern, replacement);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return str;
        }
    }

    public static String regexReplaceFirst(String str, String pattern, String replacement) {
        try {
            return str.replaceFirst(pattern, replacement);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return str;
        }
    }

    public static String toJson(Object obj) {
        try {
            if (obj instanceof Map) {
                return new JSONObject((Map) obj).toString();
            } else if (obj instanceof List) {
                return new JSONArray((List) obj).toString();
            } else if (obj instanceof String) {
                return JSONObject.quote((String) obj);
            } else {
                return obj != null ? obj.toString() : "";
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static JSONObject parseJson(String json) {
        try {
            return new JSONObject(json);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject();
        }
    }

    public static JSONArray parseJsonArray(String json) {
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONArray();
        }
    }

    public static String getString(JSONObject obj, String key) {
        return getString(obj, key, "");
    }

    public static String getString(JSONObject obj, String key, String defaultValue) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getString(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return defaultValue;
    }

    public static int getInt(JSONObject obj, String key) {
        return getInt(obj, key, 0);
    }

    public static int getInt(JSONObject obj, String key, int defaultValue) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getInt(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return defaultValue;
    }

    public static long getLong(JSONObject obj, String key) {
        return getLong(obj, key, 0L);
    }

    public static long getLong(JSONObject obj, String key, long defaultValue) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getLong(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return defaultValue;
    }

    public static double getDouble(JSONObject obj, String key) {
        return getDouble(obj, key, 0.0);
    }

    public static double getDouble(JSONObject obj, String key, double defaultValue) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getDouble(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return defaultValue;
    }

    public static boolean getBoolean(JSONObject obj, String key) {
        return getBoolean(obj, key, false);
    }

    public static boolean getBoolean(JSONObject obj, String key, boolean defaultValue) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getBoolean(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return defaultValue;
    }

    public static JSONObject getJSONObject(JSONObject obj, String key) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getJSONObject(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return new JSONObject();
    }

    public static JSONArray getJSONArray(JSONObject obj, String key) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getJSONArray(key);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return new JSONArray();
    }

    public static String buildUrl(String baseUrl, String path) {
        if (TextUtils.isEmpty(baseUrl)) return path;
        if (TextUtils.isEmpty(path)) return baseUrl;
        if (path.startsWith("http")) return path;
        String separator = baseUrl.endsWith("/") || path.startsWith("/") ? "" : "/";
        return baseUrl + separator + path;
    }

    public static String extractDomain(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getHost();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractPath(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getPath();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractQuery(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getQuery();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractFragment(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getFragment();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String md5(String str) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String sha256(String str) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] array = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }

    public static String uuidNoDash() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
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

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static String trim(String str) {
        return str == null ? "" : str.trim();
    }

    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) return str;
        return str.substring(0, maxLength);
    }

    public static String truncate(String str, int maxLength, String suffix) {
        if (str == null || str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + suffix;
    }
}