package com.infalce.batch.entity.video;

import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "video_stats_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_video_stats_history_video_date",
                columnNames = {"video_id", "snapshot_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoStatsHistory extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "comment_count")
    private Long commentCount;

    @Column(name = "vph")
    private Double vph;

    @Column(name = "outlier_score")
    private Double outlierScore;

    @Column(name = "rising_score")
    private Double risingScore;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
