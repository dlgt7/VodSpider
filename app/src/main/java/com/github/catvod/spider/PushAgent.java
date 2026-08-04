package com.github.catvod.spider;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.UrlQuerySanitizer;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Misc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PushAgent - 阿里云盘推送代理 Spider。
 * 支持阿里云盘分享链接解析、转码/原画播放、腾讯/芒果官源解析、磁力链接、直链嗅探。
 * Author: @SDL
 */
public class PushAgent extends Spider {

    /** Token 过期时间戳（秒） */
    private static long timeToken = 0;

    /** 访问令牌（"token_type access_token"） */
    private static String accessToken = "";

    /** 刷新令牌 */
    private static String refreshToken = "";

    /** share_token 缓存（share_id → share_token） */
    private static Map<String, String> shareToken = new HashMap<>();

    /** share_token 过期时间缓存（share_id → 过期时间戳秒） */
    private static Map<String, Long> shareExpires = new HashMap<>();

    /** 转码视频分片缓存（file_id → Map<media_id, url>），并发安全：代理入口 File/openFile/openselfFile 在 rLock 外写入 */
    private static final Map<String, Map<String, String>> videosMap = new ConcurrentHashMap<>();

    /** 同步锁 */
    private static final ReentrantLock rLock = new ReentrantLock();

    /** 阿里云盘分享链接正则（支持 alipan.com 和 aliyundrive.com） */
    public static Pattern regexAli = Pattern.compile("(https://www.(alipan|aliyundrive).com/s/[^\"]+)");

    /** 阿里云盘分享链接正则2（提取 share_id 和 file_id） */
    public static Pattern regexAliFolder = Pattern.compile("www.(alipan|aliyundrive).com/s/([^/]+)(/folder/([^/]+))?");

    /** PC 端 UA */
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";

    /** 默认视频格式匹配串 */
    private static final String VIDEO_FORMATS = ".m3u8#.mp4#.flv#video/tos#.mp3#.m4a#.wma";

    /** URL 过滤匹配串 */
    private static final String URL_FILTERS = "=http#.html#?http";

    // ==================== Open API / 双模式 Token 相关字段 ====================

    /** 令牌持久化存储（包名 + "_ali" 偏好文件） */
    private static SharedPreferences sharedPrefs;

    /** 公共访问令牌（Public 模式 authorization 值，格式 "token_type access_token"） */
    private static String publicAccessToken = "";

    /** 私有访问令牌（Private 模式 authorization 值） */
    private static String privateAccessToken = "";

    /** 缓存的 refresh_token（同时作为 SharedPreferences 读取默认值） */
    private static String cachedRefreshToken = "";

    /** Open API 签名前缀（"04" + Base64 编码的设备数据） */
    private static String openSignPrefix = "";

    /** Open API 签名后缀（128 位十六进制签名 + "01"） */
    private static String openSignSuffix = "";

    /** Open API 的 share_token（分享模式下使用） */
    private static String openShareToken = "";

    /** 设备 ID（从 token 刷新响应中获取） */
    private static String deviceId = "";

    /** 用户 ID（从 token 刷新响应中获取） */
    private static String userId = "";

    /** 头像 URL（从 token 刷新响应中获取） */
    private static String avatar = "";

    /** 默认 drive_id（从 token 刷新响应中获取） */
    private static String defaultDriveId = "";

    /** 本人网盘 drive_id（getSelfContent 中设置） */
    private static String selfDriveId = "";

    /** 资源 drive_id（Open API 分支读取，"null" 时回退） */
    private static String resdid = "";

    /** 存储的 headers 配置（hikerpush 中提取） */
    private static String storedHeaders = "";

    /** 存储的 format 配置（hikerpush 中提取） */
    private static String storedFormat = "false";

    /** 存储的字幕配置（hikerpush 中提取） */
    private static String storedSubtitle = "";

    /** true=使用公共访问令牌模式，false=使用私有访问令牌模式 */
    private static boolean usePublicToken = true;

    /** 账户令牌刷新就绪标志（门控 refreshAccountToken 网络请求） */
    private static boolean accountTokenReady = false;

    /** 分享模式标志（true=请求头包含 x-share-token） */
    private static boolean shareMode = false;

    /** 音频模式标志（playerContent 分支判断用） */
    private static boolean isAudioMode = false;

    // ==================== 常量字符串 ====================

    /** Open API 获取视频预览播放信息 URL */
    private static final String OPEN_VIDEO_PREVIEW_URL = "https://open.aliyundrive.com/adrive/v1.0/openFile/getVideoPreviewPlayInfo";

    /** Open API 获取下载 URL */
    private static final String OPEN_DOWNLOAD_URL = "https://open.aliyundrive.com/adrive/v1.0/openFile/getDownloadUrl";

    /** Open API 文件列表 URL */
    private static final String OPEN_FILE_LIST_URL = "https://open.aliyundrive.com/adrive/v1.0/openFile/list";

    /** 分享链接下载 URL */
    private static final String SHARE_DOWNLOAD_URL = "https://api.aliyundrive.com/v2/file/get_share_link_download_url";

    /** 分享链接视频预览 URL */
    private static final String SHARE_VIDEO_PREVIEW_URL = "https://api.aliyundrive.com/v2/file/get_share_link_video_preview_play_info";

    /** Token 刷新 URL */
    private static final String TOKEN_REFRESH_URL = "https://auth.aliyundrive.com/v2/account/token";

    /** batch 接口路径 */
    private static final String BATCH_URL = "https://api.aliyundrive.com/adrive/v2/batch";

    /** x-canary 请求头值 */
    private static final String X_CANARY_VALUE = "client=web,app=share,version=v2.3.1";

    /** Open API 默认 URL 过期时间（秒） */
    private static final String OPEN_URL_EXPIRE_SEC = "14400";

    /** SharedPreferences 公共访问令牌键名 */
    private static final String SP_PUBLIC_ACCESS_TOKEN = "PublicAccessTokenOpen";

    /** SharedPreferences 私有访问令牌键名 */
    private static final String SP_PRIVATE_ACCESS_TOKEN = "PrivateAccessTokenOpen";

    /** SharedPreferences 公共刷新令牌键名 */
    private static final String SP_PUBLIC_REFRESH_TOKEN = "PublicRefreshToken";

    /** SharedPreferences 私有刷新令牌键名 */
    private static final String SP_PRIVATE_REFRESH_TOKEN = "PrivateRefreshToken";

    /** SharedPreferences 公共默认 drive_id 键名 */
    private static final String SP_PUBLIC_DEFADID = "PublicDefadid";

    /** SharedPreferences 私有默认 drive_id 键名 */
    private static final String SP_PRIVATE_DEFADID = "PrivateDefadid";

    /** 转码清晰度优先级排序（从高到低） */
    private static final String[] TEMPLATE_PRIORITY = new String[]{"UHD", "QHD", "FHD", "HD", "SD", "LD"};

    /** m3u8 MIME 类型 */
    private static final String MIME_M3U8 = "application/vnd.apple.mpegurl";

    /** 视频流 MIME 类型 */
    private static final String MIME_VIDEO_TS = "video/MP2T";

    /** OSS 过期时间 URL 参数名 */
    private static final String OSS_EXPIRES_PARAM = "x-oss-expires";

    /** URL 过期检查阈值（60 秒） */
    private static final long URL_EXPIRE_THRESHOLD = 60;

    /** share_token 缓存有效期阈值（600 秒） */
    private static final long SHARE_TOKEN_THRESHOLD = 600;

    /** 默认推送封面图 */
    private static final String PUSH_COVER_URL = "https://github.moeyy.xyz/https://raw.githubusercontent.com/xyq254245/HikerRule/main/pushcover.png";

