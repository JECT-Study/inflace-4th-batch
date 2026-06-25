package com.infalce.batch.domain.youtube.ai;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiBrandAiExtractor implements BrandAiExtractor {

    @Qualifier("openAiChatClient")
    private final ChatClient chatClient;
    private final BrandAiProperties properties;

    @Override
    @Bulkhead(name = "youtubeBrandAiExtractor", type = Bulkhead.Type.SEMAPHORE)
    public Optional<String> extractBrand(BrandAiVideo video) {
        if (video == null || !StringUtils.hasText(video.description())) {
            return Optional.empty();
        }

        try {
            return normalizeAnswer(callOpenAi(video));
        } catch (Exception e) {
            log.warn("youtube brand AI extraction failed. youtubeVideoId={} message={}",
                    video.youtubeVideoId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String callOpenAi(BrandAiVideo video) {
        List<Message> messages = List.of(
                new SystemMessage(BrandExtractionPrompt.systemMessage()),
                new UserMessage(BrandExtractionPrompt.humanMessage(video, properties.getContextRadius()))
        );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel().modelName())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();

        return chatClient.prompt(new Prompt(messages, options))
                .call()
                .content();
    }

    private Optional<String> normalizeAnswer(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            return Optional.empty();
        }

        String answer = rawAnswer.trim()
                .replaceAll("^[\"'`]+", "")
                .replaceAll("[\"'`]+$", "")
                .trim();
        if (!StringUtils.hasText(answer)) {
            return Optional.empty();
        }

        String lowered = answer.toLowerCase(Locale.ROOT);
        if (List.of("null", "없음", "none", "n/a", "해당없음", "미확인").contains(lowered)) {
            return Optional.empty();
        }
        if (answer.length() > properties.getMaxAnswerLength() || answer.contains("\n")) {
            return Optional.empty();
        }
        return Optional.of(answer);
    }

}
