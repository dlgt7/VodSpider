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
import java.util.function.Supplier;

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

    /** 围观短剧 Spider - stub implementation. */
    private static class WeiguanDJ extends Spider {
        @Override
        public void init(Context context, String extend) throws Exception {
            super.init(context, extend);
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            ArrayList<Class> classes = new ArrayList<>();
            classes.add(new Class("0", "推荐"));
            return Result.string(classes, new ArrayList<>());
        }

        @Override
        public String homeVideoContent() throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            return Result.get().vod(new ArrayList<>()).page().string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
            Vod vod = new Vod(ids.get(0), "围观短剧", "", "");
            vod.setVodPlayFrom("围观");
            vod.setVodPlayUrl("第1集$http://example.com");
            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            if (TextUtils.isEmpty(id)) return Result.get().url("").string();
            if (id.startsWith("围观@")) {
                String url = id.substring(3);
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "okhttp/5.1.0");
                return Result.get().url(url).header(headers).parse(0).string();
            }
            return Result.get().url(id).parse(0).string();
        }

        @Override
        public String searchContent(String key, boolean quick) throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
        }

        @Override
        public String searchContent(String key, boolean quick, String pg) throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    /** 河马短剧 Spider - stub implementation. */
    private static class Hema extends Spider {
        @Override
        public void init(Context context, String extend) throws Exception {
            super.init(context, extend);
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            ArrayList<Class> classes = new ArrayList<>();
            classes.add(new Class("1", "推荐"));
            return Result.string(classes, new ArrayList<>());
        }

        @Override
        public String homeVideoContent() throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            return Result.get().vod(new ArrayList<>()).page().string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
            Vod vod = new Vod(ids.get(0), "河马短剧", "", "");
            vod.setVodPlayFrom("河马");
            vod.setVodPlayUrl("第1集$http://example.com");
            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            if (TextUtils.isEmpty(id)) return Result.get().url("").string();
            return Result.get().url(id).parse(0).string();
        }

        @Override
        public String searchContent(String key, boolean quick) throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    /** 七猫短剧 Spider - stub implementation. */
    private static class Qmdj extends Spider {
        @Override
        public void init(Context context, String extend) throws Exception {
            super.init(context, extend);
        }

        @Override
        public String homeContent(boolean filter) throws Exception {
            ArrayList<Class> classes = new ArrayList<>();
            classes.add(new Class("0", "全部"));
            classes.add(new Class("1", "男频"));
            classes.add(new Class("3", "新剧"));
            return Result.string(classes, new ArrayList<>());
        }

        @Override
        public String homeVideoContent() throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
        }

        @Override
        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
            return Result.get().vod(new ArrayList<>()).page().string();
        }

        @Override
        public String detailContent(List<String> ids) throws Exception {
            if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
            Vod vod = new Vod(ids.get(0), "七猫短剧", "", "");
            vod.setVodPlayFrom("七猫");
            vod.setVodPlayUrl("第1集$http://example.com");
            return Result.string(vod);
        }

        @Override
        public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
            if (TextUtils.isEmpty(id)) return Result.get().url("").string();
            return Result.get().url(id).parse(0).string();
        }

        @Override
        public String searchContent(String key, boolean quick) throws Exception {
            return Result.get().vod(new ArrayList<>()).string();
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
