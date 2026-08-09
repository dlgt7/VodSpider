package com.github.catvod.bean.XBPQ;

import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.XBPQ;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQ 文本解析核心工具，由 XBPQ 单体拆分而来。
 *
 * <p>包含字符串截取、Jsoup 节点解析、变量插值、工具链及分页计算等纯解析逻辑。
 * 所有方法均为静态方法；需要访问 XBPQ 运行时状态（配置、缓存、调试开关等）的方法，
 * 首参为 {@code XBPQ main}，通过 {@code main.xxx} 访问对应字段。</p>
 */
public final class XBPQParse {

    private XBPQParse() {}

    // ==================== 模块专用常量 ====================

    /** 剧集编号提取正则组（按优先级排列，首匹配为准）。 */
    private static final Pattern[] EPISODE_PATTERNS = {
            // 标准：xxx-1-2.html → 取 2
            Pattern.compile("[/-]\\d{1,2}-(?:nid-)?(?:num-)?(\\d{1,4})(?:\\.html)?"),
            // 序号：xxx-1-2.html → 取 1
            Pattern.compile("[/-](\\d{1,2})-(?:nid-)?(?:num-)?\\d{1,4}(?:\\.html)?"),
            // sid 路径编号：sid/1/abc/123 → 取 123
            Pattern.compile("sid/\\d{1,2}/\\w{3}/(\\d{1,4})"),
            // sid 序号：sid/1/... → 取 1
            Pattern.compile("sid/(\\d{1,2})/")
    };

    /** 剧集编号 URL 预过滤正则（含数字编号或 sid 路径才处理）。 */
    private static final Pattern EPISODE_URL_FILTER =
            Pattern.compile("[/-]\\d+-|sid/");

    /** 截取规则中的 [不包含:xxx] 指令正则。 */
    private static final Pattern EXCLUDE_PATTERN =
            Pattern.compile("\\[不?包含[:：]([\\S\\s]*?)\\]");

    /** 截取规则中的 [包含:xxx] 指令正则。 */
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("\\[包含[:：]([\\S\\s]*?)\\]");

    /** 截取规则中的 [替换:a>>b] 指令正则。 */
    private static final Pattern REPLACE_PATTERN =
            Pattern.compile("\\[替换:([\\S\\s]*?)\\]");

    /** 截取规则中的 分割(后:xxx) 指令正则（提取后按 xxx 截断，丢弃之后内容）。 */
    private static final Pattern SPLIT_AFTER_PATTERN =
            Pattern.compile("分割\\(后:([^)]+)\\)");

    /** 截取规则中的 分割(前:xxx) 指令正则（提取后按 xxx 截断，丢弃之前内容）。 */
    private static final Pattern SPLIT_BEFORE_PATTERN =
            Pattern.compile("分割\\(前:([^)]+)\\)");

    /** 数字提取正则（页码解析用）。 */
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");

    // ==================== 解析工具方法 ====================

    /**
     * 空格归一化：将有意空格替换为独特占位符，清除所有空白字符后恢复有意空格。
     * 用于播放 URL 中混入换行/制表符等无效空白时的清洗。
     * 占位符使用控制字符组合 \u0001\u0002\u0001，避免与真实内容冲突。
     */
    public static String normalizeSpaces(String text) {
        if (text == null || text.isEmpty()) return text;
        String placeholder = "\u0001\u0002\u0001";
        text = text.replace(" ", placeholder);
        text = text.replaceAll("\\s+", "");
        text = text.replace(placeholder, " ").trim();
        return text;
    }

