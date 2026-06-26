package com.github.catvod.js.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern PATTERN_URL = Pattern.compile("(https?://[^\\s]+)");
    private static final Pattern PATTERN_JSON = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern PATTERN_ARRAY = Pattern.compile("\\[[^\\[\\]]*\\]");
    private static final Pattern PATTERN_NUMBER = Pattern.compile("-?\\d+\\.?\\d*");
    private static final Pattern PATTERN_EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PATTERN_PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern PATTERN_IP = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern PATTERN_HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern PATTERN_HTML_ENTITY = Pattern.compile("&[a-zA-Z]+;|&#\\d+;");

    public static String extractUrl(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_URL.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    public static List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return urls;
        Matcher m = PATTERN_URL.matcher(text);
        while (m.find()) {
            urls.add(m.group(1));
        }
        return urls;
    }

    public static String extractJson(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_JSON.matcher(text);
        return m.find() ? m.group(0) : "";
    }

    public static List<String> extractJsonObjects(String text) {
        List<String> objects = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return objects;
        Matcher m = PATTERN_JSON.matcher(text);
        while (m.find()) {
            objects.add(m.group(0));
        }
        return objects;
    }

    public static String extractArray(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_ARRAY.matcher(text);
        return m.find() ? m.group(0) : "";
    }

    public static List<String> extractArrays(String text) {
        List<String> arrays = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return arrays;
        Matcher m = PATTERN_ARRAY.matcher(text);
        while (m.find()) {
            arrays.add(m.group(0));
        }
        return arrays;
    }

    public static String extractNumber(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_NUMBER.matcher(text);
        return m.find() ? m.group(0) : "";
    }

    public static List<String> extractNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return numbers;
        Matcher m = PATTERN_NUMBER.matcher(text);
        while (m.find()) {
            numbers.add(m.group(0));
        }
        return numbers;
    }

    public static String extractEmail(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_EMAIL.matcher(text);
        return m.find() ? m.group(0) : "";
    }

    public static List<String> extractEmails(String text) {
        List<String> emails = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return emails;
        Matcher m = PATTERN_EMAIL.matcher(text);
        while (m.find()) {
            emails.add(m.group(0));
        }
        return emails;
    }

    public static String extractPhone(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_PHONE.matcher(text);
        return m.find() ? m.group(0) : "";
    }

    public static List<String> extractPhones(String text) {
        List<String> phones = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return phones;
        Matcher m = PATTERN_PHONE.matcher(text);
        while (m.find()) {
            phones.add(m.group(0));
        }
        return phones;
    }

    public static String extractIp(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = PATTERN_IP.matcher(text);
        return m.find() ? m.group(0) : "";
    }

    public static List<String> extractIps(String text) {
        List<String> ips = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return ips;
        Matcher m = PATTERN_IP.matcher(text);
        while (m.find()) {
            ips.add(m.group(0));
        }
        return ips;
    }

    public static String stripHtmlTags(String html) {
        if (TextUtils.isEmpty(html)) return "";
        return PATTERN_HTML_TAG.matcher(html).replaceAll("");
    }

    public static String unescapeHtmlEntities(String text) {
        if (TextUtils.isEmpty(text)) return "";
        text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
        
        Pattern pattern = Pattern.compile("&#(\\d+);");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, String.valueOf((char) Integer.parseInt(matcher.group(1))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String cleanHtml(String html) {
        if (TextUtils.isEmpty(html)) return "";
        return unescapeHtmlEntities(stripHtmlTags(html)).trim();
    }

    public static Map<String, String> parseUrlParams(String url) {
        Map<String, String> params = new HashMap<>();
        if (TextUtils.isEmpty(url)) return params;
        try {
            Uri uri = Uri.parse(url);
            for (String key : uri.getQueryParameterNames()) {
                params.put(key, uri.getQueryParameter(key));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return params;
    }

    public static String buildUrlParams(String baseUrl, Map<String, String> params) {
        if (TextUtils.isEmpty(baseUrl)) return "";
        if (params == null || params.isEmpty()) return baseUrl;
        try {
            Uri.Builder builder = Uri.parse(baseUrl).buildUpon();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            return builder.build().toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return baseUrl;
        }
    }

    public static String extractDomain(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            return uri.getHost();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractPath(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            return uri.getPath();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractQuery(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            return uri.getQuery();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractFragment(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            return uri.getFragment();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String extractScheme(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            return uri.getScheme();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static int extractPort(String url) {
        if (TextUtils.isEmpty(url)) return -1;
        try {
            Uri uri = Uri.parse(url);
            return uri.getPort();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return -1;
        }
    }

    public static String extractBetween(String text, String start, String end) {
        if (TextUtils.isEmpty(text)) return "";
        try {
            int startIndex = text.indexOf(start);
            if (startIndex == -1) return "";
            startIndex += start.length();
            int endIndex = text.indexOf(end, startIndex);
            if (endIndex == -1) return "";
            return text.substring(startIndex, endIndex);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static List<String> extractAllBetween(String text, String start, String end) {
        List<String> results = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return results;
        try {
            int startIndex = 0;
            while (true) {
                int foundStart = text.indexOf(start, startIndex);
                if (foundStart == -1) break;
                foundStart += start.length();
                int foundEnd = text.indexOf(end, foundStart);
                if (foundEnd == -1) break;
                results.add(text.substring(foundStart, foundEnd));
                startIndex = foundEnd + end.length();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return results;
    }

    public static String extractAttribute(String html, String tag, String attr) {
        if (TextUtils.isEmpty(html)) return "";
        try {
            Pattern pattern = Pattern.compile("<" + tag + "[^>]*" + attr + "=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(html);
            return m.find() ? m.group(1) : "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static List<String> extractAttributes(String html, String tag, String attr) {
        List<String> results = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return results;
        try {
            Pattern pattern = Pattern.compile("<" + tag + "[^>]*" + attr + "=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(html);
            while (m.find()) {
                results.add(m.group(1));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return results;
    }

    public static String extractText(String html, String tag) {
        if (TextUtils.isEmpty(html)) return "";
        try {
            Pattern pattern = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = pattern.matcher(html);
            return m.find() ? cleanHtml(m.group(1)) : "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static List<String> extractTexts(String html, String tag) {
        List<String> results = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return results;
        try {
            Pattern pattern = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = pattern.matcher(html);
            while (m.find()) {
                results.add(cleanHtml(m.group(1)));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return results;
    }

    public static boolean isValidUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            Uri uri = Uri.parse(url);
            return !TextUtils.isEmpty(uri.getScheme()) && !TextUtils.isEmpty(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidJson(String json) {
        if (TextUtils.isEmpty(json)) return false;
        try {
            new JSONObject(json);
            return true;
        } catch (Exception e) {
            try {
                new org.json.JSONArray(json);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    public static boolean isValidEmail(String email) {
        if (TextUtils.isEmpty(email)) return false;
        return PATTERN_EMAIL.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (TextUtils.isEmpty(phone)) return false;
        return PATTERN_PHONE.matcher(phone).matches();
    }

    public static boolean isValidIp(String ip) {
        if (TextUtils.isEmpty(ip)) return false;
        if (!PATTERN_IP.matcher(ip).matches()) return false;
        String[] parts = ip.split("\\.");
        for (String part : parts) {
            int num = Integer.parseInt(part);
            if (num < 0 || num > 255) return false;
        }
        return true;
    }
}