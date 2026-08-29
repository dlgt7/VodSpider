package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.StringCutRule;

/**
 * 正则视频列表提取器
 * <p>
 * 使用 list_array 正则循环切分列表项，并在<b>单项内部</b>提取
 * list_name/list_id/list_pic/list_remarks 字段（避免全文字段串项）。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class RegexVideoListExtractor implements ExtractorFactory.VideoListExtractor {

    @Override
    public JSONArray extract(String html, JSONObject config) throws Exception {
        JSONArray videos = new JSONArray();

        try {
            String arrayRule = config.optString("list_array", "");
            if (arrayRule.isEmpty()) {
                return videos;
            }

            // CSS 规则自动转由 CSS 提取器处理
            if (CssRule.isCssRule(arrayRule)) {
                return new CssVideoListExtractor().extract(html, config);
            }

            // 二次截取
            String content = html;
            String twiceRule = config.optString("list_twice", "");
            if (!twiceRule.isEmpty()) {
                content = StringCutRule.applySecondCut(content, twiceRule);
            }

            // 未配置字段的默认规则（与写法说明一致）：skr2 等规则不配 标题/图片，
            // 依赖默认规则在条目内提取。alt 兜底覆盖 vodlist_thumb 型站点
            //（实测 skr2 列表项无 title 属性，标题在 alt= 上）
            String nameRule = config.optString("list_name", "");
            if (nameRule.isEmpty()) nameRule = "title=\"&&\"||alt=\"&&\"";
            String idRule = config.optString("list_id", "");
            if (idRule.isEmpty()) idRule = "href=\"&&\"";
            String picRule = config.optString("list_pic", "");
            if (picRule.isEmpty()) picRule = "src=\"&&\"||data-original=\"&&\"";
            String remarksRule = config.optString("list_remarks", "");
            String prefix = config.optString("list_prefix", "");
            String suffix = config.optString("list_suffix", "");

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
            SpiderDebug.log("RegexVideoListExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