    /** 从 html 中提取 pre...suf 之间的内容（首个匹配）。支持转义：\\[→[、\\]→]。 */
    public static String extractBetween(String html, String pre, String suf) {
        if (html == null || pre == null || suf == null) return html == null ? "" : html;
        pre = unescapeSelector(pre);
        suf = unescapeSelector(suf);
        if (pre.isEmpty() && suf.isEmpty()) return html;
        try {
            int start = pre.isEmpty() ? 0 : html.indexOf(pre);
            if (start < 0) return "";
            start += pre.length();
            int end = suf.isEmpty() ? html.length() : html.indexOf(suf, start);
            if (end < 0) end = html.length();
            return html.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * XBPQ 选择器反转义：将转义序列还原为字面字符。
     * \\[ → [、\\] → ]、\\\\ → \\，其余 \\x 原样保留。
     */
    public static String unescapeSelector(String s) {
        if (s == null || s.isEmpty() || s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '[' || next == ']' || next == '\\') {
                    sb.append(next);
                    i++;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== 变量插值与工具链 ====================

    /**
     * {@code {{变量名}}} 插值替换：递归展开变量引用，支持 [工具:...] 后处理。
     * @param text 待处理文本（URL/选择器/配置值）
     * @return 插值后的文本
     */
    public static String interpolate(XBPQ main, String text) {
        if (text == null || text.isEmpty() || !text.contains("{{")) return text;
        return interpolate(main, text, 0);
    }

    /** 递归插值（最大深度 5 防止循环引用）。 */
    public static String interpolate(XBPQ main, String text, int depth) {
        if (depth > 5 || text == null || !text.contains("{{")) return text;
        Pattern varpattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher m = varpattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1).trim();
            String value = resolveVariable(main, varName, depth);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 解析单个变量：优先缓存 → config 读取并递归插值 → 工具链处理。 */
    public static String resolveVariable(XBPQ main, String varName, int depth) {
        if (main.varCache.containsKey(varName)) return main.varCache.get(varName);
        String raw = main.config != null ? main.config.get("", varName) : "";
        if (raw.isEmpty()) {
            main.varCache.put(varName, "");
            return "";
        }
        // 递归插值
        String interpolated = interpolate(main, raw, depth + 1);
        // 工具链处理
        String result = applyTools(main, interpolated);
        main.varCache.put(varName, result);
        return result;
    }

    /**
     * [工具:...] 后处理：对已插值的文本应用工具变换。
     * 支持内联格式 {@code https://url[工具:源码#...]} 和独立格式 {@code [工具:SHA]}。
     * 内联格式时，URL 部分作为工具输入；独立格式时，整个文本作为工具输入。
     */
    public static String applyTools(XBPQ main, String text) {
        if (text == null || !text.contains("[工具:")) return text;
        // 检查是否为 URL + [工具:...] 的内联格式
        int toolIdx = text.indexOf("[工具:");
        if (toolIdx > 0) {
            String prefix = text.substring(0, toolIdx).trim();
            String toolPart = text.substring(toolIdx);
            // 如果前缀是 URL（以 http 开头），工具作用于 URL，结果替换整个文本
            if (prefix.startsWith("http")) {
                Pattern toolPattern = Pattern.compile("\\[工具:([^\\]]+)\\]");
                Matcher m = toolPattern.matcher(toolPart);
                while (m.find()) {
                    String toolSpec = m.group(1);
                    String result = executeTool(main, prefix, toolSpec);
                    if (!result.isEmpty()) return result;
                }
                return text; // 工具执行失败，返回原文
            }
        }
        // 独立格式：[工具:...] 作用于整个文本
        Pattern toolPattern = Pattern.compile("\\[工具:([^\\]]+)\\]");
        Matcher m = toolPattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String toolSpec = m.group(1);
            String replacement = executeTool(main, text, toolSpec);
            if (!replacement.isEmpty()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        m.appendTail(sb);
        String result = sb.toString();
        return result.isEmpty() ? text : result;
    }

    /**
     * 执行单个工具指令。
     * @param fullText 工具前的完整文本（作为工具输入）
     * @param toolSpec 工具规格（如 "源码#prefix*suffix截取"）
     * @return 工具输出
     */
    public static String executeTool(XBPQ main, String fullText, String toolSpec) {
        try {
            if (toolSpec.startsWith("源码")) {
                // [工具:源码#prefix*suffix截取] 或 [工具:源码#prefix*suffix截取#...]
                return toolFetchSource(main, fullText, toolSpec);
            } else if (toolSpec.equals("SHA")) {
                return toolSHA(fullText);
            } else if (toolSpec.startsWith("随机字符")) {
                return toolRandomString(toolSpec);
            } else if (toolSpec.matches("\\d+截取\\d+")) {
                return toolSubstring(fullText, toolSpec);
            } else if (toolSpec.startsWith("解b64")) {
                return toolBase64Decode(main, fullText, toolSpec);
            } else if (toolSpec.startsWith("解密aes") || toolSpec.startsWith("源码转b64")) {
                return toolAesDecrypt(main, fullText, toolSpec);
            } else if (toolSpec.equals("解url") || toolSpec.startsWith("解url")) {
                return toolUrlDecode(fullText, toolSpec);
            }
        } catch (Exception e) {
            if (main.debug && main.spiderApi != null) main.spiderApi.log("工具[" + toolSpec + "]执行失败：" + e.getMessage());
        }
        return "";
    }

    /** [工具:解url] - URL 解码。 */
    public static String toolUrlDecode(String text, String toolSpec) {
        try {
            return URLDecoder.decode(text.trim(), "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }

    /** [工具:源码#prefix*suffix截取] - 获取 URL 源码并截取。支持 ∬ 多段尝试和逗号分隔多步。 */
    public static String toolFetchSource(XBPQ main, String url, String toolSpec) {
        String params = toolSpec.substring("源码".length());
        if (params.startsWith("#")) params = params.substring(1);
        // ∬ 分隔多个提取尝试，首个成功即返回
        String[] alternatives = params.split("∬");
        for (String alt : alternatives) {
            String result = fetchAndExtractSource(main, url, alt);
            if (!result.isEmpty()) return result;
        }
        return "";
    }

    /** 单段源码提取：按逗号分隔多步截取。 */
    public static String fetchAndExtractSource(XBPQ main, String url, String stepsStr) {
        String content = XBPQHttp.fetchHtml(main, url.trim());
        if (content == null || content.isEmpty()) return "";
        // 逗号分隔多步截取
        String[] steps = stepsStr.split(",");
        for (String step : steps) {
            if (step.isEmpty()) continue;
            if (step.contains("*") && step.contains("截取")) {
                // prefix*suffix截取 → extractBetween(content, prefix, suffix)
                String[] parts = step.split("\\*");
                String pre = parts[0];
                // 截取标记后移除，取首个 suf
                String suf = parts.length > 1 ? parts[1].replace("截取", "") : "";
                content = extractBetween(content, pre, suf);
                if (content.isEmpty()) return "";
            }
        }
        return content;
    }

    /** [工具:SHA] - SHA-1 哈希。 */
    public static String toolSHA(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** [工具:随机字符-N-唯一] - 生成 N 位随机字符串。 */
    public static String toolRandomString(String toolSpec) {
        String[] parts = toolSpec.split("-");
        int len = parts.length > 1 ? Integer.parseInt(parts[1]) : 6;
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /** [工具:N截取M] - 从第 N 位开始截取 M 位。 */
    public static String toolSubstring(String text, String toolSpec) {
        Matcher m = Pattern.compile("(\\d+)截取(\\d+)").matcher(toolSpec);
        if (m.find()) {
            int start = Integer.parseInt(m.group(1));
            int len = Integer.parseInt(m.group(2));
            if (start < text.length()) {
                int end = Math.min(start + len, text.length());
                return text.substring(start, end);
            }
        }
        return text;
    }

    /** [工具:解b64#...] - Base64 解码。 */
    public static String toolBase64Decode(XBPQ main, String text, String toolSpec) {
        try {
            byte[] decoded = Base64.decode(text.trim(), Base64.DEFAULT);
            String result = new String(decoded, StandardCharsets.UTF_8);
            // 后续步骤（如 words.random()*'.截取'）
            String[] steps = toolSpec.split("#");
            for (int i = 1; i < steps.length; i++) {
                String step = steps[i];
                if (step.contains("*") && step.contains("截取")) {
                    String[] parts = step.split("\\*");
                    result = extractBetween(result, parts[0], parts.length > 1 ? parts[1].replace("截取", "") : "");
                }
            }
            return result;
        } catch (Exception e) {
            return "";
        }
    }

    /** [工具:解密aes-key-iv-mode] / [工具:源码转b64#解密aes-...] - AES 解密。 */
    public static String toolAesDecrypt(XBPQ main, String text, String toolSpec) {
        try {
            String input = text;
            // 源码转b64：先提取源码中的 base64，再解密
            if (toolSpec.startsWith("源码转b64")) {
                String[] steps = toolSpec.split("#");
                if (steps.length >= 2) {
                    input = toolBase64Decode(main, input, steps[0]);
                    toolSpec = steps[1];
                }
            }
            if (toolSpec.startsWith("解密aes-")) {
                String[] parts = toolSpec.split("-");
                if (parts.length >= 4) {
                    String key = parts[1];
                    String iv = parts[2];
                    String mode = parts[3];
                    return XBPQCrypto.aesDecrypt(input.trim(), key, iv, mode);
                }
            }
        } catch (Exception e) {
            if (main.debug && main.spiderApi != null) main.spiderApi.log("AES解密失败：" + e.getMessage());
        }
        return text;
    }

    /**
     * || 多段选择器解析：按当前分类名选择对应段。
     * 格式：{@code defaultSelector||group1--selector1||group2,group3--selector2}
     * 无 -- 前缀的段为默认选择器；有 -- 前缀的段按组名匹配分类名。
     */
    public static String resolveMultiSection(XBPQ main, String selector, String tid) {
        if (selector == null || !selector.contains("||")) return selector;
        String currentName = main.tidToName.get(tid);
        if (currentName == null) currentName = tid;
        String defaultSel = selector;
        for (String section : selector.split("\\|\\|")) {
            int dashIdx = section.indexOf("--");
            if (dashIdx > 0) {
                String groupName = section.substring(0, dashIdx);
                String sectionSelector = section.substring(dashIdx + 2);
                // 组名可逗号分隔，匹配分类名或 tid
                for (String name : groupName.split(",")) {
                    String trimmed = name.trim();
                    if (trimmed.equals(currentName) || trimmed.equals(tid)) {
                        return sectionSelector;
                    }
                }
            } else if (defaultSel.equals(selector)) {
                defaultSel = section;
            }
        }
        return defaultSel;
    }

    /** 从 html 中提取所有 pre...suf 之间的内容块。 */
    public static List<String> extractAll(XBPQ main, String html, String pre, String suf) {
        List<String> result = new ArrayList<>();
        if (html == null || html.isEmpty()) return result;
        if (pre == null || pre.isEmpty()) pre = "<a";
        if (suf == null || suf.isEmpty()) suf = "</a>";
        // 反转义选择器（\[→[、\]→]）
        pre = unescapeSelector(pre);
        suf = unescapeSelector(suf);
        // 处理 [不包含:xxx] 指令
        String excludeList = "";
        Matcher excludeMatcher = EXCLUDE_PATTERN.matcher(pre);
        if (excludeMatcher.find()) {
            excludeList = excludeMatcher.group(1);
            pre = pre.replaceAll("\\[不?包含.*?\\]", "");
            suf = suf.replaceAll("\\[不?包含.*?\\]", "");
        }
        // 处理 [包含:xxx] 指令
        String includeList = "";
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(pre);
        if (includeMatcher.find()) {
            includeList = includeMatcher.group(1);
            pre = pre.replaceAll("\\[包含.*?\\]", "");
            suf = suf.replaceAll("\\[包含.*?\\]", "");
        }
        // 处理 分割(后:xxx) 指令：提取后按 xxx 截断，保留之前的内容
        String splitAfter = "";
        Matcher splitAfterMatcher = SPLIT_AFTER_PATTERN.matcher(suf);
        if (splitAfterMatcher.find()) {
            splitAfter = splitAfterMatcher.group(1);
            suf = suf.replaceAll("分割\\(后:[^)]+\\)", "");
        }
        // 处理 分割(前:xxx) 指令：提取后按 xxx 截断，保留之后的内容
        String splitBefore = "";
        Matcher splitBeforeMatcher = SPLIT_BEFORE_PATTERN.matcher(suf);
        if (splitBeforeMatcher.find()) {
            splitBefore = splitBeforeMatcher.group(1);
            suf = suf.replaceAll("分割\\(前:[^)]+\\)", "");
        }
        try {
            int searchPos = 0;
            while (searchPos < html.length()) {
                int start = html.indexOf(pre, searchPos);
                if (start < 0) break;
                int end = html.indexOf(suf, start + pre.length());
                if (end < 0) break;
                String block = html.substring(start, end + suf.length());
                boolean keep = true;
                if (!excludeList.isEmpty()) {
                    for (String exclude : excludeList.split("#")) {
                        if (!exclude.isEmpty() && block.contains(exclude)) {
                            keep = false;
                            break;
                        }
                    }
                }
                if (keep && !includeList.isEmpty()) {
                    boolean hasInclude = false;
                    for (String include : includeList.split("#")) {
                        if (!include.isEmpty() && block.contains(include)) {
                            hasInclude = true;
                            break;
                        }
                    }
                    keep = hasInclude;
                }
                if (keep) {
                    // 应用 分割(后:xxx)：截断 xxx 之后的内容
                    if (!splitAfter.isEmpty()) {
                        int splitIdx = block.indexOf(splitAfter);
                        if (splitIdx >= 0) {
                            block = block.substring(0, splitIdx + splitAfter.length());
                        }
                    }
                    // 应用 分割(前:xxx)：截断 xxx 之前的内容
                    if (!splitBefore.isEmpty()) {
                        int splitIdx = block.indexOf(splitBefore);
                        if (splitIdx >= 0) {
                            block = block.substring(splitIdx);
                        }
                    }
                    result.add(block);
                }
                searchPos = end + suf.length();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        // Jsoup 回退：字符串截取未命中且 jsoupMode 开启时，尝试 CSS 选择器
        if (result.isEmpty() && main.jsoupMode && !pre.contains("&&") && !suf.contains("&&")) {
            String cssSelector = pre;
            // 兼容 pre&&suf 格式传入的场景：如果 pre 含特殊字符则跳过
            if (!cssSelector.matches(".*[<>\"'=].*")) {
                return extractAllWithJsoup(main, html, cssSelector);
            }
        }
        return result;
    }

    /**
     * Jsoup CSS 选择器批量提取：返回所有匹配元素的 outerHtml。
     * @param html 源 HTML
     * @param cssSelector CSS 选择器
     * @return 匹配元素的 outerHtml 列表，失败返回空列表
     */
    public static List<String> extractAllWithJsoup(XBPQ main, String html, String cssSelector) {
        List<String> result = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(cssSelector);
            for (Element el : elements) {
                result.add(el.outerHtml());
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("Jsoup 列表提取：" + result.size() + " 项（选择器[" + cssSelector + "]）");
            }
        } catch (Exception e) {
            if (main.debug && main.spiderApi != null) main.spiderApi.log("Jsoup 列表选择器未命中：" + cssSelector);
        }
        return result;
    }

    /**
     * 从文本块中按选择器提取值。
     * 选择器格式：{@code pre&&suf}（取 pre 后 suf 前的内容）。
     * 支持 "+" 拼接语法：{@code 前缀+pre&&suf+后缀}，含 "&&" 的段为选择器，其余为字面量。
     */
    public static String pick(XBPQ main, String text, String selector) {
        if (text == null || selector == null || selector.isEmpty()) return "";
        // "+" 拼接：含 "&&" 的段为子选择器，其余为字面量前缀/后缀
        if (selector.contains("+") && selector.contains("&&")) {
            StringBuilder result = new StringBuilder();
            for (String part : selector.split("\\+")) {
                if (part.isEmpty()) continue;
                if (part.contains("&&")) {
                    result.append(pickSingle(main, text, part));
                } else {
                    result.append(part);
                }
            }
            return result.toString().trim();
        }
        return pickSingle(main, text, selector);
    }

    /** 单段选择器提取（不含 "+" 拼接）。 */
    public static String pickSingle(XBPQ main, String text, String selector) {
        if (text == null || selector == null || selector.isEmpty()) return "";
        String[] parts = selector.split("&&");
        String pre = parts.length >= 1 ? parts[0] : "";
        String suf = parts.length >= 2 ? parts[1] : "";
        // 处理 [替换:xxx>>yyy]
        String replaceRule = "";
        Matcher replaceMatcher = REPLACE_PATTERN.matcher(selector);
        if (replaceMatcher.find()) {
            replaceRule = replaceMatcher.group(1);
            pre = pre.replaceAll("\\[替换.*?\\]", "");
            suf = suf.replaceAll("\\[替换.*?\\]", "");
        }
        String value = extractBetween(text, pre, suf);
        // Jsoup 回退：字符串截取未命中且 jsoupMode 开启时，尝试 CSS 选择器
        if (value.isEmpty() && main.jsoupMode && !selector.contains("&&")) {
            value = pickWithJsoup(main, text, selector);
        }
        if (!replaceRule.isEmpty() && replaceRule.contains(">>")) {
            String[] rp = replaceRule.split(">>");
            if (rp.length >= 2) value = value.replace(rp[0], rp[1]);
        }
        return value.trim();
    }

    /**
     * Jsoup CSS 选择器提取：支持 "cssSelector" 或 "cssSelector@attr" 格式。
     * @param html 源 HTML
     * @param selector CSS 选择器，可用 @attr 指定属性提取（如 "img@src"）
     * @return 提取到的文本或属性值，失败返回空串
     */
    public static String pickWithJsoup(XBPQ main, String html, String selector) {
        try {
            String cssSelector = selector;
            String attr = "";
            // 支持 "selector@attr" 语法提取属性
            int atIdx = selector.lastIndexOf('@');
            if (atIdx > 0) {
                cssSelector = selector.substring(0, atIdx);
                attr = selector.substring(atIdx + 1);
            }
            Document doc = Jsoup.parse(html);
            Element el = doc.selectFirst(cssSelector);
            if (el == null) return "";
            if (attr.isEmpty()) {
                return el.text();
            } else {
                return el.attr(attr);
            }
        } catch (Exception e) {
            if (main.debug && main.spiderApi != null) main.spiderApi.log("Jsoup 选择器未命中：" + selector);
            return "";
        }
    }

    /**
     * Jsoup CSS 选择器取节点 outerHtml（保留 HTML 结构供后续 parsePlayFrom/parsePlayUrl 解析）。
     * @param html 源 HTML
     * @param cssSelector CSS 选择器（如 "div.play-list"、"#playlist"）
     * @return 首个匹配元素的 outerHtml，未命中返回空串
     */
    public static String selectNodeOuterHtml(XBPQ main, String html, String cssSelector) {
        if (html == null || cssSelector == null || cssSelector.isEmpty()) return "";
        try {
            Document doc = Jsoup.parse(html);
            Element el = doc.selectFirst(cssSelector);
            if (el == null) return "";
            return el.outerHtml();
        } catch (Exception e) {
            if (main.debug && main.spiderApi != null) main.spiderApi.log("Jsoup 节点选择器未命中：" + cssSelector);
            return "";
        }
    }

    /**
     * 二级截取：按配置优先级选段，缩小 HTML 范围到播放区域。
     * <ol>
     *   <li>CSS 选择器（{@code 二级截取选择器}）：Jsoup 精确选段，最稳</li>
     *   <li>前后字符串截取（{@code 二级截取前}/{@code 二级截取后}）：定位"播放区域"前后字符串</li>
     *   <li>回退：按 {@code <ul/<div} 标签分段 + 数字下标（{@code 二级截取起始}/{@code 二级截取末位}）</li>
     * </ol>
     * @param html 原始详情页 HTML
     * @return 截取后的 HTML，未命中返回空串（调用方保留原文）
     */
    public static String secondaryCutHtml(XBPQ main, String html) {
        if (html == null || html.isEmpty()) return "";
        // 1. CSS 选择器优先
        if (!main.secondaryCutSelector.isEmpty()) {
            String cut = selectNodeOuterHtml(main, html, main.secondaryCutSelector);
            if (!cut.isEmpty()) {
                if (main.debug && main.spiderApi != null) {
                    main.spiderApi.log("二级截取[CSS选择器]命中：" + main.secondaryCutSelector + " → " + cut.length() + " 字符");
                }
                return cut;
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("二级截取[CSS选择器]未命中，回退前后截取/数字下标");
            }
        }
        // 2. 前后字符串截取
        if (!main.secondaryCutPre.isEmpty() || !main.secondaryCutSuf.isEmpty()) {
            String cut = extractBetween(html, main.secondaryCutPre, main.secondaryCutSuf);
            if (!cut.isEmpty()) {
                if (main.debug && main.spiderApi != null) {
                    main.spiderApi.log("二级截取[前后截取]命中 → " + cut.length() + " 字符");
                }
                return cut;
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("二级截取[前后截取]未命中，回退数字下标");
            }
        }
        // 3. 回退：按 <ul/<div 分段 + 数字下标
        String[] sections = html.split("(?i)(?=<ul|<div)");
        if (sections.length > main.secondaryCutEnd) {
            StringBuilder cutHtml = new StringBuilder();
            for (int i = main.secondaryCutStart; i <= main.secondaryCutEnd && i < sections.length; i++) {
                cutHtml.append(sections[i]);
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("二级截取[数字下标]命中：" + sections.length + " 段，取[" + main.secondaryCutStart + "-" + main.secondaryCutEnd + "]");
            }
            return cutHtml.toString();
        }
        return "";
    }

    /** 提取详情字段（多键名选择器查找）。 */
    public static String extractField(XBPQ main, String html, String... keys) {
        if (main.config == null) return "";
        String sel = main.config.get("", keys);
        if (sel.isEmpty()) return "";
        return pick(main, html, sel);
    }

    /** 解析播放线路名。 */
    public static String parsePlayFrom(XBPQ main, String html) {
        try {
            String tabArr = main.config.get("线路数组", "xljiequshuzuqian", "tab_arr_pre", "");
            String tabTitle = main.config.get("线路标题", "xlbiaotiqian", "tab_title", "");
            // 线路二次截取：支持组合格式 "pre&&suf"（单键）和分离格式
            String tabTwiceRaw = main.config.get("", "线路二次截取", "xljiequqian", "tab_twice_pre");
            String tabTwicePre, tabTwiceSuf;
            if (tabTwiceRaw.contains("&&")) {
                String[] tabParts = tabTwiceRaw.split("&&");
                tabTwicePre = tabParts[0];
                tabTwiceSuf = tabParts.length > 1 ? tabParts[1] : "";
            } else {
                tabTwicePre = tabTwiceRaw;
                tabTwiceSuf = main.config.get("", "线路二次截取后", "tab_twice_suf");
            }
            if (!tabTwicePre.isEmpty() && !tabTwiceSuf.isEmpty()) {
                html = extractBetween(html, tabTwicePre, tabTwiceSuf);
            }
            if (tabArr.isEmpty()) {
                // 26 个默认 HTML 模板自动探测
                String[] defaultTemplates = {
                    "<ul*tab-title&&</ul>",
                    "<ul class=\"nav nav-btn&&</ul>",
                    "\"playname\"&&</ul>",
                    "\"from*list\"&&</ul>",
                    "<dt&&</dt>",
                    "play_source_tab&&</div>",
                    "module-tab-item&&</small>",
                    "module-tab-item&&</div>",
                    "module-tab-item &&</a>",
                    "tabindex=*\"tab\">&&<",
                    "\"tab\"*>&&<",
                    "\"hl-text-site\">&&<",
                    "playfrom*>&&</div>",
                    "channelname*>&&</a>",
                    "tabs-play*>&&</span>",
                    "swiper-slide*>&&</a>",
                    "=\"pull-left\"*>&&<",
                    "pull-right\">&&</div>",
                    "pay-url*>&&</a>",
                    "<h3*>&&</h3>",
                    "<h4*>&&</h4>",
                    "<h2*>&&</h2>",
                    "换线路&&</ul>",
                    "选择播放源&&</ul>",
                    "节点列表&&</ul>",
                    "\"tab\"*>&&<[不包含:同]"
                };
                for (String template : defaultTemplates) {
                    String[] parts = template.split("&&");
                    if (parts.length >= 2) {
                        List<String> tabs = extractAll(main, html, parts[0], parts[1]);
                        if (!tabs.isEmpty()) {
                            List<String> names = new ArrayList<>();
                            for (String tab : tabs) {
                                String name = pick(main, tab, ">&&<");
                                name = name.replaceAll("<[^>]*>", "").trim();
                                if (!name.isEmpty() && !names.contains(name)) names.add(name);
                            }
                            if (!names.isEmpty()) {
                                return TextUtils.join("$$$", names);
                            }
                        }
                    }
                }
                return "默认线路";
            }
            if (main.debug && main.spiderApi != null) main.spiderApi.log("线路数组使用配置选择器[" + tabArr + "]");
            // 线路数组：支持组合格式 "pre&&suf"
            String tabArrPre, tabArrSuf;
            if (tabArr.contains("&&")) {
                String[] tabArrParts = tabArr.split("&&");
                tabArrPre = tabArrParts[0];
                tabArrSuf = tabArrParts.length > 1 ? tabArrParts[1] : "</";
            } else {
                tabArrPre = tabArr;
                tabArrSuf = "</";
            }
            List<String> tabs = extractAll(main, html, tabArrPre, tabArrSuf);
            List<String> names = new ArrayList<>();
            for (String tab : tabs) {
                String name = pick(main, tab, tabTitle.isEmpty() ? ">&&<" : tabTitle);
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
            return names.isEmpty() ? "默认线路" : TextUtils.join("$$$", names);
        } catch (Exception e) {
            return "默认线路";
        }
    }

    /** 解析播放 URL 列表。 */
    public static String parsePlayUrl(XBPQ main, String html, String tid) {
        try {
            String listArr = main.config.get("播放数组", "bfjiequshuzuqian", "list_arr_pre", "");
            String epiTitle = main.config.get("播放标题", "bfbiaotiqian", "epi_title", ">&&</a>");
            String epiUrl = main.config.get("播放链接", "bflianjieqian", "epi_url", "href=\"&&\"");
            String epiPre = main.config.get("播放链接前缀", "bfqianzhui", "epiurl_prefix", "");
            String epiSuf = main.config.get("播放链接后缀", "bfhouzhui", "epiurl_suffix", "");

            // 播放二次截取：支持组合格式 "pre&&suf"（单键）和分离格式
            String playTwiceRaw = main.config.get("", "播放二次截取", "bfjiequqian", "list_twice_pre");
            String playTwicePre, playTwiceSuf;
            if (playTwiceRaw.contains("&&")) {
                String[] playParts = playTwiceRaw.split("&&");
                playTwicePre = playParts[0];
                playTwiceSuf = playParts.length > 1 ? playParts[1] : "";
            } else {
                playTwicePre = playTwiceRaw;
                playTwiceSuf = main.config.get("", "播放二次截取后", "list_twice_suf");
            }
            if (!playTwicePre.isEmpty() && !playTwiceSuf.isEmpty()) {
                html = extractBetween(html, playTwicePre, playTwiceSuf);
            }

            if (listArr.isEmpty()) return "";
            // 播放数组：支持组合格式 "pre&&suf"
            String listArrPre, listArrSuf;
            if (listArr.contains("&&")) {
                String[] listArrParts = listArr.split("&&");
                listArrPre = listArrParts[0];
                listArrSuf = listArrParts.length > 1 ? listArrParts[1] : "</";
            } else {
                listArrPre = listArr;
                listArrSuf = "</";
            }
            List<String> items = extractAll(main, html, listArrPre, listArrSuf);
            if (items.isEmpty() && main.spiderApi != null) {
                main.spiderApi.log("播放列表为空：播放数组[" + listArr + "] 未匹配到内容");
            } else if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("播放列表命中：" + items.size() + " 集（数组[" + listArr + "]）");
            }
            List<String> episodes = new ArrayList<>();
            int epIndex = 1;
            for (String item : items) {
                String title = pick(main, item, epiTitle);
                String url = pick(main, item, epiUrl);
                if (title.isEmpty() && url.isEmpty()) continue;
                if (!epiPre.isEmpty() && !url.startsWith("http")) url = epiPre + url;
                if (!epiSuf.isEmpty()) url = url + epiSuf;
                // 剧集编号对齐：标题无数字时从 URL 提取编号
                if (title.isEmpty() || !title.matches(".*\\d+.*")) {
                    String epNum = extractEpisodeNumber(url);
                    if (!epNum.isEmpty()) {
                        title = "第" + epNum + "集";
                    } else if (title.isEmpty()) {
                        title = "第" + epIndex + "集";
                    }
                }
                episodes.add(title + "$" + url);
                epIndex++;
            }
            return TextUtils.join("#", episodes);
        } catch (Exception e) {
            return "";
        }
    }

    /** 默认剧集提取（无配置时回退提取 a 标签）。 */
    public static String parseDefaultEpisodes(XBPQ main, String html, String tid) {
        try {
            List<String> items = extractAll(main, html, "<a", "</a>");
            List<String> episodes = new ArrayList<>();
            for (String item : items) {
                String title = pick(main, item, ">&&<");
                String url = pick(main, item, "href=\"&&\"");
                if (title.isEmpty() || url.isEmpty()) continue;
                if (!url.startsWith("http") && !url.startsWith("/")) continue;
                episodes.add(title + "$" + url);
            }
            return TextUtils.join("#", episodes);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从 URL 中提取剧集编号。
     *
     * <p>预过滤后按优先级依次尝试 4 组预编译正则（{@link #EPISODE_PATTERNS}），首匹配即返回：</p>
     * <ol>
     *   <li>{@code [/-]\d{1,2}-(?:nid-)?(?:num-)?(\d{1,4})(?:\.html)?} — 标准编号（取尾数）</li>
     *   <li>{@code [/-](\d{1,2})-(?:nid-)?(?:num-)?\d{1,4}(?:\.html)?} — 序号（取首数）</li>
     *   <li>{@code sid/\d{1,2}/\w{3}/(\d{1,4})} — sid 路径编号</li>
     *   <li>{@code sid/(\d{1,2})/} — sid 序号</li>
     * </ol>
     *
     * @param url 播放链接 URL
     * @return 剧集编号字符串，未匹配返回 ""
     */
    public static String extractEpisodeNumber(String url) {
        if (url == null || url.isEmpty()) return "";
        // 预过滤：仅处理含 [/-]数字- 或 sid/ 路径的 URL
        if (!EPISODE_URL_FILTER.matcher(url).find()) return "";
        // 按优先级尝试 4 组预编译正则，首匹配即返回
        for (Pattern p : EPISODE_PATTERNS) {
            try {
                Matcher episodeMatcher = p.matcher(url);
                if (episodeMatcher.find()) return episodeMatcher.group(1);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    /** 倒序剧集（按 # 分隔的剧集列表反转，支持 $$$ 多线路）。 */
    public static String reverseEpisodesInUrl(String playUrl) {
        try {
            String[] lines = playUrl.split("\\$\\$\\$");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) result.append("$$$");
                String[] episodes = lines[i].split("#");
                List<String> list = Arrays.asList(episodes);
                Collections.reverse(list);
                result.append(TextUtils.join("#", list));
            }
            return result.toString();
        } catch (Exception e) {
            return playUrl;
        }
    }

    /**
     * 解析页码总数：从 HTML 中提取总页数。
     * 依次查找"总页数"、"pagecount"、"总页"等关键词后的数字，取最大值。
     * 只取关键词后 50 字符内的首个数字，避免误匹配远处的无关数字（如年份）。
     */
    public static int parsePageCount(String html) {
        try {
            // 聚合多种关键词后 50 字符内的数字文本
            String numText = extractNumNear(html, "总页数")
                    + extractNumNear(html, "pagecount")
                    + extractNumNear(html, "总页")
                    + extractNumNear(html, "totalPage")
                    + extractNumNear(html, "totalpage")
                    + extractNumNear(html, "pages");
            Matcher numMatcher = DIGIT_PATTERN.matcher(numText);
            int max = 1;
            while (numMatcher.find()) {
                int pageNum = Integer.parseInt(numMatcher.group(1));
                // 合理范围校验：页数应在 1~99999 之间
                if (pageNum > 0 && pageNum < 100000 && pageNum > max) max = pageNum;
            }
            return max;
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * 解析记录总数：从 HTML 中提取总条目数。
     * 查找"总条数"、"总数"、"total"、"count"等关键词后的数字，取最大值。
     * 只取关键词后 50 字符内的首个数字，避免误匹配。
     * @return 总数，未找到返回 -1
     */
    public static int parseTotalCount(String html) {
        try {
            String numText = extractNumNear(html, "总条数")
                    + extractNumNear(html, "总数")
                    + extractNumNear(html, "total")
                    + extractNumNear(html, "count")
                    + extractNumNear(html, "recordcount");
            Matcher numMatcher = DIGIT_PATTERN.matcher(numText);
            int max = -1;
            while (numMatcher.find()) {
                int totalCount = Integer.parseInt(numMatcher.group(1));
                // 合理范围校验：总数应在 0~9999999 之间
                if (totalCount >= 0 && totalCount < 10000000 && totalCount > max) max = totalCount;
            }
            return max;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 提取关键词后 50 字符内的数字文本（避免误匹配远处的无关数字）。
     * @param html 源 HTML
     * @param keyword 关键词
     * @return 关键词后 50 字符内的文本片段
     */
    public static String extractNumNear(String html, String keyword) {
        int idx = html.indexOf(keyword);
        if (idx < 0) return "";
        int end = Math.min(html.length(), idx + keyword.length() + 50);
        return html.substring(idx, end);
    }

    /** 解析 Vod 列表（从 fetchCategory 结果）。 */
    public static List<Vod> parseVodList(JSONObject result) {
        List<Vod> list = new ArrayList<>();
        if (result == null) return list;
        JSONArray vodArray = result.optJSONArray("list");
        if (vodArray == null) return list;
        for (int i = 0; i < vodArray.length(); i++) {
            try {
                list.add(Vod.objectFrom(vodArray.getJSONObject(i).toString()));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    /**
     * 构建筛选器。
     * 支持 XBPQ 标准格式：
     *   - "类型" / "类型值"：用 & 分隔，|| 隔离多分类组（取第一组）
     *   - "地区" / "地区值"、"年份" / "年份值"、"排序" / "排序值" 同理
     *   - 值为 "*" 表示 name 自身作为 value
     *   - 也兼容 name$value 直接写在主键里的写法（用 # 分隔）
     */
    public static LinkedHashMap<String, List<Filter>> buildFilters(XBPQ main) {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        if (main.config == null) return filters;
        // 先收集所有筛选器到一个列表
        List<Filter> allFilters = new ArrayList<>();
        collectFilter(main, allFilters, "class", "类型", "类型值", "筛选");
        collectFilter(main, allFilters, "area", "地区", "地区值");
        collectFilter(main, allFilters, "year", "年份", "年份值");
        collectFilter(main, allFilters, "by", "排序", "排序值");
        collectFilter(main, allFilters, "letter", "字母", "字母值");
        collectFilter(main, allFilters, "lang", "语言", "语言值");
        if (allFilters.isEmpty()) return filters;
        // TVBox filters 格式：按 type_id 分组，每个 type_id 下放完整筛选器列表
        // XBPQ 筛选器是全局的，对所有分类生效
        if (main.categoryList != null) {
            for (String item : main.categoryList) {
                String[] parts = item.split("\\$");
                String tid = parts.length >= 2 ? parts[1].trim() : item.trim();
                filters.put(tid, new ArrayList<>(allFilters));
            }
        }
        // 确保 "1"（全部）也有筛选器
        if (!filters.containsKey("1")) {
            filters.put("1", new ArrayList<>(allFilters));
        }
        return filters;
    }

    /**
     * 收集筛选器到列表（不按 key 分组）。
     */
    private static void collectFilter(XBPQ main, List<Filter> filters,
                                      String filterKey, String nameKey, String valueKey, String... extraNameKeys) {
        String[] allNameKeys = new String[1 + (extraNameKeys != null ? extraNameKeys.length : 0)];
        allNameKeys[0] = nameKey;
        if (extraNameKeys != null && extraNameKeys.length > 0) {
            System.arraycopy(extraNameKeys, 0, allNameKeys, 1, extraNameKeys.length);
        }
        String nameStr = main.config.get("", allNameKeys);
        if (nameStr.isEmpty()) return;
        String[] nameGroups = nameStr.split("\\|\\|");
        String firstNameGroup = nameGroups[0];
        if ("空".equals(firstNameGroup.trim())) return;
        String[] names = firstNameGroup.split("&");
        String[] values = null;
        if (valueKey != null && !valueKey.isEmpty()) {
            String valueStr = main.config.get("", valueKey);
            if (!valueStr.isEmpty()) {
                String[] valueGroups = valueStr.split("\\|\\|");
                String firstValueGroup = valueGroups[0];
                values = firstValueGroup.split("&");
            }
        }
        List<Filter.Value> list = new ArrayList<>();
        list.add(new Filter.Value("全部", ""));
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim();
            if (name.isEmpty()) continue;
            String value;
            if (values != null && i < values.length && !"*".equals(values[i])) {
                value = values[i];
            } else {
                value = name;
            }
            list.add(new Filter.Value(name, value));
        }
        if (list.size() > 1) {
            filters.add(new Filter(filterKey, nameKey, list));
        }
    }

    /**
     * 通用筛选器构建：从 nameKey/valueKey 读取配置，组装 Filter。
     * @param filterKey 筛选器标识（class/area/year/by/letter/lang）
     * @param nameKey 名称配置键（如"类型"）
     * @param valueKey 值配置键（如"类型值"），可为空
     */
    public static void addFilter(XBPQ main, LinkedHashMap<String, List<Filter>> filters,
                           String filterKey, String nameKey, String valueKey, String... extraNameKeys) {
        // 合并 nameKey 和 extraNameKeys 为单一数组，适配 config.get(String, String...) 签名
        String[] allNameKeys = new String[1 + (extraNameKeys != null ? extraNameKeys.length : 0)];
        allNameKeys[0] = nameKey;
        if (extraNameKeys != null && extraNameKeys.length > 0) {
            System.arraycopy(extraNameKeys, 0, allNameKeys, 1, extraNameKeys.length);
        }
        String nameStr = main.config.get("", allNameKeys);
        if (nameStr.isEmpty()) return;
        // || 多分类组：取第一组（tvbox 筛选为全局，无法按 cateId 切换）
        String[] nameGroups = nameStr.split("\\|\\|");
        String firstNameGroup = nameGroups[0];
        if ("空".equals(firstNameGroup.trim())) return;
        String[] names = firstNameGroup.split("&");
        // 值：优先读 valueKey，"*" 表示 name 自身作为值
        String[] values = null;
        if (valueKey != null && !valueKey.isEmpty()) {
            String valueStr = main.config.get("", valueKey);
            if (!valueStr.isEmpty()) {
                String[] valueGroups = valueStr.split("\\|\\|");
                String firstValueGroup = valueGroups[0];
                values = firstValueGroup.split("&");
            }
        }
        List<Filter.Value> list = new ArrayList<>();
        list.add(new Filter.Value("全部", ""));
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim();
            if (name.isEmpty()) continue;
            String value;
            if (values != null && i < values.length && !"*".equals(values[i])) {
                value = values[i];
            } else {
                value = name; // "*" 或无值配置：name 自身作为值
            }
            list.add(new Filter.Value(name, value));
        }
        if (list.size() > 1) {
            filters.put(filterKey, Arrays.asList(new Filter(filterKey, nameKey, list)));
        }
    }

    /** 判断是否为直链（基于媒体扩展名）。 */
    public static boolean isDirectLink(String url) {
        if (url == null || url.isEmpty()) return false;
        String[] mediaExts = {".m3u8", ".mp4", ".flv", ".mkv", ".avi", ".mov", ".mp3", ".m4a", ".ts"};
        String lower = url.toLowerCase();
        for (String ext : mediaExts) {
            if (lower.contains(ext)) return true;
        }
        return false;
    }

    /** 安全整数解析。 */
    public static int parseInt(String value, int def) {
        try {
            return value == null || value.isEmpty() ? def : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 安全读取 HashMap 值，null 返回空串。 */
    public static String safeGet(HashMap<String, String> map, String key) {
        String v = map.get(key);
        return v == null ? "" : v;
    }
}
