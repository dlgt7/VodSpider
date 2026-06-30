package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Notify;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 夸克网盘核心工具类
 * 替代merge/A/a中QuarkAppStore所需的方法
 */
public class QuarkHelper {

    private static final String TAG = QuarkHelper.class.getSimpleName();

    // 夸克API
    private static final String API_BASE = "https://drive-pc.quark.cn/1/clouddrive";
    private static final String API_MEMBER = API_BASE + "/member?pr=ucpro&fr=pc&uc_param_str=&fetch_subscribe=true&_ch=home&fetch_identity=true";
    private static final String API_SHARE_DETAIL = API_BASE + "/share/sharepage/detail?pr=ucpro&fr=pc";
    private static final String API_FILE_SORT = API_BASE + "/file/sort?pr=ucpro&fr=pc&uc_param_str=&_page=1&_size=50";
    private static final String API_FILE_INFO = API_BASE + "/file/info?pr=ucpro&fr=pc";

    // Cookie存储路径
    private static final String COOKIE_FILE = "/quark_cookie.txt";

    // 静态字段 - 存储shareJson (替代merge/A/a.e)
    public static String shareJson = "[{\"shareId\":\"9a41cd6f82bc\",\"folder\":\"0\"}]";

    // 图标
    private static final String FOLDER_ICON = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/folder.png";
    private static final String APK_ICON = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/apk.png";

    // 登录方式图标
    private static final String ICON_QR = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/qr_login.png";
    private static final String ICON_APP = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/app_login.png";
    private static final String ICON_COOKIE = "https://cc-im-kefu-cos.7moor-fs2.com/im/2768a390-5474-11ea-afc9-7b323e3e16c0/e8213224-8902-4b2f-8042-ef5809445c8e/2024-06-07/2024-06-07_18:01:26/1717754486746/11664624/cookie_login.png";

