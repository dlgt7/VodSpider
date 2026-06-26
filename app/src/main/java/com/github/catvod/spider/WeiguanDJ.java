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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * WeiguanDJ Spider
 */
public class WeiguanDJ extends Spider {

    private String b; // 设备名称
    private String c; // 设备品牌
    private String d; // 客户端信息

    public WeiguanDJ() {
        this.b = "";
        this.c = "";
        this.d = "";
    }

    /**
     * 获取请求头
     */
    private Map<String, String> a() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/5.1.0");
        return headers;
    }

    /**
     * 解析视频列表
     */
    private ArrayList<Vod> b(JSONArray array) {
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                String oneId = obj.optString("oneId");
                String title = obj.optString("title");
                String horzPoster = obj.optString("horzPoster");
                String episodeCount = obj.optString("episodeCount");
                list.add(new Vod(oneId, title, horzPoster, episodeCount));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    /**
     * 构建请求参数字符串
     */
    private String c() {
        StringBuilder sb = new StringBuilder("?version_code=1500&version_name=1.5.0&device_name=");
        sb.append(b);
        sb.append("&device_type=phone&is_first_day=true&is_first_24h=true&app_launch_way=icon&default_homepage=homepage_interaction&device_owning_firm=");
        sb.append(c);
        sb.append("&font_scale=default&os_type=1&clientInfo=");
        sb.append(d);
        return sb.toString();
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        
        // 获取设备型号
        b = Build.MODEL;
        
        // 获取设备品牌
        c = Build.BRAND;
        
        // 生成随机字符串并计算 MD5
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(62)));
        }
        
        // 计算 MD5
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder md5Builder = new StringBuilder();
            for (byte byteValue : digest) {
                String hex = Integer.toHexString(byteValue & 0xff);
                if (hex.length() == 1) {
                    md5Builder.append('0');
                }
                md5Builder.append(hex);
            }
            d = md5Builder.toString();
        } catch (Exception e) {
            d = sb.toString();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        
        try {
            StringBuilder sb = new StringBuilder("https://api.drama.9ddm.com/drama/home/shortVideoTags");
            sb.append(c());
            
            String response = OkHttp.string(sb.toString(), a());
            JSONObject json = new JSONObject(response);
            JSONArray tags = json.getJSONArray("tags");
            
            for (int i = 0; i < tags.length(); i++) {
                String tag = tags.getString(i);
                classes.add(new Class(tag, tag));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return Result.string(classes, new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        // 如果 extend 包含 cateId，使用它替代 tid
        String subject = tid;
        if (extend != null && extend.containsKey("cateId")) {
            subject = extend.get("cateId");
        }
        
        try {
            JSONObject params = new JSONObject();
            params.put("audience", "全部");
            params.put("order", "最新");
            params.put("page", Integer.parseInt(pg));
            params.put("pageSize", 30);
            params.put("searchWord", "");
            params.put("subject", subject);
            
            StringBuilder sb = new StringBuilder("https://api.drama.9ddm.com/drama/home/search");
            sb.append(c());
            
            String response = OkHttp.post(sb.toString(), params.toString(), a());
            JSONObject json = new JSONObject(response);
            JSONArray data = json.getJSONArray("data");
            
            ArrayList<Vod> list = b(data);
            
            int page = Integer.parseInt(pg);
            int limit = 30;
            int totalPage;
            if (list.size() < limit) {
                totalPage = page;
            } else {
                totalPage = page + 1;
            }
            
            return Result.get().vod(list).page(page, totalPage, limit, 0).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().vod(new ArrayList<>()).page(1, 1, 0, 0).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String oneId = ids.get(0);
        
        try {
            StringBuilder sb = new StringBuilder("https://api.drama.9ddm.com/drama/home/shortVideoDetail");
            sb.append(c());
            sb.append("&oneId=");
            sb.append(oneId);
            sb.append("&page=1&pageSize=10&userId=0&queryAll=true");
            
            String response = OkHttp.string(sb.toString(), a());
            JSONObject json = new JSONObject(response);
            
            String title = json.optString("title");
            String vertPoster = json.optString("vertPoster");
            
            Vod vod = new Vod(oneId, title, vertPoster);
            vod.setVodRemarks("短剧");
            vod.setVodContent(json.optString("description"));
            
            LinkedHashMap<String, String> playMap = new LinkedHashMap<>();
            JSONArray data = json.getJSONArray("data");
            ArrayList<String> episodes = new ArrayList<>();
            
            for (int i = 0; i < data.length(); i++) {
                JSONObject episode = data.getJSONObject(i);
                String playOrder = episode.optString("playOrder");
                JSONArray videoClarityList = episode.getJSONArray("videoClarityList");
                
                // Base64 编码
                String encoded = Base64.encodeToString(videoClarityList.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                episodes.add(playOrder + "$" + encoded);
            }
            
            if (!episodes.isEmpty()) {
                playMap.put("短剧", TextUtils.join("#", episodes));
            }
            
            if (!playMap.isEmpty()) {
                vod.setVodPlayFrom(TextUtils.join("$$$", playMap.keySet()));
                vod.setVodPlayUrl(TextUtils.join("$$$", playMap.values()));
            }
            
            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取详情失败");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // Base64 解码
            byte[] decoded = Base64.decode(id, Base64.DEFAULT);
            String jsonStr = new String(decoded, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(jsonStr);
            
            ArrayList<String> urls = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                urls.add(obj.optString("name"));
                urls.add(obj.optString("url"));
            }
            
            return Result.get().url(urls).header(a()).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("解析播放链接失败");
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            JSONObject params = new JSONObject();
            params.put("audience", "");
            params.put("order", "");
            params.put("page", Integer.parseInt(pg));
            params.put("pageSize", 30);
            params.put("searchWord", key);
            params.put("subject", "");
            
            StringBuilder sb = new StringBuilder("https://api.drama.9ddm.com/drama/home/search");
            sb.append(c());
            
            String response = OkHttp.post(sb.toString(), params.toString(), a());
            JSONObject json = new JSONObject(response);
            JSONArray data = json.getJSONArray("data");
            
            ArrayList<Vod> list = b(data);
            
            return Result.string(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(new ArrayList<>());
        }
    }
}