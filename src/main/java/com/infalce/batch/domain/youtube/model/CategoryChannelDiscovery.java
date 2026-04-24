package com.infalce.batch.domain.youtube.model;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeChannelItem;

import java.util.List;

public record CategoryChannelDiscovery(
        Long categoryId,
        Integer youtubeCategoryId,
        List<YoutubeChannelItem> channels
) {
}
