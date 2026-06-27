package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

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

public class FirstAid extends Spider {

    private static final String BASE_URL = "https://m.youlai.cn";

    private static final Pattern VIDEO_PATTERN = Pattern.compile("(https://vod\\.youlai\\.cn/[^\"\'\\s<>]+)");

    private static final String[] IMAGES = new String[]{
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_jjjn.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_jtsh.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_jwzz.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_cjss.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_dwzs.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_hyjj.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_zdjj.png",
            "https://static.cnkang.com/images/youlai/patient/dissicon/jijiu/title_ywsg.png"
    };

    private static final String[] CATEGORIES = new String[]{
            "急救技能",
            "家庭生活",
            "急危重症",
            "常见损伤",
            "动物致伤",
            "海洋急救",
            "中毒急救",
            "意外事故"
    };

    public static String getImage(int index) {
        String[] arr = IMAGES;
        if (index < 0 || index >= arr.length) {
            index = 0;
        }
        return arr[index];
    }

    public static Vod buildVod(String id, String name, String pic) {
        Vod vod = new Vod(id, name, pic, "");
        vod.setStyle(Vod.Style.rect(4.0f));
        return vod;
    }

    @Override
    public void init(Context context, String extend) {
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            classes.add(new Class("jijiu|" + i, CATEGORIES[i]));
        }
        try {
            String url = new StringBuilder().append(BASE_URL).append(String.format("/%s/", "jijiu")).toString();
            String html = OkHttp.string(url, buildHeader());
            ArrayList<Vod> list = parseVod(html);
            return Result.string(classes, list);
        } catch (Exception e) {
            ArrayList<Vod> list = new ArrayList<>();
            for (int i = 0; i < CATEGORIES.length; i++) {
                list.add(buildVod("jijiu|" + i, CATEGORIES[i], getImage(i)));
            }
            return Result.string(classes, list);
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int index;
            if (TextUtils.isEmpty(tid)) {
                index = 0;
            } else {
                try {
                    String[] parts = tid.split("\\|");
                    String s = (parts.length < 2) ? tid.trim() : parts[1].trim();
                    index = Integer.parseInt(s);
                } catch (Exception ex) {
                    index = 0;
                }
            }
            String url = new StringBuilder(BASE_URL).append(String.format("/%s/", "jijiu")).toString();
            String html = OkHttp.string(url, buildHeader());
            ArrayList<Vod> list = parseVod(index, html);
            return Result.get().page(1, 1, list.size(), list.size()).vod(list).string();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = ids.get(0);
            String html = OkHttp.string(url, buildHeader());
            Document doc = Jsoup.parse(html);

            Element titleEl = doc.selectFirst(".video-title.h1-title, .h1-title, h1");
            String title = (titleEl != null) ? titleEl.text().trim() : "急救教学";

            Element docNameEl = doc.selectFirst("span.doc-name");
            String docName = (docNameEl != null) ? docNameEl.text().trim() : "";

            Element contentEl = doc.selectFirst(".img-text-con");
            String content = (contentEl != null) ? contentEl.text().trim() : "";

            String pic = "";
            if (contentEl != null) {
                for (Element img : contentEl.select("img")) {
                    String src = normalizeUrl(img.attr("src"));
                    if (TextUtils.isEmpty(src)) continue;
                    if (src.contains(".gif")) continue;
                    pic = src;
                    break;
                }
            }
            if (pic.isEmpty()) {
                Element ogImg = doc.selectFirst("meta[property=og:image]");
                if (ogImg != null) {
                    pic = normalizeUrl(ogImg.attr("content"));
                }
            }
            if (pic.isEmpty()) {
                pic = getImage(0);
            }

            String videoUrl = "";
            if (!TextUtils.isEmpty(html)) {
                Document videoDoc = Jsoup.parse(html);
                Element videoEl = videoDoc.selectFirst("#video");
                if (videoEl != null) {
                    String src = videoEl.attr("src");
                    if (!TextUtils.isEmpty(src) && src.contains("vod.youlai.cn")) {
                        videoUrl = src;
                    }
                }
                if (videoUrl.isEmpty()) {
                    Matcher matcher = VIDEO_PATTERN.matcher(html);
                    if (matcher.find()) {
                        videoUrl = matcher.group(1);
                    }
                }
            }

            if (videoUrl.isEmpty()) {
                return Result.error("未找到视频地址");
            }

            Vod vod = new Vod();
            vod.setVodId(url);
            vod.setVodName(title);
            vod.setVodPic(pic);
            vod.setVodArea("中国");
            vod.setVodActor(docName);
            vod.setVodContent(content);
            vod.setVodPlayFrom("Qile");
            vod.setVodPlayUrl(new StringBuilder().append(title).append("$").append(videoUrl).toString());
            return Result.string(vod);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(buildHeader()).string();
    }

