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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 港剧TV网/轻映短剧爬虫
 * 网站地址：https://hbcfjx.com/
 * 类型：短剧（重生、穿越、爽剧、言情、都市、古装、悬疑、剧情）
 * CMS：MacCMS v8
 *
 * @author Trae
 * @date 2026-07-14
 */
public class HbCfjx extends Spider {

    private static final String SITE_URL = "https://hbcfjx.com";
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

        // 分类列表（短剧分类）
        // URL格式：/dj/{分类ID}.html
        classes.add(new Class("1", "重生"));
        classes.add(new Class("2", "穿越"));
        classes.add(new Class("3", "爽剧"));
        classes.add(new Class("4", "言情"));
        classes.add(new Class("5", "都市"));
        classes.add(new Class("6", "古装"));
        classes.add(new Class("7", "悬疑"));
        classes.add(new Class("8", "剧情"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 首页视频项：链接指向 /djok/{id}.html
            // 优先匹配含图片的链接
            for (Element item : doc.select("a[href*=/djok/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/djok/")) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (TextUtils.isEmpty(title)) continue;

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                }
                pic = fixUrl(pic);

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

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
            // 分类页URL：/dj/{tid}.html（第1页），/dj/{tid}-{pg}.html（第2页起）
            String url;
            if ("1".equals(pg)) {
                url = siteUrl + "/dj/" + tid + ".html";
            } else {
                url = siteUrl + "/dj/" + tid + "-" + pg + ".html";
            }

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 分类页视频项：链接指向 /djok/{id}.html
            for (Element item : doc.select("a[href*=/djok/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/djok/")) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (TextUtils.isEmpty(title)) continue;

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                }
                pic = fixUrl(pic);

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
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
                // 详情页URL：/djok/{ID}.html
                String url = siteUrl + "/djok/" + id + ".html";

                Document doc = Jsoup.parse(OkHttp.string(url, headers));

                // 提取剧名
                String name = "";
                Element titleElem = doc.selectFirst("h1, h2, .title");
                if (titleElem != null) {
                    name = titleElem.text();
                }

                // 提取图片
                String pic = "";
                Element coverElem = doc.selectFirst("[data-original]");
                if (coverElem != null) {
                    pic = coverElem.attr("data-original");
                }
                if (TextUtils.isEmpty(pic)) {
                    Element imgElem = doc.selectFirst("img[src*=/uploads/], img[src*=pic]");
                    if (imgElem != null) {
                        pic = imgElem.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = imgElem.attr("src");
                    }
                }
                pic = fixUrl(pic);

                // 提取播放源和剧集
                // 网站结构：播放链接 /play/{id}-{source}-{episode}.html
                // 从URL提取source值分组，source=0是河马短剧，source=1是红豆剧场等
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 线路名称映射（source值 → 线路名）
                Map<String, String> sourceNames = new HashMap<>();
                sourceNames.put("0", "河马短剧");
                sourceNames.put("1", "红豆剧场");
                sourceNames.put("2", "备用线路");

                // 从所有播放链接提取，按source值分组
                Elements allPlayLinks = doc.select("a[href*='/play/']");
                Map<String, List<String>> sourceMap = new LinkedHashMap<>();
                List<String> sourceOrder = new ArrayList<>();

                for (Element link : allPlayLinks) {
                    String epName = link.text().trim();
                    String epUrl = link.attr("href");

                    if (TextUtils.isEmpty(epName) || TextUtils.isEmpty(epUrl)) continue;
                    if (!epUrl.contains("/play/")) continue;

                    // 从URL提取source值：/play/2055-1-0.html → source=1
                    String sourceKey = extractSourceKey(epUrl);
                    if (TextUtils.isEmpty(sourceKey)) sourceKey = "0";

                    if (!sourceMap.containsKey(sourceKey)) {
                        sourceMap.put(sourceKey, new ArrayList<>());
                        sourceOrder.add(sourceKey);
                    }

                    sourceMap.get(sourceKey).add(epName + "$" + fixUrl(epUrl));
                }

                // 按顺序组装线路和选集
                for (String sourceKey : sourceOrder) {
                    List<String> eps = sourceMap.get(sourceKey);
                    if (!eps.isEmpty()) {
                        String sourceName = sourceNames.containsKey(sourceKey) ?
                                sourceNames.get(sourceKey) : "线路" + (sources.size() + 1);
                        sources.add(sourceName);
                        episodes.add(join(eps, "#"));
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
            // 搜索URL：/search.php?searchword={关键词}（MacCMS v8格式）
            String url = siteUrl + "/search.php?searchword=" + URLEncoder.encode(key, "UTF-8");

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 搜索结果页：链接指向 /djok/{id}.html
            for (Element item : doc.select("a[href*=/djok/]")) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("/djok/")) continue;

                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (TextUtils.isEmpty(title)) continue;

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                }
                pic = fixUrl(pic);

                String vid = normalizeVid(href);
                if (seen.contains(vid)) continue;
                seen.add(vid);

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
            // 播放链接格式：/play/{id}-{source}-{episode}.html
            // 使用嗅探模式让客户端处理，避免直链域名超时问题
            String playUrl = id;
            if (!id.startsWith("http")) {
                playUrl = siteUrl + id;
            }

            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", Util.CHROME);
            header.put("Referer", siteUrl);

            // parse=1 嗅探模式，客户端会自动处理播放页和m3u8提取
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

        // 从详情页URL提取ID（格式：/djok/12345.html）
        if (href.contains("/djok/")) {
            String[] parts = href.split("/");
            for (String part : parts) {
                if (part.contains(".html")) {
                    return part.replace(".html", "");
                }
            }
        }

        return href;
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

    /**
     * 从播放链接URL提取源标识
     * 播放链接格式：/play/{id}-{source}-{episode}.html
     * 例如：/play/2055-1-0.html → source=1
     */
    private String extractSourceKey(String epUrl) {
        if (TextUtils.isEmpty(epUrl)) return "";

        // 匹配 /play/id-source-episode.html
        int playIdx = epUrl.indexOf("/play/");
        if (playIdx < 0) return "";

        String afterPlay = epUrl.substring(playIdx + 6); // /play/ 之后
        String[] parts = afterPlay.split("-");
        if (parts.length >= 2) {
            // parts[0]=id, parts[1]=source
            return parts[1];
        }

        return "";
    }
}
