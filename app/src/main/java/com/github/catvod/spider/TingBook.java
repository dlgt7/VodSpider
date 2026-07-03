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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TingBook Spider - 听书网爬虫
 */
public class TingBook extends Spider {

    // 静态字段 - BASE_URL (smali: a:String)
    private static String BASE_URL = "https://app.365ting.com";

    // 静态字段 - BOOK_URL_PATTERN (smali: a:Pattern)
    private static final Pattern BOOK_URL_PATTERN = Pattern.compile("/book/\\d+\\.html");

    // 静态字段 - PAGE_PATTERN (smali: b:Pattern)
    private static final Pattern PAGE_PATTERN = Pattern.compile("page=(\\d+)");

    // 静态字段 - RATING_PATTERN (smali: c:Pattern)
    private static final Pattern RATING_PATTERN = Pattern.compile("\\d+(\\*\\d+)+");

    // 静态字段 - CATEGORIES (smali: a:[[String)
    private static final String[][] CATEGORIES = new String[][]{
            {"6", "玄幻奇幻"},
            {"7", "都市言情"},
            {"8", "宫斗女频"},
            {"9", "官场商战"},
            {"10", "武侠仙侠"},
            {"11", "刑侦推理"},
            {"12", "探险科幻"},
            {"13", "重生穿越"},
            {"14", "恐怖惊悚"},
            {"15", "文学历史"},
            {"31", "评书相声"},
            {"49", "两性情感"},
            {"51", "儿童文学"},
            {"52", "国学启蒙"},
            {"53", "家庭教育儿"},
            {"54", "卡通动画"}
    };

    // 构造函数
    public TingBook() {
    }

    // 静态方法 a(String) - URL 补全
    public static String a(String url) {
        if (TextUtils.isEmpty(url)) {
            return BASE_URL;
        }
        if (url.startsWith("http")) {
            return url;
        }
        if (url.startsWith("//")) {
            return "https:".concat(url);
        }
        if (url.startsWith("/")) {
            return BASE_URL + url;
        }
        return BASE_URL + "/" + url;
    }

    // 静态方法 b(Document, ArrayList) - 解析播放列表
    public static void b(Document doc, ArrayList<String> list) {
        Elements items = doc.select("a.list-item[href^=/play/]");
        for (Element item : items) {
            String href = item.attr("href");
            if (TextUtils.isEmpty(href)) {
                continue;
            }
            String title = item.attr("title").trim();
            if (TextUtils.isEmpty(title)) {
                title = item.text().trim();
            }
            if (TextUtils.isEmpty(title)) {
                title = "章节";
            }
            list.add(title + "$" + href);
        }
    }

