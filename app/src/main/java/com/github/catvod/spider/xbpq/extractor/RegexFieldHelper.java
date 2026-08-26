package com.github.catvod.spider.xbpq.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.StringCutRule;
import com.github.catvod.spider.xbpq.utils.HtmlNodeHelper;

/**
 * 正则字段提取助手（包内共享）
 * <p>
 * 统一 XBPQ 字段规则语义，按优先级依次尝试：
 * <ol>
 *   <li>|| 备用规则：逐个尝试取首个非空</li>
 *   <li>css:/css:// 前缀：CSS 选择器提取</li>
 *   <li>p:xxx->attr 或 p:.class->text：CSS 简写语法转换后提取</li>
 *   <li>+(+xxx+) 拼接：剥去包装后递归处理</li>
 *   <li>包含 &amp;&amp;：前后缀二次截取</li>
 *   <li>[替换:a>>b] / [不含:xxx] / [序号:n] / 分割(xxx)：后处理器</li>
 *   <li>正则：取首个匹配的 group(1)（无捕获组则 group(0)）</li>
 *   <li>正则非法时按纯文本包含判断</li>
 * </ol>
 */
public final class RegexFieldHelper {

    private RegexFieldHelper() {
    }

    /** 正则Pattern缓存（修复：避免高频调用路径每次都重新编译正则表达式）
     * <p>
     * key = "规则字符串"（含DOTALL标志后缀），value = 预编译的Pattern实例。
     * 使用 ConcurrentHashMap 保证线程安全，无界缓存（XBPQ规则数量通常有限）。
     */
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /** 规则字符串最大长度（防御性上限，防止恶意超长规则 ReDoS/OOM） */
    private static final int REGEX_RULE_MAX_LEN = 4096;

    /** 缓存key前缀，用于标记DOTALL标志 */
    private static final String DOTALL_SUFFIX = ":dotall";

    /**
     * 获取或编译Pattern（带缓存）
     *
     * @param regex 正则表达式
     * @return 编译后的Pattern（已启用DOTALL模式）
     */
    private static Pattern getOrCreatePattern(String regex) {
        // 使用固定key格式：regex + dotall标记
        String cacheKey = regex + DOTALL_SUFFIX;
        return PATTERN_CACHE.computeIfAbsent(cacheKey, k -> Pattern.compile(regex, Pattern.DOTALL));
    }

    /**
     * 在指定范围内按规则提取单字段值
     *
     * @param scope 单个列表项/线路/全集 HTML 片段
     * @param rule  字段规则
     * @return 提取值，失败返回空串
     */
    public static String extract(String scope, String rule) {
        if (scope == null || scope.isEmpty() || rule == null || rule.isEmpty()) return "";
        // 防御性上限：超长规则直接返回空，防止恶意/损坏规则导致 ReDoS 或 OOM
        if (rule.length() > REGEX_RULE_MAX_LEN) return "";
        rule = rule.trim();

        // 剥去 +(+xxx+) 拼接包装
        rule = stripConcatWrap(rule);

        // || 备用规则（在剥去拼接包装后处理，避免 +(...)+ 里的 || 被误判）
        if (rule.contains("||")) {
            for (String part : rule.split("\\|\\|")) {
                String val = extract(scope, part);
                if (!val.isEmpty()) return val;
            }
            return "";
        }

        // p:xxx->attr 或 p:.class->text 或 css:/css:// 前缀 → CSS 提取
        if (CssRule.isCssRule(rule)) {
            return CssRule.extractByCss(scope, rule, 0);
        }

        // 从规则中提取并剥离后处理器（[替换]、[不含]、[序号]、分割）
        PostProcess pp = extractPostProcessors(rule);
        String rawRule = pp.rule;

        // && 前后缀截取（支持多级）
        if (rawRule.contains("&&")) {
            String val = multiCut(scope, rawRule);
            if (val == null) val = StringCutRule.applySecondCut(scope, rawRule);
            return applyPostProcessors(val, pp);
        }

        // 正则提取
        String val = regexExtract(scope, rawRule);
        return applyPostProcessors(val, pp);
    }

    /**
     * 剥去 +(+xxx+) 拼接包装：+(+p:a->href+) → p:a->href，然后递归处理内部
     */
    private static String stripConcatWrap(String rule) {
        if (rule == null) return "";
        String r = rule.trim();
        while (r.startsWith("+(") && r.endsWith(")+")) {
            r = r.substring(2, r.length() - 2).trim();
        }
        return r;
    }

