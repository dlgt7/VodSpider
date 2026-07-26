package com.github.catvod.spider;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 弹幕管理 Spider。
 * 提供弹幕开关、弹幕源配置、Go 弹幕服务管理等功能。
 */
public class DanmuSettings extends Spider {

    /** Go 弹幕服务运行状态 */
    private static volatile boolean goServiceRunning = false;

    /** 弹幕源状态描述 */
    private static volatile String danmuSourceStatus = "点击管理弹幕源";

    /** 弹幕源平台标识 */
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

    /** Go 弹幕服务端口和 API 地址 */
    private static final int DANMU_PORT = 5266;
    private static final String DANMU_API_BASE = "http://127.0.0.1:" + DANMU_PORT;

    // ===== 工具方法 =====

    /** 主线程显示 Toast */
    private static void showToast(String msg) {
        Init.run(() -> Toast.makeText(Init.context(), msg, Toast.LENGTH_SHORT).show());
    }

    /** 拼接图片资源 URL */
    private static String getImageUrl(String filename) {
        return IMAGE_BASE_URL + filename + IMAGE_VERSION;
    }

    /** 创建带矩形样式的弹幕管理列表项 */
    private static Vod createDanmakuVod(String vodId, String vodName, String picFile, String vodRemarks) {
        return new Vod(vodId, vodName, getImageUrl(picFile), vodRemarks, Vod.Style.rect(0.68f));
    }

    /** 检查弹幕 API 是否启用（SP: danmu_api_enabled ≠ "0" 即为开启） */
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

