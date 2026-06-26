package com.github.catvod.api;

import com.github.catvod.spider.Init;

/**
 * 弹幕渲染配置类
 * 
 * 提供DanmakuFlameMaster引擎的详细配置参数
 * 参考: https://github.com/Bilibili/DanmakuFlameMaster
 */
public class DanmakuConfig {

    // ========== 核心开关 ==========
    private static final String KEY_ENABLE = "danmaku_enable";
    private static final String KEY_DANMAKU_ENABLE_CACHE = "danmaku_enable_cache";
    private static final String KEY_DANMAKU_ENABLE_OVERLAP = "danmaku_enable_overlap";

    // ========== 显示控制 ==========
    private static final String KEY_MAX_SIZE = "danmaku_max_size";
    private static final String KEY_SCROLL_SPEED = "danmaku_scroll_speed";
    private static final String KEY_TEXT_SCALE = "danmaku_text_scale";
    private static final String KEY_DANMAKU_MARGIN = "danmaku_margin";

    // ========== 最大行数限制 ==========
    private static final String KEY_MAX_LINE_SCROLL = "danmaku_max_line_scroll";
    private static final String KEY_MAX_LINE_TOP = "danmaku_max_line_top";
    private static final String KEY_MAX_LINE_BOTTOM = "danmaku_max_line_bottom";

    // ========== 样式设置 ==========
    private static final String KEY_STYLE = "danmaku_style";
    private static final String KEY_STROKE_WIDTH = "danmaku_stroke_width";
    private static final String KEY_ALPHA = "danmaku_alpha";

    // ========== 防重叠设置 ==========
    private static final String KEY_OVERLAP_SCROLL = "danmaku_overlap_scroll";
    private static final String KEY_OVERLAP_TOP = "danmaku_overlap_top";
    private static final String KEY_OVERLAP_BOTTOM = "danmaku_overlap_bottom";

    // ========== 默认值 ==========
    private static final boolean DEFAULT_ENABLE = true;
    private static final boolean DEFAULT_ENABLE_CACHE = true;
    private static final boolean DEFAULT_ENABLE_OVERLAP = true;
    private static final float DEFAULT_SCROLL_SPEED = 1.0f;
    private static final float DEFAULT_TEXT_SCALE = 1.0f;
    private static final int DEFAULT_DANMAKU_MARGIN = 40;
    private static final int DEFAULT_MAX_LINE_SCROLL = 10;
    private static final int DEFAULT_MAX_LINE_TOP = 3;
    private static final int DEFAULT_MAX_LINE_BOTTOM = 5;
    private static final int DEFAULT_STYLE = 2; // 0=透明 1=阴影 2=描边 3=投影
    private static final float DEFAULT_STROKE_WIDTH = 3.0f;
    private static final float DEFAULT_ALPHA = 1.0f;

    // 弹幕样式常量
    public static final int STYLE_NONE = 0;      // 透明
    public static final int STYLE_SHADOW = 1;    // 阴影
    public static final int STYLE_STROKEN = 2;   // 描边
    public static final int STYLE_PROJECTION = 3; // 投影

    // ========== 核心开关 ==========

    /**
     * 是否启用弹幕
     */
    public static boolean isEnable() {
        return Init.getBoolean(KEY_ENABLE, DEFAULT_ENABLE);
    }

    public static void putEnable(boolean value) {
        Init.put(KEY_ENABLE, value);
    }

    /**
     * 是否启用弹幕绘制缓存
     * 启用后显著提升性能，但会占用更多内存
     */
    public static boolean isEnableCache() {
        return Init.getBoolean(KEY_DANMAKU_ENABLE_CACHE, DEFAULT_ENABLE_CACHE);
    }

    public static void putEnableCache(boolean value) {
        Init.put(KEY_DANMAKU_ENABLE_CACHE, value);
    }

    /**
     * 是否启用防重叠
     * 防止弹幕重叠在一起
     */
    public static boolean isEnableOverlap() {
        return Init.getBoolean(KEY_DANMAKU_ENABLE_OVERLAP, DEFAULT_ENABLE_OVERLAP);
    }

    public static void putEnableOverlap(boolean value) {
        Init.put(KEY_DANMAKU_ENABLE_OVERLAP, value);
    }

    // ========== 显示控制 ==========

    /**
     * 获取滚动速度因子
     * 默认1.0，值越大弹幕滚动越慢
     */
    public static float getScrollSpeed() {
        return Init.getFloat(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED);
    }

    public static void putScrollSpeed(float value) {
        Init.put(KEY_SCROLL_SPEED, value);
    }

    /**
     * 获取文字缩放比例
     * 默认1.0
     */
    public static float getTextScale() {
        return Init.getFloat(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE);
    }

    public static void putTextScale(float value) {
        Init.put(KEY_TEXT_SCALE, value);
    }

    /**
     * 获取弹幕间距(像素)
     * 默认40
     */
    public static int getMargin() {
        return Init.getInt(KEY_DANMAKU_MARGIN, DEFAULT_DANMAKU_MARGIN);
    }

    public static void putMargin(int value) {
        Init.put(KEY_DANMAKU_MARGIN, value);
    }

    // ========== 最大行数限制 ==========

    /**
     * 获取滚动弹幕最大显示行数
     */
    public static int getMaxLineScroll() {
        return Init.getInt(KEY_MAX_LINE_SCROLL, DEFAULT_MAX_LINE_SCROLL);
    }

