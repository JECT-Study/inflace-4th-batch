package com.infalce.batch.entity.channel;

import com.infalce.batch.entity.global.BaseTimeEntity;
import com.infalce.batch.entity.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "channel",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_channel_user_youtube",
                columnNames = {"user_id", "youtube_channel_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "youtube_channel_id")
    private String youtubeChannelId;

    @Column(name = "channel_handle")
    private String channelHandle;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "uploads_playlist_id")
    private String uploadsPlaylistId;

    @Column(name = "youtube_published_at")
    private LocalDateTime youtubePublishedAt;

    public static Channel of(User user, String name, String description, String youtubeChannelId, String channelHandle,
                             String profileImageUrl, String uploadsPlaylistId, LocalDateTime youtubePublishedAt) {
        Channel channel = new Channel();
        channel.user = user;
        channel.name = name;
        channel.description = description;
        channel.youtubeChannelId = youtubeChannelId;
        channel.channelHandle = channelHandle;
        channel.profileImageUrl = profileImageUrl;
        channel.uploadsPlaylistId = uploadsPlaylistId;
        channel.youtubePublishedAt = youtubePublishedAt;
        return channel;
    }

    public boolean update(
            String name, String description, String channelHandle, String profileImageUrl,
            String uploadsPlaylistId, LocalDateTime youtubePublishedAt
    ) {
        boolean changed = !Objects.equals(this.name, name)
                || !Objects.equals(this.description, description)
                || !Objects.equals(this.channelHandle, channelHandle)
                || !Objects.equals(this.profileImageUrl, profileImageUrl)
                || !Objects.equals(this.uploadsPlaylistId, uploadsPlaylistId)
                || !Objects.equals(this.youtubePublishedAt, youtubePublishedAt);
        if (!changed) return false;

        this.name = name;
        this.description = description;
        this.channelHandle = channelHandle;
        this.profileImageUrl = profileImageUrl;
        this.uploadsPlaylistId = uploadsPlaylistId;
        this.youtubePublishedAt = youtubePublishedAt;
        return true;
    }
}
