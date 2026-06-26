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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChildrenDuoDuo Spider - 儿歌多多
 */
public class ChildrenDuoDuo extends Spider {

    // 分类数组：[分类ID, 分类名称]
    private static final String[][] a = {
            {"29", "视频儿歌"},
            {"40", "音频儿歌"},
            {"26", "动画片"},
            {"28", "学本领"},
            {"27", "故事"},
            {"36", "玩具屋"},
            {"38", "英文动画"}
    };

    /**
     * 从 JSONObject 获取名称
     * 优先级：artist > albumname > name
     */
    private static String a(JSONObject obj) {
        String result = obj.optString("artist");
        if (TextUtils.isEmpty(result)) {
            result = obj.optString("albumname");
        }
        if (TextUtils.isEmpty(result)) {
            result = obj.optString("name");
        }
        return result;
    }

    /**
     * 构建图片 URL (m480x270)
     * URL格式：http://tx.ergecdn.com/bb/img/album/m480x270/{id%1000}/{id}.jpg
     */
    private static String b(String id) {
        try {
            int idInt = Integer.parseInt(id);
            StringBuilder sb = new StringBuilder("http://tx.ergecdn.com/bb/img/album/m480x270/");
            sb.append(idInt % 1000);
            sb.append("/");
            sb.append(id);
            sb.append(".jpg");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 检查 JSONArray 是否有更多数据
     * 通过检查第一个元素的 hasmore 字段判断
     */
    private static boolean d(JSONArray array) {
        if (array == null || array.length() == 0) {
            return false;
        }
        JSONObject first = array.optJSONObject(0);
        if (first == null) {
            return false;
        }
        return first.optInt("hasmore", 0) == 1;
    }

    /**
     * 从 JSONObject 获取 list 数组
     */
    private static JSONArray e(JSONObject obj) {
        return obj.optJSONArray("list");
    }

    /**
     * 构建图片 URL (m320x180)
     * 如果 pic 字段不为空，优先使用 pic
     * 否则构建 URL：http://tx.ergecdn.com/bb/img/album/m320x180/{id%1000}/{id}.jpg
     */
    private static String f(String id, String pic) {
        if (!TextUtils.isEmpty(pic)) {
            return pic;
        }
        String url = b(id);
        if (!TextUtils.isEmpty(url)) {
            return url;
        }
        try {
            int idInt = Integer.parseInt(id);
            StringBuilder sb = new StringBuilder("http://tx.ergecdn.com/bb/img/album/m320x180/");
            sb.append(idInt % 1000);
            sb.append("/");
            sb.append(id);
            sb.append(".jpg");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从 JSONObject 获取名称
     * 优先级：name > artist
     */
    private static String g(JSONObject obj) {
        String result = obj.optString("name");
        if (TextUtils.isEmpty(result)) {
            result = obj.optString("artist");
        }
        return result;
    }

    /**
     * 发送 HTTP 请求获取数据
     * URL：http://bb.ergeduoduo.com/baby/bb.php?type={type}&collectid={collectid}&page={page}&pagesize={pagesize}&ver=1
     */
    private JSONObject c(String type, String collectid, String page, String pagesize) {
        try {
            StringBuilder sb = new StringBuilder("http://bb.ergeduoduo.com/baby/bb.php?type=");
            sb.append(type);
            sb.append("&collectid=");
            sb.append(collectid);
            sb.append("&page=");
            sb.append(page);
            sb.append("&pagesize=");
            sb.append(pagesize);
            sb.append("&ver=1");

            String url = sb.toString();
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");

            String response = OkHttp.string(url, null, headers);
            return new JSONObject(response);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String typeId = a[i][0];
            String typeName = a[i][1];

            // 检查分类是否有内容
            boolean hasContent = false;
            if ("40".equals(typeId)) {
                // 音频儿歌使用 getlist
                JSONObject result = c("getlist", typeId, "1", "1");
                JSONArray list = e(result);
                if (list != null && list.length() > 1) {
                    JSONObject item = list.optJSONObject(1);
                    if (item != null && !TextUtils.isEmpty(item.optString("downurl"))) {
                        hasContent = true;
                    }
                }
            } else {
                // 其他分类使用 getvideos
                JSONObject result = c("getvideos", typeId, "1", "1");
                JSONArray list = e(result);
                if (list != null && list.length() > 1) {
                    JSONObject item = list.optJSONObject(1);
                    if (item != null && !TextUtils.isEmpty(item.optString("downurl"))) {
                        hasContent = true;
                    }
                }
            }

            if (hasContent) {
                classes.add(new Class(typeId, typeName));
            }
        }

        return Result.string(classes, new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        // 音频儿歌（分类ID=40）使用 getlist，其他使用 getvideos
        if ("40".equals(tid)) {
            JSONObject result = c("getlist", tid, pg, "10");
            JSONArray list = e(result);
            ArrayList<Vod> vodList = new ArrayList<>();

            if (list != null) {
                for (int i = 1; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String downurl = item.optString("downurl");
                    if (TextUtils.isEmpty(downurl)) continue;

                    String id = item.optString("id");
                    String vodId = "a:" + id;
                    String name = g(item);
                    String pic = item.optString("pic");
                    if (TextUtils.isEmpty(pic)) {
                        pic = "http://tx.ergecdn.com/bb/video/pic/m320x180/48/343734137.jpg";
                    }
                    String remarks = item.optString("artist");

                    Vod vod = new Vod(vodId, name, pic, remarks);
                    vodList.add(vod);
                }
            }

            int page = Integer.parseInt(pg);
            int count = vodList.size();
            int total = 9999;
            int limit = 999999;
            boolean hasMore = d(list);
            int nextPage = hasMore ? page + 1 : page;

            return Result.get().page(page, nextPage, count, total).vod(vodList).string();
        } else {
            // 其他分类使用 getvideos
            JSONObject result = c("getvideos", tid, pg, "10");
            JSONArray list = e(result);

            // 使用 LinkedHashMap 去重（按 pid）
            LinkedHashMap<String, JSONObject> map = new LinkedHashMap<>();

            if (list != null) {
                for (int i = 1; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String downurl = item.optString("downurl");
                    if (TextUtils.isEmpty(downurl)) continue;

                    long pid = item.optLong("pid", 0);
                    String pidStr = String.valueOf(pid);
                    if ("0".equals(pidStr)) continue;
                    if (map.containsKey(pidStr)) continue;

                    map.put(pidStr, item);
                }
            }

            ArrayList<Vod> vodList = new ArrayList<>();
            for (Map.Entry<String, JSONObject> entry : map.entrySet()) {
                String pid = entry.getKey();
                JSONObject item = entry.getValue();

                String vodId = pid + "|||" + tid;
                String name = a(item);
                String pic = item.optString("pic");
                pic = f(pid, pic);

                Vod vod = new Vod(vodId, name, pic, "");
                vodList.add(vod);
            }

            int page = Integer.parseInt(pg);
            int count = vodList.size();
            int total = 9999;
            int limit = 999999;
            boolean hasMore = d(list);
            int nextPage = hasMore ? page + 1 : page;

            return Result.get().page(page, nextPage, count, total).vod(vodList).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);

        // 解析 ID
        String[] parts;
        String typeId = "29"; // 默认分类
        String flag = "0"; // 默认标识

        if (id.startsWith("a:")) {
            // 音频儿歌格式：a:{id}
            parts = new String[]{id.substring(2), "", "1"};
        } else if (id.contains("|||")) {
            // 视频格式：{pid}|||{typeId}
            String[] split = id.split("\\|\\|\\|", 2);
            parts = new String[]{split[0], split.length > 1 ? split[1] : "29", "0"};
        } else {
            // 其他格式
            parts = new String[]{id, "29", "0"};
        }

        String pid = parts[0];
        typeId = parts[1];
        flag = parts[2];

        if ("1".equals(flag)) {
            // 音频儿歌详情
            JSONObject item = null;
            for (int page = 1; page <= 30; page++) {
                JSONObject result = c("getlist", "40", String.valueOf(page), "10");
                JSONArray list = e(result);
                if (list == null || list.length() <= 1) break;

                for (int i = 1; i < list.length(); i++) {
                    JSONObject obj = list.optJSONObject(i);
                    if (obj != null && pid.equals(obj.optString("id"))) {
                        item = obj;
                        break;
                    }
                }

                if (item != null) break;
                if (!d(list)) break;
            }

            if (item == null) {
                return Result.string(new Vod(id, pid, "", ""));
            }

            String downurl = item.optString("downurl");
            String name = g(item);
            String pic = item.optString("pic");
            if (TextUtils.isEmpty(pic)) {
                pic = "http://tx.ergecdn.com/bb/video/pic/m320x180/48/343734137.jpg";
            }

            Vod vod = new Vod(id, name, pic);
            vod.setVodPlayFrom("儿歌多多");
            vod.setVodPlayUrl(g(item) + "$" + downurl);

            return Result.string(vod);
        } else {
            // 视频详情
            ArrayList<JSONObject> items = new ArrayList<>();
            for (int page = 1; page <= 30; page++) {
                JSONObject result = c("getvideos", pid, String.valueOf(page), "10");
                JSONArray list = e(result);
                if (list == null || list.length() <= 1) break;

                for (int i = 1; i < list.length(); i++) {
                    JSONObject obj = list.optJSONObject(i);
                    if (obj != null && !TextUtils.isEmpty(obj.optString("downurl"))) {
                        items.add(obj);
                    }
                }

                if (!d(list) && items.size() < 10) break;
            }

            // 如果没有找到内容，尝试使用 typeId 搜索
            if (items.isEmpty() && !TextUtils.isEmpty(typeId)) {
                for (int page = 1; page <= 20; page++) {
                    JSONObject result = c("getvideos", typeId, String.valueOf(page), "10");
                    JSONArray list = e(result);
                    if (list == null || list.length() <= 1) break;

                    int found = 0;
                    for (int i = 1; i < list.length(); i++) {
                        JSONObject obj = list.optJSONObject(i);
                        if (obj == null) continue;
                        long objPid = obj.optLong("pid", 0);
                        if (pid.equals(String.valueOf(objPid)) && !TextUtils.isEmpty(obj.optString("downurl"))) {
                            items.add(obj);
                            found++;
                        }
                    }

                    if (!d(list) && found == 0 && page > 1) break;
                    if (!d(list)) break;
                }
            }

            String pic = b(pid);
            String name = pid;
            if (!items.isEmpty()) {
                JSONObject first = items.get(0);
                name = a(first);
                pic = f(pid, first.optString("pic"));
            }

            Vod vod = new Vod(id, name, pic);
            vod.setVodPlayFrom("儿歌多多");

            ArrayList<String> playUrls = new ArrayList<>();
            for (JSONObject item : items) {
                String downurl = item.optString("downurl");
                if (TextUtils.isEmpty(downurl)) continue;
                playUrls.add(g(item) + "$" + downurl);
            }

            vod.setVodPlayUrl(TextUtils.join("#", playUrls));

            return Result.string(vod);
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");

        return Result.get().url(id).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        ArrayList<Vod> vodList = new ArrayList<>();

        for (int page = 1; page <= 3; page++) {
            JSONObject result = c("getvideos", "29", String.valueOf(page), "10");
            JSONArray list = e(result);
            if (list == null) break;

            LinkedHashMap<String, JSONObject> map = new LinkedHashMap<>();
            for (int i = 1; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String downurl = item.optString("downurl");
                if (TextUtils.isEmpty(downurl)) continue;

                long pid = item.optLong("pid", 0);
                String pidStr = String.valueOf(pid);
                if ("0".equals(pidStr)) continue;
                if (map.containsKey(pidStr)) continue;

                // 搜索匹配
                String name1 = a(item);
                String name2 = g(item);
                if (name1.contains(key) || name2.contains(key)) {
                    map.put(pidStr, item);

                    String vodId = pidStr + "|||29";
                    String pic = item.optString("pic");
                    pic = f(pidStr, pic);

                    Vod vod = new Vod(vodId, name1, pic, "");
                    vodList.add(vod);
                }

                if (vodList.size() >= 30) break;
            }

            if (vodList.size() >= 30) break;
            if (!d(list)) break;
        }

        return Result.string(vodList);
    }
}