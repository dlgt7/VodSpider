package com.github.catvod.api;

import com.github.catvod.spider.Init;

/**
 * 去广告设置管理类
 * 
 * 功能说明：
 * 1. 去广告功能基于M3U8播放列表的DISCONTINUITY标签检测
 * 2. 通过M3u8Fix处理m3u8链接，自动移除广告片段
 * 3. 支持开关控制，可通过AdSetting.isAdblock()查询
 * 
 * 广告识别原理：
 * - m3u8中的广告片段通常带有 #EXT-X-DISCONTINUITY 标签
 * - DISCONTINUITY表示内容不连续，用于标记广告插入点
 * - 通过移除带有DISCONTINUITY标记的片段实现去广告
 * 
 * 使用方式：
 * - 启用: AdSetting.putAdblock(true)
 * - 禁用: AdSetting.putAdblock(false)
 * - 查询: AdSetting.isAdblock()
 * 
 * 在Proxy中使用:
 * M3u8Fix.fixM3u8(url, params) 会自动检查开关状态
 */
public class AdSetting {

    private static final String KEY_ADBLOCK = "adblock";
    private static final String KEY_AD_SOURCE = "ad_source";
    private static final String KEY_CUSTOM_AD_RULES = "custom_ad_rules";
    private static final String KEY_ADBLOCK_AGGRESSIVE = "adblock_aggressive";
    
    // 去广告数据源类型
    public static final int SOURCE_SYSTEM = 0;    // 使用系统内置广告拦截规则
    public static final int SOURCE_CUSTOM = 1;    // 使用自定义广告拦截规则
    public static final int SOURCE_BOTH = 2;     // 同时使用系统和自定义规则
    
    // 默认：保守模式(false)
    private static final boolean DEFAULT_AGGRESSIVE_MODE = false;

    /**
     * 是否启用去广告功能
     * 
     * @return true 如果启用去广告
     */
    public static boolean isAdblock() {
        return Init.getBoolean(KEY_ADBLOCK, true);
    }

    /**
     * 设置去广告功能开关
     * 
     * @param enabled true 启用，false 禁用
     */
    public static void putAdblock(boolean enabled) {
        Init.put(KEY_ADBLOCK, enabled);
    }

    /**
     * 获取广告数据源类型
     * 
     * @return 0:系统规则 1:自定义规则 2:两者都使用
     */
    public static int getAdSource() {
        return Init.getInt(KEY_AD_SOURCE, SOURCE_SYSTEM);
    }

    /**
     * 设置广告数据源类型
     * 
     * @param source 广告数据源类型
     */
    public static void putAdSource(int source) {
        Init.put(KEY_AD_SOURCE, source);
    }

    /**
     * 获取自定义广告规则
     * 格式：JSON数组，每个元素包含匹配规则和替换规则
     * 
     * @return 自定义规则JSON字符串
     */
    public static String getCustomAdRules() {
        return Init.getString(KEY_CUSTOM_AD_RULES, getDefaultAdRules());
    }

    /**
     * 设置自定义广告规则
     * 
     * @param rules JSON格式的广告规则
     */
    public static void putCustomAdRules(String rules) {
        Init.put(KEY_CUSTOM_AD_RULES, rules);
    }

    /**
     * 获取默认的广告拦截规则
     * 基于常见的广告域名和模式（预留用于扩展URL拦截）
     * 
     * @return 默认规则JSON
     */
    private static String getDefaultAdRules() {
        return "[" +
                "\"*.doubleclick.net\"," +
                "\"*.googlesyndication.com\"," +
                "\"*.googleadservices.com\"," +
                "\"*.adnxs.com\"," +
                "\"*.adsrvr.org\"," +
                "\"*.advertising.com\"," +
                "\"*.outbrain.com\"," +
                "\"*.taboola.com\"," +
                "\"*.criteo.com\"," +
                "\"*.adcolony.com\"," +
                "\"*.unity3d.com/ads\"," +
                "\"*.unityads.unity3d.com\"," +
                "\"*.applovin.com\"," +
                "\"*.chartboost.com\"," +
                "\"*.vungle.com\"," +
                "\"*.startappservice.com\"," +
                "\"*.moatads.com\"," +
                "\"*.quantserve.com\"," +
                "\"*.scorecardresearch.com\"," +
                "\"*.media.net\"," +
                "\"*.adform.net\"," +
                "\"*.smartadserver.com\"," +
                "\"*.pubmatic.com\"," +
                "\"*.rubiconproject.com\"," +
                "\"*.openx.net\"," +
                "\"*.bidswitch.net\"," +
                "\"*.casalemedia.com\"," +
                "\"*/ads/*\"," +
                "\"*/ad/*\"," +
                "\"*?*ad=*\"" +
                "]";
    }

    /**
     * 重置所有设置为默认值
     */
    public static void resetAll() {
        putAdblock(true);
        putAdSource(SOURCE_SYSTEM);
        putCustomAdRules(getDefaultAdRules());
        putAdblockAggressiveMode(false);
    }

    /**
     * 是否使用激进模式去广告
     * 
     * 激进模式：使用全部广告检测策略，可能有误杀但广告检测率更高
     * 保守模式：仅使用基本广告检测，误杀率低但可能漏掉部分广告
     * 
     * @return true=激进模式，false=保守模式（默认）
     */
    public static boolean isAdblockAggressiveMode() {
        return Init.getBoolean(KEY_ADBLOCK_AGGRESSIVE, DEFAULT_AGGRESSIVE_MODE);
    }

    /**
     * 设置去广告模式
     * 
     * @param aggressive true=激进模式，false=保守模式（默认）
     */
    public static void putAdblockAggressiveMode(boolean aggressive) {
        Init.put(KEY_ADBLOCK_AGGRESSIVE, aggressive);
    }

    /**
     * 获取去广告模式名称
     */
    public static String getAdblockModeName() {
        return isAdblockAggressiveMode() ? "激进模式" : "保守模式";
    }

    /**
     * 获取配置信息（用于调试）
     * 
     * @return 配置信息字符串
     */
    public static String getConfigInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("去广告配置:\n");
        sb.append("启用: ").append(isAdblock()).append("\n");
        sb.append("数据源: ").append(getAdSourceName()).append("\n");
        sb.append("自定义规则: ").append(getCustomAdRules().length() > 50 ? 
                getCustomAdRules().substring(0, 50) + "..." : getCustomAdRules());
        return sb.toString();
    }

    /**
     * 获取数据源类型名称
     */
    private static String getAdSourceName() {
        switch (getAdSource()) {
            case SOURCE_SYSTEM:
                return "系统规则";
            case SOURCE_CUSTOM:
                return "自定义规则";
            case SOURCE_BOTH:
                return "系统+自定义";
            default:
                return "未知";
        }
    }

    /**
     * 检查去广告功能是否可用
     * 
     * @return true
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * 获取去广告功能版本
     * 
     * @return 版本字符串
     */
    public static String getVersion() {
        return "1.0.0";
    }
}
