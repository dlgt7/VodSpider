package com.github.catvod.crawler;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

public abstract class Spider {

    public String siteKey;

    public void init(Context context) throws Exception {
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    public String liveContent(String url) throws Exception {
        return "";
    }

    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    public boolean isVideoFormat(String url) throws Exception {
        return false;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    public String action(String action) throws Exception {
        return null;
    }

    public void destroy() {
    }

    public static Dns safeDns() {
        return null;
    }

    public static OkHttpClient client() {
        return null;
    }

    public String localProxy(String url) throws Exception {
        return "";
    }

    public String download(String url, String path) throws Exception {
        return "";
    }

    public String config() throws Exception {
        return "";
    }

    public String cache(String key) throws Exception {
        return "";
    }

    public void clearCache(String key) throws Exception {
    }

    public String playlist(String url) throws Exception {
        return "";
    }

    public String seriesContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String seasonContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String episodeContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String recommendContent() throws Exception {
        return "";
    }

    public String hotContent() throws Exception {
        return "";
    }

    public String rankContent() throws Exception {
        return "";
    }

    public String collectContent(List<String> ids) throws Exception {
        return "";
    }

    public String historyContent(List<String> ids) throws Exception {
        return "";
    }

    public String favoriteContent(List<String> ids) throws Exception {
        return "";
    }

    public String commentContent(String id, String pg) throws Exception {
        return "";
    }

    public String danmakuContent(String id) throws Exception {
        return "";
    }

    public String subtitleContent(String id) throws Exception {
        return "";
    }

    public String imageContent(String url) throws Exception {
        return "";
    }

    public String musicContent(String id) throws Exception {
        return "";
    }

    public String bookContent(String id) throws Exception {
        return "";
    }

    public String liveChannelContent(String url) throws Exception {
        return "";
    }

    public String liveProgramContent(String url) throws Exception {
        return "";
    }

    public String liveEpgContent(String url) throws Exception {
        return "";
    }

    public String liveSearchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String liveSearchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    public String livePlayerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    public String liveRecordContent(String url) throws Exception {
        return "";
    }

    public String liveCatchupContent(String url) throws Exception {
        return "";
    }
}
