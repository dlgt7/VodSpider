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

            // 提取首页视频列表
            // 图片在a标签的data-original属性上（<a class="tu lazyload" data-original="//pic.3kor.com/pics/xxx.jpg">）
            Elements items = doc.select("a.tu.lazyload[href*=/detail/]");
            if (items.isEmpty()) {
                items = doc.select("a[href*=/detail/]");
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

                if (TextUtils.isEmpty(title)) continue;

                // 图片优先从a标签的data-original提取
                String pic = item.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) {
                            pic = img.attr("src");
                        }
                    }
                }
                pic = fixUrl(pic);

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
            String url = siteUrl + "/list/" + tid + "---" + pg + ".html";

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 分类页HTML结构：<a class="tu lazyload" title="xxx" href="/detail/xxx.html" data-original="//pic.3kor.com/pics/xxx.jpg">
            // 图片在a标签的data-original属性上，不是img标签
            Elements items = doc.select("a.tu.lazyload[href*=/detail/]");
            if (items.isEmpty()) {
                items = doc.select("a[href*=/detail/]");
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

                if (TextUtils.isEmpty(title)) continue;

                // 图片在a标签的data-original属性上
                String pic = item.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    // 兜底：从子img标签提取
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) {
                            pic = img.attr("src");
                        }
                    }
                }
                pic = fixUrl(pic);

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

                // 提取图片
                // 真实HTML结构：<div class="pic"><img class="lazyload" data-original="//pic.3kor.com/pics/3707.jpg" />
                String pic = "";

                // 优先从 div.pic 下的 img.lazyload 提取
                Element img = doc.selectFirst("div.pic img.lazyload");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("data-src");
                    }
                    if (TextUtils.isEmpty(pic)) {
                        pic = img.attr("src");
                    }
                }

                // 兜底：遍历所有img查找封面图
                if (TextUtils.isEmpty(pic)) {
                    Elements imgs = doc.select("img");
                    for (Element imgElem : imgs) {
                        // 跳过演员头像和评论头像
                        String className = imgElem.className();
                        if (className.contains("cast") || className.contains("comment") || className.contains("avatar")) continue;

                        String src = imgElem.attr("data-original");
                        if (TextUtils.isEmpty(src) || src.startsWith("data:image") || src.contains("loading.gif")) {
                            src = imgElem.attr("data-src");
                        }
                        if (TextUtils.isEmpty(src) || src.startsWith("data:image")) {
                            src = imgElem.attr("src");
                        }
                        // 跳过loading占位图和头像
                        if (TextUtils.isEmpty(src) || src.startsWith("data:image") || src.contains("loading.gif") || src.contains("/images/")) continue;

                        pic = src;
                        break;
                    }
                }

                pic = fixUrl(pic);

                // 提取播放源和剧集
                // 真实HTML结构：<div class="play"><ul><li><a href="#m" onclick="bb_a('3707_1_1','第01集',event)">第01集</a></li>...
                // href只是#m锚点，onclick中包含真实选集标识（视频ID_线路ID_集数）
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 从 div.play 区域提取选集
                Elements playLinks = doc.select("div.play a[href='#m']");
                if (playLinks.isEmpty()) {
                    playLinks = doc.select("a[href='#m']");
                }

                if (!playLinks.isEmpty()) {
                    // 按线路分组：从onclick提取线路ID（格式：bb_a('视频ID_线路ID_集数','集名',event)）
                    Map<String, List<String>> routeEpisodes = new HashMap<>();
                    Map<String, Integer> routeOrder = new HashMap<>();
                    int order = 0;

                    for (Element link : playLinks) {
                        String epName = link.text();
                        if (TextUtils.isEmpty(epName) || !epName.contains("第") || !epName.contains("集")) continue;

                        // 从onclick提取选集标识，格式：bb_a('3707_1_1','第01集',event)
                        String onclick = link.attr("onclick");
                        String routeId = "1"; // 默认线路1
                        String epId = "";

                        if (!TextUtils.isEmpty(onclick) && onclick.contains("bb_a(")) {
                            // 提取onclick中的参数：'3707_1_1'
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile("bb_a\\('([^']+)'");
                            java.util.regex.Matcher m = p.matcher(onclick);
                            if (m.find()) {
                                epId = m.group(1); // 如 "3707_1_1"
                                String[] parts = epId.split("_");
                                if (parts.length >= 2) {
                                    routeId = parts[1]; // 线路ID
                                }
                            }
                        }

                        // 构造播放URL：使用详情页URL + #m（客户端嗅探）
                        String playUrl = "/" + id + ".html#m";
                        if (!TextUtils.isEmpty(epId)) {
                            playUrl = "/" + id + ".html#m";
                        }

                        if (!routeEpisodes.containsKey(routeId)) {
                            routeEpisodes.put(routeId, new ArrayList<>());
                            routeOrder.put(routeId, order++);
                        }
                        routeEpisodes.get(routeId).add(epName + "$" + playUrl);
                    }

                    // 按顺序添加线路
                    for (String routeId : routeOrder.keySet()) {
                        List<String> eps = routeEpisodes.get(routeId);
                        if (!eps.isEmpty()) {
                            sources.add("線路" + (sources.size() + 1));
                            episodes.add(join(eps, "#"));
                        }
                    }
                }

                // 如果没有找到剧集，添加占位符
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

            Elements items = doc.select("a.tu.lazyload[href*=/detail/]");
            if (items.isEmpty()) {
                items = doc.select("a[href*=/detail/]");
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

                if (TextUtils.isEmpty(title)) continue;

                // 图片优先从a标签的data-original提取
                String pic = item.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) {
                            pic = img.attr("src");
                        }
                    }
                }
                pic = fixUrl(pic);

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