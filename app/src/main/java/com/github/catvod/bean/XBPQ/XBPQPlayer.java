package com.github.catvod.bean.XBPQ;

import com.github.catvod.bean.Result;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.XBPQ;
import com.github.catvod.utils.Notify;
import android.util.Base64;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * XBPQ 播放流程控制器。
 *
 * <p>负责播放地址前处理、JS 渲染、直链拦截、BTWAF 突破、异步验证码轮询、
 * 嗅探/AES 解密及多级跳转等播放阶段的串联与处理。</p>
 *
 * <p>本类为纯静态工具类，所有方法以 {@code XBPQ main} 为首参，访问 main 上的
 * 配置字段与辅助方法，不持有任何实例状态。</p>
 */
public final class XBPQPlayer {

    private static final Pattern HOST_PATTERN = Pattern.compile("(https?://[^/]+)");
    private static final Pattern PLAYER_JSON_PATTERN = Pattern.compile("(?:var\\s+)?(?:player_[\\w]*|MacPlayer|playerConfig|playerObj|config)\\s*=\\s*(\\{[^<]+\\})");
    private static final Pattern COOKIE_PATTERN = Pattern.compile("(?i)cookie\\$([^#]+?)#");
    private static final Pattern JUMP_COUNT_PATTERN = Pattern.compile("e(\\d)");

    private XBPQPlayer() {
    }

    public static String preprocessPlayUrl(XBPQ main, String id) {
        String playUrl = id;
        // 后缀解码：配置了"后缀解码"时，用工具链 fetch+decode 播放 URL
        if (!main.suffixDecode.isEmpty() && playUrl.startsWith("http")) {
            String decoded = XBPQParse.interpolate(main, main.suffixDecode);
            if (!decoded.isEmpty() && decoded.startsWith("http")) {
                playUrl = decoded;
            }
        }
        if (playUrl.startsWith("/") && !playUrl.startsWith("//")) {
            playUrl = main.baseUrl + playUrl;
        }
        if (playUrl.startsWith("xp")) {
            playUrl = playUrl.replaceAll("xp\\((http.*)\\)", "$1");
        }
        if (playUrl.contains(";post")) {
            String postUrl = playUrl.split(";")[0];
            String postHtml = XBPQHttp.fetchPost(main, postUrl);
            if (postHtml != null && !postHtml.isEmpty()) {
                String extracted = XBPQParse.extractBetween(postHtml, "var player_", "\"");
                if (!extracted.isEmpty()) playUrl = extracted;
            }
        }
        return playUrl;
    }

    /** 阶段 3：JS 渲染（sniffConfig 含 J 时通过 spiderApi.webParse 获取渲染后页面）。 @return Result 字符串或 null */
    public static String handleJsRender(XBPQ main, String playUrl) {
        if (main.sniffJsRender() && playUrl.startsWith("http") && main.spiderApi != null) {
            String webParseResult = main.spiderApi.webParse(playUrl, "");
            if (webParseResult != null && !webParseResult.isEmpty()) {
                return Result.get().parse(0).url(webParseResult).header(XBPQHttp.buildHeaderMap(main)).string();
            }
        }
        return null;
    }

    /** 阶段 4：直链判断（扩展名匹配时直接返回 parse=0）。 @return Result 字符串或 null */
    public static String handleDirectLink(XBPQ main, String playUrl) {
        if (playUrl.length() > 10 && (playUrl.contains("=http") || playUrl.startsWith("http"))) {
            if (XBPQParse.isDirectLink(playUrl)) {
                return Result.get().parse(0).url(playUrl).header(XBPQHttp.buildHeaderMap(main)).string();
            }
        }
        return null;
    }

