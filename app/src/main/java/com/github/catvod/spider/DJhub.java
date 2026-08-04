package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 聚合短剧爬虫 (DJhub)
 * <p>
 * 聚合 12 个短剧平台：百度、甜圈、锦鲤、番茄、星芽、西饭、软鸭、七猫、牛牛、围观、碎片、河马。
 * 由 JavaScript 源 duanju_juhe.js 转换而来，遵循 CatVodSpider 项目规范。
 * <p>
 * 分类排除规则 (cate_remove)：分类排除、软鸭、碎片、锦鲤、番茄、甜圈 —— 这些平台在首页/分类/搜索中被过滤，
 * 但仍支持直接调用详情/播放。
 */
public class DJhub extends Spider {

    // ==================== 平台 ID 常量 ====================
    private static final String BAIDU = "百度";
    private static final String TIANQUAN = "甜圈";
    private static final String JINLI = "锦鲤";
    private static final String FANQIE = "番茄";
    private static final String XINGYA = "星芽";
    private static final String XIFAN = "西饭";
    private static final String RUANYA = "软鸭";
    private static final String QIMAO = "七猫";
    private static final String NIUNIU = "牛牛";
    private static final String WEIGUAN = "围观";
    private static final String SUIPIAN = "碎片";
    private static final String HEMA = "河马";

    // ==================== 加密密钥常量 ====================
    private static final String AGG_KEYS = "d3dGiJc651gSQ8w1";                   // 七猫 sign key
    private static final String NIUNIU_HMAC_KEY = "aceaa47f96b4875d446b2e1d97e03bbb";
    private static final String NIUNIU_AES_KEY = "dafdb3d2a5c343d6";             // login ECB key
    private static final String NIUNIU_AES_KEY2 = "ce49b18dd4e0a4d8";            // post ECB key
    private static final String HEMA_AES_KEY = "647a6b6a67667978677368796c677a6d"; // hex
    private static final String HEMA_AES_IV = "6170697570646f776e65646372797074";  // hex
    private static final String SUIPIAN_AES_KEY = "p0sfjw@k&qmewu#w";             // encHex ECB key

    // ==================== 分类排除规则 ====================
    private static final List<String> CATE_REMOVE = Arrays.asList("分类排除", "软鸭", "碎片", "锦鲤", "番茄", "甜圈");

    // ==================== 河马 headers 中的 datas 字段 ====================
    private static final String HEMA_DATAS = "e5f22c6e2c82fe001738cb9ce4696eab0556d064a55aef402e0fbe6b29a083f6538e4567de38e67de2071a49d9751526bfba45314e1fd4702b11c76ab9a3b5f873262854ba66e6715ed51364dbc6ee62c7180e047fcbcdbfd49874fc8f28674b16d90ca71a02de76c70598e0b75e647c37c2c19287e49be5f2a259d727dfc4df3d28802388bf3c356576b342e17e30a2ab74859263dba4d1c8eba79990d22d60d60927fdacb2addf2f0eaadd8887585ca2eb87f603faf0c207dda18cf67dc25b2199d303baff9e6605b3314a7d2631f62864f48619daceb9452f2b7b0667773553741856df030cca68af3c57810f983d452bb428ef5fc32206aef4865ae06c629bee7f5135547304acc7ef4e7c6df887308f2e79c493fd2ee03488722861b5bb51b09cb8911dfc92c288d94e601c066d2f9d612ad2c8d4eeb4920b1d44aff3e13fd75229b857f64925df1cf12f75a00d438c422ec1726462b915903f1dd1f4bb7cdf82cc15a6d507f80c789903e710f39a62aef073f3f93a6c681e75d295428aa290d7e98f82e7e9ad6e2b23d9086dfe8c63c5d8550b13fd61a77291473a8bdd43c7c2639f264be69d9d07f0585de4342a399275a64e7d1d4400b8ed4421a2f289f622e40cdd1cfc916a0b9ce747c924ac33e32d24b91ed5d64772d6ad6896412f52724006eabf12aaecfd6e81dad432c7b3800bbf793a1c375e3e7b4fb3b097724b5fc88a8c9bcf3dbc10cbdb252965";

    // ==================== 七猫字符映射表 (Base64 -> 自定义字符) ====================
    private static final Map<String, String> CHAR_MAP = new HashMap<String, String>() {{
        put("+", "P"); put("/", "X"); put("0", "M"); put("1", "U"); put("2", "l");
        put("3", "E"); put("4", "r"); put("5", "Y"); put("6", "W"); put("7", "b");
        put("8", "d"); put("9", "J"); put("A", "9"); put("B", "s"); put("C", "a");
        put("D", "I"); put("E", "0"); put("F", "o"); put("G", "y"); put("H", "_");
        put("I", "H"); put("J", "G"); put("K", "i"); put("L", "t"); put("M", "g");
        put("N", "N"); put("O", "A"); put("P", "8"); put("Q", "F"); put("R", "k");
        put("S", "3"); put("T", "h"); put("U", "f"); put("V", "R"); put("W", "q");
        put("X", "C"); put("Y", "4"); put("Z", "p"); put("a", "m"); put("b", "B");
        put("c", "O"); put("d", "u"); put("e", "c"); put("f", "6"); put("g", "K");
        put("h", "x"); put("i", "5"); put("j", "T"); put("k", "-"); put("l", "2");
        put("m", "z"); put("n", "S"); put("o", "Z"); put("p", "1"); put("q", "V");
        put("r", "v"); put("s", "j"); put("t", "Q"); put("u", "7"); put("v", "D");
        put("w", "w"); put("x", "n"); put("y", "L"); put("z", "e");
    }};

    // ==================== 平台列表 ====================
    private static final List<String[]> PLATFORM_LIST = new ArrayList<String[]>() {{
        add(new String[]{"锦鲤短剧", JINLI});
        add(new String[]{"番茄短剧", FANQIE});
        add(new String[]{"星芽短剧", XINGYA});
        add(new String[]{"西饭短剧", XIFAN});
        add(new String[]{"七猫短剧", QIMAO});
        add(new String[]{"甜圈短剧", TIANQUAN});
        add(new String[]{"牛牛短剧", NIUNIU});
        add(new String[]{"百度短剧", BAIDU});
        add(new String[]{"围观短剧", WEIGUAN});
        add(new String[]{"软鸭短剧", RUANYA});
        add(new String[]{"碎片剧场", SUIPIAN});
        add(new String[]{"河马短剧", HEMA});
    }};

    // ==================== 默认筛选值 ====================
    private static final Map<String, String> RULE_FILTER_DEF = new HashMap<String, String>() {{
        put(BAIDU, "新剧");
        put(TIANQUAN, "逆袭");
        put(JINLI, "");
        put(FANQIE, "videoseries_hot");
        put(XINGYA, "1");
        put(XIFAN, "");
        put(RUANYA, "战神");
        put(QIMAO, "0");
        put(NIUNIU, "现言");
        put(WEIGUAN, "");
        put(SUIPIAN, "");
        put(HEMA, "308");
    }};

    // ==================== 河马分类标签映射 ====================
    private static final Map<String, String> HEMA_TAG_IDS = new HashMap<String, String>() {{
        put("308", "");
        put("309", "");
        put("310", "417,473,474,464");
        put("311", "462,466");
        put("312", "476");
        put("313", "585,616");
        put("314", "444,468");
        put("315", "417,439,464,465");
        put("316", "589");
        put("317", "416,439,463,465");
        put("318", "438");
        put("319", "417,474,464");
        put("320", "439,442,443,445,465,470");
        put("321", "417,473,474,464");
        put("322", "472,475,585");
        put("323", "590");
    }};

    // ==================== 运行时 headers / token ====================
    private Map<String, String> xingyaHeaders = new HashMap<>();
    private Map<String, String> niuniuHeaders = new HashMap<>();
    private Map<String, String> hemaHeaders = new HashMap<>();
    private String niuniuAccessToken = "";

