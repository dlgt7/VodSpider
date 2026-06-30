package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 夸克应用商店
 * 下载分享盘apk需先登录夸克账号，扫码登录，app登录，手动设置cookie
 */
public class QuarkAppStore extends Spider {

    private static final Pattern SHARE_PATTERN = Pattern.compile("https?://pan\\.quark\\.cn/s/([a-zA-Z0-9]+)(?:#(.*))?");

    private static final String DEFAULT_SHARE_JSON = "[{\"shareId\":\"9a41cd6f82bc\",\"folder\":\"0\"}]";
    private static final String FOLDER_ICON = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/folder.png";

    // 实例字段a - 存储shareJson
    private String shareJson = DEFAULT_SHARE_JSON;

    /**
     * 设置分享URL (a方法)
     * 解析分享链接，提取shareId和sharePwd
     * 或从JSON配置中读取store/storeUrl字段
     */
    private void setShareUrl(String url) {
        try {
            if (TextUtils.isEmpty(url)) return;

            if (url.startsWith("http")) {
                // 解析夸克分享链接
                Matcher matcher = SHARE_PATTERN.matcher(url);
                if (matcher.find()) {
                    String shareId = matcher.group(1);
                    String sharePwd = "";
                    if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                        sharePwd = matcher.group(2);
                    }
                    shareJson = QuarkHelper.buildShareJson(shareId, "0", sharePwd);
                }
            } else if (url.startsWith("{")) {
                // JSON配置
                JSONObject jsonObject = new JSONObject(url);
                if (jsonObject.has("store")) {
                    // 直接设置store JSON
                    shareJson = jsonObject.optString("store", DEFAULT_SHARE_JSON);
                } else if (jsonObject.has("storeUrl")) {
                    // 递归解析storeUrl
                    setShareUrl(jsonObject.optString("storeUrl", ""));
                }
            } else if (url.startsWith("[")) {
                // 直接设置JSON数组
                shareJson = url;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public String action(String action) {
        try {
            if (TextUtils.isEmpty(action)) return "";

            // 以[开头的分享操作 - 异步执行
            if (action.startsWith("[")) {
                Init.execute(new ShareAction(action));
                return "";
            }

            // 其他操作 - 显示对话框
            android.app.Activity activity = Init.activityForDialog();
            if (activity != null) {
                Init.setActivity(activity);
            }
            QuarkHelper.showShareDialog(action);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        // 直接调用QuarkHelper.getFileList
        return QuarkHelper.getFileList(tid);
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) return "";

            String id = ids.get(0).trim();

            // 以[开头、quark_app_store、quark_app_login_tab、0 - 调用W方法
            boolean isSpecial = id.startsWith("[") || "quark_app_store".equals(id) || "quark_app_login_tab".equals(id) || "0".equals(id);
            if (isSpecial) {
                return QuarkHelper.getFileList(id);
            }

            // 其他 - 显示详情对话框
            android.app.Activity activity = Init.activityForDialog();
            if (activity != null) {
                Init.setActivity(activity);
            }
            Init.showDialogAfterKillingDetail(new DetailDialog(id));
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            if (TextUtils.isEmpty(shareJson)) {
                shareJson = DEFAULT_SHARE_JSON;
            }

            // 设置QuarkHelper的静态shareJson
            if (!TextUtils.isEmpty(shareJson)) {
                QuarkHelper.shareJson = shareJson;
            }

            // 检查登录状态
            boolean loggedIn = QuarkHelper.isLoggedIn();
            if (loggedIn) {
                String userName = QuarkHelper.getUserName();
                if (TextUtils.isEmpty(userName)) {
                    // 已登录但用户名为空 - 重新检查
                    QuarkHelper.showLoginDialog();
                }
            }

            List<Class> classes = new ArrayList<>();
            List<Vod> videos = new ArrayList<>();

            if (!loggedIn) {
                // 未登录 - 显示登录分类
                classes.add(new Class("quark_app_login_tab", "登录夸克（扫码或APP）", "1"));
            } else {
                // 已登录 - 显示应用商店
                String userName = QuarkHelper.getUserName();
                String tabName;
                if (TextUtils.isEmpty(userName)) {
                    tabName = "应用商店";
                } else {
                    tabName = "应用商店 · " + userName;
                }
                classes.add(new Class("quark_app_store", tabName, "1"));
            }

            return Result.string(classes, videos);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        // 检查权限
        Init.checkPermission();

        // 初始化QuarkHelper
        QuarkHelper.init();

        // 重置shareJson
        shareJson = DEFAULT_SHARE_JSON;

        // 尝试从远程配置读取
        try {
            // 优先使用extend参数
            if (!TextUtils.isEmpty(extend)) {
                extend = extend.trim();
                setShareUrl(extend);
            }

            // 更新QuarkHelper的静态shareJson
            if (!TextUtils.isEmpty(shareJson)) {
                QuarkHelper.shareJson = shareJson;
            }
        } catch (Exception e) {
            // ignore
        }

        // 确保shareJson不为空
        if (TextUtils.isEmpty(shareJson)) {
            shareJson = DEFAULT_SHARE_JSON;
            QuarkHelper.shareJson = shareJson;
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // smali中直接返回空字符串
        return "";
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

            // 检查登录状态
            if (!QuarkHelper.isLoggedIn()) {
                return QuarkHelper.getNotLoginMessage();
            }

            String shareId = matcher.group(1);
            String sharePwd = "";
            if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                sharePwd = matcher.group(2);
            }

            // 构建分享JSON
            String vodId = QuarkHelper.buildShareJson(shareId, "0", sharePwd);
            String vodName = "分享应用: " + shareId;

            ArrayList<Vod> vodList = new ArrayList<>();
            Vod vod = new Vod(vodId, vodName, FOLDER_ICON, "点击进入");
            vod.setVodTag("folder");
            vodList.add(vod);

            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    // ==================== 内部Runnable类 ====================

    /**
     * 分享操作Runnable (替代merge/f/U)
     * 异步处理分享链接操作
     */
    private static class ShareAction implements Runnable {
        private final String shareInfo;

        public ShareAction(String shareInfo) {
            this.shareInfo = shareInfo;
        }

        @Override
        public void run() {
            try {
                // 异步处理分享操作
                JSONArray shareArray = new JSONArray(shareInfo);
                for (int i = 0; i < shareArray.length(); i++) {
                    JSONObject item = shareArray.getJSONObject(i);
                    String shareId = item.optString("shareId", "");
                    String folder = item.optString("folder", "0");
                    String sharePwd = item.optString("sharePwd", "");

                    if (TextUtils.isEmpty(shareId)) continue;

                    // 获取分享文件列表
                    QuarkHelper.getFileList(QuarkHelper.buildShareJson(shareId, folder, sharePwd));
                }
            } catch (Exception e) {
                Init.show("分享操作失败: " + e.getMessage());
            }
        }
    }

    /**
     * 详情对话框Runnable (替代merge/f/r)
     * 在详情页关闭后显示对话框
     */
    private static class DetailDialog implements Runnable {
        private final String shareInfo;

        public DetailDialog(String shareInfo) {
            this.shareInfo = shareInfo;
        }

        @Override
        public void run() {
            try {
                QuarkHelper.showShareDialog(shareInfo);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
