package com.github.catvod.spider;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQ URL规则解析器。
 * <p>解析XBPQ格式的URL规则字符串，支持转义、变量替换、编码等功能。</p>
 *
 * <h3>规则语法：</h3>
 * <ul>
 *   <li>{key} - 变量占位符</li>
 *   <li>\| - 管道符转义</li>
 *   <li>|| - 多重条件分隔符（逻辑或）</li>
 *   <li>~condition^ - 条件表达式</li>
 *   <li>{{key}} - 模板变量（用于域名映射等）</li>
 * </ul>
 */
public final class XbpqRuleParser {

    /** 管道符占位符（转义用） */
    private static final String PIPE_PLACEHOLDER = "\u0000";

    /** 条件开始标记 */
    private static final String CONDITION_START = "~";

    /** 条件结束标记 */
    private static final String CONDITION_END = "^";

    /** 变量开始标记 */
    private static final String VAR_START = "{";

    /** 变量结束标记 */
    private static final String VAR_END = "}";

    /** {{ 模板变量开始 */
    private static final String TEMPLATE_VAR_START = "{{";

    /** }} 模板变量结束 */
    private static final String TEMPLATE_VAR_END = "}}";

    /** 模板变量正则：{{key-name}} */
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{(\\w+-?\\w*)\\}\\}");

    /** 比较条件正则：var>num / var>=num / var<num / var<=num */
    private static final Pattern CONDITION_CMP_PATTERN = Pattern.compile("(\\w+)([<>]=?)(\\d+)");

    /** URL协议头提取正则 */
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https?://[^/]+)");

    /** 多级条件分隔符正则 */
    private static final Pattern MULTI_COND_PATTERN = Pattern.compile("\\|\\|");

    private XbpqRuleParser() {}

    // ==================== 管道符转义 ====================

    /**
     * 转义管道符。
     * <p>将规则字符串中的 \| 转为管道符，将 | 转为 ||（多重条件分隔符）。</p>
     *
     * @param rule 原始规则
     * @return 转义后的规则
     */
    public static String escapePipe(String rule) {
        if (rule == null) return "";
        // 先保护转义的管道符 \|
        rule = rule.replace("\\|", PIPE_PLACEHOLDER);
        // 将未转义的 | 替换为 ||（条件分隔符）
        rule = rule.replace("|", "||");
        // 还原转义的管道符
        rule = rule.replace(PIPE_PLACEHOLDER, "|");
        return rule;
    }

    // ==================== 变量替换 ====================

    /**
     * 替换规则中的 {key} 变量。
     *
     * @param rule   包含变量的规则（如 {@code http://site.com/{page}}）
     * @param values 变量值Map
     * @return 替换后的规则
     */
    public static String replaceVars(String rule, Map<String, String> values) {
        if (rule == null || rule.isEmpty()) return "";
        if (values == null || values.isEmpty()) return rule;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                rule = rule.replace(VAR_START + key + VAR_END, value);
            }
        }
        return rule;
    }

    /**
     * 替换规则中的 {{key}} 模板变量（用于域名映射等场景）。
     *
     * @param rule   包含模板变量的规则
     * @param values 变量值Map
     * @return 替换后的规则
     */
    public static String replaceTemplateVars(String rule, Map<String, String> values) {
        if (rule == null || rule.isEmpty()) return "";
        if (values == null || values.isEmpty()) return rule;

        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(rule);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = values.getOrDefault(varName, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ==================== URL构建 ====================

    /**
     * 构建URL。
     * <p>将规则中的协议头与目标URL合并。</p>
     *
     * @param base   基础URL
     * @param rule   规则（可能包含协议头，以 // 开头表示相对协议）
     * @param target 目标URL
     * @return 构建后的URL
     */
    public static String buildUrl(String base, String rule, String target) {
        if (rule == null || rule.isEmpty()) return target;
        if (!rule.startsWith("//")) return rule;

        // 提取协议
        int protocolEnd = rule.indexOf("://");
        String protocol = protocolEnd > 0 ? rule.substring(0, protocolEnd + 3) : "https:";

        // 提取主机和路径
        String hostAndPath = protocolEnd > 0 ? rule.substring(protocolEnd + 3) : rule;

        // 处理目标URL
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        } else if (target.startsWith("//")) {
            return protocol + target.substring(2);
        } else if (target.startsWith("/")) {
            // 绝对路径，使用base的host
            Pattern hostPattern = Pattern.compile("^(https?://[^/]+)");
            Matcher hostMatcher = hostPattern.matcher(base);
            String host = hostMatcher.find() ? hostMatcher.group(1) : base;
            return host + target;
        } else {
            return base + "/" + target;
        }
    }

    // ==================== 条件表达式解析 ====================

    /**
     * 解析URL中的条件参数。
     * <p>支持格式：{@code http://site.com/{page}?key=value~condition^}</p>
     *
     * @param url      原始URL
     * @param base     基础URL（用于条件为false时回退）
     * @param variable 当前变量名（用于条件评估）
     * @param values   变量值Map
     * @return 解析后的URL
     */
    public static String parseConditionalUrl(String url, String base, String variable,
            Map<String, String> values) {
        if (url == null || url.isEmpty()) return base;

        // 检查是否有条件表达式 ~condition^
        int conditionStart = url.indexOf(CONDITION_START);
        int conditionEnd = url.indexOf(CONDITION_END);

        if (conditionStart >= 0 && conditionEnd > conditionStart) {
            String condition = url.substring(conditionStart + 1, conditionEnd);
            boolean conditionMet = evaluateCondition(condition, variable, values);

            // 移除条件表达式部分
            url = url.substring(0, conditionStart) + url.substring(conditionEnd + 1);

            if (!conditionMet) {
                // 条件不满足，使用基础URL替代
                return base != null ? base : url;
            }
        }

        // 替换 {key} 变量
        url = replaceVars(url, values);
        // 替换 {{key}} 模板变量
        url = replaceTemplateVars(url, values);
        return url;
    }

    /**
     * 评估条件表达式。
     * <p>支持的条件格式：</p>
     * <ul>
     *   <li>简单条件：{@code page} - 变量非空则满足</li>
     *   <li>否定条件：{@code !page} - 变量为空则满足</li>
     *   <li>比较条件：{@code page>1} / {@code page<10}</li>
     *   <li>包含条件：{@code page~2} - 包含特定值</li>
     *   <li>多重条件：{@code cond1||cond2} - 逻辑或</li>
     * </ul>
     *
     * @param condition 条件表达式
     * @param variable  当前变量名
     * @param values    变量值Map
     * @return 是否满足条件
     */
    public static boolean evaluateCondition(String condition, String variable,
            Map<String, String> values) {
        if (condition == null || condition.isEmpty()) return true;

        // 处理多重条件（|| 逻辑或）
        if (condition.contains("||")) {
            String[] parts = MULTI_COND_PATTERN.split(condition);
            for (String part : parts) {
                if (evaluateCondition(part.trim(), variable, values)) {
                    return true;
                }
            }
            return false;
        }

        condition = condition.trim();

        // 否定条件：!variable
        if (condition.startsWith("!")) {
            String negVar = condition.substring(1).trim();
            String val = values != null ? values.get(negVar) : null;
            return val == null || val.isEmpty();
        }

        // 比较条件：variable>num 或 variable<num
        Pattern cmpPattern = Pattern.compile("(\\w+)([<>]=?)(\\d+)");
        Matcher cmpMatcher = cmpPattern.matcher(condition);
        if (cmpMatcher.find()) {
            String cmpVar = cmpMatcher.group(1);
            String cmpOp = cmpMatcher.group(2);
            int cmpVal = Integer.parseInt(cmpMatcher.group(3));
            String val = values != null ? values.get(cmpVar) : null;
            if (val == null || val.isEmpty()) return false;
            int numVal;
            try {
                numVal = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return false;
            }
            switch (cmpOp) {
                case ">": return numVal > cmpVal;
                case ">=": return numVal >= cmpVal;
                case "<": return numVal < cmpVal;
                case "<=": return numVal <= cmpVal;
                default: return false;
            }
        }

        // 包含条件：variable~value
        if (condition.contains("~")) {
            String[] parts = condition.split("~");
            if (parts.length == 2) {
                String cmpVar = parts[0].trim();
                String cmpVal = parts[1].trim();
                String val = values != null ? values.get(cmpVar) : null;
                return val != null && val.contains(cmpVal);
            }
        }

        // 简单条件：variable 非空即满足
        if (values != null && values.containsKey(condition)) {
            String val = values.get(condition);
            return val != null && !val.isEmpty();
        }

        // 默认：条件满足
        return true;
    }

    // ==================== URL编码 ====================

    /**
     * URL编码变量值。
     *
     * @param rule    原始规则
     * @param varName 变量名
     * @param value   变量值
     * @return 编码后的规则
     */
    public static String encodeVar(String rule, String varName, String value) {
        try {
            String encoded = java.net.URLEncoder.encode(value, "UTF-8");
            return rule.replace(VAR_START + varName + VAR_END, encoded);
        } catch (Exception e) {
            return rule;
        }
    }

    // ==================== 筛选URL解析 ====================

    /**
     * 解析筛选URL。
     *
     * @param base 基础URL
     * @param url  包含筛选参数的URL
     * @param vars 筛选变量
     * @return 解析后的URL
     */
    public static String parseFilterUrl(String base, String url, Map<String, String> vars) {
        if (url == null || url.isEmpty()) return base;

        // 替换变量
        url = replaceVars(url, vars);
        url = replaceTemplateVars(url, vars);

        // 移除条件表达式（已处理）
        int condStart = url.indexOf(CONDITION_START);
        int condEnd = url.indexOf(CONDITION_END);
        if (condStart >= 0 && condEnd > condStart) {
            url = url.substring(0, condStart) + url.substring(condEnd + 1);
        }

        return url;
    }

    // ==================== 选择器处理 ====================

    /**
     * 处理选择器中的管道符转义。
     *
     * @param selector 选择器字符串
     * @return 处理后的选择器
     */
    public static String processSelector(String selector) {
        if (selector == null || selector.isEmpty()) return "";
        // 将 || 替换为真正的管道符（用于选择器中的多重选择器）
        return selector.replace("||", "|");
    }

    /**
     * 从文本中提取指定标签对之间的内容。
     *
     * @param content 原始文本
     * @param tag     标签名（如 "item"）
     * @return 提取的内容列表
     */
    public static java.util.List<String> extractBetweenTags(String content, String tag) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (content == null || tag == null || tag.isEmpty()) return result;

        String openTag = "<" + tag + ">";
        String closeTag = "</" + tag + ">";
        int start = 0;
        while (true) {
            int openIdx = content.indexOf(openTag, start);
            if (openIdx < 0) break;
            int closeIdx = content.indexOf(closeTag, openIdx + openTag.length());
            if (closeIdx < 0) break;
            result.add(content.substring(openIdx + openTag.length(), closeIdx).trim());
            start = closeIdx + closeTag.length();
        }
        return result;
    }
}
