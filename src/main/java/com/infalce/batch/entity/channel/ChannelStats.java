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

    @Column(name = "subscriber_count")
    private Long subscriberCount;

    @Column(name = "total_view_count")
    private Long totalViewCount;

    @Column(name = "total_video_count")
    private Long totalVideoCount;

    @Column(name = "recent_upload_count_30d")
    private Integer recentUploadCount30d;

    @Column(name = "avg_views_recent_n")
    private Double avgViewsRecentN;

    @Column(name = "avg_engagement_rate_recent_n")
    private Double avgEngagementRateRecentN;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    public static ChannelStats of(Channel channel, Long subscriberCount, Long totalViewCount, Long totalVideoCount,
                                  Integer recentUploadCount30d, Double avgViewsRecentN,
                                  Double avgEngagementRateRecentN, LocalDateTime collectedAt) {
        ChannelStats channelStats = new ChannelStats();
        channelStats.channel = channel;
        channelStats.subscriberCount = subscriberCount;
        channelStats.totalViewCount = totalViewCount;
        channelStats.totalVideoCount = totalVideoCount;
        channelStats.recentUploadCount30d = recentUploadCount30d;
        channelStats.avgViewsRecentN = avgViewsRecentN;
        channelStats.avgEngagementRateRecentN = avgEngagementRateRecentN;
        channelStats.collectedAt = collectedAt;
        return channelStats;
    }

    public void update(Long subscriberCount, Long totalViewCount, Long totalVideoCount,
                       Integer recentUploadCount30d, Double avgViewsRecentN,
                       Double avgEngagementRateRecentN, LocalDateTime collectedAt) {
        this.subscriberCount = subscriberCount;
        this.totalViewCount = totalViewCount;
        this.totalVideoCount = totalVideoCount;
        this.recentUploadCount30d = recentUploadCount30d;
        this.avgViewsRecentN = avgViewsRecentN;
        this.avgEngagementRateRecentN = avgEngagementRateRecentN;
        this.collectedAt = collectedAt;
    }
}
