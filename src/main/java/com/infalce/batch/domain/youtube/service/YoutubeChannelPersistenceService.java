package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeChannelItem;
import com.infalce.batch.domain.youtube.repository.ChannelRepository;
import com.infalce.batch.entity.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeChannelPersistenceService {

    private final ChannelRepository channelRepository;

    public Map<String, Channel> upsertDiscoveredChannels(List<YoutubeChannelItem> channelItems) {
        ExistingChannels existingChannels = existingChannels(channelItems);
        if (existingChannels.youtubeChannelIds().isEmpty()) {
            return Map.of();
        }

        Map<String, Channel> result = new LinkedHashMap<>();
        List<Channel> newChannels = new java.util.ArrayList<>();
        for (YoutubeChannelItem item : channelItems) {
            if (!StringUtils.hasText(item.youtubeChannelId()) || result.containsKey(item.youtubeChannelId())) {
                continue;
            }

            Channel channel = existingChannels.channelsByYoutubeId().get(item.youtubeChannelId());
            if (channel == null) {
                channel = Channel.of(
                        null,
                        item.title(),
                        item.description(),
                        item.youtubeChannelId(),
                        item.customUrl(),
                        item.thumbnailUrl(),
                        item.bannerImageUrl(),
                        item.uploadsPlaylistId(),
                        YoutubeBatchSupport.parseYoutubeDateTime(item.publishedAt())
                );
                newChannels.add(channel);
            } else {
                applyChannelItem(channel, item);
            }
            result.put(item.youtubeChannelId(), channel);
        }

        YoutubeBatchSupport.saveAllInChunks(channelRepository, newChannels);
        return result;
    }

    public Map<String, Channel> refreshChannels(List<YoutubeChannelItem> channelItems) {
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

    private ExistingChannels existingChannels(List<YoutubeChannelItem> channelItems) {
        List<String> youtubeChannelIds = channelItems.stream()
                .map(YoutubeChannelItem::youtubeChannelId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (youtubeChannelIds.isEmpty()) {
            return new ExistingChannels(List.of(), Map.of());
        }
        return new ExistingChannels(youtubeChannelIds, loadExistingChannels(youtubeChannelIds));
    }

    private Map<String, Channel> loadExistingChannels(List<String> youtubeChannelIds) {
        return channelRepository.findByYoutubeChannelIdIn(youtubeChannelIds).stream()
                .collect(Collectors.toMap(Channel::getYoutubeChannelId, channel -> channel));
    }

    private void applyChannelItem(Channel channel, YoutubeChannelItem item) {
        channel.update(
                item.title(),
                item.description(),
                item.customUrl(),
                item.thumbnailUrl(),
                item.bannerImageUrl(),
                item.uploadsPlaylistId(),
                YoutubeBatchSupport.parseYoutubeDateTime(item.publishedAt())
        );
    }

    private record ExistingChannels(List<String> youtubeChannelIds, Map<String, Channel> channelsByYoutubeId) {
    }
}
