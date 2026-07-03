package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TingBookJinXia Spider - HTML 爬虫型听书网站
 */
public class TingBookJinXia extends Spider {

    // DEX 同名不同类型字段重命名（规则 1）
    private static String BASE_URL = "https://m.ting15.com";
    private static final String[][] CATEGORIES;

    // 静态初始化块：初始化分类数组
    static {
        CATEGORIES = new String[][]{
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
    }

    // 构造函数
    public TingBookJinXia() {
        super();
    }

    /**
     * 从 HTML 中提取指定字段信息（静态辅助方法）
     * merge 辅助类还原：直接使用 Jsoup API
     */
    private static String b(String prefix, Document doc) {
        Elements elements = doc.select(".binfo p");
        for (Element element : elements) {
            String text = element.text().trim();
            if (text.startsWith(prefix) && text.length() > prefix.length()) {
                return text.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    /**
     * 获取 Document（HTML 解析）
     * merge 辅助类映射：C0051a.m970N3() → Jsoup.parse(html)
     */
    private final Document a(String path) {
        // URL 补全逻辑（4 种情况）
        String url;
        if (TextUtils.isEmpty(path)) {
            url = BASE_URL;
        } else if (path.startsWith("http")) {
            url = path;
        } else if (path.startsWith("//")) {
            url = "https:" + path;
        } else if (path.startsWith("/")) {
            url = BASE_URL + path;
        } else {
            url = BASE_URL + "/" + path;
        }

        // 构建请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        headers.put("Referer", BASE_URL + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        // 获取 HTML 并解析为 Document
        String html = OkHttp.string(url, headers);
        return Jsoup.parse(html); // merge/B/a.N3() → Jsoup.parse()
    }

    /**
     * 从 Document 中解析 Vod 列表
     * merge 辅助类还原：Jsoup Element/Elements API
     */
    private final ArrayList<Vod> c(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        Elements elements = doc.select(".clist > a[href]");
        if (elements.isEmpty()) {
            elements = doc.select("section a[href$=.html]:has(h3)");
        }

        for (Element element : elements) {
            try {
                String href = element.attr("href").trim();
                if (href.isEmpty() || href.startsWith("http") || href.startsWith("javascript:")) {
                    continue;
                }

                // 提取标题
                String name = element.select("h3").text().trim();
                if (name.startsWith("[") && name.contains("]")) {
                    int idx = name.indexOf("]");
                    if (idx >= 0 && idx + 1 < name.length()) {
                        name = name.substring(idx + 1).trim();
                    }
                }
                if (TextUtils.isEmpty(name)) {
                    name = element.text().trim();
                }

                // 提取封面图
                String pic = element.select("dt img").attr("src");
                if (TextUtils.isEmpty(pic)) {
                    pic = element.select("img").attr("src");
                }
                if (!TextUtils.isEmpty(pic) && !pic.contains("@Referer=")) {
                    pic += "@Referer=https://m.ting15.com/@User-Agent=Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
                }

                // 提取备注（状态）
                String remark = "听书";
                Elements pElements = element.select("dd p");
                for (Element p : pElements) {
                    String pText = p.text().trim();
                    if (pText.startsWith("连载") || pText.startsWith("状态")) {
                        pText = pText.replace("状态：", "").replace("状态:", "").trim();
                        if (pText.startsWith("连载")) {
                            pText = pText.replace("连载：", "").replace("连载:", "").trim();
                        }
                        remark = pText;
                        break;
                    }
                }

                if (!TextUtils.isEmpty(name)) {
                    Vod vod = new Vod(href, name, pic, remark, Vod.Style.list());
                    list.add(vod);
                }

                // 限制每页最多 95 条
                if (list.size() >= 95) {
                    break;
                }
            } catch (Exception e) {
                // 异常时跳过该项
            }
        }

        return list;
    }

    // ========== Spider 标准方法（所有 @Override 方法必须声明 throws Exception - 规则 2）==========

    @Override
    public void init(Context context, String extend) throws Exception {
        Init.init(context);
        if (!TextUtils.isEmpty(extend)) {
            BASE_URL = extend.trim();
        }
        // 去除末尾斜杠
        if (BASE_URL.endsWith("/")) {
            BASE_URL = BASE_URL.substring(0, BASE_URL.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            classes.add(new Class(CATEGORIES[i][0], CATEGORIES[i][1]));
        }

        ArrayList<Vod> list = new ArrayList<>();
        try {
            Document doc = a("/");
            list = c(doc);
        } catch (Exception e) {
            // 异常时返回空列表
        }

        if (!filter) {
            return Result.string(classes, list);
        }

        // 构建筛选器
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        Filter[] filterArray = new Filter[]{
                new Filter("order", "排序", Arrays.asList(
                        new Filter.Value("全部", ""),
                        new Filter.Value("热门", "hits"),
                        new Filter.Value("最新", "addtime")
                ))
        };
        List<Filter> filterList = Arrays.asList(filterArray);

        // 为每个分类添加筛选器
        for (int i = 0; i < 9; i++) {
            filters.put(CATEGORIES[i][0], filterList);
        }

        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 解析页码
        int page;
        try {
            page = Math.max(Integer.parseInt(pg), 1);
        } catch (Exception e) {
            page = 1;
        }

        // 解析排序参数
        String order = "";
        if (extend != null && extend.containsKey("order")) {
            order = extend.get("order");
            if (order == null) {
                order = "";
            }
        }

        // 构建 URL
        StringBuilder urlBuilder;
        if (!TextUtils.isEmpty(order)) {
            urlBuilder = new StringBuilder("/")
                    .append(tid)
                    .append("/index")
                    .append(page)
                    .append("-order-")
                    .append(order);
        } else {
            urlBuilder = new StringBuilder("/").append(tid);
            if (page > 1) {
                urlBuilder.append("/index").append(page);
            } else {
                urlBuilder.append("/");
            }
        }
        urlBuilder.append(".html");

        String url = urlBuilder.toString();
        Document doc = a(url);
        ArrayList<Vod> list = c(doc);

        // 解析总页数
        int totalPage = page;
        Elements pageElements = doc.select(".cpage span");
        for (Element span : pageElements) {
            String text = span.text().trim();
            if (!text.contains("/")) {
                continue;
            }
            String[] parts = text.split("/");
            if (parts.length >= 2) {
                try {
                    int foundTotal = Integer.parseInt(parts[1].trim());
                    totalPage = Math.max(foundTotal, page);
                    break;
                } catch (Exception e) {
                    // 解析失败继续
                }
            }
        }

        return Result.string(page, totalPage, list.size(), list.size(), list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        Document doc = a(id);

        // 提取标题
        String name = doc.select(".binfo h1").text().trim();

        // 提取封面图
        String pic = doc.select(".bimg img").attr("src");
        if (!TextUtils.isEmpty(pic) && !pic.contains("@Referer=")) {
            pic += "@Referer=https://m.ting15.com/@User-Agent=Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        }

        // 提取作者、类型、状态、播音
        String actor = b("作者", doc);
        String typeName = b("类型", doc);
        String remark = b("状态", doc);
        String director = b("播音", doc);

        // 提取简介
        String content = "";
        Elements introElements = doc.select(".intro p");
        for (Element p : introElements) {
            String text = p.text().trim();
            if (!text.isEmpty() && !text.contains("看APP") && text.length() > 20) {
                content = text;
                break;
            }
        }

        // 提取播放列表
        ArrayList<String> playUrls = new ArrayList<>();
        Elements playElements = doc.select(".plist a.f");
        for (Element play : playElements) {
            String href = play.attr("href").trim();
            String title = play.text().trim();
            if (!href.isEmpty() && !title.isEmpty()) {
                playUrls.add(title + "$" + href);
            }
        }

        // 构建 Vod 对象
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setVodActor(actor);
        vod.setVodDirector(director);
        vod.setTypeName(typeName);
        vod.setVodRemarks(remark);
        vod.setVodPlayFrom("听夏");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 提取播放 URL（处理 $ 分隔符）
        if (id.contains("$")) {
            int lastIdx = id.lastIndexOf('$') + 1;
            id = id.substring(lastIdx);
        }

        // 补全 URL
        if (!id.startsWith("/")) {
            id = "/" + id;
        }

        Document doc = a(id);

        // 提取 meta 标签参数
        String bookId = doc.select("meta[name=_b]").attr("content");
        String isPay = doc.select("meta[name=_p]").attr("content");
        String page = doc.select("meta[name=_cp]").attr("content");
        String xt = doc.select("meta[name=_c]").attr("content");

        String playUrl = "";

        // 尝试通过 API 获取播放链接
        if (!TextUtils.isEmpty(bookId) && !TextUtils.isEmpty(isPay)) {
            HashMap<String, String> params = new HashMap<>();
            params.put("bookId", bookId);
            params.put("isPay", isPay);
            if (TextUtils.isEmpty(page)) {
                page = "1";
            }
            params.put("page", page);
            if (!TextUtils.isEmpty(xt)) {
                params.put("xt", xt);
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            headers.put("Referer", BASE_URL + id);

            try {
                String apiUrl = BASE_URL + "/?s=api-getneoplay";
                String response = OkHttp.post(apiUrl, params, headers);

                // 去除 BOM 头
                if (response.startsWith("\ufeff")) {
                    response = response.substring(1);
                }

                JSONObject json = new JSONObject(response);
                if (json.optInt("status", 0) == 1) {
                    playUrl = json.optString("url", "");
                    if (TextUtils.isEmpty(playUrl)) {
                        playUrl = json.optString("ourl", "");
                    }
                }
            } catch (Exception e) {
                // API 失败时继续
            }
        }

        // 回退：从 audio 标签提取
        if (TextUtils.isEmpty(playUrl)) {
            playUrl = doc.select("audio#player").attr("src");
        }

        // 构建返回结果
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        headers.put("Referer", BASE_URL + "/");

        Result result = Result.get();
        if (TextUtils.isEmpty(playUrl)) {
            result.url(BASE_URL + id);
        } else {
            result.url(playUrl);
        }
        result.header(headers).parse(0);

        return result.string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = "/?s=ting-search-wd-" + URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        Document doc = a(url);
        ArrayList<Vod> list = c(doc);
        return Result.string(list);
    }
}