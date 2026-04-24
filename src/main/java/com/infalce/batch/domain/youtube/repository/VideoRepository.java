package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.video.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByYoutubeVideoId(String youtubeVideoId);

    List<Video> findByYoutubeVideoIdIn(Collection<String> youtubeVideoIds);
}
