package com.github.catvod.crawler;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();
    private static boolean debugEnabled = false;
    private static boolean errorEnabled = true;
    private static boolean warningEnabled = false;
    private static boolean infoEnabled = false;
    // Locale.US 保证小数点始终是 .，日志格式全局一致
    private static final Locale LOG_LOCALE = Locale.US;
    // 每个线程持有独立的 SimpleDateFormat，避免 synchronized 锁竞争，线程结束时自动清理
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", LOG_LOCALE));

    // ==================== 日志队列（内部缓冲，供外部接口读取） ====================
    // BlockingQueue 支持带超时的 offer，避免队列满时线程阻塞
    private static final BlockingQueue<String> LOG_QUEUE = new ArrayBlockingQueue<>(1024);
    private static final AtomicBoolean LOG_RUNNING = new AtomicBoolean(true);
    // 记录因队列满而丢弃的日志条数
    private static final AtomicInteger DROPPED_COUNT = new AtomicInteger(0);

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void setErrorEnabled(boolean enabled) {
        errorEnabled = enabled;
    }

    public static void setWarningEnabled(boolean enabled) {
        warningEnabled = enabled;
    }

    public static void setInfoEnabled(boolean enabled) {
        infoEnabled = enabled;
    }

    /**
     * 一键关闭/开启所有日志输出（正式环境调用 setAllEnabled(false) 关闭）
     */
    public static void setAllEnabled(boolean enabled) {
        debugEnabled = enabled;
        errorEnabled = enabled;
        warningEnabled = enabled;
        infoEnabled = enabled;
    }

    // ==================== 日志输出 ====================

    public static void log(Throwable e) {
        if (!errorEnabled) return;
        String ts = getTimestamp();
        String msg = ts + " ERROR: " + e.getMessage();
        Log.e(TAG, msg, e);
        enqueue(msg + "\n" + Log.getStackTraceString(e));
    }

    public static void log(String msg) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " DEBUG: " + msg;
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void info(String msg) {
        if (!infoEnabled) return;
        String prefixed = getTimestamp() + " INFO: " + msg;
        Log.i(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void warn(String msg) {
        if (!warningEnabled) return;
        String prefixed = getTimestamp() + " WARN: " + msg;
        Log.w(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void error(String msg) {
        if (!errorEnabled) return;
        String prefixed = getTimestamp() + " ERROR: " + msg;
        Log.e(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void error(String msg, Throwable e) {
        if (!errorEnabled) return;
        String prefixed = getTimestamp() + " ERROR: " + msg;
        Log.e(TAG, prefixed, e);
        enqueue(prefixed + "\n" + Log.getStackTraceString(e));
    }

    public static void verbose(String msg) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " VERBOSE: " + msg;
        Log.v(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void d(String tag, String msg) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " DEBUG [" + tag + "]: " + msg;
        Log.d(tag, prefixed);
        enqueue(prefixed);
    }

    public static void i(String tag, String msg) {
        if (!infoEnabled) return;
        String prefixed = getTimestamp() + " INFO [" + tag + "]: " + msg;
        Log.i(tag, prefixed);
        enqueue(prefixed);
    }

    public static void w(String tag, String msg) {
        if (!warningEnabled) return;
        String prefixed = getTimestamp() + " WARN [" + tag + "]: " + msg;
        Log.w(tag, prefixed);
        enqueue(prefixed);
    }

    public static void e(String tag, String msg) {
        if (!errorEnabled) return;
        String prefixed = getTimestamp() + " ERROR [" + tag + "]: " + msg;
        Log.e(tag, prefixed);
        enqueue(prefixed);
    }

    public static void e(String tag, String msg, Throwable e) {
        if (!errorEnabled) return;
        String prefixed = getTimestamp() + " ERROR [" + tag + "]: " + msg;
        Log.e(tag, prefixed, e);
        enqueue(prefixed + "\n" + Log.getStackTraceString(e));
    }

    public static void v(String tag, String msg) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " VERBOSE [" + tag + "]: " + msg;
        Log.v(tag, prefixed);
        enqueue(prefixed);
    }

    public static void logMethod(String methodName) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " METHOD: " + methodName + "()";
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logMethodEnter(String methodName) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " ENTER: " + methodName + "()";
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logMethodExit(String methodName) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " EXIT: " + methodName + "()";
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logMethodExit(String methodName, Object result) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " EXIT: " + methodName + "() = "
                + (result != null ? result.toString() : "null");
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logRequest(String url, String method) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " REQUEST: " + method + " " + url;
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logResponse(String url, int code, long duration) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " RESPONSE: " + url + " - Code: " + code + " - Duration: " + duration + "ms";
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logError(String context, String error) {
        if (!errorEnabled) return;
        String prefixed = getTimestamp() + " ERROR [" + context + "]: " + error;
        Log.e(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logException(String context, Exception e) {
        if (!errorEnabled) return;
        String prefixed = getTimestamp() + " EXCEPTION [" + context + "]: " + e.getMessage();
        Log.e(TAG, prefixed, e);
        enqueue(prefixed + "\n" + Log.getStackTraceString(e));
    }

    public static void logPerformance(String operation, long duration) {
        if (!debugEnabled) return;
        String prefixed = getTimestamp() + " PERFORMANCE: " + operation + " took " + duration + "ms";
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    public static void logMemory(String context) {
        if (!debugEnabled) return;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        String prefixed = getTimestamp() + " MEMORY [" + context + "]: " + formatBytes(used) + " / " + formatBytes(max);
        Log.d(TAG, prefixed);
        enqueue(prefixed);
    }

    // ==================== 日志队列管理 ====================

    /**
     * 添加日志到内部队列。队列满时阻塞等待（最多 100ms），超时则丢弃并计数。
     * 已停止时（LOG_RUNNING=false）直接丢弃，不调用队列操作。
     */
    private static void enqueue(String msg) {
        if (!LOG_RUNNING.get()) return;
        try {
            if (!LOG_QUEUE.offer(msg, 100, TimeUnit.MILLISECONDS)) {
                DROPPED_COUNT.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 入队失败不影响主流程
        }
    }

    /**
     * 复制当前队列内容（非破坏性），不会清空队列。
     * 多次调用可拿到相同内容，适合多个端点同时读取。
     * 注意：使用 toArray 获取原子快照，避免遍历期间并发修改导致的不一致。
     */
    public static String peekLogContent() {
        StringBuilder sb = new StringBuilder();
        for (String item : LOG_QUEUE.toArray(new String[0])) {
            if (!item.trim().isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(item);
            }
        }
        // 附加丢弃计数（只读，不清零）
        int dropped = DROPPED_COUNT.get();
        if (dropped > 0) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("[dropped: ").append(dropped).append(" entries]");
        }
        return sb.toString();
    }

    /**
     * 获取并清空所有缓冲的日志内容（破坏性读取）。
     * 如需保留历史日志，请使用 peekLogContent() 代替。
     * 注意：此方法会同时清零 DROPPED_COUNT，先 peek 再 get 时 dropped 计数可能重复出现。
     */
    public static String getLogContent() {
        StringBuilder sb = new StringBuilder();
        String item;
        while ((item = LOG_QUEUE.poll()) != null) {
            if (!item.trim().isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(item);
            }
        }
        // 附加丢弃计数
        int dropped = DROPPED_COUNT.getAndSet(0);
        if (dropped > 0) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("[dropped: ").append(dropped).append(" entries]");
        }
        return sb.toString();
    }

    /**
     * 返回原始日志文本（未编码）。
     * Content-Type: text/plain; charset=utf-8，调用方直接按 UTF-8 读取即可。
     *
     * @return Object[]{statusCode, contentType, InputStream}，失败时返回 null
     */
    public static Object[] getLogData() {
        try {
            String content = peekLogContent();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream(bytes)};
        } catch (Exception e) {
            log(e);
            return null;
        }
    }

    /**
     * 返回 Base64 编码的日志文本，适合通过 HTTP 接口传输二进制安全的场景。
     * 调用方需自行 Base64.decode() 后以 UTF-8 解析。
     *
     * @return Object[]{statusCode, contentType, InputStream}，失败时返回 null
     */
    public static Object[] getLogDataBase64() {
        try {
            String content = peekLogContent();
            byte[] raw = content.getBytes(StandardCharsets.UTF_8);
            String b64 = Base64.encodeToString(raw, Base64.NO_WRAP);
            return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream(b64.getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            log(e);
            return null;
        }
    }

    /**
     * 返回 HTML 格式的日志页面，供浏览器直接查看。
     * 按日志级别着色：ERROR=红、WARN=橙、INFO=绿、其他=白。
     *
     * @return Object[]{statusCode, contentType, InputStream}，失败时返回 null
     */
    public static Object[] getLogFile() {
        try {
            String content = peekLogContent();
            String colored = colorizeByLevel(escapeHtml(content));
            String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                    + "<title>Spider Debug Log</title>"
                    + "<style>"
                    + "body{background:#1a1a1a;color:#ddd;font-family:Consolas,Monaco,monospace;padding:16px;}"
                    + "h2{color:#4fc3f7;margin-top:0;}"
                    + "pre{white-space:pre-wrap;word-break:break-all;}"
                    + "</style></head>"
                    + "<body><h2>Spider Debug Log</h2><pre>" + colored + "</pre></body></html>";
            return new Object[]{200, "text/html; charset=utf-8",
                    new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            log(e);
            return null;
        }
    }

    /**
     * 停止日志队列，释放资源。调用后日志不再入队。
     * 可通过 restartLogQueue() 恢复。
     */
    public static void stopLogQueue() {
        LOG_RUNNING.set(false);
        LOG_QUEUE.clear();
        DROPPED_COUNT.set(0);
        DATE_FORMAT.remove();
    }

    /**
     * 重启日志队列（stopLogQueue 后可调用恢复）。
     */
    public static void restartLogQueue() {
        LOG_RUNNING.set(true);
        LOG_QUEUE.clear();
        DROPPED_COUNT.set(0);
    }

    // ==================== 辅助工具 ====================

    /**
     * URL 解码，支持连续多次编码（最多循环 10 次，防止极端情况死循环）
     */
    public static String decodeUrl(String encoded) {
        if (encoded == null || encoded.isEmpty()) return encoded;
        try {
            String prev;
            String result = encoded;
            for (int i = 0; i < 10; i++) {
                prev = result;
                result = java.net.URLDecoder.decode(result, "UTF-8");
                if (result.equals(prev)) break;
            }
            return result;
        } catch (Exception e) {
            return encoded;
        }
    }

    /**
     * 记录错误日志：解码 URL 并拼接异常信息，一次性写入队列
     */
    public static void logError(String encodedMessage, Exception exc) {
        String decoded = decodeUrl(encodedMessage == null ? "" : encodedMessage);
        String msg = getTimestamp() + " ERROR_DECODE [" + decoded + "]: " + exc.toString();
        Log.e(TAG, msg, exc);
        enqueue(msg + "\n" + Log.getStackTraceString(exc));
    }

    /**
     * 截取字符串中间部分，去除头部 headLen 和尾部 tailLen 个字符。
     * 例：substring("abcdef", 1, 1) → "bcde"
     */
    public static String substring(String str, int headLen, int tailLen) {
        if (str == null) return "";
        int start = headLen;
        int end = str.length() - tailLen;
        if (start < 0) start = 0;
        if (end > str.length()) end = str.length();
        if (end <= start) return "";
        return str.substring(start, end);
    }

    // ==================== 私有方法 ====================

    private static String getTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(LOG_LOCALE, "%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(LOG_LOCALE, "%.2f MB", bytes / (1024.0 * 1024));
        return String.format(LOG_LOCALE, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * HTML 转义，覆盖全部 5 个特殊字符
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * 按日志级别给行着色：ERROR→红、WARN→橙、INFO→绿
     * 使用单词边界匹配，避免 URL/堆栈中的假阳性（如 "error-page" 不被误判为 ERROR）
     */
    private static String colorizeByLevel(String escaped) {
        if (escaped == null || escaped.isEmpty()) return escaped;
        String[] lines = escaped.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String cls = "color:#ddd";
            if (line.matches(".*\\bERROR\\b.*"))        cls = "color:#ef5350";
            else if (line.matches(".*\\bWARN\\b.*"))    cls = "color:#ffb74d";
            else if (line.matches(".*\\bINFO\\b.*"))    cls = "color:#81c784";
            sb.append("<span style=\"").append(cls).append("\">").append(line).append("</span>\n");
        }
        return sb.toString();
    }
}