    /**
     * 解析后处理器：[替换:a>>b]、[不含:xxx]、[含序号:n]、[序号:n]、分割(xxx)
     * 返回解析后的后处理器对象，规则部分不含这些标记。
     */
    private static PostProcess extractPostProcessors(String rule) {
        PostProcess pp = new PostProcess();
        pp.rule = rule;

        // [替换:a>>b#x>>y] — 可能多个替换用 # 分隔
        int ri = rule.indexOf("[替换:");
        if (ri >= 0) {
            int re = rule.indexOf("]", ri);
            if (re > ri) {
                pp.replacements = rule.substring(ri + 4, re);
                pp.rule = rule.substring(0, ri) + rule.substring(re + 1);
            }
        }

        // [不含:xxx] 或 [不包含:xxx]
        int xi = rule.indexOf("[不含:");
        if (xi < 0) xi = rule.indexOf("[不包含:");
        if (xi >= 0) {
            int xe = rule.indexOf("]", xi);
            if (xe > xi) {
                pp.exclude = rule.substring(xi + (xi + 4 < xe ? 5 : 4), xe);
                pp.rule = rule.substring(0, xi) + rule.substring(xe + 1);
            }
        }

        // [含序号:n][序号:m]
        int si = rule.indexOf("[含序号:");
        if (si >= 0) {
            int se = rule.indexOf("]", si);
            if (se > si) {
                pp.seqIndex = Integer.parseInt(rule.substring(si + 6, se));
                pp.rule = rule.substring(0, si) + rule.substring(se + 1);
            }
        }
        int si2 = rule.indexOf("[序号:");
        if (si2 >= 0) {
            int se2 = rule.indexOf("]", si2);
            if (se2 > si2) {
                int n = Integer.parseInt(rule.substring(si2 + 5, se2));
                if (n > 0) {
                    pp.seqFrom = n;
                    pp.seqTo = n;
                } else if (n < 0) {
                    pp.seqFrom = 1;
                    pp.seqTo = Math.abs(n);
                } else {
                    pp.seqFrom = 1;
                    pp.seqTo = 1;
                }
                pp.rule = rule.substring(0, si2) + rule.substring(se2 + 1);
            }
        }

        // 分割(前:xxx) 或 分割(后:xxx) — 支持中文括号和英文括号
        int fi = rule.indexOf("分割(前:");
        if (fi < 0) fi = rule.indexOf("分割（前:");
        if (fi >= 0) {
            int fe = rule.indexOf(")", fi);
            if (fe < 0) fe = rule.indexOf("）", fi);
            if (fe > fi) {
                pp.splitBefore = rule.substring(fi + 5, fe);
                pp.rule = rule.substring(0, fi) + rule.substring(fe + 1);
            }
        }
        int fi2 = rule.indexOf("分割(后:");
        if (fi2 < 0) fi2 = rule.indexOf("分割（后:");
        if (fi2 >= 0) {
            int fe2 = rule.indexOf(")", fi2);
            if (fe2 < 0) fe2 = rule.indexOf("）", fi2);
            if (fe2 > fi2) {
                pp.splitAfter = rule.substring(fi2 + 5, fe2);
                pp.rule = rule.substring(0, fi2) + rule.substring(fe2 + 1);
            }
        }

        return pp;
    }

    /** 后处理器数据 */
    private static class PostProcess {
        String rule = "";
        String replacements = "";
        String exclude = "";
        int seqIndex = 0;
        int seqFrom = 0;
        int seqTo = 0;
        String splitBefore = "";
        String splitAfter = "";
    }

    private static String applyPostProcessors(String value, PostProcess pp) {
        if (value == null) return "";
        if (pp == null) return clean(value);

        String result = value;

        // [不含:xxx]：命中则返回空
        if (!pp.exclude.isEmpty() && result.contains(pp.exclude)) {
            return "";
        }

        // [替换:a>>b#x>>y]：多个替换
        if (!pp.replacements.isEmpty()) {
            for (String pair : pp.replacements.split("#")) {
                String[] parts = pair.split(">>", 2);
                if (parts.length == 2) {
                    result = result.replace(parts[0].trim(), parts[1].trim());
                }
            }
        }

        // 分割(前:xxx)：取分割后部分
        if (!pp.splitBefore.isEmpty()) {
            int idx = result.indexOf(pp.splitBefore);
            if (idx >= 0) result = result.substring(idx + pp.splitBefore.length());
        }
        // 分割(后:xxx)：取分割前部分
        if (!pp.splitAfter.isEmpty()) {
            int idx = result.lastIndexOf(pp.splitAfter);
            if (idx >= 0) result = result.substring(0, idx);
        }

        // [含序号:n]：按索引截取（修复：原分割符为空字符串""会拆成单字符，
        // 应使用"\\|"或保留原始语义——XBPQ惯例中[含序号]通常指按"|"分割后的第N项）
        if (pp.seqIndex > 0) {
            String[] parts = result.split("\\|", -1);
            if (parts.length > pp.seqIndex) {
                result = parts[pp.seqIndex];
            }
        } else if (pp.seqFrom > 0) {
            // 按空格/逗号/& 分割取第N项
            String[] parts = result.split("[ ,&]+");
            int from = Math.max(1, pp.seqFrom) - 1;
            int to = Math.max(from + 1, pp.seqTo);
            if (from < parts.length) {
                StringBuilder sb = new StringBuilder();
                for (int i = from; i < Math.min(to, parts.length); i++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(parts[i]);
                }
                result = sb.toString();
            }
        }

        return clean(result);
    }

