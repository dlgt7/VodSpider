package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 悦悦源爬虫实现。
 * 支持首页分类、搜索、详情页解析及播放地址获取。
 * API 响应采用 AES-CBC 加密，需解密后解析 JSON。
 */
public class YueYue extends Spider {

    // AES-CBC 加密密钥与 IV（来自 smali 静态字段 a/b）
    private static final byte[] AES_KEY = "aZ9$kU5%qI7=yC2=zH2#gM0@pX7^wF3a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV = "hY2&tN3]kF7,dL7=".getBytes(StandardCharsets.UTF_8);

    // API 基础地址与默认配置
    private static final String DEFAULT_API_URL = "https://u.yyxdmn.com/api";
    private static final String DEFAULT_CHANNEL_CODE = "ltsp_sp02";
    private static final String DEFAULT_APP_ID = "lantianshipin";
    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 11; M2012K10C Build/RP1A.200720.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/87.0.4280.141 Mobile Safari/537.36";

    // 签名生成相关常量
    private static final String SIGN_PREFIX = "zD9[bM4~sF4~uY2)";
    private static final String SIGN_FORMAT_PREFIX = "10-";
    private static final String SIGN_SEPARATOR = "-";

    // 请求头常量
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_BADCI = "Badci";
    private static final String USER_AGENT_MOZI = "Mozi";
    private static final String ACCEPT_ALL = "*/*";
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

    // API 端点
    private static final String API_INIT = "/new_public/init_v2";
    private static final String API_TYPE_LIST = "/new_type/list_v2";
    private static final String API_SEARCH_SCREEN = "/new_search/screen_v2";
    private static final String API_SEARCH_RESULT = "/new_search/result_v2";
    private static final String API_VIDEO_RESULT = "/new_video/result_v2";
    private static final String API_VIDEO_COLLECTION = "/new_video/collection_v2";

    // 鉴权字段（作为 HTTP 头部发送）
    private static final String AUTH_USER_AGENT = "User-Agent";
    private static final String AUTH_SYS_PLATFORM = "sys_platform";
    private static final String AUTH_DEVICE_ID = "device_id";
    private static final String AUTH_SYSRELEASE = "sysrelease";
    private static final String AUTH_SIGN = "sign";
    private static final String AUTH_CUR_TIME = "cur_time";
    private static final String AUTH_CHANNEL_CODE = "channel_code";
    private static final String AUTH_MOBMODEL = "mobmodel";
    private static final String AUTH_VERSION = "version";
    private static final String AUTH_TOKEN = "token";
    private static final String AUTH_LOG_HEADER = "log-header";
    private static final String AUTH_MOB_MFR = "mob_mfr";
    private static final String AUTH_PACKAGE_NAME = "package_name";
    private static final String AUTH_APP_ID = "app_id";
    private static final String AUTH_CONTENT_TYPE = "Content-Type";

    // 鉴权默认值
    private static final String VALUE_OKHTTP_UA = "okhttp/4.9.0";
    private static final String VALUE_SYS_PLATFORM = "2";
    private static final String VALUE_SYSRELEASE = "11";
    private static final String VALUE_MOBMODEL = "localhost";
    private static final String VALUE_VERSION = "50000";
    private static final String VALUE_LOG_HEADER = "I am the log request header.";
    private static final String VALUE_MOB_MFR = "Linux";
    private static final String VALUE_PACKAGE_NAME = "com.lightmemory.simon";

    // JSON 字段名
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_USER_INFO = "user_info";
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_APP_ID = "app_id";
    private static final String FIELD_CHANNEL_CODE = "channel_code";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ID = "id";
    private static final String FIELD_MSG = "msg";
    private static final String FIELD_REMARKS = "remarks";
    private static final String FIELD_VOD_ID = "vod_id";
    private static final String FIELD_VOD_NAME = "vod_name";
    private static final String FIELD_VOD_PIC = "vod_pic";
    private static final String FIELD_VOD_YEAR = "vod_year";
    private static final String FIELD_VOD_AREA = "vod_area";
    private static final String FIELD_VOD_TAG = "vod_tag";
    private static final String FIELD_VOD_ACTOR = "vod_actor";
    private static final String FIELD_VOD_DIRECTOR = "vod_director";
    private static final String FIELD_VOD_BLURB = "vod_blurb";
    private static final String FIELD_VOD_COLLECTION = "vod_collection";
    private static final String FIELD_VOD_TOKEN = "vod_token";
    private static final String FIELD_VOD_URL = "vod_url";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_CK = "ck";

    // 筛选项字段
    private static final String FILTER_AREA = "area";
    private static final String FILTER_SORT = "sort";
    private static final String FILTER_TYPE = "type";
    private static final String FILTER_YEAR = "year";
    private static final String FILTER_NAME_AREA = "地区";
    private static final String FILTER_NAME_SORT = "排序";
    private static final String FILTER_NAME_TYPE = "类型";
    private static final String FILTER_NAME_YEAR = "年份";
    private static final String FILTER_VALUE_ALL = "全部";

    // 播放地址分隔符
    private static final String PLAY_SEPARATOR = "|||";
    private static final String PLAY_SPLIT_REGEX = "\\|\\|\\|";
    private static final String EPISODE_SEPARATOR = "$";
    private static final String PLAYLIST_SEPARATOR = "#";

    // 代理 URL 模板
    private static final String PROXY_QUERY = "?do=yueyue&type=m3u8&domain=";
    private static final String PROXY_PATH = "&path=";
    private static final String PROXY_CK = "&ck=";
    private static final String URL_SCHEME_SEP = "://";
    private static final String URL_PORT_SEP = ":";

    // 错误消息
    private static final String ERR_DECRYPT_FAILED = "悦悦配置解密失败";
    private static final String ERR_REQUEST_FAILED = "悦悦请求失败 ";
    private static final String ERR_DETAIL_FAILED = "悦悦详情获取失败";
    private static final String ERR_PLAY_EMPTY = "悦悦播放地址为空";
    private static final String ERR_PLAY_PARAM = "悦悦播放参数错误";
    private static final String SOURCE_NAME = "悦悦";

    // 实例字段
    private final Object lock = new Object();
    private String apiUrl = DEFAULT_API_URL;
    private String token = "";
    private String deviceId = "";
    private String channelCode = DEFAULT_CHANNEL_CODE;
    private String appId = DEFAULT_APP_ID;
    private String signature = "";

    public YueYue() {
        super();
    }

    /**
     * 生成随机签名。基于当前时间戳和随机数构造特定格式字符串后 MD5 哈希。
     */
    private static String generateSignature() {
        long currentTime = System.currentTimeMillis();
        long millisPart = (currentTime % 1000L) * 1000L;
        int rand1 = new Random().nextInt(0x74143dff) + 0x5f5e100;
        int rand2 = new Random().nextInt(0x1f40) + 0x1b58;
        long nonceSum = millisPart + rand2;
        long seconds = currentTime / 1000L;

        StringBuilder sb = new StringBuilder();
        sb.append(SIGN_FORMAT_PREFIX).append(rand1).append(SIGN_SEPARATOR);
        sb.append(seconds).append(SIGN_SEPARATOR);
        sb.append(millisPart).append(SIGN_SEPARATOR);
        sb.append(seconds).append(SIGN_SEPARATOR);
        sb.append(nonceSum).append(SIGN_SEPARATOR);
        sb.append(seconds).append(SIGN_SEPARATOR);
        sb.append(nonceSum + rand2);

        return md5Hex(sb.toString());
    }

    /**
     * 计算字符串的 MD5 哈希，返回小写十六进制字符串。
     */
    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 返回第一个非空字符串，若为空则返回默认值。
     */
    private static String getOrDefault(String value, String defaultValue) {
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 规范化 URL：去除首尾空格和末尾多余的斜杠。
     */
    private static String normalizeUrl(String url) {
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 生成 16 位十六进制随机设备 ID。
     */
    private static String generateDeviceId() {
        byte[] bytes = new byte[8];
        new Random().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 构建鉴权头部（包含签名、设备信息、令牌等）。
     */
    private HashMap<String, String> buildAuthHeaders(String timestamp) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put(AUTH_USER_AGENT, VALUE_OKHTTP_UA);
        headers.put(AUTH_SYS_PLATFORM, VALUE_SYS_PLATFORM);
        headers.put(AUTH_DEVICE_ID, deviceId);
        headers.put(AUTH_SYSRELEASE, VALUE_SYSRELEASE);
        headers.put(AUTH_CUR_TIME, timestamp);
        headers.put(AUTH_CHANNEL_CODE, channelCode);
        headers.put(AUTH_MOBMODEL, VALUE_MOBMODEL);
        headers.put(AUTH_VERSION, VALUE_VERSION);
        headers.put(AUTH_TOKEN, token == null ? "" : token);
        headers.put(AUTH_LOG_HEADER, VALUE_LOG_HEADER);
        headers.put(AUTH_MOB_MFR, VALUE_MOB_MFR);
        headers.put(AUTH_PACKAGE_NAME, VALUE_PACKAGE_NAME);
        headers.put(AUTH_APP_ID, appId);
        headers.put(AUTH_CONTENT_TYPE, CONTENT_TYPE_FORM);

        // 计算签名：SIGN_PREFIX + deviceId + timestamp，MD5 后大写
        String signInput = SIGN_PREFIX + deviceId + timestamp;
        String sign = md5Hex(signInput).toUpperCase(Locale.US);
        headers.put(AUTH_SIGN, sign);

        return headers;
    }

    /**
     * 发起 API 请求并解密响应。
     *
     * @param path           API 路径（如 /new_public/init_v2）
     * @param params         业务参数（作为 POST body）
     * @param skipInitCheck  是否跳过初始化检查（init 调用自身时为 true 避免递归）
     * @return 解密后的 JSON 响应
     */
    private JSONObject fetchApi(String path, HashMap<String, String> params, boolean skipInitCheck) throws Exception {
        if (!skipInitCheck && TextUtils.isEmpty(signature)) {
            ensureInitialized();
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        HashMap<String, String> headers = buildAuthHeaders(timestamp);

        String url = apiUrl + path;
        String response = OkHttp.post(url, params, headers);

        if (TextUtils.isEmpty(response)) {
            throw new Exception(ERR_REQUEST_FAILED);
        }

        // AES-CBC 解密响应
        String decrypted = null;
        try {
            byte[] decoded = Base64.decode(response.trim(), Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            decrypted = new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败，decrypted 保持 null
        }

        if (TextUtils.isEmpty(decrypted)) {
            throw new Exception(ERR_DECRYPT_FAILED);
        }

        return new JSONObject(decrypted);
    }

    /**
     * 初始化令牌、应用 ID 和渠道码。线程安全，使用双重检查锁定。
     */
    private void ensureInitialized() {
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(signature)) {
            return;
        }
        synchronized (lock) {
            if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(signature)) {
                return;
            }
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put("invited_by", "");
                params.put("ua", DEFAULT_UA);
                params.put("is_install", "1");

                JSONObject result = fetchApi(API_INIT, params, true);
                JSONObject data = result.optJSONObject(FIELD_RESULT);
                if (data != null) {
                    data = data.optJSONObject(FIELD_USER_INFO);
                }
                if (data != null) {
                    String tokenValue = data.optString(FIELD_TOKEN);
                    if (!TextUtils.isEmpty(tokenValue)) {
                        token = tokenValue;
                    }
                    String appIdValue = data.optString(FIELD_APP_ID);
                    if (!TextUtils.isEmpty(appIdValue)) {
                        appId = appIdValue;
                    }
                    String channelValue = data.optString(FIELD_CHANNEL_CODE);
                    if (!TextUtils.isEmpty(channelValue)) {
                        channelCode = channelValue;
                    }
                }
            } catch (Exception e) {
                // 忽略初始化异常，仍生成签名
            }
            signature = generateSignature();
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) {
            String trimmed = extend.trim();
            if (trimmed.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(trimmed);
                    String url = json.optString("url", "");
                    if (!TextUtils.isEmpty(url)) {
                        apiUrl = normalizeUrl(url);
                    }
                } catch (Exception e) {
                    // 忽略 JSON 解析异常
                }
            } else if (trimmed.startsWith("http")) {
                apiUrl = normalizeUrl(trimmed);
            }
        }
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = generateDeviceId();
        }
        ensureInitialized();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ensureInitialized();
        try {
            JSONObject result = fetchApi(API_TYPE_LIST, new HashMap<>(), false);
            JSONArray items = result.optJSONArray(FIELD_RESULT);

            ArrayList<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString(FIELD_ID).trim();
                    String name = item.optString(FIELD_NAME).trim();
                    if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) continue;

                    classes.add(new Class(id, name));
                    JSONArray msgArr = item.optJSONArray(FIELD_MSG);
                    List<Filter> itemFilters = parseFilters(msgArr);
                    if (!itemFilters.isEmpty()) {
                        filters.put(id, itemFilters);
                    }
                }
            }
            return Result.string(classes, filters);
        } catch (Exception e) {
            return Result.string(new ArrayList<>(), new LinkedHashMap<>());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ensureInitialized();
        try {
            String cateId = tid;
            String area = "";
            String sort = "";
            String typeValue = "";
            String year = "";

            if (extend != null) {
                try {
                    String cateIdFromExtend = (String) extend.get("cateId");
                    if (!TextUtils.isEmpty(cateIdFromExtend)) {
                        cateId = cateIdFromExtend;
                    }
                    String classValue = (String) extend.get("class");
                    String typeStr = (String) extend.get("type");
                    typeValue = getOrDefault(classValue, getOrDefault(typeStr, ""));
                    area = getOrDefault((String) extend.get(FILTER_AREA), "");
                    year = getOrDefault((String) extend.get("year"), "");
                    sort = getOrDefault((String) extend.get(FILTER_SORT), "");
                } catch (Exception e) {
                    // 忽略参数解析异常，使用默认空值
                }
            }

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }
            if (page <= 0) page = 1;

            HashMap<String, String> params = new HashMap<>();
            params.put(FILTER_AREA, area);
            params.put(FILTER_SORT, sort);
            params.put(FILTER_TYPE, typeValue);
            params.put("year", year);
            params.put("pn", String.valueOf(page));
            params.put("type_id", getOrDefault(cateId, ""));

            JSONObject result = fetchApi(API_SEARCH_SCREEN, params, false);
            ArrayList<Vod> list = parseVideoList(result);

            return Result.get().page(page, page + 1, 20, 0).vod(list).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        ensureInitialized();
        String vodId = ids.get(0);

        HashMap<String, String> params = new HashMap<>();
        params.put("sig", "");
        params.put("nc_token", "");
        params.put("phone", "");
        params.put("session_id", "");
        params.put(FIELD_VOD_ID, vodId);

        JSONObject result = fetchApi(API_VIDEO_RESULT, params, false);
        JSONObject data = result.optJSONObject(FIELD_RESULT);
        if (data == null) {
            throw new Exception(ERR_DETAIL_FAILED);
        }

        Vod vod = new Vod(vodId, data.optString(FIELD_VOD_NAME), data.optString(FIELD_VOD_PIC));
        vod.setVodYear(data.optString(FIELD_VOD_YEAR));
        vod.setVodArea(data.optString(FIELD_VOD_AREA));
        vod.setTypeName(data.optString(FIELD_VOD_TAG));
        vod.setVodActor(data.optString(FIELD_VOD_ACTOR));
        vod.setVodDirector(data.optString(FIELD_VOD_DIRECTOR));

        String blurb = data.optString(FIELD_VOD_BLURB);
        if (TextUtils.isEmpty(blurb)) {
            blurb = "";
        } else {
            blurb = blurb.replaceAll("<[^>]*>", "").trim();
        }
        vod.setVodContent(blurb);

        JSONArray collection = data.optJSONArray(FIELD_VOD_COLLECTION);
        ArrayList<String> playList = new ArrayList<>();
        if (collection != null) {
            for (int i = 0; i < collection.length(); i++) {
                JSONObject item = collection.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString(FIELD_TITLE).trim();
                String itemId = item.optString(FIELD_ID).trim();
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(itemId)) continue;

                String playUrl = itemId + PLAY_SEPARATOR
                        + item.optString(FIELD_VOD_TOKEN) + PLAY_SEPARATOR
                        + item.optString(AUTH_CUR_TIME) + PLAY_SEPARATOR
                        + vodId;
                playList.add(title + EPISODE_SEPARATOR + playUrl);
            }
        }

        String playFrom = SOURCE_NAME;
        if (playList.isEmpty()) {
            playFrom = "";
        }
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl(TextUtils.join(PLAYLIST_SEPARATOR, playList));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        ensureInitialized();

        String[] parts = id.split(PLAY_SPLIT_REGEX);
        if (parts.length < 4) {
            throw new Exception(ERR_PLAY_PARAM);
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("sig", "");
        params.put("nc_token", "");
        params.put("phone", "");
        params.put("session_id", "");
        params.put("collection_id", parts[0]);
        params.put(FIELD_VOD_TOKEN, parts[1]);
        params.put(AUTH_CUR_TIME, parts[2]);
        params.put(FIELD_VOD_ID, parts[3]);

        JSONObject result = fetchApi(API_VIDEO_COLLECTION, params, false);
        JSONObject data = result.optJSONObject(FIELD_RESULT);
        if (data == null) {
            throw new Exception(ERR_PLAY_EMPTY);
        }

        String vodUrl = data.optString(FIELD_VOD_URL).trim();
        String ck = data.optString(FIELD_CK).trim();
        if (TextUtils.isEmpty(vodUrl) || TextUtils.isEmpty(ck)) {
            throw new Exception(ERR_PLAY_EMPTY);
        }

        // 构造代理 URL：proxyUrl + ?do=yueyue&type=m3u8&domain={base64(domain)}&path={base64(path)}&ck={urlEncode(ck)}
        URL url = new URL(vodUrl);
        StringBuilder domainBuilder = new StringBuilder();
        domainBuilder.append(url.getProtocol()).append(URL_SCHEME_SEP).append(url.getHost());
        if (url.getPort() > 0) {
            domainBuilder.append(URL_PORT_SEP).append(url.getPort());
        }
        String domain = domainBuilder.toString();
        String path = url.getPath();

        int base64Flag = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;

        StringBuilder proxyUrl = new StringBuilder();
        proxyUrl.append(Proxy.getUrl());
        proxyUrl.append(PROXY_QUERY);
        proxyUrl.append(Base64.encodeToString(domain.getBytes(StandardCharsets.UTF_8), base64Flag));
        proxyUrl.append(PROXY_PATH);
        proxyUrl.append(Base64.encodeToString(path.getBytes(StandardCharsets.UTF_8), base64Flag));
        proxyUrl.append(PROXY_CK);
        proxyUrl.append(URLEncoder.encode(ck, "UTF-8"));

        // 构造请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put(HEADER_USER_AGENT, USER_AGENT_MOZI);
        headers.put(HEADER_ACCEPT, ACCEPT_ALL);
        headers.put(HEADER_BADCI, TextUtils.isEmpty(signature) ? generateSignature() : signature);

        return Result.get().url(proxyUrl.toString()).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        ensureInitialized();
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }
        try {
            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }
            if (page <= 0) page = 1;

            HashMap<String, String> params = new HashMap<>();
            params.put("kw", key.trim());
            params.put("pn", String.valueOf(page));

            JSONObject result = fetchApi(API_SEARCH_RESULT, params, false);
            ArrayList<Vod> list = parseVideoList(result);

            return Result.get().page(page, page + 1, 20, 0).vod(list).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 解析筛选项配置。
     * 支持四种筛选项：area(地区)、sort(排序)、type(类型)、year(年份)。
     */
    private List<Filter> parseFilters(JSONArray items) {
        List<Filter> filters = new ArrayList<>();
        if (items == null) return filters;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString(FIELD_NAME);
            String key;
            String displayName;

            switch (name) {
                case FILTER_AREA:
                    key = FILTER_AREA;
                    displayName = FILTER_NAME_AREA;
                    break;
                case FILTER_SORT:
                    key = FILTER_SORT;
                    displayName = FILTER_NAME_SORT;
                    break;
                case FILTER_TYPE:
                    key = "class";
                    displayName = FILTER_NAME_TYPE;
                    break;
                case FILTER_YEAR:
                    key = FILTER_YEAR;
                    displayName = FILTER_NAME_YEAR;
                    break;
                default:
                    continue;
            }

            JSONArray dataArr = item.optJSONArray(FIELD_DATA);
            if (dataArr == null) continue;

            List<Filter.Value> values = new ArrayList<>();
            for (int j = 0; j < dataArr.length(); j++) {
                String value = String.valueOf(dataArr.opt(j)).trim();
                if (TextUtils.isEmpty(value)) continue;
                if (FILTER_VALUE_ALL.equals(value) || FILTER_NAME_SORT.equals(value)) continue;
                values.add(new Filter.Value(value, value));
            }
            if (!values.isEmpty()) {
                filters.add(new Filter(key, displayName, values));
            }
        }
        return filters;
    }

    /**
     * 解析视频列表。
     */
    private ArrayList<Vod> parseVideoList(JSONObject result) {
        ArrayList<Vod> list = new ArrayList<>();
        if (result == null) return list;

        JSONArray items = result.optJSONArray(FIELD_RESULT);
        if (items == null) return list;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String id = item.optString(FIELD_ID);
            String name = item.optString(FIELD_VOD_NAME);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) continue;

            String pic = item.optString(FIELD_VOD_PIC);
            String remarks = item.optString(FIELD_REMARKS);
            list.add(new Vod(id, name, pic, remarks));
        }
        return list;
    }
}
