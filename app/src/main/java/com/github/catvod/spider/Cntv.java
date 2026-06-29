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
 * @author CatVod
 * @date 2024-10-06
 */
public class Cntv extends Spider {

    private static final Pattern GUID_PATTERN = Pattern.compile("var\\s+guid\\s*=\\s*\"(.+?)\";");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.]+)/");

    // Fallback episode extraction patterns (when API returns empty list)
    private static final Pattern EPISODE_DRAMA = Pattern.compile(
            "'title':\\s*'(?<title>.+?)',\\n{0,1}\\s*'brief':\\s*'(.+?)',\\n{0,1}\\s*'img':\\s*'(.+?)',\\n{0,1}\\s*'url':\\s*'(?<url>.+?)'"
    );
    private static final Pattern EPISODE_SPECIAL = Pattern.compile(
            "class=\"tp1\"><a\\s*href=\"(?<url>https://.+?)\"\\s*target=\"_blank\"\\s*title=\"(?<title>.+?)\"></a></div>"
    );
    private static final Pattern EPISODE_CARTOON = Pattern.compile(
            "'title':\\s*'(?<title>.+?)',\\n{0,1}\\s*'img':\\s*'(.+?)',\\n{0,1}\\s*'brief':\\s*'(.+?)',\\n{0,1}\\s*'url':\\s*'(?<url>.+?)'"
    );
    private static final Pattern EPISODE_COLUMN = Pattern.compile(
            "href=\"(?<url>.+?)\" target=\"_blank\" alt=\"(?<title>.+?)\" title=\".+?\">"
    );

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

    private static List<Filter.Value> values(String... pairs) {
        List<Filter.Value> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new Filter.Value(pairs[i], pairs[i + 1]));
        }
        return list;
    }

    private static String buildUrl(String type, String page, HashMap<String, String> extend) {
        String area = encode(getExtend(extend, "datadq-area"));
        String sc = encode(getExtend(extend, "datafl-sc"));
        String year = getExtend(extend, "datanf-year");
        String letter = getExtend(extend, "dataszm-letter");
        String channel = encode(getExtend(extend, "datapd-channel"));
        String encodedType = encode(type);

        String suffix = "&n=24&serviceId=tvcctv&topv=1&t=json";
        if ("动画片".equals(type)) {
            StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955899450127&area=");
            sb.append(area).append("&sc=").append(sc).append("&fc=").append(encodedType)
              .append("&letter=").append(letter).append("&p=");
            return sb.append(page).append(suffix).toString();
        } else if ("纪录片".equals(type)) {
            StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955924871139&fc=");
            sb.append(encodedType).append("&channel=").append(channel).append("&sc=").append(sc)
              .append("&year=").append(year).append("&letter=").append(letter).append("&p=")
              .append(page).append(suffix);
            return sb.toString();
        } else if ("电视剧".equals(type)) {
            StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955853485115&area=");
            sb.append(area).append("&sc=").append(sc).append("&fc=").append(encodedType)
              .append("&year=").append(year).append("&letter=").append(letter).append("&p=")
              .append(page).append(suffix);
            return sb.toString();
        } else if ("特别节目".equals(type)) {
            StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955953877151&channel=");
            sb.append(channel).append("&sc=").append(sc).append("&fc=").append(encodedType)
              .append("&bigday=&letter=").append(letter).append("&p=");
            return sb.append(page).append(suffix).toString();
        } else {
            // 节目大全
            String cid = getExtend(extend, "cid");
            String fc = getExtend(extend, "fc");
            String fl = getExtend(extend, "fl");
            StringBuilder sb = new StringBuilder("https://api.cntv.cn/lanmu/columnSearch?&fl=");
            sb.append(fl).append("&fc=").append(fc).append("&cid=").append(cid)
              .append("&p=").append(page).append("&n=20&serviceId=tvcctv&t=json&cb=ko");
            return sb.toString();
        }
    }

    private static List<Vod> parseList(String response, String type) throws Exception {
        List<Vod> list = new ArrayList<>();
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
        return list;
    }

    private static String removeHtml(String text) {
        return text.replaceAll("<[^>]+>", "").replace("&nbsp;", " ");
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

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/94.0.4606.54 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");
        return headers;
    }

    /**
     * 栏目大全：通过 guid 查找 ctid，再获取视频列表
     */
    private ArrayList<String> getColumnVideoList(String guid) {
        ArrayList<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(guid)) return list;

        try {
            String infoUrl = "https://api.cntv.cn/video/videoinfoByGuid?guid=" + guid + "&serviceId=tvcctv";
            String response = OkHttp.string(infoUrl, getHeaders());
            JSONObject object = new JSONObject(response);
            String ctid = object.optString("ctid");
            if (TextUtils.isEmpty(ctid)) {
                JSONObject data = object.optJSONObject("data");
                if (data != null) ctid = data.optString("ctid");
            }
            if (TextUtils.isEmpty(ctid)) return list;

            String listUrl = "https://api.cntv.cn/NewVideo/getVideoListByColumn?id=" + ctid + "&d=&p=1&n=100&sort=desc&mode=0&serviceId=tvcctv&t=json";
            String listResponse = OkHttp.string(listUrl, getHeaders());
            JSONObject listObject = new JSONObject(listResponse);
            JSONObject listData = listObject.optJSONObject("data");
            if (listData == null) return list;

            JSONArray videoList = listData.optJSONArray("list");
            if (videoList == null) return list;

            for (int i = 0; i < videoList.length(); i++) {
                JSONObject video = videoList.getJSONObject(i);
                String videoGuid = video.optString("guid");
                String title = video.optString("title");
                if (!TextUtils.isEmpty(videoGuid)) {
                    list.add(title + "$" + videoGuid);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 电视剧/动画片/纪录片/特别节目：通过 albumId 获取视频列表
     */
    private ArrayList<String> getAlbumVideoList(String id) {
        ArrayList<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(id)) return list;

        try {
            String url = "https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew?id=" + id + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
            String response = OkHttp.string(url, getHeaders());
            JSONObject object = new JSONObject(response);
            JSONObject data = object.optJSONObject("data");
            if (data == null) return list;

            JSONArray array = data.optJSONArray("list");
            if (array == null) return list;

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String guid = item.optString("guid");
                String title = item.optString("title");
                if (!TextUtils.isEmpty(guid)) {
                    list.add(title + "$" + guid);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 正则提取剧集列表（fallback：当 API 返回空列表时，从 HTML 页面提取）
     */
    private ArrayList<String> getEpisodesByRegex(String html, Pattern pattern) {
        ArrayList<String> list = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String title = matcher.group("title");
            String url = matcher.group("url");
            if (!TextUtils.isEmpty(url)) {
                list.add(title + "$" + url);
            }
        }
        return list;
    }

    /**
     * 获取视频播放地址（m3u8）
     * 返回空字符串表示获取失败，上层应触发嗅探
     */
    private String getVideoUrl(String guid) {
        String url = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + guid;
        try {
            String response = OkHttp.string(url, getHeaders());
            JSONObject object = new JSONObject(response);
            String hlsUrl = object.optString("hls_url").trim();
            if (TextUtils.isEmpty(hlsUrl)) return "";

            String hlsContent = OkHttp.string(hlsUrl, getHeaders()).trim();
            String[] lines = hlsContent.split("\n");
            if (lines.length < 1) return hlsUrl;

            Matcher matcher = DOMAIN_PATTERN.matcher(hlsUrl);
            if (!matcher.find()) return hlsUrl;

            String domain = matcher.group(1);
            String lastLine = lines[lines.length - 1];
            String[] parts = lastLine.split("/");

            parts[3] = "1200";
            parts[parts.length - 1] = "1200.m3u8";
            String hdUrl = domain + TextUtils.join("/", parts);

            // Test HD URL accessibility (Python uses HEAD request)
            try {
                OkHttp.string(hdUrl, getHeaders());
                return hdUrl;
            } catch (Exception e) {
                return "";  // Return empty to trigger sniff (matching Python)
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("节目大全", "央视大全"));
        classes.add(new Class("电视剧", "电视剧"));
        classes.add(new Class("动画片", "动画片"));
        classes.add(new Class("纪录片", "纪录片"));
        classes.add(new Class("特别节目", "特别节目"));
        return Result.string(classes, new ArrayList<>(), createFilters());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String page = TextUtils.isEmpty(pg) ? "1" : pg;
        String url = buildUrl(tid, page, extend);

        List<Vod> videos = new ArrayList<>();
        String response = OkHttp.string(url, getHeaders());
        boolean isColumn = "节目大全".equals(tid);

        if (!isColumn && response.trim().startsWith("ko(")) {
            isColumn = true;
        }

        if (isColumn) {
            // Strip ko(...) callback wrapper
            int endIndex = response.lastIndexOf(");");
            if (endIndex > 0) {
                response = response.substring(response.indexOf("(") + 1, endIndex);
            }

            JSONObject object = new JSONObject(response);
            JSONObject resp = object.optJSONObject("response");
            if (resp != null) {
                JSONArray docs = resp.optJSONArray("docs");
                if (docs != null) {
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

                        String vodId = tid + "###" + name + "###" + website + "###" + logo + "###" + videoId + "###" + playDate + "######" + brief;
                        videos.add(new Vod(vodId, name, logo, ""));
                    }
                }
            }
        } else {
            videos = parseList(response, tid);
        }

        int pageNum = 1;
        try { pageNum = Integer.parseInt(page); } catch (Exception e) { }
        int pageCount = "节目大全".equals(tid) ? 20 : 24;
        int pageCountResult = videos.size() >= pageCount ? 9999 : pageNum;

        return Result.get().vod(videos).page(pageNum, pageCountResult, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) {
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

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setTypeName(type);
        vod.setVodYear(year);
        vod.setVodActor(actor);
        vod.setVodContent(content);

        ArrayList<String> playUrls = new ArrayList<>();
        String fromId = "CCTV";

        if ("搜索".equals(type)) {
            // 搜索结果：直接使用存储的 URL
            fromId = "中央台";
            playUrls.add(name + "$" + lastVideo);
        } else {
            // 尝试通过 API 获取剧集列表
            if ("节目大全".equals(type)) {
                playUrls = getColumnVideoList(videoId);
            } else {
                playUrls = getAlbumVideoList(videoId);
            }

            // Fallback: API 返回空列表时，从 lastVideo 页面正则提取
            if (playUrls.isEmpty() && !TextUtils.isEmpty(lastVideo)) {
                fromId = "央视";
                try {
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
                        playUrls = getEpisodesByRegex(html, pattern);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (playUrls.isEmpty()) {
            return "";
        }

        vod.setVodPlayFrom(fromId);
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String playUrl = "";
        int parse = 0;

        if ("CCTV".equals(flag)) {
            playUrl = getVideoUrl(id);
        } else {
            // 非 CCTV：尝试从页面提取 guid 再获取 m3u8
            try {
                String html = OkHttp.string(id, getHeaders());
                Matcher matcher = GUID_PATTERN.matcher(html);
                if (matcher.find()) {
                    playUrl = getVideoUrl(matcher.group(1));
                } else {
                    playUrl = id;
                    parse = 1;
                }
            } catch (Exception e) {
                playUrl = id;
                parse = 1;
            }
        }

        // URL 不含 https: 时触发嗅探
        if (TextUtils.isEmpty(playUrl) || !playUrl.contains("https:")) {
            playUrl = id;
            parse = 1;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1");

        Result result = Result.get().url(playUrl).header(headers);
        if (parse == 1) result.parse();
        return result.string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> videos = new ArrayList<>();
        try {
            String encodedKey = encode(key);
            String url = "https://search.cctv.com/ifsearch.php?page=1&qtext=" + encodedKey
                    + "&sort=relevance&pageSize=20&type=video&vtime=-1&datepid=1&channel=&pageflag=0&qtext_str=" + encodedKey;
            String response = OkHttp.string(url, getHeaders());
            JSONObject object = new JSONObject(response);
            JSONArray list = object.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String urllink = item.optString("urllink");
                    if (TextUtils.isEmpty(urllink)) continue;

                    String title = removeHtml(item.optString("title"));
                    String imglink = item.optString("imglink");
                    String id = item.optString("id");
                    String channel = item.optString("channel");
                    String uploadtime = item.optString("uploadtime");

                    String vodId = "搜索" + "###" + title + "###" + urllink + "###" + imglink + "###" + id + "###" + uploadtime + "###" + "###" + channel;
                    videos.add(new Vod(vodId, title, imglink, uploadtime));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(videos);
    }
}
