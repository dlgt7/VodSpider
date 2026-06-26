package com.github.catvod.api;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.catvod.bean.Danmaku;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 弹幕API调用类
 * 
 * 支持多API源备份和本地缓存
 * 主要API: 弹弹play开放平台
 * API文档: https://api.dandanplay.net/swagger
 * 申请AppId: https://dev.dandanplay.com
 * 
 * 简繁转换：优先使用弹弹play API的chConvert参数，本地转换仅作备用
 */
public class DanmakuApi {

    private static final String TAG = "DanmakuApi";
    private static final Map<String, Call> calls = new ConcurrentHashMap<>();
    private static final AtomicInteger requestId = new AtomicInteger(0);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 最大备用源尝试次数（防止无限循环）
    private static final int MAX_BACKUP_RETRIES = 2;

    // 弹弹play API路径
    private static final String PATH_SEARCH = "/api/v2/search/anime";
    private static final String PATH_COMMENT = "/api/v2/comment/";

    // 弹弹play 简繁转换参数
    private static final int CH_CONVERT_NONE = 0;   // 不转换
    private static final int CH_CONVERT_SIMP = 1;   // 转换为简体
    private static final int CH_CONVERT_TRAD = 2;   // 转换为繁体

    // 多API源支持
    private static final String[] BACKUP_API_URLS = {
        "https://api.dandanplay.net",
        "https://api.obfs.dev"
    };
    
    // 当前使用的API源索引
    private static int currentApiIndex = 0;

    public static boolean isEnabled() {
        return DanmakuSetting.isLoad() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
    }

    /**
     * 获取当前使用的API URL
     */
    public static String getCurrentApiUrl() {
        String customUrl = DanmakuSetting.getEffectiveApiUrl();
        if (!TextUtils.isEmpty(customUrl)) {
            return customUrl;
        }
        return BACKUP_API_URLS[currentApiIndex];
    }

    /**
     * 切换到下一个可用的API源
     */
    private static boolean switchToNextApi() {
        int originalIndex = currentApiIndex;
        for (int i = 0; i < BACKUP_API_URLS.length; i++) {
            currentApiIndex = (currentApiIndex + 1) % BACKUP_API_URLS.length;
            if (currentApiIndex != originalIndex) {
                SpiderDebug.log("切换弹幕API源: " + BACKUP_API_URLS[currentApiIndex]);
                return true;
            }
        }
        return false;
    }

    /**
     * 重置API源索引
     */
    public static void resetApiSource() {
        currentApiIndex = 0;
    }

