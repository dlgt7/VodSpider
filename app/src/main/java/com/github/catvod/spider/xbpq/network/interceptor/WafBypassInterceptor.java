package com.github.catvod.spider.xbpq.network.interceptor;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.network.HttpClient;
import com.github.catvod.spider.xbpq.network.HttpRequest;
import com.github.catvod.spider.xbpq.network.HttpResponse;

/**
 * WAF绕过拦截器
 * <p>
 * 自动检测并绕过宝塔WAF等安全防护，支持btwaf token提取和自动附加。
 * 判定条件：关键词命中且页面小于大小上限（WAF拦截页通常极小，
 * 避免正文偶然包含"检测中"等词的正常页面被误判）。
 * 重试请求沿剩余拦截器链继续执行（链内索引已越过本拦截器，不会重入），
 * 保证后续 CookieManager 仍能保存重试响应的 Cookie。
 *
 * @author CatVodSpider Team
 * @version 2.2
 */
public class WafBypassInterceptor implements HttpClient.HttpInterceptor {

    private static final Pattern P_BTWAF_TOKEN = Pattern.compile("btwaf[\"'=]\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern P_BTWAF_QUERY = Pattern.compile("[?&]btwaf=([^&\"'\\s>]+)");

    private static final List<String> BT_DETECT_KEYWORDS = Arrays.asList(
            "btwaf", "检测中", "跳转中", "安全检测",
            "yanzheng_huadong", "huadong_"
    );

    /** WAF拦截页大小上限（超过视为正常内容页，不做WAF判定） */
    private static final int WAF_PAGE_MAX_SIZE = 8192;

    /** 重试前等待时长（毫秒），给WAF校验留时间 */
    private static final long DELAY_MS = 1500;

    @Override
    public HttpResponse intercept(HttpRequest request, HttpClient.InterceptorChain chain) {
        HttpResponse response = chain.proceed(request);

        if (isWafPage(response.getBody())) {
            SpiderDebug.log("检测到WAF防护，尝试绕过...");
            return bypassWaf(request, response.getBody(), chain);
        }

        return response;
    }

    /** 关键词命中且页面足够小才判定为WAF拦截页 */
    private boolean isWafPage(String html) {
        if (html == null || html.isEmpty() || html.length() > WAF_PAGE_MAX_SIZE) return false;
        for (String keyword : BT_DETECT_KEYWORDS) {
            if (html.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 提取btwaf token后带参重试；无token时延迟后原样重试一次。
     * 重试沿剩余拦截器链执行：链内索引已越过本拦截器，不会递归重入，
     * 且后续拦截器（如CookieManager）仍能处理重试响应。
     */
    private HttpResponse bypassWaf(HttpRequest originalRequest, String html, HttpClient.InterceptorChain chain) {
        String btwafToken = extractBtwafToken(html);
        HttpRequest retryRequest = btwafToken.isEmpty()
                ? originalRequest
                : buildRequestWithParam(originalRequest, "btwaf", btwafToken);

        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return chain.proceed(retryRequest);
    }

    private HttpRequest buildRequestWithParam(HttpRequest original, String param, String value) {
        String url = appendQueryParam(original.getUrl(), param, value);
        HttpRequest.Builder builder = new HttpRequest.Builder()
                .url(url)
                .method(original.getMethod())
                .headers(original.getHeaders())
                .timeout(original.getTimeout());
        if (original.getBody() != null) {
            builder.body(original.getBody());
        }
        return builder.build();
    }

    private String extractBtwafToken(String html) {
        try {
            Matcher m = P_BTWAF_TOKEN.matcher(html);
            if (m.find()) {
                return m.group(1);
            }
            m = P_BTWAF_QUERY.matcher(html);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            SpiderDebug.log("extractBtwafToken error: " + e.getMessage());
        }
        return "";
    }

    private String appendQueryParam(String url, String param, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + param + "=" + value;
    }
}
