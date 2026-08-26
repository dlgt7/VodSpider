package com.github.catvod.spider.xbpq.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.github.catvod.crawler.SpiderDebug;

/**
 * HTTP请求封装类
 * <p>
 * 封装OkHttp请求，提供统一的请求构建和响应处理。
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class HttpRequest {

    private final String url;
    private final Method method;
    private final Map<String, String> headers;
    private final String body;
    private final MediaType contentType;
    private final int timeout;

    public enum Method {
        GET, POST, PUT, DELETE
    }

    private HttpRequest(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = builder.headers;
        this.body = builder.body;
        this.contentType = builder.contentType;
        this.timeout = builder.timeout;
    }

    public String getUrl() { return url; }
    public Method getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public MediaType getContentType() { return contentType; }
    public int getTimeout() { return timeout; }

    /**
     * 构建GET请求
     */
    public Request toGetRequest() {
        Request.Builder builder = new Request.Builder().url(url);
        addHeaders(builder);
        return builder.get().build();
    }

    /**
     * 构建POST请求
     * <p>
     * 修复说明：OkHttp 的 RequestBody.contentType 会覆盖请求头中的 Content-Type。
     * 原实现无显式 contentType 时默认 RequestBody.create(MediaType.parse("application/json"), body)，
     * 导致调用方在 headers 中设置的 "application/x-www-form-urlencoded" 等表单类型被强制覆盖，
     * ";post" 搜索的表单请求被以 JSON Content-Type 发出、服务端 $_POST 解析失败。
     * 现优先级：显式 contentType > 请求头 Content-Type > 默认 application/json。
     */
    public Request toPostRequest() {
        Request.Builder builder = new Request.Builder().url(url);
        addHeaders(builder);

        RequestBody requestBody = null;
        if (body != null && !body.isEmpty()) {
            MediaType ct = contentType != null ? contentType : findHeaderContentType();
            if (ct == null) {
                ct = MediaType.parse("application/json");
            }
            requestBody = RequestBody.create(ct, body);
        }

        return builder.post(requestBody != null ? requestBody : RequestBody.create(new byte[0])).build();
    }

    /** 从请求头中查找 Content-Type（大小写不敏感） */
    private MediaType findHeaderContentType() {
        if (headers == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                String value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    return MediaType.parse(value);
                }
                return null;
            }
        }
        return null;
    }

    private void addHeaders(Request.Builder builder) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 执行请求（复用共享客户端连接池）
     */
    public HttpResponse execute() {
        try {
            int effectiveTimeout = timeout > 0 ? timeout : DEFAULT_TIMEOUT_SECONDS;
            OkHttpClient client = SharedClientHolder.get(effectiveTimeout);
            Request request = method == Method.GET ? toGetRequest() : toPostRequest();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                return HttpResponse.from(response);
            } finally {
                if (response != null) response.close();
            }
        } catch (Exception e) {
            SpiderDebug.log("HttpRequest execute error: " + e.getMessage());
            return HttpResponse.error(e.getMessage());
        }
    }

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * 按超时时长缓存的共享客户端（避免每次请求重建连接池）
     */
    private static final class SharedClientHolder {
        private static final Map<Integer, OkHttpClient> CACHE = new ConcurrentHashMap<>();

        static OkHttpClient get(int timeoutSeconds) {
            return CACHE.computeIfAbsent(timeoutSeconds, t -> new OkHttpClient.Builder()
                    .connectTimeout(t, TimeUnit.SECONDS)
                    .readTimeout(t, TimeUnit.SECONDS)
                    .writeTimeout(t, TimeUnit.SECONDS)
                    .build());
        }
    }

    /**
     * 请求构建器
     */
    public static class Builder {
        private String url;
        private Method method = Method.GET;
        private Map<String, String> headers;
        private String body;
        private MediaType contentType;
        private int timeout = 10;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder method(Method method) {
            this.method = method;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder contentType(MediaType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
