package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.video.VideoStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoStatsRepository extends JpaRepository<VideoStats, Long> {

    Optional<VideoStats> findByVideoId(Long videoId);

    List<VideoStats> findByVideoIdIn(Collection<Long> videoIds);
}
