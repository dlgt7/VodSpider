package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TingShijie extends Spider {

    private static final String CONFIG_URL = "http://101.43.48.231:8090/config/tingchina2025.txt";
    private static final String UA = "TingShiJie/1.8.8 (m.i275.com)";
    private static final String KEY = "J9gSpfUlzYxE8Hn5IXiGaD2jVMrwAm0K";
    private static final String PLAY_FROM = "世界听书";

    private static String b = "https://app.365ting.com/listen/Apitzg2025/";

    public static String c(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes("UTF-8"));
            BigInteger bigInt = new BigInteger(1, digest);
            StringBuilder sb = new StringBuilder(bigInt.toString(16));
            while (sb.length() < 32) {
                sb.insert(0, '0');
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    public final String a(String pg) {
        return Result.get().page(Integer.parseInt(pg), 1, 20, 0).vod(new ArrayList<>()).string();
    }

    public final Map<String, String> b() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        return headers;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            if (TextUtils.isEmpty(pg)) pg = "1";
            StringBuilder sb = new StringBuilder();
            sb.append(b).append("appHomeByCategory?categoryId=").append(tid).append("&page=").append(pg).append("&size=120");
            JSONObject json = new JSONObject(OkHttp.string(sb.toString(), null, b()));
            if (json.getInt("status") != 0) return a(pg);
            JSONArray data = json.getJSONArray("data");
            ArrayList<Vod> list = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                list.add(new Vod(item.optString("id"), item.optString("bookTitle"), item.optString("bookImage"), item.optString("bookAnchor")));
            }
            return Result.get().page(Integer.parseInt(pg), 100, 20, 2000).vod(list).string();
        } catch (Exception e) {
            return a(pg);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String bookId = ids.get(0);
        StringBuilder bookSb = new StringBuilder();
        bookSb.append(b).append("book?bookId=").append(bookId);
        JSONObject bookResp = new JSONObject(OkHttp.string(bookSb.toString(), null, b()));
        if (bookResp.getInt("status") != 0) {
            return Result.string(new Vod());
        }
        JSONObject bookData = bookResp.getJSONObject("data").getJSONObject("bookData");
        int count = bookData.getInt("count");
        StringBuilder playSb = new StringBuilder();
        int totalPages = (count + 999) / 1000;
        for (int page = 1; page <= totalPages; page++) {
            StringBuilder chapterSb = new StringBuilder();
            chapterSb.append(b).append("chapter?size=1000&page=").append(page).append("&sort=asc&bookId=").append(bookId);
            JSONObject chapterResp = new JSONObject(OkHttp.string(chapterSb.toString(), null, b()));
            if (chapterResp.getInt("status") != 0) continue;
            JSONArray chapterList = chapterResp.getJSONObject("data").getJSONArray("list");
            for (int i = 0; i < chapterList.length(); i++) {
                JSONObject chapter = chapterList.getJSONObject(i);
                if (playSb.length() > 0) playSb.append("#");
                playSb.append(chapter.getInt("position")).append("$").append(bookId).append("|").append(chapter.getString("chapterId"));
            }
        }
        Vod vod = new Vod(bookId, bookData.getString("bookTitle"), bookData.getString("bookImage"));
        vod.setVodContent(bookData.getString("bookDesc"));
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(playSb.toString());
        return Result.string(vod);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        List<String> ids = Arrays.asList("6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "49");
        List<String> names = Arrays.asList("玄幻奇幻", "都市言情", "宫斗女频", "官场商战", "武侠仙侠", "刑侦推理", "探险科幻", "重生穿越", "恐怖惊悚", "文学历史", "两性情感");
        for (int i = 0; i < ids.size(); i++) {
            classes.add(new Class(ids.get(i), names.get(i)));
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        String slash = "/";
        try {
            String resp = OkHttp.string(CONFIG_URL, b());
            if (!TextUtils.isEmpty(resp)) {
                b = resp.trim();
            }
            if (!b.endsWith(slash)) {
                b = b + slash;
            }
        } catch (Exception e) {
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String key = KEY;
        String emptyUrl = "";
        try {
            String[] parts = id.split("\\|");
            if (parts.length < 2) {
                return Result.get().url(emptyUrl).parse(0).string();
            }
            String timestamp = String.valueOf(System.currentTimeMillis());
            String addItParapet = c(c(timestamp + key) + key);
            StringBuilder sb = new StringBuilder();
            sb.append(b).append("AppGetChapterUrl2023?timeStamp=").append(timestamp).append("&uid=&chapterId=").append(parts[1]).append("&addItParapet=").append(addItParapet).append("&bookId=").append(parts[0]);
            JSONObject resp = new JSONObject(OkHttp.string(sb.toString(), null, b()));
            if (resp.getInt("status") == 0) {
                String src = resp.optString("src");
                if (!TextUtils.isEmpty(src)) {
                    return Result.get().url(src).parse(0).string();
                }
            }
        } catch (Exception e) {
        }
        return Result.get().url(emptyUrl).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(b).append("appSearch?client=babala-android&search=").append(key).append("&app_token=abcSEARCH-2025");
            JSONObject json = new JSONObject(OkHttp.string(sb.toString(), null, b()));
            if (json.getInt("status") != 0) {
                return Result.string(new ArrayList<>());
            }
            JSONArray bookData = json.getJSONObject("data").getJSONArray("bookData");
            ArrayList<Vod> list = new ArrayList<>();
            for (int i = 0; i < bookData.length(); i++) {
                JSONObject item = bookData.getJSONObject(i);
                list.add(new Vod(item.optString("id"), item.optString("bookTitle"), item.optString("bookImage"), item.optString("bookAnchor")));
            }
            return Result.string(list);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}
