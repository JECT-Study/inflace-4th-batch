package com.infalce.batch.domain.youtube.api;

import java.util.Map;

public class YoutubeApiException extends IllegalStateException {
    private final int statusCode;

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
        this.statusCode = statusCode;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }
}
