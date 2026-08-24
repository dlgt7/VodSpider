package com.github.catvod.spider.xbpq.network;

import java.util.Map;

import org.json.JSONObject;

import com.github.catvod.crawler.SpiderDebug;

/**
 * HTTP客户端抽象接口
 * <p>
 * 定义网络请求的标准接口，支持多种实现。
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public interface HttpClient {

    /**
     * 发送GET请求
     *
     * @param url    请求URL
     * @param headers 请求头
     * @return 响应结果
     */
    HttpResponse get(String url, Map<String, String> headers);

    /**
     * 发送POST请求
     *
     * @param url    请求URL
     * @param headers 请求头
     * @param body   请求体
     * @return 响应结果
     */
    HttpResponse post(String url, Map<String, String> headers, String body);

    /**
     * 获取响应文本
     *
     * @param url    请求URL
     * @param headers 请求头
     * @return 响应文本
     */
    String string(String url, Map<String, String> headers);

    /**
     * 发送GET请求并返回响应文本（指定超时）
     *
     * @param url     请求URL
     * @param headers 请求头
     * @param timeout 超时秒数
     * @return 响应文本
     */
    String string(String url, Map<String, String> headers, int timeout);

    /**
     * 发送POST请求并返回响应文本
     *
     * @param url     请求URL
     * @param headers 请求头
     * @param body    请求体
     * @return 响应文本
     */
    String string(String url, Map<String, String> headers, String body);

    /**
     * 获取上次请求的状态码
     *
     * @return 状态码，失败返回0
     */
    int getLastStatusCode();

    /**
     * 检测内网地址（SSRF防护）
     *
     * @param url 待检测的URL
     * @return true表示是内网地址
     */
    boolean isInternalUrl(String url);

    /**
     * 添加请求拦截器
     *
     * @param interceptor 拦截器
     */
    void addInterceptor(HttpInterceptor interceptor);

    /**
     * 移除请求拦截器
     *
     * @param interceptor 拦截器
     */
    void removeInterceptor(HttpInterceptor interceptor);

    /**
     * 清空所有拦截器
     */
    void clearInterceptors();

    /**
     * HTTP请求拦截器接口
     */
    interface HttpInterceptor {
        /**
         * 拦截请求
         *
         * @param request  请求对象
         * @param chain    拦截器链
         * @return 处理后的响应
         */
        HttpResponse intercept(HttpRequest request, InterceptorChain chain);
    }

    /**
     * 拦截器链
     */
    interface InterceptorChain {
        HttpResponse proceed(HttpRequest request);
    }
}
