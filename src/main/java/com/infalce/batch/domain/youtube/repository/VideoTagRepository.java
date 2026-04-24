package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.video.VideoTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VideoTagRepository extends JpaRepository<VideoTag, Long> {

    boolean existsByVideoIdAndTag(Long videoId, String tag);

    List<VideoTag> findByVideoIdIn(Collection<Long> videoIds);
}
