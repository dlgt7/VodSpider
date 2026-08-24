package com.github.catvod.spider.xbpq.config;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 字符串截取规则类
 * <p>
 * 支持XBPQ的二次截取语法和后处理器语法：
 * <ul>
 *   <li>二次截取：前缀&&后缀</li>
 *   <li>后处理器：[替换:a>>b]、[包含:关键词]、[不包含:关键词]</li>
 *   <li>备用选择器：规则1||规则2</li>
 * </ul>
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class StringCutRule {

    /** 二次截取标记 */
    private static final String CUT_SEPARATOR = "&&";

    /** 备用选择器标记 */
    private static final String OR_SEPARATOR = "\\|\\|";

    /** 后处理器模式 */
    private static final Pattern P_PROC_MARK = Pattern.compile("\\[(替换|包含|不包含):([^\\]]+)\\]");

    /** 替换标记 */
    private static final String REPLACE_TAG = "替换";
    /** 包含标记 */
    private static final String INCLUDE_TAG = "包含";
    /** 不包含标记 */
    private static final String EXCLUDE_TAG = "不包含";

    /**
     * 应用二次截取
     *
     * @param content 原始内容
     * @param cutRule 截取规则（前缀&&后缀）
     * @return 截取后的内容
     */
    public static String applySecondCut(String content, String cutRule) {
        if (content == null || cutRule == null || cutRule.isEmpty()) return content;

        // 备用选择器处理（contains 为字面量匹配，不能用正则表达式常量）
        if (cutRule.contains("||")) {
            String[] selectors = cutRule.split(OR_SEPARATOR);
            for (String selector : selectors) {
                String result = doSecondCut(content, selector.trim());
                if (result != null && !result.isEmpty()) return result;
            }
            return "";
        }

        return doSecondCut(content, cutRule);
    }

    /**
     * 执行二次截取
     */
    private static String doSecondCut(String content, String cutRule) {
        if (content == null || cutRule == null || cutRule.isEmpty()) return "";

        int idx = cutRule.indexOf(CUT_SEPARATOR);
        if (idx < 0) return content;

        String start = cutRule.substring(0, idx).trim();
        String end = cutRule.substring(idx + CUT_SEPARATOR.length()).trim();

        int startPos = content.indexOf(start);
        if (startPos < 0) return "";
        startPos += start.length();

        int endPos = content.indexOf(end, startPos);
        if (endPos < 0) return content.substring(startPos);

        return content.substring(startPos, endPos);
    }

    /**
     * 应用后处理器
     *
     * @param str 原始字符串
     * @return 处理后字符串，不满足条件返回空字符串
     */
    public static String applyPostProcessors(String str) {
        if (str == null || str.isEmpty()) return str;

        Matcher m = P_PROC_MARK.matcher(str);
        StringBuilder result = new StringBuilder(str);
        int offset = 0;

        while (m.find()) {
            int matchStart = m.start() + offset;
            int matchEnd = m.end() + offset;
            String type = m.group(1);
            String value = m.group(2);

            if (REPLACE_TAG.equals(type)) {
                // 替换: a>>b
                String[] parts = value.split(">>");
                if (parts.length == 2) {
                    result.replace(matchStart, matchEnd, parts[1]);
                    offset += (parts[1].length() - (matchEnd - matchStart));
                }
            } else if (INCLUDE_TAG.equals(type)) {
                // 包含检查
                if (!result.toString().contains(value)) {
                    return "";
                }
            } else if (EXCLUDE_TAG.equals(type)) {
                // 不包含检查
                if (result.toString().contains(value)) {
                    return "";
                }
            }
        }

        return result.toString();
    }

    /**
     * 清理HTML标签
     *
     * @param html HTML内容
     * @return 纯文本
     */
    public static String cleanHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+?>", "")
                   .replaceAll("&[a-zA-Z]{1,10};", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * 转义正则特殊字符
     *
     * @param keyword 关键词
     * @return 转义后的字符串
     */
    public static String escapeRegex(String keyword) {
        if (keyword == null) return "";
        return keyword.replaceAll("[\\\\^$.*+?()[\\]{}|]", "\\\\$0");
    }

    /**
     * 将截取规则解析为查找数组
     * <p>
     * 格式：[前缀, 后缀, 左偏移, 右偏移, 回溯层数]；
     * 偏移/回溯仅保留槽位（当前实现不做偏移计算，避免不可达的死代码）。
     *
     * @param rule 截取规则
     * @return 查找数组
     */
    public static String[] parseCutRule(String rule) {
        if (rule == null || rule.isEmpty()) return new String[0];

        String[] result = rule.split(CUT_SEPARATOR);
        if (result.length < 2) return new String[0];

        String[] parsed = new String[5];
        parsed[0] = result[0].trim();      // 前缀
        parsed[1] = result[1].trim();      // 后缀

        // 偏移量仅保留槽位：数组赋值不会抛异常，旧实现 try/catch 为不可达分支
        parsed[2] = result.length > 2 ? result[2].trim() : "0";  // 左偏移（预留）
        parsed[3] = result.length > 3 ? result[3].trim() : "0";  // 右偏移（预留）
        parsed[4] = "0";  // 默认回溯层数（预留）

        return parsed;
    }
}
