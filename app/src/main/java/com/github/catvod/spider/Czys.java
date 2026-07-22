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
import org.jsoup.select.Elements;

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
 * 厂长资源 Spider
 * 站点发布页: https://cz01.tv / https://www.cz01.vip
 * 推荐域名: www.4kcz.com / czzy.top / www.cz4k.com
 * WordPress(mibt主题)站点，播放页iframe的url参数包含m3u8直链
 */
public class Czys extends Spider {

    private static final String DEFAULT_HOST = "https://www.4kcz.com";

    private static final Pattern MOVIE_ID_PATTERN = Pattern.compile("/movie/(\\d+)\\.html");
    private static final Pattern IFRAME_URL_PATTERN = Pattern.compile("url=(https?://[^\"'&]+\\.m3u8[^\"'&]*)");
    private static final Pattern IFRAME_SRC_PATTERN = Pattern.compile("iframe[^>]+viframe[^>]+src=\"([^\"]+)\"");
    private static final Pattern LAST_PAGE_PATTERN = Pattern.compile("/page/(\\d+)\"[^>]*>\\s*«");

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
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("movie_bt", "全部"));
        classes.add(new Class("movie_bt_series/dyy", "电影"));
        classes.add(new Class("movie_bt_series/dianshiju", "电视剧"));
        classes.add(new Class("movie_bt_series/guochanju", "国产剧"));
        classes.add(new Class("movie_bt_series/mj", "美剧"));
        classes.add(new Class("movie_bt_series/rj", "日剧"));
        classes.add(new Class("movie_bt_series/hj", "韩剧"));
        classes.add(new Class("movie_bt_series/hwj", "海外剧"));
        classes.add(new Class("movie_bt_series/dohua", "动画"));
        classes.add(new Class("movie_bt_series/huayudianying", "华语电影"));
        classes.add(new Class("movie_bt_series/meiguodianying", "欧美电影"));
        classes.add(new Class("movie_bt_series/hanguodianying", "韩国电影"));
        classes.add(new Class("movie_bt_series/ribendianying", "日本电影"));
        classes.add(new Class("movie_bt_series/yindudianying", "印度电影"));
        classes.add(new Class("movie_bt_series/zhanchangtuijian", "站长推荐"));
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
        String basePath = tid;
        if (extend != null) {
            String area = extend.get("area");
            String year = extend.get("year");
            String cls = extend.get("class");
            if (!TextUtils.isEmpty(area)) {
                basePath = "movie_bt_cat/" + area;
            } else if (!TextUtils.isEmpty(year)) {
                basePath = "year/" + year;
            } else if (!TextUtils.isEmpty(cls)) {
                basePath = "movie_bt_tags/" + cls;
            }
        }
        String url;
        if (page <= 1) {
            url = siteUrl + "/" + basePath;
        } else {
            url = siteUrl + "/" + basePath + "/page/" + page;
        }
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseList(doc);
        int pageCount = parsePageCount(html);
        int total = list.size();
        if (pageCount > 1) total = pageCount * 20;
        int limit = list.size();
        if (limit == 0) limit = 20;
        return Result.string(page, pageCount, limit, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String detailUrl = siteUrl + "/movie/" + vid + ".html";
        String html = OkHttp.string(detailUrl, getHeader());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vid);

        Element h1 = doc.selectFirst("div.il_tt h1");
        if (h1 == null) h1 = doc.selectFirst("h3.dy_tit_big");
        if (h1 != null) vod.setVodName(h1.text().replaceAll("\\|.*$", "").trim());

        Element img = doc.selectFirst("div.dyimg img");
        if (img != null) vod.setVodPic(fixUrl(img.attr("src")));

        for (Element li : doc.select("ul.moviedteail_list li")) {
            String text = li.text();
            if (text.startsWith("类型：")) {
                vod.setTypeName(li.select("a").text());
            } else if (text.startsWith("地区：")) {
                vod.setVodArea(li.select("a").text());
            } else if (text.startsWith("年份：")) {
                vod.setVodYear(li.select("a").text());
            } else if (text.startsWith("导演：")) {
                vod.setVodDirector(getSpanText(li));
            } else if (text.startsWith("主演：")) {
                String actor = getSpanText(li);
                if (!actor.isEmpty() && !actor.equals("false")) vod.setVodActor(actor);
            } else if (text.startsWith("又名：")) {
                vod.setVodRemarks(getSpanText(li));
            }
        }

        Element content = doc.selectFirst("div.yp_context");
        if (content != null) vod.setVodContent(content.text());

        List<String> epNames = new ArrayList<>();
        List<String> epUrls = new ArrayList<>();
        for (Element a : doc.select("div.paly_list_btn a")) {
            String name = a.text().trim();
            String href = a.attr("href");
            if (name.isEmpty() || href.isEmpty()) continue;
            epNames.add(name);
            epUrls.add(href);
        }
        if (epNames.isEmpty()) {
            epNames.add("1080P-1");
            epUrls.add("/v_play/" + encodePlayId(vid, 1) + ".html");
        }
        vod.setVodPlayFrom("厂长资源");
        StringBuilder playUrl = new StringBuilder();
        for (int i = 0; i < epNames.size(); i++) {
            if (i > 0) playUrl.append("#");
            playUrl.append(epNames.get(i)).append("$").append(epUrls.get(i));
        }
        vod.setVodPlayUrl(playUrl.toString());

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
        String url = siteUrl + "/boss1O1?q=" + encodedKey + "&page=" + page;
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseList(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = fixUrl(id);
        String m3u8 = extractM3u8(playUrl);
        if (!TextUtils.isEmpty(m3u8)) {
            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", Util.CHROME);
            header.put("Referer", playUrl);
            return Result.get().parse(0).url(m3u8).header(header).string();
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

    private String getSpanText(Element li) {
        Element span = li.selectFirst("span");
        return span != null ? span.text().trim() : li.text();
    }

    /**
     * 解析视频列表，按 vod_id 去重
     */
    private List<Vod> parseList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a[href*=/movie/]")) {
            String href = a.attr("href");
            Matcher m = MOVIE_ID_PATTERN.matcher(href);
            if (!m.find()) continue;
            String vid = m.group(1);
            if (seen.contains(vid)) continue;
            Element li = a.closest("li");
            if (li == null) continue;
            seen.add(vid);
            String name = "";
            Element titleEl = li.selectFirst("h3.dytit a");
            if (titleEl != null) {
                name = titleEl.text().trim();
            } else {
                name = a.attr("title");
                if (name.isEmpty()) name = a.attr("alt");
                if (name.isEmpty()) name = li.text().trim();
            }
            if (name.isEmpty()) continue;
            String pic = "";
            Element img = li.selectFirst("img[data-original]");
            if (img != null) {
                pic = img.attr("data-original");
            } else {
                img = li.selectFirst("img[src]");
                if (img != null) {
                    String src = img.attr("src");
                    if (!src.contains("blank.gif") && !src.contains("loading")) pic = src;
                }
            }
            String remark = "";
            Element jidi = li.selectFirst("div.jidi span");
            if (jidi != null) {
                remark = jidi.text().trim();
            } else {
                Element qb = li.selectFirst("div.hdinfo span.qb");
                if (qb != null) remark = qb.text().trim();
            }
            list.add(new Vod(vid, name, fixUrl(pic), remark));
        }
        return list;
    }

    /**
     * 从播放页iframe提取m3u8直链
     */
    private String extractM3u8(String playUrl) {
        try {
            String html = OkHttp.string(playUrl, getHeader());
            Matcher srcMatcher = IFRAME_SRC_PATTERN.matcher(html);
            if (srcMatcher.find()) {
                String iframeSrc = srcMatcher.group(1);
                Matcher urlMatcher = IFRAME_URL_PATTERN.matcher(iframeSrc);
                if (urlMatcher.find()) {
                    return urlMatcher.group(1);
                }
            }
            Matcher urlMatcher = IFRAME_URL_PATTERN.matcher(html);
            if (urlMatcher.find()) {
                return urlMatcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 从分页HTML提取总页数
     */
    private int parsePageCount(String html) {
        Matcher m = LAST_PAGE_PATTERN.matcher(html);
        if (m.find()) {
            return Util.toInt(m.group(1), 1);
        }
        Matcher m2 = Pattern.compile("/page/(\\d+)").matcher(html);
        int maxPage = 1;
        while (m2.find()) {
            int p = Util.toInt(m2.group(1), 1);
            if (p > maxPage) maxPage = p;
        }
        return maxPage;
    }

    /**
     * 生成播放页base64 ID: mv_{vid}-nm_{n}
     */
    private String encodePlayId(String vid, int n) {
        String raw = "mv_" + vid + "-nm_" + n;
        return android.util.Base64.encodeToString(raw.getBytes(), android.util.Base64.NO_WRAP).replace("\n", "");
    }

    /**
     * 构建分类筛选条件(地区/年份/类型)
     */
    private LinkedHashMap<String, List<Filter>> buildFilters() {
        List<Filter.Value> areas = new ArrayList<>();
        areas.add(new Filter.Value("全部", ""));
        for (String[] area : new String[][]{
                {"中国大陆", "zh"}, {"中国香港", "hk"}, {"中国台湾", "tw"},
                {"美国", "meiguo"}, {"韩国", "hggggg"}, {"日本", "riben"},
                {"英国", "yinguo"}, {"法国", "fg"}, {"德国", "dg"},
                {"泰国", "taiguo"}, {"印度", "yidu"}, {"加拿大", "jnd"},
                {"俄罗斯", "els"}, {"意大利", "ydl"}, {"西班牙", "xby"},
                {"澳大利亚", "adly"}, {"其它", "qt"}
        }) {
            areas.add(new Filter.Value(area[0], area[1]));
        }

        List<Filter.Value> years = new ArrayList<>();
        years.add(new Filter.Value("全部", ""));
        for (int y = 2025; y >= 2013; y--) {
            years.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
        }
        years.add(new Filter.Value("2020年代", "2020"));
        years.add(new Filter.Value("2010年代", "2011nd"));

        List<Filter.Value> genres = new ArrayList<>();
        genres.add(new Filter.Value("全部", ""));
        for (String[] genre : new String[][]{
                {"剧情", "juqing"}, {"动作", "dozuo"}, {"喜剧", "xiju"},
                {"爱情", "aiqing"}, {"科幻", "kh"}, {"恐怖", "kubu"},
                {"悬疑", "xuanyi"}, {"惊悚", "kingsong"}, {"战争", "zhanzheng"},
                {"奇幻", "qihuan"}, {"动画", "dhh"}, {"古装", "guzhuang"},
                {"武侠", "wuxia"}, {"历史", "lishi"}, {"犯罪", "fanzi"},
                {"冒险", "maoxian"}, {"灾难", "zainan"}, {"纪录片", "jlpp"}
        }) {
            genres.add(new Filter.Value(genre[0], genre[1]));
        }

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (String tid : Arrays.asList(
                "movie_bt", "movie_bt_series/dyy", "movie_bt_series/dianshiju",
                "movie_bt_series/guochanju", "movie_bt_series/mj", "movie_bt_series/rj",
                "movie_bt_series/hj", "movie_bt_series/hwj", "movie_bt_series/dohua",
                "movie_bt_series/huayudianying", "movie_bt_series/meiguodianying",
                "movie_bt_series/hanguodianying", "movie_bt_series/ribendianying",
                "movie_bt_series/yindudianying", "movie_bt_series/zhanchangtuijian")) {
            List<Filter> filterList = new ArrayList<>();
            filterList.add(new Filter("area", "地区", areas));
            filterList.add(new Filter("year", "年份", years));
            filterList.add(new Filter("class", "类型", genres));
            filters.put(tid, filterList);
        }
        return filters;
    }
}
