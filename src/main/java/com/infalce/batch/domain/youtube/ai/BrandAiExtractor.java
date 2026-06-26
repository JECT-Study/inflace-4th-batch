package com.infalce.batch.domain.youtube.ai;

import java.util.Optional;

public interface BrandAiExtractor {

    Optional<String> extractBrand(BrandAiVideo video);

    record BrandAiVideo(
            String youtubeVideoId,
            String title,
            String description,
            java.util.List<String> tags
    ) {
    }
}