    public final String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:".concat(url);
        if (url.startsWith("/")) return BASE_URL.concat(url);
        return (BASE_URL + "/").concat(url);
    }

    public final String getPic(Document doc, int index) {
        String pic = "";
        Elements imgs = doc.select("img.block100");
        if (!imgs.isEmpty() && index < imgs.size()) {
            Element img = imgs.get(index);
            pic = normalizeUrl(img.attr("src"));
        }
        if (TextUtils.isEmpty(pic)) {
            pic = getImage(index);
        }
        return pic;
    }

    public final Map<String, String> buildHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        headers.put("Referer", "https://m.youlai.cn/");
        return headers;
    }

    public final String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("https://https://")) {
            return url.replace("https://https://", "https://");
        }
        if (url.startsWith("http://https://")) {
            return url.replace("http://https://", "https://");
        }
        if (url.startsWith("http")) {
            return url;
        }
        if (url.startsWith("//")) {
            return "https:".concat(url);
        }
        if (url.startsWith("https:")) {
            return "https" + url.substring(5);
        }
        return url;
    }

    public final ArrayList<Vod> parseVod(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".jj-title-li");
        for (int i = 0; i < CATEGORIES.length; i++) {
            String pic = getPic(doc, i);
            String name = CATEGORIES[i];
            String id = "jijiu|" + i;
            if (i < items.size()) {
                Element link = items.get(i).selectFirst("a[href*=/jijiu/article/]");
                if (link != null) {
                    id = fixUrl(link.attr("href"));
                    Element clamp = link.selectFirst(".line-clamp1");
                    String subtitle = (clamp != null) ? clamp.text() : link.text();
                    subtitle = subtitle.trim();
                    if (!TextUtils.isEmpty(subtitle)) {
                        name = new StringBuilder().append(CATEGORIES[i]).append(" · ").append(subtitle).toString();
                    }
                }
            }
            Vod vod = buildVod(id, name, pic);
            vod.setVodRemarks("急救");
            list.add(vod);
        }
        return list;
    }

    public final ArrayList<Vod> parseVod(int index, String html) {
        ArrayList<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".jj-title-li");
        if (items.isEmpty() || index >= items.size()) {
            return list;
        }
        String pic = getPic(doc, index);
        if (TextUtils.isEmpty(pic)) {
            pic = getImage(index);
        }
        Element item = items.get(index);
        for (Element br3 : item.select(".list-br3")) {
            Element link = br3.selectFirst("a[href]");
            if (link == null) continue;
            String href = link.attr("href");
            if (!href.contains("/jijiu/article/")) continue;
            href = fixUrl(href);
            if (href.isEmpty()) continue;
            Element clamp = link.selectFirst(".line-clamp1");
            String title = (clamp != null) ? clamp.text() : link.text();
            title = title.trim();
            if (title.isEmpty()) continue;
            list.add(buildVod(href, title, pic));
        }
        return list;
    }
}
