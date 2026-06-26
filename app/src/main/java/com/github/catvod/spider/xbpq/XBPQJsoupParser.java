package com.github.catvod.spider.xbpq;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;

/**
 * XBPQJsoupParser - Jsoup 选择器解析器
 * 
 * <p>负责处理 Jsoup 选择器相关的解析逻辑，包括：</p>
 * <ul>
 *   <li>CSS 选择器解析</li>
 *   <li>属性提取</li>
 *   <li>文本提取</li>
 *   <li>HTML 提取</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQJsoupParser {

    // ==================== 常量定义 ====================
    
    /** 分隔符：截取前后连接符 */
    private static final String SEP_AND = "&&";
    
    /** 文本属性 */
    private static final String ATTR_TEXT = "text";
    
    /** HTML 属性 */
    private static final String ATTR_HTML = "html";
    
    /** 外部 HTML 属性 */
    private static final String ATTR_OUTER_HTML = "outerHtml";
    
    // ==================== 解析方法 ====================
    
    /**
     * 判断是否为 Jsoup 选择器
     * 
     * @param rule 解析规则
     * @return 是否为 Jsoup 选择器
     */
    public static boolean isJsoupSelector(String rule) {
        if (TextUtils.isEmpty(rule)) return false;
        
        // Jsoup 选择器特征：包含 && 且第一部分是 CSS 选择器
        if (rule.contains(SEP_AND)) {
            String selector = rule.split(SEP_AND)[0];
            // CSS 选择器特征：包含 .class 或 #id 或 tag 或 [attr]
            if (selector.matches(".*[.#\\[\\]].*") || 
                selector.matches("^[a-zA-Z][a-zA-Z0-9]*$") ||
                selector.contains(" ") ||
                selector.contains(">") ||
                selector.contains(":")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 使用 Jsoup 解析 HTML
     * 
     * @param html HTML 内容
     * @param rule 解析规则（如 "img&&src"）
     * @return 解析结果
     */
    public static String parse(String html, String rule) {
        SpiderDebug.log("[XBPQ] Jsoup.parse 开始: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
        
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] Jsoup.parse: 参数为空, 返回空字符串");
            return "";
        }
        
        try {
            Document doc = Jsoup.parse(html);
            
            if (rule.contains(SEP_AND)) {
                String[] parts = rule.split(SEP_AND);
                String selector = parts[0];
                String attrOrText = parts.length > 1 ? parts[1] : ATTR_TEXT;
                SpiderDebug.log("[XBPQ] Jsoup.parse 路由: 类型=属性提取, selector=" + selector + ", attrOrText=" + attrOrText);
                
                Elements elements = doc.select(selector);
                SpiderDebug.log("[XBPQ] Jsoup.parse 选择结果: elements数量=" + elements.size());
                
                if (!elements.isEmpty()) {
                    Element element = elements.first();
                    String result = extractValue(element, attrOrText);
                    String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                    SpiderDebug.log("[XBPQ] Jsoup.parse 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                    return result;
                }
                SpiderDebug.log("[XBPQ] Jsoup.parse: 未找到匹配元素, 返回空字符串");
            } else {
                SpiderDebug.log("[XBPQ] Jsoup.parse 路由: 类型=元素提取, selector=" + rule);
                // 单独的选择器，获取第一个元素
                Elements elements = doc.select(rule);
                SpiderDebug.log("[XBPQ] Jsoup.parse 选择结果: elements数量=" + elements.size());
                
                if (!elements.isEmpty()) {
                    String result = elements.first().outerHtml();
                    String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                    SpiderDebug.log("[XBPQ] Jsoup.parse 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                    return result;
                }
                SpiderDebug.log("[XBPQ] Jsoup.parse: 未找到匹配元素, 返回空字符串");
            }
            
            return "";
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] Jsoup.parse error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 使用 Jsoup 解析数组
     * 
     * @param html HTML 内容
     * @param rule 解析规则
     * @return 解析结果数组
     */
    public static ArrayList<String> parseArray(String html, String rule) {
        SpiderDebug.log("[XBPQ] Jsoup.parseArray 开始: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
        
        ArrayList<String> result = new ArrayList<>();
        
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] Jsoup.parseArray: 参数为空, 返回空列表");
            return result;
        }
        
        try {
            Document doc = Jsoup.parse(html);
            
            if (rule.contains(SEP_AND)) {
                String[] parts = rule.split(SEP_AND);
                String selector = parts[0];
                String attrOrText = parts.length > 1 ? parts[1] : ATTR_TEXT;
                SpiderDebug.log("[XBPQ] Jsoup.parseArray 路由: 类型=属性提取, selector=" + selector + ", attrOrText=" + attrOrText);
                
                Elements elements = doc.select(selector);
                SpiderDebug.log("[XBPQ] Jsoup.parseArray 选择结果: elements数量=" + elements.size());
                
                for (Element element : elements) {
                    String value = extractValue(element, attrOrText);
                    result.add(value);
                }
            } else {
                SpiderDebug.log("[XBPQ] Jsoup.parseArray 路由: 类型=元素提取, selector=" + rule);
                // 单独的选择器，获取整个元素
                Elements elements = doc.select(rule);
                SpiderDebug.log("[XBPQ] Jsoup.parseArray 选择结果: elements数量=" + elements.size());
                
                for (Element element : elements) {
                    result.add(element.outerHtml());
                }
            }
            
            SpiderDebug.log("[XBPQ] Jsoup.parseArray 结果: 结果数量=" + result.size());
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] Jsoup.parseArray error: rule=" + rule + ", " + e.getMessage(), e);
            return result;
        }
    }
    
    /**
     * 从元素中提取值
     * 
     * @param element HTML 元素
     * @param attrOrText 属性名或文本标记
     * @return 提取的值
     */
    private static String extractValue(Element element, String attrOrText) {
        if (element == null) return "";
        
        try {
            // 判断是获取属性还是文本
            if (attrOrText.equalsIgnoreCase(ATTR_TEXT)) {
                return element.text();
            } else if (attrOrText.equalsIgnoreCase(ATTR_HTML)) {
                return element.html();
            } else if (attrOrText.equalsIgnoreCase(ATTR_OUTER_HTML)) {
                return element.outerHtml();
            } else {
                return element.attr(attrOrText);
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] extractValue error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 解析属性
     * 
     * @param html HTML 内容
     * @param selector CSS 选择器
     * @param attr 属性名
     * @return 属性值
     */
    public static String parseAttribute(String html, String selector, String attr) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(selector)) {
            return "";
        }
        
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(selector);
            
            if (!elements.isEmpty()) {
                Element element = elements.first();
                return element.attr(attr);
            }
            
            return "";
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseAttribute error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 解析文本
     * 
     * @param html HTML 内容
     * @param selector CSS 选择器
     * @return 文本内容
     */
    public static String parseText(String html, String selector) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(selector)) {
            return "";
        }
        
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(selector);
            
            if (!elements.isEmpty()) {
                return elements.first().text();
            }
            
            return "";
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseText error: " + e.getMessage(), e);
            return "";
        }
    }
}