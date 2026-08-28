package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONArray;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.StringCutRule;

/**
 * CSS搜索提取器
 * <p>
 * 使用 search_array 选择器切分搜索结果项，在单项内提取
 * search_name/search_id/search_pic/search_remarks 字段。
 * 字段提取统一走 {@link RegexFieldHelper}，完整支持 p: 简写、|| 备用规则、[替换]/[不含] 后处理器。
 *
 * @author CatVodSpider Team
 * @version 2.2
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

            // 修复：CSS 搜索模式原先忽略 搜索二次截取（search_twice），与正则模式不一致
            String twiceRule = config.optString("search_twice", "");
            if (!twiceRule.isEmpty()) {
                if (CssRule.isCssRule(twiceRule)) {
                    String cut = CssRule.cutRegion(html, twiceRule);
                    if (!cut.isEmpty()) html = cut;
                } else {
                    html = StringCutRule.applySecondCut(html, twiceRule);
                }
            }

            Document doc = Jsoup.parse(html);
            // 统一走 selectWithAnd：支持 ".stui-vodlist&&li"（容器&&条目，规格 §4.3 形态）
            // 与普通选择器两种写法；p: 简写在 selectWithAnd 内部转换
            Elements containers = CssRule.selectWithAnd(doc, CssRule.stripPrefix(containerRule));

            for (Element container : containers) {
                String itemHtml = container.outerHtml();
                // 性能修复：与 CssVideoListExtractor 一致，CSS 规则直接作用于 container Element，
                // 避免每字段重复 Jsoup.parse；正则/截取规则仍用 itemHtml 字符串。
                String name = nameRule.isEmpty() ? "" : RegexFieldHelper.extract(container, itemHtml, nameRule);
                String id = idRule.isEmpty() ? "" : RegexFieldHelper.extract(container, itemHtml, idRule);
                if (name.isEmpty() && id.isEmpty()) continue;

                JSONObject video = new JSONObject();
                if (!name.isEmpty()) video.put("vod_name", name);
                if (!id.isEmpty()) video.put("vod_id", prefix + id + suffix);
                String pic = picRule.isEmpty() ? "" : RegexFieldHelper.extract(container, itemHtml, picRule);
                if (!pic.isEmpty()) video.put("vod_pic", pic);
                String remarks = remarksRule.isEmpty() ? "" : RegexFieldHelper.extract(container, itemHtml, remarksRule);
                if (!remarks.isEmpty()) video.put("vod_remarks", remarks);
                videos.put(video);
            }
        } catch (Exception e) {
            SpiderDebug.log("CssSearchExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
