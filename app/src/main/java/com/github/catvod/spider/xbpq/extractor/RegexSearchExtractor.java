package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.StringCutRule;

/**
 * 正则搜索提取器
 * <p>
 * 使用 search_array 正则循环切分搜索结果项，并在单项内部提取
 * search_name/search_id/search_pic/search_remarks 字段。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class RegexSearchExtractor implements ExtractorFactory.SearchExtractor {

    @Override
    public JSONArray extract(String html, JSONObject config) throws Exception {
        JSONArray videos = new JSONArray();

        try {
            String arrayRule = config.optString("search_array", "");
            if (arrayRule.isEmpty()) {
                return videos;
            }

            // CSS 规则自动转由 CSS 提取器处理
            if (CssRule.isCssRule(arrayRule)) {
                return new CssSearchExtractor().extract(html, config);
            }

            // 二次截取
            String content = html;
            String twiceRule = config.optString("search_twice", "");
            if (!twiceRule.isEmpty()) {
                content = StringCutRule.applySecondCut(content, twiceRule);
            }

            String nameRule = config.optString("search_name", "");
            String idRule = config.optString("search_id", "");
            String picRule = config.optString("search_pic", "");
            String remarksRule = config.optString("search_remarks", "");
            String prefix = config.optString("search_prefix", "");
            String suffix = config.optString("search_suffix", "");

            for (String item : RegexFieldHelper.splitItems(content, arrayRule)) {
                String name = RegexFieldHelper.extract(item, nameRule);
                String id = RegexFieldHelper.extract(item, idRule);
                if (name.isEmpty() && id.isEmpty()) continue;

                JSONObject video = new JSONObject();
                if (!name.isEmpty()) video.put("vod_name", name);
                if (!id.isEmpty()) video.put("vod_id", prefix + id + suffix);
                String pic = RegexFieldHelper.extract(item, picRule);
                if (!pic.isEmpty()) video.put("vod_pic", pic);
                String remarks = RegexFieldHelper.extract(item, remarksRule);
                if (!remarks.isEmpty()) video.put("vod_remarks", remarks);
                videos.put(video);
            }
        } catch (Exception e) {
            SpiderDebug.log("RegexSearchExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
