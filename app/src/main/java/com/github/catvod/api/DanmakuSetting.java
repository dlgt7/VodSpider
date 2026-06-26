package com.github.catvod.api;

import android.text.TextUtils;

import com.github.catvod.spider.Init;
import com.github.catvod.utils.Trans;

/**
 * 弹幕设置管理类
 * 
 * 支持的弹幕API:
 * 1. 弹弹play官方API: https://api.dandanplay.net (需要AppId认证)
 * 2. 自托管danmu_api: https://github.com/huangxd-/danmu_api
 * 
 * 弹弹play API接口示例:
 * - 搜索: /api/v2/search/anime?keyword=关键词
 * - 弹幕: /api/v2/comment/{episodeId}
 */
public class DanmakuSetting {

    private static final String KEY_DANMAKU_LOAD = "danmaku_load";
    private static final String KEY_DANMAKU_AUTO = "danmaku_auto";
    private static final String KEY_DANMAKU_API_URL = "danmaku_api_url";
    private static final String KEY_DANMAKU_APP_ID = "danmaku_app_id";
    private static final String KEY_DANMAKU_SHOW = "danmaku_show";
    private static final String KEY_DANMAKU_TEXT_SCALE = "danmaku_text_scale";
    private static final String KEY_DANMAKU_TRANSPARENCY = "danmaku_transparency";
    private static final String KEY_DANMAKU_TEXT_BOLD = "danmaku_text_bold";
    private static final String KEY_DANMAKU_STYLE_MODE = "danmaku_style_mode";
    private static final String KEY_DANMAKU_DURATION = "danmaku_duration";
    private static final String KEY_DANMAKU_MAX_ON_SCREEN = "danmaku_max_on_screen";
    private static final String KEY_DANMAKU_SCROLL_AREA_RATIO = "danmaku_scroll_area_ratio";
    private static final String KEY_DANMAKU_SHOW_SCROLL = "danmaku_show_scroll";
    private static final String KEY_DANMAKU_SHOW_TOP = "danmaku_show_top";
    private static final String KEY_DANMAKU_SHOW_BOTTOM = "danmaku_show_bottom";
    private static final String KEY_DANMAKU_LOCAL_TRANS = "danmaku_local_trans";

    // 弹弹play官方API地址 (稳定可靠，需要AppId)
    // 申请AppId: https://dev.dandanplay.com
    public static final String DEFAULT_API_URL = "https://api.dandanplay.net";
    
    // 公共测试AppId (仅供测试，正式使用请申请自己的AppId)
    private static final String DEFAULT_APP_ID = "";

    /**
     * 检查API URL是否有效
     * @param url API地址
     * @return true 如果URL有效（以http://或https://开头）
     */
    public static boolean isValidApiUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * 检查API URL是否可用（已配置且有效）
     */
    public static boolean isApiUrlUsable() {
        return isValidApiUrl(getApiUrl());
    }

    /**
     * 是否启用弹幕加载
     */
    public static boolean isLoad() {
        return Init.getBoolean(KEY_DANMAKU_LOAD, false);
    }

    public static void putLoad(boolean value) {
        Init.put(KEY_DANMAKU_LOAD, value);
    }

    /**
     * 是否自动搜索弹幕
     */
    public static boolean isAuto() {
        return Init.getBoolean(KEY_DANMAKU_AUTO, true);
    }

    public static void putAuto(boolean value) {
        Init.put(KEY_DANMAKU_AUTO, value);
    }

    /**
     * 弹幕API地址
     */
    public static String getApiUrl() {
        String url = Init.getString(KEY_DANMAKU_API_URL, "");
        // 如果用户没有配置，使用默认的弹弹play API
        if (TextUtils.isEmpty(url)) {
            return DEFAULT_API_URL;
        }
        return url;
    }

