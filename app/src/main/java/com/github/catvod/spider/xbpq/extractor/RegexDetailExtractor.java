package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.StringCutRule;

/**
 * 正则详情提取器
 * <p>
 * 使用 detail_* 平铺规则在详情页（或 detail_twice 截取区域）提取字段：
 * 图片(list_pic)、简介/剧情(detail_content)、导演(detail_director)、主演(detail_actor)、
 * 类型(detail_type)、年代(detail_year)、地区(detail_area)、状态/备注(detail_remarks)。
 * 标题沿用列表页传入的 vod_name（XBPQ 惯例）。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class RegexDetailExtractor implements ExtractorFactory.DetailExtractor {

    @Override
    public JSONObject extract(String html, JSONObject config, JSONObject vinfo) throws Exception {
        JSONObject vod = new JSONObject();

        try {
            // 详情二次截取
            String content = html;
            String twiceRule = config.optString("detail_twice", "");
            if (!twiceRule.isEmpty()) {
                content = StringCutRule.applySecondCut(content, twiceRule);
            }

            // 详情页字段（平铺键，标题/图片沿用列表页键）
            putIfFound(vod, "vod_name", RegexFieldHelper.extract(content, config.optString("list_name", "")));
            putIfFound(vod, "vod_pic", RegexFieldHelper.extract(content, config.optString("list_pic", "")));
            putIfFound(vod, "vod_content", RegexFieldHelper.extract(content, config.optString("detail_content", "")));
            putIfFound(vod, "vod_director", RegexFieldHelper.extract(content, config.optString("detail_director", "")));
            putIfFound(vod, "vod_actor", RegexFieldHelper.extract(content, config.optString("detail_actor", "")));
            putIfFound(vod, "type_name", RegexFieldHelper.extract(content, config.optString("detail_type", "")));
            putIfFound(vod, "vod_year", RegexFieldHelper.extract(content, config.optString("detail_year", "")));
            putIfFound(vod, "vod_area", RegexFieldHelper.extract(content, config.optString("detail_area", "")));
            putIfFound(vod, "vod_remarks", RegexFieldHelper.extract(content, config.optString("detail_remarks", "")));

            // 回填 vod_id / vod_name / vod_pic（来自列表页信息，仅补缺不覆盖，
            // 与 CssDetailExtractor 行为一致；旧实现强覆盖 vod_name 会丢失详情页提取结果）
            if (vinfo != null) {
                fillIfMissing(vod, "vod_id", vinfo.optString("vod_id", ""));
                fillIfMissing(vod, "vod_name", vinfo.optString("vod_name", ""));
                fillIfMissing(vod, "vod_pic", vinfo.optString("vod_pic", ""));
            }
        } catch (Exception e) {
            SpiderDebug.log("RegexDetailExtractor error: " + e.getMessage());
        }

        return vod;
    }

    private void putIfFound(JSONObject vod, String key, String value) {
        if (value != null && !value.isEmpty()) {
            try { vod.put(key, value); } catch (Exception ignored) {}
        }
    }

    private void fillIfMissing(JSONObject vod, String key, String value) {
        if (value != null && !value.isEmpty() && !vod.has(key)) {
            try { vod.put(key, value); } catch (Exception ignored) {}
        }
    }
}
