package com.m3u8.parser.model;

import java.util.Objects;

/**
 * 轨道数据（包含URI、时长、加密信息等）
 */
public class TrackData {

    private String mUri;
    private TrackInfo mTrackInfo;
    private EncryptionData mEncryptionData;
    private String mProgramDateTime;
    private boolean mHasDiscontinuity;

    public TrackData() {
    }

    private TrackData(String uri, TrackInfo trackInfo, EncryptionData encryptionData, String programDateTime, boolean hasDiscontinuity) {
        mUri = uri;
        mTrackInfo = trackInfo;
        mEncryptionData = encryptionData;
        mProgramDateTime = programDateTime;
        mHasDiscontinuity = hasDiscontinuity;
    }

    public String getUri() {
        return mUri;
    }

    public void setUri(String uri) {
        this.mUri = uri;
    }

    public boolean hasTrackInfo() {
        return mTrackInfo != null;
    }

    public TrackInfo getTrackInfo() {
        return mTrackInfo;
    }

    public void setTrackInfo(TrackInfo trackInfo) {
        this.mTrackInfo = trackInfo;
    }

    public boolean hasEncryptionData() {
        return mEncryptionData != null;
    }

    public EncryptionData getEncryptionData() {
        return mEncryptionData;
    }

    public boolean isEncrypted() {
        return hasEncryptionData() && mEncryptionData.getMethod() != null && mEncryptionData.getMethod() != EncryptionMethod.NONE;
    }

    public boolean hasProgramDateTime() {
        return mProgramDateTime != null && mProgramDateTime.length() > 0;
    }

    public String getProgramDateTime() {
        return mProgramDateTime;
    }

    public void setProgramDateTime(String programDateTime) {
        this.mProgramDateTime = programDateTime;
    }

    public boolean hasDiscontinuity() {
        return mHasDiscontinuity;
    }

    public void setDiscontinuity(boolean discontinuity) {
        this.mHasDiscontinuity = discontinuity;
    }

    public EncryptionData getEncryption() {
        return mEncryptionData;
    }

    public Builder buildUpon() {
        return new Builder(getUri(), mTrackInfo, mEncryptionData, mHasDiscontinuity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackData trackData = (TrackData) o;
        return mHasDiscontinuity == trackData.mHasDiscontinuity &&
                Objects.equals(mUri, trackData.mUri) &&
                Objects.equals(mTrackInfo, trackData.mTrackInfo) &&
                Objects.equals(mEncryptionData, trackData.mEncryptionData) &&
                Objects.equals(mProgramDateTime, trackData.mProgramDateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUri, mTrackInfo, mEncryptionData, mProgramDateTime, mHasDiscontinuity);
    }

    public static class Builder {
        private String mUri;
        private TrackInfo mTrackInfo;
        private EncryptionData mEncryptionData;
        private String mProgramDateTime;
        private boolean mHasDiscontinuity;

        public Builder() {
        }

        private Builder(String uri, TrackInfo trackInfo, EncryptionData encryptionData, boolean hasDiscontinuity) {
            mUri = uri;
            mTrackInfo = trackInfo;
            mEncryptionData = encryptionData;
            mHasDiscontinuity = hasDiscontinuity;
        }

        public Builder withUri(String url) {
            mUri = url;
            return this;
        }

        public Builder withTrackInfo(TrackInfo trackInfo) {
            mTrackInfo = trackInfo;
            return this;
        }

        public Builder withEncryptionData(EncryptionData encryptionData) {
            mEncryptionData = encryptionData;
            return this;
        }

        public Builder withProgramDateTime(String programDateTime) {
            mProgramDateTime = programDateTime;
            return this;
        }

        public Builder withDiscontinuity(boolean hasDiscontinuity) {
            mHasDiscontinuity = hasDiscontinuity;
            return this;
        }

        public TrackData build() {
            return new TrackData(mUri, mTrackInfo, mEncryptionData, mProgramDateTime, mHasDiscontinuity);
        }
    }
}
