package com.github.catvod.spider.xbpq;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;

// 委托给 XBPQJsonParser 处理 JSON 解析

/**
 * XBPQParser - HTML 解析规则引擎（纯路由器）
 * 
 * <p>该类作为解析入口和路由分发器，支持多种解析方式：</p>
 * <ul>
 *   <li>Jsoup 选择器写法（如 "img&&src"）</li>
 *   <li>字符串截取写法（如 "a&&b" 或 "data-src=\"&&\""）</li>
 *   <li>JSON 模式解析</li>
 *   <li>Base64 解码</li>
 *   <li>拼接规则（+）</li>
 *   <li>指定截取（||）</li>
 * </ul>
 * 
 * <p>所有解析逻辑都委托给辅助类实现。</p>
 * 
 * @version 3.0
 * @since 2024
 */
public class XBPQParser {

    // ==================== 静态变量 ====================
    
    /** 调试模式标志（由 XBPQ 主类设置） */
    private static boolean debugMode = false;
    
    // ==================== 构造方法 ====================
    
    public XBPQParser() {
    }
    
    /**
     * 设置调试模式
     * 
     * @param mode 调试模式标志
     */
    public static void setDebugMode(boolean mode) {
        debugMode = mode;
    }

    // ==================== HTML 解析核心方法（路由分发） ====================
    
    /**
     * 解析 HTML 内容
     * 
     * <p>根据规则类型进行路由分发，委托给相应的辅助类处理。</p>
     * 
     * @param html HTML 内容字符串
     * @param rule 解析规则
     * @return 解析结果字符串
     */
    public static String parseHtml(String html, String rule) {
        if (debugMode) {
            SpiderDebug.log("[XBPQ] parseHtml 开始: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
        }
        
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseHtml: 参数为空");
            }
            return "";
        }
        
        // 处理转义符
        rule = unescapeRule(rule);
        
