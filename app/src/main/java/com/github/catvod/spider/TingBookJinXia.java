package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 堇夏听书 Spider - HTML爬虫+Filter型,支持分类筛选
 */
public class TingBookJinXia extends Spider {

    private static final String DEFAULT_HOST = "https://m.ting15.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String PLAY_FROM = "堇夏";
    private static final String PIC_SUFFIX = "@Referer=https://m.ting15.com/@User-Agent=Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static String hostUrl = DEFAULT_HOST;

    private static final String[][] CATEGORIES = new String[][]{
            {"wuxiaxuanhuan", "武侠玄幻"},
            {"kongbulingyi", "恐怖灵异"},
            {"tuilixuanyi", "推理悬疑"},
            {"dushiyanqing", "都市言情"},
            {"jiatinglunli", "家庭伦理"},
            {"wenxuemingzhu", "文学名著"},
            {"jingdianpingshu", "经典评书"},
            {"quyixiqu", "曲艺戏曲"},
            {"xiangshengxiaopin", "相声小品"}
    };

    @Override
    public void init(Context context, String extend) throws Exception {
        Init.init(context);
        if (!TextUtils.isEmpty(extend)) {
            hostUrl = extend.trim();
        }
        if (hostUrl.endsWith("/")) {
            hostUrl = hostUrl.substring(0, hostUrl.length() - 1);
        }
    }

