package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.channel.ChannelStatsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;

public interface ChannelStatsHistoryRepository extends JpaRepository<ChannelStatsHistory, Long> {

    boolean existsByChannelIdAndSnapshotDate(Long channelId, LocalDate snapshotDate);

    List<ChannelStatsHistory> findByChannelIdInAndSnapshotDateIn(Collection<Long> channelIds, Collection<LocalDate> snapshotDates);
}
