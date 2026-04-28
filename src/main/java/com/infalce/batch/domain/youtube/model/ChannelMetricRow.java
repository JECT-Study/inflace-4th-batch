package com.infalce.batch.domain.youtube.model;

import java.time.LocalDateTime;

public record ChannelMetricRow(
        String youtubeChannelId,
        Long subscriberCount,
        Long totalViewCount,
        Long totalVideoCount,
        Integer recentUploadCount30d,
        Double avgViewsRecent,
        Double avgEngagementRateRecent,
        Double avgOutlierScoreRecentExcludingTop5Pct,
        LocalDateTime collectedAt
) {
}
