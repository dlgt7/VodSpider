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
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PTT extends Spider {

    private final String url = "https://ptt.red/";
    private String extend;
    private static final Pattern CONTENT_URL = Pattern.compile("contentUrl\":\"(.*?)\"");

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        header.put("Connection", "keep-alive");
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        this.extend = extend;
        SpiderDebug.log("PTT Spider initialized with extend: " + extend);
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));
            List<Class> classes = new ArrayList<>();
            for (Element a : doc.select("li > a.px-2.px-sm-3.py-2.nav-link")) {
                String href = a.attr("href").replace("/p/", "");
                String text = a.text();
                if (Util.isNotEmpty(href) && Util.isNotEmpty(text)) {
                    classes.add(new Class(href, text));
                }
            }
            String filterJson = TextUtils.isEmpty(extend) ? "{}" : OkHttp.string(extend);
            return Result.string(classes, Json.parse(filterJson));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载首页内容失败: " + e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            Uri.Builder builder = Uri.parse(url + "p/" + tid).buildUpon();
            if (!TextUtils.isEmpty(extend.get("c"))) builder.appendEncodedPath("c/" + extend.get("c"));
            if (!TextUtils.isEmpty(extend.get("area"))) builder.appendQueryParameter("area_id", extend.get("area"));
            if (!TextUtils.isEmpty(extend.get("year"))) builder.appendQueryParameter("year", extend.get("year"));
            if (!TextUtils.isEmpty(extend.get("sort"))) builder.appendQueryParameter("sort", extend.get("sort"));
            builder.appendQueryParameter("page", pg);
            
            Document doc = Jsoup.parse(OkHttp.string(builder.toString(), getHeader()));
            List<Vod> list = new ArrayList<>();
            for (Element div : doc.select("div.card > div.embed-responsive")) {
                try {
                    Elements aElements = div.select("a");
                    if (aElements.isEmpty()) continue;
                    
                    Element a = aElements.get(0);
                    Elements imgElements = a.select("img");
                    if (imgElements.isEmpty()) continue;
                    
                    Element img = imgElements.get(0);
                    Elements badgeElements = div.select("span.badge.badge-success");
                    String remark = badgeElements.isEmpty() ? "" : badgeElements.get(0).text();
                    
                    String vodPic = img.attr("src");
                    if (!vodPic.startsWith("http")) {
                        vodPic = url + vodPic;
                    }
                    
                    String name = img.attr("alt");
                    if (Util.isNotEmpty(name)) {
                        String href = a.attr("href");
                        String id = href.length() > 3 ? href.substring(3) : href;
                        list.add(new Vod(id, name, vodPic, remark));
                    }
                } catch (Exception e) {
                    SpiderDebug.log("Error parsing item: " + e.getMessage());
                }
            }
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载分类内容失败: " + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("缺少视频ID");
            }
            
            Document doc = Jsoup.parse(OkHttp.string(url + ids.get(0) + "/1", getHeader()));
            LinkedHashMap<String, String> flags = new LinkedHashMap<>();
            List<String> playUrls = new ArrayList<>();
            
            for (Element a : doc.select("ul#w1 > li > a")) {
                String href = a.attr("href");
                String[] parts = href.split("/");
                if (parts.length >= 4) {
                    flags.put(parts[3], a.attr("title"));
                }
            }
            
            Elements items = doc.select("div > a.seq.border");
            for (String flag : flags.keySet()) {
                List<String> urls = new ArrayList<>();
                for (Element e : items) {
                    String[] parts = e.attr("href").split("/");
                    if (parts.length >= 3) {
                        urls.add(e.text() + "$" + ids.get(0) + "/" + parts[2] + "/" + flag);
                    }
                }
                if (urls.isEmpty()) {
                    urls.add("1$" + ids.get(0) + "/1/" + flag);
                }
                playUrls.add(Util.join("#", urls));
            }
            
            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodPlayFrom(Util.join("$$$", new ArrayList<>(flags.values())));
            vod.setVodPlayUrl(Util.join("$$$", playUrls));
            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载详情内容失败: " + e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String content = OkHttp.string(url + id, getHeader());
            Matcher m = CONTENT_URL.matcher(content);
            if (m.find()) {
                String playUrl = m.group(1).replace("\\", "");
                return Result.get().url(playUrl).header(getHeader()).string();
            }
            return Result.error("未找到播放地址");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载播放内容失败: " + e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            if (Util.isEmpty(key)) {
                return Result.error("搜索关键词不能为空");
            }
            
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String searchUrl = url + String.format("q/%s?page=%s", encodedKey, pg);
            
            Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));
            List<Vod> list = new ArrayList<>();
            
            for (Element div : doc.select("div.card > div.embed-responsive")) {
                try {
                    Elements aElements = div.select("a");
                    if (aElements.isEmpty()) continue;
                    
                    Element a = aElements.get(0);
                    Elements imgElements = a.select("img");
                    if (imgElements.isEmpty()) continue;
                    
                    Element img = imgElements.get(0);
                    Elements badgeElements = div.select("span.badge.badge-success");
                    String remark = badgeElements.isEmpty() ? "" : badgeElements.get(0).text();
                    
                    String vodPic = img.attr("src");
                    if (!vodPic.startsWith("http")) {
                        vodPic = url + vodPic;
                    }
                    
                    String name = img.attr("alt");
                    if (Util.isNotEmpty(name)) {
                        String href = a.attr("href");
                        String id = href.length() > 3 ? href.substring(3) : href;
                        list.add(new Vod(id, name, vodPic, remark));
                    }
                } catch (Exception e) {
                    SpiderDebug.log("Error parsing search item: " + e.getMessage());
                }
            }
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }
}
