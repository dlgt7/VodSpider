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
    /** XBPQ 简写 CSS 前缀（p:a->href、p:.class->text 等） */
    public static final String P_PREFIX = "p:";

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
     * <p>支持：css: / css:// / p: 三种前缀
     *
     * @param rule 规则字符串
     * @return true如果是CSS选择器
     */
    public static boolean isCssRule(String rule) {
        if (rule == null) return false;
        return rule.startsWith(CSS_PREFIX_FULL) || rule.startsWith(CSS_PREFIX) || rule.startsWith(P_PREFIX);
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
     * 去掉CSS协议前缀（含 p: 简写前缀）
     * <p>注意：此方法仅剥离前缀，不做语法转换；完整转换请使用 {@link #parseCssShortSyntax(String)}。
     */
    public static String stripPrefix(String rule) {
        if (rule.startsWith(CSS_PREFIX_FULL)) {
            return rule.substring(CSS_PREFIX_FULL.length());
        } else if (rule.startsWith(CSS_PREFIX)) {
            return rule.substring(CSS_PREFIX.length());
        } else if (rule.startsWith(P_PREFIX)) {
            return rule.substring(P_PREFIX.length());
        }
        return rule;
    }

    /**
     * 剥去 +( 和 +) 拼接包装：+(+p:a->href+) → p:a->href
     */
    public static String stripConcatWrap(String rule) {
        if (rule == null) return "";
        String r = rule.trim();
        while (r.startsWith("+(") && r.endsWith(")+")) {
            r = r.substring(2, r.length() - 2).trim();
        }
        return r;
    }

    /**
     * 解析精简语法（支持嵌套 +(+p:xxx+) 拼接）：
     * <ul>
     *   <li>p:a->href => a[href]</li>
     *   <li>p:a->text => a:text</li>
     *   <li>p:.class->text => .class:text</li>
     *   <li>p:div[class*="x"]->text => div[class*="x"]:text</li>
     *   <li>p:ul>li>a => ul>li>a（纯选择器，无提取模式）</li>
     *   <li>p:.hl-tabs-btn => .hl-tabs-btn（纯选择器）</li>
     *   <li>+(+p:a->href+) => a[href]（剥去 +( 和 +) 包装）</li>
     *   <li>css:div->text => div:text（保留原 css: 前缀处理逻辑）</li>
     *   <li>.class->@text => .class:text（属性形式）</li>
     *   <li>.class->@href => .class[href]（属性提取形式）</li>
     * </ul>
     */
    public static String parseCssShortSyntax(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }

        // 处理 +(+xxx+) 嵌套拼接包装：剥去外层的 +( 和 +)
        if (expr.startsWith("+(") && expr.endsWith(")+")) {
            expr = expr.substring(2, expr.length() - 2).trim();
        }

        // 再次检查 p: 前缀（剥去嵌套包装后可能暴露新的 p:）
        if (expr.startsWith(P_PREFIX)) {
            return convertPShortcut(expr);
        }

        int arrowIdx = expr.indexOf("->");

        // 无 -> 时直接返回原表达式（纯选择器，如 tag:.class）
        if (arrowIdx < 0) {
            if (expr.startsWith("tag:")) {
                return expr.substring(4);
            }
            return expr;
        }

        // 有 -> 的完整精简语法
        String left = expr.substring(0, arrowIdx).trim();
        String right = expr.substring(arrowIdx + 2).trim();

        String selector = "";

        // 处理 tag:p 或 p: 前缀
        if (left.startsWith("tag:")) {
            selector = left.substring(4).trim();
        } else if (left.startsWith(P_PREFIX)) {
            selector = left.substring(2).trim();
        } else if (!left.isEmpty()) {
            // 修复：css:div->text 经 stripPrefix 后 left="div" 无前缀，
            // 原实现 selector 保持空串，选择器整体丢失（输出 ":text"）。
            // 现将 left 本身作为选择器（输出 "div@text"）。
            selector = left;
        }

        // 处理 @text/@html/@href/@src 等属性提取标记（.class->@href 形式）
        if (right.startsWith("@")) {
            String attrMarker = right.substring(1).trim();
            if ("text".equals(attrMarker) || "ownText".equals(attrMarker)) {
                return selector.isEmpty() ? "@" + attrMarker : selector + "@" + attrMarker;
            } else if ("html".equals(attrMarker) || "outerHtml".equals(attrMarker)) {
                return selector.isEmpty() ? "@" + attrMarker : selector + "@" + attrMarker;
            } else {
                // 通用属性提取
                return selector.isEmpty() ? "@" + attrMarker : selector + "@" + attrMarker;
            }
        }

        if (right.contains("(") && right.contains(")")) {
            int start = right.indexOf('(');
            int end = right.indexOf(')');
            String attrName = right.substring(start + 1, end).trim();
            return selector.isEmpty() ? "@" + attrName : selector + "@" + attrName;
        } else {
            // 修复：right 为 text/ownText/html/outerHtml 时输出 @text 等模式标记，
            // 其余（href/src/title/data-* 等）一律按属性提取输出 @attr。
            // 原实现输出 ":attr"（非法选择器）或 "[attr]"（只选中含该属性的元素、
            // 仍取 text），导致 p:a->href / p:img->data-original 等真实规则全部取空。
            return selector.isEmpty() ? "@" + right : selector + "@" + right;
        }
    }

    /**
     * 处理 p:xxx 精简语法（含嵌套 +(+p:xxx+) 展开）
     */
    private static String convertPShortcut(String expr) {
        // 递归剥去 +( ... )+ 包装
        while (expr.startsWith("+(") && expr.endsWith(")+")) {
            expr = expr.substring(2, expr.length() - 2).trim();
        }
        if (!expr.startsWith(P_PREFIX)) return expr;
        String body = expr.substring(2).trim();

        int arrowIdx = body.indexOf("->");
        if (arrowIdx < 0) {
            // p:a 或 p:.class 纯选择器
            if (body.isEmpty() || body.startsWith(".") || body.startsWith("#") || body.startsWith("[")) {
                return body;
            }
            return body;
        }

        String left = body.substring(0, arrowIdx).trim();
        String right = body.substring(arrowIdx + 2).trim();
        String selector = left.isEmpty() ? "" : left;

        if (right.contains("(") && right.contains(")")) {
            int start = right.indexOf('(');
            int end = right.indexOf(')');
            return selector.isEmpty()
                    ? "@" + right.substring(start + 1, end).trim()
                    : selector + "@" + right.substring(start + 1, end).trim();
        } else {
            // 修复：与 parseCssShortSyntax 对齐，输出 @text/@attr 形式（原 ":right" 非法）
            return selector.isEmpty() ? "@" + right : selector + "@" + right;
        }
    }

    /**
     * 解析元素索引
     * <p>修复：原实现用 {@code contains(":first")/contains(":last")} 判定，
     * 会把 jsoup 原生的 {@code :first-child}/:last-child/:last-of-type 误当成索引标记，
     * 且 cleanIndexMarkers 剥离 ":first" 后残留 "-child" 使选择器非法。现只匹配独立形态。
     */
    private static final Pattern P_LAST_PLAIN = Pattern.compile(":last(?![\\w-])");
    private static final Pattern P_FIRST_PLAIN = Pattern.compile(":first(?![\\w-])");

    private static int parseIndex(String rule) {
        // 检查 :eq(n)
        Matcher eqMatcher = P_CSS_EQ.matcher(rule);
        if (eqMatcher.find()) {
            return Integer.parseInt(eqMatcher.group(1));
        }

        // :first / :last（jsoup 不识别，需自行转索引并从选择器中清除；
        // :first-child / :last-child / :last-of-type 等 jsoup 原生伪类不受影响）
        if (P_LAST_PLAIN.matcher(rule).find()) return LAST_INDEX;
        if (P_FIRST_PLAIN.matcher(rule).find()) return 0;

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
        rule = P_LAST_PLAIN.matcher(rule).replaceAll("");
        rule = P_FIRST_PLAIN.matcher(rule).replaceAll("");
        rule = P_CSS_INDEX.matcher(rule).replaceAll("");
        return rule.trim();
    }

    /**
     * 按规则选择元素（含 "容器&&条目" 形态展开）。
     * <p>{@code .stui-vodlist&&li}：先选容器，再在各容器内选条目，结果合并返回；
     * 普通选择器直接 {@code root.select(selector)}。入参为已 stripPrefix 的原始规则，
     * 各段会再经 {@link #parseCssShortSyntax(String)} 转换（支持 p: 简写残留）。
     */
    public static Elements selectWithAnd(Element root, String rawRule) {
        if (root == null || rawRule == null || rawRule.trim().isEmpty()) return new Elements();
        int amp = rawRule.indexOf("&&");
        if (amp < 0) {
            try {
                return root.select(parseCssShortSyntax(rawRule.trim()));
            } catch (Exception e) {
                SpiderDebug.log("selectWithAnd error: " + e.getMessage());
                return new Elements();
            }
        }
        Elements out = new Elements();
        try {
            String contSel = parseCssShortSyntax(rawRule.substring(0, amp).trim());
            String itemSel = parseCssShortSyntax(rawRule.substring(amp + 2).trim());
            for (Element container : root.select(contSel)) {
                out.addAll(container.select(itemSel));
            }
        } catch (Exception e) {
            SpiderDebug.log("selectWithAnd error: " + e.getMessage());
        }
        return out;
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
        // 剥去 +( 和 +) 拼接包装（如 +(+p:a->href+) → p:a->href）
        cssRule = stripConcatWrap(cssRule);
        if (!isCssRule(cssRule)) return "";
        try {
            CssRuleInfo info = parseRule(cssRule);
            if (info == null) return "";

            // 修复：每次调用都重新Jsoup.parse()的性能问题——
            // 对于同一HTML文档的多次字段提取，调用方应使用下方的Document重载版本。
            // 此String版本保留用于向后兼容和单次调用场景。
            Document doc = Jsoup.parse(html);
            return extractFromDocument(doc, cssRule, info, index);
        } catch (Exception e) {
            SpiderDebug.log("extractByCss error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 从已解析的 Element（Document 或单个列表项元素）中提取CSS规则匹配的内容（性能优化版）。
     * <p>
     * 修复说明：原 {@code extractByCss(String, String, int)} 每次调用都重新 {@code Jsoup.parse(html)}，
     * 当同一HTML需要提取多个字段时（如列表标题+链接+图片），会产生大量重复解析开销。
     * 本方法允许调用方传入已解析的 Document 或列表项 Element，
     * 同一元素的多个字段提取都复用同一个实例，选择器作用于该元素子树。
     *
     * @param el      已解析的Jsoup Element/Document（由调用方预先创建并复用）
     * @param cssRule CSS规则
     * @param index   元素索引（-1表示最后一个）
     * @return 提取的值
     */
    public static String extractByCss(Element el, String cssRule, int index) {
        if (el == null || !isCssRule(cssRule)) return "";
        // 剥去 +( 和 +) 拼接包装（如 +(+p:a->href+) → p:a->href）
        cssRule = stripConcatWrap(cssRule);
        if (!isCssRule(cssRule)) return "";
        try {
            CssRuleInfo info = parseRule(cssRule);
            if (info == null) return "";
            return extractFromDocument(el, cssRule, info, index);
        } catch (Exception e) {
            SpiderDebug.log("extractByCss(Element) error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 核心提取逻辑：从Element中按CssRuleInfo提取值（供两个重载方法共享）
     */
    private static String extractFromDocument(Element doc, String originalRule, CssRuleInfo info, int index) {
        // 统一走 selectWithAnd：支持 ".container&&li" 容器&&条目形态（展开后合并）
        Elements elements = selectWithAnd(doc, info.selector);

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
     * 截取CSS规则区域（供 detail_array / list_twice / search_twice 等区域截取场景）。
     * <p>
     * 修复：原实现直接复用 extractByCss（TEXT 模式），区域截取返回的是纯文本，
     * 后续内层字段规则（如 href="&&"、p:a->href）在纯文本上全部取空。
     * 现按区域语义返回 outerHtml 保留 HTML 结构；仅当规则显式带 @text/@attr 标记时
     * 才按标记提取。
     */
    public static String cutRegion(String html, String cssRule) {
        if (html == null || html.isEmpty() || !isCssRule(cssRule)) return "";
        cssRule = stripConcatWrap(cssRule);
        if (!isCssRule(cssRule)) return "";
        try {
            CssRuleInfo info = parseRule(cssRule);
            if (info == null) return "";
            Document doc = Jsoup.parse(html);
            Elements elements = selectWithAnd(doc, info.selector);
            if (elements.isEmpty()) return "";

            int useIndex = info.index;
            Element target;
            if (useIndex == LAST_INDEX) {
                target = elements.last();
            } else if (useIndex >= 0 && useIndex < elements.size()) {
                target = elements.get(useIndex);
            } else {
                return "";
            }

            boolean explicitText = info.originalRule.contains("@text") || info.originalRule.contains("@ownText");
            if (explicitText) {
                return extractValue(target, info.mode, info.attributeName);
            }
            if (info.mode == ExtractMode.ATTRIBUTE && !info.attributeName.isEmpty()) {
                return target.attr(info.attributeName);
            }
            if (info.mode == ExtractMode.HTML) return target.html();
            if (info.mode == ExtractMode.OUTER_HTML) return target.outerHtml();
            // 默认区域语义：保留 HTML 结构
            return target.outerHtml();
        } catch (Exception e) {
            SpiderDebug.log("cutRegion error: " + e.getMessage());
            return "";
        }
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
