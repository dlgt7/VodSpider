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
 * 久久韩剧爬虫
 * 网站地址：https://www.jjhj.cc
 * 类型：韩剧、日剧、泰剧、韩国电影、综艺
 *
 * @author Trae
 * @date 2026-07-14
 */
public class Jjhj extends Spider {

    private static final String SITE_URL = "https://www.jjhj.cc";
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
        // URL格式：/frim/list{分类ID}.html（分类页）
        classes.add(new Class("1", "韩剧"));
        classes.add(new Class("2", "日剧"));
        classes.add(new Class("3", "泰剧"));
        classes.add(new Class("4", "韩国电影"));
        classes.add(new Class("5", "综艺"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 提取首页视频列表（使用通用的详情链接选择器）
            for (Element item : doc.select("a[href*=/view/]")) {
                String href = item.attr("href");
                String title = "";
                String pic = "";

                // 提取剧名（优先使用title属性，否则使用链接文本）
                title = item.attr("title");
                if (TextUtils.isEmpty(title)) {
                    title = item.text();
                }

                // 提取图片（如果存在）
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

                if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

                // 从详情页URL提取ID（格式：/view/11789.html）
                String vid = href;
                if (href.contains("/view/")) {
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

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();

        try {
            // 分类页URL：/frim/list{分类ID}-{页码}.html
            String url = siteUrl + "/frim/list" + tid + "-" + pg + ".html";

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 分类页使用简单的列表结构，提取所有详情链接
            for (Element item : doc.select("a[href*=/view/]")) {
                String href = item.attr("href");
                String title = "";

                // 提取剧名（优先使用title属性，否则使用链接文本）
                title = item.attr("title");
                if (TextUtils.isEmpty(title)) {
                    title = item.text();
                }

                if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

                // 过滤掉非详情页链接
                if (!href.contains("/view/")) continue;

                String vid = href;
                if (href.contains("/view/")) {
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

                // 分类页通常没有图片，使用空字符串
                String pic = "";

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
                // 详情页URL：/view/{ID}.html
                String url = siteUrl + "/view/" + id + ".html";

                Document doc = Jsoup.parse(OkHttp.string(url, headers));

                // 提取剧名
                String name = "";
                Element titleElem = doc.selectFirst("h1, h2, .title");
                if (titleElem != null) {
                    name = titleElem.text();
                }

                // 提取图片（网站图片可能通过懒加载）
                String pic = "";
                Element img = doc.selectFirst("img");
                if (img != null) {
                    // 尝试多个可能的图片属性
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic) || pic.equals("data:image")) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic) || pic.equals("data:image")) {
                        pic = img.attr("src");
                    }
                    // 过滤掉base64占位符
                    if (!TextUtils.isEmpty(pic) && pic.startsWith("data:image")) {
                        pic = "";
                    }
                    pic = fixUrl(pic);
                }

                // 提取播放源和剧集
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 网站有多个播放源（高清云、韩剧云、量子云、百度云、超清4k）
                // 播放源标签格式：<a href="...#">高清云</a>
                Elements sourceTabs = doc.select("a[href*='/view/'][href*='#']");

                if (sourceTabs.isEmpty()) {
                    // 如果没有找到播放源标签，提取所有播放链接作为默认
                    Elements playLinks = doc.select("a[href*='/play/']");
                    if (!playLinks.isEmpty()) {
                        sources.add("默认");

                        List<String> eps = new ArrayList<>();
                        for (Element link : playLinks) {
                            String epName = link.text();
                            String epUrl = link.attr("href");
                            if (!TextUtils.isEmpty(epName) && !TextUtils.isEmpty(epUrl)) {
                                eps.add(epName + "$" + epUrl);
                            }
                        }

                        if (!eps.isEmpty()) {
                            episodes.add(join(eps, "#"));
                        }
                    }
                } else {
                    // 有播放源标签，提取播放源名称
                    for (Element sourceTab : sourceTabs) {
                        String sourceName = sourceTab.text();
                        if (TextUtils.isEmpty(sourceName)) {
                            sourceName = "线路" + (sources.size() + 1);
                        }
                        sources.add(sourceName);
                    }

                    // 提取所有播放链接并按线路ID分组
                    // 播放链接格式：/play/{视频ID}-{线路ID}-{剧集ID}.html
                    Elements playLinks = doc.select("a[href*='/play/']");
                    Map<String, List<String>> routeEpisodes = new HashMap<>();

                    for (Element link : playLinks) {
                        String epName = link.text();
                        String epUrl = link.attr("href");

                        if (TextUtils.isEmpty(epName) || TextUtils.isEmpty(epUrl)) continue;

                        // 从URL提取线路ID：/play/11764-2-0.html -> 2
                        String routeId = "";
                        String[] urlParts = epUrl.split("-");
                        if (urlParts.length >= 2) {
                            routeId = urlParts[urlParts.length - 2]; // 倒数第二部分是线路ID
                        }

                        if (TextUtils.isEmpty(routeId)) routeId = "0";

                        // 添加到对应线路的剧集列表
                        if (!routeEpisodes.containsKey(routeId)) {
                            routeEpisodes.put(routeId, new ArrayList<>());
                        }
                        routeEpisodes.get(routeId).add(epName + "$" + epUrl);
                    }

                    // 将每条线路的剧集添加到episodes
                    for (List<String> eps : routeEpisodes.values()) {
                        if (!eps.isEmpty()) {
                            episodes.add(join(eps, "#"));
                        }
                    }
                }

                if (sources.isEmpty()) {
                    sources.add("默认");
                    episodes.add("暂无资源$");
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
            // 搜索URL：/search.php?searchword={关键词}
            String url = siteUrl + "/search.php?searchword=" + URLEncoder.encode(key, "UTF-8");

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 搜索结果页使用通用的详情链接选择器
            for (Element item : doc.select("a[href*=/view/]")) {
                String href = item.attr("href");
                String title = "";
                String pic = "";

                // 提取剧名（优先使用title属性，否则使用链接文本）
                title = item.attr("title");
                if (TextUtils.isEmpty(title)) {
                    title = item.text();
                }

                // 提取图片（如果存在）
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

                if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

                String vid = href;
                if (href.contains("/view/")) {
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