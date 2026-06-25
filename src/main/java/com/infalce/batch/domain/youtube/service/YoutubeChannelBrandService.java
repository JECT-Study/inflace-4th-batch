package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.ai.BrandAiExtractor;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import com.infalce.batch.domain.youtube.model.PreparedVideoWrite;
import com.infalce.batch.domain.youtube.repository.BrandRepository;
import com.infalce.batch.domain.youtube.repository.ChannelBrandRepository;
import com.infalce.batch.entity.brand.Brand;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.channel.ChannelBrand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeChannelBrandService {

    private final BrandRepository brandRepository;
    private final ChannelBrandRepository channelBrandRepository;
    private final BrandDescriptionMatcher brandDescriptionMatcher;
    private final BrandAiExtractor brandAiExtractor;

    public void upsertChannelBrands(List<PreparedVideoWrite> preparedVideos, Set<String> newYoutubeVideoIds) {
        if (preparedVideos.isEmpty() || newYoutubeVideoIds.isEmpty()) {
            return;
        }

        List<PreparedVideoWrite> targetVideos = preparedVideos.stream()
                .filter(prepared -> prepared.channel() != null && prepared.channel().getId() != null)
                .filter(prepared -> newYoutubeVideoIds.contains(prepared.youtubeVideoId()))
                .toList();
        if (targetVideos.isEmpty()) {
            return;
        }

        Map<Long, Channel> channelsById = targetVideos.stream()
                .map(PreparedVideoWrite::channel)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Channel::getId,
                        channel -> channel,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Map<Long, ChannelBrandMatchSource>> matchesByChannelId = new LinkedHashMap<>();
        boolean hasCandidate = false;
        for (PreparedVideoWrite prepared : targetVideos) {
            YoutubeVideoItem item = prepared.item();
            if (!item.hasPaidProductPlacement() || !StringUtils.hasText(item.description())) {
                continue;
            }

            hasCandidate = true;
            Map<Long, ChannelBrandMatchSource> channelMatches = matchesByChannelId.computeIfAbsent(
                    prepared.channel().getId(),
                    key -> new LinkedHashMap<>()
            );

            Optional<BrandDescriptionMatcher.BrandMatch> aiMatch = extractAiBrand(new BrandAiExtractor.BrandAiVideo(
                            prepared.youtubeVideoId(),
                            item.title(),
                            item.description(),
                            item.tags()
                    ))
                    .flatMap(this::resolveAiBrand);
            if (aiMatch.isPresent()) {
                BrandDescriptionMatcher.BrandMatch match = aiMatch.get();
                channelMatches.put(match.brand().getId(), new ChannelBrandMatchSource(
                        match.brand(),
                        match.matchedAlias(),
                        prepared.youtubeVideoId(),
                        true
                ));
                continue;
            }

            for (BrandDescriptionMatcher.BrandMatch match : brandDescriptionMatcher.matchDescription(item.description())) {
                if (match.brand() == null || match.brand().getId() == null) {
                    continue;
                }

                ChannelBrandMatchSource candidate = new ChannelBrandMatchSource(match, prepared.youtubeVideoId());
                ChannelBrandMatchSource existing = channelMatches.get(match.brand().getId());
                if (existing == null
                        || candidate.matchedAlias().length() > existing.matchedAlias().length()) {
                    channelMatches.put(match.brand().getId(), candidate);
                }
            }
        }
        if (!hasCandidate) {
            return;
        }

        List<Long> channelIds = targetVideos.stream()
                .map(PreparedVideoWrite::channel)
                .filter(Objects::nonNull)
                .map(Channel::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (channelIds.isEmpty()) {
            return;
        }

        Map<String, ChannelBrand> existingMatches = channelBrandRepository.findByChannelIdIn(channelIds).stream()
                .collect(Collectors.toMap(
                        relation -> channelBrandKey(relation.getChannel().getId(), relation.getBrand().getId()),
                        relation -> relation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ChannelBrand> newMatches = new ArrayList<>();

        for (Map.Entry<Long, Map<Long, ChannelBrandMatchSource>> channelEntry : matchesByChannelId.entrySet()) {
            Channel channel = channelsById.get(channelEntry.getKey());
            if (channel == null) {
                continue;
            }

            for (ChannelBrandMatchSource matchSource : channelEntry.getValue().values()) {
                String key = channelBrandKey(channel.getId(), matchSource.brand().getId());
                ChannelBrand relation = existingMatches.get(key);
                if (relation == null) {
                    newMatches.add(ChannelBrand.of(
                            channel,
                            matchSource.brand(),
                            matchSource.matchedAlias(),
                            matchSource.sourceYoutubeVideoId(),
                            matchSource.aiGenerated()
                    ));
                } else {
                    relation.update(
                            matchSource.matchedAlias(),
                            matchSource.sourceYoutubeVideoId(),
                            matchSource.aiGenerated()
                    );
                }
            }
        }

        YoutubeBatchSupport.saveAllInChunks(channelBrandRepository, newMatches);
    }

    private Optional<String> extractAiBrand(BrandAiExtractor.BrandAiVideo video) {
        try {
            return brandAiExtractor.extractBrand(video);
        } catch (RuntimeException e) {
            log.warn("youtube brand AI extraction skipped. youtubeVideoId={} message={}",
                    video.youtubeVideoId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BrandDescriptionMatcher.BrandMatch> resolveAiBrand(String rawBrandName) {
        String brandName = normalizeAiBrandName(rawBrandName);
        if (!StringUtils.hasText(brandName)) {
            return Optional.empty();
        }

        Optional<BrandDescriptionMatcher.BrandMatch> indexedMatch = brandDescriptionMatcher.matchBrandName(brandName);
        if (indexedMatch.isPresent()) {
            return indexedMatch;
        }

        Optional<Brand> existingBrand = brandRepository.findByNameLike(brandName)
                .stream()
                .findFirst();
        if (existingBrand.isPresent()) {
            return Optional.of(new BrandDescriptionMatcher.BrandMatch(existingBrand.get(), existingBrand.get().getName()));
        }

        Brand newBrand = brandRepository.save(Brand.of(brandName, true));
        return Optional.of(new BrandDescriptionMatcher.BrandMatch(newBrand, brandName));
    }

    private String normalizeAiBrandName(String rawBrandName) {
        if (!StringUtils.hasText(rawBrandName)) {
            return null;
        }
        String brandName = rawBrandName.trim()
                .replaceAll("^[\"'`]+", "")
                .replaceAll("[\"'`]+$", "")
                .trim();
        if (!StringUtils.hasText(brandName) || brandName.length() > 255 || brandName.contains("\n")) {
            return null;
        }
        return brandName;
    }

    private String channelBrandKey(Long channelId, Long brandId) {
        return channelId + "::" + brandId;
    }

    private record ChannelBrandMatchSource(
            Brand brand,
            String matchedAlias,
            String sourceYoutubeVideoId,
            boolean aiGenerated
    ) {
        private ChannelBrandMatchSource(BrandDescriptionMatcher.BrandMatch match, String sourceYoutubeVideoId) {
            this(match.brand(), match.matchedAlias(), sourceYoutubeVideoId, false);
        }
    }
}
