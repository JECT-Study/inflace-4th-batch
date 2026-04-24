package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.channel.ChannelStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChannelStatsRepository extends JpaRepository<ChannelStats, Long> {

    Optional<ChannelStats> findByChannelId(Long channelId);

    List<ChannelStats> findByChannelIdIn(Collection<Long> channelIds);
}
