package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 枫林网 (imaple.tv) Spider - MacCMS 混合型视频源
 * Ajax API 提供列表/搜索数据，HTML 解析提取详情/播放信息
 * 播放页 player_aaaa JSON 包含 m3u8 直链
 */
public class Flys extends Spider {

    private static final String SITE_URL = "https://imaple.tv";
    private static final String API_URL = SITE_URL + "/index.php/ajax/data";
    private static final String UA = Util.CHROME;

    private static final Pattern PLAYER_JSON_PATTERN = Pattern.compile("player_aaaa\\s*=\\s*\\{");

    private HashMap<String, String> getHeader() {
        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        header.put("Referer", SITE_URL + "/");
        return header;
    }

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("6", "动作片"));
        classes.add(new Class("7", "喜剧片"));
        classes.add(new Class("8", "爱情片"));
        classes.add(new Class("9", "科幻片"));
        classes.add(new Class("10", "恐怖片"));
        classes.add(new Class("11", "剧情片"));
        classes.add(new Class("12", "战争片"));
        classes.add(new Class("13", "大陆剧"));
        classes.add(new Class("15", "韩剧"));
        classes.add(new Class("16", "欧美剧"));
        classes.add(new Class("20", "台剧"));
        classes.add(new Class("21", "港剧"));
        classes.add(new Class("22", "日剧"));
        classes.add(new Class("37", "泰剧"));
        classes.add(new Class("77", "海外剧"));
        classes.add(new Class("23", "大陆综艺"));
        classes.add(new Class("24", "港台综艺"));
        classes.add(new Class("25", "日韩综艺"));
        classes.add(new Class("26", "欧美综艺"));
        classes.add(new Class("78", "海外综艺"));
        classes.add(new Class("27", "大陆动漫"));
        classes.add(new Class("28", "日韩动漫"));
        classes.add(new Class("29", "欧美动漫"));
        classes.add(new Class("30", "港台动漫"));
        classes.add(new Class("35", "动漫电影"));
        classes.add(new Class("59", "伦理片"));

        ArrayList<Vod> list = new ArrayList<>();
        String json = OkHttp.string(API_URL + "?mid=1&pg=1", getHeader());
        JSONObject response = new JSONObject(json);
        JSONArray vodArray = response.optJSONArray("list");
        if (vodArray != null) {
            for (int i = 0; i < vodArray.length() && list.size() < 20; i++) {
                JSONObject item = vodArray.optJSONObject(i);
                if (item == null) continue;
                Vod vod = parseVodItem(item);
                if (vod != null) list.add(vod);
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";

        String url = API_URL + "?mid=1&type=" + tid + "&pg=" + pg;
        String json = OkHttp.string(url, getHeader());
        JSONObject response = new JSONObject(json);

        ArrayList<Vod> list = new ArrayList<>();
        JSONArray vodArray = response.optJSONArray("list");
        if (vodArray != null) {
            for (int i = 0; i < vodArray.length(); i++) {
                JSONObject item = vodArray.optJSONObject(i);
                if (item == null) continue;
                Vod vod = parseVodItem(item);
                if (vod != null) list.add(vod);
            }
        }

        int currentPage = Integer.parseInt(pg);
        int pageCount = response.optInt("pagecount", 1);
        return Result.string(currentPage, pageCount, list.size(), pageCount, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = SITE_URL + "/voddetail/" + vodId + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

        Vod vod = new Vod();
        vod.setVodId(vodId);

        // Title
        String name = doc.select("h1").text();
        vod.setVodName(name);

        // Image
        Element img = doc.select("img[class*=lazy]").first();
        if (img == null) img = doc.select(".myui-content__thumb img").first();
        if (img != null) {
            String pic = img.attr("data-original");
            if (TextUtils.isEmpty(pic) || pic.startsWith("data:")) pic = img.attr("src");
            if (!TextUtils.isEmpty(pic) && !pic.startsWith("data:")) vod.setVodPic(pic);
        }

        // Metadata from detail info blocks
        String detailText = doc.text();
        vod.setVodDirector(extractMeta(doc, "導演"));
        vod.setVodActor(extractMeta(doc, "主演"));
        vod.setVodArea(extractMeta(doc, "地區"));

        // Year from "年份" label or title suffix
        String year = extractMeta(doc, "年份");
        if (TextUtils.isEmpty(year)) {
            Matcher ym = Pattern.compile("\\((\\d{4})\\)").matcher(name);
            if (ym.find()) year = ym.group(1);
        }
        if (!TextUtils.isEmpty(year)) vod.setVodYear(year);

        // Description
        Element contentEl = doc.select(".desc, .sketch, [class*=content], [class*=detail]").first();
        if (contentEl != null) {
            String content = contentEl.text().trim();
            if (content.length() > 10) vod.setVodContent(content);
        }

        // Parse play sources and episodes
        Elements sourceLinks = doc.select("a[href^=#playlist]");
        Elements episodeContainers = doc.select("[id^=playlist]");

        StringBuilder vodPlayFrom = new StringBuilder();
        StringBuilder vodPlayUrl = new StringBuilder();

        int sourceCount = 0;
        for (Element sourceLink : sourceLinks) {
            String sourceName = sourceLink.text().trim();
            String href = sourceLink.attr("href");
            if (TextUtils.isEmpty(href) || !href.startsWith("#playlist")) continue;

            String playlistId = href.substring(1); // remove #
            Element playlist = doc.select("#" + playlistId).first();
            if (playlist == null) continue;

            if (sourceCount > 0) {
                vodPlayFrom.append("$$$");
                vodPlayUrl.append("$$$");
            }
            vodPlayFrom.append(sourceName);

            Elements episodes = playlist.select("a[href*=/vodplay/]");
            for (int j = 0; j < episodes.size(); j++) {
                if (j > 0) vodPlayUrl.append("#");
                Element ep = episodes.get(j);
                String epName = ep.text().trim();
                String epHref = ep.attr("href");
                vodPlayUrl.append(epName).append("$").append(epHref);
            }
            sourceCount++;
        }

        vod.setVodPlayFrom(vodPlayFrom.toString());
        vod.setVodPlayUrl(vodPlayUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id is the episode URL like /vodplay/570950-1-1.html
        String playUrl = SITE_URL + id;
        String html = OkHttp.string(playUrl, getHeader());

        // Extract m3u8 URL from player_aaaa JSON: "url":"https://..."
        // Use balanced-brace parsing to find the JSON, then extract url field
        String videoUrl = extractPlayerUrl(html);

        if (!TextUtils.isEmpty(videoUrl)) {
            // Determine if parse is needed (non-m3u8 needs sniffer)
            int parse = videoUrl.contains(".m3u8") ? 0 : 1;
            return Result.get().url(videoUrl).parse(parse).string();
        }

        // Fallback: return play page URL with parse=1 for WebView sniffer
        return Result.get().url(playUrl).parse(1).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = SITE_URL + "/vodsearch/" + Uri.encode(key) + "------------.html";
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));

        ArrayList<Vod> list = new ArrayList<>();
        // Search results in MacCMS v10 template: .myui-vodlist__box or .stui-vodlist__box
        Elements items = doc.select(".myui-vodlist__box a[href*=/voddetail/], a[href*=/voddetail/][title]");
        for (Element a : items) {
            String href = a.attr("href");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) name = a.text();
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(href)) continue;

            // Extract vod_id from /voddetail/570950.html
            Matcher m = Pattern.compile("/voddetail/(\\d+)\\.html").matcher(href);
            if (!m.find()) continue;
            String vid = m.group(1);

            String pic = "";
            Element img = a.select("img").first();
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic) || pic.startsWith("data:")) pic = img.attr("src");
            }

            String remark = "";
            Element picSpan = a.select("span.pic-text").first();
            if (picSpan != null) remark = picSpan.text();

            list.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(list);
    }

    /**
     * Parse a single vod item from Ajax API JSON
     */
    private Vod parseVodItem(JSONObject item) {
        String id = String.valueOf(item.optInt("vod_id", 0));
        String name = item.optString("vod_name");
        String pic = item.optString("vod_pic");
        String remark = item.optString("vod_remarks");

        if (TextUtils.isEmpty(id) || "0".equals(id) || TextUtils.isEmpty(name)) return null;

        return new Vod(id, name, pic, remark);
    }

    /**
     * Extract video URL from player_aaaa JSON in play page HTML
     * Uses balanced-brace counting to locate the full JSON object
     */
    private String extractPlayerUrl(String html) {
        Matcher m = PLAYER_JSON_PATTERN.matcher(html);
        if (!m.find()) return "";

        int start = m.end() - 1; // position of opening {
        int depth = 0;
        int end = start;

        for (int i = start; i < Math.min(start + 50000, html.length()); i++) {
            char c = html.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i + 1;
                    break;
                }
            }
        }

        if (depth != 0) return "";

        try {
            JSONObject playerJson = new JSONObject(html.substring(start, end));
            return playerJson.optString("url", "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extract metadata value from detail page by label
     */
    private String extractMeta(Document doc, String label) {
        Elements allLinks = doc.select("a[href*=" + label + "]");
        for (Element a : allLinks) {
            String text = a.text().trim();
            if (!TextUtils.isEmpty(text) && !text.equals(label)) return text;
        }

        // Fallback: search in text content
        Elements spans = doc.select("span, p, li");
        for (Element el : spans) {
            String text = el.text();
            if (text.contains(label) && (text.contains("：") || text.contains(":"))) {
                int idx = Math.max(text.indexOf("："), text.indexOf(":"));
                if (idx > 0) {
                    String before = text.substring(0, idx);
                    if (before.contains(label)) {
                        String value = text.substring(idx + 1).trim();
                        if (!TextUtils.isEmpty(value)) return value;
                    }
                }
            }
        }
        return "";
    }
}
