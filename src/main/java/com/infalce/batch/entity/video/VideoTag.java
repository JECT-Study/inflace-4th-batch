package com.infalce.batch.entity.video;

import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "video_tag",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_video_tag",
                columnNames = {"video_id", "tag"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoTag extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "tag", nullable = false)
    private String tag;

    public static VideoTag of(Video video, String tag) {
        VideoTag videoTag = new VideoTag();
        videoTag.video = video;
        videoTag.tag = tag;
        return videoTag;
    }
}
