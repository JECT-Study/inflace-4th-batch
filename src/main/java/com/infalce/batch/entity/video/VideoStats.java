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

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "comment_count")
    private Long commentCount;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    public void update(Long viewCount, Long likeCount, Long commentCount, Double vph, Double outlierScore,
                       Double risingScore, LocalDateTime collectedAt) {
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.vph = vph;
        this.outlierScore = outlierScore;
        this.risingScore = risingScore;
        this.collectedAt = collectedAt;
    }

    @Column(name = "vph")
    private Double vph;

    @Column(name = "outlier_score")
    private Double outlierScore;

    @Column(name = "rising_score")
    private Double risingScore;

    public static VideoStats of(Video video, Long viewCount, Long likeCount, Long commentCount,
                                Double vph, Double outlierScore, Double risingScore,
                                LocalDateTime collectedAt) {
        VideoStats videoStats = new VideoStats();
        videoStats.video = video;
        videoStats.viewCount = viewCount;
        videoStats.likeCount = likeCount;
        videoStats.commentCount = commentCount;
        videoStats.vph = vph;
        videoStats.outlierScore = outlierScore;
        videoStats.risingScore = risingScore;
        videoStats.collectedAt = collectedAt;
        return videoStats;
    }
}