    /** 检查 Go 弹幕服务二进制文件是否存在 */
    private static boolean isGoBinaryAvailable() {
        try {
            File dir = new File(Init.context().getFilesDir(), "fishdanmu");
            return dir.exists() && dir.isDirectory() && dir.list() != null && dir.list().length > 0;
        } catch (Exception e) {
            return false;
        }
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

    /** 检查 action 是否为 APK 安装动作（JSON 数组格式，首项 name 以 .apk 结尾） */
    private static boolean isApkInstallAction(String action) {
        if (TextUtils.isEmpty(action) || !action.startsWith("[")) return false;
        try {
            JSONArray array = new JSONArray(action);
            JSONObject obj = array.optJSONObject(0);
            if (obj == null) return false;
            String name = obj.optString("name", "");
            return !TextUtils.isEmpty(name) && name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 弹幕管理主页内容 =====

    /** 获取弹幕管理分类页面内容（弹幕开关、功能配置、弹幕源开关） */
    private static String getDanmakuCategoryContent() {
        try {
            Init.execute(DanmuSettings::refreshDanmuSourceStatus);

            ArrayList<Vod> list = new ArrayList<>();

            // 弹幕开关
            String switchRemark = isDanmakuApiEnabled() ? "已开启" : "未开启";
            list.add(createDanmakuVod("danmu_switch", "弹幕开关", "bili_danmaku.jpg", switchRemark));

            // 功能配置
            String serviceRemark = goServiceRunning ? "运行中 (:" + DANMU_PORT + ")" : "管理服务和配置";
            list.add(createDanmakuVod("danmu_service", "功能配置", "tool_browser.jpg", serviceRemark));

            // 弹幕源开关
            list.add(createDanmakuVod("danmu_sources", "弹幕源开关", "bili_sswitch.jpg", danmuSourceStatus));

            return Result.string(1, 1, list.size(), list.size(), list);
        } catch (Exception e) {
            return "";
        }
    }

    // ===== 弹幕源状态刷新 =====

    /** 从 Go 弹幕服务获取弹幕源平台状态，更新 danmuSourceStatus */
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
            danmuSourceStatus = enabled >= total
                ? "全部开启(" + enabled + "/" + total + ")"
                : "已开启(" + enabled + "/" + total + ")";

            // 同步检查 AI 配置状态
            JSONObject aiConfig = fetchDanmuApi("/danmu/aiconfig");
            if (aiConfig != null) {
                aiConfig.optBoolean("is_active", false);
                aiConfig.optBoolean("ai_enabled", false);
            }
        } catch (Exception e) {
            danmuSourceStatus = "配置不可用";
        }
    }

    // ===== Go 弹幕服务 API 交互 =====

    /** GET 请求 Go 弹幕服务 API，返回 JSON 响应 */
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

    /** POST 请求 Go 弹幕服务 API，返回是否成功 */
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

    /** 读取弹幕配置为 JSONObject */
    private static JSONObject getDanmuConfigJson() {
        JSONObject json = new JSONObject();
        try {
            for (String key : DANMU_CONFIG_KEYS) {
                String spKey = "danmu_" + key;
                String defaultVal = ("random_position".equals(key) || "random_color".equals(key)) ? "1" : "0";
                json.put(key, "1".equals(getConfig(spKey, defaultVal)));
            }
        } catch (Exception e) { /* 返回已构建部分 */ }
        return json;
    }

    /** 读取弹幕配置为 LinkedHashMap */
    private static LinkedHashMap<String, Boolean> getDanmuConfigMap() {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        JSONObject json = getDanmuConfigJson();
        for (String key : DANMU_CONFIG_KEYS) {
            boolean defaultVal = "random_position".equals(key) || "random_color".equals(key);
            map.put(key, json.optBoolean(key, defaultVal));
        }
        return map;
    }

    /** 将弹幕配置值同步写入 SP */
    private static void syncConfigToSp(JSONObject config) {
        try {
            for (String key : DANMU_CONFIG_KEYS) {
                setConfig("danmu_" + key, config.optBoolean(key, false) ? "1" : "0");
            }
        } catch (Exception ignored) {
        }
    }

    /** 保存弹幕配置（SP + Go 服务 + 本地配置文件） */
    private static void saveDanmuConfig(LinkedHashMap<String, Boolean> config) {
        try {
            JSONObject json = new JSONObject();
            for (String key : DANMU_CONFIG_KEYS) {
                boolean value = Boolean.TRUE.equals(config.get(key));
                json.put(key, value);
                setConfig("danmu_" + key, value ? "1" : "0");
            }
            syncConfigToSp(json);
            saveDanmuConfigFile(json);

            // AI 配置同步到 Go 服务
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
            syncConfigToSp(config);
        } catch (Exception ignored) {
        }
    }

    /** 切换单个弹幕配置开关 */
    private static void toggleDanmuConfig(String key, boolean enabled) {
        if (TextUtils.isEmpty(key)) return;
        setConfig("danmu_" + key, enabled ? "1" : "0");
        Init.execute(() -> {
            try {
                JSONObject config = getDanmuConfigJson();
                config.put(key, enabled);
                syncConfigToSp(config);
                saveDanmuConfigFile(config);
            } catch (Exception ignored) {
            }
        });
    }

    /** 保存弹幕配置文件到本地（fishdanmu/.danmu_config） */
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

    /** 处理弹幕动作分发 */
    private void handleDanmuAction(String action) {
        switch (action) {
            case "danmu_switch":
                // 切换弹幕开关
                Init.run(() -> {
                    boolean enabled = isDanmakuApiEnabled();
                    setConfig("danmu_api_enabled", enabled ? "0" : "1");
                    showToast(enabled ? "弹幕已关闭" : "弹幕已开启");
                });
                break;

            case "danmu_service":
                // 刷新弹幕源状态
                showToast("读取弹幕源…");
                Init.execute(() -> {
                    refreshDanmuSourceStatus();
                    showToast(danmuSourceStatus);
                });
                break;

            case "danmu_sources":
                // 打开 Go 服务弹幕源状态页
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
                // 其他弹幕动作延迟 Toast
                Init.post(() -> showToast(action), 0x3c);
                break;
        }
    }

    /** 处理 JSON 数组格式的弹幕动作（遍历 JSONArray 中的项逐个执行） */
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

            new AlertDialog.Builder(activity)
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
    public void init(android.content.Context context, String extend) {
        if (isGoBinaryAvailable()) {
            Init.execute(DanmuSettings::refreshDanmuSourceStatus);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            ArrayList<Class> classes = new ArrayList<>();
            classes.add(new Class("danmu", "弹幕管理", "1"));
            return Result.string(classes, new ArrayList<>());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            if ("danmu".equals(tid)) {
                return getDanmakuCategoryContent();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String action(String action) {
        if (action == null) return "";

        // JSON 数组弹幕动作
        if (action.startsWith("[") && isApkInstallAction(action)) {
            Init.execute(() -> handleJsonArrayAction(action));
            return "";
        }

        // 弹幕动作
        if (action.startsWith("danmu_")) {
            handleDanmuAction(action);
            return "";
        }

        return "";
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";

        String id = ids.get(0).trim();

        // 弹幕动作
        if (id.startsWith("danmu_")) {
            return action(id);
        }

        // JSON 数组弹幕动作
        if (id.startsWith("[") && isApkInstallAction(id)) {
            Init.execute(() -> handleJsonArrayAction(id));
            return "";
        }

        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }
}