    /** 默认推送作者标识 */
    private static final String PUSH_AUTHOR = "香雅情";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        // 初始化 SharedPreferences（包名 + "_ali"）
        String prefsName = Init.context().getPackageName() + "_ali";
        sharedPrefs = context.getSharedPreferences(prefsName, 0);
        if (extend.startsWith("http")) {
            refreshToken = OkHttp.string(extend, null);
        } else {
            refreshToken = extend;
        }
        cachedRefreshToken = extend;
    }

    private static HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PC_UA);
        headers.put("Referer", "https://www.aliyundrive.com/");
        return headers;
    }

    private static HashMap<String, String> getHeaders2() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PC_UA);
        return headers;
    }

    /** 获取 Open API 请求头（含 authorization + x-canary + 可选 x-share-token） */
    private static HashMap<String, String> getOpenHeaders() {
        HashMap<String, String> headers = getHeaders();
        String authValue = usePublicToken ? publicAccessToken : privateAccessToken;
        headers.put("authorization", authValue);
        headers.put("x-canary", X_CANARY_VALUE);
        if (shareMode) {
            headers.put("x-share-token", openShareToken);
        }
        return headers;
    }

    /** 获取 Open API 基础请求头（从 SharedPreferences 读取令牌） */
    private static HashMap<String, String> getOpenHeadersBase() {
        HashMap<String, String> headers = getHeaders();
        String tokenKey = usePublicToken ? SP_PUBLIC_ACCESS_TOKEN : SP_PRIVATE_ACCESS_TOKEN;
        String authValue = sharedPrefs != null ? sharedPrefs.getString(tokenKey, "") : "";
        headers.put("authorization", authValue);
        return headers;
    }

    /** 按清晰度优先级（UHD>QHD>FHD>HD>SD>LD）从转码任务列表提取视频 URL */
    private static String getVideoUrlByClarity(JSONArray playList) throws JSONException {
        for (String clarity : TEMPLATE_PRIORITY) {
            for (int i = 0; i < playList.length(); i++) {
                JSONObject task = playList.getJSONObject(i);
                if (task.optString("template_id").equals(clarity)) {
                    return task.getString("url");
                }
            }
        }
        if (playList.length() > 0) {
            return playList.getJSONObject(0).getString("url");
        }
        return "";
    }

    /** 检查 URL 是否包含海阔/白云等特定推送标识 */
    public static boolean checkstring(String str) {
        return str.contains("m3u8.pw/Cache") || str.contains("from=https://banyung.pw") || str.contains("getm3u8?url=http");
    }

    /** 清理 URL 中的海阔控制标记（#ignoreImg=true# 等）并将全角分号转为半角 */
    public static String repl(String str) {
        if (!str.isEmpty()) {
            String[] markers = {"#ignoreImg=true#", "#ignoreVideo=true#", "#ignoreMusic=true#",
                    "#isVideo=true#", "#isMusic=true#", "#ignoreM3U8#", "#isM3u8#", "video://"};
            for (String marker : markers) {
                str = str.replace(marker, "");
            }
        }
        str = str.replaceAll("；；", ";");
        return str;
    }

    /** 将字节数格式化为人类可读的大小字符串（TB/GB/MB/KB） */
    public static String getSize(double size) {
        if (size == 0) return "";
        String format = "%.2f%s";
        if (size > 1099511627776L) {
            return String.format(Locale.getDefault(), format, size / 1099511627776L, "TB");
        } else if (size > 1073741824) {
            return String.format(Locale.getDefault(), format, size / 1073741824, "GB");
        } else if (size > 1048576) {
            return String.format(Locale.getDefault(), format, size / 1048576, "MB");
        } else {
            return String.format(Locale.getDefault(), format, size / 1024, "KB");
        }
    }

    private static String postJson(String url, String jsonStr, Map<String, String> headerMap) {
        return OkHttp.post(url, jsonStr, headerMap);
    }

    protected static long getTimeSys() {
        return System.currentTimeMillis() / 1000;
    }

    private static void getRefreshTk() {
        long timeSys = getTimeSys();
        if (accessToken.isEmpty() || timeToken - timeSys <= 600) {
            try {
                JSONObject json = new JSONObject();
                json.put("refresh_token", refreshToken);
                json.put("grant_type", "refresh_token");
                JSONObject response = new JSONObject(postJson("https://auth.aliyundrive.com/v2/account/token", json.toString(), getHeaders()));
                accessToken = response.getString("token_type") + " " + response.getString("access_token");
                timeToken = response.getLong("expires_in") + timeSys;
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
    }

    private static synchronized String getShareTk(String shareId, String sharePwd) {
        synchronized (PushAgent.class) {
            try {
                long timeSys = getTimeSys();
                String token = shareToken.get(shareId);
                Long expires = shareExpires.get(shareId);
                if (!TextUtils.isEmpty(token) && expires - timeSys > 600) {
                    return token;
                }
                JSONObject json = new JSONObject();
                json.put("share_id", shareId);
                json.put("share_pwd", sharePwd);
                JSONObject response = new JSONObject(postJson("https://api.aliyundrive.com/v2/share_link/get_share_token", json.toString(), getHeaders()));
                String string = response.getString("share_token");
                shareExpires.put(shareId, timeSys + response.getLong("expires_in"));
                shareToken.put(shareId, string);
                return string;
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }
    }

    public static Object[] loadsub(String url) {
        try {
            return new Object[]{200, "application/octet-stream", new ByteArrayInputStream(OkHttp.string(url, getHeaders()).getBytes())};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public static Object[] File(Map<String, String> params) {
        try {
            String shareId = params.get("share_id");
            return new Object[]{200, "application/octet-stream", new ByteArrayInputStream(getVideoUrl(shareId, getShareTk(shareId, ""), params.get("file_id")).getBytes())};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public static Object[] ProxyMedia(Map<String, String> params) {
        try {
            String shareId = params.get("share_id");
            String fileId = params.get("file_id");
            String mediaId = params.get("media_id");
            String shareToken = getShareTk(shareId, "");
            rLock.lock();
            String url = videosMap.get(fileId).get(mediaId);
            if (Long.parseLong(new UrlQuerySanitizer(url).getValue("x-oss-expires")) - getTimeSys() <= 60) {
                getVideoUrl(shareId, shareToken, fileId);
                url = videosMap.get(fileId).get(mediaId);
            }
            rLock.unlock();
            okhttp3.Response response = OkHttp.newCall(url, getHeaders());
            return new Object[]{200, "video/MP2T", response.body().byteStream()};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public static Object[] vod(Map<String, String> map) {
        String type = map.get("type");
        if (type.equals("m3u8")) {
            return File(map);
        }
        if (type.equals("media")) {
            return ProxyMedia(map);
        }
        return null;
    }

    private static String getVideoUrl(String shareId, String shareToken, String fileId) {
        try {
            getRefreshTk();
            JSONObject json = new JSONObject();
            json.put("share_id", shareId);
            json.put("category", "live_transcoding");
            json.put("file_id", fileId);
            json.put("template_id", "");
            HashMap<String, String> headers = getHeaders();
            headers.put("x-share-token", shareToken);
            headers.put("authorization", accessToken);
            JSONObject jSONObject3 = new JSONObject(postJson("https://api.aliyundrive.com/v2/file/get_share_link_video_preview_play_info", json.toString(), headers));
            JSONArray playList = jSONObject3.getJSONObject("video_preview_play_info").getJSONArray("live_transcoding_task_list");
            String videoUrl = "";
            String[] orders = new String[]{"FHD", "HD", "SD"};
            for (String or : orders) {
                for (int i = 0; i < playList.length(); i++) {
                    JSONObject obj = playList.getJSONObject(i);
                    if (obj.optString("template_id").equals(or)) {
                        videoUrl = obj.getString("url");
                        break;
                    }
                }
                if (!videoUrl.isEmpty()) break;
            }
            if (videoUrl.isEmpty() && playList.length() > 0) {
                videoUrl = playList.getJSONObject(0).getString("url");
            }
            okhttp3.Response response = OkHttp.newCall(videoUrl, getHeaders());
            String url = response.request().url().toString();
            String medias = response.body().string();
            String site = url.substring(0, url.lastIndexOf("/")) + "/";
            ArrayList<String> lists = new ArrayList<>();
            Map<String, String> video = new HashMap<>();
            String[] split = medias.split("\n");
            int j = 0;
            for (String vod : split) {
                if (vod.contains("x-oss-expires")) {
                    j++;
                    video.put("" + j, site + vod);
                    vod = Proxy.getUrl() + "?do=ali&type=media&share_id=" + shareId + "&file_id=" + fileId + "&media_id=" + j;
                }
                lists.add(vod);
            }
            videosMap.put(fileId, video);
            return TextUtils.join("\n", lists);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private static String getOriginalVideoUrl(String shareId, String shareToken, String fileId, String category) {
        try {
            getRefreshTk();
            HashMap<String, String> headers = getHeaders();
            headers.put("x-share-token", shareToken);
            headers.put("authorization", accessToken);
            if (category.equals("video")) {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("share_id", shareId);
                jSONObject.put("category", "live_transcoding");
                jSONObject.put("file_id", fileId);
                jSONObject.put("template_id", "");
                JSONObject jSONObject2 = new JSONObject(postJson("https://api.aliyundrive.com/v2/file/get_share_link_video_preview_play_info", jSONObject.toString(), headers));
                shareId = jSONObject2.getString("share_id");
                fileId = jSONObject2.getString("file_id");
            }
            JSONObject jSONObject3 = new JSONObject();
            if (category.equals("video")) {
                jSONObject3.put("expire_sec", 600);
                jSONObject3.put("file_id", fileId);
                jSONObject3.put("share_id", shareId);
            }
            if (category.equals("audio")) {
                jSONObject3.put("share_id", shareId);
                jSONObject3.put("get_audio_play_info", true);
                jSONObject3.put("file_id", fileId);
            }
            return new JSONObject(postJson("https://api.aliyundrive.com/v2/file/get_share_link_download_url", jSONObject3.toString(), headers)).getString("download_url");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 递归列举分享链接下的视频/音频文件及字幕，收集到 map 与 subList 中。
     *
     * @param map        视频/音频结果表：key="name [size]"，value="shareId+shareToken+fileId+category"
     * @param subList    字幕累加器：元素格式 "nameWithoutExt@@@file_extension@@@file_id"
     * @param shareId    分享 ID
     * @param shareToken 分享令牌
     * @param fileId     起始文件夹 ID（"root" 表示根）
     */
    public void listFiles(Map<String, String> map, ArrayList<String> subList,
                          String shareId, String shareToken, String fileId) {
        try {
            String url = "https://api.aliyundrive.com/adrive/v2/file/list_by_share";
            HashMap<String, String> headers = getHeaders();
            headers.put("x-share-token", shareToken);
            headers.put("x-canary", X_CANARY_VALUE);
            JSONObject json = new JSONObject();
            json.put("image_thumbnail_process", "image/resize,w_160/format,jpeg");
            json.put("image_url_process", "image/resize,w_1920/format,jpeg");
            json.put("limit", 100);
            json.put("order_by", "name");
            json.put("order_direction", "ASC");
            json.put("parent_file_id", fileId);
            json.put("share_id", shareId);
            json.put("video_thumbnail_process", "video/snapshot,t_1000,f_jpg,ar_auto,w_300");
            String marker = "";
            ArrayList<String> subFolders = new ArrayList<>();
            for (int i = 1; i <= 50; i++) {
                if (i > 1 && TextUtils.isEmpty(marker)) break;
                json.put("marker", marker);
                String resp = postJson(url, json.toString(), headers);
                if (!resp.contains("file_id")) break;
                JSONObject data = new JSONObject(resp);
                JSONArray items = data.getJSONArray("items");
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j);
                    if (item.getString("type").equals("folder")) {
                        subFolders.add(item.getString("file_id"));
                        continue;
                    }
                    String category = item.optString("category", "");
                    if (category.equals("video") || category.equals("audio")) {
                        String displayName = item.getString("name").replace("#", "_").replace("$", "_")
                                + " [" + getSize(item.optDouble("size", 0)) + "]";
                        map.put(displayName, shareId + "+" + shareToken + "+" + item.getString("file_id") + "+" + category);
                    }
                    // 字幕收集
                    String lowerName = item.getString("name").toLowerCase();
                    if (lowerName.endsWith(".srt") || lowerName.endsWith(".ass")
                            || lowerName.endsWith(".stl") || lowerName.endsWith(".ttml")
                            || lowerName.endsWith(".scc")) {
                        String fullName = item.getString("name");
                        String baseName = fullName.substring(0, fullName.lastIndexOf("."))
                                .replace("#", "_").replace("$", "_");
                        subList.add(baseName + "@@@" + item.optString("file_extension", "") + "@@@" + item.getString("file_id"));
                    }
                }
                marker = data.optString("next_marker", "");
            }
            for (String subFolderId : subFolders) {
                try {
                    listFiles(map, subList, shareId, shareToken, subFolderId);
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 列举本人网盘指定文件夹下的视频/音频及字幕（不递归子文件夹）。
     *
     * @param map     结果表：key="name [size]"，value="category|fileId"
     * @param subList 字幕累加器
     * @param driveId 网盘 drive_id
     * @param fileId  文件夹 ID（"root" 表示根）
     */
    public void listSelfFiles(Map<String, String> map, ArrayList<String> subList,
                              String driveId, String fileId) {
        try {
            JSONObject body = new JSONObject();
            body.put("drive_id", driveId);
            body.put("parent_file_id", fileId);
            body.put("limit", 100);
            body.put("all", true);
            body.put("url_expire_sec", Integer.parseInt(OPEN_URL_EXPIRE_SEC));
            body.put("image_thumbnail_process", "image/resize,w_160/format,jpeg");
            body.put("image_url_process", "image/resize,w_1920/format,jpeg");
            body.put("video_thumbnail_process", "video/snapshot,t_1000,f_jpg,ar_auto,w_300");
            body.put("fields", "*");
            body.put("order_by", "name");
            body.put("order_direction", "ASC");
            String marker = "";
            for (int i = 1; i <= 50; i++) {
                if (i > 1 && TextUtils.isEmpty(marker)) break;
                body.put("marker", marker);
                String resp = OkHttp.post(OPEN_FILE_LIST_URL, body.toString(), getOpenHeaders());
                if (!resp.contains("file_id")) break;
                JSONObject data = new JSONObject(resp);
                JSONArray items = data.getJSONArray("items");
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j);
                    if (item.getString("type").equals("folder")) continue;
                    String category = item.optString("category", "");
                    if (category.equals("video") || category.equals("audio")) {
                        String displayName = item.getString("name").replace("#", "_").replace("$", "_")
                                + " [" + getSize(item.optDouble("size", 0)) + "]";
                        map.put(displayName, category + "|" + item.getString("file_id"));
                    }
                    String lowerName = item.getString("name").toLowerCase();
                    if (lowerName.endsWith(".srt") || lowerName.endsWith(".ass")
                            || lowerName.endsWith(".stl") || lowerName.endsWith(".ttml")
                            || lowerName.endsWith(".scc")) {
                        String fullName = item.getString("name");
                        String baseName = fullName.substring(0, fullName.lastIndexOf("."))
                                .replace("#", "_").replace("$", "_");
                        subList.add(baseName + "@@@" + item.optString("file_extension", "") + "@@@" + item.getString("file_id"));
                    }
                }
                marker = data.optString("next_marker", "");
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String url = ids.get(0);
            if (Misc.isVip(url) && !url.contains("qq.com") && !url.contains("mgtv.com")) {
                JSONObject result = new JSONObject();
                JSONArray list = new JSONArray();
                JSONObject vodAtom = new JSONObject();
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", url);
                vodAtom.put("vod_pic", "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg");
                vodAtom.put("type_name", "官源");
                vodAtom.put("vod_year", "");
                vodAtom.put("vod_area", "");
                vodAtom.put("vod_remarks", "");
                vodAtom.put("vod_actor", "");
                vodAtom.put("vod_director", "");
                vodAtom.put("vod_content", "");
                vodAtom.put("vod_play_from", "jx");
                vodAtom.put("vod_play_url", "立即播放$" + url);
                list.put(vodAtom);
                result.put("list", list);
                return result.toString();
            } else if (Misc.isVip(url) && url.contains("qq.com")) {
                List<String> vodItems = new ArrayList<>();
                JSONObject result = new JSONObject();
                JSONArray lists = new JSONArray();
                JSONObject vodAtom = new JSONObject();
                Document doc = Jsoup.parse(OkHttp.string(url, getHeaders2()));
                String VodName = doc.select("head > title").text();
                Elements playListA = doc.select("div.episode-list-rect__item");
                if (!playListA.isEmpty()) {
                    for (int j = 0; j < playListA.size(); j++) {
                        Element vod = playListA.get(j);
                        String a = vod.select("div").attr("data-vid");
                        String b = vod.select("div").attr("data-cid");
                        String id = "https://v.qq.com/x/cover/" + b + "/" + a;
                        String name = vod.select("div span").text();
                        vodItems.add(name + "$" + id);
                    }
                    String playList = TextUtils.join("#", vodItems);
                    vodAtom.put("vod_play_url", playList);
                } else {
                    vodAtom.put("vod_play_url", "立即播放$" + url);
                }
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", VodName);
                vodAtom.put("vod_pic", "https://img2.baidu.com/it/u=2655029475,2190949369&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=593");
                vodAtom.put("type_name", "腾讯视频");
                vodAtom.put("vod_year", "");
                vodAtom.put("vod_area", "");
                vodAtom.put("vod_remarks", "");
                vodAtom.put("vod_actor", "");
                vodAtom.put("vod_director", "");
                vodAtom.put("vod_content", url);
                vodAtom.put("vod_play_from", "jx");
                lists.put(vodAtom);
                result.put("list", lists);
                return result.toString();
            } else if (Misc.isVip(url) && url.contains("mgtv.com")) {
                List<String> vodItems = new ArrayList<>();
                JSONObject result = new JSONObject();
                JSONArray lists = new JSONArray();
                JSONObject vodAtom = new JSONObject();
                Pattern mgtv = Pattern.compile("https://\\S+mgtv.com/b/(\\d+)/(\\d+).html.*");
                Matcher mgtv1 = mgtv.matcher(url);
                String VodNames = "";
                if (mgtv1.find()) {
                    String Ep = "https://pcweb.api.mgtv.com/episode/list?video_id=" + mgtv1.group(2);
                    JSONObject Data = new JSONObject(OkHttp.string(Ep, getHeaders2()));
                    VodNames = Data.getJSONObject("data").getJSONObject("info").getString("title");
                    JSONArray a = new JSONArray(Data.getJSONObject("data").getString("list"));
                    if (a.length() > 0) {
                        for (int i = 0; i < a.length(); i++) {
                            JSONObject jObj = a.getJSONObject(i);
                            if (jObj.getString("isIntact").equals("1")) {
                                String VodName = jObj.getString("t4");
                                String id = jObj.getString("video_id");
                                String VodId = "https://www.mgtv.com/b/" + mgtv1.group(1) + "/" + id + ".html";
                                vodItems.add(VodName + "$" + VodId);
                            }
                        }
                        String playList = TextUtils.join("#", vodItems);
                        vodAtom.put("vod_play_url", playList);
                    } else {
                        vodAtom.put("vod_play_url", "立即播放$" + url);
                    }
                }
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", VodNames);
                vodAtom.put("vod_pic", "https://img2.baidu.com/it/u=2562822927,704100654&fm=253&fmt=auto&app=138&f=JPEG?w=600&h=380");
                vodAtom.put("type_name", "芒果视频");
                vodAtom.put("vod_year", "");
                vodAtom.put("vod_area", "");
                vodAtom.put("vod_remarks", "");
                vodAtom.put("vod_actor", "");
                vodAtom.put("vod_director", "");
                vodAtom.put("vod_content", url);
                vodAtom.put("vod_play_from", "jx");
                lists.put(vodAtom);
                result.put("list", lists);
                return result.toString();
            } else if (Misc.isVideoFormat(url)) {
                JSONObject result = new JSONObject();
                JSONArray list = new JSONArray();
                JSONObject vodAtom = new JSONObject();
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", url);
                vodAtom.put("vod_pic", "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg");
                vodAtom.put("type_name", "直连");
                vodAtom.put("vod_play_from", "player");
                vodAtom.put("vod_play_url", "立即播放$" + url);
                list.put(vodAtom);
                result.put("list", list);
                return result.toString();
            } else if (url.startsWith("magnet:")) {
                String name = "";
                Matcher matcher = Pattern.compile("(^|&)dn=([^&]*)(&|$)").matcher(URLDecoder.decode(url));
                if (matcher.find()) {
                    name = matcher.group(2);
                }
                JSONObject result = new JSONObject();
                JSONArray list = new JSONArray();
                JSONObject vodAtom = new JSONObject();
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", !name.isEmpty() ? name : url);
                vodAtom.put("vod_pic", "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg");
                vodAtom.put("type_name", "磁力链接");
                vodAtom.put("vod_content", url);
                vodAtom.put("vod_play_from", "magnet");
                vodAtom.put("vod_play_url", "立即播放$" + url);
                list.put(vodAtom);
                result.put("list", list);
                return result.toString();
            } else if (regexAli.matcher(url).find()) {
                Matcher matcher = regexAliFolder.matcher(url);
                if (!matcher.find()) {
                    return "";
                }
                String shareId = matcher.group(1);
                String fileId = matcher.groupCount() == 3 ? matcher.group(3) : "";
                JSONObject json = new JSONObject();
                json.put("share_id", shareId);
                JSONObject shareLinkJson = new JSONObject(postJson("https://api.aliyundrive.com/adrive/v3/share_link/get_share_by_anonymous", json.toString(), getHeaders()));
                JSONArray fileInfoLists = shareLinkJson.getJSONArray("file_infos");
                if (fileInfoLists.length() == 0) {
                    return "";
                }
                JSONObject fileInfo;
                if (!TextUtils.isEmpty(fileId)) {
                    fileInfo = null;
                    for (int i = 0; i < fileInfoLists.length(); i++) {
                        JSONObject item = fileInfoLists.getJSONObject(i);
                        if (item.getString("file_id").equals(fileId)) {
                            fileInfo = item;
                            break;
                        }
                    }
                    if (fileInfo == null) return "";
                } else {
                    fileInfo = fileInfoLists.getJSONObject(0);
                    fileId = fileInfo.getString("file_id");
                }
                JSONObject vodAtom = new JSONObject();
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", shareLinkJson.getString("share_name"));
                vodAtom.put("vod_pic", shareLinkJson.getString("avatar"));
                vodAtom.put("vod_content", url);
                vodAtom.put("type_name", "阿里云盘");
                ArrayList<String> vodItems = new ArrayList<>();
                if (!fileInfo.getString("type").equals("folder")) {
                    if (!fileInfo.getString("type").equals("file") || !fileInfo.getString("category").equals("video")) {
                        return "";
                    }
                    fileId = "root";
                }
                String shareTk = getShareTk(shareId, "");
                Map<String, String> hashMap = new LinkedHashMap<>();
                ArrayList<String> subList = new ArrayList<>();
                listFiles(hashMap, subList, shareId, shareTk, fileId);
                ArrayList<String> arrayList2 = new ArrayList<>(hashMap.keySet());
                Collections.sort(arrayList2);
                for (String item : arrayList2) {
                    vodItems.add(item + "$" + hashMap.get(item));
                }
                if (!vodItems.isEmpty()) {
                    ArrayList<String> playLists = new ArrayList<>();
                    playLists.add(TextUtils.join("#", vodItems));
                    playLists.add(TextUtils.join("#", vodItems));
                    vodAtom.put("vod_play_url", TextUtils.join("$$$", playLists));
                    vodAtom.put("vod_play_from", "AliYun$$$AliYun原画");
                }
                JSONObject result = new JSONObject();
                JSONArray list = new JSONArray();
                list.put(vodAtom);
                result.put("list", list);
                return result.toString();
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                JSONObject result = new JSONObject();
                JSONArray list = new JSONArray();
                JSONObject vodAtom = new JSONObject();
                vodAtom.put("vod_id", url);
                vodAtom.put("vod_name", url);
                vodAtom.put("vod_pic", "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg");
                vodAtom.put("type_name", "网页");
                vodAtom.put("vod_play_from", "parse");
                vodAtom.put("vod_play_url", "立即播放$" + url);
                list.put(vodAtom);
                result.put("list", list);
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            if (flag.equals("jx")) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("jx", "1");
                result.put("url", id);
                return result.toString();
            } else if (flag.equals("parse")) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("playUrl", "");
                result.put("url", id);
                return result.toString();
            } else if (flag.equals("player") || flag.equals("magnet")) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", id);
                return result.toString();
            } else if (flag.equals("AliYun")) {
                String[] split = id.split("\\+");
                String videoUrl = Proxy.getUrl() + "?do=ali&type=m3u8&share_id=" + split[0] + "&file_id=" + split[2];
                JSONObject result = new JSONObject();
                result.put("parse", "0");
                result.put("playUrl", "");
                result.put("url", videoUrl);
                result.put("header", "");
                return result.toString();
            } else if (flag.equals("AliYun原画")) {
                String[] split = id.split("\\+");
                String url = getOriginalVideoUrl(split[0], split[1], split[2], split[3]);
                okhttp3.Response response = OkHttp.newCall(url, getHeaders());
                String videoUrl = response.request().url().toString();
                JSONObject result = new JSONObject();
                result.put("parse", "0");
                result.put("playUrl", "");
                result.put("url", videoUrl);
                result.put("header", new JSONObject(getHeaders()).toString());
                return result.toString();
            } else if (flag.contains("阿里转码")) {
                // 阿里转码：使用 Open API 获取本人网盘转码播放地址
                String[] split = id.split("\\+");
                JSONObject result = new JSONObject();
                result.put("parse", "0");
                result.put("playUrl", "");
                result.put("header", new JSONObject(getHeaders()).toString());
                if (split[0].equals("audio")) {
                    String url = getselfDownloadUrl(split[1], defaultDriveId);
                    result.put("url", url);
                } else {
                    String videoUrl = Proxy.getUrl() + "?do=push&type=openselfm3u8&file_id=" + split[1] + "&drive_id=" + defaultDriveId + "&delefile=fale";
                    result.put("url", videoUrl);
                }
                return result.toString();
            } else if (flag.contains("Open原画") || flag.contains("Open转码")) {
                // Open原画/Open转码：使用 Open API 获取分享链接转码播放地址
                String[] split = id.split("\\+");
                JSONObject result = new JSONObject();
                result.put("parse", "0");
                result.put("playUrl", "");
                result.put("header", new JSONObject(getHeaders()).toString());
                if (isAudioMode) {
                    String url = getshareAudioUrl(split[2], split[0]);
                    result.put("url", url);
                } else {
                    String videoUrl = Proxy.getUrl() + "?do=push&type=m3u8&share_id=" + split[0] + "&file_id=" + split[2] + "&drive_id=" + defaultDriveId + "&delefile=fale";
                    result.put("url", videoUrl);
                }
                return result.toString();
            } else if (flag.contains("Ali转码")) {
                // Ali转码：使用分享链接 API 获取转码播放地址
                String[] split = id.split("\\+");
                JSONObject result = new JSONObject();
                result.put("parse", "0");
                result.put("playUrl", "");
                result.put("header", new JSONObject(getHeaders()).toString());
                if (isAudioMode) {
                    String url = getshareAudioUrl(split[1], split[0]);
                    result.put("url", url);
                } else {
                    String videoUrl = Proxy.getUrl() + "?do=push&type=m3u8&share_id=" + split[0] + "&file_id=" + split[2];
                    result.put("url", videoUrl);
                }
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String trim = key.trim();
            if (!regexAli.matcher(trim).find()) {
                return "";
            }
            JSONArray videos = new JSONArray();
            JSONObject v = new JSONObject();
            v.put("vod_id", trim);
            v.put("vod_name", trim);
            videos.put(v);
            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        if (url == null || url.isEmpty()) return false;
        if (url.startsWith("http://0.0") || url.startsWith("http://127.0") || url.startsWith("file://")) return false;
        String[] formats = VIDEO_FORMATS.split("#");
        for (String format : formats) {
            if (url.contains(format)) return true;
        }
        return false;
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    // ==================== 内容获取方法 ====================

    /**
     * 解析阿里云盘分享链接内容，构建详情 JSON。
     * 递归列举分享内全部视频/音频并匹配字幕，生成双源（Open原画/Open转码）播放串。
     *
     * @param ids 第 0 个元素为分享链接
     * @return 详情 JSON 字符串
     */
    public String getAliContent(List<String> ids) {
        try {
            String input = ids.get(0).trim();
            JSONObject inputJson = new JSONObject();
            if (input.startsWith("{") && input.endsWith("}") && input.contains("\"url")) {
                inputJson = new JSONObject(input);
                if (inputJson.has("url")) {
                    input = repl(inputJson.getString("url"));
                } else if (inputJson.has("urls")) {
                    input = repl(input);
                }
            }
            Matcher matcher = regexAliFolder.matcher(input);
            if (!matcher.find()) return "";
            String shareId = matcher.group(2);
            String fileId = matcher.groupCount() == 4 ? matcher.group(4) : "";

            HashMap<String, String> headers = getHeaders();
            headers.put("x-canary", X_CANARY_VALUE);
            JSONObject shareBody = new JSONObject();
            shareBody.put("share_id", shareId);
            String resp = postJson("https://api.aliyundrive.com/adrive/v3/share_link/get_share_by_anonymous",
                    shareBody.toString(), headers);
            if (resp.contains("share_link is forbidden")) {
                Init.show("文件违规，根据相关法律法规要求，该文件已禁止访问。");
            }
            if (resp.contains("share_link is cancelled") || resp.contains("share_link is expired")) {
                Init.show("来晚啦，该分享已失效。");
            }
            JSONObject shareJson = new JSONObject(resp);
            JSONArray fileInfos = shareJson.getJSONArray("file_infos");
            if (fileInfos.length() == 0) return "";

            JSONObject target = fileInfos.getJSONObject(0);
            if (TextUtils.isEmpty(fileId)) {
                fileId = target.getString("file_id");
            }
            if (target.getString("type").equals("file")) {
                fileId = "root";
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));
            vod.put("vod_name", shareJson.getString("share_name"));
            vod.put("vod_pic", shareJson.getString("avatar"));
            vod.put("vod_content", input);
            vod.put("type_name", "阿里云盘");

            Map<String, String> map = new LinkedHashMap<>();
            ArrayList<String> subList = new ArrayList<>();
            ArrayList<String> playList = new ArrayList<>();
            String shareTk = getShareTk(shareId, "");
            listFiles(map, subList, shareId, shareTk, fileId);
            // 顶层其余文件夹也一并列举
            for (int i = 1; i < fileInfos.length(); i++) {
                JSONObject obj = fileInfos.getJSONObject(i);
                if (obj.getString("type").equals("folder")) {
                    listFiles(map, subList, shareId, shareTk, obj.getString("file_id"));
                }
            }
            // 拼装播放串：name [size]$shareId+shareToken+fileId+category[+字幕]
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                for (String sub : subList) {
                    String subName = sub.split("@@@")[0];
                    if (key.contains(subName)) {
                        value = value + "+" + sub;
                        break;
                    }
                }
                playList.add(key + "$" + value);
            }
            if (!playList.isEmpty()) {
                ArrayList<String> playFrom = new ArrayList<>();
                playFrom.add(TextUtils.join("#", playList));
                playFrom.add(TextUtils.join("#", playList));
                vod.put("vod_play_url", TextUtils.join("$$$", playFrom));
                vod.put("vod_play_from", "Open原画$$$Open转码");
            }
            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(vod);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 解析本人阿里云盘文件夹内容，构建详情 JSON。
     * 强制 Private 模式，通过开放接口 listSelfFiles 列举文件，生成双源（阿里原画/阿里转码）播放串。
     *
     * @param ids 第 0 个元素为链接或 JSON
     * @return 详情 JSON 字符串
     */
    public String getSelfContent(List<String> ids) {
        try {
            String input = ids.get(0).trim();
            String vodName = "MY阿里云盘";
            JSONObject inputJson = new JSONObject();
            if (input.startsWith("{") && input.endsWith("}") && input.contains("\"url")) {
                inputJson = new JSONObject(input);
                if (inputJson.has("url")) {
                    input = repl(inputJson.getString("url"));
                } else if (inputJson.has("urls")) {
                    input = repl(input);
                }
                if (inputJson.has("name")) {
                    vodName = inputJson.getString("name");
                }
            }
            shareMode = false;
            usePublicToken = false;
            refreshAccountToken();
            String fileId = "root";
            if (input.contains("/drive/folder/")) {
                String[] parts = input.split("/folder/");
                if (parts.length > 1) fileId = parts[1];
            }
            if (inputJson.has("default_drive_id")) {
                selfDriveId = inputJson.getString("default_drive_id");
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));
            vod.put("vod_name", vodName);
            vod.put("vod_pic", avatar);
            vod.put("vod_content", input);
            vod.put("type_name", "阿里云盘");

            Map<String, String> map = new LinkedHashMap<>();
            ArrayList<String> subList = new ArrayList<>();
            ArrayList<String> playList = new ArrayList<>();
            listSelfFiles(map, subList, selfDriveId, fileId);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                for (String sub : subList) {
                    String subName = sub.split("@@@")[0];
                    if (key.contains(subName)) {
                        value = value + "|" + sub;
                        break;
                    }
                }
                playList.add(key + "$" + value);
            }
            if (!playList.isEmpty()) {
                ArrayList<String> playFrom = new ArrayList<>();
                playFrom.add(TextUtils.join("#", playList));
                playFrom.add(TextUtils.join("#", playList));
                vod.put("vod_play_url", TextUtils.join("$$$", playFrom));
                vod.put("vod_play_from", "阿里原画$$$阿里转码");
            }
            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(vod);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 下载 URL 方法 ====================

    /**
     * 获取下载链接（分享模式，复制文件后获取直链再删除）。
     *
     * @param shareId 分享 ID
     * @param fileId  文件 ID
     * @param driveId 目标 drive_id
     * @return 下载直链
     */
    public String getDownloadUrl(String shareId, String fileId, String driveId) {
        try {
            String newFileId = buildDownloadUrl(shareId, fileId, driveId);
            String downloadUrl = getselfDownloadUrl(newFileId, driveId);
            if (!TextUtils.isEmpty(newFileId)) {
                deleteFile(newFileId, driveId);
            }
            return downloadUrl;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 获取自建下载链接（通过 Open API getDownloadUrl）。
     *
     * @param fileId  文件 ID
     * @param driveId drive_id
     * @return 下载直链
     */
    public String getselfDownloadUrl(String fileId, String driveId) {
        try {
            String tokenKey = usePublicToken ? SP_PUBLIC_ACCESS_TOKEN : SP_PRIVATE_ACCESS_TOKEN;
            String token = sharedPrefs != null ? sharedPrefs.getString(tokenKey, "") : "";
            if (token.isEmpty()) {
                refreshAccountToken();
            }
            int retry = 0;
            String response;
            while (true) {
                JSONObject json = new JSONObject();
                json.put("file_id", fileId);
                json.put("drive_id", driveId);
                response = OkHttp.post(OPEN_DOWNLOAD_URL, json.toString(), getOpenHeaders());
                if (response.contains("\"url\":\"http")) {
                    break;
                }
                retry++;
                if (retry > 3) break;
            }
            return new JSONObject(response).getString("url");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 获取分享音频下载链接。
     *
     * @param fileId  文件 ID
     * @param shareId 分享 ID
     * @return 下载直链
     */
    public String getshareAudioUrl(String fileId, String shareId) {
        try {
            HashMap<String, String> headers = getHeaders();
            headers.put("x-share-token", getShareTk(shareId, ""));
            headers.put("authorization", usePublicToken ? publicAccessToken : privateAccessToken);
            JSONObject body = new JSONObject();
            body.put("file_id", fileId);
            body.put("get_audio_play_info", true);
            body.put("share_id", shareId);
            String response = postJson(SHARE_DOWNLOAD_URL, body.toString(), headers);
            return new JSONObject(response).getString("download_url");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 下载阿里字幕文件。
     *
     * @param shareId 分享 ID
     * @param fileId  字幕文件 ID
     * @return 字幕内容
     */
    public String downalisub(String shareId, String fileId) {
        try {
            HashMap<String, String> headers = getHeaders();
            headers.put("x-share-token", getShareTk(shareId, ""));
            headers.put("authorization", usePublicToken ? publicAccessToken : privateAccessToken);
            JSONObject body = new JSONObject();
            body.put("file_id", fileId);
            body.put("share_id", shareId);
            body.put("expire_sec", 14400);
            String response = postJson(SHARE_DOWNLOAD_URL, body.toString(), headers);
            String downloadUrl = new JSONObject(response).getString("download_url");
            return OkHttp.string(downloadUrl, getHeaders());
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== Token 刷新方法 ====================

    /**
     * 刷新阿里云盘账户访问令牌（支持 Public/Private 双模式）。
     * 仅当 accountTokenReady=true 时才真正发起网络刷新。
     */
    private static void refreshAccountToken() {
        long timeSys = getTimeSys();
        if (usePublicToken) {
            if (!publicAccessToken.isEmpty()) return;
        } else {
            if (!privateAccessToken.isEmpty() && timeToken - timeSys > 3600L) return;
        }
        if (!accountTokenReady) return;
        try {
            JSONObject body = new JSONObject();
            String tokenKey = usePublicToken ? SP_PUBLIC_REFRESH_TOKEN : SP_PRIVATE_REFRESH_TOKEN;
            String refreshTokenValue = sharedPrefs != null
                    ? sharedPrefs.getString(tokenKey, cachedRefreshToken).replaceAll("\r|\n", "").trim()
                    : cachedRefreshToken;
            body.put("refresh_token", refreshTokenValue);
            body.put("grant_type", "refresh_token");
            JSONObject resp = new JSONObject(postJson(TOKEN_REFRESH_URL, body.toString(), getHeaders()));
            if (!resp.has("refresh_token")) {
                cachedRefreshToken = "";
                accountTokenReady = false;
                if (sharedPrefs != null) {
                    sharedPrefs.edit().remove(tokenKey).apply();
                }
                if (usePublicToken) {
                    Init.show("Token失效，请使用海阔视界 alitoken验证 小程序推送设置Token后再使用。");
                } else {
                    Init.show("Token失效，请尝试重新使用海阔视界推送。");
                }
                return;
            }
            String tokenType = resp.getString("token_type");
            String accessTokenVal = resp.getString("access_token");
            if (usePublicToken) {
                publicAccessToken = tokenType + " " + accessTokenVal;
            } else {
                privateAccessToken = tokenType + " " + accessTokenVal;
            }
            timeToken = resp.getLong("expires_in") + timeSys;
            deviceId = resp.optString("device_id", "");
            userId = resp.optString("user_id", "");
            avatar = resp.optString("avatar", "");
            defaultDriveId = resp.optString("default_drive_id", "");
            cachedRefreshToken = resp.getString("refresh_token");
            if (sharedPrefs != null) {
                sharedPrefs.edit().putString(tokenKey, cachedRefreshToken).apply();
                String defadidKey = usePublicToken ? SP_PUBLIC_DEFADID : SP_PRIVATE_DEFADID;
                sharedPrefs.edit().putString(defadidKey, defaultDriveId).apply();
                if (!usePublicToken) {
                    sharedPrefs.edit().putString("user_id", userId).apply();
                }
            }
            // Public 模式额外持久化 alitoken.txt
            if (usePublicToken) {
                try {
                    File dir = new File(android.os.Environment.getExternalStorageDirectory(), "XYQTVBox");
                    if (!dir.exists()) dir.mkdirs();
                    File tokenFile = new File(dir, "alitoken.txt");
                    FileWriter writer = new FileWriter(tokenFile);
                    writer.write(cachedRefreshToken);
                    writer.flush();
                    writer.close();
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    // ==================== batch 操作方法 ====================

    /**
     * 构建 batch copy 请求，将分享文件复制到根目录，返回新 file_id。
     */
    private static String buildDownloadUrl(String shareId, String fileId, String driveId) throws Exception {
        String body = String.format(
                "{\"requests\":[{\"body\":{\"file_id\":\"%s\",\"share_id\":\"%s\","
                        + "\"auto_rename\":true,\"to_parent_file_id\":\"root\",\"to_drive_id\":\"%s\"},"
                        + "\"headers\":{\"Content-Type\":\"application/json\"},"
                        + "\"id\":\"0\",\"method\":\"POST\",\"url\":\"/file/copy\"}],"
                        + "\"resource\":\"file\"}",
                fileId, shareId, driveId);
        String response = postJson(BATCH_URL, body, getOpenHeaders());
        if (response.contains("exceeded the limit")) {
            if (sharedPrefs != null) {
                String tokenKey = usePublicToken ? SP_PUBLIC_REFRESH_TOKEN : SP_PRIVATE_REFRESH_TOKEN;
                String accessKey = usePublicToken ? SP_PUBLIC_ACCESS_TOKEN : SP_PRIVATE_ACCESS_TOKEN;
                sharedPrefs.edit().remove(tokenKey).apply();
                sharedPrefs.edit().remove(accessKey).apply();
            }
            Init.show("当前账号可用空间已满，请尝试清理网盘后重试。");
        }
        JSONObject json = new JSONObject(response);
        JSONArray responses = json.getJSONArray("responses");
        return responses.getJSONObject(0).getJSONObject("body").getString("file_id");
    }

    /**
     * 构建 batch delete 请求，删除临时复制的文件。
     */
    private static void deleteFile(String fileId, String driveId) {
        try {
            String body = String.format(
                    "{\"requests\":[{\"body\":{\"drive_id\":\"%s\",\"file_id\":\"%s\"},"
                            + "\"headers\":{\"Content-Type\":\"application/json\"},"
                            + "\"id\":\"%s\",\"method\":\"POST\",\"url\":\"/file/delete\"}],"
                            + "\"resource\":\"file\"}",
                    driveId, fileId, fileId);
            postJson(BATCH_URL, body, getOpenHeaders());
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    // ==================== 推送方法 ====================

    /**
     * 海阔网络推送方法。
     * 解析推送列表中的 JSON，提取 url/urls 字段，构建推送 VOD JSON。
     *
     * @param list 推送参数列表
     * @return 推送 VOD JSON 字符串
     */
    public String hikernetpush(List<String> list) {
        try {
            String originalUrl = list.get(0).trim();
            String processedUrl = originalUrl;
            if (originalUrl.startsWith("{") && originalUrl.endsWith("}") && originalUrl.contains("\"url")) {
                JSONObject json = new JSONObject(originalUrl);
                if (json.has("url")) {
                    processedUrl = repl(json.getString("url"));
                } else if (json.has("urls")) {
                    processedUrl = repl(originalUrl);
                }
            }
            JSONObject result = new JSONObject();
            JSONArray listArray = new JSONArray();
            JSONObject vod = new JSONObject();
            vod.put("vod_id", list.get(0));
            vod.put("vod_name", processedUrl.contains("/redirectPlayUrl") ? "海阔投屏直链" : "海阔网页投屏");
            vod.put("vod_pic", PUSH_COVER_URL);
            vod.put("type_name", "海阔视界投屏解析by香雅情");
            vod.put("vod_content", "使用说明，投屏新链接后请按播放界面的重播按钮刷新视频。");
            vod.put("vod_play_from", "海阔投屏");
            vod.put("vod_play_url", processedUrl);
            listArray.put(vod);
            result.put("list", listArray);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 海阔推送主方法。
     * 解析推送 JSON，支持阿里云盘链接、多播放源、headers 注入、token 管理等。
     *
     * @param list 推送参数列表
     * @return 推送 VOD JSON 字符串
     */
    public String hikerpush(List<String> list) {
        try {
            String originalUrl = list.get(0).trim();
            String playUrl = "";
            String vodName = PUSH_AUTHOR;
            String picUrl = PUSH_COVER_URL;
            String actor = "";
            String director = "";
            String content = "";
            String playFrom = "";

            JSONObject json = new JSONObject(originalUrl);
            if (json.has("url")) {
                playUrl = repl(json.getString("url"));
            } else if (json.has("urls")) {
                playUrl = repl(originalUrl);
            }

            // refresh_token 处理
            if (json.has("refresh_token")) {
                String token = json.optString("refresh_token", "");
                if (token.isEmpty()) token = cachedRefreshToken;
                cachedRefreshToken = token;
            }

            // 分发：阿里云盘分享链接
            if (playUrl.startsWith("http") && regexAli.matcher(playUrl).find()) {
                return getAliContent(list);
            }
            // 分发：阿里云盘个人网盘
            if (playUrl.startsWith("http") && playUrl.contains("www.aliyundrive.com/drive")) {
                return getSelfContent(list);
            }
            // 分发：海阔网络推送
            if (playUrl.contains(":52020") && !playUrl.contains("/redirectPlayUrl")) {
                return hikernetpush(list);
            }

            if (json.has("name")) vodName = json.getString("name");
            else if (json.has("title")) vodName = json.getString("title");
            if (json.has("pic")) picUrl = json.getString("pic");
            if (json.has("actor")) actor = json.getString("actor");
            if (json.has("director")) director = json.getString("director");
            if (json.has("content")) content = json.optString("content");
            if (json.has("headers")) {
                String headersStr = json.optString("headers");
                headersStr = headersStr.replaceAll("＆＆", "&").replaceAll("；；", ";");
                storedHeaders = headersStr;
            }
            if (json.has("format")) {
                String fmt = json.optString("format");
                if (!fmt.isEmpty()) storedFormat = fmt;
            }
            if (json.has("subtitle")) {
                storedSubtitle = json.getString("subtitle");
            }
            String fromStr = json.has("from") ? json.getString("from") : "";

            // @Referer= URL 拼接处理
            if (content.contains("@Referer=")) {
                String[] parts = content.split("@Referer=");
                if (parts.length > 1 && parts[1].startsWith("http")) {
                    content = Proxy.getUrl() + "?do=push&type=picproxy&site=" + parts[1] + "&pic=" + parts[0];
                } else {
                    content = parts[0];
                }
            }

            // 复杂播放地址解析
            if (playUrl.startsWith("{") && playUrl.endsWith("}")) {
                JSONObject urlJson = new JSONObject(playUrl);
                if (urlJson.has("urls")) {
                    JSONArray urlsArray = urlJson.getJSONArray("urls");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < urlsArray.length(); i++) {
                        String item = urlsArray.getString(i);
                        String urlPart = item;
                        if (item.contains("##")) {
                            String[] split = item.split("##");
                            if (split[0].startsWith("file://") || split[0].startsWith("http://127.0.") || split[0].startsWith("http://0.0")) {
                                urlPart = split[1];
                            } else {
                                urlPart = split[0];
                            }
                        }
                        urlPart = urlPart.replace("#", urlPart.contains("?") ? "&" : "?");
                        String episodeName = "线路" + (i + 1);
                        if (urlJson.has("names")) {
                            JSONArray namesArray = urlJson.getJSONArray("names");
                            if (i < namesArray.length()) {
                                episodeName = namesArray.getString(i).replaceAll("#", "_").replaceAll("\\$", "_");
                            }
                        }
                        if (i < 1) {
                            sb.append(episodeName).append("$").append("|").append(urlPart);
                        } else {
                            sb.append("#").append(episodeName).append("$").append(urlPart).append("$").append("|").append(urlPart);
                        }
                    }
                    playUrl = sb.toString();
                } else if (urlJson.has("url")) {
                    String urlStr = repl(urlJson.getString("url"));
                    if (urlStr.contains("##")) {
                        String[] split = urlStr.split("##");
                        if (split[0].startsWith("file://") || split[0].startsWith("http://127.0.") || split[0].startsWith("http://0.0")) {
                            urlStr = split[1];
                        } else {
                            urlStr = split[0];
                        }
                    }
                    urlStr = urlStr.replace("#", urlStr.contains("?") ? "&" : "?");
                    playUrl = "|" + urlStr;
                }
            } else if (playUrl.contains("##")) {
                String[] split = playUrl.split("##");
                if (split[0].startsWith("file://") || split[0].startsWith("http://127.0.") || split[0].startsWith("http://0.0")) {
                    playUrl = split[1];
                } else {
                    playUrl = split[0];
                }
                playUrl = playUrl.replace("#", playUrl.contains("?") ? "&" : "?");
            }

            // play_from 后处理
            if (!TextUtils.isEmpty(cachedRefreshToken)) {
                playFrom = "Open原画$$$Open转码";
                playUrl = playUrl + "$$$" + playUrl;
            } else {
                playFrom = fromStr;
            }

            JSONObject result = new JSONObject();
            JSONArray listArray = new JSONArray();
            JSONObject vod = new JSONObject();
            vod.put("vod_id", list.get(0));
            vod.put("vod_pic", picUrl);
            vod.put("vod_director", director);
            vod.put("vod_actor", actor);
            vod.put("vod_name", vodName);
            vod.put("vod_content", content);
            vod.put("type_name", "海阔视界推送by香雅情");
            if (playFrom.isEmpty()) playFrom = "香雅情定制";
            vod.put("vod_play_from", playFrom);
            vod.put("vod_play_url", playUrl);
            listArray.put(vod);
            result.put("list", listArray);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== Open API 骨架方法 ====================
    // 注意：Open API 签名依赖 merge/xyq0208 包下的加密辅助类，
    // 这些类在反编译输出中缺失，以下方法提供骨架实现。

    /**
     * Open API 代理媒体播放入口。
     * 根据 delefile 参数选择不同的播放路径。
     *
     * @param params 包含 share_id、media_id、drive_id 等参数
     * @return Object[]{200, "video/MP2T", InputStream} 或 null
     */
    public static Object[] ProxyopenMedia(Map<String, String> params) {
        try {
            String fileId = params.get("file_id");
            String mediaId = params.get("media_id");
            String driveId = params.get("drive_id");
            rLock.lock();
            String url = videosMap.get(fileId).get(mediaId);
            if (Long.parseLong(new UrlQuerySanitizer(url).getValue(OSS_EXPIRES_PARAM)) - getTimeSys() <= URL_EXPIRE_THRESHOLD) {
                getPreviewUrl(fileId, driveId, false);
                url = videosMap.get(fileId).get(mediaId);
            }
            rLock.unlock();
            okhttp3.Response response = OkHttp.newCall(url, getOpenHeaders());
            return new Object[]{200, MIME_VIDEO_TS, response.body().byteStream()};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * 获取 Open API 视频预览地址（分享模式）。
     *
     * @param orgFileId 原始文件 ID
     * @param driveId   驱动器 ID
     * @param fileId    文件 ID
     * @return m3u8 播放地址
     */
    public static String getOpenPreview(String orgFileId, String driveId, String fileId) {
        // TODO: Open API 签名依赖缺失，当前使用分享链接 API 回退
        return getPreviewUrl(fileId, driveId, "", "", true);
    }

    /**
     * 获取视频预览播放地址（5 参数完整版）。
     *
     * @param fileId    文件 ID
     * @param driveId   驱动器 ID
     * @param orgFileId 原始文件 ID
     * @param shareId   分享 ID
     * @param isShare   是否为分享模式
     * @return m3u8 播放地址
     */
    public static String getPreviewUrl(String fileId, String driveId, String orgFileId, String shareId, boolean isShare) {
        try {
            shareMode = isShare;
            String tokenKey = usePublicToken ? SP_PUBLIC_ACCESS_TOKEN : SP_PRIVATE_ACCESS_TOKEN;
            String token = sharedPrefs != null ? sharedPrefs.getString(tokenKey, "") : "";
            if (token.isEmpty()) {
                refreshAccountToken();
            }
            int retryCount = 0;
            String responseBody;
            do {
                JSONObject requestJson = new JSONObject();
                requestJson.put("file_id", fileId);
                requestJson.put("drive_id", driveId);
                requestJson.put("category", "live_transcoding");
                requestJson.put("url_expire_sec", OPEN_URL_EXPIRE_SEC);
                responseBody = OkHttp.post(OPEN_VIDEO_PREVIEW_URL, requestJson.toString(), getOpenHeaders());
                if (responseBody.contains("\"url\":\"http")) break;
                retryCount++;
            } while (retryCount <= 3);

            JSONObject responseJson = new JSONObject(responseBody);
            JSONObject previewInfo = responseJson.getJSONObject("video_preview_play_info");
            JSONArray taskList = previewInfo.getJSONArray("live_transcoding_task_list");
            String videoUrl = getVideoUrlByClarity(taskList);

            String m3u8Content = OkHttp.string(videoUrl, getOpenHeaders());
            String baseUrl = videoUrl.substring(0, videoUrl.lastIndexOf("/")) + "/";
            ArrayList<String> playList = new ArrayList<>();
            HashMap<String, String> mediaMap = new HashMap<>();
            String[] lines = m3u8Content.split("\n");
            int mediaIndex = 0;
            for (String line : lines) {
                if (line.contains(OSS_EXPIRES_PARAM)) {
                    mediaIndex++;
                    mediaMap.put("" + mediaIndex, baseUrl + line);
                    if (shareMode) {
                        line = Proxy.getUrl() + "?do=push&type=openmedia&drive_id=" + driveId
                                + "&share_id=" + shareId + "&orginfile_id=" + orgFileId
                                + "&media_id=" + mediaIndex + "&delefile=true";
                    } else {
                        line = Proxy.getUrl() + "?do=push&type=openmedia&drive_id=" + driveId
                                + "&media_id=" + mediaIndex + "&delefile=false";
                    }
                }
                playList.add(line);
            }
            if (shareMode) {
                videosMap.put(orgFileId, mediaMap);
            } else {
                videosMap.put(fileId, mediaMap);
            }
            return TextUtils.join("\n", playList);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 获取视频预览播放地址（3 参数简化版）。
     *
     * @param fileId  文件 ID
     * @param driveId 驱动器 ID
     * @param isShare 是否为分享模式
     * @return m3u8 播放地址
     */
    public static String getPreviewUrl(String fileId, String driveId, boolean isShare) {
        return getPreviewUrl(fileId, driveId, "", "", isShare);
    }

    /**
     * 打开文件并返回 m3u8 内容流。
     *
     * @param params 包含 share_id、file_id、drive_id
     * @return Object[]{200, "application/vnd.apple.mpegurl", ByteArrayInputStream} 或 null
     */
    public static Object[] openFile(Map<String, String> params) {
        try {
            String shareId = params.get("share_id");
            String fileId = params.get("file_id");
            String driveId = params.get("drive_id");
            String m3u8Content = getOpenPreview(fileId, driveId, shareId);
            return new Object[]{200, MIME_M3U8, new ByteArrayInputStream(m3u8Content.getBytes())};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * 打开自己的文件并返回 m3u8 内容流。
     *
     * @param params 包含 file_id、drive_id
     * @return Object[]{200, "application/vnd.apple.mpegurl", ByteArrayInputStream} 或 null
     */
    public static Object[] openselfFile(Map<String, String> params) {
        try {
            String fileId = params.get("file_id");
            String driveId = params.get("drive_id");
            String m3u8Content = getPreviewUrl(fileId, driveId, false);
            return new Object[]{200, MIME_M3U8, new ByteArrayInputStream(m3u8Content.getBytes())};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * 加载图片代理。
     *
     * @param params 包含 url 参数
     * @return Object[]{200, "image/*", ByteArrayInputStream} 或 null
     */
    public static Object[] loadPic(Map<String, String> params) {
        try {
            String url = params.get("url");
            byte[] data = OkHttp.bytes(url, getHeaders());
            return new Object[]{200, "image/jpeg", new ByteArrayInputStream(data)};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }
}