    /**
     * 设置弹幕API地址
     * @param url API地址
     * @return true 如果设置成功，false 如果URL无效
     */
    public static boolean putApiUrl(String url) {
        if (!isValidApiUrl(url)) {
            return false;
        }
        Init.put(KEY_DANMAKU_API_URL, url);
        return true;
    }

    /**
     * 重置API地址为默认值
     */
    public static void resetApiUrl() {
        Init.remove(KEY_DANMAKU_API_URL);
    }

    /**
     * 弹弹play AppId (用于API认证)
     * 申请地址: https://dev.dandanplay.com
     */
    public static String getAppId() {
        return Init.getString(KEY_DANMAKU_APP_ID, DEFAULT_APP_ID);
    }

    public static void putAppId(String appId) {
        Init.put(KEY_DANMAKU_APP_ID, appId);
    }

    /**
     * 重置AppId
     */
    public static void resetAppId() {
        Init.remove(KEY_DANMAKU_APP_ID);
    }

    /**
     * AppId是否已配置（用于判断是否需要提示用户配置）
     */
    public static boolean hasAppId() {
        String appId = getAppId();
        return !TextUtils.isEmpty(appId);
    }

    /**
     * 是否显示弹幕
     */
    public static boolean isShow() {
        return Init.getBoolean(KEY_DANMAKU_SHOW, true);
    }

    public static void putShow(boolean value) {
        Init.put(KEY_DANMAKU_SHOW, value);
    }

    /**
     * 弹幕文字缩放
     */
    public static float getTextScale() {
        return Init.getFloat(KEY_DANMAKU_TEXT_SCALE, 1.0f);
    }

    public static void putTextScale(float value) {
        Init.put(KEY_DANMAKU_TEXT_SCALE, value);
    }

    /**
     * 弹幕透明度
     */
    public static float getTransparency() {
        return Init.getFloat(KEY_DANMAKU_TRANSPARENCY, 0.0f);
    }

    public static void putTransparency(float value) {
        Init.put(KEY_DANMAKU_TRANSPARENCY, value);
    }

    /**
     * 弹幕文字加粗
     */
    public static boolean isTextBold() {
        return Init.getBoolean(KEY_DANMAKU_TEXT_BOLD, false);
    }

    public static void putTextBold(boolean value) {
        Init.put(KEY_DANMAKU_TEXT_BOLD, value);
    }

    /**
     * 弹幕样式模式 (0:无 1:阴影 2:描边 3:投影)
     */
    public static int getStyleMode() {
        return Init.getInt(KEY_DANMAKU_STYLE_MODE, 2);
    }

    public static void putStyleMode(int value) {
        Init.put(KEY_DANMAKU_STYLE_MODE, value);
    }

    /**
     * 弹幕显示时长 (毫秒)
     */
    public static long getDuration() {
        return Init.getLong(KEY_DANMAKU_DURATION, 8000L);
    }

    public static void putDuration(long value) {
        Init.put(KEY_DANMAKU_DURATION, value);
    }

    /**
     * 屏幕最大弹幕数
     */
    public static int getMaxOnScreen() {
        return Init.getInt(KEY_DANMAKU_MAX_ON_SCREEN, 150);
    }

    public static void putMaxOnScreen(int value) {
        Init.put(KEY_DANMAKU_MAX_ON_SCREEN, value);
    }

    /**
     * 滚动区域比例
     */
    public static float getScrollAreaRatio() {
        return Init.getFloat(KEY_DANMAKU_SCROLL_AREA_RATIO, 0.5f);
    }

    public static void putScrollAreaRatio(float value) {
        Init.put(KEY_DANMAKU_SCROLL_AREA_RATIO, value);
    }

    /**
     * 是否显示滚动弹幕
     */
    public static boolean isShowScroll() {
        return Init.getBoolean(KEY_DANMAKU_SHOW_SCROLL, true);
    }

    public static void putShowScroll(boolean value) {
        Init.put(KEY_DANMAKU_SHOW_SCROLL, value);
    }

