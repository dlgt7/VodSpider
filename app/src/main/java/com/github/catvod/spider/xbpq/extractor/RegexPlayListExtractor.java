package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.StringCutRule;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 正则播放列表提取器
 * <p>
 * 两种模式：
 * <ul>
 *   <li>多线模式：from_array 切分线路，每条线路内 from_title 提取线路名、
 *       url_array 提取集数（url_title/url_url）</li>
 *   <li>单线模式：无 from_array 时在全文（或 play_twice 截取后）用
 *       play_array/url_array 提取集数，线路名固定为"播放"</li>
 * </ul>
 * 输出结构：[{name: 线路名, episodes: ["标题$链接", ...]}, ...]
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class RegexPlayListExtractor implements ExtractorFactory.PlayListExtractor {

    @Override
    public JSONArray extract(String html, JSONObject config, int sort) throws Exception {
        JSONArray playList = new JSONArray();

        try {
            String fromArrayRule = config.optString("from_array", "");
            if (!fromArrayRule.isEmpty()) {
                if (CssRule.isCssRule(fromArrayRule)) {
                    return new CssPlayListExtractor().extract(html, config, sort);
                }
                extractMultiLine(html, config, playList);
            } else {
                extractSingleLine(html, config, playList);
            }
        } catch (Exception e) {
            SpiderDebug.log("RegexPlayListExtractor error: " + e.getMessage());
        }

        return playList;
    }

    /**
     * 多线模式：from_array 逐线路提取
     */
    private void extractMultiLine(String html, JSONObject config, JSONArray playList) throws Exception {
        String content = html;
        String lineTwice = config.optString("line_twice", "");
        // 修复：CSS 形态的线路二次截取原先被当作字符串截取规则静默失败，统一分流
        if (!lineTwice.isEmpty()) {
            if (CssRule.isCssRule(lineTwice)) {
                String cut = CssRule.cutRegion(content, lineTwice);
                if (!cut.isEmpty()) content = cut;
            } else {
                content = StringCutRule.applySecondCut(content, lineTwice);
            }
        }

        String titleRule = config.optString("from_title", "");
        List<String> lines = RegexFieldHelper.splitItems(content, config.optString("from_array", ""));
        int lineIndex = 0;
        for (String line : lines) {
            String lineName = RegexFieldHelper.extract(line, titleRule);
            if (lineName.isEmpty()) lineName = "线路" + (++lineIndex);
            else lineIndex++;

            JSONArray episodes = extractEpisodes(line, config);
            if (episodes.length() == 0) continue;

            JSONObject source = new JSONObject();
            source.put("name", lineName);
            source.put("episodes", episodes);
            playList.put(source);
        }
    }

    /**
     * 单线模式：全文（或 play_twice 截取后）提取集数
     */
    private void extractSingleLine(String html, JSONObject config, JSONArray playList) throws Exception {
        String content = html;
        String playTwice = config.optString("play_twice", "");
        if (!playTwice.isEmpty()) {
            content = StringCutRule.applySecondCut(content, playTwice);
        }

        JSONArray episodes = extractEpisodes(content, config);
        if (episodes.length() == 0) return;

        JSONObject source = new JSONObject();
        source.put("name", "播放");
        source.put("episodes", episodes);
        playList.put(source);
    }

    /**
     * 在范围内提取集数（"标题$链接"列表）
     */
    private JSONArray extractEpisodes(String scope, JSONObject config) throws Exception {
        JSONArray episodes = new JSONArray();
        String arrayRule = config.optString("url_array", "");
        if (arrayRule.isEmpty()) arrayRule = config.optString("play_array", "");
        if (arrayRule.isEmpty()) return episodes;

        String titleRule = config.optString("url_title", "");
        String urlRule = config.optString("url_url", "");
        String prefix = config.optString("play_prefix", "");
        String suffix = config.optString("play_suffix", "");

        for (String item : splitByRule(scope, arrayRule)) {
            String title = RegexFieldHelper.extract(item, titleRule);
            String url = RegexFieldHelper.extract(item, urlRule);
            if (url.isEmpty()) continue;
            episodes.put((title.isEmpty() ? String.valueOf(episodes.length() + 1) : title)
                    + "$" + prefix + url + suffix);
        }
        return episodes;
    }

    /**
     * 按数组规则切分：CSS 规则用 Jsoup 选择器，否则按正则循环匹配
     */
    private List<String> splitByRule(String scope, String arrayRule) {
        List<String> items = new ArrayList<>();
        if (CssRule.isCssRule(arrayRule)) {
            try {
                Document doc = Jsoup.parse(scope);
                for (Element el : CssRule.selectWithAnd(doc, CssRule.stripPrefix(arrayRule))) {
                    items.add(el.outerHtml());
                }
            } catch (Exception ignored) {
                // 非法选择器
            }
            return items;
        }
        return RegexFieldHelper.splitItems(scope, arrayRule);
    }
}
