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
 * 韩剧网爬虫
 * 网站地址：https://321tw.com（新地址：https://www.3kor.com）
 * 类型：韩剧、韩影、韩综
 *
 * @author Trae
 * @date 2026-07-14
 */
public class Hjw extends Spider {

    private static final String SITE_URL = "https://www.3kor.com";
    private String siteUrl = SITE_URL;

    private final Map<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
        put("Referer", SITE_URL);
    }};

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);

        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                siteUrl = extend;
            }
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();

        // 分类列表（根据网站实际分类）
        classes.add(new Class("1", "韓劇"));
        classes.add(new Class("3", "韓國電影"));
        classes.add(new Class("4", "韓國綜藝"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 提取首页视频列表（多级选择器兜底）
            Elements items = doc.select("a[href*=/detail/]");
            if (items.isEmpty()) {
                items = doc.select("div.module-items a, li a");
            }

            for (Element item : items) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/detail/")) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) {
                    Element titleElem = item.selectFirst("span, p, strong");
                    if (titleElem != null) {
                        title = titleElem.text();
                    }
                }

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                }

                if (TextUtils.isEmpty(title)) continue;

                // 从详情页URL提取ID（格式：/detail/3504.html）
                String vid = href;
                if (href.contains("/detail/")) {
                    String[] parts = href.split("/");
                    for (String part : parts) {
                        if (part.contains(".html")) {
                            vid = part.replace(".html", "");
                            break;
                        }
                    }
                }

                if (seen.contains(vid)) continue;
                seen.add(vid);

                pic = fixUrl(pic);

                list.add(new Vod(vid, title, pic));

                // 限制首页数量
                if (list.size() >= 30) break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();

        try {
            // 分类页URL：根据分类构造URL（格式：/list/{分类ID}---{页码}.html）
            String url;
            if ("1".equals(tid)) {
                url = siteUrl + "/list/1---" + pg + ".html";
            } else if ("3".equals(tid)) {
                url = siteUrl + "/list/3---" + pg + ".html";
            } else if ("4".equals(tid)) {
                url = siteUrl + "/list/4---" + pg + ".html";
            } else {
                url = siteUrl + "/list/" + tid + "---" + pg + ".html";
            }

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            Elements items = doc.select("a[href*=/detail/]");
            if (items.isEmpty()) {
                items = doc.select("div.module-items a, li a");
            }

            for (Element item : items) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/detail/")) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) {
                    Element titleElem = item.selectFirst("span, p, strong");
                    if (titleElem != null) {
                        title = titleElem.text();
                    }
                }

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                }

                if (TextUtils.isEmpty(title)) continue;

                String vid = href;
                if (href.contains("/detail/")) {
                    String[] parts = href.split("/");
                    for (String part : parts) {
                        if (part.contains(".html")) {
                            vid = part.replace(".html", "");
                            break;
                        }
                    }
                }

                if (seen.contains(vid)) continue;
                seen.add(vid);

                pic = fixUrl(pic);

                list.add(new Vod(vid, title, pic));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 72, 72, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        List<Vod> list = new ArrayList<>();

        try {
            for (String id : ids) {
                // 详情页URL：/detail/{ID}.html
                String url = siteUrl + "/detail/" + id + ".html";

                Document doc = Jsoup.parse(OkHttp.string(url, headers));

                // 提取剧名（多级选择器兜底）
                String name = "";
                Element titleElem = doc.selectFirst("h1, h2, .title, .module-title");
                if (titleElem != null) {
                    name = titleElem.text();
                }

                // 提取图片（多级选择器兜底）
                String pic = "";
                Element img = doc.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                    pic = fixUrl(pic);
                }

                // 提取播放源和剧集（MacCMS标准结构）
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 查找播放源标签（MacCMS标准：.module-tab-item）
                Elements sourceTabs = doc.select(".module-tab-item, .module-blocklist a[data-toggle]");

                if (sourceTabs.isEmpty()) {
                    // 如果没有找到播放源标签，查找所有剧集链接
                    // 网站使用锚点跳转，实际播放链接在详情页
                    Elements playLinks = doc.select("a[href*=/detail/]");
                    if (playLinks.isEmpty()) {
                        playLinks = doc.select("a");
                    }

                    if (!playLinks.isEmpty()) {
                        sources.add("默認");

                        List<String> eps = new ArrayList<>();
                        int epCount = 1;
                        for (Element link : playLinks) {
                            String epName = link.text();
                            String epUrl = link.attr("href");
                            // 过滤掉非剧集链接
                            if (TextUtils.isEmpty(epName) || TextUtils.isEmpty(epUrl)) continue;
                            if (!epUrl.contains("/detail/")) continue;

                            // 构造播放链接（格式：第01集$/detail/3599.html#m）
                            if (epName.contains("第") && epName.contains("集")) {
                                // 使用详情页URL + 播放锚点
                                String playId = id + ".html#m";
                                eps.add(epName + "$" + playId);
                            }
                        }

                        if (!eps.isEmpty()) {
                            episodes.add(join(eps, "#"));
                        }
                    }
                } else {
                    for (Element sourceTab : sourceTabs) {
                        String sourceName = sourceTab.attr("title");
                        if (TextUtils.isEmpty(sourceName)) {
                            sourceName = sourceTab.text();
                        }
                        if (TextUtils.isEmpty(sourceName)) {
                            sourceName = "線路" + (sources.size() + 1);
                        }

                        sources.add(sourceName);

                        // 查找该播放源对应的剧集列表
                        String dataTab = sourceTab.attr("data-tab");
                        String dataToggle = sourceTab.attr("data-toggle");

                        Element playList = null;
                        if (!TextUtils.isEmpty(dataTab)) {
                            playList = doc.selectFirst("div[data-tab=" + dataTab + "]");
                            if (playList == null) {
                                playList = doc.selectFirst("ul[data-tab=" + dataTab + "]");
                            }
                        }
                        if (playList == null && !TextUtils.isEmpty(dataToggle)) {
                            playList = doc.selectFirst("div[data-toggle=" + dataToggle + "]");
                            if (playList == null) {
                                playList = doc.selectFirst("ul[data-toggle=" + dataToggle + "]");
                            }
                        }

                        if (playList != null) {
                            List<String> eps = new ArrayList<>();
                            for (Element link : playList.select("a")) {
                                String epName = link.text();
                                String epUrl = link.attr("href");
                                if (!TextUtils.isEmpty(epName) && !TextUtils.isEmpty(epUrl)) {
                                    // 构造播放链接（格式：第01集$/detail/3599.html#m）
                                    String playId = id + ".html#m";
                                    eps.add(epName + "$" + playId);
                                }
                            }

                            if (!eps.isEmpty()) {
                                episodes.add(join(eps, "#"));
                            }
                        } else {
                            // 如果找不到对应的剧集列表，使用全局搜索
                            Elements playLinks = doc.select("a");
                            List<String> eps = new ArrayList<>();
                            for (Element link : playLinks) {
                                String epName = link.text();
                                String epUrl = link.attr("href");
                                if (!TextUtils.isEmpty(epName) && !TextUtils.isEmpty(epUrl)) {
                                    // 构造播放链接
                                    String playId = id + ".html#m";
                                    eps.add(epName + "$" + playId);
                                }
                            }

                            if (!eps.isEmpty() && episodes.isEmpty()) {
                                episodes.add(join(eps, "#"));
                            }
                        }
                    }
                }

                if (sources.isEmpty()) {
                    sources.add("默認");
                    episodes.add("暫無資源$");
                }

                Vod vod = new Vod(id, name, pic);
                vod.setVodPlayFrom(join(sources, "$$$"));
                vod.setVodPlayUrl(join(episodes, "$$$"));

                list.add(vod);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();

        try {
            // 搜索URL：/search/{关键词}/page/{页码}.html（推测）
            String url = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + "/page/1.html";

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            Elements items = doc.select("a[href*=/detail/]");
            if (items.isEmpty()) {
                items = doc.select("div.module-items a, li a");
            }

            for (Element item : items) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/detail/")) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) {
                    Element titleElem = item.selectFirst("span, p, strong");
                    if (titleElem != null) {
                        title = titleElem.text();
                    }
                }

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                }

                if (TextUtils.isEmpty(title)) continue;

                String vid = href;
                if (href.contains("/detail/")) {
                    String[] parts = href.split("/");
                    for (String part : parts) {
                        if (part.contains(".html")) {
                            vid = part.replace(".html", "");
                            break;
                        }
                    }
                }

                if (seen.contains(vid)) continue;
                seen.add(vid);

                pic = fixUrl(pic);

                list.add(new Vod(vid, title, pic));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 如果是完整的播放链接，直接返回
            String playUrl = id;
            if (!id.startsWith("http")) {
                playUrl = siteUrl + id;
            }

            // MacCMS站点，返回parse=1让客户端解析器处理
            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", Util.CHROME);
            header.put("Referer", siteUrl);

            return Result.get()
                    .parse(1)
                    .url(playUrl)
                    .header(header)
                    .string();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().parse(1).url("").string();
    }

    /**
     * 修正URL（补全协议和域名）
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        if (url.startsWith("//")) {
            return "https:" + url;
        } else if (url.startsWith("/")) {
            return siteUrl + url;
        }

        return url;
    }

    /**
     * 连接字符串列表
     */
    private String join(List<String> list, String separator) {
        if (list == null || list.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(list.get(i));
        }

        return sb.toString();
    }
}