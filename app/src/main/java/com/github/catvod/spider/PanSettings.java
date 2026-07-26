package com.github.catvod.spider;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.ProxyVideo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 弹幕管理 Spider，从 PanSettings.smali 转换。
 * 仅保留弹幕相关功能 + Go 代理链路，不含网盘直接操作。
 */
public class PanSettings extends Spider {

    // ===== 弹幕配置键 =====
    private static final String PREF_DANMU = "danmu";
    private static final String DANMU_ON = "1";
    private static final String DANMU_OFF = "0";
    private static final String DANMU_DIR = "danmu";

    /** Go 弹幕服务运行状态 */
    private static volatile boolean goServiceRunning = false;

    /** 弹幕源状态描述 */
    private static volatile String danmuSourceStatus = "点击管理弹幕源";

    /** 弹幕源平台标识数组 */
    private static final String[] DANMU_PLATFORMS = {
        "bilibili", "iqiyi", "tencent", "youku", "mango",
        "hanjutv", "renren", "xigua", "leshi", "maiduidui"
    };

    /** 弹幕配置项键名 */
    private static final String[] DANMU_CONFIG_KEYS = {
        "random_position", "random_color", "ai_enabled",
        "strict_title", "title_to_chinese", "remember_last"
    };

    /** 图片资源基础 URL */
    private static final String IMAGE_BASE_URL = "https://jk.catvod.site/jk/assets/";
    private static final String IMAGE_VERSION = "?v=20260720w";

    /** Go 弹幕服务端口 */
    private static final int DANMU_PORT = 5266;
    private static final String DANMU_API_BASE = "http://127.0.0.1:" + DANMU_PORT;

    // ===== 工具方法 =====

    /** 主线程显示 Toast */
    private static void showToast(String msg) {
        Init.run(() -> Toast.makeText(Init.context(), msg, Toast.LENGTH_SHORT).show());
    }

    /** 获取图片资源 URL */
    private static String getImageUrl(String filename) {
        return IMAGE_BASE_URL + filename + IMAGE_VERSION;
    }

    /** 创建带矩形样式的弹幕管理 Vod 项 */
    private static Vod createDanmakuVod(String vodId, String vodName, String picFile, String vodRemarks) {
        return new Vod(vodId, vodName, getImageUrl(picFile), vodRemarks, Vod.Style.rect(0.68f));
    }

    /** 检查弹幕 API 是否启用（SP: danmu_api_enabled ≠ "0"） */
    private static boolean isDanmakuApiEnabled() {
        return !"0".equals(Init.getString("danmu_api_enabled", "1"));
    }

    /** 读取 SP 配置 */
    private static String getConfig(String key, String defaultValue) {
        return Init.getString(key, defaultValue);
    }

    /** 写入 SP 配置 */
    private static void setConfig(String key, String value) {
        Init.put(key, value);
    }

    /** 检查 Go 弹幕服务二进制是否存在 */
    private static boolean isGoBinaryAvailable() {
        try {
            File dir = new File(Init.context().getFilesDir(), "fishdanmu");
            return dir.exists() && dir.isDirectory() && dir.list() != null && dir.list().length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 检查 Go 代理是否可用 */
    private static boolean isGoProxyAvailable() {
        return !TextUtils.isEmpty(ProxyVideo.goVer());
    }

    /** 在浏览器中打开 URL */
    private static void openInBrowser(String url, boolean newTask) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Init.context().startActivity(intent);
        } catch (Exception e) {
            showToast("打开浏览器失败");
        }
    }

