package com.github.catvod.spider.xbpq.config;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.catvod.crawler.SpiderDebug;

/**
 * CSS选择器规则解析类
 * <p>
 * 支持完整的CSS选择器语法，包括：
 * <ul>
 *   <li>基本选择器：.class, #id, tag</li>
 *   <li>属性选择器：[attr], [attr=value]</li>
 *   <li>伪类选择器：:eq(n), :first, :last, :nth-child(n)</li>
 *   <li>组合选择器：> 子选择器, 空格后代选择器</li>
 *   <li>提取模式：@text, @html, @href, @src</li>
 * </ul>
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class CssRule {

    /** CSS选择器协议前缀 */
    public static final String CSS_PREFIX = "css:";
    public static final String CSS_PREFIX_FULL = "css://";

    /** 属性提取标记 */
    private static final char ATTR_MARKER = '@';

    /** 文本提取标记 */
    private static final String TEXT_EXTRACT = "@text";
    private static final String OWN_TEXT_EXTRACT = "@ownText";
    private static final String HTML_EXTRACT = "@html";
    private static final String OUTER_HTML_EXTRACT = "@outerHtml";

    /** CSS :eq(n) 选择器 */
    private static final Pattern P_CSS_EQ = Pattern.compile(":eq\\s*\\(\\s*(\\d+)\\s*\\)");
    /** CSS [n] 下标选择器 */
    private static final Pattern P_CSS_INDEX = Pattern.compile("\\[\\s*(\\d+)\\s*\\]$");
    /** 最后一个元素特殊索引 */
    public static final int LAST_INDEX = -1;

    /**
     * CSS提取模式枚举（提升至CssRule层，供CssRuleInfo与解析逻辑共享）
     */
    public enum ExtractMode {
        TEXT, OWN_TEXT, HTML, OUTER_HTML, ATTRIBUTE
    }

    /**
     * CSS规则信息
     */
    public static class CssRuleInfo {
        public String selector;      // CSS选择器
        public ExtractMode mode;     // 提取模式
        public String attributeName; // 属性名（mode=ATTRIBUTE时使用）
        public int index;            // 元素索引
        public String originalRule;  // 原始规则

        @Override
        public String toString() {
            return String.format("CssRuleInfo{selector='%s', mode=%s, attr='%s', index=%d}",
                    selector, mode, attributeName, index);
        }
    }

    /**
     * 判断规则字符串是否为CSS选择器格式
     *
     * @param rule 规则字符串
     * @return true如果是CSS选择器
     */
    public static boolean isCssRule(String rule) {
        return rule != null && (rule.startsWith(CSS_PREFIX_FULL) || rule.startsWith(CSS_PREFIX));
    }

    /**
     * 解析CSS选择器规则
     *
     * @param rule 原始规则字符串
     * @return CssRuleInfo，解析失败返回null
     */
    public static CssRuleInfo parseRule(String rule) {
        if (rule == null || rule.isEmpty()) return null;

        try {
            // 去掉协议前缀
            String cleanRule = stripPrefix(rule);

            // CSS简写语法转换
            cleanRule = parseCssShortSyntax(cleanRule);

            // 解析提取模式
            ExtractMode mode = ExtractMode.TEXT;
            String attrName = "";
            int index = 0;

            if (cleanRule.contains(TEXT_EXTRACT)) {
                mode = ExtractMode.TEXT;
                cleanRule = cleanRule.replace(TEXT_EXTRACT, "");
            } else if (cleanRule.contains(OWN_TEXT_EXTRACT)) {
                mode = ExtractMode.OWN_TEXT;
                cleanRule = cleanRule.replace(OWN_TEXT_EXTRACT, "");
            } else if (cleanRule.contains(HTML_EXTRACT)) {
                mode = ExtractMode.HTML;
                cleanRule = cleanRule.replace(HTML_EXTRACT, "");
            } else if (cleanRule.contains(OUTER_HTML_EXTRACT)) {
                mode = ExtractMode.OUTER_HTML;
                cleanRule = cleanRule.replace(OUTER_HTML_EXTRACT, "");
            } else if (cleanRule.indexOf(ATTR_MARKER) != -1) {
                int atIdx = cleanRule.indexOf(ATTR_MARKER);
                attrName = cleanRule.substring(atIdx + 1).trim();
                cleanRule = cleanRule.substring(0, atIdx);
                mode = ExtractMode.ATTRIBUTE;
            }

            // 处理索引 :eq(n) 或 [n]
            index = parseIndex(cleanRule);
            cleanRule = cleanIndexMarkers(cleanRule);

            // 清理选择器
            cleanRule = cleanRule.trim();

            CssRuleInfo info = new CssRuleInfo();
            info.selector = cleanRule;
            info.mode = mode;
            info.attributeName = attrName;
            info.index = index;
            info.originalRule = rule;
            return info;
        } catch (Exception e) {
            SpiderDebug.log("parseCssRule error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 去掉CSS协议前缀
     */
    public static String stripPrefix(String rule) {
        if (rule.startsWith(CSS_PREFIX_FULL)) {
            return rule.substring(CSS_PREFIX_FULL.length());
        } else if (rule.startsWith(CSS_PREFIX)) {
            return rule.substring(CSS_PREFIX.length());
        }
        return rule;
    }

    /**
     * 解析精简语法：
     * <ul>
     *   <li>css:div.item->text => div.item:text</li>
     *   <li>css:div.item->attr(src) => div.item[attr]</li>
     *   <li>p:span->text => span:text（p 仅当含 -> 时才作为 tag 前缀）</li>
     *   <li>tag:a->attr(href) => a[href]</li>
     *   <li>tag:.item => .item（纯选择器无 -> 时直接返回）</li>
     * </ul>
     */
    public static String parseCssShortSyntax(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }

        int arrowIdx = expr.indexOf("->");

        // 无 -> 时直接返回原表达式（包括 tag:.class 这种纯选择器写法）
        if (arrowIdx < 0) {
            // 处理 tag:p 或 p:xxx 无前缀简写
            if (expr.startsWith("p:")) {
                String suffix = expr.substring(2);
                // 不含 -> 且是简单标签选择器时才简化（排除 .class, #id 等 CSS 选择器）
                if (!suffix.isEmpty() && !suffix.startsWith(".") && !suffix.startsWith("#") && !suffix.startsWith("[")) {
                    return suffix;
                }
            }
            if (expr.startsWith("tag:")) {
                return expr.substring(4);
            }
            return expr;
        }

        // 有 -> 的完整精简语法
        String left = expr.substring(0, arrowIdx).trim();
        String right = expr.substring(arrowIdx + 2).trim();

        String tag = null;
        String selector = left;

        // 处理 tag:p 前缀
        if (left.startsWith("tag:")) {
            tag = left.substring(4).trim();
            selector = "";
        } else if (left.startsWith("p:")) {
            tag = left.substring(2).trim();
            selector = "";
        }

        if (right.contains("(") && right.contains(")")) {
            int start = right.indexOf('(');
            int end = right.indexOf(')');
            String attrName = right.substring(start + 1, end);
            String attrExpr = "[" + attrName + "]";
            return selector.isEmpty() ? tag + attrExpr : selector + " " + tag + attrExpr;
        } else {
            return selector.isEmpty() ? tag + ":" + right : selector + " " + tag + ":" + right;
        }
    }

    /**
     * 解析元素索引
     */
    private static int parseIndex(String rule) {
        // 检查 :eq(n)
        Matcher eqMatcher = P_CSS_EQ.matcher(rule);
        if (eqMatcher.find()) {
            return Integer.parseInt(eqMatcher.group(1));
        }

        // :first / :last（jsoup 不识别，需自行转索引并从选择器中清除）
        if (rule.contains(":last")) return LAST_INDEX;
        if (rule.contains(":first")) return 0;

        // 检查 [n]
        Matcher indexMatcher = P_CSS_INDEX.matcher(rule);
        if (indexMatcher.find()) {
            return Integer.parseInt(indexMatcher.group(1));
        }

        return 0;
    }

    /**
     * 清理索引标记
     */
    private static String cleanIndexMarkers(String rule) {
        rule = P_CSS_EQ.matcher(rule).replaceAll("");
        rule = rule.replace(":first", "").replace(":last", "");
        rule = P_CSS_INDEX.matcher(rule).replaceAll("");
        return rule.trim();
    }

    /**
     * 从HTML中提取CSS规则匹配的内容
     *
     * @param html   HTML内容
     * @param cssRule CSS规则
     * @param index  元素索引（-1表示最后一个）
     * @return 提取的值
     */
    public static String extractByCss(String html, String cssRule, int index) {
        if (html == null || html.isEmpty() || !isCssRule(cssRule)) return "";
        try {
            CssRuleInfo info = parseRule(cssRule);
            if (info == null) return "";

            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(info.selector);

            if (elements.isEmpty()) return "";

            // 规则自身的 :eq(n)/[n]/:last 索引优先生效，入参 index 仅在规则未指定时兜底，
            // 修复旧实现忽略 info.index 导致 :eq(n) 失效的问题
            int useIndex = (index == 0) ? info.index : index;
            Element target;
            if (useIndex == LAST_INDEX) {
                target = elements.last();
            } else if (useIndex >= 0 && useIndex < elements.size()) {
                target = elements.get(useIndex);
            } else {
                return "";
            }

            return extractValue(target, info.mode, info.attributeName);
        } catch (Exception e) {
            SpiderDebug.log("extractByCss error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 按备用选择器提取（||分隔符）
     *
     * @param html    HTML内容
     * @param orRule  备用选择器规则（用||分隔）
     * @return 第一个非空值
     */
    public static String extractByOrSelector(String html, String orRule) {
        if (orRule == null || orRule.isEmpty()) return "";
        String[] selectors = orRule.split("\\|\\|");
        for (String selector : selectors) {
            String result = extractByCss(html, selector.trim(), 0);
            if (!result.isEmpty()) return result;
        }
        return "";
    }

    /**
     * 截取CSS规则区域
     */
    public static String cutRegion(String html, String cssRule) {
        return extractByCss(html, cssRule, 0);
    }

    /**
     * 提取元素值
     */
    private static String extractValue(Element el, ExtractMode mode, String attrName) {
        if (el == null) return "";
        try {
            switch (mode) {
                case TEXT:
                    return el.text();
                case OWN_TEXT:
                    return el.ownText();
                case HTML:
                    return el.html();
                case OUTER_HTML:
                    return el.outerHtml();
                case ATTRIBUTE:
                    if (!attrName.isEmpty()) {
                        return el.attr(attrName);
                    }
                    return "";
                default:
                    return el.text();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