    // 静态方法 c(int, String) - 解码评分/播放URL
    public static String c(int type, String str) {
        if (TextUtils.isEmpty(str)) {
            return "";
        }
        str = str.trim();
        // type==1 或 匹配评分格式时进行解码
        if (type == 1 || RATING_PATTERN.matcher(str).matches()) {
            StringBuilder sb = new StringBuilder();
            String[] parts = str.split("\\*");
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                try {
                    int code = Integer.parseInt(part);
                    sb.append((char) code);
                } catch (Exception e) {
                    return str;
                }
            }
            str = sb.toString();
        }
        // 补全 URL 协议
        if (str.startsWith("//")) {
            str = "https:".concat(str);
        }
        return str;
    }

    // 静态方法 f(String, Document) - 提取详情页字段
    public static String f(String prefix, Document doc) {
        Elements extras = doc.select(".extra");
        for (Element extra : extras) {
            String text = extra.text().trim();
            if (!text.startsWith(prefix)) {
                continue;
            }
            Element span = extra.selectFirst("span.text");
            if (span != null) {
                Element a = span.selectFirst("a");
                if (a != null) {
                    return a.text().trim();
                }
                return span.text().trim();
            }
        }
        return "";
    }

    // 静态方法 h(String) - URL 协议补全(简化版)
    public static String h(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:".concat(url);
        }
        return url;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        Init.init(context);
        if (!TextUtils.isEmpty(extend)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(extend);
                String url = json.optString("url", "");
                String site = json.optString("site", url);
                if (!TextUtils.isEmpty(site)) {
                    BASE_URL = site;
                }
            } catch (Exception e) {
                BASE_URL = extend.trim();
            }
        }
        // 移除末尾斜杠
        if (BASE_URL.endsWith("/")) {
            BASE_URL = BASE_URL.substring(0, BASE_URL.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (String[] category : CATEGORIES) {
            classes.add(new Class(category[0], category[1]));
        }
        ArrayList<Vod> list = new ArrayList<>();
        try {
            Document doc = d("/category/6/2/1.html");
            list = g(doc);
        } catch (Exception e) {
            // 异常时返回空列表
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try {
            page = Math.max(Integer.parseInt(pg), 1);
        } catch (Exception e) {
            // 解析失败时保持默认值
        }
        String url = "/category/" + tid + "/2/" + page + ".html";
        Document doc = d(url);
        ArrayList<Vod> list = g(doc);
        return Result.get().page(page, page, list.size(), list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String html = "";
        // 重试机制:最多尝试3次
        for (int retry = 0; retry < 3; retry++) {
            html = OkHttp.string(a(vodId), e());
            if (!html.contains("System Error")) {
                break;
            }
            Thread.sleep(500);
        }
        Document doc = Jsoup.parse(html);

        // 提取标题
        String name = doc.select("h2.book-title").first().text().trim();
        if (TextUtils.isEmpty(name)) {
            name = doc.select("h1.title").first().text().trim();
        }

        // 提取封面图
        String pic = h(doc.select(".book-detail img.img").attr("src"));
        if (TextUtils.isEmpty(pic)) {
            pic = h(doc.select("img[alt]").attr("src"));
        }

        // 提取简介
        String intro = doc.select(".book-intro").text().trim();

        // 提取详情字段
        String actor = f("作者", doc);
        String typeName = f("类型", doc);
        String remarks = f("状态", doc);
        if (TextUtils.isEmpty(remarks)) {
            remarks = f("原著", doc);
        }
        String director = f("播音", doc);

        // 解析播放列表
        ArrayList<String> playList = new ArrayList<>();
        b(doc, playList);

        // 提取总页数(用于分页拉取)
        int maxPage = 1;
        Elements pagination = doc.select("ul.pagination li a[href]");
        for (Element link : pagination) {
            String href = link.attr("href");
            Matcher matcher = PAGE_PATTERN.matcher(href);
            if (matcher.find()) {
                try {
                    int pageNum = Integer.parseInt(matcher.group(1));
                    maxPage = Math.max(maxPage, pageNum);
                } catch (Exception e) {
                    // 解析失败时跳过
                }
            }
        }
        maxPage = Math.min(maxPage, 200); // 最多拉取200页

        // 分页拉取播放列表
        String separator = vodId.contains("?") ? "&" : "?";
        for (int p = 2; p <= maxPage; p++) {
            try {
                String pageUrl = vodId + separator + "page=" + p;
                Document pageDoc = d(pageUrl);
                b(pageDoc, playList);
            } catch (Exception e) {
                // 分页失败时继续下一页
            }
        }

        // 构建 Vod 对象
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodContent(intro);
        vod.setVodActor(actor);
        vod.setVodDirector(director);
        vod.setTypeName(typeName);
        vod.setVodRemarks(remarks);
        vod.setVodPlayFrom("六月");
        vod.setVodPlayUrl(TextUtils.join("#", playList));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 提取章节ID(格式: 章节$URL)
        String chapterUrl = id;
        if (id.contains("$")) {
            chapterUrl = id.substring(id.lastIndexOf('$') + 1);
        }

        // 提取 bookId 和 chapterId (URL 格式: /play/bookId/chapterId.html)
        String path = chapterUrl.replace("/play/", "").replace(".html", "");
        String[] parts = path.split("/");

        Map<String, String> headers = e();

        // 尝试通过 API 获取播放URL
        if (parts.length >= 2) {
            try {
                long timestamp = System.currentTimeMillis() / 1000;
                String apiUrl = String.format(BASE_URL + "/pc/index/getchapterurl/bookId/%s/chapterId/%s/timestamp/%d.html",
                        parts[0], parts[1], timestamp);
                org.json.JSONObject json = new org.json.JSONObject(OkHttp.string(apiUrl, headers));
                int type = json.optInt("jsjm", 0);
                String src = json.optString("src", "");
                String playUrl = c(type, src);
                if (!TextUtils.isEmpty(playUrl)) {
                    return Result.get().url(playUrl).header(headers).parse(0).string();
                }
            } catch (Exception e) {
                // API 失败时回退到直链
            }
        }

        // 回退:直接使用章节URL
        String finalUrl = chapterUrl;
        if (!finalUrl.startsWith("http")) {
            if (finalUrl.startsWith("/")) {
                finalUrl = a(finalUrl);
            } else {
                finalUrl = a("/play/" + finalUrl + ".html");
            }
        }

        return Result.get().url(finalUrl).header(headers).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String keyword = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        String url = "/pc/index/search/keyword/" + keyword + ".html";
        Document doc = d(url);
        ArrayList<Vod> list = g(doc);
        return Result.string(list);
    }

    // 私有方法 d(String) - 获取 Document
    private final Document d(String path) throws Exception {
        String url = a(path);
        String html = OkHttp.string(url, e());
        return Jsoup.parse(html);
    }

    // 私有方法 e() - 构建请求头
    private final Map<String, String> e() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", BASE_URL + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return headers;
    }

    // 私有方法 g(Document) - 解析 Vod 列表
    private final ArrayList<Vod> g(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        Map<String, Boolean> dedup = new HashMap<>();

        Elements items = doc.select("a[href^=/book/]");
        for (Element item : items) {
            String href = item.attr("href");
            // 过滤非书籍链接
            if (!BOOK_URL_PATTERN.matcher(href).find()) {
                continue;
            }
            // 去重
            if (dedup.containsKey(href)) {
                continue;
            }
            dedup.put(href, Boolean.TRUE);

            // 提取标题(可能包含作者信息,格式: 书名|作者)
            String fullTitle = item.select("h2").text().trim();
            String name = "";
            String remark = "";
            int pipeIndex = fullTitle.indexOf('|');
            if (pipeIndex > 0) {
                name = fullTitle.substring(0, pipeIndex).trim();
                remark = fullTitle.substring(pipeIndex + 1).trim();
            } else {
                name = "";
            }
            if (TextUtils.isEmpty(name)) {
                name = item.text().trim();
            }

            // 提取封面图
            String pic = h(item.select("img").attr("src"));

            // 提取备注(作者)
            String finalRemark = TextUtils.isEmpty(remark) ? "听书" : remark;

            // 构建 Vod (使用 list 样式)
            Vod vod = new Vod(href, name, pic, finalRemark, Vod.Style.list());
            list.add(vod);

            // 最多返回95条
            if (list.size() >= 95) {
                break;
            }
        }
        return list;
    }
}