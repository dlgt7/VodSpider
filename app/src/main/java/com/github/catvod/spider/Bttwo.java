package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

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
 * 两个BT在线影视 Spider - HTML爬虫型视频源
 * 站点：https://bttwo.life/
 * Tailwind+Alpine.js 架构，播放页与详情页合二为一
 * 详情信息在 [x-show*=showDetail] 区域，grid 布局：.text-gray-500 为标签，.text-gray-300 为值
 * 选集链接在 [x-data*=episode] a[href*=/play/]（Alpine.js x-data 含 episode 的容器）
 */
public class Bttwo extends Spider {

    private static final String DEFAULT_HOST = "https://bttwo.life";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private String host;

    public Bttwo() {
        this.host = DEFAULT_HOST;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.host = DEFAULT_HOST;
        if (TextUtils.isEmpty(extend)) return;
        String url = extend.trim();
        try {
            if (!url.startsWith("http")) {
                org.json.JSONObject json = new org.json.JSONObject(url);
                url = json.optString("site", url);
            }
        } catch (Exception ignored) {
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!TextUtils.isEmpty(url) && url.startsWith("http")) {
            this.host = url;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("movie", "电影"));
        classes.add(new Class("tv", "电视剧"));
        classes.add(new Class("anime", "动漫"));

        Document doc = fetchDocument(host);
        ArrayList<Vod> list = parseVodList(doc);
        if (list.size() > 12) {
            list = new ArrayList<>(list.subList(0, 12));
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        String url = host + "/" + tid;
        if (!"1".equals(pg)) {
            url += "?page=" + pg;
        }

        Document doc = fetchDocument(url);
        ArrayList<Vod> list = parseVodList(doc);

        int page = Integer.parseInt(pg);
        int pagecount = page + 1;
        int limit = Math.max(list.size(), 1);
        int total = pagecount * limit;

        return Result.string(page, pagecount, limit, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String url = host + "/play/" + vodId;
        Document doc = fetchDocument(url);

        // 查找详情区域（Alpine.js x-show 控制的面板）
        Element detailPanel = doc.selectFirst("[x-show*=showDetail]");

        // 提取标题：优先从详情面板 h1，其次页面 h2
        String title = "";
        if (detailPanel != null) {
            Element h1 = detailPanel.selectFirst("h1");
            if (h1 != null) title = h1.text().trim();
        }
        if (TextUtils.isEmpty(title)) {
            Elements h2s = doc.select("h2");
            for (Element h2 : h2s) {
                String t = h2.text().trim();
                if (!TextUtils.isEmpty(t) && !t.equals("选集") && !t.equals("相关推荐") && !t.equals("影片详情")) {
                    title = t;
                    break;
                }
            }
        }

        // 提取封面图：详情面板中的 img
        String pic = "";
        if (detailPanel != null) {
            Element img = detailPanel.selectFirst("img[src]");
            if (img != null) {
                pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "src");
                pic = fixUrl(pic);
            }
        }
        if (TextUtils.isEmpty(pic)) {
            Element ogImg = doc.selectFirst("meta[property=og:image]");
            if (ogImg != null) {
                pic = ogImg.attr("content").trim();
            }
        }

        // 提取详情信息（grid 布局：标签.text-gray-500 + 值.text-gray-300）
        String year = "";
        String director = "";
        String actor = "";
        String area = "";
        String content = "";

        if (detailPanel != null) {
            // 基本信息行：年份、地区在 .text-gray-400 span 中
            Elements spans = detailPanel.select(".text-gray-400");
            if (spans.size() > 0) year = spans.get(0).text().trim();
            if (spans.size() > 1) area = spans.get(1).text().trim();

            // 详细信息 grid：标签-值对
            Elements labels = detailPanel.select(".grid .text-gray-500");
            Elements values = detailPanel.select(".grid .text-gray-300");
            for (int i = 0; i < labels.size() && i < values.size(); i++) {
                String label = labels.get(i).text().trim();
                String value = values.get(i).text().trim();
                if (label.contains("导演")) {
                    director = value;
                } else if (label.contains("主演")) {
                    actor = value;
                } else if (label.contains("地区")) {
                    if (TextUtils.isEmpty(area)) area = value;
                } else if (label.contains("上映")) {
                    if (TextUtils.isEmpty(year)) {
                        year = value.length() > 4 ? value.substring(0, 4) : value;
                    }
                } else if (label.contains("类型")) {
                    // type info available but not mapped to Vod field
                }
            }

            // 提取简介（详情面板中的 p 标签）
            Element contentEl = detailPanel.selectFirst("p");
            if (contentEl != null) {
                content = contentEl.text().trim();
            }
        }

        // 提取选集列表：[x-data*=episode] 容器内的 play 链接（Alpine.js 选集组件）
        ArrayList<String> episodes = new ArrayList<>();
        Elements playLinks = doc.select("[x-data*=episode] a[href*=/play/]");

        for (Element link : playLinks) {
            String href = link.attr("href").trim();
            String epName = link.text().trim();
            if (TextUtils.isEmpty(href) || !href.contains("/play/")) continue;

            if (TextUtils.isEmpty(epName)) epName = "正片";

            String playId = extractPlayId(href);
            if (TextUtils.isEmpty(playId)) continue;

            episodes.add(epName + "$" + playId);
        }

        // 兜底：如果 x-data 选择器没匹配到，尝试 main .grid
        if (episodes.isEmpty()) {
            playLinks = doc.select("main .grid a[href*=/play/]");
            for (Element link : playLinks) {
                String href = link.attr("href").trim();
                String epName = link.text().trim();
                if (TextUtils.isEmpty(href) || !href.contains("/play/")) continue;
                if (isInRecommendSection(link)) continue;
                if (TextUtils.isEmpty(epName)) epName = "正片";

                String playId = extractPlayId(href);
                if (TextUtils.isEmpty(playId)) continue;

                episodes.add(epName + "$" + playId);
            }
        }

        if (episodes.isEmpty()) {
            episodes.add("正片$" + vodId);
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(title);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setVodYear(year);
        vod.setVodDirector(director);
        vod.setVodActor(actor);
        vod.setVodArea(area);
        vod.setVodPlayFrom("两个BT");
        vod.setVodPlayUrl(TextUtils.join("#", episodes));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.error("播放 id 为空");
        }

        String playUrl = id.trim();
        if (playUrl.contains("$")) {
            int lastDollar = playUrl.lastIndexOf('$');
            playUrl = playUrl.substring(lastDollar + 1);
        }

        // 构造完整播放页 URL，返回 parse=1 让客户端嗅探处理
        String fullUrl = host + "/play/" + playUrl;

        return Result.get()
                .url(fullUrl)
                .parse(1)
                .header(buildHeaders())
                .string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String url = host + "/search?q=" + URLEncoder.encode(key, "UTF-8");
        if (!TextUtils.isEmpty(pg) && !"1".equals(pg)) {
            url += "&page=" + pg;
        }
        Document doc = fetchDocument(url);
        ArrayList<Vod> list = parseSearchList(doc);
        return Result.string(list);
    }

    /**
     * 从 URL 中提取 play id（/play/xxx 中的 xxx）
     */
    private String extractPlayId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int idx = url.indexOf("/play/");
        if (idx >= 0) {
            String id = url.substring(idx + 6);
            int queryIdx = id.indexOf('?');
            if (queryIdx > 0) id = id.substring(0, queryIdx);
            int slashIdx = id.indexOf('/');
            if (slashIdx > 0) id = id.substring(0, slashIdx);
            return id.trim();
        }
        return "";
    }

