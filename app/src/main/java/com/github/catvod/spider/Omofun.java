package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Omofun爬虫 - 基于苹果CMS v10(mxpro模板)的动漫视频站
 * 站点地址：https://omofuns.xyz
 * 特征：播放页player_aaaa的encrypt=0，url为m3u8直链
 *
 * @author Trae
 * @date 2026-07-18
 */
public class Omofun extends Spider {

    private static final String DEFAULT_HOST = "https://omofuns.xyz";
    private static final Pattern ID_PATTERN = Pattern.compile("/vod/detail/id/(\\d+)\\.html");

    private String siteUrl = DEFAULT_HOST;
    private final Map<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
        put("Referer", DEFAULT_HOST + "/");
    }};

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            while (extend.endsWith("/")) extend = extend.substring(0, extend.length() - 1);
            if (extend.startsWith("http")) {
                siteUrl = extend;
                headers.put("Referer", extend + "/");
            }
        }
    }

    private Map<String, String> getHeader() {
        return headers;
    }

    private String fetch(String path) {
        try {
            return OkHttp.string(path.startsWith("http") ? path : siteUrl + path, getHeader());
        } catch (Exception e) {
            return "";
        }
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return siteUrl + url;
        return url;
    }

    private String match(String content, String regex) {
        Matcher m = Pattern.compile(regex).matcher(content);
        return m.find() ? m.group(1) : "";
    }

    private List<Vod> parsePosterList(Document doc) {
        List<Vod> list = new ArrayList<>();
        for (Element a : doc.select("a.module-poster-item")) {
            String href = a.attr("href");
            String vid = extractId(href);
            if (TextUtils.isEmpty(vid)) continue;
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) {
                Element t = a.selectFirst(".module-poster-item-title");
                if (t != null) name = t.text();
            }
            String pic = "";
            Element img = a.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            pic = fixUrl(pic);
            String remark = "";
            Element note = a.selectFirst(".module-item-note");
            if (note != null) remark = note.text();
            list.add(new Vod(vid, name, pic, remark));
        }
        return list;
    }

    private String extractId(String href) {
        if (TextUtils.isEmpty(href)) return "";
        Matcher m = ID_PATTERN.matcher(href);
        return m.find() ? m.group(1) : "";
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "连续剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        List<Vod> list = parsePosterList(Jsoup.parse(fetch("/")));
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        int page = Integer.parseInt(pg);
        String path;
        if (page <= 1) {
            path = "/vod/type/id/" + tid + ".html";
        } else {
            path = "/vod/show/id/" + tid + "/page/" + pg + ".html";
        }
        List<Vod> list = parsePosterList(Jsoup.parse(fetch(path)));
        int pageCount = list.isEmpty() ? page : page + 1;
        return Result.get().vod(list).page(page, pageCount, 30, pageCount * 30).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (String id : ids) {
            String html = fetch("/vod/detail/id/" + id + ".html");
            Document doc = Jsoup.parse(html);
            String name = "";
            Element h1 = doc.selectFirst(".module-info-heading h1");
            if (h1 != null) name = h1.text();
            String pic = "";
            Element img = doc.selectFirst(".module-info-poster img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            pic = fixUrl(pic);
            Vod vod = new Vod(id, name, pic);
            Elements tags = doc.select(".module-info-tag a");
            for (Element tag : tags) {
                String title = tag.attr("title");
                String text = tag.text();
                if (!TextUtils.isEmpty(title) && TextUtils.isDigitsOnly(title)) {
                    vod.setVodYear(title);
                } else if (!TextUtils.isEmpty(text)) {
                    if (vod.getVodArea().isEmpty()) vod.setVodArea(text);
                }
            }
            Element content = doc.selectFirst(".module-info-introduction-content p");
            if (content != null) vod.setVodContent(content.text());
            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            Elements tabs = doc.select(".module-tab-item.tab-item");
            Elements panels = doc.select(".module-play-list-content");
            int size = Math.min(tabs.size(), panels.size());
            for (int i = 0; i < size; i++) {
                String sourceName = tabs.get(i).attr("data-dropdown-value");
                if (TextUtils.isEmpty(sourceName)) sourceName = tabs.get(i).text();
                if (TextUtils.isEmpty(sourceName)) sourceName = "线路" + (i + 1);
                StringBuilder eps = new StringBuilder();
                Elements links = panels.get(i).select("a.module-play-list-link");
                for (int j = 0; j < links.size(); j++) {
                    Element a = links.get(j);
                    String href = a.attr("href");
                    String epName = a.text();
                    if (TextUtils.isEmpty(epName)) {
                        Element span = a.selectFirst("span");
                        if (span != null) epName = span.text();
                    }
                    if (eps.length() > 0) eps.append("#");
                    eps.append(epName).append("$").append(href);
                }
                if (eps.length() > 0) {
                    playFrom.add(sourceName);
                    playUrl.add(eps.toString());
                }
            }
            vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
            list.add(vod);
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String path = "/vod/search.html?wd=" + Uri.encode(key);
        Document doc = Jsoup.parse(fetch(path));
        List<Vod> list = new ArrayList<>();
        for (Element card : doc.select(".module-card-item")) {
            Element poster = card.selectFirst("a.module-card-item-poster");
            if (poster == null) continue;
            String vid = extractId(poster.attr("href"));
            if (TextUtils.isEmpty(vid)) continue;
            String name = "";
            Element title = card.selectFirst(".module-card-item-title strong");
            if (title != null) name = title.text();
            if (TextUtils.isEmpty(name)) name = poster.attr("title");
            String pic = "";
            Element img = poster.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            pic = fixUrl(pic);
            String remark = "";
            Element note = card.selectFirst(".module-item-note");
            if (note != null) remark = note.text();
            list.add(new Vod(vid, name, pic, remark));
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = id.startsWith("http") ? id : siteUrl + id;
        String html = OkHttp.string(url, getHeader());
        String json = match(html, "var player_aaaa=(\\{.*?\\})</script>");
        if (TextUtils.isEmpty(json)) {
            return Result.get().parse(1).url(url).header(getHeader()).string();
        }
        JSONObject player = new JSONObject(json);
        int encrypt = player.optInt("encrypt", 0);
        String playUrl = player.optString("url", "");
        if (TextUtils.isEmpty(playUrl)) {
            return Result.get().parse(1).url(url).header(getHeader()).string();
        }
        if (encrypt == 0 && (playUrl.contains(".m3u8") || playUrl.contains(".mp4") || playUrl.contains(".flv"))) {
            return Result.get().parse(0).url(playUrl).header(getHeader()).string();
        }
        return Result.get().parse(1).url(playUrl).header(getHeader()).string();
    }
}
