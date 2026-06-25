package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.model.ChannelMetricRow;
import com.infalce.batch.domain.youtube.model.ChannelSnapshotKey;
import com.infalce.batch.domain.youtube.repository.ChannelStatsHistoryRepository;
import com.infalce.batch.domain.youtube.repository.ChannelStatsRepository;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.channel.ChannelStats;
import com.infalce.batch.entity.channel.ChannelStatsHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YoutubeChannelStatsService {

    private final ChannelStatsRepository channelStatsRepository;
    private final ChannelStatsHistoryRepository channelStatsHistoryRepository;

    public void upsertChannelStats(
            Map<String, Channel> managedChannels,
            Map<String, ChannelMetricRow> metricRows
    ) {
        List<Long> channelIds = managedChannels.values().stream()
                .map(Channel::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (channelIds.isEmpty()) {
            return;
        }

        Map<Long, ChannelStats> existingStats = channelStatsRepository.findByChannelIdIn(channelIds).stream()
                .collect(Collectors.toMap(stats -> stats.getChannel().getId(), stats -> stats));
        Set<ChannelSnapshotKey> existingHistoryKeys = loadExistingHistoryKeys(existingStats.values(), metricRows);
        List<ChannelStatsHistory> newHistories = new ArrayList<>();
        List<ChannelStats> newStats = new ArrayList<>();

        for (Map.Entry<String, ChannelMetricRow> entry : metricRows.entrySet()) {
            Channel channel = managedChannels.get(entry.getKey());
            if (channel == null) {
                continue;
            }

            ChannelMetricRow row = entry.getValue();
            ChannelStats stats = existingStats.get(channel.getId());
            if (stats == null) {
                newStats.add(ChannelStats.of(
                        channel,
                        row.subscriberCount(),
                        row.totalViewCount(),
                        row.totalVideoCount(),
                        row.recentUploadCount30d(),
                        row.avgViewsRecent(),
                        row.avgEngagementRateRecent(),
                        row.avgOutlierScoreRecentExcludingTop5Pct(),
                        row.collectedAt()
                ));
            } else {
                addChannelStatsHistoryIfAbsent(stats, row.collectedAt(), existingHistoryKeys, newHistories);
                stats.update(
                        row.subscriberCount(),
                        row.totalViewCount(),
                        row.totalVideoCount(),
                        row.recentUploadCount30d(),
                        row.avgViewsRecent(),
                        row.avgEngagementRateRecent(),
                        row.avgOutlierScoreRecentExcludingTop5Pct(),
                        row.collectedAt()
                );
            }
        }

        YoutubeBatchSupport.saveAllInChunks(channelStatsHistoryRepository, newHistories);
        YoutubeBatchSupport.saveAllInChunks(channelStatsRepository, newStats);
    }

    private Set<ChannelSnapshotKey> loadExistingHistoryKeys(
            Collection<ChannelStats> existingStats,
            Map<String, ChannelMetricRow> metricRows
    ) {
        List<Long> channelIds = new ArrayList<>();
        Set<LocalDate> snapshotDates = new LinkedHashSet<>();

        for (ChannelStats stats : existingStats) {
            channelIds.add(stats.getChannel().getId());
            ChannelMetricRow row = metricRows.get(stats.getChannel().getYoutubeChannelId());
            snapshotDates.add(resolveSnapshotDate(
                    stats,
                    row == null ? LocalDateTime.now(ZoneOffset.UTC) : row.collectedAt()
            ));
        }

        if (channelIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return channelStatsHistoryRepository
                .findByChannelIdInAndSnapshotDateIn(channelIds, snapshotDates)
                .stream()
                .map(history -> new ChannelSnapshotKey(history.getChannel().getId(), history.getSnapshotDate()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void addChannelStatsHistoryIfAbsent(
            ChannelStats stats,
            LocalDateTime collectedAt,
            Set<ChannelSnapshotKey> existingHistoryKeys,
            List<ChannelStatsHistory> newHistories
    ) {
        LocalDate snapshotDate = resolveSnapshotDate(stats, collectedAt);
        ChannelSnapshotKey key = new ChannelSnapshotKey(stats.getChannel().getId(), snapshotDate);
        if (!existingHistoryKeys.add(key)) {
            return;
        }

        newHistories.add(ChannelStatsHistory.of(
                stats.getChannel(),
                snapshotDate,
                stats.getSubscriberCount(),
                stats.getTotalViewCount(),
                stats.getTotalVideoCount(),
                stats.getCollectedAt() == null ? LocalDateTime.now(ZoneOffset.UTC) : stats.getCollectedAt()
        ));
    }

    private LocalDate resolveSnapshotDate(ChannelStats stats, LocalDateTime collectedAt) {
        return stats.getCollectedAt() == null
                ? collectedAt.toLocalDate()
                : stats.getCollectedAt().toLocalDate();
    }
}
