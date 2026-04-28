package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChannelOutlierCalculatorTest {

    @Test
    void calculatesTrimmedAverageOutlierScoreForRecentVideos() {
        List<YoutubeVideoItem> recentVideos = List.of(
                video(1_000L),
                video(2_000L),
                video(3_000L),
                video(4_000L),
                video(5_000L),
                video(6_000L),
                video(7_000L),
                video(8_000L),
                video(9_000L),
                video(10_000L),
                video(11_000L),
                video(12_000L),
                video(13_000L),
                video(14_000L),
                video(15_000L),
                video(16_000L),
                video(17_000L),
                video(18_000L),
                video(19_000L),
                video(100_000L)
        );

        double average = ChannelOutlierCalculator.calculateAverageOutlierScoreRecentExcludingTop5Pct(
                recentVideos,
                1_000_000L,
                100L
        );

        assertEquals(1.0, average);
    }

    @Test
    void returnsNullOutlierWhenChannelAverageCannotBeCalculated() {
        assertNull(ChannelOutlierCalculator.calculateOutlierScore(1_000L, 0L, 0L));
    }

    private YoutubeVideoItem video(Long viewCount) {
        return new YoutubeVideoItem(
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                viewCount,
                0L,
                0L
        );
    }
}
