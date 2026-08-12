package com.github.catvod.net;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.io.IOException;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class OkRequest {

    private final Map<String, String> header;
    private final Map<String, String> params;
    private final String method;
    private final String json;
    private Request request;
    private String url;

    OkRequest(String method, String url, Map<String, String> params, Map<String, String> header) {
        this(method, url, params, null, header);
    }

    OkRequest(String method, String url, String json, Map<String, String> header) {
        this(method, url, null, json, header);
    }

    private OkRequest(String method, String url, Map<String, String> params, String json, Map<String, String> header) {
        this.url = url;
        this.json = json;
        this.method = method;
        this.params = params;
        this.header = header;
        this.buildRequest();
    }

    // ==================== 安全 URL 构建 ====================

    /**
     * 安全地将字符串 URL 转为 HttpUrl.Builder，非法 URL 时返回 null
     */
    private static HttpUrl.Builder safeUrlBuilder(String url) {
        if (TextUtils.isEmpty(url)) return null;
        HttpUrl parsed = HttpUrl.parse(url);
        return parsed != null ? parsed.newBuilder() : null;
    }

    private void buildRequest() {
        Request.Builder builder = new Request.Builder();

        // GET 请求：使用 HttpUrl.Builder 处理 query 参数，自动编码
        if (method.equals(OkHttp.GET) && params != null && !params.isEmpty()) {
            HttpUrl.Builder urlBuilder = safeUrlBuilder(url);
            if (urlBuilder != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    urlBuilder.addQueryParameter(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
                }
                this.url = urlBuilder.build().toString();
            }
            // 非法 URL：保留原值，后续 builder.url(url) 会由 OkHttp 抛 IOException，统一走 catch 路径
        }

        // 根据方法决定是否需要 body（DELETE/HEAD 无 body，其他方法需要）
        boolean hasBody = !(method.equals(OkHttp.DELETE) || method.equals(OkHttp.HEAD));
        RequestBody body = hasBody ? getRequestBody() : null;

        // 正确的 HTTP 方法分发
        if (method.equals(OkHttp.POST)) {
            builder.post(body);
        } else if (method.equals(OkHttp.PUT)) {
            builder.put(body);
        } else if (method.equals(OkHttp.PATCH)) {
            builder.patch(body);
        } else if (method.equals(OkHttp.DELETE)) {
            if (body != null && body.contentLength() > 0) {
                builder.delete(body);
            } else {
                builder.delete();
            }
        } else if (method.equals(OkHttp.HEAD)) {
            builder.head();
        } else if (method.equals(OkHttp.OPTIONS)) {
            builder.method(OkHttp.OPTIONS, null);
        }

        if (header != null) {
            for (Map.Entry<String, String> entry : header.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        request = builder.url(url).build();
    }

    private RequestBody getRequestBody() {
        if (!TextUtils.isEmpty(json)) {
            return RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
        }
        FormBody.Builder formBody = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                formBody.add(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return formBody.build();
    }

    public OkResult execute(OkHttpClient client) {
        long start = System.currentTimeMillis();
        try (Response res = client.newCall(request).execute()) {
            long duration = System.currentTimeMillis() - start;
            OkResult result = new OkResult(res.code(), res.body().string(), res.headers().toMultimap(), duration);
            result.setUrl(request.url().toString());
            return result;
        } catch (IOException e) {
            SpiderDebug.log(e);
            return new OkResult();
        }
    }
}