    /**
     * 是否显示顶部弹幕
     */
    public static boolean isShowTop() {
        return Init.getBoolean(KEY_DANMAKU_SHOW_TOP, true);
    }

    public static void putShowTop(boolean value) {
        Init.put(KEY_DANMAKU_SHOW_TOP, value);
    }

    /**
     * 是否显示底部弹幕
     */
    public static boolean isShowBottom() {
        return Init.getBoolean(KEY_DANMAKU_SHOW_BOTTOM, true);
    }

    public static void putShowBottom(boolean value) {
        Init.put(KEY_DANMAKU_SHOW_BOTTOM, value);
    }

    /**
     * 本地简繁转换开关
     * -1: 跟随系统Locale (默认)
     * 0: 不转换
     * 1: 强制简体
     * 2: 强制繁体
     * 
     * 注意：此设置为本地转换，弹弹play API已通过chConvert参数处理简繁转换
     * 本地转换仅作为备用方案
     */
    public static int getLocalTrans() {
        return Init.getInt(KEY_DANMAKU_LOCAL_TRANS, -1);
    }

    public static void putLocalTrans(int value) {
        Init.put(KEY_DANMAKU_LOCAL_TRANS, value);
    }

    /**
     * 是否启用本地转换
     * @return true 如果需要本地转换
     */
    public static boolean isLocalTransEnabled() {
        int mode = getLocalTrans();
        if (mode == -1) {
            // 跟随系统：仅繁体环境需要转换
            return Trans.needTrans();
        }
        // 强制模式
        return mode == 1 || mode == 2;
    }

    /**
     * 获取有效的弹幕API地址
     * 优先使用用户配置的API地址
     */
    public static String getEffectiveApiUrl() {
        String userUrl = getApiUrl();
        if (!TextUtils.isEmpty(userUrl) && isValidApiUrl(userUrl)) {
            return userUrl;
        }
        return DEFAULT_API_URL;
    }

    /**
     * 重置所有设置为默认值（不包含API配置）
     */
    public static void resetAll() {
        putLoad(false);
        putAuto(true);
        putShow(true);
        putTextScale(1.0f);
        putTransparency(0.0f);
        putTextBold(false);
        putStyleMode(2);
        putDuration(8000L);
        putMaxOnScreen(150);
        putScrollAreaRatio(0.5f);
        putShowScroll(true);
        putShowTop(true);
        putShowBottom(true);
    }

    /**
     * 重置所有设置为默认值（包含API配置）
     */
    public static void resetAllIncludingApi() {
        resetAll();
        resetApiUrl();
        resetAppId();
    }

    /**
     * 获取弹幕配置信息（用于调试）
     */
    public static String getConfigInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("弹幕配置:\n");
        sb.append("启用: ").append(isLoad()).append("\n");
        sb.append("自动搜索: ").append(isAuto()).append("\n");
        sb.append("API地址: ").append(getEffectiveApiUrl()).append("\n");
        sb.append("AppId: ").append(hasAppId() ? "已配置" : "未配置").append("\n");
        sb.append("显示弹幕: ").append(isShow()).append("\n");
        sb.append("文字缩放: ").append(getTextScale()).append("\n");
        sb.append("透明度: ").append(getTransparency()).append("\n");
        sb.append("加粗: ").append(isTextBold()).append("\n");
        sb.append("样式: ").append(getStyleMode()).append("\n");
        sb.append("时长: ").append(getDuration()).append("ms\n");
        sb.append("最大数量: ").append(getMaxOnScreen()).append("\n");
        sb.append("滚动区域: ").append(getScrollAreaRatio()).append("\n");
        sb.append("显示滚动: ").append(isShowScroll()).append("\n");
        sb.append("显示顶部: ").append(isShowTop()).append("\n");
        sb.append("显示底部: ").append(isShowBottom());
        return sb.toString();
    }
}
