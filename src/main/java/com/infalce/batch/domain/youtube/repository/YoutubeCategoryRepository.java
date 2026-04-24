package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.video.YoutubeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YoutubeCategoryRepository extends JpaRepository<YoutubeCategory, Long> {

    Optional<YoutubeCategory> findByYoutubeCategoryId(Integer youtubeCategoryId);

    List<YoutubeCategory> findByYoutubeCategoryIdIn(Collection<Integer> youtubeCategoryIds);

    List<YoutubeCategory> findAllByOrderByYoutubeCategoryIdAsc();
}
