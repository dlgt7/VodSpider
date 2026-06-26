package com.github.catvod.spider.xbpq;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQStringExtractor - 字符串截取解析器
 * 
 * <p>负责处理字符串截取相关的解析逻辑，包括：</p>
 * <ul>
 *   <li>基本字符串截取（&& 和 $$ 分隔符）</li>
 *   <li>通配符截取</li>
 *   <li>数组截取</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQStringExtractor {

    // ==================== 常量定义 ====================
    
    /** 分隔符：截取前后连接符 */
    private static final String SEP_AND = "&&";
    
    /** 分隔符：截取前后连接符（备用） */
    private static final String SEP_DOLLAR = "$$";
    
    /** 通配符 */
    private static final String WILDCARD = "*";
    
    // ==================== 截取方法 ====================
    
    /**
     * 使用字符串截取解析 HTML
     * 
     * @param html HTML 内容
     * @param rule 解析规则
     * @return 解析结果
     */
    public static String extract(String html, String rule) {
        SpiderDebug.log("[XBPQ] extract 开始: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
        
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] extract: 参数为空, 返回空字符串");
            return "";
        }
        
        try {
            // 处理 && 分隔符
            if (rule.contains(SEP_AND)) {
                String[] parts = rule.split(SEP_AND);
                String startMarker = parts[0];
                String endMarker = parts.length > 1 ? parts[1] : "";
                SpiderDebug.log("[XBPQ] extract 路由: 类型=&&分隔符, startMarker=" + startMarker + ", endMarker=" + endMarker);
                
                // 处理通配符
                if (startMarker.contains(WILDCARD)) {
                    SpiderDebug.log("[XBPQ] extract 处理: startMarker包含通配符");
                    startMarker = handleWildcardStart(html, startMarker);
                }
                if (endMarker.contains(WILDCARD)) {
                    SpiderDebug.log("[XBPQ] extract 处理: endMarker包含通配符");
                    endMarker = handleWildcardEnd(html, endMarker);
                }
                
                // 执行截取
                String result = extractBetween(html, startMarker, endMarker);
                String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                SpiderDebug.log("[XBPQ] extract 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                return result;
            }
            
            // 处理 $$ 分隔符
            if (rule.contains(SEP_DOLLAR)) {
                String[] parts = rule.split(SEP_DOLLAR);
                String startMarker = parts[0];
                String endMarker = parts.length > 1 ? parts[1] : "";
                SpiderDebug.log("[XBPQ] extract 路由: 类型=$$分隔符, startMarker=" + startMarker + ", endMarker=" + endMarker);
                
                String result = extractBetween(html, startMarker, endMarker);
                String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                SpiderDebug.log("[XBPQ] extract 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                return result;
            }
            
            // 没有分隔符，直接返回规则作为固定值
            SpiderDebug.log("[XBPQ] extract 路由: 类型=无分隔符, 返回规则作为固定值");
            SpiderDebug.log("[XBPQ] extract 结果: 结果=" + rule);
            return rule;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] extract error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 使用字符串截取解析数组
     * 
     * @param html HTML 内容
     * @param rule 解析规则
     * @return 解析结果数组
     */
    public static ArrayList<String> extractArray(String html, String rule) {
        SpiderDebug.log("[XBPQ] extractArray 开始: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
        
        ArrayList<String> result = new ArrayList<>();
        
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] extractArray: 参数为空, 返回空列表");
            return result;
        }
        
        try {
            if (rule.contains(SEP_AND)) {
                String[] parts = rule.split(SEP_AND);
                String startMarker = parts[0];
                String endMarker = parts.length > 1 ? parts[1] : "";
                SpiderDebug.log("[XBPQ] extractArray 路由: 类型=&&分隔符, startMarker=" + startMarker + ", endMarker=" + endMarker);
                
                // 处理通配符
                if (startMarker.contains(WILDCARD)) {
                    SpiderDebug.log("[XBPQ] extractArray 处理: startMarker包含通配符, 使用正则匹配");
                    // 使用正则匹配
                    result = extractAllWithWildcard(html, startMarker, endMarker);
                } else {
                    SpiderDebug.log("[XBPQ] extractArray 处理: 使用普通截取");
                    // 使用普通截取
                    result = extractAllBetween(html, startMarker, endMarker);
                }
            } else if (rule.contains(SEP_DOLLAR)) {
                String[] parts = rule.split(SEP_DOLLAR);
                String startMarker = parts[0];
                String endMarker = parts.length > 1 ? parts[1] : "";
                SpiderDebug.log("[XBPQ] extractArray 路由: 类型=$$分隔符, startMarker=" + startMarker + ", endMarker=" + endMarker);
                
                result = extractAllBetween(html, startMarker, endMarker);
            } else {
                SpiderDebug.log("[XBPQ] extractArray: 规则不包含分隔符, 返回空列表");
            }
            
            SpiderDebug.log("[XBPQ] extractArray 结果: 结果数量=" + result.size());
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] extractArray error: rule=" + rule + ", " + e.getMessage(), e);
            return result;
        }
    }
    
    /**
     * 截取两个标记之间的内容
     * 
     * @param content 内容字符串
     * @param start 开始标记
     * @param end 结束标记
     * @return 截取的内容
     */
    private static String extractBetween(String content, String start, String end) {
        try {
            int startIndex = content.indexOf(start);
            if (startIndex < 0) return "";
            
            startIndex += start.length();
            
            if (TextUtils.isEmpty(end)) {
                return content.substring(startIndex);
            }
            
            int endIndex = content.indexOf(end, startIndex);
            if (endIndex < 0) return "";
            
            return content.substring(startIndex, endIndex);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 截取所有两个标记之间的内容
     * 
     * @param content 内容字符串
     * @param start 开始标记
     * @param end 结束标记
     * @return 截取的内容列表
     */
    private static ArrayList<String> extractAllBetween(String content, String start, String end) {
        ArrayList<String> result = new ArrayList<>();
        
        try {
            int pos = 0;
            while (pos < content.length()) {
                int startIndex = content.indexOf(start, pos);
                if (startIndex < 0) break;
                
                startIndex += start.length();
                
                if (TextUtils.isEmpty(end)) {
                    result.add(content.substring(startIndex));
                    break;
                }
                
                int endIndex = content.indexOf(end, startIndex);
                if (endIndex < 0) break;
                
                result.add(content.substring(startIndex, endIndex));
                pos = endIndex + end.length();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return result;
    }
    
    /**
     * 使用通配符截取所有内容
     * 
     * @param content 内容字符串
     * @param startPattern 开始模式（包含通配符）
     * @param endPattern 结束模式
     * @return 截取的内容列表
     */
    private static ArrayList<String> extractAllWithWildcard(String content, String startPattern, String endPattern) {
        ArrayList<String> result = new ArrayList<>();
        
        try {
            // 将通配符转换为正则表达式
            String regexStart = startPattern.replace(WILDCARD, "(.*?)");
            String regexEnd = TextUtils.isEmpty(endPattern) ? "" : endPattern;
            
            // 构建正则表达式
            String regex = regexStart + "(.*?)";
            if (!TextUtils.isEmpty(regexEnd)) {
                regex = regexStart + "(.*?)(" + regexEnd + ")";
            }
            
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);
            
            while (matcher.find()) {
                if (matcher.groupCount() > 0) {
                    result.add(matcher.group(1));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return result;
    }
    
    /**
     * 处理开始标记中的通配符
     * 
     * @param content 内容字符串
     * @param startMarker 开始标记
     * @return 处理后的开始标记
     */
    private static String handleWildcardStart(String content, String startMarker) {
        try {
            // 将通配符转换为正则表达式
            String regex = startMarker.replace(WILDCARD, "(.*?)");
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                return matcher.group(0);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return startMarker;
    }
    
    /**
     * 处理结束标记中的通配符
     * 
     * @param content 内容字符串
     * @param endMarker 结束标记
     * @return 处理后的结束标记
     */
    private static String handleWildcardEnd(String content, String endMarker) {
        try {
            // 将通配符转换为正则表达式
            String regex = endMarker.replace(WILDCARD, "(.*?)");
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                return matcher.group(0);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return endMarker;
    }
}