    public static void putMaxLineScroll(int value) {
        Init.put(KEY_MAX_LINE_SCROLL, value);
    }

    /**
     * 获取顶部弹幕最大显示行数
     */
    public static int getMaxLineTop() {
        return Init.getInt(KEY_MAX_LINE_TOP, DEFAULT_MAX_LINE_TOP);
    }

    public static void putMaxLineTop(int value) {
        Init.put(KEY_MAX_LINE_TOP, value);
    }

    /**
     * 获取底部弹幕最大显示行数
     */
    public static int getMaxLineBottom() {
        return Init.getInt(KEY_MAX_LINE_BOTTOM, DEFAULT_MAX_LINE_BOTTOM);
    }

    public static void putMaxLineBottom(int value) {
        Init.put(KEY_MAX_LINE_BOTTOM, value);
    }

    // ========== 样式设置 ==========

    /**
     * 获取弹幕样式
     * 0=透明, 1=阴影, 2=描边, 3=投影
     */
    public static int getStyle() {
        return Init.getInt(KEY_STYLE, DEFAULT_STYLE);
    }

    public static void putStyle(int value) {
        Init.put(KEY_STYLE, value);
    }

    /**
     * 获取描边宽度
     * 仅在样式为描边时有效
     */
    public static float getStrokeWidth() {
        return Init.getFloat(KEY_STROKE_WIDTH, DEFAULT_STROKE_WIDTH);
    }

    public static void putStrokeWidth(float value) {
        Init.put(KEY_STROKE_WIDTH, value);
    }

    /**
     * 获取透明度
     * 0.0=完全透明, 1.0=完全不透明
     */
    public static float getAlpha() {
        return Init.getFloat(KEY_ALPHA, DEFAULT_ALPHA);
    }

    public static void putAlpha(float value) {
        Init.put(KEY_ALPHA, value);
    }

    // ========== 防重叠设置 ==========

    /**
     * 滚动弹幕是否防重叠
     */
    public static boolean isOverlapScroll() {
        return Init.getBoolean(KEY_OVERLAP_SCROLL, true);
    }

    public static void putOverlapScroll(boolean value) {
        Init.put(KEY_OVERLAP_SCROLL, value);
    }

    /**
     * 顶部弹幕是否防重叠
     */
    public static boolean isOverlapTop() {
        return Init.getBoolean(KEY_OVERLAP_TOP, true);
    }

    public static void putOverlapTop(boolean value) {
        Init.put(KEY_OVERLAP_TOP, value);
    }

    /**
     * 底部弹幕是否防重叠
     */
    public static boolean isOverlapBottom() {
        return Init.getBoolean(KEY_OVERLAP_BOTTOM, true);
    }

    public static void putOverlapBottom(boolean value) {
        Init.put(KEY_OVERLAP_BOTTOM, value);
    }

    // ========== 辅助方法 ==========

    /**
     * 获取样式名称
     */
    public static String getStyleName() {
        switch (getStyle()) {
            case STYLE_NONE:
                return "透明";
            case STYLE_SHADOW:
                return "阴影";
            case STYLE_STROKEN:
                return "描边";
            case STYLE_PROJECTION:
                return "投影";
            default:
                return "描边";
        }
    }

    /**
     * 获取弹幕最大尺寸限制
     * 弹幕数量超过此值会被丢弃
     */
    public static int getMaxSize() {
        return Init.getInt(KEY_MAX_SIZE, 1000);
    }

    public static void putMaxSize(int value) {
        Init.put(KEY_MAX_SIZE, value);
    }

    /**
     * 重置所有设置为默认值
     */
    public static void resetAll() {
        putEnable(DEFAULT_ENABLE);
        putEnableCache(DEFAULT_ENABLE_CACHE);
        putEnableOverlap(DEFAULT_ENABLE_OVERLAP);
        putScrollSpeed(DEFAULT_SCROLL_SPEED);
        putTextScale(DEFAULT_TEXT_SCALE);
        putMargin(DEFAULT_DANMAKU_MARGIN);
        putMaxLineScroll(DEFAULT_MAX_LINE_SCROLL);
        putMaxLineTop(DEFAULT_MAX_LINE_TOP);
        putMaxLineBottom(DEFAULT_MAX_LINE_BOTTOM);
        putStyle(DEFAULT_STYLE);
        putStrokeWidth(DEFAULT_STROKE_WIDTH);
        putAlpha(DEFAULT_ALPHA);
        putOverlapScroll(true);
        putOverlapTop(true);
        putOverlapBottom(true);
        putMaxSize(1000);
    }

    /**
     * 获取配置信息摘要
     */
    public static String getConfigSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("弹幕配置:\n");
        sb.append("  启用: ").append(isEnable()).append("\n");
        sb.append("  缓存: ").append(isEnableCache()).append("\n");
        sb.append("  防重叠: ").append(isEnableOverlap()).append("\n");
        sb.append("  滚动速度: ").append(getScrollSpeed()).append("x\n");
        sb.append("  文字缩放: ").append(getTextScale()).append("x\n");
        sb.append("  弹幕间距: ").append(getMargin()).append("px\n");
        sb.append("  样式: ").append(getStyleName()).append("\n");
        sb.append("  透明度: ").append(getAlpha()).append("\n");
        sb.append("  滚动行数: ").append(getMaxLineScroll()).append("\n");
        sb.append("  顶部行数: ").append(getMaxLineTop()).append("\n");
        sb.append("  底部行数: ").append(getMaxLineBottom()).append("\n");
        return sb.toString();
    }
}
