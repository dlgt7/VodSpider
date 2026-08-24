package com.github.catvod.spider.xbpq.network;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.catvod.crawler.SpiderDebug;

/**
 * OkHttp网络客户端实现
 * <p>
 * 基于OkHttp的HTTP客户端实现，提供完整的网络请求能力。
 * 包含SSRF防护、WAF绕过、Cookie管理等增强功能。
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class OkHttpWrapper implements HttpClient {

    private static final Map<String, String> INTERNAL_IPS = new HashMap<>();
    private final Map<String, String> defaultHeaders;
    private final List<HttpInterceptor> interceptors;
    private int lastStatusCode = 0;

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    static {
        // 初始化内网地址前缀
        INTERNAL_IPS.put("10.", Boolean.TRUE.toString());
        INTERNAL_IPS.put("172.16.", Boolean.TRUE.toString());
        INTERNAL_IPS.put("172.31.", Boolean.TRUE.toString());
        INTERNAL_IPS.put("192.168.", Boolean.TRUE.toString());
        INTERNAL_IPS.put("127.", Boolean.TRUE.toString());
        INTERNAL_IPS.put("localhost", Boolean.TRUE.toString());
        INTERNAL_IPS.put("0.0.0.0", Boolean.TRUE.toString());
    }

    public OkHttpWrapper() {
        this.defaultHeaders = new HashMap<>();
        this.interceptors = new ArrayList<>();
        // 设置默认UA
        defaultHeaders.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mi 10 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.152 Mobile Safari/537.36");
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) {
        return get(url, headers, DEFAULT_TIMEOUT_SECONDS);
    }

    public HttpResponse get(String url, Map<String, String> headers, int timeout) {
        return executeRequest(buildRequest(url, headers, HttpRequest.Method.GET, null, timeout));
    }

    @Override
    public HttpResponse post(String url, Map<String, String> headers, String body) {
        return executeRequest(buildRequest(url, headers, HttpRequest.Method.POST, body));
    }

    @Override
    public String string(String url, Map<String, String> headers) {
        return string(url, headers, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public String string(String url, Map<String, String> headers, int timeout) {
        HttpResponse response = get(url, headers, timeout);
        lastStatusCode = response.getStatusCode();
        return response.isSuccess() ? response.getBody() : "";
    }

    @Override
    public String string(String url, Map<String, String> headers, String body) {
        HttpResponse response = post(url, headers, body);
        lastStatusCode = response.getStatusCode();
        return response.isSuccess() ? response.getBody() : "";
    }

    @Override
    public int getLastStatusCode() {
        return lastStatusCode;
    }

    @Override
    public boolean isInternalUrl(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return true;
        String lower = urlStr.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            // 仅允许 http/https，阻断 file:/ftp:/jar: 等非常规/危险协议
            return true;
        }
        try {
            URL url = new URL(urlStr);
            String host = url.getHost();
            if (host == null || host.isEmpty()) return true;
            String h = host.toLowerCase();
            if (h.startsWith("[") && h.endsWith("]")) {
                h = h.substring(1, h.length() - 1);
            }
            try {
                InetAddress addr = InetAddress.getByName(h);
                if (isInternalAddress(addr)) return true;
            } catch (UnknownHostException e) {
                // 解析失败：仅对明显内网关键字主机名拦截；
                // 普通公网域名解析抖动时放行，避免误杀正常站点（解析失败本身无法连到内网）
                for (String key : INTERNAL_IPS.keySet()) {
                    if (h.contains(key.toLowerCase())) return true;
                }
                return false;
            }
            return false;
        } catch (Exception e) {
            SpiderDebug.log("URL解析失败: " + urlStr + ", 错误: " + e.getMessage());
            return true;
        }
    }

    /** 判定 IP 地址是否为内网/保留/特殊用途地址（SSRF 防护核心） */
    private static boolean isInternalAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()) return true;      // 0.0.0.0 / ::
        if (addr.isLoopbackAddress()) return true;       // 127.0.0.0/8, ::1
        if (addr.isLinkLocalAddress()) return true;      // 169.254.0.0/16, fe80::/10
        if (addr.isSiteLocalAddress()) return true;      // 10/8, 172.16/12, 192.168/16, fec0::/10
        if (addr.isMulticastAddress()) return true;      // 224.0.0.0/4 等
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int ip = ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16)
                   | ((b[2] & 0xff) << 8) | (b[3] & 0xff);
            return isInternalV4(ip);
        } else if (b.length == 16) {
            // IPv6 唯一本地地址 fc00::/7
            if ((b[0] & 0xfe) == 0xfc) return true;
            // IPv4 映射地址 ::ffff:x.x.x.x
            if (isIpv4Mapped(b)) {
                int ip = ((b[12] & 0xff) << 24) | ((b[13] & 0xff) << 16)
                       | ((b[14] & 0xff) << 8) | (b[15] & 0xff);
                return isInternalV4(ip);
            }
        }
        return false;
    }

    /** IPv4 内网/特殊网段判定（含 CGNAT、元数据、测试网段、多播、保留段） */
    private static boolean isInternalV4(int ip) {
        if ((ip & 0xffc00000) == 0x64400000) return true; // 100.64.0.0/10   CGNAT
        if ((ip & 0xffff0000) == 0xc0000000) return true; // 192.0.0.0/16
        if ((ip & 0xffffff00) == 0xc0000000) return true; // 192.0.0.0/24
        if ((ip & 0xfffe0000) == 0xc6120000) return true; // 198.18.0.0/15   benchmarking
        if ((ip & 0xffffff00) == 0xc6336400) return true; // 198.51.100.0/24 TEST-NET-2
        if ((ip & 0xffffff00) == 0xcb007100) return true; // 203.0.113.0/24  TEST-NET-3
        if ((ip & 0xf0000000) == 0xe0000000) return true; // 224.0.0.0/4    多播
        if ((ip & 0xf0000000) == 0xf0000000) return true; // 240.0.0.0/4    保留
        return false;
    }

    /** 是否为 ::ffff:0:0/96 形式的 IPv4 映射地址 */
    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return b[10] == (byte) 0xff && b[11] == (byte) 0xff;
    }

    @Override
    public void addInterceptor(HttpInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    @Override
    public void removeInterceptor(HttpInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    @Override
    public void clearInterceptors() {
        interceptors.clear();
    }

    /**
     * 执行请求，按拦截器链顺序处理
     */
    private HttpResponse executeRequest(HttpRequest request) {
        final HttpRequest[] processed = {request};

        // 构建拦截器链：从后往前递归
        final int[] index = {0};
        InterceptorChain chain = new InterceptorChain() {
            @Override
            public HttpResponse proceed(HttpRequest req) {
                if (index[0] < interceptors.size()) {
                    HttpInterceptor interceptor = interceptors.get(index[0]++);
                    return interceptor.intercept(req, this);
                }
                // 所有拦截器已处理，执行实际网络请求
                HttpResponse response = req.execute();
                lastStatusCode = response.getStatusCode();
                return response;
            }
        };

        HttpResponse response = chain.proceed(request);
        lastStatusCode = response.getStatusCode();
        return response;
    }

    private HttpRequest buildRequest(String url, Map<String, String> headers, HttpRequest.Method method, String body) {
        return buildRequest(url, headers, method, body, DEFAULT_TIMEOUT_SECONDS);
    }

    private HttpRequest buildRequest(String url, Map<String, String> headers, HttpRequest.Method method, String body, int timeout) {
        HttpRequest.Builder builder = new HttpRequest.Builder()
                .url(url)
                .method(method);

        // 合并请求头
        Map<String, String> allHeaders = new HashMap<>(defaultHeaders);
        if (headers != null) {
            allHeaders.putAll(headers);
        }
        builder.headers(allHeaders);

        if (body != null) {
            builder.body(body);
        }
        builder.timeout(timeout);

        return builder.build();
    }

}
