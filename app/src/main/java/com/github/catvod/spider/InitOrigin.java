package com.github.catvod.spider;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Origin initializer that manages native library loading and thread scheduling.
 * Loads FishGuard.so from assets and exposes it via {@link #e} for {@link FishCrypto}.
 */
public class InitOrigin {

    private static final String TAG = "InitOrigin";

    /** Native library path, set by external initialization before {@link FishCrypto} class loads. */
    public static String e = "";

    /** Delegates to {@link Init#run(Runnable)} for UI thread execution. */
    public static void run(Runnable runnable) {
        Init.run(runnable);
    }

    /** Delegates to {@link Init#execute(Runnable)} for background thread execution. */
    public static void execute(Runnable runnable) {
        Init.execute(runnable);
    }

    /** Delegates to {@link Init#context()} for application context access. */
    public static Application context() {
        return Init.context();
    }

    /**
     * Initializes with context, auto-detecting and loading the appropriate .so library from assets.
     * This should be called from Application.onCreate() or Spider.init() before using FishCrypto.
     */
    public static void initSafe(Context context) {
        initSafe(context, null);
    }

    /**
     * Initializes with context and optional explicit .so path.
     * If soPath is null, automatically copies the appropriate library from assets.
     */
    public static void initSafe(Context context, String soPath) {
        if (context == null) return;
        
        Application app = null;
        Context appContext = context.getApplicationContext();
        if (appContext instanceof Application) {
            app = (Application) appContext;
        } else if (context instanceof Application) {
            app = (Application) context;
        }
        
        if (app == null) return;
        
        // Set application reference
        Init.init(context);
        
        // Load .so library
        if (soPath != null && !soPath.isEmpty()) {
            e = soPath;
        } else {
            e = copyAssetSo(context);
        }
        
        // Try to reload if not loaded
        if (!FishCrypto.isLoaded() && e != null && !e.isEmpty()) {
            FishCrypto.tryReload(e);
        }
        
        Log.d(TAG, "initSafe: soPath=" + e + ", loaded=" + FishCrypto.isLoaded());
    }

    /**
     * Copies the appropriate .so library from assets to the app's private directory.
     * Returns the absolute path to the copied file, or null on failure.
     */
    private static String copyAssetSo(Context context) {
        try {
            // Detect CPU architecture
            String abi = Build.SUPPORTED_ABIS[0];
            String soName;
            
            if (abi.contains("arm64") || abi.contains("x86_64")) {
                soName = "FishGuard-v8.so";
            } else {
                soName = "FishGuard-v7.so";
            }
            
            File soFile = new File(context.getFilesDir(), soName);
            
            // Copy from assets if not exists or force update
            if (!soFile.exists()) {
                InputStream is = context.getAssets().open(soName);
                FileOutputStream fos = new FileOutputStream(soFile);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();
                Log.d(TAG, "Copied " + soName + " to " + soFile.getAbsolutePath());
            }
            
            return soFile.getAbsolutePath();
        } catch (Exception ex) {
            Log.e(TAG, "Failed to copy .so library from assets", ex);
            return null;
        }
    }
}