package com.github.catvod.spider;

import android.content.Context;
import android.os.Build;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * DJHub aggregation spider for multiple short-drama sources.
 * Delegates requests to sub-spiders: WeiguanDJ, Hema, HHkk, Qmdj, Xingya, plus the Xifan (西饭) API.
 */
public class DJHub extends Spider {

    private static final String XIFAN_SEARCH_URL = "https://xifan-api-cn.youlishipin.com/xifan/search/getSearchList?reqType=search&offset=0&keyword=";
    private static final String XIFAN_DETAIL_URL = "https://xifan-api-cn.youlishipin.com/xifan/drama/getDuanjuInfo?duanjuId=";
    private static final String WEIGUAN_DETAIL_URL = "https://api.drama.9ddm.com/drama/home/shortVideoDetail";
    private static final String WEIGUAN_PARAM_PREFIX = "?version_code=1600&version_name=1.6.0&device_name=";
    private static final String WEIGUAN_PARAM_MIDDLE = "&device_type=phone&is_first_day=true&is_first_24h=true&app_launch_way=icon&default_homepage=homepage_interaction&device_owning_firm=";
    private static final String WEIGUAN_PARAM_SUFFIX = "&font_scale=default&os_type=1&clientInfo=";
    private static final String WEIGUAN_TAIL = "&page=1&pageSize=1000&userId=0&queryAll=true";

    private static final String UA_OKHTTP_3 = "okhttp/3.12.11";
    private static final String UA_OKHTTP_5 = "okhttp/5.1.0";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int HOME_LIMIT = 36;
    private static final int FILTER_LIMIT_HHKK = 40;

    public HHkk hhkk;
    public Hema hema;
    public Qmdj qmdj;
    public WeiguanDJ weiguanDJ;
    public Xingya xingya;
    public String hhkkTid;
    public String hemaTid;
    public String clientInfo;

    public DJHub() {
        this.hhkkTid = "";
        this.hemaTid = "";
        this.clientInfo = "";
    }

