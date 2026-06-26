package com.m3u8.parser.model;

import java.util.Objects;

/**
 * 轨道信息
 */
public class TrackInfo {
    public final float duration;
    public final String title;

    public TrackInfo(float duration, String title) {
        this.duration = duration;
        this.title = title;
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, title);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TrackInfo)) {
            return false;
        }
        TrackInfo other = (TrackInfo) o;
        return this.duration == other.duration && Objects.equals(this.title, other.title);
    }
}
