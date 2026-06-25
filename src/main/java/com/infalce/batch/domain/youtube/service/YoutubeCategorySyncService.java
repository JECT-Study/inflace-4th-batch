package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeCategoryItem;
import com.infalce.batch.domain.youtube.batch.summary.CategorySyncSummary;
import com.infalce.batch.domain.youtube.repository.YoutubeCategoryRepository;
import com.infalce.batch.entity.video.YoutubeCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeCategorySyncService {

    private final YoutubeApiClient youtubeApiClient;
    private final YoutubeCategoryRepository youtubeCategoryRepository;

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

        YoutubeBatchSupport.saveAllInChunks(youtubeCategoryRepository, newCategories);
        log.info("synced youtube categories. changed={}", changed);
        return new CategorySyncSummary(changed, newCategories.size());
    }
}
