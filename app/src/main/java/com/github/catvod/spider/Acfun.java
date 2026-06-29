package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acfun 视频站爬虫
 * Converted from Acfun.py
 */
public class Acfun extends Spider {

    private static final String SITE_URL = "https://www.acfun.cn";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.87 Safari/537.36";

    // homeContent 中需要跳过的分类名
    private static final String[] SKIP_KEYWORDS = {
            "动画", "娱乐", "生活", "音乐", "舞蹈·偶像", "游戏", "科技", "影视", "体育", "鱼塘"
    };

    // 匹配 href="...">title</a>
    private static final Pattern HREF_TITLE_PATTERN = Pattern.compile("href=\"([^\"]*)\">([^<]*)</a>");

    private static Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        return headers;
    }

    /**
     * 截取 startStr 和 endStr 之间的文本（首次匹配），等价于 Python extract_middle_text(pl=0)
     */
    private static String extractMiddleText(String text, String startStr, String endStr) {
        if (text == null) return "";
        int startIdx = text.indexOf(startStr);
        if (startIdx == -1) return "";
        int contentStart = startIdx + startStr.length();
        int endIdx = text.indexOf(endStr, contentStart);
        if (endIdx == -1) return "";
        return text.substring(contentStart, endIdx).replace("\\", "");
    }

    /**
     * 截取第 n 个（0 基） startStr 和 endStr 之间的文本，等价于 Python extract_nth_middle_text
     */
    private static String extractNthMiddleText(String text, String startStr, String endStr, int n) {
        if (text == null) return null;
        List<String> results = new ArrayList<>();
        int pos = 0;
        while (true) {
            int startIdx = text.indexOf(startStr, pos);
            if (startIdx == -1) break;
            int contentStart = startIdx + startStr.length();
            int endIdx = text.indexOf(endStr, contentStart);
            if (endIdx == -1) break;
            results.add(text.substring(contentStart, endIdx).replace("\\", ""));
            pos = endIdx + endStr.length();
        }
        if (n >= 0 && n < results.size()) return results.get(n);
        return null;
    }

    /**
     * 判断分类名是否应被跳过
     */
    private static boolean shouldSkip(String typeName) {
        for (String keyword : SKIP_KEYWORDS) {
            if (typeName.contains(keyword)) return true;
        }
        return false;
    }

    private static int parsePage(String pg) {
        if (TextUtils.isEmpty(pg)) return 1;
        try {
            return Integer.parseInt(pg);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 解析视频详情页的基本信息（content/director/actor/remarks/title）
     */
    private VideoInfo parseVideoInfo(String res, Document doc) {
        String content;
        try {
            String description = extractMiddleText(res, "description-container'>", "<");
            content = TextUtils.isEmpty(description) ? "Acfun" : "Acfun" + description;
        } catch (Exception e) {
            content = "Acfun";
        }

        String director;
        try {
            String views = extractMiddleText(res, "class='viewsCount'>", "<");
            director = (TextUtils.isEmpty(views) || TextUtils.isEmpty(views.trim()))
                    ? "无信息" : views + "播放";
        } catch (Exception e) {
            director = "无信息";
        }

        String actor;
        try {
            String likes = extractMiddleText(res, "likeCount\">", "<");
            actor = (TextUtils.isEmpty(likes) || TextUtils.isEmpty(likes.trim()))
                    ? "无信息" : likes + "点赞";
        } catch (Exception e) {
            actor = "无信息";
        }

        String remarks;
        try {
            Element tagDiv = doc.selectFirst("div.tag");
            remarks = (tagDiv != null && !TextUtils.isEmpty(tagDiv.text()))
                    ? tagDiv.text().trim() : "未知";
        } catch (Exception e) {
            remarks = "未知";
        }

        String title;
        try {
            title = extractMiddleText(res, "class=\"title\"><span>", "<");
            if (TextUtils.isEmpty(title)) {
                title = extractMiddleText(res, "<h1 class=\"title\">", "<");
            }
            if (TextUtils.isEmpty(title)) {
                Element titleEl = doc.selectFirst("title");
                if (titleEl != null) {
                    String titleText = titleEl.text();
                    title = titleText.split("_")[0].split("-")[0].trim();
                }
            }
            if (TextUtils.isEmpty(title)) title = "未知标题";
        } catch (Exception e) {
            title = "未知标题";
        }

        return new VideoInfo(content, director, actor, remarks, title);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String res = OkHttp.string(SITE_URL, getHeaders());
        String section = extractMiddleText(res, "番剧列表", "文章");

        Matcher matcher = HREF_TITLE_PATTERN.matcher(section);
        while (matcher.find()) {
            String href = matcher.group(1);
            String title = matcher.group(2);
            if (shouldSkip(title)) continue;
            classes.add(new Class(SITE_URL + href, title));
        }

        // 番剧 插入到首位
        classes.add(0, new Class(SITE_URL + "/bangumilist", "番剧"));

        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String cid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page = parsePage(pg);

        if (!cid.contains("bangumilist")) {
            // 普通分类列表
            String url = cid + "?page=" + page;
            String res = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(res);

            for (Element wrapper : doc.select("div.list-wrapper")) {
                for (Element item : wrapper.select("div.list-content-item")) {
                    Element titleLink = item.selectFirst("h1.list-content-title a");
                    if (titleLink == null) continue;

                    String name = titleLink.attr("title");
                    String id = titleLink.attr("href");
                    if (!TextUtils.isEmpty(id) && !id.contains("http")) {
                        id = SITE_URL + id;
                    }

                    Element img = item.selectFirst("img");
                    String pic = img != null ? img.attr("src") : "";

                    Element danmaku = item.selectFirst("div.danmaku-mask");
                    String remark = (danmaku != null && !TextUtils.isEmpty(danmaku.text()))
                            ? "时长 " + danmaku.text().trim() : "未知";

                    list.add(new Vod(id, TextUtils.isEmpty(name) ? "未知标题" : name, pic, remark));
                }
            }
        } else {
            // 番剧列表
            String url = cid + "?pageNum=" + page;
            String res = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(res);

            for (Element ul : doc.select("ul.ac-mod-ul")) {
                for (Element li : ul.select("li")) {
                    Element titleEl = li.selectFirst("div.ac-mod-title");
                    String name = titleEl != null ? titleEl.attr("title") : "";

                    Element linkEl = li.selectFirst("a.ac-mod-link");
                    String id = linkEl != null ? linkEl.attr("href") : "";

                    Element img = li.selectFirst("img");
                    String pic = img != null ? img.attr("src") : "";

                    String remark = extractMiddleText(li.outerHtml(), "<em>", "<");
                    if (TextUtils.isEmpty(remark)) remark = "未知";

                    list.add(new Vod(id, TextUtils.isEmpty(name) ? "未知标题" : name, pic, remark));
                }
            }
        }

        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String did = ids.get(0);
        String xianlu;
        String bofang;

        if (!did.contains("bangumi")) {
            // 普通视频详情
            String res = OkHttp.string(did, getHeaders());
            Document doc = Jsoup.parse(res);
            VideoInfo info = parseVideoInfo(res, doc);

            Element scrollDiv = doc.selectFirst("ul.scroll-div");
            if (scrollDiv != null) {
                StringBuilder sb = new StringBuilder();
                for (Element li : scrollDiv.select("li")) {
                    String id = li.attr("data-href");
                    if (!TextUtils.isEmpty(id) && !id.contains("http")) {
                        id = SITE_URL + id;
                    }
                    String name = li.attr("title");
                    sb.append(name).append("$").append(id).append("#");
                }
                if (sb.length() > 0) sb.setLength(sb.length() - 1);
                bofang = sb.toString();
                xianlu = "Acfun";
            } else {
                // 没有剧集列表，取 shareUrl
                String id = extractMiddleText(res, "\"shareUrl\":\"", "\"");
                String name = extractMiddleText(res, "class=\"title\"><span>", "<");
                if (TextUtils.isEmpty(name)) name = info.title;
                if (!TextUtils.isEmpty(name)) name = name.replace("#", "");
                else name = "未知";
                bofang = name + "$" + id;
                xianlu = "ACfun";
            }

            Vod vod = new Vod();
            vod.setVodId(did);
            vod.setVodName(TextUtils.isEmpty(info.title) ? "未知标题" : info.title);
            vod.setVodDirector(info.director);
            vod.setVodActor(info.actor);
            vod.setVodRemarks(info.remarks);
            vod.setVodContent(info.content);
            vod.setVodPlayFrom(xianlu);
            vod.setVodPlayUrl(bofang);
            return Result.string(vod);
        } else {
            // 番剧详情
            String res = OkHttp.string(did, getHeaders());
            Document doc = Jsoup.parse(res);
            VideoInfo info = parseVideoInfo(res, doc);

            String itemsStr = extractMiddleText(res, "\"items\":", "};");
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(itemsStr)) {
                try {
                    JSONArray data = new JSONArray(itemsStr);
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject sou = data.getJSONObject(i);
                        String bangumiId = String.valueOf(sou.opt("bangumiId"));
                        String itemId = String.valueOf(sou.opt("itemId"));
                        String id = bangumiId + "@" + itemId;
                        String name = sou.optString("title");
                        if (TextUtils.isEmpty(name)) name = sou.optString("episodeName");
                        if (TextUtils.isEmpty(name)) name = "未知";
                        sb.append(name).append("$").append(id).append("#");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (sb.length() > 0) sb.setLength(sb.length() - 1);
            bofang = sb.toString();
            xianlu = "Acfun";

            Vod vod = new Vod();
            vod.setVodId(did);
            vod.setVodName(TextUtils.isEmpty(info.title) ? "未知标题" : info.title);
            vod.setVodDirector(info.director);
            vod.setVodActor(info.actor);
            vod.setVodRemarks(info.remarks);
            vod.setVodContent(info.content);
            vod.setVodPlayFrom(xianlu);
            vod.setVodPlayUrl(bofang);
            return Result.string(vod);
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url;
        if (!id.contains("@")) {
            // 直接播放
            String res = OkHttp.string(id, getHeaders()).replace("\\", "");
            url = extractMiddleText(res, "[{\"id\":1,\"url\":\"", "\"");
        } else {
            // 番剧分集播放
            String[] parts = id.split("@", -1);
            String apiUrl = SITE_URL + "/bangumi/aa" + parts[0] + "_36188_" + parts[1];
            String res = OkHttp.string(apiUrl, getHeaders()).replace("\\", "");
            url = extractMiddleText(res, "[{\"id\":1,\"url\":\"", "\"");
        }
        return Result.get().url(url).header(getHeaders()).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContentPage(key, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContentPage(key, pg);
    }

    private String searchContentPage(String key, String pg) throws Exception {
        int page = parsePage(pg);
        List<Vod> list = new ArrayList<>();

        String url = SITE_URL + "/search?keyword=" + URLEncoder.encode(key, "UTF-8") + "&pCursor=" + page;
        String res = OkHttp.string(url, getHeaders());
        String snippet = extractNthMiddleText(res, "bigPipe.onPageletArrive(", "</script>", 5);
        Document doc = Jsoup.parse(snippet != null ? snippet : "");

        if (!doc.select("div.search-bangumi").isEmpty()) {
            // 番剧搜索结果
            for (Element cover : doc.select("div.bangumi__cover")) {
                Element img = cover.selectFirst("img");
                String name = img != null ? img.attr("alt") : "";
                String id1 = extractMiddleText(cover.outerHtml(), "content_id\":", ",");
                String id = SITE_URL + "/bangumi/aa" + id1;
                String pic = img != null ? img.attr("src") : "";
                Element epInfo = cover.selectFirst("span.episode-info");
                String remark = (epInfo != null && !TextUtils.isEmpty(epInfo.text()))
                        ? epInfo.text().trim() : "未知";
                list.add(new Vod(id, TextUtils.isEmpty(name) ? "未知标题" : name, pic, remark));
            }
        } else {
            // 视频搜索结果
            for (Element video : doc.select("div.search-video")) {
                Element titleDiv = video.selectFirst("div.video__main__title");
                String name = titleDiv != null ? titleDiv.text().trim() : "";
                String id = "";
                if (titleDiv != null) {
                    Element a = titleDiv.selectFirst("a");
                    id = a != null ? a.attr("href") : "";
                }
                if (!TextUtils.isEmpty(id) && !id.contains("http")) {
                    id = SITE_URL + id;
                }
                Element img = video.selectFirst("img");
                String pic = img != null ? img.attr("src") : "";
                Element dur = video.selectFirst("span.video__duration");
                String remark = (dur != null && !TextUtils.isEmpty(dur.text()))
                        ? "时长 " + dur.text().trim() : "未知";
                list.add(new Vod(id, TextUtils.isEmpty(name) ? "未知标题" : name, pic, remark));
            }
        }

        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }

    /**
     * 视频详情信息持有类
     */
    private static class VideoInfo {
        final String content;
        final String director;
        final String actor;
        final String remarks;
        final String title;

        VideoInfo(String content, String director, String actor, String remarks, String title) {
            this.content = content;
            this.director = director;
            this.actor = actor;
            this.remarks = remarks;
            this.title = title;
        }
    }
}
