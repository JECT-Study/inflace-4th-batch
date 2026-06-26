package com.infalce.batch.domain.youtube.api;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "youtube.batch")
public class YoutubeBatchProperties {

    private String apiKey;
    private List<String> fallbackApiKeys = new ArrayList<>();
    private String baseUrl = "https://www.googleapis.com/youtube/v3";
    private String regionCode = "KR";
    private String hl = "ko_KR";
    private int minChannelCount = 50;
    private int recentDays = 30;
    private final NotificationProperties notification = new NotificationProperties();

    @Getter
    @Setter
    public static class NotificationProperties {

        private final DiscordProperties discord = new DiscordProperties();
    }

    @Getter
    @Setter
    public static class DiscordProperties {

        private boolean enabled = true;
        private String webhookUrl;
        private String username = "inflace-batch";
    }
}