    /**
     * 搜索番剧弹幕
     * @param keyword 搜索关键词（番剧名称）
     * @return 搜索结果列表
     */
    public static List<Danmaku> searchAnime(String keyword) {
        if (!isEnabled()) return new ArrayList<>();
        
        String url = buildSearchUrl(keyword);
        
        try {
            String result = OkHttp.string(url, buildHeaders());
            return parseSearchResult(result, keyword);
        } catch (Exception e) {
            SpiderDebug.log("弹幕搜索失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 异步搜索番剧弹幕
     * @param keyword 搜索关键词
     * @param callback 搜索结果回调（返回列表）
     */
    public static void searchAnimeAsync(String keyword, DanmakuListCallback callback) {
        if (!isEnabled()) {
            callCallbackOnMainThread(callback::onError, "弹幕API未启用");
            return;
        }

        String url = buildSearchUrl(keyword);
        fetchSearchAsync(url, keyword, callback);
    }

    /**
     * 根据番剧ID获取弹幕列表
     * @param animeId 番剧ID
     * @param episode 集数（可选，传入null获取所有）
     * @return 弹幕列表
     */
    public static List<Danmaku> getDanmakuList(String animeId, String episode) {
        if (!isEnabled()) return new ArrayList<>();
        
        String url = buildCommentUrl(animeId, episode);
        
        try {
            String result = OkHttp.string(url, buildHeaders());
            return parseCommentResult(result);
        } catch (Exception e) {
            SpiderDebug.log("获取弹幕列表失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 异步获取弹幕列表
     */
    public static void getDanmakuAsync(String animeId, String episode, DanmakuListCallback callback) {
        if (!isEnabled()) {
            callCallbackOnMainThread(callback::onError, "弹幕API未启用");
            return;
        }

        String url = buildCommentUrl(animeId, episode);
        fetchCommentAsync(url, callback);
    }

    /**
     * 综合搜索弹幕（根据名称和集数自动匹配）
     * 先搜索番剧，再获取弹幕
     * 支持本地缓存和多API源备份
     * 
     * @param name 番剧名称
     * @param episode 集数（可选）
     * @param callback 搜索回调
     */
    public static void searchDanmaku(String name, String episode, DanmakuSearchCallback callback) {
        if (!isEnabled()) {
            callCallbackOnMainThread(callback::onError, "弹幕API未启用");
            return;
        }

        if (TextUtils.isEmpty(name)) {
            callCallbackOnMainThread(callback::onError, "番剧名称不能为空");
            return;
        }

        // 1. 先检查缓存
        if (DanmakuCache.isEnabled()) {
            List<Danmaku> cached = DanmakuCache.get(name, episode);
            if (cached != null && !cached.isEmpty()) {
                SpiderDebug.log("弹幕命中缓存: " + name);
                Danmaku danmaku = new Danmaku();
                danmaku.setName(name);
                danmaku.setUrl(episode != null ? episode : "0");
                callCallbackOnMainThread(() -> callback.onSuccess(danmaku, cached));
                return;
            }
        }

        // 2. 异步搜索番剧（带备用源重试）
        searchWithBackup(name, episode, callback, 0);
    }

    /**
     * 带备用源重试的搜索
     */
    private static void searchWithBackup(String name, String episode, DanmakuSearchCallback callback, int retryCount) {
        if (retryCount > MAX_BACKUP_RETRIES) {
            callCallbackOnMainThread(callback::onError, "所有API源均失败");
            return;
        }

        searchAnimeAsync(name, new DanmakuListCallback() {
            @Override
            public void onSuccess(List<Danmaku> results) {
                if (results == null || results.isEmpty()) {
                    // 结果为空，尝试备用源
                    if (switchToNextApi()) {
                        searchWithBackup(name, episode, callback, retryCount + 1);
                    } else {
                        callCallbackOnMainThread(callback::onError, "未找到相关番剧");
                    }
                } else {
                    fetchDanmakuList(results, name, episode, callback);
                }
            }

            @Override
            public void onError(String error) {
                // 请求失败，尝试备用源
                if (switchToNextApi()) {
                    searchWithBackup(name, episode, callback, retryCount + 1);
                } else {
                    callCallbackOnMainThread(() -> callback.onError(error));
                }
            }
        });
    }

    /**
     * 获取弹幕列表
     */
    private static void fetchDanmakuList(List<Danmaku> results, String name, String episode, DanmakuSearchCallback callback) {
        // 选择最佳匹配
        Danmaku bestMatch = findBestMatch(results, name, episode);
        
        // 获取该番剧的弹幕列表
        getDanmakuAsync(bestMatch.getUrl(), episode, new DanmakuListCallback() {
            @Override
            public void onSuccess(List<Danmaku> danmakuList) {
                if (danmakuList == null || danmakuList.isEmpty()) {
                    callCallbackOnMainThread(callback::onError, "未找到弹幕");
                    return;
                }
                
                // 保存到缓存
                if (DanmakuCache.isEnabled()) {
                    DanmakuCache.put(name, episode, danmakuList);
                }
                
                callCallbackOnMainThread(() -> callback.onSuccess(bestMatch, danmakuList));
            }

            @Override
            public void onError(String error) {
                callCallbackOnMainThread(() -> callback.onError(error));
            }
        });
    }

    /**
     * 查找最佳匹配的番剧
     */
    private static Danmaku findBestMatch(List<Danmaku> results, String keyword, String episode) {
        if (results == null || results.isEmpty()) {
            return Danmaku.empty();
        }
        
        if (results.size() == 1) {
            return results.get(0);
        }
        
        String lowerKeyword = keyword.toLowerCase();
        Danmaku bestMatch = results.get(0);
        int bestScore = 0;
        
        for (Danmaku danmaku : results) {
            int score = calculateMatchScore(danmaku.getName(), lowerKeyword, episode);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = danmaku;
            }
        }
        
        return bestMatch;
    }

    /**
     * 计算匹配分数
     */
    private static int calculateMatchScore(String animeTitle, String keyword, String episode) {
        if (animeTitle == null) return 0;
        
        String lowerTitle = animeTitle.toLowerCase();
        int score = 0;
        
        // 完全匹配
        if (lowerTitle.equals(keyword)) {
            score = 1000;
        }
        // 关键词在标题开头
        else if (lowerTitle.startsWith(keyword)) {
            score = 800;
        }
        // 关键词在标题中
        else if (lowerTitle.contains(keyword)) {
            score = 500;
        }
        // 关键词是标题的子串（反向）
        else if (keyword.contains(lowerTitle)) {
            score = 300;
        }
        // 部分字符匹配
        else {
            int matchCount = 0;
            for (char c : keyword.toCharArray()) {
                if (lowerTitle.indexOf(c) >= 0) {
                    matchCount++;
                }
            }
            score = matchCount * 10;
        }
        
        // 集数加成
        if (!TextUtils.isEmpty(episode) && lowerTitle.contains(episode)) {
            score += 100;
        }
        
        return score;
    }

    public static void cancelAll() {
        for (Call call : calls.values()) {
            call.cancel();
        }
        calls.clear();
    }

    private static String buildSearchUrl(String keyword) {
        String encodedKeyword = encode(keyword);
        
        String baseUrl = getCurrentApiUrl();
        StringBuilder url = new StringBuilder(baseUrl);
        
        if (!baseUrl.endsWith("/") && !PATH_SEARCH.startsWith("/")) {
            url.append("/");
        }
        url.append(PATH_SEARCH).append("?keyword=").append(encodedKeyword);
        
        // 使用弹弹play的简繁转换参数（转换为简体）
        url.append("&chConvert=").append(CH_CONVERT_SIMP);
        
        // 添加AppId
        appendAppId(url);
        
        return url.toString();
    }

    private static String buildCommentUrl(String animeId, String episode) {
        String baseUrl = getCurrentApiUrl();
        StringBuilder url = new StringBuilder(baseUrl);
        
        if (!baseUrl.endsWith("/") && !PATH_COMMENT.startsWith("/")) {
            url.append("/");
        }
        url.append(PATH_COMMENT).append(animeId);
        
        // 添加集数参数
        if (!TextUtils.isEmpty(episode)) {
            url.append("?episode=").append(episode);
        }
        
        // 使用弹弹play的简繁转换参数
        if (TextUtils.isEmpty(episode)) {
            url.append("?chConvert=").append(CH_CONVERT_SIMP);
        } else {
            url.append("&chConvert=").append(CH_CONVERT_SIMP);
        }
        
        // 添加AppId
        appendAppId(url);
        
        return url.toString();
    }

    private static void appendAppId(StringBuilder url) {
        String appId = DanmakuSetting.getAppId();
        if (!TextUtils.isEmpty(appId)) {
            url.append("&appId=").append(appId);
        }
    }

    private static Map<String, String> buildHeaders() {
        Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "CatVodSpider/1.0");
        return headers;
    }

    private static void fetchSearchAsync(String url, String keyword, DanmakuListCallback callback) {
        String tag = TAG + "_search_" + requestId.incrementAndGet();
        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .tag(tag)
                    .headers(okhttp3.Headers.of(buildHeaders()))
                    .build();
            
            Call call = OkHttp.client().newCall(request);
            calls.put(tag, call);
            
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    calls.remove(tag);
                    callCallbackOnMainThread(() -> callback.onError(e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    calls.remove(tag);
                    try {
                        String result = response.body() != null ? response.body().string() : "";
                        DanmakuCache.putLastResult(result);
                        List<Danmaku> list = parseSearchResult(result, keyword);
                        callCallbackOnMainThread(() -> callback.onSuccess(list));
                    } catch (Exception e) {
                        callCallbackOnMainThread(() -> callback.onError(e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            callCallbackOnMainThread(() -> callback.onError(e.getMessage()));
        }
    }

    private static void fetchCommentAsync(String url, DanmakuListCallback callback) {
        String tag = TAG + "_comment_" + requestId.incrementAndGet();
        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .tag(tag)
                    .headers(okhttp3.Headers.of(buildHeaders()))
                    .build();
            
            Call call = OkHttp.client().newCall(request);
            calls.put(tag, call);
            
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    calls.remove(tag);
                    callCallbackOnMainThread(() -> callback.onError(e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    calls.remove(tag);
                    try {
                        String result = response.body() != null ? response.body().string() : "";
                        List<Danmaku> list = parseCommentResult(result);
                        callCallbackOnMainThread(() -> callback.onSuccess(list));
                    } catch (Exception e) {
                        callCallbackOnMainThread(() -> callback.onError(e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            callCallbackOnMainThread(() -> callback.onError(e.getMessage()));
        }
    }

    private static List<Danmaku> parseSearchResult(String json, String keyword) {
        List<Danmaku> list = new ArrayList<>();
        try {
            if (TextUtils.isEmpty(json)) return list;
            
            JsonObject obj = Json.safeObject(json);
            JsonArray arr = Json.getJsonArray(obj, "animes");
            
            if (arr == null || arr.size() == 0) return list;
            
            for (int i = 0; i < arr.size(); i++) {
                try {
                    JsonObject item = arr.get(i).getAsJsonObject();
                    Danmaku danmaku = new Danmaku();
                    danmaku.setName(Json.getString(item, "animeTitle"));
                    danmaku.setUrl(Json.getString(item, "animeId"));
                    
                    // 尝试获取 episodeId（用于获取弹幕）
                    String episodeId = Json.getString(item, "episodeId");
                    if (!TextUtils.isEmpty(episodeId)) {
                        danmaku.setUrl(episodeId);
                    }
                    
                    list.add(danmaku);
                } catch (Exception e) {
                    // 跳过解析失败的项
                }
            }
            
            // 按匹配度排序
            if (!TextUtils.isEmpty(keyword)) {
                final String lowerKeyword = keyword.toLowerCase();
                list.sort((a, b) -> {
                    int scoreA = calculateMatchScore(a.getName(), lowerKeyword, null);
                    int scoreB = calculateMatchScore(b.getName(), lowerKeyword, null);
                    return scoreB - scoreA;
                });
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    private static List<Danmaku> parseCommentResult(String json) {
        List<Danmaku> list = new ArrayList<>();
        try {
            if (TextUtils.isEmpty(json)) return list;
            
            JsonObject obj = Json.safeObject(json);
            JsonArray arr = Json.getJsonArray(obj, "comments");
            
            if (arr == null || arr.size() == 0) return list;
            
            for (int i = 0; i < arr.size(); i++) {
                try {
                    JsonObject item = arr.get(i).getAsJsonObject();
                    Danmaku danmaku = new Danmaku();
                    danmaku.setName(Json.getString(item, "episodeTitle"));
                    danmaku.setUrl(Json.getString(item, "commentId"));
                    list.add(danmaku);
                } catch (Exception e) {
                    // 跳过解析失败的项
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    private static String encode(String text) {
        if (text == null) return "";
        try {
            return java.net.URLEncoder.encode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 在主线程执行回调
     */
    private static void callCallbackOnMainThread(Runnable callback) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.run();
        } else {
            mainHandler.post(callback);
        }
    }

    private static void callCallbackOnMainThread(ErrorCallback callback, String error) {
        callCallbackOnMainThread(() -> callback.onError(error));
    }

    @FunctionalInterface
    private interface ErrorCallback {
        void onError(String error);
    }

    /**
     * 弹幕列表回调接口
     */
    public interface DanmakuListCallback {
        void onSuccess(List<Danmaku> results);
        void onError(String error);
    }

    /**
     * 弹幕搜索回调接口
     */
    public interface DanmakuSearchCallback {
        void onSuccess(Danmaku danmaku, List<Danmaku> allDanmaku);
        void onError(String error);
    }
}
