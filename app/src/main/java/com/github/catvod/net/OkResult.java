package com.github.catvod.net;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OkResult {

    private final int code;
    private final String body;
    private final Map<String, List<String>> resp;
    private long duration;
    private String url;

    public OkResult() {
        this.code = 500;
        this.body = "";
        this.resp = new HashMap<>();
        this.duration = 0;
        this.url = "";
    }

    public OkResult(int code, String body, Map<String, List<String>> resp) {
        this.code = code;
        this.body = body;
        this.resp = resp != null ? resp : new HashMap<>();
        this.duration = 0;
        this.url = "";
    }

    public OkResult(int code, String body, Map<String, List<String>> resp, long duration) {
        this.code = code;
        this.body = body;
        this.resp = resp != null ? resp : new HashMap<>();
        this.duration = duration;
        this.url = "";
    }

    public OkResult(int code, String body, Map<String, List<String>> resp, long duration, String url) {
        this.code = code;
        this.body = body;
        this.resp = resp != null ? resp : new HashMap<>();
        this.duration = duration;
        this.url = url != null ? url : "";
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return TextUtils.isEmpty(body) ? "" : body;
    }

    public Map<String, List<String>> getResp() {
        return resp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeader(String key) {
        if (resp == null || TextUtils.isEmpty(key)) return "";
        List<String> values = resp.get(key);
        if (values == null || values.isEmpty()) return "";
        return values.get(0);
    }

    public String getHeader(String key, String defaultValue) {
        String value = getHeader(key);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    public List<String> getHeaders(String key) {
        if (resp == null || TextUtils.isEmpty(key)) return null;
        return resp.get(key);
    }

    public String getContentType() {
        return getHeader("Content-Type");
    }

    public String getContentEncoding() {
        return getHeader("Content-Encoding");
    }

    public String getContentLength() {
        return getHeader("Content-Length");
    }

    public String getServer() {
        return getHeader("Server");
    }

    public String getDate() {
        return getHeader("Date");
    }

    public String getSetCookie() {
        return getHeader("Set-Cookie");
    }

    public String getLocation() {
        String location = getHeader("Location");
        if (TextUtils.isEmpty(location)) {
            location = getHeader("location");
        }
        return location;
    }

    public boolean hasHeader(String key) {
        if (resp == null || TextUtils.isEmpty(key)) return false;
        List<String> values = resp.get(key);
        return values != null && !values.isEmpty();
    }

    public boolean hasBody() {
        return !TextUtils.isEmpty(body);
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    public boolean isRedirect() {
        return code >= 300 && code < 400;
    }

    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    public boolean isServerError() {
        return code >= 500 && code < 600;
    }

    public boolean isError() {
        return code >= 400;
    }

    public boolean isOk() {
        return code == 200;
    }

    public boolean isCreated() {
        return code == 201;
    }

    public boolean isNoContent() {
        return code == 204;
    }

    public boolean isNotFound() {
        return code == 404;
    }

    public boolean isUnauthorized() {
        return code == 401;
    }

    public boolean isForbidden() {
        return code == 403;
    }

    public boolean isTimeout() {
        return code == 408 || code == 504;
    }

    public int bodyLength() {
        return body != null ? body.length() : 0;
    }

    public int bodyBytes() {
        return body != null ? body.getBytes().length : 0;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(body);
    }

    public boolean isNotEmpty() {
        return !TextUtils.isEmpty(body);
    }

    public boolean isJson() {
        String contentType = getContentType();
        return contentType != null && contentType.contains("application/json");
    }

    public boolean isHtml() {
        String contentType = getContentType();
        return contentType != null && contentType.contains("text/html");
    }

    public boolean isXml() {
        String contentType = getContentType();
        return contentType != null && (contentType.contains("application/xml") || contentType.contains("text/xml"));
    }

    public boolean isText() {
        String contentType = getContentType();
        return contentType != null && contentType.contains("text/");
    }

    public boolean isImage() {
        String contentType = getContentType();
        return contentType != null && contentType.contains("image/");
    }

    public boolean isVideo() {
        String contentType = getContentType();
        return contentType != null && contentType.contains("video/");
    }

    public boolean isAudio() {
        String contentType = getContentType();
        return contentType != null && contentType.contains("audio/");
    }

    @Override
    public String toString() {
        return "OkResult{" +
                "code=" + code +
                ", bodyLength=" + bodyLength() +
                ", duration=" + duration + "ms" +
                ", url='" + url + '\'' +
                '}';
    }

    public String toDetailString() {
        return "OkResult{" +
                "code=" + code +
                ", body='" + (bodyLength() > 100 ? body.substring(0, 100) + "..." : body) + '\'' +
                ", resp=" + resp +
                ", duration=" + duration + "ms" +
                ", url='" + url + '\'' +
                '}';
    }
}