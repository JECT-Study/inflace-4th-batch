package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.PlaylistVideoItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeCategoryItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeChannelItem;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import com.infalce.batch.domain.youtube.batch.summary.CategorySyncSummary;
import com.infalce.batch.domain.youtube.batch.summary.ChannelMetricsWriteSummary;
import com.infalce.batch.domain.youtube.batch.summary.DiscoveryWriteSummary;
import com.infalce.batch.domain.youtube.model.CategoryChannelDiscovery;
import com.infalce.batch.domain.youtube.model.ChannelMetricRow;
import com.infalce.batch.domain.youtube.model.ChannelMetricsAggregate;
import com.infalce.batch.domain.youtube.model.ChannelSnapshotKey;
import com.infalce.batch.domain.youtube.model.PreparedVideoWrite;
import com.infalce.batch.domain.youtube.model.UploadMetrics;
import com.infalce.batch.domain.youtube.repository.ChannelCategoryRepository;
import com.infalce.batch.domain.youtube.repository.ChannelRepository;
import com.infalce.batch.domain.youtube.repository.ChannelStatsHistoryRepository;
import com.infalce.batch.domain.youtube.repository.ChannelStatsRepository;
import com.infalce.batch.domain.youtube.repository.VideoRepository;
import com.infalce.batch.domain.youtube.repository.VideoStatsRepository;
import com.infalce.batch.domain.youtube.repository.VideoTagRepository;
import com.infalce.batch.domain.youtube.repository.YoutubeCategoryRepository;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.channel.ChannelCategory;
import com.infalce.batch.entity.channel.ChannelStats;
import com.infalce.batch.entity.channel.ChannelStatsHistory;
import com.infalce.batch.entity.video.Video;
import com.infalce.batch.entity.video.VideoStats;
import com.infalce.batch.entity.video.VideoTag;
import com.infalce.batch.entity.video.YoutubeCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeCategoryChannelBatchService {

    private static final int YOUTUBE_MAX_RESULTS = 50;
    private static final int WRITE_CHUNK_SIZE = 100;
    private static final Pattern YOUTUBE_DURATION = Pattern.compile(
            "^P(?:(?<days>\\d+)D)?(?:T(?:(?<hours>\\d+)H)?(?:(?<minutes>\\d+)M)?(?:(?<seconds>\\d+)S)?)?$"
    );

    private final YoutubeApiClient youtubeApiClient;
    private final YoutubeCategoryRepository youtubeCategoryRepository;
    private final ChannelRepository channelRepository;
    private final ChannelCategoryRepository channelCategoryRepository;
    private final ChannelStatsRepository channelStatsRepository;
    private final ChannelStatsHistoryRepository channelStatsHistoryRepository;
    private final VideoRepository videoRepository;
    private final VideoStatsRepository videoStatsRepository;
    private final VideoTagRepository videoTagRepository;

    public List<YoutubeCategoryItem> loadYoutubeCategories(String regionCode, String hl) {
        return youtubeApiClient.listVideoCategories(regionCode, hl);
    }

    public CategorySyncSummary upsertCategories(List<YoutubeCategoryItem> items) {
        if (items.isEmpty()) {
            return new CategorySyncSummary(0, 0);
        }

        List<Integer> youtubeCategoryIds = items.stream()
                .map(YoutubeCategoryItem::youtubeCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, YoutubeCategory> existingCategories = youtubeCategoryRepository
                .findByYoutubeCategoryIdIn(youtubeCategoryIds)
                .stream()
                .collect(Collectors.toMap(YoutubeCategory::getYoutubeCategoryId, category -> category));

        List<YoutubeCategory> newCategories = new ArrayList<>();
        int changed = 0;
        for (YoutubeCategoryItem item : items) {
            if (item.youtubeCategoryId() == null || !StringUtils.hasText(item.title())) {
                continue;
            }

            YoutubeCategory existing = existingCategories.get(item.youtubeCategoryId());
            if (existing == null) {
                newCategories.add(YoutubeCategory.of(
                        item.youtubeCategoryId(),
                        item.title(),
                        item.assignable()
                ));
                changed++;
            } else {
                changed += existing.update(item.title(), item.assignable()) ? 1 : 0;
            }
        }

        saveAllInChunks(youtubeCategoryRepository, newCategories);
        log.info("synced youtube categories. changed={}", changed);
        return new CategorySyncSummary(changed, newCategories.size());
    }

    public CategoryChannelDiscovery collectCategoryChannelDiscovery(
            YoutubeCategory category,
            String regionCode,
            int minChannelCount
    ) {
        if (category == null || Boolean.FALSE.equals(category.getAssignable())) {
            return null;
        }

        List<String> channelIds = youtubeApiClient.getChannelIdsByVideoCategory(
                String.valueOf(category.getYoutubeCategoryId()),
                regionCode,
                minChannelCount
        );
        if (channelIds.isEmpty()) {
            log.info("no youtube channels found. categoryId={} regionCode={}",
                    category.getYoutubeCategoryId(), regionCode);
            return null;
        }

        return new CategoryChannelDiscovery(
                category.getId(),
                category.getYoutubeCategoryId(),
                youtubeApiClient.getChannels(channelIds)
        );
    }

    public DiscoveryWriteSummary writeDiscoveredChannels(List<CategoryChannelDiscovery> discoveries) {
        List<CategoryChannelDiscovery> validDiscoveries = discoveries.stream()
                .filter(java.util.Objects::nonNull)
                .filter(discovery -> !discovery.channels().isEmpty())
                .toList();
        if (validDiscoveries.isEmpty()) {
            return new DiscoveryWriteSummary(0, 0);
        }

        Map<String, Channel> channels = upsertDiscoveredChannels(validDiscoveries.stream()
                .flatMap(discovery -> discovery.channels().stream())
                .toList());
        Map<Long, YoutubeCategory> categories = youtubeCategoryRepository.findAllById(
                        validDiscoveries.stream()
                                .map(CategoryChannelDiscovery::categoryId)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(YoutubeCategory::getId, category -> category));

        for (CategoryChannelDiscovery discovery : validDiscoveries) {
            YoutubeCategory category = categories.get(discovery.categoryId());
            if (category == null) {
                continue;
            }

            Map<String, Channel> categoryChannels = discovery.channels().stream()
                    .map(item -> channels.get(item.youtubeChannelId()))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toMap(
                            Channel::getYoutubeChannelId,
                            channel -> channel,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            upsertChannelCategories(category, categoryChannels);
            log.info("discovered youtube channels. categoryId={} collected={}",
                    discovery.youtubeCategoryId(), discovery.channels().size());
        }

        int discoveredChannelCount = validDiscoveries.stream()
                .mapToInt(discovery -> discovery.channels().size())
                .sum();
        return new DiscoveryWriteSummary(validDiscoveries.size(), discoveredChannelCount);
    }

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

        Map<String, Channel> managedChannels = refreshChannels(validAggregates.stream()
                .map(ChannelMetricsAggregate::channelItem)
                .toList());
        Map<String, ChannelMetricRow> metricRows = buildChannelMetricRows(validAggregates);
        upsertChannelStats(managedChannels, metricRows);
        upsertVideosAndStats(validAggregates, managedChannels, metricRows);

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
            YoutubeApiClient.PlaylistItemsPage page = youtubeApiClient.listPlaylistItems(
                    playlistId,
                    pageToken,
                    YOUTUBE_MAX_RESULTS
            );
            if (page.items().isEmpty()) {
                break;
            }

            for (PlaylistVideoItem item : page.items()) {
                if (!StringUtils.hasText(item.videoId()) || !StringUtils.hasText(item.videoPublishedAt())) {
                    continue;
                }

                LocalDateTime publishedAt = parseYoutubeDateTime(item.videoPublishedAt());
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
                totalViews += defaultLong(video.viewCount());
                totalLikes += defaultLong(video.likeCount());
                totalComments += defaultLong(video.commentCount());
            }

            double avgViews = recentVideos.isEmpty()
                    ? 0.0
                    : round((double) totalViews / recentVideos.size(), 2);
            double avgEngagementRate = totalViews > 0
                    ? round(((double) (totalLikes + totalComments) / totalViews) * 100.0, 2)
                    : 0.0;

            rows.put(channel.youtubeChannelId(), new ChannelMetricRow(
                    channel.youtubeChannelId(),
                    channel.subscriberCount(),
                    defaultLong(channel.viewCount()),
                    defaultLong(channel.videoCount()),
                    aggregate.recentUploadCount(),
                    avgViews,
                    avgEngagementRate,
                    aggregate.collectedAt()
            ));
        }
        return rows;
    }

    private Map<String, Channel> upsertDiscoveredChannels(List<YoutubeChannelItem> channelItems) {
        ExistingChannels existingChannels = existingChannels(channelItems);
        if (existingChannels.youtubeChannelIds().isEmpty()) {
            return Map.of();
        }

        Map<String, Channel> result = new LinkedHashMap<>();
        List<Channel> newChannels = new ArrayList<>();
        for (YoutubeChannelItem item : channelItems) {
            if (!StringUtils.hasText(item.youtubeChannelId()) || result.containsKey(item.youtubeChannelId())) {
                continue;
            }

            Channel channel = existingChannels.channelsByYoutubeId().get(item.youtubeChannelId());
            if (channel == null) {
                channel = Channel.of(
                        null,
                        item.title(),
                        item.youtubeChannelId(),
                        item.customUrl(),
                        item.thumbnailUrl(),
                        item.uploadsPlaylistId(),
                        parseYoutubeDateTime(item.publishedAt())
                );
                newChannels.add(channel);
            } else {
                applyChannelItem(channel, item);
            }
            result.put(item.youtubeChannelId(), channel);
        }

        saveAllInChunks(channelRepository, newChannels);
        return result;
    }

    private Map<String, Channel> refreshChannels(List<YoutubeChannelItem> channelItems) {
        ExistingChannels existingChannels = existingChannels(channelItems);
        if (existingChannels.youtubeChannelIds().isEmpty()) {
            return Map.of();
        }
        Map<String, Channel> result = new LinkedHashMap<>();

        for (YoutubeChannelItem item : channelItems) {
            if (!StringUtils.hasText(item.youtubeChannelId()) || result.containsKey(item.youtubeChannelId())) {
                continue;
            }

            Channel channel = existingChannels.channelsByYoutubeId().get(item.youtubeChannelId());
            if (channel == null) {
                log.warn("skipping channel metrics refresh for missing channel. youtubeChannelId={}", item.youtubeChannelId());
                continue;
            }

            applyChannelItem(channel, item);
            result.put(item.youtubeChannelId(), channel);
        }

        return result;
    }

    private List<String> distinctYoutubeChannelIds(List<YoutubeChannelItem> channelItems) {
        return channelItems.stream()
                .map(YoutubeChannelItem::youtubeChannelId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private ExistingChannels existingChannels(List<YoutubeChannelItem> channelItems) {
        List<String> youtubeChannelIds = distinctYoutubeChannelIds(channelItems);
        if (youtubeChannelIds.isEmpty()) {
            return new ExistingChannels(List.of(), Map.of());
        }
        return new ExistingChannels(youtubeChannelIds, loadExistingChannels(youtubeChannelIds));
    }

    private Map<String, Channel> loadExistingChannels(List<String> youtubeChannelIds) {
        return channelRepository.findByYoutubeChannelIdIn(youtubeChannelIds).stream()
                .collect(Collectors.toMap(Channel::getYoutubeChannelId, channel -> channel));
    }

    private record ExistingChannels(List<String> youtubeChannelIds, Map<String, Channel> channelsByYoutubeId) {
    }

    private void applyChannelItem(Channel channel, YoutubeChannelItem item) {
        channel.update(
                item.title(),
                item.customUrl(),
                item.thumbnailUrl(),
                item.uploadsPlaylistId(),
                parseYoutubeDateTime(item.publishedAt())
        );
    }

    private void upsertChannelCategories(YoutubeCategory category, Map<String, Channel> channels) {
        List<Long> channelIds = channels.values().stream()
                .map(Channel::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (channelIds.isEmpty()) {
            return;
        }

        Set<Long> existingChannelIds = channelCategoryRepository
                .findByCategoryIdAndChannelIdIn(category.getId(), channelIds)
                .stream()
                .map(relation -> relation.getChannel().getId())
                .collect(Collectors.toSet());

        List<ChannelCategory> newRelations = channels.values().stream()
                .filter(channel -> !existingChannelIds.contains(channel.getId()))
                .map(channel -> ChannelCategory.of(channel, category))
                .toList();

        saveAllInChunks(channelCategoryRepository, newRelations);
    }

    private void upsertChannelStats(
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
                        row.avgViewsRecentN(),
                        row.avgEngagementRateRecentN(),
                        row.collectedAt()
                ));
            } else {
                addChannelStatsHistoryIfAbsent(stats, row.collectedAt(), existingHistoryKeys, newHistories);
                stats.update(
                        row.subscriberCount(),
                        row.totalViewCount(),
                        row.totalVideoCount(),
                        row.recentUploadCount30d(),
                        row.avgViewsRecentN(),
                        row.avgEngagementRateRecentN(),
                        row.collectedAt()
                );
            }
        }

        saveAllInChunks(channelStatsHistoryRepository, newHistories);
        saveAllInChunks(channelStatsRepository, newStats);
    }

    private void upsertVideosAndStats(
            List<ChannelMetricsAggregate> aggregates,
            Map<String, Channel> managedChannels,
            Map<String, ChannelMetricRow> metricRows
    ) {
        List<PreparedVideoWrite> preparedVideos = new ArrayList<>();
        Set<String> seenVideoIds = new LinkedHashSet<>();

        for (ChannelMetricsAggregate aggregate : aggregates) {
            for (YoutubeVideoItem item : aggregate.recentVideos()) {
                if (item == null || !StringUtils.hasText(item.youtubeVideoId()) || !seenVideoIds.add(item.youtubeVideoId())) {
                    continue;
                }

                String ownerChannelId = StringUtils.hasText(item.youtubeChannelId())
                        ? item.youtubeChannelId()
                        : aggregate.youtubeChannelId();
                Channel channel = managedChannels.get(ownerChannelId);
                if (channel == null) {
                    continue;
                }

                preparedVideos.add(new PreparedVideoWrite(
                        item.youtubeVideoId(),
                        item,
                        channel,
                        metricRows.get(ownerChannelId),
                        parseIso8601DurationToSeconds(item.duration()),
                        parseYoutubeDateTime(item.publishedAt()),
                        aggregate.collectedAt()
                ));
            }
        }

        Map<String, Video> videos = upsertVideos(preparedVideos);
        upsertVideoStatsAndTags(preparedVideos, videos);
    }

    private Map<String, Video> upsertVideos(List<PreparedVideoWrite> preparedVideos) {
        List<String> youtubeVideoIds = preparedVideos.stream()
                .map(PreparedVideoWrite::youtubeVideoId)
                .distinct()
                .toList();
        if (youtubeVideoIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Video> existingVideos = videoRepository.findByYoutubeVideoIdIn(youtubeVideoIds).stream()
                .collect(Collectors.toMap(
                        Video::getYoutubeVideoId,
                        video -> video,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Video> newVideos = new ArrayList<>();

        for (PreparedVideoWrite prepared : preparedVideos) {
            Video video = existingVideos.get(prepared.youtubeVideoId());
            if (video == null) {
                video = Video.of(
                        prepared.channel(),
                        prepared.item().categoryId(),
                        prepared.youtubeVideoId(),
                        prepared.item().title(),
                        prepared.item().description(),
                        prepared.item().thumbnailUrl(),
                        prepared.durationSeconds(),
                        prepared.durationSeconds() != null && prepared.durationSeconds() <= 60,
                        false,
                        prepared.publishedAt()
                );
                existingVideos.put(prepared.youtubeVideoId(), video);
                newVideos.add(video);
            } else {
                video.update(
                        prepared.channel(),
                        prepared.item().categoryId(),
                        prepared.item().title(),
                        prepared.item().description(),
                        prepared.item().thumbnailUrl(),
                        prepared.durationSeconds(),
                        prepared.durationSeconds() != null && prepared.durationSeconds() <= 60,
                        false,
                        prepared.publishedAt()
                );
            }
        }

        saveAllInChunks(videoRepository, newVideos);
        return existingVideos;
    }

    private void upsertVideoStatsAndTags(
            List<PreparedVideoWrite> preparedVideos,
            Map<String, Video> videos
    ) {
        List<Long> videoIds = videos.values().stream()
                .map(Video::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (videoIds.isEmpty()) {
            return;
        }

        Map<Long, VideoStats> existingStats = videoStatsRepository.findByVideoIdIn(videoIds).stream()
                .collect(Collectors.toMap(stats -> stats.getVideo().getId(), stats -> stats));
        Set<String> existingTagKeys = videoTagRepository.findByVideoIdIn(videoIds).stream()
                .map(tag -> videoTagKey(tag.getVideo().getId(), tag.getTag()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<VideoStats> newStats = new ArrayList<>();
        List<VideoTag> newTags = new ArrayList<>();

        for (PreparedVideoWrite prepared : preparedVideos) {
            Video video = videos.get(prepared.youtubeVideoId());
            if (video == null) {
                continue;
            }

            Double outlierScore = calculateOutlierScore(prepared.item().viewCount(), prepared.channelMetric());
            Double vph = calculateViewsPerHour(prepared.item().viewCount(), prepared.publishedAt(), prepared.collectedAt());
            VideoStats stats = existingStats.get(video.getId());
            if (stats == null) {
                newStats.add(VideoStats.of(
                        video,
                        defaultLong(prepared.item().viewCount()),
                        defaultLong(prepared.item().likeCount()),
                        defaultLong(prepared.item().commentCount()),
                        vph,
                        outlierScore,
                        null,
                        prepared.collectedAt()
                ));
            } else {
                stats.update(
                        defaultLong(prepared.item().viewCount()),
                        defaultLong(prepared.item().likeCount()),
                        defaultLong(prepared.item().commentCount()),
                        vph,
                        outlierScore,
                        null,
                        prepared.collectedAt()
                );
            }

            for (String rawTag : prepared.item().tags()) {
                String tag = rawTag == null ? null : rawTag.trim();
                if (!StringUtils.hasText(tag)) {
                    continue;
                }
                String key = videoTagKey(video.getId(), tag);
                if (existingTagKeys.add(key)) {
                    newTags.add(VideoTag.of(video, tag));
                }
            }
        }

        saveAllInChunks(videoStatsRepository, newStats);
        saveAllInChunks(videoTagRepository, newTags);
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

    private String videoTagKey(Long videoId, String tag) {
        return videoId + "::" + tag;
    }

    private <T> void saveAllInChunks(JpaRepository<T, ?> repository, List<T> entities) {
        if (entities.isEmpty()) {
            return;
        }
        for (List<T> chunk : chunked(entities, WRITE_CHUNK_SIZE)) {
            repository.saveAll(chunk);
        }
    }

    private Double calculateOutlierScore(Long videoViewCount, ChannelMetricRow channelMetric) {
        if (channelMetric == null || defaultLong(channelMetric.totalVideoCount()) <= 0) {
            return null;
        }
        double avgChannelViewCount = (double) defaultLong(channelMetric.totalViewCount())
                / defaultLong(channelMetric.totalVideoCount());
        if (avgChannelViewCount <= 0) {
            return null;
        }
        return round(defaultLong(videoViewCount) / avgChannelViewCount, 6);
    }

    private Double calculateViewsPerHour(Long videoViewCount, LocalDateTime publishedAt, LocalDateTime collectedAt) {
        if (publishedAt == null) {
            return null;
        }
        double elapsedSeconds = java.time.Duration.between(publishedAt, collectedAt).toSeconds();
        if (elapsedSeconds <= 0) {
            return null;
        }
        double elapsedHours = Math.max(elapsedSeconds / 3600.0, 1.0);
        return round(defaultLong(videoViewCount) / elapsedHours, 6);
    }

    private LocalDateTime parseYoutubeDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer parseIso8601DurationToSeconds(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = YOUTUBE_DURATION.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int days = parseInt(matcher.group("days"));
        int hours = parseInt(matcher.group("hours"));
        int minutes = parseInt(matcher.group("minutes"));
        int seconds = parseInt(matcher.group("seconds"));
        return days * 86_400 + hours * 3_600 + minutes * 60 + seconds;
    }

    private int parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private <T> List<List<T>> chunked(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(values.size(), start + size)));
        }
        return chunks;
    }

}