    private final Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", hostUrl + "/");
        headers.put("Accept", ACCEPT);
        return headers;
    }

    private final Document fetchDocument(String path) {
        String url = normalizeUrl(path);
        String html = OkHttp.string(url, buildHeaders());
        return Jsoup.parse(html);
    }

    private final String normalizeUrl(String path) {
        if (TextUtils.isEmpty(path)) {
            return hostUrl;
        }
        if (path.startsWith("http")) {
            return path;
        }
        if (path.startsWith("//")) {
            return "https:" + path;
        }
        if (path.startsWith("/")) {
            return hostUrl + path;
        }
        return hostUrl + "/" + path;
    }

    private static String extractInfo(String prefix, Document doc) {
        Elements infos = doc.select(".binfo p");
        for (Element info : infos) {
            String text = info.text().trim();
            if (text.startsWith(prefix) && text.length() > prefix.length()) {
                return text.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private final ArrayList<Vod> parseVodList(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        String statusPrefix = "连载";
        String statusAlt = "状态";

        Elements items = doc.select(".clist > a[href]");
        if (items.isEmpty()) {
            items = doc.select("section a[href$=.html]:has(h3)");
        }

        for (Element item : items) {
            try {
                String href = item.attr("href").trim();
                if (href.isEmpty() || href.startsWith("http") || href.startsWith("javascript:")) {
                    continue;
                }

                String title = "";
                Elements h3s = item.select("h3");
                if (!h3s.isEmpty()) {
                    String h3Text = h3s.first().text().trim();
                    if (h3Text.startsWith("[") && h3Text.contains("]")) {
                        int bracketIdx = h3Text.indexOf("]");
                        if (bracketIdx >= 0 && bracketIdx + 1 < h3Text.length()) {
                            title = h3Text.substring(bracketIdx + 1).trim();
                        }
                    }
                    if (TextUtils.isEmpty(title)) {
                        title = h3Text;
                    }
                }
                if (TextUtils.isEmpty(title)) {
                    title = item.text().trim();
                }

                String pic = "";
                Elements dtImgs = item.select("dt img");
                if (!dtImgs.isEmpty()) {
                    pic = dtImgs.first().attr("src");
                }
                if (TextUtils.isEmpty(pic)) {
                    Elements imgs = item.select("img");
                    if (!imgs.isEmpty()) {
                        pic = imgs.first().attr("src");
                    }
                }
                if (!TextUtils.isEmpty(pic) && !pic.contains("@Referer=")) {
                    pic = pic + PIC_SUFFIX;
                }

                String remark = "听书";
                Elements dds = item.select("dd p");
                for (Element dd : dds) {
                    String ddText = dd.text().trim();
                    if (ddText.startsWith(statusPrefix) || ddText.startsWith(statusAlt)) {
                        ddText = ddText.replace("状态：", "").replace("状态:", "").trim();
                        if (ddText.startsWith(statusPrefix)) {
                            ddText = ddText.replace("连载：", "").replace("连载:", "").trim();
                        }
                        remark = ddText;
                        break;
                    }
                }

                if (!TextUtils.isEmpty(title)) {
                    Vod vod = new Vod(href, title, pic, remark, Vod.Style.list());
                    list.add(vod);
                }

                if (list.size() >= 95) {
                    break;
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            classes.add(new Class(CATEGORIES[i][0], CATEGORIES[i][1]));
        }

        ArrayList<Vod> list = new ArrayList<>();
        try {
            Document doc = fetchDocument("/");
            list = parseVodList(doc);
        } catch (Exception e) {
            // 忽略错误
        }

        if (!filter) {
            return Result.string(classes, list);
        }

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        ArrayList<Filter.Value> orderValues = new ArrayList<>();
        orderValues.add(new Filter.Value("全部", ""));
        orderValues.add(new Filter.Value("热门", "hits"));
        orderValues.add(new Filter.Value("最新", "addtime"));

        ArrayList<Filter> filterList = new ArrayList<>();
        filterList.add(new Filter("order", "排序", orderValues));

        for (int i = 0; i < CATEGORIES.length; i++) {
            filters.put(CATEGORIES[i][0], filterList);
        }

        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Math.max(Integer.parseInt(pg), 1);
        } catch (Exception e) {
            page = 1;
        }

        String order = "";
        if (extend != null && extend.containsKey("order")) {
            order = extend.get("order");
            if (order == null) {
                order = "";
            }
        }

        StringBuilder pathBuilder = new StringBuilder();
        if (!TextUtils.isEmpty(order)) {
            pathBuilder.append("/").append(tid).append("/index").append(page).append("-order-").append(order);
        } else {
            pathBuilder.append("/").append(tid);
            if (page > 1) {
                pathBuilder.append("/index").append(page);
            }
        }
        pathBuilder.append(".html");

        Document doc = fetchDocument(pathBuilder.toString());
        ArrayList<Vod> list = parseVodList(doc);

        int totalPages = page;
        Elements pageSpans = doc.select(".cpage span");
        for (Element span : pageSpans) {
            String spanText = span.text().trim();
            if (!spanText.contains("/")) {
                continue;
            }
            String[] parts = spanText.split("/");
            if (parts.length < 2) {
                continue;
            }
            try {
                int maxPage = Integer.parseInt(parts[1].trim());
                totalPages = Math.max(maxPage, page);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        return Result.get().page(page, totalPages, list.size(), list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        Document doc = fetchDocument(vodId);

        String title = doc.select(".binfo h1").text().trim();

        String pic = doc.select(".bimg img").attr("src");
        if (!TextUtils.isEmpty(pic) && !pic.contains("@Referer=")) {
            pic = pic + PIC_SUFFIX;
        }

        String author = extractInfo("作者", doc);
        String type = extractInfo("类型", doc);
        String status = extractInfo("状态", doc);
        String播音 = extractInfo("播音", doc);

        String content = "";
        Elements introPs = doc.select(".intro p");
        for (Element introP : introPs) {
            String introText = introP.text().trim();
            if (introText.isEmpty() || introText.contains("看APP")) {
                continue;
            }
            if (introText.length() > 20) {
                content = introText;
                break;
            }
        }

        ArrayList<String> episodes = new ArrayList<>();
        Elements playLinks = doc.select(".plist a.f");
        for (Element playLink : playLinks) {
            String href = playLink.attr("href").trim();
            String epName = playLink.text().trim();
            if (href.isEmpty() || epName.isEmpty()) {
                continue;
            }
            episodes.add(epName + "$" + href);
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(title);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setVodActor(author);
        vod.setVodDirector(播音);
        vod.setTypeName(type);
        vod.setVodRemarks(status);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(TextUtils.join("#", episodes));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = "";
        if (id.contains("$")) {
            int lastDollar = id.lastIndexOf('$');
            playUrl = id.substring(lastDollar + 1);
        }

        if (!playUrl.startsWith("/")) {
            playUrl = "/" + playUrl;
        }

        Document doc = fetchDocument(playUrl);

        String _b = doc.select("meta[name=_b]").attr("content");
        String _p = doc.select("meta[name=_p]").attr("content");
        String _cp = doc.select("meta[name=_cp]").attr("content");
        String _c = doc.select("meta[name=_c]").attr("content");

        if (!TextUtils.isEmpty(_b) && !TextUtils.isEmpty(_p)) {
            HashMap<String, String> params = new HashMap<>();
            params.put("bookId", _b);
            params.put("isPay", _p);
            if (TextUtils.isEmpty(_cp)) {
                _cp = "1";
            }
            params.put("page", _cp);
            if (!TextUtils.isEmpty(_c)) {
                params.put("xt", _c);
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Referer", hostUrl + playUrl);

            try {
                String apiUrl = hostUrl + "/?s=api-getneoplay";
                String response = OkHttp.post(apiUrl, params, headers).getBody();
                if (response.startsWith("\ufeff")) {
                    response = response.substring(1);
                }
                org.json.JSONObject json = new org.json.JSONObject(response);
                if (json.optInt("status", 0) == 1) {
                    playUrl = json.optString("url", "");
                    if (TextUtils.isEmpty(playUrl)) {
                        playUrl = json.optString("ourl", "");
                    }
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }

        if (TextUtils.isEmpty(playUrl)) {
            Elements audioPlayers = doc.select("audio#player");
            if (!audioPlayers.isEmpty()) {
                playUrl = audioPlayers.first().attr("src");
            }
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", hostUrl + "/");

        if (TextUtils.isEmpty(playUrl)) {
            return Result.get().url(hostUrl + playUrl).header(headers).parse(0).string();
        }
        return Result.get().url(playUrl).header(headers).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        StringBuilder pathBuilder = new StringBuilder("/?s=ting-search-wd-");
        pathBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()));

        Document doc = fetchDocument(pathBuilder.toString());
        ArrayList<Vod> list = parseVodList(doc);

        return Result.string(list);
    }
}