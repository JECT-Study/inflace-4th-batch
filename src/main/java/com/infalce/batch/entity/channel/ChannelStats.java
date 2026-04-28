package com.infalce.batch.entity.channel;

import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "channel_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_channel_stats_channel",
                columnNames = "channel_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelStats extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "subscriber_count", nullable = false)
    private Long subscriberCount;

    @Column(name = "total_view_count")
    private Long totalViewCount;

    @Column(name = "total_video_count")
    private Long totalVideoCount;

    @Column(name = "recent_upload_count_30d", nullable = false)
    private Integer recentUploadCount30d;

    @Column(name = "avg_views_recent", nullable = false)
    private Double avgViewsRecent;

    @Column(name = "avg_engagement_rate_recent", nullable = false)
    private Double avgEngagementRateRecent;

    @Column(name = "avg_outlier_score_recent_excluding_top_5_pct", nullable = false)
    private Double avgOutlierScoreRecentExcludingTop5Pct;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    public static ChannelStats of(Channel channel, Long subscriberCount, Long totalViewCount, Long totalVideoCount,
                                  Integer recentUploadCount30d, Double avgViewsRecent,
                                  Double avgEngagementRateRecent, Double avgOutlierScoreRecentExcludingTop5Pct,
                                  LocalDateTime collectedAt) {
        ChannelStats channelStats = new ChannelStats();
        channelStats.channel = channel;
        channelStats.subscriberCount = subscriberCount;
        channelStats.totalViewCount = totalViewCount;
        channelStats.totalVideoCount = totalVideoCount;
        channelStats.recentUploadCount30d = recentUploadCount30d;
        channelStats.avgViewsRecent = avgViewsRecent;
        channelStats.avgEngagementRateRecent = avgEngagementRateRecent;
        channelStats.avgOutlierScoreRecentExcludingTop5Pct = avgOutlierScoreRecentExcludingTop5Pct;
        channelStats.collectedAt = collectedAt;
        return channelStats;
    }

    public void update(Long subscriberCount, Long totalViewCount, Long totalVideoCount,
                       Integer recentUploadCount30d, Double avgViewsRecent,
                       Double avgEngagementRateRecent, Double avgOutlierScoreRecentExcludingTop5Pct,
                       LocalDateTime collectedAt) {
        this.subscriberCount = subscriberCount;
        this.totalViewCount = totalViewCount;
        this.totalVideoCount = totalVideoCount;
        this.recentUploadCount30d = recentUploadCount30d;
        this.avgViewsRecent = avgViewsRecent;
        this.avgEngagementRateRecent = avgEngagementRateRecent;
        this.avgOutlierScoreRecentExcludingTop5Pct = avgOutlierScoreRecentExcludingTop5Pct;
        this.collectedAt = collectedAt;
    }
}
