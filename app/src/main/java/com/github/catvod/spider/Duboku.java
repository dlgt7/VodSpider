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
    private static final Pattern PLAY_PATTERN = Pattern.compile("/play/[^-]+-(\\d+)-(\\d+)\\.html");

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

        // 使用准确的选择器：ul.pic-list li
        Elements items = doc.select("ul.pic-list li");

        for (Element item : items) {
            try {
                // 提取链接和ID
                Element link = item.selectFirst("a[href*=/detail/]");
                if (link == null) continue;

                String href = link.attr("href");
                String vodId = extractVodId(href);
                if (TextUtils.isEmpty(vodId)) continue;

                // 提取标题（从h3标签）
                String name = "";
                Element h3 = item.selectFirst("h3");
                if (h3 != null) {
                    name = h3.text().trim();
                }

                // 提取图片（从src属性）
                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("src");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    pic = fixUrl(pic);
                }

                // 提取备注（评分/集数）
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
        // 构建分类URL
        String url;
        int page = Util.toInt(pg, 1);

        if (page == 1) {
            url = SITE_URL + "/show/" + tid + "-----------.html";
        } else {
            url = SITE_URL + "/show/" + tid + "-------" + page + "-----.html";
        }

        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);

        return Result.string(page, page + 1, list.size(), 9999, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String url = SITE_URL + "/detail/" + vodId + ".html";
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vodId);

        // 提取标题
        String name = "";
        Element titleElem = doc.selectFirst("h1, h2, .title");
        if (titleElem != null) name = titleElem.text().trim();
        vod.setVodName(name);

        // 提取图片
        String pic = "";
        Element img = doc.selectFirst(".content img, .detail img, img[src*='img.jisuimage.com'], img[src*='doubaocdn.com']");
        if (img != null) {
            pic = img.attr("src");
            if (TextUtils.isEmpty(pic)) {
                pic = img.attr("data-src");
            }
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

        // 提取播放源名称
        List<String> sourceNames = new ArrayList<>();
        Elements sourceTabs = doc.select(".play-title #Tab li a");
        for (Element tab : sourceTabs) {
            String sourceName = tab.text().trim();
            if (!TextUtils.isEmpty(sourceName)) {
                sourceNames.add(sourceName);
            }
        }

        // 提取播放链接（避免"立即观看"按钮）
        // 使用准确的选择器：.main-left .play-list a[href^="/play/"]
        Map<Integer, List<String>> sourceEpisodes = new HashMap<>();
        Elements playLinks = doc.select(".play-list a[href^='/play/']");

        for (Element playLink : playLinks) {
            String href = playLink.attr("href");
            String epName = playLink.text().trim();
            if (TextUtils.isEmpty(epName)) epName = "正片";

            // 避免提取"立即观看"
            if (epName.contains("立即观看")) continue;

            Matcher matcher = PLAY_PATTERN.matcher(href);
            if (matcher.find()) {
                int sourceId = Integer.parseInt(matcher.group(1));
                String epUrl = fixUrl(href);

                if (!sourceEpisodes.containsKey(sourceId)) {
                    sourceEpisodes.put(sourceId, new ArrayList<>());
                }
                sourceEpisodes.get(sourceId).add(epName + "$" + epUrl);
            }
        }

        // 构建播放源和播放链接
        List<String> playFromList = new ArrayList<>();
        List<String> playUrlList = new ArrayList<>();

        for (Map.Entry<Integer, List<String>> entry : sourceEpisodes.entrySet()) {
            int sourceId = entry.getKey();
            String sourceName = sourceId <= sourceNames.size() ? sourceNames.get(sourceId - 1) : "线路" + sourceId;
            playFromList.add(sourceName);
            playUrlList.add(TextUtils.join("#", entry.getValue()));
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