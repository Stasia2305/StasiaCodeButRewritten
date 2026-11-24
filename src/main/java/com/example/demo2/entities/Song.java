package com.example.demo2.entities;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Business entity representing a track in the library.
 * A track is either a local media file or a direct media URL (http/https).
 * The kind of media is derived from the file extension, while the presence of
 * an actual video track is confirmed at playback time from the media dimensions.
 */
public class Song {
    /** Containers JavaFX Media can decode locally (H.264 video + AAC audio). */
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov");

    /** Common video containers JavaFX Media cannot decode. */
    private static final Set<String> UNSUPPORTED_VIDEO_EXTENSIONS = Set.of(
            "mkv", "avi", "webm", "wmv", "flv", "mpg", "mpeg", "ts", "m2ts", "3gp"
    );

    private Integer id; // null until persisted
    private String title;
    private String artist;
    private int durationSeconds; // 0 if unknown
    private String filePath; // absolute path or http(s) URL

    public Song(Integer id, String title, String artist, int durationSeconds, String filePath) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.durationSeconds = Math.max(0, durationSeconds);
        this.filePath = filePath;
    }

    public Song(String title, String artist, int durationSeconds, String filePath) {
        this(null, title, artist, durationSeconds, filePath);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = Math.max(0, durationSeconds); }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    /**
     * True when this track is streamed from a direct media URL instead of a local file.
     */
    public boolean isUrl() {
        return isUrlString(filePath);
    }

    public static boolean isUrlString(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }

    /**
     * True when the extension is a container JavaFX Media renders as video.
     */
    public boolean isVideo() {
        return isVideoFile(filePath);
    }

    /** True for video containers JavaFX Media cannot decode (mkv, avi, webm ...). */
    public boolean hasUnsupportedVideoFormat() {
        return UNSUPPORTED_VIDEO_EXTENSIONS.contains(extensionOf(filePath));
    }

    public static boolean isVideoFile(String pathOrUrl) {
        return VIDEO_EXTENSIONS.contains(extensionOf(pathOrUrl));
    }

    /** Lower-cased extension of a path or URL, ignoring query/fragment. Empty when absent. */
    public static String extensionOf(String pathOrUrl) {
        if (pathOrUrl == null) return "";
        String value = pathOrUrl.trim();
        int cut = value.length();
        int query = value.indexOf('?');
        if (query >= 0) cut = query;
        int fragment = value.indexOf('#');
        if (fragment >= 0 && fragment < cut) cut = fragment;
        value = value.substring(0, cut);
        int dot = value.lastIndexOf('.');
        int slash = value.lastIndexOf('/');
        if (dot < 0 || dot < slash || dot == value.length() - 1) return "";
        return value.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Human readable media type used in the UI. */
    public String getMediaTypeDisplay() {
        if (hasUnsupportedVideoFormat()) return "Unsupported";
        if (isVideo()) return "Video";
        if (isUrl()) return "Stream";
        return "Audio";
    }

    public String getDurationDisplay() {
        if (durationSeconds <= 0) return "—";
        int s = durationSeconds % 60;
        int m = (durationSeconds / 60) % 60;
        int h = durationSeconds / 3600;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
    }

    @Override
    public String toString() {
        return title + " — " + artist;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song song)) return false;
        return Objects.equals(id, song.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