    /**
     * 正则提取（修复：使用缓存的Pattern实例，避免每次调用都重新编译）
     *
     * @param scope 待匹配文本
     * @param rule  正则规则
     * @return 提取值
     */
    private static String regexExtract(String scope, String rule) {
        try {
            // 使用缓存池获取或创建Pattern，避免重复编译
            Matcher m = getOrCreatePattern(rule).matcher(scope);
            if (m.find()) {
                String val = m.groupCount() >= 1 ? m.group(1) : m.group(0);
                return val == null ? "" : val;
            }
            return "";
        } catch (Exception e) {
            return scope.contains(rule) ? rule : "";
        }
    }

    /**
     * 按数组规则循环切分出所有列表项。
     * <p>
     * 支持两种规则格式：
     * <ol>
     *   <li>{@code 前缀&&后缀} 前后缀截取格式：以"前缀"起始、"后缀"结束的文本块逐段提取（含交叉匹配）</li>
     *   <li>普通正则格式：直接编译为正则，捕获组1优先，无捕获组则 group(0)</li>
     *   <li>捕获组引用格式：{@code $1}、{@code $2} 等引用正则捕获组（当 list_array 含捕获组时使用）</li>
     * </ol>
     *
     * @return 列表项集合，规则非法返回空
     */
    static List<String> splitItems(String content, String arrayRule) {
        List<String> items = new ArrayList<>();
        if (content == null || content.isEmpty() || arrayRule == null || arrayRule.isEmpty()) return items;

        // 优先尝试 && 前后缀截取格式（兼容 XBPQ 常见写法如 <li class="x"&&</li>）
        if (arrayRule.contains("&&")) {
            items = splitByCutRule(content, arrayRule);
            if (!items.isEmpty()) return items;
        }

        // 检查是否为捕获组引用格式（$1, $2, ...）
        if (arrayRule.matches("\\$\\d+(,\\s*\\$\\d+)*")) {
            // 这是字段规则，不是数组规则，回退到正则匹配
        } else {
            // 尝试作为正则匹配（支持捕获组）
            try {
                Matcher m = Pattern.compile(arrayRule, Pattern.DOTALL).matcher(content);
                while (m.find()) {
                    // 尝试使用捕获组（group(1) 优先）
                    if (m.groupCount() >= 1) {
                        String item = m.group(1);
                        if (item != null && !item.isEmpty()) {
                            items.add(item);
                            continue;
                        }
                    }
                    // 无捕获组或捕获组为空，使用完整匹配
                    String item = m.group(0);
                    if (item != null && !item.isEmpty()) items.add(item);
                }
            } catch (Exception ignored) {
                // 非法正则
            }
        }
        return items;
    }

