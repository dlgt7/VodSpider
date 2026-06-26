package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class XyDuanJu extends Spider {

    private static final String siteUrl = "https://app.whjzjx.cn";
    private static final String loginUrl = "https://u.shytkjgs.com/user/v3/account/login";

    private static String authToken = "";

    private final HashMap<String, String> headerx = new HashMap<String, String>() {{
        put("platform", "1");
        put("version_name", "3.8.3.1");
    }};

    private final HashMap<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Linux; Android 9; V1938T Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36");
    }};

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
        } catch (Exception e) {
            e.printStackTrace();
        }
        login();
    }

    private void login() {
        try {
            long timestamp = System.currentTimeMillis();
            JSONObject data = new JSONObject();
            data.put("device", "2a50580e69d38388c94c93605241fb306");
            data.put("package_name", "com.jz.xydj");
            data.put("android_id", "ec1280db12795506");
            data.put("install_first_open", true);
            data.put("first_install_time", 1752505243345L);
            data.put("last_update_time", 1752505243345L);
            data.put("report_link_url", "");
            data.put("authorization", "");
            data.put("timestamp", timestamp);

            String plainText = data.toString();
            String key = "B@ecf920Od8A4df7";
            String encrypted = aesEncrypt(plainText, key);

            HashMap<String, String> loginHeaders = new HashMap<>();
            loginHeaders.put("platform", "1");
            loginHeaders.put("user_agent", "Mozilla/5.0 (Linux; Android 9; V1938T Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36");
            loginHeaders.put("Content-Type", "application/json; charset=utf-8");

            String response = OkHttp.post(loginUrl, encrypted, loginHeaders);
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("data") && jsonResponse.getJSONObject("data").has("token")) {
                authToken = jsonResponse.getJSONObject("data").getString("token");
                headerx.put("authorization", authToken);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String aesEncrypt(String plainText, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "剧场"));
        classes.add(new Class("3", "新剧"));
        classes.add(new Class("2", "热播"));
        classes.add(new Class("7", "星选"));
        classes.add(new Class("5", "阳光"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() {
        String url = siteUrl + "/v1/theater/home_page?theater_class_id=1&class2_id=4&page_num=1&page_size=24";
        String content = OkHttp.string(url, headerx);
        List<Vod> list = new ArrayList<>();

        try {
            JSONObject json = new JSONObject(content);
            JSONArray array = json.optJSONObject("data").optJSONArray("list");
            if (array == null || array.length() == 0) {
                return Result.string(list);
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject vod = array.optJSONObject(i).optJSONObject("theater");
                if (vod == null) continue;
                String id = vod.optString("id");
                String name = vod.optString("title");
                String pic = vod.optString("cover_url");
                String remark = vod.optString("play_amount_str");
                list.add(new Vod(id, name, pic, remark));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String url = siteUrl + "/v1/theater/home_page?theater_class_id=" + tid + "&page_num=" + pg + "&page_size=24";
        String content = OkHttp.string(url, headerx);
        List<Vod> list = new ArrayList<>();
        int page = Integer.parseInt(pg);

        try {
            JSONObject json = new JSONObject(content);
            JSONArray array = json.optJSONObject("data").optJSONArray("list");
            if (array == null || array.length() == 0) {
                return Result.get().vod(list).page(page, page, 90, 0).string();
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject vod = array.optJSONObject(i).optJSONObject("theater");
                if (vod == null) continue;
                String id = vod.optString("id");
                String name = vod.optString("title");
                String pic = vod.optString("cover_url");
                String remark = vod.optString("theme");
                list.add(new Vod(id, name, pic, remark));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        String did = ids.get(0);
        String url = siteUrl + "/v2/theater_parent/detail?theater_parent_id=" + did;
        String content = OkHttp.string(url, headerx);
        List<Vod> list = new ArrayList<>();

        try {
            JSONObject json = new JSONObject(content);
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                return Result.string(list);
            }

            // 获取Jumps备用
            String jumps = "";
            try {
                String jumpUrl = "https://fs-im-kefu.7moor-fs1.com/ly/4d2c3f00-7d4c-11e5-af15-41bf63ae4ea0/1732707176882/jiduo.txt";
                String jumpContent = OkHttp.string(jumpUrl, headers);
                int s2Index = jumpContent.indexOf("s2='");
                if (s2Index != -1) {
                    int start = s2Index + 4;
                    int end = jumpContent.indexOf("'", start);
                    if (end != -1 && end > start) {
                        jumps = jumpContent.substring(start, end);
                    }
                }
            } catch (Exception e) {
                // 忽略获取Jumps的错误
            }

            Vod vod = new Vod();
            vod.setVodId(did);

            String contentStr = "剧情：" + data.optString("introduction");
            vod.setVodContent(contentStr);
            vod.setVodRemarks(data.optString("filing"));
            JSONArray descTags = data.optJSONArray("desc_tags");
            vod.setVodArea(descTags != null && descTags.length() > 0 ? descTags.optString(0) : "");

            StringBuilder bofang = new StringBuilder();
            String xianlu = "";

            JSONArray theaters = data.optJSONArray("theaters");
            if (theaters != null && theaters.length() > 0) {
                for (int i = 0; i < theaters.length(); i++) {
                    JSONObject sou = theaters.optJSONObject(i);
                    if (sou == null) continue;
                    String name = sou.optString("num");
                    String sid = sou.optString("son_video_url");
                    bofang.append(name).append("$").append(sid).append("#");
                }
                if (bofang.length() > 0 && bofang.charAt(bofang.length() - 1) == '#') {
                    bofang.setLength(bofang.length() - 1);
                }
                xianlu = "星芽";
            } else {
                String videoUrl = data.optString("video_url");
                if (!videoUrl.isEmpty()) {
                    bofang.append("1$").append(videoUrl);
                    xianlu = "星芽";
                } else {
                    bofang.append(jumps);
                    xianlu = "1";
                }
            }

            vod.setVodPlayFrom(xianlu);
            vod.setVodPlayUrl(bofang.toString());
            list.add(vod);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().parse(0).url(id).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        String url = siteUrl + "/v3/search";
        int page = Integer.parseInt(pg);
        List<Vod> list = new ArrayList<>();

        try {
            JSONObject payload = new JSONObject();
            payload.put("text", key);

            HashMap<String, String> searchHeaders = new HashMap<>(headerx);
            String response = OkHttp.post(url, payload.toString(), searchHeaders);
            JSONObject json = new JSONObject(response);

            JSONObject theater = json.optJSONObject("data").optJSONObject("theater");
            if (theater == null) {
                return Result.get().vod(list).page(page, page, 90, 0).string();
            }

            JSONArray searchData = theater.optJSONArray("search_data");
            if (searchData == null || searchData.length() == 0) {
                return Result.get().vod(list).page(page, page, 90, 0).string();
            }

            for (int i = 0; i < searchData.length(); i++) {
                JSONObject vod = searchData.optJSONObject(i);
                if (vod == null) continue;
                String id = vod.optString("id");
                String name = vod.optString("title");
                String pic = vod.optString("cover_url");
                String remark = vod.optString("score_str");
                list.add(new Vod(id, name, pic, remark));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }
}
