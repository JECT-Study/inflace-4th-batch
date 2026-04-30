package com.infalce.batch.domain.youtube.api;

import java.util.Map;

public class YoutubeApiException extends IllegalStateException {
    private final String resource;
    private final int statusCode;
    private final String responseBody;

    public YoutubeApiException(
            String resource,
            int statusCode,
            Map<String, String> params,
            String responseBody,
            Throwable cause
    ) {
        super(
                "YouTube API " + resource + " request failed. status=" + statusCode
                        + " params=" + params
                        + " response=" + responseBody,
                cause
        );
        this.resource = resource;
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public boolean isPlaylistNotFound() {
        return "playlistItems".equals(resource)
                && statusCode == 404
                && responseBody.contains("\"reason\": \"playlistNotFound\"");
    }
}
