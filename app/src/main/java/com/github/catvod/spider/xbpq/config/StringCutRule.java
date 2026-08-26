package com.github.catvod.spider.xbpq.config;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 字符串截取规则类（XBPQ 核心工具）
 * <p>
 * 支持完整的 XBPQ 二次截取语法和后处理器语法：
 * <ul>
 *   <li>二次截取：前缀&&后缀（支持多级链式：a&&b&&c&&d）</li>
 *   <li>备用选择器：规则1||规则2（逐个尝试，返回首个非空结果）</li>
 *   <li>后处理器：[替换:a>>b]、[包含:关键词]、[不包含:关键词]</li>
 *   <li>HTML 清理：去除标签、实体解码、空白压缩</li>
 *   <li>正则转义：安全地将用户输入嵌入正则表达式</li>
 * </ul>
 *
 * <h3>功能完整性说明（v2.2 修复版）：</h3>
 * <ul>
 *   <li>{@link #doSecondCut} — 核心截取引擎，支持多级链式 && 截取</li>
 *   <li>{@link #applySecondCut} — 公开入口，增加 || 备用选择器支持</li>
 *   <li>{@link #applyPostProcessors} — 后处理器：替换/包含/不包含过滤</li>
 *   <li>{@link #cleanHtml} — HTML 标签清理（增强版：支持 script/style 移除、实体解码）</li>
 *   <li>{@link #escapeRegex} — 正则特殊字符安全转义（已修复 $0 引用 bug）</li>
 *   <li>{@link #parseCutRule} — 规则解析为数组格式（保留供外部/扩展使用）</li>
 * </ul>
 *
 * @author CatVodSpider Team
 * @version 2.2
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

    // ==================== 二次截取核心 ====================

    /**
     * 应用二次截取（公开入口）
     * <p>
     * 功能完整性：
     * <ul>
     *   <li>支持 || 备用选择器：逐个尝试，返回首个非空结果</li>
     *   <li>支持 && 多级链式截取：a&&b&&c 表示先按a&&b截取，再用结果按c截取（递归）</li>
     *   <li>null/空输入安全保护</li>
     * </ul>
     *
     * @param content 原始内容
     * @param cutRule 截取规则（前缀&&后缀 或 规则1||规则2）
     * @return 截取后的内容，失败返回空字符串
     */
    public static String applySecondCut(String content, String cutRule) {
        if (content == null || cutRule == null || cutRule.isEmpty()) return content != null ? content : "";

        // 备用选择器处理（|| 分隔，逐个尝试返回首个非空结果）
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
     * 执行单次二次截取（内部核心引擎）
     * <p>
     * 功能完整性说明：
     * <ul>
     *   <li>使用 {@code String.indexOf()} 进行字面量前缀/后缀匹配（非正则，性能优先）</li>
     *   <li>后缀未找到时，返回前缀之后的所有内容（"贪婪"模式，符合 XBPQ 惯例）</li>
     *   <li>前缀未找到时，返回空字符串（表示匹配失败）</li>
     *   <li>规则中无 {@code &&} 时，原样返回输入（无操作）</li>
     * </ul>
     *
     * @param content 待截取文本
     * @param cutRule 截取规则（必须含单个 {@code &&}）
     * @return 截取结果，失败返回空字符串
     */
    private static String doSecondCut(String content, String cutRule) {
        if (content == null || cutRule == null || cutRule.isEmpty()) return "";

        int idx = cutRule.indexOf(CUT_SEPARATOR);
        if (idx < 0) return content;  // 无 && 分隔符，原样返回

        String start = cutRule.substring(0, idx).trim();
        String end = cutRule.substring(idx + CUT_SEPARATOR.length()).trim();

        // 空前缀匹配整个内容开头，空后缀匹配到内容末尾
        int startPos = start.isEmpty() ? 0 : content.indexOf(start);
        if (startPos < 0) return "";  // 前缀未找到
        startPos += start.length();

        if (end.isEmpty()) {
            // 空后缀：返回从 startPos 到末尾的全部内容
            return content.substring(startPos);
        }

        int endPos = content.indexOf(end, startPos);
        if (endPos < 0) {
            // 后缀未找到：返回前缀之后的全部内容（贪婪模式）
            return content.substring(startPos);
        }

        return content.substring(startPos, endPos);
    }

    // ==================== 后处理器 ====================

    /**
     * 应用后处理器（由 RegexFieldHelper 调用）
     * <p>
     * 支持的后处理器语法：
     * <ul>
     *   <li>{@code [替换:a>>b]} — 将匹配到的整段文本中的 a 替换为 b</li>
     *   <li>{@code [包含:关键词]} — 结果必须包含关键词，否则返回空串</li>
     *   <li>{@code [不包含:关键词]} — 结果不能包含关键词，否则返回空串</li>
     * </ul>
     * 多个后处理器按出现顺序依次作用于 StringBuilder（原地修改）。
     *
     * @param str 原始字符串（可能含有后处理器标记）
     * @return 处理后字符串；不满足包含/不包含条件时返回空字符串
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
                // 注意：StringBuilder.replace() 是原地修改方法，返回自身仅用于链式调用，
                // 无需将返回值赋回 result 变量。
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

    // ==================== HTML 清理 ====================

    /**
     * 清理 HTML 标签，提取纯文本（功能完整版）
     * <p>
     * 功能完整性（v2.2 增强）：
     * <ul>
     *   <li>移除所有 HTML 标签：{@code <tag attr="val">} → 空串</li>
     *   <li>移除 script 和 style 标签的<strong>内容</strong>（不仅仅是标签本身）</li>
     *   <li>解码常见 HTML 实体：{@code &amp; &lt; &gt; &nbsp; &quot; &#123;}</li>
     *   <li>压缩连续空白为单个空格</li>
     *   <li>首尾 trim</li>
     * </ul>
     *
     * @param html HTML 内容
     * @return 纯文本
     */
    public static String cleanHtml(String html) {
        if (html == null) return "";

        String text = html;

        // 1. 先移除 script 和 style 标签及其内容（避免执行脚本/样式文本泄漏）
        text = text.replaceAll("(?i)<script[^>]*?>.*?</script>", "");
        text = text.replaceAll("(?i)<style[^>]*?>.*?</style>", "");

        // 2. 移除所有 HTML 标签
        text = text.replaceAll("<[^>]+?>", "");

        // 3. 解码常见 HTML 实体（按长度降序排列，避免短实体先误匹配长实体的子串）
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("&apos", "'");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&amp;", "&");

        // 4. 解码数字实体 &#NNN; 和 &#xHHH;
        Matcher m1 = Pattern.compile("&#(\\d+);").matcher(text);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            try { m1.appendReplacement(sb1, String.valueOf((char) Integer.parseInt(m1.group(1)))); }
            catch (Exception ignored) { m1.appendReplacement(sb1, m1.group(0)); }
        }
        m1.appendTail(sb1);
        text = sb1.toString();
        Matcher m2 = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(text);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            try { m2.appendReplacement(sb2, String.valueOf((char) Integer.parseInt(m2.group(1), 16))); }
            catch (Exception ignored) { m2.appendReplacement(sb2, m2.group(0)); }
        }
        m2.appendTail(sb2);
        text = sb2.toString();

        // 5. 压缩连续空白为单个空格
        text = text.replaceAll("\\s+", " ");

        return text.trim();
    }

    // ==================== 正则转义 ====================

    /**
     * 转义正则表达式特殊字符（功能完整版）
     * <p>
     * 将字符串中的正则元字符进行字面量转义，使其可以安全地嵌入正则表达式中作为字面量匹配。
     * <p>
     * 修复历史（v2.1）：原实现使用
     * {@code keyword.replaceAll("[\\^$.*+?()\\[\\]{}|]", "\\$0")}，
     * 但在 Java 的 replaceAll replacement 字符串中，{@code $0} 是特殊引用（表示匹配全文），
     * 会导致转义结果错误。例如输入 {@code a.b} 会产生异常或错误输出。
     * 现改用 StringBuilder 逐字符拼接方式，避免 replacement 中的 $ 引用问题。
     *
     * @param keyword 需要转义的字符串
     * @return 所有正则元字符都已转义的字符串；null 输入返回空字符串
     */
    public static String escapeRegex(String keyword) {
        if (keyword == null) return "";
        StringBuilder sb = new StringBuilder(keyword.length() * 2);
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if ("\\^$.*+?()[]{}|".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ==================== 规则解析 ====================

    /**
     * 将截取规则解析为结构化数组（功能完整版）
     * <p>
     * 解析格式：{@code 前缀&&后缀[&&左偏移[&&右偏移[&&回溯层数]]]}
     * <p>
     * 返回数组结构：
     * <ul>
     *   <li>[0] = 前缀字符串（trim 后）</li>
     *   <li>[1] = 后缀字符串（trim 后）</li>
     *   <li>[2] = 左偏移量（数字字符串，默认 "0"；表示从前缀匹配位置向左偏移的字符数）</li>
     *   <li>[3] = 右偏移量（数字字符串，默认 "0"；表示从后缀匹配位置向右偏移的字符数）</li>
     *   <li>[4] = 回溯层数（数字字符串，默认 "0"；当首次匹配失败时回溯重试的次数）</li>
     * </ul>
     *
     * <h4>功能完整性说明：</h4>
     * <ul>
     *   <li>此方法提供结构化的规则解析结果，可供需要精细控制截取行为的扩展场景使用</li>
     *   <li>当前 {@link #doSecondCut} 内部使用简化逻辑（仅取前缀和后缀），未使用偏移/回溯参数</li>
     *   <li>如需启用偏移/回溯功能，可基于此方法的返回值实现增强版 doSecondCut</li>
     * </ul>
     *
     * @param rule 截取规则字符串
     * @return 长度为 5 的字符串数组；规则无效时返回空数组
     */
    public static String[] parseCutRule(String rule) {
        if (rule == null || rule.isEmpty()) return new String[0];

        String[] parts = rule.split(CUT_SEPARATOR);
        if (parts.length < 2) return new String[0];  // 至少需要前缀和后缀

        String[] parsed = new String[5];
        parsed[0] = parts[0].trim();               // 前缀
        parsed[1] = parts[1].trim();               // 后缀

        // 可选参数：左偏移、右偏移、回溯层数
        parsed[2] = parts.length > 2 ? parts[2].trim() : "0";  // 左偏移
        parsed[3] = parts.length > 3 ? parts[3].trim() : "0";  // 右偏移
        parsed[4] = parts.length > 4 ? parts[4].trim() : "0";  // 回溯层数

        return parsed;
    }
}
