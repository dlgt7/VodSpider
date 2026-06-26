package com.github.catvod.net;

import android.text.TextUtils;
import android.util.Log;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp {

    private static final String TAG = OkHttp.class.getSimpleName();
    private static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    private static final long MAX_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String PATCH = "PATCH";

    private static volatile OkHttpClient client;
    private static volatile OkHttpClient safeClient;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    private static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static Response newCall(String url, String tag) throws IOException {
        return client().newCall(new Request.Builder().url(url).tag(tag).build()).execute();
    }

    public static String string(String url) {
        return string(url, null);
    }

    public static String string(String url, long timeout) {
        return string(url, null, null, timeout);
    }

    public static String string(String url, Map<String, String> header) {
        return string(url, null, header);
    }

    public static String string(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(GET, url, params, header).execute(client()).getBody();
    }

    public static String string(String url, Map<String, String> params, Map<String, String> header, long timeout) {
        return new OkRequest(GET, url, params, header).execute(client(timeout)).getBody();
    }

    public static String post(String url, String json) {
        return post(url, json, null);
    }

    public static String post(String url, String json, Map<String, String> header) {
        return new OkRequest(POST, url, json, header).execute(client()).getBody();
    }

    public static String post(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(POST, url, params, header).execute(client()).getBody();
    }

    public static String put(String url, String json) {
        return put(url, json, null);
    }

    public static String put(String url, String json, Map<String, String> header) {
        return new OkRequest(PUT, url, json, header).execute(client()).getBody();
    }

    public static String delete(String url, Map<String, String> header) {
        return new OkRequest(DELETE, url, (String) null, header).execute(client()).getBody();
    }

    public static String patch(String url, String json, Map<String, String> header) {
        return new OkRequest(PATCH, url, json, header).execute(client()).getBody();
    }

    public static Object[] proxy(String url, Map<String, String> header) {
        try {
            Response response = newCall(url, header);
            return new Object[]{response.code(), response.header("Content-Type", "application/octet-stream"), response.body().byteStream()};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", null};
        }
    }

    public static Response newCall(String url, Map<String, String> header) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (header != null) for (String key : header.keySet()) builder.addHeader(key, header.get(key));
        return client().newCall(builder.build()).execute();
    }

    public static Response newCall(String url, Map<String, String> header, String tag) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).tag(tag);
        if (header != null) for (String key : header.keySet()) builder.addHeader(key, header.get(key));
        return client().newCall(builder.build()).execute();
    }

    public static String upload(String url, Map<String, String> params, Map<String, File> files) {
        return upload(url, params, files, null);
    }

    public static String upload(String url, Map<String, String> params, Map<String, File> files, Map<String, String> header) {
        return new OkUpload(url, params, files, header).execute(client()).getBody();
    }

    public static String download(String url, String path) throws IOException {
        return download(url, path, null);
    }

    public static String download(String url, String path, Map<String, String> header) throws IOException {
        Response response = newCall(url, header);
        if (!response.isSuccessful()) {
            response.close();
            throw new IOException("HTTP " + response.code());
        }
        File file = new File(path);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        try (java.io.InputStream is = response.body().byteStream(); java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) fos.write(buffer, 0, len);
        }
        response.close();
        return file.getAbsolutePath();
    }

    public static byte[] bytes(String url) {
        return bytes(url, null);
    }

    public static byte[] bytes(String url, Map<String, String> header) {
        try {
            Response response = newCall(url, header);
            if (!response.isSuccessful()) {
                response.close();
                return new byte[0];
            }
            byte[] data = response.body().bytes();
            response.close();
            return data;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new byte[0];
        }
    }

    public static void cancel(Object tag) {
        if (tag == null) return;
        try {
            client().dispatcher().cancelAll();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    public static OkHttpClient client() {
        if (client == null) {
            synchronized (OkHttp.class) {
                if (client == null) {
                    client = buildClient(DEFAULT_TIMEOUT);
                }
            }
        }
        return client;
    }

    private static OkHttpClient client(long timeout) {
        if (timeout <= 0 || timeout == DEFAULT_TIMEOUT) return client();
        return buildClient(timeout);
    }

    private static OkHttpClient buildClient(long timeout) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true);
        
        builder.connectionPool(new okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES));
        
        File cacheDir = getCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            int cacheSize = 50 * 1024 * 1024;
            builder.cache(new okhttp3.Cache(cacheDir, cacheSize));
        }
        
        return builder.build();
    }

    private static File getCacheDir() {
        try {
            if (Init.context() != null) {
                File cacheDir = new File(Init.context().getCacheDir(), "http_cache");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                return cacheDir;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    private static Headers getHeaders(Map<String, String> header) {
        if (header == null || header.isEmpty()) return new Headers.Builder().build();
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, String> entry : header.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}