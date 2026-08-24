package com.github.catvod.spider.xbpq.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript变量解析器
 * <p>
 * 从HTML中提取JavaScript变量值和对象属性，支持var/let/const声明的变量提取。
 */
public class JsParser {

    /** 变量声明模式：var name = 'value' / let name = "value" / const name = 'value' */
    private static final Pattern P_JS_VAR = Pattern.compile(
            "(?:var|let|const)\\s+(\\w+)\\s*=\\s*['\"]([^'\"]*)['\"]",
            Pattern.DOTALL
    );

    /** 对象属性模式："url":"value" / href:'value' / title: "value" */
    private static final Pattern P_JS_OBJ_PROP = Pattern.compile(
            "\"?(url|URL|href|title|name|source|from)\"?\\s*:\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /** iframe src提取 */
    private static final Pattern P_IFRAME_SRC = Pattern.compile(
            "<iframe[^>]*src\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );

    /** player对象提取：var player_xxx = {...} */
    private static final Pattern P_PLAYER_OBJ = Pattern.compile(
            "var player_\\w+\\s*=\\s*(\\{.+?\\});",
            Pattern.DOTALL
    );

    /** player URL提取 */
    private static final Pattern P_PLAYER_URL = Pattern.compile(
            "var player_\\w+\\s*=\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
    );

    /** unescape变量 */
    private static final Pattern P_JS_UNESCAPE_VAR = Pattern.compile(
            "var\\s+\\w+\\s*=\\s*unescape\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)",
            Pattern.DOTALL
    );

    /** JS unescape：%uXXXX 十六进制转义 */
    private static final Pattern P_JS_UNICODE = Pattern.compile("(?i)%u([0-9a-f]{4})");
    /** JS unescape：%XX 字节转义 */
    private static final Pattern P_JS_BYTE = Pattern.compile("(?i)%([0-9a-f]{2})");

    /** 视频URL候选模式 */
    private static final Pattern[] P_VIDEO_URL_CANDIDATES = new Pattern[]{
            Pattern.compile("(?:url|src|file|play|link)\\s*[=:]\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("(?:url|src|file|play|link)\\s*[=:]\\s*['\"]([^'\"]+\\.mp4[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("(?:url|src|file|play|link)\\s*[=:]\\s*['\"]([^'\"]+\\.flv[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://[^\\s\"']+\\.(?:m3u8|mp4|flv|avi|mkv)[^\\s\"']*", Pattern.CASE_INSENSITIVE),
    };

    /**
     * 从HTML中提取JavaScript变量值
     *
     * @param html  HTML内容
     * @param param 变量名
     * @return 变量值，未找到返回""
     */
    public static String getVar(String html, String param) {
        if (html == null || html.isEmpty() || param == null || param.isEmpty()) return "";
        try {
            Matcher m = P_JS_VAR.matcher(html);
            while (m.find()) {
                if (param.equals(m.group(1))) {
                    return m.group(2).trim();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * 从HTML中提取JavaScript对象属性值
     *
     * @param html HTML内容
     * @param prop 属性名（如url, title, name等）
     * @return 属性值，未找到返回""
     */
    public static String getObjProp(String html, String prop) {
        if (html == null || html.isEmpty() || prop == null || prop.isEmpty()) return "";
        try {
            Matcher m = P_JS_OBJ_PROP.matcher(html);
            while (m.find()) {
                if (prop.equalsIgnoreCase(m.group(1))) {
                    return m.group(2).trim();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * 提取所有JavaScript变量
     *
     * @param html HTML内容
     * @return 变量名→值的映射
     */
    public static Map<String, String> getAllVars(String html) {
        Map<String, String> vars = new HashMap<>();
        if (html == null || html.isEmpty()) return vars;
        try {
            Matcher m = P_JS_VAR.matcher(html);
            while (m.find()) {
                vars.put(m.group(1), m.group(2).trim());
            }
        } catch (Exception e) {
            // ignore
        }
        return vars;
    }

    /**
     * 提取iframe的src属性
     *
     * @param html HTML内容
     * @return iframe src，未找到返回""
     */
    public static String getIframeSrc(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            Matcher m = P_IFRAME_SRC.matcher(html);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * 提取player对象的URL
     *
     * @param html HTML内容
     * @return player URL，未找到返回""
     */
    public static String getPlayerUrl(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            Matcher m = P_PLAYER_URL.matcher(html);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * 提取player对象的完整JSON配置
     *
     * @param html HTML内容
     * @return player对象JSON字符串，未找到返回""
     */
    public static String getPlayerObj(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            Matcher m = P_PLAYER_OBJ.matcher(html);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * 提取unescape变量值
     *
     * @param html HTML内容
     * @return unescape解码后的值
     */
    public static String getUnescapeVar(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            Matcher m = P_JS_UNESCAPE_VAR.matcher(html);
            if (m.find()) {
                return jsUnescape(m.group(1));
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * JS unescape 解码：%uXXXX 与 %XX 转义。
     * URLDecoder 不支持 %uXXXX 形态，此处自行实现（借鉴 Ccys.unescape）。
     */
    public static String jsUnescape(String text) {
        if (text == null || text.indexOf('%') < 0) return text == null ? "" : text;
        try {
            Matcher m = P_JS_UNICODE.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
            }
            m.appendTail(sb);
            Matcher m2 = P_JS_BYTE.matcher(sb.toString());
            StringBuffer sb2 = new StringBuffer();
            while (m2.find()) {
                m2.appendReplacement(sb2, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m2.group(1), 16))));
            }
            m2.appendTail(sb2);
            return sb2.toString();
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 多级候选正则匹配视频URL
     *
     * @param html HTML内容
     * @return 匹配到的视频URL，未找到返回""
     */
    public static String matchVideoUrl(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            for (Pattern pattern : P_VIDEO_URL_CANDIDATES) {
                Matcher m = pattern.matcher(html);
                if (m.find()) {
                    // 部分候选正则无捕获组，需按 groupCount 兜底取 group(0)
                    String url = (m.groupCount() >= 1 && m.group(1) != null)
                            ? m.group(1).trim() : m.group(0).trim();
                    if (!url.isEmpty()) return url;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * 从HTML中提取所有匹配模式的URL列表
     *
     * @param html HTML内容
     * @return URL列表
     */
    public static List<String> matchAllVideoUrls(String html) {
        List<String> urls = new ArrayList<>();
        if (html == null || html.isEmpty()) return urls;
        try {
            for (Pattern pattern : P_VIDEO_URL_CANDIDATES) {
                Matcher m = pattern.matcher(html);
                while (m.find()) {
                    String url = (m.groupCount() >= 1 && m.group(1) != null)
                            ? m.group(1).trim() : m.group(0).trim();
                    if (!url.isEmpty() && !urls.contains(url)) {
                        urls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return urls;
    }
}