    private static ArrayList<Vod> mergeWithLimit(ArrayList<Vod> list, HashSet<String> seen, Supplier<List<Vod>> supplier) {
        if (list.size() >= HOME_LIMIT) {
            return list;
        }
        try {
            List<Vod> supplied = supplier.get();
            for (Object obj : supplied) {
                Vod vod = (Vod) obj;
                if (vod == null) continue;
                String vodId = vod.getVodId();
                if (TextUtils.isEmpty(vodId)) continue;
                if (!seen.add(vodId)) continue;
                list.add(vod);
                if (list.size() >= HOME_LIMIT) {
                    return list;
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static ArrayList<Vod> xifanSearch(int page, String keyword) {
        if (page > 1) {
            return new ArrayList<>();
        }
        StringBuilder sb = new StringBuilder(XIFAN_SEARCH_URL);
        sb.append(urlEncode(keyword));
        sb.append("&quickEngineVersion=-1&scene=");
        String url = sb.toString();
        HashMap<String, String> headers = buildXifanHeaders();
        try {
            JSONObject resp = new JSONObject(OkHttp.string(url, null, headers));
            return parseXifanSearch(keyword, resp);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Vod xifanDetail(String vodId, String sourceId) {
        String[] parts = sourceId.split("#", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("bad Xifan id");
        }
        StringBuilder sb = new StringBuilder(XIFAN_DETAIL_URL);
        sb.append(urlEncode(parts[0]));
        sb.append("&source=");
        sb.append(urlEncode(parts[1]));
        String url = sb.toString();
        HashMap<String, String> headers = buildXifanHeaders();
        try {
            JSONObject resp = new JSONObject(OkHttp.string(url, null, headers));
            JSONObject result = resp.getJSONObject("result");
            StringBuilder remark = new StringBuilder();
            remark.append(result.optInt("total")).append("集 ");
            String status = result.optString("updateStatus");
            remark.append("over".equals(status) ? "已完结" : "更新中");
            Vod vod = new Vod(vodId, result.optString("title"), result.optString("coverImageUrl"), remark.toString());
            JSONArray episodeList = result.optJSONArray("episodeList");
            ArrayList<String> episodes = new ArrayList<>();
            if (episodeList != null) {
                for (int i = 0; i < episodeList.length(); i++) {
                    JSONObject ep = episodeList.optJSONObject(i);
                    if (ep == null) continue;
                    String playUrl = ep.optString("playUrl");
                    if (TextUtils.isEmpty(playUrl)) continue;
                    int idx = ep.optInt("index", i + 1);
                    if (idx <= 0) idx = i + 1;
                    episodes.add(new StringBuilder("第").append(idx).append("集$").append(playUrl).toString());
                }
            }
            vod.setVodPlayFrom("西饭短剧");
            vod.setVodPlayUrl(TextUtils.join("#", episodes));
            return vod;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String urlEncode(String s) {
        if (s == null) return "";
        try {
            String encoded = URLEncoder.encode(s, StandardCharsets.UTF_8.name());
            return encoded.replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    private static Filter buildFilter(String key, String name, String[] pairs) {
        ArrayList<Filter.Value> values = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.add(new Filter.Value(pairs[i], pairs[i + 1]));
        }
        return new Filter(key, name, values);
    }

    private static void mergeSearch(ArrayList<Vod> list, HashSet<String> seen, String source, Supplier<List<Vod>> supplier) {
        try {
            List<Vod> supplied = supplier.get();
            ArrayList<Vod> marked = markSource(source, supplied);
            for (Vod vod : marked) {
                if (vod == null) continue;
                String vodId = vod.getVodId();
                if (TextUtils.isEmpty(vodId)) continue;
                if (!seen.add(vodId)) continue;
                list.add(vod);
            }
        } catch (Exception ignored) {
        }
    }

    private static ArrayList<Vod> parseXifanSearch(String keyword, JSONObject resp) {
        ArrayList<Vod> list = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        JSONObject result = resp.optJSONObject("result");
        JSONArray elements = result != null ? result.optJSONArray("elements") : null;
        if (elements == null) {
            return list;
        }
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.optJSONObject(i);
            if (element == null) continue;
            JSONArray contents = element.optJSONArray("contents");
            if (contents == null) {
                contents = new JSONArray();
                contents.put(element);
            }
            for (int j = 0; j < contents.length(); j++) {
                JSONObject content = contents.optJSONObject(j);
                if (content == null) continue;
                JSONObject duanjuVo = content.optJSONObject("duanjuVo");
                if (duanjuVo == null) continue;
                String duanjuId = duanjuVo.optString("duanjuId");
                if (TextUtils.isEmpty(duanjuId)) continue;
                if (!TextUtils.isEmpty(keyword)) {
                    JSONArray categories = duanjuVo.optJSONArray("categories");
                    boolean matchFound = false;
                    if (categories != null && categories.length() > 0) {
                        for (int k = 0; k < categories.length(); k++) {
                            if (keyword.equals(categories.optString(k))) {
                                matchFound = true;
                                break;
                            }
                        }
                    } else {
                        matchFound = true;
                    }
                    if (!matchFound) continue;
                }
                String id = new StringBuilder("西饭@").append(duanjuId).append("#").append(duanjuVo.optString("source")).toString();
                if (!seen.add(id)) continue;
                String total = duanjuVo.optString("total");
                if (TextUtils.isEmpty(total)) {
                    total = String.valueOf(duanjuVo.optInt("total", 0));
                }
                if (!TextUtils.isEmpty(total) && !"0".equals(total) && !total.endsWith("集")) {
                    total = total.concat("集");
                }
                list.add(new Vod(id, duanjuVo.optString("title"), duanjuVo.optString("coverImageUrl"), total));
            }
        }
        return list;
    }

    private static String getCateId(String defaultTid, HashMap<String, String> extend) {
        String area = extend != null ? extend.get("area") : null;
        return area != null ? area : defaultTid;
    }

    private static ArrayList<Vod> markSource(String source, List<Vod> list) {
        ArrayList<Vod> result = new ArrayList<>();
        if (list == null) return result;
        String prefix = source.concat("@");
        for (Object obj : list) {
            Vod vod = (Vod) obj;
            if (vod == null) continue;
            String vodId = vod.getVodId();
            if (TextUtils.isEmpty(vodId)) continue;
            if (!vodId.startsWith(prefix) && !vodId.contains("@")) {
                vod.setVodId(prefix.concat(vodId));
            }
            result.add(vod);
        }
        return result;
    }

    private static String buildPlayFrom(String source, String playFrom) {
        if (TextUtils.isEmpty(playFrom)) {
            return source.concat("短剧");
        }
        String[] parts = playFrom.split("\\$\\$\\$", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (sb.length() > 0) {
                sb.append("$$$");
            }
            if (TextUtils.isEmpty(parts[i])) {
                sb.append(source).append("短剧");
            } else if (!parts[i].contains(source)) {
                sb.append(source).append(parts[i]);
            } else {
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }

    private static String stripPrefix(String s, String prefix) {
        if (TextUtils.isEmpty(s)) return s;
        if (s.startsWith(prefix) && s.length() > prefix.length()) {
            return s.substring(prefix.length());
        }
        return s;
    }

    private static HashMap<String, String> buildXifanHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA_OKHTTP_3);
        headers.put("Content-Type", CONTENT_TYPE_JSON);
        return headers;
    }

    private Vod weiguanDetail(String vodId, String oneId) {
        StringBuilder urlBuilder = new StringBuilder(WEIGUAN_DETAIL_URL);
        String deviceName = Build.MODEL;
        if (TextUtils.isEmpty(deviceName)) deviceName = "Pixel";
        String deviceBrand = Build.BRAND;
        if (TextUtils.isEmpty(deviceBrand)) deviceBrand = "Google";
        StringBuilder paramBuilder = new StringBuilder(WEIGUAN_PARAM_PREFIX);
        paramBuilder.append(urlEncode(deviceName));
        paramBuilder.append(WEIGUAN_PARAM_MIDDLE);
        paramBuilder.append(urlEncode(deviceBrand));
        paramBuilder.append(WEIGUAN_PARAM_SUFFIX);
        paramBuilder.append(clientInfo);
        urlBuilder.append(paramBuilder.toString());
        urlBuilder.append("&oneId=");
        urlBuilder.append(urlEncode(oneId));
        urlBuilder.append(WEIGUAN_TAIL);
        String url = urlBuilder.toString();
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA_OKHTTP_5);
        headers.put("Content-Type", CONTENT_TYPE_JSON);
        try {
            JSONObject resp = new JSONObject(OkHttp.string(url, null, headers));
            JSONArray data = resp.optJSONArray("data");
            LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
            int episodeCount = 0;
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject episode = data.optJSONObject(i);
                    if (episode == null) continue;
                    episodeCount++;
                    String playOrder = episode.optString("playOrder");
                    if (TextUtils.isEmpty(playOrder)) {
                        playOrder = String.valueOf(episode.optInt("playOrder", i + 1));
                    }
                    if (TextUtils.isEmpty(playOrder) || "0".equals(playOrder)) {
                        playOrder = String.valueOf(i + 1);
                    }
                    JSONArray clarityList = episode.optJSONArray("videoClarityList");
                    if (clarityList == null) continue;
                    for (int j = 0; j < clarityList.length(); j++) {
                        JSONObject clarity = clarityList.optJSONObject(j);
                        if (clarity == null) continue;
                        String videoUrl = clarity.optString("url");
                        if (TextUtils.isEmpty(videoUrl)) continue;
                        String name = clarity.optString("name", "默认");
                        if (TextUtils.isEmpty(name)) name = "默认";
                        List<String> eps = playMap.get(name);
                        if (eps == null) {
                            eps = new ArrayList<>();
                            playMap.put(name, eps);
                        }
                        eps.add(new StringBuilder(playOrder).append("$围观@").append(videoUrl).toString());
                    }
                }
            }
            Vod vod = new Vod(vodId, resp.optString("title"), resp.optString("vertPoster"), new StringBuilder("共").append(episodeCount).append("集").toString());
            vod.setVodContent(resp.optString("description"));
            if (!playMap.isEmpty()) {
                StringBuilder fromBuilder = new StringBuilder();
                StringBuilder urlStrBuilder = new StringBuilder();
                for (Map.Entry<String, List<String>> entry : playMap.entrySet()) {
                    if (fromBuilder.length() > 0) {
                        fromBuilder.append("$$$");
                        urlStrBuilder.append("$$$");
                    }
                    fromBuilder.append(entry.getKey());
                    urlStrBuilder.append(TextUtils.join("#", entry.getValue()));
                }
                vod.setVodPlayFrom(fromBuilder.toString());
                vod.setVodPlayUrl(urlStrBuilder.toString());
            }
            return vod;
        } catch (Exception e) {
            return new Vod(vodId, vodId, "", "");
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception e) {
            page = 1;
        }
        String pgStr = String.valueOf(page);
        ArrayList<Vod> list;
        try {
            switch (tid) {
                case "七猫": {
                    String cateId = getCateId("0", extend);
                    String json = qmdj.categoryContent(cateId, pgStr, filter, extend);
                    list = parseVodList(json);
                    list = markSource("七猫", list);
                    break;
                }
                case "围观": {
                    String cateId = getCateId("全部", extend);
                    String json = weiguanDJ.categoryContent(cateId, pgStr, filter, extend);
                    list = parseVodList(json);
                    list = markSource("围观", list);
                    break;
                }
                case "好看": {
                    String cateId = getCateId("都市", extend);
                    list = xifanSearch(page, cateId);
                    break;
                }
                case "星芽": {
                    String cateId = getCateId("1", extend);
                    String json = xingya.categoryContent(cateId, String.valueOf(page), filter, extend);
                    list = parseVodList(json);
                    list = markSource("星芽", list);
                    break;
                }
                case "河马": {
                    String cateId = getCateId(hemaTid, extend);
                    String json = hema.categoryContent(cateId, pgStr, filter, extend);
                    list = parseVodList(json);
                    list = markSource("河马", list);
                    break;
                }
                case "西饭": {
                    String cateId = getCateId(hhkkTid, extend);
                    if (TextUtils.isEmpty(cateId)) cateId = hhkkTid;
                    String json = hhkk.categoryContent(cateId, pgStr, filter, extend);
                    list = parseVodList(json);
                    list = markSource("西饭", list);
                    break;
                }
                default:
                    list = new ArrayList<>();
                    break;
            }
            int totalPage = list.isEmpty() ? page : page + 1;
            int limit = Math.max(1, list.size());
            int total = list.size();
            return Result.get().vod(list).page(page, totalPage, limit, total).string();
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).page(page, 1, 0, 0).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        Vod vod;
        if (TextUtils.isEmpty(id) || !id.contains("@")) {
            vod = new Vod(id, id, "", "");
            return Result.string(vod);
        }
        int atIdx = id.indexOf("@");
        String[] parts = new String[]{id.substring(0, atIdx), id.substring(atIdx + 1)};
        String source = parts[0];
        String realId = parts[1];
        try {
            Spider subSpider = null;
            switch (source) {
                case "西饭":
                    vod = xifanDetail(id, realId);
                    return Result.string(vod);
                case "围观":
                    vod = weiguanDetail(id, realId);
                    return Result.string(vod);
                case "七猫":
                    subSpider = qmdj;
                    break;
                case "围观短剧":
                case "围观短剧_":
                    subSpider = weiguanDJ;
                    break;
                case "好看":
                    subSpider = hhkk;
                    break;
                case "星芽":
                    subSpider = xingya;
                    break;
                case "河马":
                    subSpider = hema;
                    break;
                default:
                    break;
            }
            if (subSpider == null) {
                vod = new Vod(id, id, "", "");
                return Result.string(vod);
            }
            String detailJson = subSpider.detailContent(Collections.singletonList(realId));
            List<Vod> vodList = Result.objectFrom(detailJson).getList();
            if (vodList.isEmpty()) {
                vod = new Vod(id, id, "", "");
                return Result.string(vod);
            }
            vod = vodList.get(0);
            vod.setVodId(id);
            vod.setVodPlayFrom(buildPlayFrom(source, vod.getVodPlayFrom()));
            return Result.string(vod);
        } catch (Exception e) {
            vod = new Vod(id, id, "", "加载失败");
            return Result.string(vod);
        }
    }

    private ArrayList<Vod> parseVodList(String json) {
        if (TextUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Result.objectFrom(json).getList());
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("围观", "围观短剧"));
        classes.add(new Class("河马", "河马短剧"));
        classes.add(new Class("好看", "好看短剧"));
        classes.add(new Class("七猫", "七猫短剧"));
        classes.add(new Class("星芽", "星芽短剧"));
        classes.add(new Class("西饭", "西饭短剧"));
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        String areaKey = "area";
        String classifyName = "分类";

        Filter qmdjAreaFilter = buildFilter(areaKey, classifyName, new String[]{
                "全部", "0", "男频", "1", "新剧", "3", "现代言情", "21", "神豪", "37",
                "萌宝", "356", "穿越", "373", "战神", "527", "神医", "1269", "古装", "1272"
        });
        filters.put("七猫", Collections.singletonList(qmdjAreaFilter));

        Filter[] xingyaFilters = new Filter[]{
                buildFilter(areaKey, classifyName, new String[]{
                        "剧场", "1", "热播短剧", "2", "会员专享", "8", "星选好剧", "7",
                        "新剧", "3", "阳光剧场", "5"
                }),
                buildFilter("class2", "类型", new String[]{
                        "全部", "0", "都市", "4", "逆袭", "7", "古装", "1272", "穿越", "373",
                        "亲情", "41", "现代言情", "15", "重生", "6", "虐恋", "8", "玄幻", "35",
                        "战神", "17", "脑洞", "32", "甜宠", "33", "古代言情", "37", "神医", "24",
                        "历史", "40", "赘婿", "26", "萌宝", "9", "神豪", "25"
                }),
                buildFilter("rank", "榜单", new String[]{
                        "实时热榜", "1", "热搜榜", "2", "新剧榜", "3", "剧单榜", "4", "口碑榜", "5"
                })
        };
        filters.put("星芽", Arrays.asList(xingyaFilters));

        Filter xifanAreaFilter = buildFilter(areaKey, classifyName, new String[]{
                "全部", "", "全部", "0", "都市", "4", "逆袭", "7", "古装", "1272", "穿越", "373",
                "家庭", "家庭", "复仇", "复仇", "现代言情", "现代言情", "悬疑", "悬疑",
                "爱情", "爱情", "战神", "战神", "神医", "神医", "神豪", "神豪",
                "萌宝", "萌宝", "职场", "职场", "古代言情", "古代言情", "虐恋", "虐恋"
        });
        filters.put("西饭", Collections.singletonList(xifanAreaFilter));

        Filter weiguanAreaFilter = buildFilter(areaKey, classifyName, new String[]{
                "全部", "", "全部", "0", "都市", "4", "逆袭", "7", "古装", "1272", "穿越", "373",
                "家庭", "家庭", "复仇", "复仇", "现代言情", "现代言情", "悬疑", "悬疑",
                "爱情", "爱情", "战神", "战神", "神医", "神医", "神豪", "神豪",
                "萌宝", "萌宝", "职场", "职场", "古代言情", "古代言情", "虐恋", "虐恋"
        });
        filters.put("围观", Collections.singletonList(weiguanAreaFilter));

        ArrayList<Filter.Value> hemaValues = new ArrayList<>();
        try {
            JSONObject hemaHome = new JSONObject(hema.homeContent(filter));
            JSONArray hemaClass = hemaHome.optJSONArray("class");
            if (hemaClass != null) {
                for (int i = 0; i < hemaClass.length(); i++) {
                    JSONObject item = hemaClass.optJSONObject(i);
                    if (item == null) continue;
                    hemaValues.add(new Filter.Value(item.optString("type_name"), item.optString("type_id")));
                }
            }
        } catch (Exception ignored) {
        }
        if (hemaValues.isEmpty()) {
            hemaValues.add(new Filter.Value("推荐", hemaTid));
        }
        filters.put("河马", Collections.singletonList(new Filter(areaKey, classifyName, hemaValues)));

        ArrayList<Filter.Value> hhkkValues = new ArrayList<>();
        String hhkkFallback = TextUtils.isEmpty(hhkkTid) ? "" : hhkkTid;
        hhkkValues.add(new Filter.Value("推荐", hhkkFallback));
        try {
            JSONObject hhkkHome = new JSONObject(hhkk.homeContent(filter));
            JSONArray hhkkClass = hhkkHome.optJSONArray("class");
            if (hhkkClass != null) {
                for (int i = 0; i < hhkkClass.length() && i < FILTER_LIMIT_HHKK; i++) {
                    JSONObject item = hhkkClass.optJSONObject(i);
                    if (item == null) continue;
                    hhkkValues.add(new Filter.Value(item.optString("type_name"), item.optString("type_id")));
                }
            }
        } catch (Exception ignored) {
        }
        filters.put("好看", Collections.singletonList(new Filter(areaKey, classifyName, hhkkValues)));

        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        mergeWithLimit(list, seen, () -> {
            try {
                return Result.objectFrom(hema.homeContent(false)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeWithLimit(list, seen, () -> {
            try {
                return Result.objectFrom(hhkk.homeContent(false)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeWithLimit(list, seen, () -> {
            try {
                return Result.objectFrom(qmdj.homeContent(false)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeWithLimit(list, seen, () -> {
            try {
                return Result.objectFrom(weiguanDJ.homeContent(false)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeWithLimit(list, seen, () -> {
            try {
                return Result.objectFrom(xingya.homeContent(false)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        if (list.size() > HOME_LIMIT) {
            list = new ArrayList<>(list.subList(0, HOME_LIMIT));
        }
        return Result.get().vod(list).string();
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        weiguanDJ = new WeiguanDJ();
        hema = new Hema();
        hhkk = new HHkk();
        qmdj = new Qmdj();
        xingya = new Xingya();
        weiguanDJ.init(context, "");
        hema.init(context, "");
        hhkk.init(context, "");
        qmdj.init(context, "");
        xingya.init(context, "");

        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            clientInfo = hex.toString();
        } catch (Exception e) {
            clientInfo = sb.toString();
        }

        try {
            JSONObject hemaHome = new JSONObject(hema.homeContent(true));
            JSONArray hemaClass = hemaHome.optJSONArray("class");
            if (hemaClass != null && hemaClass.length() > 0) {
                hemaTid = hemaClass.optJSONObject(0).optString("type_id");
            }
        } catch (Exception ignored) {
        }
        try {
            JSONObject hhkkHome = new JSONObject(hhkk.homeContent(true));
            JSONArray hhkkClass = hhkkHome.optJSONArray("class");
            if (hhkkClass != null && hhkkClass.length() > 0) {
                hhkkTid = hhkkClass.optJSONObject(0).optString("type_id");
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            if (!TextUtils.isEmpty(id) && id.startsWith("围观@")) {
                String url = id.substring(3);
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", UA_OKHTTP_5);
                headers.put("Content-Type", CONTENT_TYPE_JSON);
                return Result.get().url(url).header(headers).parse(0).string();
            }
            if (!TextUtils.isEmpty(flag) && flag.contains("河马")) {
                String newFlag = stripPrefix(flag, "河马");
                return hema.playerContent(newFlag, id, vipFlags);
            }
            if (!TextUtils.isEmpty(flag) && flag.contains("围观")) {
                return weiguanDJ.playerContent(flag, id, vipFlags);
            }
            if (!TextUtils.isEmpty(flag) && flag.contains("好看")) {
                String newFlag = stripPrefix(flag, "好看");
                return hhkk.playerContent(newFlag, id, vipFlags);
            }
            if (!TextUtils.isEmpty(flag) && flag.contains("西饭")) {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", UA_OKHTTP_3);
                headers.put("Content-Type", CONTENT_TYPE_JSON);
                return Result.get().url(id).header(headers).parse(0).string();
            }
            if (!TextUtils.isEmpty(id) && (id.startsWith("http://") || id.startsWith("https://"))) {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "okhttp/4.10.0");
                return Result.get().url(id).header(headers).parse(0).string();
            }
            if (!TextUtils.isEmpty(id) && id.contains("@") && !id.startsWith("http")) {
                String newFlag = TextUtils.isEmpty(flag) ? "" : stripPrefix(flag, "好看");
                return hhkk.playerContent(newFlag, id, vipFlags);
            }
            return Result.get().url(id).parse(0).string();
        } catch (Exception e) {
            return Result.get().url("").msg("解析失败").string();
        }
    }

    /**
     * Hema Spider - 视频资源爬虫
     * 对应 smali 文件：com/github/catvod/spider/Hema.smali
     */
    private static class Hema extends Spider {

        // API 端点（从 smali 解密）
        private static final String API_BASE_URL = "/free-video-portal/portal/1125";

        // 构造函数
        public Hema() {
        }

        /**
         * HTTP POST 请求辅助方法
         * @param url API 端点
         * @param params JSON 参数
         * @return 响应 JSON 对象
         */
        private JSONObject postRequest(String url, JSONObject params) throws Exception {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("User-Agent", "okhttp/4.9.3");

            String response = OkHttp.post(url, params.toString(), headers);
            if (TextUtils.isEmpty(response)) {
                return new JSONObject();
            }
            return new JSONObject(response);
        }

        /**
         * 从 Object 中提取 URL（查找以 "http" 开头的字符串）
         */
        public static String a(Object obj) {
            String httpPrefix = "http"; // "http"

            if (obj instanceof String) {
                String str = (String) obj;
                if (str.startsWith(httpPrefix)) {
                    return str;
                }
            } else if (obj instanceof JSONArray) {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); i++) {
                    String str = array.optString(i);
                    if (str.startsWith(httpPrefix)) {
                        return str;
                    }
                }
            }

            return "";
        }

        /**
         * 将 JSONArray 转换为 Vod 列表
         */
        public static ArrayList<Vod> b(JSONArray array) {
            ArrayList<Vod> list = new ArrayList<>();
            if (array == null) return list;

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject item = array.getJSONObject(i);

                    String vodId = item.optString("bookId"); // "vod_id"
                    String vodName = item.optString("bookName"); // "vod_name"
                    String vodPic = item.optString("coverWap"); // "vod_pic"
                    String vodRemarks = item.optString("finishStatusCn"); // "vod_remarks"
                    String vodPlayUrl = item.optString("updateNum"); // "vod_play_url"

                    // 处理播放 URL
                    if (!TextUtils.isEmpty(vodPlayUrl)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(vodPlayUrl);
                        sb.append("集"); // "://"
                        sb.append(item.optString("updateNum")); // "vod_play_url"
                        vodPlayUrl = sb.toString();
                    }

                    String vodYear = item.optString("videoStarsNum"); // "vod_year"

                    Vod vod = new Vod(vodId, vodName, vodPic, vodRemarks);

                    if (!TextUtils.isEmpty(vodYear)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(vodYear);
                        sb.append("分"); // "年"
                        vod.setVodYear(sb.toString());
                    }

                    list.add(vod);
                } catch (Exception e) {
                    // 忽略解析错误，继续下一项
                }
            }

            return list;
        }

        /**
         * 从 JSONObject 中提取视频列表
         */
        public static ArrayList<Vod> c(JSONObject data) {
            ArrayList<Vod> list = new ArrayList<>();

            JSONArray modules = data.optJSONArray("columnData"); // "vod_list_modules"
            String vodListKey = "videoData"; // "vod_list"

            if (modules != null) {
                for (int i = 0; i < modules.length(); i++) {
                    JSONObject module = modules.optJSONObject(i);
                    if (module == null) continue;

                    JSONArray vodArray = module.optJSONArray(vodListKey);
                    if (vodArray != null) {
                        list.addAll(b(vodArray));
                    }
                }
            }

            if (list.isEmpty()) {
                JSONArray vodArray = data.optJSONArray(vodListKey);
                if (vodArray != null) {
                    list.addAll(b(vodArray));
                }
            }

            return list;
        }

        /**
         * 从 Object 中提取 URL（根据 key 从 JSONObject 中查找）
         */
        public static String d(Object obj, String key) {
            String httpPrefix = "http"; // "http"

            if (obj == null) return "";

            if (obj instanceof String) {
                String str = (String) obj;
                if (str.startsWith(httpPrefix)) {
                    return str;
                }
                return "";
            }

            if (!(obj instanceof JSONObject)) return "";

            JSONObject json = (JSONObject) obj;

            // 尝试从指定 key 获取
            if (!TextUtils.isEmpty(key) && json.has(key)) {
                Object value = json.get(key);
                String url = a(value);
                if (!TextUtils.isEmpty(url)) return url;
            }

            // 尝试从 "url" 字段获取
            String url = json.optString("mp4Url"); // "url"
            if (url.startsWith(httpPrefix)) {
                return url;
            }

            // 尝试从 "play_url_list" 字段获取
            JSONArray playUrlList = json.optJSONArray("mp4SwitchUrl"); // "play_url_list"
            if (playUrlList != null) {
                for (int i = 0; i < playUrlList.length(); i++) {
                    String playUrl = playUrlList.optString(i);
                    if (playUrl.startsWith(httpPrefix)) {
                        return playUrl;
                    }
                }
            }

            // 遍历所有字段查找
            JSONArray names = json.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i);
                    Object value = json.get(name);
                    String foundUrl = a(value);
                    if (!TextUtils.isEmpty(foundUrl)) {
                        return foundUrl;
                    }
                }
            }

            return "";
        }

        @Override
        public void init(Context context, String extend) throws Exception {
            super.init(context, extend);
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            JSONObject params = new JSONObject();
            params.put("recSwitch", true); // "with_model"
            params.put("pageFlag", ""); // "cursor"
            params.put("theaterSubscriptSwitch", true); // "with_vod_list"

            JSONObject response = postRequest(API_BASE_URL, params);

            ArrayList<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            ArrayList<Vod> vodList = new ArrayList<>();

            JSONArray categories = response.optJSONArray("channelGroupData"); // "categories"
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject category = categories.getJSONObject(i);

                    String typeId = String.valueOf(category.optInt("channelGroupId"));
                    String typeName = category.optString("channelGroupName");

                    // 特殊处理：如果名称是"推荐"，改为"推荐列表"
                    if ("全部".equals(typeName)) { // "推荐"
                        typeName = "推荐"; // "推荐列表"
                    }

                    classes.add(new Class(typeId, typeName));

                    JSONArray subCategories = category.optJSONArray("channelData");
                    if (subCategories != null && subCategories.length() > 0) {
                        ArrayList<Filter.Value> values = new ArrayList<>();
                        for (int j = 0; j < subCategories.length(); j++) {
                            JSONObject subCat = subCategories.getJSONObject(j);
                            String subTypeId = String.valueOf(subCat.optInt("channelId"));
                            String subTypeName = subCat.optString("channelName");

                            // 使用工具方法构建带 @ 分隔的 ID
                            String compositeId = subTypeId + "@" + subTypeName;

                            values.add(new Filter.Value(subTypeName, compositeId));
                        }

                        if (!values.isEmpty()) {
                            Filter filterObj = new Filter(
                                "class",
                                "类型",
                                values
                            );
                            filters.put(typeId, java.util.Collections.singletonList(filterObj));
                        }
                    }
                }
            }

            vodList.addAll(c(response));

            return Result.string(classes, vodList, filters);
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            int categoryId = Integer.parseInt(tid);

            int subCategoryId = 0;
            if (extend != null && extend.containsKey("class")) {
                String subCatValue = extend.get("class");
                if (!TextUtils.isEmpty(subCatValue) && subCatValue.contains("@")) {
                    String[] parts = subCatValue.split("@");
                    if (parts.length > 0) {
                        subCategoryId = Integer.parseInt(parts[0]);
                    }
                }
            }

            int page = Integer.parseInt(pg);

            JSONObject params = new JSONObject();
            params.put("recSwitch", true); // "with_model"
            params.put("pageFlag", page > 1 ? String.valueOf(page - 1) : ""); // "cursor"
            params.put("theaterSubscriptSwitch", true); // "with_vod_list"
            params.put("channelGroupId", categoryId); // "category_id"
            if (subCategoryId > 0) {
                params.put("channelId", subCategoryId); // "sub_category_id"
            }

            JSONObject response = postRequest(API_BASE_URL, params);

            ArrayList<Vod> vodList = c(response);

            // 判断是否有更多
            boolean hasMore = false;
            if (vodList.size() >= 18) {
                hasMore = true;
            }
            if (response.optBoolean("hasMore", false)) { // "has_more"
                hasMore = true;
            }

            int nextPage = hasMore ? page + 1 : page;
            int limit = 18;
            int total = page * limit;

            return Result.get()
                .page(page, nextPage, limit, total)
                .vod(vodList)
                .string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            String vodId = ids.get(0);

            JSONObject params = new JSONObject();
            params.put("bookId", vodId); // "vod_id"
            params.put("needNextChapter", 0); // "vod_tab_index"
            params.put("isNeedAlias", ""); // "vod_collect_cursor"
            params.put("bookAlias", ""); // "vod_collect_vod_cursor"
            params.put("resolutionRate", "1080P"); // "fetch_model", "DEFAULT"

            JSONObject response = postRequest(API_BASE_URL, params);

            JSONObject vodData = response.optJSONObject("videoInfo");
            if (vodData == null) {
                vodData = response;
            }

            String vodName = vodData.optString("bookName");
            String vodPic = vodData.optString("coverWap");

            Vod vod = new Vod(vodId, vodName, vodPic);

            String vodRemarks = vodData.optString("finishStatusCn");
            vod.setVodRemarks(vodRemarks);

            String vodContent = vodData.optString("introduction");
            vod.setVodContent(vodContent);

            String vodActor = vodData.optString("protagonist");
            vod.setVodActor(vodActor);

            JSONArray episodes = response.optJSONArray("chapterList");
            if (episodes == null) {
                episodes = vodData.optJSONArray("chapterList");
            }

            if (episodes == null) {
                return Result.string(vod);
            }

            ArrayList<String> playUrls = new ArrayList<>();
            ArrayList<String> playUrls1 = new ArrayList<>();
            ArrayList<String> playUrls2 = new ArrayList<>();

            for (int i = 0; i < episodes.length(); i++) {
                JSONObject episode = episodes.getJSONObject(i);

                String episodeName = episode.optString("chapterName");
                String episodeId = episode.optString("chapterId");

                // 使用工具方法构建播放 URL
                String playUrl = vodId + "@" + episodeId;

                StringBuilder sb = new StringBuilder();
                sb.append(episodeName);
                sb.append("$"); // "$"
                sb.append(playUrl);
                String episodeEntry = sb.toString();

                playUrls.add(episodeEntry);
                playUrls1.add(episodeEntry);
                playUrls2.add(episodeEntry);
            }

            vod.setVodPlayFrom("1080P$$$720P$$$480P"); // "草莓视频$$$草莓视频$$$草莓视频"

            StringBuilder playUrlSb = new StringBuilder();
            playUrlSb.append(TextUtils.join("#", playUrls)); // "#"
            playUrlSb.append("$$$"); // "$$$"
            playUrlSb.append(TextUtils.join("#", playUrls1));
            playUrlSb.append("$$$");
            playUrlSb.append(TextUtils.join("#", playUrls2));

            vod.setVodPlayUrl(playUrlSb.toString());

            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            int atIndex = id.indexOf('@');

            if (atIndex <= 0 || atIndex >= id.length() - 1) {
                return Result.get().url("").string();
            }

            String vodId = id.substring(0, atIndex);
            String episodeId = id.substring(atIndex + 1);

            JSONObject params = new JSONObject();
            params.put("bookId", vodId);
            params.put("chapterId", episodeId);

            params.put("unClockType", "load"); // "vod_module_index", "HLS"
            params.put("tierPlaySource", JSONObject.NULL); // "vod_data_collect_cursor"

            JSONArray episodeIds = new JSONArray();
            episodeIds.put(episodeId);
            params.put("chapterIds", episodeIds);

            JSONObject drmInfo = new JSONObject();
            drmInfo.put("expId", JSONObject.NULL); // "key_id"
            drmInfo.put("logId", JSONObject.NULL); // "key_url"
            drmInfo.put("originName", "bigdata_rec"); // "drm_type", "Widevine"
            drmInfo.put("recId", JSONObject.NULL); // "drm_token"

            String userAgent = "dzmf_video_sc_reco";
            drmInfo.put("scene", userAgent); // "user_agent"
            drmInfo.put("sceneId", userAgent);

            drmInfo.put("strategyId", "godum7go");
            drmInfo.put("strategyName", "omap");

            params.put("omap", drmInfo);

            JSONObject response = postRequest(API_BASE_URL, params);

            String playUrl = "";

            if (response != null) {
                JSONArray episodeList = response.optJSONArray("chapterInfo");
                if (episodeList != null && episodeList.length() > 0) {
                    JSONObject episodeData = episodeList.optJSONObject(0);
                    if (episodeData != null) {
                        playUrl = d(episodeData.opt("content"), flag);
                    }
                }

                if (TextUtils.isEmpty(playUrl)) {
                    if (response.has("ad")) {
                        JSONObject drmData = response.optJSONObject("ad");
                        if (drmData != null) {
                            playUrl = d(drmData.opt("content"), flag);
                        }
                    }
                }
            }

            HashMap<String, String> headers = new HashMap<>();
            StringBuilder uaBuilder = new StringBuilder();
            uaBuilder.append("aliplayer(appv=2.7.1&av=7.1.0&av2=7.1.0_46933858&os=android&ov=11&dm=");
            uaBuilder.append(Build.MODEL);
            uaBuilder.append(")");
            headers.put("User-Agent", uaBuilder.toString());

            return Result.get().url(playUrl).header(headers).string();
        }

        @Override
        public String searchContent(String key, boolean quick, String pg) throws Exception {
            int page = 1;
            try {
                if (!TextUtils.isEmpty(pg)) {
                    page = Math.max(1, Integer.parseInt(pg));
                }
            } catch (Exception e) {
                // 使用默认页码
            }

            JSONObject params = new JSONObject();
            String searchKey = key == null ? "" : key.trim();
            params.put("keyword", searchKey);
            params.put("page", page);
            params.put("size", 15);
            params.put("hotWordType", 2);

            JSONObject response = postRequest(API_BASE_URL, params);

            JSONArray searchResults = response.optJSONArray("searchVos");
            if (searchResults == null) {
                searchResults = response.optJSONArray("content");
            }

            ArrayList<Vod> vodList = b(searchResults);

            return Result.string(vodList);
        }
    }

    /**
     * 围观短剧 Spider - 完整实现
     * 从 WeiguanDJ.smali 还原
     */
    private static class WeiguanDJ extends Spider {

        private String a; // device_name (Build.MODEL)
        private String b; // device_brand (Build.BRAND)
        private String c; // clientInfo (MD5 hash of random string)

        private static final String API_HOST = "https://api.drama.9ddm.com";
        private static final String CATEGORY_PATH = "/drama/home/shortVideoCategory";
        private static final String DETAIL_PATH = "/drama/home/shortVideoDetail";
        private static final String SEARCH_PATH = "/drama/home/search";

        public WeiguanDJ() {
            this.a = "";
            this.b = "";
            this.c = "";
        }

        /**
         * 获取请求头
         * merge/a/u.v() 的实现
         */
        private final Map<String, String> getHeaders() {
            String key1 = "User-Agent"; // "User-Agent"
            String value1 = "okhttp/5.1.0"; // "okhttp/5.1.0"

            HashMap<String, String> headers = new HashMap<>();
            headers.put(key1, value1);
            return headers;
        }

        /**
         * 解析 JSONArray 为 ArrayList<Vod>
         */
        private final ArrayList<Vod> parseVodList(JSONArray array) {
            ArrayList<Vod> list = new ArrayList<>();
            if (array == null) {
                return list;
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);

                String vodIdKey = "oneId"; // "oneId"
                String vodId = item.optString(vodIdKey);

                String titleKey = "title"; // "title"
                String title = item.optString(titleKey);

                String picKey = "horzPoster"; // "vertPoster"
                String pic = item.optString(picKey);

                String remarkKey = "episodeCount"; // "playAmountStr"
                String remark = item.optString(remarkKey);

                Vod vod = new Vod(vodId, title, pic, remark);
                list.add(vod);
            }

            return list;
        }

        /**
         * 构建客户端信息参数
         */
        private final String buildClientInfoParam() {
            StringBuilder sb = new StringBuilder();

            String prefix = "?version_code=1500&version_name=1.5.0&device_name=";
            sb.append(prefix);
            sb.append(this.a); // device_name

            String middle1 = "&device_type=phone&is_first_day=true&is_first_24h=true&app_launch_way=icon&default_homepage=homepage_interaction&device_owning_firm=";
            sb.append(middle1);
            sb.append(this.b); // device_brand

            String middle2 = "&font_scale=default&os_type=1&clientInfo=";
            sb.append(middle2);
            sb.append(this.c); // clientInfo

            return sb.toString();
        }

        @Override
        public void init(Context context, String extend) throws Exception {
            super.init(context, extend);

            // 初始化设备信息
            this.a = Build.MODEL;
            this.b = Build.BRAND;

            // 生成随机字符串并计算 MD5
            Random random = new Random();
            StringBuilder sb = new StringBuilder();
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            // 解码后应该是字母数字字符集

            for (int i = 0; i < 10; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }

            // 计算 MD5
            try {
                MessageDigest md = MessageDigest.getInstance("MD5"); // "MD5"
                byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    String h = Integer.toHexString(b & 0xff);
                    if (h.length() == 1) {
                        hex.append('0');
                    }
                    hex.append(h);
                }
                this.c = hex.toString();
            } catch (Exception e) {
                this.c = sb.toString();
            }
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            ArrayList<Class> classes = new ArrayList<>();

            // 构建请求 URL
            StringBuilder urlBuilder = new StringBuilder(API_HOST);
            urlBuilder.append("https://api.drama.9ddm.com/drama/home/shortVideoTags");
            // 解码后应该是 /drama/home/shortVideoCategory

            urlBuilder.append(buildClientInfoParam());

            String url = urlBuilder.toString();
            Map<String, String> headers = getHeaders();

            String response = OkHttp.string(url, headers);
            JSONObject json = new JSONObject(response);

            // 解析分类列表
            String classArrayKey = "tags"; // "data"
            JSONArray classArray = json.optJSONArray(classArrayKey);

            if (classArray != null) {
                for (int i = 0; i < classArray.length(); i++) {
                    String className = classArray.getString(i);
                    classes.add(new Class(className, className));
                }
            }

            return Result.string(classes, new ArrayList<>());
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            // 处理分类 ID
            String categoryId = tid;
            if (extend != null) {
                String areaKey = "cateId"; // "area"
                if (extend.containsKey(areaKey)) {
                    categoryId = extend.get(areaKey);
                }
            }

            // 构建请求参数
            String key1 = "audience"; // "version_code"
            String value1 = "全部"; // "1600"

            String key2 = "order"; // "version_name"
            String value2 = "最新"; // "1.6.0"

            JSONObject params = new JSONObject();
            params.put(key1, value1);
            params.put(key2, value2);

            // 添加分页参数
            String pageKey = "page"; // "page"
            int page = Integer.parseInt(pg);
            params.put(pageKey, page);

            String pageSizeKey = "pageSize"; // "pageSize"
            params.put(pageSizeKey, 30);

            // 添加分类参数
            String categoryKey = "searchWord"; // "category"
            params.put(categoryKey, "");

            String tidKey = "subject"; // "classId"
            params.put(tidKey, categoryId);

            // 构建请求 URL
            StringBuilder urlBuilder = new StringBuilder(API_HOST);
            urlBuilder.append("https://api.drama.9ddm.com/drama/home/search");
            // 解码后应该是 /drama/home/shortVideoList

            urlBuilder.append(buildClientInfoParam());

            String url = urlBuilder.toString();
            String body = params.toString();
            Map<String, String> headers = getHeaders();

            String response = OkHttp.post(url, body, headers);
            JSONObject json = new JSONObject(response);

            // 解析视频列表
            String dataKey = "data"; // "data"
            JSONArray dataArray = json.optJSONArray(dataKey);
            ArrayList<Vod> list = parseVodList(dataArray);

            // 计算分页
            int limit = 30;
            int totalPage = (list.size() < limit) ? page : page + 1;
            int total = list.size();

            return Result.get().page(page, totalPage, limit, total).vod(list).string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            String vodId = ids.get(0);

            // 构建详情 URL
            StringBuilder urlBuilder = new StringBuilder(API_HOST);
            urlBuilder.append("https://api.drama.9ddm.com/drama/home/shortVideoDetail");
            // 解码后应该是 /drama/home/shortVideoDetail

            urlBuilder.append(buildClientInfoParam());

            String oneIdKey = "&oneId="; // "&oneId="
            urlBuilder.append(oneIdKey);
            urlBuilder.append(vodId);

            String tailKey = "&page=1&pageSize=1000&userId=0&queryAll=true";
            // 解码后应该是分页参数
            urlBuilder.append(tailKey);

            String url = urlBuilder.toString();
            Map<String, String> headers = getHeaders();

            String response = OkHttp.string(url, headers);
            JSONObject json = new JSONObject(response);

            // 解析详情
            String titleKey = "title"; // "title"
            String title = json.optString(titleKey);

            String picKey = "vertPoster"; // "vertPoster"
            String pic = json.optString(picKey);

            Vod vod = new Vod(vodId, title, pic);

            String remarkKey = "短剧"; // 某个备注字段
            vod.setVodRemarks(json.optString(remarkKey));

            String contentKey = "description"; // "description"
            vod.setVodContent(json.optString(contentKey));

            // 解析播放列表
            LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
            String dataKey = "data"; // "data"
            JSONArray dataArray = json.optJSONArray(dataKey);

            if (dataArray != null) {
                ArrayList<String> urls = new ArrayList<>();
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject episode = dataArray.optJSONObject(i);

                    String playOrderKey = "playOrder"; // "playOrder"
                    String playOrder = episode.optString(playOrderKey);

                    String clarityListKey = "videoClarityList"; // "videoClarityList"
                    JSONArray clarityList = episode.optJSONArray(clarityListKey);

                    if (clarityList != null && clarityList.length() > 0) {
                        // 将播放列表编码为 Base64
                        String clarityJson = clarityList.toString();
                        byte[] bytes = clarityJson.getBytes(StandardCharsets.UTF_8);
                        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

                        String urlStr = playOrder + "$" + base64;
                        urls.add(urlStr);
                    }
                }

                if (!urls.isEmpty()) {
                    String separator = "#"; // "#"
                    String urlStr = TextUtils.join(separator, urls);
                    playMap.put("围观", urls);
                }
            }

            if (!playMap.isEmpty()) {
                StringBuilder fromBuilder = new StringBuilder();
                StringBuilder urlStrBuilder = new StringBuilder();

                String separator = "$$$"; // "$$$"

                for (Map.Entry<String, List<String>> entry : playMap.entrySet()) {
                    if (fromBuilder.length() > 0) {
                        fromBuilder.append(separator);
                        urlStrBuilder.append(separator);
                    }
                    fromBuilder.append(entry.getKey());
                    urlStrBuilder.append(TextUtils.join("#", entry.getValue()));
                }

                vod.setVodPlayFrom(fromBuilder.toString());
                vod.setVodPlayUrl(urlStrBuilder.toString());
            }

            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            if (TextUtils.isEmpty(id)) {
                return Result.get().url("").string();
            }

            // 解码 Base64
            byte[] decoded = Base64.decode(id, Base64.DEFAULT);
            String jsonStr = new String(decoded, StandardCharsets.UTF_8);
            JSONArray clarityArray = new JSONArray(jsonStr);

            ArrayList<String> urls = new ArrayList<>();
            for (int i = 0; i < clarityArray.length(); i++) {
                JSONObject clarity = clarityArray.optJSONObject(i);

                String urlKey = "name"; // "url"
                String videoUrl = clarity.optString(urlKey);
                urls.add(videoUrl);

                String nameKey = "url"; // "name"
                String name = clarity.optString(nameKey);
                urls.add(name);
            }

            return Result.get().url(urls).header(getHeaders()).string();
        }

        @Override
        public String searchContent(String key, boolean quick, String pg) throws Exception {
            int page = 1;
            try {
                if (!TextUtils.isEmpty(pg)) {
                    page = Math.max(1, Integer.parseInt(pg));
                }
            } catch (Exception e) {
                page = 1;
            }

            // 构建搜索参数
            String versionCodeKey = "audience"; // "version_code"
            String versionCodeValue = "全部"; // "1600"

            String versionNameKey = "order"; // "version_name"
            String versionNameValue = "最新"; // "1.6.0"

            JSONObject params = new JSONObject();
            params.put(versionCodeKey, versionCodeValue);
            params.put(versionNameKey, versionNameValue);

            String pageKey = "page"; // "page"
            params.put(pageKey, page);

            String pageSizeKey = "pageSize"; // "pageSize"
            params.put(pageSizeKey, 30);

            // 添加搜索关键词
            String keywordKey = "searchWord"; // "keyword"
            String keyword = (key == null) ? "" : key.trim();
            params.put(keywordKey, keyword);

            String tidKey = "subject"; // "classId"
            params.put(tidKey, "");

            // 构建请求 URL
            StringBuilder urlBuilder = new StringBuilder(API_HOST);
            urlBuilder.append("https://api.drama.9ddm.com/drama/home/search");
            // 解码后应该是 /drama/home/search

            urlBuilder.append(buildClientInfoParam());

            String url = urlBuilder.toString();
            String body = params.toString();
            Map<String, String> headers = getHeaders();

            String response = OkHttp.post(url, body, headers);
            JSONObject json = new JSONObject(response);

            // 解析搜索结果
            String dataKey = "data"; // "data"
            JSONArray dataArray = json.optJSONArray(dataKey);
            if (dataArray == null) {
                dataArray = new JSONArray();
            }

            ArrayList<Vod> list = parseVodList(dataArray);
            return Result.string(list);
        }

        @Override
        public String searchContent(String key, boolean quick) throws Exception {
            return searchContent(key, quick, "1");
        }
    }

    /**
     * 七猫短剧 Spider
     */
    private static class Qmdj extends Spider {

        private static final Pattern VIDEO_PATTERN;

        static {
            VIDEO_PATTERN = Pattern.compile("<[^>]+>");
        }

        private String host;
        private String detailHost;

        public Qmdj() {
            this.host = "https://api-store.qmplaylet.com";
            this.detailHost = "https://api-read.qmplaylet.com";
        }

        /**
         * 发起 API 请求
         */
        private static JSONObject fetchApi(String hostUrl, String apiPath, LinkedHashMap<String, String> params) throws Exception {
            // 构建请求参数
            LinkedHashMap<String, String> requestBody = new LinkedHashMap<>();

            // 添加固定参数
            requestBody.put("static_score", "0.8");
            requestBody.put("uuid", "00000000-6f7c-e347-0000-000000000000");
            requestBody.put("device-id", "202504012213236fa2ed536aed584e0cc8a6a09fe2f2d4016cdc5bc74f2d5f");
            requestBody.put("mac", "");
            requestBody.put("sourceuid", "9494817a02a93435");
            requestBody.put("refresh-type", "0");
            requestBody.put("model", "M2012K10C");
            requestBody.put("wlb-imei", "");
            requestBody.put("AUTHORIZATION", "6bcc46919d10d06a");
            requestBody.put("brand", "Redmi");
            requestBody.put("oaid", "");
            requestBody.put("oaid-no-cache", "");
            requestBody.put("sys-ver", "11");
            requestBody.put("trusted-id", "");
            requestBody.put("phone-level", "H");
            requestBody.put("imei", "");
            requestBody.put("wlb-uid", "6bcc46919d10d06a");

            // 添加时间戳
            requestBody.put("session-id", String.valueOf(System.currentTimeMillis()));

            // 序列化为 JSON 并 Base64 编码
            String jsonStr = new JSONObject(requestBody).toString();
            byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            // 字符替换 (+ -> P, / -> X, 0-9/a-z/A-Z -> 自定义字符)
            StringBuilder encoded = new StringBuilder(base64.length());
            for (int i = 0; i < base64.length(); i++) {
                char c = base64.charAt(i);
                if (c == '+') {
                    encoded.append('P');
                } else if (c == '/') {
                    encoded.append('X');
                } else if (c >= '0' && c <= '9') {
                    encoded.append("MUlErYWbdJ".charAt(c - '0'));
                } else if (c >= 'A' && c <= 'Z') {
                    encoded.append("9saI0oy_HGitgNA8Fk3hfRqC4p".charAt(c - 'A'));
                } else if (c >= 'a' && c <= 'z') {
                    encoded.append("mBOuc6Kx5T-2zSZ1VvjQ7DwnLe".charAt(c - 'a'));
                } else {
                    encoded.append(c);
                }
            }

            // 构建查询参数并签名
            LinkedHashMap<String, String> queryParams = new LinkedHashMap<>(params);
            TreeMap<String, String> sortedParams = new TreeMap<>(queryParams);
            StringBuilder signBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
                signBuilder.append(entry.getKey()).append("=").append(entry.getValue() != null ? entry.getValue() : "");
            }
            signBuilder.append("d3dGiJc651gSQ8w1");
            String sign = md5(signBuilder.toString());

            queryParams.put("sign", sign);

            // 构建 HTTP headers
            HashMap<String, String> headers = new HashMap<>();
            headers.put("authorization", "");
            headers.put("reg", "");
            headers.put("is-white", "");
            headers.put("user-agent", "webviewversion/0");
            headers.put("net-env", "1");
            headers.put("channel", "va-vivo_lf");
            headers.put("platform", "android");
            headers.put("application-id", "com.duoduo.read");
            headers.put("app-version", "10001");
            headers.put("qm-params", encoded.toString());
            headers.put("no-permiss", "3");

            // 构建签名 URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(String.format("AUTHORIZATION=app-version=10001application-id=com.duoduo.readchannel=va-vivo_lfis-white=net-env=1platform=androidqm-params=%sreg=", encoded));
            urlBuilder.append(sign);

            String signUrl = md5(urlBuilder.toString());
            headers.put("sign", signUrl);

            // 发起 HTTP 请求
            String url = hostUrl + apiPath;
            String response = OkHttp.string(url, queryParams, headers);

            return new JSONObject(response);
        }

        /**
         * 解析视频列表
         */
        private ArrayList<Vod> parseVideoList(JSONArray array) throws Exception {
            ArrayList<Vod> list = new ArrayList<>();
            if (array == null) return list;

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                // 获取视频 ID
                String videoId = item.optString("id");
                String name = item.optString("playlet_id", videoId);
                if (TextUtils.isEmpty(name)) continue;

                // 获取封面
                String cover = item.optString("title");
                if (!TextUtils.isEmpty(cover)) {
                    cover = VIDEO_PATTERN.matcher(cover).replaceAll("");
                }

                // 获取图片 URL (从多个字段中查找)
                String pic = "";
                String[] picFields = {
                    "image_link",
                    "image",
                    "cover",
                    "vertical_cover",
                    "playlet_cover"
                };

                for (String field : picFields) {
                    String temp = item.optString(field);
                    if (!TextUtils.isEmpty(temp)) {
                        pic = temp;
                        break;
                    }
                }

                // 获取备注
                String remark = item.optString("total_episode_num");
                if (TextUtils.isEmpty(remark)) {
                    remark = item.optString("total_num");
                }
                if (TextUtils.isEmpty(remark)) {
                    remark = item.optString("sub_title");
                }

                list.add(new Vod(videoId, name, pic, remark));
            }

            return list;
        }

        /**
         * MD5 哈希
         */
        private static String md5(String input) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b & 0xff));
                }
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public void init(Context context, String extend) throws Exception {
            super.init(context, extend);

            try {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "webviewversion/0");

                String configUrl = "https://neptune.qmplaylet.com/playlet-domain-android.json";
                String response = OkHttp.string(configUrl, headers);

                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");

                if (data != null) {
                    String url = data.optString("bc");
                    String detail = data.optString("ks");

                    if (!TextUtils.isEmpty(url)) {
                        this.host = url.replaceAll("/$", "");
                    }
                    if (!TextUtils.isEmpty(detail)) {
                        this.detailHost = detail.replaceAll("/$", "");
                    }
                }
            } catch (Exception e) {
                // 使用默认值
            }
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            params.put("tag_id", "0");
            params.put("playlet_privacy", "1");
            params.put("operation", "1");

            JSONObject response = fetchApi(this.host, "/api/v1/playlet/index", params);
            JSONObject data = response.optJSONObject("data");

            ArrayList<Class> classes = new ArrayList<>();
            if (data != null) {
                JSONArray categoryArray = data.optJSONArray("tag_items");
                if (categoryArray != null) {
                    for (int i = 0; i < categoryArray.length(); i++) {
                        JSONObject category = categoryArray.optJSONObject(i);
                        if (category == null) continue;

                        String typeId = category.optString("tag_id");
                        String typeName = category.optString("tag_name");

                        if (!TextUtils.isEmpty(typeId)) {
                            classes.add(new Class(typeId, typeName));
                        }
                    }
                }
            }

            return Result.string(classes, new ArrayList<>());
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            params.put("tag_id", tid);

            String pageNum = TextUtils.isEmpty(pg) ? "1" : pg;
            params.put("next_id", pageNum);

            // 如果是首页分类,设置为第一页
            String pageSize = "0";
            if ("0".equals(tid)) {
                pageSize = "0";
            }
            params.put("playlet_privacy", pageSize);

            JSONObject response = fetchApi(this.host, "/api/v1/playlet/index", params);
            JSONObject data = response.optJSONObject("data");

            JSONArray videoArray = null;
            if (data != null && data.has("list")) {
                videoArray = data.optJSONArray("list");
            }

            ArrayList<Vod> videos = parseVideoList(videoArray);

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }

            int nextPage = videos.isEmpty() ? page : page + 1;

            return Result.get()
                    .vod(videos)
                    .page(page, nextPage, 20, videos.size())
                    .string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            String videoId = ids.get(0);

            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            params.put("playlet_id", videoId);

            JSONObject response = fetchApi(this.detailHost, "/player/api/v1/playlet/info", params);
            JSONObject data = response.optJSONObject("data");

            if (data == null) {
                Vod vod = new Vod(videoId, videoId, "", "");
                return Result.string(vod);
            }

            // 获取名称
            String name = data.optString("title");

            // 获取封面 (从多个字段中查找)
            String pic = "";
            String[] picFields = {
                "image_link",
                "image",
                "cover"
            };

            for (String field : picFields) {
                String temp = data.optString(field);
                if (!TextUtils.isEmpty(temp)) {
                    pic = temp;
                    break;
                }
            }

            Vod vod = new Vod(videoId, name, pic, "");

            // 设置简介
            String content = data.optString("intro");
            vod.setVodContent(content);

            // 解析播放列表
            JSONArray episodes = data.optJSONArray("play_list");
            if (episodes != null && episodes.length() > 0) {
                ArrayList<String> playList = new ArrayList<>();

                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject episode = episodes.optJSONObject(i);
                    if (episode == null) continue;

                    String episodeName = episode.optString("sort");
                    if (TextUtils.isEmpty(episodeName)) {
                        episodeName = String.valueOf(i + 1);
                    }

                    String episodeUrl = episode.optString("video_url");
                    if (TextUtils.isEmpty(episodeUrl)) continue;

                    playList.add("第" + episodeName + "集$" + episodeUrl);
                }

                vod.setVodPlayFrom("七猫");
                vod.setVodPlayUrl(TextUtils.join("#", playList));
            }

            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            if (TextUtils.isEmpty(id)) {
                return Result.get().url("").parse(0).string();
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "webviewversion/0");
            headers.put("Referer", "Dalvik/2.1.0 (Linux; U; Android 11; M2012K10C Build/RP1A.200720.011)");

            return Result.get()
                    .url(id)
                    .header(headers)
                    .parse(0)
                    .string();
        }

        @Override
        public String searchContent(String key, boolean quick, String pg) throws Exception {
            if (TextUtils.isEmpty(key)) {
                return Result.get().vod(new ArrayList<>()).page().string();
            }

            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            params.put("extend", "");
            params.put("page", TextUtils.isEmpty(pg) ? "1" : pg);
            params.put("wd", key.trim());
            params.put("read_preference", "0");
            params.put("0", "6bcc46919d10d06a" + System.currentTimeMillis());

            JSONObject response = fetchApi(this.host, "/api/v1/playlet/search", params);
            JSONObject data = response.optJSONObject("data");

            JSONArray videoArray = null;
            if (data != null && data.has("list")) {
                videoArray = data.optJSONArray("list");
            }

            ArrayList<Vod> videos = parseVideoList(videoArray);

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }

            return Result.get()
                    .vod(videos)
                    .page(page, page + 1, 20, videos.size())
                    .string();
        }
    }

    /** 星芽短剧 Spider — short drama video source. */
    private static class Xingya extends Spider {
        private static final String BASE_URL = "https://app.whjzjx.cn";
        private static final String LOGIN_URL = "https://u.shytkjgs.com/user/v3/account/login";
        private static final String SEARCH_URL = "https://app.whjzjx.cn/v3/search";
        private static final String FALLBACK_PLAY_URL = "https://fs-im-kefu.7moor-fs1.com/ly/4d2c3f00-7d4c-11e5-af15-41bf63ae4ea0/1732707176882/jiduo.txt";

        private static final String AES_KEY = "B@ecf920Od8A4df7";
        private static final String AES_ALGORITHM = "AES/ECB/PKCS5Padding";
        private static final String DEVICE_ID = "2a50580e69d38388c94c93605241fb306";
        private static final String PACKAGE_NAME = "com.jz.xydj";
        private static final String ANDROID_ID = "ec1280db12795506";
        private static final long INSTALL_TIME = 0x1980973d6d1L;
        private static final String VERSION_NAME = "3.8.3.1";
        private static final String PLATFORM = "1";
        private static final String CONTENT_TYPE = "application/json; charset=utf-8";

        private static final String LOGIN_UA = "Mozilla/5.0 (Linux; Android 9; V1938T Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36";
        private static final String PLAYER_UA = "Linux; Android 12; Pixel 3 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.101 Mobile Safari/537.36";

        private static final String PLAY_FROM_XINGYA = "星芽";
        private static final String LOGIN_FAILED = "星芽登录失败";

        private String token;

        public Xingya() {
            this.token = "";
        }

        @Override
        public void init(Context context, String extend) throws Exception {
            this.token = "";
        }

        private HashMap<String, String> getHeaders() throws Exception {
            if (TextUtils.isEmpty(token)) {
                login();
            }
            HashMap<String, String> headers = new HashMap<>();
            headers.put("authorization", token);
            headers.put("platform", PLATFORM);
            headers.put("version_name", VERSION_NAME);
            return headers;
        }

        private void login() throws Exception {
            JSONObject payload = new JSONObject();
            payload.put("device", DEVICE_ID);
            payload.put("package_name", PACKAGE_NAME);
            payload.put("android_id", ANDROID_ID);
            payload.put("install_first_open", true);
            payload.put("first_install_time", INSTALL_TIME);
            payload.put("last_update_time", INSTALL_TIME);
            payload.put("report_link_url", "");
            payload.put("authorization", "");
            payload.put("timestamp", System.currentTimeMillis());

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
            String encryptedBody = Base64.encodeToString(encrypted, Base64.NO_WRAP);

            HashMap<String, String> loginHeaders = new HashMap<>();
            loginHeaders.put("platform", PLATFORM);
            loginHeaders.put("user_agent", LOGIN_UA);
            loginHeaders.put("content-type", CONTENT_TYPE);

            String response = OkHttp.post(LOGIN_URL, encryptedBody, loginHeaders);
            JSONObject json = new JSONObject(response);
            token = json.getJSONObject("data").optString("token");
            if (TextUtils.isEmpty(token)) {
                throw new Exception(LOGIN_FAILED);
            }
        }

        private JSONObject fetchJson(String path) throws Exception {
            String url = new StringBuilder(BASE_URL).append(path).toString();
            String response = OkHttp.string(url, null, getHeaders());
            return new JSONObject(response);
        }

        private static ArrayList<Vod> parseVodList(JSONArray array, String remarksKey) {
            ArrayList<Vod> list = new ArrayList<>();
            if (array == null) return list;
            for (int i = 0; i < array.length(); i++) {
                JSONObject wrapper = array.optJSONObject(i);
                if (wrapper == null) continue;
                JSONObject theater = wrapper.optJSONObject("theater");
                if (theater == null) continue;
                String id = String.valueOf(theater.optInt("id"));
                String title = theater.optString("title");
                String cover = theater.optString("cover_url");
                String remarks = theater.optString(remarksKey);
                if (TextUtils.isEmpty(remarks)) {
                    remarks = theater.optString("play_amount_str");
                }
                list.add(new Vod(id, title, cover, remarks));
            }
            return list;
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            ArrayList<Class> classes = new ArrayList<>();
            classes.add(new Class("1", "剧场"));
            classes.add(new Class("3", "新剧"));
            classes.add(new Class("2", "热播"));
            classes.add(new Class("7", "星选"));
            classes.add(new Class("5", "阳光"));
            return Result.string(classes, new ArrayList<Vod>());
        }

        @Override
        public String homeVideoContent() throws Exception {
            JSONObject json = fetchJson("/v1/theater/home_page?theater_class_id=1&class2_id=4&page_num=1&page_size=24");
            JSONArray list = json.optJSONObject("data").optJSONArray("list");
            ArrayList<Vod> vodList = parseVodList(list, "play_amount_str");
            return Result.string(vodList);
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            int page = 1;
            try {
                page = Math.max(1, Integer.parseInt(pg));
            } catch (Exception e) {
            }
            StringBuilder path = new StringBuilder("/v1/theater/home_page?theater_class_id=");
            path.append(tid);
            path.append("&page_num=");
            path.append(page);
            path.append("&page_size=24");
            JSONObject json = fetchJson(path.toString());
            JSONArray list = json.optJSONObject("data").optJSONArray("list");
            ArrayList<Vod> vodList = parseVodList(list, "theme");
            return Result.get().page(page, page + 1, 90, 9999).vod(vodList).string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            String id = ids.get(0);
            JSONObject json = fetchJson("/v2/theater_parent/detail?theater_parent_id=" + id);
            JSONObject data = json.getJSONObject("data");

            String content = "剧情：" + data.optString("introduction");

            String area = "";
            JSONArray descTags = data.optJSONArray("desc_tags");
            if (descTags != null && descTags.length() > 0) {
                area = descTags.optString(0);
            }

            String filing = data.optString("filing");

            StringBuilder playUrl = new StringBuilder();
            String playFrom;

            JSONArray theaters = data.optJSONArray("theaters");
            if (theaters != null && theaters.length() > 0) {
                for (int i = 0; i < theaters.length(); i++) {
                    if (playUrl.length() > 0) playUrl.append("#");
                    JSONObject theater = theaters.getJSONObject(i);
                    playUrl.append(theater.optString("num"));
                    playUrl.append("$");
                    playUrl.append(theater.optString("son_video_url"));
                }
                playFrom = PLAY_FROM_XINGYA;
            } else {
                String videoUrl = data.optString("video_url");
                if (TextUtils.isEmpty(videoUrl)) {
                    String external = OkHttp.string(FALLBACK_PLAY_URL, null);
                    if (!TextUtils.isEmpty(external)) {
                        int start = external.indexOf("s2='");
                        if (start >= 0) {
                            int begin = start + 4;
                            int end = external.indexOf("'", begin);
                            if (end >= 0) {
                                playUrl.append(external.substring(begin, end).replace("\\", ""));
                            }
                        }
                    }
                    playFrom = PLATFORM;
                } else {
                    playUrl.append("1$");
                    playUrl.append(videoUrl);
                    playFrom = PLAY_FROM_XINGYA;
                }
            }

            String title = data.optString("title", id);
            String cover = data.optString("cover_url", "");
            Vod vod = new Vod(id, title, cover, filing);
            vod.setVodContent(content);
            vod.setVodArea(area);
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl.toString());
            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            if (TextUtils.isEmpty(id)) {
                return Result.get().url("").parse(0).string();
            }
            HashMap<String, String> header = new HashMap<>();
            header.put("User-Agent", PLAYER_UA);
            return Result.get().url(id).header(header).parse(0).string();
        }

        @Override
        public String searchContent(String key, boolean quick, String pg) throws Exception {
            if (TextUtils.isEmpty(key)) {
                return Result.string(new ArrayList<>());
            }
            JSONObject body = new JSONObject();
            body.put("text", key.trim());
            String response = OkHttp.post(SEARCH_URL, body.toString(), getHeaders());
            JSONObject json = new JSONObject(response);
            ArrayList<Vod> list = new ArrayList<>();
            JSONObject data = json.optJSONObject("data");
            if (data == null) return Result.string(list);
            JSONObject theater = data.optJSONObject("theater");
            if (theater == null) return Result.string(list);
            JSONArray search_data = theater.optJSONArray("search_data");
            if (search_data == null) return Result.string(list);
            for (int i = 0; i < search_data.length(); i++) {
                JSONObject item = search_data.optJSONObject(i);
                if (item == null) continue;
                String vodId = item.optString("id");
                if (TextUtils.isEmpty(vodId)) {
                    long longId = item.optLong("id", 0);
                    if (longId > 0) {
                        vodId = String.valueOf(longId);
                    }
                }
                if (TextUtils.isEmpty(vodId) || "0".equals(vodId)) continue;
                String title = item.optString("title");
                String cover = item.optString("cover_url");
                String score = item.optString("score_str");
                list.add(new Vod(vodId, title, cover, score));
            }
            return Result.string(list);
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (key == null) key = "";
        else key = key.trim();
        if (TextUtils.isEmpty(key)) {
            return Result.get().vod(new ArrayList<>()).string();
        }
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception e) {
            page = 1;
        }
        String pgStr = String.valueOf(page);
        final String finalKey = key;
        final int finalPage = page;
        ArrayList<Vod> list = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        mergeSearch(list, seen, "七猫", () -> {
            try {
                return Result.objectFrom(qmdj.searchContent(finalKey, quick, pgStr)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeSearch(list, seen, "星芽", () -> {
            try {
                return Result.objectFrom(xingya.searchContent(finalKey, quick, pgStr)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeSearch(list, seen, "西饭", () -> xifanSearch(finalPage, finalKey));
        mergeSearch(list, seen, "围观", () -> {
            try {
                return Result.objectFrom(weiguanDJ.searchContent(finalKey, quick, pgStr)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeSearch(list, seen, "河马", () -> {
            try {
                return Result.objectFrom(hema.searchContent(finalKey, quick, pgStr)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        mergeSearch(list, seen, "好看", () -> {
            try {
                return Result.objectFrom(hhkk.searchContent(finalKey, quick, pgStr)).getList();
            } catch (Exception e) {
                return new ArrayList<Vod>();
            }
        });
        Result result = Result.get().vod(list);
        int count = Math.max(1, list.size());
        return result.page(page, 1, count, count).string();
    }
}