    /**
     * URL 补全：处理相对路径和协议相对路径
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return host + url;
        return url;
    }

    /**
     * 发送 HTTP GET 请求，返回 Jsoup Document
     */
    private Document fetchDocument(String url) throws Exception {
        String html = OkHttp.string(url, buildHeaders());
        return Jsoup.parse(html);
    }

    /**
     * 构造请求头（UA、Referer）
     */
    private Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", host + "/");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        return headers;
    }

    /**
     * 从 Element 的多个属性中获取第一个非空值
     */
    private static String getFirstNonEmptyAttr(Element el, String... attrs) {
        for (String attr : attrs) {
            if (el.hasAttr(attr)) {
                String value = el.attr(attr);
                if (!TextUtils.isEmpty(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    /**
     * 判断元素是否在推荐区域
     */
    private static boolean isInRecommendSection(Element el) {
        Element parent = el.parent();
        while (parent != null) {
            String cls = parent.className();
            if (cls.contains("recommend") || cls.contains("related") || cls.contains("hot")) {
                return true;
            }
            parent = parent.parent();
        }
        return false;
    }

    /**
     * 从 HTML Document 解析视频列表（首页/分类页）
     * 结构：<a href="/play/xxx"><img data-src="..."><h3>标题</h3>...</a>
     * 图片使用 data-src 懒加载，h3 在 a 标签内部
     */
    private ArrayList<Vod> parseVodList(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        HashMap<String, Boolean> idSet = new HashMap<>();

        // 优先：首页轮播区 div.slide-inner 含 data-title 属性
        Elements slides = doc.select("div.slide-inner[data-title]");
        for (Element slide : slides) {
            String title = slide.attr("data-title").trim();
            if (TextUtils.isEmpty(title)) continue;

            Element link = slide.selectFirst("a[href*=/play/]");
            if (link == null) continue;

            String playId = extractPlayId(link.attr("href"));
            if (TextUtils.isEmpty(playId) || idSet.containsKey(playId)) continue;
            idSet.put(playId, true);

            String pic = "";
            Element img = slide.selectFirst("img");
            if (img != null) {
                pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "src");
                pic = fixUrl(pic);
            }

            String remark = slide.attr("data-rating").trim();
            list.add(new Vod(playId, title, pic, remark));
        }

        // 卡片区：a[href*=/play/] 内含 h3 标题和 img 图片
        Elements items = doc.select("a[href*=/play/]:has(h3)");

        // 兜底：如果没有 h3 结构，尝试更宽泛的选择器
        if (items.isEmpty()) {
            items = doc.select("a[href*=/play/]");
        }

        for (Element link : items) {
            String href = link.attr("href").trim();
            if (TextUtils.isEmpty(href)) continue;

            String playId = extractPlayId(href);
            if (TextUtils.isEmpty(playId) || idSet.containsKey(playId)) continue;
            idSet.put(playId, true);

            // 提取标题：优先 h3 子元素文本
            String title = "";
            Element h3 = link.selectFirst("h3");
            if (h3 != null) {
                title = h3.text().trim();
            }
            if (TextUtils.isEmpty(title)) {
                title = link.text().trim();
            }
            if (TextUtils.isEmpty(title)) {
                title = link.attr("title").trim();
            }
            if (TextUtils.isEmpty(title)) continue;

            // 提取图片：a 标签内的 img（优先 data-src 懒加载）
            String pic = "";
            Element img = link.selectFirst("img");
            if (img != null) {
                pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "data-lazy-src", "src");
                pic = fixUrl(pic);
            }
            // 如果 a 内没有 img，向上查找
            if (TextUtils.isEmpty(pic)) {
                Element parent = link.parent();
                int depth = 0;
                while (parent != null && pic.isEmpty() && depth < 3) {
                    img = parent.selectFirst("img");
                    if (img != null) {
                        pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "data-lazy-src", "src");
                        pic = fixUrl(pic);
                    }
                    parent = parent.parent();
                    depth++;
                }
            }

            // 提取备注
            String remark = "";
            Element remarkEl = link.selectFirst(".text-xs, .badge, .hdtag, span.update, .pic-text");
            if (remarkEl != null) {
                remark = remarkEl.text().trim();
                if (remark.length() > 20) remark = "";
            }

            list.add(new Vod(playId, title, pic, remark));
        }

        return list;
    }

    /**
     * 从搜索结果页解析视频+视频列表
     * 结构与分类页相同：a[href*=/play/] 内含 h3 和 img
     */
    private ArrayList<Vod> parseSearchList(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        HashMap<String, Boolean> idSet = new HashMap<>();

        Elements items = doc.select("a[href*=/play/]:has(h3)");
        if (items.isEmpty()) {
            items = doc.select("a[href*=/play/]");
        }

        for (Element link : items) {
            String href = link.attr("href").trim();
            if (TextUtils.isEmpty(href)) continue;

            String playId = extractPlayId(href);
            if (TextUtils.isEmpty(playId) || idSet.containsKey(playId)) continue;
            idSet.put(playId, true);

            // 提取标题：优先 h3 子元素
            String title = "";
            Element h3 = link.selectFirst("h3");
            if (h3 != null) {
                title = h3.text().trim();
            }
            if (TextUtils.isEmpty(title)) {
                title = link.text().trim();
            }
            if (TextUtils.isEmpty(title)) {
                title = link.attr("title").trim();
            }
            if (TextUtils.isEmpty(title)) continue;

            // 提取图片
            String pic = "";
            Element img = link.selectFirst("img");
            if (img != null) {
                pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "data-lazy-src", "src");
                pic = fixUrl(pic);
            }
            if (TextUtils.isEmpty(pic)) {
                Element parent = link.parent();
                int depth = 0;
                while (parent != null && pic.isEmpty() && depth < 3) {
                    img = parent.selectFirst("img");
                    if (img != null) {
                        pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "data-lazy-src", "src");
                        pic = fixUrl(pic);
                    }
                    parent = parent.parent();
                    depth++;
                }
            }

            // 提取备注
            String remark = "";
            Element remarkEl = link.selectFirst(".text-xs, .badge, span.update");
            if (remarkEl != null) {
                remark = remarkEl.text().trim();
                if (remark.length() > 20) remark = "";
            }

            list.add(new Vod(playId, title, pic, remark));
        }

        return list;
    }
}
