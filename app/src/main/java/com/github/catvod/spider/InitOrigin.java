package com.github.catvod.spider;

import android.app.Application;

/**
 * Origin initializer wrapper that delegates to {@link Init} and exposes the native library path.
 * Used by {@link FishCrypto} and other Spider classes for thread scheduling and context access.
 */
public class InitOrigin {

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
}