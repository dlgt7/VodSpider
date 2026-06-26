package com.m3u8.parser;

import com.m3u8.parser.model.ContextType;
import com.m3u8.parser.model.PlayList;
import com.m3u8.parser.model.TrackData;
import com.m3u8.parser.model.TrackInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3U8解析器 - 健壮版
 * 
 * 支持解析：
 * - Master Playlist (含#EXT-X-STREAM-INF)
 * - Media Playlist (含媒体片段)
 * 
 * 解析内容：
 * - EXTINF 时长
 * - EXT-X-DISCONTINUITY 不连续标记
 * - EXT-X-PROGRAM-DATE-TIME 时间戳
 * - EXT-X-KEY 加密信息
 * - 相对URI转绝对URI
 */
public class Parser {

    // 正则表达式
    private static final Pattern EXTINF = Pattern.compile("#EXTINF:([\\d.]+)(?:,.*)?");
    private static final Pattern EXT_X_DISCONTINUITY = Pattern.compile("#EXT-X-DISCONTINUITY");
    private static final Pattern EXT_X_PROGRAM_DATE_TIME = Pattern.compile("#EXT-X-PROGRAM-DATE-TIME:(.+)");
    private static final Pattern EXT_X_KEY = Pattern.compile("#EXT-X-KEY:(.+)");
    private static final Pattern EXT_X_STREAM_INF = Pattern.compile("#EXT-X-STREAM-INF");

    /**
     * 解析M3U8内容
     */
    public static void parse(PlayList playList) {
        if (playList.getM3u8() == null || playList.getM3u8().isEmpty()) return;

        String[] lines = playList.getM3u8().split("\\r?\\n");

        if (isMasterPlaylist(lines)) {
            masterParse(playList, lines);
        } else {
            mediaParse(playList, lines);
        }
    }

    /**
     * 判断是否为Master Playlist
     */
    private static boolean isMasterPlaylist(String[] lines) {
        for (String line : lines) {
            if (line.startsWith("#EXT-X-STREAM-INF")) return true;
        }
        return false;
    }

    /**
     * 解析Master Playlist
     */
    private static void masterParse(PlayList playList, String[] lines) {
        playList.setContentType(ContextType.MASTER);
        List<String> headers = new ArrayList<>();
        List<String> subUri = new ArrayList<>();

        for (String line : lines) {
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                headers.add(line);
            } else if (!line.startsWith("#") && !line.trim().isEmpty()) {
                subUri.add(line.trim());
            } else if (line.startsWith("#")) {
                headers.add(line);
            }
        }

        playList.setHeaders(headers);
        playList.setSubUri(subUri);
    }

    /**
     * 解析Media Playlist
     */
    private static void mediaParse(PlayList playList, String[] lines) {
        playList.setContentType(ContextType.MEDIA);
        List<String> headers = new ArrayList<>();
        List<TrackData> tracks = new ArrayList<>();

        float currentDuration = 0;
        boolean hasDiscontinuity = false;
        String programDateTime = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // 常规头信息
            if (line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE") ||
                line.startsWith("#EXT-X-VERSION") ||
                line.startsWith("#EXT-X-PLAYLIST-TYPE")) {
                headers.add(rawLine);
                continue;
            }

            // DISCONTINUITY标记
            if (line.equals("#EXT-X-DISCONTINUITY")) {
                hasDiscontinuity = true;
                headers.add(rawLine);
                continue;
            }

            // 播放列表结束
            if (line.equals("#EXT-X-ENDLIST")) {
                headers.add(rawLine);
                continue;
            }

            // 加密信息
            if (line.startsWith("#EXT-X-KEY:")) {
                headers.add(rawLine);
                continue;
            }

            // 时间戳
            if (line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) {
                Matcher m = EXT_X_PROGRAM_DATE_TIME.matcher(line);
                if (m.matches()) programDateTime = m.group(1);
                headers.add(rawLine);
                continue;
            }

            // EXTINF时长
            if (line.startsWith("#EXTINF:")) {
                Matcher m = EXTINF.matcher(line);
                if (m.matches()) {
                    try {
                        currentDuration = Float.parseFloat(m.group(1));
                    } catch (Exception ignored) {
                        currentDuration = 0;
                    }
                }
                continue;
            }

            // 媒体URI
            if (!line.startsWith("#")) {
                String uri = resolveUri(playList.getUrl(), line);
                TrackData track = new TrackData.Builder()
                        .withUri(uri)
                        .withTrackInfo(new TrackInfo(currentDuration, ""))
                        .withProgramDateTime(programDateTime)
                        .withDiscontinuity(hasDiscontinuity)
                        .build();

                tracks.add(track);

                // 重置状态
                hasDiscontinuity = false;
                programDateTime = null;
                currentDuration = 0;
            } else {
                // 其他#标签
                headers.add(rawLine);
            }
        }

        playList.getTrackDataList().addAll(tracks);
        playList.setHeaders(headers);
    }

    /**
     * 解析相对URI为绝对URI
     */
    private static String resolveUri(String baseUrl, String uri) {
        if (uri.startsWith("http")) return uri;
        try {
            return new URI(baseUrl).resolve(uri).toString();
        } catch (Exception e) {
            return uri;
        }
    }

    /**
     * 输出播放列表为字符串
     */
    public static String printPlaylist(PlayList playList) {
        StringBuilder sb = new StringBuilder();

        for (String header : playList.getHeaders()) {
            sb.append(header).append("\n");
        }

        if (playList.getContentType() == ContextType.MEDIA) {
            for (TrackData item : playList.getTrackDataList()) {
                if (item.hasDiscontinuity()) {
                    sb.append("#EXT-X-DISCONTINUITY\n");
                }
                if (item.hasTrackInfo()) {
                    sb.append("#EXTINF:").append(item.getTrackInfo().duration).append(",\n");
                }
                if (item.hasProgramDateTime()) {
                    sb.append("#EXT-X-PROGRAM-DATE-TIME:").append(item.getProgramDateTime()).append("\n");
                }
                sb.append(item.getUri()).append("\n");
            }
            sb.append("#EXT-X-ENDLIST");
        }

        return sb.toString();
    }
}
