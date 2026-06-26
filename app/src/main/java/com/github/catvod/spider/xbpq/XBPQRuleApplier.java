package com.github.catvod.spider.xbpq;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQRuleApplier - 规则应用器
 * 
 * <p>负责处理过滤、替换、排序等规则的应用，包括：</p>
 * <ul>
 *   <li>包含/不包含过滤</li>
 *   <li>替换规则</li>
 *   <li>排序规则</li>
 *   <li>序号替换</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQRuleApplier {

    // ==================== 常量定义 ====================
    
    /** 分隔符：多个关键词分隔 */
    private static final String SEP_HASH = "#";
    
    /** 分隔符：替换前后分隔 */
    private static final String SEP_REPLACE = ">>";
    
    /** 空值替换标记 */
    private static final String EMPTY_VALUE = "空";
    
    /** 序号标记 */
    private static final String SEQ_MARKER = "<序号>";
    
    /** 通配符 */
    private static final String WILDCARD = "*";
    
    // ==================== 正则表达式 ====================
    
    /** 过滤规则匹配 */
    private static final Pattern FILTER_PATTERN = Pattern.compile("\\[(包含|不包含):([^\\]]+)\\]");
    
    /** 替换规则匹配 */
    private static final Pattern REPLACE_PATTERN = Pattern.compile("\\[替换:([^\\]]+)\\]");
    
    /** 排序规则匹配 */
    private static final Pattern SORT_PATTERN = Pattern.compile("\\[排序:([^\\]]+)\\]");
    
    // ==================== 过滤规则 ====================
    
    /**
     * 应用过滤规则
     * 
     * @param text 文本内容
     * @param rule 包含过滤规则的完整规则
     * @return 过滤后的文本（如果不符合过滤条件则返回空字符串）
     */
    public static String applyFilter(String text, String rule) {
        SpiderDebug.log("[XBPQ] applyFilter 开始: text长度=" + (text != null ? text.length() : 0) + ", rule=" + rule);
        
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] applyFilter: 参数为空, 返回原文本");
            return text;
        }
        
        try {
            String filterRule = extractFilterRule(rule);
            if (!TextUtils.isEmpty(filterRule)) {
                SpiderDebug.log("[XBPQ] applyFilter 路由: filterRule=" + filterRule);
                if (!applyFilterRule(text, filterRule)) {
                    SpiderDebug.log("[XBPQ] applyFilter 结果: 过滤不通过, 返回空字符串");
                    return "";
                }
                SpiderDebug.log("[XBPQ] applyFilter 结果: 过滤通过, 返回原文本");
            } else {
                SpiderDebug.log("[XBPQ] applyFilter: 未找到过滤规则, 返回原文本");
            }
            
            return text;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyFilter error: rule=" + rule + ", " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * 应用过滤规则（单个）
     * 
     * @param text 文本内容
     * @param filterRule 过滤规则（如 "包含:magnet" 或 "不包含:首页#资讯"）
     * @return 是否通过过滤
     */
    public static boolean applyFilterRule(String text, String filterRule) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(filterRule)) {
            return true;
        }
        
        try {
            Matcher matcher = FILTER_PATTERN.matcher(filterRule);
            if (matcher.find()) {
                String type = matcher.group(1); // 包含 或 不包含
                String keywords = matcher.group(2); // 关键词列表
                
                if (TextUtils.isEmpty(keywords)) {
                    return true;
                }
                
                String[] keywordList = keywords.split(SEP_HASH);
                
                for (String keyword : keywordList) {
                    keyword = keyword.trim();
                    if (TextUtils.isEmpty(keyword)) continue;
                    
                    if (type.equals("包含")) {
                        if (!text.contains(keyword)) {
                            return false;
                        }
                    } else if (type.equals("不包含")) {
                        if (text.contains(keyword)) {
                            return false;
                        }
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyFilterRule error: filterRule=" + filterRule + ", " + e.getMessage(), e);
            return true;
        }
    }
    
    /**
     * 应用数组过滤规则
     * 
     * @param array 数组列表
     * @param rule 包含过滤规则的完整规则
     * @return 过滤后的数组
     */
    public static ArrayList<String> applyArrayFilter(ArrayList<String> array, String rule) {
        if (array == null) {
            SpiderDebug.log("[XBPQ] applyArrayFilter: array为null");
            return new ArrayList<>();
        }
        
        if (array.isEmpty()) return array;
        
        try {
            String filterRule = extractFilterRule(rule);
            if (TextUtils.isEmpty(filterRule)) return array;
            
            Matcher matcher = FILTER_PATTERN.matcher(filterRule);
            if (matcher.find()) {
                String type = matcher.group(1);
                String keywords = matcher.group(2);
                
                if (TextUtils.isEmpty(keywords)) return array;
                
                ArrayList<String> result = new ArrayList<>();
                String[] keywordList = keywords.split(SEP_HASH);
                
                // 处理序号过滤
                ArrayList<Integer> excludeIndices = new ArrayList<>();
                for (String keyword : keywordList) {
                    keyword = keyword.trim();
                    if (TextUtils.isEmpty(keyword)) continue;
                    
                    // 检查是否为序号
                    if (keyword.matches("\\d+(-\\d+)?")) {
                        try {
                            if (keyword.contains("-")) {
                                // 范围序号，如 9-11
                                String[] range = keyword.split("-");
                                int start = Integer.parseInt(range[0]);
                                int end = Integer.parseInt(range[1]);
                                for (int i = start; i <= end; i++) {
                                    excludeIndices.add(i - 1); // 转换为 0-based 索引
                                }
                            } else {
                                // 单个序号
                                excludeIndices.add(Integer.parseInt(keyword) - 1);
                            }
                        } catch (NumberFormatException e) {
                            SpiderDebug.log("[XBPQ] applyArrayFilter: 序号解析失败 - " + keyword);
                        }
                    }
                }
                
                for (int i = 0; i < array.size(); i++) {
                    String item = array.get(i);
                    if (item == null) continue;
                    
                    boolean shouldInclude = true;
                    
                    // 检查序号过滤
                    if (excludeIndices.contains(i)) {
                        shouldInclude = false;
                    }
                    
                    // 检查关键词过滤
                    if (shouldInclude) {
                        for (String keyword : keywordList) {
                            keyword = keyword.trim();
                            if (TextUtils.isEmpty(keyword)) continue;
                            if (keyword.matches("\\d+(-\\d+)?")) continue; // 跳过序号
                            
                            if (type.equals("包含")) {
                                if (!item.contains(keyword)) {
                                    shouldInclude = false;
                                    break;
                                }
                            } else if (type.equals("不包含")) {
                                if (item.contains(keyword)) {
                                    shouldInclude = false;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (shouldInclude) {
                        result.add(item);
                    }
                }
                
                return result;
            }
            
            return array;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyArrayFilter error: rule=" + rule + ", " + e.getMessage(), e);
            return array;
        }
    }
    
    // ==================== 替换规则 ====================
    
    /**
     * 应用替换规则
     * 
     * @param text 文本内容
     * @param rule 包含替换规则的完整规则
     * @return 替换后的文本
     */
    public static String applyReplace(String text, String rule) {
        SpiderDebug.log("[XBPQ] applyReplace 开始: text长度=" + (text != null ? text.length() : 0) + ", rule=" + rule);
        
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] applyReplace: 参数为空, 返回原文本");
            return text;
        }
        
        try {
            String replaceRule = extractReplaceRule(rule);
            if (!TextUtils.isEmpty(replaceRule)) {
                SpiderDebug.log("[XBPQ] applyReplace 路由: replaceRule=" + replaceRule);
                String result = applyReplaceRule(text, replaceRule);
                String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                SpiderDebug.log("[XBPQ] applyReplace 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                return result;
            } else {
                SpiderDebug.log("[XBPQ] applyReplace: 未找到替换规则, 返回原文本");
            }
            
            return text;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyReplace error: rule=" + rule + ", " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * 应用替换规则（单个）
     * 
     * @param text 文本内容
     * @param replaceRule 替换规则（如 "线路1>>腾腾#播放>>空#(*)$空"）
     * @return 替换后的文本
     */
    public static String applyReplaceRule(String text, String replaceRule) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(replaceRule)) {
            return text;
        }
        
        try {
            String[] replacements = replaceRule.split(SEP_HASH);
            
            for (String replacement : replacements) {
                replacement = replacement.trim();
                if (TextUtils.isEmpty(replacement)) continue;
                
                if (replacement.contains(SEP_REPLACE)) {
                    String[] parts = replacement.split(SEP_REPLACE);
                    String oldValue = parts[0];
                    String newValue = parts.length > 1 ? parts[1] : "";
                    
                    // 处理空值标记
                    if (newValue.equals(EMPTY_VALUE)) {
                        newValue = "";
                    }
                    
                    // 处理通配符替换
                    if (oldValue.contains(WILDCARD)) {
                        // 将通配符转换为正则表达式
                        String regex = oldValue.replace(WILDCARD, "(.*?)");
                        if (newValue.contains(WILDCARD)) {
                            // 替换内容也包含通配符，表示保留截取内容
                            newValue = newValue.replace(WILDCARD, "$1");
                        }
                        text = text.replaceAll(regex, newValue);
                    } else {
                        text = text.replace(oldValue, newValue);
                    }
                }
            }
            
            return text;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyReplaceRule error: replaceRule=" + replaceRule + ", " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * 应用数组替换规则
     * 
     * @param array 数组列表
     * @param rule 包含替换规则的完整规则
     * @param startIndex 序号起始值（用于 <序号> 替换）
     * @return 替换后的数组
     */
    public static ArrayList<String> applyArrayReplace(ArrayList<String> array, String rule, int startIndex) {
        if (array == null) {
            SpiderDebug.log("[XBPQ] applyArrayReplace: array为null");
            return new ArrayList<>();
        }
        
        if (array.isEmpty()) return array;
        
        try {
            String replaceRule = extractReplaceRule(rule);
            if (TextUtils.isEmpty(replaceRule)) return array;
            
            ArrayList<String> result = new ArrayList<>();
            
            for (int i = 0; i < array.size(); i++) {
                String item = array.get(i);
                if (item == null) {
                    result.add("");
                    continue;
                }
                
                // 处理 <序号> 替换
                if (replaceRule.contains(SEQ_MARKER)) {
                    String seqReplace = replaceRule.replace(SEQ_MARKER, String.valueOf(startIndex + i));
                    item = applyReplaceRule(item, seqReplace);
                } else {
                    item = applyReplaceRule(item, replaceRule);
                }
                
                result.add(item);
            }
            
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyArrayReplace error: rule=" + rule + ", " + e.getMessage(), e);
            return array;
        }
    }
    
    // ==================== 排序规则 ====================
    
    /**
     * 应用数组排序规则
     *
     * @param array 数组列表
     * @param rule 包含排序规则的完整规则
     * @return 排序后的数组
     */
    public static ArrayList<String> applyArraySort(ArrayList<String> array, String rule) {
        SpiderDebug.log("[XBPQ] applyArraySort 开始: array数量=" + (array != null ? array.size() : 0) + ", rule=" + rule);
        
        if (array == null) {
            SpiderDebug.log("[XBPQ] applyArraySort: array为null");
            return new ArrayList<>();
        }
        
        if (array.isEmpty()) {
            SpiderDebug.log("[XBPQ] applyArraySort: array为空, 返回原数组");
            return array;
        }
        
        if (TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] applyArraySort: rule为空, 返回原数组");
            return array;
        }

        try {
            String sortRule = extractSortRule(rule);
            if (TextUtils.isEmpty(sortRule)) {
                SpiderDebug.log("[XBPQ] applyArraySort: 未找到排序规则, 返回原数组");
                return array;
            }

            SpiderDebug.log("[XBPQ] applyArraySort 路由: sortRule=" + sortRule);
            Matcher matcher = SORT_PATTERN.matcher(sortRule);
            if (matcher.find()) {
                String sortConfig = matcher.group(1);
                
                if (TextUtils.isEmpty(sortConfig)) {
                    SpiderDebug.log("[XBPQ] applyArraySort: sortConfig为空, 返回原数组");
                    return array;
                }

                // 解析排序配置，如 "1080zyk>>腾腾>>优优"
                String[] sortKeywords = sortConfig.split(SEP_REPLACE);
                SpiderDebug.log("[XBPQ] applyArraySort 排序关键词数量=" + sortKeywords.length);
                
                if (sortKeywords == null || sortKeywords.length == 0) {
                    SpiderDebug.log("[XBPQ] applyArraySort: sortKeywords为空, 返回原数组");
                    return array;
                }

                // 创建排序映射：关键词 -> 优先级（越小越靠前）
                final Map<String, Integer> sortMap = new HashMap<>();
                for (int i = 0; i < sortKeywords.length; i++) {
                    String keyword = sortKeywords[i];
                    if (!TextUtils.isEmpty(keyword)) {
                        sortMap.put(keyword, i);
                        SpiderDebug.log("[XBPQ] applyArraySort 排序映射: keyword=" + keyword + ", priority=" + i);
                    }
                }

                // 预先计算每个元素的排序优先级，避免在 Comparator 中重复遍历
                final int[] ranks = new int[array.size()];
                for (int i = 0; i < array.size(); i++) {
                    String item = array.get(i);
                    if (item == null) {
                        ranks[i] = Integer.MAX_VALUE;
                        continue;
                    }
                    
                    int rank = Integer.MAX_VALUE;
                    for (Map.Entry<String, Integer> entry : sortMap.entrySet()) {
                        if (item.contains(entry.getKey())) {
                            rank = entry.getValue();
                            break;
                        }
                    }
                    ranks[i] = rank;
                }

                // 创建索引数组并排序
                final Integer[] indices = new Integer[array.size()];
                for (int i = 0; i < array.size(); i++) {
                    indices[i] = i;
                }

                // 根据预计算的优先级对索引进行排序
                Arrays.sort(indices, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer a, Integer b) {
                        if (a == null || b == null) return 0;
                        return Integer.compare(ranks[a], ranks[b]);
                    }
                });

                // 根据排序后的索引构建结果数组
                ArrayList<String> result = new ArrayList<>(array.size());
                for (int index : indices) {
                    if (index >= 0 && index < array.size()) {
                        result.add(array.get(index));
                    }
                }

                SpiderDebug.log("[XBPQ] applyArraySort 结果: 排序后数量=" + result.size());
                return result;
            }

            SpiderDebug.log("[XBPQ] applyArraySort: 未匹配到排序模式, 返回原数组");
            return array;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyArraySort error: rule=" + rule + ", " + e.getMessage(), e);
            return array;
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 提取过滤规则
     * 
     * @param rule 完整规则
     * @return 过滤规则
     */
    public static String extractFilterRule(String rule) {
        try {
            Matcher matcher = FILTER_PATTERN.matcher(rule);
            if (matcher.find()) {
                return matcher.group(0);
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 提取替换规则
     * 
     * @param rule 完整规则
     * @return 替换规则
     */
    public static String extractReplaceRule(String rule) {
        try {
            Matcher matcher = REPLACE_PATTERN.matcher(rule);
            if (matcher.find()) {
                return matcher.group(0);
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 提取排序规则
     * 
     * @param rule 完整规则
     * @return 排序规则
     */
    public static String extractSortRule(String rule) {
        try {
            Matcher matcher = SORT_PATTERN.matcher(rule);
            if (matcher.find()) {
                return matcher.group(0);
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 提取纯净规则（去除过滤、替换、排序部分）
     * 
     * @param rule 完整规则
     * @return 纯净规则
     */
    public static String extractCleanRule(String rule) {
        try {
            String cleanRule = rule;
            
            // 移除过滤规则
            Matcher filterMatcher = FILTER_PATTERN.matcher(cleanRule);
            if (filterMatcher.find()) {
                cleanRule = cleanRule.replace(filterMatcher.group(0), "");
            }
            
            // 移除替换规则
            Matcher replaceMatcher = REPLACE_PATTERN.matcher(cleanRule);
            if (replaceMatcher.find()) {
                cleanRule = cleanRule.replace(replaceMatcher.group(0), "");
            }
            
            // 移除排序规则
            Matcher sortMatcher = SORT_PATTERN.matcher(cleanRule);
            if (sortMatcher.find()) {
                cleanRule = cleanRule.replace(sortMatcher.group(0), "");
            }
            
            return cleanRule.trim();
        } catch (Exception e) {
            return rule;
        }
    }
}