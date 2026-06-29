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
 * 中央电视台 spider
 * Converted from 央视.py
 */
public class Cntv extends Spider {

    private static final String UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    private static final String UA_MOBILE = "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1";

    // var guid = "xxx";
    private static final Pattern GUID_PATTERN = Pattern.compile("var\\s+guid\\s*=\\s*\"(.+?)\";");
    // 提取域名前缀 http(s)://xxx/
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.]+)/");

    // 回退集数正则（API 返回空列表时使用），对照 Python get_EpisodesList_re
    private static final Pattern EPISODE_DRAMA = Pattern.compile(
            "'title':\\s*'(?<title>.+?)',\\n{0,1}\\s*'brief':\\s*'(.+?)',\\n{0,1}\\s*'img':\\s*'(.+?)',\\n{0,1}\\s*'url':\\s*'(?<url>.+?)'",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern EPISODE_SPECIAL = Pattern.compile(
            "class=\"tp1\"><a\\s*href=\"(?<url>https://.+?)\"\\s*target=\"_blank\"\\s*title=\"(?<title>.+?)\"></a></div>",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern EPISODE_CARTOON = Pattern.compile(
            "'title':\\s*'(?<title>.+?)',\\n{0,1}\\s*'img':\\s*'(.+?)',\\n{0,1}\\s*'brief':\\s*'(.+?)',\\n{0,1}\\s*'url':\\s*'(?<url>.+?)'",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern EPISODE_COLUMN = Pattern.compile(
            "href=\"(?<url>.+?)\" target=\"_blank\" alt=\"(?<title>.+?)\" title=\".+?\">",
            Pattern.MULTILINE | Pattern.DOTALL);

    private static Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA_PC);
        headers.put("Host", "tv.cctv.com");
        headers.put("Referer", "https://tv.cctv.com/");
        return headers;
    }

    private static Map<String, String> getPlayerHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA_MOBILE);
        return headers;
    }

    private static String getExtend(HashMap<String, String> extend, String key) {
        if (extend != null && extend.containsKey(key)) {
            String value = extend.get(key);
            if (value != null) return value;
        }
        return "";
    }

    private static String encode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
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

    private static List<Filter.Value> values(String... pairs) {
        List<Filter.Value> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new Filter.Value(pairs[i], pairs[i + 1]));
        }
        return list;
    }

    private static String removeHtml(String text) {
        return text.replaceAll("<[^>]+>", "").replace("&nbsp;", " ");
    }

    /**
     * 正则取单值，对照 Python get_RegexGetText
     */
    private static String getRegexText(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    /**
     * 构建分类 URL，参数顺序严格对照 Python categoryContent
     */
    private static String buildUrl(String tid, String pg, HashMap<String, String> extend) {
        String suffix = "&n=24&serviceId=tvcctv&topv=1&t=json";
        if ("动画片".equals(tid)) {
            String id = encode(tid);
            String area = encode(getExtend(extend, "datadq-area"));
            String letter = getExtend(extend, "dataszm-letter");
            String datafl = encode(getExtend(extend, "datafl-sc"));
            // Python: area={0}&sc={4}&fc={1}&letter={2}&p={3}
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955899450127&area=" + area
                    + "&sc=" + datafl + "&fc=" + id + "&letter=" + letter + "&p=" + pg + suffix;
        } else if ("纪录片".equals(tid)) {
            String id = encode(tid);
            String channel = encode(getExtend(extend, "datapd-channel"));
            String datafl = encode(getExtend(extend, "datafl-sc"));
            String year = getExtend(extend, "datanf-year");
            String letter = getExtend(extend, "dataszm-letter");
            // Python: fc={0}&channel={1}&sc={2}&year={3}&letter={4}&p={5}
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955924871139&fc=" + id
                    + "&channel=" + channel + "&sc=" + datafl + "&year=" + year + "&letter=" + letter + "&p=" + pg + suffix;
        } else if ("电视剧".equals(tid)) {
            String id = encode(tid);
            String datafl = encode(getExtend(extend, "datafl-sc"));
            String year = getExtend(extend, "datanf-year");
            String letter = getExtend(extend, "dataszm-letter");
            // Python 不读 datadq-area，area 始终为空
            // Python: area={0}&sc={1}&fc={2}&year={3}&letter={4}&p={5}
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955853485115&area="
                    + "&sc=" + datafl + "&fc=" + id + "&year=" + year + "&letter=" + letter + "&p=" + pg + suffix;
        } else if ("特别节目".equals(tid)) {
            String id = encode(tid);
            String channel = encode(getExtend(extend, "datapd-channel"));
            String datafl = encode(getExtend(extend, "datafl-sc"));
            String letter = getExtend(extend, "dataszm-letter");
            // Python: channel={0}&sc={1}&fc={2}&bigday=&letter={3}&p={4}
            return "https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955953877151&channel=" + channel
                    + "&sc=" + datafl + "&fc=" + id + "&bigday=&letter=" + letter + "&p=" + pg + suffix;
        } else {
            // 节目大全
            String cid = getExtend(extend, "cid");
            String fc = getExtend(extend, "fc");
            String fl = getExtend(extend, "fl");
            // Python: fl={0}&fc={1}&cid={2}&p={3}
            return "https://api.cntv.cn/lanmu/columnSearch?&fl=" + fl + "&fc=" + fc + "&cid=" + cid
                    + "&p=" + pg + "&n=20&serviceId=tvcctv&t=json&cb=ko";
        }
    }

    /**
     * 解析普通分类列表（data.list），对照 Python get_list
     */
    private static List<Vod> parseAlbumList(String html, String tid) {
        List<Vod> videos = new ArrayList<>();
        try {
            JSONObject data = new JSONObject(html).optJSONObject("data");
            if (data == null) return videos;
            JSONArray jsonList = data.optJSONArray("list");
            if (jsonList == null) return videos;
            for (int i = 0; i < jsonList.length(); i++) {
                JSONObject vod = jsonList.getJSONObject(i);
                String url = vod.optString("url");
                if (TextUtils.isEmpty(url)) continue;
                String title = vod.optString("title");
                String img = vod.optString("image");
                String id = vod.optString("id");
                String brief = vod.optString("brief");
                String year = vod.optString("year");
                String actors = vod.optString("actors");
                String guid = tid + "###" + title + "###" + url + "###" + img + "###" + id + "###" + year + "###" + actors + "###" + brief;
                videos.add(new Vod(guid, title, img, ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videos;
    }

    /**
     * 解析栏目大全列表（response.docs），对照 Python get_list1
     */
    private static List<Vod> parseColumnList(String html, String tid) {
        List<Vod> videos = new ArrayList<>();
        try {
            JSONObject response = new JSONObject(html).optJSONObject("response");
            if (response == null) return videos;
            JSONArray jsonList = response.optJSONArray("docs");
            if (jsonList == null) return videos;
            for (int i = 0; i < jsonList.length(); i++) {
                JSONObject vod = jsonList.getJSONObject(i);
                String url = vod.optString("column_website");
                if (TextUtils.isEmpty(url)) continue;
                String id = vod.optJSONObject("lastVIDE").optString("videoSharedCode");
                String title = vod.optString("column_name");
                String img = vod.optString("column_logo");
                String year = vod.optString("column_playdate");
                String brief = vod.optString("column_brief");
                String guid = tid + "###" + title + "###" + url + "###" + img + "###" + id + "###" + year + "###" + "###" + brief;
                videos.add(new Vod(guid, title, img, ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videos;
    }

    /**
     * 解析搜索结果（list），对照 Python get_list_search
     */
    private static List<Vod> parseSearchList(String html, String tid) {
        List<Vod> videos = new ArrayList<>();
        try {
            JSONArray jsonList = new JSONObject(html).optJSONArray("list");
            if (jsonList == null) return videos;
            for (int i = 0; i < jsonList.length(); i++) {
                JSONObject vod = jsonList.getJSONObject(i);
                String url = vod.optString("urllink");
                if (TextUtils.isEmpty(url)) continue;
                String title = removeHtml(vod.optString("title"));
                String img = vod.optString("imglink");
                String id = vod.optString("id");
                String brief = vod.optString("channel");
                String year = vod.optString("uploadtime");
                String guid = tid + "###" + title + "###" + url + "###" + img + "###" + id + "###" + year + "###" + "###" + brief;
                videos.add(new Vod(guid, title, img, year));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videos;
    }

    /**
     * 从 JSON 提集数，对照 Python get_EpisodesList
     */
    private static List<String> extractEpisodes(JSONArray jsonList) {
        List<String> videos = new ArrayList<>();
        try {
            for (int i = 0; i < jsonList.length(); i++) {
                JSONObject vod = jsonList.getJSONObject(i);
                String url = vod.optString("guid");
                if (TextUtils.isEmpty(url)) continue;
                String title = vod.optString("title");
                videos.add(title + "$" + url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videos;
    }

    /**
     * 从 HTML 正则提取集数（回退），对照 Python get_EpisodesList_re
     */
    private static List<String> extractEpisodesRegex(String html, Pattern pattern) {
        List<String> videos = new ArrayList<>();
        Matcher m = pattern.matcher(html);
        while (m.find()) {
            String url = m.group("url");
            if (TextUtils.isEmpty(url)) continue;
            String title = m.group("title");
            videos.add(title + "$" + url);
        }
        return videos;
    }

    private static Pattern getFallbackPattern(String tid) {
        if ("电视剧".equals(tid) || "纪录片".equals(tid)) return EPISODE_DRAMA;
        if ("特别节目".equals(tid)) return EPISODE_SPECIAL;
        if ("动画片".equals(tid)) return EPISODE_CARTOON;
        if ("节目大全".equals(tid)) return EPISODE_COLUMN;
        return null;
    }

    /**
     * 取 m3u8 高清地址，对照 Python get_m3u8
     */
    private static String getM3u8(String pid) {
        try {
            String url = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + pid;
            String html = OkHttp.string(url, getHeaders());
            String link = new JSONObject(html).optString("hls_url").trim();
            String content = OkHttp.string(link, getHeaders()).trim();
            String[] arr = content.split("\n");

            Matcher m = DOMAIN_PATTERN.matcher(link);
            String urlPrefix = m.find() ? m.group(1) : "";

            String[] subUrl = arr[arr.length - 1].split("/");
            subUrl[3] = "1200";
            subUrl[subUrl.length - 1] = "1200.m3u8";
            String hdUrl = urlPrefix + TextUtils.join("/", subUrl);

            if (testWebPage(hdUrl)) return hdUrl;
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 测试网络地址是否存在，对照 Python TestWebPage（HEAD 请求）
     */
    private static boolean testWebPage(String url) {
        try {
            okhttp3.Response response = OkHttp.newCall(url, "");
            int code = response.code();
            response.close();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static LinkedHashMap<String, List<Filter>> createFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        filters.put("电视剧", Arrays.asList(
            new Filter("datafl-sc", "类型", values("全部","","谍战","谍战","悬疑","悬疑","刑侦","刑侦","历史","历史","古装","古装","武侠","武侠","军旅","军旅","战争","战争","喜剧","喜剧","青春","青春","言情","言情","偶像","偶像","家庭","家庭","年代","年代","革命","革命","农村","农村","都市","都市","其他","其他")),
            new Filter("datadq-area", "地区", values("全部","","中国大陆","中国大陆","中国香港","香港","美国","美国","欧洲","欧洲","泰国","泰国")),
            new Filter("datanf-year", "年份", values("全部","","2023","2023","2022","2022","2021","2021","2020","2020","2019","2019","2018","2018","2017","2017","2016","2016","2015","2015","2014","2014","2013","2013","2012","2012","2011","2011","2010","2010","2009","2009","2008","2008","2007","2007","2006","2006","2005","2005","2004","2004","2003","2003","2002","2002","2001","2001","2000","2000","1999","1999","1998","1998","1997","1997")),
            new Filter("dataszm-letter", "字母", values("全部","","A","A","C","C","E","E","F","F","G","G","H","H","I","I","J","J","K","K","L","L","M","M","N","N","O","O","P","P","Q","Q","R","R","S","S","T","T","U","U","V","V","W","W","X","X","Y","Y","Z","Z","0-9","0-9"))
        ));

        filters.put("动画片", Arrays.asList(
            new Filter("datafl-sc", "类型", values("全部","","亲子","亲子","搞笑","搞笑","冒险","冒险","动作","动作","宠物","宠物","体育","体育","益智","益智","历史","历史","教育","教育","校园","校园","言情","言情","武侠","武侠","经典","经典","未来","未来","古代","古代","神话","神话","真人","真人","励志","励志","热血","热血","奇幻","奇幻","童话","童话","剧情","剧情","夺宝","夺宝","其他","其他")),
            new Filter("datadq-area", "地区", values("全部","","中国大陆","中国大陆","美国","美国","欧洲","欧洲")),
            new Filter("dataszm-letter", "字母", values("全部","","A","A","C","C","E","E","F","F","G","G","H","H","I","I","J","J","K","K","L","L","M","M","N","N","O","O","P","P","Q","Q","R","R","S","S","T","T","U","U","V","V","W","W","X","X","Y","Y","Z","Z","0-9","0-9"))
        ));

        filters.put("纪录片", Arrays.asList(
            new Filter("datapd-channel", "频道", values("全部","","CCTV{1 综合","CCTV{1 综合","CCTV{2 财经","CCTV{2 财经","CCTV{3 综艺","CCTV{3 综艺","CCTV{4 中文国际","CCTV{4 中文国际","CCTV{5 体育","CCTV{5 体育","CCTV{6 电影","CCTV{6 电影","CCTV{7 国防军事","CCTV{7 国防军事","CCTV{8 电视剧","CCTV{8 电视剧","CCTV{9 纪录","CCTV{9 纪录","CCTV{10 科教","CCTV{10 科教","CCTV{11 戏曲","CCTV{11 戏曲","CCTV{12 社会与法","CCTV{12 社会与法","CCTV{13 新闻","CCTV{13 新闻","CCTV{14 少儿","CCTV{14 少儿","CCTV{15 音乐","CCTV{15 音乐","CCTV{17 农业农村","CCTV{17 农业农村")),
            new Filter("datafl-sc", "类型", values("全部","","人文历史","人文历史","人物","人物","军事","军事","探索","探索","社会","社会","时政","时政","经济","经济","科技","科技")),
            new Filter("datanf-year", "年份", values("全部","","2023","2023","2022","2022","2021","2021","2020","2020","2019","2019","2018","2018","2017","2017","2016","2016","2015","2015","2014","2014","2013","2013","2012","2012","2011","2011","2010","2010","2009","2009","2008","2008")),
            new Filter("dataszm-letter", "字母", values("全部","","A","A","C","C","E","E","F","F","G","G","H","H","I","I","J","J","K","K","L","L","M","M","N","N","O","O","P","P","Q","Q","R","R","S","S","T","T","U","U","V","V","W","W","X","X","Y","Y","Z","Z","0-9","0-9"))
        ));

        filters.put("特别节目", Arrays.asList(
            new Filter("datapd-channel", "频道", values("全部","","CCTV{1 综合","CCTV{1 综合","CCTV{2 财经","CCTV{2 财经","CCTV{3 综艺","CCTV{3 综艺","CCTV{4 中文国际","CCTV{4 中文国际","CCTV{5 体育","CCTV{5 体育","CCTV{6 电影","CCTV{6 电影","CCTV{7 国防军事","CCTV{7 国防军事","CCTV{8 电视剧","CCTV{8 电视剧","CCTV{9 纪录","CCTV{9 纪录","CCTV{10 科教","CCTV{10 科教","CCTV{11 戏曲","CCTV{11 戏曲","CCTV{12 社会与法","CCTV{12 社会与法","CCTV{13 新闻","CCTV{13 新闻","CCTV{14 少儿","CCTV{14 少儿","CCTV{15 音乐","CCTV{15 音乐","CCTV{17 农业农村","CCTV{17 农业农村")),
            new Filter("datafl-sc", "类型", values("全部","","全部","全部","新闻","新闻","经济","经济","综艺","综艺","体育","体育","军事","军事","影视","影视","科教","科教","戏曲","戏曲","青少","青少","音乐","音乐","社会","社会","公益","公益","其他","其他")),
            new Filter("dataszm-letter", "字母", values("全部","","A","A","C","C","E","E","F","F","G","G","H","H","I","I","J","J","K","K","L","L","M","M","N","N","O","O","P","P","Q","Q","R","R","S","S","T","T","U","U","V","V","W","W","X","X","Y","Y","Z","Z","0-9","0-9"))
        ));

        filters.put("节目大全", Arrays.asList(
            new Filter("cid", "频道", values("全部","","CCTV-1综合","EPGC1386744804340101","CCTV-2财经","EPGC1386744804340102","CCTV-3综艺","EPGC1386744804340103","CCTV-4中文国际","EPGC1386744804340104","CCTV-5体育","EPGC1386744804340107","CCTV-6电影","EPGC1386744804340108","CCTV-7国防军事","EPGC1386744804340109","CCTV-8电视剧","EPGC1386744804340110","CCTV-9纪录","EPGC1386744804340112","CCTV-10科教","EPGC1386744804340113","CCTV-11戏曲","EPGC1386744804340114","CCTV-12社会与法","EPGC1386744804340115","CCTV-13新闻","EPGC1386744804340116","CCTV-14少儿","EPGC1386744804340117","CCTV-15音乐","EPGC1386744804340118","CCTV-16奥林匹克","EPGC1634630207058998","CCTV-17农业农村","EPGC1563932742616872","CCTV-5+体育赛事","EPGC1468294755566101")),
            new Filter("fc", "分类", values("全部","","新闻","新闻","体育","体育","综艺","综艺","健康","健康","生活","生活","科教","科教","经济","经济","农业","农业","法治","法治","军事","军事","少儿","少儿","动画","动画","纪实","纪实","戏曲","戏曲","音乐","音乐","影视","影视")),
            new Filter("fl", "字母", values("全部","","A","A","B","B","C","C","D","D","E","E","F","F","G","G","H","H","I","I","J","J","K","K","L","L","M","M","N","N","O","O","P","P","Q","Q","R","R","S","S","T","T","U","U","V","V","W","W","X","X","Y","Y","Z","Z")),
            new Filter("year", "年份", values("全部","","2023","2023","2022","2022","2021","2021","2020","2020","2019","2019","2018","2018","2017","2017","2016","2016","2015","2015","2014","2014","2013","2013","2012","2012","2011","2011","2010","2010","2009","2009","2008","2008","2007","2007","2006","2006","2005","2005","2004","2004","2003","2003","2002","2002","2001","2001","2000","2000")),
            new Filter("month", "月份", values("全部","","12","12","11","11","10","10","09","09","08","08","07","07","06","06","05","05","04","04","03","03","02","02","01","01"))
        ));

        return filters;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        // 对照 Python cateManual: type_name=k, type_id=cateManual[k]
        classes.add(new Class("节目大全", "央视大全"));
        classes.add(new Class("电视剧", "电视剧"));
        classes.add(new Class("动画片", "动画片"));
        classes.add(new Class("纪录片", "纪录片"));
        classes.add(new Class("特别节目", "特别节目"));
        return Result.string(classes, new ArrayList<>(), createFilters());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = parsePage(pg);
        int pageCount = "节目大全".equals(tid) ? 20 : 24;
        String url = buildUrl(tid, pg, extend);

        List<Vod> videos = new ArrayList<>();
        try {
            String htmlText = OkHttp.string(url, getHeaders());
            if ("节目大全".equals(tid)) {
                // JSONP 回调 ko(...) 去壳：htmlText[3:index]
                int index = htmlText.lastIndexOf(");");
                if (index > -1) {
                    htmlText = htmlText.substring(3, index);
                    videos = parseColumnList(htmlText, tid);
                }
            } else {
                videos = parseAlbumList(htmlText, tid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int count = videos.size() >= pageCount ? 9999 : page;
        return Result.get().vod(videos).page(page, count, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        // vodId 格式：tid###title###url###img###id###year###actors###brief
        String[] aid = ids.get(0).split("###");
        String tid = aid[0];
        String title = aid[1];
        String lastVideo = aid[2];
        String logo = aid[3];
        String id = aid[4];
        String vodYear = aid[5];
        String actors = aid[6];
        String brief = aid[7];
        String fromId = "CCTV";

        // 构建 URL（在 try 外部，节目大全需先获取 ctid）
        String url;
        if ("节目大全".equals(tid)) {
            String lastUrl = "https://api.cntv.cn/video/videoinfoByGuid?guid=" + id + "&serviceId=tvcctv";
            String htmlTxt = OkHttp.string(lastUrl, getHeaders());
            String topicId = new JSONObject(htmlTxt).optString("ctid");
            url = "https://api.cntv.cn/NewVideo/getVideoListByColumn?id=" + topicId + "&d=&p=1&n=100&sort=desc&mode=0&serviceId=tvcctv&t=json";
        } else {
            url = "https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew?id=" + id + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
        }

        List<String> videoList = new ArrayList<>();
        try {
            if ("搜索".equals(tid)) {
                fromId = "中央台";
                videoList.add(title + "$" + lastVideo);
            } else {
                String htmlTxt = OkHttp.string(url, getHeaders());
                JSONObject data = new JSONObject(htmlTxt).optJSONObject("data");
                if (data != null) {
                    JSONArray jsonList = data.optJSONArray("list");
                    if (jsonList != null) {
                        videoList = extractEpisodes(jsonList);
                    }
                }
                // 回退正则提取
                if (videoList.isEmpty()) {
                    String fallbackHtml = OkHttp.string(lastVideo, getHeaders());
                    Pattern pattern = getFallbackPattern(tid);
                    if (pattern != null) {
                        videoList = extractEpisodesRegex(fallbackHtml, pattern);
                        fromId = "央视";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (videoList.isEmpty()) return "";

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(title);
        vod.setVodPic(logo);
        vod.setTypeName(tid);
        vod.setVodYear(vodYear);
        vod.setVodArea("");
        vod.setVodRemarks("");
        vod.setVodActor(actors);
        vod.setVodDirector("");
        vod.setVodContent(brief);
        vod.setVodPlayFrom(fromId);
        vod.setVodPlayUrl(TextUtils.join("#", videoList));
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String encodedKey = encode(key);
        // Python 始终用 page=1
        String url = "https://search.cctv.com/ifsearch.php?page=1&qtext=" + encodedKey
                + "&sort=relevance&pageSize=20&type=video&vtime=-1&datepid=1&channel=&pageflag=0&qtext_str=" + encodedKey;
        List<Vod> videos = new ArrayList<>();
        try {
            String htmlTxt = OkHttp.string(url, getHeaders());
            videos = parseSearchList(htmlTxt, "搜索");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(videos);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = "";
        int parse = 0;

        if ("CCTV".equals(flag)) {
            url = getM3u8(id);
        } else {
            try {
                String html = OkHttp.string(id, getHeaders());
                String guid = getRegexText(html, GUID_PATTERN);
                url = getM3u8(guid);
            } catch (Exception e) {
                url = id;
                parse = 1;
            }
        }

        // url 不含 https: → 回退嗅探
        if (!url.contains("https:")) {
            url = id;
            parse = 1;
        }

        return Result.get().url(url).header(getPlayerHeaders()).parse(parse).string();
    }
}
