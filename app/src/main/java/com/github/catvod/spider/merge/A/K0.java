package com.github.catvod.spider.merge.A;

import java.util.ArrayList;

/** Site class registry consumed by {@link com.github.catvod.spider.FishConfig#homeContent(boolean)}. */
public class K0 {

    public static String[] f;
    public static String[] g;
    public static String[] h;

    public static boolean isReady() {
        return false;
    }

    public static int findIndex(String needle, String[] haystack) {
        if (haystack == null) {
            return -1;
        }
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i] != null && haystack[i].equals(needle)) {
                return i;
            }
        }
        return -1;
    }

    public static ArrayList<Integer> d(String name, String[] keys, String[] values) {
        return new ArrayList<>();
    }
}
