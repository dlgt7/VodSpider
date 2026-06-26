package com.m3u8.parser.model;

import com.m3u8.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * M3U8播放列表
 */
public class PlayList {
    private List<String> headers = new ArrayList<>();
    private final List<TrackData> trackDataList = new ArrayList<>();
    private int contentType;
    private List<String> subUri = new ArrayList<>();
    private String m3u8;
    private String url;

    public PlayList(String m3u8, String url) {
        this.m3u8 = m3u8;
        this.url = url;
        parse();
    }

    public String getM3u8() {
        return m3u8;
    }

    public void setM3u8(String m3u8) {
        this.m3u8 = m3u8;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<TrackData> getTrackDataList() {
        return trackDataList;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public List<String> getSubUri() {
        return subUri;
    }

    public void setSubUri(List<String> subUri) {
        this.subUri = subUri;
    }

    private void parse() {
        Parser.parse(this);
    }

    @Override
    public String toString() {
        return Parser.printPlaylist(this);
    }
}
