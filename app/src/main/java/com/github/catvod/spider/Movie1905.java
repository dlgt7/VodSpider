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

/**
 * 1905电影网爬虫（www.1905.com），CCTV6电影频道官网
 */
public class Movie1905 extends Spider {

    private static final String SITE_URL = "https://www.1905.com";

    private static final String[][] CATEGORIES = {
            {"1", "电影"}, {"2", "系列电影"}, {"922", "微电影"},
            {"927", "纪录片"}, {"586", "晚会"}, {"178", "独家"},
            {"1024", "综艺"}, {"1053", "体育"}
    };

    private final Map<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
        put("Referer", SITE_URL);
    }};

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
    }

    /**
     * 构建分类页URL
     * tid="1" → /vod/list/n_1/o3p{pg}.html
     * tid="2" → /vod/list/n_2/o3p{pg}.html
     * 其他    → /vod/list/n_1_c_{tid}/o3p{pg}.html
     */
    private String buildCategoryUrl(String tid, String pg) {
        String path;
        if ("1".equals(tid)) {
            path = "/vod/list/n_1/o3p" + pg + ".html";
        } else if ("2".equals(tid)) {
            path = "/vod/list/n_2/o3p" + pg + ".html";
        } else {
            path = "/vod/list/n_1_c_" + tid + "/o3p" + pg + ".html";
        }
        return SITE_URL + path;
    }

    /**
     * 修正URL（补全协议）
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return url;
    }

    /**
     * 从链接中提取视频ID
     * 格式: /vod/play/{id}.html 或 /vod/{id}.html
     */
    private String extractVodId(String href) {
        if (TextUtils.isEmpty(href)) return "";
        // 匹配 /vod/play/数字.html 或 /vod/数字.html
        String id = href;
        if (href.contains("/vod/play/")) {
            id = href.replace("/vod/play/", "").replace(".html", "");
        } else if (href.contains("/vod/")) {
            id = href.replace("/vod/", "").replace(".html", "");
        }
        // 去除前导斜杠
        if (id.startsWith("/")) id = id.substring(1);
        return id.trim();
    }

    /**
     * 从列表页HTML解析视频列表
     */
    private ArrayList<Vod> parseList(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);

            // 主要选择器: .list li 或 .pic-pack-outer
            Elements items = doc.select(".list li");
            if (items.isEmpty()) {
                items = doc.select(".pic-pack-outer");
            }
            if (items.isEmpty()) {
                items = doc.select(".grid-12x li");
            }

            for (Element item : items) {
                // 提取链接
                Element linkElem = item.selectFirst("a[href*=/vod/]");
                if (linkElem == null) continue;

                String href = linkElem.attr("href");
                if (TextUtils.isEmpty(href)) continue;

                String vodId = extractVodId(href);
                if (TextUtils.isEmpty(vodId)) continue;

                // 提取标题
                String title = linkElem.attr("title");
                if (TextUtils.isEmpty(title)) {
                    title = linkElem.text();
                }
                if (TextUtils.isEmpty(title)) {
                    Element titleElem = item.selectFirst("a[title]");
                    if (titleElem != null) title = titleElem.attr("title");
                }
                if (TextUtils.isEmpty(title)) continue;

                // 提取图片
                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("src");
                    if (TextUtils.isEmpty(pic) || pic.contains("loading") || pic.contains("blank")) {
                        pic = img.attr("data-original");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                }
                pic = fixUrl(pic);

                // 提取备注（评分或标签）
                String remarks = "";
                Element ratingElem = item.selectFirst(".rating, .score, .tag, em");
                if (ratingElem != null) {
                    remarks = ratingElem.text().trim();
                }

                list.add(new Vod(vodId, title, pic, remarks));
            }
        } catch (Exception e) {
            // skip
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (String[] cat : CATEGORIES) {
            classes.add(new Class(cat[0], cat[1]));
        }

        ArrayList<Vod> list = new ArrayList<>();
        try {
            // 首页推荐：获取电影分类第1页
            String url = buildCategoryUrl("1", "1");
            String html = OkHttp.string(url, headers);
            list = parseList(html);
            if (list.size() > 30) {
                list = new ArrayList<>(list.subList(0, 30));
            }
        } catch (Exception e) {
            // skip
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";

        ArrayList<Vod> list = new ArrayList<>();
        try {
            String url = buildCategoryUrl(tid, pg);
            String html = OkHttp.string(url, headers);
            list = parseList(html);
        } catch (Exception e) {
            // skip
        }

        int page = Integer.parseInt(pg);
        // 1905每页约24条
        return Result.string(page, 72, 24, list.size(), list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();

        try {
            for (String id : ids) {
                // 详情页URL: /vod/play/{id}.html 或 /vod/{id}.html
                String url = SITE_URL + "/vod/play/" + id + ".html";

                Document doc = Jsoup.parse(OkHttp.string(url, headers));

                // 提取标题
                String name = "";
                Element titleElem = doc.selectFirst("h1");
                if (titleElem != null) {
                    name = titleElem.text().trim();
                }
                if (TextUtils.isEmpty(name)) {
                    titleElem = doc.selectFirst(".title, .vod-name, .movie-title");
                    if (titleElem != null) name = titleElem.text().trim();
                }

                // 提取封面图
                String pic = "";
                Element img = doc.selectFirst(".pic img, .poster img, .vod-pic img");
                if (img != null) {
                    pic = img.attr("src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                }
                pic = fixUrl(pic);

                // 提取导演、演员、地区、年份、简介等信息
                String director = "";
                String actor = "";
                String area = "";
                String year = "";
                String content = "";

                // 尝试从信息区域提取
                Elements infoItems = doc.select(".info li, .detail-info li, .meta span, .vod-info span");
                for (Element info : infoItems) {
                    String text = info.text();
                    if (text.contains("导演")) {
                        director = text.replaceAll("导演[:：]\\s*", "").trim();
                    } else if (text.contains("主演") || text.contains("演员")) {
                        actor = text.replaceAll("主演[:：]\\s*", "").replaceAll("演员[:：]\\s*", "").trim();
                    } else if (text.contains("地区") || text.contains("国家")) {
                        area = text.replaceAll("地区[:：]\\s*", "").replaceAll("国家[:：]\\s*", "").trim();
                    } else if (text.contains("年份") || text.contains("年代")) {
                        year = text.replaceAll("年份[:：]\\s*", "").replaceAll("年代[:：]\\s*", "").trim();
                    }
                }

                // 简介
                Element descElem = doc.selectFirst(".desc, .content, .vod-content, .brief, .summary");
                if (descElem != null) {
                    content = descElem.text().trim();
                }

                // 提取播放链接
                // 1905播放页就是当前页，播放地址为 /vod/play/{id}.html
                List<String> playFrom = new ArrayList<>();
                List<String> playUrl = new ArrayList<>();

                // 检查是否有选集（系列电影等）
                Elements episodeLinks = doc.select("a[href*=/vod/play/]");
                if (episodeLinks.size() > 1) {
                    List<String> episodes = new ArrayList<>();
                    for (Element ep : episodeLinks) {
                        String epHref = ep.attr("href");
                        String epTitle = ep.attr("title");
                        if (TextUtils.isEmpty(epTitle)) epTitle = ep.text();
                        if (TextUtils.isEmpty(epTitle)) epTitle = "正片";
                        String epId = extractVodId(epHref);
                        if (!TextUtils.isEmpty(epId)) {
                            episodes.add(epTitle + "$" + epId);
                        }
                    }
                    if (!episodes.isEmpty()) {
                        playFrom.add("1905");
                        playUrl.add(join(episodes, "#"));
                    }
                }

                // 如果没有多集，添加单集
                if (playFrom.isEmpty()) {
                    playFrom.add("1905");
                    playUrl.add("正片$" + id);
                }

                Vod vod = new Vod();
                vod.setVodId(id);
                vod.setVodName(name);
                vod.setVodPic(pic);
                vod.setVodDirector(director);
                vod.setVodActor(actor);
                vod.setVodArea(area);
                vod.setVodYear(year);
                vod.setVodContent(content);
                vod.setVodPlayFrom(join(playFrom, "$$$"));
                vod.setVodPlayUrl(join(playUrl, "$$$"));

                list.add(vod);
            }
        } catch (Exception e) {
            // skip
        }

        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String url = SITE_URL + "/search/?q=" + URLEncoder.encode(key, "UTF-8");
            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);

            // 搜索结果解析
            Elements items = doc.select(".search-result li, .result-list li, .list li");
            if (items.isEmpty()) {
                items = doc.select("a[href*=/vod/]");
            }

            List<String> seen = new ArrayList<>();
            for (Element item : items) {
                Element linkElem = item.tagName().equals("a") ? item : item.selectFirst("a[href*=/vod/]");
                if (linkElem == null) continue;

                String href = linkElem.attr("href");
                if (TextUtils.isEmpty(href)) continue;

                String vodId = extractVodId(href);
                if (TextUtils.isEmpty(vodId) || seen.contains(vodId)) continue;
                seen.add(vodId);

                // 提取标题
                String title = linkElem.attr("title");
                if (TextUtils.isEmpty(title)) title = linkElem.text();
                if (TextUtils.isEmpty(title)) continue;

                // 提取图片
                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                }
                pic = fixUrl(pic);

                list.add(new Vod(vodId, title, pic));
            }
        } catch (Exception e) {
            // skip
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 1905是正版网站，大部分内容需VIP，使用parse=1让客户端解析器处理
        String playUrl = SITE_URL + "/vod/play/" + id + ".html";

        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", SITE_URL);

        return Result.get().url(playUrl).header(header).parse().string();
    }

    /**
     * 连接字符串列表
     */
    private String join(List<String> list, String separator) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
