package com.infalce.batch.domain.youtube.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class YoutubeApiClient {

    private static final int YOUTUBE_MAX_RESULTS = 50;

    private final YoutubeBatchProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public YoutubeApiClient(YoutubeBatchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public List<YoutubeCategoryItem> listVideoCategories(String regionCode, String hl) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("part", "snippet");
        if (StringUtils.hasText(regionCode)) {
            params.put("regionCode", regionCode);
        }
        if (StringUtils.hasText(hl)) {
            params.put("hl", hl);
        }

        JsonNode root = get("videoCategories", params);

        List<YoutubeCategoryItem> categories = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            JsonNode snippet = item.path("snippet");
            categories.add(new YoutubeCategoryItem(
                    toInteger(item.path("id").asText(null)),
                    snippet.path("title").asText(null),
                    snippet.path("assignable").asBoolean(false)
            ));
        }
        return categories;
    }

    public List<String> getChannelIdsByVideoCategory(String categoryId, String regionCode, int targetCount) {
        Set<String> channelIds = new LinkedHashSet<>();
        String pageToken = null;

        while (channelIds.size() < targetCount) {
            Map<String, String> params = new java.util.LinkedHashMap<>();
            params.put("part", "snippet");
            params.put("chart", "mostPopular");
            params.put("regionCode", regionCode);
            params.put("videoCategoryId", categoryId);
            params.put("maxResults", String.valueOf(YOUTUBE_MAX_RESULTS));
            params.put("fields", "nextPageToken,items(snippet(channelId,channelTitle,title))");
            if (StringUtils.hasText(pageToken)) {
                params.put("pageToken", pageToken);
            }

            JsonNode root;
            try {
                root = get("videos", params);
            } catch (YoutubeApiException e) {
                if (e.isNotFound()) {
                    log.warn("youtube video chart not found. categoryId={} regionCode={}", categoryId, regionCode);
                    break;
                }
                throw e;
            }
            JsonNode items = root.path("items");
            if (!items.elements().hasNext()) {
                break;
            }

            for (JsonNode item : items) {
                String channelId = item.path("snippet").path("channelId").asText(null);
                if (StringUtils.hasText(channelId)) {
                    channelIds.add(channelId);
                }
                if (channelIds.size() >= targetCount) {
                    break;
                }
            }

            pageToken = root.path("nextPageToken").asText(null);
            if (!StringUtils.hasText(pageToken)) {
                break;
            }
        }

        return new ArrayList<>(channelIds);
    }

    public List<YoutubeChannelItem> getChannels(List<String> channelIds) {
        List<String> normalizedChannelIds = normalizeIds(channelIds);
        if (normalizedChannelIds.isEmpty()) {
            return List.of();
        }

        List<YoutubeChannelItem> result = new ArrayList<>();
        for (List<String> chunk : chunked(normalizedChannelIds, YOUTUBE_MAX_RESULTS)) {
            JsonNode root = get("channels", Map.of(
                    "part", "snippet,statistics,contentDetails",
                    "id", String.join(",", chunk),
                    "fields", "items(id,snippet(title,customUrl,publishedAt,thumbnails),statistics(subscriberCount,hiddenSubscriberCount,videoCount,viewCount),contentDetails(relatedPlaylists/uploads))"
            ));

            for (JsonNode item : root.path("items")) {
                JsonNode snippet = item.path("snippet");
                JsonNode statistics = item.path("statistics");
                boolean hiddenSubscriberCount = statistics.path("hiddenSubscriberCount").asBoolean(false);
                result.add(new YoutubeChannelItem(
                        item.path("id").asText(null),
                        snippet.path("title").asText(null),
                        snippet.path("customUrl").asText(null),
                        snippet.path("publishedAt").asText(null),
                        bestThumbnailUrl(snippet.path("thumbnails")),
                        hiddenSubscriberCount ? null : toLong(statistics.path("subscriberCount").asText(null)),
                        hiddenSubscriberCount,
                        toLong(statistics.path("videoCount").asText(null), 0L),
                        toLong(statistics.path("viewCount").asText(null), 0L),
                        item.path("contentDetails").path("relatedPlaylists").path("uploads").asText(null)
                ));
            }
        }
        return result;
    }

    public PlaylistItemsPage listPlaylistItems(String playlistId, String pageToken, int maxResults) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("part", "contentDetails");
        params.put("playlistId", playlistId);
        params.put("maxResults", String.valueOf(maxResults));
        params.put("fields", "nextPageToken,items(contentDetails(videoId,videoPublishedAt))");
        if (StringUtils.hasText(pageToken)) {
            params.put("pageToken", pageToken);
        }

        JsonNode root = get("playlistItems", params);
        List<PlaylistVideoItem> videos = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            JsonNode details = item.path("contentDetails");
            videos.add(new PlaylistVideoItem(
                    details.path("videoId").asText(null),
                    details.path("videoPublishedAt").asText(null)
            ));
        }
        return new PlaylistItemsPage(root.path("nextPageToken").asText(null), videos);
    }

    public List<YoutubeVideoItem> getVideos(List<String> videoIds) {
        List<String> normalizedVideoIds = normalizeIds(videoIds);
        if (normalizedVideoIds.isEmpty()) {
            return List.of();
        }

        List<YoutubeVideoItem> result = new ArrayList<>();
        for (List<String> chunk : chunked(normalizedVideoIds, YOUTUBE_MAX_RESULTS)) {
            JsonNode root = get("videos", Map.of(
                    "part", "snippet,contentDetails,statistics",
                    "id", String.join(",", chunk),
                    "fields", "items(id,snippet(channelId,title,description,publishedAt,tags,thumbnails,categoryId),contentDetails(duration),statistics(viewCount,likeCount,commentCount))"
            ));

            for (JsonNode item : root.path("items")) {
                JsonNode snippet = item.path("snippet");
                JsonNode statistics = item.path("statistics");
                List<String> tags = new ArrayList<>();
                for (JsonNode tag : snippet.path("tags")) {
                    tags.add(tag.asText());
                }
                result.add(new YoutubeVideoItem(
                        item.path("id").asText(null),
                        snippet.path("channelId").asText(null),
                        snippet.path("title").asText(null),
                        snippet.path("description").asText(null),
                        snippet.path("publishedAt").asText(null),
                        tags,
                        bestThumbnailUrl(snippet.path("thumbnails")),
                        toInteger(snippet.path("categoryId").asText(null)),
                        item.path("contentDetails").path("duration").asText(null),
                        toLong(statistics.path("viewCount").asText(null), 0L),
                        toLong(statistics.path("likeCount").asText(null), 0L),
                        toLong(statistics.path("commentCount").asText(null), 0L)
                ));
            }
        }
        return result;
    }

    private JsonNode get(String resource, Map<String, String> params) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("youtube.batch.api-key or env YT_API_KEY is required");
        }

        Map<String, String> requestParams = new java.util.LinkedHashMap<>(params);
        requestParams.put("key", properties.getApiKey());

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.pathSegment(resource);
                        requestParams.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException e) {
            throw new YoutubeApiException(
                    resource,
                    e.getStatusCode().value(),
                    sanitizeParams(requestParams),
                    trimToSingleLine(e.getResponseBodyAsString()),
                    e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "YouTube API " + resource + " request failed. params=" + sanitizeParams(requestParams),
                    e
            );
        }
    }

    private Map<String, String> sanitizeParams(Map<String, String> params) {
        Map<String, String> sanitized = new java.util.LinkedHashMap<>(params);
        if (sanitized.containsKey("key")) {
            sanitized.put("key", "***");
        }
        return sanitized;
    }

    private String trimToSingleLine(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String bestThumbnailUrl(JsonNode thumbnails) {
        for (String size : List.of("maxres", "standard", "high", "medium", "default")) {
            String url = thumbnails.path(size).path("url").asText(null);
            if (StringUtils.hasText(url)) {
                return url;
            }
        }
        return null;
    }

    private Long toLong(String value) {
        return toLong(value, null);
    }

    private Long toLong(String value, Long defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value).longValue();
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer toInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private <T> List<List<T>> chunked(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(values.size(), start + size)));
        }
        return chunks;
    }

    public record YoutubeCategoryItem(Integer youtubeCategoryId, String title, Boolean assignable) {
    }

    public record YoutubeChannelItem(
            String youtubeChannelId,
            String title,
            String customUrl,
            String publishedAt,
            String thumbnailUrl,
            Long subscriberCount,
            boolean hiddenSubscriberCount,
            Long videoCount,
            Long viewCount,
            String uploadsPlaylistId
    ) {
    }

    public record PlaylistItemsPage(String nextPageToken, List<PlaylistVideoItem> items) {
    }

    public record PlaylistVideoItem(String videoId, String videoPublishedAt) {
    }

    public record YoutubeVideoItem(
            String youtubeVideoId,
            String youtubeChannelId,
            String title,
            String description,
            String publishedAt,
            List<String> tags,
            String thumbnailUrl,
            Integer categoryId,
            String duration,
            Long viewCount,
            Long likeCount,
            Long commentCount
    ) {
    }
}
