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
    private static final String ALT_SITE_URL = "http://www.99hanju.cc"; // 备用域名
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
        // URL格式：/frim/list{分类ID}.html
        classes.add(new Class("1", "韩剧"));
        classes.add(new Class("3", "日剧"));
        classes.add(new Class("4", "泰剧"));
        classes.add(new Class("5", "韩国电影"));
        classes.add(new Class("6", "日韩综艺"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 首页图片在 a.fed-list-pics.fed-lazy 的 data-original 属性上
            // 优先匹配含图片的链接
            for (Element item : doc.select("a.fed-list-pics[href*=/view/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/view/")) continue;

                String title = "";
                // 查找同级的标题链接
                Element titleLink = item.parent().selectFirst("a.fed-list-title[href*=/view/]");
                if (titleLink != null) {
                    title = titleLink.text();
                }
                if (TextUtils.isEmpty(title)) {
                    title = item.attr("title");
                }

                // 图片在a标签的data-original属性上
                String pic = item.attr("data-original");
                pic = fixUrl(pic);

                if (TextUtils.isEmpty(title)) continue;

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底：如果上面的选择器没有匹配到，使用通用方式
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href*=/view/]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !href.contains("/view/")) continue;

                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) title = item.text();
                    if (TextUtils.isEmpty(title)) continue;

                    String pic = item.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        Element img = item.selectFirst("img");
                        if (img != null) {
                            pic = img.attr("data-original");
                            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                        }
                    }
                    pic = fixUrl(pic);

                    String vid = normalizeVid(href);
                    if (seen.contains(vid)) continue;
                    seen.add(vid);

                    list.add(new Vod(vid, title, pic));
                }
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
            // 分类页URL：/frim/list{分类ID}.html（第1页），/frim/list{分类ID}_{页码}.html（第2页起）
            String url;
            if ("1".equals(pg)) {
                url = siteUrl + "/frim/list" + tid + ".html";
            } else {
                url = siteUrl + "/frim/list" + tid + "_" + pg + ".html";
            }

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 分类页图片在 a.fed-list-pics.fed-lazy 的 data-original 属性上
            for (Element item : doc.select("a.fed-list-pics[href*=/view/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/view/")) continue;

                String title = "";
                Element titleLink = item.parent().selectFirst("a.fed-list-title[href*=/view/]");
                if (titleLink != null) {
                    title = titleLink.text();
                }
                if (TextUtils.isEmpty(title)) {
                    title = item.attr("title");
                }

                String pic = item.attr("data-original");
                pic = fixUrl(pic);

                if (TextUtils.isEmpty(title)) continue;

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href*=/view/]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !href.contains("/view/")) continue;

                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) title = item.text();
                    if (TextUtils.isEmpty(title)) continue;

                    String pic = item.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        Element img = item.selectFirst("img");
                        if (img != null) {
                            pic = img.attr("data-original");
                            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                        }
                    }
                    pic = fixUrl(pic);

                    String vid = normalizeVid(href);
                    if (seen.contains(vid)) continue;
                    seen.add(vid);

                    list.add(new Vod(vid, title, pic));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 48, list.size()).string();
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

                // 提取图片（封面在data-original属性上，可能是a标签或div的背景图）
                // 真实HTML：<a data-original="/uploads/allimg/xxx.jpg" style="background-image:url(...)">
                String pic = "";

                // 从fed-list-pics或含data-original的封面元素提取
                Element coverElem = doc.selectFirst("a.fed-list-pics[data-original]");
                if (coverElem != null) {
                    pic = coverElem.attr("data-original");
                }
                if (TextUtils.isEmpty(pic)) {
                    // 从fed-play-data的同级查找
                    Element playData = doc.selectFirst("div.fed-play-data");
                    if (playData != null) {
                        Element cover = playData.parent().selectFirst("[data-original]");
                        if (cover != null) {
                            pic = cover.attr("data-original");
                        }
                    }
                }
                if (TextUtils.isEmpty(pic)) {
                    // 遍历所有含data-original的元素，查找封面图
                    for (Element elem : doc.select("[data-original]")) {
                        String src = elem.attr("data-original");
                        if (!TextUtils.isEmpty(src) && src.contains("/uploads/allimg/") && !src.contains(".webp") == false) {
                            // 优先选择jpg/png格式的大图
                            pic = src;
                            if (src.contains(".jpg") || src.contains(".png")) {
                                break;
                            }
                        }
                    }
                }
                pic = fixUrl(pic);

                // 提取播放源和剧集
                // MacCMS结构：线路标签 <li class="fed-drop-btns"><a>高清云</a></li>
                // 播放列表 div#playlist1, div#playlist2 等
                // 播放链接格式：/play/{视频ID}-{线路ID}-{集数ID}.html
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 提取线路标签
                Elements sourceTabs = doc.select("li.fed-drop-btns a");
                List<String> sourceNames = new ArrayList<>();
                for (Element tab : sourceTabs) {
                    String sourceName = tab.text();
                    if (!TextUtils.isEmpty(sourceName)) {
                        sourceNames.add(sourceName);
                    }
                }

                // 按playlist div分组提取选集
                int playlistIndex = 0;
                for (Element playlist : doc.select("div.fed-play-item")) {
                    Elements playLinks = playlist.select("a[href*='/play/']");
                    if (playLinks.isEmpty()) continue;

                    List<String> eps = new ArrayList<>();
                    for (Element link : playLinks) {
                        String epName = link.text();
                        String epUrl = link.attr("href");

                        if (TextUtils.isEmpty(epName) || TextUtils.isEmpty(epUrl)) continue;
                        // 过滤"立即播放"
                        if ("立即播放".equals(epName)) continue;

                        eps.add(epName + "$" + fixUrl(epUrl));
                    }

                    if (!eps.isEmpty()) {
                        // 使用线路名称，如果没有则用"线路N"
                        String sourceName = playlistIndex < sourceNames.size() ?
                                sourceNames.get(playlistIndex) : "线路" + (sources.size() + 1);
                        sources.add(sourceName);
                        episodes.add(join(eps, "#"));
                        playlistIndex++;
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

            // 搜索结果页图片在 a.fed-list-pics 的 data-original 属性上
            for (Element item : doc.select("a.fed-list-pics[href*=/view/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/view/")) continue;

                String title = "";
                Element titleLink = item.parent().selectFirst("a.fed-list-title[href*=/view/]");
                if (titleLink != null) {
                    title = titleLink.text();
                }
                if (TextUtils.isEmpty(title)) {
                    title = item.attr("title");
                }

                String pic = item.attr("data-original");
                pic = fixUrl(pic);

                if (TextUtils.isEmpty(title)) continue;

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href*=/view/]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !href.contains("/view/")) continue;

                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) title = item.text();
                    if (TextUtils.isEmpty(title)) continue;

                    String pic = item.attr("data-original");
                    if (TextUtils.isEmpty(pic)) {
                        Element img = item.selectFirst("img");
                        if (img != null) {
                            pic = img.attr("data-original");
                            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                        }
                    }
                    pic = fixUrl(pic);

                    String vid = normalizeVid(href);
                    if (seen.contains(vid)) continue;
                    seen.add(vid);

                    list.add(new Vod(vid, title, pic));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 播放页JS中包含直链：var now="https://v8.yuglf.com/.../index.m3u8"
            // 格式：/play/{视频ID}-{线路ID}-{集数ID}.html
            String playUrl = id;
            if (!id.startsWith("http")) {
                playUrl = siteUrl + id;
            }

            // 请求播放页，从JS中提取m3u8/mp4直链
            String html = OkHttp.string(playUrl, headers);

            // 提取 var now="..." 中的URL
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("var\\s+now\\s*=\\s*[\"'](https?://[^\"']+)[\"']");
            java.util.regex.Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String m3u8Url = matcher.group(1);

                Map<String, String> header = new HashMap<>();
                header.put("User-Agent", Util.CHROME);
                header.put("Referer", playUrl);

                // parse=0 直接播放m3u8
                return Result.get()
                        .parse(0)
                        .url(m3u8Url)
                        .header(header)
                        .string();
            }

            // 如果没有提取到直链，回退到嗅探模式
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
     * 修正URL（补全协议和域名，支持多域名）
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        if (url.startsWith("//")) {
            return "https:" + url;
        } else if (url.startsWith("/")) {
            return siteUrl + url;
        } else if (url.startsWith("http://www.99hanju.cc")) {
            // 备用域名链接，统一转换为主域名
            return url.replace("http://www.99hanju.cc", SITE_URL);
        }

        return url;
    }

    /**
     * 标准化视频ID（从URL提取，统一处理多域名）
     */
    private String normalizeVid(String href) {
        if (TextUtils.isEmpty(href)) {
            return "";
        }

        // 处理两个域名的链接
        String url = href;
        if (url.startsWith("http://www.99hanju.cc")) {
            url = url.replace("http://www.99hanju.cc", SITE_URL);
        }

        // 从详情页URL提取ID（格式：/view/11789.html 或 https://www.jjhj.cc/view/11789.html）
        String vid = url;
        if (url.contains("/view/")) {
            String[] parts = url.split("/");
            for (String part : parts) {
                if (part.contains(".html")) {
                    vid = part.replace(".html", "");
                    break;
                }
            }
        }

        return vid;
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