    /**
     * 用 && 前后缀截取规则分割 HTML 内容，支持交叉重叠匹配。
     * <p>
     * 规则格式：{@code 前缀&&后缀}，例如 {@code <li class="hl-list-item"&&</li>}
     * 每次从当前位置查找前缀，再从该位置查找后缀，截取中间内容作为一项。
     * 支持无后缀格式：{@code 前缀&&} 表示截取前缀后的所有内容。
     */
    private static List<String> splitByCutRule(String content, String arrayRule) {
        List<String> items = new ArrayList<>();
        int idx = arrayRule.indexOf("&&");
        if (idx < 0) return items;
        String start = arrayRule.substring(0, idx).trim();
        String end = arrayRule.substring(idx + 2).trim();

        if (start.isEmpty()) return items;
        if (end.isEmpty()) {
            // 无后缀格式：只截取前缀后的所有内容（直到下一个前缀）
            int pos = 0;
            while (pos <= content.length()) {
                int startIdx = content.indexOf(start, pos);
                if (startIdx < 0) break;
                int afterStart = startIdx + start.length();
                // 找下一个前缀的位置作为结束
                int nextStart = content.indexOf(start, afterStart);
                if (nextStart < 0) {
                    items.add(content.substring(afterStart));
                    break;
                } else {
                    items.add(content.substring(afterStart, nextStart));
                    pos = nextStart;
                }
            }
            return items;
        }

        int pos = 0;
        while (pos <= content.length()) {
            int startIdx = content.indexOf(start, pos);
            if (startIdx < 0) break;
            int afterStart = startIdx + start.length();
            // 找最近的闭合后缀（非贪婪），避免跨块匹配
            int endIdx = content.indexOf(end, afterStart);
            if (endIdx < 0) break;
            items.add(content.substring(afterStart, endIdx));
            pos = endIdx + end.length();
        }
        return items;
    }

    /**
     * 多级 && 截取：递归处理含多个 && 的规则，如 {@code <i&&</i>&nbsp;&&}
     * <p>
     * 支持末尾空后缀格式：{@code start&&end&&} 表示截取到 end 后的所有内容。
     * 支持空起始格式：{@code &&end} 表示从开头截取到 end。
     *
     * @return 截取结果，失败返回 null
     */
    static String multiCut(String scope, String rule) {
        if (scope == null || scope.isEmpty() || rule == null || rule.isEmpty()) return null;

        // 按 && 拆分规则
        List<String> parts = new ArrayList<>();
        int last = 0;
        while (true) {
            int idx = rule.indexOf("&&", last);
            if (idx < 0) {
                parts.add(rule.substring(last));
                break;
            }
            parts.add(rule.substring(last, idx));
            last = idx + 2;
        }

        if (parts.size() < 2) return null;

        // 检查是否为"有前缀无后缀"的截取（最后部分为空，如 "start&&end&&"）
        boolean hasTrailingEmpty = !parts.isEmpty() && parts.get(parts.size() - 1).trim().isEmpty();
        if (hasTrailingEmpty) {
            // 去掉末尾空部分，最后一对 (start, end) 使用"截到末尾"语义
            int pairs = (parts.size() - 1) / 2;
            String current = scope;
            for (int i = 0; i < pairs; i++) {
                String start = parts.get(i * 2).trim();
                String end = parts.get(i * 2 + 1).trim();
                if (start.isEmpty() && end.isEmpty()) {
                    // 两端都为空，跳过
                    continue;
                }
                if (start.isEmpty()) {
                    // 空起始：从开头截取到 end
                    int endIdx = current.indexOf(end);
                    if (endIdx < 0) return null;
                    current = current.substring(0, endIdx);
                } else if (end.isEmpty()) {
                    // 空后缀：截到末尾
                    int startIdx = current.indexOf(start);
                    if (startIdx < 0) return null;
                    current = current.substring(startIdx + start.length());
                } else {
                    int startIdx = current.indexOf(start);
                    if (startIdx < 0) return null;
                    int afterStart = startIdx + start.length();
                    int endIdx = current.indexOf(end, afterStart);
                    if (endIdx < 0) return null;
                    current = current.substring(afterStart, endIdx);
                }
            }
            return current.isEmpty() ? null : current;
        }

        // 标准模式：交替查找 start/end（空部分视为"不限"）
        String current = scope;
        for (int i = 0; i + 1 < parts.size(); i += 2) {
            String start = parts.get(i).trim();
            String end = parts.get(i + 1).trim();
            if (start.isEmpty() && end.isEmpty()) continue;
            if (start.isEmpty()) {
                int endIdx = current.indexOf(end);
                if (endIdx < 0) return null;
                current = current.substring(0, endIdx);
            } else if (end.isEmpty()) {
                int startIdx = current.indexOf(start);
                if (startIdx < 0) return null;
                current = current.substring(startIdx + start.length());
            } else {
                int startIdx = current.indexOf(start);
                if (startIdx < 0) return null;
                int afterStart = startIdx + start.length();
                int endIdx = current.indexOf(end, afterStart);
                if (endIdx < 0) return null;
                current = current.substring(afterStart, endIdx);
            }
        }
        return current.isEmpty() ? null : current;
    }

    /**
     * 清理字段值（去标签/实体/多余空白）
     */
    static String clean(String value) {
        if (value == null) return "";
        String cleaned = HtmlNodeHelper.cleanText(value);
        return cleaned == null ? "" : cleaned.trim();
    }
}
