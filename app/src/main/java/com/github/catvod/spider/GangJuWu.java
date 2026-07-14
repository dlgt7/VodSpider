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
 * 港剧屋爬虫
 * 网站地址：http://m.gangju5.cc
 * 备用域名：www.gj5.tv
 * 类型：2024港剧、经典港剧、香港电影、韩剧
 *
 * @author Trae
 * @date 2026-07-14
 */
public class GangJuWu extends Spider {

    private static final String SITE_URL = "http://m.gangju5.cc";
    private static final String ALT_SITE_URL = "http://www.gj5.tv"; // 备用域名
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
        classes.add(new Class("2024tvb", "2024港剧"));
        classes.add(new Class("2017tvb", "经典港剧"));
        classes.add(new Class("dianying", "香港电影"));
        classes.add(new Class("hanju", "韩剧"));

        List<Vod> list = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, headers));

            List<String> seen = new ArrayList<>();

            // 首页视频项：提取含图片的链接，图片通常在li或div容器内
            // 结构：img标签 + 标题链接(a标签)
            for (Element item : doc.select("li:has(img)")) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;

                String href = link.attr("href");
                if (TextUtils.isEmpty(href)) continue;
                // 只保留分类详情链接，排除导航、资讯等非视频链接
                if (!isVideoLink(href)) continue;

                String title = link.attr("title");
                if (TextUtils.isEmpty(title)) title = link.text();
                if (TextUtils.isEmpty(title)) {
                    // 查找容器内其他标题链接
                    Element titleLink = item.selectFirst("a[href]:not(:has(img))");
                    if (titleLink != null) {
                        title = titleLink.text();
                    }
                }
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
                if (TextUtils.isEmpty(vid) || seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底：如果上面的选择器没有匹配到，使用更宽泛的方式
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !isVideoLink(href)) continue;
                    // 排除播放链接
                    if (href.contains("/play-")) continue;

                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) title = item.text();
                    if (TextUtils.isEmpty(title) || title.length() < 2) continue;

                    String pic = "";
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                    pic = fixUrl(pic);

                    String vid = normalizeVid(href);
                    if (TextUtils.isEmpty(vid) || seen.contains(vid)) continue;
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
            // 分类页URL：/{分类slug}/（第1页），/{分类slug}/page/{页码}/（第2页起）
            String url;
            if ("1".equals(pg)) {
                url = siteUrl + "/" + tid + "/";
            } else {
                url = siteUrl + "/" + tid + "/page/" + pg + "/";
            }

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 分类页视频项结构与首页相同
            for (Element item : doc.select("li:has(img)")) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;

                String href = link.attr("href");
                if (TextUtils.isEmpty(href) || !isVideoLink(href)) continue;

                String title = link.attr("title");
                if (TextUtils.isEmpty(title)) title = link.text();
                if (TextUtils.isEmpty(title)) {
                    Element titleLink = item.selectFirst("a[href]:not(:has(img))");
                    if (titleLink != null) {
                        title = titleLink.text();
                    }
                }
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
                if (TextUtils.isEmpty(vid) || seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !isVideoLink(href)) continue;
                    if (href.contains("/play-")) continue;

                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) title = item.text();
                    if (TextUtils.isEmpty(title) || title.length() < 2) continue;

                    String pic = "";
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                    pic = fixUrl(pic);

                    String vid = normalizeVid(href);
                    if (TextUtils.isEmpty(vid) || seen.contains(vid)) continue;
                    seen.add(vid);

                    list.add(new Vod(vid, title, pic));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 24, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        List<Vod> list = new ArrayList<>();

        try {
            for (String id : ids) {
                // 详情页URL：siteUrl + vodId（vodId是完整路径如/2024tvb/heiseyueguangyueyuban/）
                String url = siteUrl + id;

                Document doc = Jsoup.parse(OkHttp.string(url, headers));

                // 提取剧名（h1元素）
                String name = "";
                Element titleElem = doc.selectFirst("h1");
                if (titleElem != null) {
                    name = titleElem.text();
                }
                if (TextUtils.isEmpty(name)) {
                    titleElem = doc.selectFirst("h2, .title, .post-title");
                    if (titleElem != null) {
                        name = titleElem.text();
                    }
                }

                // 提取图片（封面图，优先从详情区域内的img提取）
                String pic = "";
                // 尝试从详情内容区域提取
                Element contentArea = doc.selectFirst(".content, .post-content, .entry, article");
                if (contentArea != null) {
                    Element img = contentArea.selectFirst("img[src]");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                }
                // 兜底：从页面所有img中查找封面
                if (TextUtils.isEmpty(pic) || pic.contains("default.png")) {
                    for (Element img : doc.select("img[src]")) {
                        String src = img.attr("data-original");
                        if (TextUtils.isEmpty(src)) src = img.attr("data-src");
                        if (TextUtils.isEmpty(src)) src = img.attr("src");
                        // 跳过default.png和小图标
                        if (TextUtils.isEmpty(src) || src.contains("default.png")) continue;
                        if (src.contains("04pic.com") || src.contains("gangju") || src.contains("/uploads/")) {
                            pic = src;
                            break;
                        }
                    }
                }
                pic = fixUrl(pic);

                // 提取播放源和剧集
                // 详情页剧集链接格式：/{slug}/play-{id}-{source}-{episode}.html
                List<String> sources = new ArrayList<>();
                List<String> episodes = new ArrayList<>();

                // 按播放源分组提取选集
                // 查找所有播放链接
                Elements playLinks = doc.select("a[href*='play-']");
                if (!playLinks.isEmpty()) {
                    // 按source值分组
                    Map<String, List<String>> sourceMap = new HashMap<>();
                    List<String> sourceOrder = new ArrayList<>();

                    for (Element link : playLinks) {
                        String epName = link.text().trim();
                        String epUrl = link.attr("href");

                        if (TextUtils.isEmpty(epName) || TextUtils.isEmpty(epUrl)) continue;

                        // 从URL提取source值：play-{id}-{source}-{episode}.html
                        String sourceKey = extractSourceKey(epUrl);
                        if (TextUtils.isEmpty(sourceKey)) sourceKey = "默认";

                        if (!sourceMap.containsKey(sourceKey)) {
                            sourceMap.put(sourceKey, new ArrayList<String>());
                            sourceOrder.add(sourceKey);
                        }

                        sourceMap.get(sourceKey).add(epName + "$" + fixUrl(epUrl));
                    }

                    for (String sourceKey : sourceOrder) {
                        List<String> eps = sourceMap.get(sourceKey);
                        if (!eps.isEmpty()) {
                            sources.add(sourceKey);
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
            // WordPress搜索URL：/?s={关键词}
            String url = siteUrl + "/?s=" + URLEncoder.encode(key, "UTF-8");

            Document doc = Jsoup.parse(OkHttp.string(url, headers));

            List<String> seen = new ArrayList<>();

            // 搜索结果页视频项
            for (Element item : doc.select("li:has(img)")) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;

                String href = link.attr("href");
                if (TextUtils.isEmpty(href) || !isVideoLink(href)) continue;

                String title = link.attr("title");
                if (TextUtils.isEmpty(title)) title = link.text();
                if (TextUtils.isEmpty(title)) {
                    Element titleLink = item.selectFirst("a[href]:not(:has(img))");
                    if (titleLink != null) {
                        title = titleLink.text();
                    }
                }
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
                if (TextUtils.isEmpty(vid) || seen.contains(vid)) continue;
                seen.add(vid);

                list.add(new Vod(vid, title, pic));
            }

            // 兜底
            if (list.isEmpty()) {
                for (Element item : doc.select("a[href]")) {
                    String href = item.attr("href");
                    if (TextUtils.isEmpty(href) || !isVideoLink(href)) continue;
                    if (href.contains("/play-")) continue;

                    String title = item.attr("title");
                    if (TextUtils.isEmpty(title)) title = item.text();
                    if (TextUtils.isEmpty(title) || title.length() < 2) continue;

                    String pic = "";
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                        if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    }
                    pic = fixUrl(pic);

                    String vid = normalizeVid(href);
                    if (TextUtils.isEmpty(vid) || seen.contains(vid)) continue;
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
            // 使用嗅探模式，让客户端处理播放
            String playUrl = id;
            if (!id.startsWith("http")) {
                playUrl = siteUrl + id;
            }

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
     * 判断链接是否为视频详情页链接
     * 视频详情链接格式：/{category_slug}/{video_slug}/
     * 分类slug：2024tvb, 2017tvb, dianshiju, dianying, hanju 等
     * 排除：/mingxing/, /yule/, /play-, /page/, /?s= 等
     */
    private boolean isVideoLink(String href) {
        if (TextUtils.isEmpty(href)) return false;
        // 排除非视频内容
        if (href.contains("/mingxing/") || href.contains("/yule/") ||
            href.contains("/play-") || href.contains("/page/") ||
            href.contains("?s=") || href.contains("/tag/") ||
            href.contains("/author/") || href.contains("/category/")) {
            return false;
        }
        // 匹配视频分类路径：/{slug}/{video_slug}/
        // 已知分类slug
        if (href.contains("/2024tvb/") || href.contains("/2017tvb/") ||
            href.contains("/dianshiju/") || href.contains("/dianying/") ||
            href.contains("/hanju/")) {
            return true;
        }
        return false;
    }

    /**
     * 从播放链接URL提取源标识
     * 播放链接格式：/{slug}/play-{id}-{source}-{episode}.html
     * 提取source值作为源标识
     */
    private String extractSourceKey(String epUrl) {
        // 匹配 play-{id}-{source}-{episode}.html
        int playIdx = epUrl.indexOf("play-");
        if (playIdx < 0) return "";

        String afterPlay = epUrl.substring(playIdx + 5); // play-之后
        String[] parts = afterPlay.split("-");
        if (parts.length >= 3) {
            // parts[0]=id, parts[1]=source, parts[2]=episode.html
            return "线路" + parts[1];
        }

        return "";
    }

    /**
     * 修正URL（补全协议和域名，支持多域名）
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        // 跳过default.png等占位图
        if (url.contains("default.png")) {
            return "";
        }

        if (url.startsWith("//")) {
            return "http:" + url;
        } else if (url.startsWith("/")) {
            return siteUrl + url;
        } else if (url.contains("www.gj5.tv")) {
            // 备用域名链接，统一转换为主域名
            return url.replace("http://www.gj5.tv", SITE_URL)
                      .replace("https://www.gj5.tv", SITE_URL);
        } else if (url.contains("www.gangju5.cc")) {
            // 桌面版域名，统一转换为移动版
            return url.replace("http://www.gangju5.cc", SITE_URL)
                      .replace("https://www.gangju5.cc", SITE_URL);
        }

        return url;
    }

    /**
     * 标准化视频ID（使用完整详情页路径作为ID）
     * 例如：/2024tvb/heiseyueguangyueyuban/
     * 详情页URL = siteUrl + vid
     */
    private String normalizeVid(String href) {
        if (TextUtils.isEmpty(href)) {
            return "";
        }

        // 处理多域名链接
        String url = href;
        if (url.contains("www.gj5.tv")) {
            url = url.replace("http://www.gj5.tv", SITE_URL)
                      .replace("https://www.gj5.tv", SITE_URL);
        }
        if (url.contains("www.gangju5.cc")) {
            url = url.replace("http://www.gangju5.cc", SITE_URL)
                      .replace("https://www.gangju5.cc", SITE_URL);
        }

        // 从完整URL提取路径部分作为ID
        // 例如：http://m.gangju5.cc/2024tvb/heiseyueguangyueyuban/ → /2024tvb/heiseyueguangyueyuban/
        if (url.startsWith("http")) {
            try {
                Uri uri = Uri.parse(url);
                String path = uri.getPath();
                if (!TextUtils.isEmpty(path)) {
                    // 确保路径以/结尾
                    if (!path.endsWith("/")) {
                        path = path + "/";
                    }
                    return path;
                }
            } catch (Exception e) {
                // fallback
            }
        }

        // 相对路径直接返回
        if (url.startsWith("/")) {
            if (!url.endsWith("/")) {
                url = url + "/";
            }
            return url;
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
