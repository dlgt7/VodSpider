package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AppLY extends Spider {

    private static final String API_HOST = "https://fly.daoran.tv/API_ROP";
    private static final String PHOTO_HOST = "https://ottphoto.daoran.tv/HD/";
    private static final String UA = "okhttp/3.9.1";
    private static final String MD5 = "SkvyrWqK9QHTdCT12Rhxunjx+WwMTe9y4KwgeASFDhbYabRSPskR0Q==";
    private static final String PROJECT = "lyhxcx";
    private static final String USER_ID = "yszyz";

    public final String fetch(String path, HashMap<String, Object> params) {
        try {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json;charset=utf-8");
            headers.put("User-Agent", UA);
            headers.put("md5", MD5);
            String url = API_HOST.concat(path);
            String body = new JSONObject(params).toString();
            return OkHttp.post(url, body, headers);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        try {
            HashMap<String, Object> params = new HashMap<>();
            params.put("leastNum", "0");
            params.put("memberId", USER_ID);
            params.put("project", PROJECT);
            params.put("userId", USER_ID);
            JSONObject json = new JSONObject(fetch("/page/setinf/get", params));
            JSONArray sects = json.optJSONArray("sects");
            if (sects != null) {
                for (int i = 0; i < sects.length(); i++) {
                    JSONObject item = sects.getJSONObject(i);
                    classes.add(new Class(item.optString("code"), item.optString("name")));
                }
            }
        } catch (Exception e) {
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("cur", pg);
        params.put("free", "0");
        params.put("item", "o5");
        params.put("pageSize", "20");
        ArrayList<String> sect = new ArrayList<>();
        sect.add(tid);
        params.put("sect", sect);
        try {
            JSONObject json = new JSONObject(fetch("/search/album/list", params));
            JSONObject pb = json.getJSONObject("pb");
            JSONArray dataList = pb.getJSONArray("dataList");
            ArrayList<Vod> list = new ArrayList<>();
            for (int i = 0; i < dataList.length(); i++) {
                JSONObject item = dataList.getJSONObject(i);
                String code = item.optString("code");
                String name = item.optString("name");
                String pic = PHOTO_HOST + item.optString("img");
                String publishTime = item.optString("publishTime");
                list.add(new Vod(code, name, pic, publishTime));
            }
            int page = Integer.parseInt(pg);
            return Result.get().page(page, 9999, 20, 999999).vod(list).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String albumCode = ids.get(0);
        HashMap<String, Object> params = new HashMap<>();
        params.put("albumCode", albumCode);
        params.put("cur", "1");
        params.put("project", PROJECT);
        params.put("pageSize", "2147483647");
        params.put("selectFlag", "0");
        params.put("userId", USER_ID);
        JSONObject json = new JSONObject(fetch("/album/res/list", params));
        JSONObject album = json.getJSONObject("album");
        String name = album.optString("name");
        String pic = PHOTO_HOST + album.optString("img");
        Vod vod = new Vod(albumCode, name, pic);
        vod.setTypeName(album.optString("sect"));
        vod.setVodContent(album.optString("des"));
        vod.setVodActor(album.optString("artistName"));
        vod.setVodYear(album.optString("publishTime"));
        JSONObject pb = json.getJSONObject("pb");
        JSONArray dataList = pb.getJSONArray("dataList");
        ArrayList<String> playUrls = new ArrayList<>();
        for (int i = 0; i < dataList.length(); i++) {
            JSONObject item = dataList.getJSONObject(i);
            playUrls.add(item.optString("name") + "$" + item.optString("code"));
        }
        vod.setVodPlayFrom("在线播放");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("item", "y9");
        params.put("nodeCode", "001000");
        params.put("project", PROJECT);
        params.put("px", "2");
        params.put("resCode", id);
        params.put("userId", "92315ec6e58a45ba7f47fd143b3d7956");
        try {
            JSONObject json = new JSONObject(fetch("/play/get/playurl", params));
            JSONObject playres = json.getJSONObject("playres");
            String playurl = playres.optString("playurl");
            return Result.get().url(playurl).parse(0).string();
        } catch (Exception e) {
            return Result.get().url("").parse(0).string();
        }
    }
}
