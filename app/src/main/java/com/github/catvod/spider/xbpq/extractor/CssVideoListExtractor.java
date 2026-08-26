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
 * 字段提取统一走 {@link RegexFieldHelper}，完整支持 p: 简写、|| 备用规则、[替换]/[不含] 后处理器。
 *
 * @author CatVodSpider Team
 * @version 2.2
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
            // 完整转换：stripPrefix → parseCssShortSyntax，确保 p: 简写正确解析
            String selector = CssRule.parseCssShortSyntax(CssRule.stripPrefix(containerRule));
            Elements containers = doc.select(selector);

            for (Element container : containers) {
                String itemHtml = container.outerHtml();
                // 统一走 RegexFieldHelper，支持 || 备用规则、[替换]/[不含] 后处理器、p: 简写
                String name = nameRule.isEmpty() ? "" : RegexFieldHelper.extract(itemHtml, nameRule);
                String id = idRule.isEmpty() ? "" : RegexFieldHelper.extract(itemHtml, idRule);
                if (name.isEmpty() && id.isEmpty()) continue;

                JSONObject video = new JSONObject();
                if (!name.isEmpty()) video.put("vod_name", name);
                if (!id.isEmpty()) video.put("vod_id", prefix + id + suffix);
                String pic = picRule.isEmpty() ? "" : RegexFieldHelper.extract(itemHtml, picRule);
                if (!pic.isEmpty()) video.put("vod_pic", pic);
                String remarks = remarksRule.isEmpty() ? "" : RegexFieldHelper.extract(itemHtml, remarksRule);
                if (!remarks.isEmpty()) video.put("vod_remarks", remarks);
                videos.put(video);
            }
        } catch (Exception e) {
            SpiderDebug.log("CssVideoListExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
