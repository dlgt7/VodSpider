package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.StringCutRule;

/**
 * CSS详情提取器
 * <p>
 * 使用 detail_* 平铺规则（CSS 选择器）从详情页提取字段：
 * 标题(list_name)、图片(list_pic)、剧情(detail_content)、导演(detail_director)、
 * 主演(detail_actor)、类型(detail_type)、年代(detail_year)、地区(detail_area)、
 * 状态/备注(detail_remarks)。
 * <p>
 * 提取顺序：
 * <ol>
 *   <li>detail_array：先截取详情区域（支持 CSS/正则/&& 规则）</li>
 *   <li>detail_twice：对截取后内容再二次截取（仅 CSS 规则生效）</li>
 *   <li>依次提取各字段，标题/图片沿用列表页键（XBPQ 惯例）</li>
 * </ol>
 * 字段提取统一走 {@link RegexFieldHelper}，完整支持 p: 简写、|| 备用规则、[替换]/[不含] 后处理器。
 * 提取失败回退 vinfo 传入值。
 *
 * @author CatVodSpider Team
 * @version 2.4
 */
public class CssDetailExtractor implements ExtractorFactory.DetailExtractor {

    @Override
    public JSONObject extract(String html, JSONObject config, JSONObject vinfo) throws Exception {
        JSONObject vod = new JSONObject();

        try {
            // 1. detail_array：先截取详情区域（支持 CSS/正则/&& 规则，与 RegexDetailExtractor 一致）
            String scope = html;
            String detailArrayRule = config.optString("detail_array", "");
            if (!detailArrayRule.isEmpty()) {
                scope = extractScope(scope, detailArrayRule);
            }

            // 2. detail_twice：CSS 二次截取（如 p:.hl-detail-info 再截取）
            String twiceRule = config.optString("detail_twice", "");
            if (!twiceRule.isEmpty()) {
                String cut = extractScope(scope, twiceRule);
                if (!cut.isEmpty()) scope = cut;
            }

            // 3. 依次提取详情各字段（统一走 RegexFieldHelper，支持 || 备用规则、[替换]/[不含] 后处理器）
            putByField(vod, scope, "vod_name", config.optString("list_name", ""));
            putByField(vod, scope, "vod_pic", config.optString("list_pic", ""));
            putByField(vod, scope, "vod_content", config.optString("detail_content", ""));
            putByField(vod, scope, "vod_director", config.optString("detail_director", ""));
            putByField(vod, scope, "vod_actor", config.optString("detail_actor", ""));
            putByField(vod, scope, "type_name", config.optString("detail_type", ""));
            putByField(vod, scope, "vod_year", config.optString("detail_year", ""));
            putByField(vod, scope, "vod_area", config.optString("detail_area", ""));
            putByField(vod, scope, "vod_remarks", config.optString("detail_remarks", ""));

            // 4. 回填 vod_id / vod_name / vod_pic（来自列表页信息，仅补缺不覆盖）
            if (vinfo != null) {
                fillIfMissing(vod, "vod_id", vinfo.optString("vod_id", ""));
                fillIfMissing(vod, "vod_name", vinfo.optString("vod_name", ""));
                fillIfMissing(vod, "vod_pic", vinfo.optString("vod_pic", ""));
            }
        } catch (Exception e) {
            SpiderDebug.log("CssDetailExtractor error: " + e.getMessage());
        }

        return vod;
    }

    /**
     * 从 HTML 中提取详情范围，支持 CSS 规则和正则/&& 规则。
     */
    private String extractScope(String html, String arrayRule) {
        if (CssRule.isCssRule(arrayRule)) {
            String cut = CssRule.cutRegion(html, arrayRule);
            return (!cut.isEmpty()) ? cut : html;
        }
        return StringCutRule.applySecondCut(html, arrayRule);
    }

    /**
     * 统一字段提取（走 RegexFieldHelper，支持 || / [替换] / [不含] / p: 简写）。
     */
    private void putByField(JSONObject vod, String scope, String field, String cssRule) {
        if (cssRule == null || cssRule.isEmpty()) return;
        String value = RegexFieldHelper.extract(scope, cssRule);
        if (!value.isEmpty()) {
            try { vod.put(field, value); } catch (Exception ignored) {}
        }
    }

    private void fillIfMissing(JSONObject vod, String key, String value) {
        if (value != null && !value.isEmpty() && !vod.has(key)) {
            try { vod.put(key, value); } catch (Exception ignored) {}
        }
    }
}
