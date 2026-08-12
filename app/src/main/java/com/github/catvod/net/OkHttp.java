package com.github.catvod.net;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * 网络请求封装（CatVodSpider 专用）
 *
 * 核心改进：
 * 1. double-checked locking 替代 synchronized，消除高并发锁死
 * 2. 内置 UA 随机 + Accept-Language 拦截器，隐藏 OkHttp 指纹
 * 3. 支持 CryptoProvider SPI，底层透明加解密
 * 4. client(2小时) 专门用于 proxy() 长流转发
 */
public class OkHttp {

    private static final String TAG = OkHttp.class.getSimpleName();
    private static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    private static final long STREAM_TIMEOUT = TimeUnit.HOURS.toMillis(2); // proxy 专用长超时

    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String PATCH = "PATCH";
    public static final String HEAD = "HEAD";
    public static final String OPTIONS = "OPTIONS";
    public static final String COOKIE = "Cookie";
    public static final String UA = "User-Agent";
    public static final String REFERER = "Referer";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";

    // ==================== 客户端缓存（按超时时间区分） ====================
    private static final ConcurrentHashMap<Long, OkHttpClient> CLIENT_CACHE = new ConcurrentHashMap<>();
    private static final Object CLIENT_LOCK = new Object();
    private static volatile OkHttpClient noRedirectClient;

    // ==================== 可配置选项 ====================
    private static volatile Dns customDns;
    private static volatile boolean trustAllCerts = false;
    private static volatile java.net.Proxy customProxy;
    private static volatile CryptoProvider cryptoProvider;

    // ==================== 内部类 ====================

    /**
     * 加解密/签名 SPI 接口
     * 由调用方实现并注册，在 OkHttp 发送前/收到响应后自动处理
     */
    public interface CryptoProvider {
        /** 是否对该 URL 进行处理（加解密/签名） */
        boolean shouldProcess(String url);
        /** 请求前处理（加密/签名），返回替换后的 body */
        String encryptRequest(String url, String body);
        /** 响应后处理（解密），返回解密后的字符串 */
        String decryptResponse(String url, String body);
    }

