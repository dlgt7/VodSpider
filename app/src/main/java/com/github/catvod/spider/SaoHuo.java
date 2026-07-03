package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 骚火电影 Spider - HTML爬虫+Cookie型
 * 通过 Jsoup 解析 HTML 页面，自动提取 set-cookie 用于后续请求
 */
public class SaoHuo extends Spider {

    private static final String DEFAULT_HOST = "https://shdy5.us";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 9; ALN-AL00 Build/PQ3B.190801.05281406; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7";

    private String host = DEFAULT_HOST;
    private String cookie = "";

    public SaoHuo() {
        this.host = DEFAULT_HOST;
        this.cookie = "";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.host = DEFAULT_HOST;
        this.cookie = "";
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
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("20", "国产剧"));
        classes.add(new Class("4", "动漫"));

        Document doc = fetchDocument(host);
        ArrayList<Vod> list = parseVodList(doc);
        if (list.size() > 6) {
            list = new ArrayList<>(list.subList(0, 6));
        }

        if (filter) {
            String filtersJson = "{\"1\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"1\"},{\"n\":\"喜剧\",\"v\":\"6\"},{\"n\":\"爱情\",\"v\":\"7\"},{\"n\":\"恐怖\",\"v\":\"8\"},{\"n\":\"动作\",\"v\":\"9\"},{\"n\":\"科幻\",\"v\":\"10\"},{\"n\":\"战争\",\"v\":\"11\"},{\"n\":\"犯罪\",\"v\":\"12\"},{\"n\":\"动画\",\"v\":\"13\"},{\"n\":\"奇幻\",\"v\":\"14\"},{\"n\":\"剧情\",\"v\":\"15\"},{\"n\":\"冒险\",\"v\":\"16\"},{\"n\":\"悬疑\",\"v\":\"17\"},{\"n\":\"惊悚\",\"v\":\"18\"},{\"n\":\"其他\",\"v\":\"20\"}]}],\"2\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"2\"},{\"n\":\"国产剧\",\"v\":\"20\"},{\"n\":\"TVB\",\"v\":\"21\"},{\"n\":\"韩剧\",\"v\":\"22\"},{\"n\":\"美剧\",\"v\":\"23\"},{\"n\":\"日剧\",\"v\":\"24\"},{\"n\":\"英剧\",\"v\":\"25\"},{\"n\":\"台剧\",\"v\":\"26\"},{\"n\":\"其他\",\"v\":\"27\"}]}],\"3\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"4\"},{\"n\":\"搞笑\",\"v\":\"38\"},{\"n\":\"恋爱\",\"v\":\"39\"},{\"n\":\"热血\",\"v\":\"40\"},{\"n\":\"格斗\",\"v\":\"41\"},{\"n\":\"美少女\",\"v\":\"42\"},{\"n\":\"魔法\",\"v\":\"43\"},{\"n\":\"机战\",\"v\":\"44\"},{\"n\":\"校园\",\"v\":\"45\"},{\"n\":\"亲子\",\"v\":\"46\"},{\"n\":\"童话\",\"v\":\"47\"},{\"n\":\"冒险\",\"v\":\"48\"},{\"n\":\"真人\",\"v\":\"49\"},{\"n\":\"LOLI\",\"v\":\"50\"},{\"n\":\"其他\",\"v\":\"51\"}]}]}";
            org.json.JSONObject filters = new org.json.JSONObject(filtersJson);
            return Result.string(classes, list, filters);
        } else {
            return Result.string(classes, list);
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (extend != null && !TextUtils.isEmpty(extend.get("cateId"))) {
            tid = extend.get("cateId");
        }
        String url = host + "/list/" + tid + "-" + pg + ".html";
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
        String url = fixUrl(ids.get(0));
        Document doc = fetchDocument(url);

        // 提取标题（多个 CSS selector 尝试）
        String title = getFirstNonEmptyText(doc, "h1", ".vodh h2", ".detail-title", ".title");
        if (TextUtils.isEmpty(title)) {
            Element ogTitle = doc.selectFirst("meta[property=og:title]");
            if (ogTitle != null) {
                title = ogTitle.attr("content").trim();
            }
        }

        // 提取封面图
        String pic = "";
        Element imgEl = doc.selectFirst(".thumb img, .cover img, .detail-pic img, img.lazy");
        if (imgEl != null) {
            pic = getFirstNonEmptyAttr(imgEl, "data-original", "data-src", "src");
            pic = fixUrl(pic);
        }

        // 提取简介
        String content = getFirstNonEmptyText(doc, "p.p_txt", ".p_txt", ".vod_content", ".content", ".detail-content");
        if (!TextUtils.isEmpty(content) && !content.startsWith("简介")) {
            content = "简介：" + content;
        }

        // 提取导演/主演
        String year = "";
        String director = "";
        String actor = "";
        Element infoEl = doc.selectFirst(".info, .vod_info, .detail-info");
        if (infoEl != null) {
            String infoText = infoEl.text();
            String[] parts = infoText.split(" / 导演:| / 主演:");
            if (parts.length > 0) year = parts[0].trim();
            if (parts.length > 1) director = parts[1].trim();
            if (parts.length > 2) actor = parts[2].trim();
        }

        // 提取播放列表
        Elements playLinks = doc.select("#play_link li");
        Elements playFroms = doc.select(".play_from ul.from_list li");

        ArrayList<String> playFromList = new ArrayList<>();
        ArrayList<String> playUrlList = new ArrayList<>();

        for (int i = 0; i < playLinks.size(); i++) {
            String fromName = (i < playFroms.size()) ? playFroms.get(i).text().trim() : "线路" + (i + 1);
            if (TextUtils.isEmpty(fromName)) fromName = "播放";

            ArrayList<String> episodes = new ArrayList<>();
            Elements links = playLinks.get(i).select("a[href]");
            for (int j = links.size() - 1; j >= 0; j--) {
                Element link = links.get(j);
                String epName = link.text().trim();
                if (TextUtils.isEmpty(epName)) epName = "正片";
                String epUrl = link.attr("href").trim();
                if (TextUtils.isEmpty(epUrl)) continue;
                episodes.add(epName + "$" + epUrl);
            }

            if (!episodes.isEmpty()) {
                playFromList.add(fromName);
                playUrlList.add(TextUtils.join("#", episodes));
            }
        }

        if (playFromList.isEmpty()) {
            return Result.error("无播放列表");
        }

        Vod vod = new Vod();
        vod.setVodId(url);
        vod.setVodName(title);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setVodYear(year);
        vod.setVodDirector(director);
        vod.setVodActor(actor);
        vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
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

        if (playUrl.startsWith("http")) {
            if (playUrl.startsWith(host)) {
                playUrl = playUrl.substring(host.length());
            }
            if (!playUrl.startsWith("/")) {
                playUrl = "/" + playUrl;
            }
        } else if (!playUrl.startsWith("/")) {
            playUrl = "/" + playUrl;
        }

        String fullUrl = host + playUrl;
        Document doc = fetchDocument(fullUrl);

        // 尝试从 iframe 提取播放地址
        String videoUrl = "";
        Element iframe = doc.selectFirst("iframe[src]");
        if (iframe != null) {
            videoUrl = iframe.attr("src").trim();
        }
        if (!TextUtils.isEmpty(videoUrl)) {
            videoUrl = fixUrl(videoUrl);
        }

        // 如果 iframe 失败，直接用原始 URL
        if (TextUtils.isEmpty(videoUrl)) {
            videoUrl = host + playUrl;
        }

        return Result.get()
                .url(videoUrl)
                .parse(1)
                .header(buildHeaders())
                .string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = host + "/s----------.html?wd=" + URLEncoder.encode(key, "UTF-8");
        Document doc = fetchDocument(url);
        ArrayList<Vod> list = parseVodList(doc);
        return Result.string(list);
    }

