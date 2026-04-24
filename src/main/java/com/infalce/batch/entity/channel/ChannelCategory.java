package com.infalce.batch.entity.channel;

import com.infalce.batch.entity.global.BaseTimeEntity;
import com.infalce.batch.entity.video.YoutubeCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "channel_category",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_channel_category",
                columnNames = {"channel_id", "category_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelCategory extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private YoutubeCategory category;

    public static ChannelCategory of(Channel channel, YoutubeCategory category) {
        ChannelCategory channelCategory = new ChannelCategory();
        channelCategory.channel = channel;
        channelCategory.category = category;
        return channelCategory;
    }
}
