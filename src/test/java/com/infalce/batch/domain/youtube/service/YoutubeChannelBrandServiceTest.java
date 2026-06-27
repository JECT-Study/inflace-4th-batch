package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.ai.BrandAiExtractor;
import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import com.infalce.batch.domain.youtube.model.PreparedVideoWrite;
import com.infalce.batch.domain.youtube.repository.BrandRepository;
import com.infalce.batch.domain.youtube.repository.ChannelBrandRepository;
import com.infalce.batch.entity.brand.Brand;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.channel.ChannelBrand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YoutubeChannelBrandServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void marksExistingBrandResolvedByAiAsNotAiGenerated() {
        BrandRepository brandRepository = mock(BrandRepository.class);
        ChannelBrandRepository channelBrandRepository = mock(ChannelBrandRepository.class);
        BrandDescriptionMatcher brandDescriptionMatcher = mock(BrandDescriptionMatcher.class);
        BrandAiExtractor brandAiExtractor = mock(BrandAiExtractor.class);
        YoutubeChannelBrandService service = new YoutubeChannelBrandService(
                brandRepository,
                channelBrandRepository,
                brandDescriptionMatcher,
                brandAiExtractor
        );

        Brand coupang = Brand.of("쿠팡", false);
        ReflectionTestUtils.setField(coupang, "id", 1L);
        Channel channel = Channel.of(null, "channel", null, "youtube-channel-id", null, null, null, null, null);
        ReflectionTestUtils.setField(channel, "id", 10L);
        PreparedVideoWrite preparedVideo = new PreparedVideoWrite(
                "youtube-video-id",
                video("쿠팡 할인 이벤트"),
                channel,
                null,
                60,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(brandAiExtractor.extractBrand(any())).thenReturn(Optional.of("쿠팡"));
        when(brandDescriptionMatcher.matchBrandName("쿠팡")).thenReturn(Optional.empty());
        when(brandRepository.findByNameLike("쿠팡")).thenReturn(List.of(coupang));
        when(channelBrandRepository.findByChannelIdIn(List.of(10L))).thenReturn(List.of());

        service.upsertChannelBrands(List.of(preparedVideo), Set.of("youtube-video-id"));

        ArgumentCaptor<Iterable<ChannelBrand>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(channelBrandRepository).saveAll(captor.capture());
        ChannelBrand saved = captor.getValue().iterator().next();
        assertSame(coupang, saved.getBrand());
        assertFalse(saved.isAiGenerated());
        verify(brandRepository, never()).save(any());
    }

    private YoutubeVideoItem video(String description) {
        return new YoutubeVideoItem(
                "youtube-video-id",
                "youtube-channel-id",
                "title",
                description,
                "2026-06-27T00:00:00Z",
                List.of(),
                null,
                null,
                null,
                null,
                "PT1M",
                0L,
                0L,
                0L,
                true
        );
    }
}
