package com.infalce.batch.domain.youtube.model;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeChannelItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;

import java.time.LocalDateTime;
import java.util.List;

public record ChannelMetricsAggregate(
        String youtubeChannelId,
        YoutubeChannelItem channelItem,
        Integer recentUploadCount,
        List<YoutubeVideoItem> recentVideos,
        LocalDateTime collectedAt
) {
}
