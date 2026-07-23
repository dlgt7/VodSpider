package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 立播·影院 Spider
 * 站点发布页: https://libviogroup.github.io/ / https://www.libvio.app/
 * 推荐域名: libvio.host / www.libvios.com / www.libhd.com / libviobd.com
 * MacCMS v10 非标站点，播放页 player_aaaa 含直链
 */
public class Libvio extends Spider {

    private static final String DEFAULT_HOST = "https://libviobd.com";

    private static final Pattern PLAYER_AAAA_PATTERN = Pattern.compile("var\\s+player_aaaa\\s*=\\s*(\\{.*?\\})", Pattern.DOTALL);
    private static final Pattern VOD_ID_PATTERN = Pattern.compile("/detail/(\\d+)\\.html");
    private static final Pattern PLAY_LINK_PATTERN = Pattern.compile("/w/(\\d+)-(\\d+)-(\\d+)\\.html");
    private static final Pattern PAGE_COUNT_PATTERN = Pattern.compile(">(\\d+)/(\\d+)<");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");

    private String siteUrl = DEFAULT_HOST;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (!TextUtils.isEmpty(extend)) {
            String ext = extend.trim();
            if (ext.startsWith("{")) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(ext);
                    String host = json.optString("host", "");
                    if (!host.isEmpty()) siteUrl = host;
                } catch (Exception ignored) {
                }
            } else if (ext.startsWith("http")) {
                siteUrl = ext;
            }
        }
        while (siteUrl.endsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "剧集"));
        classes.add(new Class("4", "番剧"));
        classes.add(new Class("15", "日韩"));
        classes.add(new Class("16", "欧美"));
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<Vod> list = parseList(doc);
        if (filter) {
            return Result.string(classes, list, buildFilters());
        }
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        return Result.string(parseList(doc));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        String area = extend != null ? URLEncoder.encode(extend.getOrDefault("area", ""), "UTF-8") : "";
        String by = extend != null ? extend.getOrDefault("by", "") : "";
        String cls = extend != null ? extend.getOrDefault("class", "") : "";
        String lang = extend != null ? URLEncoder.encode(extend.getOrDefault("lang", ""), "UTF-8") : "";
        String year = extend != null ? extend.getOrDefault("year", "") : "";
        // Format: /show/{tid}-{area}-{by}-{class}-{lang}-{letter}---{pg}---{year}.html (12 fields, 11 dashes)
        String url = siteUrl + "/show/" + tid + "-" + area + "-" + by + "-" + cls + "-" + lang + "----" + page + "---" + year + ".html";
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseList(doc);
        int pageCount = parsePageCount(html);
        int total = pageCount > 0 ? pageCount * 20 : list.size();
        int limit = list.size() > 0 ? list.size() : 20;
        return Result.string(page, pageCount, limit, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String detailUrl = siteUrl + "/detail/" + vid + ".html";
        String html = OkHttp.string(detailUrl, getHeader());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vid);

        Element titleEl = doc.selectFirst("h1");
        if (titleEl == null) titleEl = doc.selectFirst("h2");
        if (titleEl != null) vod.setVodName(titleEl.text().trim());

        Element img = doc.selectFirst("img[data-original]");
        if (img == null) img = doc.selectFirst(".pic img");
        if (img == null) img = doc.selectFirst("img.vod_pic");
        if (img != null) {
            String pic = img.attr("data-original");
            if (pic.isEmpty()) pic = img.attr("data-src");
            if (pic.isEmpty()) pic = img.attr("src");
            if (!pic.isEmpty() && !pic.contains("favicon") && !pic.contains("icon")) {
                vod.setVodPic(fixUrl(pic));
            }
        }

        String pageText = doc.body() != null ? doc.body().text() : "";
        for (Element meta : doc.select(".vod-meta .meta-item")) {
            String text = meta.text().trim();
            if (text.startsWith("主演：")) {
                vod.setVodActor(text.substring(3).trim());
            } else if (text.startsWith("导演：")) {
                vod.setVodDirector(text.substring(3).trim());
            }
        }
        Element desc = doc.selectFirst(".vod-desc .detail-content");
        if (desc == null) desc = doc.selectFirst(".vod-desc .detail-sketch");
        if (desc != null) vod.setVodContent(desc.text().trim());
        List<Element> metaItems = doc.select(".vod-meta .meta-item");
        if (metaItems.size() >= 2) vod.setVodArea(metaItems.get(1).text().trim());
        Matcher yearMatcher = YEAR_PATTERN.matcher(pageText);
        if (yearMatcher.find()) vod.setVodYear(yearMatcher.group(1));

        Map<String, List<String[]>> sourceMap = new LinkedHashMap<>();
        for (Element a : doc.select(".stui-content__playlist a[href*=/w/]")) {
            String href = a.attr("href");
            Matcher m = PLAY_LINK_PATTERN.matcher(href);
            if (!m.find()) continue;
            if (!m.group(1).equals(vid)) continue;
            String sid = m.group(2);
            String epName = a.text().trim();
            if (epName.isEmpty()) continue;
            sourceMap.computeIfAbsent(sid, k -> new ArrayList<>()).add(new String[]{epName, href});
        }

        List<String> h3Names = new ArrayList<>();
        for (Element h3 : doc.select("h3")) {
            String text = h3.text().trim();
            if (!text.isEmpty() && text.contains("播放") && !text.equals("播放选集")) {
                h3Names.add(text);
            }
        }

        List<String> playFromList = new ArrayList<>();
        List<String> playUrlList = new ArrayList<>();
        int srcIdx = 0;
        for (Map.Entry<String, List<String[]>> entry : sourceMap.entrySet()) {
            String sourceName = srcIdx < h3Names.size() ? h3Names.get(srcIdx) : "线路" + entry.getKey();
            playFromList.add(sourceName);
            StringBuilder eps = new StringBuilder();
            List<String[]> episodes = entry.getValue();
            for (int i = 0; i < episodes.size(); i++) {
                if (i > 0) eps.append("#");
                eps.append(episodes.get(i)[0]).append("$").append(episodes.get(i)[1]);
            }
            playUrlList.add(eps.toString());
            srcIdx++;
        }

        if (!playFromList.isEmpty()) {
            vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
        }

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url;
        if (page <= 1) {
            url = siteUrl + "/search/" + encodedKey + "-------------.html";
        } else {
            url = siteUrl + "/search/" + encodedKey + "--------" + page + "-----.html";
        }
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseList(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = fixUrl(id);
        String directUrl = extractPlayUrl(playUrl);
        if (!TextUtils.isEmpty(directUrl)) {
            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", Util.CHROME);
            header.put("Referer", playUrl);
            return Result.get().parse(0).url(directUrl).header(header).string();
        }
        return Result.get().parse(1).url(playUrl).header(getHeader()).string();
    }

    // ==================== Helper Methods ====================

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return siteUrl + url;
    }

    private List<Vod> parseList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a[href*=/detail/]")) {
            String href = a.attr("href");
            Matcher m = VOD_ID_PATTERN.matcher(href);
            if (!m.find()) continue;
            String vid = m.group(1);
            if (seen.contains(vid)) continue;
            String name = a.attr("title");
            if (name.isEmpty()) name = a.text().trim();
            if (name.isEmpty() || name.length() > 100) {
                Element parent = a.parent();
                if (parent != null) {
                    Element heading = parent.selectFirst("h4");
                    if (heading == null) heading = parent.selectFirst("h3");
                    if (heading != null) name = heading.text().trim();
                }
            }
            if (name.isEmpty()) continue;
            seen.add(vid);
            // Image: check <a> tag's own data-original first (stui-vodlist__thumb), then child/grandchild <img>
            String pic = a.attr("data-original");
            if (pic.isEmpty()) pic = a.attr("data-src");
            if (pic.isEmpty()) {
                Element img = a.selectFirst("img");
                if (img == null) {
                    Element parent = a.parent();
                    if (parent != null) img = parent.selectFirst("img");
                    if (img == null && parent != null) {
                        Element grand = parent.parent();
                        if (grand != null) img = grand.selectFirst("img");
                    }
                }
                if (img != null) {
                    pic = img.attr("data-original");
                    if (pic.isEmpty()) pic = img.attr("data-src");
                    if (pic.isEmpty()) pic = img.attr("src");
                }
            }
            // Remark: from span.pic-text (e.g. "已完结"), combine with span.pic-tag (rating)
            String remark = "";
            Element picText = a.selectFirst("span.pic-text");
            if (picText == null) {
                Element parent = a.parent();
                if (parent != null) picText = parent.selectFirst("span.pic-text");
            }
            if (picText != null) remark = picText.text().trim();
            Element picTag = a.selectFirst("span.pic-tag");
            if (picTag == null) {
                Element parent = a.parent();
                if (parent != null) picTag = parent.selectFirst("span.pic-tag");
            }
            if (picTag != null) {
                String rating = picTag.text().trim();
                if (!rating.isEmpty()) {
                    remark = remark.isEmpty() ? rating : remark + " " + rating;
                }
            }
            list.add(new Vod(vid, name, fixUrl(pic), remark));
        }
        return list;
    }

    private String extractPlayUrl(String playUrl) {
        try {
            String html = OkHttp.string(playUrl, getHeader());
            Matcher m = PLAYER_AAAA_PATTERN.matcher(html);
            if (m.find()) {
                String jsonStr = m.group(1);
                org.json.JSONObject json = new org.json.JSONObject(jsonStr);
                String url = json.optString("url", "");
                if (!url.isEmpty()) {
                    return url.replace("\\/", "/");
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private int parsePageCount(String html) {
        Matcher m = PAGE_COUNT_PATTERN.matcher(html);
        if (m.find()) {
            return Util.toInt(m.group(2), 1);
        }
        return 1;
    }

    private LinkedHashMap<String, List<Filter>> buildFilters() {
        List<Filter.Value> areas = new ArrayList<>();
        areas.add(new Filter.Value("全部", ""));
        for (String[] area : new String[][]{
                {"中国大陆", "中国大陆"}, {"中国香港", "中国香港"}, {"中国台湾", "中国台湾"},
                {"美国", "美国"}, {"法国", "法国"}, {"英国", "英国"},
                {"日本", "日本"}, {"韩国", "韩国"}, {"德国", "德国"},
                {"泰国", "泰国"}, {"印度", "印度"}, {"意大利", "意大利"},
                {"西班牙", "西班牙"}, {"加拿大", "加拿大"}, {"其他", "其他"}
        }) {
            areas.add(new Filter.Value(area[0], area[1]));
        }

        List<Filter.Value> langs = new ArrayList<>();
        langs.add(new Filter.Value("全部", ""));
        for (String[] lang : new String[][]{
                {"国语", "国语"}, {"英语", "英语"}, {"粤语", "粤语"},
                {"闽南语", "闽南语"}, {"韩语", "韩语"}, {"日语", "日语"},
                {"法语", "法语"}, {"德语", "德语"}, {"其它", "其它"}
        }) {
            langs.add(new Filter.Value(lang[0], lang[1]));
        }

        List<Filter.Value> years = new ArrayList<>();
        years.add(new Filter.Value("全部", ""));
        for (int y = 2026; y >= 1998; y--) {
            years.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
        }

        List<Filter.Value> sorts = new ArrayList<>();
        sorts.add(new Filter.Value("时间", "time"));
        sorts.add(new Filter.Value("人气", "hits"));
        sorts.add(new Filter.Value("评分", "score"));

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (String tid : Arrays.asList("1", "2", "4", "15", "16")) {
            List<Filter> filterList = new ArrayList<>();
            filterList.add(new Filter("area", "地区", areas));
            filterList.add(new Filter("lang", "语言", langs));
            filterList.add(new Filter("year", "年份", years));
            filterList.add(new Filter("by", "排序", sorts));
            filters.put(tid, filterList);
        }
        return filters;
    }
}
