package com.github.catvod.bean.xyqbiu;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Crypto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XYQBiu 解析器通用工具方法集合。
 * 抽取自 CategoryContentParser / SearchContentParser / DetailContentParser 中重复的静态方法。
 */
public final class ParseUtils {

    private ParseUtils() {
    }

    /** 需要转义的正则特殊字符 */
    private static final String[] REGEX_SPECIAL_CHARS = {
            "/", "+", "$", ".", ")", "*", "(", "?",
            "[", "|", "^", "{", "}"
    };

    /**
     * 转义正则特殊字符：/ + $ . ) * ( ? [ | ^ { }
     *
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    public static String escapeRegex(String str) {
        if (str == null || str.isEmpty()) return str;
        for (String c : REGEX_SPECIAL_CHARS) {
            if (str.contains(c)) {
                str = str.replace(c, "\\" + c);
            }
        }
        return str;
    }

    /**
     * 计算小写十六进制 MD5 哈希。
     * <p>等价于 {@link Crypto#md5(String)}，保留此方法以兼容现有调用。</p>
     *
     * @param input 原始字符串
     * @return 32 位小写十六进制 MD5 哈希
     */
    public static String md5Hex(String input) {
        return Crypto.md5(input);
    }

    /**
     * 拼接 base 与相对路径。
     * <p>使用 java.net.URL 的规范化拼接，失败时回退到字符串拼接。</p>
     *
     * @param base     基础 URL
     * @param relative 相对路径
     * @return 完整 URL
     */
    public static String urlCombine(String base, String relative) {
        if (relative == null || relative.isEmpty()) return base;
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative;
        try {
            URL baseUrl = new URL(base);
            return new URL(baseUrl, relative).toString();
        } catch (Exception e) {
            if (base.endsWith("/") && relative.startsWith("/")) {
                return base + relative.substring(1);
            }
            if (!base.endsWith("/") && !relative.startsWith("/")) {
                return base + "/" + relative;
            }
            return base + relative;
        }
    }

    /**
     * 动态解析点号分隔的 JSON 多层路径（支持任意深度）。
     * <p>替代 CategoryContentParser / SearchContentParser 中 1~4 级硬编码的 if-else 链，
     * 避免嵌套超过 4 级时静默失败。</p>
     *
     * @param jsonObject 起始 JSON 对象
     * @param path       点号分隔的路径，如 "data.list" / "a.b.c.d"
     * @return 路径末端的 JSONArray；任一中间节点缺失或类型不符返回 null
     */
    public static JSONArray getJsonArrayByPath(JSONObject jsonObject, String path) {
        if (jsonObject == null || path == null || path.isEmpty()) return null;
        try {
            String[] keys = path.split("\\.");
            Object current = jsonObject;
            for (int i = 0; i < keys.length; i++) {
                if (!(current instanceof JSONObject)) return null;
                JSONObject obj = (JSONObject) current;
                if (i == keys.length - 1) {
                    return obj.optJSONArray(keys[i]);
                }
                current = obj.optJSONObject(keys[i]);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    /**
     * 统一的正则提取方法，替换 CategoryContentParser / SearchContentParser 中重复实现。
     * <p>在 text 中查找所有 pre...suf 之间的内容并 trim 后返回。</p>
     * <ul>
     *   <li>text 为 null → 返回 [""]</li>
     *   <li>pre 和 suf 均为空 → 返回 [text]</li>
     *   <li>pre/suf 中的全角 ＆＆ 先还原为半角 &</li>
     *   <li>无匹配 → 返回 [""]，调用方需判空再使用 get(0)</li>
     * </ul>
     *
     * @param text 待搜索文本
     * @param pre  前缀（会被正则转义）
     * @param suf  后缀（会被正则转义）
     * @return 匹配结果列表，至少包含一个元素（无匹配时为空串）
     */
    public static ArrayList<String> regexExtract(String text, String pre, String suf) {
        ArrayList<String> result = new ArrayList<>();
        if (text == null) {
            result.add("");
            return result;
        }
        if ((pre == null || pre.isEmpty()) && (suf == null || suf.isEmpty())) {
            result.add(text);
            return result;
        }
        try {
            String escapedPre = escapeRegex((pre == null ? "" : pre).replaceAll("＆＆", "&"));
            String escapedSuf = escapeRegex((suf == null ? "" : suf).replaceAll("＆＆", "&"));
            Pattern pattern = Pattern.compile(escapedPre + "(.*?)" + escapedSuf);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                result.add(matcher.group(1).trim());
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    /**
     * 安全分割字符串，限制返回数组长度。
     *
     * @param str   待分割字符串
     * @param regex 分隔正则
     * @param limit 期望最小段数
     * @return 分割后的数组，若 str 为空或分割后长度不足则返回空数组
     */
    public static String[] safeSplit(String str, String regex, int limit) {
        if (str == null || str.isEmpty()) return new String[0];
        String[] parts = str.split(regex);
        if (parts.length < limit) return new String[0];
        return parts;
    }

    /**
     * 安全截取字符串区间。
     *
     * @param str        原始字符串
     * @param beginIndex 起始索引（含）
     * @return 截取后的字符串，索引越界时返回空串
     */
    public static String safeSubstring(String str, int beginIndex) {
        if (str == null || beginIndex < 0 || beginIndex >= str.length()) return "";
        return str.substring(beginIndex);
    }

    /**
     * 安全截取字符串区间。
     *
     * @param str        原始字符串
     * @param beginIndex 起始索引（含）
     * @param endIndex   结束索引（不含）
     * @return 截取后的字符串，索引越界时返回空串
     */
    public static String safeSubstring(String str, int beginIndex, int endIndex) {
        if (str == null || beginIndex < 0 || endIndex > str.length() || beginIndex > endIndex) return "";
        return str.substring(beginIndex, endIndex);
    }
}
