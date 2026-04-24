package com.infalce.batch.entity.video;

import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "video",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_video_youtube_video",
                columnNames = "youtube_video_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    private String title;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "is_short")
    private boolean isShort;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "youtube_video_id")
    private String youtubeVideoId;

    private String description;

    @Column(name = "is_advertisement")
    private boolean isAdvertisement;

    public static Video of(Channel channel, Integer categoryId, String youtubeVideoId, String title,
                           String description, String thumbnailUrl, Integer durationSeconds, boolean isShort,
                           boolean isAdvertisement, LocalDateTime publishedAt) {
        Video video = new Video();
        video.channel = channel;
        video.categoryId = categoryId;
        video.youtubeVideoId = youtubeVideoId;
        video.title = title;
        video.description = description;
        video.thumbnailUrl = thumbnailUrl;
        video.durationSeconds = durationSeconds;
        video.isShort = isShort;
        video.isAdvertisement = isAdvertisement;
        video.publishedAt = publishedAt;
        return video;
    }

    public boolean update(
            Channel channel, Integer categoryId, String title,
            String description, String thumbnailUrl, Integer durationSeconds,
            boolean isShort, boolean isAdvertisement, LocalDateTime publishedAt
    ) {
        boolean changed = !Objects.equals(this.channel, channel)
                || !Objects.equals(this.categoryId, categoryId)
                || !Objects.equals(this.title, title)
                || !Objects.equals(this.description, description)
                || !Objects.equals(this.thumbnailUrl, thumbnailUrl)
                || !Objects.equals(this.durationSeconds, durationSeconds)
                || this.isShort != isShort
                || this.isAdvertisement != isAdvertisement
                || !Objects.equals(this.publishedAt, publishedAt);

        if (!changed) return false;

        this.channel = channel;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.durationSeconds = durationSeconds;
        this.isShort = isShort;
        this.isAdvertisement = isAdvertisement;
        this.publishedAt = publishedAt;
        return true;
    }
}
