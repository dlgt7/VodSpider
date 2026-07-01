package com.github.catvod.bean.gate;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Gate配置类，用于管理远程配置
 */
public class GateConfig {

    public int configVersion;
    public boolean jarEnabled;
    public String jarDisabledAction;
    public String jarDisabledMessage;
    public int jarDisabledDelaySec;
    public boolean packageGuard;
    public List<String> allowedPackages;
    public List<String> allowedAppNames;
    public String packageFailAction;
    public int packageFailDelaySec;
    public String packageFailMessage;
    public boolean noticeEnabled;
    public String noticeText;
    public String noticeMode;
    public int noticeIntervalDays;
    public boolean gateEnabled;
    public String gateQrUrl;
    public String gateHint;
    public String gatePasswordSha256;
    public String gateMode;
    public int gateIntervalDays;
    public String gateFailAction;
    public String liveTxtUrl;
    public String wallpaperUrl;
    public String wallpaperTvUrl;
    public String biliCookie;
    public String musicCoverUrl;
    public boolean detailAdEnabled;
    public String detailAdText;
    public boolean playFromAdEnabled;
    public String playFromAdName;
    public String quarkAppStoreUrl;
    public String quarkAppStoreJson;

    public GateConfig() {
        configVersion = 1;
        jarEnabled = true;
        jarDisabledAction = "toast";
        jarDisabledMessage = "服务维护中";
        jarDisabledDelaySec = 3;
        packageGuard = false;
        allowedPackages = new ArrayList<>();
        allowedAppNames = new ArrayList<>();
        packageFailAction = "kill_after_seconds";
        packageFailDelaySec = 3;
        packageFailMessage = "未授权客户端";
        noticeEnabled = true;
        noticeText = "资源来自网络，仅供学习交流，请勿用于商业用途。";
        noticeMode = "interval_days";
        noticeIntervalDays = 7;
        gateEnabled = false;
        gateQrUrl = "";
        gateHint = "扫码关注公众号获取口令";
        gatePasswordSha256 = "";
        gateMode = "interval_days";
        gateIntervalDays = 3;
        gateFailAction = "block";
        liveTxtUrl = "";
        wallpaperUrl = "";
        wallpaperTvUrl = "";
        biliCookie = "";
        musicCoverUrl = "";
        detailAdEnabled = false;
        detailAdText = "";
        playFromAdEnabled = false;
        playFromAdName = "";
        quarkAppStoreUrl = "";
        quarkAppStoreJson = "";
    }

    public static GateConfig empty() {
        GateConfig config = new GateConfig();
        config.packageGuard = false;
        config.allowedPackages = new ArrayList<>();
        config.allowedAppNames = new ArrayList<>();
        return config;
    }

    public static GateConfig fromJson(String json) {
        GateConfig config = new GateConfig();
        if (TextUtils.isEmpty(json) || json.trim().isEmpty()) {
            return config;
        }

        try {
            JSONObject obj = new JSONObject(json.trim());

            config.configVersion = obj.optInt("configVersion", 1);
            config.jarEnabled = obj.optBoolean("jarEnabled", true);
            config.jarDisabledAction = obj.optString("jarDisabledAction", "toast");
            config.jarDisabledMessage = obj.optString("jarDisabledMessage", "服务维护中");
            config.jarDisabledDelaySec = obj.optInt("jarDisabledDelaySec", 3);
            config.packageGuard = obj.optBoolean("packageGuard", false);
            config.allowedPackages = readList(obj, "allowedPackages");
            config.allowedAppNames = readList(obj, "allowedAppNames");
            config.packageFailAction = obj.optString("packageFailAction", "kill_after_seconds");
            config.packageFailDelaySec = obj.optInt("packageFailDelaySec", 3);
            config.packageFailMessage = obj.optString("packageFailMessage", "未授权客户端");

            JSONObject notice = obj.optJSONObject("notice");
            if (notice != null) {
                config.noticeEnabled = notice.optBoolean("enabled", true);
                config.noticeText = notice.optString("text", "资源来自网络，仅供学习交流，请勿用于商业用途。");
                config.noticeMode = notice.optString("mode", "interval_days");
                config.noticeIntervalDays = notice.optInt("intervalDays", 7);
            }

            JSONObject gate = obj.optJSONObject("gate");
            if (gate != null) {
                config.gateEnabled = gate.optBoolean("enabled", false);
                config.gateQrUrl = gate.optString("qrUrl", "");
                config.gateHint = gate.optString("hint", "扫码关注公众号获取口令");
                config.gatePasswordSha256 = gate.optString("passwordSha256", "");
                config.gateMode = gate.optString("mode", "interval_days");
                config.gateIntervalDays = gate.optInt("intervalDays", 3);
                config.gateFailAction = gate.optString("failAction", "block");
            }

            JSONObject proxy = obj.optJSONObject("proxy");
            if (proxy != null) {
                config.liveTxtUrl = proxy.optString("liveTxtUrl", "");
                config.wallpaperUrl = proxy.optString("wallpaperUrl", "");
                config.wallpaperTvUrl = proxy.optString("wallpaperTvUrl", "");
                config.biliCookie = proxy.optString("biliCookie", "");
            }

            JSONObject promo = obj.optJSONObject("promo");
            if (promo != null) {
                config.musicCoverUrl = promo.optString("musicCoverUrl", "");
                config.detailAdEnabled = promo.optBoolean("detailAdEnabled", false);
                config.detailAdText = promo.optString("detailAdText", "");
                config.playFromAdEnabled = promo.optBoolean("playFromAdEnabled", false);
                config.playFromAdName = promo.optString("playFromAdName", "");
            }

            JSONObject pan = obj.optJSONObject("pan");
            if (pan != null) {
                config.quarkAppStoreUrl = pan.optString("quarkAppStoreUrl", "");
                config.quarkAppStoreJson = pan.optString("quarkAppStoreJson", "");
            }

        } catch (Exception e) {
            // 解析失败，返回默认配置
        }

        return config;
    }

