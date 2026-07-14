package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 爬虫名称：韩剧TV
 * 爬虫类型：MacCMS v10 HTML解析型
 * 网站地址：https://www.hanjue.cc/
 * 作者：摄氏零度
 */
public class Hjtv extends Spider {

    private static final String SITE_URL = "https://www.hanjue.cc";
    private String siteUrl = SITE_URL;

    private final HashMap<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
        put("Referer", SITE_URL);
    }};

    // 分类配置（韩剧$20#韩影$23#日剧$19#日影$25#泰剧$21#泰影$24#电影$2）
    private final List<String> typeIds = Arrays.asList("20", "23", "19", "25", "21", "24", "2");
    private final List<String> typeNames = Arrays.asList("韩剧", "韩影", "日剧", "日影", "泰剧", "泰影", "电影");

    // 排序配置（最新上映&超高人气&全网热播&高分好评）
    private final List<String> sortNames = Arrays.asList("最新上映", "超高人气", "全网热播", "高分好评");
    private final List<String> sortValues = Arrays.asList("time", "hits", "up", "score");

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
        List<Vod> videos = new ArrayList<>();
        List<Map<String, String>> filters = new ArrayList<>();

        // 添加分类
        for (int i = 0; i < typeIds.size(); i++) {
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        }

        // 添加排序筛选（每个分类都支持）
        Map<String, String> sortFilter = new HashMap<>();
        for (int i = 0; i < sortNames.size(); i++) {
            sortFilter.put(sortNames.get(i), sortValues.get(i));
        }
        filters.add(sortFilter);

        // 获取首页推荐
        try {
            String html = OkHttp.string(siteUrl, headers);
            Document doc = Jsoup.parse(html);

            // 使用XBPQ配置的选择器：hl-lazy"&&</a
            // 解析为：包含class="hl-lazy"的a标签
            Elements items = doc.select("a[class*=hl-lazy]");

            for (Element item : items) {
                try {
                    String href = item.attr("href");
                    String title = item.attr("title");
                    String pic = item.attr("data-original");
                    String remark = "";

                    // 提取副标题：remarks\">&&</
                    Element remarkElem = item.selectFirst(".remarks");
                    if (remarkElem != null) {
                        remark = remarkElem.text();
                    }

                    if (TextUtils.isEmpty(title)) {
                        // 尝试从img标签获取
                        Element img = item.selectFirst("img");
                        if (img != null) {
                            title = img.attr("alt");
                            if (TextUtils.isEmpty(pic)) {
                                pic = img.attr("data-original");
                                if (TextUtils.isEmpty(pic)) {
                                    pic = img.attr("src");
                                }
                            }
                        }
                    }

                    if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(title)) {
                        videos.add(new Vod(
                            href,
                            title,
                            fixUrl(pic),
                            remark
                        ));
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.string(classes, videos);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page = Util.toInt(pg, 1);
        int pageCount = 1;
        int total = 0;
        int limit = 36;

        try {
            // 分类URL模板：/vodshow/{cateId}-{area}-{by}-{class}-{lang}-{letter}---{catePg}---{year}.html
            // 根据XBPQ配置构造URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(siteUrl).append("/vodshow/");
            urlBuilder.append(tid);

            // 处理筛选参数
            String area = extend != null ? extend.get("area") : null;
            String by = extend != null ? extend.get("by") : null;
            String lang = extend != null ? extend.get("lang") : null;
            String letter = extend != null ? extend.get("letter") : null;
            String year = extend != null ? extend.get("year") : null;

            // 构建URL
            urlBuilder.append("-").append(area != null ? area : "");
            urlBuilder.append("-").append(by != null ? by : "");
            urlBuilder.append("-").append(extend != null && extend.get("class") != null ? extend.get("class") : "");
            urlBuilder.append("-").append(lang != null ? lang : "");
            urlBuilder.append("-").append(letter != null ? letter : "");
            urlBuilder.append("---").append(page);
            urlBuilder.append("---").append(year != null ? year : "");
            urlBuilder.append(".html");

            String url = urlBuilder.toString();
            SpiderDebug.log("Category URL: " + url);

            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);

            // 解析视频列表（hl-lazy"&&</a）
            Elements items = doc.select("a[class*=hl-lazy]");

            for (Element item : items) {
                try {
                    String href = item.attr("href");
                    String title = item.attr("title");
                    String pic = item.attr("data-original");
                    String remark = "";

                    Element remarkElem = item.selectFirst(".remarks");
                    if (remarkElem != null) {
                        remark = remarkElem.text();
                    }

                    if (TextUtils.isEmpty(title)) {
                        Element img = item.selectFirst("img");
                        if (img != null) {
                            title = img.attr("alt");
                            if (TextUtils.isEmpty(pic)) {
                                pic = img.attr("data-original");
                                if (TextUtils.isEmpty(pic)) {
                                    pic = img.attr("src");
                                }
                            }
                        }
                    }

                    if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(title)) {
                        list.add(new Vod(
                            href,
                            title,
                            fixUrl(pic),
                            remark
                        ));
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }

            // 尝试解析分页信息
            Element pageInfo = doc.selectFirst(".hl-page-wrap");
            if (pageInfo != null) {
                Elements pageLinks = pageInfo.select("a[href]");
                for (Element pageLink : pageLinks) {
                    String pageNum = pageLink.text();
                    if (pageNum.matches("\\d+")) {
                        pageCount = Math.max(pageCount, Util.toInt(pageNum, 1));
                    }
                }
            }

            // 如果没有获取到总数，根据列表长度估算
            if (total == 0) {
                total = list.size() < limit ? list.size() : list.size() * page;
                pageCount = (total + limit - 1) / limit;
                if (pageCount == 0) pageCount = 1;
            }

        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.get().page(page, pageCount, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        List<Vod> result = new ArrayList<>();

        for (String vid : ids) {
            try {
                String detailUrl = fixUrl(vid);
                String html = OkHttp.string(detailUrl, headers);
                Document doc = Jsoup.parse(html);

                // 解析基本信息
                String name = "";
                String pic = "";
                String director = "";
                String actor = "";
                String area = "";
                String year = "";
                String state = "";
                String type = "";
                String remarks = "";

                // 标题
                Element titleElem = doc.selectFirst("h1.title");
                if (titleElem == null) {
                    titleElem = doc.selectFirst("h1");
                }
                if (titleElem != null) {
                    name = titleElem.text();
                }

                // 图片
                Element picElem = doc.selectFirst("img[data-original]");
                if (picElem != null) {
                    pic = picElem.attr("data-original");
                }
                if (TextUtils.isEmpty(pic)) {
                    picElem = doc.selectFirst(".hl-pic img");
                    if (picElem != null) {
                        pic = picElem.attr("src");
                    }
                }

                // 解析详情信息（XBPQ配置的提取规则）
                // 年份：年份：&&</p
                Element yearElem = doc.selectFirst("p:contains(年份：)");
                if (yearElem != null) {
                    year = yearElem.text().replace("年份：", "").trim();
                }

                // 地区：地区：&&</p
                Element areaElem = doc.selectFirst("p:contains(地区：)");
                if (areaElem != null) {
                    area = areaElem.text().replace("地区：", "").trim();
                }

                // 类型：类型：&&</p
                Element typeElem = doc.selectFirst("p:contains(类型：)");
                if (typeElem != null) {
                    type = typeElem.text().replace("类型：", "").trim();
                }

                // 状态：状态：&&</p
                Element stateElem = doc.selectFirst("p:contains(状态：)");
                if (stateElem != null) {
                    state = stateElem.text().replace("状态：", "").trim();
                }

                // 导演：导演：&&</p
                Element directorElem = doc.selectFirst("p:contains(导演：)");
                if (directorElem != null) {
                    director = directorElem.text().replace("导演：", "").trim();
                }

                // 主演：主演：&&</p
                Element actorElem = doc.selectFirst("p:contains(主演：)");
                if (actorElem != null) {
                    actor = actorElem.text().replace("主演：", "").trim();
                }

                // 简介：简介：&&</p
                Element descElem = doc.selectFirst("p:contains(简介：)");
                String desc = "";
                if (descElem != null) {
                    desc = descElem.text().replace("简介：", "").trim();
                }

                // 解析线路和播放列表
                List<String> playFromList = new ArrayList<>();
                List<String> playUrlList = new ArrayList<>();

                // 线路数组：alt=\"&&</a
                // 播放数组：hl-sort-list&&</ul>
                Elements playSources = doc.select(".hl-sort-list");
                Elements routeElems = doc.select("a[href^=#playlist]");

                // 如果没有明确的线路标签，使用默认线路
                if (routeElems.isEmpty() && !playSources.isEmpty()) {
                    routeElems = doc.select("ul.hl-sort-list");
                }

                for (int i = 0; i < playSources.size(); i++) {
                    Element source = playSources.get(i);

                    // 线路标题
                    String routeName = "线路" + (i + 1);
                    if (i < routeElems.size()) {
                        routeName = routeElems.get(i).text();
                        if (TextUtils.isEmpty(routeName)) {
                            routeName = routeElems.get(i).attr("alt");
                        }
                    }

                    // 播放列表：<a&&/a>[不包含:展开全部]
                    Elements episodes = source.select("a");
                    List<String> episodeList = new ArrayList<>();

                    for (Element ep : episodes) {
                        String epName = ep.text();
                        String epUrl = ep.attr("href");

                        // 排除"展开全部"
                        if (!TextUtils.isEmpty(epName) && !epName.contains("展开全部") && !TextUtils.isEmpty(epUrl)) {
                            // 播放标题：>&&<
                            // 播放链接：href=\"&&\"
                            episodeList.add(epName + "$" + fixUrl(epUrl));
                        }
                    }

                    if (!episodeList.isEmpty()) {
                        playFromList.add(routeName);
                        playUrlList.add(join(episodeList, "#"));
                    }
                }

                // 创建Vod对象
                Vod vod = new Vod();
                vod.setVodId(vid);
                vod.setVodName(name);
                vod.setVodPic(fixUrl(pic));
                vod.setVodRemarks(remarks);
                vod.setVodYear(year);
                vod.setVodArea(area);
                vod.setVodDirector(director);
                vod.setVodActor(actor);
                vod.setVodContent(desc);
                vod.setVodPlayFrom(join(playFromList, "$$$"));
                vod.setVodPlayUrl(join(playUrlList, "$$$"));

                result.add(vod);

            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        return Result.string(result);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page = Util.toInt(pg, 1);

        try {
            // 搜索URL：/vodsearch/{wd}----------{pg}---.html
            String searchUrl = siteUrl + "/vodsearch/" + Util.encode(key) + "----------" + page + "---.html";
            SpiderDebug.log("Search URL: " + searchUrl);

            String html = OkHttp.string(searchUrl, headers);
            Document doc = Jsoup.parse(html);

            // 搜索数组：hl-lazy"&&</a
            Elements items = doc.select("a[class*=hl-lazy]");

            for (Element item : items) {
                try {
                    String href = item.attr("href");
                    String title = item.attr("title");
                    String pic = item.attr("data-original");
                    String remark = "";

                    Element remarkElem = item.selectFirst(".remarks");
                    if (remarkElem != null) {
                        remark = remarkElem.text();
                    }

                    if (TextUtils.isEmpty(title)) {
                        Element img = item.selectFirst("img");
                        if (img != null) {
                            title = img.attr("alt");
                            if (TextUtils.isEmpty(pic)) {
                                pic = img.attr("data-original");
                                if (TextUtils.isEmpty(pic)) {
                                    pic = img.attr("src");
                                }
                            }
                        }
                    }

                    if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(title)) {
                        list.add(new Vod(
                            href,
                            title,
                            fixUrl(pic),
                            remark
                        ));
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }

        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String playUrl = fixUrl(id);
            String html = OkHttp.string(playUrl, headers);

            // 跳转播放链接：var player_*\"url\":\"&&\"
            // 使用正则表达式提取播放URL
            Pattern pattern = Pattern.compile("var\\s+player[^=]*=\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);

            String videoUrl = "";
            if (matcher.find()) {
                videoUrl = matcher.group(1);
                // 处理可能的转义字符
                videoUrl = videoUrl.replace("\\/", "/");
            }

            if (TextUtils.isEmpty(videoUrl)) {
                // 尝试其他常见的播放器变量
                String[] patterns = {
                    "var\\s+url\\s*=\\s*['\"]([^'\"]+)['\"]",
                    "url:\\s*['\"]([^'\"]+)['\"]",
                    "\"url\"\\s*:\\s*\"([^\"]+)\""
                };

                for (String p : patterns) {
                    Matcher m = Pattern.compile(p).matcher(html);
                    if (m.find()) {
                        videoUrl = m.group(1);
                        break;
                    }
                }
            }

            if (!TextUtils.isEmpty(videoUrl)) {
                // 判断是否为直链
                if (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4")) {
                    // 直链，parse=0
                    return Result.get()
                        .url(fixUrl(videoUrl))
                        .string();
                } else {
                    // 非直链，parse=1（让客户端处理）
                    return Result.get()
                        .url(fixUrl(videoUrl))
                        .parse(1)
                        .header(headers)
                        .string();
                }
            }

            // 如果无法提取到播放链接，返回原始播放页URL，让客户端处理
            return Result.get()
                .url(playUrl)
                .parse(1)
                .header(headers)
                .string();

        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * URL修复方法
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return siteUrl + url;
        return siteUrl + "/" + url;
    }

    /**
     * 字符串连接方法
     */
    private String join(List<String> list, String delimiter) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public String getName() {
        return "韩剧TV";
    }
}