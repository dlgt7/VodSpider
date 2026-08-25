package com.github.catvod.spider.xbpq.extractor;

import java.util.ArrayList;
import java.util.List;
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
 *   <li>包含 &amp;&amp;：前后缀二次截取</li>
 *   <li>正则：取首个匹配的 group(1)（无捕获组则 group(0)）</li>
 *   <li>正则非法时按纯文本包含判断</li>
 * </ol>
 */
final class RegexFieldHelper {

    private RegexFieldHelper() {
    }

    /**
     * 在指定范围内按规则提取单字段值
     *
     * @param scope 单个列表项/线路/全集 HTML 片段
     * @param rule  字段规则
     * @return 提取值，失败返回空串
     */
    static String extract(String scope, String rule) {
        if (scope == null || scope.isEmpty() || rule == null || rule.isEmpty()) return "";
        rule = rule.trim();

        // || 备用规则
        if (rule.contains("||")) {
            for (String part : rule.split("\\|\\|")) {
                String val = extract(scope, part);
                if (!val.isEmpty()) return val;
            }
            return "";
        }

        // CSS 选择器
        if (CssRule.isCssRule(rule)) {
            return CssRule.extractByCss(scope, rule, 0);
        }

        // && 前后缀截取
        if (rule.contains("&&")) {
            String val = StringCutRule.applySecondCut(scope, rule);
            return val == null ? "" : clean(val);
        }

        // 正则提取
        try {
            Matcher m = Pattern.compile(rule, Pattern.DOTALL).matcher(scope);
            if (m.find()) {
                String val = m.groupCount() >= 1 ? m.group(1) : m.group(0);
                return val == null ? "" : clean(val);
            }
            return "";
        } catch (Exception e) {
            // 非法正则按纯文本包含判断
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

        // 回退到正则匹配
        try {
            Matcher m = Pattern.compile(arrayRule, Pattern.DOTALL).matcher(content);
            while (m.find()) {
                String item = m.groupCount() >= 1 ? m.group(1) : m.group(0);
                if (item != null && !item.isEmpty()) items.add(item);
            }
        } catch (Exception ignored) {
            // 非法正则
        }
        return items;
    }

    /**
     * 用 && 前后缀截取规则分割 HTML 内容，支持交叉重叠匹配。
     * <p>
     * 规则格式：{@code 前缀&&后缀}，例如 {@code <li class="hl-list-item"&&</li>}
     * 每次从当前位置查找前缀，再从该位置查找后缀，截取中间内容作为一项。
     */
    private static List<String> splitByCutRule(String content, String arrayRule) {
        List<String> items = new ArrayList<>();
        int idx = arrayRule.indexOf("&&");
        if (idx < 0) return items;
        String start = arrayRule.substring(0, idx).trim();
        String end = arrayRule.substring(idx + 2).trim();
        if (start.isEmpty() || end.isEmpty()) return items;

        int pos = 0;
        while (pos <= content.length()) {
            int startIdx = content.indexOf(start, pos);
            if (startIdx < 0) break;
            int afterStart = startIdx + start.length();
            int endIdx = content.indexOf(end, afterStart);
            if (endIdx < 0) break;
            items.add(content.substring(afterStart, endIdx));
            pos = endIdx + end.length();
        }
        return items;
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