    private static List<String> readList(JSONObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (!obj.has(key)) {
            return list;
        }

        try {
            Object value = obj.get(key);
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) {
                    String str = arr.optString(i, "").trim();
                    if (!str.isEmpty()) {
                        list.add(str);
                    }
                }
            } else if (value instanceof String) {
                String str = ((String) value).trim();
                if (!str.isEmpty()) {
                    String[] parts = str.split("[,，\\n]");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            list.add(trimmed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，返回空列表
        }

        return list;
    }

    public String toCacheJson() {
        try {
            JSONObject obj = new JSONObject();

            obj.put("configVersion", configVersion);
            obj.put("jarEnabled", jarEnabled);
            obj.put("jarDisabledAction", jarDisabledAction);
            obj.put("jarDisabledMessage", jarDisabledMessage);
            obj.put("jarDisabledDelaySec", jarDisabledDelaySec);
            obj.put("packageGuard", packageGuard);
            obj.put("allowedPackages", new JSONArray(allowedPackages));
            obj.put("allowedAppNames", new JSONArray(allowedAppNames));
            obj.put("packageFailAction", packageFailAction);
            obj.put("packageFailDelaySec", packageFailDelaySec);
            obj.put("packageFailMessage", packageFailMessage);

            JSONObject notice = new JSONObject();
            notice.put("enabled", noticeEnabled);
            notice.put("text", noticeText);
            notice.put("mode", noticeMode);
            notice.put("intervalDays", noticeIntervalDays);
            obj.put("notice", notice);

            JSONObject gate = new JSONObject();
            gate.put("enabled", gateEnabled);
            gate.put("qrUrl", gateQrUrl);
            gate.put("hint", gateHint);
            gate.put("passwordSha256", gatePasswordSha256);
            gate.put("mode", gateMode);
            gate.put("intervalDays", gateIntervalDays);
            gate.put("failAction", gateFailAction);
            obj.put("gate", gate);

            JSONObject proxy = new JSONObject();
            proxy.put("liveTxtUrl", liveTxtUrl);
            proxy.put("wallpaperUrl", wallpaperUrl);
            proxy.put("wallpaperTvUrl", wallpaperTvUrl);
            proxy.put("biliCookie", biliCookie);
            obj.put("proxy", proxy);

            JSONObject promo = new JSONObject();
            promo.put("musicCoverUrl", musicCoverUrl);
            promo.put("detailAdEnabled", detailAdEnabled);
            promo.put("detailAdText", detailAdText);
            promo.put("playFromAdEnabled", playFromAdEnabled);
            promo.put("playFromAdName", playFromAdName);
            obj.put("promo", promo);

            JSONObject pan = new JSONObject();
            pan.put("quarkAppStoreUrl", quarkAppStoreUrl);
            pan.put("quarkAppStoreJson", quarkAppStoreJson);
            obj.put("pan", pan);

            return obj.toString();
        } catch (Exception e) {
            return "";
        }
    }
}