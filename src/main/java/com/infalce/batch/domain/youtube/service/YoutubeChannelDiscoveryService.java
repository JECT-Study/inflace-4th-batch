package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeChannelItem;
import com.infalce.batch.domain.youtube.batch.summary.DiscoveryWriteSummary;
import com.infalce.batch.domain.youtube.model.CategoryChannelDiscovery;
import com.infalce.batch.domain.youtube.repository.ChannelCategoryRepository;
import com.infalce.batch.domain.youtube.repository.YoutubeCategoryRepository;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.channel.ChannelCategory;
import com.infalce.batch.entity.video.YoutubeCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeChannelDiscoveryService {

    private final YoutubeApiClient youtubeApiClient;
    private final YoutubeCategoryRepository youtubeCategoryRepository;
    private final ChannelCategoryRepository channelCategoryRepository;
    private final YoutubeChannelPersistenceService channelPersistenceService;

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

        List<YoutubeChannelItem> channels = youtubeApiClient.getChannels(channelIds).stream()
                .filter(item -> !isTopicChannel(item))
                .toList();

        return new CategoryChannelDiscovery(
                category.getId(),
                category.getYoutubeCategoryId(),
                channels
        );
    }

    static boolean isTopicChannel(YoutubeChannelItem item) {
        return item != null
                && StringUtils.hasText(item.title())
                && item.title().strip().toLowerCase(Locale.ROOT).endsWith("- topic");
    }

    public DiscoveryWriteSummary writeDiscoveredChannels(List<CategoryChannelDiscovery> discoveries) {
        List<CategoryChannelDiscovery> validDiscoveries = discoveries.stream()
                .filter(java.util.Objects::nonNull)
                .filter(discovery -> !discovery.channels().isEmpty())
                .toList();
        if (validDiscoveries.isEmpty()) {
            return new DiscoveryWriteSummary(0, 0);
        }

        Map<String, Channel> channels = channelPersistenceService.upsertDiscoveredChannels(validDiscoveries.stream()
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

        YoutubeBatchSupport.saveAllInChunks(channelCategoryRepository, newRelations);
    }
}