    /**
     * 自动注入浏览器指纹的拦截器
     * - 未设置 UA 时随机选取真实浏览器 UA
     * - 未设置 Accept-Language 时补充 zh-CN
     * - 消除 OkHttp 默认特征
     */
    private static final Interceptor UA_INTERCEPTOR = chain -> {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder();
        if (original.header(UA) == null) {
            builder.header(UA, Util.getRandomUserAgent());
        }
        if (original.header(ACCEPT_LANGUAGE) == null) {
            builder.header(ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8");
        }
        return chain.proceed(builder.build());
    };

    /**
     * 加解密/签名拦截器（可选，仅在注册了 CryptoProvider 时生效）
     * 使用 peekBody 保证响应 body 即使解密失败也不会被消费
     */
    private static final Interceptor CRYPTO_INTERCEPTOR = chain -> {
        Request original = chain.request();
        String url = original.url().toString();
        CryptoProvider provider = cryptoProvider;
        if (provider == null || !provider.shouldProcess(url)) {
            return chain.proceed(original);
        }
        RequestBody body = original.body();
        Request encryptedRequest = original;
        if (body != null) {
            String raw = bodyToString(body);
            String encrypted = provider.encryptRequest(url, raw);
            if (encrypted != null && !encrypted.equals(raw)) {
                MediaType mt = body.contentType();
                encryptedRequest = original.newBuilder()
                        .method(original.method(), RequestBody.create(mt != null ? mt : MediaType.parse("application/octet-stream"), encrypted))
                        .build();
            }
        }
        Response response = chain.proceed(encryptedRequest);
        ResponseBody respBody = response.body();
        if (respBody == null) return response;
        // 直接读取 body 再统一重建，不依赖 peekBody（兼容所有 OkHttp 版本）
        MediaType respMt = respBody.contentType();
        String respStr;
        try {
            respStr = respBody.string();
        } catch (IOException e) {
            SpiderDebug.log(e);
            return response;
        }
        String finalContent = respStr;
        if (!respStr.isEmpty()) {
            String decrypted = provider.decryptResponse(url, respStr);
            if (decrypted != null && !decrypted.equals(respStr)) {
                finalContent = decrypted;
            }
        }
        byte[] finalBytes = finalContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return response.newBuilder()
                .body(ResponseBody.create(respMt != null ? respMt : MediaType.parse("application/octet-stream"), finalBytes))
                .build();
    };

    // ==================== 请求入口 ====================

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

    // ==================== 异步请求 ====================

    public static void asyncString(String url, Map<String, String> header, Callback callback) {
        asyncString(url, null, header, client(), callback);
    }

    public static void asyncString(String url, Map<String, String> params, Map<String, String> header, Callback callback) {
        asyncString(url, params, header, client(), callback);
    }

    public static void asyncPost(String url, String json, Map<String, String> header, Callback callback) {
        asyncPost(url, json, header, client(), callback);
    }

    private static void asyncString(String url, Map<String, String> params, Map<String, String> header,
                                     OkHttpClient client, Callback callback) {
        if (callback == null) return;
        HttpUrl.Builder urlBuilder = safeUrlBuilder(url);
        if (urlBuilder == null) {
            callback.onFailure(null, new IOException("Invalid URL: " + url));
            return;
        }
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        Request.Builder builder = new Request.Builder().url(urlBuilder.build());
        if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        client.newCall(builder.build()).enqueue(callback);
    }

    private static void asyncPost(String url, String json, Map<String, String> header,
                                   OkHttpClient client, Callback callback) {
        if (callback == null) return;
        HttpUrl.Builder urlBuilder = safeUrlBuilder(url);
        if (urlBuilder == null) {
            callback.onFailure(null, new IOException("Invalid URL: " + url));
            return;
        }
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        RequestBody body = TextUtils.isEmpty(json) ? RequestBody.create(mediaType, "{}") : RequestBody.create(mediaType, json);
        Request.Builder builder = new Request.Builder().url(urlBuilder.build()).post(body);
        if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        client.newCall(builder.build()).enqueue(callback);
    }

    // ==================== 代理 & 文件 ====================

    /**
     * 代理流（内存版）：内部完成流拷贝后释放 Response，返回 int[code], String[contentType], byte[]
     * 适用于小文件/图片等场景
     */
    public static Object[] proxy(String url, Map<String, String> header) {
        try {
            OkHttpClient streamClient = client(STREAM_TIMEOUT);
            Request.Builder rb = new Request.Builder().url(url);
            if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) rb.addHeader(entry.getKey(), entry.getValue());
            try (Response response = streamClient.newCall(rb.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return new Object[]{response.code(), response.header("Content-Type", "application/octet-stream"), new byte[0]};
                }
                byte[] data = response.body().bytes();
                String contentType = response.header("Content-Type", "application/octet-stream");
                return new Object[]{response.code(), contentType, data};
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", new byte[0]};
        }
    }

    /**
     * 流式代理：返回 Response，调用方通过 response.body().byteStream() 读取流
     * 适用于视频流等大体积数据，避免 OOM
     * 调用方必须使用 try-with-resources 关闭 Response 以释放连接池
     * @return Response，失败时返回 null
     */
    public static Response proxyStream(String url, Map<String, String> header) {
        try {
            OkHttpClient streamClient = client(STREAM_TIMEOUT);
            Request.Builder rb = new Request.Builder().url(url);
            if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) rb.addHeader(entry.getKey(), entry.getValue());
            Response response = streamClient.newCall(rb.build()).execute();
            // 不关闭，让调用方统一管理；失败时仍返回 Response 以便调用方检查状态码
            if (!response.isSuccessful() || response.body() == null) {
                return response;
            }
            return response;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
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
        long start = System.currentTimeMillis();
        Response response = newCall(url, header);
        File file = new File(path);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        try {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            try (InputStream is = response.body().byteStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) fos.write(buffer, 0, len);
            }
        } finally {
            response.close();
        }
        long duration = System.currentTimeMillis() - start;
        SpiderDebug.log("OkHttp download: " + url + " cost=" + duration + "ms");
        return file.getAbsolutePath();
    }

    public static byte[] bytes(String url) {
        return bytes(url, null);
    }

    public static byte[] bytes(String url, Map<String, String> header) {
        return bytes(url, header, 0);
    }

    public static byte[] bytes(String url, Map<String, String> header, long timeout) {
        long start = System.currentTimeMillis();
        try (Response response = timeout > 0 ? newCall(url, header, timeout) : newCall(url, header)) {
            if (!response.isSuccessful()) {
                SpiderDebug.log("OkHttp bytes: " + url + " code=" + response.code() + " cost=" + (System.currentTimeMillis() - start) + "ms");
                return new byte[0];
            }
            byte[] data = response.body().bytes();
            SpiderDebug.log("OkHttp bytes: " + url + " cost=" + (System.currentTimeMillis() - start) + "ms");
            return data;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new byte[0];
        }
    }

    // ==================== 无重定向 GET ====================

