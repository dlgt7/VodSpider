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
 * 与原版 XBPQ 对齐的三种结构（写法说明：播放数组=集数列表所在容器）：
 * <ul>
 *   <li>块内包含模式：from_array 切出的线路块内部即含剧集容器
 *       （hl-模板等），逐块提取线路名 + 剧集</li>
 *   <li>下标配对模式：线路按钮块（from_array）与剧集容器（play_array）
 *       是分离的兄弟结构（stui/旺旺影视等），按下标配对：线路 i ↔ 容器 i。
 *       仅当块内包含模式整体提取不到剧集时启用</li>
 *   <li>单线/多容器模式：无 from_array 时按 play_array 切出剧集容器，
 *       每个容器一条线路；容器规则未命中时整页兜底（如 foxjun 页面无
 *       &lt;tbody&gt;，直接按播放列表 [包含:magnet] 过滤出磁力集数）</li>
 * </ul>
 * 输出结构：[{name: 线路名, episodes: ["标题$链接", ...]}, ...]
 *
 * @author CatVodSpider Team
 * @version 2.3
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
                extractWithLines(html, config, playList);
            } else {
                extractSingleLine(html, config, playList);
            }
        } catch (Exception e) {
            SpiderDebug.log("RegexPlayListExtractor error: " + e.getMessage());
        }

        return playList;
    }

    /** 应用二次截取（支持 CSS 形态与尾部 [替换:] 后处理器） */
    private static String applyCut(String content, String twiceRule) {
        if (content == null || twiceRule == null || twiceRule.isEmpty()) return content;
        if (CssRule.isCssRule(twiceRule)) {
            String cut = CssRule.cutRegion(content, twiceRule);
            return cut.isEmpty() ? content : cut;
        }
        return StringCutRule.applySecondCut(content, twiceRule);
    }

    /**
     * 多线模式：优先线路块内提取；整体为空时回退"线路名块 × 剧集容器"下标配对。
     */
    private void extractWithLines(String html, JSONObject config, JSONArray playList) throws Exception {
        String content = applyCut(html, config.optString("line_twice", ""));
        String titleRule = config.optString("from_title", "");
        List<String> nameBlocks = RegexFieldHelper.splitItems(content, config.optString("from_array", ""));

        // 模式1：每个线路块内部直接提取剧集（块内含剧集容器）
        int lineIndex = 0;
        for (String line : nameBlocks) {
            String lineName = extractLineName(line, titleRule, ++lineIndex);
            JSONArray episodes = extractEpisodes(line, config);
            if (episodes.length() == 0) continue;
            addLine(playList, lineName, episodes);
        }
        if (playList.length() > 0) return;

        // 模式2：线路按钮与剧集容器分离（stui/旺旺影视等），按下标配对。
        // 剧集容器在 播放二次截取 后的区域上切分（旺旺影视的 [替换:] 分词依赖此顺序）
        String playContent = applyCut(html, config.optString("play_twice", ""));
        String playArrayRule = config.optString("play_array", "");
        if (playArrayRule.isEmpty()) return;
        List<String> regions = RegexFieldHelper.splitItems(playContent, playArrayRule);
        for (int i = 0; i < regions.size(); i++) {
            String lineName = (i < nameBlocks.size())
                    ? extractLineName(nameBlocks.get(i), titleRule, i + 1)
                    : "线路" + (i + 1);
            JSONArray episodes = extractEpisodes(regions.get(i), config);
            if (episodes.length() == 0) continue;
            addLine(playList, lineName, episodes);
        }
    }

    /**
     * 单线模式：无 from_array。
     * <ul>
     *   <li>只配 播放数组 未配 播放列表：播放数组即条目规则（旧语义），整页切分单线输出</li>
     *   <li>配置了 播放列表：播放数组作为剧集容器，命中多个容器时每个容器一条线路；
     *       未命中任何容器时整页兜底（foxjun 详情页无 &lt;tbody&gt; 的场景）</li>
     * </ul>
     */
    private void extractSingleLine(String html, JSONObject config, JSONArray playList) throws Exception {
        String content = applyCut(html, config.optString("play_twice", ""));

        String playArrayRule = config.optString("play_array", "");
        String urlArrayRule = config.optString("url_array", "");

        // 旧语义兜底：播放数组直接当条目规则（规则未配播放列表）
        if (urlArrayRule.isEmpty() && !playArrayRule.isEmpty()) {
            JSONArray episodes = extractEpisodes(content, config);
            if (episodes.length() > 0) addLine(playList, "播放", episodes);
            return;
        }

        List<String> regions = new ArrayList<>();
        if (!playArrayRule.isEmpty()) {
            regions = RegexFieldHelper.splitItems(content, playArrayRule);
        }
        if (regions.isEmpty()) {
            // 容器规则未命中（页面结构与规则不符）→ 整页作为一个容器兜底，
            // 仍可按 播放列表（含 [包含:] 过滤）提取出磁力/播放链接
            regions.add(content);
        }

        if (regions.size() == 1) {
            JSONArray episodes = extractEpisodes(regions.get(0), config);
            if (episodes.length() > 0) addLine(playList, "播放", episodes);
            return;
        }
        for (int i = 0; i < regions.size(); i++) {
            JSONArray episodes = extractEpisodes(regions.get(i), config);
            if (episodes.length() == 0) continue;
            addLine(playList, "线路" + (i + 1), episodes);
        }
    }

    /** 线路名提取：空规则回退"线路N"（"+"前缀拼接由 RegexFieldHelper 统一处理） */
    private static String extractLineName(String block, String titleRule, int index) {
        if (titleRule != null && !titleRule.isEmpty()) {
            String name = RegexFieldHelper.extract(block, titleRule);
            if (!name.isEmpty()) return name;
        }
        return "线路" + index;
    }

    private static void addLine(JSONArray playList, String name, JSONArray episodes) throws Exception {
        JSONObject source = new JSONObject();
        source.put("name", name);
        source.put("episodes", episodes);
        playList.put(source);
    }

    /**
     * 在范围内提取集数（"标题$链接"列表）。
     * <p>条目优先按 播放列表（url_array）切分；未配置时回退按 播放数组（play_array）
     * 切分（部分规则直接把容器规则当条目规则用）。播放链接（url_url）未配置时
     * 默认 {@code href="&&"}（写法说明默认值，skr2 等规则不配播放链接即依赖此默认）。</p>
     */
    private JSONArray extractEpisodes(String scope, JSONObject config) throws Exception {
        JSONArray episodes = new JSONArray();
        String arrayRule = config.optString("url_array", "");
        if (arrayRule.isEmpty()) arrayRule = config.optString("play_array", "");
        if (arrayRule.isEmpty()) return episodes;

        String titleRule = config.optString("url_title", "");
        String urlRule = config.optString("url_url", "");
        if (urlRule.isEmpty()) urlRule = "href=\"&&\"";
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
