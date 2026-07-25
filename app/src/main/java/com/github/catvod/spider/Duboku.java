package com.github.catvod.spider;

import android.content.Context;
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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 6693影院 Spider
 * 站点地址: https://www.6693.org/
 * MacCMS v10 非标站点，播放页为HTML，需客户端解析
 */
public class Duboku extends Spider {

    private static final String SITE_URL = "https://www.6693.org";
    private static final Pattern DETAIL_ID_PATTERN = Pattern.compile("/detail/(.+?)\\.html");

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", SITE_URL + "/");
        return header;
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return url;
    }

    private String extractVodId(String href) {
        if (TextUtils.isEmpty(href)) return "";
        Matcher matcher = DETAIL_ID_PATTERN.matcher(href);
        return matcher.find() ? matcher.group(1) : "";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("dongzuopian", "动作片"));
        classes.add(new Class("xijupian", "喜剧片"));
        classes.add(new Class("aiqingpian", "爱情片"));
        classes.add(new Class("kehuanpian", "科幻片"));
        classes.add(new Class("kongbupian", "恐怖片"));
        classes.add(new Class("juqingpian", "剧情片"));
        classes.add(new Class("fanzuipian", "犯罪片"));
        classes.add(new Class("zhanzhengpian", "战争片"));
        classes.add(new Class("jilupian", "纪录片"));
        classes.add(new Class("xuanyipian", "悬疑片"));
        classes.add(new Class("donghuapian", "动画片"));
        classes.add(new Class("qihuanpian", "奇幻片"));
        classes.add(new Class("shaoshidianying", "邵氏电影"));

        String html = OkHttp.string(SITE_URL, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);

        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = OkHttp.string(SITE_URL, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);
        return Result.string(list);
    }

    private List<Vod> parseVodList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("a[href*=/detail/]");
        
        for (Element item : items) {
            try {
                String href = item.attr("href");
                String vodId = extractVodId(href);
                if (TextUtils.isEmpty(vodId)) continue;

                String name = item.attr("title");
                if (TextUtils.isEmpty(name)) {
                    Element titleElem = item.selectFirst("h3, h2, .title");
                    if (titleElem != null) name = titleElem.text().trim();
                }
                if (TextUtils.isEmpty(name)) name = item.text().trim();

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    pic = fixUrl(pic);
                }

                String remark = "";
                Element remarkElem = item.selectFirst(".remarks, .tag, .rating");
                if (remarkElem != null) {
                    remark = remarkElem.text().trim();
                }

                if (!TextUtils.isEmpty(name)) {
                    list.add(new Vod(vodId, name, pic, remark));
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        return list;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = SITE_URL + "/show/" + tid + "-----------";
        
        if (!TextUtils.isEmpty(pg) && !"1".equals(pg)) {
            url = SITE_URL + "/show/" + tid + "-------" + pg + "-----.html";
        } else {
            url = url + ".html";
        }

        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);

        int page = Util.toInt(pg, 1);
        int pageCount = page + 1;
        return Result.string(page, pageCount, list.size(), 9999, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String url = SITE_URL + "/detail/" + vodId + ".html";
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vodId);

        String name = "";
        Element titleElem = doc.selectFirst("h1, h2, .title");
        if (titleElem != null) name = titleElem.text().trim();
        vod.setVodName(name);

        String pic = "";
        Element img = doc.selectFirst(".content .thumb img, .detail img, img[src*='img.jisuimage.com']");
        if (img != null) {
            pic = img.attr("data-src");
            if (TextUtils.isEmpty(pic)) pic = img.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            pic = fixUrl(pic);
        }
        vod.setVodPic(pic);

        // 提取基本信息
        Element infoElem = doc.selectFirst(".content, .info");
        if (infoElem != null) {
            // 导演
            Element directorElem = infoElem.selectFirst("a[href*=/search/][href*='导演'], .director");
            if (directorElem != null) vod.setVodDirector(directorElem.text().trim());

            // 演员
            Elements actorElems = infoElem.select("a[href*=/search/][href*='主演'], a[href*=/search/][href*='演员']");
            if (!actorElems.isEmpty()) {
                StringBuilder actors = new StringBuilder();
                for (Element actor : actorElems) {
                    if (actors.length() > 0) actors.append(",");
                    actors.append(actor.text().trim());
                }
                vod.setVodActor(actors.toString());
            }

            // 年份
            Element yearElem = infoElem.selectFirst("a[href*=/search/][href*='202']");
            if (yearElem != null) vod.setVodYear(yearElem.text().trim());

            // 简介
            Element descElem = infoElem.selectFirst(".desc, .description, #desc");
            if (descElem != null) vod.setVodContent(descElem.text().trim());
        }

        // 提取播放源和播放链接
        List<String> playFromList = new ArrayList<>();
        List<String> playUrlList = new ArrayList<>();

        Elements sourceElems = doc.select(".playlist li, .sources li");
        if (sourceElems.isEmpty()) {
            sourceElems = doc.select("a[href*=/play/]");
        }

        if (!sourceElems.isEmpty()) {
            String sourceName = "默认";
            List<String> episodes = new ArrayList<>();

            for (Element epElem : sourceElems) {
                String epName = epElem.text().trim();
                if (TextUtils.isEmpty(epName)) epName = "正片";

                String epUrl = epElem.attr("href");
                if (TextUtils.isEmpty(epUrl)) continue;

                epUrl = fixUrl(epUrl);
                episodes.add(epName + "$" + epUrl);
            }

            if (!episodes.isEmpty()) {
                playFromList.add(sourceName);
                playUrlList.add(TextUtils.join("#", episodes));
            }
        }

        // 检查是否有多个播放源
        Elements multiSourceElems = doc.select(".tabs li, .sources-list li");
        for (Element sourceTab : multiSourceElems) {
            String sourceName = sourceTab.text().trim();
            if (TextUtils.isEmpty(sourceName)) continue;

            List<String> episodes = new ArrayList<>();
            Elements epLinks = doc.select("a[href*=/play/]");
            for (Element epLink : epLinks) {
                String epName = epLink.text().trim();
                if (TextUtils.isEmpty(epName)) epName = "正片";
                String epUrl = fixUrl(epLink.attr("href"));
                episodes.add(epName + "$" + epUrl);
            }

            if (!episodes.isEmpty()) {
                playFromList.add(sourceName);
                playUrlList.add(TextUtils.join("#", episodes));
            }
        }

        vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String url = SITE_URL + "/search/" + URLEncoder.encode(key, "UTF-8") + ".html";
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = id;
        if (!url.startsWith("http")) {
            url = fixUrl(id);
        }

        Result result = Result.get()
                .url(url)
                .parse(1);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", SITE_URL + "/");
        result.header(headers);

        return result.string();
    }
}