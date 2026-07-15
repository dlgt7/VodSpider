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
     * 支持多清晰度 fallback: 2000 -> 1500 -> 1200 -> 800 -> 450
     */
    private String getPlayUrl(String pid) {
        try {
            String url = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + pid;
            String resp = OkHttp.string(url, getHeaders());
            JSONObject obj = new JSONObject(resp);

            String hlsUrl = obj.optString("hls_url").trim();
            if (TextUtils.isEmpty(hlsUrl)) return "";

            String hlsContent = OkHttp.string(hlsUrl, getHeaders()).trim();
            String[] lines = hlsContent.split("\n");
            if (lines.length < 1) return hlsUrl;

            // 提取域名
            Matcher matcher = DOMAIN_PATTERN.matcher(hlsUrl);
            if (!matcher.find()) return hlsUrl;
            String domain = matcher.group(1);

            // 尝试多种清晰度（从高到低）
            int[] qualities = new int[]{2000, 1500, 1200, 800, 450};
            String lastLine = lines[lines.length - 1];
            String[] parts = lastLine.split("/");

            if (parts.length > 3) {
                for (int quality : qualities) {
                    parts[3] = String.valueOf(quality);
                    parts[parts.length - 1] = quality + ".m3u8";

                    StringBuilder hdUrl = new StringBuilder(domain);
                    for (int i = 0; i < parts.length; i++) {
                        hdUrl.append("/").append(parts[i]);
                    }

                    try {
                        String hdResp = OkHttp.string(hdUrl.toString(), getHeaders());
                        if (!TextUtils.isEmpty(hdResp) && hdResp.contains("#EXT")) {
                            return hdUrl.toString();
                        }
                    } catch (Exception e) {
                        // 该清晰度不可用，继续尝试下一个
                    }
                }
            }

            // 回退：拼接最后一段（原始清晰度）
            StringBuilder fallbackUrl = new StringBuilder(domain);
            fallbackUrl.append("/").append(lastLine);
            return fallbackUrl.toString();
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

            // 方式1: 通过 albumId 获取（电视剧、纪录片、特别节目常用）
            if (!TextUtils.isEmpty(videoId)) {
                try {
                    String apiUrl = "https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew?id=" + videoId + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
                    String resp = OkHttp.string(apiUrl, getHeaders());
                    JSONObject obj = new JSONObject(resp);
                    JSONObject data = obj.optJSONObject("data");

                    if (data != null) {
                        JSONArray list = data.optJSONArray("list");
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject item = list.optJSONObject(i);
                                if (item == null) continue;

                                String guid = item.optString("guid");
                                String title = item.optString("title");
                                if (!TextUtils.isEmpty(guid)) {
                                    playUrls.add(title + "$" + guid);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // skip
                }
            }

            // 方式2: 如果方式1失败，尝试通过 url 页面获取 guid（动画片常用）
            if (playUrls.isEmpty() && !TextUtils.isEmpty(url)) {
                try {
                    String html = OkHttp.string(url, getHeaders());
                    Matcher matcher = GUID_PATTERN.matcher(html);
                    if (matcher.find()) {
                        String guid = matcher.group(1);
                        playUrls = getVideoList(guid);
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
        // id格式: 可能是完整URL或GUID
        
        String playUrl = id;
        
        // 如果是GUID,尝试获取播放地址
        if (id != null && id.startsWith("VIDE") && !id.startsWith("http")) {
            // 尝试通过API获取m3u8地址
            try {
                String m3u8Url = getPlayUrl(id);
                if (!TextUtils.isEmpty(m3u8Url)) {
                    playUrl = m3u8Url;
                }
            } catch (Exception e) {
                // API失败,继续使用原始值
            }
        }
        
        // 设置请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");
        
        // 返回播放地址
        return Result.get().url(playUrl).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // smali 中返回空结果
        return Result.string(new ArrayList<>());
    }
}