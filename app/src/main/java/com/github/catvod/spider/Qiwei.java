package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 七味影视爬虫
 * 站点结构：MacCMS 风格自定义模板
 * 分类页：/vt/{tid}-{pg}.html
 * 详情页：/mv/{id}.html
 * 搜索页：/vs/-------------.html?wd={key}
 * 播放页：/py/{id}-{src}-{ep}.html （网页地址，parse=1 交由客户端解析）
 */
public class Qiwei extends Spider {

    private static final String TAG = "Qiwei";

    private static final List<String> DEFAULT_SITES = Arrays.asList(
            "https://www.qwnull.com",
            "https://www.qwmkv.com",
            "https://www.qwfilm.com",
            "https://www.qnmp4.com",
            "https://www.qnnull.com",
            "https://www.qnhot.com",
            "https://www.qncool.com"
    );

    private String siteUrl = DEFAULT_SITES.get(0);
    private List<String> sites = new ArrayList<>(DEFAULT_SITES);

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (TextUtils.isEmpty(extend)) return;
        extend = extend.trim();
        if (extend.startsWith("{")) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(extend);
                JSONArray siteArray = json.optJSONArray("site");
                if (siteArray != null && siteArray.length() > 0) {
                    sites.clear();
                    for (int i = 0; i < siteArray.length(); i++) {
                        String s = siteArray.optString(i).trim();
                        if (!s.isEmpty()) sites.add(s);
                    }
                } else {
                    String host = json.optString("host", "");
                    if (!host.isEmpty()) {
                        sites.clear();
                        sites.add(host);
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        } else {
            sites.clear();
            for (String s : extend.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) sites.add(s);
            }
        }
        siteUrl = pickWorkingSite();
    }

    /** 遍历源站列表，返回首个可达站点 */
    private String pickWorkingSite() {
        for (String s : sites) {
            try {
                String html = OkHttp.string(s, getHeader());
                if (!TextUtils.isEmpty(html) && !html.contains("nginx")) return s;
            } catch (Exception e) {
                SpiderDebug.log(TAG + " site unreachable: " + s);
            }
        }
        return sites.get(0);
    }

    /** 相对路径转绝对路径 */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return siteUrl + (url.startsWith("/") ? url : "/" + url);
    }

    /** 解析视频列表项 */
    private List<Vod> parseList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("ul.content-list > li");
        for (Element li : items) {
            try {
                Element a = li.selectFirst("div.li-img a");
                if (a == null) continue;
                String href = a.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/mv/")) continue;
                String vid = href;
                String name = a.attr("title");
                Element img = a.selectFirst("img");
                if (TextUtils.isEmpty(name) && img != null) name = img.attr("alt");
                String pic = img != null ? img.attr("src") : "";
                pic = fixUrl(pic);
                String remark = li.select("span.bottom2").text();
                if (TextUtils.isEmpty(remark)) remark = li.select(".li-bottom span").text();
                list.add(new Vod(vid, name, pic, remark));
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "剧集"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("30", "短剧"));
        List<Vod> list = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
            list = parseList(doc);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        String url;
        if (page <= 1) {
            url = siteUrl + "/vt/" + tid + ".html";
        } else {
            url = siteUrl + "/vt/" + tid + "-" + page + ".html";
        }
        List<Vod> list = new ArrayList<>();
        int pageCount = 1;
        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));
            list = parseList(doc);
            Elements pageLinks = doc.select("div.pages a");
            for (Element a : pageLinks) {
                String text = a.text().trim();
                try {
                    int p = Integer.parseInt(text);
                    if (p > pageCount) pageCount = p;
                } catch (Exception ignored) {
                }
            }
            if (pageCount < page) pageCount = page;
            if (list.isEmpty() && page > 1) {
                pageCount = page - 1;
            } else if (!list.isEmpty()) {
                pageCount = Math.max(pageCount, page + (list.size() >= 18 ? 1 : 0));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.get().page(page, pageCount, 30, pageCount * 30).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = fixUrl(ids.get(0));
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        try {
            Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

            // 封面
            Element coverImg = doc.selectFirst(".main-ui-meta img, .main-ui-pic img, .pic img");
            if (coverImg == null) coverImg = doc.selectFirst("img[alt]");
            if (coverImg != null) vod.setVodPic(fixUrl(coverImg.attr("src")));

            // 标题 + 年份
            Element h1 = doc.selectFirst("h1");
            if (h1 != null) {
                String title = h1.ownText().trim();
                if (title.isEmpty()) title = h1.text().trim();
                Element yearSpan = h1.selectFirst("span.year");
                if (yearSpan != null) {
                    String yearText = yearSpan.text().replaceAll("[^0-9]", "");
                    if (!yearText.isEmpty()) vod.setVodYear(yearText);
                }
                vod.setVodName(title);
            }

            // 导演/主演/地区
            for (Element div : doc.select(".main-ui-meta div")) {
                String text = div.text();
                if (text.contains("导演：")) {
                    vod.setVodDirector(concatAnchorText(div.select("a")));
                } else if (text.contains("主演：")) {
                    vod.setVodActor(concatAnchorText(div.select("a")));
                } else if (text.contains("地区：")) {
                    vod.setVodArea(concatAnchorText(div.select("a")));
                }
            }

            // 简介
            Element contentEl = doc.selectFirst(".content, .text-collapse, .vod-content");
            if (contentEl != null) vod.setVodContent(contentEl.text().trim());

            // 播放源
            Elements sourceTabs = doc.select("h2:contains(在线播放) .hd li");
            Elements playLists = doc.select("ul.player");
            StringBuilder vodPlayFrom = new StringBuilder();
            StringBuilder vodPlayUrl = new StringBuilder();
            for (int i = 0; i < playLists.size(); i++) {
                String sourceName;
                if (i < sourceTabs.size()) {
                    sourceName = sourceTabs.get(i).ownText().trim();
                    if (sourceName.isEmpty()) sourceName = sourceTabs.get(i).text().replaceAll("\\d+", "").trim();
                } else {
                    sourceName = "线路" + (i + 1);
                }
                vodPlayFrom.append(sourceName).append("$$$");
                Elements episodes = playLists.get(i).select("a");
                for (int j = 0; j < episodes.size(); j++) {
                    Element ep = episodes.get(j);
                    String epName = ep.text().trim();
                    String epUrl = ep.attr("href");
                    vodPlayUrl.append(epName).append("$").append(epUrl);
                    vodPlayUrl.append(j < episodes.size() - 1 ? "#" : "$$$");
                }
                if (episodes.isEmpty()) vodPlayUrl.append("$$$");
            }
            vod.setVodPlayFrom(vodPlayFrom.toString());
            vod.setVodPlayUrl(vodPlayUrl.toString());
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(vod);
    }

    /** 拼接多个 a 标签文本 */
    private String concatAnchorText(Elements anchors) {
        if (anchors == null || anchors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Element a : anchors) {
            String t = a.text().trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(t);
        }
        return sb.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/vs/-------------.html?wd=" + Uri.encode(key);
        List<Vod> list = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));
            list = parseList(doc);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = fixUrl(id);
        return Result.get().parse(1).url(url).header(getHeader()).string();
    }
}
