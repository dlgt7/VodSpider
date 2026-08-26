package com.github.catvod.spider.xbpq.utils;

import java.util.regex.Pattern;

/**
 * HTML文本清理工具类
 * <p>
 * 性能说明：cleanText 处于字段提取热路径（每个列表项的每个字段都会调用），
 * 原实现每次调用都通过 replaceAll 隐式编译 Pattern，此处改为静态预编译。
 */
public class HtmlNodeHelper {

    /** HTML 标签（预编译） */
    private static final Pattern P_TAG = Pattern.compile("<[^>]+?>");
    /** 连续空白（预编译） */
    private static final Pattern P_WHITESPACE = Pattern.compile("\\s+");

    /**
     * 移除HTML标签并清理空白字符
     */
    public static String cleanText(String html) {
        if (html == null) return "";
        String text = html.replace("\r\n", "").replace("\n", "");
        text = P_TAG.matcher(text).replaceAll("");
        text = P_WHITESPACE.matcher(text).replaceAll(" ");
        return text.replace("&nbsp;", "")
                .replace("&emsp;", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
    }
}
