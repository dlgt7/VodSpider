package com.github.catvod.spider.xbpq.network.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.network.HttpClient;
import com.github.catvod.spider.xbpq.network.HttpRequest;
import com.github.catvod.spider.xbpq.network.HttpResponse;

/**
 * Cookie管理拦截器
 * <p>
 * 自动管理Cookie，支持持久化和自动附加。
 * <p>
 * 修复说明：原 cookieStore 为 HashMap，TVBox 框架多线程并发调用爬虫方法时
 * computeIfAbsent/put 存在数据竞争（Java 8 下并发 computeIfAbsent 可能死循环），
 * 现改为 ConcurrentHashMap（外层）+ ConcurrentHashMap（内层）。
 * 同时增加过期 Cookie 过滤，避免过期项常驻内存并随请求回传。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class CookieManager implements HttpClient.HttpInterceptor, CookieJar {

    private final Map<String, Map<String, Cookie>> cookieStore;

    public CookieManager() {
        this.cookieStore = new ConcurrentHashMap<>();
    }

    /** Cookie 是否已过期 */
    private static boolean isExpired(Cookie cookie) {
        return cookie != null && cookie.expiresAt() <= System.currentTimeMillis();
    }

    @Override
    public HttpResponse intercept(HttpRequest request, HttpClient.InterceptorChain chain) {
        // 附加Cookie
        loadCookies(request);

        // 执行请求
        HttpResponse response = chain.proceed(request);

        // 保存Cookie（基于原始请求URL确定域名）
        saveCookies(request, response);

        return response;
    }

    /** 从响应头提取并保存Cookie（基于原始请求URL确定域名） */
    public void saveCookies(HttpRequest request, HttpResponse response) {
        // 优先使用完整 Set-Cookie 列表（修复多 Set-Cookie 头仅保留最后一个的缺陷）
        List<String> cookies = response.getSetCookies();
        if (cookies != null && !cookies.isEmpty()) {
            for (String cookie : cookies) saveCookie(request.getUrl(), cookie);
            return;
        }
        // 兜底：单值兼容
        String setCookie = response.getHeaders().optString("Set-Cookie", "");
        if (!setCookie.isEmpty()) {
            saveCookie(request.getUrl(), setCookie);
        }
    }

    /**
     * 保存单个Cookie
     */
    public void saveCookie(String url, String cookie) {
        try {
            HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl == null) return;

            String domain = httpUrl.host();
            String path = httpUrl.encodedPath() != null ? httpUrl.encodedPath() : "/";

            // 解析Cookie
            Cookie.Builder builder = new Cookie.Builder()
                    .domain(domain)
                    .path(path);

            String[] parts = cookie.split(";");
            String nameValue = parts[0].trim();
            int eqIdx = nameValue.indexOf('=');
            // 无名 Cookie（无 "="）无法构建，显式跳过（原实现落入 builder.build()
            // 抛 IllegalArgumentException 后靠 catch 吞掉）
            if (eqIdx <= 0) return;

            builder.name(nameValue.substring(0, eqIdx).trim());
            builder.value(nameValue.substring(eqIdx + 1).trim());

            Cookie cookieObj = builder.build();
            cookieStore
                    .computeIfAbsent(domain, k -> new ConcurrentHashMap<>())
                    .put(cookieObj.name(), cookieObj);

            SpiderDebug.log("Cookie saved: " + cookieObj.name() + " for " + domain);
        } catch (Exception e) {
            SpiderDebug.log("saveCookie error: " + e.getMessage());
        }
    }

    /**
     * 加载Cookie到请求头
     */
    public void loadCookies(HttpRequest request) {
        try {
            HttpUrl httpUrl = HttpUrl.parse(request.getUrl());
            if (httpUrl == null) return;

            String domain = httpUrl.host();
            Map<String, Cookie> domainCookies = cookieStore.get(domain);
            if (domainCookies == null || domainCookies.isEmpty()) return;

            StringBuilder cookieHeader = new StringBuilder();
            for (Cookie cookie : domainCookies.values()) {
                if (isExpired(cookie)) continue;
                if (cookieHeader.length() > 0) {
                    cookieHeader.append("; ");
                }
                cookieHeader.append(cookie.name()).append("=").append(cookie.value());
            }

            if (cookieHeader.length() > 0) {
                request.getHeaders().put("Cookie", cookieHeader.toString());
                SpiderDebug.log("Cookies loaded for " + domain);
            }
        } catch (Exception e) {
            SpiderDebug.log("loadCookies error: " + e.getMessage());
        }
    }

    // ===== CookieJar 接口实现 =====

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        String domain = url.host();
        Map<String, Cookie> domainStore = cookieStore.computeIfAbsent(domain, k -> new ConcurrentHashMap<>());
        domainStore.clear();
        for (Cookie cookie : cookies) {
            if (isExpired(cookie)) continue;
            domainStore.put(cookie.name(), cookie);
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        String domain = url.host();
        Map<String, Cookie> cookies = cookieStore.get(domain);
        if (cookies == null || cookies.isEmpty()) return Collections.emptyList();
        List<Cookie> result = new ArrayList<>(cookies.size());
        for (Cookie cookie : cookies.values()) {
            if (!isExpired(cookie)) result.add(cookie);
        }
        return result;
    }
}
