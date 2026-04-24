package com.infalce.batch.domain.youtube.batch.notification;

import com.infalce.batch.domain.youtube.api.YoutubeBatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeBatchDiscordNotifier {

    private static final int DISCORD_CONTENT_LIMIT = 2_000;
    private static final int DISCORD_FIELD_VALUE_LIMIT = 1_024;
    private static final int COLOR_SUCCESS = 0x2ECC71;
    private static final int COLOR_FAILED = 0xE74C3C;
    private static final int COLOR_WARNING = 0xF1C40F;

    private final YoutubeBatchProperties properties;
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

    public void notifyStepFailure(StepExecution stepExecution, String customSummary, String failureMessages, String duration) {
        DiscordWebhookRequest request = new DiscordWebhookRequest(
                null,
                properties.getNotification().getDiscord().getUsername(),
                List.of(new DiscordEmbed(
                        "유튜브 배치 스텝 실패",
                        COLOR_FAILED,
                        List.of(
                                new DiscordEmbedField("잡 이름", inlineCode(stepExecution.getJobExecution().getJobInstance().getJobName()), true),
                                new DiscordEmbedField("스텝", inlineCode(stepName(stepExecution.getStepName())), true),
                                new DiscordEmbedField("상태", inlineCode(status(stepExecution.getStatus().toString()) + " / " + stepExecution.getExitStatus().getExitCode()), true),
                                new DiscordEmbedField("실행 요약", truncateField("""
                                        실행 시간=%s
                                        읽기=%d
                                        쓰기=%d
                                        필터링=%d
                                        롤백=%d
                                        읽기 스킵=%d
                                        처리 스킵=%d
                                        쓰기 스킵=%d
                                        실패 예외 수=%d
                                        """.formatted(
                                        duration,
                                        stepExecution.getReadCount(),
                                        stepExecution.getWriteCount(),
                                        stepExecution.getFilterCount(),
                                        stepExecution.getRollbackCount(),
                                        stepExecution.getReadSkipCount(),
                                        stepExecution.getProcessSkipCount(),
                                        stepExecution.getWriteSkipCount(),
                                        stepExecution.getFailureExceptions().size()
                                )), false),
                                new DiscordEmbedField("스텝 집계", truncateField(blankToDash(stripLeadingBlank(customSummary))), false),
                                new DiscordEmbedField("실패 메시지", truncateField(blankToDash(failureMessages)), false)
                        ),
                        OffsetDateTime.now(ZoneOffset.UTC).toString()
                ))
        );
        send(request);
    }

    public void notifyJobFinished(JobExecution jobExecution, String stepSummary, String executionSummary) {
        DiscordWebhookRequest request = new DiscordWebhookRequest(
                null,
                properties.getNotification().getDiscord().getUsername(),
                List.of(new DiscordEmbed(
                        "유튜브 배치 잡 " + status(jobExecution.getStatus().toString()),
                        statusColor(jobExecution.getStatus()),
                        List.of(
                                new DiscordEmbedField("잡 이름", inlineCode(jobExecution.getJobInstance().getJobName()), true),
                                new DiscordEmbedField("상태", inlineCode(status(jobExecution.getStatus().toString())), true),
                                new DiscordEmbedField("스텝 결과", truncateField(codeBlock(stepSummary)), false),
                                new DiscordEmbedField("최종 요약", truncateField(codeBlock(executionSummary)), false)
                        ),
                        OffsetDateTime.now(ZoneOffset.UTC).toString()
                ))
        );
        send(request);
    }

    private void send(DiscordWebhookRequest request) {
        YoutubeBatchProperties.DiscordProperties discord = properties.getNotification().getDiscord();
        if (!discord.isEnabled() || !StringUtils.hasText(discord.getWebhookUrl())) {
            return;
        }

        try {
            restClient.post()
                    .uri(discord.getWebhookUrl())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("failed to send discord batch notification", e);
        }
    }

    private int statusColor(BatchStatus status) {
        if (status == BatchStatus.COMPLETED) {
            return COLOR_SUCCESS;
        }
        if (status == BatchStatus.FAILED) {
            return COLOR_FAILED;
        }
        return COLOR_WARNING;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value) || value.length() <= DISCORD_CONTENT_LIMIT) {
            return value;
        }
        return value.substring(0, DISCORD_CONTENT_LIMIT - 3) + "...";
    }

    private String truncateField(String value) {
        if (!StringUtils.hasText(value) || value.length() <= DISCORD_FIELD_VALUE_LIMIT) {
            return value;
        }
        return value.substring(0, DISCORD_FIELD_VALUE_LIMIT - 3) + "...";
    }

    private String inlineCode(String value) {
        return "`" + value + "`";
    }

    private String codeBlock(String value) {
        return "```" + value + "```";
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String stripLeadingBlank(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.stripLeading();
    }

    private String status(String status) {
        return switch (status) {
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            case "STARTED" -> "시작됨";
            case "STARTING" -> "시작 중";
            case "STOPPED" -> "중지";
            case "STOPPING" -> "중지 중";
            case "UNKNOWN" -> "알 수 없음";
            default -> status;
        };
    }

    private String stepName(String stepName) {
        return switch (stepName) {
            case "youtubeCategorySyncStep" -> "카테고리 동기화";
            case "youtubeChannelDiscoveryStep" -> "채널 탐색";
            case "youtubeChannelMetricsRefreshStep" -> "채널 메트릭 갱신";
            default -> stepName;
        };
    }

    private record DiscordWebhookRequest(String content, String username, List<DiscordEmbed> embeds) {
    }

    private record DiscordEmbed(
            String title,
            Integer color,
            List<DiscordEmbedField> fields,
            String timestamp
    ) {
    }

    private record DiscordEmbedField(String name, String value, boolean inline) {
    }
}