    // ==================== 默认 headers ====================
    private Map<String, String> defaultHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "okhttp/5.1.0");
        h.put("Content-Type", "application/json");
        return h;
    }

    private Map<String, String> baiduHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        h.put("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 9; 22081212C Build/PQ3B.190801.002) Talos/1.8.13 SP-engine/3.47.0 bd_dvt/1 baiduboxapp/15.21.0.10 (Baidu; P1 9)");
        return h;
    }

    private Map<String, String> niuniuBaseHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Cache-Control", "no-cache");
        h.put("Content-Type", "application/json;charset=UTF-8");
        h.put("User-Agent", "okhttp/4.12.0");
        return h;
    }

    // ==================== 初始化 ====================
    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        initXingya();
        initNiuniu();
        initHema();
    }

    /** 登录星芽平台，获取 token 并写入 xingyaHeaders */
    private void initXingya() {
        try {
            String loginUrl = "https://u.shytkjgs.com/user/v1/account/login";
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "okhttp/4.10.0");
            headers.put("platform", "1");
            headers.put("Content-Type", "application/json");
            JsonObject body = new JsonObject();
            body.addProperty("device", "24250683a3bdb3f118dff25ba4b1cba1a");
            String response = OkHttp.post(loginUrl, body.toString(), headers);
            JsonObject res = Json.safeObject(response);
            String token = "";
            JsonObject data = Json.getJsonObject(res, "data");
            if (data.has("token")) {
                token = Json.getString(data, "token");
            } else if (data.has("data")) {
                token = Json.getString(Json.getJsonObject(data, "data"), "token");
            } else if (res.has("token")) {
                token = Json.getString(res, "token");
            } else if (res.has("result")) {
                token = Json.getString(Json.getJsonObject(res, "result"), "token");
            } else if (res.has("access_token")) {
                token = Json.getString(res, "access_token");
            }
            xingyaHeaders = defaultHeaders();
            if (!token.isEmpty()) xingyaHeaders.put("authorization", token);
        } catch (Exception e) {
            SpiderDebug.log(e);
            xingyaHeaders = defaultHeaders();
        }
    }

    /** 登录牛牛平台：先获取 visitor token，再通过加密 body 换取 access_token */
    private void initNiuniu() {
        try {
            String visitorUrl = "https://new.tianjinzhitongdaohe.com" + "/api/v1/app/user/visitorInfo";
            Map<String, String> visitorHeaders = new HashMap<>();
            visitorHeaders.put("deviceid", "aa11fc54-ba9c-3980-add5-447d3fa5b939");
            visitorHeaders.put("token", "");
            visitorHeaders.put("User-Agent", "okhttp/4.12.0");
            visitorHeaders.put("client", "app");
            visitorHeaders.put("devicetype", "Android");
            String tkHtml = OkHttp.string(visitorUrl, visitorHeaders);
            JsonObject tkRes = Json.safeObject(tkHtml);
            String niuniuToken = Json.getString(Json.getJsonObject(tkRes, "data"), "token");

            String t = String.valueOf(System.currentTimeMillis() / 1000);
            String body = "ac=wifi&os=Android&vod_version=1.10.21.6-tob&os_version=9&type=1&clientVersion=v5.2.5&uuid=Y4WNZ3SAWK7MAJMH7CXCDHJ4VMPVFRZQTBSIA4XTYO4AWEUHIK6Q01&resolution=1280*2618&openudid=889edced38f1069b&dt=Pixel%204&sha1=46121F77CE2FCAD3DBC3B9EC8A24908C1A8AD6D9&os_api=28&install_id=1549688030634536&device_brand=google&sdk_version=1.1.3.0&package_name=com.niuniu.ztdh.app&siteid=5627189&dev_log_aid=667431&oaid=&timestamp=" + t;

            String nonce = "VX1KKGtoBDCi1fB1";
            String signature = Crypto.hmacSha256(t + nonce + body, NIUNIU_HMAC_KEY);
            String encBody = Crypto.aesEcbEncrypt(body, NIUNIU_AES_KEY);

            String loginUrl = "https://csj-sp.csjdeveloper.com/csj_sp/api/v1/user/login?siteid=5627189";
            Map<String, String> loginHeaders = new HashMap<>();
            loginHeaders.put("X-Salt", "786774955F");
            loginHeaders.put("X-Nonce", nonce);
            loginHeaders.put("X-Timestamp", t);
            loginHeaders.put("X-Signature", signature);
            loginHeaders.put("Content-Type", "application/x-www-form-urlencoded");
            String loginResp = postUrlEncoded(loginUrl, encBody, loginHeaders);
            String loginData = Crypto.aesEcbDecrypt(loginResp, NIUNIU_AES_KEY);
            JsonObject accessJson = Json.safeObject(loginData);
            niuniuAccessToken = Json.getString(Json.getJsonObject(accessJson, "data"), "access_token");

            niuniuHeaders = niuniuBaseHeaders();
            niuniuHeaders.put("token", niuniuToken);
            niuniuHeaders.put("deviceid", "aa11fc54-ba9c-3980-add5-447d3fa5b939");
        } catch (Exception e) {
            SpiderDebug.log(e);
            niuniuHeaders = niuniuBaseHeaders();
        }
    }

    /** 初始化河马 headers（包含 datas 字段） */
    private void initHema() {
        hemaHeaders = new HashMap<>();
        hemaHeaders.put("datas", HEMA_DATAS);
        hemaHeaders.put("content-type", "text/plain");
    }

    // ==================== 首页分类 ====================
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (String[] item : getPlatList()) {
            classes.add(new Class(item[1], item[0]));
        }
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        if (filter) {
            for (String[] item : getPlatList()) {
                List<Filter> f = buildFilter(item[1]);
                if (!f.isEmpty()) filters.put(item[1], f);
            }
        }
        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<String[]> plats = getPlatList();
        String[] randomPlat = plats.get((int) (Math.random() * plats.size()));
        String area = RULE_FILTER_DEF.getOrDefault(randomPlat[1], "");
        HashMap<String, String> extend = new HashMap<>();
        extend.put("area", area);
        String categoryResult = categoryContent(randomPlat[1], "1", false, extend);
        JsonObject json = Json.safeObject(categoryResult);
        JsonArray list = Json.getJsonArray(json, "list");
        JsonObject result = new JsonObject();
        result.add("list", list);
        return result.toString();
    }

    // ==================== 分类列表 ====================
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        List<Vod> videos = new ArrayList<>();
        if (isSkipPlat(tid)) {
            return Result.get().page(page, page + 1, 0, 0).vod(videos).string();
        }
        if (extend != null && extend.containsKey("custom") && !extend.get("custom").isEmpty()) {
            return searchContent(extend.get("custom"), false, pg);
        }
        String area = "";
        if (extend != null && extend.containsKey("area") && !Util.isEmpty(extend.get("area"))) {
            area = extend.get("area");
        } else if (RULE_FILTER_DEF.containsKey(tid)) {
            area = RULE_FILTER_DEF.get(tid);
        }
        try {
            switch (tid) {
                case BAIDU:
                    videos = baiduCategory(page, area);
                    break;
                case TIANQUAN:
                    videos = tianquanCategory(page, area);
                    break;
                case JINLI:
                    videos = jinliCategory(page, area);
                    break;
                case FANQIE:
                    videos = fanqieCategory(page, area);
                    break;
                case XINGYA:
                    videos = xingyaCategory(page, area);
                    break;
                case XIFAN:
                    videos = xifanCategory(page, area);
                    break;
                case RUANYA:
                    videos = ruanyaCategory(page, area);
                    break;
                case QIMAO:
                    videos = qimaoCategory(area);
                    break;
                case NIUNIU:
                    videos = niuniuCategory(page, area);
                    break;
                case WEIGUAN:
                    videos = weiguanCategory(page);
                    break;
                case SUIPIAN:
                    videos = suipianCategory(page);
                    break;
                case HEMA:
                    videos = hemaCategory(page, area);
                    break;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.get().page(page, page + 1, videos.size(), videos.size() * (page + 1)).vod(videos).string();
    }

    // ==================== 详情 ====================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String[] parts = id.split("@", 2);
        String platform = parts[0];
        String did = parts.length > 1 ? parts[1] : "";
        Vod vod = new Vod(id, "加载失败", "", "");
        try {
            switch (platform) {
                case BAIDU:
                    vod = baiduDetail(id, did);
                    break;
                case TIANQUAN:
                    vod = tianquanDetail(id, did);
                    break;
                case JINLI:
                    vod = jinliDetail(id, did);
                    break;
                case FANQIE:
                    vod = fanqieDetail(id, did);
                    break;
                case XINGYA:
                    vod = xingyaDetail(id, did);
                    break;
                case XIFAN:
                    vod = xifanDetail(id, did);
                    break;
                case RUANYA:
                    vod = ruanyaDetail(id, did);
                    break;
                case QIMAO:
                    vod = qimaoDetail(id, did);
                    break;
                case NIUNIU:
                    vod = niuniuDetail(id, did);
                    break;
                case WEIGUAN:
                    vod = weiguanDetail(id, did);
                    break;
                case SUIPIAN:
                    vod = suipianDetail(id, did);
                    break;
                case HEMA:
                    vod = hemaDetail(id, did);
                    break;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(vod);
    }

    // ==================== 播放 ====================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (Pattern.compile("百度").matcher(flag).find()) return baiduPlay(id);
        if (Pattern.compile("甜圈").matcher(flag).find()) return playUrl0("https://mov.cenguigui.cn/duanju/api.php?video_id=" + id + "&type=mp4");
        if (Pattern.compile("锦鲤").matcher(flag).find()) return jinliPlay(id);
        if (Pattern.compile("番茄").matcher(flag).find()) return fanqiePlay(id);
        if (Pattern.compile("软鸭").matcher(flag).find()) return ruanyaPlay(id);
        if (Pattern.compile("牛牛").matcher(flag).find()) return niuniuPlay(id);
        if (Pattern.compile("围观").matcher(flag).find()) return weiguanPlay(id);
        if (Pattern.compile("星芽").matcher(flag).find()) return playUrl0(id);
        if (Pattern.compile("碎片").matcher(flag).find()) return playUrl0(id);
        if (Pattern.compile("河马").matcher(flag).find()) return hemaPlay(id);
        if (Pattern.compile("七猫").matcher(flag).find()) return playUrl0(id);
        return playUrl0(id);
    }

    // ==================== 搜索 ====================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        final String fkey = key;
        final int fpage = page;
        List<String[]> plats = getPlatList();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(plats.size(), 12));
        List<Future<List<Vod>>> futures = new ArrayList<>();
        for (String[] plat : plats) {
            final String pid = plat[1];
            futures.add(executor.submit(() -> searchPlatform(pid, fkey, fpage)));
        }
        List<Vod> videos = new ArrayList<>();
        for (Future<List<Vod>> f : futures) {
            try {
                List<Vod> part = f.get(8, TimeUnit.SECONDS);
                if (part != null) videos.addAll(part);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        executor.shutdown();
        String lowerKey = key.toLowerCase();
        List<Vod> filtered = new ArrayList<>();
        for (Vod v : videos) {
            if (v.getVodName() != null && v.getVodName().toLowerCase().contains(lowerKey)) filtered.add(v);
        }
        return Result.get().page(page, page + 1, filtered.size(), filtered.size() * (page + 1)).vod(filtered).string();
    }

    /** 单平台搜索 */
    private List<Vod> searchPlatform(String pid, String wd, int page) {
        List<Vod> list = new ArrayList<>();
        try {
            switch (pid) {
                case BAIDU: list = baiduSearch(wd, page); break;
                case TIANQUAN: list = tianquanSearch(wd, page); break;
                case JINLI: list = jinliSearch(wd, page); break;
                case FANQIE: list = fanqieSearch(wd, page); break;
                case XINGYA: list = xingyaSearch(wd, page); break;
                case XIFAN: list = xifanSearch(wd, page); break;
                case RUANYA: list = ruanyaSearch(wd, page); break;
                case QIMAO: list = qimaoSearch(wd, page); break;
                case NIUNIU: list = niuniuSearch(wd, page); break;
                case WEIGUAN: list = weiguanSearch(wd, page); break;
                case SUIPIAN: list = suipianSearch(wd, page); break;
                case HEMA: list = hemaSearch(wd, page); break;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    // ==================== 百度平台 ====================
    private List<Vod> baiduCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String sub = (Arrays.asList("新剧", "限时免费", "精选", "独播").contains(area)) ? area : "新剧";
        String tcsub = ("全部".equals(area) || "全部题材".equals(area)) ? "" : area;
        long t = System.currentTimeMillis() / 1000;
        String version = Crypto.md5(t + "v2");
        JsonObject inner = new JsonObject();
        inner.addProperty("page", "channel_video_landing");
        inner.addProperty("pd", "feed");
        inner.addProperty("from", "feed");
        inner.addProperty("refreshIndex", page);
        inner.addProperty("cursor", "");
        inner.addProperty("theme", "");
        inner.addProperty("timestamp", t);
        inner.addProperty("version", version);
        JsonArray themes = new JsonArray();
        JsonObject theme1 = new JsonObject(); theme1.addProperty("kind", "综合"); theme1.add("names", stringArray(sub));
        JsonObject theme2 = new JsonObject(); theme2.addProperty("kind", "题材"); theme2.add("names", stringArray(tcsub));
        themes.add(theme1); themes.add(theme2);
        inner.add("themes", themes);
        JsonObject extReq = new JsonObject(); extReq.addProperty("flow_tabid", "13");
        inner.add("extRequest", extReq);
        JsonObject dataWrap = new JsonObject(); dataWrap.add("data", inner);
        // JS request 函数对 { data: {...} } 序列化为 data=<JSON.stringify({...})>
        // 所以直接用 dataWrap（{"data":{...}}）作为 data 参数的值
        Map<String, String> params = new HashMap<>();
        params.put("data", dataWrap.toString());
        String html = OkHttp.post("https://mbd.baidu.com/feedapi/v1/videoserver/playlets/list?service=bdbox", params, baiduHeaders());
        JsonObject res = Json.safeObject(html);
        JsonArray items = Json.getJsonArray(Json.getJsonObject(res, "data"), "items");
        int limit = Math.min(items.size(), 20);
        for (int i = 0; i < limit; i++) {
            JsonObject it = items.get(i).getAsJsonObject();
            videos.add(new Vod(BAIDU + "@" + Json.getString(it, "collId"),
                    Json.getString(it, "title", "未知短剧"),
                    Json.getString(it, "img"),
                    "百度短剧 | " + Json.getString(it, "updateStatus", "更新中")));
        }
        return videos;
    }

    private List<Vod> baiduSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject inner = new JsonObject();
        inner.addProperty("query", wd);
        inner.addProperty("page", page);
        inner.add("attribute", stringArray("title"));
        inner.addProperty("fe_page_type", "search");
        JsonObject extra = new JsonObject();
        extra.addProperty("tab_id", "216");
        extra.addProperty("flow_tabid", "13");
        extra.addProperty("shortplay_source", "feed");
        extra.addProperty("from", "feed");
        extra.addProperty("tab_type", "搜索");
        extra.addProperty("sub_template", "playlet_search_result");
        inner.add("extra", extra);
        JsonObject dataWrap = new JsonObject(); dataWrap.add("data", inner);
        Map<String, String> params = new HashMap<>();
        params.put("data", dataWrap.toString());
        String html = OkHttp.post("https://mbd.baidu.com/feedapi/v1/videoserver/playlets/search?service=bdbox", params, baiduHeaders());
        JsonObject res = Json.safeObject(html);
        JsonArray data = Json.getJsonArray(Json.getJsonObject(res, "data"), "itemList");
        for (JsonElement el : data) {
            JsonObject it = el.getAsJsonObject();
            String nid = Json.getString(it, "nid");
            String collId = nid.contains("_") ? nid.split("_", 2)[1] : "";
            videos.add(new Vod(BAIDU + "@" + collId,
                    Json.getString(it, "title", "未知短剧"),
                    Json.getString(it, "img"),
                    "百度短剧 | " + Json.getString(it, "collNum", "搜索短剧")));
        }
        return videos;
    }

    private Vod baiduDetail(String id, String did) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("playlet_id", did);
        params.put("vid", "undefined");
        String html = OkHttp.post("https://sv.baidu.com/haokan/ui-video/playlet/rec/detail?log=vhk&tn=1020970b&ctn=1008350n&blur=1", params, baiduHeaders());
        JsonObject res = Json.safeObject(html);
        JsonObject data = Json.getJsonObject(res, "data");
        JsonArray vidList = Json.getJsonArray(data, "vid_list");
        StringBuilder urls = new StringBuilder();
        for (int i = 0; i < vidList.size(); i++) {
            if (urls.length() > 0) urls.append("#");
            urls.append("第").append(i + 1).append("集$").append(vidList.get(i).getAsString());
        }
        Vod vod = new Vod(id, Json.getString(data, "playlet_title", "未知短剧"), Json.getString(data, "playlet_poster"), "共" + vidList.size() + "集");
        vod.setVodContent("热度值:" + Json.getString(data, "hot_value", "0") + "\n题材:" + Json.getString(data, "tag_text") + "\n集数:" + Json.getString(data, "episodes_num", "0") + "\n简介:" + Json.getString(data, "description"));
        vod.setVodPlayFrom("百度短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    private String baiduPlay(String id) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("method", "post");
        params.put("vid", id);
        String html = OkHttp.post("https://sv.baidu.com/appui/api?cmd=video/relate&log=vhk&tn=1020970b&ctn=1008350n&blur=1", params, baiduHeaders());
        JsonObject res = Json.safeObject(html);
        JsonObject relate = Json.getJsonObject(res, "video/relate");
        JsonObject curVideo = Json.getJsonObject(Json.getJsonObject(relate, "data"), "cur_video");
        JsonArray clarityUrl = Json.getJsonArray(curVideo, "clarityUrl");
        List<String> urls = new ArrayList<>();
        String[] order = {"蓝光", "超清", "标清"};
        for (String q : order) {
            for (JsonElement el : clarityUrl) {
                JsonObject item = el.getAsJsonObject();
                if (q.equals(Json.getString(item, "title")) && !Json.getString(item, "url").isEmpty()) {
                    urls.add(q);
                    urls.add(Json.getString(item, "url"));
                    break;
                }
            }
        }
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", baiduHeaders().get("User-Agent"));
        header.put("Referer", "https://mbd.baidu.com/");
        if (urls.isEmpty()) return Result.get().parse(1).url(id).string();
        return Result.get().parse(0).url(urls).header(header).string();
    }

    // ==================== 甜圈平台 ====================
    private List<Vod> tianquanCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = "https://mov.cenguigui.cn/duanju/api.php?classname=" + URLEncoder.encode(area, "UTF-8") + "&offset=" + page;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        for (JsonElement el : Json.getJsonArray(res, "data")) {
            JsonObject it = el.getAsJsonObject();
            videos.add(new Vod(TIANQUAN + "@" + Json.getString(it, "book_id"),
                    Json.getString(it, "title", "未知标题"), Json.getString(it, "cover"),
                    "甜圈短剧 | " + Json.getString(it, "copyright")));
        }
        return videos;
    }

    private List<Vod> tianquanSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = "https://mov.cenguigui.cn/duanju/api.php?name=" + URLEncoder.encode(wd, "UTF-8") + "&offset=" + page;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        for (JsonElement el : Json.getJsonArray(res, "data")) {
            JsonObject it = el.getAsJsonObject();
            videos.add(new Vod(TIANQUAN + "@" + Json.getString(it, "book_id"),
                    Json.getString(it, "title", "未知标题"), Json.getString(it, "cover"),
                    "甜圈短剧 | " + Json.getString(it, "copyright")));
        }
        return videos;
    }

    private Vod tianquanDetail(String id, String did) throws Exception {
        String url = "https://mov.cenguigui.cn/duanju/api.php?book_id=" + did;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonArray data = Json.getJsonArray(res, "data");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : data) {
            JsonObject item = el.getAsJsonObject();
            if (urls.length() > 0) urls.append("#");
            urls.append(Json.getString(item, "title", "第1集")).append("$").append(Json.getString(item, "video_id", Json.getString(item, "id")));
        }
        Vod vod = new Vod(id, Json.getString(res, "book_name", "未知标题"), Json.getString(res, "book_pic"), Json.getString(res, "duration"));
        vod.setVodContent(Json.getString(res, "desc"));
        vod.setVodPlayFrom("甜圈短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    // ==================== 锦鲤平台 ====================
    private List<Vod> jinliCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject body = new JsonObject();
        body.addProperty("page", page);
        body.addProperty("limit", 24);
        body.addProperty("type_id", area);
        body.addProperty("year", "");
        body.addProperty("keyword", "");
        String response = OkHttp.post("https://api.jinlidj.com/api/search", body.toString(), defaultHeaders());
        JsonObject res = Json.safeObject(response);
        JsonArray list = Json.getJsonArray(Json.getJsonObject(res, "data"), "list");
        for (JsonElement el : list) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "vod_total");
            videos.add(new Vod(JINLI + "@" + Json.getString(item, "vod_id"),
                    Json.getString(item, "vod_name"), Json.getString(item, "vod_pic"),
                    "锦鲤短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private List<Vod> jinliSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject body = new JsonObject();
        body.addProperty("page", page);
        body.addProperty("limit", 20);
        body.addProperty("type_id", "");
        body.addProperty("year", "");
        body.addProperty("keyword", wd);
        String response = OkHttp.post("https://api.jinlidj.com/api/search", body.toString(), defaultHeaders());
        JsonObject res = Json.safeObject(response);
        JsonArray list = Json.getJsonArray(Json.getJsonObject(res, "data"), "list");
        for (JsonElement el : list) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "vod_total");
            videos.add(new Vod(JINLI + "@" + Json.getString(item, "vod_id"),
                    Json.getString(item, "vod_name", "未知短剧"), Json.getString(item, "vod_pic"),
                    "锦鲤短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private Vod jinliDetail(String id, String did) throws Exception {
        String url = "https://api.jinlidj.com/api/detail/" + did;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonObject data = Json.getJsonObject(res, "data");
        StringBuilder urls = new StringBuilder();
        JsonObject player = Json.getJsonObject(data, "player");
        for (Map.Entry<String, JsonElement> entry : player.entrySet()) {
            if (urls.length() > 0) urls.append("#");
            urls.append(entry.getKey()).append("$").append(entry.getValue().getAsString());
        }
        Vod vod = new Vod(id, Json.getString(data, "vod_name", "暂无名称"), Json.getString(data, "vod_pic"), Json.getString(data, "vod_remarks"));
        vod.setVodContent(Json.getString(data, "vod_blurb"));
        vod.setVodPlayFrom("锦鲤短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    private String jinliPlay(String id) throws Exception {
        String html = OkHttp.string(id + "&auto=1", null);
        Matcher matcher = Pattern.compile("let data\\s*=\\s*(\\{[^;]*});").matcher(html);
        if (matcher.find()) {
            JsonObject data = Json.safeObject(matcher.group(1));
            if (data.has("url")) return playUrl0(Json.getString(data, "url"));
        }
        return playUrl0(id);
    }

    // ==================== 番茄平台 ====================
    private List<Vod> fanqieCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String sessionId = new java.text.SimpleDateFormat("yyyyMMddHHmm").format(new java.util.Date());
        String url = "https://reading.snssdk.com/reading/bookapi/bookmall/cell/change/v?change_type=0&selected_items=" + URLEncoder.encode(area, "UTF-8") + "&tab_type=8&cell_id=6952850996422770718&version_tag=video_feed_refactor&device_id=1423244030195267&aid=1967&app_name=novelapp&ssmix=a&session_id=" + sessionId;
        if (page > 1) url += "&offset=" + ((page - 1) * 12);
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonArray items = new JsonArray();
        JsonObject dataObj = Json.getJsonObject(res, "data");
        JsonObject cellView = Json.getJsonObject(dataObj, "cell_view");
        if (cellView.has("cell_data")) items = Json.getJsonArray(cellView, "cell_data");
        if (items.size() == 0 && res.has("data") && res.get("data").isJsonArray()) items = Json.getJsonArray(res, "data");
        for (JsonElement el : items) {
            JsonObject item = el.getAsJsonObject();
            JsonObject v = Json.getJsonObject(item, "video_data");
            if (v == null || v.size() == 0) {
                if (item.has("video_data") && item.get("video_data").isJsonArray()) {
                    JsonArray vdArr = Json.getJsonArray(item, "video_data");
                    if (vdArr.size() > 0) v = vdArr.get(0).getAsJsonObject();
                }
            }
            if (v == null || v.size() == 0) v = item;
            String sid = Json.getString(v, "series_id", Json.getString(v, "book_id", Json.getString(v, "id")));
            videos.add(new Vod(FANQIE + "@" + sid, Json.getString(v, "title", "未知短剧"),
                    Json.getString(v, "cover", Json.getString(v, "horiz_cover")),
                    "番茄短剧 | " + Json.getString(v, "sub_title", Json.getString(v, "rec_text"))));
        }
        return videos;
    }

    private List<Vod> fanqieSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = "https://fqgo.52dns.cc/search?keyword=" + URLEncoder.encode(wd, "UTF-8") + "&page=" + page;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        for (JsonElement el : Json.getJsonArray(res, "data")) {
            JsonObject item = el.getAsJsonObject();
            videos.add(new Vod(FANQIE + "@" + Json.getString(item, "series_id"),
                    Json.getString(item, "title", "未知标题"), Json.getString(item, "cover"),
                    "番茄短剧 | " + Json.getString(item, "sub_title")));
        }
        return videos;
    }

    private Vod fanqieDetail(String id, String did) throws Exception {
        String url = "https://fqgo.52dns.cc/catalog?book_id=" + did;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonObject data = Json.getJsonObject(res, "data");
        JsonObject bookInfo = Json.getJsonObject(data, "book_info");
        JsonArray itemList = Json.getJsonArray(data, "item_data_list");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : itemList) {
            JsonObject item = el.getAsJsonObject();
            if (urls.length() > 0) urls.append("#");
            urls.append(Json.getString(item, "title")).append("$").append(Json.getString(item, "item_id"));
        }
        Vod vod = new Vod(id, Json.getString(bookInfo, "book_name"), Json.getString(bookInfo, "thumb_url", Json.getString(bookInfo, "audio_thumb_uri")), Json.getString(bookInfo, "sub_info", "更新至" + itemList.size() + "集"));
        vod.setVodContent(Json.getString(bookInfo, "abstract", Json.getString(bookInfo, "book_abstract_v2")));
        vod.setVodPlayFrom("番茄短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    private String fanqiePlay(String id) throws Exception {
        String url = "https://fqgo.52dns.cc/video?item_ids=" + id;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonObject data = Json.getJsonObject(res, "data");
        JsonObject idData = Json.getJsonObject(data, id);
        if (idData.size() == 0) return playUrl0(id);
        JsonObject model = Json.safeObject(Json.getString(idData, "video_model"));
        JsonObject videoList = Json.getJsonObject(model, "video_list");
        JsonObject video1 = Json.getJsonObject(videoList, "video_1");
        String mainUrl = Json.getString(video1, "main_url");
        if (mainUrl.isEmpty()) return playUrl0(id);
        return playUrl0(Crypto.base64Decode(mainUrl));
    }

    // ==================== 星芽平台 ====================
    private List<Vod> xingyaCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = "https://app.whjzjx.cn/cloud/v2/theater/home_page?theater_class_id=" + URLEncoder.encode(area, "UTF-8") + "&type=1&class2_ids=0&page_num=" + page + "&page_size=24";
        JsonObject res = Json.safeObject(OkHttp.string(url, xingyaHeaders));
        JsonArray list = Json.getJsonArray(Json.getJsonObject(res, "data"), "list");
        for (JsonElement el : list) {
            JsonObject it = el.getAsJsonObject();
            JsonObject theater = Json.getJsonObject(it, "theater");
            String total = Json.getString(theater, "total");
            videos.add(new Vod(XINGYA + "@" + Json.getString(theater, "id"),
                    Json.getString(theater, "title"), Json.getString(theater, "cover_url"),
                    "星芽短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private List<Vod> xingyaSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject body = new JsonObject();
        body.addProperty("text", wd);
        String response = OkHttp.post("https://app.whjzjx.cn/v3/search", body.toString(), xingyaHeaders);
        JsonObject res = Json.safeObject(response);
        JsonArray list = Json.getJsonArray(Json.getJsonObject(Json.getJsonObject(res, "data"), "theater"), "search_data");
        for (JsonElement el : list) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "total");
            videos.add(new Vod(XINGYA + "@" + Json.getString(item, "id"),
                    Json.getString(item, "title"), Json.getString(item, "cover_url"),
                    "星芽短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private Vod xingyaDetail(String id, String did) throws Exception {
        String url = "https://app.whjzjx.cn/v2/theater_parent/detail?theater_parent_id=" + did;
        JsonObject res = Json.safeObject(OkHttp.string(url, xingyaHeaders));
        JsonObject data = Json.getJsonObject(res, "data");
        StringBuilder urls = new StringBuilder();
        JsonArray theaters = Json.getJsonArray(data, "theaters");
        for (JsonElement el : theaters) {
            JsonObject item = el.getAsJsonObject();
            String sonUrl = Json.getString(item, "son_video_url");
            if (!sonUrl.isEmpty()) {
                if (urls.length() > 0) urls.append("#");
                urls.append("第").append(Json.getString(item, "num")).append("集$").append(sonUrl);
            }
        }
        Vod vod = new Vod(id, Json.getString(data, "title", "未知剧名"), Json.getString(data, "cover_url"), "2".equals(Json.getString(data, "is_over")) ? "连载中" : "已完结");
        vod.setVodContent(Json.getString(data, "introduction", Json.getString(data, "desc")));
        vod.setVodPlayFrom("星芽短剧");
        vod.setVodPlayUrl(urls.length() > 0 ? urls.toString() : "暂无播放地址$0");
        return vod;
    }

    // ==================== 西饭平台 ====================
    private List<Vod> xifanCategory(int page, String area) throws Exception {
        return xifanList(page, area, "aggregationPage");
    }

    private List<Vod> xifanSearch(String wd, int page) throws Exception {
        return xifanListSearch(page, wd);
    }

    private List<Vod> xifanList(int page, String area, String reqType) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String[] areaParts = area.split("@", 2);
        String typeId = areaParts[0];
        String typeName = areaParts.length > 1 ? areaParts[1] : "";
        long ts = System.currentTimeMillis() / 1000;
        String url = "https://xifan-api-cn.youlishipin.com/xifan/drama/portalPage?reqType=" + reqType + "&offset=" + ((page - 1) * 30) + "&categoryId=" + URLEncoder.encode(typeId, "UTF-8") + "&quickEngineVersion=-1&scene=&categoryNames=" + URLEncoder.encode(typeName, "UTF-8") + "&categoryVersion=1&density=1.5&pageID=page_theater&version=2001001&androidVersionCode=28&requestId=" + ts + "aa498144140ef297&appId=drama&teenMode=false&userBaseMode=false&session=eyJpbmZvIjp7InVpZCI6IiIsInJ0IjoiMTc0MDY1ODI5NCIsInVuIjoiT1BHXzFlZGQ5OTZhNjQ3ZTQ1MjU4Nzc1MTE2YzFkNzViN2QwIiwiZnQiOiIxNzQwNjU4Mjk0In19&feedssession=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1dHlwIjowLCJidWlkIjoxNjMzOTY4MTI2MTQ4NjQxNTM2LCJhdWQiOiJkcmFtYSIsInZlciI6MiwicmF0IjoxNzQwNjU4Mjk0LCJ1bm0iOiJPUEdfMWVkZDk5NmE2NDdlNDUyNTg3NzUxMTZjMWQ3NWI3ZDAiLCJleHAiOjE3NDEyNjMwOTQsImRjIjoiZ3pxeSJ9.JS3QY6ER0P2cQSxAE_OGKSMIWNAMsYUZ3mJTnEpf-Rc";
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonArray elements = Json.getJsonArray(Json.getJsonObject(res, "result"), "elements");
        for (JsonElement el : elements) {
            JsonObject soup = el.getAsJsonObject();
            for (JsonElement ce : Json.getJsonArray(soup, "contents")) {
                JsonObject vod = ce.getAsJsonObject();
                JsonObject dj = Json.getJsonObject(vod, "duanjuVo");
                String total = Json.getString(dj, "total");
                videos.add(new Vod(XIFAN + "@" + Json.getString(dj, "duanjuId") + "#" + Json.getString(dj, "source"),
                        Json.getString(dj, "title"), Json.getString(dj, "coverImageUrl"),
                        "西饭短剧 | " + (total.isEmpty() ? "" : total + "集")));
            }
        }
        return videos;
    }

    private List<Vod> xifanListSearch(int page, String wd) throws Exception {
        List<Vod> videos = new ArrayList<>();
        long ts = System.currentTimeMillis() / 1000;
        String url = "https://xifan-api-cn.youlishipin.com/xifan/search/getSearchList?reqType=search&offset=" + ((page - 1) * 20) + "&keyword=" + URLEncoder.encode(wd, "UTF-8") + "&quickEngineVersion=-1&scene=&categoryVersion=1&density=1.5&pageID=page_theater&version=2001001&androidVersionCode=28&requestId=" + ts + "aa498144140ef297&appId=drama&teenMode=false&userBaseMode=false&session=eyJpbmZvIjp7InVpZCI6IiIsInJ0IjoiMTc0MDY1ODI5NCIsInVuIjoiT1BHXzFlZGQ5OTZhNjQ3ZTQ1MjU4Nzc1MTE2YzFkNzViN2QwIiwiZnQiOiIxNzQwNjU4Mjk0In19&feedssession=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1dHlwIjowLCJidWlkIjoxNjMzOTY4MTI2MTQ4NjQxNTM2LCJhdWQiOiJkcmFtYSIsInZlciI6MiwicmF0IjoxNzQwNjU4Mjk0LCJ1bm0iOiJPUEdfMWVkZDk5NmE2NDdlNDUyNTg3NzUxMTZjMWQ3NWI3ZDAiLCJleHAiOjE3NDEyNjMwOTQsImRjIjoiZ3pxeSJ9.JS3QY6ER0P2cQSxAE_OGKSMIWNAMsYUZ3mJTnEpf-Rc";
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonArray elements = Json.getJsonArray(Json.getJsonObject(res, "result"), "elements");
        for (JsonElement el : elements) {
            JsonObject vod = el.getAsJsonObject();
            JsonObject dj = Json.getJsonObject(vod, "duanjuVo");
            String total = Json.getString(dj, "total");
            videos.add(new Vod(XIFAN + "@" + Json.getString(dj, "duanjuId") + "#" + Json.getString(dj, "source"),
                    Json.getString(dj, "title", "未知标题"), Json.getString(dj, "coverImageUrl"),
                    "西饭短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private Vod xifanDetail(String id, String did) throws Exception {
        String[] didParts = did.split("#", 2);
        String duanjuId = didParts[0];
        String source = didParts.length > 1 ? didParts[1] : "";
        String url = "https://xifan-api-cn.youlishipin.com/xifan/drama/getDuanjuInfo?duanjuId=" + duanjuId + "&source=" + source;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonObject data = Json.getJsonObject(res, "result");
        JsonArray episodeList = Json.getJsonArray(data, "episodeList");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : episodeList) {
            JsonObject ep = el.getAsJsonObject();
            if (urls.length() > 0) urls.append("#");
            urls.append(Json.getString(ep, "index")).append("$").append(Json.getString(ep, "playUrl"));
        }
        String total = Json.getString(data, "total");
        String remarks = "over".equals(Json.getString(data, "updateStatus")) ? total + "集 已完结" : "更新" + total + "集";
        Vod vod = new Vod(id, Json.getString(data, "title"), Json.getString(data, "coverImageUrl"), remarks);
        vod.setVodContent(Json.getString(data, "desc", "未知"));
        vod.setVodPlayFrom("西饭短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    // ==================== 软鸭平台 ====================
    private List<Vod> ruanyaCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = "https://api.xingzhige.com/API/playlet/?keyword=" + URLEncoder.encode(area, "UTF-8") + "&page=" + page;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        for (JsonElement el : Json.getJsonArray(res, "data")) {
            JsonObject item = el.getAsJsonObject();
            String purl = Json.getString(item, "title") + "@" + Json.getString(item, "cover") + "@" + Json.getString(item, "author") + "@" + Json.getString(item, "type") + "@" + Json.getString(item, "desc") + "@" + Json.getString(item, "book_id");
            videos.add(new Vod(RUANYA + "@" + URLEncoder.encode(purl, "UTF-8"),
                    Json.getString(item, "title"), Json.getString(item, "cover"),
                    "软鸭短剧 | " + Json.getString(item, "type")));
        }
        return videos;
    }

    private List<Vod> ruanyaSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = "https://api.xingzhige.com/API/playlet/?keyword=" + URLEncoder.encode(wd, "UTF-8") + "&page=" + page;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        for (JsonElement el : Json.getJsonArray(res, "data")) {
            JsonObject item = el.getAsJsonObject();
            String purl = Json.getString(item, "title") + "@" + Json.getString(item, "cover") + "@" + Json.getString(item, "author") + "@" + Json.getString(item, "type") + "@" + Json.getString(item, "desc") + "@" + Json.getString(item, "book_id");
            videos.add(new Vod(RUANYA + "@" + URLEncoder.encode(purl, "UTF-8"),
                    Json.getString(item, "title"), Json.getString(item, "cover"),
                    "软鸭短剧 | " + Json.getString(item, "type")));
        }
        return videos;
    }

    private Vod ruanyaDetail(String id, String did) throws Exception {
        String didDecoded = URLDecoder.decode(did, "UTF-8");
        String[] parts2 = didDecoded.split("@", 6);
        String title = parts2.length > 0 ? parts2[0] : "";
        String img = parts2.length > 1 ? parts2[1] : "";
        String author = parts2.length > 2 ? parts2[2] : "";
        String type = parts2.length > 3 ? parts2[3] : "";
        String desc = parts2.length > 4 ? parts2[4] : "";
        String bookId = parts2.length > 5 ? parts2[5] : "";
        String url = "https://api.xingzhige.com/API/playlet/?book_id=" + bookId;
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonArray videoList = Json.getJsonArray(Json.getJsonObject(res, "data"), "video_list");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : videoList) {
            JsonObject ep = el.getAsJsonObject();
            if (urls.length() > 0) urls.append("#");
            urls.append(Json.getString(ep, "title")).append("$").append(Json.getString(ep, "video_id"));
        }
        Vod vod = new Vod(id, title, img, type);
        vod.setVodActor(author);
        vod.setVodContent(desc);
        vod.setVodPlayFrom("软鸭短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    private String ruanyaPlay(String id) throws Exception {
        String url = "https://api.xingzhige.com/API/playlet/?video_id=" + id + "&quality=original";
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        String playUrl = Json.getString(Json.getJsonObject(Json.getJsonObject(res, "data"), "video"), "url");
        return playUrl0(playUrl);
    }

    // ==================== 七猫平台 ====================
    private List<Vod> qimaoCategory(String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String sign = Crypto.md5("operation=1playlet_privacy=1tag_id=" + area + AGG_KEYS);
        String url = "https://api-store.qmplaylet.com/api/v1/playlet/index?tag_id=" + URLEncoder.encode(area, "UTF-8") + "&playlet_privacy=1&operation=1&sign=" + sign;
        JsonObject res = Json.safeObject(OkHttp.string(url, getQiMaoHeaders()));
        JsonArray list = Json.getJsonArray(Json.getJsonObject(res, "data"), "list");
        for (JsonElement el : list) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "total_episode_num");
            videos.add(new Vod(QIMAO + "@" + URLEncoder.encode(Json.getString(item, "playlet_id"), "UTF-8"),
                    Json.getString(item, "title"), Json.getString(item, "image_link"),
                    "七猫短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private List<Vod> qimaoSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String sign = Crypto.md5("operation=2playlet_privacy=1search_word=" + wd + AGG_KEYS);
        String url = "https://api-store.qmplaylet.com/api/v1/playlet/search?search_word=" + URLEncoder.encode(wd, "UTF-8") + "&playlet_privacy=1&operation=2&sign=" + sign;
        JsonObject res = Json.safeObject(OkHttp.string(url, getQiMaoHeaders()));
        JsonArray list = Json.getJsonArray(Json.getJsonObject(res, "data"), "list");
        for (JsonElement el : list) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "total_episode_num");
            videos.add(new Vod(QIMAO + "@" + URLEncoder.encode(Json.getString(item, "playlet_id"), "UTF-8"),
                    Json.getString(item, "title", "未知标题"), Json.getString(item, "image_link"),
                    "七猫短剧 | " + Json.getString(item, "tags") + " " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private Vod qimaoDetail(String id, String did) throws Exception {
        String didDecoded = URLDecoder.decode(did, "UTF-8");
        String sign = Crypto.md5("playlet_id=" + didDecoded + AGG_KEYS);
        String url = "https://api-read.qmplaylet.com/player/api/v1/playlet/info?playlet_id=" + didDecoded + "&sign=" + sign;
        JsonObject res = Json.safeObject(OkHttp.string(url, getQiMaoHeaders()));
        JsonObject data = Json.getJsonObject(res, "data");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : Json.getJsonArray(data, "play_list")) {
            JsonObject it = el.getAsJsonObject();
            if (urls.length() > 0) urls.append("#");
            urls.append(Json.getString(it, "sort")).append("$").append(Json.getString(it, "video_url"));
        }
        Vod vod = new Vod(id, Json.getString(data, "title", "未知标题"), Json.getString(data, "image_link"), Json.getString(data, "tags") + " " + Json.getString(data, "total_episode_num", "0") + "集");
        vod.setVodContent(Json.getString(data, "intro", "未知剧情"));
        vod.setVodPlayFrom("七猫短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    // ==================== 牛牛平台 ====================
    private List<Vod> niuniuCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject condition = new JsonObject();
        condition.addProperty("classify", area);
        condition.addProperty("typeId", "S1");
        JsonObject body = new JsonObject();
        body.add("condition", condition);
        body.addProperty("pageNum", page);
        body.addProperty("pageSize", 24);
        String url = "https://new.tianjinzhitongdaohe.com/api/v1/app/screen/screenMovie";
        JsonObject res = Json.safeObject(OkHttp.post(url, body.toString(), niuniuHeaders));
        JsonArray records = Json.getJsonArray(Json.getJsonObject(res, "data"), "records");
        for (JsonElement el : records) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "totalEpisode");
            videos.add(new Vod(NIUNIU + "@" + Json.getString(item, "id"),
                    Json.getString(item, "name"), Json.getString(item, "cover"),
                    "牛牛短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private List<Vod> niuniuSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject condition = new JsonObject();
        condition.addProperty("typeId", "S1");
        condition.addProperty("value", wd);
        JsonObject body = new JsonObject();
        body.add("condition", condition);
        body.addProperty("pageNum", page);
        body.addProperty("pageSize", 20);
        String url = "https://new.tianjinzhitongdaohe.com/api/v1/app/search/searchMovie";
        JsonObject res = Json.safeObject(OkHttp.post(url, body.toString(), niuniuHeaders));
        JsonArray records = Json.getJsonArray(Json.getJsonObject(res, "data"), "records");
        for (JsonElement el : records) {
            JsonObject item = el.getAsJsonObject();
            String total = Json.getString(item, "totalEpisode");
            videos.add(new Vod(NIUNIU + "@" + Json.getString(item, "id"),
                    Json.getString(item, "name"), Json.getString(item, "cover"),
                    "牛牛短剧 | " + (total.isEmpty() ? "" : total + "集")));
        }
        return videos;
    }

    private Vod niuniuDetail(String id, String did) throws Exception {
        // desc
        JsonObject descBody = new JsonObject();
        descBody.addProperty("id", did);
        descBody.addProperty("typeId", "S1");
        String descUrl = "https://new.tianjinzhitongdaohe.com/api/v1/app/play/movieDesc";
        JsonObject descRes = Json.safeObject(OkHttp.post(descUrl, descBody.toString(), niuniuHeaders));
        JsonObject descInfo = Json.getJsonObject(descRes, "data");

        // detail
        JsonObject listBody = new JsonObject();
        listBody.addProperty("id", did);
        listBody.addProperty("source", 0);
        listBody.addProperty("typeId", "S1");
        listBody.addProperty("userId", "546932");
        String detailUrl = "https://new.tianjinzhitongdaohe.com/api/v1/app/play/movieDetails";
        JsonObject listRes = Json.safeObject(OkHttp.post(detailUrl, listBody.toString(), niuniuHeaders));
        JsonObject listInfo = Json.getJsonObject(listRes, "data");

        StringBuilder urls = new StringBuilder();
        JsonArray episodeList = Json.getJsonArray(listInfo, "episodeList");
        String listUrl = Json.getString(listInfo, "url");
        if (!listUrl.isEmpty() && episodeList.size() > 0) {
            for (JsonElement el : episodeList) {
                JsonObject ep = el.getAsJsonObject();
                if (urls.length() > 0) urls.append("#");
                urls.append(Json.getString(ep, "episode")).append("$").append(did).append("+").append(Json.getString(ep, "id"));
            }
        } else {
            String thirdPlayId = Json.getString(listInfo, "thirdPlayId");
            if (!thirdPlayId.isEmpty()) {
                String data1 = "not_include=0&lock_free=1&type=1&clientVersion=v5.2.5&uuid=6IDYUSASPQY5BBVACWQW3LLTPV4V7DE26UOCX5TZTVUGX4VUJNXQ01&resolution=1080*2320&openudid=82f4175d577a2939&dt=22021211RC&os_api=31&install_id=1496879012031075&sdk_version=1.1.3.0&siteid=5627189&dev_log_aid=667431&oaid=abec0dfff623201b&timestamp=1752498494&direction=0&ac=mobile&os=Android&vod_version=1.10.21.6-tob&os_version=12&count=1&index=1&shortplay_id=" + thirdPlayId + "&sha1=46121F77CE2FCAD3DBC3B9EC8A24908C1A8AD6D9&device_brand=Redmi&package_name=com.niuniu.ztdh.app";
                try {
                    JsonObject html1 = niuniuPost("https://csj-sp.csjdeveloper.com/csj_sp/api/v1/shortplay/detail?siteid=5627189", data1, "1");
                    JsonArray rightList = Json.getJsonArray(Json.getJsonObject(html1, "data"), "episode_right_list");
                    for (JsonElement el : rightList) {
                        JsonObject it = el.getAsJsonObject();
                        String lockType = Json.getString(it, "lock_type", "free");
                        if (urls.length() > 0) urls.append("#");
                        urls.append("第").append(Json.getString(it, "index")).append("集$").append(Json.getString(it, "index")).append("+").append(lockType).append("+").append(thirdPlayId);
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        }
        String totalEp = Json.getString(descInfo, "totalEpisode", Json.getString(listInfo, "totalEpisode", "0"));
        Vod vod = new Vod(id, Json.getString(descInfo, "name", Json.getString(listInfo, "name", "未知名称")), Json.getString(descInfo, "cover", Json.getString(listInfo, "cover")), "共" + totalEp + "集");
        vod.setVodContent("类型：" + Json.getString(descInfo, "classify") + "\n评分：" + Json.getString(descInfo, "score") + "\n简介：" + Json.getString(descInfo, "introduce"));
        vod.setVodPlayFrom("牛牛短剧");
        vod.setVodPlayUrl(urls.length() > 0 ? urls.toString() : "暂无播放地址$0");
        return vod;
    }

    private String niuniuPlay(String id) throws Exception {
        String[] inputArr = id.split("\\+");
        if (inputArr.length == 2) {
            Matcher m = Pattern.compile("\\d+").matcher(inputArr[0]);
            String ep = m.find() ? m.group() : "";
            String videoId = inputArr[1];
            JsonObject body = new JsonObject();
            body.addProperty("id", videoId);
            body.addProperty("source", 0);
            body.addProperty("typeId", "S1");
            body.addProperty("userId", "546932");
            body.addProperty("episodeId", ep);
            String url = "https://new.tianjinzhitongdaohe.com/api/v1/app/play/movieDetails";
            JsonObject res = Json.safeObject(OkHttp.post(url, body.toString(), niuniuHeaders));
            String playUrl = Json.getString(Json.getJsonObject(res, "data"), "url");
            return playUrl0(playUrl.isEmpty() ? id : playUrl);
        } else if (inputArr.length == 3) {
            String index = inputArr[0];
            String lockType = inputArr[1];
            String thirdPlayId = inputArr[2];
            if ("free".equals(lockType)) {
                String frdata = "not_include=0&lock_free=1&type=1&clientVersion=v5.2.5&uuid=6IDYUSASPQY5BBVACWQW3LLTPV4V7DE26UOCX5TZTVUGX4VUJNXQ01&resolution=1080*2320&openudid=82f4175d577a2939&dt=22021211RC&os_api=31&install_id=1496879012031075&sdk_version=1.1.3.0&siteid=5627189&dev_log_aid=667431&oaid=abec0dfff623201b&timestamp=1752498494&direction=0&ac=mobile&os=Android&vod_version=1.10.21.6-tob&os_version=12&count=1&index=1&shortplay_id=" + thirdPlayId + "&sha1=46121F77CE2FCAD3DBC3B9EC8A24908C1A8AD6D9&device_brand=Redmi&package_name=com.niuniu.ztdh.app";
                JsonObject frhtml = niuniuPost("https://csj-sp.csjdeveloper.com/csj_sp/api/v1/shortplay/detail?siteid=5627189", frdata, index);
                JsonArray list = Json.getJsonArray(Json.getJsonObject(frhtml, "data"), "list");
                if (list.size() > 0) {
                    String mainUrl = Json.getString(Json.getJsonObject(Json.getJsonObject(Json.getJsonObject(list.get(0).getAsJsonObject(), "video_model"), "video_list"), "video_1"), "main_url");
                    return playUrl0(Crypto.base64Decode(mainUrl));
                }
            } else {
                String unlockData = "ac=mobile&os=Android&vod_version=1.10.21.6-tob&os_version=12&lock_ad=3&lock_free=3&type=1&clientVersion=v5.2.5&uuid=6IDYUSASPQY5BBVACWQW3LLTPV4V7DE26UOCX5TZTVUGX4VUJNXQ01&resolution=1080*2320&openudid=82f4175d577a2939&shortplay_id=" + thirdPlayId + "&dt=22021211RC&sha1=46121F77CE2FCAD3DBC3B9EC8A24908C1A8AD6D9&lock_index=21&os_api=31&install_id=1496879012031075&device_brand=Redmi&sdk_version=1.1.3.0&package_name=com.niuniu.ztdh.app&siteid=5627189&dev_log_aid=667431&oaid=abec0dfff623201b&timestamp=1752498493";
                niuniuPost("https://csj-sp.csjdeveloper.com/csj_sp/api/v1/pay/ad_unlock?siteid=5627189", unlockData, index);
                String udata = "not_include=0&lock_free=1&type=1&clientVersion=v5.2.5&uuid=6IDYUSASPQY5BBVACWQW3LLTPV4V7DE26UOCX5TZTVUGX4VUJNXQ01&resolution=1080*2320&openudid=82f4175d577a2939&dt=22021211RC&os_api=31&install_id=1496879012031075&sdk_version=1.1.3.0&siteid=5627189&dev_log_aid=667431&oaid=abec0dfff623201b&timestamp=1752498494&direction=0&ac=mobile&os=Android&vod_version=1.10.21.6-tob&os_version=12&count=1&index=1&shortplay_id=" + thirdPlayId + "&sha1=46121F77CE2FCAD3DBC3B9EC8A24908C1A8AD6D9&device_brand=Redmi&package_name=com.niuniu.ztdh.app";
                JsonObject unhtml = niuniuPost("https://csj-sp.csjdeveloper.com/csj_sp/api/v1/shortplay/detail?siteid=5627189", udata, index);
                JsonArray list = Json.getJsonArray(Json.getJsonObject(unhtml, "data"), "list");
                if (list.size() > 0) {
                    String mainUrl = Json.getString(Json.getJsonObject(Json.getJsonObject(Json.getJsonObject(list.get(0).getAsJsonObject(), "video_model"), "video_list"), "video_1"), "main_url");
                    return playUrl0(Crypto.base64Decode(mainUrl));
                }
            }
        }
        return playUrl0(id);
    }

    // ==================== 围观平台 ====================
    private List<Vod> weiguanCategory(int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject body = new JsonObject();
        body.addProperty("audience", "全部受众");
        body.addProperty("page", page);
        body.addProperty("pageSize", 30);
        body.addProperty("searchWord", "");
        body.addProperty("subject", "全部主题");
        String url = "https://api.drama.9ddm.com/drama/home/search?version_code=1500&os_type=1";
        JsonObject res = Json.safeObject(OkHttp.post(url, body.toString(), defaultHeaders()));
        JsonArray data = Json.getJsonArray(res, "data");
        for (JsonElement el : data) {
            JsonObject it = el.getAsJsonObject();
            videos.add(new Vod(WEIGUAN + "@" + Json.getString(it, "oneId"),
                    Json.getString(it, "title", "未知短剧"),
                    Json.getString(it, "vertPoster", Json.getString(it, "horizonPoster")),
                    "围观短剧 | 集数:" + Json.getString(it, "episodeCount", "0")));
        }
        return videos;
    }

    private List<Vod> weiguanSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject body = new JsonObject();
        body.addProperty("audience", "");
        body.addProperty("page", page);
        body.addProperty("pageSize", 20);
        body.addProperty("searchWord", wd);
        body.addProperty("subject", "");
        String url = "https://api.drama.9ddm.com/drama/home/search?version_code=1500&os_type=1";
        JsonObject res = Json.safeObject(OkHttp.post(url, body.toString(), defaultHeaders()));
        JsonArray data = Json.getJsonArray(res, "data");
        for (JsonElement el : data) {
            JsonObject it = el.getAsJsonObject();
            videos.add(new Vod(WEIGUAN + "@" + Json.getString(it, "oneId"),
                    Json.getString(it, "title", "未知标题"),
                    Json.getString(it, "vertPoster", Json.getString(it, "horizonPoster")),
                    "围观短剧 | 集数:" + Json.getString(it, "episodeCount", "0")));
        }
        return videos;
    }

    private Vod weiguanDetail(String id, String did) throws Exception {
        String url = "https://api.drama.9ddm.com/drama/home/shortVideoDetail?version_code=1500&os_type=1&oneId=" + did + "&page=1&pageSize=1000";
        JsonObject res = Json.safeObject(OkHttp.string(url, defaultHeaders()));
        JsonArray data = Json.getJsonArray(res, "data");
        StringBuilder urls = new StringBuilder();
        JsonObject first = data.size() > 0 ? data.get(0).getAsJsonObject() : new JsonObject();
        for (JsonElement el : data) {
            JsonObject ep = el.getAsJsonObject();
            JsonArray playSetting = new JsonArray();
            // playSetting 可能是数组，也可能是 JSON 字符串（JS 中有 typeof === 'string' 判断）
            if (ep.has("playSetting")) {
                JsonElement psEl = ep.get("playSetting");
                if (psEl.isJsonArray()) {
                    playSetting = psEl.getAsJsonArray();
                } else if (psEl.isJsonPrimitive()) {
                    try { playSetting = Json.parse(psEl.getAsString()).getAsJsonArray(); } catch (Exception ignored) {}
                }
            }
            if (playSetting.size() == 0 && ep.has("videoClarityList")) {
                JsonElement vcEl = ep.get("videoClarityList");
                if (vcEl.isJsonArray()) {
                    playSetting = vcEl.getAsJsonArray();
                } else if (vcEl.isJsonPrimitive()) {
                    try { playSetting = Json.parse(vcEl.getAsString()).getAsJsonArray(); } catch (Exception ignored) {}
                }
            }
            String playUrl = "";
            for (JsonElement ps : playSetting) {
                JsonObject item = ps.getAsJsonObject();
                if ("1080P".equals(Json.getString(item, "name"))) { playUrl = Json.getString(item, "url"); break; }
            }
            if (playUrl.isEmpty()) {
                for (JsonElement ps : playSetting) {
                    JsonObject item = ps.getAsJsonObject();
                    if ("720P".equals(Json.getString(item, "name"))) { playUrl = Json.getString(item, "url"); break; }
                }
            }
            if (playUrl.isEmpty()) {
                for (JsonElement ps : playSetting) {
                    JsonObject item = ps.getAsJsonObject();
                    if ("480P".equals(Json.getString(item, "name"))) { playUrl = Json.getString(item, "url"); break; }
                }
            }
            if (!playUrl.isEmpty()) {
                if (urls.length() > 0) urls.append("#");
                urls.append("第").append(Json.getString(ep, "playOrder", "1")).append("集$").append(playUrl);
            }
        }
        Vod vod = new Vod(id, Json.getString(first, "title"), Json.getString(first, "vertPoster", Json.getString(first, "horizonPoster")), "共" + data.size() + "集");
        vod.setVodContent("播放量:" + Json.getString(first, "viewCount", "0") + " 收藏:" + Json.getString(first, "collectionCount", "0") + " 评论:" + Json.getString(first, "commentCount", "0"));
        vod.setVodPlayFrom("围观短剧");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    private String weiguanPlay(String id) throws Exception {
        try {
            JsonObject playSetting = Json.safeObject(id);
            List<String> urls = new ArrayList<>();
            if (playSetting.has("super")) { urls.add("超清"); urls.add(Json.getString(playSetting, "super")); }
            if (playSetting.has("high")) { urls.add("高清"); urls.add(Json.getString(playSetting, "high")); }
            if (playSetting.has("normal")) { urls.add("流畅"); urls.add(Json.getString(playSetting, "normal")); }
            if (urls.isEmpty()) return playUrl0(id);
            return Result.get().parse(0).url(urls).string();
        } catch (Exception e) {
            return playUrl0(id);
        }
    }

    // ==================== 碎片平台 ====================
    private List<Vod> suipianCategory(int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String token = getSuiPianToken();
        Map<String, String> headers = defaultHeaders();
        headers.put("Authorization", token);
        String url = "https://free-api.bighotwind.cc/papaya/papaya-api/videos/page?type=5&tagId=&pageNum=" + page + "&pageSize=24";
        JsonObject res = Json.safeObject(OkHttp.string(url, headers));
        for (JsonElement el : Json.getJsonArray(res, "list")) {
            JsonObject it = el.getAsJsonObject();
            String imageKey = Json.getString(it, "imageKey");
            String pic = imageKey.isEmpty() ? "https://t8.baidu.com/it/u=615012979,225344800&fm=193" : "https://free-api.bighotwind.cc/papaya/papaya-file/files/download/" + imageKey + "/" + Json.getString(it, "imageName", "cover.jpg");
            String epMax = Json.getString(it, "episodesMax");
            String hitNum = Json.getString(it, "hitShowNum");
            videos.add(new Vod(SUIPIAN + "@" + Json.getString(it, "itemId") + "@" + Json.getString(it, "videoCode"),
                    Json.getString(it, "title", "未知剧名"), pic,
                    "碎片剧场 | " + (epMax.isEmpty() ? "" : epMax + "集") + (hitNum.isEmpty() ? "" : " 播放:" + hitNum)));
        }
        return videos;
    }

    private List<Vod> suipianSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String token = getSuiPianToken();
        Map<String, String> headers = defaultHeaders();
        headers.put("Authorization", token);
        String url = "https://free-api.bighotwind.cc/papaya/papaya-api/videos/page?type=5&tagId=&pageNum=" + page + "&pageSize=20&title=" + URLEncoder.encode(wd, "UTF-8");
        JsonObject res = Json.safeObject(OkHttp.string(url, headers));
        for (JsonElement el : Json.getJsonArray(res, "list")) {
            JsonObject it = el.getAsJsonObject();
            String imageKey = Json.getString(it, "imageKey");
            String pic = "https://free-api.bighotwind.cc/papaya/papaya-file/files/download/" + imageKey + "/" + Json.getString(it, "imageName");
            String epMax = Json.getString(it, "episodesMax");
            String hitNum = Json.getString(it, "hitShowNum");
            videos.add(new Vod(SUIPIAN + "@" + Json.getString(it, "itemId") + "@" + Json.getString(it, "videoCode"),
                    Json.getString(it, "title"), pic,
                    "碎片剧场 | " + (epMax.isEmpty() ? "" : epMax + "集") + (hitNum.isEmpty() ? "" : " 播放:" + hitNum)));
        }
        return videos;
    }

    private Vod suipianDetail(String id, String did) throws Exception {
        String[] didParts = did.split("@", 2);
        String itemId = didParts[0];
        String videoCode = didParts.length > 1 ? didParts[1] : "";
        String token = getSuiPianToken();
        Map<String, String> headers = defaultHeaders();
        headers.put("Authorization", token);
        String url = "https://free-api.bighotwind.cc/papaya/papaya-api/videos/info?videoCode=" + videoCode + "&itemId=" + itemId;
        JsonObject res = Json.safeObject(OkHttp.string(url, headers));
        JsonObject data = res.has("data") ? Json.getJsonObject(res, "data") : res;
        JsonArray episodesList = Json.getJsonArray(data, "episodesList");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : episodesList) {
            JsonObject episode = el.getAsJsonObject();
            JsonArray resolutionList = Json.getJsonArray(episode, "resolutionList");
            if (resolutionList.size() == 0) continue;
            // sort by resolution desc
            List<JsonObject> resolutions = new ArrayList<>();
            for (JsonElement re : resolutionList) resolutions.add(re.getAsJsonObject());
            resolutions.sort((a, b) -> Json.getInt(b, "resolution") - Json.getInt(a, "resolution"));
            JsonObject best = resolutions.get(0);
            if (urls.length() > 0) urls.append("#");
            urls.append("第").append(Json.getString(episode, "episodes", "1")).append("集$https://free-api.bighotwind.cc/papaya/papaya-file/files/download/").append(Json.getString(best, "fileKey")).append("/").append(Json.getString(best, "fileName"));
        }
        String pic = "https://free-api.bighotwind.cc/papaya/papaya-file/files/download/" + Json.getString(data, "imageKey") + "/" + Json.getString(data, "imageName");
        Vod vod = new Vod(id, Json.getString(data, "title"), pic, "共" + Json.getString(data, "episodesMax", "0") + "集");
        vod.setVodContent(Json.getString(data, "content", Json.getString(data, "description", "播放量:" + Json.getString(data, "hitShowNum", "0") + " 点赞:" + Json.getString(data, "likeNum", "0"))));
        vod.setVodPlayFrom("碎片剧场");
        vod.setVodPlayUrl(urls.toString());
        return vod;
    }

    // ==================== 河马平台 ====================
    private List<Vod> hemaCategory(int page, String area) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String sub = area.isEmpty() ? "308" : area;
        String tagIds = HEMA_TAG_IDS.getOrDefault(sub, "");
        JsonObject bodys = new JsonObject();
        bodys.addProperty("recSwitch", true);
        bodys.addProperty("channelId", sub);
        bodys.addProperty("tagIds", tagIds);
        bodys.addProperty("cnxhFlag", page - 1);
        bodys.addProperty("playListFlag", true);
        JsonArray watchRecords = new JsonArray();
        watchRecords.add("41000103722_572752006");
        bodys.add("watchRecords", watchRecords);
        String body = hemaEncrypt(bodys.toString());
        String url = "https://freevideo.zqqds.cn/free-video-portal/portal/1121";
        JsonObject res = Json.safeObject(OkHttp.post(url, body, hemaHeaders));
        String dehtml = Json.getString(res, "data");
        if (dehtml.isEmpty()) return videos;
        String hmdata = hemaDecrypt(dehtml);
        if ("{}".equals(hmdata) || hmdata.isEmpty()) return videos;
        JsonObject hm = Json.safeObject(hmdata);
        JsonArray columnData = Json.getJsonArray(hm, "columnData");
        for (JsonElement colEl : columnData) {
            JsonArray videoData = Json.getJsonArray(colEl.getAsJsonObject(), "videoData");
            for (JsonElement vEl : videoData) {
                JsonObject video = vEl.getAsJsonObject();
                videos.add(new Vod(HEMA + "@" + Json.getString(video, "bookId"),
                        Json.getString(video, "bookName"),
                        Json.getString(video, "coverWap", Json.getString(video, "coverCutWap")),
                        "河马短剧 | 更新" + Json.getString(video, "updateNum", "0") + "集"));
            }
        }
        return videos;
    }

    private List<Vod> hemaSearch(String wd, int page) throws Exception {
        List<Vod> videos = new ArrayList<>();
        JsonObject hmbody = new JsonObject();
        hmbody.addProperty("keyword", wd);
        hmbody.addProperty("page", page);
        hmbody.addProperty("size", 20);
        String url = "https://freevideo.zqqds.cn/free-video-portal/portal/1803";
        JsonObject res = Json.safeObject(OkHttp.post(url, hemaEncrypt(hmbody.toString()), hemaHeaders));
        String xmres = Json.getString(res, "data");
        if (xmres.isEmpty()) return videos;
        String dexmres = hemaDecrypt(xmres);
        if ("{}".equals(dexmres) || dexmres.isEmpty()) return videos;
        JsonObject hm = Json.safeObject(dexmres);
        JsonArray searchVos = Json.getJsonArray(hm, "searchVos");
        for (JsonElement el : searchVos) {
            JsonObject video = el.getAsJsonObject();
            videos.add(new Vod(HEMA + "@" + Json.getString(video, "bookId"),
                    Json.getString(video, "bookName"),
                    Json.getString(video, "coverWap") + "@Referer=",
                    "河马短剧 | 共" + Json.getString(video, "updateNum", "0") + "集"));
        }
        return videos;
    }

    private Vod hemaDetail(String id, String did) throws Exception {
        String bookId = did;
        // detail
        JsonObject detailBody = new JsonObject();
        detailBody.addProperty("bookId", bookId);
        String detailResp = OkHttp.post("https://freevideo.zqqds.cn/free-video-portal/portal/1131", hemaEncrypt(detailBody.toString()), hemaHeaders);
        JsonObject detailRes = Json.safeObject(detailResp);
        String detailHtml = Json.getString(detailRes, "data");
        JsonObject videoInfo = Json.getJsonObject(Json.safeObject(hemaDecrypt(detailHtml)), "videoInfo");

        // episode
        JsonObject episodeBody = new JsonObject();
        episodeBody.addProperty("bookId", bookId);
        episodeBody.addProperty("chapterMin", Json.getString(videoInfo, "updateNum", "0"));
        episodeBody.addProperty("chapterMax", Json.getString(videoInfo, "chapterIndex", "0"));
        String episodeResp = OkHttp.post("https://freevideo.zqqds.cn/free-video-portal/portal/1132", hemaEncrypt(episodeBody.toString()), hemaHeaders);
        JsonObject episodeRes = Json.safeObject(episodeResp);
        String episodeHtml = Json.getString(episodeRes, "data");
        JsonArray chapterList = Json.getJsonArray(Json.safeObject(hemaDecrypt(episodeHtml)), "chapterList");
        StringBuilder urls = new StringBuilder();
        for (JsonElement el : chapterList) {
            JsonObject item = el.getAsJsonObject();
            if (urls.length() > 0) urls.append("#");
            urls.append(Json.getString(item, "chapterName")).append("$").append(Json.getString(item, "chapterId")).append("++").append(Json.getString(item, "chapterIndex")).append("++").append(bookId);
        }
        Vod vod = new Vod(id, Json.getString(videoInfo, "bookName", "未知剧名"), Json.getString(videoInfo, "coverWap"), Json.getString(videoInfo, "finishStatusCn", "更新至" + Json.getString(videoInfo, "updateNum", "0") + "集"));
        vod.setVodContent(Json.getString(videoInfo, "introduction", "暂无简介"));
        vod.setVodPlayFrom("河马短剧");
        vod.setVodPlayUrl(urls.length() > 0 ? urls.toString() : "暂无播放地址$0");
        return vod;
    }

    private String hemaPlay(String id) throws Exception {
        String[] arr = id.split("\\+\\+");
        String chapterId = arr.length > 0 ? arr[0] : "";
        String index = arr.length > 1 ? arr[1] : "";
        String bookId = arr.length > 2 ? arr[2] : "";
        JsonObject fsbody = new JsonObject();
        fsbody.addProperty("bookId", bookId);
        fsbody.addProperty("chapterId", chapterId);
        fsbody.addProperty("unClockType", "pay");
        fsbody.addProperty("confirmPay", 2);
        fsbody.addProperty("autoPayFlag", true);
        JsonObject omap = new JsonObject();
        omap.addProperty("channelName", "精选");
        omap.addProperty("logId", "17a6500357709bb2547e1e122b438cfc");
        omap.addProperty("originName", "书城");
        omap.addProperty("recId", "bigdata_rec");
        omap.addProperty("scene", "nsc_727");
        omap.addProperty("sceneId", "dzmf_video_sc_reco");
        omap.addProperty("strategyId", "g6y6b5sq");
        fsbody.add("omap", omap);
        String resp = OkHttp.post("https://freevideo.zqqds.cn/free-video-portal/portal/1133", hemaEncrypt(fsbody.toString()), hemaHeaders);
        JsonObject res = Json.safeObject(resp);
        String fshtml = Json.getString(res, "data");
        if (!fshtml.isEmpty()) {
            String fsdata = hemaDecrypt(fshtml);
            if (!"{}".equals(fsdata) && !fsdata.isEmpty()) {
                JsonObject parsed = Json.safeObject(fsdata);
                String type = Json.getString(parsed, "chaptersPayType");
                if ("免费".equals(type)) {
                    JsonArray data = Json.getJsonArray(parsed, "chapterInfo");
                    if (data.size() > 0) {
                        JsonObject content = Json.getJsonObject(data.get(0).getAsJsonObject(), "content");
                        String url = Json.getString(content, "m3u8720p");
                        if (!url.isEmpty()) return playUrl0(url);
                    }
                }
            }
        }
        String playurl = "https://api.cenguigui.cn/api/duanju/hema.php?book_id=" + bookId + "&video_id=" + chapterId + "&type=mp4";
        return playUrl0(playurl + "#isVideo=true#");
    }

    // ==================== 加密 / 工具函数 ====================

    /** 河马 AES-CBC 加密：hex key/iv，输出大写 hex 字符串 */
    private String hemaEncrypt(String plaintext) throws Exception {
        byte[] keyBytes = Crypto.hexToBytes(HEMA_AES_KEY);
        byte[] ivBytes = Crypto.hexToBytes(HEMA_AES_IV);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encrypted).toUpperCase();
    }

    /** 河马 AES-CBC 解密：输入为 hex 字符串 */
    private String hemaDecrypt(String hexCiphertext) {
        try {
            byte[] keyBytes = Crypto.hexToBytes(HEMA_AES_KEY);
            byte[] ivBytes = Crypto.hexToBytes(HEMA_AES_IV);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Crypto.hexToBytes(hexCiphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "{}";
        }
    }

    /** 碎片 encHex：AES-ECB 加密，输出小写 hex */
    private String encHex(String txt) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(SUIPIAN_AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(txt.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encrypted);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 牛牛加密 POST：替换 body 模板 -> ECB 加密 -> HMAC 签名 -> POST -> ECB 解密响应 */
    private JsonObject niuniuPost(String url, String data, String index) throws Exception {
        String t = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "X9UknYKtLa3DmtjC";
        String body1 = data.replaceAll("&lock_free=\\d+", "&lock_free=1")
                .replaceAll("&timestamp=\\d+", "&timestamp=" + t)
                .replaceAll("&count=\\d+", "&count=1")
                .replaceAll("&index=\\d+", "&index=" + index)
                .replaceAll("&lock_ad=\\d+", "&lock_ad=1")
                .replaceAll("&lock_index=\\d+", "&lock_index=" + index);
        String body2 = Crypto.aesEcbEncrypt(body1, NIUNIU_AES_KEY2);
        String signature = Crypto.hmacSha256(t + nonce + body1, NIUNIU_HMAC_KEY);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Salt", "FD8188A8D5");
        headers.put("X-Nonce", nonce);
        headers.put("X-Timestamp", t);
        headers.put("X-Access-Token", niuniuAccessToken);
        headers.put("X-Signature", signature);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        String html = postUrlEncoded(url, body2, headers);
        String decoded = Crypto.aesEcbDecrypt(html, NIUNIU_AES_KEY2);
        return Json.safeObject(decoded);
    }

    /** 七猫 qm-params 构建与签名 */
    private JsonObject getAndSign() {
        String sessionId = String.valueOf(System.currentTimeMillis());
        JsonObject data = new JsonObject();
        data.addProperty("static_score", "0.8");
        data.addProperty("uuid", "00000000-7fc7-08dc-0000-000000000000");
        data.addProperty("device-id", "20250220125449b9b8cac84c2dd3d035c9052a2572f7dd0122edde3cc42a70");
        data.addProperty("mac", "");
        data.addProperty("sourceuid", "aa7de295aad621a6");
        data.addProperty("refresh-type", "0");
        data.addProperty("model", "22021211RC");
        data.addProperty("wlb-imei", "");
        data.addProperty("client-id", "aa7de295aad621a6");
        data.addProperty("brand", "Redmi");
        data.addProperty("oaid", "");
        data.addProperty("oaid-no-cache", "");
        data.addProperty("sys-ver", "12");
        data.addProperty("trusted-id", "");
        data.addProperty("phone-level", "H");
        data.addProperty("imei", "");
        data.addProperty("wlb-uid", "aa7de295aad621a6");
        data.addProperty("session-id", sessionId);
        String jsonStr = data.toString();
        String base64Str = Crypto.base64Encode(jsonStr);
        StringBuilder qmParams = new StringBuilder();
        for (int i = 0; i < base64Str.length(); i++) {
            String c = String.valueOf(base64Str.charAt(i));
            qmParams.append(CHAR_MAP.getOrDefault(c, c));
        }
        String paramsStr = "AUTHORIZATION=app-version=10001application-id=com.duoduo.readchannel=unknownis-white=net-env=5platform=androidqm-params=" + qmParams + "reg=" + AGG_KEYS;
        String sign = Crypto.md5(paramsStr);
        JsonObject result = new JsonObject();
        result.addProperty("qmParams", qmParams.toString());
        result.addProperty("sign", sign);
        return result;
    }

    /** 七猫请求 headers */
    private Map<String, String> getQiMaoHeaders() {
        JsonObject qs = getAndSign();
        Map<String, String> headers = new HashMap<>(defaultHeaders());
        headers.put("net-env", "5");
        headers.put("reg", "");
        headers.put("channel", "unknown");
        headers.put("is-white", "");
        headers.put("platform", "android");
        headers.put("application-id", "com.duoduo.read");
        headers.put("authorization", "");
        headers.put("app-version", "10001");
        headers.put("user-agent", "webviewversion/0");
        headers.put("qm-params", Json.getString(qs, "qmParams"));
        headers.put("sign", Json.getString(qs, "sign"));
        return headers;
    }

    /** 碎片 token：openId=MD5(guid).substring(0,16)，key=encHex(timestamp) hex */
    private String getSuiPianToken() throws Exception {
        String openId = Crypto.md5(guid()).substring(0, 16);
        String api = "https://free-api.bighotwind.cc/papaya/papaya-api/oauth2/uuid";
        String key = encHex(String.valueOf(System.currentTimeMillis()));
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("key", key);
        JsonObject body = new JsonObject();
        body.addProperty("openId", openId);
        String resp = OkHttp.post(api, body.toString(), headers);
        JsonObject res = Json.safeObject(resp);
        return Json.getString(Json.getJsonObject(res, "data"), "token");
    }

    /** 生成 GUID 字符串 */
    private String guid() {
        char[] chars = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".toCharArray();
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            if (c == 'x' || c == 'y') {
                int r = (int) (Math.random() * 16);
                int v = c == 'x' ? r : (r & 0x3 | 0x8);
                sb.append(Integer.toHexString(v));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== 辅助工具方法 ====================

    /** 判断平台是否应被跳过（在首页/分类/搜索中过滤） */
    private boolean isSkipPlat(String platformId) {
        for (String word : CATE_REMOVE) {
            if (Pattern.compile(word, Pattern.CASE_INSENSITIVE).matcher(platformId).find()) return true;
        }
        return false;
    }

    /** 获取过滤后的平台列表 */
    private List<String[]> getPlatList() {
        List<String[]> list = new ArrayList<>();
        for (String[] item : PLATFORM_LIST) {
            if (!isSkipPlat(item[1])) list.add(item);
        }
        return list;
    }

    /** 构建平台筛选器 */
    private List<Filter> buildFilter(String platformId) {
        List<Filter.Value> values = new ArrayList<>();
        switch (platformId) {
            case TIANQUAN:
                for (String[] kv : new String[][]{{"逆袭", "逆袭"}, {"霸总", "霸总"}, {"现代言情", "现代言情"}, {"打脸虐渣", "打脸虐渣"}, {"豪门恩怨", "豪门恩怨"}, {"神豪", "神豪"}, {"马甲", "马甲"}, {"都市日常", "都市日常"}, {"战神归来", "战神归来"}, {"穿越", "穿越"}, {"重生", "重生"}, {"闪婚", "闪婚"}, {"虐恋", "虐恋"}, {"追妻", "追妻"}})
                    values.add(new Filter.Value(kv[0], kv[1]));
                break;
            case JINLI:
                values.add(new Filter.Value("全部", ""));
                values.add(new Filter.Value("情感关系", "1"));
                values.add(new Filter.Value("成长逆袭", "2"));
                values.add(new Filter.Value("奇幻异能", "3"));
                values.add(new Filter.Value("战斗热血", "4"));
                values.add(new Filter.Value("伦理现实", "5"));
                values.add(new Filter.Value("时空穿越", "6"));
                values.add(new Filter.Value("权谋身份", "7"));
                break;
            case FANQIE:
                values.add(new Filter.Value("热剧", "videoseries_hot"));
                values.add(new Filter.Value("新剧", "firstonlinetime_new"));
                values.add(new Filter.Value("逆袭", "cate_739"));
                values.add(new Filter.Value("总裁", "cate_29"));
                values.add(new Filter.Value("现言", "cate_3"));
                values.add(new Filter.Value("打脸", "cate_1051"));
                values.add(new Filter.Value("马甲", "cate_266"));
                values.add(new Filter.Value("豪门", "cate_1053"));
                values.add(new Filter.Value("都市", "cate_261"));
                values.add(new Filter.Value("神豪", "cate_20"));
                break;
            case XINGYA:
                values.add(new Filter.Value("剧场", "1"));
                values.add(new Filter.Value("热播剧", "2"));
                values.add(new Filter.Value("会员专享", "8"));
                values.add(new Filter.Value("星选好剧", "7"));
                values.add(new Filter.Value("新剧", "3"));
                values.add(new Filter.Value("阳光剧场", "5"));
                break;
            case XIFAN:
                values.add(new Filter.Value("全部", ""));
                values.add(new Filter.Value("都市", "68@都市"));
                values.add(new Filter.Value("青春", "68@青春"));
                values.add(new Filter.Value("现代言情", "81@现代言情"));
                values.add(new Filter.Value("豪门", "81@豪门"));
                values.add(new Filter.Value("大女主", "80@大女主"));
                values.add(new Filter.Value("逆袭", "79@逆袭"));
                values.add(new Filter.Value("打脸虐渣", "79@打脸虐渣"));
                values.add(new Filter.Value("穿越", "81@穿越"));
                break;
            case RUANYA:
                values.add(new Filter.Value("全部", ""));
                values.add(new Filter.Value("战神", "战神"));
                values.add(new Filter.Value("逆袭", "逆袭"));
                values.add(new Filter.Value("霸总", "霸总"));
                values.add(new Filter.Value("神豪", "神豪"));
                values.add(new Filter.Value("都市", "都市"));
                values.add(new Filter.Value("玄幻", "玄幻"));
                values.add(new Filter.Value("言情", "言情"));
                break;
            case QIMAO:
                values.add(new Filter.Value("全部", ""));
                values.add(new Filter.Value("推荐", "0"));
                values.add(new Filter.Value("新剧", "-1"));
                values.add(new Filter.Value("都市情感", "1273"));
                values.add(new Filter.Value("古装", "1272"));
                values.add(new Filter.Value("都市", "571"));
                values.add(new Filter.Value("玄幻仙侠", "1286"));
                values.add(new Filter.Value("奇幻", "570"));
                values.add(new Filter.Value("逆袭", "400"));
                values.add(new Filter.Value("穿越", "373"));
                values.add(new Filter.Value("重生", "784"));
                values.add(new Filter.Value("闪婚", "480"));
                values.add(new Filter.Value("战神", "527"));
                values.add(new Filter.Value("赘婿", "36"));
                values.add(new Filter.Value("神医", "1269"));
                values.add(new Filter.Value("神豪", "37"));
                break;
            case NIUNIU:
                values.add(new Filter.Value("全部", ""));
                values.add(new Filter.Value("现言", "现言"));
                values.add(new Filter.Value("古言", "古言"));
                values.add(new Filter.Value("历史", "历史"));
                values.add(new Filter.Value("都市", "都市"));
                values.add(new Filter.Value("逆袭", "逆袭"));
                values.add(new Filter.Value("豪门", "豪门"));
                values.add(new Filter.Value("战神", "战神"));
                values.add(new Filter.Value("甜宠", "甜宠"));
                values.add(new Filter.Value("穿越", "穿越"));
                values.add(new Filter.Value("古装", "古装"));
                values.add(new Filter.Value("虐心", "虐心"));
                values.add(new Filter.Value("神医", "神医"));
                values.add(new Filter.Value("赘婿", "赘婿"));
                break;
            case BAIDU:
                values.add(new Filter.Value("新剧", "新剧"));
                values.add(new Filter.Value("限时免费", "限时免费"));
                values.add(new Filter.Value("精选", "精选"));
                values.add(new Filter.Value("独播", "独播"));
                values.add(new Filter.Value("全部", "全部题材"));
                values.add(new Filter.Value("神医", "神医"));
                values.add(new Filter.Value("都市", "都市"));
                values.add(new Filter.Value("现代言情", "现代言情"));
                values.add(new Filter.Value("异能", "异能"));
                values.add(new Filter.Value("逆袭", "逆袭"));
                values.add(new Filter.Value("甜宠", "甜宠"));
                values.add(new Filter.Value("总裁", "总裁"));
                values.add(new Filter.Value("战神", "战神"));
                values.add(new Filter.Value("神豪", "神豪"));
                values.add(new Filter.Value("虐恋", "虐恋"));
                values.add(new Filter.Value("闪婚", "闪婚"));
                values.add(new Filter.Value("玄幻", "玄幻"));
                values.add(new Filter.Value("穿越重生", "穿越重生"));
                break;
            case HEMA:
                values.add(new Filter.Value("推荐", "308"));
                values.add(new Filter.Value("新剧", "309"));
                values.add(new Filter.Value("逆袭", "310"));
                values.add(new Filter.Value("恋爱", "311"));
                values.add(new Filter.Value("强者回归", "312"));
                values.add(new Filter.Value("豪门恩怨", "313"));
                values.add(new Filter.Value("古装", "314"));
                values.add(new Filter.Value("重生", "315"));
                values.add(new Filter.Value("萌宝", "316"));
                values.add(new Filter.Value("复仇", "317"));
                values.add(new Filter.Value("神医", "318"));
                values.add(new Filter.Value("高手下山", "319"));
                values.add(new Filter.Value("神豪", "322"));
                values.add(new Filter.Value("民国", "323"));
                break;
            default:
                values.add(new Filter.Value("全部", ""));
                break;
        }
        if (values.isEmpty()) return new ArrayList<>();
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("area", "分类", values));
        return filters;
    }

    /** 把多个字符串转为 JsonArray */
    private JsonArray stringArray(String... values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    /** 返回 parse=0, url=... 的播放结果 */
    private String playUrl0(String url) {
        return Result.get().parse(0).url(url).string();
    }

    /** 以 application/x-www-form-urlencoded 形式 POST 原始字符串（用于牛牛等 API，body 不是 key=value 格式） */
    private String postUrlEncoded(String url, String body, Map<String, String> headers) {
        try {
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), body);
            Request.Builder builder = new Request.Builder().url(url).post(requestBody);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            Response response = OkHttp.client().newCall(builder.build()).execute();
            return response.body().string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}
