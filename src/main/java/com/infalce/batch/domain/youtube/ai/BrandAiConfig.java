package com.infalce.batch.domain.youtube.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BrandAiConfig {

    @Bean
    public ChatClient openAiChatClient(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            BrandAiProperties properties
    ) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel().modelName())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();

        return ChatClient.builder(chatModel, ObservationRegistry.NOOP, null)
                .build();
    }
}
