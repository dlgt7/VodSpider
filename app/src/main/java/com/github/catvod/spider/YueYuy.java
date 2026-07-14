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
 * 粤语影院爬虫
 * 网站地址：https://www.yueyuy.com
 * 类型：电影、电视剧、综艺、动漫、短剧、港剧
 *
 * @author Trae
 * @date 2026-07-14
 */
public class YueYuy extends Spider {

    private static final String SITE_URL = "https://www.yueyuy.com";
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
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();

        // 分类列表（根据网站实际分类）
        // URL格式：/s/id/{分类ID}.html
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("35", "短剧"));
        classes.add(new Class("14", "港剧"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 首页视频链接格式：/vod/{id}.html，图片在data-original属性上
            // 优先匹配含图片的链接
            for (Element item : doc.select("a[href*=/vod/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/vod/")) continue;

                // 只匹配包含图片的链接（视频卡片）
                String pic = item.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                }
                if (TextUtils.isEmpty(pic)) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (TextUtils.isEmpty(title)) continue;

                pic = fixUrl(pic);

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底：如果上面的选择器没有匹配到，使用更宽松的方式
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href*=/vod/]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !href.contains("/vod/")) continue;

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
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        try {
            // 分类页URL：/s/id/{分类ID}.html（第1页），/s/id/{分类ID}_{页码}.html（第2页起）
            String url;
            if ("1".equals(pg)) {
                url = siteUrl + "/s/id/" + tid + ".html";
            } else {
                url = siteUrl + "/s/id/" + tid + "_" + pg + ".html";
            }

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 分类页视频列表
            for (Element item : doc.select("a[href*=/vod/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/vod/")) continue;

                // 只匹配包含图片的链接（视频卡片）
                String pic = item.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                }
                if (TextUtils.isEmpty(pic)) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (TextUtils.isEmpty(title)) continue;

                pic = fixUrl(pic);

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href*=/vod/]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !href.contains("/vod/")) continue;

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
    public String detailContent(List<String> ids) throws Exception {
        List<Vod> list = new ArrayList<>();

        try {
            for (String id : ids) {
                // 详情页URL：/vod/{ID}.html
                String url = siteUrl + "/vod/" + id + ".html";

                Document doc = Jsoup.parse(OkHttp.string(url, headers));

                // 提取剧名（详情页h1标签）
                String name = "";
                Element titleElem = doc.selectFirst("h1");
                if (titleElem != null) {
                    name = titleElem.text();
                }

                // 提取图片（封面在data-original属性上）
                String pic = "";
                Element coverElem = doc.selectFirst("[data-original]");
                if (coverElem != null) {
                    pic = coverElem.attr("data-original");
                }
                if (TextUtils.isEmpty(pic)) {
                    Element img = doc.selectFirst("img[src*=/uploads/]");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                }
                pic = fixUrl(pic);

                // 提取播放源和剧集
                // 线路标签：<a href="#playlist1">线路名</a>
                // 播放列表：div#playlist1, div#playlist2 等，包含a链接
                // 播放链接格式：/play/{视频ID}-{线路ID}-{集数ID}.html
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 提取线路标签
                Elements sourceTabs = doc.select("a[href*=#playlist]");
                List<String> sourceNames = new ArrayList<>();
                for (Element tab : sourceTabs) {
                    String sourceName = tab.text();
                    if (!TextUtils.isEmpty(sourceName)) {
                        sourceNames.add(sourceName);
                    }
                }

                // 按playlist div分组提取选集
                int playlistIndex = 0;
                for (Element playlist : doc.select("div[id^=playlist]")) {
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
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();

        try {
            // 搜索URL：/search.html?wd={关键词}
            String url = siteUrl + "/search.html?wd=" + URLEncoder.encode(key, "UTF-8");

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 搜索结果页视频链接
            for (Element item : doc.select("a[href*=/vod/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/vod/")) continue;

                // 只匹配包含图片的链接
                String pic = item.attr("data-original");
                if (TextUtils.isEmpty(pic)) {
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                }
                if (TextUtils.isEmpty(pic)) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (TextUtils.isEmpty(title)) continue;

                pic = fixUrl(pic);

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底：如果带图片的选择器没有匹配到
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href*=/vod/]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !href.contains("/vod/")) continue;

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
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 播放页JS中可能包含直链：var now="https://.../index.m3u8"
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
     * 标准化视频ID（从URL提取数字ID）
     */
    private String normalizeVid(String href) {
        if (TextUtils.isEmpty(href)) {
            return "";
        }

        // 从详情页URL提取ID（格式：/vod/20770.html 或 https://www.yueyuy.com/vod/20770.html）
        String vid = href;
        if (href.contains("/vod/")) {
            String[] parts = href.split("/");
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
