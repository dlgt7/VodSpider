package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 央视电视台爬虫
 * Converted from 央视.py
 */
public class Cntv extends Spider {

    private static final Pattern GUID_PATTERN = Pattern.compile("var\\s+guid\\s*=\\s*\"(.+?)\";");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.]+)/");

    // 正则回退：剧集提取（对应 Python get_EpisodesList_re）
    private static final Pattern EPISODE_DRAMA = Pattern.compile("'title':\\s*'(?<title>.+?)',\\s*'brief':\\s*'.+?',\\s*'img':\\s*'.+?',\\s*'url':\\s*'(?<url>.+?)'", Pattern.DOTALL);
    private static final Pattern EPISODE_SPECIAL = Pattern.compile("class=\"tp1\"><a\\s*href=\"(?<url>https://.+?)\"\\s*target=\"_blank\"\\s*title=\"(?<title>.+?)\"></a></div>", Pattern.DOTALL);
    private static final Pattern EPISODE_CARTOON = Pattern.compile("'title':\\s*'(?<title>.+?)',\\s*'img':\\s*'.+?',\\s*'brief':\\s*'.+?',\\s*'url':\\s*'(?<url>.+?)'", Pattern.DOTALL);
    private static final Pattern EPISODE_COLUMN = Pattern.compile("href=\"(?<url>.+?)\" target=\"_blank\" alt=\"(?<title>.+?)\" title=\".+?\">", Pattern.DOTALL);

    private static Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36");
        headers.put("Host", "tv.cctv.com");
        headers.put("Referer", "https://tv.cctv.com/");
        return headers;
    }

    private static String getExtend(HashMap<String, String> extend, String key) {
        if (extend != null && extend.containsKey(key)) {
            String value = extend.get(key);
            if (value != null) return value;
        }
        return "";
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
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
     * 构建分类列表 URL（对应 Python categoryContent 中的 URL 拼接）
     */
    private static String buildUrl(String type, String page, HashMap<String, String> extend) {
        String area = getExtend(extend, "datadq-area");
        String sc = getExtend(extend, "datafl-sc");
        String year = getExtend(extend, "datanf-year");
        String letter = getExtend(extend, "dataszm-letter");
        String channel = getExtend(extend, "datapd-channel");
        String encodedType = encode(type);

        if ("动画片".equals(type)) {
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955899450127&area=" + area
                    + "&sc=" + sc + "&fc=" + encodedType + "&letter=" + letter
                    + "&p=" + page + "&n=24&serviceId=tvcctv&topv=1&t=json";
        } else if ("纪录片".equals(type)) {
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955924871139&fc=" + encodedType
                    + "&channel=" + channel + "&sc=" + sc + "&year=" + year + "&letter=" + letter
                    + "&p=" + page + "&n=24&serviceId=tvcctv&topv=1&t=json";
        } else if ("电视剧".equals(type)) {
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955853485115&area=" + area
                    + "&sc=" + sc + "&fc=" + encodedType + "&year=" + year + "&letter=" + letter
                    + "&p=" + page + "&n=24&serviceId=tvcctv&topv=1&t=json";
        } else if ("特别节目".equals(type)) {
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955953877151&channel=" + channel
                    + "&sc=" + sc + "&fc=" + encodedType + "&bigday=&letter=" + letter
                    + "&p=" + page + "&n=24&serviceId=tvcctv&topv=1&t=json";
        } else {
            // 节目大全
            String cid = getExtend(extend, "cid");
            String fc = getExtend(extend, "fc");
            String fl = getExtend(extend, "fl");
            return "https://api.cntv.cn/lanmu/columnSearch?&fl=" + fl + "&fc=" + fc + "&cid=" + cid
                    + "&p=" + page + "&n=20&serviceId=tvcctv&t=json&cb=ko";
        }
    }

    /**
     * 解析普通分类列表（对应 Python get_list，解析 data.list）
     */
    private static List<Vod> parseList(String response, String type) {
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject object = new JSONObject(response);
            JSONObject data = object.optJSONObject("data");
            if (data == null) return list;
            JSONArray array = data.optJSONArray("list");
            if (array == null) return list;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String url = item.optString("url");
                if (TextUtils.isEmpty(url)) continue;
                String title = item.optString("title");
                String image = item.optString("image");
                String id = item.optString("id");
                String year = item.optString("year");
                String actors = item.optString("actors");
                String brief = item.optString("brief");
                String vodId = type + "###" + title + "###" + url + "###" + image + "###" + id + "###" + year + "###" + actors + "###" + brief;
                list.add(new Vod(vodId, title, image, ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 解析节目大全列表（对应 Python get_list1，解析 response.docs）
     */
    private static List<Vod> parseColumnList(String response) {
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject object = new JSONObject(response);
            JSONObject resp = object.optJSONObject("response");
            if (resp == null) return list;
            JSONArray docs = resp.optJSONArray("docs");
            if (docs == null) return list;
            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.getJSONObject(i);
                JSONObject lastVideo = doc.optJSONObject("lastVIDE");
                String videoId = lastVideo != null ? lastVideo.optString("videoSharedCode") : "";
                String name = doc.optString("column_name");
                String website = doc.optString("column_website");
                String logo = doc.optString("column_logo");
                String playDate = doc.optString("column_playdate");
                String brief = doc.optString("column_brief");
                if (TextUtils.isEmpty(website)) continue;
                String vodId = "节目大全" + "###" + name + "###" + website + "###" + logo + "###" + videoId + "###" + playDate + "###" + "###" + brief;
                list.add(new Vod(vodId, name, logo, ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 解析搜索列表（对应 Python get_list_search，解析 list）
     */
    private static List<Vod> parseSearchList(String response) {
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject object = new JSONObject(response);
            JSONArray array = object.optJSONArray("list");
            if (array == null) return list;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String url = item.optString("urllink");
                if (TextUtils.isEmpty(url)) continue;
                String title = item.optString("title").replaceAll("<[^>]+>", "").replace("&nbsp;", " ");
                String img = item.optString("imglink");
                String id = item.optString("id");
                String brief = item.optString("channel");
                String year = item.optString("uploadtime");
                String vodId = "搜索" + "###" + title + "###" + url + "###" + img + "###" + id + "###" + year + "###" + "###" + brief;
                list.add(new Vod(vodId, title, img, year));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 从 JSON 列表提取剧集（对应 Python get_EpisodesList）
     */
    private static List<String> extractEpisodes(JSONArray list) {
        List<String> videos = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject item = list.getJSONObject(i);
                String guid = item.optString("guid");
                String title = item.optString("title");
                if (TextUtils.isEmpty(guid)) continue;
                videos.add(title + "$" + guid);
            } catch (Exception e) {
                // skip
            }
        }
        return videos;
    }

    /**
     * 正则回退提取剧集（对应 Python get_EpisodesList_re）
     */
    private static List<String> extractEpisodesRegex(String html, Pattern pattern) {
        List<String> videos = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String url = matcher.group("url");
            String title = matcher.group("title");
            if (TextUtils.isEmpty(url)) continue;
            videos.add(title + "$" + url);
        }
        return videos;
    }

    /**
     * 获取 m3u8 播放地址（对应 Python get_m3u8）
     */
    private static String getM3u8(String guid) {
        String apiUrl = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + guid;
        try {
            String response = OkHttp.string(apiUrl, getHeaders());
            JSONObject object = new JSONObject(response);
            String hlsUrl = object.optString("hls_url").trim();
            if (TextUtils.isEmpty(hlsUrl)) return "";
            String hlsContent = OkHttp.string(hlsUrl, getHeaders()).trim();
            String[] lines = hlsContent.split("\n");
            if (lines.length < 1) return "";
            Matcher matcher = DOMAIN_PATTERN.matcher(hlsUrl);
            if (!matcher.find()) return "";
            String urlPrefix = matcher.group(1);
            String lastLine = lines[lines.length - 1];
            String[] subUrl = lastLine.split("/");
            if (subUrl.length > 3) {
                subUrl[3] = "1200";
                subUrl[subUrl.length - 1] = "1200.m3u8";
                String hdUrl = urlPrefix + "/" + TextUtils.join("/", subUrl);
                // 测试 HD URL 是否可用（对应 Python TestWebPage）
                try {
                    okhttp3.Response hdResponse = OkHttp.newCall(hdUrl, getHeaders());
                    int code = hdResponse.code();
                    hdResponse.close();
                    if (code == 200) return hdUrl;
                } catch (Exception e) {
                    // HD URL 不可用
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 创建筛选器（对应 Python config.filter）
     */
    private static LinkedHashMap<String, List<Filter>> createFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 节目大全
        filters.put("节目大全", Arrays.asList(
                new Filter("cid", "频道", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("CCTV-1综合", "EPGC1386744804340101"),
                        new Filter.Value("CCTV-2财经", "EPGC1386744804340102"), new Filter.Value("CCTV-3综艺", "EPGC1386744804340103"),
                        new Filter.Value("CCTV-4中文国际", "EPGC1386744804340104"), new Filter.Value("CCTV-5体育", "EPGC1386744804340107"),
                        new Filter.Value("CCTV-6电影", "EPGC1386744804340108"), new Filter.Value("CCTV-7国防军事", "EPGC1386744804340109"),
                        new Filter.Value("CCTV-8电视剧", "EPGC1386744804340110"), new Filter.Value("CCTV-9纪录", "EPGC1386744804340112"),
                        new Filter.Value("CCTV-10科教", "EPGC1386744804340113"), new Filter.Value("CCTV-11戏曲", "EPGC1386744804340114"),
                        new Filter.Value("CCTV-12社会与法", "EPGC1386744804340115"), new Filter.Value("CCTV-13新闻", "EPGC1386744804340116"),
                        new Filter.Value("CCTV-14少儿", "EPGC1386744804340117"), new Filter.Value("CCTV-15音乐", "EPGC1386744804340118"),
                        new Filter.Value("CCTV-16奥林匹克", "EPGC1634630207058998"), new Filter.Value("CCTV-17农业农村", "EPGC1563932742616872"),
                        new Filter.Value("CCTV-5+体育赛事", "EPGC1468294755566101"))),
                new Filter("fc", "分类", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("新闻", "新闻"), new Filter.Value("体育", "体育"),
                        new Filter.Value("综艺", "综艺"), new Filter.Value("健康", "健康"), new Filter.Value("生活", "生活"),
                        new Filter.Value("科教", "科教"), new Filter.Value("经济", "经济"), new Filter.Value("农业", "农业"),
                        new Filter.Value("法治", "法治"), new Filter.Value("军事", "军事"), new Filter.Value("少儿", "少儿"),
                        new Filter.Value("动画", "动画"), new Filter.Value("纪实", "纪实"), new Filter.Value("戏曲", "戏曲"),
                        new Filter.Value("音乐", "音乐"), new Filter.Value("影视", "影视"))),
                new Filter("fl", "字母", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("A", "A"), new Filter.Value("B", "B"),
                        new Filter.Value("C", "C"), new Filter.Value("D", "D"), new Filter.Value("E", "E"),
                        new Filter.Value("F", "F"), new Filter.Value("G", "G"), new Filter.Value("H", "H"),
                        new Filter.Value("I", "I"), new Filter.Value("J", "J"), new Filter.Value("K", "K"),
                        new Filter.Value("L", "L"), new Filter.Value("M", "M"), new Filter.Value("N", "N"),
                        new Filter.Value("O", "O"), new Filter.Value("P", "P"), new Filter.Value("Q", "Q"),
                        new Filter.Value("R", "R"), new Filter.Value("S", "S"), new Filter.Value("T", "T"),
                        new Filter.Value("U", "U"), new Filter.Value("V", "V"), new Filter.Value("W", "W"),
                        new Filter.Value("X", "X"), new Filter.Value("Y", "Y"), new Filter.Value("Z", "Z"))),
                new Filter("year", "年份", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("2023", "2023"), new Filter.Value("2022", "2022"),
                        new Filter.Value("2021", "2021"), new Filter.Value("2020", "2020"), new Filter.Value("2019", "2019"),
                        new Filter.Value("2018", "2018"), new Filter.Value("2017", "2017"), new Filter.Value("2016", "2016"),
                        new Filter.Value("2015", "2015"), new Filter.Value("2014", "2014"), new Filter.Value("2013", "2013"),
                        new Filter.Value("2012", "2012"), new Filter.Value("2011", "2011"), new Filter.Value("2010", "2010"),
                        new Filter.Value("2009", "2009"), new Filter.Value("2008", "2008"), new Filter.Value("2007", "2007"),
                        new Filter.Value("2006", "2006"), new Filter.Value("2005", "2005"), new Filter.Value("2004", "2004"),
                        new Filter.Value("2003", "2003"), new Filter.Value("2002", "2002"), new Filter.Value("2001", "2001"),
                        new Filter.Value("2000", "2000"))),
                new Filter("month", "月份", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("12", "12"), new Filter.Value("11", "11"),
                        new Filter.Value("10", "10"), new Filter.Value("09", "09"), new Filter.Value("08", "08"),
                        new Filter.Value("07", "07"), new Filter.Value("06", "06"), new Filter.Value("05", "05"),
                        new Filter.Value("04", "04"), new Filter.Value("03", "03"), new Filter.Value("02", "02"),
                        new Filter.Value("01", "01")))
        ));

        // 电视剧
        filters.put("电视剧", Arrays.asList(
                new Filter("datafl-sc", "类型", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("谍战", "谍战"), new Filter.Value("悬疑", "悬疑"),
                        new Filter.Value("刑侦", "刑侦"), new Filter.Value("历史", "历史"), new Filter.Value("古装", "古装"),
                        new Filter.Value("武侠", "武侠"), new Filter.Value("军旅", "军旅"), new Filter.Value("战争", "战争"),
                        new Filter.Value("喜剧", "喜剧"), new Filter.Value("青春", "青春"), new Filter.Value("言情", "言情"),
                        new Filter.Value("偶像", "偶像"), new Filter.Value("家庭", "家庭"), new Filter.Value("年代", "年代"),
                        new Filter.Value("革命", "革命"), new Filter.Value("农村", "农村"), new Filter.Value("都市", "都市"),
                        new Filter.Value("其他", "其他"))),
                new Filter("datadq-area", "地区", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("中国大陆", "中国大陆"), new Filter.Value("中国香港", "香港"),
                        new Filter.Value("美国", "美国"), new Filter.Value("欧洲", "欧洲"), new Filter.Value("泰国", "泰国"))),
                new Filter("datanf-year", "年份", buildYearFilter(2023, 1997)),
                new Filter("dataszm-letter", "字母", buildLetterFilter())
        ));

        // 动画片
        filters.put("动画片", Arrays.asList(
                new Filter("datafl-sc", "类型", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("亲子", "亲子"), new Filter.Value("搞笑", "搞笑"),
                        new Filter.Value("冒险", "冒险"), new Filter.Value("动作", "动作"), new Filter.Value("宠物", "宠物"),
                        new Filter.Value("体育", "体育"), new Filter.Value("益智", "益智"), new Filter.Value("历史", "历史"),
                        new Filter.Value("教育", "教育"), new Filter.Value("校园", "校园"), new Filter.Value("言情", "言情"),
                        new Filter.Value("武侠", "武侠"), new Filter.Value("经典", "经典"), new Filter.Value("未来", "未来"),
                        new Filter.Value("古代", "古代"), new Filter.Value("神话", "神话"), new Filter.Value("真人", "真人"),
                        new Filter.Value("励志", "励志"), new Filter.Value("热血", "热血"), new Filter.Value("奇幻", "奇幻"),
                        new Filter.Value("童话", "童话"), new Filter.Value("剧情", "剧情"), new Filter.Value("夺宝", "夺宝"),
                        new Filter.Value("其他", "其他"))),
                new Filter("datadq-area", "地区", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("中国大陆", "中国大陆"), new Filter.Value("美国", "美国"),
                        new Filter.Value("欧洲", "欧洲"))),
                new Filter("dataszm-letter", "字母", buildLetterFilter())
        ));

        // 纪录片
        filters.put("纪录片", Arrays.asList(
                new Filter("datapd-channel", "频道", buildChannelFilter()),
                new Filter("datafl-sc", "类型", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("人文历史", "人文历史"), new Filter.Value("人物", "人物"),
                        new Filter.Value("军事", "军事"), new Filter.Value("探索", "探索"), new Filter.Value("社会", "社会"),
                        new Filter.Value("时政", "时政"), new Filter.Value("经济", "经济"), new Filter.Value("科技", "科技"))),
                new Filter("datanf-year", "年份", buildYearFilter(2023, 2008)),
                new Filter("dataszm-letter", "字母", buildLetterFilter())
        ));

        // 特别节目
        filters.put("特别节目", Arrays.asList(
                new Filter("datapd-channel", "频道", buildChannelFilter()),
                new Filter("datafl-sc", "类型", Arrays.asList(
                        new Filter.Value("全部", ""), new Filter.Value("全部", "全部"), new Filter.Value("新闻", "新闻"),
                        new Filter.Value("经济", "经济"), new Filter.Value("综艺", "综艺"), new Filter.Value("体育", "体育"),
                        new Filter.Value("军事", "军事"), new Filter.Value("影视", "影视"), new Filter.Value("科教", "科教"),
                        new Filter.Value("戏曲", "戏曲"), new Filter.Value("青少", "青少"), new Filter.Value("音乐", "音乐"),
                        new Filter.Value("社会", "社会"), new Filter.Value("公益", "公益"), new Filter.Value("其他", "其他"))),
                new Filter("dataszm-letter", "字母", buildLetterFilter())
        ));

        return filters;
    }

    private static List<Filter.Value> buildYearFilter(int from, int to) {
        List<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", ""));
        for (int y = from; y >= to; y--) {
            values.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
        }
        return values;
    }

    private static List<Filter.Value> buildLetterFilter() {
        List<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", ""));
        String[] letters = {"A", "C", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "0-9"};
        for (String letter : letters) {
            values.add(new Filter.Value(letter, letter));
        }
        return values;
    }

    private static List<Filter.Value> buildChannelFilter() {
        String[] channels = {
                "CCTV{1 综合", "CCTV{2 财经", "CCTV{3 综艺", "CCTV{4 中文国际", "CCTV{5 体育",
                "CCTV{6 电影", "CCTV{7 国防军事", "CCTV{8 电视剧", "CCTV{9 纪录", "CCTV{10 科教",
                "CCTV{11 戏曲", "CCTV{12 社会与法", "CCTV{13 新闻", "CCTV{14 少儿", "CCTV{15 音乐",
                "CCTV{17 农业农村"
        };
        List<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", ""));
        for (String ch : channels) {
            values.add(new Filter.Value(ch, ch));
        }
        return values;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        // 对照 Python homeContent: cateManual 映射
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("节目大全", "央视大全"));
        classes.add(new Class("电视剧", "电视剧"));
        classes.add(new Class("动画片", "动画片"));
        classes.add(new Class("纪录片", "纪录片"));
        classes.add(new Class("特别节目", "特别节目"));

        List<Vod> videos = new ArrayList<>();
        try {
            String url = buildUrl("电视剧", "1", new HashMap<>());
            String response = OkHttp.string(url, getHeaders());
            videos = parseList(response, "电视剧");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (filter) {
            return Result.string(classes, videos, createFilters());
        }
        return Result.string(classes, videos);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = parsePage(pg);
        String url = buildUrl(tid, String.valueOf(page), extend);
        List<Vod> videos = new ArrayList<>();
        int pagecount = "节目大全".equals(tid) ? 20 : 24;

        try {
            String response = OkHttp.string(url, getHeaders());
            if ("节目大全".equals(tid)) {
                // JSONP 回调去壳（对应 Python: htmlText[3:index]）
                int endIndex = response.lastIndexOf(");");
                if (endIndex > 0) {
                    response = response.substring(response.indexOf("(") + 1, endIndex);
                }
                videos = parseColumnList(response);
            } else {
                videos = parseList(response, tid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int pc = videos.size() >= pagecount ? 9999 : page;
        return Result.get().vod(videos).page(page, pc, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String[] parts = id.split("###", 8);
        String type = parts[0];
        String name = parts.length > 1 ? parts[1] : "央视";
        String lastVideo = parts.length > 2 ? parts[2] : "";
        String pic = parts.length > 3 ? parts[3] : "";
        String videoId = parts.length > 4 ? parts[4] : "";
        String year = parts.length > 5 ? parts[5] : "";
        String actor = parts.length > 6 ? parts[6] : "";
        String content = parts.length > 7 ? parts[7] : "";

        String fromId = "CCTV";
        List<String> playUrls = new ArrayList<>();

        try {
            if ("搜索".equals(type)) {
                // 对应 Python: if tid=="搜索"
                fromId = "中央台";
                playUrls.add(name + "$" + lastVideo);
            } else if ("节目大全".equals(type)) {
                // 对应 Python: if tid=="节目大全"
                String infoUrl = "https://api.cntv.cn/video/videoinfoByGuid?guid=" + videoId + "&serviceId=tvcctv";
                String infoResponse = OkHttp.string(infoUrl, getHeaders());
                JSONObject infoObj = new JSONObject(infoResponse);
                String ctid = infoObj.optString("ctid");
                if (!TextUtils.isEmpty(ctid)) {
                    String listUrl = "https://api.cntv.cn/NewVideo/getVideoListByColumn?id=" + ctid + "&d=&p=1&n=100&sort=desc&mode=0&serviceId=tvcctv&t=json";
                    String listResponse = OkHttp.string(listUrl, getHeaders());
                    JSONObject listObj = new JSONObject(listResponse);
                    JSONObject data = listObj.optJSONObject("data");
                    if (data != null) {
                        JSONArray list = data.optJSONArray("list");
                        if (list != null) {
                            playUrls = extractEpisodes(list);
                        }
                    }
                }
            } else {
                // 对应 Python: else (电视剧/纪录片/动画片/特别节目)
                String listUrl = "https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew?id=" + videoId + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
                String listResponse = OkHttp.string(listUrl, getHeaders());
                JSONObject listObj = new JSONObject(listResponse);
                JSONObject data = listObj.optJSONObject("data");
                if (data != null) {
                    JSONArray list = data.optJSONArray("list");
                    if (list != null) {
                        playUrls = extractEpisodes(list);
                    }
                }
                // 回退：正则提取剧集（对应 Python: if len(videoList)<1）
                if (playUrls.isEmpty() && !TextUtils.isEmpty(lastVideo)) {
                    String html = OkHttp.string(lastVideo, getHeaders());
                    Pattern pattern = null;
                    if ("电视剧".equals(type) || "纪录片".equals(type)) {
                        pattern = EPISODE_DRAMA;
                    } else if ("特别节目".equals(type)) {
                        pattern = EPISODE_SPECIAL;
                    } else if ("动画片".equals(type)) {
                        pattern = EPISODE_CARTOON;
                    } else if ("节目大全".equals(type)) {
                        pattern = EPISODE_COLUMN;
                    }
                    if (pattern != null) {
                        playUrls = extractEpisodesRegex(html, pattern);
                    }
                    fromId = "央视";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 对应 Python: if len(videoList) == 0: return {}
        if (playUrls.isEmpty()) {
            return Result.string(new ArrayList<>());
        }

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setTypeName(type);
        vod.setVodYear(year);
        vod.setVodActor(actor);
        vod.setVodContent(content);
        vod.setVodPlayFrom(fromId);
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 对应 Python playerContent
        String playUrl = "";
        int parse = 0;

        if ("CCTV".equals(flag)) {
            playUrl = getM3u8(id);
        } else {
            try {
                String html = OkHttp.string(id, getHeaders());
                Matcher matcher = GUID_PATTERN.matcher(html);
                if (matcher.find()) {
                    playUrl = getM3u8(matcher.group(1));
                } else {
                    playUrl = id;
                    parse = 1;
                }
            } catch (Exception e) {
                playUrl = id;
                parse = 1;
            }
        }

        // 对应 Python: if url.find('https:')<0: url=id; parse=1
        if (TextUtils.isEmpty(playUrl) || !playUrl.contains("https:")) {
            playUrl = id;
            parse = 1;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1");
        return Result.get().url(playUrl).header(headers).parse(parse).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        // 对应 Python searchContentPage
        List<Vod> list = new ArrayList<>();
        try {
            String encodedKey = encode(key);
            String url = "https://search.cctv.com/ifsearch.php?page=1&qtext=" + encodedKey
                    + "&sort=relevance&pageSize=20&type=video&vtime=-1&datepid=1&channel=&pageflag=0&qtext_str=" + encodedKey;
            String response = OkHttp.string(url, getHeaders());
            list = parseSearchList(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }
}