    /**
     * URL 补全：处理 http/https/协议相对/根路径/相对路径
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return host + url;
        return host + "/" + url;
    }

    /**
     * 发送 HTTP GET 请求，返回 Jsoup Document，同时提取 set-cookie
     */
    private Document fetchDocument(String url) throws Exception {
        okhttp3.Response response = OkHttp.newCall(url, buildHeaders());
        try {
            // 提取 set-cookie
            okhttp3.Headers headers = response.headers();
            for (String name : headers.names()) {
                if (name.equalsIgnoreCase("set-cookie")) {
                    String cookieValue = headers.get(name);
                    if (!TextUtils.isEmpty(cookieValue)) {
                        int semicolonIndex = cookieValue.indexOf(';');
                        if (semicolonIndex > 0) {
                            cookieValue = cookieValue.substring(0, semicolonIndex);
                        }
                        this.cookie = cookieValue;
                    }
                }
            }
            // 获取响应体并解析 HTML
            String body = response.body().string();
            return Jsoup.parse(body);
        } finally {
            response.close();  // 重要:关闭 Response
        }
    }

    /**
     * 构造请求头（UA、Referer、Cookie）
     */
    private Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("accept-language", ACCEPT_LANGUAGE);
        headers.put("Referer", host + "/");
        if (!TextUtils.isEmpty(cookie)) {
            headers.put("Cookie", cookie);
        }
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
     * 从 Document 的多个 CSS selector 中获取第一个非空 text
     */
    private static String getFirstNonEmptyText(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String text = el.text();
                if (!TextUtils.isEmpty(text)) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    /**
     * 从 HTML Document 解析视频列表
     */
    private ArrayList<Vod> parseVodList(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> idSet = new LinkedHashSet<>();

        Elements items = doc.select(".v_list li, .vlist li, ul.clearfix li, li:has(a[title]):has(img)");
        for (Element item : items) {
            Element link = item.selectFirst("a[href]");
            if (link == null) {
                link = item.selectFirst("a[title], h3 a, h4 a, .title a, a[href]");
            }
            if (link == null) continue;

            String href = link.attr("href");
            if (TextUtils.isEmpty(href)) continue;

            href = fixUrl(href);
            if (idSet.contains(href)) continue;
            idSet.add(href);

            // 提取标题
            String title = getFirstNonEmptyAttr(link, "title");
            if (TextUtils.isEmpty(title)) {
                title = link.text().trim();
            }
            if (TextUtils.isEmpty(title)) continue;

            // 提取图片
            Element img = item.selectFirst("img");
            String pic = "";
            if (img != null) {
                pic = getFirstNonEmptyAttr(img, "data-original", "data-src", "src");
                pic = fixUrl(pic);
            }

            // 提取备注
            String remark = "";
            Element remarkEl = item.selectFirst(".hdtag, .note, .pic-text, .remarks, span");
            if (remarkEl != null) {
                remark = remarkEl.text().trim();
            }

            Vod vod = new Vod(href, title, pic, remark);
            list.add(vod);
        }
        return list;
    }
}