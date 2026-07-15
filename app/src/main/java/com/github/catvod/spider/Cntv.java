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
 * 从smali还原，修正关键逻辑差异
 */
public class Cntv extends Spider {

    // DEX 同名不同类型字段必须重命名（smali中两个a字段）
    private static final Pattern GUID_PATTERN;
    private static final Pattern DOMAIN_PATTERN;
    private static final String[] CATEGORIES;

    static {
        // <clinit> 初始化静态字段
        GUID_PATTERN = Pattern.compile("var\\s+guid\\s*=\\s*\"(.+?)\";");
        DOMAIN_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.]+)/");
        CATEGORIES = new String[]{"电视剧", "动画片", "纪录片", "特别节目", "栏目大全"};
    }

    public Cntv() {
        super();
    }

    /**
     * 构建 API URL (静态方法 a)
     * 根据不同分类构造不同的API请求URL
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

            // 动画片
            if ("动画片".equals(tid)) {
                StringBuilder sb = new StringBuilder();
                sb.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955899450127&area=");
                sb.append(area);
                sb.append("&sc=").append(sc);
                sb.append("&fc=").append(encodedTid);
                sb.append("&letter=").append(letter);
                sb.append("&p=").append(pg);
                sb.append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            }
            // 纪录片
            else if ("纪录片".equals(tid)) {
                StringBuilder sb = new StringBuilder();
                sb.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955924871139&fc=");
                sb.append(encodedTid);
                sb.append("&channel=").append(channel);
                sb.append("&sc=").append(sc);
                sb.append("&year=").append(year);
                sb.append("&letter=").append(letter);
                sb.append("&p=").append(pg);
                sb.append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            }
            // 电视剧
            else if ("电视剧".equals(tid)) {
                StringBuilder sb = new StringBuilder();
                sb.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955853485115&area=");
                sb.append(area);
                sb.append("&sc=").append(sc);
                sb.append("&fc=").append(encodedTid);
                sb.append("&year=").append(year);
                sb.append("&letter=").append(letter);
                sb.append("&p=").append(pg);
                sb.append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            }
            // 特别节目
            else if ("特别节目".equals(tid)) {
                StringBuilder sb = new StringBuilder();
                sb.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955953877151&channel=");
                sb.append(channel);
                sb.append("&sc=").append(sc);
                sb.append("&fc=").append(encodedTid);
                sb.append("&bigday=&letter=").append(letter);
                sb.append("&p=").append(pg);
                sb.append("&n=24&serviceId=tvcctv&topv=1&t=json");
                return sb.toString();
            }
            // 栏目大全（默认）
            else {
                String cid = getExtendValue("cid", extend);
                String fc = getExtendValue("fc", extend);
                String fl = getExtendValue("fl", extend);
                StringBuilder sb = new StringBuilder();
                sb.append("https://api.cntv.cn/lanmu/columnSearch?&fl=");
                sb.append(fl);
                sb.append("&fc=").append(fc);
                sb.append("&cid=").append(cid);
                sb.append("&p=").append(pg);
                sb.append("&n=20&serviceId=tvcctv&t=json&cb=ko");
                return sb.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从 HashMap 获取参数值 (静态方法 e)
     * 如果键不存在或值为null，返回空字符串
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
     * 提取视频列表，构建包含完整信息的vod_id
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
                String vsetid = item.optString("vsetid");
                String year = item.optString("year");
                String actors = item.optString("actors");
                String brief = item.optString("brief");

                // 构建 vod_id: tid###title###url###image###vsetid###year###actors###brief
                // 格式与smali完全一致
                StringBuilder vodId = new StringBuilder();
                vodId.append(tid).append("###").append(title).append("###").append(url)
                        .append("###").append(image).append("###").append(vsetid)
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
     * 根据guid获取剧集列表
     */
    private ArrayList<String> getVideoList(String guid) {
        ArrayList<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(guid)) return list;

        try {
            // 第一次 API 调用：获取ctid
            String url1 = "https://api.cntv.cn/video/videoinfoByGuid?guid=" + guid + "&serviceId=tvcctv";
            String resp1 = OkHttp.string(url1, getHeaders());
            JSONObject obj1 = new JSONObject(resp1);

            String ctid = obj1.optString("ctid");
            if (TextUtils.isEmpty(ctid)) {
                JSONObject data = obj1.optJSONObject("data");
                if (data != null) ctid = data.optString("ctid");
            }

            // 如果ctid非空，尝试获取剧集列表
            if (!TextUtils.isEmpty(ctid)) {
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

                            if (!TextUtils.isEmpty(itemGuid)) {
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
     * 获取 HTTP 请求头 (实例方法 c)
     */
    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/94.0.4606.54 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");
        return headers;
    }

    /**
     * 获取播放地址 (实例方法 d) - 从smali完整还原
     * 核心逻辑：从API获取hls_url，然后从返回的HTML中提取分辨率列表
     */
    private String getPlayUrl(String pid) {
        try {
            // 第一步：调用API获取hls_url
            String apiUrl = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + pid;
            String resp = OkHttp.string(apiUrl, getHeaders());
            JSONObject obj = new JSONObject(resp);

            String hlsUrl = obj.optString("hls_url");
            hlsUrl = hlsUrl.trim();

            if (TextUtils.isEmpty(hlsUrl)) {
                return "";
            }

            // 第二步：获取hls_url对应的HTML内容
            String htmlContent = OkHttp.string(hlsUrl, getHeaders()).trim();

            // 第三步：按换行符分割，获取分辨率列表
            String[] lines = htmlContent.split("\n");

            // 如果只有一行，直接返回原始hls_url
            if (lines.length < 1) {
                return hlsUrl;
            }

            // 第四步：用DOMAIN_PATTERN提取域名
            Matcher matcher = DOMAIN_PATTERN.matcher(hlsUrl);
            if (!matcher.find()) {
                return hlsUrl;
            }

            String domain = matcher.group(1);

            // 第五步：从最后一行提取分辨率选项（用/分隔）
            String lastLine = lines[lines.length - 1];
            String[] qualities = lastLine.split("/");

            // 第六步：如果分辨率数组长度>3，尝试构造高清URL
            if (qualities.length > 3) {
                // 修改第4个元素（索引3）为"1200"
                qualities[3] = "1200";
                // 修改最后一个元素为"1200.m3u8"
                qualities[qualities.length - 1] = "1200.m3u8";

                // 构造新URL
                StringBuilder newUrl = new StringBuilder(domain);
                newUrl.append("/");
                newUrl.append(TextUtils.join("/", qualities));

                // 尝试访问，如果成功则返回
                try {
                    OkHttp.string(newUrl.toString(), getHeaders());
                    return newUrl.toString();
                } catch (Exception e) {
                    // 失败则继续
                }
            }

            // 第七步：失败或分辨率数不足，返回最后一行
            return domain + "/" + lines[lines.length - 1];

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
        String vsetid = parts.length > 4 ? parts[4] : "";
        String year = parts.length > 5 ? parts[5] : "";
        String actor = parts.length > 6 ? parts[6] : "";
        String brief = parts.length > 7 ? parts[7] : "";

        ArrayList<String> playUrls = new ArrayList<>();

        // 栏目大全特殊处理
        if ("栏目大全".equals(tid)) {
            playUrls = getVideoList(vsetid);
        } else {
            // 其他分类：尝试多种方式获取播放列表

            // 方式1: 使用vsetid获取剧集列表
            if (!TextUtils.isEmpty(vsetid) && vsetid.startsWith("VSET")) {
                try {
                    String apiUrl = "https://api.cntv.cn/NewVideo/getVideoListById?id=" + vsetid + "&serviceId=tvcctv";
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

            // 方式2: 从详情页提取剧集链接
            if (playUrls.isEmpty() && !TextUtils.isEmpty(url)) {
                try {
                    String html = OkHttp.string(url, getHeaders());

                    // 提取guid
                    Matcher guidMatcher = GUID_PATTERN.matcher(html);
                    if (guidMatcher.find()) {
                        String guid = guidMatcher.group(1);
                        playUrls.add("正片$" + guid);
                    }
                } catch (Exception e) {
                    // skip
                }
            }

            // 方式3: 如果vsetid是32位哈希值
            if (playUrls.isEmpty() && !TextUtils.isEmpty(vsetid)) {
                if (vsetid.matches("[0-9a-fA-F]{32}")) {
                    playUrls = getVideoList(vsetid);
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
        String playUrl = "";

        // 如果flag是CCTV或者id不是http开头，调用getPlayUrl
        if ("CCTV".equals(flag) || !id.startsWith("http")) {
            playUrl = getPlayUrl(id);
        } else {
            // 否则尝试从HTML页面提取guid
            try {
                String html = OkHttp.string(id, getHeaders());
                Matcher matcher = GUID_PATTERN.matcher(html);
                if (matcher.find()) {
                    playUrl = getPlayUrl(matcher.group(1));
                }
            } catch (Exception ignored) {}
        }

        if (TextUtils.isEmpty(playUrl)) {
            playUrl = id;  // 最终兜底
        }

        // 构造headers
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/94.0.4606.54 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");

        return Result.get().url(playUrl).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // smali 中返回空结果
        return Result.string(new ArrayList<>());
    }
}