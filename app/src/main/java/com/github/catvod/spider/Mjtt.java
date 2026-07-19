package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 美剧天堂 Spider - HTML 解析型视频源
 * 支持 魔幻科幻/灵异惊悚/都市情感/犯罪历史/选秀综艺/动漫卡通 分类
 * 播放页通过 var now=unescape("...") 提取 m3u8 直链
 */
public class Mjtt extends Spider {

    private static final String SITE_URL = "https://www.meijutt.cc";
    private static final String UA = Util.CHROME;

    private static final Pattern NOW_VAR_PATTERN = Pattern.compile("var\\s+now\\s*=\\s*unescape\\(\"(.*?)\"\\)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\((\\d{4})\\)");

    private HashMap<String, String> getHeader() {
        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        return header;
    }

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        // No special init needed
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "魔幻科幻"));
        classes.add(new Class("2", "灵异惊悚"));
        classes.add(new Class("3", "都市情感"));
        classes.add(new Class("4", "犯罪历史"));
        classes.add(new Class("5", "选秀综艺"));
        classes.add(new Class("6", "动漫卡通"));

        Document doc = Jsoup.parse(OkHttp.string(SITE_URL, getHeader()));
        ArrayList<Vod> list = new ArrayList<>();

        // Parse homepage items from "最近连载" section
        Elements items = doc.select("div[class*=hot] li a[href*=/meijutt/]");
        for (Element a : items) {
            String href = a.attr("href");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) name = a.text();
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(href)) continue;

            String pic = "";
            Element img = a.select("img").first();
            if (img != null) {
                pic = img.attr("src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("data-original");
            }

            String vid = href;
            list.add(new Vod(vid, name, pic, ""));
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";

        String cateUrl;
        if ("1".equals(pg)) {
            cateUrl = SITE_URL + "/mjtt/" + tid + ".html";
        } else {
            cateUrl = SITE_URL + "/mjtt/" + tid + "-" + pg + ".html";
        }

        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        ArrayList<Vod> list = new ArrayList<>();

        // Parse category list items
        Elements links = doc.select(".list_20 a.B");
        for (Element a : links) {
            String href = a.attr("href");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(href)) continue;

            // Get remark from the sibling elements (update status)
            String remark = "";
            Element parentLi = a.parent();
            if (parentLi != null) {
                Element nextLi = parentLi.nextElementSibling();
                if (nextLi != null) {
                    String text = nextLi.text().trim();
                    // Extract update status like "至第2集", "本季终"
                    Matcher m = Pattern.compile("(至第\\d+集|本季终|全剧完结|预告|第\\d+集)").matcher(text);
                    if (m.find()) remark = m.group(1);
                }
            }

            list.add(new Vod(href, name, "", remark));
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = SITE_URL + vodId;

        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

        Vod vod = new Vod();
        vod.setVodId(vodId);

        // Title
        String name = doc.select("h1").text();
        vod.setVodName(name);

        // Year from title suffix like (2026)
        Element h1 = doc.select("h1").first();
        Matcher yearMatcher = h1 != null ? YEAR_PATTERN.matcher(h1.parent().text()) : null;
        if (yearMatcher != null && yearMatcher.find()) {
            vod.setVodYear(yearMatcher.group(1));
        }

        // Image - look for the main poster image
        Elements imgs = doc.select("img[src*=tvmi], img[src*=doubaocdn], img[data-original*=tvmi], img[data-original*=doubaocdn]");
        for (Element img : imgs) {
            String pic = img.attr("src");
            if (TextUtils.isEmpty(pic) || pic.startsWith("data:")) pic = img.attr("data-original");
            if (!TextUtils.isEmpty(pic) && !pic.startsWith("data:")) {
                vod.setVodPic(pic);
                break;
            }
        }

        // Metadata from detail text
        String detailText = doc.text();

        // Director
        String director = extractMeta(doc, "导演");
        if (!TextUtils.isEmpty(director)) vod.setVodDirector(director);

        // Actor
        String actor = extractMeta(doc, "主演");
        if (!TextUtils.isEmpty(actor)) vod.setVodActor(actor);

        // Area
        String area = extractMeta(doc, "地区");
        if (!TextUtils.isEmpty(area)) vod.setVodArea(area);

        // Content/Description
        Element contentEl = doc.select(".o_detail, .detail-sketch, [class*=desc], [class*=info]").first();
        if (contentEl != null) {
            String content = contentEl.text().trim();
            if (!TextUtils.isEmpty(content) && content.length() > 10) {
                vod.setVodContent(content);
            }
        }

        // Parse play sources and episodes
        // Source tabs: .from-tabs label (skip 迅雷云盘 and 百度网盘)
        Elements sourceLabels = doc.select(".from-tabs label");
        // Episode lists: .omlist_box7 ul.mn_list_li_movie
        Elements episodeLists = doc.select(".omlist_box7 ul.mn_list_li_movie");

        StringBuilder vodPlayFrom = new StringBuilder();
        StringBuilder vodPlayUrl = new StringBuilder();

        int sourceCount = 0;
        for (int i = 0; i < sourceLabels.size() && i < episodeLists.size(); i++) {
            String sourceName = sourceLabels.get(i).text();
            // Remove episode count like [10]
            sourceName = sourceName.replaceAll("\\[\\d+\\]", "").trim();

            // Skip cloud disk sources (no playable links)
            if (sourceName.contains("云盘") || sourceName.contains("网盘")) continue;

            if (sourceCount > 0) {
                vodPlayFrom.append("$$$");
                vodPlayUrl.append("$$$");
            }
            vodPlayFrom.append(sourceName);

            Elements episodes = episodeLists.get(i).select("li a");
            for (int j = 0; j < episodes.size(); j++) {
                if (j > 0) vodPlayUrl.append("#");
                Element ep = episodes.get(j);
                String epName = ep.text();
                String epUrl = ep.attr("href");
                vodPlayUrl.append(epName).append("$").append(epUrl);
            }
            sourceCount++;
        }

        vod.setVodPlayFrom(vodPlayFrom.toString());
        vod.setVodPlayUrl(vodPlayUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = SITE_URL + id;
        String html = OkHttp.string(playUrl, getHeader());

        // Extract m3u8 URL from: var now=unescape("encoded_url")
        Matcher matcher = NOW_VAR_PATTERN.matcher(html);
        if (matcher.find()) {
            String encoded = matcher.group(1);
            String m3u8Url = URLDecoder.decode(encoded, "UTF-8");
            return Result.get().url(m3u8Url).parse(0).string();
        }

        // Fallback: return the play page URL with parse=1 for sniffer
        return Result.get().url(playUrl).parse(1).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = SITE_URL + "/search.php?searchword=" + Uri.encode(key);
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));

        ArrayList<Vod> list = new ArrayList<>();
        // Search results may use similar structure to category list
        Elements links = doc.select(".list_20 a.B, a[href*=/meijutt/][title]");
        for (Element a : links) {
            String href = a.attr("href");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) name = a.text();
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(href)) continue;
            if (!href.contains("/meijutt/")) continue;

            String pic = "";
            Element img = a.select("img").first();
            if (img != null) {
                pic = img.attr("src");
                if (TextUtils.isEmpty(pic) || pic.startsWith("data:")) pic = img.attr("data-original");
            }

            list.add(new Vod(href, name, pic, ""));
        }

        return Result.string(list);
    }

    /**
     * Extract metadata value from detail page by label name
     */
    private String extractMeta(Document doc, String label) {
        // Try to find label in the format "label：value" or "label:value"
        Elements allText = doc.select("li, p, span, font");
        for (Element el : allText) {
            String text = el.text();
            if (text.contains(label) && text.contains("：")) {
                int idx = text.indexOf("：");
                if (idx > 0) {
                    String before = text.substring(0, idx);
                    if (before.contains(label)) {
                        String value = text.substring(idx + 1).trim();
                        // Clean up "更多>>" suffix
                        value = value.replace("更多>>", "").trim();
                        if (!TextUtils.isEmpty(value)) return value;
                    }
                }
            }
        }
        return "";
    }
}
