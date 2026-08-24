package com.github.catvod.spider.xbpq.network;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HTTP响应类
 * <p>
 * 封装HTTP响应信息，包括状态码、响应头和响应体。
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class HttpResponse {

    private final int statusCode;
    private final JSONObject headers;
    private final String body;
    private final boolean success;
    private final String errorMessage;
    private final List<String> setCookieHeaders;

    private HttpResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.headers = builder.headers;
        this.body = builder.body;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.setCookieHeaders = builder.setCookies != null ? builder.setCookies
                : Collections.emptyList();
    }

    /** 获取响应中所有 Set-Cookie 头（修复多 Set-Cookie 仅保留最后一个的缺陷） */
    public List<String> getSetCookies() { return setCookieHeaders; }

    public int getStatusCode() { return statusCode; }
    public JSONObject getHeaders() { return headers; }
    public String getBody() { return body; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * 从OkHttp响应构建
     */
    public static HttpResponse from(okhttp3.Response response) {
        Builder builder = new Builder()
                .statusCode(response.code())
                .success(response.isSuccessful());

        // 提取响应头
        JSONObject headers = new JSONObject();
        List<String> setCookies = new ArrayList<>();
        for (int i = 0; i < response.headers().size(); i++) {
            try {
                String name = response.headers().name(i);
                String value = response.headers().value(i);
                headers.put(name, value);
                if ("Set-Cookie".equalsIgnoreCase(name)) {
                    setCookies.add(value);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        builder.headers(headers).setCookies(setCookies);

        // 提取响应体
        try {
            if (response.body() != null) {
                builder.body(response.body().string());
            }
        } catch (Exception e) {
            builder.errorMessage(e.getMessage());
        }

        return builder.build();
    }

    /**
     * 创建错误响应
     */
    public static HttpResponse error(String message) {
        return new Builder()
                .statusCode(0)
                .success(false)
                .errorMessage(message)
                .build();
    }

    /**
     * 响应构建器
     */
    public static class Builder {
        private int statusCode;
        private JSONObject headers;
        private String body;
        private boolean success;
        private String errorMessage;
        private List<String> setCookies;

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder headers(JSONObject headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder setCookies(List<String> setCookies) {
            this.setCookies = setCookies;
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(this);
        }
    }
}
