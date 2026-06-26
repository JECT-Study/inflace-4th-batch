package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.model.ChannelMetricRow;
import com.infalce.batch.domain.youtube.model.ChannelMetricsAggregate;
import com.infalce.batch.domain.youtube.model.PreparedVideoWrite;
import com.infalce.batch.domain.youtube.repository.VideoRepository;
import com.infalce.batch.domain.youtube.repository.VideoStatsRepository;
import com.infalce.batch.domain.youtube.repository.VideoTagRepository;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.video.Video;
import com.infalce.batch.entity.video.VideoStats;
import com.infalce.batch.entity.video.VideoTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YoutubeVideoIngestionService {

    private final VideoRepository videoRepository;
    private final VideoStatsRepository videoStatsRepository;
    private final VideoTagRepository videoTagRepository;
    private final YoutubeChannelBrandService youtubeChannelBrandService;

    public void upsertVideosAndStats(
            List<ChannelMetricsAggregate> aggregates,
            Map<String, Channel> managedChannels,
            Map<String, ChannelMetricRow> metricRows
    ) {
        List<PreparedVideoWrite> preparedVideos = new ArrayList<>();
        Set<String> seenVideoIds = new LinkedHashSet<>();

        for (ChannelMetricsAggregate aggregate : aggregates) {
            for (var item : aggregate.recentVideos()) {
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
                        YoutubeBatchSupport.parseIso8601DurationToSeconds(item.duration()),
                        YoutubeBatchSupport.parseYoutubeDateTime(item.publishedAt()),
                        aggregate.collectedAt()
                ));
            }
        }

        UpsertVideosResult videosResult = upsertVideos(preparedVideos);
        youtubeChannelBrandService.upsertChannelBrands(preparedVideos, videosResult.newYoutubeVideoIds());
        upsertVideoStatsAndTags(preparedVideos, videosResult.videos());
    }

    private UpsertVideosResult upsertVideos(List<PreparedVideoWrite> preparedVideos) {
        List<String> youtubeVideoIds = preparedVideos.stream()
                .map(PreparedVideoWrite::youtubeVideoId)
                .distinct()
                .toList();
        if (youtubeVideoIds.isEmpty()) {
            return new UpsertVideosResult(Map.of(), Set.of());
        }

        Map<String, Video> existingVideos = videoRepository.findByYoutubeVideoIdIn(youtubeVideoIds).stream()
                .collect(Collectors.toMap(
                        Video::getYoutubeVideoId,
                        video -> video,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Video> newVideos = new ArrayList<>();
        Set<String> newYoutubeVideoIds = new LinkedHashSet<>();

        for (PreparedVideoWrite prepared : preparedVideos) {
            Video video = existingVideos.get(prepared.youtubeVideoId());
            boolean isShort = isShortVideo(prepared.item(), prepared.durationSeconds());
            if (video == null) {
                video = Video.of(
                        prepared.channel(),
                        prepared.item().categoryId(),
                        prepared.youtubeVideoId(),
                        prepared.item().title(),
                        prepared.item().description(),
                        prepared.item().thumbnailUrl(),
                        prepared.durationSeconds(),
                        isShort,
                        prepared.item().hasPaidProductPlacement(),
                        prepared.publishedAt()
                );
                existingVideos.put(prepared.youtubeVideoId(), video);
                newVideos.add(video);
                newYoutubeVideoIds.add(prepared.youtubeVideoId());
            } else {
                video.update(
                        prepared.channel(),
                        prepared.item().categoryId(),
                        prepared.item().title(),
                        prepared.item().description(),
                        prepared.item().thumbnailUrl(),
                        prepared.durationSeconds(),
                        isShort,
                        prepared.item().hasPaidProductPlacement(),
                        prepared.publishedAt()
                );
            }
        }

        YoutubeBatchSupport.saveAllInChunks(videoRepository, newVideos);
        return new UpsertVideosResult(existingVideos, newYoutubeVideoIds);
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
                        YoutubeBatchSupport.defaultLong(prepared.item().viewCount()),
                        YoutubeBatchSupport.defaultLong(prepared.item().likeCount()),
                        YoutubeBatchSupport.defaultLong(prepared.item().commentCount()),
                        vph,
                        outlierScore,
                        null,
                        prepared.collectedAt()
                ));
            } else {
                stats.update(
                        YoutubeBatchSupport.defaultLong(prepared.item().viewCount()),
                        YoutubeBatchSupport.defaultLong(prepared.item().likeCount()),
                        YoutubeBatchSupport.defaultLong(prepared.item().commentCount()),
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

        YoutubeBatchSupport.saveAllInChunks(videoStatsRepository, newStats);
        YoutubeBatchSupport.saveAllInChunks(videoTagRepository, newTags);
    }

    static boolean isShortVideo(com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem item, Integer durationSeconds) {
        return YoutubeBatchSupport.isShortVideo(item, durationSeconds);
    }

    private Double calculateOutlierScore(Long videoViewCount, ChannelMetricRow channelMetric) {
        if (channelMetric == null) {
            return null;
        }
        return ChannelOutlierCalculator.calculateOutlierScore(
                videoViewCount,
                channelMetric.totalViewCount(),
                channelMetric.totalVideoCount()
        );
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
        return YoutubeBatchSupport.round(YoutubeBatchSupport.defaultLong(videoViewCount) / elapsedHours, 6);
    }

    private String videoTagKey(Long videoId, String tag) {
        return videoId + "::" + tag;
    }

    private record UpsertVideosResult(
            Map<String, Video> videos,
            Set<String> newYoutubeVideoIds
    ) {
    }
}
