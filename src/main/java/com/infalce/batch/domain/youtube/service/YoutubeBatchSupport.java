package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeVideoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YoutubeBatchSupport {

    static final int WRITE_CHUNK_SIZE = 100;

    private static final Pattern YOUTUBE_DURATION = Pattern.compile(
            "^P(?:(?<days>\\d+)D)?(?:T(?:(?<hours>\\d+)H)?(?:(?<minutes>\\d+)M)?(?:(?<seconds>\\d+)S)?)?$"
    );

    private YoutubeBatchSupport() {
    }

    static <T> void saveAllInChunks(JpaRepository<T, ?> repository, List<T> entities) {
        if (entities.isEmpty()) {
            return;
        }
        for (List<T> chunk : chunked(entities, WRITE_CHUNK_SIZE)) {
            repository.saveAll(chunk);
        }
    }

    static LocalDateTime parseYoutubeDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Integer parseIso8601DurationToSeconds(String value) {
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

    static boolean isShortVideo(YoutubeVideoItem item, Integer durationSeconds) {
        boolean isShortDuration = durationSeconds != null && durationSeconds <= 180;
        Integer thumbnailWidth = item.thumbnailWidth();
        Integer thumbnailHeight = item.thumbnailHeight();
        boolean isPortraitThumbnail = thumbnailWidth != null
                && thumbnailHeight != null
                && thumbnailWidth > 0
                && thumbnailHeight > 0
                && thumbnailHeight > thumbnailWidth;

        return isShortDuration || isPortraitThumbnail;
    }

    static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    static double round(double value, int scale) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(scale, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    static <T> List<List<T>> chunked(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(values.size(), start + size)));
        }
        return chunks;
    }

    private static int parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }
}