    /**
     * 初始化 (替代merge/A/a.N0)
     */
    public static void init() {
        try {
            // 读取Cookie文件
            String cookie = readCookieFile();
            if (!TextUtils.isEmpty(cookie)) {
                // 验证Cookie是否有效
                checkLoginStatus(cookie);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 是否已登录 (替代merge/A/a.J1)
     */
    public static boolean isLoggedIn() {
        try {
            String cookie = readCookieFile();
            if (TextUtils.isEmpty(cookie)) return false;
            // 检查Cookie是否有效
            Map<String, String> headers = cookieToHeaders(cookie);
            String response = OkHttp.string(API_MEMBER, headers);
            if (TextUtils.isEmpty(response)) return false;
            JSONObject json = new JSONObject(response);
            int status = json.optInt("status", 0);
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取用户名 (替代merge/A/a.t2)
     */
    public static String getUserName() {
        try {
            String cookie = readCookieFile();
            if (TextUtils.isEmpty(cookie)) return "";
            Map<String, String> headers = cookieToHeaders(cookie);
            String response = OkHttp.string(API_MEMBER, headers);
            if (TextUtils.isEmpty(response)) return "";
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return "";
            return data.optString("nickname", "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 显示登录对话框 (替代merge/A/a.I3)
     */
    public static void showLoginDialog() {
        try {
            android.app.Activity activity = Init.getActivity();
            if (activity == null) return;
            // 在主线程显示对话框
            Init.post(() -> {
                try {
                    showLoginTypeDialog(activity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 打开分享对话框 (替代merge/A/a.n4)
     */
    public static void showShareDialog(String shareInfo) {
        try {
            android.app.Activity activity = Init.getActivity();
            if (activity == null) return;
            Init.post(() -> {
                try {
                    showShareInputDialog(activity, shareInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取文件列表 (替代merge/A/a.W)
     * 根据tid获取夸克分享文件列表
     */
    public static String getFileList(String tid) {
        try {
            if (TextUtils.isEmpty(tid)) return Result.string(new ArrayList<>());

            // 登录分类 - 显示登录方式选项
            if ("quark_app_login_tab".equals(tid)) {
                return getLoginOptions();
            }

            // 应用商店分类或0 - 显示分享内容
            if ("quark_app_store".equals(tid) || "0".equals(tid)) {
                if (!isLoggedIn()) {
                    return getLoginOptions();
                }
                return getShareFileList(shareJson);
            }

            // JSON格式 - 解析分享信息获取文件列表
            if (tid.startsWith("[")) {
                return getShareFileList(tid);
            }

            // 其他 - 尝试作为shareId处理
            return getShareFileList(tid);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 获取分类文件列表 (替代merge/A/a.Y)
     */
    public static String getCategoryContent(String tid, HashMap<String, String> extend) {
        return getFileList(tid);
    }

    /**
     * 构建分享JSON (替代merge/A/a.E)
     * 接收shareId, folder, sharePwd，返回vodId格式的JSON字符串
     */
    public static String buildShareJson(String shareId, String folder, String sharePwd) {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("shareId", shareId);
            jsonObject.put("folder", TextUtils.isEmpty(folder) ? "0" : folder);
            if (!TextUtils.isEmpty(sharePwd)) {
                jsonObject.put("sharePwd", sharePwd);
            }
            jsonArray.put(jsonObject);
            return jsonArray.toString();
        } catch (Exception e) {
            return "[{\"shareId\":\"" + shareId + "\",\"folder\":\"0\"}]";
        }
    }

    /**
     * 未登录提示 (替代merge/A/a.w2)
     */
    public static String getNotLoginMessage() {
        return Result.error("请先登录夸克账号");
    }

    /**
     * 保存Cookie (替代merge/A/a.L4)
     */
    public static void saveCookie(String cookie) {
        try {
            if (TextUtils.isEmpty(cookie)) return;
            writeCookieFile(cookie);
            Notify.show("Cookie已保存");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取Cookie (替代merge/A/a.n2)
     */
    public static String getCookie() {
        return readCookieFile();
    }

    /**
     * Cookie字符串转请求头 (替代merge/A/a.q3)
     */
    public static Map<String, String> cookieToHeaders(String cookie) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", cookie);
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", "https://pan.quark.cn/");
        headers.put("Origin", "https://pan.quark.cn");
        return headers;
    }

    /**
     * 保存登录信息 (替代merge/A/a.a4)
     */
    public static void saveLoginInfo(String cookie, String nickname, String memberType) {
        try {
            writeCookieFile(cookie);
            // 保存用户信息到SharedPreferences
            Init.put("quark_nickname", nickname != null ? nickname : "");
            Init.put("quark_member_type", memberType != null ? memberType : "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 获取登录选项列表
     * 下载分享盘apk需先登录夸克账号
     */
    private static String getLoginOptions() {
        try {
            ArrayList<Vod> vodList = new ArrayList<>();

            // 扫码登录
            Vod qrLogin = new Vod("qr_login", "扫码登录", ICON_QR, "点击扫码");
            qrLogin.setVodTag("action");
            vodList.add(qrLogin);

            // APP登录
            Vod appLogin = new Vod("app_login", "APP登录", ICON_APP, "点击登录");
            appLogin.setVodTag("action");
            vodList.add(appLogin);

            // 手动设置Cookie
            Vod cookieLogin = new Vod("cookie_login", "手动设置Cookie", ICON_COOKIE, "点击设置");
            cookieLogin.setVodTag("action");
            vodList.add(cookieLogin);

            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 获取分享文件列表
     * 调用夸克API获取分享内容
     */
    private static String getShareFileList(String shareInfo) {
        try {
            ArrayList<Vod> vodList = new ArrayList<>();

            // 解析shareInfo
            JSONArray shareArray;
            if (shareInfo.startsWith("[")) {
                shareArray = new JSONArray(shareInfo);
            } else {
                // 单个shareId
                shareArray = new JSONArray();
                JSONObject item = new JSONObject();
                item.put("shareId", shareInfo);
                item.put("folder", "0");
                shareArray.put(item);
            }

            // 检查登录状态
            if (!isLoggedIn()) {
                return getLoginOptions();
            }

            String cookie = readCookieFile();
            Map<String, String> headers = cookieToHeaders(cookie);

            for (int i = 0; i < shareArray.length(); i++) {
                JSONObject item = shareArray.getJSONObject(i);
                String shareId = item.optString("shareId", "");
                String folder = item.optString("folder", "0");
                String sharePwd = item.optString("sharePwd", "");

                if (TextUtils.isEmpty(shareId)) continue;

                // 获取分享详情
                List<Vod> files = getShareDetail(headers, shareId, sharePwd, folder);
                vodList.addAll(files);
            }

            if (vodList.isEmpty()) {
                Vod empty = new Vod("", "暂无内容", FOLDER_ICON, "");
                vodList.add(empty);
            }

            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 获取分享详情 - 调用夸克API
     */
    private static List<Vod> getShareDetail(Map<String, String> headers, String shareId, String sharePwd, String folder) {
        List<Vod> vodList = new ArrayList<>();
        try {
            // 1. 获取分享详情
            String url = API_SHARE_DETAIL + "&share_id=" + shareId;
            if (!TextUtils.isEmpty(sharePwd)) {
                url += "&passcode=" + sharePwd;
            }
            String response = OkHttp.string(url, headers);
            if (TextUtils.isEmpty(response)) return vodList;

            JSONObject json = new JSONObject(response);
            int status = json.optInt("status", 0);
            if (status != 200) return vodList;

            JSONObject data = json.optJSONObject("data");
            if (data == null) return vodList;

            // 获取分享文件fid
            String shareFid = data.optString("fid", "");
            if (TextUtils.isEmpty(shareFid)) return vodList;

            // 2. 获取文件列表
            String fileUrl = API_FILE_SORT + "&pdir_fid=" + shareFid + "&_page=1&_size=50";
            response = OkHttp.string(fileUrl, headers);
            if (TextUtils.isEmpty(response)) return vodList;

            json = new JSONObject(response);
            status = json.optInt("status", 0);
            if (status != 200) return vodList;

            data = json.optJSONObject("data");
            if (data == null) return vodList;

            JSONArray list = data.optJSONArray("list");
            if (list == null) return vodList;

            for (int i = 0; i < list.length(); i++) {
                JSONObject file = list.getJSONObject(i);
                String fileName = file.optString("file_name", "");
                String fileId = file.optString("fid", "");
                int fileType = file.optInt("file_type", 0); // 0=文件夹, 1=文件
                long fileSize = file.optLong("size", 0);
                String formatType = file.optString("format_type", "");
                String thumbnail = file.optString("thumbnail", "");

                // 只显示APK文件和文件夹
                boolean isFolder = fileType == 0;
                boolean isApk = "apk".equalsIgnoreCase(formatType) || fileName.toLowerCase().endsWith(".apk");

                if (!isFolder && !isApk) continue;

                // 构建vodId
                String vodId;
                if (isFolder) {
                    vodId = QuarkHelper.buildShareJson(shareId, fileId, sharePwd);
                } else {
                    vodId = fileId + "|" + shareId;
                }

                String icon = isFolder ? FOLDER_ICON : (TextUtils.isEmpty(thumbnail) ? APK_ICON : thumbnail);
                String remarks = isFolder ? "文件夹" : formatFileSize(fileSize);

                Vod vod = new Vod(vodId, fileName, icon, remarks);
                vod.setVodTag(isFolder ? "folder" : "file");
                vodList.add(vod);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return vodList;
    }

    /**
     * 检查登录状态
     */
    private static boolean checkLoginStatus(String cookie) {
        try {
            Map<String, String> headers = cookieToHeaders(cookie);
            String response = OkHttp.string(API_MEMBER, headers);
            if (TextUtils.isEmpty(response)) return false;
            JSONObject json = new JSONObject(response);
            return json.optInt("status", 0) == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 显示登录类型选择对话框
     */
    private static void showLoginTypeDialog(android.app.Activity activity) {
        try {
            String[] items = {"扫码登录", "APP登录", "手动设置Cookie"};
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("登录夸克账号")
                    .setItems(items, (dialog, which) -> {
                        switch (which) {
                            case 0: // 扫码登录
                                Notify.show("请使用夸克APP扫码登录");
                                break;
                            case 1: // APP登录
                                Notify.show("请在夸克APP中确认登录");
                                break;
                            case 2: // 手动设置Cookie
                                showCookieInputDialog(activity);
                                break;
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示Cookie输入对话框
     */
    private static void showCookieInputDialog(android.app.Activity activity) {
        try {
            android.widget.EditText editText = new android.widget.EditText(activity);
            editText.setHint("请输入夸克网盘Cookie");
            editText.setSingleLine(false);
            editText.setMinLines(3);

            new android.app.AlertDialog.Builder(activity)
                    .setTitle("手动设置Cookie")
                    .setView(editText)
                    .setPositiveButton("保存", (dialog, which) -> {
                        String cookie = editText.getText().toString().trim();
                        if (!TextUtils.isEmpty(cookie)) {
                            saveCookie(cookie);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示分享链接输入对话框
     */
    private static void showShareInputDialog(android.app.Activity activity, String shareInfo) {
        try {
            android.widget.EditText editText = new android.widget.EditText(activity);
            editText.setHint("请输入夸克分享链接");
            editText.setText(shareInfo);

            new android.app.AlertDialog.Builder(activity)
                    .setTitle("输入分享链接")
                    .setView(editText)
                    .setPositiveButton("确定", (dialog, which) -> {
                        String url = editText.getText().toString().trim();
                        if (!TextUtils.isEmpty(url)) {
                            Notify.show("分享链接已更新");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== 文件操作 ====================

    /**
     * 读取Cookie文件
     */
    private static String readCookieFile() {
        try {
            File file = getCookieFile();
            if (!file.exists()) return "";
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            return new String(buffer, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 写入Cookie文件
     */
    private static void writeCookieFile(String cookie) {
        try {
            File file = getCookieFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(cookie.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取Cookie文件
     */
    private static File getCookieFile() {
        try {
            return new File(com.github.catvod.utils.Path.tv(), "quark_cookie.txt");
        } catch (Exception e) {
            return new File(System.getProperty("java.io.tmpdir", "/tmp"), "quark_cookie.txt");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long size) {
        if (size <= 0) return "";
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.1fKB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1fMB", size / (1024.0 * 1024));
        return String.format("%.1fGB", size / (1024.0 * 1024 * 1024));
    }
}
