package com.infalce.batch.entity.video;

import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "video_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_video_stats_video",
                columnNames = "video_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoStats extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "comment_count", nullable = false)
    private Long commentCount = 0L;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "vph", nullable = false)
    private Double vph = 0.0;

    @Column(name = "outlier_score", nullable = false)
    private Double outlierScore = 0.0;

    @Column(name = "rising_score")
    private Double risingScore;

    public void update(Long viewCount, Long likeCount, Long commentCount, Double vph, Double outlierScore,
                       Double risingScore, LocalDateTime collectedAt) {
        this.viewCount = defaultLong(viewCount);
        this.likeCount = defaultLong(likeCount);
        this.commentCount = defaultLong(commentCount);
        this.vph = defaultDouble(vph);
        this.outlierScore = defaultDouble(outlierScore);
        this.risingScore = risingScore;
        this.collectedAt = collectedAt;
    }

    public static VideoStats of(Video video, Long viewCount, Long likeCount, Long commentCount,
                                Double vph, Double outlierScore, Double risingScore,
                                LocalDateTime collectedAt) {
        VideoStats videoStats = new VideoStats();
        videoStats.video = video;
        videoStats.viewCount = defaultLong(viewCount);
        videoStats.likeCount = defaultLong(likeCount);
        videoStats.commentCount = defaultLong(commentCount);
        videoStats.vph = defaultDouble(vph);
        videoStats.outlierScore = defaultDouble(outlierScore);
        videoStats.risingScore = risingScore;
        videoStats.collectedAt = collectedAt;
        return videoStats;
    }

    private static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private static double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
