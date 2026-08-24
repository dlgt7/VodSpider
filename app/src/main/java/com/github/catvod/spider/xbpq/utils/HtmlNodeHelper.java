package com.github.catvod.spider.xbpq.utils;

/**
 * HTML文本清理工具类
 */
public class HtmlNodeHelper {

    /**
     * 移除HTML标签并清理空白字符
     */
    public static String cleanText(String html) {
        if (html == null) return "";
        return html.replace("\r\n", "")
                .replace("\n", "")
                .replaceAll("<[^>]+?>", "")
                .replaceAll("\\s+", " ")
                .replace("&nbsp;", "")
                .replace("&emsp;", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
    }
}
