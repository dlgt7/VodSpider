package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AppGet Spider - 提供视频搜索、分类、详情、播放等功能
 */
public class AppGet extends Spider {

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*\\.(txt|json)$", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 14; Build/SKQ1.231004.001)";
    private static final String DEFAULT_PARSE_TYPE = "1";

    private String apiUrl;
    private final HashMap<String, String> headers = new HashMap<>();
    private JsonObject configData;
    private boolean searchVerifyEnabled;
    private String dataKey;
    private String dataIv;

    public AppGet() {
        headers.put("User-Agent", DEFAULT_USER_AGENT);
    }

    /**
     * 从 JsonObject 中获取第一个非空字符串
     */
    private static String getFirstNonEmptyString(JsonObject json, String[] keys) {
        for (String key : keys) {
            if (json.has(key)) {
                JsonElement element = json.get(key);
                if (!element.isJsonNull()) {
                    String value = element.getAsString();
                    if (value != null && !value.isEmpty() && !"空".equals(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * POST 请求并解密数据
     */
    private String fetchAndDecrypt(String path, HashMap<String, String> params) throws Exception {
        String url = apiUrl + path;
        String response = OkHttp.post(url, params, headers);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        String encryptedData = json.get("data").getAsString();
        return Crypto.CBC(encryptedData, dataKey, dataIv);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);

        if (extend == null || extend.trim().isEmpty()) {
            return;
        }

        String trimmed = extend.trim();
        JsonObject config;

        if (trimmed.startsWith("{")) {
            config = JsonParser.parseString(trimmed).getAsJsonObject();
        } else if (trimmed.contains("|")) {
            String[] parts = trimmed.split("\\|");
            ArrayList<String> list = new ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    list.add(part.trim());
                }
            }

            if (list.size() < 2) {
                throw new Exception("AppGet ext 缺少 host/url 或 datakey/key");
            }

            config = new JsonObject();
            config.addProperty("host", list.get(0));
            config.addProperty("datakey", list.get(1));
            config.addProperty("dataiv", list.get(1));

            if (list.size() > 2) {
                String third = list.get(2);
                if (third.matches("(?i)^V\\d+$") || third.toLowerCase().startsWith("init") ||
                    third.length() == 16 || third.length() == 32) {
                    config.addProperty("dataiv", third);
                } else {
                    config.addProperty("init_suffix", third);
                }
            }

            if (list.size() > 3) {
                config.addProperty("ua", list.get(3));
            }
        } else {
            if (!URL_PATTERN.matcher(trimmed).matches()) {
                throw new Exception("AppGet ext 缺少 host/url 或 datakey/key");
            }
            config = new JsonObject();
            config.addProperty("host", trimmed);
        }

        String hostUrl = getFirstNonEmptyString(config, new String[]{"host", "url", "site"});
        this.dataKey = getFirstNonEmptyString(config, new String[]{"datakey", "dataKey", "key"});
        this.dataIv = getFirstNonEmptyString(config, new String[]{"dataiv", "dataIv", "iv"});

        if (hostUrl == null || hostUrl.isEmpty()) {
            throw new Exception("AppGet ext 缺少 host/url 或 datakey/key");
        }

        // 处理 host URL（如果是 txt/json 文件，需要先拉取内容）
        if (URL_PATTERN.matcher(hostUrl).matches()) {
            hostUrl = OkHttp.string(hostUrl, headers).trim().replaceAll("/+$", "");
        }
        hostUrl = hostUrl.replaceAll("/+$", "");

        this.apiUrl = hostUrl + "/api.php/getappapi";

        if (this.dataKey == null || this.dataKey.isEmpty()) {
            throw new Exception("AppGet ext 缺少 host/url 或 datakey/key");
        }

        if (this.dataIv == null || this.dataIv.isEmpty()) {
            this.dataIv = this.dataKey;  // 如果 dataIv 为空，回退使用 dataKey
        }

        String ua = getFirstNonEmptyString(config, new String[]{"ua"});
        if (ua != null && !ua.isEmpty()) {
            headers.put("User-Agent", ua);
        }

        String initSuffix = getFirstNonEmptyString(config, new String[]{"init_suffix", "init"});
        if (initSuffix == null || initSuffix.isEmpty()) {
            initSuffix = "init";
        }
        if (!initSuffix.toLowerCase().startsWith("init")) {
            initSuffix = "init" + initSuffix;
        }

        String initUrl = this.apiUrl + ".index/" + initSuffix;

        String initResponse = OkHttp.string(initUrl, headers);
        JsonObject initJson = JsonParser.parseString(initResponse).getAsJsonObject();
        String encryptedInitData = initJson.get("data").getAsString();
        String decryptedInitData = Crypto.CBC(encryptedInitData, this.dataKey, this.dataIv);
        this.configData = JsonParser.parseString(decryptedInitData).getAsJsonObject();

        if (this.configData.has("config")) {
            JsonObject configObj = this.configData.getAsJsonObject("config");
            if (configObj.has("system_search_verify_status")) {
                this.searchVerifyEnabled = configObj.get("system_search_verify_status").getAsBoolean();
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        JsonObject filters = new JsonObject();

        JsonArray typeList = configData.getAsJsonArray("type_list");

        HashMap<String, String> filterNames = new HashMap<>();
        filterNames.put("class", "类型");
        filterNames.put("area", "地区");
        filterNames.put("lang", "语言");
        filterNames.put("year", "年份");
        filterNames.put("sort", "排序");

        for (JsonElement element : typeList) {
            JsonObject typeObj = element.getAsJsonObject();
            String typeName = typeObj.get("type_name").getAsString();

            if ("全部".equals(typeName) || "QQ".equals(typeName) || typeName.contains("企鹅群")) {
                continue;
            }

            String typeId = typeObj.get("type_id").getAsString();
            classes.add(new Class(typeId, typeName));

            if (filter && typeObj.has("filter_type_list")) {
                JsonArray filterTypeList = typeObj.getAsJsonArray("filter_type_list");
                JsonArray filterArray = new JsonArray();

                for (JsonElement filterElement : filterTypeList) {
                    JsonObject filterObj = filterElement.getAsJsonObject();
                    if (!filterObj.has("list")) {
                        continue;
                    }

                    JsonArray listArray = filterObj.getAsJsonArray("list");
                    if (listArray.size() == 0) {
                        continue;
                    }

                    JsonArray valueArray = new JsonArray();
                    for (JsonElement listElement : listArray) {
                        String value = listElement.getAsString();
                        JsonObject item = new JsonObject();
                        item.addProperty("n", value);
                        item.addProperty("v", value);
                        valueArray.add(item);
                    }

                    String filterName = filterObj.get("name").getAsString();
                    String filterKey = "年份".equals(filterName) ? "by" : filterName;

                    JsonObject filterItem = new JsonObject();
                    filterItem.addProperty("key", filterKey);
                    filterItem.addProperty("name", filterNames.containsKey(filterName) ? filterNames.get(filterName) : filterName);
                    filterItem.add("value", valueArray);
                    filterArray.add(filterItem);
                }

                if (filterArray.size() > 0) {
                    filters.add(typeId, filterArray);
                }
            }
        }

        return Result.string(classes, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        JsonArray typeList = configData.getAsJsonArray("type_list");

        for (JsonElement typeElement : typeList) {
            JsonObject typeObj = typeElement.getAsJsonObject();
            if (!typeObj.has("recommend_list")) {
                continue;
            }

            JsonArray recommendList = typeObj.getAsJsonArray("recommend_list");
            for (JsonElement recommendElement : recommendList) {
                JsonObject recommendObj = recommendElement.getAsJsonObject();
                String vodId = recommendObj.get("vod_id").getAsString();
                String vodName = recommendObj.get("vod_name").getAsString();
                String vodPic = recommendObj.get("vod_pic").getAsString();
                String vodRemarks = recommendObj.get("vod_remarks").getAsString();
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
        }

        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, String> params = new HashMap<>();

        String area = extend != null && extend.containsKey("area") ? extend.get("area") : "全部";
        params.put("area", area);

        String year = extend != null && extend.containsKey("year") ? extend.get("year") : "全部";
        params.put("year", year);

        params.put("type_id", tid);
        params.put("page", pg);

        String sort;
        if (extend != null && extend.containsKey("by") && extend.get("by") != null) {
            sort = extend.get("by");
        } else if (extend != null && extend.containsKey("sort") && extend.get("sort") != null) {
            sort = extend.get("sort");
        } else {
            sort = "最新";
        }
        params.put("sort", sort);

        String lang = extend != null && extend.containsKey("lang") ? extend.get("lang") : "全部";
        params.put("lang", lang);

        String classValue = "全部";
        if (extend != null && extend.containsKey("class") && extend.get("class") != null) {
            classValue = extend.get("class");
        }
        params.put("class", classValue);

        String decrypted = fetchAndDecrypt(".index/typeFilterVodList", params);
        JsonObject json = JsonParser.parseString(decrypted).getAsJsonObject();

        ArrayList<Vod> list = new ArrayList<>();
        JsonArray recommendList = json.getAsJsonArray("recommend_list");

        for (JsonElement element : recommendList) {
            JsonObject vodObj = element.getAsJsonObject();
            String vodId = vodObj.get("vod_id").getAsString();
            String vodName = vodObj.get("vod_name").getAsString();
            String vodPic = vodObj.get("vod_pic").getAsString();
            String vodRemarks = vodObj.get("vod_remarks").getAsString();
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 90, 1000000).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("vod_id", ids.get(0));

        String[] detailApis = {"vodDetail", "vodDetail2"};
        JsonObject vodData = null;

        for (String api : detailApis) {
            try {
                String url = apiUrl + ".index/" + api;
                String decrypted = fetchAndDecrypt(url, params);
                vodData = JsonParser.parseString(decrypted).getAsJsonObject();
                break;
            } catch (Exception e) {
                // 继续尝试下一个 API
            }
        }

        if (vodData == null) {
            ArrayList<Vod> emptyList = new ArrayList<>();
            return Result.string(emptyList);
        }

        JsonObject vodObj = vodData.getAsJsonObject("vod");
        StringBuilder playFromBuilder = new StringBuilder();
        StringBuilder playUrlBuilder = new StringBuilder();

        JsonArray playListArray = vodData.getAsJsonArray("vod_play_list");
        int lineNum = 1;

        for (JsonElement playElement : playListArray) {
            JsonObject playObj = playElement.getAsJsonObject();
            JsonObject playerInfo = playObj.getAsJsonObject("player_info");

            String show = playerInfo.get("show").getAsString();
            if (show.contains("防走丢") || show.contains("群") || show.contains("官网")) {
                show = lineNum + "线";
            }

            if (playFromBuilder.length() > 0) {
                playFromBuilder.append("$$$");
            }
            playFromBuilder.append(show);

            String parse = playerInfo.get("parse").getAsString();
            String parseType = playerInfo.get("parse_type").getAsString();
            String playerParseType = playerInfo.get("player_parse_type").getAsString();

            StringBuilder episodeBuilder = new StringBuilder();
            JsonArray urlsArray = playObj.getAsJsonArray("urls");

            for (JsonElement urlElement : urlsArray) {
                JsonObject urlObj = urlElement.getAsJsonObject();
                if (episodeBuilder.length() > 0) {
                    episodeBuilder.append("#");
                }

                episodeBuilder.append(urlObj.get("name").getAsString());
                episodeBuilder.append("$");
                episodeBuilder.append(parse);
                episodeBuilder.append(",");
                episodeBuilder.append(urlObj.get("url").getAsString());
                episodeBuilder.append(",");
                episodeBuilder.append("token+");
                episodeBuilder.append(urlObj.get("token").getAsString());
                episodeBuilder.append(",");
                episodeBuilder.append(playerParseType);
                episodeBuilder.append(",");
                episodeBuilder.append(parseType);
            }

            if (playUrlBuilder.length() > 0) {
                playUrlBuilder.append("$$$");
            }
            playUrlBuilder.append(episodeBuilder.toString());

            lineNum++;
        }

        Vod vod = new Vod(ids.get(0), vodObj.get("vod_name").getAsString(), vodObj.get("vod_pic").getAsString());

        String actor = vodObj.get("vod_actor").getAsString().replace("演员", "");
        vod.setVodActor(actor);

        if (vodObj.has("vod_director")) {
            String director = vodObj.get("vod_director").getAsString().replace("导演", "");
            vod.setVodDirector(director);
        }

        vod.setVodContent(vodObj.get("vod_content").getAsString());
        vod.setVodYear(vodObj.get("vod_year").getAsString() + "年");
        vod.setVodArea(vodObj.get("vod_area").getAsString());
        vod.setVodRemarks("时间:" + vodObj.get("vod_remarks").getAsString() + " 语言:" + vodObj.get("vod_lang").getAsString());
        vod.setVodPlayFrom(playFromBuilder.toString());
        vod.setVodPlayUrl(playUrlBuilder.toString());

        ArrayList<Vod> list = new ArrayList<>();
        list.add(vod);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split(",", 5);

        String parseType = parts.length > 3 ? parts[3] : DEFAULT_PARSE_TYPE;
        String playerParseType = parts.length > 4 ? parts[4] : DEFAULT_PARSE_TYPE;
        String token = parts.length > 2 ? parts[2].replace("token+", "") : "";

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DEFAULT_USER_AGENT);

        Result result = Result.get();

        if ("0".equals(playerParseType)) {
            result.parse(0).url(parts[1]);
        } else if ("2".equals(playerParseType)) {
            result.parse(1).url(parseType + parts[1]);
        } else if ("1".equals(parseType)) {
            String url = parseType + parts[1];
            String response = OkHttp.string(url, headers);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            String playUrl = json.get("url").getAsString();
            result.url(playUrl);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put("parse_api", parseType);
            params.put("url", Crypto.aesEncrypt(parts[1], dataKey, dataIv));
            params.put("player_parse_type", playerParseType);
            params.put("token", token);

            String decrypted = fetchAndDecrypt(".index/vodParse", params);
            JsonObject json = JsonParser.parseString(decrypted).getAsJsonObject();
            String jsonData = json.get("json").getAsString();

            JsonObject playJson = JsonParser.parseString(jsonData).getAsJsonObject();
            String playUrl = playJson.get("url").getAsString();
            result.url(playUrl);
        }

        result.header(headers);
        return result.string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (searchVerifyEnabled) {
            return Result.get().msg("本站搜索需验证码，JAR 版暂未接入 OCR，请换源或关闭搜索验证").string();
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("keywords", key);
        params.put("type_id", "0");
        params.put("page", pg);

        String url = apiUrl + ".index/searchList";
        String response = OkHttp.post(url, params, headers);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (!json.has("data") || json.get("data").isJsonNull()) {
            return Result.string(new ArrayList<>());
        }

        String encryptedData = json.get("data").getAsString();
        String decryptedData = Crypto.CBC(encryptedData, dataKey, dataIv);
        JsonObject searchJson = JsonParser.parseString(decryptedData).getAsJsonObject();

        ArrayList<Vod> list = new ArrayList<>();
        JsonArray searchList = searchJson.getAsJsonArray("search_list");

        for (JsonElement element : searchList) {
            JsonObject vodObj = element.getAsJsonObject();
            String vodId = vodObj.get("vod_id").getAsString();
            String vodName = vodObj.get("vod_name").getAsString();
            String vodPic = vodObj.get("vod_pic").getAsString();
            String vodYear = vodObj.get("vod_year").getAsString();
            String vodClass = vodObj.get("vod_class").getAsString();
            String remarks = vodYear + " " + vodClass;
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(list);
    }
}