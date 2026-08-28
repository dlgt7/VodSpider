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
     * 在指定范围内按规则提取单字段值（预解析 Element 版，性能优化）。
     * <p>
     * CSS 规则直接作用于已解析的 Element（Document 或列表项元素），
     * 避免每个字段都触发一次 {@code Jsoup.parse}；
     * 非 CSS 规则（&& 截取/正则/后处理器）仍作用于 scope 字符串。
     *
     * @param el    已解析的 Jsoup Element（Document 或单个列表项元素）
     * @param scope 与 el 对应的 HTML 片段（供正则/截取类规则使用）
     * @param rule  字段规则
     * @return 提取值，失败返回空串
     */
    public static String extract(org.jsoup.nodes.Element el, String scope, String rule) {
        if (el == null) return extract(scope, rule);
        if (rule == null || rule.isEmpty()) return "";
        // 防御性上限：与 String 版一致，超长规则视为非法丢弃
        if (rule.length() > REGEX_RULE_MAX_LEN) return "";
        String trimmed = rule.trim();
        // || 备用规则：每条单独分流（混合 CSS/正则规则时可各自走最优路径）
        String stripped = stripConcatWrap(trimmed);
        if (stripped.contains("||")) {
            for (String part : stripped.split("\\|\\|")) {
                String val = extract(el, scope, part);
                if (!val.isEmpty()) return val;
            }
            return "";
        }
        // "+" 拼接（真实规则形态：p:span->text+(+p:small->text+)、
        // p:div->text+p:div->text[序号:2]）：逐段提取后按序连接
        List<String> concat = splitConcat(trimmed);
        if (concat.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String part : concat) {
                sb.append(extractCssOrFallback(el, scope, part));
            }
            return clean(sb.toString());
        }
        return extractCssOrFallback(el, scope, trimmed);
    }

    /** Element 版单段提取：CSS 规则作用于元素子树，其余回落到 String 版 */
    private static String extractCssOrFallback(org.jsoup.nodes.Element el, String scope, String rule) {
        String trimmed = rule == null ? "" : rule.trim();
        if (trimmed.isEmpty()) return "";
        // 后处理器必须先于 CSS 分支剥离：真实规则形如
        // "p:div[...]->text[含序号:3][替换:导演：>>空]"，标记混进选择器会使其非法
        PostProcess pp = extractPostProcessors(trimmed);
        String rawRule = pp.rule.trim();
        if (rawRule.isEmpty()) return "";
        if (CssRule.isCssRule(rawRule)) {
            String val = CssRule.extractByCss(el, rawRule, cssIndexFromSeq(pp));
            return applyPostProcessors(val, pp);
        }
        return extract(scope, trimmed);
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

        // "+" 拼接：仅 CSS 简写规则使用（真实规则 "p:span->text+(+p:small->text+)"）。
        // 正则规则普遍含 "+"（量词），绝不对其做拼接切分。
        List<String> concat = splitConcat(rule);
        if (concat.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String part : concat) {
                sb.append(extractCssSingle(scope, part));
            }
            return clean(sb.toString());
        }

        return extractCssSingle(scope, rule);
    }

    /** String 版单段提取：后处理器前置 → CSS / && 截取 / 正则 三分流 */
    private static String extractCssSingle(String scope, String rule) {
        // 从规则中提取并剥离后处理器（[替换]、[不含]、[序号]、分割）。
        // 修复：原先该步骤在 CSS 分支之后，p:xxx->text[含序号:3] 的标记会混入
        // 选择器使其非法（真实规则大量使用此形态）。
        PostProcess pp = extractPostProcessors(rule);
        String rawRule = pp.rule;

        // CSS 提取：[含序号:n]/[序号:n] 映射为第 n 个匹配元素（1-based）
        if (CssRule.isCssRule(rawRule)) {
            String val = CssRule.extractByCss(scope, rawRule, cssIndexFromSeq(pp));
            return applyPostProcessors(val, pp);
        }

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
     * 将后处理器中的序号配置转换为 CSS 元素索引（0-based，供 extractByCss 使用）。
     * <p>真实规则语义（见 擦边2.json）："p:div[...]->text[含序号:3]" = 第 3 个匹配元素；
     * [序号:n] 同义。</p>
     */
    private static int cssIndexFromSeq(PostProcess pp) {
        if (pp.seqIndex > 0) return pp.seqIndex - 1;
        if (pp.seqFrom > 0) return pp.seqFrom - 1;
        return 0;
    }

    /**
     * 拆分 "+" 拼接的 CSS 简写规则。
     * <p>仅当整条规则以 p:/css: 开头时生效。为避免误拆选择器属性值中的 "+"
     *（如 {@code p:div[class*="x+y"]}），只在 "+" 后面紧跟 p:/css: 前缀或
     * 拼接括号 "(" / ")" 时才切分；括号碎片（"+(+xxx+)" 拆分残留）直接丢弃。</p>
     */
    private static List<String> splitConcat(String rule) {
        List<String> parts = new ArrayList<>();
        if (rule == null) return parts;
        String t = rule.trim();
        if (!(t.startsWith("p:") || t.startsWith("css:"))) return parts;
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '+') {
                String rest = t.substring(i + 1).trim();
                if (rest.startsWith("p:") || rest.startsWith("css:")
                        || rest.startsWith("(") || rest.startsWith(")")) {
                    addConcatPart(parts, cur.toString());
                    cur.setLength(0);
                    i++;
                    continue;
                }
            }
            cur.append(c);
            i++;
        }
        addConcatPart(parts, cur.toString());
        return parts;
    }

    /** 拼接段入队：空段与括号碎片丢弃 */
    private static void addConcatPart(List<String> parts, String seg) {
        String s = seg.trim();
        if (s.isEmpty() || s.equals("(") || s.equals(")")) return;
        parts.add(s);
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
        // 修复：各后处理块原先都从【原始 rule】重建 pp.rule，多类标记共存时
        // 后处理的块会覆盖前面块的剥离结果（如 "[含序号:3][替换:x>>空]" 残留 [替换:]）。
        // 现统一在一个 remaining 串上依次剥离。
        String remaining = rule;

        // [替换:a>>b#x>>y] — 可能多个替换用 # 分隔
        int ri = remaining.indexOf("[替换:");
        if (ri >= 0) {
            int re = remaining.indexOf("]", ri);
            if (re > ri) {
                pp.replacements = remaining.substring(ri + 4, re);
                remaining = remaining.substring(0, ri) + remaining.substring(re + 1);
            }
        }

        // [不含:xxx] 或 [不包含:xxx]
        // 修复：原实现 xi + 4 < xe ? 5 : 4 的偏移判断依据是位置而非匹配的前缀形式，
        // "[不含:abc]"（4字符前缀）会错误地 +5 起始，导致提取值丢失首字符。
        // 现按实际匹配的前缀分别记录其长度。
        int xi = remaining.indexOf("[不含:");
        int xPrefixLen = 4;
        if (xi < 0) {
            xi = remaining.indexOf("[不包含:");
            xPrefixLen = 5;
        }
        if (xi >= 0) {
            int xe = remaining.indexOf("]", xi);
            if (xe > xi) {
                pp.exclude = remaining.substring(xi + xPrefixLen, xe);
                remaining = remaining.substring(0, xi) + remaining.substring(xe + 1);
            }
        }

        // [含序号:n][序号:m]
        // 修复：两处取值偏移各多跳 1 个字符（"[含序号:" 为 5 字符、"[序号:" 为 4 字符），
        // 原实现 "[含序号:3]" 取到空串、"[序号:12]" 取到 "2"，序号功能整体失效
        int si = remaining.indexOf("[含序号:");
        if (si >= 0) {
            int se = remaining.indexOf("]", si);
            if (se > si) {
                // 修复：parseInt 未做容错，规则写成 [含序号:第N集] 等非法数值时
                // 抛 NumberFormatException 会一路向上打断整个字段提取流程
                pp.seqIndex = parseIntQuiet(remaining.substring(si + 5, se), 0);
                remaining = remaining.substring(0, si) + remaining.substring(se + 1);
            }
        }
        int si2 = remaining.indexOf("[序号:");
        if (si2 >= 0) {
            int se2 = remaining.indexOf("]", si2);
            if (se2 > si2) {
                int n = parseIntQuiet(remaining.substring(si2 + 4, se2), 0);
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
                remaining = remaining.substring(0, si2) + remaining.substring(se2 + 1);
            }
        }

        // 分割(前:xxx) 或 分割(后:xxx) — 支持中文括号和英文括号
        int fi = remaining.indexOf("分割(前:");
        if (fi < 0) fi = remaining.indexOf("分割（前:");
        if (fi >= 0) {
            int fe = remaining.indexOf(")", fi);
            if (fe < 0) fe = remaining.indexOf("）", fi);
            if (fe > fi) {
                pp.splitBefore = remaining.substring(fi + 5, fe);
                remaining = remaining.substring(0, fi) + remaining.substring(fe + 1);
            }
        }
        int fi2 = remaining.indexOf("分割(后:");
        if (fi2 < 0) fi2 = remaining.indexOf("分割（后:");
        if (fi2 >= 0) {
            int fe2 = remaining.indexOf(")", fi2);
            if (fe2 < 0) fe2 = remaining.indexOf("）", fi2);
            if (fe2 > fi2) {
                pp.splitAfter = remaining.substring(fi2 + 5, fe2);
                remaining = remaining.substring(0, fi2) + remaining.substring(fe2 + 1);
            }
        }

        pp.rule = remaining;
        return pp;
    }

    /** 安全整数解析：非法数值返回默认值，绝不抛异常 */
    private static int parseIntQuiet(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
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

        // [替换:a>>b#x>>y]：多个替换（"空" 为删除语义，见真实规则 [替换:导演：>>空]）
        if (!pp.replacements.isEmpty()) {
            for (String pair : pp.replacements.split("#")) {
                String[] parts = pair.split(">>", 2);
                if (parts.length == 2) {
                    String from = parts[0].trim();
                    String to = parts[1].trim();
                    if ("空".equals(to)) to = "";
                    if (!from.isEmpty()) {
                        result = result.replace(from, to);
                    }
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

        // [含序号:n]（非 CSS 规则）：按 "|" 分割后取第 n 项（1-based）
        if (pp.seqIndex > 0) {
            String[] parts = result.split("\\|", -1);
            if (parts.length >= pp.seqIndex) {
                result = parts[pp.seqIndex - 1];
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
        // 修复：真实规则里存在"字段值即常量"的写法（如
        // "搜索图片": "https://cainisi.cf/暂无封面.jpg"），它并不是截取规则。
        // 原先会被当作正则去 HTML 里匹配，匹配不到就返回空，导致封面永远为空。
        if (isPlainValue(rule)) return rule.trim();
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
     * 判断规则是否为"常量值"而非截取规则：形如完整的 http(s) 链接，
     * 且不含 XBPQ 截取语法（&&、$、[替换: 等）与空白字符。
     */
    private static boolean isPlainValue(String rule) {
        if (rule == null) return false;
        String v = rule.trim();
        if (v.length() < 8) return false;
        if (!v.startsWith("http://") && !v.startsWith("https://")) return false;
        if (v.contains("&&") || v.contains("$") || v.contains("[替换:")
                || v.contains("[不含:") || v.contains("[序号:") || v.contains("[含序号:")) {
            return false;
        }
        for (int i = 0; i < v.length(); i++) {
            if (Character.isWhitespace(v.charAt(i))) return false;
        }
        return true;
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
    public static List<String> splitItems(String content, String arrayRule) {
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
            // 修复：改用 PATTERN_CACHE 缓存预编译 Pattern（原实现每次调用都 compile）
            try {
                Matcher m = getOrCreatePattern(arrayRule).matcher(content);
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
            // 去掉末尾空部分，最后一对 (start, end) 使用"截到末尾"语义。
            // 修复：原公式 (size-1)/2 在三段截取（如 "<i&&</i>&nbsp;&&" →
            // [<i, </i>, &nbsp;, ""]）时算出 1 对，末段 "截到末尾" 被丢弃；
            // 正确对数 = 去掉末尾空段后的段数 / 2，即 size/2。
            int pairs = parts.size() / 2;
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
