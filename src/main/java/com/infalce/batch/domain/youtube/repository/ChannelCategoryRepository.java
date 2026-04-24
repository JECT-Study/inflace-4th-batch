package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.channel.ChannelCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChannelCategoryRepository extends JpaRepository<ChannelCategory, Long> {

    boolean existsByChannelIdAndCategoryId(Long channelId, Long categoryId);

    List<ChannelCategory> findByCategoryIdAndChannelIdIn(Long categoryId, Collection<Long> channelIds);
}
