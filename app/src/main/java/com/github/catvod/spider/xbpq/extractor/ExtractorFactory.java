package com.github.catvod.spider.xbpq.extractor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 提取器工厂类
 * <p>
 * 根据配置自动选择适合的提取策略。
 * 支持CSS选择器提取和正则提取两种模式。
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class ExtractorFactory {

    /**
     * 创建视频列表提取器
     *
     * @param isCssMode 是否使用CSS模式
     * @return 对应的提取器
     */
    public static VideoListExtractor createVideoListExtractor(boolean isCssMode) {
        return isCssMode ? new CssVideoListExtractor() : new RegexVideoListExtractor();
    }

    /**
     * 创建详情提取器
     *
     * @param isCssMode 是否使用CSS模式
     * @return 对应的提取器
     */
    public static DetailExtractor createDetailExtractor(boolean isCssMode) {
        return isCssMode ? new CssDetailExtractor() : new RegexDetailExtractor();
    }

    /**
     * 创建搜索提取器
     *
     * @param isCssMode 是否使用CSS模式
     * @return 对应的提取器
     */
    public static SearchExtractor createSearchExtractor(boolean isCssMode) {
        return isCssMode ? new CssSearchExtractor() : new RegexSearchExtractor();
    }

    /**
     * 创建播放列表提取器
     *
     * @param isCssMode 是否使用CSS模式
     * @return 对应的提取器
     */
    public static PlayListExtractor createPlayListExtractor(boolean isCssMode) {
        return isCssMode ? new CssPlayListExtractor() : new RegexPlayListExtractor();
    }

    /**
     * 视频列表提取器接口
     */
    public interface VideoListExtractor {
        JSONArray extract(String html, JSONObject config) throws Exception;
    }

    /**
     * 详情提取器接口
     */
    public interface DetailExtractor {
        JSONObject extract(String html, JSONObject config, JSONObject vinfo) throws Exception;
    }

    /**
     * 搜索提取器接口
     */
    public interface SearchExtractor {
        JSONArray extract(String html, JSONObject config) throws Exception;
    }

    /**
     * 播放列表提取器接口
     */
    public interface PlayListExtractor {
        JSONArray extract(String html, JSONObject config, int sort) throws Exception;
    }
}
