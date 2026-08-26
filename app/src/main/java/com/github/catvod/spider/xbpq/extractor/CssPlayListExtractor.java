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
 * CSS播放列表提取器
 * <p>
 * 两种模式：
 * <ul>
 *   <li>多线模式：from_array 选择器切分线路，每条线路<b>内部</b>用 from_title 提取线路名、
 *       url_array 提取集数（url_title/url_url）</li>
 *   <li>单线模式：无 from_array 时在全文用 play_array/url_array 提取集数，线路名固定为"播放"</li>
 * </ul>
 * 输出结构：[{name: 线路名, episodes: ["标题$链接", ...]}, ...]
 *
 * @author CatVodSpider Team
 * @version 2.2
 */
public class CssPlayListExtractor implements ExtractorFactory.PlayListExtractor {

    /**
     * 将 CSS 规则（含 p: 简写）转换为 Jsoup 可直接使用的选择器字符串。
     * <p>完整路径：stripPrefix → parseCssShortSyntax，与 extractByCss 内部处理保持一致。
     */
    private static String toJsoupSelector(String rawRule) {
        String stripped = CssRule.stripPrefix(rawRule);
        return CssRule.parseCssShortSyntax(stripped);
    }

    @Override
    public JSONArray extract(String html, JSONObject config, int sort) throws Exception {
        JSONArray playList = new JSONArray();

        try {
            String lineArrayRule = config.optString("from_array", "");
            Document doc = Jsoup.parse(html);

            if (!lineArrayRule.isEmpty()) {
                // 多线模式：每条线路内部提取集数
                String rawLineArrayRule = toJsoupSelector(lineArrayRule);
                Elements lines = doc.select(rawLineArrayRule);
                String lineTitleRule = config.optString("from_title", "");

                int lineIndex = 0;
                for (Element line : lines) {
                    lineIndex++;
                    // 提取线路名：支持 from_title CSS 规则、向上查找同级别按钮、默认"线路N"
                    String lineName = extractLineName(doc, line, lineIndex, lineTitleRule);

                    // 使用 outerHtml 作为范围，让内部规则（url_title/url_url）在正确作用域内提取
                    JSONArray episodes = extractEpisodes(line.outerHtml(), config);
                    if (episodes.length() == 0) continue;

                    JSONObject source = new JSONObject();
                    source.put("name", lineName);
                    source.put("episodes", episodes);
                    playList.put(source);
                }

                // 如果多线模式未提取到任何集数，尝试多容器分组模式
                if (playList.length() == 0) {
                    extractMultiContainerLines(doc, config, playList);
                }
            } else {
                // 单线模式：全文提取集数
                String playArrayRule = config.optString("play_array", "");
                if (!playArrayRule.isEmpty() && CssRule.isCssRule(playArrayRule)) {
                    // CSS 模式：只取第一个匹配的容器
                    String rawPlayArrayRule = toJsoupSelector(playArrayRule);
                    Elements firstOnly = doc.select(rawPlayArrayRule);
                    if (!firstOnly.isEmpty()) {
                        // 找到第一个父容器（ul），只在其内部提取
                        Element parentUl = firstOnly.first().parent();
                        if (parentUl != null) {
                            JSONArray episodes = extractEpisodes(parentUl.outerHtml(), config);
                            if (episodes.length() > 0) {
                                JSONObject source = new JSONObject();
                                source.put("name", "播放");
                                source.put("episodes", episodes);
                                playList.put(source);
                            }
                        }
                    }
                } else {
                    JSONArray episodes = extractEpisodes(html, config);
                    if (episodes.length() > 0) {
                        JSONObject source = new JSONObject();
                        source.put("name", "播放");
                        source.put("episodes", episodes);
                        playList.put(source);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("CssPlayListExtractor error: " + e.getMessage());
        }

        return playList;
    }

    /**
     * 在线路容器中尝试多种策略提取线路名：
     * 1. from_title 规则在当前线路元素内提取
     * 2. 向上查找包含线路按钮的父容器，按序号取对应按钮文本/alt属性
     *    （处理热剧TV网等线路按钮与播放列表分离的结构）
     * 3. 默认"线路N"
     */
    private String extractLineName(Document doc, Element line, int lineIndex, String lineTitleRule) {
        // 策略1：用 from_title 规则在线路元素内提取
        if (!lineTitleRule.isEmpty()) {
            String name = RegexFieldHelper.extract(line.outerHtml(), lineTitleRule);
            if (!name.isEmpty()) return name;
        }

        // 策略2：向上逐级查找，找到包含 .hl-tabs-btn 的父容器后按序号取按钮
        Element parent = line;
        for (int i = 0; i < 8; i++) {
            parent = parent.parent();
            if (parent == null) break;
            Elements buttons = parent.select("a.hl-tabs-btn, .hl-tabs-btn");
            if (!buttons.isEmpty()) {
                int btnIdx = lineIndex - 1;
                if (btnIdx >= 0 && btnIdx < buttons.size()) {
                    Element btn = buttons.get(btnIdx);
                    // 优先取 alt 属性（如 alt="线路1"），其次取 text 内容
                    String name = btn.attr("alt").trim();
                    if (name.isEmpty()) name = btn.text().trim();
                    if (!name.isEmpty()) return name;
                }
                // 找到按钮容器后不再继续向上
                break;
            }
        }

        return "线路" + lineIndex;
    }

    /**
     * 在范围内用 url_array（或 play_array）选择器提取集数（"标题$链接"列表）
     * <p>使用 RegexFieldHelper.extract 统一处理：支持 p: 简写、|| 备用规则、后处理器等。
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

        Document doc = Jsoup.parse(scope);
        String rawArrayRule = toJsoupSelector(arrayRule);
        Elements items = doc.select(rawArrayRule);

        for (Element item : items) {
            // 使用 item.outerHtml() 作为提取作用域，确保 url_title/url_url 中的 CSS 规则
            // 可以正确限定在当前 li 内；RegexFieldHelper 支持 p: 简写、|| 备用、[替换]/[不含] 后处理器
            String itemHtml = item.outerHtml();
            String title = titleRule.isEmpty() ? "" : RegexFieldHelper.extract(itemHtml, titleRule);
            String url = urlRule.isEmpty() ? "" : RegexFieldHelper.extract(itemHtml, urlRule);
            if (url.isEmpty()) continue;
            episodes.put((title.isEmpty() ? String.valueOf(episodes.length() + 1) : title)
                    + "$" + prefix + url + suffix);
        }
        return episodes;
    }

    /**
     * 多容器分组提取：支持 from_array 指向父容器（如 .hl-tabs-box），
     * 每个容器内用 play_array 提取集数，线路名从同级 or 上级获取。
     * 用于详情页中线路按钮与播放列表分离的结构。
     */
    private void extractMultiContainerLines(Document doc, JSONObject config, JSONArray playList) throws Exception {
        String lineArrayRule = config.optString("from_array", "");
        if (lineArrayRule.isEmpty()) return;

        String rawLineArrayRule = toJsoupSelector(lineArrayRule);
        Elements lineContainers = doc.select(rawLineArrayRule);
        if (lineContainers.isEmpty()) return;

        String lineTitleRule = config.optString("from_title", "");
        int lineIndex = 0;

        for (Element container : lineContainers) {
            lineIndex++;
            String lineName = extractLineName(doc, container, lineIndex, lineTitleRule);

            // 使用 container 的 outerHtml 作为提取范围
            String containerHtml = container.outerHtml();
            JSONArray episodes = extractEpisodes(containerHtml, config);
            if (episodes.length() == 0) continue;

            JSONObject source = new JSONObject();
            source.put("name", lineName);
            source.put("episodes", episodes);
            playList.put(source);
        }
    }
}