    /** 检测是否为 APK 安装动作（JSON 数组 + 首项 name 以 .apk 结尾） */
    private static boolean isApkInstallAction(String str) {
        if (TextUtils.isEmpty(str) || !str.startsWith("[")) return false;
        try {
            JSONArray array = new JSONArray(str);
            JSONObject obj = array.optJSONObject(0);
            if (obj == null) return false;
            String name = obj.optString("name", "");
            return !TextUtils.isEmpty(name) && name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取弹幕源文件（如果 action 是 URL，则从本地 danmu 目录缓存读取） */
    private static String readDanmuSources(String action) {
        if (action != null && action.startsWith("http")) {
            try {
                String danmuDir = Init.context().getFilesDir().getAbsolutePath();
                File file = new File(danmuDir, DANMU_DIR);
                if (file.exists()) {
                    String content = Path.read(file);
                    if (!TextUtils.isEmpty(content)) return content;
                }
            } catch (Exception ignored) {
            }
        }
        return action;
    }

    // ===== 弹幕管理主页内容 =====

    /**
     * 获取弹幕管理分类页面内容。
     * 包含：弹幕开关、功能配置、弹幕源开关 三个条目。
     */
    private static String getDanmakuCategoryContent() {
        try {
            // 后台刷新弹幕源状态
            Init.execute(PanSettings::refreshDanmuSourceStatus);

            ArrayList<Vod> list = new ArrayList<>();

            // 1. 弹幕开关
            String switchRemark = isDanmakuApiEnabled() ? "已开启" : "未开启";
            list.add(createDanmakuVod("danmu_switch", "弹幕开关", "bili_danmaku.jpg", switchRemark));

            // 2. 功能配置
            String serviceRemark = goServiceRunning ? "运行中 (:" + DANMU_PORT + ")" : "管理服务和配置";
            list.add(createDanmakuVod("danmu_service", "功能配置", "tool_browser.jpg", serviceRemark));

            // 3. 弹幕源开关
            list.add(createDanmakuVod("danmu_sources", "弹幕源开关", "bili_sswitch.jpg", danmuSourceStatus));

            return Result.string(1, 1, list.size(), list.size(), list);
        } catch (Exception e) {
            return "";
        }
    }

    // ===== 弹幕源状态刷新 =====

    /**
     * 从 Go 弹幕服务获取弹幕源平台状态，更新 danmuSourceStatus 字段。
     */
    private static void refreshDanmuSourceStatus() {
        try {
            if (!goServiceRunning && !isGoBinaryAvailable()) {
                danmuSourceStatus = "请先启动 Go";
                return;
            }

            JSONObject status = fetchDanmuApi("/danmu/status");
            if (status == null) {
                danmuSourceStatus = goServiceRunning ? "读取失败" : "请先启动 Go";
                return;
            }

            JSONArray platforms = status.optJSONArray("platforms");
            LinkedHashSet<String> enabledSet = new LinkedHashSet<>();
            if (platforms != null) {
                for (int i = 0; i < platforms.length(); i++) {
                    enabledSet.add(platforms.optString(i, "").toLowerCase());
                }
            }

            int enabled = 0;
            for (String platform : DANMU_PLATFORMS) {
                if (enabledSet.contains(platform)) enabled++;
            }

            int total = DANMU_PLATFORMS.length;
            if (enabled >= total) {
                danmuSourceStatus = "全部开启(" + enabled + "/" + total + ")";
            } else {
                danmuSourceStatus = "已开启(" + enabled + "/" + total + ")";
            }
        } catch (Exception e) {
            danmuSourceStatus = "配置不可用";
        }
    }

    // ===== Go 弹幕服务 API 交互 =====

    /** GET 请求 Go 弹幕服务 API */
    private static JSONObject fetchDanmuApi(String path) {
        if (!goServiceRunning && !isGoBinaryAvailable()) return null;
        try {
            String url = DANMU_API_BASE + path;
            String body = OkHttp.string(url, 3000);
            if (!TextUtils.isEmpty(body)) {
                return new JSONObject(body);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** POST 请求 Go 弹幕服务 API */
    private static boolean postDanmuApi(String path, String jsonBody) {
        if (!isGoBinaryAvailable()) return false;
        try {
            String url = DANMU_API_BASE + path;
            if (jsonBody == null) jsonBody = "{}";
            String respBody = OkHttp.post(url, jsonBody);
            if (TextUtils.isEmpty(respBody)) return false;
            JSONObject result = new JSONObject(respBody);
            return result.optBoolean("success", false);
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 弹幕配置管理 =====

    /** 获取弹幕配置 JSON */
    private static JSONObject getDanmuConfigJson() {
        JSONObject json = new JSONObject();
        try {
            for (String key : DANMU_CONFIG_KEYS) {
                String spKey = "danmu_" + key;
                String defaultVal = ("random_position".equals(key) || "random_color".equals(key)) ? "1" : "0";
                String value = getConfig(spKey, defaultVal);
                json.put(key, "1".equals(value));
            }
        } catch (Exception e) {
            // 返回已构建的部分
        }
        return json;
    }

    /** 从 SP 读取弹幕配置为 LinkedHashMap */
    private static LinkedHashMap<String, Boolean> getDanmuConfigMap() {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        JSONObject json = getDanmuConfigJson();
        for (String key : DANMU_CONFIG_KEYS) {
            boolean defaultVal = "random_position".equals(key) || "random_color".equals(key);
            map.put(key, json.optBoolean(key, defaultVal));
        }
        return map;
    }

    /** 将弹幕配置同步到 Go 服务 */
    private static void syncConfigToGoService(JSONObject config) {
        try {
            for (String key : DANMU_CONFIG_KEYS) {
                String spKey = "danmu_" + key;
                boolean value = config.optBoolean(key, false);
                setConfig(spKey, value ? "1" : "0");
            }
        } catch (Exception ignored) {
        }
    }

    /** 保存弹幕配置（SP + Go 服务 + 配置文件） */
    private static void saveDanmuConfig(LinkedHashMap<String, Boolean> config) {
        try {
            JSONObject json = new JSONObject();
            for (String key : DANMU_CONFIG_KEYS) {
                boolean value = Boolean.TRUE.equals(config.get(key));
                json.put(key, value);
                setConfig("danmu_" + key, value ? "1" : "0");
            }
            syncConfigToGoService(json);
            saveDanmuConfigFile(json);

            // 处理 AI 配置
            if (config.containsKey("ai_enabled")) {
                JSONObject aiConfig = fetchDanmuApi("/danmu/aiconfig");
                String apiKey = "";
                String baseUrl = "https://api.deepseek.com";
                String model = "deepseek-chat";
                if (aiConfig != null) {
                    apiKey = aiConfig.optString("ai_api_key", "");
                    if (apiKey.matches("\\*+")) apiKey = "";
                    baseUrl = aiConfig.optString("ai_base_url", baseUrl);
                    model = aiConfig.optString("ai_model", model);
                }
                boolean aiEnabled = Boolean.TRUE.equals(config.get("ai_enabled"));
                updateAiConfig(baseUrl, model, apiKey, aiEnabled);
            }
        } catch (Exception ignored) {
        }
    }

    /** 更新 AI 配置到 Go 服务 */
    private static void updateAiConfig(String baseUrl, String model, String apiKey, boolean enabled) {
        if (!isGoBinaryAvailable()) return;
        try {
            JSONObject json = new JSONObject();
            json.put("ai_enabled", enabled);
            if (baseUrl != null) json.put("ai_base_url", baseUrl);
            if (model != null) json.put("ai_model", model);
            json.put("ai_api_key", apiKey);
            postDanmuApi("/danmu/aiconfig", json.toString());

            JSONObject config = getDanmuConfigJson();
            config.put("ai_enabled", enabled);
            syncConfigToGoService(config);
        } catch (Exception ignored) {
        }
    }

    /** 切换弹幕配置开关 */
    private static void toggleDanmuConfig(String key, boolean enabled) {
        if (TextUtils.isEmpty(key)) return;
        setConfig("danmu_" + key, enabled ? "1" : "0");
        Init.execute(() -> {
            try {
                JSONObject config = getDanmuConfigJson();
                config.put(key, enabled);
                syncConfigToGoService(config);
                saveDanmuConfigFile(config);
            } catch (Exception ignored) {
            }
        });
    }

    /** 保存弹幕配置文件到本地 */
    private static void saveDanmuConfigFile(JSONObject config) {
        try {
            if (Init.context() == null) return;
            File dir = new File(Init.context().getFilesDir(), "fishdanmu");
            if (!dir.exists()) dir.mkdirs();

            JSONObject json = new JSONObject();
            json.put("port", DANMU_PORT);
            json.put("enabled", isDanmakuApiEnabled());

            JSONArray enabledPlatforms = new JSONArray();
            JSONObject status = fetchDanmuApi("/danmu/status");
            if (status != null && status.optJSONArray("platforms") != null) {
                enabledPlatforms = status.optJSONArray("platforms");
            } else {
                for (String platform : DANMU_PLATFORMS) {
                    enabledPlatforms.put(platform);
                }
            }
            json.put("enabled_platforms", enabledPlatforms);
            json.put("random_position", config.optBoolean("random_position", true));
            json.put("random_color", config.optBoolean("random_color", true));
            json.put("ai_enabled", config.optBoolean("ai_enabled", false));

            JSONObject aiConfig = fetchDanmuApi("/danmu/aiconfig");
            if (aiConfig != null) {
                String apiKey = aiConfig.optString("ai_api_key", "");
                if (!apiKey.matches("\\*+")) {
                    json.put("ai_api_key", apiKey);
                }
                json.put("ai_base_url", aiConfig.optString("ai_base_url", "https://api.deepseek.com"));
                json.put("ai_model", aiConfig.optString("ai_model", "deepseek-chat"));
            } else {
                json.put("ai_base_url", "https://api.deepseek.com");
                json.put("ai_model", "deepseek-chat");
            }

            File configFile = new File(dir, ".danmu_config");
            FileWriter writer = new FileWriter(configFile);
            writer.write(json.toString(2));
            writer.close();
        } catch (Exception ignored) {
        }
    }

    // ===== 弹幕动作处理 =====

    /**
     * 处理弹幕动作分发。
     * 对应 smali action() 中 hashCode switch 逻辑：
     * - 0x6b13693a → danmu_switch（弹幕开关）
     * - 0x6a1217ae → danmu_service（功能配置）
     * - -0x15a4d58e → danmu_sources（弹幕源开关）
     * - -0x26ddaa91 → 弹幕配置对话框
     */
    private void handleDanmuAction(String action) {
        switch (action) {
            case "danmu_switch":
                // 弹幕开关切换
                Init.run(() -> {
                    boolean enabled = isDanmakuApiEnabled();
                    setConfig("danmu_api_enabled", enabled ? "0" : "1");
                    showToast(enabled ? "弹幕已关闭" : "弹幕已开启");
                });
                break;

            case "danmu_service":
                // 功能配置：刷新弹幕源状态
                showToast("读取弹幕源…");
                Init.execute(() -> {
                    refreshDanmuSourceStatus();
                    showToast(danmuSourceStatus);
                });
                break;

            case "danmu_sources":
                // 弹幕源开关：在浏览器中打开 Go 服务状态页
                Init.run(() -> {
                    if (!goServiceRunning) {
                        showToast("弹幕未就绪，请先启动");
                    } else {
                        openInBrowser(DANMU_API_BASE + "/danmu/status", true);
                    }
                });
                break;

            case "danmu_cfg_show":
                // 弹幕配置对话框
                Init.run(() -> {
                    try {
                        Activity activity = Init.getActivity();
                        if (activity != null) {
                            showDanmuConfigDialog(activity);
                        }
                    } catch (Exception ignored) {
                    }
                });
                break;

            default:
                // 其他弹幕动作：延迟 Toast
                Init.post(() -> showToast(action), 0x3c);
                break;
        }
    }

    /**
     * 处理 JSON 数组格式的弹幕动作。
     * 对应 smali: merge.g.n2.O(String)V
     * 遍历 JSONArray，根据 type 字段分发动作。
     */
    private static void handleJsonArrayAction(String action) {
        try {
            JSONArray array = new JSONArray(action);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String name = obj.optString("name", "");
                String url = obj.optString("url", "");
                if (!TextUtils.isEmpty(url)) {
                    openInBrowser(url, true);
                }
                if (!TextUtils.isEmpty(name)) {
                    showToast("处理: " + name);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 弹幕配置对话框 */
    private void showDanmuConfigDialog(Activity activity) {
        try {
            LinkedHashMap<String, Boolean> config = getDanmuConfigMap();
            String[] items = DANMU_CONFIG_KEYS;
            boolean[] checked = new boolean[items.length];
            for (int i = 0; i < items.length; i++) {
                checked[i] = Boolean.TRUE.equals(config.get(items[i]));
            }

            new android.app.AlertDialog.Builder(activity)
                .setTitle("弹幕配置")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    config.put(items[which], isChecked);
                })
                .setPositiveButton("确定", (dialog, which) -> {
                    saveDanmuConfig(config);
                    showToast("配置已保存");
                })
                .setNegativeButton("取消", null)
                .show();
        } catch (Exception e) {
            showToast("配置对话框打开失败");
        }
    }

    // ===== Spider 接口实现 =====

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        // 初始化弹幕配置（对应 n2.T / k2 / l2）
        // 原始 init 中的 QuarkYun/UcYun 等网盘初始化已移除

        // 初始化默认配置
        try {
            String version = Prefers.getString("version", "");
            if (!"25.0".equals(version)) {
                Prefers.put("version", "25.0");
                Prefers.put("update", "关闭");
                Prefers.put("danmuColor", "默认");
                Prefers.put("proxyMode", "Go多线程");
            }
        } catch (Exception ignored) {
        }

        // Go 配置同步（Cookie 等同步到 Go 代理服务）
        // 原 merge.g.n2.k2() 逻辑：将 SP 中的 Cookie/配置同步到 Go 服务
        // 此处由 ProxyVideo.go() 内部处理

        // 加载 Gate 远程配置
        // 原 merge.g.n2.l2(extend) 逻辑：加载 extend JSON 配置
        if (!TextUtils.isEmpty(extend)) {
            try {
                JSONObject ext = new JSONObject(extend);
                java.util.Iterator<String> keys = ext.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Init.put(key, ext.optString(key, ""));
                }
            } catch (Exception ignored) {
            }
        }

        // Go 二进制初始化
        ProxyVideo.go();

        // Go 代理可用检查 + Go 服务启动
        if (isGoProxyAvailable()) {
            Init.execute(() -> ProxyVideo.go());
        }

        // Go 弹幕服务就绪检查
        if (isGoBinaryAvailable()) {
            Init.execute(PanSettings::refreshDanmuSourceStatus);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            ArrayList<Class> classes = new ArrayList<>();
            classes.add(new Class("pan_danmu", "弹幕管理", "1"));
            return Result.string(classes, new ArrayList<>());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            if (tid == null) tid = "";

            // pan_danmu 或 danmu_cfg → 弹幕管理页面
            if ("pan_danmu".equals(tid) || "danmu_cfg".equals(tid)) {
                return getDanmakuCategoryContent();
            }

            // jm_cfg → jm 配置页面
            if ("jm_cfg".equals(tid)) {
                return getConfig("jm_cfg", "");
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String action(String action) throws Exception {
        String empty = "";
        if (action == null) action = empty;

        // "[" 前缀 + APK 安装检测 → 异步处理
        if (action.startsWith("[") && isApkInstallAction(action)) {
            Init.execute(() -> handleJsonArrayAction(action));
            return empty;
        }

        // 设置 Activity
        Activity activity = Init.getActivity();
        if (activity != null) {
            Init.setActivity(activity);
        }

        // "jm_" 前缀 → jm 动作处理（打开浏览器查看 jm 页面）
        if (action.startsWith("jm_")) {
            Init.run(() -> openInBrowser("https://jmcomic.bet", true));
            return empty;
        }

        // "danmu_" 前缀 → 弹幕动作分发
        if (action.startsWith("danmu_")) {
            handleDanmuAction(action);
            return empty;
        }

        // 默认 → 延迟 Toast 显示
        Init.post(() -> showToast(action), 0x3c);
        return empty;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String empty = "";
        if (ids == null || ids.isEmpty()) return empty;

        String id = ids.get(0).trim();

        // "jm_" 前缀 → action()
        if (id.startsWith("jm_")) {
            return action(id);
        }

        // "danmu_" 前缀 → action()
        if (id.startsWith("danmu_")) {
            return action(id);
        }

        // "pdir#" 前缀 → Go 代理网盘目录（通过 Go 服务 API 获取）
        if (id.startsWith("pdir#")) {
            try {
                String encoded = java.net.URLEncoder.encode(id, "UTF-8");
                String goResult = OkHttp.string(DANMU_API_BASE + "/pan/detail?id=" + encoded);
                if (!TextUtils.isEmpty(goResult)) return goResult;
            } catch (Exception ignored) {
            }
            return empty;
        }

        // "[" 前缀 + APK 安装检测
        if (isApkInstallAction(id)) {
            Init.execute(() -> handleJsonArrayAction(id));
            return empty;
        }

        // 分享链接检测（pan.baidu.com / pan.quark.cn / drive.uc.cn 等）
        if (id.contains("pan.baidu.com") || id.contains("pan.quark.cn") || id.contains("drive.uc.cn")) {
            try {
                String encoded = java.net.URLEncoder.encode(id, "UTF-8");
                String goResult = OkHttp.string(DANMU_API_BASE + "/pan/detail?url=" + encoded);
                if (!TextUtils.isEmpty(goResult)) return goResult;
            } catch (Exception ignored) {
            }
            return empty;
        }

        // 百度网盘分享 JSON 格式检测（s/ 开头或 bdpan:// 协议）
        if (id.startsWith("s/") || id.startsWith("bdpan://") || id.contains("baidu.com/s/")) {
            try {
                String encoded = java.net.URLEncoder.encode(id, "UTF-8");
                String goResult = OkHttp.string(DANMU_API_BASE + "/pan/detail?id=" + encoded);
                if (!TextUtils.isEmpty(goResult)) return goResult;
            } catch (Exception ignored) {
            }
            return empty;
        }

        return empty;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 弹幕管理不涉及播放
        return "";
    }
}