        try {
            // 路由分发：拼接规则
            if (rule.contains("+")) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseHtml 路由: 规则类型=拼接规则");
                }
                return parseWithConcat(html, rule);
            }
            
            // 路由分发：指定截取
            if (rule.contains("||")) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseHtml 路由: 规则类型=指定截取");
                }
                return parseWithSpecificRule(html, rule, "");
            }
            
            // 路由分发：Base64
            if (rule.startsWith("Base64(") || rule.equals("Base64")) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseHtml 路由: 规则类型=Base64解码");
                }
                return parseWithBase64(html, rule);
            }
            
            // 路由分发：JSON 模式
            if (isJsonMode(rule)) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseHtml 路由: 规则类型=JSON模式");
                }
                return parseJson(html, rule);
            }
            
            // 提取纯净规则
            String cleanRule = XBPQRuleApplier.extractCleanRule(rule);
            
            // 路由分发：Jsoup 选择器
            if (XBPQJsoupParser.isJsoupSelector(rule)) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseHtml 路由: 规则类型=Jsoup选择器, cleanRule=" + cleanRule);
                }
                String result = XBPQJsoupParser.parse(html, cleanRule);
                result = XBPQRuleApplier.applyReplace(result, rule);
                result = XBPQRuleApplier.applyFilter(result, rule);
                if (debugMode) {
                    String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                    SpiderDebug.log("[XBPQ] parseHtml 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                }
                return result;
            }
            
            // 路由分发：字符串截取
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseHtml 路由: 规则类型=字符串截取, cleanRule=" + cleanRule);
            }
            String result = XBPQStringExtractor.extract(html, cleanRule);
            result = XBPQRuleApplier.applyReplace(result, rule);
            result = XBPQRuleApplier.applyFilter(result, rule);
            if (debugMode) {
                String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                SpiderDebug.log("[XBPQ] parseHtml 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseHtml error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 解析数组
     * 
     * <p>根据规则类型进行路由分发，委托给相应的辅助类处理。</p>
     * 
     * @param html HTML 内容字符串
     * @param rule 解析规则
     * @return 解析结果列表
     */
    public static ArrayList<String> parseArray(String html, String rule) {
        if (debugMode) {
            SpiderDebug.log("[XBPQ] parseArray 开始: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
        }
        
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseArray: 参数为空");
            }
            return new ArrayList<>();
        }
        
        // 处理转义符
        rule = unescapeRule(rule);
        
        try {
            // 处理二次截取
            if (rule.contains("&&") && rule.indexOf("&&") != rule.lastIndexOf("&&")) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseArray 路由: 规则类型=二次截取");
                }
                String[] parts = splitByLastSep(rule, "&&");
                if (parts.length == 2) {
                    rule = parts[0];
                    html = parseHtml(html, rule);
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ] parseArray 二次截取后: html长度=" + (html != null ? html.length() : 0) + ", rule=" + rule);
                    }
                }
            }
            
            // 提取纯净规则
            String cleanRule = XBPQRuleApplier.extractCleanRule(rule);
            
            // 路由分发：Jsoup 选择器
            if (XBPQJsoupParser.isJsoupSelector(rule)) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseArray 路由: 规则类型=Jsoup选择器, cleanRule=" + cleanRule);
                }
                ArrayList<String> result = XBPQJsoupParser.parseArray(html, cleanRule);
                result = XBPQRuleApplier.applyArrayFilter(result, rule);
                result = XBPQRuleApplier.applyArraySort(result, rule);
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseArray 结果: 结果数量=" + (result != null ? result.size() : 0));
                }
                return result;
            }
            
            // 路由分发：字符串截取
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseArray 路由: 规则类型=字符串截取, cleanRule=" + cleanRule);
            }
            ArrayList<String> result = XBPQStringExtractor.extractArray(html, cleanRule);
            result = XBPQRuleApplier.applyArrayFilter(result, rule);
            result = XBPQRuleApplier.applyArraySort(result, rule);
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseArray 结果: 结果数量=" + (result != null ? result.size() : 0));
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseArray error: rule=" + rule + ", " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // ==================== 规则应用方法（委托） ====================
    
    /**
     * 应用替换规则
     * 
     * <p>委托给 XBPQRuleApplier 实现替换逻辑。</p>
     * 
     * @param text 文本内容
     * @param rule 包含替换规则的完整规则
     * @return 替换后的文本
     */
    public static String applyReplace(String text, String rule) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(rule)) {
            return text;
        }
        
        try {
            return XBPQRuleApplier.applyReplace(text, rule);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyReplace error: " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * 应用过滤规则
     * 
     * <p>委托给 XBPQRuleApplier 实现过滤逻辑。</p>
     * 
     * @param text 文本内容
     * @param rule 包含过滤规则的完整规则
     * @return 过滤后的文本（如果不符合过滤条件则返回空字符串）
     */
    public static String applyFilter(String text, String rule) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(rule)) {
            return text;
        }
        
        try {
            return XBPQRuleApplier.applyFilter(text, rule);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyFilter error: " + e.getMessage(), e);
            return text;
        }
    }
    
    /**
     * 应用数组排序规则
     * 
     * <p>委托给 XBPQRuleApplier 实现排序逻辑。</p>
     * 
     * @param array 数组列表
     * @param rule 包含排序规则的完整规则
     * @return 排序后的数组
     */
    public static ArrayList<String> applyArraySort(ArrayList<String> array, String rule) {
        if (array == null || array.isEmpty() || TextUtils.isEmpty(rule)) {
            return array;
        }
        
        try {
            return XBPQRuleApplier.applyArraySort(array, rule);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] applyArraySort error: " + e.getMessage(), e);
            return array;
        }
    }
    
    /**
     * 解析文本
     * 
     * <p>从 HTML 内容中提取文本内容。</p>
     * 
     * @param html HTML 内容字符串
     * @param rule 解析规则
     * @return 解析结果文本
     */
    public static String parseText(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] parseText: 参数为空");
            return "";
        }
        
        try {
            String result = parseHtml(html, rule);
            result = XBPQRuleApplier.applyReplace(result, rule);
            result = XBPQRuleApplier.applyFilter(result, rule);
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseText error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 解析属性
     * 
     * <p>从 HTML 元素中提取指定属性值。</p>
     * 
     * @param html HTML 内容字符串
     * @param rule 解析规则（如 "img&&src"）
     * @return 解析结果属性值
     */
    public static String parseAttribute(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] parseAttribute: 参数为空");
            return "";
        }
        
        try {
            // 处理转义符
            rule = unescapeRule(rule);
            
            // 提取纯净规则
            String cleanRule = XBPQRuleApplier.extractCleanRule(rule);
            
            // 处理 Jsoup 属性选择器
            if (XBPQJsoupParser.isJsoupSelector(rule)) {
                if (cleanRule.contains("&&")) {
                    String[] parts = cleanRule.split("&&");
                    if (parts.length >= 2) {
                        return XBPQJsoupParser.parseAttribute(html, parts[0], parts[1]);
                    }
                }
            }
            
            // 处理字符串截取属性
            return XBPQStringExtractor.extract(html, cleanRule);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseAttribute error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }

    // ==================== 拼接规则方法 ====================
    
    /**
     * 使用拼接规则解析
     * 
     * @param html HTML 内容
     * @param rule 拼接规则（如 "/play/+/vod/&&.html+-1-1.html"）
     * @return 拼接后的结果
     */
    private static String parseWithConcat(String html, String rule) {
        if (debugMode) {
            SpiderDebug.log("[XBPQ] parseWithConcat 开始: rule=" + rule);
        }
        try {
            StringBuilder result = new StringBuilder();
            String[] parts = rule.split("\\+");
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseWithConcat 拼接部分数量=" + parts.length);
            }
            
            for (String part : parts) {
                part = part.trim();
                if (TextUtils.isEmpty(part)) continue;
                
                // 判断是固定字符串还是解析规则
                if (part.contains("&&") || part.contains("$$")) {
                    // 解析规则
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ] parseWithConcat 处理: 类型=解析规则, part=" + part);
                    }
                    String parsed = parseHtml(html, part);
                    result.append(parsed);
                } else if (part.startsWith("'") && part.endsWith("'")) {
                    // JSON 模式的固定字符串（单引号包裹）
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ] parseWithConcat 处理: 类型=固定字符串(单引号), part=" + part);
                    }
                    result.append(part.substring(1, part.length() - 1));
                } else {
                    // 固定字符串
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ] parseWithConcat 处理: 类型=固定字符串, part=" + part);
                    }
                    result.append(part);
                }
            }
            
            String finalResult = result.toString();
            if (debugMode) {
                String preview = finalResult != null && finalResult.length() > 100 ? finalResult.substring(0, 100) : finalResult;
                SpiderDebug.log("[XBPQ] parseWithConcat 结果: 结果长度=" + (finalResult != null ? finalResult.length() : 0) + ", 结果预览=" + preview);
            }
            return finalResult;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseWithConcat error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }

    // ==================== 指定截取方法 ====================
    
    /**
     * 使用指定截取规则解析
     * 
     * @param html HTML 内容
     * @param rule 指定截取规则（如 "默认--a&&b||连续剧--c&&d||首页--e&&f"）
     * @param cateId 分类 ID
     * @return 解析结果
     */
    private static String parseWithSpecificRule(String html, String rule, String cateId) {
        if (debugMode) {
            SpiderDebug.log("[XBPQ] parseWithSpecificRule 开始: rule=" + rule + ", cateId=" + cateId);
        }
        try {
            String[] rules = rule.split("\\|\\|");
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseWithSpecificRule 规则数量=" + rules.length);
            }
            
            // 默认规则
            String defaultRule = "";
            String matchedRule = "";
            
            for (String r : rules) {
                r = r.trim();
                if (TextUtils.isEmpty(r)) continue;
                
                if (r.contains("--")) {
                    String[] parts = r.split("--");
                    String category = parts[0];
                    String actualRule = parts.length > 1 ? parts[1] : "";
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ] parseWithSpecificRule 解析: category=" + category + ", actualRule=" + actualRule);
                    }
                    
                    if (category.equals("默认")) {
                        defaultRule = actualRule;
                    }
                    
                    if (!TextUtils.isEmpty(cateId) && category.equals(cateId)) {
                        matchedRule = actualRule;
                        if (debugMode) {
                            SpiderDebug.log("[XBPQ] parseWithSpecificRule 匹配: cateId=" + cateId + ", matchedRule=" + matchedRule);
                        }
                        break;
                    }
                } else {
                    // 没有 -- 分隔符，作为默认规则
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ] parseWithSpecificRule 解析: 无分隔符, 作为默认规则=" + r);
                    }
                    defaultRule = r;
                }
            }
            
            // 使用匹配的规则或默认规则
            String finalRule = TextUtils.isEmpty(matchedRule) ? defaultRule : matchedRule;
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseWithSpecificRule 最终规则: finalRule=" + finalRule);
            }
            
            String result = parseHtml(html, finalRule);
            if (debugMode) {
                String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                SpiderDebug.log("[XBPQ] parseWithSpecificRule 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseWithSpecificRule error: rule=" + rule + ", cateId=" + cateId + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 使用指定截取规则解析（带分类名称）
     * 
     * @param html HTML 内容
     * @param rule 指定截取规则
     * @param cateName 分类名称
     * @return 解析结果
     */
    public static String parseWithCategory(String html, String rule, String cateName) {
        return parseWithSpecificRule(html, rule, cateName);
    }

    // ==================== JSON 模式解析方法 ====================
    
    /**
     * 判断是否为 JSON 模式
     * 
     * <p>委托给 XBPQJsonParser 实现。</p>
     * 
     * @param rule 解析规则
     * @return 是否为 JSON 模式
     */
    private static boolean isJsonMode(String rule) {
        return XBPQJsonParser.isJsonMode(rule);
    }
    
    /**
     * 解析 JSON 数据
     * 
     * <p>委托给 XBPQJsonParser 实现。</p>
     * 
     * @param json JSON 字符串
     * @param rule JSON 解析规则（如 "data.list[1].name"）
     * @return 解析结果
     */
    private static String parseJson(String json, String rule) {
        return XBPQJsonParser.parseJson(json, rule);
    }
    
    /**
     * 解析 JSON 数组
     * 
     * <p>委托给 XBPQJsonParser 实现。</p>
     * 
     * @param json JSON 字符串
     * @param rule JSON 解析规则
     * @return 解析结果数组
     */
    public static ArrayList<String> parseJsonArray(String json, String rule) {
        return XBPQJsonParser.parseJsonArray(json, rule);
    }

    // ==================== Base64 解码方法 ====================
    
    /**
     * 使用 Base64 解码解析
     * 
     * @param html HTML 内容
     * @param rule Base64 规则（如 "Base64(a&&b)"）
     * @return 解析结果
     */
    private static String parseWithBase64(String html, String rule) {
        if (debugMode) {
            SpiderDebug.log("[XBPQ] parseWithBase64 开始: rule=" + rule);
        }
        try {
            if (TextUtils.isEmpty(html)) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseWithBase64: html为空");
                }
                return "";
            }
            
            // 提取 Base64 内部的规则
            if (rule.startsWith("Base64(") && rule.endsWith(")")) {
                String innerRule = rule.substring(7, rule.length() - 1);
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseWithBase64 路由: 类型=带内部规则, innerRule=" + innerRule);
                }
                
                // 先解析内容
                String content = parseHtml(html, innerRule);
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseWithBase64 解析内容: content长度=" + (content != null ? content.length() : 0));
                }
                
                // 再进行 Base64 解码（使用 XBPQUtils 的方法）
                String result = XBPQUtils.decodeBase64(content);
                if (debugMode) {
                    String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                    SpiderDebug.log("[XBPQ] parseWithBase64 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                }
                return result;
            } else if (rule.equals("Base64")) {
                if (debugMode) {
                    SpiderDebug.log("[XBPQ] parseWithBase64 路由: 类型=直接解码");
                }
                // 整个 HTML 是 Base64 编码（使用 XBPQUtils 的方法）
                String result = XBPQUtils.decodeBase64(html);
                if (debugMode) {
                    String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
                    SpiderDebug.log("[XBPQ] parseWithBase64 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
                }
                return result;
            }
            
            if (debugMode) {
                SpiderDebug.log("[XBPQ] parseWithBase64: 无法识别的Base64规则格式");
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseWithBase64 error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 处理转义符
     * 
     * <p>XBPQ 使用到的连接符（$ # & * [ ]）用于表示本义的时候，需要用 \\ 转义。</p>
     * 
     * @param rule 规则字符串
     * @return 处理后的规则
     */
    private static String unescapeRule(String rule) {
        if (TextUtils.isEmpty(rule)) return rule;
        
        try {
            // 处理转义的连接符
            rule = rule.replace("\\#", "#");
            rule = rule.replace("\\$", "$");
            rule = rule.replace("\\&", "&");
            rule = rule.replace("\\*", "*");
            rule = rule.replace("\\[", "[");
            rule = rule.replace("\\]", "]");
            rule = rule.replace("\\|", "|");
            rule = rule.replace("\\+", "+");
            rule = rule.replace("\\-", "-");
            
            return rule;
        } catch (Exception e) {
            return rule;
        }
    }
    
    /**
     * 按最后一个分隔符分割字符串
     * 
     * @param str 字符串
     * @param sep 分隔符
     * @return 分割后的数组
     */
    private static String[] splitByLastSep(String str, String sep) {
        try {
            int lastSepIndex = str.lastIndexOf(sep);
            if (lastSepIndex < 0) {
                return new String[]{str};
            }
            
            String firstPart = str.substring(0, lastSepIndex);
            String secondPart = str.substring(lastSepIndex + sep.length());
            
            return new String[]{firstPart, secondPart};
        } catch (Exception e) {
            return new String[]{str};
        }
    }
}