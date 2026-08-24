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
 * CSS视频列表提取器
 * <p>
 * 使用 list_array 选择器切分列表项，在单项内提取
 * list_name/list_id/list_pic/list_remarks 字段，支持 list_prefix/list_suffix 包装。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class CssVideoListExtractor implements ExtractorFactory.VideoListExtractor {

    @Override
    public JSONArray extract(String html, JSONObject config) throws Exception {
        JSONArray videos = new JSONArray();

        try {
            String containerRule = config.optString("list_array", "");
            if (containerRule.isEmpty()) {
                return videos;
            }

            String nameRule = config.optString("list_name", "");
            String idRule = config.optString("list_id", "");
            String picRule = config.optString("list_pic", "");
            String remarksRule = config.optString("list_remarks", "");
            String prefix = config.optString("list_prefix", "");
            String suffix = config.optString("list_suffix", "");

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
            SpiderDebug.log("CssVideoListExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
