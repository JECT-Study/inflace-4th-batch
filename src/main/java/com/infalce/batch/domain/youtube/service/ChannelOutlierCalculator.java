package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class ChannelOutlierCalculator {

    private ChannelOutlierCalculator() {
    }

    static double calculateAverageOutlierScoreRecentExcludingTop5Pct(
            List<YoutubeVideoItem> recentVideos,
            Long channelTotalViewCount,
            Long channelTotalVideoCount
    ) {
        List<Double> outlierScores = recentVideos.stream()
                .map(video -> calculateOutlierScore(video.viewCount(), channelTotalViewCount, channelTotalVideoCount))
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .toList();

        if (outlierScores.isEmpty()) {
            return 0.0;
        }

        // Floor keeps small recent-video sets from dropping a single video too aggressively.
        int excludedCount = (int) Math.floor(outlierScores.size() * 0.05d);
        int startIndex = Math.min(excludedCount, outlierScores.size());
        List<Double> retainedScores = outlierScores.subList(startIndex, outlierScores.size());
        if (retainedScores.isEmpty()) {
            return 0.0;
        }

        double average = retainedScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return round(average, 6);
    }

    static Double calculateOutlierScore(Long videoViewCount, Long channelTotalViewCount, Long channelTotalVideoCount) {
        long normalizedVideoCount = defaultLong(channelTotalVideoCount);
        if (normalizedVideoCount <= 0) {
            return null;
        }

        double avgChannelViewCount = (double) defaultLong(channelTotalViewCount) / normalizedVideoCount;
        if (avgChannelViewCount <= 0) {
            return null;
        }

        return round(defaultLong(videoViewCount) / avgChannelViewCount, 6);
    }

    private static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
