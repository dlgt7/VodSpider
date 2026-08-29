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

            // 未配置字段的默认规则（与正则提取器一致，alt 兜底 vodlist_thumb 型站点）
            String nameRule = config.optString("list_name", "");
            if (nameRule.isEmpty()) nameRule = "title=\"&&\"||alt=\"&&\"";
            String idRule = config.optString("list_id", "");
            if (idRule.isEmpty()) idRule = "href=\"&&\"";
            String picRule = config.optString("list_pic", "");
            if (picRule.isEmpty()) picRule = "src=\"&&\"||data-original=\"&&\"";
            String remarksRule = config.optString("list_remarks", "");
            String prefix = config.optString("list_prefix", "");
            String suffix = config.optString("list_suffix", "");

            // 修复：CSS 模式原先完全忽略 分类二次截取（list_twice），
            // 与正则模式行为不一致，导致配置了二次截取的 CSS 规则抓到整页无关条目。
            String twiceRule = config.optString("list_twice", "");
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
                // 性能修复：原实现对每个字段都 RegexFieldHelper.extract(itemHtml, ...)
                // → CssRule.extractByCss(String) 每次重新 Jsoup.parse(itemHtml)，
                // 一页 20 项 × 4 字段 = 80 次重复解析。现直接传入 container Element，
                // CSS 规则作用于元素子树零重复解析；正则/截取规则仍用 itemHtml 字符串。
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
            SpiderDebug.log("CssVideoListExtractor error: " + e.getMessage());
        }

        return videos;
    }
}
