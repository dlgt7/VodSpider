package com.github.catvod.crawler;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();
    private static boolean debugEnabled = true;
    private static boolean errorEnabled = true;
    private static boolean warningEnabled = true;
    private static boolean infoEnabled = true;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

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

    public static void log(Throwable e) {
        if (errorEnabled) {
            Log.e(TAG, getTimestamp() + " ERROR: " + e.getMessage(), e);
        }
    }

    public static void log(String msg) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " DEBUG: " + msg);
        }
    }

    public static void info(String msg) {
        if (infoEnabled) {
            Log.i(TAG, getTimestamp() + " INFO: " + msg);
        }
    }

    public static void warn(String msg) {
        if (warningEnabled) {
            Log.w(TAG, getTimestamp() + " WARN: " + msg);
        }
    }

    public static void error(String msg) {
        if (errorEnabled) {
            Log.e(TAG, getTimestamp() + " ERROR: " + msg);
        }
    }

    public static void error(String msg, Throwable e) {
        if (errorEnabled) {
            Log.e(TAG, getTimestamp() + " ERROR: " + msg, e);
        }
    }

    public static void verbose(String msg) {
        if (debugEnabled) {
            Log.v(TAG, getTimestamp() + " VERBOSE: " + msg);
        }
    }

    public static void d(String tag, String msg) {
        if (debugEnabled) {
            Log.d(tag, getTimestamp() + " DEBUG: " + msg);
        }
    }

    public static void i(String tag, String msg) {
        if (infoEnabled) {
            Log.i(tag, getTimestamp() + " INFO: " + msg);
        }
    }

    public static void w(String tag, String msg) {
        if (warningEnabled) {
            Log.w(tag, getTimestamp() + " WARN: " + msg);
        }
    }

    public static void e(String tag, String msg) {
        if (errorEnabled) {
            Log.e(tag, getTimestamp() + " ERROR: " + msg);
        }
    }

    public static void e(String tag, String msg, Throwable e) {
        if (errorEnabled) {
            Log.e(tag, getTimestamp() + " ERROR: " + msg, e);
        }
    }

    public static void v(String tag, String msg) {
        if (debugEnabled) {
            Log.v(tag, getTimestamp() + " VERBOSE: " + msg);
        }
    }

    public static void logMethod(String methodName) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " METHOD: " + methodName + "()");
        }
    }

    public static void logMethodEnter(String methodName) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " ENTER: " + methodName + "()");
        }
    }

    public static void logMethodExit(String methodName) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " EXIT: " + methodName + "()");
        }
    }

    public static void logMethodExit(String methodName, Object result) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " EXIT: " + methodName + "() = " + (result != null ? result.toString() : "null"));
        }
    }

    public static void logRequest(String url, String method) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " REQUEST: " + method + " " + url);
        }
    }

    public static void logResponse(String url, int code, long duration) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " RESPONSE: " + url + " - Code: " + code + " - Duration: " + duration + "ms");
        }
    }

    public static void logError(String context, String error) {
        if (errorEnabled) {
            Log.e(TAG, getTimestamp() + " ERROR [" + context + "]: " + error);
        }
    }

    public static void logException(String context, Exception e) {
        if (errorEnabled) {
            Log.e(TAG, getTimestamp() + " EXCEPTION [" + context + "]: " + e.getMessage(), e);
        }
    }

    public static void logPerformance(String operation, long duration) {
        if (debugEnabled) {
            Log.d(TAG, getTimestamp() + " PERFORMANCE: " + operation + " took " + duration + "ms");
        }
    }

    public static void logMemory(String context) {
        if (debugEnabled) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            Log.d(TAG, getTimestamp() + " MEMORY [" + context + "]: " + formatBytes(usedMemory) + " / " + formatBytes(maxMemory));
        }
    }

    private static String getTimestamp() {
        return dateFormat.format(new Date());
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}