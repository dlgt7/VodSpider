package com.github.catvod.bean;

import android.text.TextUtils;

import com.github.catvod.api.DanmakuConfig;
import com.github.catvod.utils.Trans;

import java.util.regex.Matcher;

/**
 * 弹幕数据项
 * 用于解析B站等平台的XML格式弹幕
 */
public class DanmakuData {

    /**
     * 弹幕类型
     * 1: 滚动弹幕
     * 4: 顶部弹幕
     * 5: 底部弹幕
     */
    private int type;

    /**
     * 弹幕颜色 (ARGB)
     */
    private int color;

    /**
     * 时间戳 (毫秒)
     */
    private long time;

    /**
     * 弹幕文字
     */
    private String text;

    /**
     * 弹幕大小
     */
    private float size;

    /**
     * 默认显示时长 (毫秒)
     */
    private long duration = 5000;

    /**
     * 是否需要描边（用于浅色文字在浅色背景可见性）
     */
    private boolean needsStroke;

    public DanmakuData() {
    }

    /**
     * 从B站XML弹幕解析Matcher创建
     * 格式: <d p="时间,类型,颜色,大小">文字</d>
     */
    public DanmakuData(Matcher matcher) throws Exception {
        this.param(matcher.group(1));
        this.text = matcher.group(2);
        this.trans();
    }

    private void param(String param) throws Exception {
        String[] params = param.split(",");
        if (params.length < 4) throw new Exception("Invalid danmaku param");
        this.type = Integer.parseInt(params[1]);
        this.time = (long) (Float.parseFloat(params[0]) * 1000);
        this.size = Float.parseFloat(params[2]);
        // B站颜色是十进制的RGB，需要转换为ARGB
        long rgb = Long.parseLong(params[3]);
        this.color = (int) (0xFF000000L | rgb);
        // 计算是否需要描边
        this.needsStroke = calculateNeedsStroke(this.color);
    }

    /**
     * 计算颜色是否需要描边
     * 白色/浅色弹幕在浅色背景上可见性差，需要描边
     * 
     * @param color ARGB颜色值
     * @return true 如果需要描边
     */
    private static boolean calculateNeedsStroke(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int brightness = r + g + b;
        // RGB分量之和 > 600 被认为是浅色
        return brightness > 600;
    }

    /**
     * 创建简单弹幕
     */
    public static DanmakuData create(String text, long time) {
        return create(text, time, 1, 0xFFFFFFFF, 25f);
    }

    /**
     * 创建带颜色的弹幕
     */
    public static DanmakuData create(String text, long time, int color) {
        return create(text, time, 1, color, 25f);
    }

    /**
     * 创建指定类型的弹幕
     */
    public static DanmakuData create(String text, long time, int type, int color, float size) {
        DanmakuData data = new DanmakuData();
        data.text = text;
        data.time = time;
        data.type = type;
        data.color = color;
        data.size = size;
        data.needsStroke = calculateNeedsStroke(color);
        return data;
    }

    /**
     * 创建带显示时长的弹幕
     */
    public static DanmakuData create(String text, long time, int type, int color, float size, long duration) {
        DanmakuData data = create(text, time, type, color, size);
        data.duration = duration;
        return data;
    }

    /**
     * 繁简转换
     */
    private void trans() {
        if (!Trans.needTrans()) return;
        this.text = Trans.s2t(text);
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        this.needsStroke = calculateNeedsStroke(color);
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getText() {
        return TextUtils.isEmpty(text) ? "" : text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = size;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    /**
     * 获取适合显示的颜色值
     * 
     * @return ARGB颜色值
     */
    public int getDisplayColor() {
        return color;
    }

    /**
     * 检查是否需要描边
     * @return true 如果是浅色需要描边
     */
    public boolean needsStroke() {
        return needsStroke;
    }

    /**
     * 获取描边颜色（黑色）
     */
    public int getStrokeColor() {
        return 0xFF000000;
    }

    /**
     * 是否是滚动弹幕
     */
    public boolean isScroll() {
        return type == 1;
    }

    /**
     * 是否是顶部弹幕
     */
    public boolean isTop() {
        return type == 4;
    }

    /**
     * 是否是底部弹幕
     */
    public boolean isBottom() {
        return type == 5;
    }

    /**
     * 转换为ASS字幕格式
     * @param durationMs 显示时长（毫秒），默认使用this.duration
     */
    public String toAss(long durationMs) {
        if (durationMs <= 0) durationMs = this.duration;
        
        StringBuilder sb = new StringBuilder();
        sb.append("Dialogue: ");
        sb.append(formatTime(time)).append(","); // 开始时间
        sb.append(formatTime(time + durationMs)).append(","); // 结束时间
        sb.append(getStyleForType()).append(","); // 样式
        sb.append("0,0,0,,{");
        sb.append(String.format("\\c&H%06X&", color & 0xFFFFFF)); // 颜色
        
        // 如果是浅色弹幕，添加描边（使用可配置的描边宽度）
        if (needsStroke()) {
            float strokeWidth = DanmakuConfig.getStrokeWidth();
            sb.append(String.format("\\3c&H%06X&", getStrokeColor() & 0xFFFFFF)); // 描边颜色
            sb.append(String.format("\\3\\bord%.1f", strokeWidth)); // 描边宽度
        }
        
        sb.append(String.format("\\fs%2.0f", size * 2)); // 字号
        sb.append("}").append(escapeAss(text));
        return sb.toString();
    }

    /**
     * 转换为ASS字幕格式（使用默认时长）
     */
    public String toAss() {
        return toAss(0);
    }

    private String getStyleForType() {
        switch (type) {
            case 4:
                return "Top"; // 顶部
            case 5:
                return "Bottom"; // 底部
            default:
                return "Scroll"; // 滚动
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        int hour = (int) (seconds / 3600);
        int minute = (int) ((seconds % 3600) / 60);
        int sec = (int) (seconds % 60);
        int centisec = (int) ((ms % 1000) / 10);
        return String.format("%d:%02d:%02d.%02d", hour, minute, sec, centisec);
    }

    private String escapeAss(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("\n", "\\N");
    }

    @Override
    public String toString() {
        return "DanmakuData{" +
                "time=" + time +
                ", type=" + type +
                ", color=" + String.format("#%06X", color & 0xFFFFFF) +
                ", text='" + text + '\'' +
                '}';
    }
}
