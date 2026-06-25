package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.PlaylistVideoItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeChannelItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiException;
import com.infalce.batch.domain.youtube.batch.summary.ChannelMetricsWriteSummary;
import com.infalce.batch.domain.youtube.model.ChannelMetricRow;
import com.infalce.batch.domain.youtube.model.ChannelMetricsAggregate;
import com.infalce.batch.domain.youtube.model.UploadMetrics;
import com.infalce.batch.entity.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeChannelMetricsService {

    private static final int YOUTUBE_MAX_RESULTS = 50;

    private final YoutubeApiClient youtubeApiClient;
    private final YoutubeChannelPersistenceService channelPersistenceService;
    private final YoutubeChannelStatsService channelStatsService;
    private final YoutubeVideoIngestionService videoIngestionService;

    public ChannelMetricsWriteSummary refreshChannelMetrics(List<Channel> channels, int recentDays) {
        Map<String, Channel> targetChannels = channels.stream()
                .filter(java.util.Objects::nonNull)
                .filter(channel -> StringUtils.hasText(channel.getYoutubeChannelId()))
                .collect(Collectors.toMap(
                        Channel::getYoutubeChannelId,
                        channel -> channel,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (targetChannels.isEmpty()) {
            return new ChannelMetricsWriteSummary(0, 0);
        }

        List<YoutubeChannelItem> channelItems = youtubeApiClient.getChannels(new ArrayList<>(targetChannels.keySet()));
        if (channelItems.isEmpty()) {
            return new ChannelMetricsWriteSummary(0, 0);
        }

        Map<String, UploadMetrics> uploadMetricsByChannelId = new LinkedHashMap<>();
        Set<String> recentVideoIds = new LinkedHashSet<>();
        Map<String, LocalDateTime> collectedAtByChannelId = new LinkedHashMap<>();
        for (YoutubeChannelItem channelItem : channelItems) {
            if (!StringUtils.hasText(channelItem.youtubeChannelId())
                    || !StringUtils.hasText(channelItem.uploadsPlaylistId())) {
                continue;
            }

            UploadMetrics uploadMetrics = collectUploadMetrics(
                    channelItem.uploadsPlaylistId(),
                    recentDays
            );
            uploadMetricsByChannelId.put(channelItem.youtubeChannelId(), uploadMetrics);
            recentVideoIds.addAll(uploadMetrics.recentVideoIds());
            collectedAtByChannelId.put(channelItem.youtubeChannelId(), LocalDateTime.now(ZoneOffset.UTC));
        }

        Map<String, YoutubeVideoItem> videosById = recentVideoIds.isEmpty()
                ? Map.of()
                : youtubeApiClient.getVideos(new ArrayList<>(recentVideoIds)).stream()
                .filter(video -> StringUtils.hasText(video.youtubeVideoId()))
                .collect(Collectors.toMap(
                        YoutubeVideoItem::youtubeVideoId,
                        video -> video,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<ChannelMetricsAggregate> aggregates = new ArrayList<>();
        for (YoutubeChannelItem channelItem : channelItems) {
            if (!StringUtils.hasText(channelItem.youtubeChannelId())
                    || !StringUtils.hasText(channelItem.uploadsPlaylistId())) {
                continue;
            }

            UploadMetrics uploadMetrics = uploadMetricsByChannelId.get(channelItem.youtubeChannelId());
            if (uploadMetrics == null) {
                continue;
            }

            List<YoutubeVideoItem> recentVideos = uploadMetrics.recentVideoIds().stream()
                    .map(videosById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            aggregates.add(new ChannelMetricsAggregate(
                    channelItem.youtubeChannelId(),
                    channelItem,
                    uploadMetrics.recentUploadCount(),
                    recentVideos,
                    collectedAtByChannelId.get(channelItem.youtubeChannelId())
            ));
        }

        return writeChannelMetrics(aggregates);
    }

    public ChannelMetricsWriteSummary writeChannelMetrics(List<ChannelMetricsAggregate> aggregates) {
        List<ChannelMetricsAggregate> validAggregates = aggregates.stream()
                .filter(java.util.Objects::nonNull)
                .filter(aggregate -> aggregate.channelItem() != null)
                .toList();
        if (validAggregates.isEmpty()) {
            return new ChannelMetricsWriteSummary(0, 0);
        }

        Map<String, Channel> managedChannels = channelPersistenceService.refreshChannels(validAggregates.stream()
                .map(ChannelMetricsAggregate::channelItem)
                .toList());
        Map<String, ChannelMetricRow> metricRows = buildChannelMetricRows(validAggregates);
        channelStatsService.upsertChannelStats(managedChannels, metricRows);
        videoIngestionService.upsertVideosAndStats(validAggregates, managedChannels, metricRows);

        int videoCount = validAggregates.stream()
                .mapToInt(aggregate -> aggregate.recentVideos().size())
                .sum();
        log.info("refreshed youtube channel metrics. channelCount={} videoCount={}",
                validAggregates.size(), videoCount);
        return new ChannelMetricsWriteSummary(validAggregates.size(), videoCount);
    }

    private UploadMetrics collectUploadMetrics(String playlistId, int recentDays) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(recentDays);
        int recentUploadCount = 0;
        List<String> recentVideoIds = new ArrayList<>();
        String pageToken = null;

        while (true) {
            YoutubeApiClient.PlaylistItemsPage page;
            try {
                page = youtubeApiClient.listPlaylistItems(
                        playlistId,
                        pageToken,
                        YOUTUBE_MAX_RESULTS
                );
            } catch (YoutubeApiException e) {
                if (e.isPlaylistNotFound()) {
                    log.warn("youtube uploads playlist not found. playlistId={}", playlistId);
                    return new UploadMetrics(0, List.of());
                }
                throw e;
            }
            if (page.items().isEmpty()) {
                break;
            }

            for (PlaylistVideoItem item : page.items()) {
                if (!StringUtils.hasText(item.videoId()) || !StringUtils.hasText(item.videoPublishedAt())) {
                    continue;
                }

                LocalDateTime publishedAt = YoutubeBatchSupport.parseYoutubeDateTime(item.videoPublishedAt());
                if (publishedAt != null && !publishedAt.isBefore(cutoff)) {
                    recentVideoIds.add(item.videoId());
                    recentUploadCount++;
                } else {
                    return new UploadMetrics(recentUploadCount, recentVideoIds);
                }
            }

            pageToken = page.nextPageToken();
            if (!StringUtils.hasText(pageToken)) {
                break;
            }
        }

        return new UploadMetrics(recentUploadCount, recentVideoIds);
    }

    private Map<String, ChannelMetricRow> buildChannelMetricRows(List<ChannelMetricsAggregate> aggregates) {
        Map<String, ChannelMetricRow> rows = new LinkedHashMap<>();
        for (ChannelMetricsAggregate aggregate : aggregates) {
            YoutubeChannelItem channel = aggregate.channelItem();
            List<YoutubeVideoItem> recentVideos = aggregate.recentVideos();

            long totalViews = 0;
            long totalLikes = 0;
            long totalComments = 0;
            for (YoutubeVideoItem video : recentVideos) {
                totalViews += YoutubeBatchSupport.defaultLong(video.viewCount());
                totalLikes += YoutubeBatchSupport.defaultLong(video.likeCount());
                totalComments += YoutubeBatchSupport.defaultLong(video.commentCount());
            }

            double avgViews = recentVideos.isEmpty()
                    ? 0.0
                    : YoutubeBatchSupport.round((double) totalViews / recentVideos.size(), 2);
            double avgEngagementRate = totalViews > 0
                    ? YoutubeBatchSupport.round(((double) (totalLikes + totalComments) / totalViews) * 100.0, 2)
                    : 0.0;
            double avgOutlierScoreRecentExcludingTop5Pct =
                    ChannelOutlierCalculator.calculateAverageOutlierScoreRecentExcludingTop5Pct(
                            recentVideos,
                            channel.viewCount(),
                            channel.videoCount()
                    );

            rows.put(channel.youtubeChannelId(), new ChannelMetricRow(
                    channel.youtubeChannelId(),
                    channel.subscriberCount(),
                    YoutubeBatchSupport.defaultLong(channel.viewCount()),
                    YoutubeBatchSupport.defaultLong(channel.videoCount()),
                    aggregate.recentUploadCount(),
                    avgViews,
                    avgEngagementRate,
                    avgOutlierScoreRecentExcludingTop5Pct,
                    aggregate.collectedAt()
            ));
        }
        return rows;
    }
}
