package com.infalce.batch.entity.video;

import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "youtube_category",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_youtube_category_youtube_category_id",
                columnNames = "youtube_category_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YoutubeCategory extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "youtube_category_id", nullable = false)
    private Integer youtubeCategoryId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "assignable")
    private Boolean assignable;

    public static YoutubeCategory of(Integer youtubeCategoryId, String title, Boolean assignable) {
        YoutubeCategory youtubeCategory = new YoutubeCategory();
        youtubeCategory.youtubeCategoryId = youtubeCategoryId;
        youtubeCategory.title = title;
        youtubeCategory.assignable = assignable;
        return youtubeCategory;
    }

    public boolean update(String title, Boolean assignable) {
        boolean changed = !Objects.equals(this.title, title)
                || !Objects.equals(this.assignable, assignable);
        if (!changed) return false;

        this.title = title;
        this.assignable = assignable;
        return true;
    }
}
