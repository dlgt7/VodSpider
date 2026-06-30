package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Notify;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 夸克应用商店
 */
public class QuarkAppStore extends Spider {

    private static final Pattern SHARE_PATTERN = Pattern.compile("https?://pan\\.quark\\.cn/s/([a-zA-Z0-9]+)(?:#(.*))?");

    private static final String DEFAULT_SHARE_JSON = "[{\"shareId\":\"9a41cd6f82bc\",\"folder\":\"0\"}]";
    private static final String FOLDER_ICON = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/folder.png";

    private String shareJson = DEFAULT_SHARE_JSON;
    private String userName = "";

    public QuarkAppStore() {
        super();
    }

    /**
     * 解析分享链接，提取shareId和sharePwd
     */
    private void parseShareUrl(String url) {
        try {
            if (TextUtils.isEmpty(url)) return;

            if (url.startsWith("http")) {
                Matcher matcher = SHARE_PATTERN.matcher(url);
                if (matcher.find()) {
                    String shareId = matcher.group(1);
                    String sharePwd = matcher.groupCount() >= 2 ? matcher.group(2) : "";

                    JSONArray jsonArray = new JSONArray();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("shareId", shareId);
                    jsonObject.put("folder", "0");

                    if (!TextUtils.isEmpty(sharePwd)) {
                        jsonObject.put("sharePwd", sharePwd);
                    }

                    jsonArray.put(jsonObject);
                    shareJson = jsonArray.toString();
                }
            } else if (url.startsWith("{")) {
                JSONObject jsonObject = new JSONObject(url);
                if (jsonObject.has("store")) {
                    shareJson = jsonObject.optString("store", DEFAULT_SHARE_JSON);
                } else if (jsonObject.has("storeUrl")) {
                    parseShareUrl(jsonObject.optString("storeUrl", ""));
                }
            } else if (url.startsWith("[")) {
                shareJson = url;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String action(String action) {
        if (TextUtils.isEmpty(action)) return "";

        Notify.show("操作已执行");
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (TextUtils.isEmpty(tid)) return Result.string(new ArrayList<>());

            // 根据分类ID返回内容
            if ("quark_app_login_tab".equals(tid)) {
                return Result.string(new ArrayList<>());
            }

            if ("quark_app_store".equals(tid) || "0".equals(tid)) {
                return Result.string(new ArrayList<>());
            }

            // 其他分类ID直接作为shareId处理
            JSONArray jsonArray = new JSONArray();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("shareId", tid);
            jsonObject.put("folder", "0");
            jsonArray.put(jsonObject);

            return buildVodResult(jsonArray);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) return Result.string(new Vod());

            String id = ids.get(0).trim();

            // 特殊分类返回空结果
            if (id.startsWith("[") || "quark_app_store".equals(id) || "quark_app_login_tab".equals(id) || "0".equals(id)) {
                return Result.string(new Vod());
            }

            // 构建Vod对象
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName("夸克分享");
            vod.setVodPic(FOLDER_ICON);
            vod.setVodTag("folder");
            vod.setVodRemarks("点击进入");

            JSONArray playArray = new JSONArray();
            JSONObject playItem = new JSONObject();
            playItem.put("shareId", id);
            playItem.put("folder", "0");
            playArray.put(playItem);

            vod.setVodPlayFrom("夸克");
            vod.setVodPlayUrl(playArray.toString());

            return Result.string(vod);
        } catch (Exception e) {
            return Result.string(new Vod());
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            if (TextUtils.isEmpty(shareJson)) {
                shareJson = DEFAULT_SHARE_JSON;
            }

            List<Class> classes = new ArrayList<>();

            // 根据登录状态显示不同分类
            boolean isLoggedIn = !TextUtils.isEmpty(userName);

            if (isLoggedIn) {
                String tabName = TextUtils.isEmpty(userName) ? "应用商店" : "应用商店 · " + userName;
                classes.add(new Class("quark_app_store", tabName, "1"));
            } else {
                classes.add(new Class("quark_app_login_tab", "登录夸克（扫码或APP）", "1"));
            }

            List<Vod> videos = new ArrayList<>();
            return Result.string(classes, videos);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        shareJson = DEFAULT_SHARE_JSON;

        try {
            // 尝试从远程配置获取
            if (!TextUtils.isEmpty(extend)) {
                extend = extend.trim();
                parseShareUrl(extend);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 确保shareJson有效
        if (TextUtils.isEmpty(shareJson)) {
            shareJson = DEFAULT_SHARE_JSON;
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (TextUtils.isEmpty(id)) return "";

            // 构建播放URL
            JSONObject result = new JSONObject();
            result.put("url", id);
            result.put("header", "{}");

            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            if (TextUtils.isEmpty(key)) return Result.string(new ArrayList<>());

            key = key.trim();
            Matcher matcher = SHARE_PATTERN.matcher(key);

            if (!matcher.find()) {
                return Result.string(new ArrayList<>());
            }

            String shareId = matcher.group(1);
            String sharePwd = matcher.groupCount() >= 2 ? matcher.group(2) : "";
            if (TextUtils.isEmpty(sharePwd)) sharePwd = "";

            // 构建搜索结果
            ArrayList<Vod> vodList = new ArrayList<>();
            String vodId = "[{\"shareId\":\"" + shareId + "\",\"folder\":\"0\",\"sharePwd\":\"" + sharePwd + "\"}]";
            String vodName = "分享应用: " + shareId;
            String vodRemarks = "点击进入";

            Vod vod = new Vod(vodId, vodName, FOLDER_ICON, vodRemarks);
            vod.setVodTag("folder");
            vodList.add(vod);

            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 构建Vod结果列表
     */
    private String buildVodResult(JSONArray shareArray) {
        try {
            ArrayList<Vod> vodList = new ArrayList<>();

            for (int i = 0; i < shareArray.length(); i++) {
                JSONObject item = shareArray.getJSONObject(i);
                String shareId = item.optString("shareId", "");
                String folder = item.optString("folder", "0");
                String sharePwd = item.optString("sharePwd", "");

                if (TextUtils.isEmpty(shareId)) continue;

                String vodId = "[{\"shareId\":\"" + shareId + "\",\"folder\":\"" + folder + "\",\"sharePwd\":\"" + sharePwd + "\"}]";
                Vod vod = new Vod(vodId, shareId, FOLDER_ICON, "");
                vod.setVodTag("folder");
                vodList.add(vod);
            }

            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}