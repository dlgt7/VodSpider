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
     * 按数组规则循环切分出所有列表项
     *
     * @return 列表项集合（优先捕获组1），规则非法返回空
     */
    static List<String> splitItems(String content, String arrayRule) {
        List<String> items = new ArrayList<>();
        if (content == null || content.isEmpty() || arrayRule == null || arrayRule.isEmpty()) return items;
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
     * 清理字段值（去标签/实体/多余空白）
     */
    static String clean(String value) {
        if (value == null) return "";
        String cleaned = HtmlNodeHelper.cleanText(value);
        return cleaned == null ? "" : cleaned.trim();
    }
}
