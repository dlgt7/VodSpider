package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONArray;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;

/**
 * CSS搜索提取器
 * <p>
 * 使用 search_array 选择器切分搜索结果项，在单项内提取
 * search_name/search_id/search_pic/search_remarks 字段。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class CssSearchExtractor implements ExtractorFactory.SearchExtractor {

    @Override
    public JSONArray extract(String html, JSONObject config) throws Exception {
        JSONArray videos = new JSONArray();

        try {
            String containerRule = config.optString("search_array", "");
            if (containerRule.isEmpty()) {
                return videos;
            }

            String nameRule = config.optString("search_name", "");
            String idRule = config.optString("search_id", "");
            String picRule = config.optString("search_pic", "");
            String remarksRule = config.optString("search_remarks", "");
            String prefix = config.optString("search_prefix", "");
            String suffix = config.optString("search_suffix", "");

            Document doc = Jsoup.parse(html);
            Elements containers = doc.select(CssRule.stripPrefix(containerRule));

            for (Element container : containers) {
                String itemHtml = container.outerHtml();
                String name = CssRule.extractByCss(itemHtml, nameRule, 0);
                String id = CssRule.extractByCss(itemHtml, idRule, 0);
                if (name.isEmpty() && id.isEmpty()) continue;

                JSONObject video = new JSONObject();
                if (!name.isEmpty()) video.put("vod_name", name);
                if (!id.isEmpty()) video.put("vod_id", prefix + id + suffix);
                String pic = CssRule.extractByCss(itemHtml, picRule, 0);
                if (!pic.isEmpty()) video.put("vod_pic", pic);
                String remarks = CssRule.extractByCss(itemHtml, remarksRule, 0);
                if (!remarks.isEmpty()) video.put("vod_remarks", remarks);
                videos.put(video);
            }
        } catch (Exception e) {
            SpiderDebug.log("CssSearchExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
