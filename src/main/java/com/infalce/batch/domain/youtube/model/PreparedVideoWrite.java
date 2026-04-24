package com.infalce.batch.domain.youtube.model;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import com.infalce.batch.entity.channel.Channel;

import java.time.LocalDateTime;

public record PreparedVideoWrite(
        String youtubeVideoId,
        YoutubeVideoItem item,
        Channel channel,
        ChannelMetricRow channelMetric,
        Integer durationSeconds,
        LocalDateTime publishedAt,
        LocalDateTime collectedAt
) {
}