    /**
     * GET请求，不跟随重定向，可获取302等跳转的原始响应
     * 使用 try-with-resources 确保 Response 正确关闭，避免资源泄漏
     */
    public static String getStringNoRedirect(String url, Map<String, String> header, Map<String, List<String>> headerCollector) {
        OkHttpClient noRedirect = getNoRedirectClient();
        Request.Builder builder = new Request.Builder().url(url);
        if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        try (Response response = noRedirect.newCall(builder.build()).execute()) {
            if (headerCollector != null) headerCollector.putAll(response.headers().toMultimap());
            return safeString(response.body());
        } catch (IOException e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== Header 工具 ====================

    public static String getHeader(Map<String, List<String>> headers, String fieldName) {
        if (headers == null || headers.isEmpty()) return null;
        List<String> values = headers.get(fieldName);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    // ==================== 取消 ====================

    public static void cancel(Object tag) {
        if (tag == null) {
            cancelAll();
            return;
        }
        try {
            OkHttpClient c = client();
            for (Call call : c.dispatcher().queuedCalls()) {
                if (tag.equals(call.request().tag())) call.cancel();
            }
            for (Call call : c.dispatcher().runningCalls()) {
                if (tag.equals(call.request().tag())) call.cancel();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    public static void cancelAll() {
        try {
            client().dispatcher().cancelAll();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    // ==================== 客户端管理（double-checked locking） ====================

    public static OkHttpClient client() {
        return client(DEFAULT_TIMEOUT);
    }

    public static OkHttpClient client(long timeout) {
        if (timeout <= 0) timeout = DEFAULT_TIMEOUT;
        OkHttpClient client = CLIENT_CACHE.get(timeout);
        if (client == null) {
            synchronized (CLIENT_LOCK) {
                client = CLIENT_CACHE.get(timeout);
                if (client == null) {
                    client = buildClient(timeout, trustAllCerts);
                    CLIENT_CACHE.put(timeout, client);
                }
            }
        }
        return client;
    }

    private static OkHttpClient getNoRedirectClient() {
        if (noRedirectClient == null) {
            synchronized (OkHttp.class) {
                if (noRedirectClient == null) {
                    noRedirectClient = client(DEFAULT_TIMEOUT)
                            .newBuilder()
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build();
                }
            }
        }
        return noRedirectClient;
    }

    // ==================== 构建客户端 ====================

    private static OkHttpClient buildClient(long timeout, boolean trustAll) {
        Dns dns = customDns != null ? customDns : Dns.SYSTEM;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .dns(dns)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(UA_INTERCEPTOR);   // 统一注入浏览器指纹

        if (cryptoProvider != null) {
            builder.addInterceptor(CRYPTO_INTERCEPTOR); // 仅当注册了 CryptoProvider 时才添加
        }

        if (customProxy != null) builder.proxy(customProxy);

        if (trustAll) {
            builder.hostnameVerifier((hostname, session) -> true)
                   .sslSocketFactory(createTrustAllSslSocketFactory(), createTrustAllManager());
        }

        builder.connectionPool(new okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES));

        File cacheDir = getCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            builder.cache(new okhttp3.Cache(cacheDir, 50 * 1024 * 1024));
        }

        return builder.build();
    }

    /**
     * 构建信任所有证书的 OkHttpClient
     */
    public static OkHttpClient buildTrustAllClient(long timeout) {
        return buildClient(timeout, true);
    }

    /**
     * 构建使用系统证书链的 OkHttpClient（更安全）
     */
    public static OkHttpClient buildSystemClient(long timeout) {
        return buildClient(timeout, false);
    }

    // ==================== 配置接口 ====================

    /**
     * 设置自定义 DNS（如 DoH），清除缓存使后续 client() 使用新 DNS
     */
    public static void setDns(Dns dns) {
        customDns = dns;
        clearClientCache();
    }

    /**
     * 设置自定义代理，清除缓存
     */
    public static void setProxy(java.net.Proxy proxy) {
        customProxy = proxy;
        clearClientCache();
    }

    /**
     * 切换信任所有证书模式
     * @param trustAll true=信任所有证书（兼容旧行为），false=使用系统证书链
     * 清除缓存使后续 client() 使用新配置
     */
    public static void setTrustAll(boolean trustAll) {
        trustAllCerts = trustAll;
        clearClientCache();
    }

    /**
     * 注册全局加解密/签名处理器
     * 调用 shouldProcess(url) 判断是否需要处理，true 则在发送/接收时自动加解密
     */
    public static void setCryptoProvider(CryptoProvider provider) {
        cryptoProvider = provider;
        clearClientCache(); // 重新构建客户端以加入/移除 CRYPTO_INTERCEPTOR
    }

    /**
     * 清除所有已缓存的客户端，强制下次调用时重建
     */
    public static void clearClientCache() {
        CLIENT_CACHE.clear();
        noRedirectClient = null;
    }

    // ==================== 内部方法 ====================

    private static HttpUrl.Builder safeUrlBuilder(String url) {
        if (TextUtils.isEmpty(url)) return null;
        HttpUrl parsed = HttpUrl.parse(url);
        return parsed != null ? parsed.newBuilder() : null;
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

    public static Response newCall(String url, Map<String, String> header) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        return client().newCall(builder.build()).execute();
    }

    public static Response newCall(String url, Map<String, String> header, long timeout) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        return client(timeout).newCall(builder.build()).execute();
    }

    public static Response newCall(String url, Map<String, String> header, String tag) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).tag(tag);
        if (header != null) for (Map.Entry<String, String> entry : header.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        return client().newCall(builder.build()).execute();
    }

    private static String safeString(ResponseBody body) {
        try {
            return body.string();
        } catch (IOException e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private static String bodyToString(RequestBody body) {
        if (body == null) return "";
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== SSL 工具（内联，替代不存在的 SslSocketFactory/TrustAllManager） ====================

    /**
     * 创建信任所有证书的 SSLSocketFactory
     */
    private static SSLSocketFactory createTrustAllSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{createTrustAllManager()}, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    /**
     * 创建信任所有证书的 TrustManager
     */
    private static X509TrustManager createTrustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
