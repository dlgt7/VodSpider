package com.github.catvod.spider;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class TVBYB extends Spider {

    // 发布页
    private static final String RELEASE_URL = "http://www.hktvyb.cc/";

    // 备用URL列表
    private static final String[] BACKUP_URLS = {
            "http://www.viptvb08.com",  // 网址1(电信)
            "http://www.tvyun04.com",   // 网址2(移动)
            "http://www.tvyun03.com",   // 网址3
            "http://www.viptvb06.com"   // 旧网址
    };

    // 当前使用的主站点URL
    private static final String SITE_URL = "http://www.viptvb08.com";

    private static final Pattern ID_PATTERN = Pattern.compile("/id/(\\d+)");
    private static final Pattern ID_PATTERN2 = Pattern.compile("-id-(\\d+)");
    private static final String VERIFY_CACHE_KEY = "tvbyb_verify_cookie";
    private static final long VERIFY_TTL_MS = 30 * 60 * 1000; // 30分钟

    private final HashMap<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
    }};

    private String verifyCookie = "";
    private long verifiedAt = 0;
    private SharedPreferences sharedPreferences;
    private String ddddOcrApi = ""; // 外部验证服务API地址

    public String getName() {
        return "TVBYB";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (context != null) {
            sharedPreferences = context.getSharedPreferences("tvbyb_cache", Context.MODE_PRIVATE);
            loadVerifyCache();
        }
        // 从extend参数解析外部验证服务API地址
        if (!TextUtils.isEmpty(extend)) {
            try {
                // extend格式可以是JSON: {"ddddocr_api":"http://xxx"} 或直接URL字符串
                if (extend.startsWith("{")) {
                    org.json.JSONObject json = new org.json.JSONObject(extend);
                    if (json.has("ddddocr_api")) {
                        ddddOcrApi = json.getString("ddddocr_api").replaceAll("/$", "");
                    }
                } else {
                    // 直接作为API地址
                    ddddOcrApi = extend.replaceAll("/$", "");
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("16", "欧美剧"));
        classes.add(new Class("13", "国产剧"));
        classes.add(new Class("15", "日韩剧"));
        classes.add(new Class("14", "港台剧"));

        List<Vod> videos = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        if (filter) {
            filters.put("1", getFilterData("1"));
            filters.put("2", getFilterData("2"));
            filters.put("3", getFilterData("3"));
            filters.put("4", getFilterData("4"));
            filters.put("13", getFilterData("2"));
            filters.put("14", getFilterData("2"));
            filters.put("15", getFilterData("2"));
            filters.put("16", getFilterData("2"));
        }

        try {
            String url = SITE_URL + "/index.php/vod/show/id/1.html";
            String content = requestWithVerify(url);
            if (!TextUtils.isEmpty(content)) {
                Document doc = Jsoup.parse(content);
                Elements items = doc.select("ul.myui-vodlist li");

                for (Element item : items) {
                    Element a = item.select("a").first();
                    if (a == null) continue;

                    String title = a.attr("title");
                    String pic = item.select(".lazyload").attr("data-original");
                    String href = a.attr("href");
                    String remarks = item.select(".tag").text();

                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(href)) {
                        String id = getIdFromHref(href);
                        pic = fixUrl(pic);
                        videos.add(new Vod(id, title, pic, remarks));
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.string(classes, videos, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            String url = SITE_URL + "/index.php/vod/show/id/1.html";
            String content = requestWithVerify(url);
            if (!TextUtils.isEmpty(content)) {
                Document doc = Jsoup.parse(content);
                Elements items = doc.select("ul.myui-vodlist li");

                for (Element item : items) {
                    Element a = item.select("a").first();
                    if (a == null) continue;

                    String title = a.attr("title");
                    String pic = item.select(".lazyload").attr("data-original");
                    String href = a.attr("href");
                    String remarks = item.select(".tag").text();

                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(href)) {
                        String id = getIdFromHref(href);
                        pic = fixUrl(pic);
                        list.add(new Vod(id, title, pic, remarks));
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        List<Vod> list = new ArrayList<>();
        int pageCount = 999;
        int total = 999999;

        try {
            StringBuilder filterStr = new StringBuilder();
            if (extend != null && !extend.isEmpty()) {
                for (String value : extend.values()) {
                    if (!TextUtils.isEmpty(value)) {
                        filterStr.append(value);
                    }
                }
            }

            String url = SITE_URL + "/index.php/vod/show/id/" + tid + filterStr.toString() + "/page/" + page + ".html";
            String content = requestWithVerify(url);

            if (!TextUtils.isEmpty(content)) {
                Document doc = Jsoup.parse(content);
                Elements items = doc.select(".myui-vodlist__box, ul.myui-vodlist li");

                for (Element item : items) {
                    Element a = item.select("a").first();
                    if (a == null) continue;

                    String title = a.attr("title");
                    if (TextUtils.isEmpty(title)) {
                        title = a.text();
                    }

                    String pic = item.select(".lazyload").attr("data-original");
                    String href = a.attr("href");
                    String remarks = item.select(".tag").text();
                    if (TextUtils.isEmpty(remarks)) {
                        remarks = item.select(".pic-text").text();
                    }

                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(href)) {
                        String id = getIdFromHref(href);
                        pic = fixUrl(pic);

                        boolean exists = false;
                        for (Vod v : list) {
                            if (v.getVodId().equals(id)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            list.add(new Vod(id, title, pic, remarks));
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.get().page(page, pageCount, list.size(), total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.error("无内容");
        }

        String did = ids.get(0);
        Vod vod = new Vod();
        vod.setVodId(did);

        try {
            String url = SITE_URL + "/index.php/vod/detail/id/" + did + ".html";
            String content = requestWithVerify(url);

            if (!TextUtils.isEmpty(content)) {
                Document doc = Jsoup.parse(content);

                String name = doc.select("h1").first().text();
                if (TextUtils.isEmpty(name)) {
                    Element dataA = doc.select(".data a").first();
                    if (dataA != null) {
                        name = dataA.text();
                    }
                }
                vod.setVodName(name);

                String pic = doc.select(".lazyload").attr("data-original");
                pic = fixUrl(pic);
                vod.setVodPic(pic);

                Elements dataItems = doc.select(".data");
                if (dataItems.size() > 0) {
                    Element firstData = dataItems.first();
                    Elements links = firstData.select("a");
                    if (links.size() > 2) {
                        vod.setVodYear(links.get(2).text());
                    }
                    if (links.size() > 1) {
                        vod.setVodArea(links.get(1).text());
                    }
                    if (links.size() > 0) {
                        vod.setVodActor(links.get(0).text());
                    }
                }

                Element contentElem = doc.select(".text-collapse span").first();
                if (contentElem != null) {
                    vod.setVodContent(contentElem.text());
                }

                List<String> playFrom = new ArrayList<>();
                List<String> playUrls = new ArrayList<>();

                Elements tabs = doc.select(".myui-panel__head h3");
                for (Element tab : tabs) {
                    String tabText = tab.text().trim();
                    if (!TextUtils.isEmpty(tabText)) {
                        String lowerText = tabText.toLowerCase();
                        if (lowerText.contains("播放") || lowerText.contains("线路") ||
                            lowerText.contains("云播") || lowerText.contains("在线") ||
                            lowerText.contains("资源") || lowerText.contains("高清") ||
                            lowerText.contains("极速") || lowerText.contains("备用") ||
                            lowerText.contains("专享") || lowerText.contains("秒播")) {
                            playFrom.add(tabText);
                        }
                    }
                }

                Elements allLists = doc.select(".myui-content__list");
                for (int i = 0; i < playFrom.size(); i++) {
                    StringBuilder urls = new StringBuilder();
                    if (i < allLists.size()) {
                        Elements listItems = allLists.get(i).select("li");
                        for (Element item : listItems) {
                            Element a = item.select("a").first();
                            if (a == null) continue;

                            String href = a.attr("href");
                            href = fixUrl(href);
                            String title = a.text().trim();

                            if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(href)) {
                                urls.append(title).append("$").append(href).append("#");
                            }
                        }
                    }
                    if (urls.length() > 0 && urls.charAt(urls.length() - 1) == '#') {
                        urls.setLength(urls.length() - 1);
                    }
                    playUrls.add(urls.toString());
                }

                if (playFrom.isEmpty() || playUrls.isEmpty() || TextUtils.isEmpty(playUrls.get(0))) {
                    StringBuilder urls = new StringBuilder();
                    Element firstList = doc.select(".myui-content__list").first();
                    if (firstList != null) {
                        Elements listItems = firstList.select("li");
                        for (Element item : listItems) {
                            Element a = item.select("a").first();
                            if (a == null) continue;

                            String href = a.attr("href");
                            href = fixUrl(href);
                            String title = a.text().trim();

                            if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(href)) {
                                urls.append(title).append("$").append(href).append("#");
                            }
                        }
                    }
                    if (urls.length() > 0) {
                        if (urls.charAt(urls.length() - 1) == '#') {
                            urls.setLength(urls.length() - 1);
                        }
                        playFrom.add("播放源");
                        playUrls.add(urls.toString());
                    }
                }

                while (playUrls.size() < playFrom.size()) {
                    playUrls.add("");
                }
                if (playFrom.size() < playUrls.size()) {
                    playUrls = playUrls.subList(0, playFrom.size());
                }

                List<String> playFromClean = new ArrayList<>();
                List<String> playUrlsClean = new ArrayList<>();
                for (int i = 0; i < playFrom.size(); i++) {
                    if (!TextUtils.isEmpty(playFrom.get(i)) && !TextUtils.isEmpty(playUrls.get(i))) {
                        playFromClean.add(playFrom.get(i));
                        playUrlsClean.add(playUrls.get(i));
                    }
                }

                if (!playFromClean.isEmpty() && !playUrlsClean.isEmpty()) {
                    vod.setVodPlayFrom(TextUtils.join("$$$", playFromClean));
                    vod.setVodPlayUrl(TextUtils.join("$$$", playUrlsClean));
                } else {
                    vod.setVodPlayFrom("TVBYB");
                    vod.setVodPlayUrl("播放$" + url);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        List<Vod> list = new ArrayList<>();
        int pageCount = 1;
        int total = 0;

        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String url = SITE_URL + "/index.php/vod/search.html?wd=" + encodedKey + "&submit=";
            String content = requestWithVerify(url);

            if (!TextUtils.isEmpty(content)) {
                Document doc = Jsoup.parse(content);
                Elements items = doc.select("ul.myui-vodlist__media li, ul.myui-vodlist li");

                for (Element item : items) {
                    Element a = item.select("a").first();
                    if (a == null) continue;

                    String title = a.attr("title");
                    if (TextUtils.isEmpty(title)) {
                        title = a.text();
                    }

                    String pic = item.select(".lazyload").attr("data-original");
                    String href = a.attr("href");
                    String remarks = item.select(".tag").text();
                    if (TextUtils.isEmpty(remarks)) {
                        remarks = item.select(".pic-text").text();
                    }

                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(href)) {
                        String id = getIdFromHref(href);
                        pic = fixUrl(pic);

                        if (quick && !title.toLowerCase().contains(key.toLowerCase())) {
                            continue;
                        }

                        boolean exists = false;
                        for (Vod v : list) {
                            if (v.getVodId().equals(id)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            list.add(new Vod(id, title, pic, remarks));
                        }
                    }
                }
            }

            total = list.size();
            pageCount = total > 0 ? (total + 20 - 1) / 20 : 1;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.get().page(page, pageCount, 20, total).vod(list).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.error("播放地址错误");
        }
        return Result.get().parse(1).url(id).header(headers).string();
    }

    private List<Filter> getFilterData(String tid) {
        List<Filter> filters = new ArrayList<>();

        if ("1".equals(tid)) {
            List<Filter.Value> cateValues = new ArrayList<>();
            cateValues.add(new Filter.Value("全部", "1"));
            cateValues.add(new Filter.Value("动作片", "6"));
            cateValues.add(new Filter.Value("喜剧片", "7"));
            cateValues.add(new Filter.Value("爱情片", "8"));
            cateValues.add(new Filter.Value("科幻片", "9"));
            cateValues.add(new Filter.Value("恐怖片", "10"));
            cateValues.add(new Filter.Value("剧情片", "11"));
            cateValues.add(new Filter.Value("战争片", "12"));
            filters.add(new Filter("cateId", "类型", cateValues));

            List<Filter.Value> classValues = new ArrayList<>();
            classValues.add(new Filter.Value("全部", ""));
            classValues.add(new Filter.Value("喜剧", "/class/喜剧"));
            classValues.add(new Filter.Value("爱情", "/class/爱情"));
            classValues.add(new Filter.Value("恐怖", "/class/恐怖"));
            classValues.add(new Filter.Value("动作", "/class/动作"));
            classValues.add(new Filter.Value("科幻", "/class/科幻"));
            classValues.add(new Filter.Value("剧情", "/class/剧情"));
            classValues.add(new Filter.Value("战争", "/class/战争"));
            classValues.add(new Filter.Value("警匪", "/class/警匪"));
            classValues.add(new Filter.Value("犯罪", "/class/犯罪"));
            classValues.add(new Filter.Value("动画", "/class/动画"));
            classValues.add(new Filter.Value("奇幻", "/class/奇幻"));
            classValues.add(new Filter.Value("武侠", "/class/武侠"));
            classValues.add(new Filter.Value("冒险", "/class/冒险"));
            classValues.add(new Filter.Value("枪战", "/class/枪战"));
            classValues.add(new Filter.Value("悬疑", "/class/悬疑"));
            classValues.add(new Filter.Value("惊悚", "/class/惊悚"));
            classValues.add(new Filter.Value("经典", "/class/经典"));
            classValues.add(new Filter.Value("青春", "/class/青春"));
            classValues.add(new Filter.Value("文艺", "/class/文艺"));
            classValues.add(new Filter.Value("微电影", "/class/微电影"));
            classValues.add(new Filter.Value("古装", "/class/古装"));
            classValues.add(new Filter.Value("历史", "/class/历史"));
            classValues.add(new Filter.Value("运动", "/class/运动"));
            classValues.add(new Filter.Value("农村", "/class/农村"));
            classValues.add(new Filter.Value("儿童", "/class/儿童"));
            classValues.add(new Filter.Value("网络电影", "/class/网络电影"));
            filters.add(new Filter("class", "剧情", classValues));

            List<Filter.Value> areaValues = new ArrayList<>();
            areaValues.add(new Filter.Value("全部", ""));
            areaValues.add(new Filter.Value("大陆", "/area/大陆"));
            areaValues.add(new Filter.Value("香港", "/area/香港"));
            areaValues.add(new Filter.Value("台湾", "/area/台湾"));
            areaValues.add(new Filter.Value("美国", "/area/美国"));
            areaValues.add(new Filter.Value("法国", "/area/法国"));
            areaValues.add(new Filter.Value("英国", "/area/英国"));
            areaValues.add(new Filter.Value("日本", "/area/日本"));
            areaValues.add(new Filter.Value("韩国", "/area/韩国"));
            areaValues.add(new Filter.Value("德国", "/area/Germany"));
            areaValues.add(new Filter.Value("泰国", "/area/Thailand"));
            areaValues.add(new Filter.Value("India", "/area/India"));
            areaValues.add(new Filter.Value("Italy", "/area/Italy"));
            areaValues.add(new Filter.Value("Spain", "/area/Spain"));
            areaValues.add(new Filter.Value("Canada", "/area/Canada"));
            areaValues.add(new Filter.Value("Other", "/area/Other"));
            filters.add(new Filter("area", "地区", areaValues));

            List<Filter.Value> langValues = new ArrayList<>();
            langValues.add(new Filter.Value("全部", ""));
            langValues.add(new Filter.Value("国语", "/lang/国语"));
            langValues.add(new Filter.Value("英语", "/lang/英语"));
            langValues.add(new Filter.Value("粤语", "/lang/粤语"));
            langValues.add(new Filter.Value("韩语", "/lang/韩语"));
            langValues.add(new Filter.Value("日语", "/lang/日语"));
            langValues.add(new Filter.Value("法语", "/lang/法语"));
            langValues.add(new Filter.Value("德语", "/lang/德语"));
            langValues.add(new Filter.Value("其它", "/lang/其它"));
            filters.add(new Filter("lang", "语言", langValues));

            List<Filter.Value> yearValues = new ArrayList<>();
            yearValues.add(new Filter.Value("全部", ""));
            yearValues.add(new Filter.Value("2026", "/year/2026"));
            yearValues.add(new Filter.Value("2025", "/year/2025"));
            yearValues.add(new Filter.Value("2024", "/year/2024"));
            yearValues.add(new Filter.Value("2023", "/year/2023"));
            yearValues.add(new Filter.Value("2022", "/year/2022"));
            yearValues.add(new Filter.Value("2021", "/year/2021"));
            yearValues.add(new Filter.Value("2020", "/year/2020"));
            yearValues.add(new Filter.Value("2019", "/year/2019"));
            yearValues.add(new Filter.Value("2018", "/year/2018"));
            yearValues.add(new Filter.Value("2017", "/year/2017"));
            yearValues.add(new Filter.Value("2016", "/year/2016"));
            yearValues.add(new Filter.Value("2015", "/year/2015"));
            yearValues.add(new Filter.Value("2014", "/year/2014"));
            yearValues.add(new Filter.Value("2013", "/year/2013"));
            yearValues.add(new Filter.Value("2012", "/year/2012"));
            yearValues.add(new Filter.Value("2011", "/year/2011"));
            yearValues.add(new Filter.Value("2010", "/year/2010"));
            yearValues.add(new Filter.Value("2009", "/year/2009"));
            yearValues.add(new Filter.Value("2008", "/year/2008"));
            yearValues.add(new Filter.Value("2007", "/year/2007"));
            yearValues.add(new Filter.Value("2006", "/year/2006"));
            yearValues.add(new Filter.Value("2005", "/year/2005"));
            yearValues.add(new Filter.Value("2004", "/year/2004"));
            filters.add(new Filter("year", "年份", yearValues));

            List<Filter.Value> byValues = new ArrayList<>();
            byValues.add(new Filter.Value("全部", ""));
            byValues.add(new Filter.Value("时间", "/by/time"));
            byValues.add(new Filter.Value("人气", "/by/hits"));
            byValues.add(new Filter.Value("评分", "/by/score"));
            filters.add(new Filter("by", "排序", byValues));
        } else if ("2".equals(tid)) {
            List<Filter.Value> cateValues = new ArrayList<>();
            cateValues.add(new Filter.Value("全部", "2"));
            cateValues.add(new Filter.Value("国产剧", "13"));
            cateValues.add(new Filter.Value("港台剧", "14"));
            cateValues.add(new Filter.Value("日韩剧", "15"));
            cateValues.add(new Filter.Value("欧美剧", "16"));
            filters.add(new Filter("cateId", "类型", cateValues));

            List<Filter.Value> classValues = new ArrayList<>();
            classValues.add(new Filter.Value("全部", ""));
            classValues.add(new Filter.Value("古装", "/class/古装"));
            classValues.add(new Filter.Value("青春", "/class/青春"));
            classValues.add(new Filter.Value("偶像", "/class/偶像"));
            classValues.add(new Filter.Value("喜剧", "/class/喜剧"));
            classValues.add(new Filter.Value("家庭", "/class/家庭"));
            classValues.add(new Filter.Value("犯罪", "/class/犯罪"));
            classValues.add(new Filter.Value("动作", "/class/动作"));
            classValues.add(new Filter.Value("奇幻", "/class/奇幻"));
            classValues.add(new Filter.Value("剧情", "/class/剧情"));
            classValues.add(new Filter.Value("历史", "/class/历史"));
            classValues.add(new Filter.Value("经典", "/class/经典"));
            classValues.add(new Filter.Value("乡村", "/class/乡村"));
            classValues.add(new Filter.Value("情景", "/class/情景"));
            classValues.add(new Filter.Value("商战", "/class/商战"));
            classValues.add(new Filter.Value("网剧", "/class/网剧"));
            classValues.add(new Filter.Value("其他", "/class/其他"));
            filters.add(new Filter("class", "剧情", classValues));

            List<Filter.Value> areaValues = new ArrayList<>();
            areaValues.add(new Filter.Value("全部", ""));
            areaValues.add(new Filter.Value("内地", "/area/内地"));
            areaValues.add(new Filter.Value("韩国", "/area/韩国"));
            areaValues.add(new Filter.Value("香港", "/area/香港"));
            areaValues.add(new Filter.Value("台湾", "/area/台湾"));
            areaValues.add(new Filter.Value("日本", "/area/日本"));
            areaValues.add(new Filter.Value("美国", "/area/美国"));
            areaValues.add(new Filter.Value("泰国", "/area/泰国"));
            areaValues.add(new Filter.Value("英国", "/area/英国"));
            areaValues.add(new Filter.Value("Singapore", "/area/Singapore"));
            areaValues.add(new Filter.Value("Other", "/area/Other"));
            filters.add(new Filter("area", "地区", areaValues));

            List<Filter.Value> yearValues = new ArrayList<>();
            yearValues.add(new Filter.Value("全部", ""));
            yearValues.add(new Filter.Value("2026", "/year/2026"));
            yearValues.add(new Filter.Value("2025", "/year/2025"));
            yearValues.add(new Filter.Value("2024", "/year/2024"));
            yearValues.add(new Filter.Value("2023", "/year/2023"));
            yearValues.add(new Filter.Value("2022", "/year/2022"));
            yearValues.add(new Filter.Value("2021", "/year/2021"));
            yearValues.add(new Filter.Value("2020", "/year/2020"));
            yearValues.add(new Filter.Value("2019", "/year/2019"));
            yearValues.add(new Filter.Value("2018", "/year/2018"));
            yearValues.add(new Filter.Value("2017", "/year/2017"));
            yearValues.add(new Filter.Value("2016", "/year/2016"));
            yearValues.add(new Filter.Value("2015", "/year/2015"));
            yearValues.add(new Filter.Value("2014", "/year/2014"));
            yearValues.add(new Filter.Value("2013", "/year/2013"));
            yearValues.add(new Filter.Value("2012", "/year/2012"));
            yearValues.add(new Filter.Value("2011", "/year/2011"));
            yearValues.add(new Filter.Value("2010", "/year/2010"));
            yearValues.add(new Filter.Value("2009", "/year/2009"));
            yearValues.add(new Filter.Value("2008", "/year/2008"));
            yearValues.add(new Filter.Value("2007", "/year/2007"));
            yearValues.add(new Filter.Value("2006", "/year/2006"));
            yearValues.add(new Filter.Value("2005", "/year/2005"));
            yearValues.add(new Filter.Value("2004", "/year/2004"));
            filters.add(new Filter("year", "年份", yearValues));

            List<Filter.Value> langValues = new ArrayList<>();
            langValues.add(new Filter.Value("全部", ""));
            langValues.add(new Filter.Value("国语", "/lang/国语"));
            langValues.add(new Filter.Value("英语", "/lang/英语"));
            langValues.add(new Filter.Value("粤语", "/lang/粤语"));
            langValues.add(new Filter.Value("韩语", "/lang/韩语"));
            langValues.add(new Filter.Value("日语", "/lang/日语"));
            langValues.add(new Filter.Value("其它", "/lang/其它"));
            filters.add(new Filter("lang", "语言", langValues));

            List<Filter.Value> byValues = new ArrayList<>();
            byValues.add(new Filter.Value("全部", ""));
            byValues.add(new Filter.Value("时间", "/by/time"));
            byValues.add(new Filter.Value("人气", "/by/hits"));
            byValues.add(new Filter.Value("评分", "/by/score"));
            filters.add(new Filter("by", "排序", byValues));
        } else if ("3".equals(tid)) {
            List<Filter.Value> classValues = new ArrayList<>();
            classValues.add(new Filter.Value("全部", ""));
            classValues.add(new Filter.Value("选秀", "/class/选秀"));
            classValues.add(new Filter.Value("情感", "/class/情感"));
            classValues.add(new Filter.Value("访谈", "/class/访谈"));
            classValues.add(new Filter.Value("播报", "/class/播报"));
            classValues.add(new Filter.Value("旅游", "/class/旅游"));
            classValues.add(new Filter.Value("音乐", "/class/音乐"));
            classValues.add(new Filter.Value("美食", "/class/美食"));
            classValues.add(new Filter.Value("纪实", "/class/纪实"));
            classValues.add(new Filter.Value("曲艺", "/class/曲艺"));
            classValues.add(new Filter.Value("生活", "/class/生活"));
            classValues.add(new Filter.Value("游戏互动", "/class/游戏互动"));
            classValues.add(new Filter.Value("财经", "/class/财经"));
            classValues.add(new Filter.Value("求职", "/class/求职"));
            filters.add(new Filter("class", "剧情", classValues));

            List<Filter.Value> areaValues = new ArrayList<>();
            areaValues.add(new Filter.Value("全部", ""));
            areaValues.add(new Filter.Value("内地", "/area/内地"));
            areaValues.add(new Filter.Value("港台", "/area/港台"));
            areaValues.add(new Filter.Value("日韩", "/area/日韩"));
            areaValues.add(new Filter.Value("欧美", "/area/欧美"));
            filters.add(new Filter("area", "地区", areaValues));

            List<Filter.Value> langValues = new ArrayList<>();
            langValues.add(new Filter.Value("全部", ""));
            langValues.add(new Filter.Value("国语", "/lang/国语"));
            langValues.add(new Filter.Value("英语", "/lang/英语"));
            langValues.add(new Filter.Value("粤语", "/lang/粤语"));
            langValues.add(new Filter.Value("韩语", "/lang/韩语"));
            langValues.add(new Filter.Value("日语", "/lang/日语"));
            langValues.add(new Filter.Value("其它", "/lang/其它"));
            filters.add(new Filter("lang", "语言", langValues));

            List<Filter.Value> yearValues = new ArrayList<>();
            yearValues.add(new Filter.Value("全部", ""));
            yearValues.add(new Filter.Value("2026", "/year/2026"));
            yearValues.add(new Filter.Value("2025", "/year/2025"));
            yearValues.add(new Filter.Value("2024", "/year/2024"));
            yearValues.add(new Filter.Value("2023", "/year/2023"));
            yearValues.add(new Filter.Value("2022", "/year/2022"));
            yearValues.add(new Filter.Value("2021", "/year/2021"));
            yearValues.add(new Filter.Value("2020", "/year/2020"));
            yearValues.add(new Filter.Value("2019", "/year/2019"));
            yearValues.add(new Filter.Value("2018", "/year/2018"));
            yearValues.add(new Filter.Value("2017", "/year/2017"));
            yearValues.add(new Filter.Value("2016", "/year/2016"));
            yearValues.add(new Filter.Value("2015", "/year/2015"));
            yearValues.add(new Filter.Value("2014", "/year/2014"));
            yearValues.add(new Filter.Value("2013", "/year/2013"));
            yearValues.add(new Filter.Value("2012", "/year/2012"));
            yearValues.add(new Filter.Value("2011", "/year/2011"));
            yearValues.add(new Filter.Value("2010", "/year/2010"));
            yearValues.add(new Filter.Value("2009", "/year/2009"));
            yearValues.add(new Filter.Value("2008", "/year/2008"));
            yearValues.add(new Filter.Value("2007", "/year/2007"));
            yearValues.add(new Filter.Value("2006", "/year/2006"));
            yearValues.add(new Filter.Value("2005", "/year/2005"));
            yearValues.add(new Filter.Value("2004", "/year/2004"));
            filters.add(new Filter("year", "年份", yearValues));

            List<Filter.Value> byValues = new ArrayList<>();
            byValues.add(new Filter.Value("全部", ""));
            byValues.add(new Filter.Value("时间", "/by/time"));
            byValues.add(new Filter.Value("人气", "/by/hits"));
            byValues.add(new Filter.Value("评分", "/by/score"));
            filters.add(new Filter("by", "排序", byValues));
        } else if ("4".equals(tid)) {
            List<Filter.Value> classValues = new ArrayList<>();
            classValues.add(new Filter.Value("全部", ""));
            classValues.add(new Filter.Value("情感", "/class/情感"));
            classValues.add(new Filter.Value("科幻", "/class/科幻"));
            classValues.add(new Filter.Value("热血", "/class/热血"));
            classValues.add(new Filter.Value("推理", "/class/推理"));
            classValues.add(new Filter.Value("搞笑", "/class/搞笑"));
            classValues.add(new Filter.Value("冒险", "/class/冒险"));
            classValues.add(new Filter.Value("萝莉", "/class/萝莉"));
            classValues.add(new Filter.Value("校园", "/class/校园"));
            classValues.add(new Filter.Value("动作", "/class/动作"));
            classValues.add(new Filter.Value("机战", "/class/机战"));
            classValues.add(new Filter.Value("运动", "/class/运动"));
            classValues.add(new Filter.Value("战争", "/class/战争"));
            classValues.add(new Filter.Value("少年", "/class/少年"));
            classValues.add(new Filter.Value("少女", "/class/少女"));
            classValues.add(new Filter.Value("社会", "/class/社会"));
            classValues.add(new Filter.Value("原创", "/class/原创"));
            classValues.add(new Filter.Value("亲子", "/class/亲子"));
            classValues.add(new Filter.Value("益智", "/class/益智"));
            classValues.add(new Filter.Value("励志", "/class/励志"));
            classValues.add(new Filter.Value("其它", "/class/其他"));
            filters.add(new Filter("class", "剧情", classValues));

            List<Filter.Value> areaValues = new ArrayList<>();
            areaValues.add(new Filter.Value("全部", ""));
            areaValues.add(new Filter.Value("国产", "/area/国产"));
            areaValues.add(new Filter.Value("日本", "/area/日本"));
            areaValues.add(new Filter.Value("欧美", "/area/欧美"));
            areaValues.add(new Filter.Value("Other", "/area/Other"));
            filters.add(new Filter("area", "地区", areaValues));

            List<Filter.Value> langValues = new ArrayList<>();
            langValues.add(new Filter.Value("全部", ""));
            langValues.add(new Filter.Value("国语", "/lang/国语"));
            langValues.add(new Filter.Value("英语", "/lang/英语"));
            langValues.add(new Filter.Value("粤语", "/lang/粤语"));
            langValues.add(new Filter.Value("韩语", "/lang/韩语"));
            langValues.add(new Filter.Value("日语", "/lang/日语"));
            langValues.add(new Filter.Value("其它", "/lang/其它"));
            filters.add(new Filter("lang", "语言", langValues));

            List<Filter.Value> yearValues = new ArrayList<>();
            yearValues.add(new Filter.Value("全部", ""));
            yearValues.add(new Filter.Value("2026", "/year/2026"));
            yearValues.add(new Filter.Value("2025", "/year/2025"));
            yearValues.add(new Filter.Value("2024", "/year/2024"));
            yearValues.add(new Filter.Value("2023", "/year/2023"));
            yearValues.add(new Filter.Value("2022", "/year/2022"));
            yearValues.add(new Filter.Value("2021", "/year/2021"));
            yearValues.add(new Filter.Value("2020", "/year/2020"));
            yearValues.add(new Filter.Value("2019", "/year/2019"));
            yearValues.add(new Filter.Value("2018", "/year/2018"));
            yearValues.add(new Filter.Value("2017", "/year/2017"));
            yearValues.add(new Filter.Value("2016", "/year/2016"));
            yearValues.add(new Filter.Value("2015", "/year/2015"));
            yearValues.add(new Filter.Value("2014", "/year/2014"));
            yearValues.add(new Filter.Value("2013", "/year/2013"));
            yearValues.add(new Filter.Value("2012", "/year/2012"));
            yearValues.add(new Filter.Value("2011", "/year/2011"));
            yearValues.add(new Filter.Value("2010", "/year/2010"));
            yearValues.add(new Filter.Value("2009", "/year/2009"));
            yearValues.add(new Filter.Value("2008", "/year/2008"));
            yearValues.add(new Filter.Value("2007", "/year/2007"));
            yearValues.add(new Filter.Value("2006", "/year/2006"));
            yearValues.add(new Filter.Value("2005", "/year/2005"));
            yearValues.add(new Filter.Value("2004", "/year/2004"));
            filters.add(new Filter("year", "年份", yearValues));

            List<Filter.Value> byValues = new ArrayList<>();
            byValues.add(new Filter.Value("全部", ""));
            byValues.add(new Filter.Value("时间", "/by/time"));
            byValues.add(new Filter.Value("人气", "/by/hits"));
            byValues.add(new Filter.Value("评分", "/by/score"));
            filters.add(new Filter("by", "排序", byValues));
        }

        return filters;
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    private String getIdFromHref(String href) {
        Matcher matcher = ID_PATTERN.matcher(href);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = ID_PATTERN2.matcher(href);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return href;
    }

    // ==================== 滑块验证功能 ====================

    /**
     * 加载验证缓存
     */
    private void loadVerifyCache() {
        if (!TextUtils.isEmpty(verifyCookie) && System.currentTimeMillis() - verifiedAt < VERIFY_TTL_MS) {
            return;
        }
        if (sharedPreferences == null) return;
        try {
            String cached = sharedPreferences.getString(VERIFY_CACHE_KEY, "");
            if (TextUtils.isEmpty(cached)) return;
            String[] parts = cached.split("\\|");
            if (parts.length == 2) {
                String cookie = parts[0];
                long timestamp = Long.parseLong(parts[1]);
                if (!TextUtils.isEmpty(cookie) && System.currentTimeMillis() - timestamp < VERIFY_TTL_MS) {
                    verifyCookie = cookie;
                    verifiedAt = timestamp;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 保存验证缓存
     */
    private void saveVerifyCache() {
        verifiedAt = System.currentTimeMillis();
        if (sharedPreferences == null) return;
        try {
            sharedPreferences.edit()
                    .putString(VERIFY_CACHE_KEY, verifyCookie + "|" + verifiedAt)
                    .apply();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 清除验证缓存
     */
    private void clearVerifyCache() {
        verifyCookie = "";
        verifiedAt = 0;
        if (sharedPreferences == null) return;
        try {
            sharedPreferences.edit().remove(VERIFY_CACHE_KEY).apply();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 检测是否为验证页面
     */
    private boolean isVerifyPage(String html) {
        if (TextUtils.isEmpty(html)) return false;
        return html.contains("滑动验证") || html.contains("huadong_") || html.contains("yanzheng_huadong.php");
    }

    /**
     * MD5加密
     */
    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest((text == null ? "" : text).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 字符串转十六进制编码(每个字符ASCII码+1)
     */
    private String stringtoHex(String str) {
        if (TextUtils.isEmpty(str)) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            sb.append((int) str.charAt(i) + 1);
        }
        return sb.toString();
    }

    /**
     * 合并Cookie
     */
    private String mergeCookie(String oldCookie, String setCookie) {
        HashMap<String, String> jar = new HashMap<>();

        // 解析旧cookie
        if (!TextUtils.isEmpty(oldCookie)) {
            String[] cookies = oldCookie.split(";");
            for (String cookie : cookies) {
                String trimmed = cookie.trim();
                if (TextUtils.isEmpty(trimmed)) continue;
                int idx = trimmed.indexOf('=');
                if (idx > 0) {
                    jar.put(trimmed.substring(0, idx), trimmed.substring(idx + 1));
                }
            }
        }

        // 解析新cookie
        if (!TextUtils.isEmpty(setCookie)) {
            // 取第一个分号前的部分
            String first = setCookie.split(";")[0];
            int idx = first.indexOf('=');
            if (idx > 0) {
                jar.put(first.substring(0, idx), first.substring(idx + 1));
            }
        }

        // 重新组装
        StringBuilder sb = new StringBuilder();
        for (HashMap.Entry<String, String> entry : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 尝试使用外部验证服务
     */
    private boolean tryExternalVerify(String html) {
        if (TextUtils.isEmpty(ddddOcrApi)) return false;

        try {
            // 构建请求JSON
            JSONObject requestBody = new JSONObject();
            requestBody.put("url", SITE_URL);
            requestBody.put("html", html != null ? html : "");
            requestBody.put("cookie", verifyCookie);
            requestBody.put("type", "tvb_huadong");

            // 构建请求头（严格匹配JS：Content-Type + DEFAULT_HEADERS）
            HashMap<String, String> apiHeaders = new HashMap<>();
            apiHeaders.put("Content-Type", "application/json");
            apiHeaders.put("User-Agent", headers.get("User-Agent"));
            apiHeaders.put("Referer", SITE_URL + "/");

            // 发送POST请求到外部验证服务（严格匹配JS的timeout: 8000）
            String apiUrl = ddddOcrApi + "/verify";
            String response = OkHttp.post(apiUrl, requestBody.toString(), apiHeaders, 8000);

            if (TextUtils.isEmpty(response)) return false;

            // 严格匹配JS的响应处理逻辑
            // const data = typeof res.data === "object" ? res.data : {};
            JSONObject data;
            try {
                data = new JSONObject(response);
            } catch (Exception e) {
                data = new JSONObject();
            }

            // 严格匹配JS的cookie提取顺序：
            // const cookie = data.cookie || data.cookies || (data.data && data.data.cookie) || (data.data && data.data.cookies);
            String cookie = null;
            if (data.has("cookie") && !TextUtils.isEmpty(data.getString("cookie"))) {
                cookie = data.getString("cookie");
            } else if (data.has("cookies") && !TextUtils.isEmpty(data.getString("cookies"))) {
                cookie = data.getString("cookies");
            } else if (data.has("data")) {
                try {
                    JSONObject innerData = data.getJSONObject("data");
                    if (innerData.has("cookie") && !TextUtils.isEmpty(innerData.getString("cookie"))) {
                        cookie = innerData.getString("cookie");
                    } else if (innerData.has("cookies") && !TextUtils.isEmpty(innerData.getString("cookies"))) {
                        cookie = innerData.getString("cookies");
                    }
                } catch (Exception e) {
                    // data.data可能不是JSONObject，忽略
                }
            }

            if (!TextUtils.isEmpty(cookie)) {
                // 严格匹配JS的cookie合并逻辑：
                // VERIFY_STORE.cookie = mergeCookie(VERIFY_STORE.cookie, String(cookie).split(/;\s*/).map(x => x));
                String[] cookies = String.valueOf(cookie).split(";\\s*");
                for (String c : cookies) {
                    if (!TextUtils.isEmpty(c)) {
                        verifyCookie = mergeCookie(verifyCookie, c);
                    }
                }
                SpiderDebug.log("外部验证服务成功，已更新cookie");
                return true;
            }

        } catch (Exception e) {
            // 严格匹配JS: catch (_) {}
            SpiderDebug.log(e);
        }

        return false;
    }

    /**
     * 执行滑块验证
     */
    private boolean passSliderVerify(String html) {
        try {
            // 优先尝试外部验证服务
            if (tryExternalVerify(html)) {
                SpiderDebug.log("使用外部验证服务成功");
                return true;
            }

            // 提取huadong_*.js脚本路径
            Pattern scriptPattern = Pattern.compile("src=[\"']([^\"']*huadong_[^\"']+\\.js\\?id=\\d+)[\"']");
            Matcher scriptMatcher = scriptPattern.matcher(html);
            if (!scriptMatcher.find()) return false;

            String scriptPath = scriptMatcher.group(1);
            String scriptUrl = scriptPath.startsWith("http") ? scriptPath : SITE_URL + scriptPath;

            // 获取脚本内容(需要响应头)
            HashMap<String, String> jsHeaders = new HashMap<>(headers);
            if (!TextUtils.isEmpty(verifyCookie)) {
                jsHeaders.put("Cookie", verifyCookie);
            }
            jsHeaders.put("Referer", SITE_URL + "/");

            com.github.catvod.net.OkResult jsResult = new com.github.catvod.net.OkRequest(
                    com.github.catvod.net.OkHttp.GET, scriptUrl, null, jsHeaders
            ).execute(com.github.catvod.net.OkHttp.client());

            String jsContent = jsResult.getBody();
            if (TextUtils.isEmpty(jsContent)) return false;

            // 更新cookie(从响应头)
            String setCookie = jsResult.getSetCookie();
            if (!TextUtils.isEmpty(setCookie)) {
                verifyCookie = mergeCookie(verifyCookie, setCookie);
            }

            // 提取key、value、verifyPath
            Pattern keyPattern = Pattern.compile("key\\s*=\\s*[\"']([^\"']+)[\"']");
            Pattern valuePattern = Pattern.compile("value\\s*=\\s*[\"']([^\"']+)[\"']");
            Pattern verifyPathPattern = Pattern.compile("c\\.get\\([\"']([^\"']*yanzheng_huadong\\.php\\?[^\"']*)[\"']\\s*\\+");
            Pattern verifyPathPattern2 = Pattern.compile("([\\w_\\/.-]*yanzheng_huadong\\.php\\?type=[^\"']+)&key=");

            Matcher keyMatcher = keyPattern.matcher(jsContent);
            Matcher valueMatcher = valuePattern.matcher(jsContent);
            Matcher verifyPathMatcher = verifyPathPattern.matcher(jsContent);
            Matcher verifyPathMatcher2 = verifyPathPattern2.matcher(jsContent);

            if (!keyMatcher.find() || !valueMatcher.find()) return false;

            String key = keyMatcher.group(1);
            String value = valueMatcher.group(1);
            String verifyPath = null;

            if (verifyPathMatcher.find()) {
                verifyPath = verifyPathMatcher.group(1);
            } else if (verifyPathMatcher2.find()) {
                verifyPath = verifyPathMatcher2.group(1);
            }

            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value) || TextUtils.isEmpty(verifyPath)) {
                return false;
            }

            // 构建验证URL
            String verifyUrl;
            if (verifyPath.contains("&key=")) {
                verifyUrl = SITE_URL + verifyPath + URLEncoder.encode(key, "UTF-8") + "&value=" + md5(stringtoHex(value));
            } else {
                verifyUrl = SITE_URL + verifyPath + "&key=" + URLEncoder.encode(key, "UTF-8") + "&value=" + md5(stringtoHex(value));
            }

            // 执行验证(需要响应头)
            HashMap<String, String> verifyHeaders = new HashMap<>(headers);
            if (!TextUtils.isEmpty(verifyCookie)) {
                verifyHeaders.put("Cookie", verifyCookie);
            }
            verifyHeaders.put("Referer", SITE_URL + "/");

            com.github.catvod.net.OkResult verifyResult = new com.github.catvod.net.OkRequest(
                    com.github.catvod.net.OkHttp.GET, verifyUrl, null, verifyHeaders
            ).execute(com.github.catvod.net.OkHttp.client());

            // 验证成功后会返回新的cookie(通常在响应头中)
            String verifySetCookie = verifyResult.getSetCookie();
            if (!TextUtils.isEmpty(verifySetCookie)) {
                verifyCookie = mergeCookie(verifyCookie, verifySetCookie);
            }

            SpiderDebug.log("滑块验证完成: " + verifyUrl);
            return verifyResult.isSuccess();

        } catch (Exception e) {
            SpiderDebug.log(e);
            return false;
        }
    }

    /**
     * 带验证的请求
     */
    private String requestWithVerify(String url) {
        try {
            // 加载缓存
            loadVerifyCache();

            // 构建请求头
            HashMap<String, String> requestHeaders = new HashMap<>(headers);
            if (!TextUtils.isEmpty(verifyCookie)) {
                requestHeaders.put("Cookie", verifyCookie);
            }

            String content = OkHttp.string(url, requestHeaders);

            // 检测验证页面
            if (!TextUtils.isEmpty(content) && isVerifyPage(content)) {
                SpiderDebug.log("检测到验证页面,开始执行滑块验证");

                boolean verified = passSliderVerify(content);
                if (verified) {
                    saveVerifyCache();

                    // 重试请求
                    requestHeaders.put("Cookie", verifyCookie);
                    content = OkHttp.string(url, requestHeaders);

                    // 再次检测
                    if (isVerifyPage(content)) {
                        clearVerifyCache();
                    }
                } else {
                    clearVerifyCache();
                }
            }

            return content;

        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}