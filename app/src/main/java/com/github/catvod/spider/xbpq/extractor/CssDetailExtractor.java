package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;

/**
 * CSS详情提取器
 * <p>
 * 使用 detail_* 平铺规则（CSS 选择器）从详情页提取字段：
 * 标题(list_name)、图片(list_pic)、剧情(detail_content)、导演(detail_director)、
 * 主演(detail_actor)、类型(detail_type)、年代(detail_year)、地区(detail_area)、
 * 状态/备注(detail_remarks)。detail_twice 为 CSS 规则时先截取区域。
 * 标题/图片沿用列表页键（XBPQ 惯例），提取失败回退 vinfo 传入值。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class CssDetailExtractor implements ExtractorFactory.DetailExtractor {

    @Override
    public JSONObject extract(String html, JSONObject config, JSONObject vinfo) throws Exception {
        JSONObject vod = new JSONObject();

        try {
            // 详情二次截取（仅 CSS 规则生效）
            String scope = html;
            String twiceRule = config.optString("detail_twice", "");
            if (CssRule.isCssRule(twiceRule)) {
                String cut = CssRule.cutRegion(scope, twiceRule);
                if (cut != null && !cut.isEmpty()) scope = cut;
            }

            putByCss(vod, scope, "vod_name", config.optString("list_name", ""));
            putByCss(vod, scope, "vod_pic", config.optString("list_pic", ""));
            putByCss(vod, scope, "vod_content", config.optString("detail_content", ""));
            putByCss(vod, scope, "vod_director", config.optString("detail_director", ""));
            putByCss(vod, scope, "vod_actor", config.optString("detail_actor", ""));
            putByCss(vod, scope, "type_name", config.optString("detail_type", ""));
            putByCss(vod, scope, "vod_year", config.optString("detail_year", ""));
            putByCss(vod, scope, "vod_area", config.optString("detail_area", ""));
            putByCss(vod, scope, "vod_remarks", config.optString("detail_remarks", ""));

            // 回填 vod_id / vod_name / vod_pic（来自列表页信息，仅补缺）
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

    private void putByCss(JSONObject vod, String scope, String field, String cssRule) {
        if (cssRule == null || cssRule.isEmpty()) return;
        String value = CssRule.extractByCss(scope, cssRule, 0);
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
