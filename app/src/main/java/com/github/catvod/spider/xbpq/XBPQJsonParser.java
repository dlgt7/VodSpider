package com.github.catvod.spider.xbpq;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQJsonParser - JSON 解析器
 * 
 * <p>负责处理 JSON 格式数据的解析，支持：</p>
 * <ul>
 *   <li>JSON 对象解析（如 data.list[1].name）</li>
 *   <li>JSON 数组解析（如 data.list[]）</li>
 *   <li>JSON 遍历和提取</li>
 *   <li>JSON 路径表达式解析</li>
 * </ul>
 * 
 * <p>JSON 路径语法说明：</p>
 * <ul>
 *   <li>点号分隔字段：data.list 表示获取 data 对象下的 list 字段</li>
 *   <li>数组下标：list[1] 表示获取数组第 1 个元素（从 1 开始）</li>
 *   <li>数组切片：list[1,] 表示从第 1 个元素开始获取到末尾</li>
 *   <li>整个数组：list[] 表示获取整个数组</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQJsonParser {

    // ==================== 正则表达式规则 ====================
    
    /**
     * JSON 数组下标匹配规则
     * 
     * <p>匹配格式：</p>
     * <ul>
     *   <li>[] - 整个数组</li>
     *   <li>[n] - 第 n 个元素</li>
     *   <li>[n,] - 从第 n 个元素开始到末尾</li>
     * </ul>
     */
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[(\\d*)(,)?\\]");

    // ==================== JSON 模式判断方法 ====================
    
    /**
     * 判断是否为 JSON 模式
     * 
     * <p>JSON 模式的判断规则：</p>
     * <ul>
     *   <li>规则包含点号（.）或数组下标（[]）</li>
     *   <li>规则不包含 HTML 解析分隔符（&& 或 $$）</li>
     * </ul>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>data.list - 是 JSON 模式</li>
     *   <li>data.list[1].name - 是 JSON 模式</li>
     *   <li>div&&a - 不是 JSON 模式（包含 &&）</li>
     *   <li>div$$a - 不是 JSON 模式（包含 $$）</li>
     * </ul>
     * 
     * @param rule 解析规则
     * @return 是否为 JSON 模式
     */
    public static boolean isJsonMode(String rule) {
        if (TextUtils.isEmpty(rule)) {
            return false;
        }
        
        // JSON 模式特征：包含 . 或 [] 且不包含 && 或 $$
        if (rule.contains(".") || (rule.contains("[") && rule.contains("]"))) {
            if (!rule.contains("&&") && !rule.contains("$$")) {
                return true;
            }
        }
        
        return false;
    }

    // ==================== JSON 解析核心方法 ====================
    
    /**
     * 解析 JSON 数据
     * 
     * <p>根据 JSON 路径规则从 JSON 字符串中提取数据。</p>
     * 
     * <p>路径语法示例：</p>
     * <ul>
     *   <li>data - 获取 data 字段</li>
     *   <li>data.list - 获取 data 对象下的 list 字段</li>
     *   <li>data.list[1] - 获取 list 数组的第 1 个元素</li>
     *   <li>data.list[1].name - 获取 list 数组第 1 个元素的 name 字段</li>
     *   <li>data.list[] - 获取整个 list 数组</li>
     *   <li>data.list[2,] - 获取 list 数组从第 2 个元素开始到末尾</li>
     * </ul>
     * 
     * @param jsonStr JSON 字符串
     * @param rule JSON 解析规则（如 "data.list[1].name"）
     * @return 解析结果字符串，如果解析失败返回空字符串
     */
    public static String parseJson(String jsonStr, String rule) {
        SpiderDebug.log("[XBPQ] parseJson 开始: jsonStr长度=" + (jsonStr != null ? jsonStr.length() : 0) + ", rule=" + rule);
        
        try {
            if (TextUtils.isEmpty(jsonStr)) {
                SpiderDebug.log("[XBPQ] parseJson: jsonStr为空, 返回空字符串");
                return "";
            }
            
            // 尝试解析 JSON
            Object jsonObj;
            if (jsonStr.trim().startsWith("{")) {
                SpiderDebug.log("[XBPQ] parseJson 路由: 类型=JSONObject");
                jsonObj = new JSONObject(jsonStr);
            } else if (jsonStr.trim().startsWith("[")) {
                SpiderDebug.log("[XBPQ] parseJson 路由: 类型=JSONArray");
                jsonObj = new JSONArray(jsonStr);
            } else {
                SpiderDebug.log("[XBPQ] parseJson: 无法识别JSON格式, 返回空字符串");
                return "";
            }
            
            // 解析 JSON 路径
            String[] paths = rule.split("\\.");
            SpiderDebug.log("[XBPQ] parseJson 路径数量=" + paths.length);
            
            for (String path : paths) {
                path = path.trim();
                if (TextUtils.isEmpty(path)) {
                    continue;
                }
                
                // 处理数组下标
                Matcher arrayMatcher = JSON_ARRAY_PATTERN.matcher(path);
                if (arrayMatcher.find()) {
                    SpiderDebug.log("[XBPQ] parseJson 处理: 类型=数组下标, path=" + path);
                    jsonObj = traverseJsonArray(jsonObj, path, arrayMatcher);
                } else {
                    SpiderDebug.log("[XBPQ] parseJson 处理: 类型=普通字段, path=" + path);
                    // 普通字段
                    jsonObj = traverseJson(jsonObj, path);
                }
            }
            
            // 返回最终结果
            String result = convertJsonToString(jsonObj);
            String preview = result != null && result.length() > 100 ? result.substring(0, 100) : result;
            SpiderDebug.log("[XBPQ] parseJson 结果: 结果长度=" + (result != null ? result.length() : 0) + ", 结果预览=" + preview);
            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseJson error: rule=" + rule + ", " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 解析 JSON 数组
     * 
     * <p>从 JSON 字符串中提取数组数据，返回字符串列表。</p>
     * 
     * <p>使用场景：</p>
     * <ul>
     *   <li>从 JSON 响应中提取视频列表</li>
     *   <li>从 JSON 响应中提取播放链接数组</li>
     *   <li>批量提取 JSON 数组中的数据</li>
     * </ul>
     * 
     * @param jsonStr JSON 字符串
     * @param rule JSON 解析规则
     * @return 解析结果数组列表，如果解析失败返回空列表
     */
    public static ArrayList<String> parseJsonArray(String jsonStr, String rule) {
        SpiderDebug.log("[XBPQ] parseJsonArray 开始: jsonStr长度=" + (jsonStr != null ? jsonStr.length() : 0) + ", rule=" + rule);
        
        ArrayList<String> result = new ArrayList<>();
        
        if (TextUtils.isEmpty(jsonStr) || TextUtils.isEmpty(rule)) {
            SpiderDebug.log("[XBPQ] parseJsonArray: 参数为空, 返回空列表");
            return result;
        }
        
        try {
            String arrayStr = parseJson(jsonStr, rule);
            SpiderDebug.log("[XBPQ] parseJsonArray 解析结果: arrayStr长度=" + (arrayStr != null ? arrayStr.length() : 0));
            
            if (TextUtils.isEmpty(arrayStr)) {
                SpiderDebug.log("[XBPQ] parseJsonArray: arrayStr为空, 返回空列表");
                return result;
            }
            
            if (arrayStr.trim().startsWith("[")) {
                SpiderDebug.log("[XBPQ] parseJsonArray 路由: 类型=JSONArray解析");
                JSONArray array = new JSONArray(arrayStr);
                for (int i = 0; i < array.length(); i++) {
                    result.add(array.get(i).toString());
                }
            } else {
                SpiderDebug.log("[XBPQ] parseJsonArray 路由: 类型=单值添加");
                result.add(arrayStr);
            }
            
            SpiderDebug.log("[XBPQ] parseJsonArray 结果: 结果数量=" + result.size());
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseJsonArray error: rule=" + rule + ", " + e.getMessage(), e);
        }
        
        return result;
    }

    // ==================== JSON 遍历辅助方法 ====================
    
    /**
     * 遍历 JSON 对象获取指定字段
     * 
     * <p>从 JSONObject 或 JSONArray 中获取指定字段名的值。</p>
     * 
     * <p>处理逻辑：</p>
     * <ul>
     *   <li>如果是 JSONObject，直接获取字段值</li>
     *   <li>如果是 JSONArray，先获取第一个元素再获取字段值</li>
     * </ul>
     * 
     * @param jsonObj JSON 对象（JSONObject 或 JSONArray）
     * @param fieldName 字段名称
     * @return 字段值对象，如果获取失败返回 null
     */
    private static Object traverseJson(Object jsonObj, String fieldName) {
        try {
            if (jsonObj instanceof JSONObject) {
                JSONObject json = (JSONObject) jsonObj;
                return json.get(fieldName);
            } else if (jsonObj instanceof JSONArray) {
                // 数组后没有下标，获取第一个元素
                JSONArray array = (JSONArray) jsonObj;
                if (array.length() > 0) {
                    Object firstElement = array.get(0);
                    if (firstElement instanceof JSONObject) {
                        return ((JSONObject) firstElement).get(fieldName);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] traverseJson error: fieldName=" + fieldName + ", " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 遍历 JSON 数组获取指定元素
     * 
     * <p>根据数组下标规则从 JSONObject 或 JSONArray 中获取数据。</p>
     * 
     * <p>支持的数组下标格式：</p>
     * <ul>
     *   <li>[] - 获取整个数组</li>
     *   <li>[n] - 获取第 n 个元素（从 1 开始计数）</li>
     *   <li>[n,] - 获取从第 n 个元素开始到数组末尾的所有元素</li>
     * </ul>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>list[] - 获取整个 list 数组</li>
     *   <li>list[1] - 获取 list 数组的第 1 个元素</li>
     *   <li>list[2,] - 获取 list 数组从第 2 个元素开始到末尾</li>
     * </ul>
     * 
     * @param jsonObj JSON 对象（JSONObject 或 JSONArray）
     * @param path 包含数组下标的路径（如 "list[1]" 或 "[1]"）
     * @param arrayMatcher 已匹配的数组下标正则 Matcher
     * @return 处理后的 JSON 对象
     */
    private static Object traverseJsonArray(Object jsonObj, String path, Matcher arrayMatcher) {
        try {
            String fieldName = path.contains("[") ? path.substring(0, path.indexOf("[")) : "";
            String indexStr = arrayMatcher.group(1);
            boolean isFromEnd = arrayMatcher.group(2) != null; // 有逗号表示从某位置开始
            
            // 如果有字段名，先获取字段
            if (!TextUtils.isEmpty(fieldName)) {
                if (jsonObj instanceof JSONObject) {
                    jsonObj = ((JSONObject) jsonObj).get(fieldName);
                } else if (jsonObj instanceof JSONArray) {
                    JSONArray array = (JSONArray) jsonObj;
                    if (array.length() > 0) {
                        jsonObj = array.get(0);
                        if (jsonObj instanceof JSONObject) {
                            jsonObj = ((JSONObject) jsonObj).get(fieldName);
                        }
                    }
                }
            }
            
            // 处理数组下标
            if (jsonObj instanceof JSONArray) {
                JSONArray array = (JSONArray) jsonObj;
                
                if (TextUtils.isEmpty(indexStr)) {
                    // [] 表示整个数组
                    return array;
                } else if (isFromEnd) {
                    // [n,] 表示从第 n 个开始
                    int start = Integer.parseInt(indexStr);
                    JSONArray subArray = new JSONArray();
                    for (int i = start - 1; i < array.length(); i++) {
                        subArray.put(array.get(i));
                    }
                    return subArray;
                } else {
                    // [n] 表示第 n 个元素
                    int index = Integer.parseInt(indexStr);
                    if (index > 0 && index <= array.length()) {
                        return array.get(index - 1);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] traverseJsonArray error: path=" + path + ", " + e.getMessage(), e);
        }
        return jsonObj;
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 将 JSON 对象转换为字符串
     * 
     * <p>根据 JSON 对象类型返回对应的字符串表示：</p>
     * <ul>
     *   <li>String - 直接返回</li>
     *   <li>JSONArray - 返回 JSON 数组字符串</li>
     *   <li>JSONObject - 返回 JSON 对象字符串</li>
     *   <li>其他类型 - 调用 toString() 方法</li>
     * </ul>
     * 
     * @param jsonObj JSON 对象
     * @return 字符串表示
     */
    private static String convertJsonToString(Object jsonObj) {
        if (jsonObj == null) {
            return "";
        }
        
        if (jsonObj instanceof String) {
            return (String) jsonObj;
        } else if (jsonObj instanceof JSONArray) {
            return ((JSONArray) jsonObj).toString();
        } else if (jsonObj instanceof JSONObject) {
            return ((JSONObject) jsonObj).toString();
        } else {
            return jsonObj.toString();
        }
    }
}