    /** 阶段 5：btwaf 防护处理（提取 btwaf 参数重新请求获取真实播放地址）。 */
    public static String handleBtwafProtection(XBPQ main, String playUrl) {
        if (main.lowerCaseSniff && playUrl.contains("btwaf")) {
            String btwaf = XBPQParse.extractBetween(playUrl, "btwaf=", "\"");
            if (!btwaf.isEmpty()) {
                playUrl = playUrl.split("\\?")[0] + "?btwaf=" + btwaf;
                String html = XBPQHttp.fetchHtml(main, playUrl);
                if (html != null && !html.isEmpty()) {
                    String newUrl = XBPQParse.extractBetween(html, "var player_", "\"");
                    if (!newUrl.isEmpty()) playUrl = newUrl;
                }
            }
        }
        return playUrl;
    }

    /** 阶段 6：验证码异步处理（按 URL 隔离，指数退避轮询等待）。 */
    public static void handleVerificationAsync(XBPQ main, String playUrl) {
        String currentVerifyState = main.verifyStateMap.get(playUrl);
        if (!playUrl.startsWith("http") || currentVerifyState != null || !main.sniffVerifyEnabled()) return;

        synchronized (main.verifyStateMap) {
            if (!main.verifyStateMap.containsKey(playUrl)) {
                main.verifyStateMap.put(playUrl, "");
            }
        }
        try {
            final String verifyUrl = playUrl;
            Init.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String html = XBPQHttp.fetchHtml(main, verifyUrl);
                        if (html == null || html.isEmpty()) {
                            main.verifyStateMap.put(verifyUrl, "0");
                            return;
                        }
                        boolean needVerify = false;
                        for (String keyword : main.getVerifyKeywords()) {
                            if (html.contains(keyword)) {
                                needVerify = true;
                                break;
                            }
                        }
                        if (needVerify) {
                            if (main.spiderApi != null)
                                main.spiderApi.log("需要验证，源码为--> " + html.substring(0, Math.min(100, html.length())));
                            String verifyConfig = main.config != null ? main.config.get("", "验证") : "";
                            if (verifyConfig.isEmpty()) {
                                main.verifyStateMap.put(verifyUrl, "0");
                                return;
                            }
                            Notify.show("正在验证...");
                            boolean verified = handleVerification(main, verifyUrl, html, verifyConfig);
                            if (!verified) main.verifyStateMap.put(verifyUrl, "0");
                        } else {
                            main.verifyStateMap.put(verifyUrl, "1");
                            Notify.show("验证成功！");
                        }
                    } catch (Exception e) {
                        SpiderDebug.log(e);
                        if (main.spiderApi != null) main.spiderApi.log("点击播放弹窗错误！-->" + e);
                        main.verifyStateMap.put(verifyUrl, "0");
                    }
                }
            });
            // 轮询等待：指数退避 200ms→400ms→800ms...，总等待约 6 秒
            int retryCount = 0;
            long sleepMs = 200;
            while ("".equals(main.verifyStateMap.get(playUrl)) && retryCount < 8) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    SpiderDebug.log(e);
                    break;
                }
                retryCount++;
                sleepMs = Math.min(sleepMs * 2, 1000L);
            }
            if ("".equals(main.verifyStateMap.get(playUrl))) {
                main.verifyStateMap.put(playUrl, "0");
                if (main.spiderApi != null) main.spiderApi.log("验证超时，本次跳过");
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (main.spiderApi != null) main.spiderApi.log("点击播放弹窗错误！-->" + e);
        }
    }

    /** 阶段 7：免嗅处理（sniffConfig 含缓存标记时通过 fetchPlayUrl 提取真实地址）。 @return Result 字符串或 null */
    public static String handleSniffing(XBPQ main, String playUrl) {
        String verifyStateBeforeSniff = main.verifyStateMap.get(playUrl);
        if (playUrl.startsWith("http") && !XBPQParse.isDirectLink(playUrl) && !"0".equals(verifyStateBeforeSniff)
                && main.sniffCacheEnabled()) {
            String sniffedUrl = fetchPlayUrl(main, playUrl);
            if (!sniffedUrl.isEmpty()) {
                return Result.get().parse(0).url(sniffedUrl).header(XBPQHttp.buildHeaderMap(main)).string();
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("免嗅失败：未提取到播放地址，url=" + playUrl);
            }
        }
        return null;
    }

    /** 阶段 8：加密播放解密（encrypt=1/2 + player_* JSON）。 @return Result 字符串或 null */
    public static String handleDecryption(XBPQ main, String playUrl) {
        if (playUrl.contains("encrypt=") || playUrl.contains("player_")) {
            String decrypted = decryptPlayerUrl(main, playUrl);
            if (!decrypted.isEmpty()) {
                return Result.get().parse(0).url(decrypted).header(XBPQHttp.buildHeaderMap(main)).string();
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("加密解密失败：decryptPlayerUrl 返回空，url=" + playUrl);
            }
        }
        return null;
    }

    /** 阶段 9：多级跳转解析（sniffConfig 含 e 时启用）。 @return Result 字符串或 null */
    public static String handleJumpResolution(XBPQ main, String playUrl) {
        if (main.sniffJumpEnabled() && !playUrl.isEmpty()) {
            String jumped = resolveJumpUrl(main, playUrl);
            if (!jumped.equals(playUrl)) {
                return Result.get().parse(0).url(jumped).header(XBPQHttp.buildHeaderMap(main)).string();
            }
            if (main.debug && main.spiderApi != null) {
                main.spiderApi.log("多级跳转未变化，最终url=" + jumped);
            }
        }
        return null;
    }

    /** 阶段 10：最终组装（空格归一化 + parse 判断 + Origin 头 + Result 构建）。 */
    public static String buildFinalResult(XBPQ main, String playUrl, String originalId) throws Exception {
        // 免嗅结果处理
        if (playUrl.startsWith("http") && main.isVideoFormat(playUrl)) {
            return Result.get().parse(0).url(playUrl).header(XBPQHttp.buildHeaderMap(main)).string();
        }
        playUrl = XBPQParse.normalizeSpaces(playUrl);
        if (playUrl.isEmpty() && main.spiderApi != null) {
            main.spiderApi.log("播放地址为空，原始id=" + originalId);
        }
        int parse = main.isVideoFormat(playUrl) ? 0 : 1;
        Map<String, String> finalHeaders = XBPQHttp.buildHeaderMap(main);
        if (playUrl.startsWith("http")) {
            Matcher originMatcher = HOST_PATTERN.matcher(playUrl);
            if (originMatcher.find()) {
                finalHeaders.put("Origin", originMatcher.group(1));
            }
        }
        return Result.get().parse(parse).url(playUrl).header(finalHeaders).string();
    }

    /**
     * 从 HTML 中提取播放器 JSON 对象字符串。
     * 优先使用配置"播放变量名"精确匹配，未命中则用内置宽泛正则兜底。
     * @param html 播放页源码
     * @return JSON 字符串（不含变量赋值部分），未命中返回空串
     */
    public static String extractPlayerJson(XBPQ main, String html) {
        // 优先尝试配置的自定义播放变量名
        if (main.config != null) {
            String customVarName = main.config.get("", "播放变量名", "playerVar");
            if (!customVarName.isEmpty()) {
                Pattern customPattern = Pattern.compile(
                        "(?:var\\s+)?" + Pattern.quote(customVarName) + "\\s*=\\s*(\\{[^<]+\\})");
                Matcher customMatcher = customPattern.matcher(html);
                if (customMatcher.find()) return customMatcher.group(1);
            }
        }
        // 兜底：内置宽泛正则（player_* / MacPlayer / config 等）
        Matcher playerMatcher = PLAYER_JSON_PATTERN.matcher(html);
        if (playerMatcher.find()) return playerMatcher.group(1);
        return "";
    }

    /** 解密播放器 URL（处理 encrypt=1/2 加密）。 */
    public static String decryptPlayerUrl(XBPQ main, String html) {
        try {
            String jsonStr = extractPlayerJson(main, html);
            if (jsonStr.isEmpty()) return "";
            JSONObject player = new JSONObject(jsonStr);
            String url = player.optString("url", "");
            int encrypt = player.optInt("encrypt", 0);
            if (url.isEmpty()) return "";
            if (encrypt == 1) {
                if (!main.sniffNoUrlDecode()) url = URLDecoder.decode(url, "UTF-8");
            } else if (encrypt == 2) {
                url = new String(Base64.decode(url, Base64.DEFAULT), StandardCharsets.UTF_8);
                if (!main.sniffNoUrlDecode()) url = URLDecoder.decode(url, "UTF-8");
            }
            return url;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public static String sniffCacheKey(XBPQ main, String url) {
        String digest;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sum = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : sum) sb.append(String.format("%02x", b));
            digest = sb.substring(0, 16);
        } catch (Exception e) {
            // 回退：URL 长度 + hashCode（极端情况兜底，仍优于纯 hashCode）
            digest = Integer.toHexString(url.hashCode()) + "_" + url.length();
        }
        return main.sniffCachePrefix + digest + "_" + url.length();
    }

    public static String fetchPlayUrl(XBPQ main, String url) {
        try {
            // 验证状态为"0"时跳过（按 URL 隔离）
            if ("0".equals(main.verifyStateMap.get(url))) return "";

            // 内存缓存命中：嗅探结果 Map 中已有当前 URL 的结果
            String sniffResult = main.sniffResultMap.get(url);
            if (sniffResult != null && !sniffResult.isEmpty()) return sniffResult;

            // 持久化缓存：sniffConfig 含缓存标记时检查 SharedPreferences（key 用 URL 的 SHA-1 摘要，抗碰撞）
            boolean needCache = main.sniffCacheEnabled() || main.sniffVerifyEnabled();
            String perUrlCacheKey = sniffCacheKey(main, url);
            if (needCache && !main.sniffCachePrefix.isEmpty()) {
                String cached = Init.getString(perUrlCacheKey, "");
                if (cached.length() > 1) {
                    main.sniffResultMap.put(url, cached);
                    return cached;
                }
            }

            // 请求头组装
            Map<String, String> headers = XBPQHttp.buildHeaders(main);
            String postBody = ""; // 由 cookieStr 中 ;post; 段指定的 POST body

            // Cookie / Referer 处理：cookieStr 形如 "k=v;k=v" 或 "k=v;k=v;post;body"
            // 也可仅配置为一个 URL 字符串（无 = 视为 Referer）
            if (!main.cookieStr.isEmpty()) {
                String cookiePart = main.cookieStr;
                if (main.cookieStr.contains(";post;")) {
                    String[] postParts = main.cookieStr.split(";post;", 2);
                    cookiePart = postParts[0];
                    if (postParts.length > 1) postBody = postParts[1];
                }
                // 含 = 视作 Cookie 键值对集合，按 & 或 ; 切分
                if (cookiePart.contains("=")) {
                    StringBuilder cookieBuilder = new StringBuilder();
                    for (String pair : cookiePart.split("[&;]")) {
                        String trimmed = pair.trim();
                        // 过滤空串、纯键（无值）、缺失键的项
                        int equalsPos = trimmed.indexOf('=');
                        if (equalsPos <= 0 || equalsPos == trimmed.length() - 1) continue;
                        String key = trimmed.substring(0, equalsPos).trim();
                        String value = trimmed.substring(equalsPos + 1).trim();
                        if (key.isEmpty() || value.isEmpty()) continue;
                        if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                        cookieBuilder.append(key).append('=').append(value);
                    }
                    if (cookieBuilder.length() > 0) {
                        headers.put("Cookie", cookieBuilder.toString());
                    }
                    // Referer 默认取站点根 URL，保证相对完整
                    if (!main.baseUrl.isEmpty()) headers.put("Referer", main.baseUrl);
                } else if (!cookiePart.isEmpty()) {
                    // 不含 = 时把 cookiePart 当作 Referer URL
                    headers.put("Referer", cookiePart);
                }
            } else if (!main.baseUrl.isEmpty()) {
                headers.put("Referer", main.baseUrl);
            }

            // Origin 提取
            Matcher originMatcher = HOST_PATTERN.matcher(url);
            if (originMatcher.find()) {
                headers.put("Origin", originMatcher.group(1));
            }

            // HTTP 请求
            String html;
            String cleanUrl = url.split(";")[0];
            if (url.contains(";post")) {
                // POST 请求：postBody 优先取 cookieStr 的 ;post; 段，否则取配置"POST请求数据"
                String body = !postBody.isEmpty() ? postBody
                        : (main.config != null ? main.config.get("", "POST请求数据", "sea_PtBody") : "");
                html = OkHttp.post(cleanUrl, body, headers);
            } else {
                html = OkHttp.string(cleanUrl, headers);
            }
            if (html == null || html.isEmpty()) return "";

            // 提取播放地址
            String playUrl = "";
            // 优先从播放器 JSON 中提取（支持配置"播放变量名"）
            String playerJson = extractPlayerJson(main, html);
            if (!playerJson.isEmpty()) {
                try {
                    JSONObject player = new JSONObject(playerJson);
                    playUrl = player.optString("url", "");
                    int encrypt = player.optInt("encrypt", 0);
                    if (encrypt == 1) {
                        playUrl = URLDecoder.decode(playUrl, "UTF-8");
                    } else if (encrypt == 2) {
                        playUrl = new String(Base64.decode(playUrl, Base64.DEFAULT), StandardCharsets.UTF_8);
                        playUrl = URLDecoder.decode(playUrl, "UTF-8");
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }

            // 播放请求头 cookie 提取与去重
            String playHeader = main.config != null ? main.config.get("", "播放请求头", "直接播放直链视频请求头") : "";
            if (!playUrl.isEmpty() && (playHeader.contains("ookie") || playUrl.contains("ookie"))) {
                try {
                    // 优先从 playUrl 中提取 Cookie$xxx# 段，否则从 playHeader 提取
                    String cookieExtracted = playUrl;
                    Matcher cookieMatcher = COOKIE_PATTERN.matcher(playUrl);
                    if (cookieMatcher.find()) {
                        cookieExtracted = cookieMatcher.group(1);
                    } else {
                        Matcher headerCookieMatcher = COOKIE_PATTERN.matcher(playHeader);
                        if (headerCookieMatcher.find()) {
                            cookieExtracted = headerCookieMatcher.group(1);
                        }
                    }
                    // cookie 去重：按 ; 或 $ 切分后保留首次出现的键
                    StringBuilder dedupCookie = new StringBuilder();
                    Set<String> seenKeys = new HashSet<>();
                    for (String pair : cookieExtracted.split("[;$]")) {
                        String trimmed = pair.trim();
                        if (trimmed.isEmpty()) continue;
                        int equalsPos = trimmed.indexOf('=');
                        String cookieKey = equalsPos > 0 ? trimmed.substring(0, equalsPos).trim() : trimmed;
                        if (!seenKeys.add(cookieKey)) continue; // 已存在则跳过
                        if (dedupCookie.length() > 0) dedupCookie.append("; ");
                        dedupCookie.append(trimmed);
                    }
                    if (dedupCookie.length() > 0) {
                        headers.put("Cookie", dedupCookie.toString());
                    }
                } catch (Exception ignored) {
                }
            }

            // 内存缓存 + 持久化缓存写入（按 URL 隔离）
            if (playUrl.length() > 1) {
                main.sniffResultMap.put(url, playUrl);
                if (!main.sniffCachePrefix.isEmpty()) {
                    Init.put(perUrlCacheKey, playUrl);
                }
            }
            if ("cookie".equals(main.playMode)) {
                Notify.show(playUrl);
            }
            return playUrl;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 验证码 fetch/fetchPost 处理：根据"验证"配置构造验证 URL 并请求。
     *
     * <p>当播放页源码含验证关键词时，从"验证"配置中提取验证 URL，
     * 通过 fetch（GET）或 fetchPost（POST）方式请求验证 URL 以触发验证流程，
     * 之后重新请求播放页源码校验是否仍含验证关键词。</p>
     *
     * @param playUrl  播放页 URL
     * @param html     播放页源码（仅用于日志，实际不参与请求）
     * @param verifyConfig 验证配置（"验证"键的值，格式 "fetch;url[;body]" 或 "fetchPost;url[;body]"）
     * @return true 表示验证成功（不再含验证关键词），false 表示失败或异常
     */
    public static boolean handleVerification(XBPQ main, String playUrl, String html, String verifyConfig) {
        if (verifyConfig == null || verifyConfig.isEmpty()) return false;
        try {
            // 配置格式：mode;verifyUrl[;postBody]，split 限制 3 段以兼容 body 中含 ; 的情况
            String[] configParts = verifyConfig.split(";", 3);
            String mode = configParts.length > 0 ? configParts[0].trim() : "fetch";
            String verifyUrl = configParts.length > 1 ? configParts[1].trim() : "";

            // 验证 URL 为空时使用播放页 URL
            if (verifyUrl.isEmpty()) verifyUrl = playUrl;

            // URL 补全（相对路径）
            if (!verifyUrl.startsWith("http") && !verifyUrl.startsWith("//")) {
                verifyUrl = main.baseUrl + (verifyUrl.startsWith("/") ? "" : "/") + verifyUrl;
            }

            // 触发验证流程：fetchPost 走 POST，其它均走 GET
            if ("fetchPost".equals(mode)) {
                String postBody = configParts.length > 2 ? configParts[2] : "";
                String result = OkHttp.post(verifyUrl, postBody, XBPQHttp.buildHeaders(main));
                if (main.spiderApi != null && result != null) {
                    main.spiderApi.log("验证POST结果--> " + (result.length() > 100 ? result.substring(0, 100) : result));
                }
            } else {
                String result = OkHttp.string(verifyUrl, XBPQHttp.buildHeaders(main));
                if (main.spiderApi != null && result != null) {
                    main.spiderApi.log("验证GET结果--> " + (result.length() > 100 ? result.substring(0, 100) : result));
                }
            }

            // 校验验证是否成功：重新请求播放页，若仍含验证关键词则视为失败
            String verifyResult = XBPQHttp.fetchHtml(main, playUrl);
            if (verifyResult == null || verifyResult.isEmpty()) {
                if (main.spiderApi != null) main.spiderApi.log("验证后获取源码为空，验证失败");
                return false;
            }
            for (String keyword : main.getVerifyKeywords()) {
                if (verifyResult.contains(keyword)) {
                    if (main.spiderApi != null) main.spiderApi.log("验证后仍含关键词[" + keyword + "]，验证失败");
                    return false;
                }
            }
            main.verifyStateMap.put(playUrl, "1");
            Notify.show("验证成功！");
            return true;
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (main.spiderApi != null) main.spiderApi.log("验证处理错误！-->" + e);
            return false;
        }
    }

    public static String resolveJumpUrl(XBPQ main, String url) {
        try {
            int jumpCount = 1;
            Matcher jumpCountMatcher = JUMP_COUNT_PATTERN.matcher(main.sniffConfig);
            if (jumpCountMatcher.find()) jumpCount = Integer.parseInt(jumpCountMatcher.group(1));

            String[] jumpKeys = {"跳转播放链接", "二次跳转播放链接", "三次跳转播放链接", "四次跳转播放链接", "五次跳转播放链接"};
            String current = url;
            for (int i = 0; i < jumpCount && i < jumpKeys.length; i++) {
                String jumpSel = main.config.get("", jumpKeys[i]);
                if (jumpSel.isEmpty()) break;
                String html = XBPQHttp.fetchHtml(main, current.split(";")[0]);
                if (html == null || html.isEmpty()) break;
                String next = XBPQParse.pick(main, html, jumpSel);
                if (next.isEmpty()) break;
                if (!next.startsWith("http") && !next.startsWith("//")) {
                    next = main.baseUrl + (next.startsWith("/") ? "" : "/") + next;
                }
                current = next;
            }
            return current;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }
}
