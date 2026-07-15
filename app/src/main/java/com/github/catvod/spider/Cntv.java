package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cntv Spider (央视网)
 * Converted from smali
 */
public class Cntv extends Spider {

    // DEX 同名不同类型字段必须重命名
    private static final Pattern GUID_PATTERN = Pattern.compile("var\\s+guid\\s*=\\s*\"(.+?)\";");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.]+)/");
    private static final String[] CATEGORIES = new String[]{"电视剧", "动画片", "纪录片", "特别节目", "栏目大全"};

    public Cntv() {
        super();
    }

    /**
     * 构建 API URL (静态方法 a)
     */
    private static String buildApiUrl(String tid, String pg, HashMap<String, String> extend) {
        // 从 HashMap 获取各项参数
        String area = getExtendValue("datadq-area", extend);
        String sc = getExtendValue("datafl-sc", extend);
        String year = getExtendValue("datanf-year", extend);
        String letter = getExtendValue("dataszm-letter", extend);
        String channel = getExtendValue("datapd-channel", extend);

        try {
            String encodedTid = URLEncoder.encode(tid, "UTF-8");

            if ("动画片".equals(tid)) {
                StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955899450127&area=");
                sb.append(area).append("&sc=").append(sc).append("&fc=").append(encodedTid)
                        .append("&letter=").append(letter).append("&p=").append(pg)
                        .append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            } else if ("纪录片".equals(tid)) {
                StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955924871139&fc=");
                sb.append(encodedTid).append("&channel=").append(channel).append("&sc=").append(sc)
                        .append("&year=").append(year).append("&letter=").append(letter)
                        .append("&p=").append(pg).append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            } else if ("电视剧".equals(tid)) {
                StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955853485115&area=");
                sb.append(area).append("&sc=").append(sc).append("&fc=").append(encodedTid)
                        .append("&year=").append(year).append("&letter=").append(letter)
                        .append("&p=").append(pg).append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            } else if ("特别节目".equals(tid)) {
                StringBuilder sb = new StringBuilder("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955953877151&channel=");
                sb.append(channel).append("&sc=").append(sc).append("&fc=").append(encodedTid)
                        .append("&bigday=&letter=").append(letter).append("&p=").append(pg)
                        .append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            } else {
                // 栏目大全
                String cid = getExtendValue("cid", extend);
                String fc = getExtendValue("fc", extend);
                String fl = getExtendValue("fl", extend);
                StringBuilder sb = new StringBuilder("https://api.cntv.cn/lanmu/columnSearch?&fl=");
                sb.append(fl).append("&fc=").append(fc).append("&cid=").append(cid)
                        .append("&p=").append(pg).append("&n=20&serviceId=tvcctv&t=json&cb=ko");
                return sb.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从 HashMap 获取参数值 (静态方法 e)
     */
    private static String getExtendValue(String key, HashMap<String, String> extend) {
        if (extend != null && extend.containsKey(key)) {
            String value = extend.get(key);
            if (value != null) return value;
        }
        return "";
    }

    /**
     * 解析列表数据 (静态方法 f)
     */
    private static ArrayList<Vod> parseVideoList(String json, String tid) {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data == null) return list;
            JSONArray array = data.optJSONArray("list");
            if (array == null) return list;

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String url = item.optString("url");
                if (TextUtils.isEmpty(url)) continue;

                String title = item.optString("title");
                String image = item.optString("image");
                String id = item.optString("id");
                String year = item.optString("year");
                String actors = item.optString("actors");
                String brief = item.optString("brief");

                // 构建 vod_id: tid###title###url###image###id###year###actors###brief
                StringBuilder vodId = new StringBuilder();
                vodId.append(tid).append("###").append(title).append("###").append(url)
                        .append("###").append(image).append("###").append(id)
                        .append("###").append(year).append("###").append(actors)
                        .append("###").append(brief);

                list.add(new Vod(vodId.toString(), title, image, ""));
            }
        } catch (Exception e) {
            // skip
        }
        return list;
    }

    /**
     * 获取视频列表 (实例方法 b)
     */
    private ArrayList<String> getVideoList(String guid) {
        ArrayList<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(guid)) return list;

        try {
            // 第一次 API 调用
            String url1 = "https://api.cntv.cn/video/videoinfoByGuid?guid=" + guid + "&serviceId=tvcctv";
            String resp1 = OkHttp.string(url1, getHeaders());
            JSONObject obj1 = new JSONObject(resp1);

            String ctid = obj1.optString("ctid");
            if (TextUtils.isEmpty(ctid)) {
                JSONObject data = obj1.optJSONObject("data");
                if (data != null) ctid = data.optString("ctid");
            }

            if (!TextUtils.isEmpty(ctid)) {
                // 第二次 API 调用
                String url2 = "https://api.cntv.cn/NewVideo/getVideoListByColumn?id=" + ctid + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
                String resp2 = OkHttp.string(url2, getHeaders());
                JSONObject obj2 = new JSONObject(resp2);
                JSONObject data = obj2.optJSONObject("data");

                if (data != null) {
                    JSONArray array = data.optJSONArray("list");
                    if (array != null && array.length() > 0) {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.optJSONObject(i);
                            if (item == null) continue;

                            String itemGuid = item.optString("guid");
                            String title = item.optString("title");
                            String itemUrl = item.optString("url"); // 获取完整URL
                            
                            if (!TextUtils.isEmpty(itemUrl)) {
                                // 优先使用完整URL
                                list.add(title + "$" + itemUrl);
                            } else if (!TextUtils.isEmpty(itemGuid)) {
                                // 回退使用GUID
                                list.add(title + "$" + itemGuid);
                            }
                        }
                        return list;
                    }
                }
            }

            // 回退：返回单个视频
            String title = obj1.optString("title", "正片");
            String fallbackUrl = obj1.optString("url");
            if (!TextUtils.isEmpty(fallbackUrl)) {
                list.add(title + "$" + fallbackUrl);
            } else {
                list.add(title + "$" + guid);
            }
        } catch (Exception e) {
            // skip
        }
        return list;
    }

    /**
     * 获取 HTTP headers (实例方法 c)
     */
    private Map<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/94.0.4606.54 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");
        return headers;
    }

    /**
     * 获取视频播放地址 (实例方法 d)
     * 简化清晰度逻辑,避免花屏
     */
    private String getPlayUrl(String pid) {
        try {
            String url = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + pid;
            String resp = OkHttp.string(url, getHeaders());
            JSONObject obj = new JSONObject(resp);

            String hlsUrl = obj.optString("hls_url").trim();
            if (TextUtils.isEmpty(hlsUrl)) return "";

            // 直接返回原始hls_url,避免清晰度切换导致花屏
            // 央视网的hls_url通常已经是最优清晰度
            return hlsUrl;
            
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (String category : CATEGORIES) {
            classes.add(new Class(category, category));
        }

        // 获取首页推荐(电视剧第1页)
        ArrayList<Vod> videos = new ArrayList<>();
        try {
            String url = buildApiUrl("电视剧", "1", new HashMap<>());
            String resp = OkHttp.string(url, getHeaders());
            videos = parseVideoList(resp, "电视剧");
        } catch (Exception e) {
            // skip
        }

        return Result.string(classes, videos);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";

        String url = buildApiUrl(tid, pg, extend);
        String resp = OkHttp.string(url, getHeaders());
        ArrayList<Vod> videos = new ArrayList<>();

        boolean isColumn = "栏目大全".equals(tid);
        boolean needStripJsonp = isColumn || (resp != null && resp.trim().startsWith("ko("));

        if (needStripJsonp) {
            // JSONP 回调去壳
            int endIndex = resp.lastIndexOf(");");
            if (endIndex > 0) {
                int startIndex = resp.indexOf("(");
                if (startIndex >= 0) {
                    resp = resp.substring(startIndex + 1, endIndex);
                }
            }

            if (isColumn) {
                // 解析栏目大全列表
                try {
                    JSONObject obj = new JSONObject(resp);
                    JSONObject response = obj.optJSONObject("response");
                    if (response != null) {
                        JSONArray docs = response.optJSONArray("docs");
                        if (docs != null) {
                            for (int i = 0; i < docs.length(); i++) {
                                JSONObject doc = docs.optJSONObject(i);
                                if (doc == null) continue;

                                JSONObject lastVIDE = doc.optJSONObject("lastVIDE");
                                String videoSharedCode = lastVIDE != null ? lastVIDE.optString("videoSharedCode") : "";
                                String column_name = doc.optString("column_name");
                                String column_website = doc.optString("column_website");
                                String column_logo = doc.optString("column_logo");
                                String column_playdate = doc.optString("column_playdate");
                                String column_brief = doc.optString("column_brief");

                                if (TextUtils.isEmpty(column_website)) continue;

                                // vod_id: 栏目大全###name###website###logo###videoSharedCode###playdate###brief
                                StringBuilder vodId = new StringBuilder();
                                vodId.append("栏目大全").append("###").append(column_name)
                                        .append("###").append(column_website)
                                        .append("###").append(column_logo)
                                        .append("###").append(videoSharedCode)
                                        .append("###").append(column_playdate)
                                        .append("######").append(column_brief);

                                videos.add(new Vod(vodId.toString(), column_name, column_logo, ""));
                            }
                        }
                    }
                } catch (Exception e) {
                    // skip
                }
            }
        }

        if (!isColumn) {
            videos = parseVideoList(resp, tid);
        }

        return Result.string(videos);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String[] parts = id.split("###", 8);

        String tid = parts[0];
        String name = parts.length > 1 ? parts[1] : "央视";
        String url = parts.length > 2 ? parts[2] : "";
        String pic = parts.length > 3 ? parts[3] : "";
        String videoId = parts.length > 4 ? parts[4] : "";
        String year = parts.length > 5 ? parts[5] : "";
        String actor = parts.length > 6 ? parts[6] : "";
        String brief = parts.length > 7 ? parts[7] : "";

        ArrayList<String> playUrls = new ArrayList<>();

        // 栏目大全特殊处理
        if ("栏目大全".equals(tid)) {
            playUrls = getVideoList(videoId);
        } else {
            // 其他分类：尝试多种方式获取播放列表

            // 方式1: 通过 albumId 获取（电视剧、纪录片、特别节目、动画片）
            // 注意：电视剧和动画片的API是 getVideoAlbumList，返回的item结构不同
            if (!TextUtils.isEmpty(videoId)) {
                try {
                    // 尝试电视剧/动画片专用API
                    String apiUrl = "https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew?id=" + videoId + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
                    String resp = OkHttp.string(apiUrl, getHeaders());
                    JSONObject obj = new JSONObject(resp);
                    JSONObject data = obj.optJSONObject("data");

                    if (data != null) {
                        JSONArray list = data.optJSONArray("list");
                        if (list != null && list.length() > 0) {
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject item = list.optJSONObject(i);
                                if (item == null) continue;

                                String guid = item.optString("guid");
                                String title = item.optString("title");
                                String itemUrl = item.optString("url"); // 获取完整URL
                                
                                // 必须使用完整URL,不能是bare GUID
                                if (!TextUtils.isEmpty(itemUrl)) {
                                    playUrls.add(title + "$" + itemUrl);
                                } else if (!TextUtils.isEmpty(guid)) {
                                    // 如果没有URL,尝试从GUID构造视频页URL
                                    // GUID格式: VIDE + 日期信息
                                    if (guid.startsWith("VIDE")) {
                                        // 从GUID提取日期(简化处理,使用当天日期)
                                        String datePath = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new java.util.Date());
                                        String constructedUrl = "https://tv.cctv.com/" + datePath + "/" + guid + ".shtml";
                                        playUrls.add(title + "$" + constructedUrl);
                                    } else {
                                        playUrls.add(title + "$" + guid);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // skip
                }
            }

            // 方式2: 如果方式1失败，尝试通过 url 页面获取 guid（动画片、电视剧详情页）
            if (playUrls.isEmpty() && !TextUtils.isEmpty(url)) {
                try {
                    String html = OkHttp.string(url, getHeaders());
                    
                    // 尝试从详情页提取剧集列表
                    // 方法1: 提取所有视频链接
                    Pattern videoLinkPattern = Pattern.compile("href=\"(https?://tv\\.cctv\\.com/[^\"]+\\.shtml)\"");
                    Matcher videoLinkMatcher = videoLinkPattern.matcher(html);
                    
                    int episodeCount = 0;
                    while (videoLinkMatcher.find() && episodeCount < 100) {
                        String videoUrl = videoLinkMatcher.group(1);
                        episodeCount++;
                        String episodeTitle = "第" + episodeCount + "集";
                        playUrls.add(episodeTitle + "$" + videoUrl);
                    }
                    
                    // 方法2: 如果没有提取到视频链接，尝试提取guid
                    if (playUrls.isEmpty()) {
                        Matcher matcher = GUID_PATTERN.matcher(html);
                        if (matcher.find()) {
                            String guid = matcher.group(1);
                            playUrls = getVideoList(guid);
                        }
                    }
                } catch (Exception e) {
                    // skip
                }
            }

            // 方式3: 如果方式2失败，videoId 是 32位哈希值，尝试直接获取
            if (playUrls.isEmpty() && !TextUtils.isEmpty(videoId)) {
                if (videoId.matches("[0-9a-fA-F]{32}")) {
                    playUrls = getVideoList(videoId);
                }
            }

            // 方式4: 最后尝试用 url 作为页面解析
            if (playUrls.isEmpty() && !TextUtils.isEmpty(url) && url.startsWith("http")) {
                try {
                    playUrls = getVideoList(url);
                } catch (Exception e) {
                    // skip
                }
            }
        }

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setTypeName(tid);
        vod.setVodYear(year);
        vod.setVodActor(actor);
        vod.setVodContent(brief);
        vod.setVodPlayFrom("CCTV");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id必须是完整URL(视频页或m3u8)
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");
        
        // 如果是视频页面URL,尝试从HTML提取真实播放地址
        if (id.startsWith("http") && id.contains(".shtml")) {
            try {
                String html = OkHttp.string(id, headers);
                
                // 尝试提取videoCenterGuid
                Pattern guidPattern = Pattern.compile("var\\s+videoCenterGuid\\s*=\\s*['\"]([^'\"]+)['\"]");
                Matcher guidMatcher = guidPattern.matcher(html);
                if (guidMatcher.find()) {
                    String guid = guidMatcher.group(1);
                    // 使用GUID调用API获取m3u8
                    String m3u8Url = getPlayUrl(guid);
                    if (!TextUtils.isEmpty(m3u8Url)) {
                        return Result.get().url(m3u8Url).parse(0).header(headers).string();
                    }
                }
                
                // 尝试提取其他常见视频配置变量
                Pattern urlPattern = Pattern.compile("(?:videoUrl|playUrl|src)\\s*[=:]\\s*['\"]([^'\"]*(?:\\.m3u8|\\.mp4)[^'\"]*)['\"]");
                Matcher urlMatcher = urlPattern.matcher(html);
                if (urlMatcher.find()) {
                    String videoUrl = urlMatcher.group(1);
                    if (videoUrl.startsWith("http")) {
                        return Result.get().url(videoUrl).parse(0).header(headers).string();
                    }
                }
                
            } catch (Exception e) {
                // 提取失败,继续使用parse(1)嗅探
            }
        }
        
        // 回退:返回视频页URL,让播放器嗅探
        return Result.get().url(id).parse(1).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // smali 中返回空结果
        return Result.string(new ArrayList<>());
    }
}